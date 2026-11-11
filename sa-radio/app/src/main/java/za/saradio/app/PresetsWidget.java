package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

/** Car-radio presets: your first six favourites as numbered buttons. Tap to play; tap the playing one to stop. */
public class PresetsWidget extends AppWidgetProvider {
    private static final int[] SLOTS = {R.id.w_slot1, R.id.w_slot2, R.id.w_slot3, R.id.w_slot4, R.id.w_slot5, R.id.w_slot6};
    private static final int[] LOGOS = {R.id.w_slot1_logo, R.id.w_slot2_logo, R.id.w_slot3_logo, R.id.w_slot4_logo,
        R.id.w_slot5_logo, R.id.w_slot6_logo};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        update(c, m, id);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : Widgets.ids(c, PresetsWidget.class)) update(c, m, id);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        try {
            m.updateAppWidget(id, build(c, id));
        } catch (Exception ignored) {
        }
    }

    static RemoteViews build(Context c, int id) {
        int[] size = Widgets.sizeDp(c, id, 300, 70);
        return build(c, size[0], size[1]);
    }

    static RemoteViews build(Context c, int wDp, int hDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_presets);
        List<Station> favs = Prefs.favourites(c);
        boolean empty = favs.isEmpty();
        v.setViewVisibility(R.id.w_empty, empty ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.w_slots, empty ? View.GONE : View.VISIBLE);
        if (empty) {
            v.setOnClickPendingIntent(R.id.w_root, Widgets.open(c, Widgets.OPEN_FAVS, 402));
            return v;
        }
        Station cur = RadioService.current != null ? RadioService.current : Prefs.lastStation(c);
        boolean active = RadioService.isActive();
        float sc = Widgets.scale(c);
        // Each slot is a sixth of the strip, less its margins; the logo fills most of it.
        int slotW = (wDp - 19 - 25) / 6, slotH = hDp - 17;
        int logoDp = Math.max(24, Math.min(64, Math.min(slotW, slotH) - 10));
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < favs.size()) {
                Station s = favs.get(i);
                boolean on = cur != null && cur.key().equals(s.key()) && active;
                v.setInt(SLOTS[i], "setBackgroundResource", on ? R.drawable.w_slot_on : R.drawable.w_slot_off);
                v.setImageViewBitmap(LOGOS[i], Widgets.roundel(c, s, Math.round(logoDp * sc), on ? Ui.YELLOW : Ui.PAPER));
                v.setInt(LOGOS[i], "setColorFilter", 0);
                v.setContentDescription(SLOTS[i], "Preset " + (i + 1) + ", " + s.name + (on ? ", playing. Tap to stop." : ""));
                v.setOnClickPendingIntent(SLOTS[i], on ? Widgets.toggle(c) : Widgets.play(c, s, Tuner.FAVS, 410 + i));
            } else {
                v.setInt(SLOTS[i], "setBackgroundResource", R.drawable.w_slot_off);
                v.setImageViewResource(LOGOS[i], R.drawable.ic_plus);
                v.setInt(LOGOS[i], "setColorFilter", Ui.MUTED);
                v.setContentDescription(SLOTS[i], "Preset " + (i + 1) + ", empty. Add favourites in SA Radio.");
                v.setOnClickPendingIntent(SLOTS[i], Widgets.open(c, Widgets.OPEN_FAVS, 402));
            }
        }
        return v;
    }
}
