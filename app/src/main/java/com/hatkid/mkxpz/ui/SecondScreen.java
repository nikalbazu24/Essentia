package com.hatkid.mkxpz.ui;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

/**
 * Dual-screen mode in the library: on devices with a second screen (e.g. AYN Thor),
 * shows the selected game's details there: cover, name, last played and where its
 * saves are. It never takes the controller away from the main screen.
 */
public final class SecondScreen extends Presentation
{
    private ImageView mBackdrop;
    private ImageView mCover;
    private TextView mTitle;
    private TextView mDetail;

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
        root.setBackgroundColor(Ui.BG);

        mBackdrop = new ImageView(c);
        mBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mBackdrop.setAlpha(0.4f);
        if (Build.VERSION.SDK_INT >= 31) {
            mBackdrop.setRenderEffect(RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP));
        }
        root.addView(mBackdrop, match());
        android.view.View shade = new android.view.View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ 0x4014162B, 0xE614162B }));
        root.addView(shade, match());

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 22);
        row.setPadding(pad, pad, pad, pad);

        FrameLayout coverFrame = new FrameLayout(c);
        coverFrame.setBackground(Ui.rounded(c, Ui.SURFACE, 16));
        coverFrame.setClipToOutline(true);
        coverFrame.setElevation(Ui.dp(c, 10));
        mCover = new ImageView(c);
        mCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(mCover, match());
        row.addView(coverFrame, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(Ui.dp(c, 20), 0, 0, 0);

        TextView label = Ui.text(c, c.getString(R.string.second_label), 12, Ui.ACCENT_LIGHT, true);
        label.setAllCaps(true);
        label.setLetterSpacing(0.12f);
        info.addView(label);

        mTitle = Ui.text(c, c.getString(R.string.app_name), 24, Ui.TEXT, true);
        mTitle.setMaxLines(3);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        info.addView(mTitle);

        mDetail = Ui.text(c, "", 13, Ui.TEXT_DIM, false);
        info.addView(mDetail);

        TextView hint = Ui.text(c, c.getString(R.string.lib_second_hint), 14, Ui.GOLD, true);
        hint.setPadding(0, Ui.dp(c, 14), 0, 0);
        info.addView(hint);

        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        root.addView(row, match());
        setContentView(root);
    }

    private static FrameLayout.LayoutParams match()
    {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public ImageView coverView() { return mCover; }
    public ImageView backdropView() { return mBackdrop; }

    public void showGame(String title, Bitmap cover)
    {
        showGame(title, null, cover);
    }

    /** Title, detail lines and (optionally) the cover. */
    public void showGame(String title, String detail, Bitmap cover)
    {
        if (mTitle == null) return;
        mTitle.setText(title);
        mDetail.setText(detail == null ? "" : detail);
        if (cover != null) mCover.setImageBitmap(cover);
        else mCover.setImageResource(R.drawable.ic_launcher_foreground);
    }
}
