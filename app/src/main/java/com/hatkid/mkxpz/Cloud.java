package com.hatkid.mkxpz;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Essentia's own database: a Google Apps Script web app (see backend/) that
 * keeps error reports, shared game translations and save backups.
 */
public final class Cloud
{
    /** Where reports go when the user hasn't set another database. Filled when deployed. */
    public static final String DEFAULT_URL =
        "https://script.google.com/macros/s/AKfycbyV8BV8HQV4LTorF04vlTts_t7FVCHnMYFSvY1KksedvIwv0w4Uh5wr3MA20StGXKjwgA/exec";

    private static final String KEY_URL = "cloud_url";
    private static final String KEY_BACKUP_KEY = "cloud_backup_key";

    private Cloud() { }

    private static SharedPreferences prefs(Context c)
    {
        return c.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE);
    }

    public static String url(Context c)
    {
        String u = prefs(c).getString(KEY_URL, "");
        return u.isEmpty() ? DEFAULT_URL : u;
    }

    public static void setUrl(Context c, String v) { prefs(c).edit().putString(KEY_URL, v.trim()).commit(); }
    public static String backupKey(Context c) { return prefs(c).getString(KEY_BACKUP_KEY, ""); }
    public static void setBackupKey(Context c, String v) { prefs(c).edit().putString(KEY_BACKUP_KEY, v.trim()).commit(); }

    public static boolean configured(Context c) { return url(c).startsWith("https://"); }
    public static boolean backupsReady(Context c) { return configured(c) && !backupKey(c).isEmpty(); }

    public static String essentiaVersion(Context c)
    {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static String device()
    {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    /** Sends a request and returns the answer (throws on network or server errors). */
    public static JSONObject post(Context c, JSONObject body, int timeoutMs) throws Exception
    {
        String target = url(c);
        if (!target.startsWith("https://")) throw new IllegalStateException("no database");
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection con = (HttpURLConnection) new URL(target).openConnection();
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs);
        con.setInstanceFollowRedirects(false);
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        con.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = con.getOutputStream()) {
            out.write(data);
        }
        int code = con.getResponseCode();
        // Apps Script answers through a redirect that must be fetched with GET
        for (int hops = 0; (code == 301 || code == 302 || code == 303 || code == 307) && hops < 5; hops++) {
            String next = con.getHeaderField("Location");
            con.disconnect();
            if (next == null) break;
            con = (HttpURLConnection) new URL(next).openConnection();
            con.setConnectTimeout(timeoutMs);
            con.setReadTimeout(timeoutMs);
            con.setInstanceFollowRedirects(false);
            code = con.getResponseCode();
        }
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        String text;
        try (InputStream in = con.getInputStream()) {
            text = new String(SaveTransaction.read(in, 32L * 1024 * 1024), "UTF-8");
        } finally {
            con.disconnect();
        }
        JSONObject res = new JSONObject(text);
        if (!res.optBoolean("ok", false)) throw new java.io.IOException(res.optString("error", "error"));
        return res;
    }
}
