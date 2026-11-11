package za.saradio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Frequencies, the tuning order, the widget text extractor and the widget stores. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TuningTest {
    private Context c;

    @Before
    public void setUp() {
        c = RuntimeEnvironment.getApplication();
        Prefs.get(c).edit().clear().apply();
        Tuner.setStations(null);
    }

    private static Station st(String name, String tags) {
        Station s = new Station();
        s.name = name;
        s.tags = tags;
        s.uuid = "id-" + name;
        s.url = "https://example.org/" + name.replace(' ', '-');
        return s;
    }

    @Test
    public void frequenciesFromNamesTagsAndTheTable() {
        assertEquals(94.5f, Freq.of(st("KFM 94.5", "")), 0.001f);
        assertEquals(95.9f, Freq.of(st("Kaya 959", "")), 0.001f);
        assertEquals(102.7f, Freq.of(st("Hot 1027", "")), 0.001f);
        assertEquals(94.7f, Freq.of(st("947", "")), 0.001f);
        assertEquals(98.7f, Freq.of(st("Power 98.7", "news,talk")), 0.001f);
        assertEquals(107.5f, Freq.of(st("CCFM", "christian,fm 107,5")), 0.001f);
        // Not FM frequencies: an AM dial number, an Islamic broadcaster's name, a year.
        assertEquals(92.7f, Freq.of(st("702 Johannesburg", "")), 0.001f);
        assertEquals(0f, Freq.of(st("Radio 786", "")), 0.001f);
        assertEquals(0f, Freq.of(st("Radio 2000", "")), 0.001f);
        assertEquals(0f, Freq.of(st("Cape Talk 567", "")), 0.001f);
        assertEquals(0f, Freq.of(st("OFM 94 & 97 FM", "")), 0.001f);
        // From the table of big stations whose listings leave it out.
        assertEquals(94.2f, Freq.of(st("Jacaranda FM", "")), 0.001f);
        assertEquals(96.4f, Freq.of(st("Metro FM", "")), 0.001f);
        assertEquals(94.0f, Freq.of(st("ECR - East Coast Radio -KwaZulu Natal", "")), 0.001f);
        assertEquals(0f, Freq.of(st("Ukhozi FM S.A", "")), 0.001f);
        assertEquals("94.2", Freq.label(94.2f));
        assertEquals("", Freq.label(0));
    }

    @Test
    public void alphabeticalAndFrequencyOrder() {
        List<Station> all = new ArrayList<>();
        all.add(st("Ukhozi FM", ""));
        all.add(st("KFM 94.5", ""));
        all.add(st("The Beat", ""));
        all.add(st("Jacaranda FM", ""));
        all.add(st("Smile 90.4 FM", ""));
        all.add(st("Bok Radio", ""));
        List<Station> az = Tuner.order(all, Tuner.AZ);
        assertEquals("The Beat", az.get(0).name); // "The" is ignored
        assertEquals("Bok Radio", az.get(1).name);
        assertEquals("Ukhozi FM", az.get(az.size() - 1).name);
        List<Station> mhz = Tuner.order(all, Tuner.FREQ);
        assertEquals("Smile 90.4 FM", mhz.get(0).name);
        assertEquals("Jacaranda FM", mhz.get(1).name); // 94.2
        assertEquals("KFM 94.5", mhz.get(2).name);
        // Stations without a known frequency follow, A to Z.
        assertEquals("The Beat", mhz.get(3).name);
        assertEquals("Bok Radio", mhz.get(4).name);
        assertEquals("Ukhozi FM", mhz.get(5).name);
    }

    @Test
    public void duplicateListingsAppearOnceOnTheDial() {
        List<Station> all = new ArrayList<>();
        all.add(st("Jacaranda FM", ""));
        all.add(st("Jacaranda", ""));
        all.add(st("Hot 102.7 FM", ""));
        all.add(st("Hot 91.9", ""));
        all.add(st("Hot 102.7 FM Rock", ""));
        all.add(st("Ukhozi FM S.A", ""));
        all.add(st("Ukhozi FM", ""));
        List<Station> d = Tuner.order(all, Tuner.AZ);
        assertEquals(5, d.size());
        // A duplicate that was left off the dial still tunes from its twin's place.
        assertEquals(Tuner.indexOf(d, all.get(0)), Tuner.indexOf(d, all.get(1)));
    }

    @Test
    public void stepsWrapRoundAndStartFromTheEnds() {
        List<Station> d = new ArrayList<>();
        d.add(st("A", ""));
        d.add(st("B", ""));
        d.add(st("C", ""));
        assertEquals("B", Tuner.step(d, d.get(0), 1).name);
        assertEquals("A", Tuner.step(d, d.get(2), 1).name);
        assertEquals("C", Tuner.step(d, d.get(0), -1).name);
        Station stranger = st("Z", "");
        assertEquals("A", Tuner.step(d, stranger, 1).name);
        assertEquals("C", Tuner.step(d, stranger, -1).name);
        assertEquals(null, Tuner.step(new ArrayList<>(), stranger, 1));
    }

    @Test
    public void favouritesBandUsesSavedOrderAndFallsBackWhenEmpty() {
        List<Station> all = new ArrayList<>();
        for (String n : new String[] {"Delta", "Alpha", "Charlie", "Bravo"}) all.add(st(n, ""));
        Tuner.setStations(all);
        Tuner.setBand(c, Tuner.FAVS);
        // No favourites: tunes A to Z instead.
        assertEquals("Alpha", Tuner.step(c, null, 1).name);
        List<Station> favs = new ArrayList<>();
        favs.add(all.get(2)); // Charlie
        favs.add(all.get(0)); // Delta
        Prefs.saveFavourites(c, favs);
        assertEquals("Delta", Tuner.step(c, all.get(2), 1).name);
        assertEquals("Charlie", Tuner.step(c, all.get(0), 1).name);
        // Leaving favourites returns to the last all-stations order.
        Tuner.setBand(c, Tuner.FREQ);
        Tuner.setBand(c, Tuner.FAVS);
        assertEquals(Tuner.FREQ, Tuner.allBand(c));
        Station[] nb = Tuner.neighbours(c, all.get(2));
        assertEquals("Delta", nb[0].name);
        assertEquals("Delta", nb[1].name);
    }

    @Test
    public void dialStateDescribesTheStation() {
        List<Station> all = new ArrayList<>();
        for (String n : new String[] {"Alpha", "KFM 94.5", "Zulu"}) all.add(st(n, ""));
        Tuner.setStations(all);
        Tuner.setBand(c, Tuner.AZ);
        Dial.State s = Dial.State.of(c, all.get(1));
        assertEquals("KFM 94.5", s.name);
        assertEquals("Alpha", s.prev);
        assertEquals("Zulu", s.next);
        assertEquals(2, s.index + 1);
        assertTrue(s.detail().contains("94.5 MHz"));
        assertTrue(s.detail().contains("2 of 3"));
        assertTrue(s.needle > 0.3f && s.needle < 0.4f);
    }

    @Test
    public void plainTextFindsTheStoryAndDropsTheRest() {
        String html = "<html><head><title>x</title><script>var ad = 1;</script></head><body>"
                + "<nav><ul><li><a href=/>Home</a></li><li><a href=/news>News</a></li></ul></nav>"
                + "<div class=\"sidebar\"><p>Subscribe to our newsletter for the latest news every morning.</p></div>"
                + "<article><h1>Taxi rank upgrade</h1>"
                + "<div class=\"article-body\">"
                + "<p>Commuters at the city&rsquo;s busiest taxi rank will get covered walkways, water points and safer crossings, the city said on Monday.</p>"
                + "<p>Work starts in January and is expected to take eight months, with the rank staying open throughout.</p>"
                + "<div class=\"ad\"><p>Advertisement</p></div>"
                + "<h2>What changes</h2>"
                + "<p>Marshals will direct pedestrians during construction, and temporary shelters will go up at the north entrance.</p>"
                + "<p><p>Taxi associations welcomed the plan, though some drivers worry about access, and asked for weekly meetings.</p>"
                + "<p><a href=/more>Read more: City budget explained</a></p>"
                + "</div></article>"
                + "<footer><p>&copy; 2026 Sample News. All rights reserved and then some more words.</p></footer>"
                + "</body></html>";
        List<PlainText.Block> b = PlainText.extract(html);
        assertEquals(5, b.size());
        assertTrue(b.get(0).text.startsWith("Commuters at the city’s busiest"));
        assertFalse(b.get(0).heading);
        assertEquals("What changes", b.get(2).text);
        assertTrue(b.get(2).heading);
        assertTrue(b.get(4).text.startsWith("Taxi associations"));
        for (PlainText.Block x : b) {
            assertFalse(x.text, x.text.contains("Subscribe") || x.text.contains("Advertisement") || x.text.contains("Read more"));
        }
        assertTrue(PlainText.extract("").isEmpty());
        assertTrue(PlainText.extract("<html><body><p>Too short.</p></body></html>").isEmpty());
    }

    @Test
    public void widgetStoresRoundTrip() {
        List<PlainText.Block> blocks = new ArrayList<>();
        blocks.add(new PlainText.Block("First paragraph.", false));
        blocks.add(new PlainText.Block("A heading", true));
        WidgetStore.putStory(c, "https://example.org/a", blocks);
        List<PlainText.Block> back = WidgetStore.story(c, "https://example.org/a");
        assertNotNull(back);
        assertEquals(2, back.size());
        assertTrue(back.get(1).heading);
        assertEquals(null, WidgetStore.story(c, "https://example.org/b"));

        long now = System.currentTimeMillis();
        List<Sports.Match> ms = new ArrayList<>();
        ms.add(match("1", Sports.POST, now - 86400_000L, true));
        ms.add(match("2", Sports.LIVE, now - 1800_000L, true));
        ms.add(match("3", Sports.PRE, now + 86400_000L, true));
        ms.add(match("4", Sports.PRE, now + 3600_000L, false)); // not South African: left out
        WidgetStore.saveScores(c, ms, now);
        WidgetStore.Scores s = WidgetStore.loadScores(c);
        assertEquals(3, s.matches.size());
        List<Sports.Match> board = WidgetStore.board(s.matches, now);
        assertEquals("2", board.get(0).id); // live first
        assertEquals("3", board.get(1).id); // then coming up
        assertEquals("1", board.get(2).id); // then results
        assertTrue(WidgetStore.needsLiveWatch(s.matches, now));
        assertEquals("66", board.get(2).away.score);
    }

    private static Sports.Match match(String id, int state, long time, boolean sa) {
        Sports.Match m = new Sports.Match();
        m.id = id;
        m.sport = Sports.RUGBY;
        m.feed = "test";
        m.compName = "Nations Championship";
        m.state = state;
        m.time = time;
        m.sa = sa;
        m.home = new Sports.Side();
        m.home.name = "Italy";
        m.home.score = "7";
        m.away = new Sports.Side();
        m.away.name = "South Africa";
        m.away.score = "66";
        m.away.sa = sa;
        m.away.winner = true;
        return m;
    }
}
