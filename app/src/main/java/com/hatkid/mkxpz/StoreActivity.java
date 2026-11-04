package com.hatkid.mkxpz;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hatkid.mkxpz.ui.ControllerActivity;
import com.hatkid.mkxpz.ui.CoverLoader;
import com.hatkid.mkxpz.ui.Ui;
import com.hatkid.mkxpz.ui.UpdateFlow;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The fan game store and the updates of every game.
 *
 * "Explore": the catalog, with pictures, language, Essentia compatibility and
 * a button to the creator's official page (Essentia doesn't host games). When
 * the game is downloaded as a .zip, Essentia finds it in Downloads, installs it
 * in the games folder and adds it to the library.
 *
 * "My games": every game in the library with its version and the latest one,
 * and "Update all".
 */
public class StoreActivity extends ControllerActivity
{
    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_GAME = "game";   // a library path: open its update
    private static final int REQ_ZIP = 41;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mPool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> mImages = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private CoverLoader mCovers;

    private List<Catalog.Game> mGames = new ArrayList<>();
    private int mTab = 0;            // 0 explore, 1 my games
    private int mFilter = 0;         // 0 all, 1 Spanish, 2 tested
    private boolean mShowFilters;
    private int mSource = 0;         // 0 all, otherwise Catalog.SOURCES index + 1
    private Catalog.Game mDetail;    // shown game, or null for the list
    private TextView mUpdatesTab;
    private LinearLayout mContent;
    private ScrollView mScroll;
    private final List<Catalog.Update> mUpdates = new ArrayList<>();
    private boolean mChecking;
    /** What a zip picked with the file picker is for. */
    private Catalog.Game mZipFor;
    private String mZipForPath;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mCovers = new CoverLoader(Ui.dp(this, 120));
        mTab = getIntent().getIntExtra(EXTRA_TAB, 0);
        if(savedInstanceState!=null){mTab=savedInstanceState.getInt("tab",mTab);mFilter=savedInstanceState.getInt("filter",0);mSource=savedInstanceState.getInt("source",0);mShowFilters=savedInstanceState.getBoolean("filters",false);}
        mGames = Catalog.cached(this);
        showList();
        mPool.execute(() -> {
            List<Catalog.Game> fresh = Catalog.refresh(this, false);
            Community.refresh(this);
            mMain.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                mGames = fresh;
                if (mDetail == null) showList();
            });
        });
        loadPendingUpdates();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("tab",mTab);out.putInt("filter",mFilter);out.putInt("source",mSource);out.putBoolean("filters",mShowFilters);super.onSaveInstanceState(out);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        UpdateService.setListener(() -> mMain.post(() -> {
            if (!isFinishing() && mDetail == null && mTab == 1) fillContent();
        }));
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        UpdateService.setListener(null);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mPool.shutdownNow();
    }

    @Override
    public void onBackPressed()
    {
        if (mDetail != null) {
            mDetail = null;
            showList();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------ list

    private void showList()
    {
        mDetail = null;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, Ui.dp(this, 14), pad, 0);
        root.addView(col, match());

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        names.addView(Ui.text(this, "Descubrir", 26, Ui.TEXT, true));
        names.addView(Ui.text(this, getResources().getQuantityString(R.plurals.store_count, mGames.size(), mGames.size()),
            12, Ui.TEXT_DIM, false));
        bar.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(bar);
        GridLayout tabs = actionGrid(2);
        TextView explore = pill(getString(R.string.store_tab_explore), mTab == 0);
        explore.setOnClickListener(v -> { mTab = 0; showList(); });
        addAction(tabs, explore);
        mUpdatesTab = pill(updatesTabLabel(), mTab == 1);
        mUpdatesTab.setOnClickListener(v -> { mTab = 1; showList(); });
        addAction(tabs, mUpdatesTab);
        col.addView(tabs);

        mScroll = new ScrollView(this);
        mScroll.setFillViewport(true);
        mScroll.setClipToPadding(false);
        mScroll.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 20));
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mContent, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        fillContent();
        (mTab == 0 ? explore : mUpdatesTab).requestFocus();
    }

    private String updatesTabLabel()
    {
        int n = 0;
        for (Catalog.Update u : mUpdates) if (u.newer()) n++;
        return n > 0 ? getString(R.string.store_tab_mine_n, n) : getString(R.string.store_tab_mine);
    }

    private void fillContent()
    {
        if (mContent == null) return;
        mContent.removeAllViews();
        if (mUpdatesTab != null) mUpdatesTab.setText(updatesTabLabel());
        if (mTab == 0) fillExplore(); else fillMine();
    }

    private void fillExplore()
    {
        TextView intro=Ui.text(this,"Encuentra tu próxima aventura",21,Ui.TEXT,true);mContent.addView(intro);
        mContent.addView(Ui.text(this,"Juegos de sus creadores · compatibilidad y procedencia en cada ficha",14,Ui.TEXT_DIM,false));
        android.widget.Button toggle=Ui.button(this,mShowFilters?"Ocultar filtros":"Filtrar juegos y comunidades",false);
        toggle.setOnClickListener(v->{mShowFilters=!mShowFilters;fillContent();});mContent.addView(toggle);
        LinearLayout filterContent=new LinearLayout(this);filterContent.setOrientation(LinearLayout.VERTICAL);
        filterContent.setVisibility(mShowFilters?View.VISIBLE:View.GONE);mContent.addView(filterContent);
        LinearLayout catalogContent=mContent;mContent=filterContent;
        sectionTitle(mContent, R.string.store_filter_games);
        GridLayout filters = actionGrid(getResources().getConfiguration().screenWidthDp >= 600 ? 4 : 2);
        String[] names = { getString(R.string.store_f_all), getString(R.string.store_f_es), getString(R.string.store_f_tested), getString(R.string.store_f_popular) };
        for (int i = 0; i < names.length; i++) {
            final int f = i;
            TextView chip = pill(names[i], mFilter == i);
            chip.setTag("filter:" + i);
            chip.setTextSize(13);
            chip.setOnClickListener(v -> { mFilter = f; fillContent(); focusChip("filter:" + f); });
            addAction(filters, chip);
        }
        mContent.addView(filters);

        sectionTitle(mContent, R.string.store_filter_source);
        GridLayout sources = actionGrid(3);
        for (int i = 0; i <= Catalog.SOURCES.length; i++) {
            final int source = i;
            TextView chip = pill(i == 0 ? getString(R.string.store_sources_all) : Catalog.SOURCES[i - 1].name,
                mSource == i);
            chip.setTag("source:" + i);
            chip.setTextSize(13);
            chip.setOnClickListener(v -> { mSource = source; fillContent(); focusChip("source:" + source); });
            addAction(sources, chip);
        }
        mContent.addView(sources);

        TextView communities = Ui.text(this, getString(R.string.store_communities), 16, Ui.TEXT, true);
        communities.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        mContent.addView(communities);
        mContent.addView(Ui.text(this, getString(R.string.store_communities_hint), 12, Ui.TEXT_DIM, false));
        GridLayout browse = actionGrid();
        for (Catalog.Source source : Catalog.SOURCES) {
            android.widget.Button button = Ui.button(this, getString(R.string.store_browse_source, source.name), false);
            button.setOnClickListener(v -> openPage(source.url));
            addAction(browse, button);
        }
        mContent.addView(browse);

        mContent=catalogContent;
        List<Catalog.Game> shown = new ArrayList<>();
        for (Catalog.Game g : mGames) {
            if (mFilter == 1 && !g.spanish()) continue;
            if (mFilter == 2 && !g.tested()) continue;
            if (mFilter == 3 && g.communityRank == 0) continue;
            if (mSource > 0 && Catalog.sourceFor(g.sourcePage) != Catalog.SOURCES[mSource - 1]) continue;
            shown.add(g);
        }
        if (mFilter == 3) {
            Collections.sort(shown, Comparator.comparingInt(g -> g.communityRank));
            mContent.addView(Ui.text(this, getString(R.string.store_popular_hint), 12, Ui.TEXT_DIM, false));
        }
        int perRow = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_PORTRAIT ? 2 : 3;
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190));
                lp.topMargin = Ui.dp(this, 12);
                mContent.addView(row, lp);
            }
            View card = gameCard(shown.get(i));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.rightMargin = (i % perRow == perRow - 1) ? 0 : Ui.dp(this, 12);
            row.addView(card, clp);
        }
        while (row != null && row.getChildCount() < perRow) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.rightMargin = row.getChildCount() == perRow - 1 ? 0 : Ui.dp(this, 12);
            row.addView(new View(this), clp);
        }
        if (shown.isEmpty()) {
            TextView none = Ui.text(this, getString(R.string.store_empty), 15, Ui.TEXT_DIM, false);
            none.setPadding(0, Ui.dp(this, 24), 0, 0);
            mContent.addView(none);
        }

        TextView info = Ui.text(this, getString(R.string.store_disclaimer), 12, Ui.TEXT_DIM, false);
        info.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        mContent.addView(info);
        GridLayout actions = actionGrid();
        android.widget.Button zip = Ui.button(this, getString(R.string.store_install_zip_any), false);
        zip.setOnClickListener(v -> chooseZip(null, null, null));
        addAction(actions, zip);
        android.widget.Button suggest = Ui.button(this, getString(R.string.store_suggest), false);
        suggest.setOnClickListener(v -> suggest());
        addAction(actions, suggest);
        mContent.addView(actions);
    }

    /** A catalog card: picture (or a colored one with the name), name, author and badges. */
    private View gameCard(Catalog.Game g)
    {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.focusable(this, Ui.SURFACE, 16));
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> showDetail(g));
        int inset = Ui.dp(this, 3);

        FrameLayout art = new FrameLayout(this);
        art.setBackground(coverBackground(g));
        art.setClipToOutline(true);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        alp.setMargins(inset, inset, inset, inset);
        card.addView(art, alp);
        art.addView(bigName(g, 22), match());
        ImageView img = new ImageView(this);
        img.setScaleType(g.cover.isEmpty() ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        art.addView(img, match());
        loadImage(g.cover.isEmpty() ? g.logo : g.cover, img);

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ 0x00000000, 0x22000000, 0xDD000000 }));
        art.addView(shade, match());

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        text.setPadding(p, p, p, p);
        TextView title = Ui.text(this, g.title, 16, Ui.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(6, 0, 1, 0xFF000000);
        text.addView(title);
        Catalog.Source source = Catalog.sourceFor(g.sourcePage);
        String sub = joinNonEmpty("  ·  ", g.author, langLabel(g), source == null ? "" : source.name);
        TextView s = Ui.text(this, sub, 12, 0xFFDDDAF0, false);
        s.setMaxLines(1);
        s.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(s);
        art.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        LinearLayout badges = new LinearLayout(this);
        badges.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
        if (Catalog.installed(this, g) != null) badges.addView(badge(getString(R.string.store_in_library), Ui.ACCENT_LIGHT), gapSmall());
        badges.addView(badge(statusLabel(g.status), statusColor(g.status)), gapSmall());
        art.addView(badges, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
        return card;
    }

    // ------------------------------------------------------------ my games

    private void loadPendingUpdates()
    {
        mUpdates.clear();
        for (Library.Entry e : Library.load(this)) {
            Catalog.Update u = Catalog.pending(this, e);
            if (u != null) mUpdates.add(u);
        }
        String forGame = getIntent().getStringExtra(EXTRA_GAME);
        if (forGame != null || Catalog.checkAllDue(this)) checkAll();
    }

    private void checkAll()
    {
        if (mChecking) return;
        mChecking = true;
        if (mDetail == null && mTab == 1) fillContent();
        mPool.execute(() -> {
            List<Catalog.Update> all = new ArrayList<>();
            List<Catalog.Game> games = Catalog.refresh(this, false);
            for (Library.Entry e : Library.load(this)) {
                Catalog.Update u = Catalog.check(this, e, games);
                if (u == null) {
                    Catalog.clearUpdate(this, e.path);
                    continue;
                }
                all.add(u);
            }
            // keep the badges in the library in step
            Catalog.checkAllStore(this, all);
            mMain.post(() -> {
                mChecking = false;
                if (isFinishing()) return;
                mUpdates.clear();
                mUpdates.addAll(all);
                if (mDetail == null) fillContent();
            });
        });
    }

    private Catalog.Update updateFor(Library.Entry e)
    {
        for (Catalog.Update u : mUpdates) if (u.path.equals(e.path)) return u;
        return null;
    }

    private void fillMine()
    {
        List<Library.Entry> lib = Library.load(this);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView explain = Ui.text(this, mChecking ? getString(R.string.store_checking)
            : getString(R.string.store_mine_text), 13, Ui.TEXT_DIM, false);
        head.addView(explain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button check = Ui.button(this, getString(R.string.store_check_now), false);
        check.setEnabled(!mChecking);
        check.setOnClickListener(v -> checkAll());
        head.addView(check, gap());
        int auto = 0;
        for (Catalog.Update u : mUpdates) if (u.newer() && !u.link.isEmpty() && !UpdateService.busy(u.path)) auto++;
        int manual = 0;
        for (Catalog.Update u : mUpdates) if (u.newer() && u.link.isEmpty()) manual++;
        if (auto + manual > 0) {
            android.widget.Button all = Ui.button(this, getString(R.string.store_update_all, auto + manual), true);
            all.setOnClickListener(v -> updateAll());
            head.addView(all, gap());
        }
        mContent.addView(head);

        if (lib.isEmpty()) {
            TextView none = Ui.text(this, getString(R.string.store_no_games), 15, Ui.TEXT_DIM, false);
            none.setPadding(0, Ui.dp(this, 24), 0, 0);
            mContent.addView(none);
            return;
        }
        // games with an update first
        List<Library.Entry> sorted = new ArrayList<>(lib);
        java.util.Collections.sort(sorted, (a, b) -> {
            Catalog.Update ua = updateFor(a), ub = updateFor(b);
            boolean na = ua != null && ua.newer(), nb = ub != null && ub.newer();
            if (na != nb) return na ? -1 : 1;
            return a.displayTitle().compareToIgnoreCase(b.displayTitle());
        });
        for (Library.Entry e : sorted) mContent.addView(gameRow(e), rowParams());
    }

    private View gameRow(Library.Entry e)
    {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(this, Ui.SURFACE, 14));
        int p = Ui.dp(this, 10);
        row.setPadding(p, p, p, p);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.rounded(this, Ui.SURFACE_HI, 10));
        cover.setClipToOutline(true);
        mCovers.load(e.path, e.cover, cover, R.drawable.ic_launcher_foreground);
        row.addView(cover, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 54)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        text.addView(Ui.text(this, e.displayTitle(), 16, Ui.TEXT, true));
        Catalog.Update u = updateFor(e);
        String current = u != null ? u.current : Catalog.localVersion(this, new File(e.path));
        String line;
        int color = Ui.TEXT_DIM;
        UpdateService.State busy = UpdateService.stateFor(e.path);
        if (busy != null) {
            int pct = busy.percent();
            line = getString(busy.step == 0 ? R.string.upd_downloading : R.string.upd_installing) + (pct >= 0 ? "  " + pct + "%" : "");
            color = Ui.GOLD;
        } else if (u != null && u.newer()) {
            line = getString(R.string.store_new_version, current.isEmpty() ? "?" : current, u.latest);
            color = Ui.GOLD;
        } else if (u != null) {
            line = getString(R.string.store_up_to_date, current.isEmpty() ? u.latest : current);
        } else if (!current.isEmpty()) {
            line = getString(R.string.store_version_only, current);
        } else {
            line = getString(R.string.store_no_info);
        }
        text.addView(Ui.text(this, line, 13, color, false));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Catalog.Game g = Catalog.match(mGames, e.displayTitle(), new File(e.path));
        if (busy == null && u != null && u.newer() && !u.link.isEmpty()) {
            android.widget.Button b = Ui.button(this, getString(R.string.store_update), true);
            b.setOnClickListener(v -> startUpdate(u));
            row.addView(b, gap());
        } else if (busy == null && u != null && u.newer()) {
            android.widget.Button b = Ui.button(this, getString(R.string.store_install_zip), true);
            b.setOnClickListener(v -> chooseZip(g, e.path, e.displayTitle()));
            row.addView(b, gap());
        }
        String page = u != null && !u.page.isEmpty() ? u.page : g != null ? g.page : "";
        if (!page.isEmpty()) {
            android.widget.Button b = Ui.button(this, getString(R.string.store_page_short), false);
            b.setOnClickListener(v -> openPage(page));
            row.addView(b, gap());
        }
        if (g != null) {
            android.widget.Button b = Ui.button(this, getString(R.string.store_details), false);
            b.setOnClickListener(v -> showDetail(g));
            row.addView(b, gap());
        }
        return row;
    }

    private void updateAll()
    {
        List<Catalog.Update> manual = new ArrayList<>();
        int started = 0;
        for (Catalog.Update u : mUpdates) {
            if (!u.newer() || UpdateService.busy(u.path)) continue;
            if (!u.link.isEmpty()) { startUpdate(u, false); started++; }
            else manual.add(u);
        }
        if (started > 0) Toast.makeText(this, getResources().getQuantityString(R.plurals.store_updating, started, started),
            Toast.LENGTH_LONG).show();
        fillContent();
        if (manual.isEmpty()) return;
        String[] names = new String[manual.size()];
        for (int i = 0; i < names.length; i++) names[i] = manual.get(i).title + "  →  " + manual.get(i).latest;
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.store_manual_title)
            .setItems(names, (x, w) -> {
                Catalog.Update u = manual.get(w);
                Library.Entry e = Library.find(this, u.path);
                if (!u.page.isEmpty()) openPage(u.page);
                else if (e != null) chooseZip(Catalog.match(mGames, e.displayTitle(), new File(e.path)), u.path, u.title);
            })
            .setNegativeButton(R.string.close, null)
            .create();
        UpdateFlow.controllerFriendly(d);
        d.show();
    }

    private void startUpdate(Catalog.Update u) { startUpdate(u, true); }

    private void startUpdate(Catalog.Update u, boolean tell)
    {
        askNotifications();
        UpdateService.start(getApplicationContext(), u.path, u.title, u.link, null, u.latest, false);
        if (tell) Toast.makeText(this, getString(R.string.upd_background, u.latest), Toast.LENGTH_LONG).show();
        fillContent();
    }

    // ------------------------------------------------------------ detail

    private void showDetail(Catalog.Game g)
    {
        mDetail = g;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // blurred backdrop of the picture
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.28f);
        if (Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(40f, 40f,
                android.graphics.Shader.TileMode.CLAMP));
        }
        root.addView(backdrop, match());
        loadImage(g.cover, backdrop);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        col.setPadding(pad, Ui.dp(this, 16), pad, pad);
        scroll.addView(col);
        root.addView(scroll, match());

        TextView back = pill("←  " + getString(R.string.store_back), false);
        back.setOnClickListener(v -> onBackPressed());
        LinearLayout top = new LinearLayout(this);
        top.addView(back);
        col.addView(top);

        boolean portrait = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(this, 14);
        col.addView(main, mlp);

        FrameLayout art = new FrameLayout(this);
        art.setBackground(coverBackground(g));
        art.setClipToOutline(true);
        art.setElevation(Ui.dp(this, 8));
        art.addView(bigName(g, 30), match());
        ImageView img = new ImageView(this);
        img.setScaleType(g.cover.isEmpty() ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        art.addView(img, match());
        loadImage(g.cover.isEmpty() ? g.logo : g.cover, img);
        main.addView(art, portrait
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 200))
            : new LinearLayout.LayoutParams(0, Ui.dp(this, 250), 1f));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(portrait ? 0 : Ui.dp(this, 22), portrait ? Ui.dp(this, 14) : 0, 0, 0);
        main.addView(info, portrait
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));

        if (!g.logo.isEmpty()) {
            ImageView logo = new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription(getString(R.string.store_logo, g.title));
            info.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 80)));
            loadImage(g.logo, logo);
        }
        TextView title = Ui.text(this, g.title, 26, Ui.TEXT, true);
        info.addView(title);
        if (!g.author.isEmpty()) info.addView(Ui.text(this, getString(R.string.store_by, g.author), 14, Ui.TEXT_DIM, false));
        Catalog.Source source = Catalog.sourceFor(g.sourcePage);
        if (source != null) info.addView(Ui.text(this, getString(R.string.store_source_credit, source.name),
            12, Ui.TEXT_DIM, false));

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        chips.addView(badge(statusLabel(g.status), statusColor(g.status)), gapSmall());
        if (!langLabel(g).isEmpty()) chips.addView(badge(langLabel(g), 0x33FFFFFF), gapSmall());
        if (!g.version.isEmpty()) chips.addView(badge("v" + g.version.replaceFirst("^[vV]", ""), 0x33FFFFFF), gapSmall());
        if (!g.essentials.isEmpty()) chips.addView(badge("Essentials " + g.essentials, 0x33FFFFFF), gapSmall());
        if (!g.size.isEmpty()) chips.addView(badge(g.size, 0x33FFFFFF), gapSmall());
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        info.addView(chipScroll);
        TextView compatibilitySummary = Ui.text(this, Community.summary(this,g.id), 14, Ui.TEXT_DIM, false);
        info.addView(compatibilitySummary);
        android.widget.Button contribute = Ui.button(this, "Aportar mi prueba de compatibilidad", false);
        contribute.setOnClickListener(v -> Community.contribute(this,g));
        info.addView(contribute);


        if (!g.description.isEmpty()) {
            TextView d = Ui.text(this, g.description, 15, Ui.TEXT, false);
            d.setPadding(0, Ui.dp(this, 8), 0, 0);
            d.setLineSpacing(0, 1.15f);
            info.addView(d);
        }
        if (!g.note.isEmpty()) {
            TextView n = Ui.text(this, getString(R.string.store_note, g.note), 13, Ui.TEXT_DIM, false);
            n.setPadding(0, Ui.dp(this, 8), 0, 0);
            info.addView(n);
        }

        if (g.communityRank > 0) {
            TextView popularity = Ui.text(this, getString(R.string.store_popular_rank, g.communityRank, g.popularityChecked), 13, Ui.TEXT_DIM, false);
            popularity.setPadding(0, Ui.dp(this, 12), 0, 0);
            info.addView(popularity);
        }

        // actions
        GridLayout actions = actionGrid();
        actions.setPadding(0, Ui.dp(this, 16), 0, 0);
        info.addView(actions);
        View first = null;
        Library.Entry mine = Catalog.installed(this, g);
        if (mine != null) {
            android.widget.Button play = Ui.button(this, "▶  " + getString(R.string.lib_play), true);
            play.setOnClickListener(v -> Games.startGame(this, new File(mine.path), false));
            addAction(actions, play);
            first = play;
            Catalog.Update u = updateFor(mine);
            if (u != null && u.newer() && !UpdateService.busy(mine.path)) {
                android.widget.Button up = Ui.button(this, getString(R.string.store_update_to, u.latest), true);
                up.setOnClickListener(v -> {
                    if (!u.link.isEmpty()) startUpdate(u); else chooseZip(g, mine.path, mine.displayTitle());
                });
                addAction(actions, up);
            }
        }
        if (!g.page.isEmpty() && !g.page.equals(g.sourcePage)) {
            android.widget.Button page = Ui.button(this, getString(R.string.store_page), mine == null);
            page.setOnClickListener(v -> openPage(g.page));
            addAction(actions, page);
            if (first == null) first = page;
        }
        if (source != null) {
            android.widget.Button community = Ui.button(this, getString(R.string.store_source_page, source.name), false);
            community.setOnClickListener(v -> openPage(g.sourcePage));
            addAction(actions, community);
            if (first == null) first = community;
        }
        if (source == null && !g.mediaPage.isEmpty() && !g.mediaPage.equals(g.page)) {
            android.widget.Button mediaSource = Ui.button(this, getString(R.string.store_media_source), false);
            mediaSource.setOnClickListener(v -> openPage(g.mediaPage));
            addAction(actions, mediaSource);
        }
        if (!g.trailer.isEmpty()) {
            android.widget.Button trailer = Ui.button(this, getString(R.string.store_video), false);
            trailer.setOnClickListener(v -> openPage(g.trailer));
            addAction(actions, trailer);
        }
        if (mine == null && !g.download.isEmpty()) {
            android.widget.Button direct = Ui.button(this, getString(R.string.store_direct), false);
            direct.setOnClickListener(v -> installDirect(g));
            addAction(actions, direct);
            if (first == null) first = direct;
        }
        if (g.communityRank > 0) {
            android.widget.Button ranking = Ui.button(this, getString(R.string.store_popular_source), false);
            ranking.setOnClickListener(v -> openPage(g.popularityPage));
            addAction(actions, ranking);
        }
        android.widget.Button zip = Ui.button(this, getString(mine == null ? R.string.store_install_zip : R.string.store_update_zip), false);
        zip.setOnClickListener(v -> chooseZip(g, mine != null ? mine.path : null, mine != null ? mine.displayTitle() : g.title));
        addAction(actions, zip);
        if (first == null) first = zip;

        if (mine != null) {
            android.widget.Button delete = Ui.button(this, getString(R.string.delete_game_files), false);
            delete.setTextColor(0xFFFF8A7A);
            delete.setOnClickListener(v -> GameRemoval.show(this, mine, () -> {
                loadPendingUpdates();
                showDetail(g);
            }));
            addAction(actions, delete);
        }

        TextView how = Ui.text(this, getString(mine == null ? R.string.store_how_install : R.string.store_how_update),
            12, Ui.TEXT_DIM, false);
        how.setPadding(0, Ui.dp(this, 10), 0, 0);
        info.addView(how);

        // screenshots
        if (!g.screens.isEmpty()) {
            TextView gallery = Ui.text(this, getString(R.string.store_gallery), 18, Ui.TEXT, true);
            gallery.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
            col.addView(gallery);
            LinearLayout shots = new LinearLayout(this);
            for (int i = 0; i < g.screens.size(); i++) {
                final String s = g.screens.get(i);
                ImageView shot = new ImageView(this);
                shot.setScaleType(ImageView.ScaleType.FIT_CENTER);
                shot.setBackground(Ui.focusable(this, Ui.SURFACE, 12));
                shot.setClipToOutline(true);
                shot.setFocusable(true);
                shot.setContentDescription(getString(R.string.store_screenshot, i + 1, g.title));
                shot.setOnClickListener(v -> openPage(s));
                int sp = Ui.dp(this, 3);
                shot.setPadding(sp, sp, sp, sp);
                loadImage(s, shot);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 300), Ui.dp(this, 169));
                slp.rightMargin = Ui.dp(this, 12);
                shots.addView(shot, slp);
            }
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(shots);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = Ui.dp(this, 20);
            col.addView(hs, hlp);
        }
        if (!g.mediaCredit.isEmpty()) {
            TextView credit = Ui.text(this, getString(R.string.store_media_credit, g.mediaCredit), 12, Ui.TEXT_DIM, false);
            credit.setPadding(0, Ui.dp(this, 12), 0, 0);
            col.addView(credit);
        }

        setContentView(root);
        if (first != null) first.requestFocus();
    }

    // ------------------------------------------------------------ installing

    private void openPage(String page)
    {
        String safe = Catalog.webUrl(page);
        if (safe.isEmpty()) {
            Toast.makeText(this, R.string.store_invalid_link, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.store_no_browser, page), Toast.LENGTH_LONG).show();
        }
    }

    private void installDirect(Catalog.Game g)
    {
        File dir = freeFolder(Catalog.gamesFolder(this), g.title);
        askNotifications();
        UpdateService.start(getApplicationContext(), dir.getAbsolutePath(), g.title, g.download, null, g.version, true);
        Toast.makeText(this, getString(R.string.store_installing, dir.getParentFile().getName()), Toast.LENGTH_LONG).show();
    }

    /**
     * Picks a downloaded .zip (from Downloads, the game's first) and installs it:
     * over a game in the library (path) or as a new game.
     */
    private void chooseZip(Catalog.Game g, String path, String title)
    {
        mPool.execute(() -> {
            List<File> zips = title != null ? Catalog.zipsFor(title) : Catalog.downloadedZips();
            mMain.post(() -> {
                if (isFinishing()) return;
                List<String> labels = new ArrayList<>();
                for (File z : zips) {
                    labels.add(z.getName() + "\n" + GameUpdater.mb(z.length()) + "  ·  "
                        + DateUtils.getRelativeTimeSpanString(z.lastModified(), System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS));
                }
                labels.add(getString(R.string.store_zip_other));
                AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(zips.isEmpty() ? getString(R.string.store_zip_none) : getString(R.string.store_zip_pick))
                    .setItems(labels.toArray(new String[0]), (x, w) -> {
                        if (w < zips.size()) installZip(zips.get(w), g, path);
                        else pickZipFile(g, path);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
                UpdateFlow.controllerFriendly(d);
                d.show();
            });
        });
    }

    private void pickZipFile(Catalog.Game g, String path)
    {
        mZipFor = g;
        mZipForPath = path;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/zip", "application/x-zip-compressed", "application/x-rar-compressed", "application/vnd.rar", "application/x-7z-compressed", "application/octet-stream" });
        try {
            startActivityForResult(i, REQ_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, R.string.store_zip_none, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ZIP || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Catalog.Game g = mZipFor;
        String path = mZipForPath;
        // a real file path when possible (no copy); else a copy in the cache
        String real = Games.pathFromUri(this, uri);
        if (real != null && new File(real).isFile()) {
            installZip(new File(real), g, path);
            return;
        }
        Toast.makeText(this, R.string.store_zip_copying, Toast.LENGTH_SHORT).show();
        mPool.execute(() -> {
            File out = new File(getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir(), "import-" + java.util.UUID.randomUUID() + ".archive");
            boolean ok = false;
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while (in != null && (n = in.read(buf)) > 0) o.write(buf, 0, n);
                ok = in != null;
            } catch (Exception ignored) { }
            final boolean copied = ok;
            mMain.post(() -> {
                if (copied) installZip(out, g, path);
                else Toast.makeText(this, R.string.upd_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void installZip(File zip, Catalog.Game g, String path)
    {
        mPool.execute(() -> {
            boolean game = GameUpdater.zipHasGame(zip);
            String name = GameUpdater.zipGameName(zip);
            mMain.post(() -> {
                if (isFinishing()) return;
                if (!game) {
                    message(getString(R.string.store_zip_bad, zip.getName()));
                    return;
                }
                askNotifications();
                String version = g != null ? g.version : "";
                if (path != null) {
                    Library.Entry e = Library.find(this, path);
                    String title = e != null ? e.displayTitle() : name;
                    UpdateService.start(getApplicationContext(), path, title, null, zip.getAbsolutePath(), version, false);
                    Toast.makeText(this, getString(R.string.store_updating_one, title), Toast.LENGTH_LONG).show();
                } else {
                    String title = g != null ? g.title : name;
                    File dir = freeFolder(Catalog.gamesFolder(this), title);
                    UpdateService.start(getApplicationContext(), dir.getAbsolutePath(), title, null,
                        zip.getAbsolutePath(), version, true);
                    Toast.makeText(this, getString(R.string.store_installing, dir.getParentFile().getName()), Toast.LENGTH_LONG).show();
                }
                if (mDetail == null) fillContent();
            });
        });
    }

    /** A new folder for a game ("Pokémon X", or "Pokémon X (2)" if taken). */
    private static File freeFolder(File parent, String title)
    {
        String base = Saves.safeName(title);
        if (base.isEmpty()) base = "Juego";
        File f = new File(parent, base);
        for (int i = 2; f.exists() && i < 50; i++) f = new File(parent, base + " (" + i + ")");
        return f;
    }

    private void askNotifications()
    {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 78); } catch (Exception ignored) { }
        }
    }

    /** "Suggest a game": goes to Essentia's database, like a report. */
    private void suggest()
    {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        EditText name = new EditText(this);
        name.setHint(R.string.store_suggest_name);
        box.addView(name);
        EditText link = new EditText(this);
        link.setHint(R.string.store_suggest_link);
        box.addView(link);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.store_suggest)
            .setView(box)
            .setPositiveButton(R.string.rep_send, (x, w) -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                String l = link.getText().toString().trim();
                android.content.Context app = getApplicationContext();
                new Thread(() -> {
                    boolean ok = false;
                    try {
                        JSONObject r = new JSONObject();
                        r.put("action", "report");
                        r.put("essentia", Cloud.essentiaVersion(app));
                        r.put("device", Cloud.device());
                        r.put("android", Build.VERSION.RELEASE);
                        r.put("game", n);
                        r.put("kind", "Sugerencia para la tienda");
                        r.put("error", "Sugerencia para la tienda" + (l.isEmpty() ? "" : "\n" + l));
                        r.put("detail", l);
                        Cloud.post(app, r, 30000);
                        ok = true;
                    } catch (Exception ignored) { }
                    final boolean sent = ok;
                    mMain.post(() -> Toast.makeText(app, sent ? R.string.store_suggest_sent : R.string.rep_failed,
                        Toast.LENGTH_LONG).show());
                }, "EssentiaSuggest").start();
            })
            .setNegativeButton(R.string.cancel, null)
            .create();
        d.show();
    }

    private void message(String text)
    {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        UpdateFlow.controllerFriendly(d);
        d.show();
    }

    // ------------------------------------------------------------ pictures

    /** Loads a picture from the internet (kept on disk after the first time). */
    private void loadImage(String url, ImageView into)
    {
        if (isFinishing() || isDestroyed() || mPool.isShutdown()) return;
        if (Catalog.webUrl(url).isEmpty()) return;
        into.setTag(url);
        Bitmap cached = mImages.get(url);
        if (cached != null) { into.setImageBitmap(cached); return; }
        mPool.execute(() -> {
            Bitmap b = null;
            try {
                File dir = new File(getCacheDir(), "store_img");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
                StringBuilder key = new StringBuilder();
                for (byte part : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
                File f = new File(dir, key + ".img");
                if (!f.isFile() || f.length() == 0) {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "Essentia");
                    c.setInstanceFollowRedirects(true);
                    File tmp = File.createTempFile("image-", ".part", dir);
                    try {
                        if (c.getResponseCode() != 200 || c.getContentLength() > 8 * 1024 * 1024)
                            throw new java.io.IOException("Image unavailable or too large");
                        try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[1 << 15];
                            int n, total = 0;
                            while ((n = in.read(buf)) > 0) {
                                total += n;
                                if (total > 8 * 1024 * 1024) throw new java.io.IOException("Image too large");
                                o.write(buf, 0, n);
                            }
                        }
                        // Concurrent requests for the same cover use separate temporary files.
                        if (!f.isFile() && !tmp.renameTo(f)) throw new java.io.IOException("Image cache unavailable");
                    } finally {
                        c.disconnect();
                        //noinspection ResultOfMethodCallIgnored
                        tmp.delete();
                    }
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                if (o.outWidth <= 0 || o.outHeight <= 0) {
                    // A transient HTML error page must not poison the cache permanently.
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    return;
                }
                while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            } catch (Exception ignored) { }
            final Bitmap bmp = b;
            if (bmp == null) return;
            mImages.put(url, bmp);
            mMain.post(() -> {
                if (!isFinishing() && !isDestroyed() && url.equals(into.getTag())) {
                    float target = into.getAlpha();
                    into.setImageBitmap(bmp);
                    into.setAlpha(0f);
                    into.animate().alpha(target).setDuration(180).start();
                }
            });
        });
    }

    /** Wrap store actions into clear rows instead of hiding them off-screen. */
    private GridLayout actionGrid()
    {
        return actionGrid(getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1);
    }

    private GridLayout actionGrid(int columns)
    {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        return grid;
    }

    private void addAction(GridLayout grid, View button)
    {
        int column = grid.getChildCount() % grid.getColumnCount();
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.setMargins(column == 0 ? 0 : Ui.dp(this, 6), Ui.dp(this, 6),
            column == grid.getColumnCount() - 1 ? 0 : Ui.dp(this, 6), Ui.dp(this, 6));
        button.setMinimumHeight(Ui.dp(this, 48));
        grid.addView(button, lp);
    }

    private void sectionTitle(LinearLayout parent, int text)
    {
        TextView title = Ui.text(this, getString(text), 13, Ui.TEXT_DIM, true);
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        parent.addView(title);
    }

    private void focusChip(String tag)
    {
        View chip = mContent.findViewWithTag(tag);
        if (chip != null) chip.requestFocus();
    }

    /** A colored gradient for games without a picture (their own color, or one from the name). */
    private GradientDrawable coverBackground(Catalog.Game g)
    {
        int base;
        try {
            base = g.color.isEmpty() ? 0 : Color.parseColor(g.color);
        } catch (Exception e) {
            base = 0;
        }
        if (base == 0) {
            float hue = (g.title.hashCode() & 0x7fffffff) % 360;
            base = Color.HSVToColor(new float[] { hue, 0.55f, 0.55f });
        }
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[2] = Math.max(0.12f, hsv[2] * 0.45f);
        int dark = Color.HSVToColor(hsv);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { base, dark });
        d.setCornerRadius(Ui.dp(this, 14));
        return d;
    }

    /** The game's name in big letters, for the colored covers. */
    private View bigName(Catalog.Game g, int sp)
    {
        String t = g.title.replaceFirst("(?i)^pok[eé]mon\\s+", "");
        TextView v = Ui.text(this, t, sp, 0x33FFFFFF, true);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(2);
        v.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), Ui.dp(this, 26));
        v.setAllCaps(true);
        v.setLetterSpacing(0.06f);
        return v;
    }

    // ------------------------------------------------------------ small parts

    private String statusLabel(String s)
    {
        switch (s) {
            case Catalog.PERFECT: return getString(R.string.store_s_perfect);
            case Catalog.PLAYABLE: return getString(R.string.store_s_playable);
            case Catalog.ISSUES: return getString(R.string.store_s_issues);
            case Catalog.BROKEN: return getString(R.string.store_s_broken);
            default: return getString(R.string.store_s_untested);
        }
    }

    private static int statusColor(String s)
    {
        switch (s) {
            case Catalog.PERFECT: return 0xFF2EBD6B;
            case Catalog.PLAYABLE: return 0xFF86E3AE;
            case Catalog.ISSUES: return 0xFFFFC266;
            case Catalog.BROKEN: return 0xFFFF8A7A;
            default: return 0xFFC9C6E3;
        }
    }

    private String langLabel(Catalog.Game g)
    {
        List<String> out = new ArrayList<>();
        for (String l : g.lang) {
            switch (l) {
                case "es": out.add(getString(R.string.store_l_es)); break;
                case "en": out.add(getString(R.string.store_l_en)); break;
                default: out.add(l.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return TextUtils.join(" / ", out);
    }

    private static String joinNonEmpty(String sep, String... parts)
    {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }

    private TextView badge(String text, int color)
    {
        float lum = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f;
        boolean light = lum > 0.55f;
        TextView t = Ui.text(this, text, 11, Color.alpha(color) < 0x80 ? Ui.TEXT : (light ? 0xFF14162B : Ui.TEXT), true);
        t.setBackground(Ui.rounded(this, color, 8));
        t.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        t.setSingleLine(true);
        return t;
    }

    private TextView pill(String text, boolean selected)
    {
        TextView t = Ui.text(this, text, 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.focusable(this, selected ? Ui.ACCENT : Ui.SURFACE, 22));
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 9), Ui.dp(this, 18), Ui.dp(this, 9));
        t.setMinHeight(Ui.dp(this, 48));
        t.setMaxLines(2);
        t.setFocusable(true);
        t.setClickable(true);
        return t;
    }

    private static FrameLayout.LayoutParams match()
    {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams gap()
    {
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = Ui.dp(this, 10);
        return lp;
    }

    private LinearLayout.LayoutParams gapSmall()
    {
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = Ui.dp(this, 6);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap()
    {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams rowParams()
    {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        return lp;
    }

    @Override
    protected boolean onControllerButton(int keyCode)
    {
        // L1 / R1 change tab, Y checks for updates
        if (mDetail != null) return false;
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 && mTab != 0) { mTab = 0; showList(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1 && mTab != 1) { mTab = 1; showList(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && mTab == 1) { checkAll(); return true; }
        return false;
    }

    @Override
    protected boolean wantsButton(int keyCode)
    {
        return mDetail == null && (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1
            || keyCode == KeyEvent.KEYCODE_BUTTON_Y);
    }
}
