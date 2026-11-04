package com.hatkid.mkxpz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Game updates in the background: keeps going while you use the console for
 * something else, play another game or close it (screen off). A notification
 * shows the progress (download, then installing files) and says when the game
 * is ready; tapping it opens the game.
 */
public class UpdateService extends Service
{
    private static final String ACTION_CANCEL = "essentia.update.CANCEL";
    private static final String CHANNEL_PROGRESS = "essentia_updates";
    private static final String CHANNEL_DONE = "essentia_updates_done";
    private static final int ID_PROGRESS = 7001;

    /** Progress of a game being updated, for the library. */
    public static final class State
    {
        public final String title;
        public int step;          // 0 download, 1 install
        public float fraction = -1;
        public String detail = "";
        State(String title) { this.title = title; }

        public int percent() { return fraction < 0 ? -1 : Math.round(fraction * 100); }
    }

    private static final Map<String, State> sStates = new HashMap<>();
    private static Runnable sListener;

    public static synchronized State stateFor(String path) { return sStates.get(path); }

    public static synchronized boolean busy(String path) { return sStates.containsKey(path); }
    public static synchronized boolean hasWork() { return !sStates.isEmpty(); }

    /** Called (on any thread) whenever progress changes; the library refreshes. */
    public static synchronized void setListener(Runnable r) { sListener = r; }

    private static void changed()
    {
        Runnable r;
        synchronized (UpdateService.class) { r = sListener; }
        if (r != null) r.run();
    }

    public static void start(Context c, String path, String title, String link, String version)
    {
        start(c, path, title, link, null, version, false);
    }

