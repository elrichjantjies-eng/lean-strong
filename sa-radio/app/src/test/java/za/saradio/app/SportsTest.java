package za.saradio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Offline checks of the sports parsing and rules, on small hand-written feeds. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SportsTest {

    private static Sports.Comp comp(String feed) {
        for (Sports.Comp c : Sports.COMPS) if (c.feed.equals(feed)) return c;
        throw new AssertionError(feed);
    }

    private static String event(String id, String state, String statusName, String home, String hs, String away, String as, boolean homeWon) {
        return "{\"id\":\"" + id + "\",\"date\":\"2026-11-07T11:40Z\",\"status\":{\"type\":{\"name\":\"" + statusName + "\",\"state\":\""
                + state + "\",\"shortDetail\":\"FT\"}},\"competitions\":[{\"venue\":{\"fullName\":\"Allianz Stadium\"},\"competitors\":["
                + "{\"homeAway\":\"home\",\"score\":\"" + hs + "\",\"winner\":" + homeWon + ",\"team\":{\"displayName\":\"" + home
                + "\",\"logo\":\"https://a.espncdn.com/i/teamlogos/rugby/teams/500/20.png\"}},"
                + "{\"homeAway\":\"away\",\"score\":\"" + as + "\",\"winner\":" + !homeWon + ",\"team\":{\"displayName\":\"" + away + "\"}}]}]}";
    }

    @Test
    public void rugbyResultsAndSouthAfricanTeams() throws Exception {
        JSONObject d = new JSONObject("{\"events\":[" + event("1", "post", "STATUS_FINAL", "Italy", "7", "South Africa", "66", false) + ","
                + event("2", "post", "STATUS_FINAL", "British and Irish Lions", "20", "Australia", "18", true) + "]}");
        List<Sports.Match> l = Sports.parseEspn(comp("rugby/17567"), d);
        assertEquals(2, l.size());
        Sports.Match m = l.get(0);
        assertEquals(Sports.POST, m.state);
        assertEquals("Italy", m.home.name);
        assertTrue(m.away.sa);
        assertTrue(m.away.winner);
        assertTrue(m.sa);
        assertEquals("Allianz Stadium", m.venue);
        assertTrue("logos are fetched small", m.home.logo.contains("combiner") && m.home.logo.contains("w=80"));
        assertFalse("the Lions tour isn't a South African team", l.get(1).sa);
    }

    @Test
    public void postponedIsNotAResult() throws Exception {
        JSONObject d = new JSONObject("{\"events\":[" + event("3", "post", "STATUS_POSTPONED", "Kaizer Chiefs", "0", "AmaZulu", "0", false) + "]}");
        Sports.Match m = Sports.parseEspn(comp("soccer/rsa.1"), d).get(0);
        assertEquals(Sports.OFF, m.state);
        assertEquals("", m.home.score);
        assertTrue("every Premiership club is South African", m.home.sa && m.away.sa);
    }

    @Test
    public void filtersKeepTheRelevantMatches() throws Exception {
        JSONObject friendlies = new JSONObject("{\"events\":[" + event("4", "pre", "STATUS_SCHEDULED", "Vietnam", "", "Chinese Taipei", "", false) + ","
                + event("5", "pre", "STATUS_SCHEDULED", "South Africa", "", "Mozambique", "", false) + "]}");
        List<Sports.Match> f = Sports.parseEspn(comp("soccer/fifa.friendly"), friendlies);
        assertEquals("only Bafana friendlies", 1, f.size());
        assertEquals("South Africa", f.get(0).home.name);
        assertEquals("", f.get(0).home.score);
        JSONObject tests = new JSONObject("{\"events\":[" + event("6", "pre", "STATUS_SCHEDULED", "Pakistan", "", "Uzbekistan", "", false) + ","
                + event("7", "pre", "STATUS_SCHEDULED", "Georgia", "", "Portugal", "", false) + "]}");
        List<Sports.Match> t = Sports.parseEspn(comp("rugby/289234"), tests);
        assertEquals("only established rugby nations", 1, t.size());
        assertEquals("Georgia", t.get(0).home.name);
    }

    @Test
    public void cricketKeepsSouthAfricaAndMajorTeams() throws Exception {
        String sa = "{\"leagues\":[{\"id\":\"8656\",\"name\":\"South Africa Domestic T20/Pro20\"}],\"events\":[{\"id\":\"11\",\"date\":\"2026-11-07T16:00Z\","
                + "\"status\":{\"summary\":\"W Province won by 9 runs\",\"type\":{\"state\":\"post\",\"name\":\"STATUS_FINAL\"}},"
                + "\"competitions\":[{\"competitors\":[{\"order\":1,\"score\":\"178/9\",\"team\":{\"displayName\":\"Western Province\"}},"
                + "{\"order\":2,\"score\":\"169/6 (20 ov, target 179)\",\"team\":{\"displayName\":\"Lions\"}}]}]}]}";
        String other = "{\"leagues\":[{\"id\":\"1\",\"name\":\"Nepal Premier League\"}],\"events\":[{\"id\":\"12\",\"date\":\"2026-11-09T06:00Z\","
                + "\"status\":{\"summary\":\"Starts at 13:00 local time\",\"type\":{\"state\":\"pre\"}},"
                + "\"competitions\":[{\"competitors\":[{\"order\":1,\"team\":{\"displayName\":\"Karnali Yaks\"}},{\"order\":2,\"team\":{\"displayName\":\"Pokhara Avengers\"}}]}]}]}";
        String test = "{\"leagues\":[{\"id\":\"2\",\"name\":\"India tour of New Zealand\"}],\"events\":[{\"id\":\"13\",\"date\":\"2026-11-10T02:00Z\","
                + "\"status\":{\"summary\":\"Starts at 15:00 local time\",\"type\":{\"state\":\"pre\"}},"
                + "\"competitions\":[{\"competitors\":[{\"order\":1,\"team\":{\"displayName\":\"New Zealand\"}},{\"order\":2,\"team\":{\"displayName\":\"India\"}}]}]}]}";
        List<Sports.Match> l = Sports.parseCricket(new JSONObject("{\"scores\":[" + sa + "," + other + "," + test + "]}"));
        assertEquals(2, l.size());
        Sports.Match wp = l.get(0);
        assertEquals("Result", wp.status);
        assertEquals("W Province won by 9 runs", wp.summary);
        assertTrue(wp.sa && wp.home.sa);
        assertEquals("cricket/8656", wp.feed);
        Sports.Match nz = l.get(1);
        assertFalse(nz.sa);
        assertEquals("the venue's local start time is dropped", "", nz.summary);
    }

    @Test
    public void golfTiesAndSouthAfricans() throws Exception {
        List<JSONObject> players = new ArrayList<>();
        String[][] p = {{"Jon Rahm", "Spain", "-23"}, {"Tyrrell Hatton", "England", "-21"}, {"Jayden Schaper", "South Africa", "-21"},
            {"Rory McIlroy", "Northern Ireland", "-19"}};
        for (String[] x : p) {
            players.add(new JSONObject("{\"score\":\"" + x[2] + "\",\"athlete\":{\"displayName\":\"" + x[0] + "\",\"flag\":{\"alt\":\"" + x[1] + "\"}}}"));
        }
        List<Sports.Line> b = Sports.leaderboard(players);
        assertEquals("1", b.get(0).rank);
        assertEquals("T2", b.get(1).rank);
        assertEquals("T2", b.get(2).rank);
        assertTrue(b.get(2).sa);
        assertEquals("4", b.get(3).rank);
    }

    @Test
    public void fightResultsReadNaturally() throws Exception {
        JSONObject bout = new JSONObject("{\"type\":{\"abbreviation\":\"Middleweight\"},\"competitors\":["
                + "{\"winner\":false,\"athlete\":{\"displayName\":\"Fighter One\",\"flag\":{\"alt\":\"Brazil\"}}},"
                + "{\"winner\":true,\"athlete\":{\"displayName\":\"Dricus du Plessis\",\"flag\":{\"alt\":\"South Africa\"}}}]}");
        Sports.Line l = Sports.bout(bout);
        assertEquals("Dricus du Plessis def. Fighter One", l.name);
        assertEquals("Middleweight", l.value);
        assertTrue(l.sa);
    }

    @Test
    public void lappedCarsShowLapsNotTimes() throws Exception {
        JSONObject p9 = new JSONObject("{\"position\":\"9\",\"laps\":\"70\",\"status\":\"Lapped\",\"Time\":{\"time\":\"+4.427\"},"
                + "\"Driver\":{\"givenName\":\"Lewis\",\"familyName\":\"Hamilton\"},\"Constructor\":{\"name\":\"Ferrari\"}}");
        assertEquals("+1 lap", Sports.f1Line(p9, 71).value);
        JSONObject p1 = new JSONObject("{\"position\":\"1\",\"laps\":\"71\",\"status\":\"Finished\",\"Time\":{\"time\":\"1:35:23.504\"},"
                + "\"Driver\":{\"givenName\":\"Lando\",\"familyName\":\"Norris\"},\"Constructor\":{\"name\":\"McLaren\"}}");
        assertEquals("1:35:23.504", Sports.f1Line(p1, 71).value);
        assertEquals("P1", Sports.f1Line(p1, 71).rank);
        JSONObject dnf = new JSONObject("{\"position\":\"21\",\"laps\":\"30\",\"status\":\"Retired\"}");
        assertEquals("Retired", Sports.f1Line(dnf, 71).value);
    }

    @Test
    public void monthsCoverTheWindow() throws Exception {
        long late = Sports.parseTime("2026-11-28T12:00:00Z");
        Set<String> m = Sports.months(late - 8 * Sports.DAY, late + 15 * Sports.DAY);
        assertTrue(m.contains("202611") && m.contains("202612"));
        assertEquals(2, m.size());
        assertEquals(Sports.parseTime("2026-11-08T17:00:00Z"), Sports.jolpicaTime(new JSONObject("{\"date\":\"2026-11-08\",\"time\":\"17:00:00Z\"}")));
    }
}
