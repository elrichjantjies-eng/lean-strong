package com.skyglass.weather.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Background refresh: every 30 minutes (and on demand) fetch the forecast for every place a
 * widget shows plus the first place, attach SAWS warnings, redraw the widgets and send any
 * notifications that are due. A second, one-off job fires at the daily forecast time.
 */
public class RefreshWorker extends Worker {

    private static final String PERIODIC = "skyglass-refresh";
    private static final String NOW = "skyglass-refresh-now";
    private static final String DAILY = "skyglass-daily";
    private static final String KEY_DAILY = "daily";

    public RefreshWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    public static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(RefreshWorker.class, 30, TimeUnit.MINUTES)
            .setConstraints(online())
            .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void runNow(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(RefreshWorker.class).setConstraints(online()).build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req);
    }

    /** Schedules (or cancels) the daily forecast notification for the next HH:MM. */
    public static void scheduleDaily(Context ctx, Store.Settings s) {
        WorkManager wm = WorkManager.getInstance(ctx);
        if (!s.notifyDaily) {
            wm.cancelUniqueWork(DAILY);
            return;
        }
        long delay = millisUntil(s.dailyAt);
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(RefreshWorker.class)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(new Data.Builder().putBoolean(KEY_DAILY, true).build())
            .build();
        wm.enqueueUniqueWork(DAILY, ExistingWorkPolicy.REPLACE, req);
    }

    static long millisUntil(String hhmm) {
        int h = 6;
        int m = 30;
        try {
            String[] p = hhmm.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (RuntimeException ignored) {
            // Keep the default.
        }
        Calendar now = Calendar.getInstance();
        Calendar at = (Calendar) now.clone();
        at.set(Calendar.HOUR_OF_DAY, h);
        at.set(Calendar.MINUTE, m);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        if (!at.after(now)) at.add(Calendar.DAY_OF_YEAR, 1);
        return at.getTimeInMillis() - now.getTimeInMillis();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Store store = new Store(ctx);
        Store.Settings settings = store.settings();
        boolean daily = getInputData().getBoolean(KEY_DAILY, false);
        List<Store.Place> places = store.places();
        if (places.isEmpty()) return Result.success();

        // The places to fetch: the first one (notifications) and every place a widget shows.
        Map<String, Store.Place> wanted = new LinkedHashMap<>();
        wanted.put(places.get(0).id, places.get(0));
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (Class<?> cls : WidgetRenderer.PROVIDERS) {
            for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, cls))) {
                Store.Place p = store.place(store.widgetPlace(id));
                if (p != null) wanted.put(p.id, p);
            }
        }

        // SAWS warnings, once, if any of the places is in South Africa.
        List<Saws.Warning> warnings = null;
        for (Store.Place p : wanted.values()) {
            if (Saws.inSouthAfrica(p.lat, p.lon)) {
                try {
                    warnings = Saws.fetchWarnings();
                } catch (Exception ignored) {
                    warnings = null;
                }
                break;
            }
        }

        boolean anyFailed = false;
        Wx.Data primary = null;
        List<Saws.Warning> primaryWarnings = new ArrayList<>();
        for (Store.Place p : wanted.values()) {
            Wx.Data d;
            try {
                d = Wx.fetch(p.id, p.name, p.current, p.lat, p.lon);
            } catch (Exception e) {
                anyFailed = true;
                d = store.data(p.id);
                if (d == null) continue;
            }
            if (warnings != null) {
                List<Saws.Warning> mine = Saws.covering(warnings, p.lat, p.lon);
                if (mine.isEmpty()) {
                    d.alertText = null;
                    d.alertColor = null;
                } else {
                    Saws.Warning w = mine.get(0);
                    d.alertText = Saws.colorName(w.color) + (w.level > 0 ? " L" + w.level : "") + ": " + w.event;
                    d.alertColor = w.color;
                }
                if (p.id.equals(places.get(0).id)) primaryWarnings = mine;
            }
            store.saveData(d);
            if (p.id.equals(places.get(0).id)) primary = d;
        }

        WidgetRenderer.updateAll(ctx);

        if (primary != null) {
            Notifier.ensureChannels(ctx);
            if (settings.notifyWarnings && warnings != null) Notifier.warnings(ctx, store, primary, primaryWarnings);
            if (settings.notifyRain) Notifier.rainSoon(ctx, store, primary, settings);
            if (daily && settings.notifyDaily) Notifier.daily(ctx, store, primary, settings);
        }
        if (daily) scheduleDaily(ctx, settings);
        return anyFailed && primary == null ? Result.retry() : Result.success();
    }
}
