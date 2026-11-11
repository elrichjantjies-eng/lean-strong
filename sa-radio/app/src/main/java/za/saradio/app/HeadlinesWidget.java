package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import java.util.List;

/** The latest headlines. Tap a story to read it in the app's ad-free reader. */
public class HeadlinesWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "za.saradio.app.widget.NEWS_REFRESH";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
        WidgetJobs.scheduleNews(c);
    }

    @Override
    public void onEnabled(Context c) {
        WidgetJobs.scheduleNews(c);
    }

    @Override
    public void onDisabled(Context c) {
        WidgetJobs.cancelNewsIfUnused(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            WidgetJobs.newsNow(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = Widgets.ids(c, HeadlinesWidget.class);
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
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_headlines);
        List<NewsRepo.Item> items = NewsRepo.local(NewsRepo.load(c));
        v.setTextViewText(R.id.w_sub, items.isEmpty() ? "AD-FREE" : "AD-FREE · " + items.size() + " STORIES");
        Intent svc = new Intent(c, WidgetListService.class)
                .putExtra(WidgetListService.EXTRA_KIND, WidgetListService.KIND_HEADLINES)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse("saradio://list/headlines/" + id));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        v.setPendingIntentTemplate(R.id.w_list, Widgets.openTemplate(c, Widgets.OPEN_ARTICLE, 420));
        v.setOnClickPendingIntent(R.id.w_refresh, Widgets.broadcast(c, HeadlinesWidget.class, ACTION_REFRESH, id, false));
        v.setOnClickPendingIntent(R.id.w_header, Widgets.open(c, Widgets.OPEN_NEWS, 421));
        v.setOnClickPendingIntent(R.id.w_empty, Widgets.broadcast(c, HeadlinesWidget.class, ACTION_REFRESH, id, false));
        return v;
    }
}
