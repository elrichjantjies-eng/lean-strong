package za.saradio.app;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/**
 * Renders the home-screen widgets: with live data and logos into build/screenshots (to check by
 * eye), and with made-up sample content and no logos into build/widget-previews (the pictures in
 * the launcher's widget list). Runs with {@code ./gradlew test -Plive=true}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class WidgetsTest {
    private Context c;
    private List<Station> stations;

    @Before
    public void seed() throws Exception {
        Assume.assumeTrue("widget renders need -Plive=true", Boolean.getBoolean("live"));
        c = RuntimeEnvironment.getApplication();
        String json = LogicTest.fixture("stations_fixture.json");
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), "stations.json"))) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Prefs.get(c).edit().clear().putLong("stationsAt", System.currentTimeMillis()).apply();
        stations = StationRepo.parse(json);
        Tuner.setStations(stations);
        Ui.init(c);
        ImageLoader.init(c);
    }

    private Station named(String n) {
        for (Station s : stations) if (s.name.equalsIgnoreCase(n)) return s;
        for (Station s : stations) if (s.name.toLowerCase().startsWith(n.toLowerCase())) return s;
        return stations.get(0);
    }

    private void tuneTo(Station s, String song) {
        RadioService.current = s;
        RadioService.state = RadioService.PLAYING;
        RadioService.songTitle = song;
    }

    private static void idle(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /** Draws a widget at a size, filling any list from its factory. */
    private Bitmap render(RemoteViews rv, RemoteViewsService.RemoteViewsFactory rows, int wDp, int hDp, boolean wallpaper) {
        float d = c.getResources().getDisplayMetrics().density;
        int pad = wallpaper ? Math.round(14 * d) : 0;
        FrameLayout host = new FrameLayout(c);
        View v = rv.apply(c, host);
        host.addView(v, new FrameLayout.LayoutParams(Math.round(wDp * d), Math.round(hDp * d)));
        if (rows != null) {
            rows.onCreate();
            rows.onDataSetChanged();
            ListView list = v.findViewById(R.id.w_list);
            list.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return rows.getCount();
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
                public View getView(int i, View convert, ViewGroup parent) {
                    return rows.getViewAt(i).apply(c, parent);
                }
            });
            v.findViewById(R.id.w_empty).setVisibility(rows.getCount() == 0 ? View.VISIBLE : View.GONE);
        }
        int w = Math.round(wDp * d) + pad * 2, h = Math.round(hDp * d) + pad * 2;
        host.measure(View.MeasureSpec.makeMeasureSpec(Math.round(wDp * d), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(hDp * d), View.MeasureSpec.EXACTLY));
        host.layout(0, 0, Math.round(wDp * d), Math.round(hDp * d));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        if (wallpaper) {
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF3D6B8C, 0xFF1D3446, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
        }
        cv.translate(pad, pad);
        host.draw(cv);
        return b;
    }

    private static void save(Bitmap b, String dir, String name) throws Exception {
        File d = new File(dir);
        assertTrue(d.isDirectory() || d.mkdirs());
        try (FileOutputStream o = new FileOutputStream(new File(d, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    private static String shots() {
        return System.getProperty("screenshots.dir", "build/screenshots");
    }

    private static String previews() {
        return new File(shots()).getParent() + "/widget-previews";
    }

    @Test
    public void liveWidgets() throws Exception {
        // Favourites for the presets, and logos fetched the way the app fetches them.
        List<Station> favs = new ArrayList<>();
        for (String n : new String[] {"Jacaranda FM", "KFM 94.5", "Metro FM", "Ukhozi FM", "5FM"}) {
            Station s = named(n);
            boolean dup = false;
            for (Station f : favs) dup |= f.key().equals(s.key());
            if (!dup) favs.add(s);
        }
        Prefs.saveFavourites(c, favs);
        tuneTo(favs.get(0), "Tyla – Water");
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.setTab(MainActivity.TAB_FAV);
        idle(9000);

        save(render(RadioWidget.build(c, 1, 340, 170), null, 340, 170, true), shots(), "30_widget_radio");
        Tuner.setBand(c, Tuner.FREQ);
        tuneTo(named("KFM 94.5"), "");
        save(render(RadioWidget.build(c, 1, 300, 120), null, 300, 120, true), shots(), "31_widget_radio_small_mhz");
        tuneTo(favs.get(0), "Tyla – Water");
        save(render(PresetsWidget.build(c, 340, 78), null, 340, 78, true), shots(), "32_widget_presets");

        List<NewsRepo.Item> items = NewsRepo.fetchAll();
        assertTrue("live feeds returned " + items.size(), items.size() > 20);
        NewsRepo.save(c, items);
        save(render(HeadlinesWidget.build(c, 1), new WidgetListService.HeadlineRows(c), 340, 300, true), shots(), "33_widget_headlines");

        // The story widget before and after "read the whole story here".
        int pick = 0;
        for (int i = 0; i < Math.min(12, items.size()); i++) {
            if (!items.get(i).summary.isEmpty()) {
                pick = i;
                break;
            }
        }
        Prefs.get(c).edit().putInt("story_1", pick).apply();
        save(render(StoryWidget.build(c, 1), new WidgetListService.StoryRows(c, 1), 340, 420, true), shots(), "34_widget_story");
        int loaded = -1;
        for (int i = pick; i < Math.min(pick + 8, items.size()) && loaded < 0; i++) {
            String[] page = Extractor.fetch(items.get(i).link);
            List<PlainText.Block> blocks = PlainText.extract(page[0]);
            if (blocks.size() >= 3) {
                WidgetStore.putStory(c, items.get(i).link, blocks);
                loaded = i;
            }
        }
        assertTrue("no story text could be pulled from the first stories", loaded >= 0);
        Prefs.get(c).edit().putInt("story_1", loaded).apply();
        save(render(StoryWidget.build(c, 1), new WidgetListService.StoryRows(c, 1), 340, 420, true), shots(), "35_widget_story_read");

        Sports.Result r = Sports.load(System.currentTimeMillis());
        assertTrue("sports returned nothing", !r.matches.isEmpty());
        WidgetStore.saveScores(c, r.matches, System.currentTimeMillis());
        save(render(SportWidget.build(c, 1), new WidgetListService.SportRows(c), 340, 330, true), shots(), "36_widget_sport");
    }

    /** The launcher's widget-list pictures: made-up sample content, painted letters instead of logos. */
    @Test
    public void previewPictures() throws Exception {
        List<Station> favs = new ArrayList<>();
        for (String n : new String[] {"Jacaranda FM", "KFM 94.5", "Metro FM", "Ukhozi FM", "5FM", "Kaya FM"}) {
            Station s = named(n);
            s.favicon = "";
            boolean dup = false;
            for (Station f : favs) dup |= f.key().equals(s.key());
            if (!dup) favs.add(s);
        }
        for (Station s : stations) s.favicon = "";
        Tuner.setStations(stations);
        Prefs.saveFavourites(c, favs);
        tuneTo(favs.get(0), "");
        Tuner.setBand(c, Tuner.AZ);
        save(render(RadioWidget.build(c, 1, 320, 160), null, 320, 160, false), previews(), "preview_radio");
        save(render(PresetsWidget.build(c, 320, 74), null, 320, 74, false), previews(), "preview_presets");

        List<NewsRepo.Item> items = new ArrayList<>();
        String[][] sample = {
            {"Sample News", "Taxi rank upgrade promises shade, water and safer crossings for commuters",
                "Work starts in January on a rank used by 40,000 people a day."},
            {"Sample Business", "Fuel price set to drop at midnight as the rand firms", ""},
            {"Sample Sport", "Springboks name unchanged side for the weekend test", ""},
            {"Sample News", "New bus routes link three townships to the city centre", ""},
            {"Sample Tech", "Data prices fall again as networks compete for prepaid users", ""},
        };
        long now = System.currentTimeMillis();
        for (int i = 0; i < sample.length; i++) {
            NewsRepo.Item it = new NewsRepo.Item();
            it.source = sample[i][0];
            it.title = sample[i][1];
            it.summary = sample[i][2];
            it.link = "https://example.org/story/" + i;
            it.time = now - (i + 1) * 23 * 60_000L;
            items.add(it);
        }
        NewsRepo.save(c, items);
        save(render(HeadlinesWidget.build(c, 1), new WidgetListService.HeadlineRows(c), 320, 260, false), previews(), "preview_headlines");
        List<PlainText.Block> text = new ArrayList<>();
        text.add(new PlainText.Block("Commuters at the city's busiest taxi rank will get covered walkways, water points and safer "
                + "crossings, the city said on Monday.", false));
        text.add(new PlainText.Block("Work starts in January and is expected to take eight months, with the rank staying open "
                + "throughout.", false));
        text.add(new PlainText.Block("What changes", true));
        text.add(new PlainText.Block("Marshals will direct pedestrians during construction, and temporary shelters will go up at "
                + "the north entrance.", false));
        WidgetStore.putStory(c, items.get(0).link, text);
        Prefs.get(c).edit().putInt("story_1", 0).apply();
        save(render(StoryWidget.build(c, 1), new WidgetListService.StoryRows(c, 1), 320, 340, false), previews(), "preview_story");

        List<Sports.Match> ms = new ArrayList<>();
        ms.add(game("1", Sports.RUGBY, "Nations Championship", Sports.LIVE, "54'", now - 3000_000L, "France", "17", "South Africa", "24", true));
        ms.add(game("2", Sports.CRICKET, "Test series", Sports.PRE, "", now + 26 * 3600_000L, "South Africa", "", "Bangladesh", "", true));
        ms.add(game("3", Sports.SOCCER, "Betway Premiership", Sports.POST, "FT", now - 20 * 3600_000L, "Orlando Pirates", "2", "Kaizer Chiefs", "1", true));
        ms.add(game("4", Sports.RUGBY, "URC", Sports.POST, "FT", now - 30 * 3600_000L, "Stormers", "31", "Leinster", "27", true));
        WidgetStore.saveScores(c, ms, now);
        save(render(SportWidget.build(c, 1), new WidgetListService.SportRows(c), 320, 300, false), previews(), "preview_sport");
    }

    private static Sports.Match game(String id, String sport, String comp, int state, String status, long time,
            String home, String hs, String away, String as, boolean saAway) {
        Sports.Match m = new Sports.Match();
        m.id = id;
        m.sport = sport;
        m.feed = "sample";
        m.compName = comp;
        m.state = state;
        m.status = status;
        m.time = time;
        m.sa = true;
        m.home = new Sports.Side();
        m.home.name = home;
        m.home.score = hs;
        m.home.sa = !saAway || home.equals("South Africa") || home.contains("Pirates") || home.contains("Stormers");
        m.away = new Sports.Side();
        m.away.name = away;
        m.away.score = as;
        m.away.sa = saAway && (away.equals("South Africa") || away.contains("Chiefs"));
        if (state == Sports.POST) {
            boolean homeWon = Integer.parseInt(hs) > Integer.parseInt(as);
            m.home.winner = homeWon;
            m.away.winner = !homeWon;
        }
        return m;
    }
}
