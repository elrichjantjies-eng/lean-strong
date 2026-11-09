package za.saradio.app;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Details for one match or event: scorers, line-ups, scorecards, classifications, leaderboards. */
final class MatchSheet {
    private final MainActivity a;
    private final Sports.Match m;
    private final Sheet s;
    private final LinearLayout content;
    private final Handler main = new Handler(Looper.getMainLooper());

    private MatchSheet(MainActivity a, Sports.Match m) {
        this.a = a;
        this.m = m;
        s = new Sheet(a, m.compName);
        content = Ui.column(a);
    }

    static void show(MainActivity a, Sports.Match m) {
        MatchSheet ms = new MatchSheet(a, m);
        ms.build();
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private void build() {
        if (m.isEvent()) eventHeader();
        else scoreHeader();
        s.body.addView(content, Ui.lp(-1, -2));
        if (m.state == Sports.LIVE) {
            ImageView refresh = Ui.iconButton(a, R.drawable.ic_refresh, Ui.TEXT, Ui.SURFACE3, 40, "Refresh");
            refresh.setOnClickListener(v -> {
                v.animate().rotationBy(360).setDuration(500).start();
                s.dismiss();
                MatchSheet.show(a, m);
            });
            s.addAction(refresh);
        }
        s.show();
        switch (m.sport) {
            case Sports.SOCCER:
            case Sports.RUGBY:
                fetch("https://site.api.espn.com/apis/site/v2/sports/" + m.feed + "/summary?event=" + m.id);
                break;
            case Sports.CRICKET:
                fetch("https://site.api.espn.com/apis/site/v2/sports/" + m.feed + "/summary?event=" + m.id);
                break;
            case Sports.F1:
                f1();
                break;
            case Sports.GOLF:
                golf();
                break;
            default:
                ufc();
        }
    }

    // ---- Headers ----------------------------------------------------------------------------

    private void scoreHeader() {
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.TOP);
        row.addView(teamColumn(m.home), Ui.weight(1));
        LinearLayout mid = Ui.column(a);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean scored = (m.state == Sports.POST || m.state == Sports.LIVE) && !m.home.score.isEmpty();
        boolean cricket = Sports.CRICKET.equals(m.sport);
        String big = scored && !cricket ? m.home.score + "  –  " + m.away.score : m.state == Sports.PRE ? Sports.clock(m.time) : "v";
        TextView score = Ui.text(a, big, scored && !cricket ? 40 : 30, Ui.TEXT, Ui.W_HEAVY);
        score.setTypeface(Ui.display);
        score.setGravity(Gravity.CENTER);
        mid.addView(score, Ui.lp(-2, -2, 0, dp(14), 0, 0));
        String st = m.state == Sports.LIVE ? (m.status.isEmpty() ? "LIVE" : m.status) : m.state == Sports.PRE
                ? new SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(m.time) : m.status;
        TextView status = Ui.text(a, st, 12.5f, m.state == Sports.LIVE ? Ui.LIVE : Ui.TEXT2, Ui.W_HEAVY);
        status.setGravity(Gravity.CENTER);
        mid.addView(status, Ui.lp(-2, -2, 0, dp(6), 0, 0));
        row.addView(mid, Ui.lp(dp(110), -2));
        row.addView(teamColumn(m.away), Ui.weight(1));
        s.body.addView(row, Ui.lp(-1, -2, 0, dp(4), 0, 0));
        if (cricket && scored) {
            TextView sc = Ui.text(a, m.home.shortName + "  " + m.home.score + "\n" + m.away.shortName + "  " + m.away.score, 14, Ui.TEXT2,
                    Ui.W_BOLD);
            sc.setGravity(Gravity.CENTER);
            sc.setLineSpacing(0, 1.4f);
            s.body.addView(sc, Ui.lp(-1, -2, 0, dp(14), 0, 0));
        }
        if (!m.summary.isEmpty()) {
            TextView sum = Ui.text(a, m.summary, 15, Ui.ACCENT, Ui.W_BOLD);
            sum.setGravity(Gravity.CENTER);
            s.body.addView(sum, Ui.lp(-1, -2, 0, dp(12), 0, 0));
        }
        meta();
    }

