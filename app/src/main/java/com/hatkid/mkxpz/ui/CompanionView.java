package com.hatkid.mkxpz.ui;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The second screen while playing (dual-screen devices such as the AYN Thor).
 *
 * "panel": a live map of where you are (Pokémon Essentials region map), quick game
 * actions (team, bag, Pokédex, quick save, turbo, screenshot), the keys a controller
 * doesn't have and a few cheats. "black": a dark screen. It never takes the
 * controller away from the game.
 */
public final class CompanionView extends FrameLayout
{
    public interface Keys { void down(int keycode); void up(int keycode); }

    public interface Actions
    {
        void controls();
        void notes();
        void progress();
        void menu();
        /** A quick action for the game (see essentia_actions.rb). */
        void game(String command);
        void turbo();
        void autoFire();
        void keyboard();
    }

    private static final int PAGE_SUMMARY=0, PAGE_MAP=1, PAGE_NOTES=2, PAGE_ACTIONS=3, PAGE_KEYS=4, PAGE_CHEATS=5;

    private final boolean mBlack;
    private final String mTitle;
    private final File mGameDir;
    private final File mLiveFile;
    private final GamePanel.Keys mKeys;
    private final GamePanel.Actions mActions;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private LiveMapView mMap;
    private TextView mPlace;
    private TextView mProgress;
    private TextView mNotes;
    private TextView mMessage;
    private TextView mTurboChip;
    private Tile mTurboTile;
    private Tile mAutoTile;
    private FrameLayout mPages;
    private final View[] mPageViews = new View[6];
    private final TextView[] mTabs = new TextView[6];
    private final Map<String, Tile> mToggles = new HashMap<>();
    private String mLastLine = null;
    private volatile boolean mStopped;
    private volatile boolean mReadPending;
    private final java.util.concurrent.ExecutorService mReader = java.util.concurrent.Executors.newSingleThreadExecutor();
    private String mLoadedImage = null;
    private long mStart;
    private boolean mTurbo = false;
    private int mTurboSpeed = 3;

    private final Runnable mPoll = new Runnable() {
        @Override public void run() {
            if (mStopped) return;
            if (!mReadPending) {
                mReadPending = true;
                try { mReader.execute(() -> { try { readLive(); } finally { mReadPending = false; } }); }
                catch(java.util.concurrent.RejectedExecutionException ignored){mReadPending=false;}
            }
            if (mNotes != null) {
                String notes=getContext().getSharedPreferences("essentia_notes",Context.MODE_PRIVATE).getString(mGameDir.getAbsolutePath(),"");
                mNotes.setText(notes.isEmpty()?"Aún no tienes notas. Usa Editar para anotar tu próximo objetivo.":notes);
            }
            mHandler.postDelayed(this, 500);
        }
    };

    private final Runnable mHideMessage = () -> {
        if (mMessage != null) mMessage.setVisibility(View.GONE);
    };

    public CompanionView(Context c, boolean black, String title, String gamePath,
                         File liveFile, GamePanel.Keys keys, GamePanel.Actions actions) {
        super(c);mBlack=black;mTitle=title;mGameDir=new File(gamePath);
        mLiveFile=liveFile;mKeys=keys;mActions=actions;buildContent();
    }

    // ------------------------------------------------------------ layout

