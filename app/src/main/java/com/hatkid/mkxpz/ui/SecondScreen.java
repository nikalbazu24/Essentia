package com.hatkid.mkxpz.ui;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

/**
 * Dual-screen mode: on devices with a second screen (e.g. AYN Thor), shows the
 * selected game's cover and name there while the library stays on the other screen.
 */
public final class SecondScreen extends Presentation
{
    private ImageView mCover;
    private TextView mTitle;
    private TextView mHint;

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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Context c = getContext();

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int pad = Ui.dp(c, 24);
        col.setPadding(pad, pad, pad, pad);

        mCover = new ImageView(c);
        mCover.setAdjustViewBounds(true);
        mCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mCover.setImageResource(R.drawable.ic_launcher_foreground);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        col.addView(mCover, lp);

        mTitle = Ui.text(c, "Essentia", 26, Ui.TEXT, true);
        mTitle.setGravity(Gravity.CENTER);
        mTitle.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        col.addView(mTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        mHint = Ui.text(c, c.getString(R.string.lib_second_hint), 15, Ui.TEXT_DIM, false);
        mHint.setGravity(Gravity.CENTER);
        col.addView(mHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    public void showGame(String title, Bitmap cover)
    {
        if (mTitle == null) return;
        mTitle.setText(title);
        if (cover != null) {
            mCover.setImageBitmap(cover);
        } else {
            mCover.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }
}
