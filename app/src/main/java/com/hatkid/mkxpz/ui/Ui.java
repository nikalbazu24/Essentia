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
    public static int BG = Color.parseColor("#EEF0FC");
    public static int SURFACE = Color.parseColor("#FFFFFF");
    public static int SURFACE_HI = Color.parseColor("#E6E0FA");
    public static int ACCENT = Color.parseColor("#6935DB");
    public static int ACCENT_LIGHT = Color.parseColor("#5630BB");
    public static final int GOLD = Color.parseColor("#FFE066");
    public static int TEXT = Color.parseColor("#272332");
    public static final int INK=Color.parseColor("#272332");
    public static int ON_ACCENT=Color.WHITE;
    public static boolean LIGHT=true;
    public static int TEXT_DIM = Color.parseColor("#6C657B");

    /** Themes: id, background, surface, raised surface, accent, light accent. */
    public static final String[][] THEMES = {
        { "esencia",   "#EEF0FC", "#FFFFFF", "#E6E0FA", "#6935DB", "#5630BB" },
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
        LIGHT="esencia".equals(id);
        TEXT=Color.parseColor(LIGHT?"#272332":"#F1F7F6");
        TEXT_DIM=Color.parseColor(LIGHT?"#6C657B":"#B1C5C8");
        ON_ACCENT=contrast(ACCENT);
        sTheme = id;
        sStamp++;
    }

    public static int contrast(int color) {
        double[] rgb={Color.red(color)/255.0,Color.green(color)/255.0,Color.blue(color)/255.0};
        for(int i=0;i<3;i++)rgb[i]=rgb[i]<=0.04045?rgb[i]/12.92:Math.pow((rgb[i]+0.055)/1.055,2.4);
        return 0.2126*rgb[0]+0.7152*rgb[1]+0.0722*rgb[2]>0.179?INK:Color.WHITE;
    }
    public static Drawable backdrop() {
        return LIGHT?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFFE9E1FF,0xFFF4F5FD,0xFFDDF5F1}):new android.graphics.drawable.ColorDrawable(BG);
    }
    public static int pastel(int index) {
        if(!LIGHT)return SURFACE;
        int[] colors={0xFFE9DFFF,0xFFD5F2ED,0xFFFFE0E9,0xFFDBE9FF};return colors[Math.abs(index)%colors.length];
    }
    public static int dialogTheme(){return LIGHT?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert;}

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
        if(color==SURFACE||color==SURFACE_HI) {
            d.setOrientation(GradientDrawable.Orientation.TL_BR);
            d.setColors(new int[]{alpha(color,LIGHT?245:240),alpha(color,LIGHT?206:218)});
            d.setStroke(dp(c,1),LIGHT?0xE6FFFFFF:0x506E7C90);
        }else d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));

        return d;
    }

    /** Background that shows a bright outline when focused (controller) or pressed (touch). */
    public static Drawable focusable(Context c, int color, float radiusDp)
    {
        GradientDrawable normal = rounded(c, color, radiusDp);

        GradientDrawable focused = rounded(c, color, radiusDp);
        focused.setStroke(dp(c, 3), ACCENT_LIGHT);

        GradientDrawable pressed = rounded(c, color, radiusDp);
        pressed.setStroke(dp(c,2),ACCENT_LIGHT);

        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable selected=rounded(c,color==ACCENT?ACCENT:SURFACE_HI,radiusDp);selected.setStroke(dp(c,1),alpha(ACCENT_LIGHT,0x99));
        s.addState(new int[]{android.R.attr.state_selected}, selected);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static android.widget.EditText input(Context c) {
        android.widget.EditText input=new android.widget.EditText(c);
        input.setTextColor(TEXT);input.setHintTextColor(TEXT_DIM);input.setTextSize(16);
        input.setBackground(focusable(c,SURFACE_HI,12));int p=dp(c,14);input.setPadding(p,p,p,p);
        input.setMinHeight(dp(c,48));return input;
    }

    public static Button button(Context c, String text, boolean primary)
    {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary?ON_ACCENT:TEXT);
        b.setBackground(new android.graphics.drawable.RippleDrawable(tint(alpha(ACCENT_LIGHT,45)),focusable(c, primary ? ACCENT : SURFACE, 16),rounded(c,Color.WHITE,16)));
        b.setStateListAnimator(null);
        b.setContentDescription(text);
        int h = dp(c, 16), v = dp(c, 10);
        b.setPadding(h, v, h, v);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setFocusable(true);
        return b;
    }

    public static int tone(int i) {
        int[] colors={0xFF6733D6,0xFF007D7A,0xFFB93365,0xFF2662CC};
        return colors[(i & 0x7fffffff)%colors.length];
    }

    /** A vivid feature surface; static drawing, no animation loop during play. */
    public static Drawable feature(Context c) {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint=new android.graphics.Paint(3);
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b=getBounds();float r=dp(c,24);
                paint.setShader(new android.graphics.LinearGradient(b.left,b.top,b.right,b.bottom,
                    LIGHT?0xFF28194F:BG,LIGHT?0xFF7544CA:ACCENT,android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRoundRect(new android.graphics.RectF(b),r,r,paint);paint.setShader(null);
                int saved=canvas.save();android.graphics.Path path=new android.graphics.Path();
                path.addRoundRect(new android.graphics.RectF(b),r,r,android.graphics.Path.Direction.CW);canvas.clipPath(path);
                paint.setColor(0x246FE8D7);canvas.drawCircle(b.right-dp(c,25),b.top+dp(c,16),dp(c,92),paint);
                paint.setColor(0x18FFFFFF);canvas.drawCircle(b.right-dp(c,60),b.bottom+dp(c,80),dp(c,140),paint);
                canvas.restoreToCount(saved);
            }
            public void setAlpha(int a){} public void setColorFilter(android.graphics.ColorFilter f){}
            public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }

    public static android.view.View brand(Context c,int size) {
        android.widget.FrameLayout badge=new android.widget.FrameLayout(c);badge.setBackground(feature(c));
        badge.setContentDescription("Essentia");badge.setTag("essentia-brand");
        android.widget.ImageView mark=new android.widget.ImageView(c);mark.setImageResource(com.hatkid.mkxpz.R.drawable.ic_essentia_mark);
        int p=dp(c,5);mark.setPadding(p,p,p,p);badge.addView(mark,new android.widget.FrameLayout.LayoutParams(-1,-1));
        badge.setMinimumWidth(dp(c,size));badge.setMinimumHeight(dp(c,size));return badge;
    }

    public static android.widget.ImageView badge(Context c,int icon,int color) {
        android.widget.ImageView v=new android.widget.ImageView(c);v.setImageResource(icon);v.setColorFilter(Color.WHITE);
        v.setBackground(rounded(c,color,13));int p=dp(c,9);v.setPadding(p,p,p,p);return v;
    }

    public static Button button(Context c,String label,boolean primary,int icon) {
        Button b=button(c,label,primary);icon(b,icon,primary?ON_ACCENT:ACCENT_LIGHT);return b;
    }

    public static void icon(TextView v,int resource,int color) {
        Drawable d=v.getContext().getDrawable(resource).mutate();d.setTint(color);int s=dp(v.getContext(),20);d.setBounds(0,0,s,s);
        v.setCompoundDrawablesRelative(d,null,null,null);v.setCompoundDrawablePadding(dp(v.getContext(),9));
    }

    public static android.view.View section(Context c,String title,int icon,int color) {
        android.widget.LinearLayout row=new android.widget.LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(badge(c,icon,color),new android.widget.LinearLayout.LayoutParams(dp(c,36),dp(c,36)));
        TextView t=text(c,title,19,TEXT,true);t.setPadding(dp(c,10),0,0,0);row.addView(t,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        return row;
    }

    /** Distinct original placeholders for games without artwork, never official covers. */
    public static Drawable coverPlaceholder(Context c,String name) {
        String cleaned=name.replaceAll("[_\\-]+"," ").trim();String[] words=cleaned.split("\\s+");
        String initials=cleaned.isEmpty()?"E":cleaned.substring(0,1);
        if(words.length>1&&!words[words.length-1].isEmpty())initials+=words[words.length-1].substring(0,1);
        final String letters=initials.toUpperCase(java.util.Locale.ROOT);final int color=tone(name.hashCode());
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint p=new android.graphics.Paint(3);
            public void draw(android.graphics.Canvas canvas){android.graphics.Rect b=getBounds();
                p.setShader(new android.graphics.LinearGradient(0,0,b.width(),b.height(),color,0xFF251A44,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(b,p);p.setShader(null);
                p.setColor(0x28FFFFFF);canvas.drawCircle(b.right,b.top,b.width()*.6f,p);canvas.drawCircle(b.left,b.bottom,b.width()*.35f,p);
                p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));p.setTextSize(Math.min(b.width(),b.height())*.37f);p.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText(letters,b.exactCenterX(),b.exactCenterY()-(p.ascent()+p.descent())/2,p);
            }
            public void setAlpha(int a){} public void setColorFilter(android.graphics.ColorFilter f){} public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
        };
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

    /** Grouped per-game actions; all operations keep their existing safety flows. */
    public static void gameOptions(android.app.Activity a,String title,java.util.List<String> labels,
                                   java.util.List<Runnable> actions,int[] groups) {
        android.app.Dialog dialog=new android.app.Dialog(a);
        android.widget.LinearLayout col=new android.widget.LinearLayout(a);col.setOrientation(1);
        int p=dp(a,16);col.setPadding(p,p,p,p);col.setBackground(rounded(a,SURFACE,20));
        TextView heading=text(a,title,22,TEXT,true);heading.setMaxLines(2);col.addView(heading);
        android.widget.HorizontalScrollView tabsScroll=new android.widget.HorizontalScrollView(a);tabsScroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout tabs=new android.widget.LinearLayout(a);tabsScroll.addView(tabs);col.addView(tabsScroll);
        android.widget.ScrollView scroll=new android.widget.ScrollView(a);
        android.widget.LinearLayout content=new android.widget.LinearLayout(a);content.setOrientation(1);scroll.addView(content);
        col.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1f));
        String[] names={"Jugar","Personalizar","Partidas","Gestionar"};
        for(int group=0;group<names.length;group++) {
            final int selected=group;Button tab=button(a,names[group],false,new int[]{com.hatkid.mkxpz.R.drawable.ic_e_play,com.hatkid.mkxpz.R.drawable.ic_e_settings,com.hatkid.mkxpz.R.drawable.ic_p_save,com.hatkid.mkxpz.R.drawable.ic_e_grid}[group]);
            android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(a,6);tabs.addView(tab,lp);
            tab.setOnClickListener(v->{
                for(int i=0;i<tabs.getChildCount();i++)tabs.getChildAt(i).setSelected(i==selected);
                content.removeAllViews();
                for(int i=0;i<labels.size();i++)if(groups[i]==selected){
                    final Runnable action=actions.get(i);Button item=button(a,labels.get(i),false,new int[]{com.hatkid.mkxpz.R.drawable.ic_e_play,com.hatkid.mkxpz.R.drawable.ic_e_settings,com.hatkid.mkxpz.R.drawable.ic_p_save,com.hatkid.mkxpz.R.drawable.ic_e_grid}[selected]);
                    item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setOnClickListener(x->{dialog.dismiss();action.run();});
                    android.widget.LinearLayout.LayoutParams row=new android.widget.LinearLayout.LayoutParams(-1,-2);row.topMargin=dp(a,8);content.addView(item,row);
                }
                scroll.scrollTo(0,0);
            });
        }
        Button close=button(a,"Cerrar",false);close.setOnClickListener(v->dialog.dismiss());col.addView(close);
        dialog.setContentView(col);
        dialog.setOnKeyListener((d,code,event)->{
            if(code==android.view.KeyEvent.KEYCODE_BUTTON_B){if(event.getAction()==android.view.KeyEvent.ACTION_UP)dialog.dismiss();return true;}
            if(code==android.view.KeyEvent.KEYCODE_BUTTON_A){dialog.getWindow().superDispatchKeyEvent(new android.view.KeyEvent(event.getDownTime(),event.getEventTime(),event.getAction(),android.view.KeyEvent.KEYCODE_DPAD_CENTER,event.getRepeatCount()));return true;}
            return false;
        });dialog.show();
        android.util.DisplayMetrics dm=a.getResources().getDisplayMetrics();
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(Math.min(dm.widthPixels-dp(a,24),dp(a,620)),Math.min(dm.heightPixels-dp(a,32),dp(a,620)));
        tabs.getChildAt(0).performClick();
    }

    public static ColorStateList tint(int color)
    {
        return ColorStateList.valueOf(color);
    }
}