    private View teamColumn(Sports.Side side) {
        LinearLayout c = Ui.column(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        LogoView l = new LogoView(a);
        l.setCircle(true);
        SportView.bindLogo(a, l, side.name, side.logo);
        c.addView(l, Ui.lp(dp(60), dp(60)));
        TextView n = Ui.text(a, side.name, 14.5f, side.sa ? Ui.ACCENT : Ui.TEXT, Ui.W_BOLD);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        c.addView(n, Ui.lp(-1, -2, 0, dp(8), 0, 0));
        return c;
    }

    private void eventHeader() {
        TextView t = Ui.text(a, m.title, 26, Ui.TEXT, Ui.W_HEAVY);
        t.setTypeface(Ui.display);
        t.setLineSpacing(0, 1.05f);
        s.body.addView(t, Ui.lp(-1, -2, 0, dp(2), 0, 0));
        String st = m.state == Sports.LIVE ? "Live · " + m.status : m.state == Sports.PRE ? "Starts " + when(m.time) : m.status;
        TextView status = Ui.text(a, st, 13, m.state == Sports.LIVE ? Ui.LIVE : Ui.ACCENT, Ui.W_HEAVY);
        s.body.addView(status, Ui.lp(-1, -2, 0, dp(8), 0, 0));
        meta();
    }

    private void meta() {
        StringBuilder sb = new StringBuilder();
        sb.append(when(m.time));
        if (!m.venue.isEmpty()) sb.append("\n").append(m.venue);
        TextView t = Ui.text(a, sb, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        t.setGravity(m.isEvent() ? Gravity.START : Gravity.CENTER);
        t.setLineSpacing(0, 1.35f);
        s.body.addView(t, Ui.lp(-1, -2, 0, dp(10), 0, dp(4)));
    }

    static String when(long t) {
        return new SimpleDateFormat("EEEE d MMMM, HH:mm", Locale.ENGLISH).format(t);
    }

    // ---- Loading ----------------------------------------------------------------------------

    private void loading() {
        ProgressBar p = new ProgressBar(a);
        p.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        content.addView(p, Ui.lp(dp(36), dp(36), 0, dp(24), 0, dp(24)));
        ((LinearLayout.LayoutParams) p.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
    }

    private void fetch(final String url) {
        loading();
        new Thread(() -> {
            JSONObject d = null;
            try {
                d = Sports.getJson(url);
            } catch (Exception ignored) {
            }
            final JSONObject data = d;
            main.post(() -> {
                content.removeAllViews();
                if (data == null) {
                    note("Couldn't load the details. Check your connection.");
                    return;
                }
                try {
                    if (Sports.SOCCER.equals(m.sport)) soccer(data);
                    else if (Sports.RUGBY.equals(m.sport)) rugby(data);
                    else cricket(data);
                } catch (Exception e) {
                    note("Some details aren't available for this match.");
                }
                if (content.getChildCount() == 0) note(m.state == Sports.PRE
                        ? "Line-ups usually appear about an hour before kick-off."
                        : "No further details for this match.");
            });
        }, "match").start();
    }

    // ---- Building blocks --------------------------------------------------------------------

    private TextView section(String title) {
        TextView t = Ui.text(a, title.toUpperCase(Locale.ROOT), 11.5f, Ui.MUTED, Ui.W_BOLD);
        t.setLetterSpacing(0.12f);
        Ui.heading(t);
        content.addView(t, Ui.lp(-2, -2, dp(2), dp(22), 0, dp(10)));
        return t;
    }

    private LinearLayout card() {
        LinearLayout k = Ui.column(a);
        k.setPadding(dp(14), dp(8), dp(14), dp(8));
        k.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(18), Ui.STROKE, dp(1)));
        content.addView(k, Ui.lp(-1, -2));
        return k;
    }

    private void note(String text) {
        TextView t = Ui.text(a, text, 14, Ui.MUTED, Ui.W_MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.3f);
        content.addView(t, Ui.lp(-1, -2, 0, dp(24), 0, dp(12)));
    }

    /** Minute, team tag, then what happened, across the full width. */
    private void timelineRow(LinearLayout parent, String minute, String icon, String text, boolean home) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView mid = Ui.text(a, minute, 12, Ui.ACCENT, Ui.W_HEAVY);
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(Ui.round(Ui.alpha(Ui.ACCENT, 0.12f), dp(10)));
        mid.setPadding(dp(4), dp(3), dp(4), dp(3));
        r.addView(mid, Ui.lp(dp(46), -2, 0, 0, dp(10), 0));
        Sports.Side side = home ? m.home : m.away;
        String tag = !side.abbr.isEmpty() && side.abbr.length() <= 4 ? side.abbr : side.shortName;
        TextView team = Ui.text(a, tag, 12, side.sa ? Ui.ACCENT : Ui.MUTED, Ui.W_HEAVY);
        team.setSingleLine(true);
        team.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(team, Ui.lp(dp(48), -2, 0, dp(2), dp(6), 0));
        TextView what = Ui.text(a, (icon.isEmpty() ? "" : icon + "  ") + text, 13.5f, Ui.TEXT, Ui.W_MEDIUM);
        what.setLineSpacing(0, 1.2f);
        r.addView(what, Ui.weight(1));
        parent.addView(r);
    }

