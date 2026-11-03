package com.hatkid.mkxpz;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where each game keeps its saves, and importing saves from other apps.
 *
 * By default saves live in the game's own folder, like JoiPlay, so the same save
 * works in both apps. Optionally they go to /storage/emulated/0/Essentia/Partidas/<game>.
 */
public final class Saves
{
    private static final String TAG = "Essentia[Saves]";

    private Saves() { }

    /** The folder where this game's saves go (created if needed). */
    public static File dirFor(Context c, File gameDir, String title)
    {
        File dir;
        if (EssentiaSettings.SAVES_ESSENTIA.equals(EssentiaSettings.saveLocation(c)) || !canWrite(gameDir)) {
            dir = new File(new File(Environment.getExternalStorageDirectory(), "Essentia/Partidas"), safeName(title));
        } else {
            dir = gameDir;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static boolean canWrite(File dir)
    {
        try {
            File probe = new File(dir, ".essentia_write_test");
            if (probe.createNewFile() || probe.exists()) {
                //noinspection ResultOfMethodCallIgnored
                probe.delete();
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static String safeName(String s)
    {
        String n = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        return n.isEmpty() ? "Juego" : n;
    }

    /** True for files that look like RPG Maker saves (Game.rxdata, Save01.rvdata2, ...). */
    public static boolean looksLikeSave(String name)
    {
        String n = name.toLowerCase(Locale.ROOT);
        if (!(n.endsWith(".rxdata") || n.endsWith(".rvdata") || n.endsWith(".rvdata2") || n.endsWith(".sav")))
            return false;
        // Game data that sits next to saves in some folders
        return !(n.startsWith("scripts") || n.startsWith("map") || n.startsWith("tilesets")
            || n.startsWith("system.") || n.startsWith("actors") || n.startsWith("classes")
            || n.startsWith("skills") || n.startsWith("items") || n.startsWith("weapons")
            || n.startsWith("armors") || n.startsWith("enemies") || n.startsWith("troops")
            || n.startsWith("states") || n.startsWith("animations") || n.startsWith("commonevents")
            || n.startsWith("pluginscripts"));
    }

    /** Save files directly inside a folder. */
    public static List<File> findIn(File dir)
    {
        List<File> out = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return out;
        for (File f : files) if (f.isFile() && looksLikeSave(f.getName())) out.add(f);
        return out;
    }

    /** Saves left by Essentia 0.1.14 and older (all games shared the app's internal folder). */
    public static List<File> legacy(Context c)
    {
        return findIn(c.getFilesDir());
    }

    /** Copies files into the save folder, keeping a backup of any save it replaces. */
    public static int copyFiles(List<File> files, File target)
    {
        int n = 0;
        for (File f : files) {
            try (InputStream in = new FileInputStream(f)) {
                if (copy(in, target, f.getName())) n++;
            } catch (Exception e) {
                Log.w(TAG, "Could not import " + f + ": " + e);
            }
        }
        return n;
    }

    /** Copies documents picked with Android's file picker into the save folder. */
    public static int copyUris(Context c, List<Uri> uris, List<String> names, File target)
    {
        int n = 0;
        for (int i = 0; i < uris.size(); i++) {
            try (InputStream in = c.getContentResolver().openInputStream(uris.get(i))) {
                if (in != null && copy(in, target, names.get(i))) n++;
            } catch (Exception e) {
                Log.w(TAG, "Could not import " + uris.get(i) + ": " + e);
            }
        }
        return n;
    }

    private static boolean copy(InputStream in, File targetDir, String name) throws Exception
    {
        File dest = new File(targetDir, name);
        if (dest.exists()) {
            File backup = new File(targetDir, name + ".respaldo-" + System.currentTimeMillis());
            if (!dest.renameTo(backup)) Log.w(TAG, "Could not back up " + dest);
        }
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
        return true;
    }
}
