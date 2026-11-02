package com.hatkid.mkxpz;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.SecondScreen;
import com.hatkid.mkxpz.ui.Ui;

/** Essentia's settings: on-screen controls, screen, updates and credits. */
public class SettingsActivity extends ControllerActivity
{
    private LinearLayout mList;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        mList.setPadding(pad, pad, pad, pad);
        scroll.addView(mList, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Ui.button(this, "‹  " + getString(R.string.set_back), false);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = Ui.text(this, getString(R.string.lib_settings), 24, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        header.addView(title);
        mList.addView(header);

        buildControls();
        buildScreen();
        buildUpdates();
        buildAbout();

        setContentView(scroll);
        back.requestFocus();
    }

    // ------------------------------------------------------------ sections

    private void buildControls()
    {
        section(R.string.set_osc_title, R.string.set_osc_desc);

        RadioGroup group = new RadioGroup(this);
        String mode = EssentiaSettings.oscMode(this);
        group.addView(radio(R.string.set_osc_auto, EssentiaSettings.OSC_AUTO, mode));
        group.addView(radio(R.string.set_osc_always, EssentiaSettings.OSC_ALWAYS, mode));
        group.addView(radio(R.string.set_osc_never, EssentiaSettings.OSC_NEVER, mode));
        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setOscMode(this, (String) r.getTag());
        });
        card(group);

        slider(R.string.set_osc_size, 60, 160, EssentiaSettings.oscScale(this),
            v -> EssentiaSettings.setOscScale(this, v));
        slider(R.string.set_osc_opacity, 10, 100, EssentiaSettings.oscOpacity(this),
            v -> EssentiaSettings.setOscOpacity(this, v));

        TextView map = Ui.text(this, getString(R.string.set_buttons_map), 14, Ui.TEXT_DIM, false);
        map.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        mList.addView(map);
    }

    private void buildScreen()
    {
        section(R.string.set_screen_title, 0);

        CheckBox smooth = check(R.string.set_smooth, EssentiaSettings.smoothScaling(this));
        smooth.setOnCheckedChangeListener((b, on) -> EssentiaSettings.setSmoothScaling(this, on));
        card(smooth);

        boolean hasSecond = SecondScreen.findOtherDisplay(this, getWindowManager().getDefaultDisplay()) != null;
        CheckBox dual = check(R.string.set_dual,
            EssentiaSettings.DUAL_AUTO.equals(EssentiaSettings.dualScreen(this)));
        dual.setOnCheckedChangeListener((b, on) -> EssentiaSettings.setDualScreen(this,
            on ? EssentiaSettings.DUAL_AUTO : EssentiaSettings.DUAL_OFF));
        card(dual);

        TextView note = Ui.text(this, getString(hasSecond ? R.string.set_dual_found : R.string.set_dual_none),
            13, Ui.TEXT_DIM, false);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
        mList.addView(note);
    }

    private void buildUpdates()
    {
        section(R.string.set_updates_title, 0);
        TextView version = Ui.text(this, getString(R.string.set_version, BuildConfig.VERSION_NAME), 15, Ui.TEXT, false);
        version.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        mList.addView(version);

        Button check = Ui.button(this, getString(R.string.set_check_updates), true);
        check.setOnClickListener(v -> Updater.check(this, () -> { }, true));
        mList.addView(check, wrap());
    }

    private void buildAbout()
    {
        section(R.string.set_about_title, 0);
        TextView about = Ui.text(this, getString(R.string.set_about_text), 14, Ui.TEXT_DIM, false);
        about.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        mList.addView(about);

        Button repo = Ui.button(this, getString(R.string.set_open_repo), false);
        repo.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nikalbazu24/Essentia")));
            } catch (Exception ignored) { }
        });
        mList.addView(repo, wrap());
    }

    // ------------------------------------------------------------ widgets

    private interface IntSetter { void set(int v); }

    private LinearLayout.LayoutParams wrap()
    {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void section(int titleRes, int descRes)
    {
        TextView t = Ui.text(this, getString(titleRes), 18, Ui.ACCENT_LIGHT, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 26), 0, Ui.dp(this, 6));
        mList.addView(t);
        if (descRes != 0) {
            TextView d = Ui.text(this, getString(descRes), 14, Ui.TEXT_DIM, false);
            d.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
            mList.addView(d);
        }
    }

    private void card(View content)
    {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(this, Ui.SURFACE, 14));
        int p = Ui.dp(this, 8);
        box.setPadding(p, p, p, p);
        box.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        mList.addView(box, lp);
    }

    private RadioButton radio(int textRes, String value, String current)
    {
        RadioButton r = new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(textRes);
        r.setTag(value);
        r.setTextColor(Ui.TEXT);
        r.setTextSize(16);
        r.setButtonTintList(Ui.tint(Ui.ACCENT_LIGHT));
        r.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 10);
        r.setPadding(p, p, p, p);
        r.setChecked(value.equals(current));
        return r;
    }

    private CheckBox check(int textRes, boolean checked)
    {
        CheckBox c = new CheckBox(this);
        c.setText(textRes);
        c.setTextColor(Ui.TEXT);
        c.setTextSize(16);
        c.setButtonTintList(Ui.tint(Ui.ACCENT_LIGHT));
        c.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 10);
        c.setPadding(p, p, p, p);
        c.setChecked(checked);
        return c;
    }

    private void slider(int labelRes, int min, int max, int value, IntSetter setter)
    {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView label = Ui.text(this, getString(labelRes, value), 15, Ui.TEXT, false);
        label.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, 0);
        col.addView(label);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setKeyProgressIncrement(5);
        bar.setProgressTintList(Ui.tint(Ui.ACCENT));
        bar.setThumbTintList(Ui.tint(Ui.ACCENT_LIGHT));
        bar.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 14);
        bar.setPadding(p, p, p, p);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress;
                label.setText(getString(labelRes, v));
                setter.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(bar);
        card(col);
    }
}
