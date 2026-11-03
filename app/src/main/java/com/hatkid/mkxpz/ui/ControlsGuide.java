package com.hatkid.mkxpz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hatkid.mkxpz.R;

/**
 * The controls manual: which controller button sends which PC key, and what that key
 * usually does in Pokémon Essentials games.
 */
public final class ControlsGuide
{
    private ControlsGuide() { }

    /** Rows: button, PC key, typical use. Strings are resource ids. */
    private static final int[][] ROWS = {
        { R.string.ctl_btn_a,      R.string.ctl_key_a,      R.string.ctl_use_a },
        { R.string.ctl_btn_b,      R.string.ctl_key_b,      R.string.ctl_use_b },
        { R.string.ctl_btn_start,  R.string.ctl_key_start,  R.string.ctl_use_start },
        { R.string.ctl_btn_x,      R.string.ctl_key_x,      R.string.ctl_use_x },
        { R.string.ctl_btn_y,      R.string.ctl_key_y,      R.string.ctl_use_y },
        { R.string.ctl_btn_l1r1,   R.string.ctl_key_l1r1,   R.string.ctl_use_l1r1 },
        { R.string.ctl_btn_l2,     R.string.ctl_key_l2,     R.string.ctl_use_l2 },
        { R.string.ctl_btn_r2,     R.string.ctl_key_r2,     R.string.ctl_use_r2 },
        { R.string.ctl_btn_move,   R.string.ctl_key_move,   R.string.ctl_use_move },
        { R.string.ctl_btn_menu,   R.string.ctl_key_menu,   R.string.ctl_use_menu },
        { R.string.ctl_btn_f1,     R.string.ctl_key_f1,     R.string.ctl_use_f1 },
        { R.string.ctl_btn_turbo,  R.string.ctl_key_essentia, R.string.ctl_use_turbo },
        { R.string.ctl_btn_shot,   R.string.ctl_key_essentia, R.string.ctl_use_shot },
    };

    /** The table as a view. */
    public static LinearLayout buildTable(Context c)
    {
        LinearLayout table = new LinearLayout(c);
        table.setOrientation(LinearLayout.VERTICAL);

        table.addView(row(c, c.getString(R.string.ctl_col_button), c.getString(R.string.ctl_col_key),
            c.getString(R.string.ctl_col_use), true, false));
        boolean alt = false;
        for (int[] r : ROWS) {
            table.addView(row(c, c.getString(r[0]), c.getString(r[1]), c.getString(r[2]), false, alt));
            alt = !alt;
        }

        TextView note = Ui.text(c, c.getString(R.string.ctl_note), 13, Ui.TEXT_DIM, false);
        note.setPadding(Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6), 0);
        table.addView(note);
        return table;
    }

    private static LinearLayout row(Context c, String a, String b, String d, boolean header, boolean alt)
    {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 8);
        r.setPadding(p, p, p, p);
        if (header) r.setBackground(Ui.rounded(c, Ui.SURFACE_HI, 8));
        else if (alt) r.setBackground(Ui.rounded(c, Ui.SURFACE, 8));

        int color = header ? Ui.ACCENT_LIGHT : Ui.TEXT;
        r.addView(cell(c, a, header ? Ui.ACCENT_LIGHT : Ui.GOLD, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        r.addView(cell(c, b, color, header), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        r.addView(cell(c, d, header ? Ui.ACCENT_LIGHT : Ui.TEXT_DIM, header), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        return r;
    }

    private static TextView cell(Context c, String s, int color, boolean bold)
    {
        TextView t = Ui.text(c, s, 14, color, bold);
        t.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        return t;
    }

    /** Shows the manual as a dialog (used inside the game). */
    public static void showDialog(Activity a)
    {
        ScrollView scroll = new ScrollView(a);
        LinearLayout table = buildTable(a);
        int p = Ui.dp(a, 12);
        table.setPadding(p, p, p, p);
        scroll.addView(table);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.ctl_title)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .show();
    }
}
