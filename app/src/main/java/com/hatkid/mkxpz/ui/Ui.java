package com.hatkid.mkxpz.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

/** Essentia's look: the colors of the essence flask, and focus-friendly widgets. */
public final class Ui
{
    public static final int BG = Color.parseColor("#14162B");
    public static final int SURFACE = Color.parseColor("#1E2240");
    public static final int SURFACE_HI = Color.parseColor("#2A2F55");
    public static final int ACCENT = Color.parseColor("#8A5CF6");
    public static final int ACCENT_LIGHT = Color.parseColor("#B48CFF");
    public static final int GOLD = Color.parseColor("#FFE066");
    public static final int TEXT = Color.parseColor("#F3EEFE");
    public static final int TEXT_DIM = Color.parseColor("#A9A6C9");

    private Ui() { }

    public static int dp(Context c, float v)
    {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context c, int color, float radiusDp)
    {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Background that shows a bright outline when focused (controller) or pressed (touch). */
    public static Drawable focusable(Context c, int color, float radiusDp)
    {
        GradientDrawable normal = rounded(c, color, radiusDp);

        GradientDrawable focused = rounded(c, SURFACE_HI, radiusDp);
        focused.setStroke(dp(c, 3), ACCENT_LIGHT);

        GradientDrawable pressed = rounded(c, ACCENT, radiusDp);

        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_selected}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static Button button(Context c, String text, boolean primary)
    {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(TEXT);
        b.setBackground(focusable(c, primary ? ACCENT : SURFACE, 12));
        b.setStateListAnimator(null);
        int h = dp(c, 16), v = dp(c, 10);
        b.setPadding(h, v, h, v);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setFocusable(true);
        return b;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold)
    {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    public static ColorStateList tint(int color)
    {
        return ColorStateList.valueOf(color);
    }
}
