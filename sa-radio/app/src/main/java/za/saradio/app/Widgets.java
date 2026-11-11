package za.saradio.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Shared plumbing for the home-screen widgets: the list of kinds, refreshes, intents and pictures. */
final class Widgets {
    private Widgets() {}

    static final String EXTRA_OPEN = "open";
    static final String OPEN_PLAYER = "player";
    static final String OPEN_FAVS = "favs";
    static final String OPEN_NEWS = "news";
    static final String OPEN_ARTICLE = "article";
    static final String OPEN_SPORT = "sport";

    /** One kind of widget, as listed in Settings. */
    static final class Kind {
        final Class<? extends AppWidgetProvider> provider;
        final int icon;
        final String title;
        final String blurb;

        Kind(Class<? extends AppWidgetProvider> provider, int icon, String title, String blurb) {
            this.provider = provider;
            this.icon = icon;
            this.title = title;
            this.blurb = blurb;
        }
    }

    static final Kind[] KINDS = {
        new Kind(RadioWidget.class, R.drawable.ic_nav_radio, "Pocket radio", "A little radio: tune ◀ ▶, play and switch band"),
        new Kind(PresetsWidget.class, R.drawable.ic_star, "Presets", "Your first six favourites, one tap each, like a car radio"),
        new Kind(HeadlinesWidget.class, R.drawable.ic_nav_news, "Headlines", "The latest stories; tap one to read it"),
        new Kind(StoryWidget.class, R.drawable.ic_text_size, "Read here", "Flip through stories and read them in the widget"),
        new Kind(SportWidget.class, R.drawable.ic_trophy, "Scoreboard", "Live scores, fixtures and results, SA focus"),
    };

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> ASKED = Collections.synchronizedSet(new HashSet<>());
    private static Context app;
    private static final Runnable RADIO = () -> {
        if (app == null) return;
        RadioWidget.updateAll(app);
        PresetsWidget.updateAll(app);
    };

