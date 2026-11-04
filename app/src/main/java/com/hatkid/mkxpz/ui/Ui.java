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
    // The palette in use (set by applyTheme; the defaults are the "Esencia" theme)
    public static int BG = Color.parseColor("#0B1B21");
    public static int SURFACE = Color.parseColor("#142B32");
    public static int SURFACE_HI = Color.parseColor("#23424A");
    public static int ACCENT = Color.parseColor("#117E78");
    public static int ACCENT_LIGHT = Color.parseColor("#8BE0CC");
    public static final int GOLD = Color.parseColor("#FFE066");
    public static final int TEXT = Color.parseColor("#F1F7F6");
    public static final int TEXT_DIM = Color.parseColor("#B1C5C8");

    /** Themes: id, background, surface, raised surface, accent, light accent. */
    public static final String[][] THEMES = {
        { "esencia",   "#0B1B21", "#142B32", "#23424A", "#117E78", "#8BE0CC" },
        { "oceano",    "#0D1A2B", "#14273E", "#1E3757", "#2F8CFF", "#86BFFF" },
        { "bosque",    "#0E1C16", "#162A20", "#213D2E", "#2EBD6B", "#86E3AE" },
        { "fuego",     "#1D1111", "#2A1919", "#3E2323", "#F0533A", "#FF9C88" },
        { "electrico", "#1A1808", "#26230F", "#383316", "#E8B500", "#FFE066" },
        { "rosa",      "#1E1220", "#2C1A2F", "#402544", "#E5489A", "#FF94C8" },
        { "noche",     "#000000", "#101014", "#1C1C24", "#117E78", "#8BE0CC" },
    };

    private static int sStamp = 0;
    private static String sTheme = null;

    /** Loads the chosen theme (cheap: only when it changed). */
    public static void applyTheme(Context c)
    {
        String id = c.getSharedPreferences("essentia", Context.MODE_PRIVATE).getString("theme", "esencia");
        if (id.equals(sTheme)) return;
        for (String[] t : THEMES) {
            if (!t[0].equals(id)) continue;
            BG = Color.parseColor(t[1]);
            SURFACE = Color.parseColor(t[2]);
            SURFACE_HI = Color.parseColor(t[3]);
            ACCENT = Color.parseColor(t[4]);
            ACCENT_LIGHT = Color.parseColor(t[5]);
        }
        sTheme = id;
        sStamp++;
    }

    public static void setTheme(Context c, String id)
    {
        c.getSharedPreferences("essentia", Context.MODE_PRIVATE).edit().putString("theme", id).commit();
        applyTheme(c);
    }

    public static String theme(Context c)
    {
        return c.getSharedPreferences("essentia", Context.MODE_PRIVATE).getString("theme", "esencia");
    }

    /** Changes every time a new theme is loaded (screens compare it to rebuild). */
    public static int themeStamp() { return sStamp; }

    /** A color with another opacity (0..255). */
    public static int alpha(int color, int a)
    {
        return (color & 0x00FFFFFF) | (a << 24);
    }

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
        b.setContentDescription(text);
        int h = dp(c, 16), v = dp(c, 10);
        b.setPadding(h, v, h, v);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
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
        t.setFontFeatureSettings("kern");
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static ColorStateList tint(int color)
    {
        return ColorStateList.valueOf(color);
    }
}
