package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fan game catalog (the store) and the updates of every game in the
 * library.
 *
 * The catalog is a JSON file kept in Essentia's repository (catalog/catalog.json),
 * so games can be added or updated without a new version of the app; a copy
 * goes inside the app for the first start without internet. Essentia never
 * hosts games: each entry points to its creator's official page, and may give
 * a direct link only when the creator publishes one.
 */
public final class Catalog
{
    public static final String URL =
        "https://raw.githubusercontent.com/Nikalbazu24/essentia/main/catalog/catalog.json";
    private static final long REFRESH_MS = 6L * 60 * 60 * 1000;
    /** All the library is checked for updates at most this often (when the library opens). */
    public static final long CHECK_ALL_MS = 12L * 60 * 60 * 1000;

    public static final String PERFECT = "perfecto";
    public static final String PLAYABLE = "jugable";
    public static final String ISSUES = "con_fallas";
    public static final String BROKEN = "no_abre";
    public static final String UNTESTED = "sin_probar";

    /** One game of the catalog. */
    public static final class Game
    {
        public String id = "";
        public String title = "";
        public final List<String> aliases = new ArrayList<>();
        public String author = "";
        public String version = "";
        public final List<String> lang = new ArrayList<>();
        public String essentials = "";
        public String maker = "";
        public String status = UNTESTED;
        public String description = "";
        public String note = "";
        /** The creator's official page (web, forum thread, itch.io, GitHub...). */
        public String page = "";
        /** A direct .zip link, only when the creator publishes one. */
        public String download = "";
        public String cover = "";
        public final List<String> screens = new ArrayList<>();
        public String size = "";
        /** Updated with PokéUpdater from inside the game folder. */
        public boolean pokeUpdater = false;
        /** Accent color for the generated cover (#RRGGBB), when there's no picture. */
        public String color = "";

        public boolean spanish() { return lang.contains("es"); }
        public boolean tested() { return PERFECT.equals(status) || PLAYABLE.equals(status); }
    }

    private static List<Game> sGames;

    private Catalog() { }

    // ------------------------------------------------------------ catalog

    private static File cacheFile(Context c) { return new File(c.getFilesDir(), "catalog.json"); }

