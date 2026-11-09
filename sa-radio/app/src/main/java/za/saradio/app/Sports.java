package za.saradio.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fixtures and results for the sport South Africans follow. Rugby, soccer, cricket, golf and UFC
 * come from ESPN's public score feeds (the ones its website uses; no key needed); Formula 1 comes
 * from Jolpica, the open successor to the Ergast F1 database.
 */
final class Sports {
    private Sports() {}

    static final int PRE = 0;
    static final int LIVE = 1;
    static final int POST = 2;
    /** Postponed, cancelled or abandoned. */
    static final int OFF = 3;

    static final String RUGBY = "rugby";
    static final String SOCCER = "soccer";
    static final String CRICKET = "cricket";
    static final String F1 = "f1";
    static final String GOLF = "golf";
    static final String UFC = "ufc";

    /** Filter chips: "sa" means anything involving South African teams or players, plus F1. */
    static final String[] FILTERS = {"sa", RUGBY, SOCCER, CRICKET, F1, GOLF, UFC};
    static final String[] FILTER_NAMES = {"SA focus", "Rugby", "Soccer", "Cricket", "F1", "Golf", "UFC"};

    static final long DAY = 86_400_000L;

    /** A competition and how to fetch it. Lower priority numbers are listed first. */
    static final class Comp {
        final String sport;
        final String feed; // ESPN path: "rugby/17567", "soccer/rsa.1", "golf/eur", "mma/ufc"
        final String name;
        final int priority;
        final int filter;
        /** Big European leagues: fetched only when the Soccer filter is opened, to save data. */
        final boolean onDemand;

        Comp(String sport, String feed, String name, int priority, int filter) {
            this(sport, feed, name, priority, filter, false);
        }

        Comp(String sport, String feed, String name, int priority, int filter, boolean onDemand) {
            this.sport = sport;
            this.feed = feed;
            this.name = name;
            this.priority = priority;
            this.filter = filter;
            this.onDemand = onDemand;
        }
    }

    static final int ALL = 0;
    /** Only matches involving South Africa (friendlies). */
    static final int SA_ONLY = 1;
    /** Only tests between established rugby nations (the feed includes e.g. Pakistan v Uzbekistan). */
    static final int MAJOR_NATIONS = 2;

    static final Comp[] COMPS = {
        new Comp(RUGBY, "rugby/17567", "Nations Championship", 10, ALL),
        new Comp(RUGBY, "rugby/289234", "Test matches", 11, MAJOR_NATIONS),
        new Comp(RUGBY, "rugby/164205", "Rugby World Cup", 12, ALL),
        new Comp(RUGBY, "rugby/244293", "The Rugby Championship", 13, ALL),
        new Comp(RUGBY, "rugby/270557", "United Rugby Championship", 14, ALL),
        new Comp(RUGBY, "rugby/270555", "Currie Cup", 15, ALL),
        new Comp(RUGBY, "rugby/271937", "Champions Cup", 16, ALL),
        new Comp(RUGBY, "rugby/272073", "Challenge Cup", 17, ALL),
        new Comp(RUGBY, "rugby/180659", "Six Nations", 18, ALL),
        new Comp(RUGBY, "rugby/267979", "Premiership Rugby", 19, ALL),
        new Comp(RUGBY, "rugby/270559", "Top 14", 20, ALL),
        new Comp(SOCCER, "soccer/rsa.1", "Betway Premiership", 30, ALL),
        new Comp(SOCCER, "soccer/rsa.nedbank", "Nedbank Cup", 31, ALL),
        new Comp(SOCCER, "soccer/rsa.mtn8", "MTN8", 32, ALL),
        new Comp(SOCCER, "soccer/fifa.worldq.caf", "World Cup qualifiers", 33, ALL),
        new Comp(SOCCER, "soccer/caf.nations", "Africa Cup of Nations", 34, ALL),
        new Comp(SOCCER, "soccer/fifa.world", "FIFA World Cup", 35, ALL),
        new Comp(SOCCER, "soccer/fifa.friendly", "Friendlies", 36, SA_ONLY),
        new Comp(SOCCER, "soccer/caf.champions", "CAF Champions League", 37, ALL),
        new Comp(SOCCER, "soccer/caf.confed", "CAF Confederation Cup", 38, ALL),
        new Comp(SOCCER, "soccer/rsa.2", "Motsepe Foundation Championship", 39, ALL),
        new Comp(SOCCER, "soccer/eng.1", "Premier League", 40, ALL, true),
        new Comp(SOCCER, "soccer/uefa.champions", "Champions League", 41, ALL, true),
        new Comp(SOCCER, "soccer/esp.1", "La Liga", 42, ALL, true),
        new Comp(SOCCER, "soccer/ita.1", "Serie A", 43, ALL, true),
        new Comp(SOCCER, "soccer/ger.1", "Bundesliga", 44, ALL, true),
        new Comp(SOCCER, "soccer/uefa.europa", "Europa League", 45, ALL, true),
        new Comp(GOLF, "golf/eur", "DP World Tour", 70, ALL),
        new Comp(GOLF, "golf/pga", "PGA Tour", 71, ALL),
        new Comp(UFC, "mma/ufc", "UFC", 80, ALL),
    };
    static final int CRICKET_PRIORITY = 50;
    static final int F1_PRIORITY = 60;

