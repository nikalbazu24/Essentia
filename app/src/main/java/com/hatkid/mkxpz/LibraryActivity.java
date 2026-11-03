package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
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
import android.widget.Button;
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

/** Essentia's home screen: the game library. */
public class LibraryActivity extends ControllerActivity
{
    private static final int REQ_ALL_FILES = 1;
    private static final int REQ_PICK_FOLDER = 2;
    private static final int REQ_PICK_COVER = 3;
    private static final int REQ_IMPORT_FILES = 4;
    private static final int REQ_IMPORT_FOLDER = 5;
    private static final String PREF_LAST_TREE = "last_tree_uri";

    private final List<Library.Entry> mGames = new ArrayList<>();
    private GamesAdapter mAdapter;
    private GridView mGrid;
    private CoverLoader mCovers;
    private SecondScreen mSecond;
    private TextView mCount;
    private Button mViewButton;
    private Button mSortButton;
    private View mZoomed;
    private int mSelected = 0;
    private int mBaseColumn;
    private String mView;
    private String mSort;
    /** Game waiting for a picked cover or save files. */
    private Library.Entry mPending;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        mBaseColumn = Ui.dp(this, widthDp >= 900 ? 220 : (widthDp >= 600 ? 190 : 150));
        mCovers = new CoverLoader(mBaseColumn * 2);
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

    // ------------------------------------------------------------------ UI

    private View buildUi()
    {
        int pad = Ui.dp(this, 18);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, Ui.dp(this, 8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ Ui.SURFACE, Ui.BG, Ui.BG });
        root.setBackground(bg);

        // Header: icon, name + count, buttons
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setBackground(Ui.rounded(this, Ui.BG, 14));
        int iconSize = Ui.dp(this, 48);
        header.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        names.addView(Ui.text(this, getString(R.string.app_name), 24, Ui.TEXT, true));
        mCount = Ui.text(this, "", 13, Ui.TEXT_DIM, false);
        names.addView(mCount);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button add = Ui.button(this, getString(R.string.lib_add), true);
        add.setOnClickListener(v -> openFolderPicker());
        buttons.addView(add);
        mViewButton = headerButton(buttons, "", v -> cycleView());
        mSortButton = headerButton(buttons, "", v -> toggleSort());
        headerButton(buttons, getString(R.string.lib_controls), v -> startActivity(new Intent(this, ControlsActivity.class)));
        headerButton(buttons, getString(R.string.lib_settings), v -> startActivity(new Intent(this, SettingsActivity.class)));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(buttons);
        header.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Games + empty state
        FrameLayout body = new FrameLayout(this);

