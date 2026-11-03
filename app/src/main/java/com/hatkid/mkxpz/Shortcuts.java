package com.hatkid.mkxpz;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * ".essentia" shortcut files for frontends (Díptico, ES-DE, Daijishō...).
 * Each one is a tiny text file named after the game; its first line is the game folder.
 * Frontends list them like ROMs and open them with Essentia.
 */
public final class Shortcuts
{
    private static final String TAG = "Essentia[Shortcuts]";
    public static final String EXT = ".essentia";

    private Shortcuts() { }

    private static final String PREF_FOLDER = "shortcuts_folder";

    /** Rewrites the shortcuts in the folder used last time (after adding games). */
    public static void refresh(Context c)
    {
        String last = c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE).getString(PREF_FOLDER, null);
        if (last != null) export(c, new File(last));
    }

    /** Writes one shortcut per game in the library. Returns how many were written. */
    public static int export(Context c, File folder)
    {
        c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_FOLDER, folder.getAbsolutePath()).apply();
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        int n = 0;
        for (Library.Entry e : Library.load(c)) {
            File f = new File(folder, Saves.safeName(e.displayTitle()) + EXT);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
                w.write(e.path);
                w.write("\n# Essentia: acceso a un juego de RPG Maker. La primera línea es la carpeta del juego.\n");
                n++;
            } catch (Exception ex) {
                Log.w(TAG, "Could not write " + f + ": " + ex);
            }
        }
        return n;
    }
}