    static final String[] RUGBY_NATIONS = {
        "south africa", "new zealand", "australia", "argentina", "england", "ireland", "france", "scotland", "wales",
        "italy", "japan", "fiji", "georgia", "samoa", "tonga", "portugal", "uruguay", "united states", "usa", "canada",
        "chile", "spain", "romania", "namibia", "zimbabwe", "kenya", "british and irish lions"
    };
    static final String[] CRICKET_NATIONS = {
        "south africa", "australia", "england", "india", "new zealand", "pakistan", "sri lanka", "west indies",
        "bangladesh", "afghanistan", "ireland", "zimbabwe"
    };
    static final String[] SA_RUGBY = {
        "south africa", "bulls", "stormers", "sharks", "lions", "cheetahs", "pumas", "griquas", "western province",
        "leopards", "griffons", "border bulldogs", "eastern province", "boland", "valke", "sa a"
    };
    static final String[] SA_CLUBS = {
        "sundowns", "orlando pirates", "kaizer chiefs", "stellenbosch", "sekhukhune", "amazulu", "ts galaxy",
        "golden arrows", "chippa", "polokwane", "richards bay", "magesi", "marumo", "durban city", "siwelele",
        "orbit college", "cape town city", "supersport united", "royal am", "moroka swallows"
    };

    // ---- Model ------------------------------------------------------------------------------

    static final class Side {
        String name = "";
        String shortName = "";
        String abbr = "";
        String logo = "";
        String score = "";
        boolean winner;
        boolean sa;
    }

    /** One row of an event card: a driver, golfer or bout. */
    static final class Line {
        String rank = "";
        String name = "";
        String value = "";
        /** Golf: round scores. */
        String extra = "";
        boolean sa;

        Line(String rank, String name, String value, boolean sa) {
            this.rank = rank;
            this.name = name;
            this.value = value;
            this.sa = sa;
        }
    }

    static final class Match {
        String id = "";
        String sport = "";
        String feed = "";
        String compName = "";
        int priority;
        long time;
        long endTime;
        int state;
        String status = "";
        String summary = "";
        String venue = "";
        Side home;
        Side away;
        String title = "";
        final List<Line> lines = new ArrayList<>();
        /** Golf: the whole leaderboard. UFC: the whole card. */
        final List<Line> board = new ArrayList<>();
        boolean sa;
        String season = "";
        String round = "";

        boolean isEvent() {
            return home == null;
        }

        String key() {
            return sport + ":" + feed + ":" + id;
        }
    }

    /** What a load brought back. */
    static final class Result {
        final List<Match> matches = new ArrayList<>();
        int failed;
        int sources;
        long at;
    }

    // ---- Loading ----------------------------------------------------------------------------

    /** Everything from a week before {@code now} to two weeks after. Runs on a background thread. */
    static Result load(long now) {
        return load(now, false, true);
    }

    /** Just the big European soccer leagues. */
    static Result loadOnDemand(long now) {
        return load(now, true, false);
    }

