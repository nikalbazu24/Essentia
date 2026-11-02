package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.net.URLDecoder;
import java.util.List;

/**
 * Essentia entry point.
 *
 * Decides which game folder to run, then starts the engine (MainActivity) in its
 * own process. The game folder can come from:
 *  1. A frontend (Díptico, ES-DE, Daijishō...): a file path or URI in the intent
 *     data, or a string extra such as "path", "game" or "rom". The path may point to
 *     the game folder itself or to any file inside it (Game.exe, Game.ini...).
 *  2. Android's folder picker, when Essentia is opened from its own icon.
 */
public class LauncherActivity extends Activity
{
    private static final String TAG = "Essentia[Launcher]";
    private static final int REQ_ALL_FILES = 1;
    private static final int REQ_PICK_FOLDER = 2;

    private static final String PREFS = "essentia";
    private static final String PREF_LAST_TREE = "last_tree_uri";

    /** Extra keys accepted from frontends (checked in this order). */
    private static final String[] PATH_EXTRAS = {
        "path", "game", "rom", "ROM", "file", "folder", "gamePath", "game_path",
        "filePath", "file_path", "romPath", "rom_path"
    };

    /** Files that identify an RPG Maker XP/VX/VX Ace game folder (case-insensitive). */
    private static final String[] GAME_MARKERS = {
        "game.ini", "game.exe", "mkxp.json", "game.rgssad", "game.rgss2a", "game.rgss3a"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            continueLaunch();
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        // A frontend launched another game while this screen was still open.
        super.onNewIntent(intent);
        setIntent(intent);
        continueLaunch();
    }

    // ------------------------------------------------------------------ flow

    private void continueLaunch()
    {
        if (!hasAllFilesAccess()) {
            askAllFilesAccess();
            return;
        }

        String requested = pathFromIntent(getIntent());

        if (requested != null) {
            Log.i(TAG, "Path from caller: " + requested);
            File dir = resolveGameFolder(requested);
            if (dir != null) {
                startGame(dir);
            } else {
                showError(getString(R.string.err_not_a_game, requested), true);
            }
            return;
        }

        // Opened from the app icon: let the user pick the game folder.
        openFolderPicker();
    }

    private void startGame(File gameDir)
    {
        Log.i(TAG, "Starting game in " + gameDir);

        // The engine can only run one game per process (Ruby can't restart), so make
        // sure any game still running is closed before starting a new one.
        killGameProcess();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_GAME_PATH, gameDir.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private void killGameProcess()
    {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;

        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return;

        String gameProcess = getPackageName() + ":game";
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (gameProcess.equals(p.processName)) {
                Log.i(TAG, "Closing running game (pid " + p.pid + ")");
                android.os.Process.killProcess(p.pid);
            }
        }
    }

    // -------------------------------------------------------- permissions

    private boolean hasAllFilesAccess()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        // Android 10 and older use the classic storage permission (requested by the engine).
        return true;
    }

    private void askAllFilesAccess()
    {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_grant, (d, w) -> {
                try {
                    Uri uri = Uri.parse("package:" + getPackageName());
                    startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
                        REQ_ALL_FILES);
                } catch (Exception e) {
                    startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        REQ_ALL_FILES);
                }
            })
            .setNegativeButton(R.string.cancel, (d, w) -> finish())
            .show();
    }

    // -------------------------------------------------------- folder picker

    private void openFolderPicker()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        String last = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_LAST_TREE, null);
        if (last != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(last));
        }

        try {
            Toast.makeText(this, R.string.pick_hint, Toast.LENGTH_LONG).show();
            startActivityForResult(intent, REQ_PICK_FOLDER);
        } catch (Exception e) {
            Log.e(TAG, "No folder picker available", e);
            showError(getString(R.string.err_no_picker), false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ALL_FILES) {
            if (hasAllFilesAccess()) {
                continueLaunch();
            } else {
                finish();
            }
            return;
        }

        if (requestCode == REQ_PICK_FOLDER) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finish();
                return;
            }

            Uri tree = data.getData();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_LAST_TREE, tree.toString()).apply();

            String path = pathFromUri(tree);
            File dir = (path != null) ? resolveGameFolder(path) : null;

            if (dir != null) {
                startGame(dir);
            } else {
                Toast.makeText(this,
                    getString(R.string.err_not_a_game, path != null ? path : tree.toString()),
                    Toast.LENGTH_LONG).show();
                openFolderPicker();
            }
        }
    }

    // -------------------------------------------------------- path helpers

    /** Extracts a filesystem path from whatever a frontend sent us. */
    private String pathFromIntent(Intent intent)
    {
        if (intent == null) return null;

        if (intent.getExtras() != null) {
            for (String key : PATH_EXTRAS) {
                Object value = intent.getExtras().get(key);
                if (value instanceof String && !((String) value).isEmpty()) {
                    String p = pathFromString((String) value);
                    if (p != null) return p;
                } else if (value instanceof Uri) {
                    String p = pathFromUri((Uri) value);
                    if (p != null) return p;
                }
            }
        }

        Uri data = intent.getData();
        if (data != null) {
            return pathFromUri(data);
        }

        return null;
    }

    private String pathFromString(String value)
    {
        if (value.startsWith("/")) return value;
        if (value.contains("://")) return pathFromUri(Uri.parse(value));
        return null;
    }

    /**
     * Converts file:// and storage-provider content:// URIs into plain paths.
     * Essentia has all-files access, so plain paths are the simplest way to read games.
     */
    private String pathFromUri(Uri uri)
    {
        if (uri == null) return null;
        String scheme = uri.getScheme();

        if (scheme == null || "file".equals(scheme)) {
            return uri.getPath();
        }

        if (!"content".equals(scheme)) return null;

        try {
            String docId = null;
            List<String> segments = uri.getPathSegments();

            if (segments.size() >= 4 && "tree".equals(segments.get(0)) && "document".equals(segments.get(2))) {
                docId = DocumentsContract.getDocumentId(uri);
            } else if (segments.size() >= 2 && "tree".equals(segments.get(0))) {
                docId = DocumentsContract.getTreeDocumentId(uri);
            } else if (DocumentsContract.isDocumentUri(this, uri)) {
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

    /** "primary:Juegos/Pokemon Z" -> /storage/emulated/0/Juegos/Pokemon Z ; "1234-ABCD:x" -> /storage/1234-ABCD/x */
    private String pathFromExternalStorageDocId(String docId)
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

    /**
     * Turns the given path into the game folder: accepts the folder itself, any file in
     * it, or a folder that has the game one level down (common after unzipping).
     */
    private File resolveGameFolder(String path)
    {
        File f = new File(path);
        if (f.isFile()) f = f.getParentFile();
        if (f == null || !f.isDirectory()) return null;

        if (isGameFolder(f)) return f;

        File[] children = f.listFiles();
        if (children != null) {
            File only = null;
            for (File c : children) {
                if (c.isDirectory() && isGameFolder(c)) {
                    if (only != null) return null; // several games: ambiguous
                    only = c;
                }
            }
            return only;
        }
        return null;
    }

    private boolean isGameFolder(File dir)
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

    private void showError(String message, boolean offerPicker)
    {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton(R.string.close, (d, w) -> finish());
        if (offerPicker) {
            b.setPositiveButton(R.string.choose_folder, (d, w) -> openFolderPicker());
        }
        b.show();
    }
}
