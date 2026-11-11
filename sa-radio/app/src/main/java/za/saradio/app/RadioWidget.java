package za.saradio.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * The pocket radio: a little yellow radio with an aerial, the station's logo on the speaker, a
 * tuning dial, ◀ ▶ to change station, play/stop, and a band switch (A–Z, MHz or favourites).
 */
public class RadioWidget extends AppWidgetProvider {
    static final String ACTION_BAND = "za.saradio.app.widget.BAND";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        update(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_BAND.equals(i.getAction())) {
            // Cycle A–Z → MHz → Favs.
            int b = (Tuner.band(c) + 1) % Tuner.LABELS.length;
            Tuner.setBand(c, b);
            updateAll(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : Widgets.ids(c, RadioWidget.class)) update(c, m, id);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        try {
            m.updateAppWidget(id, build(c, id));
        } catch (Exception ignored) {
            // A launcher that has gone away, or a picture too big to send: try again next time.
        }
    }

    static RemoteViews build(Context c, int id) {
        int[] size = Widgets.sizeDp(c, id, 300, 150);
        return build(c, id, size[0], size[1]);
    }

    static RemoteViews build(Context c, int id, int wDp, int hDp) {
        Ui.init(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_radio);
        Station s = RadioService.current != null ? RadioService.current : Prefs.lastStation(c);
        boolean active = RadioService.isActive();
        float sc = Widgets.scale(c);

        // The dial fills the right two-thirds of the body, above the buttons.
        float bodyW = wDp - 4 - 24, bodyH = hDp - 4 - 17 - 24;
        int dialW = Math.max(120, Math.round((bodyW - 10) * 0.66f));
        int dialH = Math.max(40, Math.round(bodyH - 48));
        Dial.State st = Dial.State.of(c, s);
        if (s == null) st.name = "Tap ▶ to tune in";
        v.setImageViewBitmap(R.id.w_dial, dial(c, dialW, dialH, sc, st));
        v.setContentDescription(R.id.w_dial, s == null ? "Tuning dial" : "Tuned to " + s.name
                + (st.freq > 0 ? ", " + Freq.label(st.freq) + " megahertz" : ""));

        // The station's logo on the speaker.
        int speakerW = Math.round((bodyW - 10) * 0.34f);
        int logoDp = Math.max(40, Math.min(72, Math.min(speakerW - 14, Math.round(bodyH) - 14)));
        v.setImageViewBitmap(R.id.w_logo, Widgets.roundel(c, s, Math.round(logoDp * sc), Ui.INK));

        v.setImageViewResource(R.id.w_play, active ? R.drawable.ic_stop : R.drawable.ic_play);
        v.setContentDescription(R.id.w_play, active ? "Stop" : "Play");
        int band = Tuner.band(c);
        v.setTextViewText(R.id.w_band, "BAND\n" + Tuner.shortLabel(band));
        v.setContentDescription(R.id.w_band, "Band: " + Tuner.name(band) + ". Tap to change.");

        v.setOnClickPendingIntent(R.id.w_prev, Widgets.tune(c, -1));
        v.setOnClickPendingIntent(R.id.w_next, Widgets.tune(c, 1));
        v.setOnClickPendingIntent(R.id.w_play, s != null ? Widgets.toggle(c) : Widgets.open(c, Widgets.OPEN_PLAYER, 401));
        v.setOnClickPendingIntent(R.id.w_band, Widgets.broadcast(c, RadioWidget.class, ACTION_BAND, id, false));
        v.setOnClickPendingIntent(R.id.w_speaker, Widgets.open(c, Widgets.OPEN_PLAYER, 401));
        v.setOnClickPendingIntent(R.id.w_dial, Widgets.open(c, Widgets.OPEN_PLAYER, 401));
        return v;
    }

    /** The dial picture, drawn on the radio's yellow so it can be stored without transparency. */
    private static Bitmap dial(Context c, int wDp, int hDp, float sc, Dial.State st) {
        Bitmap b = Bitmap.createBitmap(Math.round(wDp * sc), Math.round(hDp * sc), Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        cv.drawColor(Ui.YELLOW);
        Dial.paint(cv, b.getWidth(), b.getHeight(), sc, st, st.needle, 0);
        return b;
    }
}
