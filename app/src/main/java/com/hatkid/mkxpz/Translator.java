package com.hatkid.mkxpz;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads dialogue batches and fills the game's language-specific cache off the game thread. */
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
    private final String mLanguage;
    private final ScheduledExecutorService mWorker = Executors.newSingleThreadScheduledExecutor(
        task -> new Thread(task, "EssentiaTranslate"));
    private FileObserver mObserver;
    private boolean mDrainScheduled;
    private boolean mNotified;
    private volatile boolean mRunning = true;
    private Map<String, String> mGlossary;

    private Translator(Context c, String game, File tsv, File req, String language)
    {
        mContext = c;
        mGame = game;
        mTsv = tsv;
        mReq = req;
        mLanguage = language;
    }

    public static File folder(String game)
    {
        File d = new File(Environment.getExternalStorageDirectory(), "Essentia/Traducciones/" + Saves.safeName(game));
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static File translationFile(String game, String language) {
        return new File(folder(game), TranslationLanguage.normalize(language) + ".tsv");
    }

    public static Translator start(Context context, String game, String language) throws java.io.IOException {
        File requests = new File(context.getCacheDir(), "translation-requests");
        if (!requests.isDirectory() && !requests.mkdirs()) throw new java.io.IOException("Cannot create dialogue mailbox");
        File[] stale = requests.listFiles((dir, name) -> name.endsWith(".req") || name.endsWith(".tmp"));
        if (stale != null) for (File file : stale) file.delete();
        Translator translator = new Translator(context, game, translationFile(game, language), requests, language);
        translator.mObserver = new FileObserver(requests.getAbsolutePath(), FileObserver.MOVED_TO) {
            @Override public void onEvent(int event, String name) {
                if (name != null && name.endsWith(".req")) translator.scheduleDrain();
            }
        };
        translator.mObserver.startWatching();
        return translator;
    }

    public File translationsFile() { return mTsv; }
    public File requestFile() { return mReq; }

    public synchronized void stop() {
        mRunning = false;
        if (mObserver != null) mObserver.stopWatching();
        mWorker.shutdownNow();
    }

    private synchronized void scheduleDrain() {
        if (!mRunning || mDrainScheduled) return;
        mDrainScheduled = true;
        mWorker.schedule(this::drain, 25, TimeUnit.MILLISECONDS);
    }

    private void drain() {
        synchronized (this) { mDrainScheduled = false; }
        File[] requests = mReq.listFiles((dir, name) -> name.endsWith(".req"));
        if (requests == null) return;
        java.util.Arrays.sort(requests);
        Set<String> texts = new LinkedHashSet<>();
        for (File request : requests) {
            if (!mRunning) return;
            try { texts.addAll(readRequests(request)); }
            catch (Exception ignored) { Log.w(TAG, "Could not read a dialogue batch"); }
            finally { request.delete(); }
        }
        if (!texts.isEmpty()) translate(new ArrayList<>(texts));
    }

    private void notifyFailure(int message) {
        if (mNotified || !mRunning) return;
        mNotified = true;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mRunning) android.widget.Toast.makeText(mContext, message, android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private List<String> readRequests(File f) throws Exception
    {
        Set<String> out = new LinkedHashSet<>();
        try (InputStream in = new FileInputStream(f)) {
            for (String line : new String(SaveTransaction.read(in, 256 * 1024), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isEmpty()) out.add(unesc(line));
            }
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
        if (remote.isEmpty() || !mRunning) return;
        if (!Cloud.configured(mContext) && !AiTranslation.enabled(mContext)) {
            notifyFailure(R.string.translation_unavailable);
            return;
        }

        Map<String, List<String>> batch = new java.util.LinkedHashMap<>();
        Map<String, List<String>> codes = new HashMap<>();
        int length = 0;
        for (String original : remote) {
            if (!mRunning) return;
            List<String> placeholders = new ArrayList<>();
            String protectedText = protect(original, placeholders);
            if (protectedText.length() > 3500) continue;
            if (!batch.containsKey(protectedText)) {
                if (batch.size() >= 24 || length + protectedText.length() > 3500) {
                    requestBatch(batch, codes);
                    batch.clear();
                    codes.clear();
                    length = 0;
                }
                length += protectedText.length();
            }
            List<String> originals = batch.get(protectedText);
            if (originals == null) {
                originals = new ArrayList<>();
                batch.put(protectedText, originals);
            }
            originals.add(original);
            codes.put(original, placeholders);
        }
        if (!batch.isEmpty() && mRunning) requestBatch(batch, codes);
    }

    private long retryAfter;

    private void requestBatch(Map<String, List<String>> batch, Map<String, List<String>> codes) {
        if (android.os.SystemClock.elapsedRealtime() < retryAfter) return;
        JSONArray texts = new JSONArray(batch.keySet());
        try {
            JSONObject response;
            if (AiTranslation.enabled(mContext)) {
                response = AiTranslation.translate(mContext, texts, mLanguage);
            } else {
                JSONObject body = new JSONObject().put("action", "translate").put("game", mGame)
                    .put("lang", mLanguage).put("texts", texts);
                response = Cloud.post(mContext, body, 6500).getJSONObject("translations");
            }
            Map<String, String> translated = new HashMap<>();
            for (String key : batch.keySet()) {
                Object value = response.opt(key);
                if (!(value instanceof String) || ((String) value).length() > 12000) continue;
                for (String original : batch.get(key)) {
                    String restored = restore((String) value, codes.get(original));
                    if (restored != null && !restored.trim().isEmpty()) translated.put(original, restored);
                }
            }
            append(translated);
            if (translated.isEmpty()) {
                retryAfter = android.os.SystemClock.elapsedRealtime() + 10000;
                notifyFailure(R.string.translation_no_result);
            }
        } catch (Exception ignored) {
            retryAfter = android.os.SystemClock.elapsedRealtime() + 10000;
            Log.w(TAG, "Translation provider unavailable");
            notifyFailure(R.string.translation_unavailable);
        }
    }

    private synchronized void append(Map<String, String> m)
    {
        if (m.isEmpty() || !mRunning) return;
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
        Matcher m = CODES.matcher(t);
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
        Matcher m = TOKEN.matcher(t);
        StringBuffer sb = new StringBuffer();
        boolean[] used = new boolean[found.size()];
        while (m.find()) {
            if (m.group(1).length() > 5) return null;
            int i = Integer.parseInt(m.group(1));
            if (i >= found.size() || used[i]) return null;
            used[i] = true;
            m.appendReplacement(sb, Matcher.quoteReplacement(found.get(i)));
        }
        m.appendTail(sb);
        for (boolean present : used) if (!present) return null;
        return sb.toString();
    }

    // ------------------------------------------------------------ glossary

    private String glossary(String t)
    {
        if (!"es".equals(mLanguage) || t.length() > 400 || CODES.matcher(t).find()) return null;
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

}