    private void buildContent()
    {
        Context c = getContext();
        Ui.applyTheme(c);

        FrameLayout root = new FrameLayout(c);
        if (mBlack) {
            root.setBackgroundColor(0xFF000000);
            addView(root, new FrameLayout.LayoutParams(-1,-1));
            return;
        }
        root.setBackground(Ui.backdrop());
        LinearLayout col=new LinearLayout(c);col.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(c,12);col.setPadding(pad,pad,pad,pad);
        col.addView(buildHeader(c));
        col.addView(buildTabs(c),new LinearLayout.LayoutParams(-1,Ui.dp(c,56)));
        mPages=new FrameLayout(c);
        LinearLayout summary=new LinearLayout(c);summary.setOrientation(LinearLayout.VERTICAL);
        mProgress=Ui.text(c,"Equipo y progreso\nAbre tu partida para consultar datos compatibles.",16,Ui.TEXT,false);
        mProgress.setPadding(pad,pad,pad,pad);mProgress.setBackground(Ui.rounded(c,Ui.pastel(1),20));
        summary.addView(mProgress);
        TextView status=Ui.text(c,"Solo datos leídos del juego. Si no hay conexión compatible, puedes usar tus notas y los controles.",14,Ui.TEXT_DIM,false);
        status.setPadding(0,pad,0,pad);summary.addView(status);
        android.widget.Button progress=Ui.button(c,"Ver última lectura guardada",false);
        progress.setOnClickListener(v->mActions.progress());summary.addView(progress);
        android.widget.Button controls=Ui.button(c,"Ver controles del juego",false);
        controls.setOnClickListener(v->mActions.controls());summary.addView(controls);
        mPageViews[PAGE_SUMMARY]=scroll(c,summary);
        mPageViews[PAGE_MAP]=buildMapCard(c);
        LinearLayout notes=new LinearLayout(c);notes.setOrientation(LinearLayout.VERTICAL);
        notes.addView(Ui.text(c,"Tu cuaderno de aventura",22,Ui.TEXT,true));
        notes.addView(Ui.text(c,"Objetivos, lugares y recordatorios. Se guardan en este dispositivo para este juego.",14,Ui.TEXT_DIM,false));
        mNotes=Ui.text(c,c.getSharedPreferences("essentia_notes",0).getString(mGameDir.getAbsolutePath(),"Aún no tienes notas."),17,Ui.TEXT,false);mNotes.setPadding(pad,pad,pad,pad);
        mNotes.setBackground(Ui.rounded(c,Ui.pastel(2),20));notes.addView(mNotes);
        android.widget.Button edit=Ui.button(c,"Editar mis notas",true);edit.setOnClickListener(v->mActions.notes());notes.addView(edit);
        mPageViews[PAGE_NOTES]=scroll(c,notes);
        mPageViews[PAGE_ACTIONS]=buildActions(c);
        mPageViews[PAGE_KEYS]=buildKeys(c);
        mPageViews[PAGE_CHEATS]=buildCheats(c);
        for(View view:mPageViews)mPages.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams pages=new LinearLayout.LayoutParams(-1,0,1f);pages.topMargin=pad;col.addView(mPages,pages);
        mMessage=Ui.text(c,"",14,Ui.TEXT,true);mMessage.setPadding(pad,pad,pad,pad);
        mMessage.setBackground(Ui.rounded(c,Ui.SURFACE_HI,12));mMessage.setVisibility(View.GONE);col.addView(mMessage);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        addView(root, new FrameLayout.LayoutParams(-1,-1));
        showPage(PAGE_SUMMARY);
        setTurbo(mTurbo, mTurboSpeed);

        mStart = System.currentTimeMillis();
        mHandler.post(mPoll);
    }

