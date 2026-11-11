package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import java.util.List;
import java.util.Locale;

/**
 * "Read here": one story at a time, readable in the widget itself. ◀ ▶ flip through the
 * headlines; "Read the whole story here" downloads just that page's text into the widget (on Wi-Fi
 * it does this by itself, unless Data is on Saver). The text is pulled out of the page in the app;
 * no ads, pictures or scripts are loaded.
 */
public class StoryWidget extends AppWidgetProvider {
    static final String ACTION_PREV = "za.saradio.app.widget.STORY_PREV";
    static final String ACTION_NEXT = "za.saradio.app.widget.STORY_NEXT";
    /** From a list row: "read" downloads the story's text into the widget. */
    static final String ACTION_ROW = "za.saradio.app.widget.STORY_ROW";
    static final String EXTRA_DO = "do";
    static final String EXTRA_LINK = "link";
    private static final long LOADING_FOR = 30_000;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) refresh(c, id);
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
    public void onDeleted(Context c, int[] ids) {
        android.content.SharedPreferences.Editor e = Prefs.get(c).edit();
        for (int id : ids) e.remove("story_" + id);
        e.apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (ACTION_PREV.equals(action) || ACTION_NEXT.equals(action)) {
            List<NewsRepo.Item> items = NewsRepo.local(NewsRepo.load(c));
            if (items.isEmpty()) return;
            int pos = position(c, id, items.size()) + (ACTION_NEXT.equals(action) ? 1 : -1);
            pos = ((pos % items.size()) + items.size()) % items.size();
            Prefs.get(c).edit().putInt("story_" + id, pos).apply();
            NewsRepo.Item it = items.get(pos);
            // On Wi-Fi (and not on Saver) the text comes straight in; otherwise it waits for a tap.
            boolean auto = !DataMode.onMeteredNetwork(c) && DataMode.effective(c) != DataMode.SAVER
                    && WidgetStore.story(c, it.link) == null;
            if (auto) startLoading(c, it.link);
            refresh(c, id);
            if (auto) load(c, it.link, goAsync());
            return;
        }
        if (ACTION_ROW.equals(action)) {
            String what = i.getStringExtra(EXTRA_DO);
            String link = i.getStringExtra(EXTRA_LINK);
            if (link == null) return;
            if ("read".equals(what)) {
                startLoading(c, link);
                refresh(c, id);
                load(c, link, goAsync());
            }
            return;
        }
        super.onReceive(c, i);
    }

    private static void startLoading(Context c, String link) {
        Prefs.get(c).edit().putLong("storyLoading:" + link, System.currentTimeMillis()).remove("storyFailed:" + link).apply();
    }

    static boolean loading(Context c, String link) {
        return System.currentTimeMillis() - Prefs.get(c).getLong("storyLoading:" + link, 0) < LOADING_FOR;
    }

    static boolean failed(Context c, String link) {
        return Prefs.get(c).getBoolean("storyFailed:" + link, false);
    }

    /** Downloads the page and pulls out its text, then redraws the widgets. */
    private static void load(final Context c, final String link, final PendingResult done) {
        final Context ac = c.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                String[] page = Extractor.fetch(link);
                List<PlainText.Block> blocks = PlainText.extract(page[0]);
                if (blocks.size() >= 2) {
                    WidgetStore.putStory(ac, link, blocks);
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            android.content.SharedPreferences.Editor e = Prefs.get(ac).edit().remove("storyLoading:" + link);
            if (!ok) e.putBoolean("storyFailed:" + link, true);
            e.apply();
            updateAll(ac);
            if (done != null) done.finish();
        }, "widget-story").start();
    }

    static Intent articleIntent(Context c, NewsRepo.Item it) {
        return Widgets.openIntent(c, Widgets.OPEN_ARTICLE)
                .putExtra("link", it.link).putExtra("title", it.title).putExtra("source", it.source)
                .putExtra("summary", it.summary).putExtra("image", it.image).putExtra("time", it.time);
    }

    static int position(Context c, int id, int count) {
        if (count <= 0) return 0;
        int p = Prefs.get(c).getInt("story_" + id, 0);
        return Math.max(0, Math.min(count - 1, p));
    }

    static void updateAll(Context c) {
        for (int id : Widgets.ids(c, StoryWidget.class)) refresh(c, id);
    }

    private static void refresh(Context c, int id) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            m.updateAppWidget(id, build(c, id));
            m.notifyAppWidgetViewDataChanged(new int[] {id}, R.id.w_list);
        } catch (Exception ignored) {
        }
    }

    static RemoteViews build(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_story);
        List<NewsRepo.Item> items = NewsRepo.local(NewsRepo.load(c));
        v.setOnClickPendingIntent(R.id.w_prev, Widgets.broadcast(c, StoryWidget.class, ACTION_PREV, id, false));
        v.setOnClickPendingIntent(R.id.w_next, Widgets.broadcast(c, StoryWidget.class, ACTION_NEXT, id, false));
        if (items.isEmpty()) {
            v.setTextViewText(R.id.w_sub, "READ HERE");
            v.setTextViewText(R.id.w_title, "No stories yet");
            v.setTextViewText(R.id.w_meta, "Open SA Radio's News tab to load the headlines.");
            v.setOnClickPendingIntent(R.id.w_open, Widgets.open(c, Widgets.OPEN_NEWS, 430));
            v.setOnClickPendingIntent(R.id.w_title, Widgets.open(c, Widgets.OPEN_NEWS, 430));
        } else {
            int pos = position(c, id, items.size());
            NewsRepo.Item it = items.get(pos);
            v.setTextViewText(R.id.w_sub, it.source.toUpperCase(Locale.ROOT) + "  ·  " + (pos + 1) + "/" + items.size());
            v.setTextViewText(R.id.w_title, it.title);
            List<PlainText.Block> text = WidgetStore.story(c, it.link);
            String ago = NewsRepo.ago(it.time);
            String meta = ago.isEmpty() ? "" : ago;
            if (text != null) {
                int words = 0;
                for (PlainText.Block b : text) words += b.text.split("\\s+").length;
                meta += (meta.isEmpty() ? "" : " · ") + Math.max(1, Math.round(words / 230f)) + " min read · ad-free";
            }
            v.setTextViewText(R.id.w_meta, meta);
            v.setContentDescription(R.id.w_title, it.title + ". Story " + (pos + 1) + " of " + items.size() + " from " + it.source);
            android.app.PendingIntent openIt = android.app.PendingIntent.getActivity(c, 431 + id, articleIntent(c, it)
                            .setData(Uri.parse("saradio://story/" + id)),
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.w_open, openIt);
            v.setOnClickPendingIntent(R.id.w_title, openIt);
        }
        Intent svc = new Intent(c, WidgetListService.class)
                .putExtra(WidgetListService.EXTRA_KIND, WidgetListService.KIND_STORY)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.setData(Uri.parse("saradio://list/story/" + id));
        v.setRemoteAdapter(R.id.w_list, svc);
        v.setEmptyView(R.id.w_list, R.id.w_empty);
        v.setPendingIntentTemplate(R.id.w_list, Widgets.broadcast(c, StoryWidget.class, ACTION_ROW, id, true));
        return v;
    }
}
