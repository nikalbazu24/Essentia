package com.hatkid.mkxpz.ui;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
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

/**
 * The second screen while playing (dual-screen devices such as the AYN Thor).
 *
 * "panel": big touch keys the controller doesn't have (menu, F1, Shift...) and quick
 * actions (controls manual, exit). "black": a dark screen, nothing to distract.
 * It never takes the controller away from the game.
 */
public final class GamePanel extends Presentation
{
    public interface Keys { void down(int keycode); void up(int keycode); }
    public interface Actions { void controls(); void menu(); }

    private final boolean mBlack;
    private final String mTitle;
    private final Keys mKeys;
    private final Actions mActions;

    private GamePanel(Context outer, Display display, boolean black, String title, Keys keys, Actions actions)
    {
        super(outer, display);
        mBlack = black;
        mTitle = title;
        mKeys = keys;
        mActions = actions;
    }

    public static GamePanel show(Context c, Display current, boolean black, String title, Keys keys, Actions actions)
    {
        Display other = SecondScreen.findOtherDisplay(c, current);
        if (other == null) return null;
        try {
            GamePanel p = new GamePanel(c, other, black, title, keys, actions);
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
        int pad = Ui.dp(c, 16);
        col.setPadding(pad, pad, pad, pad);

        // Header: game name + clock
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(c, mTitle, 18, Ui.TEXT, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextClock clock = new TextClock(c);
        clock.setTextColor(Ui.TEXT_DIM);
        clock.setTextSize(16);
        header.addView(clock);
        col.addView(header);

        TextView keysLabel = Ui.text(c, c.getString(R.string.panel_keys), 12, Ui.ACCENT_LIGHT, true);
        keysLabel.setAllCaps(true);
        keysLabel.setLetterSpacing(0.1f);
        keysLabel.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        col.addView(keysLabel);

        GridLayout keys = new GridLayout(c);
        keys.setColumnCount(3);
        keyButton(keys, R.string.panel_menu, KeyEvent.KEYCODE_ESCAPE, true);
        keyButton(keys, R.string.panel_enter, KeyEvent.KEYCODE_ENTER, true);
        keyButton(keys, R.string.panel_shift, KeyEvent.KEYCODE_SHIFT_LEFT, false);
        keyButton(keys, R.string.panel_f1, KeyEvent.KEYCODE_F1, false);
        keyButton(keys, R.string.panel_ctrl, KeyEvent.KEYCODE_CTRL_LEFT, false);
        keyButton(keys, R.string.panel_fps, KeyEvent.KEYCODE_F2, false);
        col.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView actLabel = Ui.text(c, c.getString(R.string.panel_actions), 12, Ui.ACCENT_LIGHT, true);
        actLabel.setAllCaps(true);
        actLabel.setLetterSpacing(0.1f);
        actLabel.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        col.addView(actLabel);

        LinearLayout actions = new LinearLayout(c);
        actionButton(actions, R.string.menu_controls, v -> mActions.controls());
        actionButton(actions, R.string.panel_essentia_menu, v -> mActions.menu());
        col.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 56)));

        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void keyButton(GridLayout grid, int labelRes, int keycode, boolean primary)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 17, Ui.TEXT, true);
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
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = 0;
        int m = Ui.dp(c, 5);
        lp.setMargins(m, m, m, m);
        grid.addView(b, lp);
    }

    private void actionButton(LinearLayout row, int labelRes, View.OnClickListener l)
    {
        Context c = getContext();
        TextView b = Ui.text(c, c.getString(labelRes), 15, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.focusable(c, 0x33FFFFFF, 18));
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        int m = Ui.dp(c, 5);
        lp.setMargins(m, 0, m, 0);
        row.addView(b, lp);
    }
}