    /** Asks the launcher to add a widget (Android 8+ launchers that support it). */
    static boolean pin(Context c, Kind k) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new ComponentName(c, k.provider), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    static int[] ids(Context c, Class<?> provider) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            return m == null ? new int[0] : m.getAppWidgetIds(new ComponentName(c, provider));
        } catch (Exception e) {
            return new int[0];
        }
    }

    static boolean any(Context c, Class<?> provider) {
        return ids(c, provider).length > 0;
    }

    /** Playback, station or favourites changed: redraw the radio and presets (coalesced). */
    static void radioChanged(Context c) {
        app = c.getApplicationContext();
        MAIN.removeCallbacks(RADIO);
        MAIN.postDelayed(RADIO, 150);
    }

    static void newsChanged(Context c) {
        final Context ac = c.getApplicationContext();
        MAIN.post(() -> {
            HeadlinesWidget.updateAll(ac);
            StoryWidget.updateAll(ac);
        });
    }

    static void sportChanged(Context c) {
        final Context ac = c.getApplicationContext();
        MAIN.post(() -> SportWidget.updateAll(ac));
    }

    // ---- Intents ----------------------------------------------------------------------------

    static Intent openIntent(Context c, String what) {
        return new Intent(c, MainActivity.class)
                .setAction("za.saradio.app.OPEN_" + what)
                .putExtra(EXTRA_OPEN, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    static PendingIntent open(Context c, String what, int code) {
        return PendingIntent.getActivity(c, code, openIntent(c, what), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** A template for list rows, which fill in their own details. Fill-ins need a mutable intent. */
    static PendingIntent openTemplate(Context c, String what, int code) {
        int mutable = android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        return PendingIntent.getActivity(c, code, openIntent(c, what), mutable | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent broadcast(Context c, Class<?> provider, String action, int widgetId, boolean mutable) {
        Intent i = new Intent(c, provider).setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("saradio://widget/" + widgetId + "/" + action));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (mutable && android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        return PendingIntent.getBroadcast(c, widgetId, i, flags);
    }

    static PendingIntent tune(Context c, int dir) {
        Intent i = new Intent(c, RadioService.class).setAction(RadioService.ACTION_TUNE).putExtra(RadioService.EXTRA_DIR, dir);
        return PendingIntent.getForegroundService(c, dir > 0 ? 301 : 302, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent toggle(Context c) {
        Intent i = new Intent(c, RadioService.class).setAction(RadioService.ACTION_TOGGLE);
        return PendingIntent.getForegroundService(c, 303, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Plays a station and switches the tuning band (presets tune through favourites). */
    static PendingIntent play(Context c, Station s, int band, int code) {
        Intent i = new Intent(c, RadioService.class).setAction(RadioService.ACTION_PLAY)
                .putExtra(RadioService.EXTRA_STATION, s.toJson().toString())
                .putExtra(RadioService.EXTRA_BAND, band)
                .setData(Uri.parse("saradio://play/" + Uri.encode(s.key())));
        return PendingIntent.getForegroundService(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---- Sizes and pictures -----------------------------------------------------------------

    /** The widget's size in dp: {width, height}, using the portrait width and height. */
    static int[] sizeDp(Context c, int id, int defW, int defH) {
        try {
            Bundle o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            return new int[] {w > 0 ? w : defW, h > 0 ? h : defH};
        } catch (Exception e) {
            return new int[] {defW, defH};
        }
    }

    /** Pixels per dp for widget pictures: sharp enough, small enough to send to the launcher. */
    static float scale(Context c) {
        return Math.min(2f, c.getResources().getDisplayMetrics().density);
    }

    /**
     * A station's logo in a white roundel with an ink ring, on an opaque {@code ground} so the
     * picture can be stored compactly. Fetches a missing logo in the background and redraws.
     */
    static Bitmap roundel(final Context c, Station s, int px, int ground) {
        Ui.init(c);
        ImageLoader.init(c);
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(b);
        cv.drawColor(ground);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = px / 2f, ring = Math.max(2f, px * 0.045f);
        p.setColor(Ui.INK);
        cv.drawCircle(r, r, r - 0.5f, p);
        Bitmap logo = null;
        if (s != null && s.favicon != null && !s.favicon.isEmpty()) {
            logo = ImageLoader.cachedAny(s.favicon);
            if (logo == null) logo = ImageLoader.fromDisk(s.favicon, Math.max(px, 96));
            if (logo == null && ASKED.add(s.favicon)) {
                ImageLoader.get(s.favicon, Math.max(px, 96), new ImageLoader.Target() {
                    @Override
                    public boolean wants(String url) {
                        return true;
                    }

                    @Override
                    public void onBitmap(String url, Bitmap bm) {
                        radioChanged(c);
                    }
                });
            }
        }
        int plate = Color.WHITE;
        if (logo != null) {
            try {
                int corner = logo.getPixel(Math.min(2, logo.getWidth() - 1), Math.min(2, logo.getHeight() - 1));
                if (Color.alpha(corner) > 200) plate = corner | 0xFF000000;
            } catch (Exception ignored) {
            }
        }
        p.setColor(plate);
        cv.drawCircle(r, r, r - ring, p);
        if (logo != null) {
            float box = (r - ring) * 2 * (plate == Color.WHITE ? 0.72f : 1f);
            float lw = logo.getWidth(), lh = logo.getHeight();
            float sc = plate == Color.WHITE ? Math.min(box / lw, box / lh) : Math.max(box / lw, box / lh);
            float dw = lw * sc, dh = lh * sc;
            cv.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(r, r, r - ring, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            cv.drawBitmap(logo, null, new RectF(r - dw / 2, r - dh / 2, r + dw / 2, r + dh / 2), p);
            cv.restore();
        } else {
            String name = s == null ? "" : s.name;
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setTypeface(Ui.sign);
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(s == null ? Ui.INK : LogoView.monoColor(name));
            t.setTextSize(px * 0.42f);
            String letter = s == null ? "♪" : LogoView.monoLetter(name);
            cv.drawText(letter, r, r - (t.descent() + t.ascent()) / 2, t);
        }
        return b;
    }
}
