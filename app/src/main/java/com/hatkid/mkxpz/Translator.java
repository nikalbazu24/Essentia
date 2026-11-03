package com.hatkid.mkxpz;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic translation to Spanish while a game runs (game process).
 * essentia_translate.rb writes the texts it needs to a request file; this
 * worker translates them (official Pokémon names and descriptions from a
 * glossary, the rest through Essentia's database, which shares translations
 * between players) and appends them to the game's translations file, which
 * the game reads. The file lives in Essentia/Traducciones and can be edited.
 */
public final class Translator
{
    private static final String TAG = "Essentia[Translate]";
    private static final Pattern CODES = Pattern.compile(
        "\\\\[A-Za-z]+\\[[^\\]]*\\]|\\\\[A-Za-z]+|\\\\[.|!^<>{}\\\\]|<[^>]{1,40}>|\\[[^\\]]{1,20}\\]|\\{\\d+[^}]{0,6}\\}");
    private static final Pattern TOKEN = Pattern.compile("\u27E6\\s*(\\d+)\\s*\u27E7");

    private final Context mContext;
    private final String mGame;
    private final File mTsv;
    private final File mReq;
    private volatile boolean mRunning = true;
    private Map<String, String> mGlossary;

    private Translator(Context c, String game, File tsv, File req)
    {
        mContext = c.getApplicationContext();
        mGame = game;
        mTsv = tsv;
        mReq = req;
    }

