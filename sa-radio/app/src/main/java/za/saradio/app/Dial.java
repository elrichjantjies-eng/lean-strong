package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.List;
import java.util.Locale;

/**
 * The tuning window: an ink panel with an FM scale, a red needle, the current station's name and
 * the stations either side. The app shows it on Now Playing and the radio widget draws the same
 * picture into a bitmap.
 */
public class Dial extends View {

    /** What the dial shows. */
    static final class State {
        String prev = "";
        String name = "";
        String next = "";
        float freq;
        int index = -1;
        int count;
        int band = Tuner.AZ;
        /** Where the needle sits along the scale, 0 to 1. */
        float needle = 0.5f;

        static State of(Context c, Station cur) {
            State s = new State();
            if (cur == null) return s;
            s.name = cur.name;
            s.freq = Freq.of(cur);
            s.band = Tuner.band(c);
            List<Station> d = Tuner.dial(c, s.band);
            if (d.isEmpty() && s.band == Tuner.FAVS) d = Tuner.dial(c, Tuner.allBand(c));
            s.count = d.size();
            s.index = Tuner.indexOf(d, cur);
            if (d.size() >= 2) {
                Station p = Tuner.step(d, cur, -1), n = Tuner.step(d, cur, 1);
                s.prev = p == null ? "" : p.name;
                s.next = n == null ? "" : n.name;
            }
            if (s.freq > 0) s.needle = (s.freq - Freq.MIN) / (Freq.MAX - Freq.MIN);
            else if (s.index >= 0 && s.count > 1) s.needle = s.index / (float) (s.count - 1);
            s.needle = Math.max(0.02f, Math.min(0.98f, s.needle));
            return s;
        }

        String detail() {
            StringBuilder sb = new StringBuilder();
            if (freq > 0) sb.append(Freq.label(freq)).append(" MHz");
            if (index >= 0 && count > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(index + 1).append(" of ").append(count);
            }
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(Tuner.shortLabel(band));
            return sb.toString();
        }
    }

    private State state = new State();
    private float shownNeedle = 0.5f;
    private float slide;
    private ValueAnimator anim;

    public Dial(Context c) {
        super(c);
        setContentDescription("Tuning dial");
    }

    void setState(State s, boolean animate) {
        boolean changed = !s.name.equals(state.name);
        final float fromNeedle = shownNeedle;
        final int dir = s.index > state.index ? 1 : -1;
        state = s;
        setContentDescription("Tuned to " + s.name + (s.freq > 0 ? ", " + Freq.label(s.freq) + " megahertz" : "")
                + (s.next.isEmpty() ? "" : ". Next: " + s.next) + (s.prev.isEmpty() ? "" : ". Previous: " + s.prev));
        if (anim != null) anim.cancel();
        if (!animate || !changed || getWidth() == 0) {
            shownNeedle = s.needle;
            slide = 0;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            shownNeedle = fromNeedle + (state.needle - fromNeedle) * t;
            slide = (1 - t) * dir;
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        paint(c, getWidth(), getHeight(), getResources().getDisplayMetrics().density, state, shownNeedle, slide);
    }

    /** Draws the dial for the widget. */
    static Bitmap render(Context c, int wPx, int hPx, State s) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, wPx), Math.max(1, hPx), Bitmap.Config.ARGB_8888);
        Ui.init(c);
        paint(new Canvas(b), wPx, hPx, c.getResources().getDisplayMetrics().density, s, s.needle, 0);
        return b;
    }

    static void paint(Canvas c, float w, float h, float d, State s, float needle, float slide) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF(0, 0, w, h);
        float rad = 10 * d;
        p.setColor(Ui.INK);
        c.drawRoundRect(r, rad, rad, p);

        // The FM scale along the top: a tick every MHz, numbers every 4 (not on a very short dial).
        boolean compact = h < 64 * d;
        float left = 14 * d, right = w - 14 * d, top = compact ? 6 * d : 9 * d;
        TextPaint num = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        num.setTypeface(Ui.bold);
        num.setTextSize(8.5f * d);
        num.setColor(0xFF9A9A9A);
        num.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFF6E6E6E);
        for (int f = 88; f <= 108; f++) {
            float x = left + (f - Freq.MIN) / (Freq.MAX - Freq.MIN) * (right - left);
            boolean major = f % 4 == 0;
            float len = (compact ? (major ? 6 : 3.5f) : (major ? 9 : 5)) * d;
            c.drawRect(x - 0.6f * d, top, x + 0.6f * d, top + len, p);
            if (major && !compact) c.drawText(String.valueOf(f), x, top + len + 9 * d, num);
        }
        float scaleBottom = top + (compact ? 7 : 21) * d;

        // Rows from the bottom: the stations either side (when there's room), the detail line, the name.
        boolean sides = h >= 90 * d;
        boolean detailLine = h >= 46 * d;
        float sideBase = h - 9 * d;
        float base = sides ? sideBase - 15 * d : h - (compact ? 7 : 10) * d;
        float shift = slide * w * 0.25f;
        if (sides) {
            TextPaint side = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            side.setTypeface(Ui.bold);
            side.setTextSize(10f * d);
            side.setColor(0xFFA8A8A8);
            float sideW = w * 0.42f;
            if (!s.prev.isEmpty()) {
                c.drawText("‹ " + TextUtils.ellipsize(s.prev, side, sideW, TextUtils.TruncateAt.END), 10 * d, sideBase, side);
            }
            if (!s.next.isEmpty()) {
                side.setTextAlign(Paint.Align.RIGHT);
                c.drawText(TextUtils.ellipsize(s.next, side, sideW, TextUtils.TruncateAt.END) + " ›", w - 10 * d, sideBase, side);
            }
        }
        TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        name.setTypeface(Ui.display);
        name.setColor(Ui.YELLOW);
        name.setTextAlign(Paint.Align.CENTER);
        float mid = w * 0.8f;
        float nameBaseGuess = detailLine ? base - 13 * d : base;
        float size = Math.max(10 * d, Math.min(22 * d, (nameBaseGuess - scaleBottom) * 0.92f));
        name.setTextSize(size);
        String label = s.name.toUpperCase(Locale.ROOT);
        while (name.measureText(label) > mid && size > 12 * d) {
            size -= d;
            name.setTextSize(size);
        }
        label = TextUtils.ellipsize(label, name, mid, TextUtils.TruncateAt.END).toString();
        float nameBase = detailLine ? base - 13 * d : base;
        c.drawText(label, w / 2f + shift, nameBase, name);
        TextPaint det = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        det.setTypeface(Ui.bold);
        det.setTextSize(9.5f * d);
        det.setLetterSpacing(0.08f);
        det.setColor(0xFFFFFFFF);
        det.setTextAlign(Paint.Align.CENTER);
        if (detailLine) {
            String detail = TextUtils.ellipsize(s.detail(), det, mid, TextUtils.TruncateAt.END).toString();
            c.drawText(detail, w / 2f + shift, base, det);
        }

        // The needle.
        float nx = left + needle * (right - left);
        p.setColor(Ui.SIGN_RED);
        c.drawRect(nx - 1.5f * d, top - 3 * d, nx + 1.5f * d, nameBase - size - 2 * d, p);
        c.drawCircle(nx, top - 1 * d, 3.2f * d, p);
    }
}