        mGrid = new GridView(this);
        mGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        int gap = Ui.dp(this, 14);
        mGrid.setPadding(Ui.dp(this, 6), gap, Ui.dp(this, 6), gap);
        mGrid.setClipToPadding(false);
        mGrid.setClipChildren(false);
        mGrid.setDrawSelectorOnTop(true);
        mGrid.setSelector(selectorDrawable());
        mGrid.setFocusable(true);
        mAdapter = new GamesAdapter();
        mGrid.setAdapter(mAdapter);
        mGrid.setOnItemClickListener((p, v, pos, id) -> play(pos));
        mGrid.setOnItemLongClickListener((p, v, pos, id) -> { showOptions(pos); return true; });
        mGrid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                mSelected = pos;
                zoom(v);
                updateSecondScreen();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { zoom(null); }
        });
        body.addView(mGrid, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View empty = buildEmptyState();
        body.addView(empty, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mGrid.setEmptyView(empty);

        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hints = Ui.text(this, getString(R.string.lib_hints), 13, Ui.TEXT_DIM, false);
        hints.setGravity(Gravity.CENTER);
        root.addView(hints, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private Button headerButton(LinearLayout parent, String text, View.OnClickListener l)
    {
        Button b = Ui.button(this, text, false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private View buildEmptyState()
    {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int pad = Ui.dp(this, 24);
        col.setPadding(pad, pad, pad, pad);

        ImageView flask = new ImageView(this);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        int s = Ui.dp(this, 120);
        col.addView(flask, new LinearLayout.LayoutParams(s, s));

        TextView title = Ui.text(this, getString(R.string.lib_empty_title), 20, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView body = Ui.text(this, getString(R.string.lib_empty_text), 15, Ui.TEXT_DIM, false);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        body.setMaxWidth(Ui.dp(this, 460));
        col.addView(body);

        Button add = Ui.button(this, getString(R.string.lib_add), true);
        add.setOnClickListener(v -> openFolderPicker());
        col.addView(add);
        return col;
    }

    // ------------------------------------------------------------ view modes

    private boolean isList()
    {
        return EssentiaSettings.VIEW_LIST.equals(mView);
    }

    private int columnWidth()
    {
        return EssentiaSettings.VIEW_BIG.equals(mView) ? mBaseColumn * 8 / 5 : mBaseColumn;
    }

    private void applyViewMode()
    {
        int gap = Ui.dp(this, isList() ? 8 : 14);
        mGrid.setHorizontalSpacing(gap);
        mGrid.setVerticalSpacing(gap);
        if (isList()) {
            mGrid.setNumColumns(1);
        } else {
            mGrid.setNumColumns(GridView.AUTO_FIT);
            mGrid.setColumnWidth(columnWidth());
        }
        int label = isList() ? R.string.lib_view_list
            : EssentiaSettings.VIEW_BIG.equals(mView) ? R.string.lib_view_big : R.string.lib_view_grid;
        mViewButton.setText(getString(R.string.lib_view, getString(label)));
        mSortButton.setText(getString(R.string.lib_sort,
            getString(EssentiaSettings.SORT_AZ.equals(mSort) ? R.string.lib_sort_az : R.string.lib_sort_recent)));
        zoom(null);
        mAdapter.notifyDataSetChanged();
    }

    private void cycleView()
    {
        mView = EssentiaSettings.VIEW_GRID.equals(mView) ? EssentiaSettings.VIEW_BIG
            : EssentiaSettings.VIEW_BIG.equals(mView) ? EssentiaSettings.VIEW_LIST : EssentiaSettings.VIEW_GRID;
        EssentiaSettings.setViewMode(this, mView);
        applyViewMode();
    }

    private void toggleSort()
    {
        mSort = EssentiaSettings.SORT_AZ.equals(mSort) ? EssentiaSettings.SORT_RECENT : EssentiaSettings.SORT_AZ;
        EssentiaSettings.setSortMode(this, mSort);
        applyViewMode();
        reload();
    }

    private void zoom(View v)
    {
        if (mZoomed != null && mZoomed != v) {
            mZoomed.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
        mZoomed = null;
        if (v != null && !isList() && mGrid.hasFocus()) {
            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
            mZoomed = v;
        }
    }

    // ------------------------------------------------------------ items

    private String detailOf(Library.Entry e)
    {
        String when = e.lastPlayed == 0 ? getString(R.string.lib_never_played)
            : getString(R.string.lib_played, DateUtils.getRelativeTimeSpanString(e.lastPlayed,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString());
        return e.widescreen ? when + "  ·  16:9" : when;
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
            return isList() ? row(e, convert) : card(e, convert);
        }
    }

    private static final class Holder
    {
        ImageView cover;
        TextView title;
        TextView detail;
        TextView badge;
    }

    private View card(Library.Entry e, View convert)
    {
        Holder h;
        if (convert == null) {
            FrameLayout card = new FrameLayout(this);
            card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
            card.setClipToOutline(true);

            h = new Holder();
            h.cover = new ImageView(this);
            h.cover.setBackgroundColor(Ui.SURFACE_HI);
            card.addView(h.cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ 0x00000000, 0x99000000, 0xE6000000 }));
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            card.addView(shade, slp);

            h.title = Ui.text(this, "", 15, Ui.TEXT, true);
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
            convert = card;
        } else {
            h = (Holder) convert.getTag();
        }
        int w = mGrid.getColumnWidth() > 0 ? mGrid.getColumnWidth() : columnWidth();
        convert.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w * 3 / 4));
        convert.setScaleX(1f);
        convert.setScaleY(1f);
        h.title.setText(e.displayTitle());
        h.badge.setVisibility(e.widescreen ? View.VISIBLE : View.GONE);
        mCovers.load(e.path, e.cover, h.cover, R.drawable.ic_launcher_foreground);
        return convert;
    }

    private View row(Library.Entry e, View convert)
    {
        Holder h;
        if (convert == null) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.rounded(this, Ui.SURFACE, 14));
            int p = Ui.dp(this, 8);
            row.setPadding(p, p, p, p);

            h = new Holder();
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
            convert = row;
        } else {
            h = (Holder) convert.getTag();
        }
        h.title.setText(e.displayTitle());
        h.detail.setText(detailOf(e) + "\n" + new File(e.path).getParent());
        mCovers.load(e.path, e.cover, h.cover, R.drawable.ic_launcher_foreground);
        return convert;
    }

    private void reload()
    {
        mGames.clear();
        mGames.addAll(Library.load(this));
        if (EssentiaSettings.SORT_AZ.equals(mSort)) {
            Collections.sort(mGames, (a, b) -> a.displayTitle().compareToIgnoreCase(b.displayTitle()));
        }
        mAdapter.notifyDataSetChanged();
        mCount.setText(getResources().getQuantityString(R.plurals.lib_count, mGames.size(), mGames.size()));
        if (mSelected >= mGames.size()) mSelected = Math.max(0, mGames.size() - 1);
        if (!mGames.isEmpty()) {
            mGrid.setSelection(mSelected);
            mGrid.requestFocus();
        }
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
        mSecond.showGame(e.displayTitle(), detailOf(e), mCovers.cached(e.path, e.cover));
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
        labels.add(getString(e.widescreen ? R.string.opt_wide_off : R.string.opt_wide_on));
        actions.add(() -> toggleWide(e));
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

    private void toggleWide(Library.Entry e)
    {
        e.widescreen = !e.widescreen;
        Library.update(this, e);
        if (e.widescreen) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.opt_wide_on)
                .setMessage(R.string.opt_wide_info)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
        reload();
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
            case KeyEvent.KEYCODE_BUTTON_Y: {
                int pos = mGrid.getSelectedItemPosition();
                if (!mGames.isEmpty()) showOptions(pos >= 0 ? pos : mSelected);
                return true;
            }
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
