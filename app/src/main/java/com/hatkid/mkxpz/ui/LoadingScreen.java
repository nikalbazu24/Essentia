package com.hatkid.mkxpz.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hatkid.mkxpz.Games;
import com.hatkid.mkxpz.R;

import java.io.File;
import java.util.Locale;

/** Shown over the game until it draws its first frame, so a slow start is never a mystery. */
public final class LoadingScreen extends FrameLayout
{
    private static final String TAG = "Essentia[Loading]";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final long mStart = SystemClock.elapsedRealtime();
    private final TextView mStatus;
    private final TextView mHint;
    private boolean mDone = false;
    private final boolean mShowTimes;
    private String mPhase = null;
    private long mPhaseStart;
    private final java.util.LinkedHashMap<String, Long> mPhaseTimes = new java.util.LinkedHashMap<>();

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (mDone) return;
            long secs = (SystemClock.elapsedRealtime() - mStart) / 1000;
            mStatus.setText(getContext().getString(R.string.loading_status, secs)
                + (mPhase != null ? "  ·  " + phaseLabel(mPhase) : ""));
            if (secs >= 10) mHint.setAlpha(1f);
            mHandler.postDelayed(this, 500);
        }
    };

    public LoadingScreen(Context c, String title, String gamePath, boolean showTimes)
    {
        super(c);
        mShowTimes = showTimes;
        setBackgroundColor(Ui.BG);
        setClickable(true); // touches don't reach the game while it loads

        ImageView backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.22f);
        addView(backdrop, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageView flask = new ImageView(c);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        int s = Ui.dp(c, 96);
        col.addView(flask, new LinearLayout.LayoutParams(s, s));
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.45f);
        pulse.setDuration(900);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        flask.startAnimation(pulse);

        TextView name = Ui.text(c, title.isEmpty() ? c.getString(R.string.app_name) : title, 24, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(Ui.dp(c, 24), Ui.dp(c, 12), Ui.dp(c, 24), Ui.dp(c, 6));
        col.addView(name);

        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(Ui.tint(Ui.ACCENT_LIGHT));
        col.addView(bar, new LinearLayout.LayoutParams(Ui.dp(c, 220), Ui.dp(c, 12)));

        mStatus = Ui.text(c, c.getString(R.string.loading_status, 0), 15, Ui.TEXT_DIM, false);
        mStatus.setGravity(Gravity.CENTER);
        mStatus.setPadding(0, Ui.dp(c, 8), 0, 0);
        col.addView(mStatus);

        mHint = Ui.text(c, c.getString(R.string.loading_hint), 13, Ui.TEXT_DIM, false);
        mHint.setGravity(Gravity.CENTER);
        mHint.setPadding(Ui.dp(c, 32), Ui.dp(c, 10), Ui.dp(c, 32), 0);
        mHint.setAlpha(0f);
        col.addView(mHint);

        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        mHandler.post(mTick);

        // The game's own title picture as a soft background
        new Thread(() -> {
            try {
                File cover = Games.findCover(new File(gamePath));
                if (cover == null) return;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bmp = BitmapFactory.decodeFile(cover.getAbsolutePath(), o);
                if (bmp != null) mHandler.post(() -> { if (!mDone) backdrop.setImageBitmap(bmp); });
            } catch (Throwable ignored) { }
        }, "EssentiaLoadingCover").start();
    }

    private String phaseLabel(String phase)
    {
        Context c = getContext();
        switch (phase) {
            case "files": return c.getString(R.string.loading_files);
            case "engine": return c.getString(R.string.loading_engine);
            case "scripts": return c.getString(R.string.loading_scripts);
            case "start": return c.getString(R.string.loading_start);
            default: return phase;
        }
    }

    /** The engine moved to another loading stage. */
    public void setPhase(String phase)
    {
        long now = SystemClock.elapsedRealtime();
        if (mPhase != null) mPhaseTimes.put(mPhase, now - mPhaseStart);
        else mPhaseTimes.put("prep", now - mStart);
        mPhase = phase;
        mPhaseStart = now;
    }

    /** The game is running: fade away. */
    public void finish()
    {
        if (mDone) return;
        mDone = true;
        long now = SystemClock.elapsedRealtime();
        if (mPhase != null) mPhaseTimes.put(mPhase, now - mPhaseStart);
        float total = (now - mStart) / 1000f;
        Log.i(TAG, String.format(Locale.ROOT, "Game ready after %.1f s %s", total, mPhaseTimes));

        if (mShowTimes) {
            StringBuilder sb = new StringBuilder(getContext().getString(R.string.loading_done, total));
            for (java.util.Map.Entry<String, Long> e : mPhaseTimes.entrySet()) {
                String name = "prep".equals(e.getKey()) ? getContext().getString(R.string.loading_prep) : phaseLabel(e.getKey());
                sb.append("\n").append(name).append(": ")
                  .append(String.format(Locale.ROOT, "%.1f s", e.getValue() / 1000f));
            }
            android.widget.Toast.makeText(getContext(), sb.toString(), android.widget.Toast.LENGTH_LONG).show();
        }

        AlphaAnimation out = new AlphaAnimation(1f, 0f);
        out.setDuration(350);
        out.setFillAfter(true);
        out.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) { }
            @Override public void onAnimationRepeat(Animation a) { }
            @Override public void onAnimationEnd(Animation a) {
                setVisibility(GONE);
                if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(LoadingScreen.this);
            }
        });
        startAnimation(out);
    }
}
