package za.saradio.app;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the widgets show when the app isn't open: a snapshot of the scores and the text of stories
 * read in the story widget. Both are small JSON files in the app's private storage.
 */
final class WidgetStore {
    private WidgetStore() {}

    private static final String SPORT = "widget_sport.json";
    private static final String STORIES = "widget_stories.json";
    private static final int MAX_STORIES = 30;

    // ---- Scores -----------------------------------------------------------------------------

    /** The scores snapshot: when it was loaded and the South African-focus matches. */
    static final class Scores {
        long at;
        final List<Sports.Match> matches = new ArrayList<>();
    }

    /** Saves the matches the scoreboard shows (the SA focus selection). */
    static synchronized void saveScores(Context c, List<Sports.Match> all, long at) {
        try {
            JSONArray arr = new JSONArray();
            for (Sports.Match m : all) {
                if (!Sports.matchesFilter(m, "sa")) continue;
                arr.put(toJson(m));
            }
            JSONObject o = new JSONObject().put("at", at).put("matches", arr);
            write(c, SPORT, o.toString());
        } catch (Exception ignored) {
        }
    }

    static synchronized Scores loadScores(Context c) {
        Scores s = new Scores();
        try {
            String raw = read(c, SPORT);
            if (raw == null) return s;
            JSONObject o = new JSONObject(raw);
            s.at = o.optLong("at");
            JSONArray arr = o.optJSONArray("matches");
            for (int i = 0; arr != null && i < arr.length(); i++) s.matches.add(fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return s;
    }

    static JSONObject toJson(Sports.Match m) throws org.json.JSONException {
        JSONObject o = new JSONObject()
                .put("id", m.id).put("sport", m.sport).put("feed", m.feed).put("comp", m.compName)
                .put("time", m.time).put("end", m.endTime).put("state", m.state).put("status", m.status)
                .put("summary", m.summary).put("venue", m.venue).put("title", m.title).put("sa", m.sa);
        if (m.home != null) o.put("home", side(m.home));
        if (m.away != null) o.put("away", side(m.away));
        if (!m.lines.isEmpty()) {
            JSONArray ls = new JSONArray();
            for (int i = 0; i < Math.min(3, m.lines.size()); i++) {
                Sports.Line l = m.lines.get(i);
                ls.put(new JSONObject().put("rank", l.rank).put("name", l.name).put("value", l.value).put("sa", l.sa));
            }
            o.put("lines", ls);
        }
        return o;
    }

    static Sports.Match fromJson(JSONObject o) throws org.json.JSONException {
        Sports.Match m = new Sports.Match();
        m.id = o.optString("id");
        m.sport = o.optString("sport");
        m.feed = o.optString("feed");
        m.compName = o.optString("comp");
        m.time = o.optLong("time");
        m.endTime = o.optLong("end");
        m.state = o.optInt("state");
        m.status = o.optString("status");
        m.summary = o.optString("summary");
        m.venue = o.optString("venue");
        m.title = o.optString("title");
        m.sa = o.optBoolean("sa");
        if (o.has("home")) m.home = side(o.getJSONObject("home"));
        if (o.has("away")) m.away = side(o.getJSONObject("away"));
        JSONArray ls = o.optJSONArray("lines");
        for (int i = 0; ls != null && i < ls.length(); i++) {
            JSONObject l = ls.getJSONObject(i);
            m.lines.add(new Sports.Line(l.optString("rank"), l.optString("name"), l.optString("value"), l.optBoolean("sa")));
        }
        return m;
    }

    private static JSONObject side(Sports.Side s) throws org.json.JSONException {
        return new JSONObject().put("name", s.name).put("short", s.shortName).put("abbr", s.abbr).put("score", s.score)
                .put("winner", s.winner).put("sa", s.sa);
    }

    private static Sports.Side side(JSONObject o) {
        Sports.Side s = new Sports.Side();
        s.name = o.optString("name");
        s.shortName = o.optString("short");
        s.abbr = o.optString("abbr");
        s.score = o.optString("score");
        s.winner = o.optBoolean("winner");
        s.sa = o.optBoolean("sa");
        return s;
    }

    /** Live games and games starting within the next quarter of an hour. */
    static boolean needsLiveWatch(List<Sports.Match> ms, long now) {
        for (Sports.Match m : ms) {
            if (m.state == Sports.LIVE) return true;
            if (m.state == Sports.PRE && m.time > now - 3 * 3600_000L && m.time < now + 15 * 60_000L) return true;
        }
        return false;
    }

    /**
     * What the scoreboard lists, in order: live games, then what's coming up (soonest first), then
     * results (latest first).
     */
    static List<Sports.Match> board(List<Sports.Match> ms, long now) {
        List<Sports.Match> live = new ArrayList<>(), next = new ArrayList<>(), done = new ArrayList<>();
        for (Sports.Match m : ms) {
            if (m.state == Sports.LIVE) live.add(m);
            else if (m.state == Sports.PRE && m.time >= now - 3 * 3600_000L) next.add(m);
            else if (m.state == Sports.POST || m.state == Sports.OFF) done.add(m);
        }
        Collections.sort(live, (x, y) -> Long.compare(x.time, y.time));
        Collections.sort(next, (x, y) -> Long.compare(x.time, y.time));
        Collections.sort(done, (x, y) -> Long.compare(Math.max(y.time, y.endTime), Math.max(x.time, x.endTime)));
        List<Sports.Match> out = new ArrayList<>(live);
        out.addAll(next.subList(0, Math.min(12, next.size())));
        out.addAll(done.subList(0, Math.min(12, done.size())));
        return out;
    }

    // ---- Story text -------------------------------------------------------------------------

    static synchronized List<PlainText.Block> story(Context c, String link) {
        try {
            String raw = read(c, STORIES);
            if (raw == null) return null;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!link.equals(o.optString("link"))) continue;
                List<PlainText.Block> out = new ArrayList<>();
                JSONArray b = o.getJSONArray("blocks");
                for (int j = 0; j < b.length(); j++) {
                    JSONObject x = b.getJSONObject(j);
                    out.add(new PlainText.Block(x.optString("t"), x.optBoolean("h")));
                }
                return out;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static synchronized void putStory(Context c, String link, List<PlainText.Block> blocks) {
        try {
            String raw = read(c, STORIES);
            JSONArray old = raw == null ? new JSONArray() : new JSONArray(raw);
            JSONArray arr = new JSONArray();
            JSONArray b = new JSONArray();
            for (PlainText.Block x : blocks) b.put(new JSONObject().put("t", x.text).put("h", x.heading));
            arr.put(new JSONObject().put("link", link).put("blocks", b));
            for (int i = 0; i < old.length() && arr.length() < MAX_STORIES; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!link.equals(o.optString("link"))) arr.put(o);
            }
            write(c, STORIES, arr.toString());
        } catch (Exception ignored) {
        }
    }

    // ---- Files ------------------------------------------------------------------------------

    private static String read(Context c, String name) {
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            return new String(StationRepo.readAll(in, 8 << 20), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(Context c, String name, String data) {
        File f = new File(c.getFilesDir(), name);
        File tmp = new File(c.getFilesDir(), name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data.getBytes("UTF-8"));
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }
}
