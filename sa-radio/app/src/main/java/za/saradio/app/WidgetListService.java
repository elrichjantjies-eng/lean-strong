package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fills the scrolling lists in the headlines, story and scoreboard widgets. */
public class WidgetListService extends RemoteViewsService {
    static final String EXTRA_KIND = "kind";
    static final String KIND_HEADLINES = "headlines";
    static final String KIND_STORY = "story";
    static final String KIND_SPORT = "sport";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        String kind = intent.getStringExtra(EXTRA_KIND);
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        Context c = getApplicationContext();
        if (KIND_STORY.equals(kind)) return new StoryRows(c, id);
        if (KIND_SPORT.equals(kind)) return new SportRows(c);
        return new HeadlineRows(c);
    }

    /** Shared bits of a list: no stable ids needed, rows built fresh on each change. */
    abstract static class Rows implements RemoteViewsFactory {
        final Context c;

        Rows(Context c) {
            this.c = c;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDestroy() {}

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }

    // ---- Headlines --------------------------------------------------------------------------

    static final class HeadlineRows extends Rows {
        private List<NewsRepo.Item> items = new ArrayList<>();

        HeadlineRows(Context c) {
            super(c);
        }

        @Override
        public void onDataSetChanged() {
            List<NewsRepo.Item> all = NewsRepo.local(NewsRepo.load(c));
            items = all.subList(0, Math.min(40, all.size()));
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.widget_row_headline);
            if (i >= items.size()) return r;
            NewsRepo.Item it = items.get(i);
            String ago = NewsRepo.ago(it.time);
            r.setTextViewText(R.id.w_meta, it.source.toUpperCase(Locale.ROOT) + (ago.isEmpty() ? "" : "  ·  " + ago.toUpperCase(Locale.ROOT)));
            r.setTextViewText(R.id.w_title, it.title);
            r.setContentDescription(R.id.w_row, it.title + ". " + it.source + (ago.isEmpty() ? "" : ", " + ago));
            r.setOnClickFillInIntent(R.id.w_row, new Intent()
                    .putExtra("link", it.link).putExtra("title", it.title).putExtra("source", it.source)
                    .putExtra("summary", it.summary).putExtra("image", it.image).putExtra("time", it.time));
            return r;
        }
    }

    // ---- Story text -------------------------------------------------------------------------

    static final class StoryRows extends Rows {
        private final int widgetId;
        /** Each row: {layout, text}. */
        private final List<Object[]> rows = new ArrayList<>();
        private String link = "";

        StoryRows(Context c, int widgetId) {
            super(c);
            this.widgetId = widgetId;
        }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            List<NewsRepo.Item> items = NewsRepo.local(NewsRepo.load(c));
            if (items.isEmpty()) return;
            NewsRepo.Item it = items.get(StoryWidget.position(c, widgetId, items.size()));
            link = it.link;
            List<PlainText.Block> text = WidgetStore.story(c, it.link);
            if (text != null) {
                for (PlainText.Block b : text) rows.add(new Object[] {b.heading ? R.layout.widget_row_subhead : R.layout.widget_row_para, b.text});
                rows.add(new Object[] {R.layout.widget_row_note, "Ad-free text from " + it.source + ". Tap ↗ above for pictures and the full page."});
                return;
            }
            if (!it.summary.isEmpty()) rows.add(new Object[] {R.layout.widget_row_para, it.summary});
            if (StoryWidget.loading(c, it.link)) {
                rows.add(new Object[] {R.layout.widget_row_note, "Loading the story…"});
            } else {
                if (StoryWidget.failed(c, it.link)) {
                    rows.add(new Object[] {R.layout.widget_row_note,
                        "Couldn't get this story's text here (it may be for subscribers, or not a normal article). Tap ↗ above to open it in SA Radio."});
                }
                rows.add(new Object[] {R.layout.widget_row_action, "READ THE WHOLE STORY HERE"});
            }
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            if (i >= rows.size()) return new RemoteViews(c.getPackageName(), R.layout.widget_row_note);
            int layout = (Integer) rows.get(i)[0];
            RemoteViews r = new RemoteViews(c.getPackageName(), layout);
            r.setTextViewText(R.id.w_text, (String) rows.get(i)[1]);
            if (layout == R.layout.widget_row_action) {
                r.setOnClickFillInIntent(R.id.w_row, new Intent()
                        .putExtra(StoryWidget.EXTRA_DO, "read").putExtra(StoryWidget.EXTRA_LINK, link));
            }
            return r;
        }
    }

    // ---- Scores -----------------------------------------------------------------------------

    static final class SportRows extends Rows {
        /** Rows: a String is a section heading, a Match is a game. */
        private final List<Object> rows = new ArrayList<>();

        SportRows(Context c) {
            super(c);
        }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            long now = System.currentTimeMillis();
            String section = null;
            for (Sports.Match m : WidgetStore.board(WidgetStore.loadScores(c).matches, now)) {
                String s = m.state == Sports.LIVE ? "LIVE NOW" : m.state == Sports.PRE ? "COMING UP" : "RESULTS";
                if (!s.equals(section)) {
                    rows.add(s);
                    section = s;
                }
                rows.add(m);
            }
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            if (i >= rows.size()) return new RemoteViews(c.getPackageName(), R.layout.widget_row_section);
            Object o = rows.get(i);
            if (o instanceof String) {
                RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.widget_row_section);
                r.setTextViewText(R.id.w_text, (String) o);
                return r;
            }
            return match(c, (Sports.Match) o);
        }

        static RemoteViews match(Context c, Sports.Match m) {
            RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.widget_row_match);
            String state;
            int pill;
            if (m.state == Sports.LIVE) {
                state = m.status.isEmpty() ? "LIVE" : "LIVE\n" + shortStatus(m.status);
                pill = R.drawable.w_pill_live;
                r.setTextColor(R.id.w_state, 0xFFFFFFFF);
            } else if (m.state == Sports.PRE) {
                state = new SimpleDateFormat("EEE", Locale.ENGLISH).format(m.time).toUpperCase(Locale.ROOT) + "\n" + Sports.clock(m.time);
                pill = R.drawable.w_pill_pre;
                r.setTextColor(R.id.w_state, Ui.INK);
            } else {
                state = m.state == Sports.OFF ? "OFF" : "FT";
                pill = R.drawable.w_pill_ft;
                r.setTextColor(R.id.w_state, Ui.INK);
            }
            r.setTextViewText(R.id.w_state, state);
            r.setInt(R.id.w_state, "setBackgroundResource", pill);
            String comp = m.compName.toUpperCase(Locale.ROOT);
            if (m.isEvent()) {
                r.setTextViewText(R.id.w_comp, comp);
                r.setTextViewText(R.id.w_home, m.title);
                r.setTextViewText(R.id.w_hs, "");
                if (!m.lines.isEmpty()) {
                    Sports.Line l = m.lines.get(0);
                    r.setViewVisibility(R.id.w_away_row, View.VISIBLE);
                    r.setTextViewText(R.id.w_away, (l.rank.isEmpty() ? "" : l.rank + "  ") + l.name);
                    r.setTextViewText(R.id.w_as, l.value);
                } else {
                    r.setViewVisibility(R.id.w_away_row, View.GONE);
                }
            } else {
                r.setTextViewText(R.id.w_comp, comp + (m.state == Sports.PRE && !m.venue.isEmpty() ? " · " + m.venue.toUpperCase(Locale.ROOT) : ""));
                side(r, R.id.w_home, R.id.w_hs, m.home, m);
                side(r, R.id.w_away, R.id.w_as, m.away, m);
                r.setViewVisibility(R.id.w_away_row, View.VISIBLE);
            }
            r.setContentDescription(R.id.w_row, SportView.describe(m));
            r.setOnClickFillInIntent(R.id.w_row, new Intent().putExtra("match", m.key()));
            return r;
        }

        private static void side(RemoteViews r, int nameId, int scoreId, Sports.Side s, Sports.Match m) {
            if (s == null) return;
            String n = s.shortName.isEmpty() ? s.name : s.shortName;
            boolean lost = m.state == Sports.POST && !s.winner && (m.home != null && m.home.winner || m.away != null && m.away.winner);
            r.setTextViewText(nameId, n);
            r.setTextColor(nameId, s.sa ? Ui.ACCENT : lost ? Ui.MUTED : Ui.INK);
            r.setTextViewText(scoreId, s.score);
            r.setTextColor(scoreId, lost ? Ui.MUTED : Ui.INK);
        }

        private static String shortStatus(String s) {
            return s.length() > 9 ? s.substring(0, 9) : s;
        }
    }
}