    /** The catalog already on the device (no internet): the last download, or the copy in the app. */
    public static synchronized List<Game> cached(Context c)
    {
        if (sGames != null) return sGames;
        List<Game> out = null;
        File f = cacheFile(c);
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) { out = parse(readAll(in)); } catch (Exception ignored) { }
        }
        if (out == null || out.isEmpty()) {
            try (InputStream in = c.getAssets().open("essentia_catalog.json")) { out = parse(readAll(in)); }
            catch (Exception e) { out = new ArrayList<>(); }
        }
        sGames = out;
        return out;
    }

    /** Downloads a fresh catalog when it's old (or always with force). Off the main thread. */
    public static List<Game> refresh(Context c, boolean force)
    {
        File f = cacheFile(c);
        if (!force && f.isFile() && System.currentTimeMillis() - f.lastModified() < REFRESH_MS) return cached(c);
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(URL).openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(20000);
            con.setRequestProperty("User-Agent", "Essentia");
            con.setUseCaches(false);
            if (con.getResponseCode() == 200) {
                String text;
                try (InputStream in = con.getInputStream()) { text = readAll(in); }
                List<Game> games = parse(text);
                if (!games.isEmpty()) {
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
                    synchronized (Catalog.class) { sGames = games; }
                    return games;
                }
            }
            con.disconnect();
        } catch (Exception ignored) { }
        return cached(c);
    }

    static List<Game> parse(String json) throws Exception
    {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("games");
        List<Game> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Game g = new Game();
            g.id = o.optString("id", "");
            g.title = o.optString("title", "");
            if (g.title.isEmpty()) continue;
            list(o.optJSONArray("aliases"), g.aliases);
            g.author = o.optString("author", "");
            g.version = o.optString("version", "");
            list(o.optJSONArray("lang"), g.lang);
            g.essentials = o.optString("essentials", "");
            g.maker = o.optString("maker", "");
            g.status = o.optString("status", UNTESTED);
            g.description = o.optString("description", "");
            g.note = o.optString("note", "");
            g.page = o.optString("page", "");
            g.download = o.optString("download", "");
            g.cover = o.optString("cover", "");
            list(o.optJSONArray("screens"), g.screens);
            g.size = o.optString("size", "");
            g.pokeUpdater = o.optBoolean("pokeupdater", false);
            g.color = o.optString("color", "");
            out.add(g);
        }
        return out;
    }

    private static void list(JSONArray a, List<String> into)
    {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) into.add(s);
        }
    }

    /** "Pokémon Añil 4.0" → "anil": for matching names written in different ways. */
    public static String norm(String s)
    {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        t = t.replaceAll("\\bpokemon\\b", " ").replaceAll("\\b(v|ver|version)?\\s*\\d+(\\.\\d+)*[a-z]?\\b", " ");
        t = t.replaceAll("[^a-z0-9]+", "");
        return t;
    }

    /** The catalog entry for a game in the library, or null. */
    public static Game match(List<Game> games, String title, File dir)
    {
        String a = norm(title);
        String b = dir != null ? norm(dir.getName()) : "";
        for (Game g : games) {
            if (same(g.title, a, b)) return g;
            for (String al : g.aliases) if (same(al, a, b)) return g;
        }
        return null;
    }

    private static boolean same(String name, String a, String b)
    {
        String n = norm(name);
        return !n.isEmpty() && (n.equals(a) || n.equals(b));
    }

    /** The library entry of a catalog game, or null when it isn't installed. */
    public static Library.Entry installed(Context c, Game g)
    {
        List<Game> one = new ArrayList<>();
        one.add(g);
        for (Library.Entry e : Library.load(c)) {
            if (match(one, e.displayTitle(), new File(e.path)) != null) return e;
        }
        return null;
    }

    /** Where games from the store go: the folder that already has most of the user's games. */
    public static File gamesFolder(Context c)
    {
        Map<String, Integer> count = new HashMap<>();
        String best = null;
        for (Library.Entry e : Library.load(c)) {
            File parent = new File(e.path).getParentFile();
            if (parent == null || !parent.canWrite()) continue;
            String p = parent.getAbsolutePath();
            int n = count.containsKey(p) ? count.get(p) + 1 : 1;
            count.put(p, n);
            if (best == null || n > count.get(best)) best = p;
        }
        File dir = best != null ? new File(best) : new File(Environment.getExternalStorageDirectory(), "Essentia/Juegos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        EssentiaSettings.addGameRoot(c, dir.getAbsolutePath());
        return dir;
    }

    // ------------------------------------------------------------ versions

    private static SharedPreferences prefs(Context c)
    {
        return c.getSharedPreferences("essentia_store", Context.MODE_PRIVATE);
    }

    /** The version a game folder has: PokéUpdater's file, the one Essentia installed, or the game's own. */
    public static String localVersion(Context c, File dir)
    {
        String v = GameUpdater.localVersion(dir);
        if (v != null && !v.isEmpty()) return v;
        v = prefs(c).getString("v:" + dir.getAbsolutePath(), "");
        if (!v.isEmpty()) return v;
        try {
            v = GameProfile.of(c, dir).gameVersion;
        } catch (Exception e) {
            v = "";
        }
        return v == null ? "" : v;
    }

    public static void rememberVersion(Context c, String path, String version)
    {
        if (version == null || version.isEmpty()) return;
        prefs(c).edit().putString("v:" + path, version).apply();
    }

    // ------------------------------------------------------------ updates

    /** A newer version of a game in the library. */
    public static final class Update
    {
        public String path = "";
        public String title = "";
        public String current = "";
        public String latest = "";
        /** What Essentia can download and install by itself ("" if only the page). */
        public String link = "";
        /** The creator's page, to download it by hand. */
        public String page = "";
        public boolean newer() { return !latest.isEmpty() && GameUpdater.compare(latest, current) > 0; }
    }

    /** Checks one game (may use the internet for PokéUpdater games). Null: nothing known. */
    public static Update check(Context c, Library.Entry e, List<Game> games)
    {
        File dir = new File(e.path);
        Update u = new Update();
        u.path = e.path;
        u.title = e.displayTitle();
        u.current = localVersion(c, dir);
        Game g = match(games, e.displayTitle(), dir);
        if (g != null) u.page = g.page;
        if (GameUpdater.supported(dir)) {
            try {
                GameUpdater.Info info = GameUpdater.check(dir);
                u.current = info.current;
                u.latest = info.latest;
                if (!info.links.isEmpty()) u.link = info.links.values().iterator().next();
                return u;
            } catch (Exception ignored) { }
        }
        if (g == null || g.version.isEmpty()) return null;
        u.latest = g.version;
        u.link = g.download;
        return u;
    }

    /**
     * Checks every game of the library and keeps the results (for the badges).
     * Returns the games with a newer version. Off the main thread.
     */
    public static List<Update> checkAll(Context c)
    {
        List<Game> games = refresh(c, false);
        List<Update> found = new ArrayList<>();
        for (Library.Entry e : Library.load(c)) {
            Update u = check(c, e, games);
            if (u != null && u.newer()) {
                save(c, u);
                found.add(u);
            } else {
                clearUpdate(c, e.path);
            }
        }
        prefs(c).edit().putLong("checked_all", System.currentTimeMillis()).apply();
        return found;
    }

    /** Keeps the results of a check made elsewhere (the store's "My games"). */
    public static void checkAllStore(Context c, List<Update> all)
    {
        for (Update u : all) {
            if (u.newer()) save(c, u); else clearUpdate(c, u.path);
        }
        prefs(c).edit().putLong("checked_all", System.currentTimeMillis()).apply();
    }

    public static boolean checkAllDue(Context c)
    {
        return System.currentTimeMillis() - prefs(c).getLong("checked_all", 0) > CHECK_ALL_MS;
    }

    private static void save(Context c, Update u)
    {
        prefs(c).edit()
            .putString("u:" + u.path + ":latest", u.latest)
            .putString("u:" + u.path + ":link", u.link)
            .putString("u:" + u.path + ":page", u.page)
            .apply();
    }

    public static void clearUpdate(Context c, String path)
    {
        prefs(c).edit().remove("u:" + path + ":latest").remove("u:" + path + ":link").remove("u:" + path + ":page").apply();
    }

    /** The newer version found for a game at the last check, or null. No internet. */
    public static Update pending(Context c, Library.Entry e)
    {
        SharedPreferences p = prefs(c);
        String latest = p.getString("u:" + e.path + ":latest", "");
        if (latest.isEmpty()) return null;
        Update u = new Update();
        u.path = e.path;
        u.title = e.displayTitle();
        u.latest = latest;
        u.link = p.getString("u:" + e.path + ":link", "");
        u.page = p.getString("u:" + e.path + ":page", "");
        u.current = localVersion(c, new File(e.path));
        if (!u.newer()) {
            clearUpdate(c, e.path); // updated in the meantime
            return null;
        }
        return u;
    }

    public static int pendingCount(Context c, List<Library.Entry> games)
    {
        int n = 0;
        for (Library.Entry e : games) if (pending(c, e) != null) n++;
        return n;
    }

    // ------------------------------------------------------------ downloads folder

    /** Game zips in the Download folder (newest first), for "install a download". */
    public static List<File> downloadedZips()
    {
        List<File> out = new ArrayList<>();
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        collectZips(dl, out, 0);
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void collectZips(File dir, List<File> out, int depth)
    {
        File[] list = dir != null ? dir.listFiles() : null;
        if (list == null || depth > 2) return;
        for (File f : list) {
            if (f.isDirectory()) collectZips(f, out, depth + 1);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip") && f.length() > 1024 * 1024) out.add(f);
        }
    }

    /** Zips first whose name looks like the game's. */
    public static List<File> zipsFor(String title)
    {
        List<File> all = downloadedZips();
        String n = norm(title);
        List<File> mine = new ArrayList<>(), rest = new ArrayList<>();
        for (File f : all) {
            String z = norm(f.getName().replaceAll("(?i)\\.zip$", ""));
            if (!n.isEmpty() && (z.contains(n) || (!z.isEmpty() && n.contains(z)))) mine.add(f); else rest.add(f);
        }
        mine.addAll(rest);
        return mine;
    }

    // ------------------------------------------------------------ misc

    private static String readAll(InputStream in) throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }
}
