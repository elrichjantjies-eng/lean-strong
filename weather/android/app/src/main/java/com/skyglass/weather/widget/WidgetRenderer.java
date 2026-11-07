package com.skyglass.weather.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import com.skyglass.weather.MainActivity;
import com.skyglass.weather.R;
import java.text.DateFormat;
import java.util.Date;

/** Draws every Skyglass widget from the cached forecast and the user's widget style. */
public final class WidgetRenderer {

    private WidgetRenderer() {}

    public static final Class<?>[] PROVIDERS = {
        SmallWidget.class, WideWidget.class, MediumWidget.class, LargeWidget.class, ClockWidget.class,
    };

    /** Redraws every widget on the home screen (cheap: uses cached data only). */
    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (Class<?> cls : PROVIDERS) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, cls));
            for (int id : ids) render(ctx, mgr, id, kindOf(cls));
        }
    }

    public static int widgetCount(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int n = 0;
        for (Class<?> cls : PROVIDERS) n += mgr.getAppWidgetIds(new ComponentName(ctx, cls)).length;
        return n;
    }

    public static String kindOf(Class<?> cls) {
        if (cls == SmallWidget.class) return "small";
        if (cls == WideWidget.class) return "wide";
        if (cls == MediumWidget.class) return "medium";
        if (cls == LargeWidget.class) return "large";
        return "clock";
    }

    public static Class<?> providerFor(String kind) {
        switch (kind) {
            case "small": return SmallWidget.class;
            case "wide": return WideWidget.class;
            case "large": return LargeWidget.class;
            case "clock": return ClockWidget.class;
            default: return MediumWidget.class;
        }
    }

    /** Kind of an existing widget id, by asking which provider owns it. */
    public static String kindOfWidget(Context ctx, int widgetId) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        android.appwidget.AppWidgetProviderInfo info = mgr.getAppWidgetInfo(widgetId);
        if (info == null) return "medium";
        for (Class<?> cls : PROVIDERS) if (info.provider.getClassName().equals(cls.getName())) return kindOf(cls);
        return "medium";
    }

    private static int layout(String kind) {
        switch (kind) {
            case "small": return R.layout.widget_small;
            case "wide": return R.layout.widget_wide;
            case "large": return R.layout.widget_large;
            case "clock": return R.layout.widget_clock;
            default: return R.layout.widget_medium;
        }
    }

    /** Text colours for the chosen glass: dark text on frosted (milky) glass, white elsewhere. */
    private static final class Ink {
        final int main, dim, faint, pop;

        Ink(boolean light) {
            main = light ? 0xFF1B2433 : 0xFFFFFFFF;
            dim = light ? 0xFF3D4A5C : 0xD9FFFFFF;
            faint = light ? 0xFF5B6B80 : 0xA6FFFFFF;
            pop = light ? 0xFF1E6FD9 : 0xFF8FD8FF;
        }
    }

    public static void render(Context ctx, AppWidgetManager mgr, int widgetId, String kind) {
        Store store = new Store(ctx);
        Store.Place place = store.place(store.widgetPlace(widgetId));
        Wx.Data d = place == null ? null : store.data(place.id);
        RemoteViews v = build(ctx, kind, place, d, store.settings(), mgr.getAppWidgetOptions(widgetId), widgetId);
        mgr.updateAppWidget(widgetId, v);
    }

    /**
     * The widget's views. `widgetId` < 0 builds a static picture (no tap actions), which is how
     * the layouts are previewed in tests.
     */
    public static RemoteViews build(Context ctx, String kind, Store.Place place, Wx.Data d, Store.Settings s,
        Bundle o, int widgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout(kind));
        boolean interactive = widgetId >= 0;
        boolean frosted = "frosted".equals(s.style);
        Ink ink = new Ink(frosted);

        // Glass background and opacity.
        int bg;
        switch (s.style) {
            case "frosted": bg = R.drawable.glass_frosted; break;
            case "dark": bg = R.drawable.glass_dark; break;
            case "tinted": bg = d != null ? Wx.tint(d) : R.drawable.tint_sunny; break;
            default: bg = R.drawable.glass_clear;
        }
        v.setImageViewResource(R.id.bg, bg);
        v.setInt(R.id.bg, "setImageAlpha", Math.round(s.opacity * 2.55f));

        // Only touch views that exist in this layout: RemoteViews fails the whole widget otherwise.
        v.setTextColor(R.id.place, ink.main);
        v.setTextColor(R.id.temp, ink.main);
        if (!kind.equals("clock")) v.setTextColor(R.id.cond, ink.main);
        v.setTextColor(R.id.hilo, ink.dim);
        v.setInt(R.id.pin, "setColorFilter", ink.main);
        if (kind.equals("clock")) {
            v.setTextColor(R.id.clock, ink.main);
            v.setTextColor(R.id.date, ink.main);
            v.setTextColor(R.id.place, ink.dim);
        }
        if (kind.equals("medium") || kind.equals("large")) {
            v.setTextColor(R.id.updated, ink.faint);
            v.setTextColor(R.id.feels, ink.faint);
            v.setInt(R.id.refresh, "setColorFilter", ink.dim);
            v.setInt(R.id.hours, "setBackgroundResource", frosted ? R.drawable.glass_inner_dark : R.drawable.glass_inner);
            if (interactive) v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx, kind));
        }

        // Tap anywhere: open the app on this widget's place.
        if (interactive) v.setOnClickPendingIntent(R.id.root, openIntent(ctx, widgetId, place));

        if (place == null) {
            v.setTextViewText(R.id.place, "Skyglass");
            v.setTextViewText(R.id.temp, "–");
            if (!kind.equals("clock")) v.setTextViewText(R.id.cond, "Open Skyglass to add a location");
            v.setTextViewText(R.id.hilo, "");
            v.setViewVisibility(R.id.pin, View.GONE);
            return v;
        }

        v.setTextViewText(R.id.place, place.name);
        v.setViewVisibility(R.id.pin, place.current ? View.VISIBLE : View.GONE);

        if (d == null) {
            v.setTextViewText(R.id.temp, "–");
            if (!kind.equals("clock")) v.setTextViewText(R.id.cond, "Updating…");
            v.setTextViewText(R.id.hilo, "");
            return v;
        }

        boolean f = s.fahrenheit;
        Wx.Day today = d.today();
        String cond = Wx.label(d.code, d.isDay);
        v.setTextViewText(R.id.temp, Wx.temp(d.temp, f));
        v.setImageViewResource(R.id.icon, Wx.icon(d.code, d.isDay));
        v.setContentDescription(R.id.root, place.name + ": " + Wx.temp(d.temp, f) + ", " + cond);
        if (!kind.equals("clock")) v.setTextViewText(R.id.cond, cond);
        if (today != null) {
            boolean compact = kind.equals("wide") || kind.equals("clock");
            v.setTextViewText(R.id.hilo, compact
                ? Wx.temp(today.max, f) + " / " + Wx.temp(today.min, f)
                : "H:" + Wx.temp(today.max, f) + "  L:" + Wx.temp(today.min, f));
        }

        if (kind.equals("medium") || kind.equals("large")) {
            v.setTextViewText(R.id.updated,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(d.fetchedAt)));
            String feels = "Feels like " + Wx.temp(d.feels, f);
            if (today != null && today.pop > 0) feels += " · Rain " + today.pop + "%";
            v.setTextViewText(R.id.feels, feels);
            boolean hasAlert = d.alertText != null && !d.alertText.isEmpty();
            if (kind.equals("medium")) {
                // 4×2 has no room for a badge: a warning takes the "feels like" line instead.
                if (hasAlert) {
                    v.setTextViewText(R.id.feels, "⚠ " + d.alertText);
                    v.setTextColor(R.id.feels, frosted ? 0xFFB45309 : 0xFFFDE68A);
                }
            } else if (hasAlert) {
                v.setViewVisibility(R.id.alert, View.VISIBLE);
                v.setTextViewText(R.id.alert, "⚠ " + d.alertText);
                int res;
                switch (d.alertColor == null ? "" : d.alertColor) {
                    case "red": res = R.drawable.alert_red; break;
                    case "orange": res = R.drawable.alert_orange; break;
                    case "advisory": res = R.drawable.alert_advisory; break;
                    default: res = R.drawable.alert_yellow;
                }
                v.setInt(R.id.alert, "setBackgroundResource", res);
                v.setTextColor(R.id.alert, "red".equals(d.alertColor) || "advisory".equals(d.alertColor) ? 0xFFFFFFFF : 0xFF1B2433);
            } else {
                v.setViewVisibility(R.id.alert, View.GONE);
            }
            int width = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int hours = width > 0 ? Math.max(4, Math.min(8, (width - 28) / 46)) : 6;
            fillHours(ctx, v, d, s, ink, hours, kind.equals("large"));
            if (kind.equals("large")) {
                int height = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
                int top = d.alertText != null && !d.alertText.isEmpty() ? 236 : 210;
                int days = height > 0 ? Math.max(2, Math.min(7, (height - top) / 30)) : 5;
                fillDays(ctx, v, d, s, ink, days);
            }
        }
        return v;
    }

    private static void fillHours(Context ctx, RemoteViews v, Wx.Data d, Store.Settings s, Ink ink, int count,
        boolean showPop) {
        v.removeAllViews(R.id.hours);
        long now = System.currentTimeMillis() / 1000;
        int added = 0;
        for (int i = 0; i < d.hours.size() && added < count; i++) {
            Wx.Hour h = d.hours.get(i);
            if (h.time + 3600 <= now) continue;
            RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_hour_item);
            boolean first = added == 0;
            item.setTextViewText(R.id.h_time, first ? "Now" : Wx.hour(h.time, d.utcOffset, s.h24));
            item.setImageViewResource(R.id.h_icon, first ? Wx.icon(d.code, d.isDay) : Wx.icon(h.code, h.day));
            item.setTextViewText(R.id.h_temp, Wx.temp(first ? d.temp : h.temp, s.fahrenheit));
            item.setTextColor(R.id.h_time, first ? ink.main : ink.dim);
            item.setTextColor(R.id.h_temp, ink.main);
            // The 4×2 widget has no height to spare: its rain chance is in the summary line.
            if (showPop && h.pop >= 20) {
                item.setViewVisibility(R.id.h_pop, View.VISIBLE);
                item.setTextViewText(R.id.h_pop, h.pop + "%");
                item.setTextColor(R.id.h_pop, ink.pop);
            }
            v.addView(R.id.hours, item);
            added++;
        }
    }

    private static void fillDays(Context ctx, RemoteViews v, Wx.Data d, Store.Settings s, Ink ink, int count) {
        v.removeAllViews(R.id.days);
        long now = System.currentTimeMillis() / 1000;
        int added = 0;
        for (Wx.Day day : d.days) {
            if (day.time + 86400 <= now) continue;
            if (added >= count) break;
            RemoteViews row = new RemoteViews(ctx.getPackageName(), R.layout.widget_day_row);
            row.setTextViewText(R.id.d_day, added == 0 ? "Today" : Wx.weekday(day.time, d.utcOffset));
            row.setImageViewResource(R.id.d_icon, Wx.icon(day.code, true));
            row.setTextViewText(R.id.d_pop, day.pop >= 20 ? day.pop + "%" : "");
            row.setTextViewText(R.id.d_min, Wx.temp(day.min, s.fahrenheit));
            row.setTextViewText(R.id.d_max, Wx.temp(day.max, s.fahrenheit));
            row.setTextColor(R.id.d_day, ink.main);
            row.setTextColor(R.id.d_min, ink.dim);
            row.setTextColor(R.id.d_max, ink.main);
            row.setTextColor(R.id.d_pop, ink.pop);
            v.addView(R.id.days, row);
            added++;
        }
    }

    static PendingIntent openIntent(Context ctx, int widgetId, Store.Place place) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        if (place != null) i.setData(Uri.parse("skyglass://place/" + Uri.encode(place.id)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 1000 + widgetId, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent refreshIntent(Context ctx, String kind) {
        Intent i = new Intent(ctx, providerFor(kind));
        i.setAction(BaseWidget.ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, kind.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