    private View buildHeader(Context c)
    {
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView flask = new ImageView(c);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        int fs = Ui.dp(c, 38);
        header.addView(flask, new LinearLayout.LayoutParams(fs, fs));

        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 8), 0);
        TextView name = Ui.text(c, mTitle, 17, Ui.TEXT, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name);
        mPlace = Ui.text(c, c.getString(R.string.panel_map_waiting), 13, Ui.TEXT_DIM, false);
        mPlace.setMaxLines(1);
        mPlace.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(mPlace);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mTurboChip = Ui.text(c, "", 13, Ui.INK, true);
        mTurboChip.setBackground(Ui.rounded(c, Ui.GOLD, 10));
        mTurboChip.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        mTurboChip.setVisibility(View.GONE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = Ui.dp(c, 8);
        header.addView(mTurboChip, clp);

        TextClock clock = new TextClock(c);
        clock.setTextColor(Ui.TEXT);
        clock.setTextSize(16);
        header.addView(clock);
        return header;
    }

    private View buildMapCard(Context c)
    {
        FrameLayout card = new FrameLayout(c);
        GradientDrawable bg = Ui.rounded(c, Ui.SURFACE, 16);
        bg.setStroke(Ui.dp(c, 1), Ui.SURFACE_HI);
        card.setBackground(bg);
        card.setClipToOutline(true);

        mMap = new LiveMapView(c);
        mMap.setEmptyText(c.getString(R.string.panel_map_waiting));
        mMap.setOnClickListener(v -> mMap.setZoom(!mMap.isZoom()));
        card.addView(mMap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView zoom = new ImageView(c);
        zoom.setImageResource(R.drawable.ic_p_zoom);
        zoom.setBackground(Ui.rounded(c, Ui.alpha(Ui.BG, 0xAA), 14));
        int zp = Ui.dp(c, 6);
        zoom.setPadding(zp, zp, zp, zp);
        zoom.setOnClickListener(v -> mMap.setZoom(!mMap.isZoom()));
        int zs = Ui.dp(c, 34);
        FrameLayout.LayoutParams zlp = new FrameLayout.LayoutParams(zs, zs, Gravity.TOP | Gravity.END);
        zlp.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
        card.addView(zoom, zlp);

        return card;
    }

    private View buildActions(Context c)
    {
        GridLayout g = grid(c, 3);
        addTile(g, R.drawable.ic_p_party, R.string.act_party, false, v -> mActions.game("party"));
        addTile(g, R.drawable.ic_p_bag, R.string.act_bag, false, v -> mActions.game("bag"));
        addTile(g, R.drawable.ic_p_dex, R.string.act_dex, false, v -> mActions.game("dex"));
        addTile(g, R.drawable.ic_p_save, R.string.act_save, false, v -> mActions.game("save"));
        mTurboTile = addTile(g, R.drawable.ic_p_turbo, R.string.act_turbo, false, v -> mActions.turbo());
        addTile(g, R.drawable.ic_p_shot, R.string.act_shot, false, v -> mActions.game("shot"));
        return scroll(c,g);
    }

    private View buildKeys(Context c)
    {
        GridLayout g = grid(c, 3);
        keyTile(g, R.string.panel_menu, KeyEvent.KEYCODE_ESCAPE, true);
        keyTile(g, R.string.panel_enter, KeyEvent.KEYCODE_ENTER, true);
        keyTile(g, R.string.panel_shift, KeyEvent.KEYCODE_SHIFT_LEFT, false);
        keyTile(g, R.string.panel_key_a, KeyEvent.KEYCODE_A, false);
        keyTile(g, R.string.panel_key_d, KeyEvent.KEYCODE_D, false);
        keyTile(g, R.string.panel_f1, KeyEvent.KEYCODE_F1, false);
        keyTile(g, R.string.panel_ctrl, KeyEvent.KEYCODE_CTRL_LEFT, false);
        mAutoTile = addTile(g, R.drawable.ic_p_auto, R.string.btn_autofire_short, false, v -> mActions.autoFire());
        addTile(g, R.drawable.ic_p_keys, R.string.btn_keyboard, false, v -> mActions.keyboard());
        return scroll(c,g);
    }

    private View buildCheats(Context c)
    {
        GridLayout g = grid(c, 2);
        mToggles.put("through", addTile(g, R.drawable.ic_p_walls, R.string.act_through, false,
            v -> mActions.game("through")));
        mToggles.put("noenc", addTile(g, R.drawable.ic_p_noenc, R.string.act_noenc, false,
            v -> mActions.game("noenc")));
        addTile(g, R.drawable.ic_p_heal, R.string.act_heal, false, v -> mActions.game("heal"));
        addTile(g, R.drawable.ic_p_money, R.string.act_money, false, v -> mActions.game("money"));
        addTile(g, R.drawable.ic_p_bag, R.string.act_candy, false, v -> mActions.game("candy"));
        addTile(g, R.drawable.ic_p_party, R.string.act_testpokemon, false, v -> mActions.game("testpokemon"));
        for (int i=0;i<g.getChildCount();i++) {
            GridLayout.LayoutParams lp=(GridLayout.LayoutParams)g.getChildAt(i).getLayoutParams();
            lp.height=Ui.dp(c,72);lp.rowSpec=GridLayout.spec(GridLayout.UNDEFINED);
            g.getChildAt(i).setLayoutParams(lp);
        }
        ScrollView scroll=new ScrollView(c);scroll.addView(g,new ScrollView.LayoutParams(-1,-2));
        return scroll;
    }

    private View scroll(Context c, View content) {
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.addView(content);return scroll;
    }

    private View buildTabs(Context c) {
        HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels={"Resumen","Mapa","Notas","Acciones","Teclas","Ayudas"};
        for(int i=0;i<labels.length;i++) {
            final int page=i;TextView tab=Ui.text(c,labels[i],14,Ui.TEXT,true);
            tab.setGravity(Gravity.CENTER);tab.setFocusable(true);tab.setClickable(true);
            tab.setPadding(Ui.dp(c,16),0,Ui.dp(c,16),0);tab.setOnClickListener(v->showPage(page));
            mTabs[i]=tab;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(c,48));
            lp.rightMargin=Ui.dp(c,6);row.addView(tab,lp);
        }
        scroll.addView(row);return scroll;
    }

    private void showPage(int page) {
        for(int i=0;i<mPageViews.length;i++) {
            mPageViews[i].setVisibility(i==page?View.VISIBLE:View.GONE);
            mTabs[i].setSelected(i==page);mTabs[i].setTextColor(i==page?Ui.TEXT:Ui.TEXT_DIM);
            mTabs[i].setBackground(Ui.focusable(getContext(),i==page?Ui.SURFACE_HI:Ui.SURFACE,12));
        }
    }

    // ------------------------------------------------------------ tiles

    private static final class Tile
    {
        LinearLayout view;
        ImageView icon;
        TextView label;
        int baseColor;
    }

    private GridLayout grid(Context c, int columns)
    {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(columns);
        return g;
    }

    private GridLayout.LayoutParams cell(Context c)
    {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = Ui.dp(c,88);
        int m = Ui.dp(c, 4);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private Tile addTile(GridLayout g, int iconRes, int labelRes, boolean on, View.OnClickListener l)
    {
        Context c = getContext();
        Tile t = new Tile();
        t.baseColor=Ui.pastel(g.getChildCount()%4);
        t.view = new LinearLayout(c);
        t.view.setOrientation(LinearLayout.VERTICAL);
        t.view.setGravity(Gravity.CENTER);
        t.icon = new ImageView(c);
        t.icon.setImageResource(iconRes);
        int s = Ui.dp(c, 30);
        t.view.addView(t.icon, new LinearLayout.LayoutParams(s, s));
        t.label = Ui.text(c, c.getString(labelRes), 13, Ui.TEXT, true);
        t.label.setGravity(Gravity.CENTER);
        t.label.setPadding(0, Ui.dp(c, 4), 0, 0);
        t.label.setMaxLines(2);
        t.label.setEllipsize(TextUtils.TruncateAt.END);
        t.view.addView(t.label);
        t.view.setFocusable(true);
        t.view.setClickable(true);
        t.view.setOnClickListener(l);
        setTileOn(t, on);
        g.addView(t.view, cell(c));
        return t;
    }

    private void setTileOn(Tile t, boolean on)
    {
        Context c = getContext();
        t.view.setBackground(Ui.focusable(c, on ? Ui.GOLD : t.baseColor, 18));
        int color = on ? Ui.INK : Ui.TEXT;
        t.label.setTextColor(color);
        t.icon.setColorFilter(color);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void keyTile(GridLayout grid, int labelRes, int keycode, boolean primary)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 16, primary?Ui.ON_ACCENT:Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.focusable(c, primary ? Ui.ACCENT : Ui.pastel(grid.getChildCount()%4), 18));
        b.setFocusable(true);b.setClickable(true);
        b.setOnClickListener(v->{mKeys.down(keycode);mKeys.up(keycode);});
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mKeys.down(keycode);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mKeys.up(keycode);
                    return true;
            }
            return true;
        });
        grid.addView(b, cell(c));
    }

    // ------------------------------------------------------------ state from the game

    public void setTurbo(boolean on, int speed)
    {
        mTurbo = on;
        mTurboSpeed = speed;
        if (mTurboTile == null) return;
        setTileOn(mTurboTile, on);
        String label = getContext().getString(R.string.act_turbo) + (on ? " x" + speed : "");
        mTurboTile.label.setText(label);
        mTurboChip.setText("▶▶ x" + speed);
        mTurboChip.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    public void setAutoFire(boolean on)
    {
        if (mAutoTile != null) setTileOn(mAutoTile, on);
    }

    /** Cheat toggles ("through", "noenc") confirmed by the game. */
    public void setToggle(String what, boolean on)
    {
        Tile t = mToggles.get(what);
        if (t != null) setTileOn(t, on);
    }

    /** A short message over the map ("Saved", "Screenshot saved"...). */
    public void showMessage(String text)
    {
        if (mMessage == null) return;
        mMessage.setText(text);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.animate().cancel();
        mMessage.setAlpha(1f);
        mHandler.removeCallbacks(mHideMessage);
        mHandler.postDelayed(mHideMessage, 2600);
    }

    public void dispose()
    {
        mStopped = true;
        mReader.shutdownNow();
        mHandler.removeCallbacks(mPoll);
        mHandler.removeCallbacks(mHideMessage);
    }

    @Override protected void onDetachedFromWindow() {dispose();super.onDetachedFromWindow();}

    // ------------------------------------------------------------ live map

    /** Fields: map id, x, y, map width, height, region, square x, square y, image, name. */
    private void readLive()
    {
        if (mMap == null) return;
        String line;
        try {
            if (!mLiveFile.isFile()) {
                if (System.currentTimeMillis() - mStart > 20000)
                    mHandler.post(() -> { if (!mStopped) {
                        mMap.setEmptyText(getContext().getString(R.string.panel_map_none));
                        mPlace.setText("Sin datos compatibles en esta sesión");
                        mProgress.setText("Equipo y progreso no disponibles en esta sesión.\nTus notas, teclas y controles siguen disponibles.");
                    } });
                return;
            }
            if (mLiveFile.length() > 16384) return;
            try (FileInputStream in = new FileInputStream(mLiveFile)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 16384) return;
                    out.write(buffer, 0, n);
                }
                line = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return;
        }
        final String result = line;
        mHandler.post(() -> { if (!mStopped) applyLive(result); });
    }

    private void applyLive(String line)
    {
        if (line.equals(mLastLine)) return;
        mLastLine = line;

        String[] f = line.split("\t", -1);
        if (f.length < 10) return;
        String name = f[9].trim();
        mPlace.setText(name.isEmpty() ? getContext().getString(R.string.panel_map_position,
            parse(f[1]), parse(f[2])) : name);

        if (f.length >= 14 && mProgress != null) {
            String team = f[10].isEmpty() ? "Equipo no disponible" : f[10];
            mProgress.setText(team + "\nMedallas: " + (f[11].isEmpty() ? "—" : f[11])
                + " · Pokédex: " + (f[12].isEmpty() ? "—" : f[12]) + " capturados / "
                + (f[13].isEmpty() ? "—" : f[13]) + " vistos");
        }
        String image = f[8].trim();
        int sx = parse(f[6]), sy = parse(f[7]);
        if (image.isEmpty() || sx < 0) {
            mLoadedImage = null;
            mMap.setMap(null);
            mMap.setEmptyText(getContext().getString(R.string.panel_map_none));
            return;
        }
        mMap.setPlayerSquare(sx, sy);
        if (!image.equals(mLoadedImage)) {
            mLoadedImage = image;
            new Thread(() -> {
                Bitmap bmp = loadImage(image);
                mHandler.post(() -> {
                    if (!mStopped && image.equals(mLoadedImage) && mMap != null) {
                        mMap.setMap(bmp);
                        if (bmp == null) mMap.setEmptyText(getContext().getString(R.string.panel_map_none));
                    }
                });
            }, "EssentiaMapImage").start();
        }
    }

    private static int parse(String s)
    {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    /** Finds the game's image (any letter case, with or without extension). */
    private Bitmap loadImage(String relative)
    {
        File f = mGameDir;
        String[] parts = relative.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length && f != null; i++) {
            f = findChild(f, parts[i], i == parts.length - 1);
        }
        if (f == null || !f.isFile()) return null;
        try {
            if (!f.getCanonicalPath().startsWith(mGameDir.getCanonicalPath() + File.separator)) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), options);
            int sample = 1;
            while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) sample *= 2;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static File findChild(File dir, String name, boolean isImage)
    {
        String[] names = dir.list();
        if (names == null) return null;
        String want = name.toLowerCase(Locale.ROOT);
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            if (low.equals(want)) return new File(dir, n);
            if (isImage && (low.equals(want + ".png") || low.equals(want + ".jpg"))) return new File(dir, n);
        }
        return null;
    }
}
