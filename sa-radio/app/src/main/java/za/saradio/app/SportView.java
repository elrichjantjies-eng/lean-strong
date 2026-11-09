package za.saradio.app;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fixtures and results, inside the News tab. Nothing here touches the radio. */
final class SportView extends LinearLayout {

    private static final int T_DAY = 0;
    private static final int T_COMP = 1;
    private static final int T_MATCH = 2;
    private static final int T_EVENT = 3;

    private static final class Row {
        final int type;
        final String text;
        final Sports.Match m;
        final boolean live;

        Row(int type, String text, Sports.Match m, boolean live) {
            this.type = type;
            this.text = text;
            this.m = m;
            this.live = live;
        }
    }

    private final MainActivity a;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Segmented mode;
    private final LinearLayout chips;
    private final TextView status;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final LinearLayout empty;
    private final TextView emptyTitle;
    private final TextView emptyBody;
    private final TextView emptyButton;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Sports.Match> all = new HashMap<>();
    private String filter;
    private boolean showResults;
    private boolean loading;
    private boolean onDemandLoaded;
    private boolean failedLast;
    private long loadedAt;
    private boolean visible;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            refreshLive();
            main.postDelayed(this, 60_000);
        }
    };

    SportView(MainActivity act) {
        super(act);
        a = act;
        setOrientation(VERTICAL);
        filter = Prefs.get(act).getString("sportFilter", "sa");
        showResults = Prefs.get(act).getBoolean("sportResults", true);

        mode = new Segmented(act, new String[] {"Results", "Fixtures"});
        mode.select(showResults ? 0 : 1, false);
        mode.setListener(i -> {
            showResults = i == 0;
            Prefs.get(a).edit().putBoolean("sportResults", showResults).apply();
            rebuild();
            toTop();
        });
        addView(mode, Ui.lp(-1, -2, Ui.dp(act, 16), Ui.dp(act, 2), Ui.dp(act, 16), Ui.dp(act, 10)));

        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        chips = Ui.row(act);
        chips.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 8), 0);
        hs.addView(chips);
        addView(hs);
        rebuildChips();

        status = Ui.text(act, "", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        status.setPadding(Ui.dp(act, 22), Ui.dp(act, 10), Ui.dp(act, 22), Ui.dp(act, 2));
        addView(status);

        FrameLayout holder = new FrameLayout(act);
        list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(adapter);
        holder.addView(list, new FrameLayout.LayoutParams(-1, -1));

        empty = Ui.column(act);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(act, 40), 0, Ui.dp(act, 40), Ui.dp(act, 120));
        emptyTitle = Ui.text(act, "", 18, Ui.TEXT, Ui.W_HEAVY);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        emptyBody = Ui.text(act, "", 14, Ui.MUTED, Ui.W_MEDIUM);
        emptyBody.setGravity(Gravity.CENTER);
        emptyBody.setLineSpacing(0, 1.3f);
        empty.addView(emptyBody, Ui.lp(-2, -2, 0, Ui.dp(act, 8), 0, 0));
        emptyButton = Ui.primaryButton(act, "Try again");
        emptyButton.setOnClickListener(v -> refresh(true));
        empty.addView(emptyButton, Ui.lp(-2, -2, 0, Ui.dp(act, 18), 0, 0));
        empty.setVisibility(GONE);
        holder.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    // ---- Tests ----

    List<Sports.Match> matches() {
        return new ArrayList<>(all.values());
    }

    void showFor(String f, boolean results) {
        filter = f;
        showResults = results;
        mode.select(results ? 0 : 1, false);
        rebuildChips();
        rebuild();
        if (Sports.SOCCER.equals(f)) loadOnDemand();
    }

    private void toTop() {
        list.setSelection(0);
    }

    void setBottomPadding(int px) {
        list.setPadding(0, Ui.dp(a, 4), 0, px);
    }

    boolean isLoading() {
        return loading;
    }

    /** The user switched to Sport. */
    void shown() {
        visible = true;
        if (all.isEmpty() || System.currentTimeMillis() - loadedAt > 15 * 60_000L) refresh(false);
        else rebuild();
        main.removeCallbacks(tick);
        main.postDelayed(tick, 60_000);
    }

    void hidden() {
        visible = false;
        main.removeCallbacks(tick);
    }

    void refresh(final boolean announce) {
        if (loading) return;
        loading = true;
        updateStatus();
        a.updateHeaderSpinner();
        if (all.isEmpty()) showEmpty("Fetching scores…", "Rugby, soccer, cricket, F1, golf and UFC.", false);
        final boolean withOnDemand = onDemandLoaded || Sports.SOCCER.equals(filter);
        new Thread(() -> {
            long now = System.currentTimeMillis();
            Sports.Result r = Sports.load(now);
            Sports.Result extra = withOnDemand ? Sports.loadOnDemand(now) : null;
            main.post(() -> {
                loading = false;
                failedLast = r.matches.isEmpty() && r.failed > 0;
                if (!r.matches.isEmpty()) {
                    all.clear();
                    for (Sports.Match m : r.matches) all.put(m.key(), m);
                    if (extra != null) {
                        for (Sports.Match m : extra.matches) all.put(m.key(), m);
                        onDemandLoaded = true;
                    }
                    loadedAt = r.at;
                    if (announce) a.snack("Scores up to date");
                } else if (announce || all.isEmpty()) {
                    a.snack("Couldn't load scores. Check your connection.");
                }
                rebuild();
                a.updateHeaderSpinner();
            });
        }, "sports").start();
    }

    /** Soccer's big European leagues are fetched the first time Soccer is opened. */
    private void loadOnDemand() {
        if (onDemandLoaded || loading) return;
        loading = true;
        updateStatus();
        a.updateHeaderSpinner();
        new Thread(() -> {
            Sports.Result r = Sports.loadOnDemand(System.currentTimeMillis());
            main.post(() -> {
                loading = false;
                if (!r.matches.isEmpty()) {
                    onDemandLoaded = true;
                    for (Sports.Match m : r.matches) all.put(m.key(), m);
                }
                rebuild();
                a.updateHeaderSpinner();
            });
        }, "sports-more").start();
    }

    private void refreshLive() {
        if (loading || all.isEmpty()) return;
        final List<Sports.Match> current = new ArrayList<>(all.values());
        new Thread(() -> {
            List<Sports.Match> fresh = Sports.refreshLive(current, System.currentTimeMillis());
            if (fresh.isEmpty()) return;
            main.post(() -> {
                for (Sports.Match m : fresh) if (all.containsKey(m.key())) all.put(m.key(), m);
                rebuild();
            });
        }, "sports-live").start();
    }

    private void rebuildChips() {
        chips.removeAllViews();
        for (int i = 0; i < Sports.FILTERS.length; i++) {
            final String f = Sports.FILTERS[i];
            TextView c = Ui.chip(a, Sports.FILTER_NAMES[i], f.equals(filter));
            c.setOnClickListener(v -> {
                filter = f;
                Prefs.get(a).edit().putString("sportFilter", f).apply();
                rebuildChips();
                rebuild();
                toTop();
                if (Sports.SOCCER.equals(f)) loadOnDemand();
            });
            chips.addView(c, Ui.lp(-2, -2, 0, 0, Ui.dp(a, 8), 0));
        }
    }

    private void updateStatus() {
        int live = 0;
        for (Sports.Match m : all.values()) if (m.state == Sports.LIVE && Sports.matchesFilter(m, filter)) live++;
        StringBuilder sb = new StringBuilder();
        if (loading) sb.append("Updating…");
        else if (loadedAt > 0) sb.append("Updated ").append(Sports.clock(loadedAt));
        if (live > 0) sb.append(sb.length() > 0 ? "  ·  " : "").append(live).append(" live, refreshing every minute");
        else if (!loading && loadedAt > 0) sb.append("  ·  times in your time zone");
        status.setText(sb);
    }

    private void showEmpty(String title, String body, boolean button) {
        emptyTitle.setText(title);
        emptyBody.setText(body);
        emptyButton.setVisibility(button ? VISIBLE : GONE);
        empty.setVisibility(VISIBLE);
    }

    /** Builds the list: live matches first, then days (newest first for results), competitions within a day. */
    private void rebuild() {
        updateStatus();
        rows.clear();
        long now = System.currentTimeMillis();
        List<Sports.Match> live = new ArrayList<>();
        List<Sports.Match> rest = new ArrayList<>();
        for (Sports.Match m : all.values()) {
            if (!Sports.matchesFilter(m, filter)) continue;
            if (m.state == Sports.LIVE) live.add(m);
            else if (showResults && (m.state == Sports.POST || m.state == Sports.OFF) && m.time <= now) rest.add(m);
            else if (!showResults && m.state == Sports.PRE && m.time > now - 3 * 3600_000L) rest.add(m);
        }
        if (!live.isEmpty()) {
            sort(live, true);
            rows.add(new Row(T_DAY, "Live now", null, true));
            addGrouped(live);
        }
        // Results file under the day they finished (golf runs Thursday to Sunday).
        sort(rest, !showResults);
        long day = -1;
        List<Sports.Match> bucket = new ArrayList<>();
        for (Sports.Match m : rest) {
            long d = Sports.dayStart(showResults ? Math.min(Math.max(m.endTime, m.time), now) : m.time);
            if (d != day) {
                addGrouped(bucket);
                bucket.clear();
                day = d;
                rows.add(new Row(T_DAY, Sports.dayLabel(d, now), null, false));
            }
            bucket.add(m);
        }
        addGrouped(bucket);
        adapter.notifyDataSetChanged();

        if (!rows.isEmpty()) {
            empty.setVisibility(GONE);
        } else if (loading) {
            showEmpty("Fetching scores…", "Rugby, soccer, cricket, F1, golf and UFC.", false);
        } else if (failedLast && all.isEmpty()) {
            showEmpty("Couldn't load scores", "Check your connection and try again.", true);
        } else {
            String what = "sa".equals(filter) ? "South African sport" : Sports.FILTER_NAMES[indexOf(filter)];
            showEmpty(showResults ? "No results this week" : "Nothing scheduled",
                    (showResults ? "No " + what + " results in the past week." : "No " + what + " fixtures in the next two weeks.")
                            + ("sa".equals(filter) ? "" : " Try SA focus or another sport."), false);
        }
    }

    private static int indexOf(String f) {
        for (int i = 0; i < Sports.FILTERS.length; i++) if (Sports.FILTERS[i].equals(f)) return i;
        return 0;
    }

    private void sort(List<Sports.Match> l, final boolean ascending) {
        final long now = System.currentTimeMillis();
        Collections.sort(l, (x, y) -> {
            long dx = Sports.dayStart(ascending ? x.time : Math.min(Math.max(x.endTime, x.time), now));
            long dy = Sports.dayStart(ascending ? y.time : Math.min(Math.max(y.endTime, y.time), now));
            if (dx != dy) return ascending ? Long.compare(dx, dy) : Long.compare(dy, dx);
            if (x.priority != y.priority) return Integer.compare(x.priority, y.priority);
            int c = x.compName.compareTo(y.compName);
            if (c != 0) return c;
            if (x.sa != y.sa) return x.sa ? -1 : 1;
            return Long.compare(x.time, y.time);
        });
    }

    private void addGrouped(List<Sports.Match> ms) {
        String comp = null;
        for (Sports.Match m : ms) {
            if (!m.compName.equals(comp)) {
                comp = m.compName;
                rows.add(new Row(T_COMP, emoji(m.sport) + "  " + comp, null, false));
            }
            rows.add(new Row(m.isEvent() ? T_EVENT : T_MATCH, null, m, m.state == Sports.LIVE));
        }
    }

    static String emoji(String sport) {
        switch (sport) {
            case Sports.RUGBY: return "🏉";
            case Sports.SOCCER: return "⚽";
            case Sports.CRICKET: return "🏏";
            case Sports.F1: return "🏎";
            case Sports.GOLF: return "⛳";
            default: return "🥊";
        }
    }

    // ---- Rows -------------------------------------------------------------------------------

    private static final class MatchHolder {
        TextView state;
        LogoView logo1;
        LogoView logo2;
        TextView name1;
        TextView name2;
        TextView score1;
        TextView score2;
        TextView summary;
        View accent;
        View card;
    }

    private static final class EventHolder {
        TextView title;
        TextView pill;
        TextView sub;
        LinearLayout lines;
        View accent;
        View card;
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int i) {
            return rows.get(i).type;
        }

        @Override
        public boolean isEnabled(int i) {
            return false;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            Row r = rows.get(i);
            switch (r.type) {
                case T_DAY: return dayRow((TextView) v, r);
                case T_COMP: return compRow((TextView) v, r);
                case T_EVENT: return eventRow(v, r.m);
                default: return matchRow(v, r.m);
            }
        }
    }

    private View dayRow(TextView t, Row r) {
        if (t == null) {
            t = Ui.text(a, "", 18, Ui.TEXT, Ui.W_HEAVY);
            t.setPadding(Ui.dp(a, 22), Ui.dp(a, 18), Ui.dp(a, 22), Ui.dp(a, 4));
            t.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            Ui.heading(t);
        }
        t.setText(r.live ? "●  " + r.text : r.text);
        t.setTextColor(r.live ? Ui.LIVE : Ui.TEXT);
        return t;
    }

    private View compRow(TextView t, Row r) {
        if (t == null) {
            t = Ui.text(a, "", 12, Ui.MUTED, Ui.W_HEAVY);
            t.setLetterSpacing(0.06f);
            t.setPadding(Ui.dp(a, 22), Ui.dp(a, 12), Ui.dp(a, 22), Ui.dp(a, 8));
            t.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        }
        t.setText(r.text.toUpperCase(Locale.ROOT));
        return t;
    }

    private FrameLayout cardShell(LinearLayout content, View[] accentOut) {
        FrameLayout outer = new FrameLayout(a);
        outer.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 8));
        FrameLayout card = new FrameLayout(a);
        float r = Ui.dp(a, 18);
        card.setBackground(Ui.ripple(Ui.roundStroke(Ui.alpha(Ui.SURFACE, 0.94f), r, Ui.STROKE, Ui.dp(a, 1)), r));
        Ui.roundClip(card, r);
        card.addView(content, new FrameLayout.LayoutParams(-1, -2));
        View accent = new View(a);
        accent.setBackgroundColor(Ui.GOLD);
        card.addView(accent, new FrameLayout.LayoutParams(Ui.dp(a, 3), -1, Gravity.START));
        accentOut[0] = accent;
        outer.addView(card, new FrameLayout.LayoutParams(-1, -2));
        Ui.press(card, 0.98f);
        outer.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return outer;
    }

    private View matchRow(View v, final Sports.Match m) {
        MatchHolder h;
        if (v == null) {
            h = new MatchHolder();
            LinearLayout row = Ui.row(a);
            row.setPadding(Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            h.state = Ui.text(a, "", 12.5f, Ui.MUTED, Ui.W_HEAVY);
            h.state.setGravity(Gravity.CENTER);
            h.state.setMaxLines(2);
            row.addView(h.state, Ui.lp(Ui.dp(a, 54), -2));
            View div = new View(a);
            div.setBackgroundColor(Ui.STROKE);
            row.addView(div, Ui.lp(Ui.dp(a, 1), Ui.dp(a, 40), 0, 0, Ui.dp(a, 12), 0));
            LinearLayout col = Ui.column(a);
            LinearLayout t1 = Ui.row(a), t2 = Ui.row(a);
            h.logo1 = logo();
            h.logo2 = logo();
            h.name1 = name();
            h.name2 = name();
            h.score1 = score();
            h.score2 = score();
            t1.addView(h.logo1, Ui.lp(Ui.dp(a, 26), Ui.dp(a, 26)));
            t1.addView(h.name1, Ui.weight(1));
            t1.addView(h.score1);
            t2.addView(h.logo2, Ui.lp(Ui.dp(a, 26), Ui.dp(a, 26)));
            t2.addView(h.name2, Ui.weight(1));
            t2.addView(h.score2);
            col.addView(t1);
            col.addView(t2, Ui.lp(-1, -2, 0, Ui.dp(a, 6), 0, 0));
            h.summary = Ui.text(a, "", 12, Ui.MUTED, Ui.W_MEDIUM);
            h.summary.setMaxLines(2);
            h.summary.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(h.summary, Ui.lp(-1, -2, 0, Ui.dp(a, 7), 0, 0));
            row.addView(col, Ui.weight(1));
            View[] acc = new View[1];
            FrameLayout outer = cardShell(row, acc);
            h.accent = acc[0];
            h.card = outer.getChildAt(0);
            outer.setTag(h);
            v = outer;
        } else {
            h = (MatchHolder) v.getTag();
        }
        boolean post = m.state == Sports.POST;
        bindSide(h.logo1, h.name1, h.score1, m.home, post && m.away.winner && !m.home.winner);
        bindSide(h.logo2, h.name2, h.score2, m.away, post && m.home.winner && !m.away.winner);
        switch (m.state) {
            case Sports.LIVE:
                h.state.setText(m.status.isEmpty() ? "LIVE" : m.status);
                h.state.setTextColor(Ui.LIVE);
                break;
            case Sports.PRE:
                h.state.setText(Sports.clock(m.time));
                h.state.setTextColor(Ui.TEXT);
                break;
            case Sports.OFF:
                h.state.setText("Off");
                h.state.setTextColor(Ui.RED);
                break;
            default:
                h.state.setText(m.status.length() > 8 ? "FT" : m.status);
                h.state.setTextColor(Ui.MUTED);
        }
        String sum = m.state == Sports.OFF ? m.status : !m.summary.isEmpty() ? m.summary : m.state == Sports.PRE ? m.venue : "";
        h.summary.setText(sum);
        h.summary.setVisibility(sum.isEmpty() ? GONE : VISIBLE);
        h.accent.setVisibility(m.sa ? VISIBLE : GONE);
        h.card.setOnClickListener(x -> MatchSheet.show(a, m));
        h.card.setContentDescription(describe(m));
        return v;
    }

    static String describe(Sports.Match m) {
        if (m.isEvent()) return m.title + ", " + m.status;
        String s = m.home.name + (m.home.score.isEmpty() ? "" : " " + m.home.score) + " versus " + m.away.name
                + (m.away.score.isEmpty() ? "" : " " + m.away.score);
        if (m.state == Sports.PRE) s += ", " + Sports.clock(m.time);
        else if (!m.status.isEmpty()) s += ", " + m.status;
        if (!m.summary.isEmpty()) s += ". " + m.summary;
        return s;
    }

    private LogoView logo() {
        LogoView l = new LogoView(a);
        l.setCircle(true);
        return l;
    }

    private TextView name() {
        TextView t = Ui.text(a, "", 15, Ui.TEXT, Ui.W_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 8), 0);
        return t;
    }

    private TextView score() {
        TextView t = Ui.text(a, "", 15, Ui.TEXT, Ui.W_HEAVY);
        t.setGravity(Gravity.END);
        t.setMaxWidth(Ui.dp(a, 150));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private void bindSide(LogoView logo, TextView name, TextView score, Sports.Side s, boolean lost) {
        bindLogo(a, logo, s.name, s.logo);
        name.setText(s.shortName.isEmpty() ? s.name : s.shortName);
        name.setTextColor(s.sa ? Ui.GOLD : lost ? Ui.MUTED : Ui.TEXT);
        score.setText(s.score);
        score.setTextColor(lost ? Ui.MUTED : Ui.TEXT);
        // Cricket scores ("169/6 (20 ov, target 179)") are long; keep them smaller.
        score.setTextSize(s.score.length() > 6 ? 13 : 16);
    }

    static void bindLogo(MainActivity a, final LogoView v, String name, String url) {
        v.setStation(name, url);
        if (url == null || url.isEmpty()) return;
        Bitmap b = ImageLoader.cachedAny(url);
        if (b != null) {
            v.setLogo(b);
            return;
        }
        ImageLoader.get(url, 96, new ImageLoader.Target() {
            @Override
            public boolean wants(String u) {
                return u.equals(v.getUrl());
            }

            @Override
            public void onBitmap(String u, Bitmap bm) {
                v.setLogo(bm);
            }
        });
    }

    private View eventRow(View v, final Sports.Match m) {
        EventHolder h;
        if (v == null) {
            h = new EventHolder();
            LinearLayout col = Ui.column(a);
            col.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 12));
            LinearLayout top = Ui.row(a);
            h.title = Ui.text(a, "", 15.5f, Ui.TEXT, Ui.W_HEAVY);
            h.title.setMaxLines(2);
            top.addView(h.title, Ui.weight(1));
            h.pill = Ui.text(a, "", 11, Ui.TEXT2, Ui.W_HEAVY);
            h.pill.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3));
            top.addView(h.pill, Ui.lp(-2, -2, Ui.dp(a, 8), 0, 0, 0));
            col.addView(top);
            h.sub = Ui.text(a, "", 12, Ui.MUTED, Ui.W_MEDIUM);
            h.sub.setSingleLine(true);
            h.sub.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(h.sub, Ui.lp(-1, -2, 0, Ui.dp(a, 4), 0, 0));
            h.lines = Ui.column(a);
            col.addView(h.lines, Ui.lp(-1, -2, 0, Ui.dp(a, 6), 0, 0));
            View[] acc = new View[1];
            FrameLayout outer = cardShell(col, acc);
            h.accent = acc[0];
            h.card = outer.getChildAt(0);
            outer.setTag(h);
            v = outer;
        } else {
            h = (EventHolder) v.getTag();
        }
        h.title.setText(m.title);
        String pill;
        int bg;
        int fg;
        if (m.state == Sports.LIVE) {
            pill = m.status.isEmpty() ? "LIVE" : m.status;
            bg = Ui.alpha(Ui.LIVE, 0.18f);
            fg = Ui.LIVE;
        } else if (m.state == Sports.PRE) {
            pill = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(m.time);
            bg = Ui.alpha(Ui.GOLD, 0.14f);
            fg = Ui.GOLD;
        } else {
            pill = m.status.isEmpty() ? "Final" : m.status;
            bg = Ui.SURFACE3;
            fg = Ui.TEXT2;
        }
        if (pill.length() > 22) pill = pill.substring(0, 22) + "…";
        h.pill.setText(pill.toUpperCase(Locale.ROOT));
        h.pill.setTextColor(fg);
        h.pill.setBackground(Ui.round(bg, Ui.dp(a, 8)));
        h.sub.setText(m.venue);
        h.sub.setVisibility(m.venue.isEmpty() ? GONE : VISIBLE);
        h.lines.removeAllViews();
        for (Sports.Line l : m.lines) h.lines.addView(lineRow(a, l, false));
        h.accent.setVisibility(m.sa && !Sports.F1.equals(m.sport) ? VISIBLE : GONE);
        h.card.setOnClickListener(x -> MatchSheet.show(a, m));
        h.card.setContentDescription(describe(m));
        return v;
    }

    /** A rank / name / value row, used in cards and in the detail sheet. */
    static View lineRow(MainActivity a, Sports.Line l, boolean big) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, Ui.dp(a, big ? 7 : 4), 0, Ui.dp(a, big ? 7 : 4));
        if (!l.rank.isEmpty()) {
            TextView rank = Ui.text(a, l.rank, 12.5f, Ui.MUTED, Ui.W_HEAVY);
            r.addView(rank, Ui.lp(Ui.dp(a, 38), -2));
        }
        LinearLayout mid = Ui.column(a);
        TextView n = Ui.text(a, l.name + (l.sa ? "  🇿🇦" : ""), big ? 14.5f : 13.5f, l.sa ? Ui.GOLD : Ui.TEXT2,
                l.sa ? Ui.W_BOLD : Ui.W_MEDIUM);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(n);
        if (big && !l.extra.isEmpty()) {
            TextView ex = Ui.text(a, l.extra, 11.5f, Ui.MUTED, Ui.W_MEDIUM);
            ex.setPadding(0, Ui.dp(a, 2), 0, 0);
            mid.addView(ex);
        }
        r.addView(mid, Ui.weight(1));
        TextView val = Ui.text(a, l.value, 13.5f, Ui.TEXT, Ui.W_BOLD);
        val.setTypeface(Ui.bold, Typeface.NORMAL);
        r.addView(val, Ui.lp(-2, -2, Ui.dp(a, 8), 0, 0, 0));
        return r;
    }
}
