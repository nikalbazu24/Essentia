package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hatkid.mkxpz.EssentiaSettings;
import com.hatkid.mkxpz.GameUpdater;
import com.hatkid.mkxpz.R;

import java.io.File;

/**
 * Game updates (PokéUpdater games such as Pokémon Añil), shown from the library
 * ("Update the game") and automatically before a game opens when there is a new
 * official version. Saves are never touched (and are backed up first).
 */
public final class UpdateFlow
{
    /** Before opening a game, look again after this long (not on every launch). */
    private static final long RECHECK_MS = 60L * 60 * 1000;
    /** Don't keep the player waiting if the internet is slow. */
    private static final long CHECK_TIMEOUT_MS = 6000;

    private UpdateFlow() { }

    private static SharedPreferences prefs(Context c)
    {
        return c.getSharedPreferences("essentia_game_updates", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ before playing

    /**
     * Runs {@code launch} right away, or after offering (and maybe doing) an update
     * if the game announces a newer official version.
     */
    public static void beforeLaunch(Activity a, File dir, String title, Runnable launch)
    {
        com.hatkid.mkxpz.UpdateService.State busy = com.hatkid.mkxpz.UpdateService.stateFor(dir.getAbsolutePath());
        if (busy != null) {
            int pct = busy.percent();
            message(a, title, a.getString(R.string.upd_busy, pct < 0 ? "…" : pct + "%"),
                () -> { if (a instanceof com.hatkid.mkxpz.LauncherActivity) a.finish(); });
            return;
        }
        if (!EssentiaSettings.flag(a, EssentiaSettings.AUTO_UPDATE, true) || !GameUpdater.supported(dir)) {
            launch.run();
            return;
        }
        String key = dir.getAbsolutePath();
        long last = prefs(a).getLong(key + ":time", 0);
        if (System.currentTimeMillis() - last < RECHECK_MS) {
            launch.run();
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] done = { false };
        AlertDialog checking = dialog(a)
            .setTitle(title)
            .setMessage(R.string.upd_checking)
            .setCancelable(false)
            .setNegativeButton(R.string.upd_skip, null)
            .create();
        Runnable proceed = () -> {
            if (done[0]) return;
            done[0] = true;
            if (checking.isShowing()) checking.dismiss();
            launch.run();
        };
        checking.setOnShowListener(d -> checking.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener(v -> proceed.run()));
        controllerFriendly(checking);
        checking.show();
        main.postDelayed(proceed, CHECK_TIMEOUT_MS);

        new Thread(() -> {
            GameUpdater.Info info = null;
            try { info = GameUpdater.check(dir); } catch (Exception ignored) { }
            final GameUpdater.Info result = info;
            main.post(() -> {
                if (done[0]) return; // too slow: the game is already open
                prefs(a).edit().putLong(key + ":time", System.currentTimeMillis()).apply();
                String skipped = prefs(a).getString(key + ":skip", "");
                if (result == null || !result.newer() || result.links.isEmpty() || result.latest.equals(skipped)) {
                    proceed.run();
                    return;
                }
                done[0] = true;
                checking.dismiss();
                offer(a, dir, title, result, launch);
            });
        }, "EssentiaUpdateCheck").start();
    }

    private static void offer(Activity a, File dir, String title, GameUpdater.Info info, Runnable launch)
    {
        String host = info.links.keySet().iterator().next();
        AlertDialog d = dialog(a)
            .setTitle(a.getString(R.string.upd_found, info.latest))
            .setMessage(a.getString(R.string.upd_offer, title, info.current, info.latest))
            .setCancelable(false)
            .setPositiveButton(R.string.upd_do, (x, w) -> run(a, dir, title, info.links.get(host), info.latest,
                () -> { if (a instanceof com.hatkid.mkxpz.LauncherActivity) a.finish(); }))
            .setNegativeButton(R.string.upd_play_now, (x, w) -> launch.run())
            .setNeutralButton(R.string.upd_not_this, (x, w) -> {
                prefs(a).edit().putString(dir.getAbsolutePath() + ":skip", info.latest).apply();
                launch.run();
            })
            .create();
        controllerFriendly(d);
        d.show();
    }

    // ------------------------------------------------------------ from the library

    /** "Update the game" in the library options. */
    public static void manual(Activity a, File dir, String title, Runnable after)
    {
        AlertDialog checking = dialog(a)
            .setTitle(title)
            .setMessage(R.string.upd_checking)
            .setCancelable(false)
            .create();
        controllerFriendly(checking);
        checking.show();
        new Thread(() -> {
            GameUpdater.Info info = null;
            try { info = GameUpdater.check(dir); } catch (Exception ignored) { }
            final GameUpdater.Info result = info;
            a.runOnUiThread(() -> {
                checking.dismiss();
                if (result == null) {
                    message(a, title, a.getString(R.string.upd_check_failed), after);
                } else if (!result.newer()) {
                    message(a, title, a.getString(R.string.upd_latest, result.current), after);
                } else if (result.links.isEmpty()) {
                    message(a, title, a.getString(R.string.upd_no_host), after);
                } else {
                    prefs(a).edit().remove(dir.getAbsolutePath() + ":skip").apply();
                    String[] hosts = result.links.keySet().toArray(new String[0]);
                    final int[] chosen = { 0 };
                    AlertDialog.Builder b = dialog(a)
                        .setTitle(a.getString(R.string.upd_found, result.latest))
                        .setPositiveButton(R.string.upd_do, (d, w) ->
                            run(a, dir, title, result.links.get(hosts[chosen[0]]), result.latest, after))
                        .setNegativeButton(R.string.cancel, (d, w) -> { if (after != null) after.run(); });
                    if (hosts.length > 1) b.setSingleChoiceItems(hosts, 0, (d, w) -> chosen[0] = w);
                    else b.setMessage(a.getString(R.string.upd_found_msg, result.current, result.latest, hosts[0]));
                    AlertDialog d = b.create();
                    controllerFriendly(d);
                    d.show();
                }
            });
        }, "EssentiaUpdateCheck").start();
    }

    // ------------------------------------------------------------ the update itself

    /**
     * Starts the update in the background (notification with progress) and
     * continues with {@code after}: the player can keep using the console.
     */
    public static void run(Activity a, File dir, String title, String link, String version, Runnable after)
    {
        allowBackground(a, () -> {
            com.hatkid.mkxpz.UpdateService.start(a.getApplicationContext(), dir.getAbsolutePath(), title, link, version);
            message(a, title, a.getString(R.string.upd_background, version), after);
        });
    }

    /**
     * Android 13+: permission to show the progress notification. Also asks once to
     * let Essentia keep working with the screen off (console closed).
     */
    private static void allowBackground(Activity a, Runnable then)
    {
        if (android.os.Build.VERSION.SDK_INT >= 33
            && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { a.requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 77); } catch (Exception ignored) { }
        }
        android.os.PowerManager pm = (android.os.PowerManager) a.getSystemService(Context.POWER_SERVICE);
        boolean asked = prefs(a).getBoolean("asked_battery", false);
        if (android.os.Build.VERSION.SDK_INT >= 23 && pm != null && !asked
            && !pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
            prefs(a).edit().putBoolean("asked_battery", true).apply();
            AlertDialog d = dialog(a)
                .setTitle(R.string.upd_battery_title)
                .setMessage(R.string.upd_battery_msg)
                .setPositiveButton(R.string.upd_battery_allow, (x, w) -> {
                    try {
                        a.startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception ignored) { }
                    then.run();
                })
                .setNegativeButton(R.string.upd_battery_skip, (x, w) -> then.run())
                .setCancelable(false)
                .create();
            controllerFriendly(d);
            d.show();
            return;
        }
        then.run();
    }

    // ------------------------------------------------------------ helpers

    private static AlertDialog.Builder dialog(Activity a)
    {
        return new AlertDialog.Builder(a, com.hatkid.mkxpz.ui.Ui.dialogTheme());
    }

    private static void message(Activity a, String title, String text, Runnable after)
    {
        AlertDialog d = dialog(a)
            .setTitle(title)
            .setMessage(text)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, (x, w) -> { if (after != null) after.run(); })
            .create();
        controllerFriendly(d);
        d.show();
    }

    /** A presses the selected button, B cancels (some devices don't do this in dialogs). */
    public static void controllerFriendly(AlertDialog d)
    {
        d.setOnKeyListener((dialog, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_UP) {
                return code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_B;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_A) {
                View f = d.getCurrentFocus();
                if (f instanceof android.widget.ListView) {
                    android.widget.ListView lv = (android.widget.ListView) f;
                    int pos = lv.getSelectedItemPosition();
                    if (pos >= 0) lv.performItemClick(lv.getSelectedView(), pos, lv.getItemIdAtPosition(pos));
                } else if (f != null) {
                    f.performClick();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_B) {
                View neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (neg != null && neg.getVisibility() == View.VISIBLE && neg.isEnabled()) neg.performClick();
                else {
                    View pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (pos == null || pos.getVisibility() != View.VISIBLE) d.cancel(); // a plain list
                }
                return true;
            }
            return false;
        });
    }
}
