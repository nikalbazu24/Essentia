package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * What kind of game this is (RPG Maker XP/VX/VX Ace, Pokémon Essentials and
 * its version) and the settings that suit it, from its own scripts and from
 * Essentia's list of known games (assets/essentia_profiles.json).
 */
public final class GameProfile
{
    public String maker = "RPG Maker";      // XP, VX, VX Ace
    public String essentials = "";          // "", or its version ("16.2", "21.1"...)
    public boolean essentials_ = false;     // made with Pokémon Essentials
    public boolean oldResizer = false;      // old Essentials window resizer (up to v16-17)
    /** Recommended settings ("" = no recommendation). */
    public String screen = "";
    public String engine = "";
    public String note = "";

    private GameProfile() { }

    public String label()
    {
        if (essentials_) return essentials.isEmpty() ? "Pokémon Essentials" : "Essentials " + essentials;
        return maker;
    }

    /** Old Pokémon Essentials (before v19): the riskiest group. */
    public boolean oldEssentials()
    {
        if (!essentials_) return false;
        if (oldResizer) return true;
        try {
            return !essentials.isEmpty() && Float.parseFloat(essentials.replaceAll("[^0-9.].*$", "")) < 19f;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ detection

    /** The profile of a game folder (remembered until its scripts change). */
    public static GameProfile of(Context c, File dir)
    {
        File scripts = scriptsFile(dir);
        long stamp = scripts != null ? scripts.lastModified() + scripts.length() : 0;
        SharedPreferences p = c.getSharedPreferences("essentia_profiles", Context.MODE_PRIVATE);
        String key = dir.getAbsolutePath();
        GameProfile g = new GameProfile();
        if (p.getLong(key + ":stamp", -1) == stamp && p.contains(key + ":maker")) {
            g.maker = p.getString(key + ":maker", g.maker);
            g.essentials = p.getString(key + ":ess", "");
            g.essentials_ = p.getBoolean(key + ":isess", false);
            g.oldResizer = p.getBoolean(key + ":old", false);
        } else {
            detect(dir, scripts, g);
            p.edit().putLong(key + ":stamp", stamp).putString(key + ":maker", g.maker)
                .putString(key + ":ess", g.essentials).putBoolean(key + ":isess", g.essentials_)
                .putBoolean(key + ":old", g.oldResizer).apply();
        }
        applyKnown(c, dir, g);
        recommend(g);
        return g;
    }

    private static File child(File dir, String name)
    {
        if (dir == null) return null;
        File f = new File(dir, name);
        if (f.exists()) return f;
        String[] list = dir.list();
        if (list == null) return null;
        for (String n : list) if (n.equalsIgnoreCase(name)) return new File(dir, n);
        return null;
    }

    private static File scriptsFile(File dir)
    {
        File data = child(dir, "Data");
        for (String n : new String[] { "Scripts.rxdata", "Scripts.rvdata", "Scripts.rvdata2" }) {
            File f = child(data, n);
            if (f != null && f.isFile()) return f;
        }
        return null;
    }

    private static void detect(File dir, File scripts, GameProfile g)
    {
        String name = scripts != null ? scripts.getName().toLowerCase(java.util.Locale.ROOT) : "";
        if (name.endsWith("rvdata2") || child(dir, "Game.rgss3a") != null) g.maker = "RPG Maker VX Ace";
        else if (name.endsWith("rvdata") || child(dir, "Game.rgss2a") != null) g.maker = "RPG Maker VX";
        else g.maker = "RPG Maker XP";
        g.essentials_ = child(dir, "PBS") != null || child(child(dir, "Graphics"), "Battlers") != null
            || child(child(dir, "Graphics"), "Pokemon") != null;
        if (scripts == null) return;
        try {
            List<String[]> list = readScripts(scripts, 400);
            Pattern ver = Pattern.compile("ESSENTIALS_?VERSION\\s*=\\s*\"([^\"]+)\"");
            // v19+: module Essentials ... VERSION = "21.1"
            Pattern ver2 = Pattern.compile("module\\s+Essentials\\b[\\s\\S]{0,400}?\\bVERSION\\s*=\\s*\"([^\"]+)\"");
            for (String[] s : list) {
                String section = s[0];
                if (section.equalsIgnoreCase("Sprite_Resizer")) { g.oldResizer = true; g.essentials_ = true; }
                if (section.startsWith("PokeBattle_") || section.startsWith("PScreen_") || section.equals("Compiler")) {
                    g.essentials_ = true;
                }
                if (g.essentials.isEmpty() && s[1] != null) {
                    Matcher m = ver.matcher(s[1]);
                    if (m.find()) { g.essentials = m.group(1); g.essentials_ = true; }
                    else {
                        Matcher m2 = ver2.matcher(s[1]);
                        if (m2.find()) { g.essentials = m2.group(1); g.essentials_ = true; }
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    /** Known games: settings by title (assets/essentia_profiles.json). */
    private static void applyKnown(Context c, File dir, GameProfile g)
    {
        try (InputStream in = c.getAssets().open("essentia_profiles.json")) {
            JSONArray arr = new JSONArray(new String(readAll(in), "UTF-8"));
            String title = Games.readTitle(dir).toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!Pattern.compile(o.getString("title"), Pattern.CASE_INSENSITIVE).matcher(title).find()) continue;
                g.screen = o.optString("screen", g.screen);
                g.engine = o.optString("engine", g.engine);
                g.note = o.optString("note", g.note);
                return;
            }
        } catch (Exception ignored) { }
    }

    private static void recommend(GameProfile g)
    {
        // old Essentials: menus and battles are kept in their own shape
        if (g.screen.isEmpty() && g.oldEssentials()) g.screen = EssentiaSettings.SCREEN_WIDE_SAFE;
    }

    // ------------------------------------------------------------ Scripts.rxdata (Ruby Marshal)

    /** [section name, code] of the game's scripts (code only for the first few). */
    static List<String[]> readScripts(File f, int maxCode) throws Exception
    {
        byte[] data;
        try (InputStream in = new java.io.FileInputStream(f)) { data = readAll(in); }
        Marshal m = new Marshal(data);
        return m.scripts(maxCode);
    }

    private static final class Marshal
    {
        private final byte[] d;
        private int pos = 2;
        private final List<String> symbols = new ArrayList<>();

        Marshal(byte[] d) { this.d = d; }

        int b() { return d[pos++] & 0xFF; }

        int num()
        {
            int c = (byte) d[pos++];
            if (c == 0) return 0;
            if (c >= 5) return c - 5;
            if (c <= -5) return c + 5;
            if (c > 0) {
                int x = 0;
                for (int i = 0; i < c; i++) x |= b() << (8 * i);
                return x;
            }
            int x = -1;
            for (int i = 0; i < -c; i++) { x &= ~(0xFF << (8 * i)); x |= b() << (8 * i); }
            return x;
        }

        byte[] bytes() { int n = num(); byte[] o = new byte[n]; System.arraycopy(d, pos, o, 0, n); pos += n; return o; }

        /** Reads one value; strings come back as byte[], others as null (skipped). */
        Object value()
        {
            int t = b();
            switch (t) {
                case '0': case 'T': case 'F': return null;
                case 'i': num(); return null;
                case '"': return bytes();
                case ':': { String s = new String(bytes(), java.nio.charset.StandardCharsets.UTF_8); symbols.add(s); return null; }
                case ';': num(); return null;
                case 'I': {
                    Object v = value();
                    int n = num();
                    for (int i = 0; i < n; i++) { value(); value(); }
                    return v;
                }
                case '[': {
                    int n = num();
                    List<Object> l = new ArrayList<>();
                    for (int i = 0; i < n; i++) l.add(value());
                    return l;
                }
                default:
                    throw new IllegalStateException("marshal type " + t);
            }
        }

        @SuppressWarnings("unchecked")
        List<String[]> scripts(int maxCode) throws Exception
        {
            Object root = value();
            List<String[]> out = new ArrayList<>();
            if (!(root instanceof List)) return out;
            int i = 0;
            for (Object o : (List<Object>) root) {
                if (!(o instanceof List)) continue;
                List<Object> e = (List<Object>) o;
                if (e.size() < 3) continue;
                String name = e.get(1) instanceof byte[] ? new String((byte[]) e.get(1), "UTF-8") : "";
                String code = null;
                boolean wanted = i++ < 12 || name.toLowerCase(java.util.Locale.ROOT).matches(".*(settings|essentials|version).*");
                if (wanted && i <= maxCode && e.get(2) instanceof byte[]) code = inflate((byte[]) e.get(2));
                out.add(new String[] { name, code });
            }
            return out;
        }
    }

    static String inflate(byte[] z)
    {
        try {
            Inflater inf = new Inflater();
            inf.setInput(z);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
            }
            inf.end();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int r;
        while ((r = in.read(b)) > 0) buf.write(b, 0, r);
        return buf.toByteArray();
    }
}
