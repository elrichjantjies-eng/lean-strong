package com.skyglass.weather.widget;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.skyglass.weather.MainActivity;
import com.skyglass.weather.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Weather warnings, "rain soon" and the daily forecast, as Android notifications. */
public final class Notifier {

    private Notifier() {}

    static final String CH_WARNINGS = "warnings";
    static final String CH_RAIN = "rain";
    static final String CH_DAILY = "daily";

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel w = new NotificationChannel(CH_WARNINGS, ctx.getString(R.string.channel_warnings),
            NotificationManager.IMPORTANCE_HIGH);
        w.setDescription(ctx.getString(R.string.channel_warnings_desc));
        NotificationChannel r = new NotificationChannel(CH_RAIN, ctx.getString(R.string.channel_rain),
            NotificationManager.IMPORTANCE_DEFAULT);
        r.setDescription(ctx.getString(R.string.channel_rain_desc));
        NotificationChannel d = new NotificationChannel(CH_DAILY, ctx.getString(R.string.channel_daily),
            NotificationManager.IMPORTANCE_LOW);
        d.setDescription(ctx.getString(R.string.channel_daily_desc));
        nm.createNotificationChannel(w);
        nm.createNotificationChannel(r);
        nm.createNotificationChannel(d);
    }

    private static boolean allowed(Context ctx) {
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    private static PendingIntent open(Context ctx, Wx.Data d) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse("skyglass://place/" + Uri.encode(d.placeId)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("MissingPermission") // checked by allowed()
    private static void post(Context ctx, int id, NotificationCompat.Builder b) {
        if (!allowed(ctx)) return;
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the post.
        }
    }

    /** One notification per new SAWS warning covering the place. */
    static void warnings(Context ctx, Store store, Wx.Data d, List<Saws.Warning> mine) {
        Set<String> seen = store.notifiedWarnings();
        Set<String> current = new HashSet<>();
        for (Saws.Warning w : mine) {
            current.add(w.id);
            if (seen.contains(w.id)) continue;
            String title = "SAWS " + Saws.colorName(w.color) + " warning" + (w.level > 0 ? " · level " + w.level : "")
                + ": " + w.event;
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_WARNINGS)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(title)
                .setContentText(w.headline)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(w.headline + "\n" + d.name))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(0xFFF59E0B)
                .setContentIntent(open(ctx, d))
                .setAutoCancel(true);
            post(ctx, 2000 + Math.abs(w.id.hashCode() % 1000), b);
        }
        // Remember only warnings that are still active, so the set doesn't grow forever.
        store.setNotifiedWarnings(current);
    }

    /** "Rain expected around 14:45" when it's dry now and rain is due within the hour. */
    static void rainSoon(Context ctx, Store store, Wx.Data d, Store.Settings s) {
        if (d.minutely.size() < 4) return;
        long now = System.currentTimeMillis() / 1000;
        if (now - store.lastRainAlert() / 1000 < 3 * 3600) return;
        int startIdx = (int) Math.max(0, (now - d.minutelyStart) / 900);
        if (startIdx >= d.minutely.size()) return;
        if (d.minutely.get(startIdx) >= 0.1) return; // already raining
        for (int i = startIdx + 1; i < Math.min(d.minutely.size(), startIdx + 5); i++) {
            double mm = d.minutely.get(i);
            if (mm >= 0.2) {
                long at = d.minutelyStart + i * 900L;
                String heavy = mm >= 2 ? "Heavy rain" : "Rain";
                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_RAIN)
                    .setSmallIcon(R.drawable.ic_stat_weather)
                    .setContentTitle(heavy + " expected around " + Wx.clock(at, d.utcOffset, s.h24))
                    .setContentText(d.name + ": you may want an umbrella.")
                    .setContentIntent(open(ctx, d))
                    .setAutoCancel(true)
                    .setTimeoutAfter(90 * 60_000L);
                post(ctx, 3000, b);
                store.setLastRainAlert(System.currentTimeMillis());
                return;
            }
        }
    }

    /** The morning summary. */
    static void daily(Context ctx, Store store, Wx.Data d, Store.Settings s) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(store.lastDaily())) return;
        Wx.Day t = d.today();
        if (t == null) return;
        String uv = t.uv >= 11 ? "extreme" : t.uv >= 8 ? "very high" : t.uv >= 6 ? "high" : t.uv >= 3 ? "moderate" : "low";
        String title = d.name + ": " + Wx.temp(t.max, s.fahrenheit) + " / " + Wx.temp(t.min, s.fahrenheit) + ", "
            + Wx.label(t.code, true).toLowerCase(Locale.US);
        StringBuilder text = new StringBuilder();
        text.append(t.pop > 0 ? t.pop + "% chance of rain" : "No rain expected");
        text.append(" · UV ").append(Math.round(t.uv)).append(" (").append(uv).append(")");
        text.append(" · Now ").append(Wx.temp(d.temp, s.fahrenheit));
        if (d.alertText != null) text.append("\n⚠ ").append(d.alertText);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_DAILY)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text.toString())
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text.toString()))
            .setContentIntent(open(ctx, d))
            .setAutoCancel(true);
        post(ctx, 4000, b);
        store.setLastDaily(today);
    }
}