    private void lineupToggle(final JSONArray rosters, final boolean rugby) {
        if (rosters == null || rosters.length() < 2) return;
        section(rugby ? "Line-ups" : "Line-ups");
        JSONObject r0 = rosters.optJSONObject(0), r1 = rosters.optJSONObject(1);
        // Home team first.
        if ("away".equals(r0.optString("homeAway"))) {
            JSONObject t = r0;
            r0 = r1;
            r1 = t;
        }
        final JSONObject[] teams = {r0, r1};
        String n0 = teamLabel(teams[0], m.home);
        String n1 = teamLabel(teams[1], m.away);
        Segmented seg = new Segmented(a, new String[] {n0, n1});
        content.addView(seg, Ui.lp(-1, -2, 0, 0, 0, dp(10)));
        final LinearLayout holder = Ui.column(a);
        content.addView(holder, Ui.lp(-1, -2));
        seg.setListener(i -> {
            holder.removeAllViews();
            holder.addView(lineup(teams[i], rugby));
        });
        seg.select(0, false);
        holder.addView(lineup(teams[0], rugby));
    }

    private static String teamLabel(JSONObject roster, Sports.Side fallback) {
        JSONObject t = roster.optJSONObject("team");
        if (t != null) {
            for (String k : new String[] {"shortDisplayName", "displayName", "name", "abbreviation"}) {
                String v = t.optString(k);
                if (!v.isEmpty()) return v;
            }
        }
        return fallback.shortName.isEmpty() ? fallback.name : fallback.shortName;
    }

