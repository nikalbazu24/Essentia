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
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
public final class GamePanel extends Presentation
{
    public interface Keys { void down(int keycode); void up(int keycode); }

    public interface Actions
    {
        void controls();
        void menu();
        /** A quick action for the game (see essentia_actions.rb). */
        void game(String command);
        void turbo();
        void autoFire();
        void keyboard();
    }

    private static final int PAGE_ACTIONS = 0, PAGE_KEYS = 1, PAGE_CHEATS = 2;

    private final boolean mBlack;
    private final String mTitle;
    private final File mGameDir;
    private final File mLiveFile;
    private final Keys mKeys;
    private final Actions mActions;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private LiveMapView mMap;
    private TextView mPlace;
    private TextView mMessage;
    private TextView mTurboChip;
    private Tile mTurboTile;
    private Tile mAutoTile;
    private FrameLayout mPages;
    private final View[] mPageViews = new View[3];
    private final TextView[] mTabs = new TextView[3];
    private final Map<String, Tile> mToggles = new HashMap<>();
    private String mLastLine = null;
    private String mLoadedImage = null;
    private long mStart;
    private boolean mTurbo = false;
    private int mTurboSpeed = 3;

    private final Runnable mPoll = new Runnable() {
        @Override public void run() {
            readLive();
            mHandler.postDelayed(this, 500);
        }
    };

    private final Runnable mHideMessage = () -> {
        if (mMessage != null) mMessage.animate().alpha(0f).setDuration(250).start();
    };

    private GamePanel(Context outer, Display display, boolean black, String title, String gamePath,
                      File liveFile, Keys keys, Actions actions)
    {
        super(outer, display);
        mBlack = black;
        mTitle = title;
        mGameDir = new File(gamePath);
        mLiveFile = liveFile;
        mKeys = keys;
        mActions = actions;
    }

