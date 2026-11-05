package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The user's game library, stored as JSON in Essentia's preferences. */
public final class Library
{
    private static final String KEY = "library_v1";

    public static final class Entry
    {
        public final String path;
        public String title;
        public long lastPlayed;
        /** Custom cover image chosen by the user (absolute path), or "" for automatic. */
        public String cover = "";
        /** Experimental 16:9 mode for this game (older setting, now part of screenMode). */
        public boolean widescreen = false;
        /** Screen format for this game, or "" to use the general setting. */
        public String screenMode = "";
        WideOptions panorama = WideOptions.DEFAULT;
        /** Ruby engine for this game: "" / modern (Ruby 3.1) or classic (Ruby 1.8). */
        public String engine = "";
        /** Translate this game to Spanish automatically. */
        public boolean translate = false;
        public String translationLanguage = "es";

        Entry(String path, String title, long lastPlayed)
        {
            this.path = path;
            this.title = title;
            this.lastPlayed = lastPlayed;
        }

        public String displayTitle()
        {
            return title == null || title.isEmpty() ? new File(path).getName() : title;
        }
    }

    private Library() { }

    private static SharedPreferences prefs(Context c)
    {
        return c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE);
    }

    /** All games, most recently played first, then by title. */
    public static synchronized List<Entry> load(Context c)
    {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry(o.getString("path"), o.optString("title", ""), o.optLong("lastPlayed", 0));
                e.cover = o.optString("cover", "");
                e.widescreen = o.optBoolean("widescreen", false);
                e.screenMode = o.optString("screenMode", e.widescreen ? EssentiaSettings.SCREEN_WIDE : "");
                e.engine = o.optString("engine", "");
                e.panorama = WideOptions.read(o.optJSONObject("panorama"));
                e.translate = o.optBoolean("translate", false);
                e.translationLanguage = TranslationLanguage.normalize(o.optString("translationLanguage", "es"));
                out.add(e);
            }
        } catch (Exception ignored) { }

        Collections.sort(out, (a, b) -> {
            if (a.lastPlayed != b.lastPlayed) return Long.compare(b.lastPlayed, a.lastPlayed);
            return a.displayTitle().compareToIgnoreCase(b.displayTitle());
        });
        return out;
    }

    private static synchronized void save(Context c, List<Entry> list)
    {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("path", e.path);
                o.put("title", e.title);
                o.put("lastPlayed", e.lastPlayed);
                o.put("cover", e.cover);
                o.put("widescreen", EssentiaSettings.SCREEN_WIDE.equals(e.screenMode));
                o.put("screenMode", e.screenMode);
                o.put("engine", e.engine);
                o.put("panorama", e.panorama.write());
                o.put("translate", e.translate);
                o.put("translationLanguage", e.translationLanguage);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString(KEY, arr.toString()).commit();
    }

    /** The entry for a game folder, or null if it isn't in the library. */
    public static synchronized Entry find(Context c, String path)
    {
        for (Entry e : load(c)) if (e.path.equals(path)) return e;
        return null;
    }

    /** Adds a game (if new). Returns true when it was added. */
    public static synchronized boolean add(Context c, File dir)
    {
        // A scan begun before deletion must not re-add a folder that has since disappeared.
        if (!dir.isDirectory() || !Games.isGameFolder(dir)) return false;
        String path = dir.getAbsolutePath();
        List<Entry> list = load(c);
        for (Entry e : list) if (e.path.equals(path)) return false;
        list.add(new Entry(path, Games.readTitle(dir), 0));
        save(c, list);
        return true;
    }

    /** Saves the changes made to an entry (title, cover, widescreen...). */
    public static synchronized void update(Context c, Entry changed)
    {
        List<Entry> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).path.equals(changed.path)) list.set(i, changed);
        }
        save(c, list);
    }

    public static synchronized void remove(Context c, String path)
    {
        List<Entry> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).path.equals(path)) list.remove(i);
        }
        save(c, list);
    }

    public static synchronized Entry markPlayed(Context c, String path)
    {
        List<Entry> list = load(c);
        for (Entry entry : list) {
            if (entry.path.equals(path)) {
                entry.lastPlayed = System.currentTimeMillis();
                save(c, list);
                return entry;
            }
        }
        return null;
    }
}