    private View lineup(JSONObject team, boolean rugby) {
        LinearLayout k = Ui.column(a);
        k.setPadding(dp(14), dp(8), dp(14), dp(8));
        k.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(18), Ui.STROKE, dp(1)));
        String formation = team.optString("formation");
        if (!formation.isEmpty()) {
            TextView f = Ui.text(a, "Formation  " + formation, 13, Ui.ACCENT, Ui.W_HEAVY);
            f.setPadding(0, dp(6), 0, dp(6));
            k.addView(f);
        }
        JSONArray ps = team.optJSONArray("roster");
        if (ps == null || ps.length() == 0) {
            TextView t = Ui.text(a, "Line-up not announced yet.", 13.5f, Ui.MUTED, Ui.W_MEDIUM);
            t.setPadding(0, dp(10), 0, dp(10));
            k.addView(t);
            return k;
        }
        ArrayList<JSONObject> starters = new ArrayList<>(), bench = new ArrayList<>();
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (p == null) continue;
            boolean start = p.optBoolean("starter");
            // Rugby feeds don't mark starters; the numbers do (1–15 start, 16–23 on the bench).
            if (rugby) {
                int j = 0;
                try {
                    j = Integer.parseInt(p.optString("jersey"));
                } catch (Exception ignored) {
                }
                start = j >= 1 && j <= 15;
            }
            (start ? starters : bench).add(p);
        }
        java.util.Comparator<JSONObject> byNumber = (x, y) -> Integer.compare(num(x), num(y));
        if (rugby) {
            starters.sort(byNumber);
            bench.sort(byNumber);
        }
        sub(k, rugby ? "Starting XV" : "Starting XI");
        for (JSONObject p : starters) k.addView(playerRow(p, rugby));
        if (!bench.isEmpty()) {
            sub(k, rugby ? "Replacements" : "Substitutes");
            for (JSONObject p : bench) k.addView(playerRow(p, rugby));
        }
        return k;
    }

    private static int num(JSONObject p) {
        try {
            return Integer.parseInt(p.optString("jersey"));
        } catch (Exception e) {
            return 99;
        }
    }

    private void sub(LinearLayout k, String text) {
        TextView t = Ui.text(a, text, 12, Ui.MUTED, Ui.W_HEAVY);
        t.setPadding(0, dp(10), 0, dp(4));
        k.addView(t);
    }

    private View playerRow(JSONObject p, boolean rugby) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView num = Ui.text(a, p.optString("jersey"), 13, Ui.ACCENT, Ui.W_HEAVY);
        r.addView(num, Ui.lp(dp(32), -2));
        JSONObject ath = p.optJSONObject("athlete");
        String name = ath == null ? "" : ath.optString("displayName");
        StringBuilder marks = new StringBuilder();
        if (p.optBoolean("captain")) marks.append("  (C)");
        if (p.optBoolean("subbedOut")) marks.append("  ▼");
        if (p.optBoolean("subbedIn")) marks.append("  ▲");
        TextView n = Ui.text(a, name + marks, 14, Ui.TEXT, Ui.W_BOLD);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, Ui.weight(1));
        JSONObject pos = p.optJSONObject("position");
        String ps = pos == null ? "" : rugby ? pos.optString("displayName") : pos.optString("abbreviation");
        TextView po = Ui.text(a, ps, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        r.addView(po, Ui.lp(-2, -2, dp(8), 0, 0, 0));
        return r;
    }

    // ---- Soccer -----------------------------------------------------------------------------

    private void soccer(JSONObject d) {
        JSONArray ke = d.optJSONArray("keyEvents");
        String homeId = teamId(d, true);
        LinearLayout tl = null;
        for (int i = 0; ke != null && i < ke.length(); i++) {
            JSONObject e = ke.optJSONObject(i);
            if (e == null) continue;
            String type = e.optJSONObject("type") != null ? e.optJSONObject("type").optString("text") : "";
            String lower = type.toLowerCase(Locale.ROOT);
            String icon;
            if (lower.contains("own goal")) icon = "⚽ (OG)";
            else if (lower.contains("goal") || lower.contains("penalty - scored")) icon = "⚽";
            else if (lower.contains("red card")) icon = "🟥";
            else if (lower.contains("yellow card")) icon = "🟨";
            else continue;
            if (tl == null) {
                section("Goals & cards");
                tl = card();
            }
            String who = "";
            JSONArray parts = e.optJSONArray("participants");
            if (parts != null && parts.length() > 0 && parts.optJSONObject(0).optJSONObject("athlete") != null) {
                who = parts.optJSONObject(0).optJSONObject("athlete").optString("displayName");
            }
            if (lower.contains("penalty")) who += " (pen)";
            String team = e.optJSONObject("team") != null ? e.optJSONObject("team").optString("id") : "";
            String min = e.optJSONObject("clock") != null ? e.optJSONObject("clock").optString("displayValue") : "";
            timelineRow(tl, min, icon, who, team.equals(homeId));
        }
        lineupToggle(d.optJSONArray("rosters"), false);
        stats(d);
        info(d);
    }

    private String teamId(JSONObject d, boolean home) {
        JSONObject h = d.optJSONObject("header");
        JSONArray comps = h == null ? null : h.optJSONArray("competitions");
        JSONArray cs = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0).optJSONArray("competitors");
        for (int i = 0; cs != null && i < cs.length(); i++) {
            JSONObject c = cs.optJSONObject(i);
            if (("home".equals(c.optString("homeAway"))) == home) return c.optJSONObject("team") != null ? c.optJSONObject("team").optString("id") : c.optString("id");
        }
        return "";
    }

    private void stats(JSONObject d) {
        JSONObject box = d.optJSONObject("boxscore");
        JSONArray teams = box == null ? null : box.optJSONArray("teams");
        if (teams == null || teams.length() < 2) return;
        JSONObject t0 = teams.optJSONObject(0), t1 = teams.optJSONObject(1);
        if ("away".equals(t0.optString("homeAway"))) {
            JSONObject t = t0;
            t0 = t1;
            t1 = t;
        }
        String[][] want = {{"possessionPct", "Possession", "%"}, {"totalShots", "Shots", ""}, {"shotsOnTarget", "On target", ""},
            {"wonCorners", "Corners", ""}, {"foulsCommitted", "Fouls", ""}, {"offsides", "Offsides", ""},
            {"yellowCards", "Yellow cards", ""}, {"redCards", "Red cards", ""}, {"saves", "Saves", ""}};
        LinearLayout k = null;
        for (String[] w : want) {
            String v0 = stat(t0, w[0]), v1 = stat(t1, w[0]);
            if (v0.isEmpty() && v1.isEmpty()) continue;
            if (k == null) {
                section("Match stats");
                k = card();
            }
            statRow(k, w[1], v0, v1, w[2]);
        }
    }

    private static String stat(JSONObject team, String name) {
        JSONArray st = team.optJSONArray("statistics");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject x = st.optJSONObject(i);
            if (x != null && name.equals(x.optString("name"))) return x.optString("displayValue");
        }
        return "";
    }

    private void statRow(LinearLayout k, String label, String v0, String v1, String unit) {
        LinearLayout c = Ui.column(a);
        c.setPadding(0, dp(7), 0, dp(7));
        LinearLayout r = Ui.row(a);
        TextView l = Ui.text(a, v0 + unit, 14, Ui.TEXT, Ui.W_HEAVY);
        TextView mid = Ui.text(a, label, 12.5f, Ui.MUTED, Ui.W_BOLD);
        mid.setGravity(Gravity.CENTER);
        TextView rr = Ui.text(a, v1 + unit, 14, Ui.TEXT, Ui.W_HEAVY);
        rr.setGravity(Gravity.END);
        r.addView(l, Ui.lp(dp(60), -2));
        r.addView(mid, Ui.weight(1));
        r.addView(rr, Ui.lp(dp(60), -2));
        c.addView(r);
        float a0 = parse(v0), a1 = parse(v1);
        if (a0 + a1 > 0) {
            LinearLayout bar = Ui.row(a);
            View b0 = new View(a);
            b0.setBackground(Ui.round(Ui.ACCENT, dp(2)));
            View b1 = new View(a);
            b1.setBackground(Ui.round(Ui.SURFACE3, dp(2)));
            LinearLayout.LayoutParams p0 = new LinearLayout.LayoutParams(0, dp(4), a0);
            p0.setMargins(0, 0, dp(2), 0);
            bar.addView(b0, p0);
            bar.addView(b1, new LinearLayout.LayoutParams(0, dp(4), a1));
            c.addView(bar, Ui.lp(-1, -2, 0, dp(6), 0, 0));
        }
        k.addView(c);
    }

    private static float parse(String v) {
        try {
            return Float.parseFloat(v.replace("%", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void info(JSONObject d) {
        JSONObject gi = d.optJSONObject("gameInfo");
        if (gi == null) return;
        StringBuilder sb = new StringBuilder();
        int att = gi.optInt("attendance");
        if (att > 0) sb.append("Attendance ").append(String.format(Locale.ROOT, "%,d", att).replace(',', ' '));
        JSONArray off = gi.optJSONArray("officials");
        if (off != null && off.length() > 0 && off.optJSONObject(0) != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Referee ").append(off.optJSONObject(0).optString("displayName"));
        }
        if (sb.length() == 0) return;
        TextView t = Ui.text(a, sb, 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        t.setLineSpacing(0, 1.35f);
        content.addView(t, Ui.lp(-1, -2, dp(2), dp(16), 0, 0));
    }

    // ---- Rugby ------------------------------------------------------------------------------

    private void rugby(JSONObject d) {
        JSONObject h = d.optJSONObject("header");
        JSONArray comps = h == null ? null : h.optJSONArray("competitions");
        JSONObject comp = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0);
        JSONArray details = comp == null ? null : comp.optJSONArray("details");
        String homeId = teamId(d, true);
        if (details != null && details.length() > 0) {
            LinearLayout k = null;
            for (int i = 0; i < details.length(); i++) {
                JSONObject e = details.optJSONObject(i);
                if (e == null) continue;
                String type = e.optJSONObject("type") != null ? e.optJSONObject("type").optString("text") : "";
                String t = type.toLowerCase(Locale.ROOT);
                // Scores and cards only; the feed also lists every substitution.
                if (!(t.contains("try") || t.contains("conversion") || t.contains("penalty") || t.contains("drop") || t.contains("card"))) continue;
                if (t.contains("miss")) continue;
                String label = t.contains("penalty try") ? "Penalty try" : t.contains("try") ? "Try" : t.contains("conversion") ? "Con"
                        : t.contains("drop") ? "Drop goal" : t.contains("penalty") ? "Pen" : t.contains("card") ? (t.contains("red") ? "🟥" : "🟨") : type;
                String who = "";
                JSONArray parts = e.optJSONArray("participants");
                if (parts != null && parts.length() > 0 && parts.optJSONObject(0) != null && parts.optJSONObject(0).optJSONObject("athlete") != null) {
                    who = parts.optJSONObject(0).optJSONObject("athlete").optString("displayName");
                }
                String score = e.has("homeScore") ? "  (" + e.optInt("homeScore") + "–" + e.optInt("awayScore") + ")" : "";
                String team = e.optJSONObject("team") != null ? e.optJSONObject("team").optString("id") : "";
                String min = e.optJSONObject("clock") != null ? e.optJSONObject("clock").optString("displayValue") : "";
                String text = (who.isEmpty() ? label : label + " · " + who) + score;
                if (k == null) {
                    section("Scoring");
                    k = card();
                }
                timelineRow(k, min, "", text, team.equals(homeId));
            }
        }
        lineupToggle(d.optJSONArray("rosters"), true);
        info(d);
    }

    // ---- Cricket ----------------------------------------------------------------------------

    private void cricket(JSONObject d) {
        JSONArray cards = d.optJSONArray("matchcards");
        if (cards != null && cards.length() > 0) {
            // Group by innings: batting card, then the bowling card for the same innings.
            for (int inn = 1; inn <= 4; inn++) {
                JSONObject bat = null, bowl = null;
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject c = cards.optJSONObject(i);
                    if (c == null || !String.valueOf(inn).equals(c.optString("inningsNumber"))) continue;
                    if ("Batting".equalsIgnoreCase(c.optString("headline"))) bat = c;
                    else if ("Bowling".equalsIgnoreCase(c.optString("headline"))) bowl = c;
                }
                if (bat == null) continue;
                section(ordinal(inn) + " innings · " + bat.optString("teamName"));
                LinearLayout k = card();
                header(k, "Batter", "R", "B");
                JSONArray rows = bat.optJSONArray("playerDetails");
                for (int i = 0; rows != null && i < rows.length(); i++) {
                    JSONObject p = rows.optJSONObject(i);
                    if (p == null) continue;
                    String dis = p.optString("dismissal");
                    batRow(k, p.optString("playerName"), dis, p.optString("runs"), p.optString("ballsFaced"));
                }
                totalRow(k, "Extras " + bat.optString("extras"), "");
                totalRow(k, "Total", bat.optString("runs") + " " + bat.optString("total"));
                if (bowl != null) {
                    LinearLayout b = card();
                    ((LinearLayout.LayoutParams) b.getLayoutParams()).topMargin = dp(8);
                    header(b, "Bowler", "O-M-R", "W");
                    JSONArray br = bowl.optJSONArray("playerDetails");
                    for (int i = 0; br != null && i < br.length(); i++) {
                        JSONObject p = br.optJSONObject(i);
                        if (p == null) continue;
                        cells(b, p.optString("playerName"), p.optString("overs") + "-" + p.optString("maidens") + "-" + p.optString("conceded"),
                                p.optString("wickets"), false);
                    }
                }
            }
        }
        JSONArray rosters = d.optJSONArray("rosters");
        if (rosters != null && rosters.length() >= 2) {
            section("Playing XIs");
            for (int t = 0; t < 2; t++) {
                JSONObject team = rosters.optJSONObject(t);
                if (team == null) continue;
                LinearLayout k = card();
                if (t == 1) ((LinearLayout.LayoutParams) k.getLayoutParams()).topMargin = dp(8);
                TextView tn = Ui.text(a, team.optJSONObject("team") != null ? team.optJSONObject("team").optString("displayName") : "", 13,
                        Ui.ACCENT, Ui.W_HEAVY);
                tn.setPadding(0, dp(6), 0, dp(4));
                k.addView(tn);
                StringBuilder sb = new StringBuilder();
                JSONArray ps = team.optJSONArray("roster");
                for (int i = 0; ps != null && i < ps.length(); i++) {
                    JSONObject p = ps.optJSONObject(i);
                    if (p == null || p.optJSONObject("athlete") == null) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(p.optJSONObject("athlete").optString("displayName"));
                    if (p.optBoolean("captain")) sb.append(" (c)");
                    JSONObject pos = p.optJSONObject("position");
                    if (pos != null && "WK".equals(pos.optString("abbreviation"))) sb.append(" (wk)");
                }
                TextView xi = Ui.text(a, sb, 13.5f, Ui.TEXT2, Ui.W_MEDIUM);
                xi.setLineSpacing(0, 1.4f);
                xi.setPadding(0, 0, 0, dp(8));
                k.addView(xi);
            }
        }
    }

    private static String ordinal(int n) {
        return n == 1 ? "1st" : n == 2 ? "2nd" : n == 3 ? "3rd" : n + "th";
    }

    private void header(LinearLayout k, String c1, String c2, String c3) {
        cells(k, c1, c2, c3, true);
    }

    private void cells(LinearLayout k, String c1, String c2, String c3, boolean head) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, dp(head ? 6 : 5), 0, dp(head ? 6 : 5));
        int col = head ? Ui.MUTED : Ui.TEXT;
        int w = head ? Ui.W_HEAVY : Ui.W_BOLD;
        TextView a1 = Ui.text(a, c1, head ? 12 : 14, col, w);
        a1.setSingleLine(true);
        a1.setEllipsize(TextUtils.TruncateAt.END);
        TextView a2 = Ui.text(a, c2, head ? 12 : 14, head ? Ui.MUTED : Ui.TEXT2, w);
        a2.setGravity(Gravity.END);
        TextView a3 = Ui.text(a, c3, head ? 12 : 14, head ? Ui.MUTED : Ui.ACCENT, Ui.W_HEAVY);
        a3.setGravity(Gravity.END);
        r.addView(a1, Ui.weight(1));
        r.addView(a2, Ui.lp(dp(72), -2));
        r.addView(a3, Ui.lp(dp(40), -2));
        k.addView(r);
    }

    private void batRow(LinearLayout k, String name, String dismissal, String runs, String balls) {
        LinearLayout c = Ui.column(a);
        cells(c, name, runs, balls, false);
        if (!dismissal.isEmpty()) {
            TextView d = Ui.text(a, dismissal, 11.5f, Ui.MUTED, Ui.W_MEDIUM);
            d.setPadding(0, 0, 0, dp(3));
            c.addView(d);
        }
        // Swap colours: runs matter more than balls in a batting card.
        LinearLayout r = (LinearLayout) c.getChildAt(0);
        ((TextView) r.getChildAt(1)).setTextColor(Ui.ACCENT);
        ((TextView) r.getChildAt(2)).setTextColor(Ui.MUTED);
        k.addView(c);
    }

    private void totalRow(LinearLayout k, String label, String value) {
        LinearLayout r = Ui.row(a);
        r.setPadding(0, dp(6), 0, dp(4));
        TextView l = Ui.text(a, label, 13, Ui.MUTED, Ui.W_BOLD);
        r.addView(l, Ui.weight(1));
        TextView v = Ui.text(a, value, 13.5f, Ui.TEXT, Ui.W_HEAVY);
        r.addView(v);
        k.addView(r);
    }

    // ---- Formula 1 --------------------------------------------------------------------------

    private void f1() {
        loading();
        new Thread(() -> {
            JSONObject res = null, quali = null, stand = null, race = null;
            String base = Sports.JOLPICA + m.season + "/" + m.round;
            try {
                if (m.state == Sports.PRE) {
                    race = first(Sports.getJson(base + ".json"));
                } else {
                    res = first(Sports.getJson(base + "/results.json"));
                    quali = first(Sports.getJson(base + "/qualifying.json"));
                }
                JSONArray lists = Sports.getJson(Sports.JOLPICA + m.season + "/driverStandings.json").getJSONObject("MRData")
                        .getJSONObject("StandingsTable").getJSONArray("StandingsLists");
                if (lists.length() > 0) stand = lists.getJSONObject(0);
            } catch (Exception ignored) {
            }
            final JSONObject fRes = res, fQuali = quali, fStand = stand, fRace = race;
            main.post(() -> {
                content.removeAllViews();
                if (fRace != null) schedule(fRace);
                if (fRes != null) classification(fRes);
                if (fQuali != null) qualifying(fQuali);
                if (fStand != null) standings(fStand);
                if (content.getChildCount() == 0) note("Couldn't load the details. Check your connection.");
            });
        }, "f1").start();
    }

    private static JSONObject first(JSONObject d) throws org.json.JSONException {
        JSONArray races = d.getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
        return races.length() > 0 ? races.getJSONObject(0) : null;
    }

    private void schedule(JSONObject race) {
        section("Race weekend (your time)");
        LinearLayout k = card();
        String[][] sessions = {{"FirstPractice", "Practice 1"}, {"SecondPractice", "Practice 2"}, {"ThirdPractice", "Practice 3"},
            {"SprintQualifying", "Sprint qualifying"}, {"Sprint", "Sprint"}, {"Qualifying", "Qualifying"}};
        ArrayList<Object[]> rows = new ArrayList<>();
        for (String[] ss : sessions) {
            JSONObject x = race.optJSONObject(ss[0]);
            if (x != null) rows.add(new Object[] {Sports.parseTime(x.optString("date") + "T" + x.optString("time", "12:00:00Z")), ss[1]});
        }
        rows.add(new Object[] {Sports.jolpicaTime(race), "Race"});
        rows.sort((x, y) -> Long.compare((Long) x[0], (Long) y[0]));
        for (Object[] r : rows) {
            Sports.Line l = new Sports.Line("", (String) r[1], new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format((Long) r[0]), false);
            k.addView(lineRow(l));
        }
    }

    private void classification(JSONObject race) {
        JSONArray rs = race.optJSONArray("Results");
        if (rs == null || rs.length() == 0) return;
        section("Race result");
        LinearLayout k = card();
        for (int i = 0; i < rs.length(); i++) {
            JSONObject x = rs.optJSONObject(i);
            if (x == null) continue;
            Sports.Line l = Sports.f1Line(x, rs.optJSONObject(0).optInt("laps"));
            String pts = x.optString("points");
            if (!"0".equals(pts)) l.value = l.value + "   " + pts + " pts";
            k.addView(lineRow(l));
        }
    }

    private void qualifying(JSONObject race) {
        JSONArray q = race.optJSONArray("QualifyingResults");
        if (q == null || q.length() == 0) return;
        section("Qualifying");
        LinearLayout k = card();
        for (int i = 0; i < Math.min(10, q.length()); i++) {
            JSONObject x = q.optJSONObject(i);
            JSONObject d = x.optJSONObject("Driver");
            String best = !x.optString("Q3").isEmpty() ? x.optString("Q3") : !x.optString("Q2").isEmpty() ? x.optString("Q2") : x.optString("Q1");
            k.addView(lineRow(new Sports.Line("P" + x.optString("position"),
                    d == null ? "" : d.optString("givenName") + " " + d.optString("familyName"), best, false)));
        }
    }

    private void standings(JSONObject list) {
        JSONArray ds = list.optJSONArray("DriverStandings");
        if (ds == null || ds.length() == 0) return;
        section("Drivers' championship");
        LinearLayout k = card();
        for (int i = 0; i < Math.min(10, ds.length()); i++) {
            JSONObject x = ds.optJSONObject(i);
            JSONObject d = x.optJSONObject("Driver");
            JSONArray cs = x.optJSONArray("Constructors");
            String team = cs != null && cs.length() > 0 ? cs.optJSONObject(0).optString("name") : "";
            k.addView(lineRow(new Sports.Line(x.optString("position"),
                    (d == null ? "" : d.optString("givenName") + " " + d.optString("familyName")) + (team.isEmpty() ? "" : "  ·  " + team),
                    x.optString("points") + " pts", false)));
        }
    }

    // ---- Golf & UFC -------------------------------------------------------------------------

    private void golf() {
        if (m.board.isEmpty()) {
            note(m.state == Sports.PRE ? "The leaderboard opens when play starts." : "No leaderboard yet.");
            return;
        }
        int saCount = 0;
        for (Sports.Line l : m.board) if (l.sa) saCount++;
        if (saCount > 0) {
            section("South Africans");
            LinearLayout k = card();
            for (Sports.Line l : m.board) if (l.sa) k.addView(lineRow(l));
        }
        section("Leaderboard");
        LinearLayout k = card();
        for (int i = 0; i < Math.min(30, m.board.size()); i++) k.addView(lineRow(m.board.get(i)));
        if (m.board.size() > 30) {
            TextView more = Ui.text(a, (m.board.size() - 30) + " more players", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
            more.setPadding(0, dp(8), 0, dp(6));
            k.addView(more);
        }
    }

    private void ufc() {
        if (m.board.isEmpty()) {
            note("The fight card hasn't been published yet.");
            return;
        }
        section(m.state == Sports.POST ? "Results" : "Fight card");
        LinearLayout k = card();
        for (Sports.Line l : m.board) k.addView(lineRow(l));
    }

    private View lineRow(Sports.Line l) {
        return SportView.lineRow(a, l, true);
    }
}