    public static GamePanel show(Context c, Display current, boolean black, String title, String gamePath,
                                 File liveFile, Keys keys, Actions actions)
    {
        Display other = SecondScreen.findOtherDisplay(c, current);
        if (other == null) return null;
        try {
            GamePanel p = new GamePanel(c, other, black, title, gamePath, liveFile, keys, actions);
            p.show();
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ layout

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SecondScreen.notFocusable(this);
        Context c = getContext();
        Ui.applyTheme(c);

        FrameLayout root = new FrameLayout(c);
        if (mBlack) {
            root.setBackgroundColor(0xFF000000);
            setContentView(root);
            return;
        }
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xFF1B1E3D, Ui.BG, 0xFF0E1022 }));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 12);
        col.setPadding(pad, pad, pad, pad);

        col.addView(buildHeader(c));

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.25f);
        mlp.topMargin = Ui.dp(c, 10);
        col.addView(buildMapCard(c), mlp);

        mPages = new FrameLayout(c);
        mPageViews[PAGE_ACTIONS] = buildActions(c);
        mPageViews[PAGE_KEYS] = buildKeys(c);
        mPageViews[PAGE_CHEATS] = buildCheats(c);
        for (View v : mPageViews) mPages.addView(v, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        plp.topMargin = Ui.dp(c, 8);
        col.addView(mPages, plp);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 48));
        tlp.topMargin = Ui.dp(c, 8);
        col.addView(buildTabs(c), tlp);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        showPage(PAGE_ACTIONS);
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

        mTurboChip = Ui.text(c, "", 13, Ui.BG, true);
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
        GradientDrawable bg = Ui.rounded(c, 0xFF101326, 20);
        bg.setStroke(Ui.dp(c, 1), 0x33B48CFF);
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

        mMessage = Ui.text(c, "", 14, Ui.BG, true);
        mMessage.setGravity(Gravity.CENTER);
        mMessage.setBackground(Ui.rounded(c, 0xF2F3EEFE, 14));
        mMessage.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
        mMessage.setAlpha(0f);
        FrameLayout.LayoutParams msg = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        msg.bottomMargin = Ui.dp(c, 10);
        card.addView(mMessage, msg);
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
        return g;
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
        return g;
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
        return g;
    }

    private View buildTabs(Context c)
    {
        LinearLayout row = new LinearLayout(c);
        row.setBackground(Ui.rounded(c, 0xFF101326, 16));
        int p = Ui.dp(c, 4);
        row.setPadding(p, p, p, p);
        int[] labels = { R.string.panel_tab_actions, R.string.panel_tab_keys, R.string.panel_tab_cheats };
        for (int i = 0; i < 3; i++) {
            final int page = i;
            TextView t = Ui.text(c, c.getString(labels[i]), 14, Ui.TEXT_DIM, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> showPage(page));
            mTabs[i] = t;
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        ImageView menu = new ImageView(c);
        menu.setImageResource(R.drawable.ic_p_menu);
        menu.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        menu.setBackground(Ui.focusable(c, Ui.SURFACE_HI, 12));
        menu.setOnClickListener(v -> mActions.menu());
        row.addView(menu, new LinearLayout.LayoutParams(Ui.dp(c, 56), ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    private void showPage(int page)
    {
        for (int i = 0; i < 3; i++) {
            mPageViews[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
            mTabs[i].setTextColor(i == page ? Ui.TEXT : Ui.TEXT_DIM);
            mTabs[i].setBackground(i == page ? Ui.rounded(getContext(), Ui.ACCENT, 12) : null);
        }
    }

    // ------------------------------------------------------------ tiles

    private static final class Tile
    {
        LinearLayout view;
        ImageView icon;
        TextView label;
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
            GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = 0;
        int m = Ui.dp(c, 4);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private Tile addTile(GridLayout g, int iconRes, int labelRes, boolean on, View.OnClickListener l)
    {
        Context c = getContext();
        Tile t = new Tile();
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
        t.label.setMaxLines(1);
        t.label.setEllipsize(TextUtils.TruncateAt.END);
        t.view.addView(t.label);
        t.view.setClickable(true);
        t.view.setOnClickListener(l);
        setTileOn(t, on);
        g.addView(t.view, cell(c));
        return t;
    }

    private void setTileOn(Tile t, boolean on)
    {
        Context c = getContext();
        t.view.setBackground(Ui.focusable(c, on ? Ui.GOLD : Ui.SURFACE_HI, 18));
        int color = on ? Ui.BG : Ui.TEXT;
        t.label.setTextColor(color);
        t.icon.setColorFilter(color);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void keyTile(GridLayout grid, int labelRes, int keycode, boolean primary)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 16, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.focusable(c, primary ? Ui.ACCENT : Ui.SURFACE_HI, 18));
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
        mMessage.animate().cancel();
        mMessage.setAlpha(1f);
        mHandler.removeCallbacks(mHideMessage);
        mHandler.postDelayed(mHideMessage, 2600);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mHandler.removeCallbacks(mPoll);
        mHandler.removeCallbacks(mHideMessage);
    }

    // ------------------------------------------------------------ live map

    /** Fields: map id, x, y, map width, height, region, square x, square y, image, name. */
    private void readLive()
    {
        if (mMap == null) return;
        String line;
        try {
            if (!mLiveFile.isFile()) {
                if (System.currentTimeMillis() - mStart > 20000)
                    mMap.setEmptyText(getContext().getString(R.string.panel_map_none));
                return;
            }
            line = new String(Files.readAllBytes(mLiveFile.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }
        if (line.equals(mLastLine)) return;
        mLastLine = line;

        String[] f = line.split("\t", -1);
        if (f.length < 10) return;
        String name = f[9].trim();
        mPlace.setText(name.isEmpty() ? getContext().getString(R.string.panel_map_position,
            parse(f[1]), parse(f[2])) : name);

        String image = f[8].trim();
        int sx = parse(f[6]), sy = parse(f[7]);
        if (image.isEmpty() || sx < 0) {
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
                    if (mMap != null) {
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
            return BitmapFactory.decodeFile(f.getAbsolutePath());
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