    private static Result load(long now, boolean onDemand, boolean rest) {
        Result r = new Result();
        r.at = now;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<List<Match>>> jobs = new ArrayList<>();
        Set<String> months = months(now - 8 * DAY, now + 15 * DAY);
        for (final Comp c : COMPS) {
            if (c.onDemand != onDemand) continue;
            for (final String m : months) {
                // Golf and UFC are few per month; the others are fetched for both months as needed.
                jobs.add(pool.submit(() -> espnScoreboard(c, m)));
            }
        }
        if (rest) {
            for (final long day : new long[] {now - 6 * DAY, now - 2 * DAY, now + 2 * DAY, now + 6 * DAY}) {
                jobs.add(pool.submit(() -> cricketPanel(day)));
            }
            jobs.add(pool.submit(() -> f1(now)));
        }
        r.sources = jobs.size();
        Map<String, Match> unique = new HashMap<>();
        for (Future<List<Match>> j : jobs) {
            try {
                for (Match m : j.get(30, TimeUnit.SECONDS)) unique.put(m.key(), m);
            } catch (Exception e) {
                r.failed++;
            }
        }
        pool.shutdownNow();
        for (Match m : unique.values()) {
            if (m.time < now - 8 * DAY && m.endTime < now - 8 * DAY) continue;
            if (m.time > now + 15 * DAY) continue;
            r.matches.add(m);
        }
        return r;
    }

    /** Reloads just the competitions that have live matches. */
    static List<Match> refreshLive(List<Match> current, long now) {
        Set<String> feeds = new LinkedHashSet<>();
        boolean cricket = false;
        boolean f1 = false;
        for (Match m : current) {
            if (m.state != LIVE && !(m.state == PRE && m.time < now + 10 * 60_000L && m.time > now - 6 * 3600_000L)) continue;
            if (CRICKET.equals(m.sport)) cricket = true;
            else if (F1.equals(m.sport)) f1 = true;
            else feeds.add(m.feed);
        }
        List<Match> out = new ArrayList<>();
        String month = new SimpleDateFormat("yyyyMM", Locale.ROOT).format(now);
        for (Comp c : COMPS) {
            if (!feeds.contains(c.feed)) continue;
            try {
                out.addAll(espnScoreboard(c, month));
            } catch (Exception ignored) {
            }
        }
        try {
            if (cricket) out.addAll(cricketPanel(now));
            if (f1) out.addAll(f1(now));
        } catch (Exception ignored) {
        }
        return out;
    }

