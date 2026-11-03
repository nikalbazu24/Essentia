package com.hatkid.mkxpz.ui;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * The second screen while playing (dual-screen devices such as the AYN Thor).
 *
 * "panel": a live map of where you are (Pokémon Essentials town map, read from what
 * essentia_live.rb writes), big touch keys the controller doesn't have and quick
 * actions. "black": a dark screen. It never takes the controller away from the game.
 */
public final class GamePanel extends Presentation
{
    public interface Keys { void down(int keycode); void up(int keycode); }
    public interface Actions { void controls(); void menu(); }

    private final boolean mBlack;
    private final String mTitle;
    private final File mGameDir;
    private final File mLiveFile;
    private final Keys mKeys;
    private final Actions mActions;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private LiveMapView mMap;
    private TextView mPlace;
    private String mLastLine = null;
    private String mLoadedImage = null;
    private long mStart;

    private final Runnable mPoll = new Runnable() {
        @Override public void run() {
            readLive();
            mHandler.postDelayed(this, 500);
        }
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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SecondScreen.notFocusable(this);
        Context c = getContext();

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(mBlack ? 0xFF000000 : Ui.BG);
        if (mBlack) {
            setContentView(root);
            return;
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        col.setPadding(pad, pad, pad, pad);

        // Header: game name + clock
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(c, mTitle, 17, Ui.TEXT, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextClock clock = new TextClock(c);
        clock.setTextColor(Ui.TEXT_DIM);
        clock.setTextSize(15);
        header.addView(clock);
        col.addView(header);

        // Live map card
        FrameLayout mapCard = new FrameLayout(c);
        mapCard.setBackground(Ui.rounded(c, Ui.SURFACE, 16));
        mapCard.setClipToOutline(true);
        mMap = new LiveMapView(c);
        mMap.setEmptyText(c.getString(R.string.panel_map_waiting));
        mapCard.addView(mMap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mPlace = Ui.text(c, "", 14, Ui.TEXT, true);
        mPlace.setBackground(Ui.rounded(c, 0xCC14162B, 10));
        mPlace.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        mPlace.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        plp.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0);
        mapCard.addView(mPlace, plp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f);
        mlp.topMargin = Ui.dp(c, 10);
        col.addView(mapCard, mlp);

        // Keys
        GridLayout keys = new GridLayout(c);
        keys.setColumnCount(3);
        keyButton(keys, R.string.panel_menu, KeyEvent.KEYCODE_ESCAPE, true);
        keyButton(keys, R.string.panel_enter, KeyEvent.KEYCODE_ENTER, true);
        keyButton(keys, R.string.panel_shift, KeyEvent.KEYCODE_SHIFT_LEFT, false);
        keyButton(keys, R.string.panel_f1, KeyEvent.KEYCODE_F1, false);
        keyButton(keys, R.string.panel_ctrl, KeyEvent.KEYCODE_CTRL_LEFT, false);
        keyButton(keys, R.string.panel_fps, KeyEvent.KEYCODE_F2, false);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        klp.topMargin = Ui.dp(c, 8);
        col.addView(keys, klp);

        LinearLayout actions = new LinearLayout(c);
        actionButton(actions, R.string.menu_controls, v -> mActions.controls());
        actionButton(actions, R.string.panel_essentia_menu, v -> mActions.menu());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 50));
        alp.topMargin = Ui.dp(c, 8);
        col.addView(actions, alp);

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        mStart = System.currentTimeMillis();
        mHandler.post(mPoll);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mHandler.removeCallbacks(mPoll);
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
        mPlace.setText(name);
        mPlace.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);

        String image = f[8].trim();
        int sx = parse(f[6]), sy = parse(f[7]);
        if (image.isEmpty() || sx < 0) {
            mMap.setMap(null);
            mMap.setEmptyText(getContext().getString(R.string.panel_map_position,
                parse(f[1]), parse(f[2])));
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

    // ------------------------------------------------------------ buttons

    @SuppressLint("ClickableViewAccessibility")
    private void keyButton(GridLayout grid, int labelRes, int keycode, boolean primary)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 16, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.focusable(c, primary ? Ui.ACCENT : Ui.SURFACE_HI, 16));
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
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = 0;
        int m = Ui.dp(c, 4);
        lp.setMargins(m, m, m, m);
        grid.addView(b, lp);
    }

    private void actionButton(LinearLayout row, int labelRes, View.OnClickListener l)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 15, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.focusable(c, 0x33FFFFFF, 16));
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        int m = Ui.dp(c, 4);
        lp.setMargins(m, 0, m, 0);
        row.addView(b, lp);
    }
}
