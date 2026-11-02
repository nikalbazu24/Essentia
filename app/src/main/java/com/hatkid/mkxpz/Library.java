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

        Entry(String path, String title, long lastPlayed)
        {
            this.path = path;
            this.title = title;
            this.lastPlayed = lastPlayed;
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
                out.add(new Entry(o.getString("path"), o.optString("title", ""), o.optLong("lastPlayed", 0)));
            }
        } catch (Exception ignored) { }

        Collections.sort(out, (a, b) -> {
            if (a.lastPlayed != b.lastPlayed) return Long.compare(b.lastPlayed, a.lastPlayed);
            return a.title.compareToIgnoreCase(b.title);
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
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString(KEY, arr.toString()).commit();
    }

    /** Adds a game (if new). Returns true when it was added. */
    public static synchronized boolean add(Context c, File dir)
    {
        String path = dir.getAbsolutePath();
        List<Entry> list = load(c);
        for (Entry e : list) if (e.path.equals(path)) return false;
        list.add(new Entry(path, Games.readTitle(dir), 0));
        save(c, list);
        return true;
    }

    public static synchronized void remove(Context c, String path)
    {
        List<Entry> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).path.equals(path)) list.remove(i);
        }
        save(c, list);
    }

    public static synchronized void markPlayed(Context c, String path)
    {
        List<Entry> list = load(c);
        boolean found = false;
        for (Entry e : list) {
            if (e.path.equals(path)) {
                e.lastPlayed = System.currentTimeMillis();
                found = true;
            }
        }
        if (found) save(c, list);
    }
}
