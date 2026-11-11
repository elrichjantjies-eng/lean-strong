package za.saradio.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Background refreshes for the widgets, run by the system's job scheduler so they wait for a
 * network and don't wake the phone needlessly.
 * <ul>
 *   <li>Headlines: every 2 hours (4 on Saver, and only on Wi-Fi), or now when you tap refresh.</li>
 *   <li>Scores: hourly (only on Wi-Fi on Saver). A full reload when the snapshot is more than 3
 *   hours old (12 on Saver); otherwise just the competitions with games on.</li>
 *   <li>Live games: every 2 minutes while a game is on (5 on Saver), fetching only those games'
 *   competitions.</li>
 * </ul>
 */
public class WidgetJobs extends JobService {
    private static final int NEWS = 1101;
    private static final int NEWS_NOW = 1102;
    private static final int SPORT = 1201;
    private static final int SPORT_NOW = 1202;
    private static final int SPORT_LIVE = 1203;
    private static final String FULL = "full";

    // ---- Scheduling -------------------------------------------------------------------------

    private static JobScheduler js(Context c) {
        return (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    private static ComponentName me(Context c) {
        return new ComponentName(c, WidgetJobs.class);
    }

    private static boolean saver(Context c) {
        return DataMode.effective(c) == DataMode.SAVER || Prefs.dataMode(c) == DataMode.SAVER;
    }

    private static void schedule(Context c, JobInfo job) {
        try {
            js(c).schedule(job);
        } catch (Exception ignored) {
        }
    }

    private static boolean pending(Context c, int id) {
        try {
            return js(c).getPendingJob(id) != null;
        } catch (Exception e) {
            return false;
        }
    }

    static void scheduleNews(Context c) {
        if (pending(c, NEWS)) return;
        boolean saver = saver(c);
        schedule(c, new JobInfo.Builder(NEWS, me(c))
                .setPeriodic((saver ? 4 : 2) * 3600_000L)
                .setRequiredNetworkType(saver ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    static void newsNow(Context c) {
        schedule(c, new JobInfo.Builder(NEWS_NOW, me(c))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(60_000)
                .build());
    }

    static void cancelNewsIfUnused(Context c) {
        if (Widgets.any(c, HeadlinesWidget.class) || Widgets.any(c, StoryWidget.class)) return;
        js(c).cancel(NEWS);
    }

    static void scheduleSport(Context c) {
        if (pending(c, SPORT)) return;
        schedule(c, new JobInfo.Builder(SPORT, me(c))
                .setPeriodic(3600_000L)
                .setRequiredNetworkType(saver(c) ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    /** A full reload of the scores now (tap on refresh, or a new widget). */
    static void sportNow(Context c) {
        PersistableBundle x = new PersistableBundle();
        x.putBoolean(FULL, true);
        Prefs.get(c).edit().putLong("sportTriedAt", System.currentTimeMillis()).apply();
        schedule(c, new JobInfo.Builder(SPORT_NOW, me(c))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(60_000)
                .setExtras(x)
                .build());
    }

    /** Reloads stale scores, but tries at most every quarter of an hour. */
    static void sportIfStale(Context c, long snapshotAt) {
        long now = System.currentTimeMillis();
        if (now - snapshotAt < 6 * 3600_000L) return;
        if (now - Prefs.get(c).getLong("sportTriedAt", 0) < 15 * 60_000L) return;
        sportNow(c);
    }

    private static void watchLive(Context c) {
        schedule(c, new JobInfo.Builder(SPORT_LIVE, me(c))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency((saver(c) ? 5 : 2) * 60_000L)
                .setOverrideDeadline((saver(c) ? 8 : 4) * 60_000L)
                .build());
    }

    static void cancelSport(Context c) {
        js(c).cancel(SPORT);
        js(c).cancel(SPORT_NOW);
        js(c).cancel(SPORT_LIVE);
    }

    // ---- Running ----------------------------------------------------------------------------

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                switch (params.getJobId()) {
                    case NEWS:
                    case NEWS_NOW:
                        if (Widgets.any(c, HeadlinesWidget.class) || Widgets.any(c, StoryWidget.class)) refreshNews(c);
                        break;
                    default:
                        if (Widgets.any(c, SportWidget.class)) {
                            refreshSport(c, params.getJobId() == SPORT_NOW || params.getExtras().getBoolean(FULL));
                        }
                }
            } catch (Exception ignored) {
            }
            jobFinished(params, false);
        }, "widget-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    static void refreshNews(Context c) {
        List<NewsRepo.Item> fresh = NewsRepo.fetchAll();
        if (!fresh.isEmpty()) NewsRepo.save(c, fresh); // also redraws the news widgets
        else Widgets.newsChanged(c);
    }

    static void refreshSport(Context c, boolean forceFull) {
        long now = System.currentTimeMillis();
        WidgetStore.Scores snap = WidgetStore.loadScores(c);
        long staleAfter = (saver(c) ? 12 : 3) * 3600_000L;
        if (forceFull || snap.matches.isEmpty() || now - snap.at > staleAfter) {
            Sports.Result r = Sports.load(now);
            if (!r.matches.isEmpty()) WidgetStore.saveScores(c, r.matches, now);
        } else if (WidgetStore.needsLiveWatch(snap.matches, now)) {
            List<Sports.Match> fresh = Sports.refreshLive(snap.matches, now);
            if (!fresh.isEmpty()) {
                Map<String, Sports.Match> byKey = new HashMap<>();
                for (Sports.Match m : snap.matches) byKey.put(m.key(), m);
                for (Sports.Match m : fresh) if (byKey.containsKey(m.key())) byKey.put(m.key(), m);
                WidgetStore.saveScores(c, new java.util.ArrayList<>(byKey.values()), snap.at);
            }
        }
        Widgets.sportChanged(c);
        if (WidgetStore.needsLiveWatch(WidgetStore.loadScores(c).matches, System.currentTimeMillis())) watchLive(c);
    }
}
