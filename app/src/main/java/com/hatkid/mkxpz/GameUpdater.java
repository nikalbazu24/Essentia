package com.hatkid.mkxpz;

import android.os.StatFs;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Updates fan games that use PokéUpdater (Pokémon Añil and others): the game
 * folder has a "pu_config" file with its version and a link to a text with the
 * latest version and download links (MEGA, Dropbox, GitHub).
 *
 * Essentia does what poke_updater.exe does on Windows, but never deletes
 * anything: the new files are copied over the game, and the saves are backed
 * up first.
 */
public final class GameUpdater
{
    public interface Progress
    {
        /** step: 0 download, 1 extract, 2 copy; fraction 0..1 or -1 if unknown */
        void update(int step, float fraction, String detail);
        boolean cancelled();
    }

    public static final class Info
    {
        public String current = "";
        public String latest = "";
        /** host name -> link */
        public final Map<String, String> links = new LinkedHashMap<>();

        public boolean newer()
        {
            return compare(latest, current) > 0;
        }
    }

    private GameUpdater() { }

    // ------------------------------------------------------------ config

    public static File configFile(File gameDir)
    {
        String[] names = gameDir.list();
        if (names == null) return null;
        for (String n : names) if (n.equalsIgnoreCase("pu_config")) return new File(gameDir, n);
        return null;
    }

    public static boolean supported(File gameDir)
    {
        return configFile(gameDir) != null;
    }

