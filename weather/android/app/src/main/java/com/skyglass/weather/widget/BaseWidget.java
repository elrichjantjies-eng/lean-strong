package com.skyglass.weather.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Shared behaviour of the five widget sizes; each subclass only names its kind. */
public abstract class BaseWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.skyglass.weather.action.REFRESH";

    protected abstract String kind();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) WidgetRenderer.render(ctx, mgr, id, kind());
        RefreshWorker.schedule(ctx);
        // Fetch now if any widget has no forecast yet or it's stale.
        Store store = new Store(ctx);
        for (int id : ids) {
            Store.Place p = store.place(store.widgetPlace(id));
            Wx.Data d = p == null ? null : store.data(p.id);
            if (p != null && (d == null || System.currentTimeMillis() - d.fetchedAt > 40 * 60_000L)) {
                RefreshWorker.runNow(ctx);
                break;
            }
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        // Resized: show more or fewer hours and days.
        WidgetRenderer.render(ctx, mgr, id, kind());
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        Store store = new Store(ctx);
        for (int id : ids) store.forgetWidget(id);
    }

    @Override
    public void onEnabled(Context ctx) {
        RefreshWorker.schedule(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            RefreshWorker.runNow(ctx);
            return;
        }
        super.onReceive(ctx, intent);
    }
}
