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
    private String mQuery="";
    private boolean mTypingSearch;
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
    private TextView mHeroLabel;
    private LinearLayout mHeroChips;
    private View mHeroPlay;
    private HorizontalScrollView mShelfScroll;
    private LinearLayout mShelf;
    private GridView mGrid;
    private GamesAdapter mAdapter;
    private View mEmpty;
    private View mZoomed;
    private View mRoot;
    private int mThemeStamp;
    /** The intro plays once per app start. */
    private static boolean sIntroShown = false;
    /** Details of games (saves, version, size...), measured in the background. */
    private final java.util.Map<String, GameStats> mStats = new java.util.HashMap<>();
    private TextView mStoreLabel;
    private boolean mCheckingAll;
    private final java.util.concurrent.ExecutorService mStatsExec =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private long mTotalPlay = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        mBaseColumn = Ui.dp(this, widthDp >= 900 ? 170 : (widthDp >= 600 ? 160 : 150));
        mCovers = new CoverLoader(Ui.dp(this, 360));
        mView = EssentiaSettings.viewMode(this);
        mSort = EssentiaSettings.sortMode(this);
        if(savedInstanceState!=null){mSelected=savedInstanceState.getInt("selected",0);mQuery=savedInstanceState.getString("query","");}

        setContentView(buildUi());
        mThemeStamp = Ui.themeStamp();
        if (savedInstanceState == null && !sIntroShown) {
            sIntroShown = true;
            showIntro();
        }
        applyViewMode();


        if (!Games.hasAllFilesAccess()) {
            Games.askAllFilesAccess(this, REQ_ALL_FILES, this::finish);
        } else if (savedInstanceState == null) {
            Updater.check(this, () -> { }, false);

            rescanFolders();
            if (EssentiaSettings.flag(this, EssentiaSettings.RESUME_LAST, false)) {
                List<Library.Entry> all = Library.load(this);
                if (!all.isEmpty() && all.get(0).lastPlayed > 0) {
                    mRoot.postDelayed(() -> { if (!isFinishing()) play(0); }, 600);
                }
            }
        }
    }

    private boolean mResumed;
    private android.app.Dialog mTour;
    private void showGuideIfNeeded() {
        if (!mResumed || isFinishing() || isDestroyed() || !Games.hasAllFilesAccess()) return;
        if(mTour!=null&&mTour.isShowing())return;
        if(com.hatkid.mkxpz.ui.LibraryTour.needed(this))mTour=com.hatkid.mkxpz.ui.LibraryTour.show(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        if(intent.getBooleanExtra("show_experience_tour",false)){
            intent.removeExtra("show_experience_tour");
            mRoot.postDelayed(()->{if(mResumed&&!isFinishing())mTour=com.hatkid.mkxpz.ui.LibraryTour.show(this);},400);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        mResumed=true;
        if(getIntent().getBooleanExtra("show_experience_tour",false)){
            getIntent().removeExtra("show_experience_tour");
            mRoot.postDelayed(()->{if(mResumed&&!isFinishing())mTour=com.hatkid.mkxpz.ui.LibraryTour.show(this);},2600);
        }else mRoot.postDelayed(this::showGuideIfNeeded,2600);
        if (mThemeStamp != Ui.themeStamp()) { recreate(); return; }
        MusicPlayer.resume(this);
        UpdateService.setListener(() -> runOnUiThread(this::refreshUpdates));
        reload();
        showSecondScreen();
        checkAllUpdates();
    }

    @Override
    protected void onPause()
    {
        mResumed=false;
        super.onPause();
        MusicPlayer.pause();
        UpdateService.setListener(null);
        if (mSecond != null) {
            mSecond.dismiss();
            mSecond = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(mTour!=null)mTour.dismiss();
        mCovers.shutdown();
        mStatsExec.shutdownNow();
    }

    // ================================================================== UI

    // ------------------------------------------------------------ intro

    /** A short intro when Essentia opens: the flask glows in, then the library appears. */
    private void showIntro()
    {
        if (!(mRoot instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) mRoot;
        FrameLayout intro = new FrameLayout(this);
        intro.setBackgroundColor(Ui.BG);
        intro.setClickable(true);

        View glow = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{ Ui.alpha(Ui.ACCENT, 0x70), Ui.alpha(Ui.ACCENT, 0x00) });
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Ui.dp(this, 170));
        glow.setBackground(g);
        int gs = Ui.dp(this, 360);
        intro.addView(glow, new FrameLayout.LayoutParams(gs, gs, Gravity.CENTER));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView flask = new ImageView(this);
        flask.setImageResource(R.drawable.ic_launcher_foreground);
        int fs = Ui.dp(this, 150);
        col.addView(flask, new LinearLayout.LayoutParams(fs, fs));
        TextView name = Ui.text(this, "Essentia by Díptico", 34, Ui.TEXT, true);
        name.setLetterSpacing(0.12f);
        name.setGravity(Gravity.CENTER);
        col.addView(name);
        TextView tag = Ui.text(this, getString(R.string.intro_tagline), 14, Ui.ACCENT_LIGHT, false);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(tag);
        intro.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(intro, match());

        flask.setScaleX(0.6f); flask.setScaleY(0.6f); flask.setAlpha(0f);
        flask.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250)
            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        glow.setScaleX(0.3f); glow.setScaleY(0.3f); glow.setAlpha(0f);
        glow.animate().scaleX(1.15f).scaleY(1.15f).alpha(1f).setDuration(300).start();
        name.setAlpha(0f); name.setTranslationY(Ui.dp(this, 16));
        name.animate().alpha(1f).translationY(0).setStartDelay(60).setDuration(180).start();
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(100).setDuration(180).start();

        Runnable close = () -> {
            if (intro.getParent() == null) return;
            intro.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                root.removeView(intro);
                focusSelected();
            }).start();
        };
        intro.setOnClickListener(v -> close.run());
        intro.postDelayed(close, 360);
    }

    private boolean isPortrait()
    {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private View buildUi()
    {
        FrameLayout root = new FrameLayout(this);
        mRoot = root;
        root.setBackground(Ui.backdrop());

        mBackdrop = new ImageView(this); // cover loader target; no animated full-screen backdrop
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 8));
        root.addView(col, match());

        col.addView(buildTopBar(), wrapWidth());
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
        mViewIcon=iconButton(tools,R.drawable.ic_e_grid,R.string.lib_view_label,v->cycleView());
        View sorting=pillButton(tools,R.drawable.ic_e_sort,"",false,v->toggleSort());
        mSortLabel=sorting.findViewWithTag("label");
        android.widget.Button help=Ui.button(this,"Tour",false,R.drawable.ic_e_compass);
        help.setTag("tour-start");help.setOnClickListener(v->mTour=com.hatkid.mkxpz.ui.LibraryTour.show(this));
        LinearLayout.LayoutParams helpLp=new LinearLayout.LayoutParams(-2,Ui.dp(this,48));helpLp.leftMargin=Ui.dp(this,8);tools.addView(help,helpLp);
        col.addView(tools,wrapWidth());
        EditText search=com.hatkid.mkxpz.ui.Ui.input(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.TEXT_DIM);
        search.setTag("tour-search");Ui.icon(search,R.drawable.ic_e_search,Ui.ACCENT_LIGHT);search.setHint(getString(R.string.library_search));search.setContentDescription(getString(R.string.library_search));search.setBackground(Ui.rounded(this,Ui.SURFACE,12));
        search.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);search.setText(mQuery);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(isPortrait()?-1:0,Ui.dp(this,48),isPortrait()?0f:1f);
        searchLp.topMargin=isPortrait()?Ui.dp(this,8):0;searchLp.leftMargin=isPortrait()?0:Ui.dp(this,8);
        if(isPortrait())col.addView(search,searchLp);else tools.addView(search,searchLp);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){
                mQuery=s.toString();mSelected=0;mTypingSearch=true;reload();mTypingSearch=false;
            }public void afterTextChanged(android.text.Editable e){}
        });

        // Featured game (shelf view)
        mHero = buildHero();
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(this, 10);
        col.addView(mHero, hlp);

        // Body: shelf, grid/list, or the empty state
        FrameLayout body = new FrameLayout(this);
        mShelfScroll = new HorizontalScrollView(this);
        mShelfScroll.setHorizontalScrollBarEnabled(false);
        mShelfScroll.setClipToPadding(false);
        // room around the cards for the zoomed one and its highlight
        mShelfScroll.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        // the cards always fit the height there is (zoom and highlight included)
        mShelfScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) v.post(this::fitShelfCards);
        });
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
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        View brand=Ui.brand(this,52);brand.setTag("tour-brand");bar.addView(brand,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(Ui.dp(this,12),0,0,0);
        names.addView(Ui.text(this,"Essentia",25,Ui.TEXT,true));
        mCount=Ui.text(this,"",12,Ui.ACCENT_LIGHT,true);names.addView(mCount);
        bar.addView(names,new LinearLayout.LayoutParams(0,-2,1f));
        View add=pillButton(bar,R.drawable.ic_e_add,"Añadir",true,v->openFolderPicker());add.setTag("tour-add");
        mStoreLabel=null;
        return bar;
    }

    private View buildHero()
    {
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setTag("tour-game");
        hero.setBackground(Ui.feature(this));int p=Ui.dp(this,14);hero.setPadding(p,p,p,p);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        mHeroCover=new ImageView(this);mHeroCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mHeroCover.setBackground(Ui.rounded(this,Ui.SURFACE_HI,14));mHeroCover.setClipToOutline(true);
        top.addView(mHeroCover,new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(p,0,p,0);
        mHeroLabel=Ui.text(this,"TU PRÓXIMA PARTIDA",11,0xFFBFF7EB,true);info.addView(mHeroLabel);
        mHeroTitle=Ui.text(this,"",22,0xFFFFFFFF,true);mHeroTitle.setMaxLines(2);mHeroTitle.setEllipsize(TextUtils.TruncateAt.END);info.addView(mHeroTitle);
        mHeroDetail=Ui.text(this,"",12,0xFFE7DFF9,false);mHeroDetail.setMaxLines(1);info.addView(mHeroDetail);
        mHeroChips=new LinearLayout(this);
        top.addView(info,new LinearLayout.LayoutParams(0,-2,1f));hero.addView(top);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        mHeroPlay=pillButton(actions,R.drawable.ic_e_play,"Jugar",false,v->play(mSelected));
        View options=pillButton(actions,R.drawable.ic_e_settings,"Opciones",false,v->showOptions(mSelected));
        for(int i=0;i<actions.getChildCount();i++){
            View action=actions.getChildAt(i);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);lp.leftMargin=i==0?0:Ui.dp(this,8);action.setLayoutParams(lp);
        }
        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);actionsLp.topMargin=Ui.dp(this,12);hero.addView(actions,actionsLp);return hero;
    }

    private GridView buildGrid()
    {
        GridView g = new GridView(this);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        g.setClipToPadding(false);
        g.setSelector(new ColorDrawable(0x00000000)); // the card draws its own highlight
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

        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);return scroll;
    }

    private void hint(LinearLayout row, String button, int labelRes)
    {
        TextView key = Ui.text(this, button, 11, Ui.contrast(Ui.TEXT_DIM), true);
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
        title.setTag("empty-title");
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView body = Ui.text(this, getString(R.string.lib_empty_text), 15, Ui.TEXT_DIM, false);
        body.setTag("empty-body");
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        body.setMaxWidth(Ui.dp(this, 460));
        col.addView(body);

        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        pillButton(holder, R.drawable.ic_e_add, getString(R.string.lib_add).replaceFirst("^\\+\\s*", ""), true, v -> openFolderPicker());
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
        b.setBackground(Ui.focusable(this, Ui.SURFACE, 22));
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(getString(descRes));
        b.setOnClickListener(l);
        int size = Ui.dp(this, 48);
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
        b.setBackground(Ui.focusable(this, primary ? Ui.ACCENT : Ui.SURFACE, 22));
        b.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 18), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);

        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setImageTintList(Ui.tint(primary?Ui.ON_ACCENT:Ui.TEXT));
        b.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));

        TextView t = Ui.text(this, text, 15, primary?Ui.ON_ACCENT:Ui.TEXT, true);
        t.setTag("label");
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        b.addView(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48));
        lp.leftMargin = Ui.dp(this, 8);
        parent.addView(b, lp);
        return b;
    }

    /**
     * The highlight of the selected game: a ring drawn around the card, on a
     * frame that leaves room for it, so it's never cut (no zooming either).
     */
    private Drawable ringDrawable()
    {
        int stroke = Ui.dp(this, 3);
        GradientDrawable ring = Ui.rounded(this, 0x00000000, 21);
        ring.setStroke(stroke, Ui.ACCENT_LIGHT);
        GradientDrawable press = Ui.rounded(this, Ui.alpha(Ui.ACCENT, 0x40), 21);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, press);
        s.addState(new int[]{android.R.attr.state_focused}, ring);
        s.addState(new int[]{android.R.attr.state_activated}, ring);
        s.addState(new int[]{}, new ColorDrawable(0x00000000));
        return s;
    }

    private int slotPad() { return Ui.dp(this, 6); }

    /** A card inside its frame (the frame draws the highlight). */
    private FrameLayout newSlot()
    {
        FrameLayout slot = new FrameLayout(this);
        int p = slotPad();
        slot.setPadding(p, p, p, p);
        slot.setBackground(ringDrawable());
        View card = newCard();
        slot.addView(card, match());
        slot.setTag(card.getTag());
        card.setAlpha(1f);
        return slot;
    }

    // ------------------------------------------------------------ view modes

    private boolean isShelf() { return EssentiaSettings.VIEW_SHELF.equals(mView); }
    private boolean isList() { return EssentiaSettings.VIEW_LIST.equals(mView); }

    private void applyViewMode()
    {
        boolean empty = mGames.isEmpty();
        mHero.setVisibility(!empty ? View.VISIBLE : View.GONE);
        mShelfScroll.setVisibility(isShelf() && !empty ? View.VISIBLE : View.GONE);
        mGrid.setVisibility(!isShelf() && !empty ? View.VISIBLE : View.GONE);
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        TextView emptyTitle=mEmpty.findViewWithTag("empty-title");TextView emptyBody=mEmpty.findViewWithTag("empty-body");
        emptyTitle.setText(mQuery.isEmpty()?getString(R.string.lib_empty_title):"No hay coincidencias");
        emptyBody.setText(mQuery.isEmpty()?getString(R.string.lib_empty_text):"Prueba con otro nombre o borra la búsqueda para ver tus juegos.");

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

    /** Marks the selected card: ring around it, full brightness. */
    private void zoom(View v)
    {
        if (mZoomed != null && mZoomed != v) {
            mZoomed.setActivated(false);
            View card = mZoomed instanceof ViewGroup && ((ViewGroup) mZoomed).getChildCount() > 0
                ? ((ViewGroup) mZoomed).getChildAt(0) : null;
            if (card != null) card.animate().alpha(1f).setDuration(120).start();
        }
        mZoomed = null;
        if (v != null && !isList()) {
            v.setActivated(true);
            View card = v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0 ? ((ViewGroup) v).getChildAt(0) : null;
            if (card != null) card.animate().alpha(1f).setDuration(150).start();
            mZoomed = v;
        }
    }

    // ------------------------------------------------------------ items

    private String detailOf(Library.Entry e)
    {
        UpdateService.State up = UpdateService.stateFor(e.path);
        if (up != null) {
            int pct = up.percent();
            return getString(up.step == 0 ? R.string.upd_downloading : R.string.upd_installing)
                + (pct >= 0 ? "  " + pct + "%" : "");
        }
        String when = e.lastPlayed == 0 ? getString(R.string.lib_never_played)
            : getString(R.string.lib_played, DateUtils.getRelativeTimeSpanString(e.lastPlayed,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString());
        String mode = screenLabel(e.screenMode);
        String out = mode == null ? when : when + "  ·  " + mode;
        return usesClassic(e) ? out + "  ·  Ruby 1.8" : out;
    }

    /** The classic engine, chosen or found by the automatic engine. */
    private boolean usesClassic(Library.Entry e)
    {
        return EssentiaSettings.ENGINE_CLASSIC.equals(e.engine)
            || (EssentiaSettings.ENGINE_AUTO.equals(e.engine) && EssentiaSettings.learnedClassic(this, e.path));
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
            new int[]{ 0x00000000, 0x220C0825, 0xF018102D }));
        card.addView(shade, match());

        h.title = Ui.text(this, "", 14, 0xFFFFFFFF, true);
        h.title.setMaxLines(2);
        h.title.setEllipsize(TextUtils.TruncateAt.END);
        h.title.setBackgroundColor(0x8818102D);
        int p = Ui.dp(this, 10);
        h.title.setPadding(p, p, p, p);
        card.addView(h.title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        h.badge = Ui.text(this, "16:9", 11, Ui.INK, true);
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
        UpdateService.State up = UpdateService.stateFor(e.path);
        if (up != null) mode = "\u2193 " + (up.percent() >= 0 ? up.percent() + "%" : "…");
        else if (Catalog.pending(this, e) != null) mode = "\u2191 " + getString(R.string.store_badge_new);
        h.badge.setText(mode == null ? "" : mode);
        h.badge.setVisibility(mode == null ? View.GONE : View.VISIBLE);
        mCovers.load(e.path, e.cover, h.cover, R.drawable.ic_launcher_foreground);
    }

    private View newRow()
    {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(this, Ui.alpha(Ui.SURFACE, 0x99), 14));
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
            if (convert == null) convert = newSlot();
            boolean on = pos == mSelected && mGrid.hasFocus();
            convert.setActivated(on);
            ((ViewGroup) convert).getChildAt(0).setAlpha(1f);
            if (on) mZoomed = convert;
            int w = mGrid.getColumnWidth() > 0 ? mGrid.getColumnWidth() : mBaseColumn;
            convert.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (w - 2 * slotPad()) * 3 / 4 + 2 * slotPad()));
            bindCard(convert, e);
            return convert;
        }
    }

    /** Builds the shelf cards (shelf view only). */
    private void rebuildShelf()
    {
        mShelf.removeAllViews();
        if (!isShelf()) return;

        int h = shelfCardHeight();
        int w = shelfCardWidth(h);
        for (int i = 0; i < mGames.size(); i++) {
            final int pos = i;
            View card = newSlot();
            bindCard(card, mGames.get(i));
            card.setFocusable(true);
            card.setClickable(true);
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
            lp.rightMargin = Ui.dp(this, 6);
            mShelf.addView(card, lp);
        }
    }

    /** Slot height on the shelf (card + frame): as designed, or what fits. */
    private int shelfCardHeight()
    {
        int h = Ui.dp(this, isPortrait() ? 132 : 144);
        int avail = mShelfScroll.getHeight() - mShelfScroll.getPaddingTop() - mShelfScroll.getPaddingBottom();
        if (avail > 0) h = Math.min(h, avail);
        return Math.max(h, Ui.dp(this, 60));
    }

    /** 4:3 card inside the frame. */
    private int shelfCardWidth(int slotH)
    {
        int p = 2 * slotPad();
        return (slotH - p) * 4 / 3 + p;
    }

    private void fitShelfCards()
    {
        int h = shelfCardHeight();
        int w = shelfCardWidth(h);
        for (int i = 0; i < mShelf.getChildCount(); i++) {
            View c = mShelf.getChildAt(i);
            ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (lp.height != h || lp.width != w) {
                lp.height = h;
                lp.width = w;
                c.setLayoutParams(lp);
            }
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

    /** While a game runs, the library gives back the memory it doesn't need. */
    @Override
    public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN && mCovers != null) mCovers.trim();
    }

    /** Looks for new versions of every game now and then (in the background), for the badges. */
    private void checkAllUpdates()
    {
        updateStoreLabel();
        if (mCheckingAll || mGames.isEmpty() || !EssentiaSettings.flag(this, EssentiaSettings.AUTO_UPDATE, true)
            || !Catalog.checkAllDue(this)) return;
        mCheckingAll = true;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            List<Catalog.Update> found = Catalog.checkAll(app);
            runOnUiThread(() -> {
                mCheckingAll = false;
                if (isFinishing()) return;
                refreshUpdates();
                if (!found.isEmpty()) {
                    Toast.makeText(this, getResources().getQuantityString(R.plurals.store_found, found.size(), found.size()),
                        Toast.LENGTH_LONG).show();
                }
            });
        }, "EssentiaCheckAll").start();
    }

    private void updateStoreLabel()
    {
        if (mStoreLabel == null) return;
        int n = Catalog.pendingCount(this, mGames);
        mStoreLabel.setText(n > 0 ? getString(R.string.store_short_n, n) : getString(R.string.store_short));
    }

    /** A game update in the background moved on: show its progress on the cards. */
    private void refreshUpdates()
    {
        if (isFinishing()) return;
        updateStoreLabel();
        if (isShelf()) {
            for (int i = 0; i < mShelf.getChildCount() && i < mGames.size(); i++) {
                bindCard(mShelf.getChildAt(i), mGames.get(i));
            }
        } else {
            mAdapter.notifyDataSetChanged();
        }
        if (!mGames.isEmpty() && mSelected < mGames.size()) fillHero(mGames.get(mSelected));
    }

    private void reload()
    {
        mGames.clear();
        for(Library.Entry game:Library.load(this))if(mQuery.isEmpty()||game.displayTitle().toLowerCase(java.util.Locale.ROOT).contains(mQuery.toLowerCase(java.util.Locale.ROOT)))mGames.add(game);
        mStats.clear(); // play time and saves may have changed
        mTotalPlay = GameStats.totalPlay(this, mGames);
        if (EssentiaSettings.SORT_AZ.equals(mSort)) {
            Collections.sort(mGames, (a, b) -> a.displayTitle().compareToIgnoreCase(b.displayTitle()));
        }
        mCount.setText(getResources().getQuantityString(R.plurals.lib_count, mGames.size(), mGames.size()));
        if (mSelected >= mGames.size()) mSelected = Math.max(0, mGames.size() - 1);
        applyViewMode();

        select(mSelected);
        if(!mTypingSearch&&mQuery.isEmpty())focusSelected();
    }

    @Override protected void onSaveInstanceState(Bundle out){out.putInt("selected",mSelected);out.putString("query",mQuery);super.onSaveInstanceState(out);}

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
        boolean resume = mSelected == 0 && e.lastPlayed > 0 && !EssentiaSettings.SORT_AZ.equals(mSort);
        mHeroLabel.setText(resume ? R.string.lib_continue : R.string.lib_featured);
        fillHero(e);
        loadStats(e);
        mCovers.load(e.path, e.cover, mHeroCover, R.drawable.ic_launcher_foreground);
        mCovers.load(e.path, e.cover, mBackdrop, 0);
        mBackdrop.setAlpha(0f);

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
        List<Catalog.Game> news = Catalog.cached(this);
        String newsText = news.isEmpty() ? "Descubre fangames en la tienda" : "De la tienda · "
            + news.get(0).title + (news.size()>1 ? " · " + news.get(1).title : "");
        mSecond.navigation(() -> select(mGames.isEmpty()?0:(mSelected+mGames.size()-1)%mGames.size()),
            () -> play(mSelected), () -> select(mGames.isEmpty()?0:(mSelected+1)%mGames.size()),
            () -> startActivity(new Intent(this,StoreActivity.class)), newsText);
        String footer = getResources().getQuantityString(R.plurals.lib_count, mGames.size(), mGames.size())
            + (mTotalPlay > 0 ? "  ·  " + getString(R.string.stat_total, GameStats.duration(this, mTotalPlay)) : "")
            + "  ·  " + getString(R.string.lib_second_hint);
        if (mGames.isEmpty()) {
            mSecond.showGame(getString(R.string.app_name), getString(R.string.lib_empty_title), null, footer);
            return;
        }
        Library.Entry e = mGames.get(Math.min(mSelected, mGames.size() - 1));
        GameStats st = statsOf(e);
        List<SecondScreen.Stat> list = new ArrayList<>();
        list.add(new SecondScreen.Stat(getString(R.string.stat_play), st.playMs > 0 ? GameStats.duration(this, st.playMs) : "—"));
        list.add(new SecondScreen.Stat(getString(R.string.stat_sessions), String.valueOf(st.sessions)));
        list.add(new SecondScreen.Stat(getString(R.string.stat_last), e.lastPlayed == 0 ? "—"
            : DateUtils.getRelativeTimeSpanString(e.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()));
        String saves = st.saves < 0 ? "…" : String.valueOf(st.saves);
        String savesLabel = st.lastSave > 0 ? getString(R.string.stat_saves_last,
            DateUtils.getRelativeTimeSpanString(st.lastSave, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
            : getString(R.string.stat_saves);
        list.add(new SecondScreen.Stat(savesLabel, saves));
        Catalog.Update pend = Catalog.pending(this, e);
        if (pend != null) {
            list.add(new SecondScreen.Stat(getString(R.string.store_chip_new, "").trim(),
                (st.version.isEmpty() ? "?" : st.version) + " \u2192 " + pend.latest));
        } else {
            list.add(new SecondScreen.Stat(getString(R.string.stat_version), st.version.isEmpty() ? "—" : st.version));
        }
        list.add(new SecondScreen.Stat(getString(R.string.stat_size), st.size < 0 ? "…" : GameStats.bytes(st.size)));

        StringBuilder sub = new StringBuilder();
        if (!st.kind.isEmpty()) sub.append(st.kind);
        sub.append(sub.length() > 0 ? "  ·  " : "").append(usesClassic(e)
            ? getString(R.string.eng_classic_short) : getString(R.string.eng_modern_short));
        String mode = screenLabel(e.screenMode);
        if (mode != null) sub.append("  ·  ").append(mode);
        UpdateService.State up = UpdateService.stateFor(e.path);
        if (up != null) sub.append("  ·  ").append(detailOf(e));

        mSecond.showGame(e.displayTitle(), sub.toString(), list, footer);
        mCovers.load(e.path, e.cover, mSecond.coverView(), R.drawable.ic_launcher_foreground);
        mCovers.load(e.path, e.cover, mSecond.backdropView(), 0);
    }

    // ------------------------------------------------------------ game details

    private GameStats statsOf(Library.Entry e)
    {
        GameStats st = mStats.get(e.path);
        return st != null ? st : GameStats.quick(this, e.path);
    }

    /** Measures saves, version and size in the background, then shows them. */
    private void loadStats(Library.Entry e)
    {
        if (mStats.containsKey(e.path)) return;
        final String path = e.path;
        try {
            mStatsExec.execute(() -> {
                GameStats st = GameStats.details(this, e);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    mStats.put(path, st);
                    if (!mGames.isEmpty() && mSelected < mGames.size() && mGames.get(mSelected).path.equals(path)) {
                        fillHero(mGames.get(mSelected));
                        updateSecondScreen();
                    }
                });
            });
        } catch (Exception ignored) { }
    }

    /** Chips under the title: play time, last played, kind, version, format, engine. */
    private void fillHero(Library.Entry e)
    {
        GameStats st = statsOf(e);
        mHeroChips.removeAllViews();
        UpdateService.State up = UpdateService.stateFor(e.path);
        if (up != null) chip(detailOf(e), true);
        Catalog.Update pend = up == null ? Catalog.pending(this, e) : null;
        if (pend != null) chip("\u2191 " + getString(R.string.store_chip_new, pend.latest), true);
        if (st.playMs > 0) chip("\u23F1 " + GameStats.duration(this, st.playMs), false);
        chip(e.lastPlayed == 0 ? getString(R.string.lib_never_played)
            : DateUtils.getRelativeTimeSpanString(e.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), false);
        if (!st.kind.isEmpty()) chip(st.kind, false);
        if (!st.version.isEmpty()) chip("v" + st.version.replaceFirst("^[vV]", ""), false);
        String mode = screenLabel(e.screenMode);
        if (mode != null) chip(mode, false);
        if (usesClassic(e)) chip("Ruby 1.8", false);
        if (e.translate) chip(getString(R.string.chip_translated), false);

        if (st.saves > 0) {
            String when = st.lastSave > 0 ? DateUtils.getRelativeTimeSpanString(st.lastSave,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() : "";
            mHeroDetail.setText(getResources().getQuantityString(R.plurals.stat_saves_line, st.saves, st.saves, when));
        } else {
            mHeroDetail.setText(st.saves == 0 ? getString(R.string.stat_no_saves) : "");
        }
    }

    private void chip(String text, boolean highlight)
    {
        TextView t = Ui.text(this, text, 13, highlight ? Ui.INK : Ui.TEXT, true);
        t.setBackground(Ui.rounded(this, highlight ? Ui.GOLD : 0x33FFFFFF, 12));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        t.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        mHeroChips.addView(t, lp);
    }

    // ------------------------------------------------------------ actions

    private void play(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);
        File dir = new File(e.path);
        if (!dir.isDirectory() || !Games.isGameFolder(dir)) {
            new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
                .setTitle(e.displayTitle())
                .setMessage(R.string.lib_missing)
                .setPositiveButton(R.string.lib_remove, (d, w) -> { Library.remove(this, e.path); reload(); })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }
        if (!EssentiaSettings.SORT_AZ.equals(mSort)) mSelected = 0; // becomes the most recent
        // A new official version of the game? Offer it before playing.
        com.hatkid.mkxpz.ui.UpdateFlow.beforeLaunch(this, dir, e.displayTitle(),
            () -> Games.startGame(this, dir, false));
    }

    private void showOptions(int pos)
    {
        if (pos < 0 || pos >= mGames.size()) return;
        Library.Entry e = mGames.get(pos);

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.lib_play)); actions.add(() -> play(pos));
        labels.add("Herramientas, notas y recuperación"); actions.add(() -> startActivity(
            new Intent(this, GuideActivity.class).putExtra(MainActivity.EXTRA_GAME_PATH, e.path)));
        labels.add("Controles de este juego"); actions.add(() -> startActivity(
            new Intent(this, ControlsActivity.class).putExtra(MainActivity.EXTRA_GAME_PATH, e.path)));
        labels.add(getString(R.string.opt_cover)); actions.add(() -> pickCover(e));
        if (!e.cover.isEmpty()) { labels.add(getString(R.string.opt_cover_reset)); actions.add(() -> resetCover(e)); }
        labels.add(getString(R.string.opt_rename)); actions.add(() -> rename(e));
        labels.add(getString(R.string.opt_screen)); actions.add(() -> chooseScreen(e));
        labels.add(getString(R.string.opt_engine)); actions.add(() -> chooseEngine(e));
        labels.add(getString(R.string.opt_import)); actions.add(() -> importSaves(e));
        labels.add(getString(R.string.upd_game)); actions.add(() -> {
            if (GameUpdater.supported(new File(e.path))) updateGame(e);
            else startActivity(new Intent(this, StoreActivity.class).putExtra(StoreActivity.EXTRA_TAB, 1)
                .putExtra(StoreActivity.EXTRA_GAME, e.path));
        });
        if (Shortcuts.canPin(this)) {
            labels.add(getString(R.string.opt_pin)); actions.add(() -> {
                boolean ok = Shortcuts.pin(this, e);
                Toast.makeText(this, ok ? R.string.opt_pin_done : R.string.opt_pin_failed, Toast.LENGTH_LONG).show();
            });
        }
        labels.add(getString(R.string.game_translation));
        actions.add(() -> chooseTranslation(e));
        labels.add(getString(R.string.opt_cloud)); actions.add(() -> cloudBackup(e));
        labels.add(getString(R.string.opt_report)); actions.add(() -> ReportActivity.open(this, e.path, null));
        labels.add(getString(R.string.opt_send_scripts)); actions.add(() -> sendScripts(e));
        labels.add(getString(R.string.lib_remove)); actions.add(() -> { Library.remove(this, e.path); reload(); });
        labels.add(getString(R.string.delete_game_files)); actions.add(() -> GameRemoval.show(this, e, this::reload));

        int[] groups=new int[labels.size()];
        for(int i=0;i<labels.size();i++) {
            String label=labels.get(i);groups[i]=1;
            if(i<3)groups[i]=0;
            else if(label.equals(getString(R.string.opt_import))||label.equals(getString(R.string.upd_game))||label.equals(getString(R.string.opt_cloud)))groups[i]=2;
            else if(label.equals(getString(R.string.opt_report))||label.equals(getString(R.string.opt_send_scripts))||label.equals(getString(R.string.lib_remove))||label.equals(getString(R.string.delete_game_files)))groups[i]=3;
        }
        Ui.gameOptions(this,e.displayTitle(),labels,actions,groups);
    }

    private void rename(Library.Entry e)
    {
        EditText input = com.hatkid.mkxpz.ui.Ui.input(this);
        input.setText(e.displayTitle());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
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
            case EssentiaSettings.SCREEN_WIDE: return "16:9+";
            case EssentiaSettings.SCREEN_WIDE_SAFE: return "16:9";
            case EssentiaSettings.SCREEN_AMBIENT: return getString(R.string.scr_badge_ambient);
            default: return null;
        }
    }

    // ------------------------------------------------------------ translation and backups

    private void chooseTranslation(Library.Entry entry)
    {
        String[] codes = TranslationLanguage.codes();
        String[] labels = new String[codes.length + 1];
        labels[0] = getString(R.string.game_translation_off);
        for (int i = 0; i < codes.length; i++) {
            labels[i + 1] = TranslationLanguage.label(codes[i], getResources().getConfiguration().locale);
        }
        int selected = entry.translate ? 1 + java.util.Arrays.asList(codes).indexOf(entry.translationLanguage) : 0;
        new AlertDialog.Builder(this, Ui.dialogTheme())
            .setTitle(R.string.game_translation)
            .setMessage(R.string.game_translation_info)
            .setPositiveButton(R.string.game_translation_choose, (info, ignored) ->
                new AlertDialog.Builder(this, Ui.dialogTheme())
                    .setTitle(R.string.game_translation_choose)
                    .setSingleChoiceItems(labels, selected, (dialog, choice) -> {
                        entry.translate = choice > 0;
                        if (entry.translate) entry.translationLanguage = codes[choice - 1];
                        Library.update(this, entry);
                        dialog.dismiss();
                        reload();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void cloudBackup(Library.Entry e)
    {
        if (!Cloud.backupsReady(this)) {
            new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
                .setTitle(R.string.opt_cloud)
                .setMessage(R.string.cloud_not_ready)
                .setPositiveButton(R.string.lib_settings, (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                .setNegativeButton(R.string.cancel, null)
                .show();
            return;
        }
        long last = CloudBackup.lastBackup(this, e.path);
        String when = last == 0 ? getString(R.string.cloud_never)
            : DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
            .setTitle(e.displayTitle())
            .setMessage(getString(R.string.cloud_last, when))
            .setPositiveButton(R.string.cloud_now, (d, w) -> runCloud(() -> {
                boolean sent = CloudBackup.backup(this, e.path, true);
                return getString(sent ? R.string.cloud_done : R.string.cloud_nothing);
            }))
            .setNeutralButton(R.string.cloud_restore, (d, w) -> chooseBackup(e))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private interface CloudJob { String run() throws Exception; }

    private void runCloud(CloudJob job)
    {
        Toast.makeText(this, R.string.cloud_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String msg;
            try { msg = job.run(); } catch (Exception ex) { msg = getString(R.string.cloud_failed, ex.getMessage()); }
            final String text = msg;
            runOnUiThread(() -> { if (!isFinishing()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); });
        }, "EssentiaCloud").start();
    }

    private void chooseBackup(Library.Entry e)
    {
        Toast.makeText(this, R.string.cloud_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String[]> list;
            try { list = CloudBackup.list(this, e.path); } catch (Exception ex) { list = null; }
            final List<String[]> found = list;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (found == null || found.isEmpty()) {
                    Toast.makeText(this, found == null ? R.string.cloud_list_failed : R.string.cloud_none, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < found.size(); i++) names[i] = CloudBackup.readable(found.get(i)[0]) + "\n" + found.get(i)[1];
                new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
                    .setTitle(R.string.cloud_restore)
                    .setItems(names, (d, which) -> new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
                        .setTitle(R.string.cloud_restore)
                        .setMessage(getString(R.string.cloud_restore_confirm, CloudBackup.readable(found.get(which)[0])))
                        .setPositiveButton(R.string.cloud_restore, (d2, w2) -> runCloud(() -> {
                            int n = CloudBackup.restore(this, e.path, found.get(which)[0]);
                            return getResources().getQuantityString(R.plurals.cloud_restored, n, n);
                        }))
                        .setNegativeButton(R.string.cancel, null)
                        .show())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            });
        }, "EssentiaCloudList").start();
    }

    // ------------------------------------------------------------ scripts for review

    /** Takes the game's scripts file (also from packed games) and opens the share menu. */
    private void sendScripts(Library.Entry e)
    {
        Toast.makeText(this, R.string.scripts_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File out = new File(ShareProvider.folder(this), Saves.safeName(e.displayTitle()) + " - Scripts.rxdata");
            boolean ok;
            try {
                ok = GameScripts.extract(new File(e.path), out);
            } catch (Exception ex) {
                ok = false;
            }
            final boolean found = ok;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!found) {
                    Toast.makeText(this, R.string.scripts_none, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    ShareProvider.share(this, out, getString(R.string.opt_send_scripts));
                } catch (Exception ex) {
                    Toast.makeText(this, R.string.err_no_picker, Toast.LENGTH_LONG).show();
                }
            });
        }, "EssentiaScripts").start();
    }

    // ------------------------------------------------------------ game updates (PokéUpdater)

    private void updateGame(Library.Entry e)
    {
        com.hatkid.mkxpz.ui.UpdateFlow.manual(this, new File(e.path), e.displayTitle(), this::reload);
    }

    private void chooseEngine(Library.Entry e)
    {
        boolean available = Games.classicEngineAvailable(this);
        final String[] values = { EssentiaSettings.ENGINE_AUTO, EssentiaSettings.ENGINE_MODERN, EssentiaSettings.ENGINE_CLASSIC };
        String auto = getString(R.string.eng_auto);
        if (EssentiaSettings.learnedClassic(this, e.path)) auto += "  " + getString(R.string.eng_auto_learned);
        final String[] labels = { auto, getString(R.string.eng_modern), getString(R.string.eng_classic) };
        int checked = EssentiaSettings.ENGINE_CLASSIC.equals(e.engine) ? 2
            : EssentiaSettings.ENGINE_MODERN.equals(e.engine) ? 1 : 0;
        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
            .setTitle(R.string.opt_engine)
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                if (which == 2 && !available) {
                    Toast.makeText(this, R.string.eng_missing, Toast.LENGTH_LONG).show();
                    return;
                }
                e.engine = values[which];
                // choosing a fixed engine forgets what the automatic one learned
                if (which != 0) EssentiaSettings.setLearnedClassic(this, e.path, false);
                Library.update(this, e);
                d.dismiss();
                reload();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void chooseScreen(Library.Entry e)
    {
        final String[] values = { "", EssentiaSettings.SCREEN_WIDE_SAFE, EssentiaSettings.SCREEN_WIDE,
            EssentiaSettings.SCREEN_AMBIENT, EssentiaSettings.SCREEN_ORIGINAL, EssentiaSettings.SCREEN_STRETCH };
        final String[] labels = { getString(R.string.scr_general), getString(R.string.scr_wide_safe),
            getString(R.string.scr_wide), getString(R.string.scr_ambient), getString(R.string.scr_original),
            getString(R.string.scr_stretch) };
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(e.screenMode)) checked = i;
        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
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
        if (Games.isGameRunning(this) || UpdateService.hasWork() || GameRemoval.busy()) {
            Toast.makeText(this,"Cierra los juegos y espera a que terminen las instalaciones",Toast.LENGTH_LONG).show();
            return;
        }
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

        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
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
        new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
            .setTitle(R.string.imp_choose)
            .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
            .setPositiveButton(R.string.imp_do, (d, w) -> {
                List<File> chosen = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) if (checked[i]) chosen.add(files.get(i));
                if (Games.isGameRunning(this) || UpdateService.hasWork() || GameRemoval.busy()) {
                    Toast.makeText(this,"Cierra los juegos antes de importar partidas",Toast.LENGTH_LONG).show(); return;
                }
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
                if (Games.isGameRunning(this) || UpdateService.hasWork() || GameRemoval.busy()) {
                    Toast.makeText(this,"Cierra los juegos antes de importar partidas",Toast.LENGTH_LONG).show(); return;
                }
                int n = Saves.copyUris(this, uris, names, saveDirOf(mPending));
                Toast.makeText(this, getResources().getQuantityString(R.plurals.imp_done, n, n), Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    /** New games in the folders already added (in the background). */
    private void rescanFolders()
    {
        if (!EssentiaSettings.flag(this, EssentiaSettings.RESCAN, true)) return;
        java.util.Set<String> known = EssentiaSettings.gameRoots(this);
        if (known.isEmpty()) {
            // games added before this was remembered: their parent folders
            for (Library.Entry e : Library.load(this)) {
                File parent = new File(e.path).getParentFile();
                if (parent != null) EssentiaSettings.addGameRoot(this, parent.getAbsolutePath());
            }
            known = EssentiaSettings.gameRoots(this);
        }
        final java.util.Set<String> roots = known;
        if (roots.isEmpty()) return;
        new Thread(() -> {
            int added = 0;
            for (String r : roots) {
                File root = new File(r);
                if (!root.isDirectory()) continue;
                for (File f : Games.findGames(root)) if (Library.add(this, f)) added++;
            }
            if (added == 0) return;
            Shortcuts.refresh(this);
            final int n = added;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(this, getResources().getQuantityString(R.plurals.lib_found_new, n, n), Toast.LENGTH_LONG).show();
                reload();
            });
        }, "EssentiaRescan").start();
    }

    private void addGamesFrom(File root)
    {
        EssentiaSettings.addGameRoot(this, root.getAbsolutePath());
        Toast.makeText(this, R.string.lib_scanning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<File> found = Games.findGames(root);
            int added = 0;
            for (File f : found) if (Library.add(this, f)) added++;
            if (added > 0) Shortcuts.refresh(this);
            final int count = added, total = found.size();
            runOnUiThread(() -> {
                if (total == 0) {
                    new AlertDialog.Builder(this, com.hatkid.mkxpz.ui.Ui.dialogTheme())
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
