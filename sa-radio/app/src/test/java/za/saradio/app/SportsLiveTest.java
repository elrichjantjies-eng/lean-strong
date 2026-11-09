package za.saradio.app;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Loads real fixtures and results (run with -Plive=true) and prints what came back. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SportsLiveTest {

    @Test
    public void loadsRealFixturesAndResults() {
        Assume.assumeTrue("needs -Plive=true", Boolean.getBoolean("live"));
        long now = System.currentTimeMillis();
        long t0 = System.currentTimeMillis();
        Sports.Result r = Sports.load(now);
        Sports.Result extra = Sports.loadOnDemand(now);
        System.out.println("SPORTS load: " + r.matches.size() + " items from " + r.sources + " requests, " + r.failed + " failed, "
                + (System.currentTimeMillis() - t0) + " ms; on-demand +" + extra.matches.size());
        for (String f : Sports.FILTERS) {
            int n = 0, live = 0, pre = 0, post = 0;
            for (Sports.Match m : r.matches) {
                if (!Sports.matchesFilter(m, f)) continue;
                n++;
                if (m.state == Sports.LIVE) live++;
                else if (m.state == Sports.PRE) pre++;
                else post++;
            }
            System.out.println(String.format(Locale.ROOT, "SPORTS %-8s %3d  (live %d, fixtures %d, results %d)", f, n, live, pre, post));
        }
        List<Sports.Match> sa = new ArrayList<>();
        for (Sports.Match m : r.matches) if (Sports.matchesFilter(m, "sa")) sa.add(m);
        Collections.sort(sa, (a, b) -> Long.compare(a.time, b.time));
        for (Sports.Match m : sa) {
            String what = m.isEvent()
                    ? m.title + (m.lines.isEmpty() ? "" : " | " + m.lines.get(0).rank + " " + m.lines.get(0).name + " " + m.lines.get(0).value)
                    : m.home.name + " " + m.home.score + " v " + m.away.score + " " + m.away.name + (m.summary.isEmpty() ? "" : " | " + m.summary);
            System.out.println(String.format(Locale.ROOT, "SPORTS  %s %-6s %-26s %s", Sports.clock(m.time) + " " + Sports.dayLabel(m.time, now),
                    m.status, m.compName.length() > 26 ? m.compName.substring(0, 26) : m.compName, what));
        }
        assertTrue("expected some fixtures and results", r.matches.size() > 20);
        assertTrue("most sources should answer", r.failed < r.sources / 3);
    }
}
