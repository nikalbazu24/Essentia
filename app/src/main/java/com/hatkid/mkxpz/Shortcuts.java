package com.hatkid.mkxpz;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
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

    // ------------------------------------------------------------ home screen

    /** True when the launcher can add game shortcuts to the home screen. */
    public static boolean canPin(Context c)
    {
        if (Build.VERSION.SDK_INT < 26) return false;
        android.content.pm.ShortcutManager sm = c.getSystemService(android.content.pm.ShortcutManager.class);
        return sm != null && sm.isRequestPinShortcutSupported();
    }

    /** Asks the launcher to add a shortcut that opens this game directly, with its cover as icon. */
    public static boolean pin(Context c, Library.Entry e)
    {
        if (!canPin(c)) return false;
        try {
            Intent open = new Intent(c, LauncherActivity.class);
            open.setAction(Intent.ACTION_VIEW);
            open.putExtra("path", e.path);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            android.graphics.drawable.Icon icon;
            Bitmap art = coverIcon(e);
            icon = art != null ? android.graphics.drawable.Icon.createWithBitmap(art)
                : android.graphics.drawable.Icon.createWithResource(c, R.mipmap.ic_launcher);

            android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(c,
                    "game-" + Integer.toHexString(e.path.hashCode()))
                .setShortLabel(e.displayTitle())
                .setLongLabel(e.displayTitle())
                .setIcon(icon)
                .setIntent(open)
                .build();
            android.content.pm.ShortcutManager sm = c.getSystemService(android.content.pm.ShortcutManager.class);
            return sm.requestPinShortcut(info, null);
        } catch (Exception ex) {
            Log.w(TAG, "Pin shortcut: " + ex);
            return false;
        }
    }

    /** Square, rounded picture from the game's cover. */
    private static Bitmap coverIcon(Library.Entry e)
    {
        File f = e.cover != null && !e.cover.isEmpty() ? new File(e.cover) : Games.findCover(new File(e.path));
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 256 && o.outHeight / (sample * 2) >= 256) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (src == null) return null;
        int side = Math.min(src.getWidth(), src.getHeight());
        int size = 192;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(0, 0, size, size, size * 0.22f, size * 0.22f, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        Rect from = new Rect((src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
            (src.getWidth() + side) / 2, (src.getHeight() + side) / 2);
        canvas.drawBitmap(src, from, new Rect(0, 0, size, size), p);
        src.recycle();
        return out;
    }
}
