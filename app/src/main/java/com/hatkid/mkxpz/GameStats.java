package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * What Essentia knows about a game besides its name: play time and sessions
 * (written by the game screen, which runs in its own process, to a small file),
 * saves, version, RPG Maker kind and size on disk.
 */
public final class GameStats
{
    public long playMs;
    public int sessions;
    public long lastSession;
    // filled by details()
    public int saves = -1;
    public long lastSave;
    public String version = "";
    public String kind = "";
    public long size = -1;

    private GameStats() { }

    // ------------------------------------------------------------ play time

    private static File playFile(Context c, String path)
    {
        File dir = new File(c.getFilesDir(), "playtime");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, Integer.toHexString(path.hashCode()) + ".txt");
    }

    /** Play time and sessions (quick: a tiny file in Essentia's own storage). */
    public static GameStats quick(Context c, String path)
    {
        GameStats s = new GameStats();
        File f = playFile(c, path);
        if (!f.isFile()) return s;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 256)];
            int n = in.read(b);
            String[] p = new String(b, 0, Math.max(n, 0), StandardCharsets.UTF_8).trim().split(" ");
            if (p.length > 0) s.playMs = Long.parseLong(p[0]);
            if (p.length > 1) s.sessions = Integer.parseInt(p[1]);
            if (p.length > 2) s.lastSession = Long.parseLong(p[2]);
        } catch (Exception ignored) { }
        return s;
    }

    /** Adds played time (called by the game screen). */
    public static synchronized void addPlay(Context c, String path, long ms, boolean newSession)
    {
        if (path == null || path.isEmpty()) return;
        GameStats s = quick(c, path);
        s.playMs += Math.max(0, ms);
        if (newSession) s.sessions++;
        s.lastSession = System.currentTimeMillis();
        File f = playFile(c, path);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write((s.playMs + " " + s.sessions + " " + s.lastSession).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Total play time of all games. */
    public static long totalPlay(Context c, List<Library.Entry> games)
    {
        long t = 0;
        for (Library.Entry e : games) t += quick(c, e.path).playMs;
        return t;
    }

    // ------------------------------------------------------------ details (slow: run off the main thread)

    public static GameStats details(Context c, Library.Entry e)
    {
        GameStats s = quick(c, e.path);
        File dir = new File(e.path);
        try {
            List<File> found = Saves.findIn(Saves.dirFor(c, dir, e.displayTitle()));
            s.saves = found.size();
            for (File f : found) s.lastSave = Math.max(s.lastSave, f.lastModified());
        } catch (Exception ignored) { }
        try {
            String v = GameUpdater.localVersion(dir);
            s.version = v == null ? "" : v;
        } catch (Exception ignored) { }
        try {
            s.kind = GameProfile.of(c, dir).label();
        } catch (Exception ex) {
            s.kind = kindOf(dir);
        }
        s.size = cachedSize(c, dir);
        return s;
    }

    private static String kindOf(File dir)
    {
        if (new File(dir, "Game.rgss3a").exists() || new File(dir, "Data/Scripts.rvdata2").exists()) return "RPG Maker VX Ace";
        if (new File(dir, "Game.rgss2a").exists() || new File(dir, "Data/Scripts.rvdata").exists()) return "RPG Maker VX";
        if (new File(dir, "Game.rgssad").exists() || new File(dir, "Data/Scripts.rxdata").exists()) {
            boolean essentials = new File(dir, "PBS").isDirectory() || new File(dir, "Data/species.dat").exists()
                || new File(dir, "Data/pokemon.dat").exists() || new File(dir, "Graphics/Battlers").isDirectory()
                || new File(dir, "Graphics/Pokemon").isDirectory();
            return essentials ? "Pokémon Essentials" : "RPG Maker XP";
        }
        return "RPG Maker";
    }

    /** Size of the game folder, measured once (and again after an update). */
    private static long cachedSize(Context c, File dir)
    {
        SharedPreferences p = c.getSharedPreferences("essentia_sizes", Context.MODE_PRIVATE);
        String key = dir.getAbsolutePath();
        long stamp = new File(dir, "Data").lastModified();
        if (p.getLong(key + ":stamp", -1) == stamp && p.contains(key)) return p.getLong(key, -1);
        long size = sizeOf(dir, 0);
        p.edit().putLong(key, size).putLong(key + ":stamp", stamp).apply();
        return size;
    }

    private static long sizeOf(File f, int depth)
    {
        if (depth > 12) return 0;
        File[] list = f.listFiles();
        if (list == null) return f.length();
        long t = 0;
        for (File x : list) t += x.isDirectory() ? sizeOf(x, depth + 1) : x.length();
        return t;
    }

    // ------------------------------------------------------------ text

    public static String duration(Context c, long ms)
    {
        long min = ms / 60000;
        if (min < 1) return c.getString(R.string.stat_less_minute);
        if (min < 60) return c.getString(R.string.stat_minutes, (int) min);
        long h = min / 60;
        long m = min % 60;
        return m == 0 ? c.getString(R.string.stat_hours, (int) h) : c.getString(R.string.stat_hours_minutes, (int) h, (int) m);
    }

    public static String bytes(long b)
    {
        if (b < 0) return "…";
        if (b < 1024L * 1024) return Math.max(1, b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.0f MB", b / 1048576.0);
        return String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0);
    }
}
