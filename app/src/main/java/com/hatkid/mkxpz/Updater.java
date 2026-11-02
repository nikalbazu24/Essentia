package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Essentia's in-app updater.
 *
 * Every build publishes essentia.apk and version.json to the "dev" release on GitHub.
 * When Essentia is opened from its icon, it reads version.json; if the versionCode is
 * higher than the installed one, it offers to download the APK and opens the
 * Android installer.
 */
public final class Updater
{
    private static final String TAG = "Essentia[Updater]";
    private static final String BASE = "https://github.com/nikalbazu24/Essentia/releases/download/dev/";
    private static final String VERSION_URL = BASE + "version.json";
    private static final int TIMEOUT_MS = 4000;

    public interface Done { void run(); }

    private Updater() { }

    /**
     * Checks for an update; calls {@code then} on the UI thread when the user can continue.
     * With {@code manual}, also tells the user when there is nothing new (or the check failed).
     */
    public static void check(Activity activity, Done then, boolean manual)
    {
        Handler ui = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            JSONObject info = null;
            try {
                info = new JSONObject(download(VERSION_URL));
            } catch (Exception e) {
                Log.i(TAG, "Update check skipped: " + e);
            }

            final JSONObject result = info;
            ui.post(() -> {
                if (activity.isFinishing()) return;

                if (result == null) {
                    if (manual) Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show();
                    then.run();
                    return;
                }
                if (!result.optBoolean("signed", false)
                    || result.optInt("versionCode", 0) <= BuildConfig.VERSION_CODE) {
                    if (manual) Toast.makeText(activity, R.string.update_latest, Toast.LENGTH_LONG).show();
                    then.run();
                    return;
                }

                String apkUrl = BASE + result.optString("apk", "essentia.apk");
                String name = result.optString("versionName", "?");
                askToUpdate(activity, name, apkUrl, then);
            });
        }, "EssentiaUpdateCheck").start();
    }

    private static void askToUpdate(Activity activity, String versionName, String apkUrl, Done then)
    {
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.update_title)
            .setMessage(activity.getString(R.string.update_message, versionName, BuildConfig.VERSION_NAME))
            .setCancelable(false)
            .setPositiveButton(R.string.update_now, (d, w) -> startDownload(activity, apkUrl, then))
            .setNegativeButton(R.string.update_later, (d, w) -> then.run())
            .show();
    }

    private static void startDownload(Activity activity, String apkUrl, Done then)
    {
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File target = new File(dir, "essentia-update.apk");
            if (target.exists()) //noinspection ResultOfMethodCallIgnored
                target.delete();

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(activity.getString(R.string.app_name))
                .setDescription(activity.getString(R.string.update_downloading))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "essentia-update.apk");

            final long id = dm.enqueue(req);
            Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_LONG).show();

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { activity.unregisterReceiver(this); } catch (Exception ignored) { }

                    Uri apk = dm.getUriForDownloadedFile(id);
                    if (apk == null) {
                        Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
                        then.run();
                        return;
                    }

                    Intent install = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apk, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        activity.startActivity(install);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not open installer", e);
                        Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
                        then.run();
                    }
                }
            };

            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
            then.run();
        }
    }

    private static String download(String url) throws Exception
    {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            int code = c.getResponseCode();
            // GitHub redirects release downloads to another https host
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                return download(loc);
            }
            if (code != 200) throw new Exception("HTTP " + code);

            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 64 * 1024) throw new Exception("version.json too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
