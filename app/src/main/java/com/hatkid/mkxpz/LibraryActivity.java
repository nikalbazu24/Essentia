package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.CoverLoader;
import com.hatkid.mkxpz.ui.SecondScreen;
import com.hatkid.mkxpz.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Essentia's home screen: the game library.
 *
 * Shelf view: the selected game is featured at the top (big cover over a blurred
 * backdrop of its own art) and all games sit on a horizontal shelf below.
 * Grid and list views show more games at once.
 */
public class LibraryActivity extends ControllerActivity
{
    private static final int REQ_ALL_FILES = 1;
    private static final int REQ_PICK_FOLDER = 2;
    private static final int REQ_PICK_COVER = 3;
    private static final int REQ_IMPORT_FILES = 4;
    private static final int REQ_IMPORT_FOLDER = 5;
    private static final String PREF_LAST_TREE = "last_tree_uri";

    private final List<Library.Entry> mGames = new ArrayList<>();
    private CoverLoader mCovers;
    private SecondScreen mSecond;
    private int mSelected = 0;
    private int mBaseColumn;
    private String mView;
    private String mSort;
    /** Game waiting for a picked cover or save files. */
    private Library.Entry mPending;

    // Views
    private ImageView mBackdrop;
    private TextView mCount;
    private ImageView mViewIcon;
    private TextView mSortLabel;
    private View mHero;
    private ImageView mHeroCover;
    private TextView mHeroTitle;
    private TextView mHeroDetail;
    private View mHeroPlay;
    private HorizontalScrollView mShelfScroll;
    private LinearLayout mShelf;
    private GridView mGrid;
    private GamesAdapter mAdapter;
    private View mEmpty;
    private View mZoomed;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        mBaseColumn = Ui.dp(this, widthDp >= 900 ? 220 : (widthDp >= 600 ? 190 : 150));
        mCovers = new CoverLoader(Ui.dp(this, 360));
        mView = EssentiaSettings.viewMode(this);
        mSort = EssentiaSettings.sortMode(this);

        setContentView(buildUi());
        applyViewMode();

