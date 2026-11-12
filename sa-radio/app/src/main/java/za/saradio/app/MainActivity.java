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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements RadioService.Listener {
    static final int TAB_RADIO = 0;
    static final int TAB_FAV = 1;
    static final int TAB_NEWS = 2;
    static final int TAB_GAMES = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Sheet> sheets = new ArrayList<>();

    // Shell
    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout bottomArea;
    private View bottomFade;
    private View statusBand;
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
    private TextView sectionTag;
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
    private SportView sportView;
    private LinearLayout newsContent;
    private Segmented newsMode;
    private boolean sportMode;
    /** The World list instead of South African headlines. */
    private boolean worldMode;
    private String worldRegion = "";

    // Mini player
    private LinearLayout mini;
    private LogoView miniArt;
    private TextView miniName;
    private TextView miniSub;
    private TextView miniStatus;
    private BarsView miniBars;
    private ProgressBar miniSpinner;
    private ImageView miniPlay;
    private String miniKey;

    private NavBar nav;
    private GamesPage gamesPage;
    private GameHost gameHost;

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
        Tuner.setStations(all);
        news = NewsRepo.load(this);
        rebuildNewsChips();
        applyFilter();
        applyNewsFilter();
        // The list changes slowly; don't spend data re-downloading it on every launch.
        long age = System.currentTimeMillis() - Prefs.get(this).getLong("stationsAt", 0);
        long maxAge = DataMode.effective(this) == DataMode.SAVER ? 3 * 86400_000L : 12 * 3600_000L;
        if (all.isEmpty() || age > maxAge) refreshStations(false);
        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleWidgetIntent(i);
    }

    /** A tap on a home-screen widget: go to the matching screen. */
    private void handleWidgetIntent(Intent i) {
        if (i == null) return;
        String what = i.getStringExtra(Widgets.EXTRA_OPEN);
        if (what == null) return;
        i.removeExtra(Widgets.EXTRA_OPEN); // once only, not again after a rotation
        while (!sheets.isEmpty()) sheets.get(sheets.size() - 1).dismiss();
        if (!Widgets.OPEN_PLAYER.equals(what) && nowPlaying != null && nowPlaying.isOpen()) nowPlaying.close();
        switch (what) {
            case Widgets.OPEN_PLAYER:
                if (RadioService.current == null) RadioService.current = Prefs.lastStation(this);
                if (RadioService.current != null) main.post(this::openNowPlaying);
                break;
            case Widgets.OPEN_FAVS:
                setTab(TAB_FAV);
                break;
            case Widgets.OPEN_SPORT:
                setTab(TAB_NEWS);
                showSport(true);
                break;
            case Widgets.OPEN_ARTICLE: {
                NewsRepo.Item it = new NewsRepo.Item();
                it.link = nz(i.getStringExtra("link"));
                it.title = nz(i.getStringExtra("title"));
                it.source = nz(i.getStringExtra("source"));
                it.summary = nz(i.getStringExtra("summary"));
                it.image = nz(i.getStringExtra("image"));
                it.time = i.getLongExtra("time", 0);
                setTab(TAB_NEWS);
                showSport(false);
                if (!it.link.isEmpty()) main.post(() -> openArticle(it));
                break;
            }
            default:
                setTab(TAB_NEWS);
                showSport(false);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.setListener(this);
        main.post(ticker);
        if (gameHost != null) gameHost.resume();
        if (web != null) web.onResume();
        rebuildRecents();
        if (tab == TAB_NEWS && sportMode) sportView.shown();
    }

    @Override
    protected void onPause() {
        RadioService.setListener(null);
        main.removeCallbacks(ticker);
        if (gameHost != null) gameHost.pause();
        if (web != null) web.onPause();
        sportView.hidden();
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
        } else if (gameHost != null && gameHost.isOpen()) {
            gameHost.close();
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
            getWindow().setNavigationBarColor(Ui.ROAD);
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
        ViewGroup.LayoutParams sb = statusBand.getLayoutParams();
        sb.height = insetTop;
        statusBand.setLayoutParams(sb);
        nav.setBottomInset(insetNav);
        for (Sheet s : sheets) s.applyInsets(insetNav, insetIme, insetTop);
        if (nowPlaying != null) nowPlaying.applyInsets(insetTop, insetNav);
        if (gameHost != null) gameHost.applyInsets(insetTop, insetNav);
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
                    Tuner.setStations(all);
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
        updatePlate();
        if (sectionTitle == null) return;
        boolean filtered = query.trim().length() > 0 || province.length() > 0;
        if (tab == TAB_FAV) {
            sectionTitle.setText(filtered ? "Matches" : "Favourites");
            sectionTag.setText(filtered ? "from your stations" : "your top picks");
            sectionHint.setText(favs.size() + " saved · hold a station to remove it");
        } else {
            boolean searching = query.trim().length() > 0;
            sectionTitle.setText(searching ? "Results" : "Stations");
            sectionTag.setText(province.length() > 0 ? "in " + province : searching ? "off the dial" : "fresh every day!");
            sectionHint.setText(shown.size() + (shown.size() == 1 ? " station" : " stations") + " · tap to play, hold to save");
        }
        View titleRow = (View) sectionTitle.getParent();
        titleRow.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        sectionHint.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);

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
        Widgets.radioChanged(this);
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
        // Playing from Favourites tunes through favourites; playing from anywhere else tunes through everything.
        if (tab == TAB_FAV) Tuner.setBand(this, Tuner.FAVS);
        else if (Tuner.band(this) == Tuner.FAVS) Tuner.setBand(this, Tuner.allBand(this));
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

    /** Steps to the next ({@code dir} 1) or previous (-1) station on the tuning band. */
    void tune(int dir) {
        if (RadioService.current == null && Tuner.dial(this, Tuner.band(this)).isEmpty() && all.isEmpty()) return;
        RadioService.tune(this, dir);
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
        miniSub.setTextColor(st == RadioService.ERROR ? 0xFFFF9A9A : 0xFFE6E6E6);
        miniStatus.setText(playing ? "NOW PLAYING · LIVE" : st == RadioService.BUFFERING ? "TUNING IN" : "ON THE DIAL");
        miniBars.setVisibility(playing ? View.VISIBLE : View.GONE);
        miniBars.setRunning(playing);
        miniSpinner.setVisibility(st == RadioService.BUFFERING ? View.VISIBLE : View.GONE);
        boolean active = RadioService.isActive();
        miniPlay.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_play);
        miniPlay.setContentDescription(active ? "Stop" : "Play");
        mini.setContentDescription(s.name + ", " + playerLine() + ". Open player");
        if (nowPlaying != null) nowPlaying.refresh();
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
        boolean stations = t == TAB_RADIO || t == TAB_FAV;
        radioPage.setVisibility(stations ? View.VISIBLE : View.GONE);
        newsPage.setVisibility(t == TAB_NEWS && articlePage.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        gamesPage.setVisibility(t == TAB_GAMES ? View.VISIBLE : View.GONE);
        if (t == TAB_GAMES) gamesPage.refresh();
        if (t != TAB_NEWS) articlePage.setVisibility(View.GONE);
        provinceScroll.setVisibility(t == TAB_RADIO ? View.VISIBLE : View.GONE);
        search.setHint(t == TAB_FAV ? "Search your favourites" : "Where to? Station, genre or city");
        title.setText(t == TAB_RADIO ? "SA Radio" : t == TAB_FAV ? "Favourites" : t == TAB_GAMES ? "Games"
                : sportMode ? "Sport" : worldMode ? "World" : "News");
        updatePlate();
        if (t == TAB_FAV) {
            actionBtn.setImageResource(R.drawable.ic_plus);
            actionBtn.setContentDescription("Add a station");
            actionBtn.setTooltipText("Add a station");
            actionBtn.setVisibility(View.VISIBLE);
        } else if (t == TAB_NEWS) {
            actionBtn.setImageResource(R.drawable.ic_refresh);
            actionBtn.setContentDescription(sportMode ? "Refresh scores" : "Refresh headlines");
            actionBtn.setTooltipText(sportMode ? "Refresh scores" : "Refresh headlines");
            actionBtn.setVisibility(View.VISIBLE);
        } else {
            actionBtn.setVisibility(View.GONE);
        }
        nav.select(t, true);
        if (stations) {
            applyFilter();
            grid.setSelection(0);
        }
        if (t == TAB_NEWS && sportMode) sportView.shown();
        else sportView.hidden();
        if (t == TAB_NEWS && !sportMode && (news.isEmpty() || NewsRepo.local(news).size() == news.size()
                || System.currentTimeMillis() - newsLoadedAt > 15 * 60_000L)) {
            refreshNews(false);
        }
        updateHeaderSpinner();
    }

    /** The number plate under the title: what's on this screen, at a glance. */
    private void updatePlate() {
        if (greeting == null) return;
        String s;
        if (tab == TAB_RADIO) s = all.isEmpty() ? "Mzansi on air" : "Mzansi · " + all.size() + " stations";
        else if (tab == TAB_FAV) s = favs.isEmpty() ? "Nothing saved yet" : favs.size() + (favs.size() == 1 ? " station saved" : " stations saved");
        else if (tab == TAB_GAMES) s = Games.ALL.length + " puzzles · new every day";
        else if (sportMode) s = "Scores & fixtures";
        else if (worldMode) {
            Set<String> countries = new LinkedHashSet<>();
            for (NewsRepo.Item it : newsShown) countries.add(it.country);
            s = newsShown.isEmpty() ? "Many countries, many voices"
                    : (worldRegion.isEmpty() ? "World" : NewsRepo.regionName(worldRegion)) + " · " + countries.size() + " countries";
        }
        else s = newsShown.isEmpty() ? "Read while you listen" : newsShown.size() + " stories · ad-free";
        greeting.setText(s.toUpperCase(Locale.ROOT));
    }

    void updateHeaderSpinner() {
        boolean busy = tab == TAB_NEWS ? (sportMode ? sportView.isLoading() : newsLoading) : loading;
        topSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- UI construction --------------------------------------------------------------------

    /** Phones shorter than ~700dp (older 5-inch screens) get a tighter layout. */
    private boolean shortScreen() {
        return getResources().getConfiguration().screenHeightDp < 700;
    }

    private int columnsFor() {
        float wdp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return Math.max(2, (int) (wdp / 180));
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        statusBand = new View(this);
        statusBand.setBackgroundColor(Ui.INK);
        root.addView(statusBand, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));

        content = Ui.column(this);
        content.addView(buildHeader());
        FrameLayout pages = new FrameLayout(this);
        content.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        cols = columnsFor();
        pages.addView(buildRadioPage());
        pages.addView(buildNewsPage());
        pages.addView(buildArticlePage());
        gamesPage = new GamesPage(this);
        gamesPage.setVisibility(View.GONE);
        pages.addView(gamesPage);

        // Bottom: a soft fade, the mini player and the floating nav. Lists scroll underneath.
        bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Ui.alpha(Ui.BG, 0f), Ui.alpha(Ui.BG, 0.92f), Ui.BG}));
        root.addView(bottomFade, new FrameLayout.LayoutParams(-1, Ui.dp(this, 120), Gravity.BOTTOM));
        bottomArea = Ui.column(this);
        bottomArea.addView(buildMini(), Ui.lp(-1, -2, Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 10)));
        nav = new NavBar(this, new int[] {R.drawable.ic_nav_radio, R.drawable.ic_star, R.drawable.ic_nav_news, R.drawable.ic_nav_games},
                new String[] {"Radio", "Favourites", "News", "Games"});
        nav.setListener(this::setTab);
        bottomArea.addView(nav, Ui.lp(-1, -2));
        bottomArea.setClipChildren(false);
        root.addView(bottomArea, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        bottomArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || t != ot) main.post(this::padLists);
        });

        snack = Ui.text(this, "", 14, Ui.PAPER, Ui.W_BOLD);
        snack.setGravity(Gravity.CENTER);
        snack.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        snack.setBackground(Ui.roundStroke(Ui.INK, Ui.dp(this, 10), Ui.YELLOW, Ui.line()));
        snack.setElevation(Ui.dp(this, 6));
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
        int h = bottomArea.getHeight() + Ui.dp(this, 8);
        grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), h);
        newsList.setPadding(newsList.getPaddingLeft(), newsList.getPaddingTop(), newsList.getPaddingRight(), h);
        sportView.setBottomPadding(h);
        gamesPage.setPadding(0, 0, 0, 0);
        ((View) gamesPage.getChildAt(0)).setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), h);
        ViewGroup.LayoutParams fl = bottomFade.getLayoutParams();
        fl.height = h + Ui.dp(this, 24);
        bottomFade.setLayoutParams(fl);
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) snack.getLayoutParams();
        sl.bottomMargin = h + Ui.dp(this, 4);
        snack.setLayoutParams(sl);
        FrameLayout.LayoutParams el = (FrameLayout.LayoutParams) empty.getLayoutParams();
        el.bottomMargin = h;
        empty.setLayoutParams(el);
    }

    private View buildHeader() {
        LinearLayout outer = Ui.column(this);
        headerView = outer;
        boolean tight = shortScreen();
        outer.addView(new Livery.Band(this, Ui.PAPER), Ui.lp(-1, Ui.dp(this, 12)));
        LinearLayout h = Ui.row(this);
        h.setGravity(Gravity.TOP);
        h.setPadding(Ui.dp(this, 20), Ui.dp(this, tight ? 10 : 16), Ui.dp(this, 16), Ui.dp(this, tight ? 10 : 14));
        LinearLayout titles = Ui.column(this);
        title = Ui.display(this, "SA Radio", tight ? 38 : 48, Ui.INK);
        Ui.heading(title);
        titles.addView(title);
        greeting = numberPlate(this, "");
        titles.addView(greeting, Ui.lp(-2, Ui.dp(this, 30), 0, Ui.dp(this, tight ? 6 : 9), 0, 0));
        h.addView(titles, Ui.weight(1));

        topSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        topSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK));
        topSpinner.setContentDescription("Loading");
        h.addView(topSpinner, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20), 0, Ui.dp(this, 14), Ui.dp(this, 10), 0));

        actionBtn = Ui.iconButton(this, R.drawable.ic_refresh, Ui.YELLOW, Ui.INK, 48, "Refresh");
        actionBtn.setOnClickListener(v -> {
            if (tab == TAB_NEWS) {
                v.animate().rotationBy(360).setDuration(500).start();
                if (sportMode) sportView.refresh(true);
                else refreshNews(true);
            } else {
                Panels.addStation(this);
            }
        });
        h.addView(actionBtn, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        ImageView settings = Ui.iconButton(this, R.drawable.ic_tune, Ui.YELLOW, Ui.INK, 48, "Settings: data, sound and stations");
        settings.setOnClickListener(v -> Panels.settings(this));
        h.addView(settings, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48), Ui.dp(this, 8), 0, 0, 0));
        outer.addView(h);
        return outer;
    }

    /** A white number plate with two screws, for short labels. */
    static TextView numberPlate(android.content.Context c, String s) {
        TextView t = Ui.text(c, s, 13, Ui.INK, Ui.W_BOLD);
        t.setLetterSpacing(0.12f);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        t.setBackground(Ui.roundStroke(Ui.PAPER, Ui.dp(c, 6), Ui.INK, Ui.line()));
        GradientDrawable l = Ui.oval(0xFF9A9A9A), r = Ui.oval(0xFF9A9A9A);
        int s5 = Ui.dp(c, 5);
        l.setBounds(0, 0, s5, s5);
        r.setBounds(0, 0, s5, s5);
        t.setCompoundDrawables(l, null, r, null);
        t.setCompoundDrawablePadding(Ui.dp(c, 8));
        return t;
    }

    private View buildRadioPage() {
        radioPage = Ui.column(this);

        LinearLayout box = Ui.row(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 6), 0);
        box.setBackground(Ui.roundStroke(Ui.PAPER, Ui.dp(this, 10), Ui.INK, Ui.line()));
        ImageView si = new ImageView(this);
        si.setImageResource(R.drawable.ic_search);
        si.setColorFilter(Ui.INK);
        box.addView(si, Ui.lp(Ui.dp(this, 20), Ui.dp(this, 20)));
        search = new EditText(this);
        search.setHint("Where to? Station, genre or city");
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
        radioPage.addView(box, Ui.lp(-1, Ui.dp(this, 52), Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 4)));

        provinceScroll = new HorizontalScrollView(this);
        provinceScroll.setHorizontalScrollBarEnabled(false);
        provinceScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        provinceRow = Ui.row(this);
        provinceRow.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 12), 0);
        provinceScroll.addView(provinceRow);
        rebuildProvinceChips();

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
        TextView t = Ui.display(this, "Jump back in", 22, Ui.INK);
        Ui.heading(t);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 10));
        recentBlock.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recentRow = Ui.row(this);
        recentRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
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
            TextView n = Ui.text(this, s.name, 12.5f, on ? Ui.SIGN_RED : Ui.INK, Ui.W_BOLD);
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
        h.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 14));
        LinearLayout tr = Ui.row(this);
        tr.setGravity(Gravity.START | Gravity.TOP); // lines the two scripts up on their baselines
        tr.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        sectionTitle = Ui.sign(this, "Stations", 24, Ui.SIGN_RED);
        Ui.paintedShadow(sectionTitle, Ui.dp(this, 2), Ui.dp(this, 2), Ui.INK);
        sectionTitle.setSingleLine(true);
        Ui.heading(sectionTitle);
        tr.addView(sectionTitle);
        sectionTag = Ui.text(this, "fresh every day!", 21, Ui.COBALT, Ui.W_REGULAR);
        sectionTag.setTypeface(Ui.brush);
        sectionTag.setSingleLine(true);
        sectionTag.setEllipsize(TextUtils.TruncateAt.END);
        sectionTag.setPadding(Ui.dp(this, 10), 0, 0, 0);
        tr.addView(sectionTag, Ui.lp(-2, -2));
        h.addView(tr);
        sectionHint = Ui.text(this, "", 12.5f, Ui.TEXT2, Ui.W_MEDIUM);
        sectionHint.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), 0);
        h.addView(sectionHint);
        h.addView(provinceScroll, Ui.lp(-1, -2, 0, Ui.dp(this, 12), 0, 0));
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(h);
        return wrap;
    }

    private View buildEmpty() {
        empty = Ui.column(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 40), 0, Ui.dp(this, 40), 0);
        emptyIcon = new ImageView(this);
        emptyIcon.setColorFilter(Ui.INK);
        int p = Ui.dp(this, 20);
        emptyIcon.setPadding(p, p, p, p);
        emptyIcon.setBackground(Ui.roundStroke(Ui.PAPER, Ui.dp(this, 12), Ui.INK, Ui.line()));
        empty.addView(emptyIcon, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)));
        emptyTitle = Ui.display(this, "", 26, Ui.INK);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, Ui.lp(-2, -2, 0, Ui.dp(this, 18), 0, 0));
        emptyBody = Ui.text(this, "", 14.5f, Ui.TEXT2, Ui.W_MEDIUM);
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
        TextView badge;
        ImageView star;
        String key;
        Station station;
        int painted;
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
                // Each sign carries a 3dp shadow on its right and bottom, so trim that from the gaps.
                r.setPadding(Ui.dp(MainActivity.this, 20), 0, Ui.dp(MainActivity.this, 17), Ui.dp(MainActivity.this, 9));
                Tile[] tiles = new Tile[cols];
                for (int i = 0; i < cols; i++) {
                    tiles[i] = makeTile();
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 136), 1f);
                    lp.rightMargin = i < cols - 1 ? Ui.dp(MainActivity.this, 9) : 0;
                    r.addView(tiles[i].view, lp);
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
        FrameLayout v = new FrameLayout(this);
        int sh = Ui.dp(this, 3);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 11) + sh, Ui.dp(this, 11) + sh);
        t.art = new LogoView(this);
        col.addView(t.art, Ui.lp(Ui.dp(this, 64), Ui.dp(this, 64)));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));
        t.name = Ui.sign(this, "", 15, Ui.INK);
        t.name.setMaxLines(2);
        t.name.setEllipsize(TextUtils.TruncateAt.END);
        t.name.setPadding(Ui.dp(this, 2), 0, 0, 0);
        col.addView(t.name, Ui.lp(-1, -2));
        t.sub = Ui.text(this, "", 12, Ui.INK, Ui.W_BOLD);
        t.sub.setSingleLine(true);
        t.sub.setEllipsize(TextUtils.TruncateAt.END);
        t.sub.setPadding(Ui.dp(this, 2), 0, 0, 0);
        col.addView(t.sub, Ui.lp(-1, -2, 0, Ui.dp(this, 3), 0, 0));
        v.addView(col, new FrameLayout.LayoutParams(-1, -1));

        t.badge = Ui.text(this, "ON AIR", 10.5f, Ui.INK, Ui.W_BOLD);
        t.badge.setLetterSpacing(0.1f);
        t.badge.setPadding(Ui.dp(this, 7), Ui.dp(this, 4), Ui.dp(this, 7), Ui.dp(this, 4));
        t.badge.setBackground(Ui.roundStroke(Ui.YELLOW, Ui.dp(this, 4), Ui.INK, Ui.line()));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bl.setMargins(0, Ui.dp(this, 10), Ui.dp(this, 10) + sh, 0);
        v.addView(t.badge, bl);
        t.badge.setVisibility(View.GONE);

        t.star = new ImageView(this);
        t.star.setImageResource(R.drawable.ic_star);
        t.star.setColorFilter(Ui.INK);
        int sp = Ui.dp(this, 5);
        t.star.setPadding(sp, sp, sp, sp);
        GradientDrawable sb = Ui.oval(Ui.YELLOW);
        sb.setStroke(Ui.line(), Ui.INK);
        t.star.setBackground(sb);
        t.star.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28), Gravity.TOP | Gravity.END);
        stl.setMargins(0, Ui.dp(this, 9), Ui.dp(this, 9) + sh, 0);
        v.addView(t.star, stl);
        t.star.setVisibility(View.GONE);

        t.art.setArtListener(x -> paintTile(t));
        v.setOnClickListener(x -> {
            if (t.station != null) playStation(t.station);
        });
        v.setOnLongClickListener(x -> {
            if (t.station == null) return false;
            x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toggleFav(t.station);
            return true;
        });
        Ui.press(v, 0.95f);
        t.view = v;
        return t;
    }

    /** Paints the sign in the station's colour once its logo (or monogram) is known. */
    private void paintTile(Tile t) {
        int brand = t.art.getBrandColor();
        if (brand == t.painted) return;
        t.painted = brand;
        // An ink sign gets a red shadow so it still stands off the page.
        int shadow = Ui.luminance(brand) < 0.03 ? Ui.SIGN_RED : Ui.INK;
        t.view.setBackground(Ui.plate(brand, Ui.dp(this, 8), Ui.dp(this, 3), shadow));
        int on = Ui.onColor(brand);
        t.name.setTextColor(on == Ui.PAPER && Ui.luminance(brand) < 0.03 ? Ui.YELLOW : on);
        t.sub.setTextColor(on);
    }

    private void bindTile(Tile t, Station s) {
        t.station = s;
        if (!s.key().equals(t.key)) {
            t.key = s.key();
            bindLogo(t.art, s, DataMode.logoPx(DataMode.effective(this)));
        }
        paintTile(t);
        Station cur = RadioService.current;
        boolean isCur = cur != null && cur.key().equals(s.key());
        boolean on = isCur && RadioService.isActive();
        boolean fav = isFav(s);
        t.art.setPlaying(on);
        t.name.setText(s.name);
        t.sub.setText(shortSub(s));
        if (on) {
            t.badge.setText(RadioService.state == RadioService.PLAYING ? "ON AIR" : "TUNING…");
            t.badge.setVisibility(View.VISIBLE);
        } else {
            t.badge.setVisibility(View.GONE);
        }
        t.star.setVisibility(tab == TAB_RADIO && fav && !on ? View.VISIBLE : View.GONE);
        t.view.setContentDescription(s.name + ", " + shortSub(s) + (on ? ", playing" : "") + (fav ? ", favourite" : ""));
    }

    static String shortSub(Station s) {
        if (s.custom) return "Your stream";
        String f = Freq.label(s);
        String rest = shortSubNoFreq(s);
        return f.isEmpty() ? rest : f + " · " + rest;
    }

    private static String shortSubNoFreq(Station s) {
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
        for (NewsRepo.Item it : news) {
            boolean world = !it.region.isEmpty();
            if (world != worldMode) continue;
            if (world ? (worldRegion.length() > 0 && !worldRegion.equals(it.region)) : (newsSource.length() > 0 && !newsSource.equals(it.source))) continue;
            newsShown.add(it);
        }
        newsAdapter.notifyDataSetChanged();
        updateNewsStatus();
    }

    private void updateNewsStatus() {
        updatePlate();
        if (newsStatus == null) return;
        if (!news.isEmpty()) {
            Set<String> pubs = new LinkedHashSet<>();
            for (NewsRepo.Item it : worldMode ? newsShown : news) if (!worldMode || !it.region.isEmpty()) pubs.add(it.source);
            String from;
            if (worldMode) {
                Set<String> countries = new LinkedHashSet<>();
                for (NewsRepo.Item it : newsShown) countries.add(it.country);
                from = "from " + pubs.size() + " outlets in " + countries.size() + " countries";
            } else {
                from = newsSource.length() > 0 ? "from " + newsSource : "from " + pubs.size() + " publishers";
            }
            newsStatus.setText(newsShown.size() + " stories " + from + (newsLoading ? " · updating…" : ""));
        } else {
            newsStatus.setText(newsLoading ? "Fetching the latest headlines…" : "No headlines yet. Tap refresh to try again.");
        }
    }

    private void rebuildNewsChips() {
        newsChipRow.removeAllViews();
        if (worldMode) {
            addChip(newsChipRow, "Everywhere", worldRegion.length() == 0, v -> pickRegion(""));
            for (int i = 0; i < NewsRepo.REGIONS.length; i++) {
                final String r = NewsRepo.REGIONS[i];
                addChip(newsChipRow, NewsRepo.REGION_NAMES[i], r.equals(worldRegion), v -> pickRegion(r));
            }
            return;
        }
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

    private void pickRegion(String r) {
        worldRegion = r;
        Prefs.get(this).edit().putString("worldRegion", r).apply();
        rebuildNewsChips();
        applyNewsFilter();
        newsList.setSelection(0);
    }

    private View buildNewsPage() {
        newsPage = Ui.column(this);
        // South African headlines, the World list, or Sport. None of these touch the radio.
        sportMode = Prefs.get(this).getBoolean("newsSport", false);
        worldMode = !sportMode && Prefs.get(this).getBoolean("newsWorld", false);
        worldRegion = Prefs.get(this).getString("worldRegion", "");
        newsMode = new Segmented(this, new String[] {"Headlines", "World", "Sport"});
        newsMode.select(sportMode ? 2 : worldMode ? 1 : 0, false);
        newsMode.setListener(i -> applyNewsMode(i == 2, i == 1));
        newsPage.addView(newsMode, Ui.lp(-1, -2, Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 12)));
        newsContent = Ui.column(this);
        newsPage.addView(newsContent, new LinearLayout.LayoutParams(-1, 0, 1f));
        sportView = new SportView(this);
        newsPage.addView(sportView, new LinearLayout.LayoutParams(-1, 0, 1f));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        newsChipRow = Ui.row(this);
        newsChipRow.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 12), 0);
        hs.addView(newsChipRow);
        newsContent.addView(hs);
        newsStatus = Ui.text(this, "", 12.5f, Ui.TEXT2, Ui.W_MEDIUM);
        newsStatus.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 6));
        newsContent.addView(newsStatus);
        newsList = new ListView(this);
        newsList.setDivider(null);
        newsList.setDividerHeight(0);
        newsList.setSelector(android.R.color.transparent);
        newsList.setClipToPadding(false);
        newsList.setVerticalScrollBarEnabled(false);
        newsList.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 17), Ui.dp(this, 160));
        newsAdapter = new NewsAdapter();
        newsList.setAdapter(newsAdapter);
        newsList.setOnItemClickListener((p, v, i, id) -> {
            if (i < newsShown.size()) openArticle(newsShown.get(i));
        });
        newsContent.addView(newsList, new LinearLayout.LayoutParams(-1, 0, 1f));
        newsContent.setVisibility(sportMode ? View.GONE : View.VISIBLE);
        sportView.setVisibility(sportMode ? View.VISIBLE : View.GONE);
        newsPage.setVisibility(View.GONE);
        return newsPage;
    }

    private void applyNewsMode(boolean sport, boolean world) {
        sportMode = sport;
        worldMode = world;
        Prefs.get(this).edit().putBoolean("newsSport", sport).putBoolean("newsWorld", world).apply();
        rebuildNewsChips();
        applyNewsFilter();
        newsList.setSelection(0);
        newsContent.setVisibility(sport ? View.GONE : View.VISIBLE);
        sportView.setVisibility(sport ? View.VISIBLE : View.GONE);
        if (tab == TAB_NEWS) setTab(TAB_NEWS);
    }

    private View buildArticlePage() {
        articlePage = Ui.column(this);
        articlePage.setBackgroundColor(Ui.PAPER);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 8));
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
        webProgress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        webProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE3));
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

    SportView sportView() {
        return sportView;
    }

    void showSport(boolean sport) {
        newsMode.select(sport ? 2 : worldMode ? 1 : 0, false);
        applyNewsMode(sport, !sport && worldMode);
    }

    /** Tests and widgets: the World list, optionally filtered to one region. */
    void showWorld(String region) {
        worldRegion = region == null ? "" : region;
        Prefs.get(this).edit().putString("worldRegion", worldRegion).apply();
        newsMode.select(1, false);
        applyNewsMode(false, true);
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
        articleView.setBottomPadding(bottomArea.getHeight() + Ui.dp(this, 8));
        bottomFade.setVisibility(View.GONE);
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
        lp.bottomMargin = bottomArea.getHeight();
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
        bottomFade.setVisibility(View.VISIBLE);
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
        int[][] sets = {{Ui.COBALT, 0xFF14228F}, {Ui.LIVERY_GREEN, 0xFF006B44}, {Ui.PINK, 0xFFC2275F}, {Ui.SIGN_RED, 0xFF8C1C2B}, {0xFF61107D, 0xFF3A0A4B}};
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
            c.meta.setText(i == 0 ? it.byline().toUpperCase(Locale.ROOT) + (ago.length() > 0 ? "  ·  " + ago.toUpperCase(Locale.ROOT) : "")
                    : it.byline() + (ago.length() > 0 ? "  ·  " + ago : ""));
            if (c.summary != null) {
                c.summary.setText(it.summary);
                c.summary.setVisibility(it.summary.length() > 0 ? View.VISIBLE : View.GONE);
            }
            int eff = DataMode.effective(MainActivity.this);
            int px = i == 0 ? (eff == DataMode.SAVER ? 480 : 900) : (eff == DataMode.SAVER ? 160 : 260);
            ImageLoader.loadArticleImage(c.image, it, newsPlaceholder(it.source), px, DataMode.fetchArticlePreviews(eff));
            v.setContentDescription(it.title + ". " + it.byline() + ", " + ago);
            return v;
        }

        private View hero(NewsCell c) {
            LinearLayout outer = Ui.column(MainActivity.this);
            outer.setPadding(0, Ui.dp(MainActivity.this, 4), 0, Ui.dp(MainActivity.this, 14));
            int sh = Ui.dp(MainActivity.this, 4);
            FrameLayout frame = new FrameLayout(MainActivity.this);
            frame.setPadding(0, 0, sh, sh);
            frame.setBackground(Ui.plate(Ui.INK, Ui.dp(MainActivity.this, 12), sh, Ui.INK));
            FrameLayout card = new FrameLayout(MainActivity.this);
            card.setBackground(Ui.round(Ui.INK, Ui.dp(MainActivity.this, 12)));
            card.setForeground(Ui.roundStroke(0, Ui.dp(MainActivity.this, 12), Ui.INK, Ui.line()));
            Ui.roundClip(card, Ui.dp(MainActivity.this, 12));
            frame.addView(card);
            c.image = new ImageView(MainActivity.this);
            c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(c.image, new FrameLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 250)));
            View shade = new View(MainActivity.this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00000000, 0x66000000, 0xF0080A0F}));
            card.addView(shade, new FrameLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 250)));
            LinearLayout txt = Ui.column(MainActivity.this);
            txt.setPadding(Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 18));
            TextView badge = Ui.text(MainActivity.this, "TOP STORY", 10.5f, Ui.INK, Ui.W_HEAVY);
            badge.setLetterSpacing(0.14f);
            badge.setBackground(Ui.roundStroke(Ui.YELLOW, Ui.dp(MainActivity.this, 4), Ui.INK, Ui.line()));
            badge.setPadding(Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 9), Ui.dp(MainActivity.this, 4));
            txt.addView(badge, Ui.lp(-2, -2));
            c.title = Ui.text(MainActivity.this, "", 25, 0xFFFFFFFF, Ui.W_HEAVY);
            c.title.setTypeface(Ui.display);
            c.title.setMaxLines(3);
            c.title.setEllipsize(TextUtils.TruncateAt.END);
            c.title.setLineSpacing(0, 1.05f);
            c.title.setPadding(0, Ui.dp(MainActivity.this, 10), 0, Ui.dp(MainActivity.this, 8));
            txt.addView(c.title);
            c.meta = Ui.text(MainActivity.this, "", 11.5f, 0xFFD6DAE2, Ui.W_BOLD);
            c.meta.setLetterSpacing(0.06f);
            txt.addView(c.meta);
            card.addView(txt, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            outer.addView(frame, Ui.lp(-1, -2));
            c.summary = Ui.text(MainActivity.this, "", 14.5f, Ui.INK, Ui.W_MEDIUM);
            c.summary.setMaxLines(3);
            c.summary.setEllipsize(TextUtils.TruncateAt.END);
            c.summary.setLineSpacing(0, 1.3f);
            c.summary.setPadding(Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 6), 0);
            outer.addView(c.summary);
            return outer;
        }

        private View row(NewsCell c) {
            FrameLayout outer = new FrameLayout(MainActivity.this);
            outer.setPadding(0, Ui.dp(MainActivity.this, 5), 0, Ui.dp(MainActivity.this, 6));
            LinearLayout r = Ui.row(MainActivity.this);
            int sh = Ui.dp(MainActivity.this, 3);
            r.setBackground(Ui.plate(Ui.PAPER, Ui.dp(MainActivity.this, 10), sh, Ui.INK));
            r.setPadding(Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 14) + sh, Ui.dp(MainActivity.this, 10) + sh);
            c.image = new ImageView(MainActivity.this);
            c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            c.image.setForeground(Ui.roundStroke(0, Ui.dp(MainActivity.this, 6), Ui.INK, Ui.line()));
            Ui.roundClip(c.image, Ui.dp(MainActivity.this, 6));
            r.addView(c.image, Ui.lp(Ui.dp(MainActivity.this, 92), Ui.dp(MainActivity.this, 92)));
            LinearLayout col = Ui.column(MainActivity.this);
            col.setPadding(Ui.dp(MainActivity.this, 14), 0, 0, 0);
            c.meta = Ui.text(MainActivity.this, "", 12, Ui.ACCENT, Ui.W_BOLD);
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
        // The "now playing" card: an ink destination board with livery stripes up one end.
        mini.setBackground(new Livery.Board(this, Ui.INK, new int[] {Ui.YELLOW, Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN}, 92));
        mini.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        miniArt = new LogoView(this);
        mini.addView(miniArt, Ui.lp(Ui.dp(this, 54), Ui.dp(this, 54)));
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        LinearLayout line = Ui.row(this);
        miniBars = new BarsView(this, BarsView.MINI, Ui.YELLOW);
        line.addView(miniBars, Ui.lp(Ui.dp(this, 12), Ui.dp(this, 10), 0, 0, Ui.dp(this, 6), 0));
        miniSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        miniSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.YELLOW));
        line.addView(miniSpinner, Ui.lp(Ui.dp(this, 11), Ui.dp(this, 11), 0, 0, Ui.dp(this, 6), 0));
        miniStatus = Ui.text(this, "NOW PLAYING", 10.5f, Ui.YELLOW, Ui.W_BOLD);
        miniStatus.setLetterSpacing(0.14f);
        miniStatus.setSingleLine(true);
        line.addView(miniStatus);
        col.addView(line);
        miniName = Ui.display(this, "", 22, Ui.PAPER);
        miniName.setSingleLine(true);
        miniName.setEllipsize(TextUtils.TruncateAt.END);
        miniName.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 2));
        col.addView(miniName);
        miniSub = Ui.text(this, "", 12.5f, 0xFFE6E6E6, Ui.W_MEDIUM);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        miniSub.setMarqueeRepeatLimit(-1);
        miniSub.setSelected(true);
        col.addView(miniSub);
        mini.addView(col, Ui.weight(1));
        ImageView miniNext = Ui.iconButton(this, R.drawable.ic_tune_next, Ui.INK, Ui.YELLOW, 40, "Next station");
        GradientDrawable nb = Ui.oval(Ui.YELLOW);
        nb.setStroke(Ui.line(), Ui.INK);
        miniNext.setBackground(nb);
        miniNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            tune(1);
        });
        Ui.press(miniNext, 0.88f);
        mini.addView(miniNext, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40), 0, 0, Ui.dp(this, 8), 0));
        miniPlay = Ui.iconButton(this, R.drawable.ic_play, Ui.INK, Ui.PAPER, 54, "Play");
        GradientDrawable pb = Ui.oval(Ui.PAPER);
        pb.setStroke(Ui.line(), Ui.INK);
        miniPlay.setBackground(pb);
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

    Handler main() {
        return main;
    }

    void openGame(String id) {
        if (gameHost == null) gameHost = new GameHost(this);
        hideKeyboard();
        gameHost.open(id);
    }

    /** A puzzle was finished or closed: update the Games tab's labels. */
    void gamesChanged() {
        if (gamesPage != null) gamesPage.refresh();
    }

    GameHost gameHost() {
        return gameHost;
    }

    /** Re-binds visible station tiles (e.g. after the data mode changes logo sizes). */
    void refreshLists() {
        adapter.notifyDataSetChanged();
        newsAdapter.notifyDataSetChanged();
        rebuildRecents();
    }
}
