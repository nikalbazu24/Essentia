package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.EssentiaSettings;
import com.hatkid.mkxpz.R;

/**
 * A short tour of what changed, shown once after Essentia updates itself.
 * The pages are updated with every release.
 */
public final class WhatsNew
{
    private static final String KEY = "whats_new_seen";
    private static final String KEY_TOUR = "whats_new_tour";
    /** Raise this when PAGES change: small fix releases don't show the tour again. */
    private static final int TOUR = 1;

    /** Icon, title, text for this release. */
    private static final int[][] PAGES = {
        { R.drawable.ic_w_download, R.string.wn_bg_title, R.string.wn_bg_text },
        { R.drawable.ic_w_music, R.string.wn_music_title, R.string.wn_music_text },
        { R.drawable.ic_e_gamepad, R.string.wn_controls_title, R.string.wn_controls_text },
        { R.drawable.ic_w_wide, R.string.wn_wide_title, R.string.wn_wide_text },
        { R.drawable.ic_w_speed, R.string.wn_light_title, R.string.wn_light_text },
    };

    private WhatsNew() { }

    /** Shows the tour if this version is new for the user. */
    public static void showIfNew(Activity a)
    {
        SharedPreferences p = a.getSharedPreferences(EssentiaSettings.PREFS, Context.MODE_PRIVATE);
        int seen = p.getInt(KEY_TOUR, p.getInt(KEY, 0) > 0 ? 1 : 0);
        if (seen >= TOUR) return;
        p.edit().putInt(KEY_TOUR, TOUR).apply();
        show(a);
    }

    public static void show(Activity a)
    {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(a, 24);
        box.setPadding(pad, pad, pad, Ui.dp(a, 14));
        box.setBackground(Ui.rounded(a, Ui.BG, 22));

        TextView header = Ui.text(a, a.getString(R.string.wn_header), 14, Ui.ACCENT_LIGHT, true);
        header.setGravity(Gravity.CENTER);
        box.addView(header);

        ImageView icon = new ImageView(a);
        icon.setColorFilter(Ui.GOLD);
        int is = Ui.dp(a, 56);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        ilp.topMargin = Ui.dp(a, 14);
        box.addView(icon, ilp);

        TextView title = Ui.text(a, "", 22, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 6));
        box.addView(title);

        TextView text = Ui.text(a, "", 15, Ui.TEXT_DIM, false);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.15f);
        box.addView(text, new LinearLayout.LayoutParams(Ui.dp(a, 420), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView dots = Ui.text(a, "", 16, Ui.ACCENT_LIGHT, true);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        box.addView(dots);

        LinearLayout buttons = new LinearLayout(a);
        Button close = Ui.button(a, a.getString(R.string.wn_close), false);
        Button next = Ui.button(a, a.getString(R.string.wn_next), true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), 0);
        buttons.addView(close, blp);
        buttons.addView(next, new LinearLayout.LayoutParams(blp));
        box.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(box)
            .create();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final int[] page = { 0 };
        Runnable showPage = () -> {
            int[] pg = PAGES[page[0]];
            icon.setImageResource(pg[0]);
            title.setText(pg[1]);
            text.setText(pg[2]);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < PAGES.length; i++) sb.append(i == page[0] ? "● " : "○ ");
            dots.setText(sb.toString().trim());
            next.setText(page[0] == PAGES.length - 1 ? R.string.wn_done : R.string.wn_next);
        };
        next.setOnClickListener(v -> {
            if (page[0] >= PAGES.length - 1) d.dismiss();
            else { page[0]++; showPage.run(); }
        });
        close.setOnClickListener(v -> d.dismiss());
        d.setOnKeyListener((x, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_UP) {
                return code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_B
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT;
            }
            switch (code) {
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    next.performClick();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (page[0] > 0) { page[0]--; showPage.run(); }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    d.dismiss();
                    return true;
            }
            return false;
        });
        showPage.run();
        d.show();
        next.requestFocus();
    }
}