    static Set<String> months(long from, long to) {
        Set<String> out = new LinkedHashSet<>();
        SimpleDateFormat f = new SimpleDateFormat("yyyyMM", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (long t = from; t <= to + DAY; t += 10 * DAY) out.add(f.format(t));
        out.add(f.format(to));
        return out;
    }

    static List<Match> espnScoreboard(Comp c, String month) throws IOException {
        String url = "https://site.api.espn.com/apis/site/v2/sports/" + c.feed + "/scoreboard?dates=" + month + "&limit=300";
        return parseEspn(c, getJson(url));
    }

    /** ESPN scoreboard JSON to matches (soccer and rugby) or events (golf and UFC). */
    static List<Match> parseEspn(Comp c, JSONObject d) {
        List<Match> out = new ArrayList<>();
        JSONArray evs = d.optJSONArray("events");
        for (int i = 0; evs != null && i < evs.length(); i++) {
            JSONObject e = evs.optJSONObject(i);
            if (e == null) continue;
            try {
                Match m;
                if (GOLF.equals(c.sport)) m = golfEvent(c, e);
                else if (UFC.equals(c.sport)) m = ufcEvent(c, e);
                else m = headToHead(c, e);
                if (m != null) out.add(m);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static Match base(Comp c, JSONObject e) {
        Match m = new Match();
        m.id = e.optString("id");
        m.sport = c.sport;
        m.feed = c.feed;
        m.compName = c.name;
        m.priority = c.priority;
        m.time = parseTime(e.optString("date"));
        m.endTime = parseTime(e.optString("endDate"));
        if (m.endTime == 0) m.endTime = m.time;
        JSONObject st = e.optJSONObject("status");
        if (st == null) {
            JSONArray comps = e.optJSONArray("competitions");
            if (comps != null && comps.length() > 0) st = comps.optJSONObject(0).optJSONObject("status");
        }
        applyStatus(m, st);
        return m;
    }

    static void applyStatus(Match m, JSONObject st) {
        JSONObject t = st == null ? null : st.optJSONObject("type");
        if (t == null) {
            m.state = PRE;
            return;
        }
        String name = t.optString("name");
        String state = t.optString("state");
        if (name.contains("POSTPONED") || name.contains("CANCELED") || name.contains("CANCELLED") || name.contains("ABANDONED")
                || name.contains("SUSPENDED") || name.contains("FORFEIT")) {
            m.state = OFF;
            m.status = t.optString("shortDetail", "Postponed");
        } else if ("in".equals(state)) {
            m.state = LIVE;
            m.status = t.optString("shortDetail");
        } else if ("post".equals(state)) {
            m.state = POST;
            String sd = t.optString("shortDetail");
            m.status = sd.isEmpty() || sd.equalsIgnoreCase("Final") || sd.equalsIgnoreCase("Full Time") ? "FT" : sd;
        } else {
            m.state = PRE;
        }
        String summary = st.optString("summary");
        // "Starts at 13:00 local time" is the venue's time; the app shows its own kick-off time instead.
        if (!summary.isEmpty() && !summary.toLowerCase(Locale.ROOT).startsWith("starts at")) m.summary = summary;
    }

    private static Match headToHead(Comp c, JSONObject e) throws org.json.JSONException {
        JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
        Match m = base(c, e);
        JSONArray cs = comp.getJSONArray("competitors");
        for (int k = 0; k < cs.length(); k++) {
            JSONObject x = cs.getJSONObject(k);
            Side s = side(x);
            if ("away".equals(x.optString("homeAway")) || (m.home != null && m.away == null)) m.away = s;
            else m.home = s;
        }
        if (m.home == null || m.away == null) return null;
        if (m.state == PRE || m.state == OFF) {
            m.home.score = "";
            m.away.score = "";
        }
        JSONObject v = comp.optJSONObject("venue");
        if (v != null) {
            String city = v.optJSONObject("address") != null ? v.optJSONObject("address").optString("city") : "";
            m.venue = v.optString("fullName") + (city.isEmpty() || v.optString("fullName").contains(city) ? "" : ", " + city);
        }
        String h = m.home.name.toLowerCase(Locale.ROOT), a = m.away.name.toLowerCase(Locale.ROOT);
        if (RUGBY.equals(c.sport)) {
            m.home.sa = saRugby(h);
            m.away.sa = saRugby(a);
            if (c.filter == MAJOR_NATIONS && !(any(h, RUGBY_NATIONS) || any(a, RUGBY_NATIONS))) return null;
        } else {
            boolean league = c.feed.startsWith("soccer/rsa.");
            m.home.sa = league || h.equals("south africa") || any(h, SA_CLUBS);
            m.away.sa = league || a.equals("south africa") || any(a, SA_CLUBS);
            if (c.filter == SA_ONLY && !(h.equals("south africa") || a.equals("south africa"))) return null;
        }
        m.sa = m.home.sa || m.away.sa;
        return m;
    }

    static Side side(JSONObject x) {
        Side s = new Side();
        JSONObject t = x.optJSONObject("team");
        if (t == null) t = x.optJSONObject("athlete");
        if (t != null) {
            s.name = t.optString("displayName", t.optString("name"));
            s.shortName = t.optString("shortDisplayName", s.name);
            s.abbr = t.optString("abbreviation");
            s.logo = smallLogo(t.optString("logo"));
        }
        if ("South Africa".equals(s.name)) s.abbr = "RSA"; // ESPN uses "SOU"
        s.score = x.optString("score");
        s.winner = x.optBoolean("winner");
        return s;
    }

    /** ESPN logos are 500 px; its image service sends an 80 px copy at a tenth of the size. */
    static String smallLogo(String url) {
        if (url == null || url.isEmpty()) return "";
        String p = "https://a.espncdn.com/i/";
        if (url.startsWith(p)) return "https://a.espncdn.com/combiner/i?img=/i/" + url.substring(p.length()) + "&w=80&h=80";
        return url;
    }

    static boolean saRugby(String lower) {
        if (lower.contains("irish lions")) return false;
        return any(lower, SA_RUGBY);
    }

    private static boolean any(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    private static boolean fromSA(JSONObject athlete) {
        JSONObject flag = athlete == null ? null : athlete.optJSONObject("flag");
        return flag != null && "South Africa".equalsIgnoreCase(flag.optString("alt"));
    }

    private static Match golfEvent(Comp c, JSONObject e) throws org.json.JSONException {
        Match m = base(c, e);
        m.title = e.optString("name");
        JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
        JSONArray ps = comp.optJSONArray("competitors");
        List<JSONObject> players = new ArrayList<>();
        for (int i = 0; ps != null && i < ps.length(); i++) players.add(ps.getJSONObject(i));
        Collections.sort(players, (p, q) -> Integer.compare(p.optInt("order", 999), q.optInt("order", 999)));
        List<Line> board = leaderboard(players);
        for (int i = 0; i < board.size(); i++) {
            JSONArray ls = players.get(i).optJSONArray("linescores");
            StringBuilder r = new StringBuilder();
            for (int k = 0; ls != null && k < ls.length(); k++) {
                JSONObject l = ls.optJSONObject(k);
                if (l == null || !l.has("value")) continue;
                if (r.length() > 0) r.append("  ");
                r.append(Math.round(l.optDouble("value")));
            }
            board.get(i).extra = r.toString();
        }
        m.board.addAll(board);
        m.sa = false;
        int shown = 0;
        for (Line l : board) {
            if (shown < 3) {
                m.lines.add(l);
                shown++;
            }
            if (l.sa) m.sa = true;
        }
        int saShown = 0;
        for (Line l : board) {
            if (l.sa && !m.lines.contains(l) && saShown < 3) {
                m.lines.add(l);
                saShown++;
            }
        }
        if (m.state == PRE) m.lines.clear();
        JSONObject st = e.optJSONObject("status");
        if (st != null && st.optJSONObject("type") != null) {
            String detail = st.optJSONObject("type").optString("detail");
            if (m.state == LIVE) m.status = detail.isEmpty() ? "In progress" : detail;
            if (m.state == POST) m.status = "Final";
        }
        return m;
    }

    /** Positions with ties ("T8") worked out from the scores. */
    static List<Line> leaderboard(List<JSONObject> players) {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            JSONObject p = players.get(i);
            String score = p.optString("score");
            int pos = i + 1;
            while (pos > 1 && players.get(pos - 2).optString("score").equals(score)) pos--;
            boolean tie = (i > 0 && players.get(i - 1).optString("score").equals(score))
                    || (i + 1 < players.size() && players.get(i + 1).optString("score").equals(score));
            JSONObject a = p.optJSONObject("athlete");
            String name = a == null ? "" : a.optString("displayName");
            out.add(new Line((tie ? "T" : "") + pos, name, score, fromSA(a)));
        }
        return out;
    }

    private static Match ufcEvent(Comp c, JSONObject e) throws org.json.JSONException {
        String name = e.optString("name");
        if (name.toLowerCase(Locale.ROOT).contains("contender series")) return null;
        Match m = base(c, e);
        m.title = name;
        JSONArray bouts = e.optJSONArray("competitions");
        int n = bouts == null ? 0 : bouts.length();
        // The main event is listed last; show the top of the card first.
        for (int i = n - 1; i >= 0; i--) {
            JSONObject b = bouts.getJSONObject(i);
            Line l = bout(b);
            if (l == null) continue;
            m.board.add(l);
            if (l.sa) m.sa = true;
            if (m.lines.size() < 3 || l.sa) m.lines.add(l);
        }
        JSONObject comp0 = n > 0 ? bouts.getJSONObject(n - 1) : null;
        JSONObject v = comp0 == null ? null : comp0.optJSONObject("venue");
        if (v != null) m.venue = v.optString("fullName");
        if (m.state == POST) m.status = "Results";
        return m;
    }

    /** "Silva def. Wang" (or "Silva vs Wang" before the fight), weight class as the value. */
    static Line bout(JSONObject b) throws org.json.JSONException {
        JSONArray cs = b.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        JSONObject x = cs.getJSONObject(0), y = cs.getJSONObject(1);
        JSONObject ax = x.optJSONObject("athlete"), ay = y.optJSONObject("athlete");
        String nx = ax == null ? "" : ax.optString("displayName"), ny = ay == null ? "" : ay.optString("displayName");
        String text;
        if (x.optBoolean("winner")) text = nx + " def. " + ny;
        else if (y.optBoolean("winner")) text = ny + " def. " + nx;
        else text = nx + " vs " + ny;
        JSONObject type = b.optJSONObject("type");
        String weight = type == null ? "" : type.optString("abbreviation");
        return new Line("", text, weight, fromSA(ax) || fromSA(ay));
    }

    // ---- Cricket ----------------------------------------------------------------------------

    /** ESPN's cricket panel lists matches from about four days around the given date. */
    static List<Match> cricketPanel(long day) throws IOException {
        String d = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(day);
        return parseCricket(getJson("https://site.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=" + d));
    }

    static List<Match> parseCricket(JSONObject d) {
        List<Match> out = new ArrayList<>();
        JSONArray scores = d.optJSONArray("scores");
        for (int i = 0; scores != null && i < scores.length(); i++) {
            JSONObject sb = scores.optJSONObject(i);
            JSONArray lgs = sb == null ? null : sb.optJSONArray("leagues");
            if (lgs == null || lgs.length() == 0) continue;
            JSONObject lg = lgs.optJSONObject(0);
            if (lg == null) continue;
            String series = lg.optString("name");
            String lgId = lg.optString("id");
            JSONArray evs = sb.optJSONArray("events");
            for (int k = 0; evs != null && k < evs.length(); k++) {
                try {
                    Match m = cricketMatch(series, lgId, evs.getJSONObject(k));
                    if (m != null) out.add(m);
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    static Match cricketMatch(String series, String lgId, JSONObject e) throws org.json.JSONException {
        JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
        JSONArray cs = comp.getJSONArray("competitors");
        if (cs.length() < 2) return null;
        Match m = new Match();
        m.sport = CRICKET;
        m.feed = "cricket/" + lgId;
        m.id = e.optString("id");
        m.compName = series;
        m.time = parseTime(e.optString("date"));
        m.endTime = parseTime(e.optString("endDate"));
        if (m.endTime == 0) m.endTime = m.time;
        applyStatus(m, e.optJSONObject("status"));
        if (m.state == POST) m.status = "Result";
        if (m.state == LIVE) m.status = "Live";
        JSONObject x = cs.getJSONObject(0), y = cs.getJSONObject(1);
        if (x.optInt("order", 1) > y.optInt("order", 2)) {
            JSONObject t = x;
            x = y;
            y = t;
        }
        m.home = side(x);
        m.away = side(y);
        // ESPN's cricket short names are codes ("WPR", "TITNS"); the full names read better.
        m.home.shortName = m.home.name;
        m.away.shortName = m.away.name;
        // Its end dates are placeholders (the panel's window), so file matches under their start.
        m.endTime = m.time;
        String h = m.home.name.toLowerCase(Locale.ROOT), a = m.away.name.toLowerCase(Locale.ROOT);
        String s = series.toLowerCase(Locale.ROOT);
        boolean saSeries = s.contains("south africa") || s.contains("sa20") || s.contains("csa ");
        m.home.sa = h.startsWith("south africa") || (saSeries && !h.contains("south africa") && s.contains("domestic"));
        m.away.sa = a.startsWith("south africa") || (saSeries && !a.contains("south africa") && s.contains("domestic"));
        m.sa = m.home.sa || m.away.sa || saSeries;
        boolean bigNations = nation(h) && nation(a);
        boolean icc = s.contains("icc") || s.contains("world cup") || s.contains("champions trophy") || s.contains("indian premier league")
                || s.contains("sa20");
        if (!m.sa && !bigNations && !icc) return null;
        if (s.contains("under-19") || s.contains(" a tour") || h.endsWith(" a") || a.endsWith(" a")) {
            if (!m.sa) return null;
        }
        m.priority = m.sa ? Sports.CRICKET_PRIORITY : Sports.CRICKET_PRIORITY + 1;
        JSONObject v = comp.optJSONObject("venue");
        if (v != null) m.venue = v.optString("fullName");
        return m;
    }

    private static boolean nation(String lower) {
        String n = lower.replace(" women", "");
        for (String c : CRICKET_NATIONS) if (n.equals(c)) return true;
        return false;
    }

    // ---- Formula 1 (Jolpica) ----------------------------------------------------------------

    static final String JOLPICA = "https://api.jolpi.ca/ergast/f1/";

    static List<Match> f1(long now) throws IOException, org.json.JSONException {
        String season = new SimpleDateFormat("yyyy", Locale.ROOT).format(now);
        JSONArray races = getJson(JOLPICA + season + ".json").getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
        List<Match> out = new ArrayList<>();
        for (int i = 0; i < races.length(); i++) {
            JSONObject r = races.getJSONObject(i);
            long t = jolpicaTime(r);
            if (t < now - 8 * DAY || t > now + 15 * DAY) continue;
            Match m = new Match();
            m.sport = F1;
            m.feed = "f1";
            m.season = r.optString("season", season);
            m.round = r.optString("round");
            m.id = m.season + "-" + m.round;
            m.compName = "Formula 1";
            m.priority = F1_PRIORITY;
            m.title = r.optString("raceName");
            JSONObject circ = r.optJSONObject("Circuit");
            if (circ != null) {
                JSONObject loc = circ.optJSONObject("Location");
                m.venue = circ.optString("circuitName") + (loc != null ? ", " + loc.optString("country") : "");
            }
            m.time = t;
            m.endTime = t + 2 * 3600_000L;
            m.sa = true; // followed by everyone; always in SA focus
            if (t < now) {
                JSONArray res = null;
                try {
                    res = getJson(JOLPICA + m.season + "/" + m.round + "/results.json").getJSONObject("MRData")
                            .getJSONObject("RaceTable").getJSONArray("Races");
                } catch (Exception ignored) {
                }
                if (res != null && res.length() > 0) {
                    m.state = POST;
                    m.status = "Result";
                    JSONArray rs = res.getJSONObject(0).getJSONArray("Results");
                    int leaderLaps = rs.length() > 0 ? rs.getJSONObject(0).optInt("laps") : 0;
                    for (int k = 0; k < Math.min(3, rs.length()); k++) m.lines.add(f1Line(rs.getJSONObject(k), leaderLaps));
                } else {
                    m.state = now < t + 3 * 3600_000L ? LIVE : POST;
                    m.status = m.state == LIVE ? "Racing" : "Awaiting result";
                }
            } else {
                m.state = PRE;
            }
            out.add(m);
        }
        return out;
    }

    static Line f1Line(JSONObject x, int leaderLaps) {
        JSONObject d = x.optJSONObject("Driver"), c = x.optJSONObject("Constructor");
        String name = d == null ? "" : d.optString("givenName") + " " + d.optString("familyName");
        String team = c == null ? "" : c.optString("name");
        JSONObject time = x.optJSONObject("Time");
        String status = x.optString("status");
        String val;
        if ("Lapped".equalsIgnoreCase(status) || status.startsWith("+")) {
            // A lap or more down: the feed's "time" for these cars isn't a gap to the winner.
            int down = leaderLaps - x.optInt("laps", leaderLaps);
            val = down > 0 ? "+" + down + (down == 1 ? " lap" : " laps") : status;
        } else if (time != null && "Finished".equalsIgnoreCase(status)) {
            val = time.optString("time");
        } else {
            val = status;
        }
        return new Line("P" + x.optString("position"), name + (team.isEmpty() ? "" : "  ·  " + team), val, false);
    }

    static long jolpicaTime(JSONObject r) {
        String date = r.optString("date");
        String time = r.optString("time", "12:00:00Z");
        return parseTime(date + "T" + time);
    }

    // ---- Helpers ----------------------------------------------------------------------------

    static long parseTime(String s) {
        if (s == null || s.length() < 10) return 0;
        String[] pats = {"yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mmX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd"};
        for (String p : pats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.ROOT);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                f.setLenient(false);
                return f.parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static JSONObject getJson(String url) throws IOException {
        try {
            return new JSONObject(new String(get(url), StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException("bad JSON from " + url);
        }
    }

    static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(9000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", StationRepo.USER_AGENT);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            byte[] body = StationRepo.readAll(c.getInputStream(), 8 << 20);
            // ESPN sends gzip even when not asked; Android unpacks it, plain Java doesn't.
            if (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    body = StationRepo.readAll(in, 32 << 20);
                }
            }
            return body;
        } finally {
            c.disconnect();
        }
    }

    // ---- Views of the list ------------------------------------------------------------------

    static boolean matchesFilter(Match m, String filter) {
        if ("sa".equals(filter)) {
            if (F1.equals(m.sport)) return true;
            return m.sa;
        }
        return filter.equals(m.sport);
    }

    /** Midnight (local time) of the day {@code t} falls on. */
    static long dayStart(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        k.set(Calendar.HOUR_OF_DAY, 0);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        return k.getTimeInMillis();
    }

    static String dayLabel(long day, long now) {
        long today = dayStart(now);
        long diff = Math.round((dayStart(day) - today) / (double) DAY);
        if (diff == 0) return "Today";
        if (diff == -1) return "Yesterday";
        if (diff == 1) return "Tomorrow";
        return new SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(day);
    }

    static String clock(long t) {
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(t);
    }
}
