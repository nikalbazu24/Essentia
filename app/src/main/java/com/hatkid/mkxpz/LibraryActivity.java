package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.CoverLoader;
import com.hatkid.mkxpz.ui.SecondScreen;
import com.hatkid.mkxpz.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Essentia's home screen: the game library. */
public class LibraryActivity extends ControllerActivity
{
    private static final int REQ_ALL_FILES = 1;
    private static final int REQ_PICK_FOLDER = 2;
    private static final String PREF_LAST_TREE = "last_tree_uri";

    private final List<Library.Entry> mGames = new ArrayList<>();
    private GamesAdapter mAdapter;
    private GridView mGrid;
    private CoverLoader mCovers;
    private SecondScreen mSecond;
    private int mSelected = 0;
    private int mColumnWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        mColumnWidth = Ui.dp(this, widthDp >= 900 ? 220 : (widthDp >= 600 ? 190 : 150));
        mCovers = new CoverLoader(mColumnWidth * 2);

        setContentView(buildUi());

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
        root.setPadding(pad, pad, pad, Ui.dp(this, 10));

        // Header: icon, name, buttons
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(Ui.rounded(this, Ui.SURFACE, 12));
        int iconSize = Ui.dp(this, 44);
        header.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView name = Ui.text(this, getString(R.string.app_name), 24, Ui.TEXT, true);
        name.setPadding(Ui.dp(this, 12), 0, 0, 0);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = Ui.button(this, getString(R.string.lib_add), true);
        add.setOnClickListener(v -> openFolderPicker());
        header.addView(add);

        Button settings = Ui.button(this, getString(R.string.lib_settings), false);
        settings.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 10);
        header.addView(settings, slp);

        root.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Grid of games + empty state
        FrameLayout body = new FrameLayout(this);

        mGrid = new GridView(this);
        mGrid.setNumColumns(GridView.AUTO_FIT);
        mGrid.setColumnWidth(mColumnWidth);
        mGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        int gap = Ui.dp(this, 14);
        mGrid.setHorizontalSpacing(gap);
        mGrid.setVerticalSpacing(gap);
        mGrid.setPadding(0, gap, 0, gap);
        mGrid.setClipToPadding(false);
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
                updateSecondScreen();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        body.addView(mGrid, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View empty = buildEmptyState();
        body.addView(empty, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mGrid.setEmptyView(empty);

        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Controller hints
        TextView hints = Ui.text(this, getString(R.string.lib_hints), 13, Ui.TEXT_DIM, false);
        hints.setGravity(Gravity.CENTER);
        root.addView(hints, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private android.graphics.drawable.Drawable selectorDrawable()
    {
        android.graphics.drawable.GradientDrawable focus = Ui.rounded(this, 0x00000000, 14);
        focus.setStroke(Ui.dp(this, 3), Ui.ACCENT_LIGHT);
        android.graphics.drawable.GradientDrawable press = Ui.rounded(this, 0x408A5CF6, 14);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, press);
        s.addState(new int[]{android.R.attr.state_focused}, focus);
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
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

    private final class GamesAdapter extends BaseAdapter
    {
        @Override public int getCount() { return mGames.size(); }
        @Override public Object getItem(int i) { return mGames.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent)
        {
            Holder h;
            if (convert == null) {
                LinearLayout card = new LinearLayout(LibraryActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.rounded(LibraryActivity.this, Ui.SURFACE, 14));
                card.setClipToOutline(true);

                h = new Holder();
                h.cover = new ImageView(LibraryActivity.this);
                h.cover.setBackgroundColor(Ui.SURFACE_HI);
                int w = mGrid.getColumnWidth() > 0 ? mGrid.getColumnWidth() : mColumnWidth;
                card.addView(h.cover, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, w * 3 / 4));

                h.title = Ui.text(LibraryActivity.this, "", 14, Ui.TEXT, true);
                h.title.setMaxLines(2);
                h.title.setEllipsize(TextUtils.TruncateAt.END);
                int p = Ui.dp(LibraryActivity.this, 10);
                h.title.setPadding(p, p, p, p);
                h.title.setMinLines(2);
                card.addView(h.title);

                card.setTag(h);
                convert = card;
            } else {
                h = (Holder) convert.getTag();
            }

            Library.Entry e = mGames.get(pos);
            h.title.setText(e.title.isEmpty() ? new File(e.path).getName() : e.title);
            mCovers.load(e.path, h.cover, R.drawable.ic_launcher_foreground);
            return convert;
        }
    }

    private static final class Holder
    {
        ImageView cover;
        TextView title;
    }

    private void reload()
    {
        mGames.clear();
        mGames.addAll(Library.load(this));
        mAdapter.notifyDataSetChanged();
        if (mSelected >= mGames.size()) mSelected = Math.max(0, mGames.size() - 1);
        if (!mGames.isEmpty()) {
            mGrid.setSelection(mSelected);
            mGrid.requestFocus();
        }
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
            mSecond.showGame(getString(R.string.app_name), null);
            return;
        }
        Library.Entry e = mGames.get(Math.min(mSelected, mGames.size() - 1));
        mSecond.showGame(e.title, mCovers.cached(e.path));
    }

    // ------------------------------------------------------------ actions

    private void play(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);
        File dir = new File(e.path);
        if (!dir.isDirectory() || !Games.isGameFolder(dir)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.title)
                .setMessage(R.string.lib_missing)
                .setPositiveButton(R.string.lib_remove, (d, w) -> { Library.remove(this, e.path); reload(); })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }
        mSelected = 0; // it becomes the most recent game
        Games.startGame(this, dir, false);
    }

    private void showOptions(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);
        String[] items = { getString(R.string.lib_play), getString(R.string.lib_remove) };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(e.title)
            .setItems(items, (d, which) -> {
                if (which == 0) play(pos);
                else { Library.remove(this, e.path); reload(); }
            })
            .show();
    }

    private void openSettings()
    {
        startActivity(new Intent(this, SettingsActivity.class));
    }

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
            if (Games.hasAllFilesAccess()) {
                Updater.check(this, () -> { }, false);
            } else {
                finish();
            }
            return;
        }

        if (requestCode == REQ_PICK_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            getSharedPreferences(EssentiaSettings.PREFS, MODE_PRIVATE).edit()
                .putString(PREF_LAST_TREE, tree.toString()).apply();
            String path = Games.pathFromUri(this, tree);
            if (path == null) {
                Toast.makeText(this, getString(R.string.err_not_a_game, tree.toString()), Toast.LENGTH_LONG).show();
                return;
            }
            addGamesFrom(new File(path));
        }
    }

    private void addGamesFrom(File root)
    {
        Toast.makeText(this, R.string.lib_scanning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<File> found = Games.findGames(root);
            int added = 0;
            for (File f : found) if (Library.add(this, f)) added++;
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
            || keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
    }

    @Override
    protected boolean onControllerButton(int keyCode)
    {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (!mGames.isEmpty()) showOptions(mGrid.getSelectedItemPosition() >= 0
                    ? mGrid.getSelectedItemPosition() : mSelected);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                openFolderPicker();
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                openSettings();
                return true;
        }
        return false;
    }
}
