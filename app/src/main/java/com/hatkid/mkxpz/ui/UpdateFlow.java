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
            .setPositiveButton(R.string.upd_do, (x, w) -> run(a, dir, title, info.links.get(host), info.latest, launch))
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

    public static void run(Activity a, File dir, String title, String link, String version, Runnable after)
    {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, p, p, p);
        TextView status = Ui.text(a, a.getString(R.string.upd_downloading), 16, Ui.TEXT, true);
        box.addView(status);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 16));
        blp.topMargin = Ui.dp(a, 12);
        box.addView(bar, blp);
        TextView detail = Ui.text(a, "", 14, Ui.TEXT_DIM, false);
        detail.setPadding(0, Ui.dp(a, 8), 0, 0);
        box.addView(detail);
        TextView note = Ui.text(a, a.getString(R.string.upd_note), 13, Ui.TEXT_DIM, false);
        note.setPadding(0, Ui.dp(a, 10), 0, 0);
        box.addView(note);

        final boolean[] cancel = { false };
        AlertDialog dlg = dialog(a)
            .setTitle(title + "  →  " + version)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel, (d, w) -> cancel[0] = true)
            .create();
        controllerFriendly(dlg);
        dlg.show();
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final int[] steps = { R.string.upd_downloading, R.string.upd_extracting, R.string.upd_copying };
        GameUpdater.Progress progress = new GameUpdater.Progress() {
            @Override public void update(int step, float fraction, String text) {
                a.runOnUiThread(() -> {
                    status.setText(steps[step]);
                    bar.setIndeterminate(fraction < 0);
                    if (fraction >= 0) bar.setProgress((int) (fraction * 1000));
                    detail.setText(text);
                    View cancelButton = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
                    if (step == 2 && cancelButton != null) cancelButton.setEnabled(false);
                });
            }
            @Override public boolean cancelled() { return cancel[0]; }
        };

        new Thread(() -> {
            String msg;
            try {
                File backup = GameUpdater.install(dir, link,
                    new File(Environment.getExternalStorageDirectory(), "Essentia/Respaldos"), progress);
                if (cancel[0]) msg = null;
                else msg = a.getString(R.string.upd_done, version)
                    + (backup != null ? "\n\n" + a.getString(R.string.upd_backup, backup.getAbsolutePath()) : "");
            } catch (GameUpdater.UnsupportedFormat ex) {
                msg = a.getString(R.string.upd_format, ex.getMessage());
            } catch (GameUpdater.NoSpace ex) {
                msg = a.getString(R.string.upd_space, GameUpdater.mb(ex.needed));
            } catch (Exception ex) {
                msg = a.getString(R.string.upd_failed) + "\n(" + ex.getMessage() + ")";
            }
            final String text = msg;
            a.runOnUiThread(() -> {
                a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (dlg.isShowing()) dlg.dismiss();
                if (text == null) { if (after != null) after.run(); }
                else message(a, title, text, after);
            });
        }, "EssentiaUpdate").start();
    }

    // ------------------------------------------------------------ helpers

    private static AlertDialog.Builder dialog(Activity a)
    {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
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
    private static void controllerFriendly(AlertDialog d)
    {
        d.setOnKeyListener((dialog, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_UP) {
                return code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_B;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_A) {
                View f = d.getCurrentFocus();
                if (f != null) f.performClick();
                return true;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_B) {
                View neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (neg != null && neg.isEnabled()) neg.performClick();
                return true;
            }
            return false;
        });
    }
}