    static Map<String, String> readConfig(File f) throws IOException
    {
        Map<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int i = line.indexOf('=');
                out.put(line.substring(0, i).trim(), line.substring(i + 1).trim().replace("\"", ""));
            }
        }
        return out;
    }

    /** 1.2.3 vs 1.10 by numbers, like PokéUpdater. */
    static int compare(String a, String b)
    {
        List<Integer> x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int p = i < x.size() ? x.get(i) : 0, q = i < y.size() ? y.get(i) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static List<Integer> numbers(String s)
    {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(s == null ? "" : s);
        while (m.find()) {
            try { out.add(Integer.parseInt(m.group())); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** The version the game says it is (PokéUpdater games), or null. No internet. */
    public static String localVersion(File gameDir)
    {
        try {
            File cfg = configFile(gameDir);
            if (cfg == null) return null;
            String v = readConfig(cfg).get("CURRENT_GAME_VERSION");
            return v == null || v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ check

    public static Info check(File gameDir) throws Exception
    {
        File cfg = configFile(gameDir);
        if (cfg == null) throw new IOException("no pu_config");
        Map<String, String> c = readConfig(cfg);
        Info info = new Info();
        info.current = c.containsKey("CURRENT_GAME_VERSION") ? c.get("CURRENT_GAME_VERSION") : "";
        String url = c.containsKey("PASTEBIN_URL") && !c.get("PASTEBIN_URL").isEmpty()
            ? c.get("PASTEBIN_URL") : c.get("VERSION_PASTEBIN");
        if (url == null || url.isEmpty()) throw new IOException("no version link");

        HttpURLConnection connection = open(url, null);
        String text;
        try (InputStream in = connection.getInputStream()) {
            text = new String(SaveTransaction.read(in, 1024 * 1024), StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
        for (String line : text.split("\n")) {
            line = line.trim();
            if (!line.contains("=")) continue;
            String value = line.substring(line.indexOf('=') + 1).trim();
            if (line.contains("GAME_VERSION") && info.latest.isEmpty()) {
                info.latest = value;
            } else if (line.contains("DOWNLOAD_URL")) {
                String host = host(value);
                if (host != null) info.links.put(host, value);
            }
        }
        return info;
    }

    static String host(String url)
    {
        try {
            URL parsed = new URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol()) || parsed.getUserInfo() != null) return null;
            String h = parsed.getHost().toLowerCase(Locale.ROOT);
            if (h.equals("mega.nz") || h.equals("mega.co.nz")) return "MEGA";
            if (h.equals("dropbox.com") || h.endsWith(".dropbox.com")) return "Dropbox";
            if (h.equals("github.com")) return "GitHub";
        } catch (Exception ignored) { }
        return null;
    }

    // ------------------------------------------------------------ update

    /** Downloads and installs the new version. Returns the saves backup folder (or null). */
    public static File install(File gameDir, String link, File backupRoot, Progress p) throws Exception
    {
        File work = new File(gameDir.getParentFile(), ".essentia-update-" + java.util.UUID.randomUUID());
        if (!work.mkdirs()) throw new IOException("can't create " + work);
        try {
            // 1. download
            File archive = download(link, work, p);
            if (p.cancelled()) return null;
            String lower = archive.getName().toLowerCase(Locale.ROOT);
            archive = GameArchives.zip(archive, work, p);

            // 2. back up the saves, then 3. install straight from the zip
            File backup = backupSaves(gameDir, backupRoot);
            List<String> ignored = new ArrayList<>();
            File cfg = configFile(gameDir);
            if (cfg != null) {
                String s = readConfig(cfg).get("IGNORED_FOLDERS");
                if (s != null) for (String f : s.split(",")) if (!f.trim().isEmpty()) ignored.add(f.trim().toLowerCase(Locale.ROOT));
            }
            if (ignored.isEmpty()) ignored.add("fotos");
            installFromZip(archive, gameDir, ignored, p);
            return backup;
        } finally {
            deleteTree(work);
        }
    }

    /**
     * Installs a game zip that is already on the device: over a game (update,
     * saves backed up first) or into a new folder (a game from the store).
     * Returns the saves backup folder (or null).
     */
    public static File installLocalZip(File zip, File gameDir, File backupRoot, Progress p) throws Exception
    {
        File work = new File(gameDir.getParentFile(), ".essentia-update-" + java.util.UUID.randomUUID());
        if (!work.mkdirs()) throw new IOException("No se puede preparar la instalación");
        try {
        zip = GameArchives.zip(zip, work, p);
        if (!gameDir.isDirectory() && !gameDir.mkdirs()) throw new IOException("can't create " + gameDir);
        File backup = backupSaves(gameDir, backupRoot);
        List<String> ignored = new ArrayList<>();
        File cfg = configFile(gameDir);
        if (cfg != null) {
            String s = readConfig(cfg).get("IGNORED_FOLDERS");
            if (s != null) for (String f : s.split(",")) if (!f.trim().isEmpty()) ignored.add(f.trim().toLowerCase(Locale.ROOT));
        }
        installFromZip(zip, gameDir, ignored, p);
        return backup;
        } finally { deleteTree(work); }
    }

    /** True when the zip holds an RPG Maker game. */
    public static boolean zipHasGame(File zip)
    {
        try { return GameArchives.gameRoot(zip) != null; }
        catch (Exception | LinkageError e) { return false; }
    }

    public static String zipGameName(File zip)
    {
        try {
            String root = GameArchives.gameRoot(zip);
            if (!root.isEmpty()) {
                String clean = root.substring(0, root.length() - 1);
                return clean.substring(clean.lastIndexOf('/') + 1);
            }
        } catch (Exception | LinkageError ignored) { }
        return zip.getName().replaceAll("(?i)\\.(zip|rar|7z)$", "");
    }

    /** Downloads a direct link (GitHub, Dropbox, MEGA or any .zip) into a folder. */
    public static File downloadTo(String link, File dir, Progress p) throws Exception
    {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't create " + dir);
        return download(link, dir, p);
    }

    public static final class UnsupportedFormat extends IOException
    {
        UnsupportedFormat(String name) { super(name); }
    }

    public static final class NoSpace extends IOException
    {
        public final long needed;
        NoSpace(long needed) { super("no space"); this.needed = needed; }
    }

    // ------------------------------------------------------------ download

    private static HttpURLConnection open(String url, String post) throws IOException
    {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Essentia");
            if (post != null) {
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(post.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code == 509) throw new IOException("bandwidth");
            if (code >= 400) throw new IOException("HTTP " + code);
            return c;
        }
        throw new IOException("too many redirects");
    }

    private static File download(String link, File dir, Progress p) throws Exception
    {
        String host = host(link);
        if ("MEGA".equals(host)) return downloadMega(link, dir, p);
        String url = link;
        String name;
        if ("Dropbox".equals(host)) {
            url = link.contains("dl=") ? link.replace("dl=0", "dl=1") : link + (link.contains("?") ? "&" : "?") + "dl=1";
            String path = new URL(url).getPath();
            name = path.substring(path.lastIndexOf('/') + 1);
        } else {
            String path = new URL(url).getPath();
            name = path.substring(path.lastIndexOf('/') + 1);
        }
        if (name.isEmpty()) name = "update.zip";
        name = Saves.safeName(new File(java.net.URLDecoder.decode(name, "UTF-8").replace('\\', '/')).getName());
        HttpURLConnection c = open(url, null);
        File f = new File(dir, name);
        stream(c.getInputStream(), length(c), f, null, p);
        return f;
    }

    private static long length(HttpURLConnection c)
    {
        try { return Long.parseLong(c.getHeaderField("content-length")); } catch (Exception e) { return -1; }
    }

    private static void stream(InputStream in, long total, File f, Cipher cipher, Progress p) throws Exception
    {
        try (InputStream is = new BufferedInputStream(in); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            long last = 0;
            while ((n = is.read(buf)) > 0) {
                if (p.cancelled()) return;
                if (cipher != null) {
                    byte[] dec = cipher.update(buf, 0, n);
                    if (dec != null) out.write(dec);
                } else {
                    out.write(buf, 0, n);
                }
                done += n;
                long now = System.currentTimeMillis();
                if (now - last > 300) {
                    last = now;
                    p.update(0, total > 0 ? (float) done / total : -1, mb(done) + (total > 0 ? " / " + mb(total) : ""));
                }
            }
            if (cipher != null) {
                byte[] tail = cipher.doFinal();
                if (tail != null) out.write(tail);
            }
        }
    }

    public static String mb(long bytes)
    {
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0);
    }

    // MEGA public links: https://mega.nz/file/<id>#<key> or https://mega.nz/#!<id>!<key>
    private static File downloadMega(String link, File dir, Progress p) throws Exception
    {
        String id, key;
        Matcher m2 = Pattern.compile("/file/([^#/?]+)#([^/?&]+)").matcher(link);
        Matcher m1 = Pattern.compile("#!([^!]+)!([^/?&]+)").matcher(link);
        if (m2.find()) { id = m2.group(1); key = m2.group(2); }
        else if (m1.find()) { id = m1.group(1); key = m1.group(2); }
        else throw new IOException("bad MEGA link");

        int[] a = toInts(base64Url(key));
        if (a.length < 8) throw new IOException("bad MEGA key");
        int[] k = { a[0] ^ a[4], a[1] ^ a[5], a[2] ^ a[6], a[3] ^ a[7] };
        byte[] keyBytes = toBytes(k);
        byte[] iv = toBytes(new int[] { a[4], a[5], 0, 0 });

        String body = "[{\"a\":\"g\",\"g\":1,\"p\":\"" + id + "\"}]";
        JSONObject data = null;
        for (int attempt = 0; attempt < 5 && data == null; attempt++) {
            String resp = new String(readAll(open("https://g.api.mega.co.nz/cs?id=" + (int) (Math.random() * 1e9), body)
                .getInputStream()), StandardCharsets.UTF_8).trim();
            if (resp.startsWith("[{")) data = new JSONArray(resp).getJSONObject(0);
            else Thread.sleep(2000L << attempt);
        }
        if (data == null || !data.has("g")) throw new IOException("MEGA file not available");

        String name = "update.zip";
        try {
            Cipher cbc = Cipher.getInstance("AES/CBC/NoPadding");
            cbc.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(new byte[16]));
            String attr = new String(cbc.doFinal(base64Url(data.getString("at"))), StandardCharsets.UTF_8);
            if (attr.startsWith("MEGA{")) {
                name = new JSONObject(attr.substring(4, attr.lastIndexOf('}') + 1)).optString("n", name);
            }
        } catch (Exception ignored) { }

        Cipher ctr = Cipher.getInstance("AES/CTR/NoPadding");
        ctr.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(iv));
        HttpURLConnection c = open(data.getString("g"), null);
        File f = new File(dir, new File(name).getName());
        stream(c.getInputStream(), data.optLong("s", -1), f, ctr, p);
        return f;
    }

    private static byte[] base64Url(String s)
    {
        String t = s.replace('-', '+').replace('_', '/').replace(",", "");
        while (t.length() % 4 != 0) t += "=";
        return Base64.decode(t, Base64.DEFAULT);
    }

    private static int[] toInts(byte[] b)
    {
        int n = (b.length + 3) / 4;
        ByteBuffer bb = ByteBuffer.allocate(n * 4);
        bb.put(b);
        bb.rewind();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = bb.getInt();
        return out;
    }

    private static byte[] toBytes(int[] a)
    {
        ByteBuffer bb = ByteBuffer.allocate(a.length * 4);
        for (int v : a) bb.putInt(v);
        return bb.array();
    }

    private static byte[] readAll(InputStream in) throws IOException
    {
        try (InputStream is = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------ files

    private static ZipFile openZip(File zip) throws IOException
    {
        if (android.os.Build.VERSION.SDK_INT < 24) return new ZipFile(zip);
        try {
            ZipFile z = new ZipFile(zip, StandardCharsets.UTF_8);
            // names that aren't valid UTF-8 throw while listing: use the old DOS encoding then
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) e.nextElement();
            return z;
        } catch (IllegalArgumentException | java.util.zip.ZipException e) {
            return new ZipFile(zip, Charset.forName("CP437"));
        }
    }

    private static final String[] GAME_MARKERS = {
        "game.ini", "game.exe", "mkxp.json", "game.rgssad", "game.rgss2a", "game.rgss3a"
    };

    /** The folder inside the zip that holds the game ("" or "Pokemon Anil/"). */
    private static String gameRoot(ZipFile z)
    {
        String best = null;
        for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
            String n = e.nextElement().getName().replace('\\', '/');
            String low = n.toLowerCase(Locale.ROOT);
            if (low.contains("poke_updater/")) continue;
            String base = low.substring(low.lastIndexOf('/') + 1);
            for (String m : GAME_MARKERS) {
                if (base.equals(m)) {
                    String prefix = n.substring(0, n.length() - base.length());
                    if (best == null || prefix.length() < best.length()) best = prefix;
                }
            }
        }
        return best;
    }

    /**
     * Writes the new files straight into the game folder, in one pass:
     * files that didn't change (same size and checksum) are skipped, saves and
     * ignored folders are never touched, folder listings are read only once.
     */
    private static void installFromZip(File zip, File gameDir, List<String> ignored, Progress p) throws Exception
    {
        ArchiveInstall.install(zip, gameDir, ignored, p);
    }

    private static long crc(File f, byte[] buf) throws IOException
    {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) c.update(buf, 0, n);
        }
        return c.getValue();
    }

    /**
     * The file in the game folder for a path from the zip, keeping the letter
     * case the game folder already uses (Graphics vs graphics). Folder listings
     * are read once and remembered.
     */
    private static File resolve(File gameDir, String rel, Map<String, Map<String, String>> listings, boolean create)
    {
        String[] parts = rel.split("/");
        File dir = gameDir;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
            Map<String, String> names = listings.get(dir.getPath());
            if (names == null) {
                names = new java.util.HashMap<>();
                String[] list = dir.list();
                if (list != null) for (String n : list) names.put(n.toLowerCase(Locale.ROOT), n);
                listings.put(dir.getPath(), names);
            }
            String actual = names.get(part.toLowerCase(Locale.ROOT));
            if (actual == null) {
                actual = part;
                if (create) names.put(part.toLowerCase(Locale.ROOT), part);
            }
            File next = new File(dir, actual);
            if (i < parts.length - 1 && create && !next.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                next.mkdirs();
            }
            dir = next;
        }
        return dir;
    }

    private static File backupSaves(File gameDir, File backupRoot) throws IOException
    {
        List<File> saves = new ArrayList<>(Saves.findIn(gameDir));
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(new Date());
        File dest = new File(backupRoot, Saves.safeName(gameDir.getName()) + "_" + stamp);
        if (saves.isEmpty()) return null;
        if (!dest.mkdirs() && !dest.isDirectory()) throw new IOException("can't back up saves");
        for (File s : saves) copyFile(s, new File(dest, s.getName()));
        return dest;
    }

    private static void copyFile(File a, File b) throws IOException
    {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void deleteTree(File f)
    {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
