package com.hatkid.mkxpz;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.SecondScreen;
import com.hatkid.mkxpz.ui.Ui;

/** Essentia's settings: on-screen controls, screen, updates and credits. */
public class SettingsActivity extends ControllerActivity
{
    private LinearLayout mList;
    private ScrollView mScroll;
    private LinearLayout mTabs;
    private int mCategory = 0;

    /** Settings are grouped: one category on screen at a time. */
    private static final int[] CATEGORIES = {
        R.string.cat_look, R.string.cat_screen, R.string.cat_controls, R.string.cat_games,
        R.string.cat_cloud, R.string.cat_advanced, R.string.cat_about
    };
    private static final int[] CATEGORY_ICONS = {
        R.drawable.ic_e_grid, R.drawable.ic_e_shelf, R.drawable.ic_e_gamepad, R.drawable.ic_e_play,
        R.drawable.ic_e_sort, R.drawable.ic_e_settings, R.drawable.ic_e_more
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) mCategory = savedInstanceState.getInt("category", 0);

        boolean portrait = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);

        // Left (or top): back button and the categories
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        side.setPadding(pad, pad, portrait ? pad : Ui.dp(this, 8), portrait ? 0 : pad);
        side.setBackgroundColor(Ui.alpha(Ui.SURFACE, 0x88));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Ui.button(this, "\u2039  " + getString(R.string.set_back), false);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = Ui.text(this, getString(R.string.lib_settings), 22, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title);
        side.addView(header);

        mTabs = new LinearLayout(this);
        mTabs.setOrientation(portrait ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        mTabs.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int cat = i;
            View tab = tab(CATEGORY_ICONS[i], getString(CATEGORIES[i]));
            tab.setOnClickListener(v -> showCategory(cat));

            LinearLayout.LayoutParams lp = portrait
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 52))
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
            lp.setMargins(0, 0, portrait ? Ui.dp(this, 6) : 0, portrait ? 0 : Ui.dp(this, 4));
            mTabs.addView(tab, lp);
        }
        if (portrait) {
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(mTabs);
            side.addView(hs);
        } else {
            ScrollView vs = new ScrollView(this);
            vs.addView(mTabs);
            side.addView(vs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        root.addView(side, portrait
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(Ui.dp(this, 204), ViewGroup.LayoutParams.MATCH_PARENT));

        // Right (or below): the chosen category
        mScroll = new ScrollView(this);
        mScroll.setFillViewport(true);
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 20));
        mScroll.addView(mList, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mScroll, portrait
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
        showCategory(mCategory);
        View first = mTabs.getChildAt(mCategory);
        if (first != null) first.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle out)
    {
        super.onSaveInstanceState(out);
        out.putInt("category", mCategory);
    }

    private View tab(int icon, String text)
    {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 14), 0);
        b.setFocusable(true);
        b.setClickable(true);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focused = Ui.rounded(this, Ui.SURFACE_HI, 12);
        focused.setStroke(Ui.dp(this, 2), Ui.ACCENT_LIGHT);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.rounded(this, Ui.alpha(Ui.ACCENT, 0x55), 12));
        bg.addState(new int[]{android.R.attr.state_pressed}, Ui.rounded(this, Ui.ACCENT, 12));
        bg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        b.setBackground(bg);
        android.widget.ImageView i = new android.widget.ImageView(this);
        i.setImageResource(icon);
        i.setImageTintList(Ui.tint(Ui.TEXT));
        b.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = Ui.text(this, text, 15, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        b.addView(t);
        return b;
    }

    private void showCategory(int cat)
    {
        if (mList == null) return;
        if (cat == mCategory && mList.getChildCount() > 0) return;
        mCategory = cat;
        for (int i = 0; i < mTabs.getChildCount(); i++) mTabs.getChildAt(i).setSelected(i == cat);
        mList.removeAllViews();
        TextView heading=Ui.text(this,getString(CATEGORIES[cat]),26,Ui.TEXT,true);mList.addView(heading);
        String[] hints={"Hazlo tuyo. Los cambios de aspecto conservan tus juegos.","Pantalla principal y acompañante de juego.","Mando, pantalla táctil y accesos durante la partida.","Juegos, partidas y recursos en un solo lugar.","Conexiones opcionales entre tus dispositivos Android.","Cambia una opción a la vez y compara en el mismo juego.","Actualizaciones, ayuda y créditos de Essentia."};
        TextView hint=Ui.text(this,hints[cat],14,Ui.TEXT_DIM,false);hint.setPadding(0,0,0,Ui.dp(this,16));mList.addView(hint);
        switch (cat) {
            case 0: buildTheme(); buildMusic(); break;
            case 1: buildScreen(); break;
            case 2: buildControls(); buildTurbo(); break;
            case 3: buildGames(); buildSaves(); buildGameUpdates(); buildRtp(); break;
            case 4: buildCloud(); buildFrontends(); break;
            case 5: buildPerformance(); break;
            default: buildUpdates(); buildAbout(); break;
        }
        mScroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------ sections

    private void buildTheme()
    {
        section(R.string.set_theme_title, R.string.set_theme_desc);
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(getResources().getConfiguration().screenWidthDp < 600 ? 2 : 3);
        String current = Ui.theme(this);
        int[] names = { R.string.theme_esencia, R.string.theme_oceano, R.string.theme_bosque, R.string.theme_fuego,
            R.string.theme_electrico, R.string.theme_rosa, R.string.theme_noche };
        for (int i = 0; i < Ui.THEMES.length; i++) {
            final String[] t = Ui.THEMES[i];
            LinearLayout swatch = new LinearLayout(this);
            swatch.setOrientation(LinearLayout.VERTICAL);
            swatch.setGravity(Gravity.CENTER);
            swatch.setFocusable(true);
            swatch.setClickable(true);
            int p = Ui.dp(this, 8);
            swatch.setPadding(p, p, p, p);
            android.graphics.drawable.GradientDrawable sel = Ui.rounded(this, android.graphics.Color.parseColor(t[2]), 14);
            sel.setStroke(Ui.dp(this, t[0].equals(current) ? 3 : 1),
                t[0].equals(current) ? Ui.GOLD : android.graphics.Color.parseColor(t[3]));
            android.graphics.drawable.GradientDrawable foc = Ui.rounded(this, android.graphics.Color.parseColor(t[3]), 14);
            foc.setStroke(Ui.dp(this, 3), Ui.TEXT);
            android.graphics.drawable.StateListDrawable st = new android.graphics.drawable.StateListDrawable();
            st.addState(new int[]{android.R.attr.state_focused}, foc);
            st.addState(new int[]{}, sel);
            swatch.setBackground(st);
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{ android.graphics.Color.parseColor(t[4]), android.graphics.Color.parseColor(t[5]) });
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setBackground(d);
            swatch.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
            TextView name = Ui.text(this, getString(names[i]), 13, Ui.TEXT, t[0].equals(current));
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Ui.dp(this, 6), 0, 0);
            swatch.addView(name);
            swatch.setOnClickListener(v -> {
                Ui.setTheme(this, t[0]);
                recreate();
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f);
            lp.height=Ui.dp(this,100);
            lp.setMargins(0, 0, Ui.dp(this, 8), Ui.dp(this, 8));
            grid.addView(swatch, lp);
        }
        mList.addView(grid);
    }

    private void buildGames()
    {
        section(R.string.set_comfort_title, 0);
        flagRow(R.string.set_fast_text, EssentiaSettings.FAST_TEXT, false);
        flagRow(R.string.set_skip_intro, EssentiaSettings.SKIP_INTRO, false);
        flagRow(R.string.set_resume_last, EssentiaSettings.RESUME_LAST, false);
        flagRow(R.string.set_rescan, EssentiaSettings.RESCAN, true);
    }

    private void buildMusic()
    {
        section(R.string.set_music_title, R.string.set_music_desc);
        CheckBox on = check(R.string.set_music, MusicPlayer.enabled(this));
        on.setOnCheckedChangeListener((b, v) -> {
            EssentiaSettings.setFlag(this, EssentiaSettings.MUSIC, v);
            MusicPlayer.refresh(this);
        });
        card(on);
        slider(R.string.set_music_volume, 5, 100, MusicPlayer.volume(this), v -> {
            EssentiaSettings.setMusicVolume(this, v);
            MusicPlayer.refresh(this);
        });
    }

    private void buildControls()
    {
        section(R.string.set_osc_title, R.string.set_osc_desc);

        RadioGroup group = new RadioGroup(this);
        String mode = EssentiaSettings.oscMode(this);
        group.addView(radio(R.string.set_osc_auto, EssentiaSettings.OSC_AUTO, mode));
        group.addView(radio(R.string.set_osc_always, EssentiaSettings.OSC_ALWAYS, mode));
        group.addView(radio(R.string.set_osc_never, EssentiaSettings.OSC_NEVER, mode));
        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setOscMode(this, (String) r.getTag());
        });
        card(group);

        slider(R.string.set_osc_size, 60, 160, EssentiaSettings.oscScale(this),
            v -> EssentiaSettings.setOscScale(this, v));
        slider(R.string.set_osc_opacity, 10, 100, EssentiaSettings.oscOpacity(this),
            v -> EssentiaSettings.setOscOpacity(this, v));

        flagRow(R.string.set_haptics, EssentiaSettings.HAPTICS, true);
        flagRow(R.string.set_diagonal, EssentiaSettings.DIAGONAL, false);

        Button buttons = Ui.button(this, getString(R.string.set_button_map), false);
        buttons.setOnClickListener(v -> startActivity(new Intent(this, ControlsActivity.class)));
        card(buttons);
    }

    private void buildScreen()
    {
        section(R.string.set_screen_title, 0);

        TextView fmtLabel = Ui.text(this, getString(R.string.set_screen_format), 15, Ui.TEXT, true);
        fmtLabel.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        mList.addView(fmtLabel);
        RadioGroup fmt = new RadioGroup(this);
        String sm = EssentiaSettings.screenMode(this);
        fmt.addView(radio(R.string.scr_wide_safe_long, EssentiaSettings.SCREEN_WIDE_SAFE, sm));
        fmt.addView(radio(R.string.scr_wide_long, EssentiaSettings.SCREEN_WIDE, sm));
        fmt.addView(radio(R.string.scr_ambient_long, EssentiaSettings.SCREEN_AMBIENT, sm));
        fmt.addView(radio(R.string.scr_original_long, EssentiaSettings.SCREEN_ORIGINAL, sm));
        fmt.addView(radio(R.string.scr_stretch_long, EssentiaSettings.SCREEN_STRETCH, sm));
        fmt.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setScreenMode(this, (String) r.getTag());
        });
        card(fmt);
        TextView fmtNote = Ui.text(this, getString(R.string.set_screen_format_note), 13, Ui.TEXT_DIM, false);
        fmtNote.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        mList.addView(fmtNote);

        TextView orLabel = Ui.text(this, getString(R.string.set_orient_title), 15, Ui.TEXT, true);
        orLabel.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        mList.addView(orLabel);
        RadioGroup orient = new RadioGroup(this);
        String om = EssentiaSettings.orientation(this);
        orient.addView(radio(R.string.set_orient_landscape, EssentiaSettings.ORIENT_LANDSCAPE, om));
        orient.addView(radio(R.string.set_orient_portrait, EssentiaSettings.ORIENT_PORTRAIT, om));
        orient.addView(radio(R.string.set_orient_auto, EssentiaSettings.ORIENT_AUTO, om));
        orient.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setOrientation(this, (String) r.getTag());
        });
        card(orient);
        TextView orNote = Ui.text(this, getString(R.string.set_orient_note), 13, Ui.TEXT_DIM, false);
        orNote.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        mList.addView(orNote);

        TextView filterLabel = Ui.text(this, getString(R.string.set_filter_title), 15, Ui.TEXT, true);
        filterLabel.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        mList.addView(filterLabel);
        RadioGroup filter = new RadioGroup(this);
        String fm = EssentiaSettings.filter(this);
        filter.addView(radio(R.string.set_filter_sharp, EssentiaSettings.FILTER_SHARP, fm));
        filter.addView(radio(R.string.set_filter_smooth, EssentiaSettings.FILTER_SMOOTH, fm));
        filter.addView(radio(R.string.set_filter_crt, EssentiaSettings.FILTER_CRT, fm));
        filter.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setFilter(this, (String) r.getTag());
        });
        card(filter);

        boolean hasSecond = SecondScreen.findOtherDisplay(this, getWindowManager().getDefaultDisplay()) != null;
        CheckBox dual = check(R.string.set_dual,
            EssentiaSettings.DUAL_AUTO.equals(EssentiaSettings.dualScreen(this)));
        dual.setOnCheckedChangeListener((b, on) -> EssentiaSettings.setDualScreen(this,
            on ? EssentiaSettings.DUAL_AUTO : EssentiaSettings.DUAL_OFF));
        card(dual);

        TextView gameLabel = Ui.text(this, getString(R.string.set_game2_title), 15, Ui.TEXT, true);
        gameLabel.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 4));
        mList.addView(gameLabel);
        RadioGroup game2 = new RadioGroup(this);
        String g2 = EssentiaSettings.gameSecondScreen(this);
        game2.addView(radio(R.string.set_game2_panel, EssentiaSettings.GAME2_PANEL, g2));
        game2.addView(radio(R.string.set_game2_black, EssentiaSettings.GAME2_BLACK, g2));
        game2.addView(radio(R.string.set_game2_off, EssentiaSettings.GAME2_OFF, g2));
        game2.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setGameSecondScreen(this, (String) r.getTag());
        });
        card(game2);

        TextView note = Ui.text(this, getString(hasSecond ? R.string.set_dual_found : R.string.set_dual_none),
            13, Ui.TEXT_DIM, false);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
        mList.addView(note);
    }

    private void buildPerformance()
    {
        section(R.string.set_perf_title, R.string.set_perf_desc);
        flagRow(R.string.set_perf_fast_load, EssentiaSettings.PERF_FAST_LOAD, true);
        flagRow(R.string.set_perf_load_times, EssentiaSettings.PERF_LOAD_TIMES, true);
        flagRow(R.string.set_perf_fps, EssentiaSettings.PERF_SHOW_FPS, false);
        flagRow(R.string.set_perf_frameskip, EssentiaSettings.PERF_FRAMESKIP, false);
        flagRow(R.string.set_perf_vsync, EssentiaSettings.PERF_VSYNC, false);
        flagRow(R.string.set_perf_integer, EssentiaSettings.PERF_INTEGER, false);
        flagRow(R.string.set_perf_sustained, EssentiaSettings.PERF_SUSTAINED, true);
        flagRow(R.string.set_perf_60hz, EssentiaSettings.PERF_60HZ, true);
        flagRow(R.string.set_debug_input, EssentiaSettings.DEBUG_INPUT, false);
    }

    private void flagRow(int textRes, String key, boolean def)
    {
        CheckBox c = check(textRes, EssentiaSettings.flag(this, key, def));
        c.setOnCheckedChangeListener((b, on) -> EssentiaSettings.setFlag(this, key, on));
        card(c);
    }

    private void buildTurbo()
    {
        section(R.string.set_turbo_title, R.string.set_turbo_desc);
        RadioGroup group = new RadioGroup(this);
        String cur = String.valueOf(EssentiaSettings.turboSpeed(this));
        group.addView(radio(R.string.set_turbo_2, "2", cur));
        group.addView(radio(R.string.set_turbo_3, "3", cur));
        group.addView(radio(R.string.set_turbo_4, "4", cur));
        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setTurboSpeed(this, Integer.parseInt((String) r.getTag()));
        });
        card(group);
    }

    private void buildRtp()
    {
        section(R.string.set_rtp_title, 0);
        Button install = Ui.button(this, "Importar recursos RTP desde ZIP", false);
        install.setOnClickListener(v -> startActivity(new Intent(this,RtpActivity.class)));
        card(install);
        java.io.File root = EssentiaSettings.rtpRoot();
        TextView t = Ui.text(this, getString(R.string.set_rtp_desc, root.getAbsolutePath()), 14, Ui.TEXT_DIM, false);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        card(t);
        Button make = Ui.button(this, getString(R.string.set_rtp_make), false);
        make.setOnClickListener(v -> {
            boolean ok = true;
            for (String n : new String[] { "XP", "VX", "VXAce" }) ok &= new java.io.File(root, n).mkdirs() || new java.io.File(root, n).isDirectory();
            android.widget.Toast.makeText(this, ok ? R.string.set_rtp_made : R.string.act_fail,
                android.widget.Toast.LENGTH_LONG).show();
        });
        card(make);
    }

    private void buildGameUpdates()
    {
        section(R.string.set_auto_update_title, R.string.set_auto_update_desc);
        flagRow(R.string.set_auto_update, EssentiaSettings.AUTO_UPDATE, true);
    }

    private void buildSaves()
    {
        section(R.string.set_saves_title, R.string.set_saves_desc);
        RadioGroup group = new RadioGroup(this);
        String loc = EssentiaSettings.saveLocation(this);
        group.addView(radio(R.string.set_saves_game, EssentiaSettings.SAVES_GAME, loc));
        group.addView(radio(R.string.set_saves_essentia, EssentiaSettings.SAVES_ESSENTIA, loc));
        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton r = g.findViewById(id);
            if (r != null) EssentiaSettings.setSaveLocation(this, (String) r.getTag());
        });
        card(group);
    }

    private static final int REQ_EXPORT = 10;

    private void buildFrontends()
    {
        section(R.string.set_fe_title, R.string.set_fe_desc);

        Button export = Ui.button(this, getString(R.string.set_fe_export), true);
        export.setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_EXPORT);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, R.string.err_no_picker, android.widget.Toast.LENGTH_LONG).show();
            }
        });
        mList.addView(export, wrap());

        TextView how = Ui.text(this, getString(R.string.set_fe_how), 14, Ui.TEXT_DIM, false);
        how.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 10));
        mList.addView(how);

        Button guide = Ui.button(this, getString(R.string.set_fe_guide), false);
        guide.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/nikalbazu24/Essentia/blob/main/docs/frontends.md")));
            } catch (Exception ignored) { }
        });
        mList.addView(guide, wrap());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String path = Games.pathFromUri(this, data.getData());
        if (path == null) {
            android.widget.Toast.makeText(this, R.string.set_fe_failed, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        int n = Shortcuts.export(this, new java.io.File(path));
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.set_fe_title)
            .setMessage(getResources().getQuantityString(R.plurals.fe_done, n, n, path))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void buildUpdates()
    {
        section(R.string.set_updates_title, 0);
        TextView version = Ui.text(this, getString(R.string.set_version, BuildConfig.VERSION_NAME), 15, Ui.TEXT, false);
        version.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        mList.addView(version);

        Button check = Ui.button(this, getString(R.string.set_check_updates), true);
        check.setOnClickListener(v -> Updater.check(this, () -> { }, true));
        mList.addView(check, wrap());
    }

    private void buildAbout()
    {
        Button licenses = Ui.button(this,"Créditos y licencias",false);
        licenses.setOnClickListener(v -> Licenses.show(this));
        mList.addView(licenses,wrap());
        Button guide = Ui.button(this, "Essentia by Díptico · Guía y herramientas", false);
        guide.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        mList.addView(guide, wrap());
        Button news = Ui.button(this, getString(R.string.wn_show), false);
        news.setOnClickListener(v -> com.hatkid.mkxpz.ui.WhatsNew.show(this));

        section(R.string.set_about_title, 0);
        TextView about = Ui.text(this, getString(R.string.set_about_text), 14, Ui.TEXT_DIM, false);
        about.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        mList.addView(about);

        Button repo = Ui.button(this, getString(R.string.set_open_repo), false);
        repo.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nikalbazu24/Essentia")));
            } catch (Exception ignored) { }
        });
        mList.addView(repo, wrap());
        LinearLayout.LayoutParams nlp = wrap();
        nlp.topMargin = Ui.dp(this, 8);
        mList.addView(news, nlp);
    }

    // ------------------------------------------------------------ database

    private void buildCloud()
    {
        section(R.string.set_cloud_title, R.string.set_cloud_desc);

        TextView urlLabel = Ui.text(this, getString(R.string.set_cloud_url), 15, Ui.TEXT, true);
        urlLabel.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        mList.addView(urlLabel);
        android.widget.EditText url = new android.widget.EditText(this);
        url.setSingleLine(true);
        url.setTextColor(Ui.TEXT);
        url.setHint("https://script.google.com/macros/s/…/exec");
        url.setText(getSharedPreferences(EssentiaSettings.PREFS, MODE_PRIVATE).getString("cloud_url", ""));
        url.addTextChangedListener(saver(t -> Cloud.setUrl(this, t)));
        card(url);

        TextView keyLabel = Ui.text(this, getString(R.string.set_cloud_key), 15, Ui.TEXT, true);
        keyLabel.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        mList.addView(keyLabel);
        android.widget.EditText key = new android.widget.EditText(this);
        key.setSingleLine(true);
        key.setTextColor(Ui.TEXT);
        key.setText(Cloud.backupKey(this));
        key.addTextChangedListener(saver(t -> Cloud.setBackupKey(this, t)));
        card(key);

        flagRow(R.string.set_cloud_backup, EssentiaSettings.CLOUD_BACKUP, false);

        Button test = Ui.button(this, getString(R.string.set_cloud_test), false);
        test.setOnClickListener(v -> {
            Cloud.setUrl(this, url.getText().toString());
            Cloud.setBackupKey(this, key.getText().toString());
            new Thread(() -> {
                boolean ok;
                try {
                    org.json.JSONObject b = new org.json.JSONObject();
                    b.put("action", "ping");
                    Cloud.post(getApplicationContext(), b, 20000);
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                }
                final boolean done = ok;
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                    done ? R.string.set_cloud_ok : R.string.set_cloud_fail, android.widget.Toast.LENGTH_LONG).show());
            }).start();
        });
        mList.addView(test, wrap());
    }


    private interface TextSetter { void set(String v); }

    private android.text.TextWatcher saver(TextSetter setter)
    {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { setter.set(e.toString()); }
        };
    }

    // ------------------------------------------------------------ widgets

    private interface IntSetter { void set(int v); }

    private LinearLayout.LayoutParams wrap()
    {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void section(int titleRes, int descRes)
    {
        TextView t = Ui.text(this, getString(titleRes), 18, Ui.ACCENT_LIGHT, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 26), 0, Ui.dp(this, 6));
        mList.addView(t);
        if (descRes != 0) {
            TextView d = Ui.text(this, getString(descRes), 14, Ui.TEXT_DIM, false);
            d.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
            mList.addView(d);
        }
    }

    private void card(View content)
    {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(this, Ui.SURFACE, 14));
        int p = Ui.dp(this, 8);
        box.setPadding(p, p, p, p);
        box.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        mList.addView(box, lp);
    }

    private RadioButton radio(int textRes, String value, String current)
    {
        RadioButton r = new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(textRes);
        r.setTag(value);
        r.setTextColor(Ui.TEXT);
        r.setTextSize(16);
        r.setButtonTintList(Ui.tint(Ui.ACCENT_LIGHT));
        r.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 10);
        r.setPadding(p, p, p, p);
        r.setChecked(value.equals(current));
        return r;
    }

    private CheckBox check(int textRes, boolean checked)
    {
        CheckBox c = new CheckBox(this);
        c.setText(textRes);
        c.setTextColor(Ui.TEXT);
        c.setTextSize(16);
        c.setButtonTintList(Ui.tint(Ui.ACCENT_LIGHT));
        c.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 10);
        c.setPadding(p, p, p, p);
        c.setChecked(checked);
        return c;
    }

    private void slider(int labelRes, int min, int max, int value, IntSetter setter)
    {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView label = Ui.text(this, getString(labelRes, value), 15, Ui.TEXT, false);
        label.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, 0);
        col.addView(label);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setKeyProgressIncrement(5);
        bar.setProgressTintList(Ui.tint(Ui.ACCENT));
        bar.setThumbTintList(Ui.tint(Ui.ACCENT_LIGHT));
        bar.setBackground(Ui.focusable(this, Ui.SURFACE, 10));
        int p = Ui.dp(this, 14);
        bar.setPadding(p, p, p, p);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress;
                label.setText(getString(labelRes, v));
                setter.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(bar);
        card(col);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        MusicPlayer.resume(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        MusicPlayer.pause();
    }
}
