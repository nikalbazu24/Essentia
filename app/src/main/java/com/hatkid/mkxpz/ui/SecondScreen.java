package com.hatkid.mkxpz.ui;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

/**
 * Dual-screen mode in the library: on devices with a second screen (e.g. AYN Thor)
 * the selected game's card is shown there: cover, play time, sessions, saves,
 * version, size... and a status bar with the time, battery and library totals.
 * It never takes the controller away from the main screen.
 */
public final class SecondScreen extends Presentation
{
    /** One stat: label + value. */
    public static final class Stat
    {
        final String label;
        final String value;
        public Stat(String label, String value) { this.label = label; this.value = value; }
    }

    private ImageView mBackdrop;
    private ImageView mCover;
    private TextView mTitle;
    private TextView mSubtitle;
    private LinearLayout mTiles;
    private TextView mFooter;
    private TextView mBattery;
    private LinearLayout mNavigation;
    private TextView mNews;

    public void navigation(Runnable previous, Runnable play, Runnable next, Runnable store, String news) {
        if (mNavigation == null) return;
        mNavigation.removeAllViews();
        String[] labels = { "‹", "Jugar", "›", "Descubrir" };
        Runnable[] actions = { previous, play, next, store };
        for (int i=0;i<labels.length;i++) {
            android.widget.Button b=Ui.button(getContext(),labels[i],false);
            final Runnable action=actions[i]; b.setOnClickListener(v->action.run());
            b.setContentDescription(i==0?"Juego anterior":i==2?"Juego siguiente":labels[i]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(getContext(),48),1f);
            lp.setMargins(Ui.dp(getContext(),3),0,Ui.dp(getContext(),3),0);mNavigation.addView(b,lp);
        }
        mNews.setText(news);
    }

    private SecondScreen(Context outer, Display display)
    {
        super(outer, display);
    }

    /** The other screen available for a presentation, or null if there is none. */
    public static Display findOtherDisplay(Context c, Display current)
    {
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (current == null || d.getDisplayId() != current.getDisplayId()) return d;
        }
        return null;
    }

    public static SecondScreen show(Context c, Display current)
    {
        Display other = findOtherDisplay(c, current);
        if (other == null) return null;
        try {
            SecondScreen s = new SecondScreen(c, other);
            s.show();
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** Keeps controller input on the main screen. */
    public static void notFocusable(android.app.Dialog d)
    {
        if (d.getWindow() != null) {
            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        notFocusable(this);
        Context c = getContext();

        FrameLayout root = new FrameLayout(c);
        root.setBackground(Ui.backdrop());

        mBackdrop = new ImageView(c); // retained loader target; no decorative full-screen layer
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 18);
        col.setPadding(pad, pad, pad, Ui.dp(c, 10));
        root.addView(col, match());

        // ---- game card
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout coverFrame = new FrameLayout(c);
        coverFrame.setBackground(Ui.rounded(c, Ui.SURFACE, 16));
        coverFrame.setClipToOutline(true);
        coverFrame.setElevation(Ui.dp(c, 10));
        mCover = new ImageView(c);
        mCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(mCover, match());
        row.addView(coverFrame, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.9f));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(c, 18), 0, 0, 0);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.6f));

        TextView label = Ui.text(c, c.getString(R.string.second_label), 11, Ui.ACCENT_LIGHT, true);
        label.setAllCaps(true);
        label.setLetterSpacing(0.04f);
        info.addView(label);

        mTitle = Ui.text(c, c.getString(R.string.app_name), 22, Ui.TEXT, true);
        mTitle.setMaxLines(2);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setPadding(0, Ui.dp(c, 2), 0, 0);
        info.addView(mTitle);

        mSubtitle = Ui.text(c, "", 12, Ui.TEXT_DIM, false);
        mSubtitle.setMaxLines(1);
        mSubtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        mSubtitle.setPadding(0, 0, 0, Ui.dp(c, 8));
        info.addView(mSubtitle);

        mTiles = new LinearLayout(c);
        mTiles.setOrientation(LinearLayout.VERTICAL);
        info.addView(mTiles, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mNavigation=new LinearLayout(c);
        mNavigation.setPadding(0,Ui.dp(c,8),0,0);
        col.addView(mNavigation,new LinearLayout.LayoutParams(-1,-2));
        mNews=Ui.text(c,"",12,Ui.ACCENT_LIGHT,false);mNews.setMaxLines(1);
        mNews.setEllipsize(TextUtils.TruncateAt.END);col.addView(mNews);

        // ---- status bar
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), 0);
        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Ui.TEXT);
        clock.setTextSize(14);
        clock.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bar.addView(clock);
        mBattery = Ui.text(c, "", 13, Ui.TEXT_DIM, false);
        mBattery.setPadding(Ui.dp(c, 12), 0, 0, 0);
        bar.addView(mBattery);
        mFooter = Ui.text(c, "", 13, Ui.TEXT_DIM, false);
        mFooter.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        mFooter.setMaxLines(1);
        mFooter.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(mFooter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private static FrameLayout.LayoutParams match()
    {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public ImageView coverView() { return mCover; }
    public ImageView backdropView() { return mBackdrop; }

    /** The selected game: title, a small line under it and its stats (3 per row). */
    public void showGame(String title, String subtitle, java.util.List<Stat> stats, String footer)
    {
        if (mTitle == null) return;
        Context c = getContext();
        mTitle.setText(title);
        mSubtitle.setText(subtitle == null ? "" : subtitle);
        mSubtitle.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        mFooter.setText(footer == null ? "" : footer);
        mBattery.setText(battery(c));

        mTiles.removeAllViews();
        if (stats == null) return;
        LinearLayout line = null;
        for (int i = 0; i < stats.size(); i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(c);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                llp.bottomMargin = Ui.dp(c, 8);
                mTiles.addView(line, llp);
            }
            Stat s = stats.get(i);
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setBackground(Ui.rounded(c, Ui.alpha(Ui.SURFACE_HI, 0x88), 12));
            int p = Ui.dp(c, 10);
            tile.setPadding(p, Ui.dp(c, 4), p, Ui.dp(c, 4));
            TextView v = Ui.text(c, s.value, 16, Ui.TEXT, true);
            v.setMaxLines(1);
            v.setEllipsize(TextUtils.TruncateAt.END);
            TextView l = Ui.text(c, s.label, 11, Ui.TEXT_DIM, false);
            l.setMaxLines(1);
            l.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(v);
            tile.addView(l);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            tlp.rightMargin = (i % 3 == 2) ? 0 : Ui.dp(c, 8);
            line.addView(tile, tlp);
        }
        // keep the grid even
        while (line != null && line.getChildCount() < 3) {
            View filler = new View(c);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            flp.rightMargin = line.getChildCount() == 2 ? 0 : Ui.dp(c, 8);
            line.addView(filler, flp);
        }
    }

    private static String battery(Context c)
    {
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return "";
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level < 0 || scale <= 0) return "";
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            return (charging ? "⚡ " : "🔋 ") + (level * 100 / scale) + "%";
        } catch (Exception e) {
            return "";
        }
    }
}