    /**
     * link: download it; or zip: a game zip already on the device. fresh: a new
     * game (from the store) that goes into the library when it's ready.
     */
    public static void start(Context c, String path, String title, String link, String zip, String version, boolean fresh)
    {
        synchronized (UpdateService.class) {
            if (GameRemoval.busy()) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(c.getApplicationContext(), R.string.delete_busy,
                        android.widget.Toast.LENGTH_LONG).show());
                return;
            }
            if (sStates.containsKey(path)) return;
            sStates.put(path, new State(title));
        }
        changed();
        Intent i = new Intent(c, UpdateService.class)
            .putExtra("path", path).putExtra("title", title)
            .putExtra("link", link).putExtra("zip", zip).putExtra("fresh", fresh)
            .putExtra("version", version == null ? "" : version);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    // ------------------------------------------------------------ service

    private final ArrayDeque<Intent> mQueue = new ArrayDeque<>();
    private Thread mWorker;
    private volatile boolean mCancel;
    private PowerManager.WakeLock mWake;
    private WifiManager.WifiLock mWifi;
    private NotificationManager mNm;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mNm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS,
                getString(R.string.upd_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationChannel done = new NotificationChannel(CHANNEL_DONE,
                getString(R.string.upd_channel_done), NotificationManager.IMPORTANCE_DEFAULT);
            mNm.createNotificationChannel(progress);
            mNm.createNotificationChannel(done);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Essentia:update");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) mWifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Essentia:update");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            mCancel = true;
            return START_NOT_STICKY;
        }
        startForeground(ID_PROGRESS, progressNotification(intent != null ? intent.getStringExtra("title") : "",
            getString(R.string.upd_downloading), -1, ""));
        if (intent != null && intent.getStringExtra("path") != null) {
            synchronized (mQueue) { mQueue.add(intent); }
        }
        if (mWorker == null) {
            mWorker = new Thread(this::work, "EssentiaUpdateService");
            mWorker.start();
        }
        return START_NOT_STICKY;
    }

    private void work()
    {
        try {
            if (!mWake.isHeld()) mWake.acquire(6 * 60 * 60 * 1000L);
            if (mWifi != null && !mWifi.isHeld()) mWifi.acquire();
            while (true) {
                Intent job;
                synchronized (mQueue) { job = mQueue.poll(); }
                if (job == null) break;
                mCancel = false;
                runJob(job);
            }
        } finally {
            if (mWake.isHeld()) mWake.release();
            if (mWifi != null && mWifi.isHeld()) mWifi.release();
            mWorker = null;
            stopForeground(true);
            stopSelf();
        }
    }

    private void runJob(Intent job)
    {
        String path = job.getStringExtra("path");
        String title = job.getStringExtra("title");
        String link = job.getStringExtra("link");
        String version = job.getStringExtra("version");
        String zip = job.getStringExtra("zip");
        boolean fresh = job.getBooleanExtra("fresh", false);
        State state = stateFor(path);
        if (state == null) state = new State(title);
        final State st = state;
        final long[] last = { 0 };
        final int[] stepNames = { R.string.upd_downloading, R.string.upd_installing };

        GameUpdater.Progress progress = new GameUpdater.Progress() {
            @Override public void update(int step, float fraction, String detail) {
                synchronized (UpdateService.class) {
                    st.step = Math.min(step, 1);
                    st.fraction = fraction;
                    st.detail = detail;
                }
                long now = System.currentTimeMillis();
                if (now - last[0] < 700) return;
                last[0] = now;
                changed();
                mNm.notify(ID_PROGRESS, progressNotification(title, getString(stepNames[st.step]), fraction, detail));
            }
            @Override public boolean cancelled() { return mCancel; }
        };

        String message;
        boolean ok = false;
        try {
            File dir = new File(path);
            File backupRoot = new File(Environment.getExternalStorageDirectory(), "Essentia/Respaldos");
            if (fresh && !dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("can't create " + dir);
            File backup = zip != null ? GameUpdater.installLocalZip(new File(zip), dir, backupRoot, progress)
                : GameUpdater.install(dir, link, backupRoot, progress);
            if (mCancel) {
                message = getString(R.string.upd_cancelled);
            } else {
                ok = true;
                Catalog.rememberVersion(this, path, version);
                if (fresh) {
                    Library.add(this, dir);
                    message = getString(R.string.store_installed_notif);
                } else {
                    message = version == null || version.isEmpty() ? getString(R.string.store_updated_notif)
                        : getString(R.string.upd_done_notif, version);
                }
                // no need to look for an update again right away
                getSharedPreferences("essentia_game_updates", MODE_PRIVATE).edit()
                    .putLong(path + ":time", System.currentTimeMillis()).apply();
            }
        } catch (GameUpdater.UnsupportedFormat ex) {
            message = getString(R.string.upd_format, ex.getMessage());
        } catch (GameUpdater.NoSpace ex) {
            message = getString(R.string.upd_space, GameUpdater.mb(ex.needed));
        } catch (Exception ex) {
            message = getString(R.string.upd_failed);
        }
        if (!ok && fresh) {
            // a new game that didn't arrive: leave no empty folder behind
            File d = new File(path);
            String[] left = d.list();
            //noinspection ResultOfMethodCallIgnored
            if (left != null && left.length == 0) d.delete();
        }
        if (ok) Catalog.clearUpdate(this, path);
        synchronized (UpdateService.class) { sStates.remove(path); }
        changed();
        doneNotification(path, title, message, ok);
    }

    // ------------------------------------------------------------ notifications

    private Notification.Builder builder(String channel)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private Notification progressNotification(String title, String step, float fraction, String detail)
    {
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, LibraryActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags);
        PendingIntent cancel = PendingIntent.getService(this, 2,
            new Intent(this, UpdateService.class).setAction(ACTION_CANCEL), flags);
        String pct = fraction >= 0 ? Math.round(fraction * 100) + "%" : "";
        Notification.Builder b = builder(CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(step + (pct.isEmpty() ? "" : "  ·  " + pct) + (detail.isEmpty() ? "" : "  ·  " + detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(1000, fraction < 0 ? 0 : (int) (fraction * 1000), fraction < 0)
            .addAction(new Notification.Action.Builder(null, getString(R.string.cancel), cancel).build());
        return b.build();
    }

    private void doneNotification(String path, String title, String message, boolean ok)
    {
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;
        Intent play = ok
            ? new Intent(this, LauncherActivity.class).putExtra("path", path)
            : new Intent(this, LibraryActivity.class);
        play.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, path.hashCode(), play, flags);
        Notification n = builder(CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(ok ? message + "  " + getString(R.string.upd_tap_play) : message)
            .setStyle(new Notification.BigTextStyle().bigText(ok ? message + "\n" + getString(R.string.upd_tap_play) : message))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build();
        mNm.notify(path.hashCode(), n);
    }
}
