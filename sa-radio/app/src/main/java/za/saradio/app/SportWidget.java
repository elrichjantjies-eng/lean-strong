package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * The scoreboard: live games first, then what's coming up and the latest results, for South
 * African teams and players (plus F1). While a game is on it refreshes every few minutes.
 */
public class SportWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "za.saradio.app.widget.SPORT_REFRESH";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
        WidgetJobs.scheduleSport(c);
    }

    @Override
    public void onEnabled(Context c) {
        WidgetJobs.scheduleSport(c);
        WidgetJobs.sportNow(c);
    }

    @Override
    public void onDisabled(Context c) {
        WidgetJobs.cancelSport(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            WidgetJobs.sportNow(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = Widgets.ids(c, SportWidget.class);
        for (int id : ids) update(c, m, id);
        if (ids.length > 0) m.notifyAppWidgetViewDataChanged(ids, R.id.w_list);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        try {
            m.updateAppWidget(id, build(c, id));
        } catch (Exception ignored) {
        }
    }

    static RemoteViews build(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_sport);
        WidgetStore.Scores s = WidgetStore.loadScores(c);
        long now = System.currentTimeMillis();
        int live = 0;
        for (Sports.Match m : s.matches) if (m.state == Sports.LIVE) live++;
        String sub = "SA FOCUS";
        if (live > 0) sub += " · " + live + " LIVE";
        if (s.at > 0) sub += " · " + Sports.clock(s.at);
        v.setTextViewText(R.id.w_sub, sub);
        v.setTextViewText(R.id.w_empty, s.at == 0 ? "Loading scores… Tap to refresh." : "No South African games this week. Tap to refresh.");
        Intent svc = new Intent(c, WidgetListService.class)
                .putExtra(WidgetListService.EXTRA_KIND, WidgetListService.KIND_SPORT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse("saradio://list/sport/" + id));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        v.setPendingIntentTemplate(R.id.w_list, Widgets.openTemplate(c, Widgets.OPEN_SPORT, 440));
        v.setOnClickPendingIntent(R.id.w_refresh, Widgets.broadcast(c, SportWidget.class, ACTION_REFRESH, id, false));
        v.setOnClickPendingIntent(R.id.w_header, Widgets.open(c, Widgets.OPEN_SPORT, 441));
        v.setOnClickPendingIntent(R.id.w_empty, Widgets.broadcast(c, SportWidget.class, ACTION_REFRESH, id, false));
        WidgetJobs.sportIfStale(c, s.at);
        return v;
    }
}