        if (!Games.hasAllFilesAccess()) {
            Games.askAllFilesAccess(this, REQ_ALL_FILES, this::finish);
        } else if (savedInstanceState == null) {
            Updater.check(this, () -> { }, false);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        reload();
        showSecondScreen();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (mSecond != null) {
            mSecond.dismiss();
            mSecond = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mCovers.shutdown();
    }

    // ================================================================== UI

    private boolean isPortrait()
    {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private View buildUi()
    {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // Backdrop: the selected game's art, blurred and dimmed
        mBackdrop = new ImageView(this);
        mBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mBackdrop.setAlpha(0.45f);
        if (Build.VERSION.SDK_INT >= 31) {
            mBackdrop.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP));
        }
        root.addView(mBackdrop, match());
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ 0x6614162B, 0xCC14162B, 0xFF14162B }));
        root.addView(fade, match());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 8));
        root.addView(col, match());

        col.addView(buildTopBar(), wrapWidth());

        // Featured game (shelf view)
        mHero = buildHero();
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f);
        hlp.topMargin = Ui.dp(this, 10);
        col.addView(mHero, hlp);

        // Body: shelf, grid/list, or the empty state
        FrameLayout body = new FrameLayout(this);
        mShelfScroll = new HorizontalScrollView(this);
        mShelfScroll.setHorizontalScrollBarEnabled(false);
        mShelfScroll.setClipToPadding(false);
        mShelfScroll.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14));
        mShelf = new LinearLayout(this);
        mShelf.setOrientation(LinearLayout.HORIZONTAL);
        mShelf.setGravity(Gravity.CENTER_VERTICAL);
        mShelf.setClipChildren(false);
        mShelfScroll.addView(mShelf, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(mShelfScroll, match());

        mGrid = buildGrid();
        body.addView(mGrid, match());

        mEmpty = buildEmptyState();
        body.addView(mEmpty, match());

        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        col.addView(buildHints(), wrapWidth());
        return root;
    }

    private View buildTopBar()
    {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView flask = new ImageView(this);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        flask.setBackground(Ui.rounded(this, Ui.SURFACE, 12));
        bar.addView(flask, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        TextView title = Ui.text(this, getString(R.string.app_name), 22, Ui.TEXT, true);
        title.setLetterSpacing(0.02f);
        names.addView(title);
        mCount = Ui.text(this, "", 12, Ui.TEXT_DIM, false);
        names.addView(mCount);
        bar.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mViewIcon = iconButton(bar, R.drawable.ic_e_shelf, R.string.lib_view_label, v -> cycleView());
        View sort = pillButton(bar, R.drawable.ic_e_sort, "", false, v -> toggleSort());
        mSortLabel = sort.findViewWithTag("label");
        iconButton(bar, R.drawable.ic_e_gamepad, R.string.lib_controls, v -> startActivity(new Intent(this, ControlsActivity.class)));
        iconButton(bar, R.drawable.ic_e_settings, R.string.lib_settings, v -> startActivity(new Intent(this, SettingsActivity.class)));
        pillButton(bar, R.drawable.ic_e_add, getString(R.string.lib_add_short), true, v -> openFolderPicker());
        return bar;
    }

    private View buildHero()
    {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(isPortrait() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        // Text + actions
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 20), 0);

        TextView label = Ui.text(this, getString(R.string.lib_featured), 12, Ui.ACCENT_LIGHT, true);
        label.setLetterSpacing(0.12f);
        label.setAllCaps(true);
        info.addView(label);

        mHeroTitle = Ui.text(this, "", 30, Ui.TEXT, true);
        mHeroTitle.setMaxLines(2);
        mHeroTitle.setEllipsize(TextUtils.TruncateAt.END);
        mHeroTitle.setShadowLayer(12, 0, 2, 0x99000000);
        mHeroTitle.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        info.addView(mHeroTitle);

        mHeroDetail = Ui.text(this, "", 14, Ui.TEXT_DIM, false);
        info.addView(mHeroDetail);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, Ui.dp(this, 16), 0, 0);
        mHeroPlay = pillButton(actions, R.drawable.ic_e_play, getString(R.string.lib_play), true, v -> play(mSelected));
        pillButton(actions, R.drawable.ic_e_more, getString(R.string.lib_options), false, v -> showOptions(mSelected));
        info.addView(actions);

        // Cover
        FrameLayout coverFrame = new FrameLayout(this);
        coverFrame.setBackground(Ui.rounded(this, Ui.SURFACE, 18));
        coverFrame.setElevation(Ui.dp(this, 12));
        coverFrame.setClipToOutline(true);
        mHeroCover = new ImageView(this);
        coverFrame.addView(mHeroCover, match());

        if (isPortrait()) {
            hero.addView(coverFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            hero.addView(info, wrapWidth());
        } else {
            hero.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            hero.addView(coverFrame, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.05f));
        }
        return hero;
    }

    private GridView buildGrid()
    {
        GridView g = new GridView(this);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
        g.setClipToPadding(false);
        g.setDrawSelectorOnTop(true);
        g.setSelector(selectorDrawable());
        g.setFocusable(true);
        mAdapter = new GamesAdapter();
        g.setAdapter(mAdapter);
        g.setOnItemClickListener((p, v, pos, id) -> play(pos));
        g.setOnItemLongClickListener((p, v, pos, id) -> { showOptions(pos); return true; });
        g.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                select(pos);
                zoom(v);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { zoom(null); }
        });
        return g;
    }

    private View buildHints()
    {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, Ui.dp(this, 4), 0, 0);
        hint(row, "A", R.string.lib_play);
        hint(row, "Y", R.string.lib_options);
        hint(row, "X", R.string.lib_add_short);
        hint(row, "Select", R.string.lib_view_label);
        hint(row, "Start", R.string.lib_settings);
        return row;
    }

    private void hint(LinearLayout row, String button, int labelRes)
    {
        TextView key = Ui.text(this, button, 11, Ui.BG, true);
        key.setGravity(Gravity.CENTER);
        key.setBackground(Ui.rounded(this, Ui.TEXT_DIM, 10));
        key.setPadding(Ui.dp(this, 7), Ui.dp(this, 1), Ui.dp(this, 7), Ui.dp(this, 1));
        row.addView(key);
        TextView label = Ui.text(this, getString(labelRes), 12, Ui.TEXT_DIM, false);
        label.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 16), 0);
        row.addView(label);
    }

    private View buildEmptyState()
    {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 24);
        col.setPadding(pad, pad, pad, pad);

        ImageView flask = new ImageView(this);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        int s = Ui.dp(this, 110);
        col.addView(flask, new LinearLayout.LayoutParams(s, s));

        TextView title = Ui.text(this, getString(R.string.lib_empty_title), 20, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView body = Ui.text(this, getString(R.string.lib_empty_text), 15, Ui.TEXT_DIM, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        body.setMaxWidth(Ui.dp(this, 460));
        col.addView(body);

        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        pillButton(holder, R.drawable.ic_e_add, getString(R.string.lib_add), true, v -> openFolderPicker());
        col.addView(holder);
        return col;
    }

    // ------------------------------------------------------------ widgets

    private static FrameLayout.LayoutParams match()
    {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrapWidth()
    {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Round icon-only button with a focus ring. */
    private ImageView iconButton(LinearLayout parent, int icon, int descRes, View.OnClickListener l)
    {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setImageTintList(Ui.tint(Ui.TEXT));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = Ui.dp(this, 10);
        b.setPadding(p, p, p, p);
        b.setBackground(Ui.focusable(this, 0x33FFFFFF, 22));
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(getString(descRes));
        b.setOnClickListener(l);
        int size = Ui.dp(this, 44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = Ui.dp(this, 8);
        parent.addView(b, lp);
        return b;
    }

    /** Rounded pill with an icon and a label. */
    private LinearLayout pillButton(LinearLayout parent, int icon, String text, boolean primary, View.OnClickListener l)
    {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Ui.focusable(this, primary ? Ui.ACCENT : 0x33FFFFFF, 22));
        b.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 18), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);

        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setImageTintList(Ui.tint(Ui.TEXT));
        b.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));

        TextView t = Ui.text(this, text, 15, Ui.TEXT, true);
        t.setTag("label");
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        b.addView(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        lp.leftMargin = Ui.dp(this, 8);
        parent.addView(b, lp);
        return b;
    }

    private Drawable selectorDrawable()
    {
        GradientDrawable focus = Ui.rounded(this, 0x00000000, 16);
        focus.setStroke(Ui.dp(this, 3), Ui.ACCENT_LIGHT);
        GradientDrawable press = Ui.rounded(this, 0x408A5CF6, 16);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, press);
        s.addState(new int[]{android.R.attr.state_focused}, focus);
        s.addState(new int[]{}, new ColorDrawable(0x00000000));
        return s;
    }

    // ------------------------------------------------------------ view modes

    private boolean isShelf() { return EssentiaSettings.VIEW_SHELF.equals(mView); }
    private boolean isList() { return EssentiaSettings.VIEW_LIST.equals(mView); }

    private void applyViewMode()
    {
        boolean empty = mGames.isEmpty();
        mHero.setVisibility(isShelf() && !empty ? View.VISIBLE : View.GONE);
        mShelfScroll.setVisibility(isShelf() && !empty ? View.VISIBLE : View.GONE);
        mGrid.setVisibility(!isShelf() && !empty ? View.VISIBLE : View.GONE);
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        int gap = Ui.dp(this, isList() ? 8 : 14);
        mGrid.setHorizontalSpacing(gap);
        mGrid.setVerticalSpacing(gap);
        if (isList()) {
            mGrid.setNumColumns(1);
        } else {
            mGrid.setNumColumns(GridView.AUTO_FIT);
            mGrid.setColumnWidth(mBaseColumn);
        }
        mViewIcon.setImageResource(isShelf() ? R.drawable.ic_e_shelf
            : isList() ? R.drawable.ic_e_list : R.drawable.ic_e_grid);
        mSortLabel.setText(EssentiaSettings.SORT_AZ.equals(mSort) ? R.string.lib_sort_az : R.string.lib_sort_recent);
        zoom(null);
        mAdapter.notifyDataSetChanged();
        rebuildShelf();
    }

    private void cycleView()
    {
        mView = isShelf() ? EssentiaSettings.VIEW_GRID
            : EssentiaSettings.VIEW_GRID.equals(mView) ? EssentiaSettings.VIEW_LIST : EssentiaSettings.VIEW_SHELF;
        EssentiaSettings.setViewMode(this, mView);
        applyViewMode();
        focusSelected();
        String name = getString(isShelf() ? R.string.lib_view_shelf
            : isList() ? R.string.lib_view_list : R.string.lib_view_grid);
        Toast.makeText(this, getString(R.string.lib_view, name), Toast.LENGTH_SHORT).show();
    }

    private void toggleSort()
    {
        mSort = EssentiaSettings.SORT_AZ.equals(mSort) ? EssentiaSettings.SORT_RECENT : EssentiaSettings.SORT_AZ;
        EssentiaSettings.setSortMode(this, mSort);
        mSelected = 0;
        reload();
    }

    private void zoom(View v)
    {
        if (mZoomed != null && mZoomed != v) {
            mZoomed.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(120).start();
        }
        mZoomed = null;
        if (v != null && !isList()) {
            v.animate().scaleX(1.07f).scaleY(1.07f).translationZ(Ui.dp(this, 8)).setDuration(150).start();
            mZoomed = v;
        }
    }

    // ------------------------------------------------------------ items

    private String detailOf(Library.Entry e)
    {
        String when = e.lastPlayed == 0 ? getString(R.string.lib_never_played)
            : getString(R.string.lib_played, DateUtils.getRelativeTimeSpanString(e.lastPlayed,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString());
        String mode = screenLabel(e.screenMode);
        String out = mode == null ? when : when + "  ·  " + mode;
        return EssentiaSettings.ENGINE_CLASSIC.equals(e.engine) ? out + "  ·  Ruby 1.8" : out;
    }

    private static final class Holder
    {
        ImageView cover;
        TextView title;
        TextView detail;
        TextView badge;
    }

    /** A cover card: art, a soft shade and the name on top. */
    private View newCard()
    {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setClipToOutline(true);

        Holder h = new Holder();
        h.cover = new ImageView(this);
        h.cover.setBackgroundColor(Ui.SURFACE_HI);
        card.addView(h.cover, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ 0x00000000, 0x00000000, 0xCC000000 }));
        card.addView(shade, match());

        h.title = Ui.text(this, "", 14, Ui.TEXT, true);
        h.title.setMaxLines(2);
        h.title.setEllipsize(TextUtils.TruncateAt.END);
        h.title.setShadowLayer(6, 0, 1, 0xFF000000);
        int p = Ui.dp(this, 10);
        h.title.setPadding(p, p, p, p);
        card.addView(h.title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        h.badge = Ui.text(this, "16:9", 11, Ui.BG, true);
        h.badge.setBackground(Ui.rounded(this, Ui.GOLD, 8));
        h.badge.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        blp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
        card.addView(h.badge, blp);

        card.setTag(h);
        return card;
    }

    private void bindCard(View card, Library.Entry e)
    {
        Holder h = (Holder) card.getTag();
        h.title.setText(e.displayTitle());
        String mode = screenLabel(e.screenMode);
        h.badge.setText(mode == null ? "" : mode);
        h.badge.setVisibility(mode == null ? View.GONE : View.VISIBLE);
        mCovers.load(e.path, e.cover, h.cover, R.drawable.ic_launcher_foreground);
    }

    private View newRow()
    {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(this, 0x991E2240, 14));
        int p = Ui.dp(this, 8);
        row.setPadding(p, p, p, p);

        Holder h = new Holder();
        h.cover = new ImageView(this);
        h.cover.setBackground(Ui.rounded(this, Ui.SURFACE_HI, 10));
        h.cover.setClipToOutline(true);
        row.addView(h.cover, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 84)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        h.title = Ui.text(this, "", 17, Ui.TEXT, true);
        h.title.setMaxLines(1);
        h.title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(h.title);
        h.detail = Ui.text(this, "", 13, Ui.TEXT_DIM, false);
        h.detail.setMaxLines(2);
        h.detail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(h.detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setTag(h);
        return row;
    }

    private final class GamesAdapter extends BaseAdapter
    {
        @Override public int getCount() { return mGames.size(); }
        @Override public Object getItem(int i) { return mGames.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return isList() ? 1 : 0; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent)
        {
            Library.Entry e = mGames.get(pos);
            if (isList()) {
                if (convert == null) convert = newRow();
                Holder h = (Holder) convert.getTag();
                h.title.setText(e.displayTitle());
                h.detail.setText(detailOf(e) + "\n" + new File(e.path).getParent());
                mCovers.load(e.path, e.cover, h.cover, R.drawable.ic_launcher_foreground);
                return convert;
            }
            if (convert == null) convert = newCard();
            int w = mGrid.getColumnWidth() > 0 ? mGrid.getColumnWidth() : mBaseColumn;
            convert.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w * 3 / 4));
            convert.setScaleX(1f);
            convert.setScaleY(1f);
            bindCard(convert, e);
            return convert;
        }
    }

    /** Builds the shelf cards (shelf view only). */
    private void rebuildShelf()
    {
        mShelf.removeAllViews();
        if (!isShelf()) return;

        int h = Ui.dp(this, isPortrait() ? 120 : 132);
        int w = h * 4 / 3;
        for (int i = 0; i < mGames.size(); i++) {
            final int pos = i;
            View card = newCard();
            bindCard(card, mGames.get(i));
            card.setFocusable(true);
            card.setClickable(true);
            card.setForeground(selectorDrawable());
            card.setOnClickListener(v -> play(pos));
            card.setOnLongClickListener(v -> { showOptions(pos); return true; });
            card.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    select(pos);
                    zoom(v);
                    v.post(() -> {
                        int center = v.getLeft() + v.getWidth() / 2 - mShelfScroll.getWidth() / 2;
                        mShelfScroll.smoothScrollTo(Math.max(0, center), 0);
                    });
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.rightMargin = Ui.dp(this, 14);
            mShelf.addView(card, lp);
        }
    }

    private void focusSelected()
    {
        if (mGames.isEmpty()) return;
        int pos = Math.min(mSelected, mGames.size() - 1);
        if (isShelf()) {
            View v = mShelf.getChildAt(pos);
            if (v != null) v.post(v::requestFocus);
        } else {
            mGrid.setSelection(pos);
            mGrid.requestFocus();
        }
    }

    private void reload()
    {
        mGames.clear();
        mGames.addAll(Library.load(this));
        if (EssentiaSettings.SORT_AZ.equals(mSort)) {
            Collections.sort(mGames, (a, b) -> a.displayTitle().compareToIgnoreCase(b.displayTitle()));
        }
        mCount.setText(getResources().getQuantityString(R.plurals.lib_count, mGames.size(), mGames.size()));
        if (mSelected >= mGames.size()) mSelected = Math.max(0, mGames.size() - 1);
        applyViewMode();
        select(mSelected);
        focusSelected();
    }

    /** A game was selected: update the featured area, the backdrop and the second screen. */
    private void select(int pos)
    {
        if (mGames.isEmpty()) {
            mBackdrop.setImageDrawable(null);
            updateSecondScreen();
            return;
        }
        mSelected = Math.max(0, Math.min(pos, mGames.size() - 1));
        Library.Entry e = mGames.get(mSelected);
        mHeroTitle.setText(e.displayTitle());
        mHeroDetail.setText(detailOf(e));
        mCovers.load(e.path, e.cover, mHeroCover, R.drawable.ic_launcher_foreground);
        mCovers.load(e.path, e.cover, mBackdrop, 0);
        mBackdrop.setAlpha(0f);
        mBackdrop.animate().alpha(0.45f).setDuration(250).start();
        updateSecondScreen();
    }

    // ------------------------------------------------------------ second screen

    private void showSecondScreen()
    {
        if (!EssentiaSettings.DUAL_AUTO.equals(EssentiaSettings.dualScreen(this))) return;
        if (mSecond == null) {
            mSecond = SecondScreen.show(this, getWindowManager().getDefaultDisplay());
        }
        updateSecondScreen();
    }

    private void updateSecondScreen()
    {
        if (mSecond == null) return;
        if (mGames.isEmpty()) {
            mSecond.showGame(getString(R.string.app_name), null, null);
            return;
        }
        Library.Entry e = mGames.get(Math.min(mSelected, mGames.size() - 1));
        File saves = Saves.dirFor(this, new File(e.path), e.displayTitle());
        String detail = detailOf(e) + "\n" + getString(R.string.second_saves, saves.getAbsolutePath());
        mSecond.showGame(e.displayTitle(), detail, null);
        mCovers.load(e.path, e.cover, mSecond.coverView(), R.drawable.ic_launcher_foreground);
        mCovers.load(e.path, e.cover, mSecond.backdropView(), 0);
    }

    // ------------------------------------------------------------ actions

    private void play(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);
        File dir = new File(e.path);
        if (!dir.isDirectory() || !Games.isGameFolder(dir)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.displayTitle())
                .setMessage(R.string.lib_missing)
                .setPositiveButton(R.string.lib_remove, (d, w) -> { Library.remove(this, e.path); reload(); })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }
        if (!EssentiaSettings.SORT_AZ.equals(mSort)) mSelected = 0; // becomes the most recent
        Games.startGame(this, dir, false);
    }

    private void showOptions(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.lib_play)); actions.add(() -> play(pos));
        labels.add(getString(R.string.opt_cover)); actions.add(() -> pickCover(e));
        if (!e.cover.isEmpty()) { labels.add(getString(R.string.opt_cover_reset)); actions.add(() -> resetCover(e)); }
        labels.add(getString(R.string.opt_rename)); actions.add(() -> rename(e));
        labels.add(getString(R.string.opt_screen)); actions.add(() -> chooseScreen(e));
        labels.add(getString(R.string.opt_engine)); actions.add(() -> chooseEngine(e));
        labels.add(getString(R.string.opt_import)); actions.add(() -> importSaves(e));
        labels.add(getString(R.string.lib_remove)); actions.add(() -> { Library.remove(this, e.path); reload(); });

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(e.displayTitle())
            .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
            .show();
    }

    private void rename(Library.Entry e)
    {
        EditText input = new EditText(this);
        input.setText(e.displayTitle());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.opt_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String t = input.getText().toString().trim();
                e.title = t.isEmpty() ? Games.readTitle(new File(e.path)) : t;
                Library.update(this, e);
                reload();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /** Short badge for a game with its own screen format, or null when it follows the general one. */
    private String screenLabel(String mode)
    {
        if (mode == null || mode.isEmpty()) return null;
        switch (mode) {
            case EssentiaSettings.SCREEN_ORIGINAL: return "4:3";
            case EssentiaSettings.SCREEN_STRETCH: return getString(R.string.scr_badge_stretch);
            case EssentiaSettings.SCREEN_WIDE: return "16:9";
            case EssentiaSettings.SCREEN_AMBIENT: return getString(R.string.scr_badge_ambient);
            default: return null;
        }
    }

    private void chooseEngine(Library.Entry e)
    {
        boolean available = Games.classicEngineAvailable(this);
        final String[] values = { EssentiaSettings.ENGINE_MODERN, EssentiaSettings.ENGINE_CLASSIC };
        final String[] labels = { getString(R.string.eng_modern), getString(R.string.eng_classic) };
        int checked = EssentiaSettings.ENGINE_CLASSIC.equals(e.engine) ? 1 : 0;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.opt_engine)
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                if (which == 1 && !available) {
                    Toast.makeText(this, R.string.eng_missing, Toast.LENGTH_LONG).show();
                    return;
                }
                e.engine = values[which];
                Library.update(this, e);
                d.dismiss();
                reload();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void chooseScreen(Library.Entry e)
    {
        final String[] values = { "", EssentiaSettings.SCREEN_WIDE, EssentiaSettings.SCREEN_AMBIENT,
            EssentiaSettings.SCREEN_ORIGINAL, EssentiaSettings.SCREEN_STRETCH };
        final String[] labels = { getString(R.string.scr_general), getString(R.string.scr_wide),
            getString(R.string.scr_ambient), getString(R.string.scr_original), getString(R.string.scr_stretch) };
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(e.screenMode)) checked = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.opt_screen)
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                e.screenMode = values[which];
                e.widescreen = EssentiaSettings.SCREEN_WIDE.equals(e.screenMode);
                Library.update(this, e);
                d.dismiss();
                reload();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void pickCover(Library.Entry e)
    {
        mPending = e;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK_COVER);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.err_no_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void resetCover(Library.Entry e)
    {
        if (!e.cover.isEmpty()) //noinspection ResultOfMethodCallIgnored
            new File(e.cover).delete();
        e.cover = "";
        Library.update(this, e);
        reload();
    }

    private void saveCover(Library.Entry e, Uri uri)
    {
        File dir = new File(getFilesDir(), "covers");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File dest = new File(dir, Integer.toHexString(e.path.hashCode()) + "-" + System.currentTimeMillis() + ".img");
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("no data");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.opt_cover_failed, Toast.LENGTH_LONG).show();
            return;
        }
        if (!e.cover.isEmpty()) //noinspection ResultOfMethodCallIgnored
            new File(e.cover).delete();
        e.cover = dest.getAbsolutePath();
        Library.update(this, e);
        reload();
    }

    // ------------------------------------------------------------ saves

    private File saveDirOf(Library.Entry e)
    {
        return Saves.dirFor(this, new File(e.path), e.displayTitle());
    }

    private void importSaves(Library.Entry e)
    {
        mPending = e;
        File target = saveDirOf(e);
        List<File> legacy = Saves.legacy(this);

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!legacy.isEmpty()) {
            labels.add(getString(R.string.imp_legacy, legacy.size()));
            actions.add(() -> chooseFiles(legacy, target));
        }
        labels.add(getString(R.string.imp_folder));
        actions.add(() -> pickTree(REQ_IMPORT_FOLDER));
        labels.add(getString(R.string.imp_files));
        actions.add(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(i, REQ_IMPORT_FILES); }
            catch (Exception ex) { Toast.makeText(this, R.string.err_no_picker, Toast.LENGTH_LONG).show(); }
        });

        TextView info = Ui.text(this, getString(R.string.imp_info, target.getAbsolutePath()), 14, Ui.TEXT_DIM, false);
        int p = Ui.dp(this, 20);
        info.setPadding(p, Ui.dp(this, 8), p, 0);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.imp_title, e.displayTitle()))
            .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
            .setNegativeButton(R.string.cancel, null)
            .setView(info)
            .show();
    }

    /** Lets the user pick which of the found save files to import. */
    private void chooseFiles(List<File> files, File target)
    {
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.imp_none, Toast.LENGTH_LONG).show();
            return;
        }
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        String[] names = new String[files.size()];
        boolean[] checked = new boolean[files.size()];
        for (int i = 0; i < files.size(); i++) {
            names[i] = files.get(i).getName() + "   (" + fmt.format(new Date(files.get(i).lastModified())) + ")";
            checked[i] = true;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.imp_choose)
            .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
            .setPositiveButton(R.string.imp_do, (d, w) -> {
                List<File> chosen = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) if (checked[i]) chosen.add(files.get(i));
                int n = Saves.copyFiles(chosen, target);
                Toast.makeText(this, getResources().getQuantityString(R.plurals.imp_done, n, n), Toast.LENGTH_LONG).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void pickTree(int request)
    {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), request);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.err_no_picker, Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri)
    {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "partida" : last.substring(last.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------ adding games

    private void openFolderPicker()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        String last = getSharedPreferences(EssentiaSettings.PREFS, MODE_PRIVATE).getString(PREF_LAST_TREE, null);
        if (last != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(last));
        }
        try {
            Toast.makeText(this, R.string.lib_pick_hint, Toast.LENGTH_LONG).show();
            startActivityForResult(intent, REQ_PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, R.string.err_no_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ALL_FILES) {
            if (Games.hasAllFilesAccess()) Updater.check(this, () -> { }, false);
            else finish();
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;

        switch (requestCode) {
            case REQ_PICK_FOLDER: {
                Uri tree = data.getData();
                if (tree == null) return;
                getSharedPreferences(EssentiaSettings.PREFS, MODE_PRIVATE).edit()
                    .putString(PREF_LAST_TREE, tree.toString()).apply();
                String path = Games.pathFromUri(this, tree);
                if (path == null) {
                    Toast.makeText(this, getString(R.string.err_not_a_game, tree.toString()), Toast.LENGTH_LONG).show();
                    return;
                }
                addGamesFrom(new File(path));
                break;
            }
            case REQ_PICK_COVER:
                if (mPending != null && data.getData() != null) saveCover(mPending, data.getData());
                break;
            case REQ_IMPORT_FOLDER: {
                if (mPending == null || data.getData() == null) return;
                String path = Games.pathFromUri(this, data.getData());
                List<File> found = path == null ? new ArrayList<>() : Saves.findIn(new File(path));
                chooseFiles(found, saveDirOf(mPending));
                break;
            }
            case REQ_IMPORT_FILES: {
                if (mPending == null) return;
                List<Uri> uris = new ArrayList<>();
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                List<String> names = new ArrayList<>();
                for (Uri u : uris) names.add(displayName(u));
                int n = Saves.copyUris(this, uris, names, saveDirOf(mPending));
                Toast.makeText(this, getResources().getQuantityString(R.plurals.imp_done, n, n), Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    private void addGamesFrom(File root)
    {
        Toast.makeText(this, R.string.lib_scanning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<File> found = Games.findGames(root);
            int added = 0;
            for (File f : found) if (Library.add(this, f)) added++;
            if (added > 0) Shortcuts.refresh(this);
            final int count = added, total = found.size();
            runOnUiThread(() -> {
                if (total == 0) {
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.app_name)
                        .setMessage(getString(R.string.lib_none_found, root.getAbsolutePath()))
                        .setPositiveButton(R.string.choose_folder, (d, w) -> openFolderPicker())
                        .setNegativeButton(R.string.close, null)
                        .show();
                } else {
                    Toast.makeText(this, getResources().getQuantityString(R.plurals.lib_added, count, count),
                        Toast.LENGTH_LONG).show();
                    mSelected = 0;
                    reload();
                }
            });
        }, "EssentiaScan").start();
    }

    // ------------------------------------------------------------ controller

    @Override
    protected boolean wantsButton(int keyCode)
    {
        return keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_BUTTON_X
            || keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
            || keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1;
    }

    @Override
    protected boolean onControllerButton(int keyCode)
    {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (!mGames.isEmpty()) showOptions(mSelected);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                openFolderPicker();
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                cycleView();
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                toggleSort();
                return true;
        }
        return false;
    }
}
