package za.saradio.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements RadioService.Listener {
    static final int TAB_RADIO = 0;
    static final int TAB_FAV = 1;
    static final int TAB_NEWS = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Sheet> sheets = new ArrayList<>();

    // Shell
    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout bottomArea;
    private View bottomFade;
    private TextView snack;
    private int insetTop;
    private int insetNav;
    private int insetIme;
    NowPlaying nowPlaying;

    // Header
    private TextView greeting;
    private TextView title;
    private ProgressBar topSpinner;
    private ImageView actionBtn;

    // Radio & favourites
    private LinearLayout radioPage;
    private EditText search;
    private ImageView clearSearch;
    private HorizontalScrollView provinceScroll;
    private LinearLayout provinceRow;
    private ListView grid;
    private GridAdapter adapter;
    private LinearLayout recentBlock;
    private LinearLayout recentRow;
    private TextView sectionTitle;
    private TextView sectionHint;
    private LinearLayout empty;
    private ImageView emptyIcon;
    private TextView emptyTitle;
    private TextView emptyBody;
    private TextView emptyButton;
    private int cols = 3;

    // News
    private LinearLayout newsPage;
    private LinearLayout newsChipRow;
    private TextView newsStatus;
    private ListView newsList;
    private NewsAdapter newsAdapter;
    private LinearLayout articlePage;
    private TextView articleSource;
    private ProgressBar webProgress;
    private WebView web;
    private FrameLayout articleBody;
    private ArticleView articleView;
    private ImageView sizeBtn;
    private ImageView modeBtn;
    private NewsRepo.Item articleItem;
    private boolean originalMode;
    private boolean toldBlocked;
    private final java.util.concurrent.atomic.AtomicInteger blockedCount = new java.util.concurrent.atomic.AtomicInteger();
    private Extractor extractor;
    private View headerView;

    // Mini player
    private LinearLayout mini;
    private LogoView miniArt;
    private TextView miniName;
    private TextView miniSub;
    private BarsView miniBars;
    private ProgressBar miniSpinner;
    private ImageView miniPlay;
    private String miniKey;

    private NavBar nav;

    // Data
    private List<Station> all = new ArrayList<>();
    private List<Station> favs = new ArrayList<>();
    private final List<Station> shown = new ArrayList<>();
    private List<NewsRepo.Item> news = new ArrayList<>();
    private final List<NewsRepo.Item> newsShown = new ArrayList<>();
    private int tab = TAB_RADIO;
    private String province = "";
    private String query = "";
    private String newsSource = "";
    private boolean loading;
    private boolean newsLoading;
    private long newsLoadedAt;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshPlayer();
            main.postDelayed(this, 15000);
        }
    };
    private final Runnable hideSnack = () -> snack.animate().alpha(0f).translationY(Ui.dp(this, 8)).setDuration(200)
            .withEndAction(() -> snack.setVisibility(View.GONE)).start();

    // ---- Lifecycle --------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        ImageLoader.init(this);
        goEdgeToEdge();
        favs = Prefs.favourites(this);
        if (RadioService.current == null) RadioService.current = Prefs.lastStation(this);
        setContentView(buildUi());
        extractor = new Extractor(this);
        String cache = StationRepo.loadCache(this);
        if (cache != null) all = StationRepo.parse(cache);
        news = NewsRepo.load(this);
        rebuildNewsChips();
        applyFilter();
        applyNewsFilter();
        // The list changes slowly; don't spend data re-downloading it on every launch.
        long age = System.currentTimeMillis() - Prefs.get(this).getLong("stationsAt", 0);
        long maxAge = DataMode.effective(this) == DataMode.SAVER ? 3 * 86400_000L : 12 * 3600_000L;
        if (all.isEmpty() || age > maxAge) refreshStations(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.setListener(this);
        main.post(ticker);
        if (web != null) web.onResume();
        rebuildRecents();
    }

    @Override
    protected void onPause() {
        RadioService.setListener(null);
        main.removeCallbacks(ticker);
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        int n = columnsFor();
        if (n != cols) {
            cols = n;
            adapter.notifyDataSetChanged();
        }
        if (nowPlaying != null) nowPlaying.relayout();
    }

    private String gridState;

    @Override
    public void onRadioState(Station s, int st, String msg) {
        refreshPlayer();
        String now = (s == null ? "" : s.key()) + "|" + st;
        if (!now.equals(gridState)) {
            gridState = now;
            adapter.notifyDataSetChanged();
            rebuildRecents();
        }
    }

    @Override
    public void onBackPressed() {
        if (!sheets.isEmpty()) {
            sheets.get(sheets.size() - 1).dismiss();
        } else if (nowPlaying != null && nowPlaying.isOpen()) {
            nowPlaying.close();
        } else if (tab == TAB_NEWS && articlePage.getVisibility() == View.VISIBLE) {
            if (originalMode && web != null && web.canGoBack()) web.goBack();
            else if (originalMode) showReader();
            else closeArticle();
        } else if (search.getText().length() > 0) {
            search.setText("");
        } else if (tab != TAB_RADIO) {
            setTab(TAB_RADIO);
        } else {
            moveTaskToBack(true);
        }
    }

    // ---- Insets -----------------------------------------------------------------------------

    private void goEdgeToEdge() {
        getWindow().setStatusBarColor(0);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setNavigationBarColor(0);
        } else if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            getWindow().setNavigationBarColor(0);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            getWindow().setNavigationBarColor(Ui.BG);
        }
    }

    private WindowInsets onInsets(View v, WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = in.getInsets(WindowInsets.Type.ime());
            insetTop = bars.top;
            insetNav = bars.bottom;
            insetIme = ime.bottom;
        } else {
            insetTop = in.getSystemWindowInsetTop();
            insetNav = in.getStableInsetBottom();
            int b = in.getSystemWindowInsetBottom();
            insetIme = b > insetNav + Ui.dp(this, 80) ? b : 0;
        }
        content.setPadding(0, insetTop, 0, 0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomArea.getLayoutParams();
        lp.bottomMargin = insetNav;
        bottomArea.setLayoutParams(lp);
        for (Sheet s : sheets) s.applyInsets(insetNav, insetIme, insetTop);
        if (nowPlaying != null) nowPlaying.applyInsets(insetTop, insetNav);
        return in;
    }

    FrameLayout root() {
        return root;
    }

    int topInset() {
        return insetTop;
    }

    int navInset() {
        return insetNav;
    }

    int imeInset() {
        return insetIme;
    }

    void pushSheet(Sheet s) {
        sheets.add(s);
    }

    void popSheet(Sheet s) {
        sheets.remove(s);
    }

    // ---- Stations ---------------------------------------------------------------------------

    void refreshStations(final boolean announce) {
        if (loading) return;
        loading = true;
        updateHeaderSpinner();
        updateSection();
        new Thread(() -> {
            String raw;
            try {
                raw = StationRepo.fetchRaw();
            } catch (Exception e) {
                raw = null;
            }
            final List<Station> list = raw != null ? StationRepo.parse(raw) : null;
            if (list != null && !list.isEmpty()) StationRepo.saveCache(this, raw);
            runOnUiThread(() -> {
                loading = false;
                if (list != null && !list.isEmpty()) {
                    all = list;
                    if (announce) snack(list.size() + " stations up to date");
                } else if (announce || all.isEmpty()) {
                    snack("Couldn't update stations. Check your connection.");
                }
                ImageLoader.clearFailures();
                applyFilter();
                updateHeaderSpinner();
            });
        }, "stations").start();
    }

    private void applyFilter() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        String[] words = q.length() > 0 ? q.split("\\s+") : new String[0];
        for (Station s : tab == TAB_FAV ? favs : all) {
            if (tab == TAB_RADIO && province.length() > 0 && !province.equals(s.province)) continue;
            if (words.length > 0) {
                String hay = (s.name + " " + s.tags + " " + s.state + " " + s.province).toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String w : words) if (!hay.contains(w)) ok = false;
                if (!ok) continue;
            }
            shown.add(s);
        }
        adapter.notifyDataSetChanged();
        updateSection();
        rebuildRecents();
    }

    private void updateSection() {
        if (sectionTitle == null) return;
        boolean filtered = query.trim().length() > 0 || province.length() > 0;
        if (tab == TAB_FAV) {
            sectionTitle.setText(filtered ? "Matching favourites" : "Your favourites");
            sectionHint.setText(favs.size() + " saved · hold a station to remove it");
        } else {
            sectionTitle.setText(filtered ? (province.length() > 0 ? province : "Results") : "All stations");
            sectionHint.setText(shown.size() + (shown.size() == 1 ? " station" : " stations") + " · tap to play, hold to save");
        }
        View header = (View) sectionTitle.getParent();
        header.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);

        boolean showEmpty = shown.isEmpty();
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (!showEmpty) return;
        emptyButton.setVisibility(View.GONE);
        if (tab == TAB_FAV && favs.isEmpty()) {
            emptyIcon.setImageResource(R.drawable.ic_star);
            emptyTitle.setText("No favourites yet");
            emptyBody.setText("Hold any station to save it here, or add a station by its stream link.");
            emptyButton.setText("Add a station");
            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setOnClickListener(v -> Panels.addStation(this));
        } else if (tab == TAB_FAV || !all.isEmpty()) {
            emptyIcon.setImageResource(R.drawable.ic_search);
            emptyTitle.setText("Nothing matches");
            emptyBody.setText("Try another name, genre or city" + (province.length() > 0 ? ", or look across all of SA." : "."));
            if (province.length() > 0) {
                emptyButton.setText("Search all of SA");
                emptyButton.setVisibility(View.VISIBLE);
                emptyButton.setOnClickListener(v -> {
                    province = "";
                    rebuildProvinceChips();
                    applyFilter();
                });
            }
        } else if (loading) {
            emptyIcon.setImageResource(R.drawable.ic_nav_radio);
            emptyTitle.setText("Tuning in…");
            emptyBody.setText("Fetching South African stations.");
        } else {
            emptyIcon.setImageResource(R.drawable.ic_wifi);
            emptyTitle.setText("Can't reach the station list");
            emptyBody.setText("Check your connection and try again.");
            emptyButton.setText("Try again");
            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setOnClickListener(v -> refreshStations(true));
        }
    }

    boolean isFav(Station s) {
        for (Station f : favs) if (f.key().equals(s.key())) return true;
        return false;
    }

    void toggleFav(Station s) {
        if (isFav(s)) {
            for (int i = favs.size() - 1; i >= 0; i--) if (favs.get(i).key().equals(s.key())) favs.remove(i);
            snack("Removed from favourites");
        } else {
            favs.add(s);
            snack("Saved to favourites");
        }
        Prefs.saveFavourites(this, favs);
        applyFilter();
        refreshPlayer();
    }

    void addCustomStation(String name, String url) {
        Station s = new Station();
        s.name = name.length() > 0 ? name : "My station";
        s.url = url;
        s.custom = true;
        if (!isFav(s)) favs.add(s);
        Prefs.saveFavourites(this, favs);
        setTab(TAB_FAV);
        playStation(s);
    }

    int stationCount() {
        return all.size();
    }

    // ---- Playback ---------------------------------------------------------------------------

    void playStation(Station s) {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.get(this).getBoolean("askedNotif", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Asked once; the player controls in the notification need it, but nagging on every play doesn't help.
            Prefs.get(this).edit().putBoolean("askedNotif", true).apply();
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        s = withLatestStreams(s);
        Station cur = RadioService.current;
        boolean same = cur != null && cur.key().equals(s.key());
        if (same && RadioService.isActive()) {
            openNowPlaying();
            return;
        }
        if (!same) RadioService.songTitle = "";
        RadioService.current = s;
        RadioService.state = RadioService.BUFFERING;
        RadioService.message = "Connecting…";
        RadioService.send(this, RadioService.ACTION_PLAY, s, 0);
        refreshPlayer();
        adapter.notifyDataSetChanged();
        hideKeyboard();
    }

    /** Favourites and recents saved earlier may lack the station's other streams; use the fresh listing. */
    private Station withLatestStreams(Station s) {
        if (s.custom) return s;
        for (Station x : all) if (x.key().equals(s.key())) return x;
        return s;
    }

    void togglePlay() {
        Station s = RadioService.current;
        if (s == null) return;
        if (RadioService.isActive()) RadioService.send(this, RadioService.ACTION_STOP, null, 0);
        else playStation(s);
    }

    void openNowPlaying() {
        if (RadioService.current == null) return;
        if (nowPlaying == null) nowPlaying = new NowPlaying(this);
        hideKeyboard();
        nowPlaying.open();
    }

    /** Called when the data &amp; quality setting changes. */
    void onDataModeChanged() {
        if (RadioService.isActive()) RadioService.send(this, RadioService.ACTION_QUALITY, null, 0);
        refreshPlayer();
    }

    static String playerLine() {
        int st = RadioService.state;
        if (st == RadioService.PLAYING && RadioService.songTitle.length() > 0) return RadioService.songTitle;
        if (st == RadioService.PLAYING) return "Live now";
        String m = RadioService.message;
        return m == null || m.length() == 0 ? "Tap play to listen" : m;
    }

    void refreshPlayer() {
        Station s = RadioService.current;
        if (s == null) {
            mini.setVisibility(View.GONE);
            return;
        }
        if (mini.getVisibility() != View.VISIBLE) {
            mini.setVisibility(View.VISIBLE);
            mini.setTranslationY(Ui.dp(this, 90));
            mini.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
        }
        int st = RadioService.state;
        boolean playing = st == RadioService.PLAYING;
        if (!s.key().equals(miniKey)) {
            miniKey = s.key();
            miniName.setText(s.name);
            bindLogo(miniArt, s, 160);
        }
        miniSub.setText(playerLine());
        miniSub.setTextColor(st == RadioService.ERROR ? Ui.RED : playing && RadioService.songTitle.length() > 0 ? Ui.TEXT2 : Ui.MUTED);
        miniBars.setVisibility(playing ? View.VISIBLE : View.GONE);
        miniBars.setRunning(playing);
        miniSpinner.setVisibility(st == RadioService.BUFFERING ? View.VISIBLE : View.GONE);
        boolean active = RadioService.isActive();
        miniPlay.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_play);
        miniPlay.setContentDescription(active ? "Stop" : "Play");
        mini.setContentDescription(s.name + ", " + playerLine() + ". Open player");
        tintMini();
        if (nowPlaying != null) nowPlaying.refresh();
    }

    private void tintMini() {
        int mood = Ui.moodColor(miniArt.getAccentColor());
        GradientDrawable g = Ui.gradient(GradientDrawable.Orientation.LEFT_RIGHT, Ui.dp(this, 24),
                Ui.blend(Ui.SURFACE2, mood, 0.32f), Ui.SURFACE2);
        g.setStroke(Ui.dp(this, 1), Ui.STROKE2);
        mini.setBackground(g);
    }

    void bindLogo(final LogoView v, Station s, int px) {
        v.setStation(s.name, s.favicon);
        Bitmap b = ImageLoader.cached(s.favicon, px);
        if (b == null) b = ImageLoader.cachedAny(s.favicon);
        if (b != null) v.setLogo(b);
        ImageLoader.get(s.favicon, px, new ImageLoader.Target() {
            @Override
            public boolean wants(String url) {
                return url.equals(v.getUrl());
            }

            @Override
            public void onBitmap(String url, Bitmap bm) {
                v.setLogo(bm);
                if (v == miniArt) tintMini();
                if (nowPlaying != null) nowPlaying.onArt(v);
            }
        });
    }

    // ---- Tabs -------------------------------------------------------------------------------

    void setTab(int t) {
        if (t != tab) {
            hideKeyboard();
            if (tab == TAB_NEWS && articlePage.getVisibility() == View.VISIBLE) closeArticle();
        }
        tab = t;
        boolean stations = t != TAB_NEWS;
        radioPage.setVisibility(stations ? View.VISIBLE : View.GONE);
        newsPage.setVisibility(!stations && articlePage.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        if (stations) articlePage.setVisibility(View.GONE);
        provinceScroll.setVisibility(t == TAB_RADIO ? View.VISIBLE : View.GONE);
        search.setHint(t == TAB_FAV ? "Search your favourites" : "Search stations, genres, cities");
        title.setText(t == TAB_RADIO ? "SA Radio" : t == TAB_FAV ? "Favourites" : "News");
        greeting.setText(t == TAB_NEWS ? "Read while you listen" : t == TAB_FAV ? "Your stations" : greetingText());
        if (t == TAB_FAV) {
            actionBtn.setImageResource(R.drawable.ic_plus);
            actionBtn.setContentDescription("Add a station");
            actionBtn.setTooltipText("Add a station");
            actionBtn.setVisibility(View.VISIBLE);
        } else if (t == TAB_NEWS) {
            actionBtn.setImageResource(R.drawable.ic_refresh);
            actionBtn.setContentDescription("Refresh headlines");
            actionBtn.setTooltipText("Refresh headlines");
            actionBtn.setVisibility(View.VISIBLE);
        } else {
            actionBtn.setVisibility(View.GONE);
        }
        nav.select(t, true);
        if (stations) {
            applyFilter();
            grid.setSelection(0);
        }
        if (t == TAB_NEWS && (news.isEmpty() || System.currentTimeMillis() - newsLoadedAt > 15 * 60_000L)) refreshNews(false);
        updateHeaderSpinner();
    }

    private static String greetingText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    private void updateHeaderSpinner() {
        boolean busy = tab == TAB_NEWS ? newsLoading : loading;
        topSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- UI construction --------------------------------------------------------------------

    /** Phones shorter than ~700dp (older 5-inch screens) get a tighter layout. */
    private boolean shortScreen() {
        return getResources().getConfiguration().screenHeightDp < 700;
    }

    private int columnsFor() {
        float wdp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return Math.max(3, (int) (wdp / 124));
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.addView(new BackdropView(this), new FrameLayout.LayoutParams(-1, -1));

        content = Ui.column(this);
        content.addView(buildHeader());
        FrameLayout pages = new FrameLayout(this);
        content.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        cols = columnsFor();
        pages.addView(buildRadioPage());
        pages.addView(buildNewsPage());
        pages.addView(buildArticlePage());

        // Bottom: a soft fade, the mini player and the floating nav. Lists scroll underneath.
        bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x000B0D12, 0xE60B0D12, Ui.BG}));
        root.addView(bottomFade, new FrameLayout.LayoutParams(-1, Ui.dp(this, 120), Gravity.BOTTOM));
        bottomArea = Ui.column(this);
        bottomArea.addView(buildMini(), Ui.lp(-1, -2, Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 10)));
        nav = new NavBar(this, new int[] {R.drawable.ic_nav_radio, R.drawable.ic_star, R.drawable.ic_nav_news},
                new String[] {"Radio", "Favourites", "News"});
        nav.setListener(this::setTab);
        bottomArea.addView(nav, Ui.lp(-1, Ui.dp(this, 60), Ui.dp(this, 22), 0, Ui.dp(this, 22), Ui.dp(this, 12)));
        root.addView(bottomArea, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        bottomArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || t != ot) main.post(this::padLists);
        });

        snack = Ui.text(this, "", 14, Ui.TEXT, Ui.W_BOLD);
        snack.setGravity(Gravity.CENTER);
        snack.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        snack.setBackground(Ui.roundStroke(Ui.SURFACE3, Ui.dp(this, 22), Ui.STROKE2, Ui.dp(this, 1)));
        snack.setElevation(Ui.dp(this, 16));
        snack.setVisibility(View.GONE);
        snack.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(snack, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        root.setOnApplyWindowInsetsListener(this::onInsets);
        setTab(TAB_RADIO);
        nav.select(TAB_RADIO, false);
        refreshPlayer();
        return root;
    }

    /** Keeps the end of each list clear of the floating player and nav. */
    private void padLists() {
        int h = bottomArea.getHeight() + insetNav + Ui.dp(this, 8);
        grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), h);
        newsList.setPadding(newsList.getPaddingLeft(), newsList.getPaddingTop(), newsList.getPaddingRight(), h);
        ViewGroup.LayoutParams fl = bottomFade.getLayoutParams();
        fl.height = h + Ui.dp(this, 24);
        bottomFade.setLayoutParams(fl);
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) snack.getLayoutParams();
        sl.bottomMargin = h + Ui.dp(this, 4);
        snack.setLayoutParams(sl);
        FrameLayout.LayoutParams el = (FrameLayout.LayoutParams) empty.getLayoutParams();
        el.bottomMargin = h - insetNav;
        empty.setLayoutParams(el);
    }

    private View buildHeader() {
        LinearLayout h = Ui.row(this);
        headerView = h;
        boolean tight = shortScreen();
        h.setPadding(Ui.dp(this, 22), Ui.dp(this, tight ? 8 : 14), Ui.dp(this, 14), Ui.dp(this, tight ? 8 : 12));
        LinearLayout titles = Ui.column(this);
        LinearLayout gl = Ui.row(this);
        gl.addView(new FlagStripe(this), Ui.lp(Ui.dp(this, 22), Ui.dp(this, 4), 0, 0, Ui.dp(this, 8), 0));
        greeting = Ui.text(this, greetingText(), 13.5f, Ui.MUTED, Ui.W_BOLD);
        gl.addView(greeting);
        titles.addView(gl);
        title = Ui.text(this, "SA Radio", tight ? 26 : 31, Ui.TEXT, Ui.W_HEAVY);
        title.setLetterSpacing(-0.02f);
        title.setPadding(0, Ui.dp(this, tight ? 4 : 6), 0, 0);
        Ui.heading(title);
        titles.addView(title);
        h.addView(titles, Ui.weight(1));

        topSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        topSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GOLD));
        topSpinner.setContentDescription("Loading");
        h.addView(topSpinner, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20), 0, 0, Ui.dp(this, 6), 0));

        actionBtn = Ui.iconButton(this, R.drawable.ic_refresh, Ui.TEXT, Ui.alpha(Ui.SURFACE2, 0.9f), 46, "Refresh");
        actionBtn.setOnClickListener(v -> {
            if (tab == TAB_NEWS) {
                v.animate().rotationBy(360).setDuration(500).start();
                refreshNews(true);
            } else {
                Panels.addStation(this);
            }
        });
        h.addView(actionBtn, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46), Ui.dp(this, 4), 0, 0, 0));
        ImageView settings = Ui.iconButton(this, R.drawable.ic_tune, Ui.TEXT, Ui.alpha(Ui.SURFACE2, 0.9f), 46, "Settings: data, sound and stations");
        settings.setOnClickListener(v -> Panels.settings(this));
        h.addView(settings, Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46), Ui.dp(this, 8), 0, 0, 0));
        return h;
    }

    private View buildRadioPage() {
        radioPage = Ui.column(this);

        LinearLayout box = Ui.row(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 6), 0);
        box.setBackground(Ui.roundStroke(Ui.alpha(Ui.SURFACE2, 0.92f), Ui.dp(this, 18), Ui.STROKE, Ui.dp(this, 1)));
        ImageView si = new ImageView(this);
        si.setImageResource(R.drawable.ic_search);
        si.setColorFilter(Ui.MUTED);
        box.addView(si, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20)));
        search = new EditText(this);
        search.setHint("Search stations, genres, cities");
        search.setHintTextColor(Ui.MUTED);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(15);
        search.setTypeface(Ui.medium);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString();
                clearSearch.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            hideKeyboard();
            return true;
        });
        box.addView(search, Ui.weight(1));
        clearSearch = Ui.iconButton(this, R.drawable.ic_close, Ui.MUTED, 0, 40, "Clear search");
        clearSearch.setBackground(null);
        clearSearch.setVisibility(View.GONE);
        clearSearch.setOnClickListener(v -> search.setText(""));
        box.addView(clearSearch);
        radioPage.addView(box, Ui.lp(-1, Ui.dp(this, 52), Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12)));

        provinceScroll = new HorizontalScrollView(this);
        provinceScroll.setHorizontalScrollBarEnabled(false);
        provinceScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        provinceRow = Ui.row(this);
        provinceRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        provinceScroll.addView(provinceRow);
        rebuildProvinceChips();
        radioPage.addView(provinceScroll, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(this, 4)));

        FrameLayout listHolder = new FrameLayout(this);
        grid = new ListView(this);
        grid.setDivider(null);
        grid.setDividerHeight(0);
        grid.setSelector(android.R.color.transparent);
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 160));
        grid.addHeaderView(buildRecents(), null, false);
        grid.addHeaderView(buildSectionHeader(), null, false);
        adapter = new GridAdapter();
        grid.setAdapter(adapter);
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {
                if (s != SCROLL_STATE_IDLE) hideKeyboard();
            }

            @Override
            public void onScroll(AbsListView v, int a, int b, int c) {}
        });
        listHolder.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        listHolder.addView(buildEmpty(), new FrameLayout.LayoutParams(-1, -1));
        radioPage.addView(listHolder, new LinearLayout.LayoutParams(-1, 0, 1f));
        return radioPage;
    }

    private View buildRecents() {
        recentBlock = Ui.column(this);
        TextView t = Ui.text(this, "Jump back in", 18, Ui.TEXT, Ui.W_HEAVY);
        Ui.heading(t);
        t.setPadding(Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 22), Ui.dp(this, 10));
        recentBlock.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recentRow = Ui.row(this);
        recentRow.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        recentRow.setGravity(Gravity.TOP);
        hs.addView(recentRow);
        recentBlock.addView(hs);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(recentBlock);
        return wrap;
    }

    private void rebuildRecents() {
        if (recentRow == null) return;
        List<Station> rec = Prefs.recents(this);
        boolean show = tab == TAB_RADIO && query.trim().length() == 0 && province.length() == 0 && rec.size() >= 2
                && !shown.isEmpty() && !shortScreen();
        recentBlock.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        recentRow.removeAllViews();
        Station cur = RadioService.current;
        for (final Station s : rec) {
            LinearLayout cell = Ui.column(this);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LogoView art = new LogoView(this);
            bindLogo(art, s, DataMode.logoPx(DataMode.effective(this)));
            boolean on = cur != null && cur.key().equals(s.key()) && RadioService.isActive();
            art.setPlaying(on);
            cell.addView(art, Ui.lp(Ui.dp(this, 84), Ui.dp(this, 84)));
            TextView n = Ui.text(this, s.name, 12.5f, on ? Ui.GOLD : Ui.TEXT2, Ui.W_BOLD);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setGravity(Gravity.CENTER);
            cell.addView(n, Ui.lp(Ui.dp(this, 84), -2, 0, Ui.dp(this, 4), 0, 0));
            cell.setOnClickListener(v -> playStation(s));
            cell.setContentDescription(s.name);
            Ui.press(cell, 0.93f);
            recentRow.addView(cell, Ui.lp(Ui.dp(this, 92), -2));
        }
    }

    private View buildSectionHeader() {
        LinearLayout h = Ui.column(this);
        h.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 10));
        sectionTitle = Ui.text(this, "All stations", 18, Ui.TEXT, Ui.W_HEAVY);
        Ui.heading(sectionTitle);
        h.addView(sectionTitle);
        sectionHint = Ui.text(this, "", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        sectionHint.setPadding(0, Ui.dp(this, 4), 0, 0);
        h.addView(sectionHint);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(h);
        return wrap;
    }

    private View buildEmpty() {
        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 40), 0, Ui.dp(this, 40), 0);
        emptyIcon = new ImageView(this);
        emptyIcon.setColorFilter(Ui.GOLD);
        int p = Ui.dp(this, 20);
        emptyIcon.setPadding(p, p, p, p);
        emptyIcon.setBackground(Ui.round(Ui.alpha(Ui.GOLD, 0.12f), Ui.dp(this, 36)));
        empty.addView(emptyIcon, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)));
        emptyTitle = Ui.text(this, "", 19, Ui.TEXT, Ui.W_HEAVY);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, Ui.lp(-2, -2, 0, Ui.dp(this, 18), 0, 0));
        emptyBody = Ui.text(this, "", 14.5f, Ui.MUTED, Ui.W_MEDIUM);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setLineSpacing(0, 1.3f);
        empty.addView(emptyBody, Ui.lp(-2, -2, 0, Ui.dp(this, 8), 0, 0));
        emptyButton = Ui.primaryButton(this, "");
        empty.addView(emptyButton, Ui.lp(-2, -2, 0, Ui.dp(this, 22), 0, 0));
        empty.setVisibility(View.GONE);
        return empty;
    }

    private void rebuildProvinceChips() {
        provinceRow.removeAllViews();
        addChip(provinceRow, "All of SA", province.length() == 0, v -> {
            province = "";
            rebuildProvinceChips();
            applyFilter();
            grid.setSelection(0);
        });
        for (final String p : StationRepo.PROVINCES) {
            addChip(provinceRow, p, p.equals(province), v -> {
                province = p;
                rebuildProvinceChips();
                applyFilter();
                grid.setSelection(0);
            });
        }
    }

    private void addChip(LinearLayout row, String label, boolean on, View.OnClickListener l) {
        TextView c = Ui.chip(this, label, on);
        c.setOnClickListener(l);
        row.addView(c, Ui.lp(-2, -2, 0, 0, Ui.dp(this, 8), 0));
    }

    // ---- Station grid -----------------------------------------------------------------------

    private static final class Tile {
        View view;
        LogoView art;
        TextView name;
        TextView sub;
        String key;
        Station station;
    }

    private final class GridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return (shown.size() + cols - 1) / cols;
        }

        @Override
        public Object getItem(int i) {
            return i;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean isEnabled(int position) {
            return false; // tiles handle their own taps
        }

        @Override
        public View getView(int row, View convert, ViewGroup parent) {
            LinearLayout r = (LinearLayout) convert;
            if (r == null || r.getChildCount() != cols) {
                r = Ui.row(MainActivity.this);
                r.setGravity(Gravity.TOP);
                r.setPadding(Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 6));
                Tile[] tiles = new Tile[cols];
                for (int i = 0; i < cols; i++) {
                    tiles[i] = makeTile();
                    r.addView(tiles[i].view, Ui.weight(1));
                }
                r.setTag(tiles);
            }
            Tile[] tiles = (Tile[]) r.getTag();
            for (int i = 0; i < cols; i++) {
                int idx = row * cols + i;
                Tile t = tiles[i];
                if (idx >= shown.size()) {
                    t.view.setVisibility(View.INVISIBLE);
                    t.station = null;
                    continue;
                }
                t.view.setVisibility(View.VISIBLE);
                bindTile(t, shown.get(idx));
            }
            return r;
        }
    }

    private Tile makeTile() {
        final Tile t = new Tile();
        LinearLayout v = Ui.column(this);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 10));
        t.art = new LogoView(this);
        v.addView(t.art, Ui.lp(-1, -2));
        t.name = Ui.text(this, "", 13.5f, Ui.TEXT, Ui.W_BOLD);
        t.name.setGravity(Gravity.CENTER);
        t.name.setMaxLines(2);
        t.name.setEllipsize(TextUtils.TruncateAt.END);
        t.name.setLineSpacing(0, 1.0f);
        t.name.setPadding(Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2), 0);
        v.addView(t.name, Ui.lp(-1, -2));
        t.sub = Ui.text(this, "", 11.5f, Ui.MUTED, Ui.W_MEDIUM);
        t.sub.setGravity(Gravity.CENTER);
        t.sub.setSingleLine(true);
        t.sub.setEllipsize(TextUtils.TruncateAt.END);
        t.sub.setPadding(Ui.dp(this, 2), Ui.dp(this, 3), Ui.dp(this, 2), 0);
        v.addView(t.sub, Ui.lp(-1, -2));
        v.setOnClickListener(x -> {
            if (t.station != null) playStation(t.station);
        });
        v.setOnLongClickListener(x -> {
            if (t.station == null) return false;
            x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toggleFav(t.station);
            return true;
        });
        Ui.press(v, 0.93f);
        t.view = v;
        return t;
    }

    private void bindTile(Tile t, Station s) {
        t.station = s;
        if (!s.key().equals(t.key)) {
            t.key = s.key();
            bindLogo(t.art, s, DataMode.logoPx(DataMode.effective(this)));
        }
        Station cur = RadioService.current;
        boolean isCur = cur != null && cur.key().equals(s.key());
        boolean on = isCur && RadioService.isActive();
        t.art.setPlaying(on);
        t.art.setFav(tab == TAB_RADIO && isFav(s));
        t.name.setText(s.name);
        t.name.setTextColor(isCur ? Ui.GOLD : Ui.TEXT);
        if (on) {
            t.sub.setText(RadioService.state == RadioService.PLAYING ? "● Live" : "Connecting…");
            t.sub.setTextColor(RadioService.state == RadioService.PLAYING ? Ui.LIVE : Ui.MUTED);
        } else {
            t.sub.setText(shortSub(s));
            t.sub.setTextColor(Ui.MUTED);
        }
        boolean fav = isFav(s);
        t.view.setContentDescription(s.name + ", " + shortSub(s) + (on ? ", playing" : "") + (fav ? ", favourite" : ""));
    }

    static String shortSub(Station s) {
        if (s.custom) return "Your stream";
        String tag = s.tags == null ? "" : s.tags.split(",")[0].trim();
        if (tag.length() > 14 || tag.equalsIgnoreCase("south africa") || tag.equalsIgnoreCase("za")) tag = "";
        if (tag.length() > 0) tag = Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
        String p = s.province;
        if (p.length() > 0 && tag.length() > 0) return tag + " · " + StationRepo.abbreviate(p);
        if (p.length() > 0) return p;
        return tag.length() > 0 ? tag : "Live radio";
    }

    // ---- News -------------------------------------------------------------------------------

    void refreshNews(final boolean announce) {
        if (newsLoading) return;
        newsLoading = true;
        updateNewsStatus();
        updateHeaderSpinner();
        new Thread(() -> {
            final List<NewsRepo.Item> fresh = NewsRepo.fetchAll();
            if (!fresh.isEmpty()) NewsRepo.save(this, fresh);
            runOnUiThread(() -> {
                newsLoading = false;
                if (!fresh.isEmpty()) {
                    news = fresh;
                    newsLoadedAt = System.currentTimeMillis();
                } else if (announce || news.isEmpty()) {
                    snack("Couldn't load the news. Check your connection.");
                }
                rebuildNewsChips();
                applyNewsFilter();
                updateHeaderSpinner();
            });
        }, "news").start();
    }

    private void applyNewsFilter() {
        newsShown.clear();
        for (NewsRepo.Item it : news) if (newsSource.length() == 0 || newsSource.equals(it.source)) newsShown.add(it);
        newsAdapter.notifyDataSetChanged();
        updateNewsStatus();
    }

    private void updateNewsStatus() {
        if (newsStatus == null) return;
        if (!news.isEmpty()) {
            Set<String> pubs = new LinkedHashSet<>();
            for (NewsRepo.Item it : news) pubs.add(it.source);
            newsStatus.setText(newsShown.size() + " stories" + (newsSource.length() > 0 ? " from " + newsSource : " from " + pubs.size() + " publishers")
                    + (newsLoading ? " · updating…" : ""));
        } else {
            newsStatus.setText(newsLoading ? "Fetching the latest headlines…" : "No headlines yet. Tap refresh to try again.");
        }
    }

    private void rebuildNewsChips() {
        newsChipRow.removeAllViews();
        Set<String> have = new LinkedHashSet<>();
        for (String[] f : NewsRepo.FEEDS) {
            for (NewsRepo.Item it : news) {
                if (it.source.equals(f[0])) {
                    have.add(f[0]);
                    break;
                }
            }
        }
        if (newsSource.length() > 0 && !have.contains(newsSource)) newsSource = "";
        addChip(newsChipRow, "Top stories", newsSource.length() == 0, v -> {
            newsSource = "";
            rebuildNewsChips();
            applyNewsFilter();
            newsList.setSelection(0);
        });
        for (final String s : have) {
            addChip(newsChipRow, s, s.equals(newsSource), v -> {
                newsSource = s;
                rebuildNewsChips();
                applyNewsFilter();
                newsList.setSelection(0);
            });
        }
    }

    private View buildNewsPage() {
        newsPage = Ui.column(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        newsChipRow = Ui.row(this);
        newsChipRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        hs.addView(newsChipRow);
        newsPage.addView(hs);
        newsStatus = Ui.text(this, "", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        newsStatus.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 6));
        newsPage.addView(newsStatus);
        newsList = new ListView(this);
        newsList.setDivider(null);
        newsList.setDividerHeight(0);
        newsList.setSelector(android.R.color.transparent);
        newsList.setClipToPadding(false);
        newsList.setVerticalScrollBarEnabled(false);
        newsList.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 160));
        newsAdapter = new NewsAdapter();
        newsList.setAdapter(newsAdapter);
        newsList.setOnItemClickListener((p, v, i, id) -> {
            if (i < newsShown.size()) openArticle(newsShown.get(i));
        });
        newsPage.addView(newsList, new LinearLayout.LayoutParams(-1, 0, 1f));
        newsPage.setVisibility(View.GONE);
        return newsPage;
    }

    private View buildArticlePage() {
        articlePage = Ui.column(this);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, Ui.TEXT, Ui.SURFACE2, 44, "Back to headlines");
        back.setOnClickListener(v -> closeArticle());
        bar.addView(back);
        articleSource = Ui.text(this, "", 16, Ui.TEXT, Ui.W_BOLD);
        articleSource.setSingleLine(true);
        articleSource.setEllipsize(TextUtils.TruncateAt.END);
        articleSource.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        bar.addView(articleSource, Ui.weight(1));
        sizeBtn = Ui.iconButton(this, R.drawable.ic_text_size, Ui.TEXT, Ui.SURFACE2, 44, "Text size");
        sizeBtn.setOnClickListener(v -> {
            int next = (ArticleView.textSize(this) + 1) % ArticleView.SIZES.length;
            Prefs.get(this).edit().putInt("readerSize", next).apply();
            articleView.restyle();
            snack("Text size: " + ArticleView.SIZE_NAMES[next]);
        });
        bar.addView(sizeBtn, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44), Ui.dp(this, 8), 0, 0, 0));
        ImageView share = Ui.iconButton(this, R.drawable.ic_share, Ui.TEXT, Ui.SURFACE2, 44, "Share article");
        share.setOnClickListener(v -> shareArticle());
        bar.addView(share, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44), Ui.dp(this, 8), 0, 0, 0));
        modeBtn = Ui.iconButton(this, R.drawable.ic_globe, Ui.TEXT, Ui.SURFACE2, 44, "Show original page");
        modeBtn.setOnClickListener(v -> {
            if (originalMode) showReader();
            else showOriginal();
        });
        bar.addView(modeBtn, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44), Ui.dp(this, 8), 0, 0, 0));
        articlePage.addView(bar);
        webProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        webProgress.setMax(100);
        webProgress.setProgressTintList(ColorStateList.valueOf(Ui.GOLD));
        webProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE2));
        webProgress.setVisibility(View.GONE);
        articlePage.addView(webProgress, Ui.lp(-1, Ui.dp(this, 3)));
        articleBody = new FrameLayout(this);
        articleView = new ArticleView(this, new ArticleView.Host() {
            @Override
            public void openOriginal() {
                showOriginal();
            }

            @Override
            public void openExternal(String url) {
                openInBrowser(url);
            }

            @Override
            public void shareArticle() {
                MainActivity.this.shareArticle();
            }
        });
        articleBody.addView(articleView, new FrameLayout.LayoutParams(-1, -1));
        articlePage.addView(articleBody, new LinearLayout.LayoutParams(-1, 0, 1f));
        articlePage.setVisibility(View.GONE);
        return articlePage;
    }

    /** Opens a story in the reader: just the words and pictures, drawn by the app. */
    private void openArticle(final NewsRepo.Item it) {
        enterArticle(it);
        Article known = extractor.cached(it.link);
        if (known != null) {
            articleView.show(it, known);
            return;
        }
        articleView.loading(it);
        int eff = DataMode.effective(this);
        int want = eff == DataMode.BEST ? 1080 : eff == DataMode.SAVER ? 540 : 800;
        extractor.extract(it.link, want, art -> {
            if (articleItem != it || originalMode) return;
            if (art.ok) articleView.show(it, art);
            else articleView.failed(it, art);
        });
    }

    ArticleView articleView() {
        return articleView;
    }

    /** Tests: show an already-extracted story. */
    void openArticleWith(NewsRepo.Item it, Article art) {
        enterArticle(it);
        if (art.ok) articleView.show(it, art);
        else articleView.failed(it, art);
    }

    private void enterArticle(NewsRepo.Item it) {
        articleItem = it;
        articleSource.setText(it.source);
        headerView.setVisibility(View.GONE); // more room for the story
        showReader();
        newsPage.setVisibility(View.GONE);
        articlePage.setVisibility(View.VISIBLE);
        articlePage.setAlpha(0f);
        articlePage.setTranslationX(Ui.dp(this, 40));
        articlePage.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        // The story scrolls under the player and nav; keep its end clear of them.
        articleView.setBottomPadding(bottomArea.getHeight() + insetNav + Ui.dp(this, 8));
    }

    private void showReader() {
        originalMode = false;
        if (web != null) {
            web.stopLoading();
            web.setVisibility(View.GONE);
        }
        webProgress.setVisibility(View.GONE);
        articleView.setVisibility(View.VISIBLE);
        modeBtn.setImageResource(R.drawable.ic_globe);
        modeBtn.setContentDescription("Show original page");
        modeBtn.setTooltipText("Show original page");
        sizeBtn.setVisibility(View.VISIBLE);
    }

    /** The publisher's own page, with ad networks and trackers blocked. */
    private void showOriginal() {
        if (articleItem == null) return;
        originalMode = true;
        ensureWeb();
        web.getSettings().setLoadsImagesAutomatically(DataMode.effective(this) != DataMode.SAVER);
        blockedCount.set(0);
        toldBlocked = false;
        web.loadUrl(articleItem.link);
        web.setVisibility(View.VISIBLE);
        articleView.setVisibility(View.GONE);
        modeBtn.setImageResource(R.drawable.ic_nav_news);
        modeBtn.setContentDescription("Back to reader view");
        modeBtn.setTooltipText("Back to reader view");
        sizeBtn.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
        lp.bottomMargin = bottomArea.getHeight() + insetNav;
        web.setLayoutParams(lp);
    }

    private void ensureWeb() {
        if (web != null) return;
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(0xFFFFFFFF);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String scheme = r.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (AdBlock.blocked(r.getUrl().getHost())) {
                    blockedCount.incrementAndGet();
                    return AdBlock.empty();
                }
                return null;
            }

            @Override
            public void onPageCommitVisible(WebView v, String url) {
                v.evaluateJavascript(AdBlock.injectScript(), null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(AdBlock.injectScript(), null);
                int n = blockedCount.get();
                if (originalMode && !toldBlocked && n > 0) {
                    toldBlocked = true;
                    snack(n + (n == 1 ? " ad or tracker blocked" : " ads and trackers blocked"));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                webProgress.setProgress(p);
                webProgress.setVisibility(originalMode && p < 100 ? View.VISIBLE : View.GONE);
            }
        });
        articleBody.addView(web, new FrameLayout.LayoutParams(-1, -1));
    }

    private void shareArticle() {
        if (articleItem == null) return;
        String text = articleItem.title.isEmpty() ? articleItem.link : articleItem.title + "\n" + articleItem.link;
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), "Share article"));
    }

    private void openInBrowser(String url) {
        if (url == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void closeArticle() {
        articlePage.setVisibility(View.GONE);
        headerView.setVisibility(View.VISIBLE);
        if (tab == TAB_NEWS) newsPage.setVisibility(View.VISIBLE);
        articleItem = null;
        originalMode = false;
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.setVisibility(View.GONE);
        }
    }

    private Drawable newsPlaceholder(String source) {
        int[][] sets = {{0xFF1E3A5F, 0xFF0F1B2D}, {0xFF0B4F3C, 0xFF07261D}, {0xFF6B2A1F, 0xFF2A100C}, {0xFF4A3B12, 0xFF1F1806}, {0xFF3D2A5C, 0xFF180F26}};
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, sets[(source.hashCode() & Integer.MAX_VALUE) % sets.length]);
    }

    private static final class NewsCell {
        ImageView image;
        TextView title;
        TextView meta;
        TextView summary;
    }

    private final class NewsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return newsShown.size();
        }

        @Override
        public Object getItem(int i) {
            return newsShown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int i) {
            return i == 0 ? 0 : 1;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            NewsRepo.Item it = newsShown.get(i);
            NewsCell c;
            if (v == null) {
                c = new NewsCell();
                v = i == 0 ? hero(c) : row(c);
                v.setTag(c);
                Ui.press(v, 0.98f);
            } else {
                c = (NewsCell) v.getTag();
            }
            c.title.setText(it.title);
            String ago = NewsRepo.ago(it.time);
            c.meta.setText(i == 0 ? it.source.toUpperCase(Locale.ROOT) + (ago.length() > 0 ? "  ·  " + ago.toUpperCase(Locale.ROOT) : "")
                    : it.source + (ago.length() > 0 ? "  ·  " + ago : ""));
            if (c.summary != null) {
                c.summary.setText(it.summary);
                c.summary.setVisibility(it.summary.length() > 0 ? View.VISIBLE : View.GONE);
            }
            int eff = DataMode.effective(MainActivity.this);
            int px = i == 0 ? (eff == DataMode.SAVER ? 480 : 900) : (eff == DataMode.SAVER ? 160 : 260);
            ImageLoader.loadArticleImage(c.image, it, newsPlaceholder(it.source), px, DataMode.fetchArticlePreviews(eff));
            v.setContentDescription(it.title + ". " + it.source + ", " + ago);
            return v;
        }

        private View hero(NewsCell c) {
            LinearLayout outer = Ui.column(MainActivity.this);
            outer.setPadding(0, Ui.dp(MainActivity.this, 4), 0, Ui.dp(MainActivity.this, 14));
            FrameLayout card = new FrameLayout(MainActivity.this);
            card.setBackground(Ui.round(Ui.SURFACE2, Ui.dp(MainActivity.this, 26)));
            Ui.roundClip(card, Ui.dp(MainActivity.this, 26));
            c.image = new ImageView(MainActivity.this);
            c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(c.image, new FrameLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 250)));
            View shade = new View(MainActivity.this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0x66000000, 0xF0080A0F}));
            card.addView(shade, new FrameLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 250)));
            LinearLayout txt = Ui.column(MainActivity.this);
            txt.setPadding(Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18));
            TextView badge = Ui.text(MainActivity.this, "TOP STORY", 10.5f, Ui.ON_GOLD, Ui.W_HEAVY);
            badge.setLetterSpacing(0.14f);
            badge.setBackground(Ui.round(Ui.GOLD, Ui.dp(MainActivity.this, 8)));
            badge.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 4));
            txt.addView(badge, Ui.lp(-2, -2));
            c.title = Ui.text(MainActivity.this, "", 21, 0xFFFFFFFF, Ui.W_HEAVY);
            c.title.setMaxLines(3);
            c.title.setEllipsize(TextUtils.TruncateAt.END);
            c.title.setLineSpacing(0, 1.12f);
            c.title.setPadding(0, Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 8));
            txt.addView(c.title);
            c.meta = Ui.text(MainActivity.this, "", 11.5f, 0xFFD6DAE2, Ui.W_BOLD);
            c.meta.setLetterSpacing(0.06f);
            txt.addView(c.meta);
            card.addView(txt, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            outer.addView(card, Ui.lp(-1, -2));
            c.summary = Ui.text(MainActivity.this, "", 14, Ui.TEXT2, Ui.W_REGULAR);
            c.summary.setMaxLines(3);
            c.summary.setEllipsize(TextUtils.TruncateAt.END);
            c.summary.setLineSpacing(0, 1.3f);
            c.summary.setPadding(Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6), 0);
            outer.addView(c.summary);
            return outer;
        }

        private View row(NewsCell c) {
            FrameLayout outer = new FrameLayout(MainActivity.this);
            outer.setPadding(0, Ui.dp(MainActivity.this, 5), 0, Ui.dp(MainActivity.this, 5));
            LinearLayout r = Ui.row(MainActivity.this);
            r.setBackground(Ui.roundStroke(Ui.alpha(Ui.SURFACE, 0.9f), Ui.dp(MainActivity.this, 22), Ui.STROKE, Ui.dp(MainActivity.this, 1)));
            r.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 10));
            c.image = new ImageView(MainActivity.this);
            c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.roundClip(c.image, Ui.dp(MainActivity.this, 16));
            r.addView(c.image, Ui.lp(Ui.dp(MainActivity.this, 92), Ui.dp(MainActivity.this, 92)));
            LinearLayout col = Ui.column(MainActivity.this);
            col.setPadding(Ui.dp(MainActivity.this, 14), 0, 0, 0);
            c.meta = Ui.text(MainActivity.this, "", 12, Ui.GOLD, Ui.W_BOLD);
            col.addView(c.meta);
            c.title = Ui.text(MainActivity.this, "", 15.5f, Ui.TEXT, Ui.W_BOLD);
            c.title.setMaxLines(3);
            c.title.setEllipsize(TextUtils.TruncateAt.END);
            c.title.setLineSpacing(0, 1.15f);
            c.title.setPadding(0, Ui.dp(MainActivity.this, 5), 0, 0);
            col.addView(c.title);
            r.addView(col, Ui.weight(1));
            outer.addView(r);
            return outer;
        }
    }

    // ---- Mini player ------------------------------------------------------------------------

    private View buildMini() {
        mini = Ui.row(this);
        mini.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        mini.setElevation(Ui.dp(this, 10));
        miniArt = new LogoView(this);
        miniArt.setCornerFraction(0.26f);
        mini.addView(miniArt, Ui.lp(Ui.dp(this, 50), Ui.dp(this, 50)));
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        miniName = Ui.text(this, "", 15, Ui.TEXT, Ui.W_BOLD);
        miniName.setSingleLine(true);
        miniName.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(miniName);
        LinearLayout line = Ui.row(this);
        line.setPadding(0, Ui.dp(this, 4), 0, 0);
        miniBars = new BarsView(this, BarsView.MINI, Ui.GOLD);
        line.addView(miniBars, Ui.lp(Ui.dp(this, 14), Ui.dp(this, 12), 0, 0, Ui.dp(this, 7), 0));
        miniSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        miniSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GOLD));
        line.addView(miniSpinner, Ui.lp(Ui.dp(this, 13), Ui.dp(this, 13), 0, 0, Ui.dp(this, 7), 0));
        miniSub = Ui.text(this, "", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        miniSub.setMarqueeRepeatLimit(-1);
        miniSub.setSelected(true);
        line.addView(miniSub);
        col.addView(line);
        mini.addView(col, Ui.weight(1));
        miniPlay = Ui.iconButton(this, R.drawable.ic_play, Ui.ON_GOLD, Ui.GOLD, 48, "Play");
        miniPlay.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            togglePlay();
        });
        Ui.press(miniPlay, 0.88f);
        mini.addView(miniPlay);
        mini.setOnClickListener(v -> openNowPlaying());
        Ui.press(mini, 0.98f);
        mini.setVisibility(View.GONE);
        return mini;
    }

    // ---- Small things -----------------------------------------------------------------------

    void snack(String msg) {
        snack.setText(msg);
        main.removeCallbacks(hideSnack);
        if (snack.getVisibility() != View.VISIBLE) {
            snack.setVisibility(View.VISIBLE);
            snack.setAlpha(0f);
            snack.setTranslationY(Ui.dp(this, 8));
        }
        snack.bringToFront();
        snack.animate().alpha(1f).translationY(0).setDuration(200).start();
        main.postDelayed(hideSnack, 2400);
    }

    void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (im != null && f != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
        if (search != null) search.clearFocus();
    }

    /** Re-binds visible station tiles (e.g. after the data mode changes logo sizes). */
    void refreshLists() {
        adapter.notifyDataSetChanged();
        newsAdapter.notifyDataSetChanged();
        rebuildRecents();
    }

    /** Tiny four-colour stripe from the national flag, next to the greeting. */
    static final class FlagStripe extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int[] c = {Ui.SA_GREEN, Ui.GOLD, Ui.SA_RED, 0xFF2F5BFF};

        FlagStripe(android.content.Context ctx) {
            super(ctx);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float w = getWidth() / 4f, h = getHeight();
            for (int i = 0; i < 4; i++) {
                p.setColor(c[i]);
                cv.drawRoundRect(i * w + 1, 0, (i + 1) * w - 1, h, h / 2, h / 2, p);
            }
        }
    }
}
