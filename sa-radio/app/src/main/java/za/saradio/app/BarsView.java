package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Sound bars. When the app decodes the stream itself they follow the real audio; for streams the
 * system player handles (HLS), they fall back to a gentle animation.
 */
public class BarsView extends View {
    public static final int MINI = 0;
    public static final int WIDE = 1;

    private static volatile long lastWatched;

    /** The decoder only analyses audio while a visualiser is actually on screen. */
    static boolean anyoneWatching() {
        return SystemClock.uptimeMillis() - lastWatched < 1500;
    }

    private final int style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float[] raw = new float[Spectrum.BANDS];
    private float[] shown = new float[0];
    private boolean running;
    private int colorTop;
    private int colorBottom;
    private int shaderH = -1;

    public BarsView(Context c, int style, int color) {
        super(c);
        this.style = style;
        colorTop = color;
        colorBottom = color;
        paint.setColor(color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setColors(int top, int bottom) {
        colorTop = top;
        colorBottom = bottom;
        shaderH = -1;
        invalidate();
    }

    public void setRunning(boolean on) {
        if (running == on) return;
        running = on;
        invalidate();
    }

    private int barCount() {
        if (style == MINI) return 4;
        float unit = getResources().getDisplayMetrics().density * 7f;
        return Math.max(12, Math.min(64, (int) (getWidth() / unit)));
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = barCount();
        if (shown.length != n) shown = new float[n];
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (shaderH != (int) h) {
            shaderH = (int) h;
            paint.setShader(colorTop == colorBottom ? null
                    : new LinearGradient(0, 0, 0, h, colorTop, colorBottom, Shader.TileMode.CLAMP));
            if (colorTop == colorBottom) paint.setColor(colorTop);
        }

        boolean live = false;
        if (running) {
            lastWatched = SystemClock.uptimeMillis();
            live = Spectrum.read(raw);
        }
        float t = SystemClock.uptimeMillis() / 1000f;
        for (int i = 0; i < n; i++) {
            float target;
            if (!running) {
                target = 0f;
            } else if (live) {
                target = sample(i, n);
            } else {
                // Fallback animation: overlapping waves, livelier in the middle.
                float mid = 1f - Math.abs((i + 0.5f) / n - 0.5f) * 1.2f;
                double v = 0.5 + 0.25 * Math.sin(t * (5.3 + i % 5) + i * 1.7) + 0.25 * Math.sin(t * (2.1 + i % 3) + i * 0.6);
                target = (float) (v * (style == MINI ? 1f : mid));
            }
            float k = target > shown[i] ? 0.55f : 0.16f; // quick to rise, slow to fall
            shown[i] += (target - shown[i]) * k;
        }

        float gapFrac = style == MINI ? 0.45f : 0.6f;
        float barW = w / (n + (n - 1) * gapFrac);
        float gap = barW * gapFrac;
        float minH = style == MINI ? h * 0.22f : barW;
        for (int i = 0; i < n; i++) {
            float x = i * (barW + gap);
            float bh = Math.max(minH, shown[i] * h);
            if (style == WIDE) {
                float cy = h / 2;
                r.set(x, cy - bh / 2, x + barW, cy + bh / 2);
            } else {
                r.set(x, h - bh, x + barW, h);
            }
            c.drawRoundRect(r, barW / 2, barW / 2, paint);
        }

        boolean settling = false;
        for (float v : shown) if (v > 0.01f) settling = true;
        if ((running || settling) && isShown()) postInvalidateOnAnimation();
    }

    /** Maps the analyser's bands onto this view's bars. */
    private float sample(int i, int n) {
        int bands = Spectrum.BANDS;
        if (style == MINI) {
            // Four groups: bass, low-mid, high-mid, treble.
            int per = bands / 4;
            float sum = 0;
            for (int b = i * per; b < (i + 1) * per; b++) sum += raw[b];
            return Math.min(1f, sum / per * 1.25f);
        }
        float pos = (i + 0.5f) / n * (bands - 1);
        int a = (int) pos;
        int b = Math.min(bands - 1, a + 1);
        float f = pos - a;
        return raw[a] * (1 - f) + raw[b] * f;
    }
}
