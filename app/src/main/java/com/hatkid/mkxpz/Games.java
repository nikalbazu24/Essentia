package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared helpers: finding games, reading their title/cover and starting them. */
public final class Games
{
    private static final String TAG = "Essentia[Games]";

    /** Files that identify an RPG Maker XP/VX/VX Ace game folder (case-insensitive). */
    private static final String[] GAME_MARKERS = {
        "game.ini", "game.exe", "mkxp.json", "game.rgssad", "game.rgss2a", "game.rgss3a"
    };

    private static final String[] IMAGE_EXTS = { ".png", ".jpg", ".jpeg", ".bmp", ".webp" };

    private Games() { }

    // ------------------------------------------------------------ permissions

    public static boolean hasAllFilesAccess()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true; // Android 10 and older: classic storage permission, asked by the engine
    }

    public static void askAllFilesAccess(Activity a, int requestCode, Runnable onCancel)
    {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_grant, (d, w) -> {
                try {
                    Uri uri = Uri.parse("package:" + a.getPackageName());
                    a.startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri), requestCode);
                } catch (Exception e) {
                    a.startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), requestCode);
                }
            })
            .setNegativeButton(R.string.cancel, (d, w) -> onCancel.run())
            .show();
    }

    // ------------------------------------------------------------ starting

    /**
     * Starts the engine with the given game folder. The engine runs in its own process
     * (":game") because Ruby can only run one game per process; any running game is
     * closed first.
     *
     * @param newTask true when launched for a frontend (the game gets its own task, and
     *                closing it returns to the frontend); false from the library (closing
     *                the game returns to the library).
     */
    public static void startGame(Activity a, File gameDir, boolean newTask)
    {
        Log.i(TAG, "Starting game in " + gameDir);
        killGameProcess(a);
        Library.markPlayed(a, gameDir.getAbsolutePath());

        Intent intent = new Intent(a, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_GAME_PATH, gameDir.getAbsolutePath());
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        a.startActivity(intent);
        a.overridePendingTransition(0, 0);
    }

    public static void killGameProcess(Context c)
    {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return;

        String gameProcess = c.getPackageName() + ":game";
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (gameProcess.equals(p.processName)) {
                Log.i(TAG, "Closing running game (pid " + p.pid + ")");
                android.os.Process.killProcess(p.pid);
            }
        }
    }

    // ------------------------------------------------------------ folders

    public static boolean isGameFolder(File dir)
    {
        String[] names = dir.list();
        if (names == null) return false;
        for (String n : names) {
            for (String m : GAME_MARKERS) {
                if (n.equalsIgnoreCase(m)) return true;
            }
        }
        return false;
    }

    /**
     * Turns a path into the game folder: accepts the folder itself, any file in it,
     * or a folder that has exactly one game one level down (common after unzipping).
     */
    public static File resolveGameFolder(String path)
    {
        if (path == null) return null;
        File f = new File(path);
        if (f.isFile()) f = f.getParentFile();
        if (f == null || !f.isDirectory()) return null;

        if (isGameFolder(f)) return f;

        File[] children = f.listFiles();
        if (children == null) return null;
        File only = null;
        for (File c : children) {
            if (c.isDirectory() && isGameFolder(c)) {
                if (only != null) return null; // several games: ambiguous
                only = c;
            }
        }
        return only;
    }

    /** Finds every game inside {@code root} (the folder itself, or up to two levels down). */
    public static List<File> findGames(File root)
    {
        List<File> out = new ArrayList<>();
        collect(root, 0, out);
        return out;
    }

    private static void collect(File dir, int depth, List<File> out)
    {
        if (dir == null || !dir.isDirectory()) return;
        if (isGameFolder(dir)) {
            out.add(dir);
            return;
        }
        if (depth >= 2) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory() && !c.getName().startsWith(".")) collect(c, depth + 1, out);
        }
    }

    // ------------------------------------------------------------ title & cover

    /** Title from Game.ini ([Game] Title=...), or the folder name. */
    public static String readTitle(File dir)
    {
        File ini = findChild(dir, "Game.ini");
        if (ini != null && ini.length() < 64 * 1024) {
            try (FileInputStream in = new FileInputStream(ini)) {
                byte[] data = new byte[(int) ini.length()];
                int n = in.read(data);
                String text = decode(data, Math.max(n, 0));
                for (String line : text.split("\\r?\\n")) {
                    String t = line.trim();
                    if (t.toLowerCase(Locale.ROOT).startsWith("title=")) {
                        String title = t.substring(6).trim();
                        if (!title.isEmpty()) return title;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read " + ini + ": " + e);
            }
        }
        return dir.getName();
    }

    private static String decode(byte[] data, int len)
    {
        int start = 0;
        if (len >= 3 && (data[0] & 0xff) == 0xEF && (data[1] & 0xff) == 0xBB && (data[2] & 0xff) == 0xBF)
            start = 3;
        String utf8 = new String(data, start, len - start, Charset.forName("UTF-8"));
        if (!utf8.contains("�")) return utf8;
        return new String(data, start, len - start, Charset.forName("windows-1252"));
    }

    /** Best guess for a cover picture: the game's title screen, or an icon. */
    public static File findCover(File dir)
    {
        File titles = findChild(findChild(dir, "Graphics"), "Titles");
        File f = findImage(titles, "title");
        if (f == null) f = findAnyImage(titles);
        if (f == null) f = findImage(findChild(findChild(dir, "Graphics"), "System"), "title");
        if (f == null) f = findImage(findChild(findChild(dir, "Graphics"), "Pictures"), "title");
        if (f == null) f = findImage(dir, "cover");
        if (f == null) f = findImage(dir, "icon");
        return f;
    }

    private static File findChild(File dir, String name)
    {
        if (dir == null) return null;
        File direct = new File(dir, name);
        if (direct.exists()) return direct;
        String[] names = dir.list();
        if (names == null) return null;
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) return new File(dir, n);
        }
        return null;
    }

    private static boolean isImage(String name)
    {
        String n = name.toLowerCase(Locale.ROOT);
        for (String e : IMAGE_EXTS) if (n.endsWith(e)) return true;
        return false;
    }

    private static File findImage(File dir, String baseName)
    {
        if (dir == null || !dir.isDirectory()) return null;
        String[] names = dir.list();
        if (names == null) return null;
        String base = baseName.toLowerCase(Locale.ROOT);
        File prefix = null;
        for (String n : names) {
            if (!isImage(n)) continue;
            String lower = n.toLowerCase(Locale.ROOT);
            String stem = lower.substring(0, lower.lastIndexOf('.'));
            if (stem.equals(base)) return new File(dir, n);
            if (prefix == null && stem.startsWith(base)) prefix = new File(dir, n);
        }
        return prefix;
    }

    private static File findAnyImage(File dir)
    {
        if (dir == null || !dir.isDirectory()) return null;
        String[] names = dir.list();
        if (names == null) return null;
        java.util.Arrays.sort(names);
        for (String n : names) if (isImage(n)) return new File(dir, n);
        return null;
    }

    // ------------------------------------------------------------ URIs & paths

    /** Extracts a filesystem path from a frontend's string (a path or a URI). */
    public static String pathFromString(Context c, String value)
    {
        if (value == null || value.isEmpty()) return null;
        if (value.startsWith("/")) return value;
        if (value.contains("://")) return pathFromUri(c, Uri.parse(value));
        return null;
    }

    /** Converts file:// and storage-provider content:// URIs into plain paths. */
    public static String pathFromUri(Context c, Uri uri)
    {
        if (uri == null) return null;
        String scheme = uri.getScheme();

        if (scheme == null || "file".equals(scheme)) return uri.getPath();
        if (!"content".equals(scheme)) return null;

        try {
            String docId = null;
            List<String> segments = uri.getPathSegments();

            if (segments.size() >= 4 && "tree".equals(segments.get(0)) && "document".equals(segments.get(2))) {
                docId = DocumentsContract.getDocumentId(uri);
            } else if (segments.size() >= 2 && "tree".equals(segments.get(0))) {
                docId = DocumentsContract.getTreeDocumentId(uri);
            } else if (DocumentsContract.isDocumentUri(c, uri)) {
                docId = DocumentsContract.getDocumentId(uri);
            }

            if (docId != null && "com.android.externalstorage.documents".equals(uri.getAuthority())) {
                return pathFromExternalStorageDocId(docId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read URI " + uri, e);
        }

        // Last resort: some providers put the real path inside the URI.
        try {
            String decoded = URLDecoder.decode(uri.toString(), "UTF-8");
            int i = decoded.indexOf("/storage/");
            if (i >= 0) return decoded.substring(i);
            i = decoded.indexOf("/sdcard/");
            if (i >= 0) return decoded.substring(i);
        } catch (Exception ignored) { }

        return null;
    }

    /** "primary:Juegos/X" -> /storage/emulated/0/Juegos/X ; "1234-ABCD:x" -> /storage/1234-ABCD/x */
    private static String pathFromExternalStorageDocId(String docId)
    {
        int colon = docId.indexOf(':');
        String volume = colon >= 0 ? docId.substring(0, colon) : docId;
        String rel = colon >= 0 ? docId.substring(colon + 1) : "";

        String root;
        if ("primary".equalsIgnoreCase(volume)) {
            root = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else if ("home".equalsIgnoreCase(volume)) {
            root = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Documents";
        } else {
            root = "/storage/" + volume;
        }
        return rel.isEmpty() ? root : root + "/" + rel;
    }
}