    public static File folder(String game)
    {
        File d = new File(Environment.getExternalStorageDirectory(), "Essentia/Traducciones/" + Saves.safeName(game));
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** Starts the worker; returns the two files for the game (translations, requests). */
    public static Translator start(Context c, String game)
    {
        File tsv = new File(folder(game), "es.tsv");
        File req = new File(c.getCacheDir(), "translate_requests.txt");
        //noinspection ResultOfMethodCallIgnored
        req.delete();
        Translator t = new Translator(c, game, tsv, req);
        new Thread(t::run, "EssentiaTranslate").start();
        return t;
    }

    public File translationsFile() { return mTsv; }
    public File requestFile() { return mReq; }
    public void stop() { mRunning = false; }

    // ------------------------------------------------------------ worker

    private void run()
    {
        seedFromDatabase();
        File work = new File(mReq.getPath() + ".work");
        while (mRunning) {
            try {
                if (mReq.isFile() && mReq.length() > 0 && mReq.renameTo(work)) {
                    List<String> texts = readRequests(work);
                    //noinspection ResultOfMethodCallIgnored
                    work.delete();
                    if (!texts.isEmpty()) translate(texts);
                }
                Thread.sleep(120);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                Log.w(TAG, "worker: " + e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    private List<String> readRequests(File f) throws Exception
    {
        Set<String> out = new LinkedHashSet<>();
        for (String line : new String(readAll(f), StandardCharsets.UTF_8).split("\n")) {
            if (!line.isEmpty()) out.add(unesc(line));
        }
        return new ArrayList<>(out);
    }

    private void translate(List<String> texts)
    {
        Map<String, String> done = new HashMap<>();
        List<String> remote = new ArrayList<>();
        for (String t : texts) {
            String g = glossary(t);
            if (g != null) done.put(t, g);
            else remote.add(t);
        }
        if (!done.isEmpty()) append(done);
        if (remote.isEmpty() || !Cloud.configured(mContext)) return;

        for (int i = 0; i < remote.size(); i += 40) {
            List<String> part = remote.subList(i, Math.min(remote.size(), i + 40));
            Map<String, List<String>> codes = new HashMap<>();
            JSONArray arr = new JSONArray();
            Map<String, String> protectedToOriginal = new HashMap<>();
            for (String t : part) {
                List<String> found = new ArrayList<>();
                String p = protect(t, found);
                codes.put(p, found);
                protectedToOriginal.put(p, t);
                arr.put(p);
            }
            try {
                JSONObject body = new JSONObject();
                body.put("action", "translate");
                body.put("game", mGame);
                body.put("lang", "es");
                body.put("texts", arr);
                JSONObject res = Cloud.post(mContext, body, 45000).getJSONObject("translations");
                Map<String, String> got = new HashMap<>();
                Iterator<String> keys = res.keys();
                while (keys.hasNext()) {
                    String p = keys.next();
                    String orig = protectedToOriginal.get(p);
                    if (orig == null) continue;
                    got.put(orig, restore(res.getString(p), codes.get(p)));
                }
                append(got);
            } catch (Exception e) {
                Log.w(TAG, "translate: " + e);
            }
        }
    }

    /** Translations already made by anyone for this game. */
    private void seedFromDatabase()
    {
        if (!Cloud.configured(mContext) || (mTsv.isFile() && mTsv.length() > 0)) return;
        try {
            JSONObject body = new JSONObject();
            body.put("action", "tr_dump");
            body.put("game", mGame);
            body.put("lang", "es");
            JSONObject res = Cloud.post(mContext, body, 30000).getJSONObject("translations");
            // the database keeps texts with their codes as ⟦n⟧ placeholders: those
            // are translated again when needed; plain texts can be used right away
            Map<String, String> plain = new HashMap<>();
            Iterator<String> keys = res.keys();
            while (keys.hasNext()) {
                String p = keys.next();
                if (!TOKEN.matcher(p).find()) plain.put(p, res.getString(p));
            }
            append(plain);
        } catch (Exception e) {
            Log.w(TAG, "seed: " + e);
        }
    }

    private synchronized void append(Map<String, String> m)
    {
        if (m.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            sb.append(esc(e.getKey())).append('\t').append(esc(e.getValue())).append('\n');
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(mTsv, true), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "write: " + e);
        }
    }

    // ------------------------------------------------------------ codes

    /** Message codes (\PN, \v[1], <ac>...) become ⟦n⟧ so the translator leaves them alone. */
    static String protect(String t, List<String> found)
    {
        String s = t.replace("\n", " ");
        Matcher m = CODES.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            found.add(m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement("\u27E6" + (found.size() - 1) + "\u27E7"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String restore(String t, List<String> found)
    {
        if (found == null || found.isEmpty()) return t;
        Matcher m = TOKEN.matcher(t);
        StringBuffer sb = new StringBuffer();
        boolean[] used = new boolean[found.size()];
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            String code = i >= 0 && i < found.size() ? found.get(i) : "";
            if (i >= 0 && i < used.length) used[i] = true;
            m.appendReplacement(sb, Matcher.quoteReplacement(code));
        }
        m.appendTail(sb);
        // codes the translation lost go at the start (colors, names...)
        StringBuilder lost = new StringBuilder();
        for (int i = 0; i < used.length; i++) if (!used[i]) lost.append(found.get(i));
        return lost + sb.toString();
    }

    // ------------------------------------------------------------ glossary

    private String glossary(String t)
    {
        if (t.length() > 400) return null;
        if (mGlossary == null) loadGlossary();
        return mGlossary.get(normalize(t));
    }

    private void loadGlossary()
    {
        mGlossary = new HashMap<>();
        try (InputStream in = mContext.getAssets().open("essentia_translate/glossary_es.json")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) buf.write(b, 0, r);
            JSONObject o = new JSONObject(buf.toString("UTF-8"));
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                mGlossary.put(k, o.getString(k));
            }
        } catch (Exception e) {
            Log.w(TAG, "glossary: " + e);
        }
    }

    static String normalize(String s)
    {
        String n = Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    // ------------------------------------------------------------ helpers

    static String esc(String s)
    {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }

    static String unesc(String s)
    {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static byte[] readAll(File f) throws Exception
    {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) buf.write(b, 0, r);
            return buf.toByteArray();
        }
    }
}
