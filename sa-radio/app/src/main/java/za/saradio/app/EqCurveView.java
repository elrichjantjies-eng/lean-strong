package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;

/** The equalizer curve. Drag a handle up or down to shape the sound; the result becomes "Custom". */
public class EqCurveView extends View {

    public interface Listener {
        void onBand(int band, float db);

        void onCommit(float[] db);
    }

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();

    private int[] bandsHz = EqPresets.DEFAULT_BANDS_HZ;
    private float[] db = new float[bandsHz.length];
    private float range = 12;
    private int dragging = -1;
    private boolean enabled = true;
    private Listener listener;
    private float[] xs = new float[0];

    public EqCurveView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        grid.setColor(0x1F000000);
        grid.setStrokeWidth(d);
        zero.setColor(0x66000000);
        zero.setStrokeWidth(d);
        zero.setPathEffect(new DashPathEffect(new float[] {4 * d, 4 * d}, 0));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Ui.ACCENT);
        knob.setColor(Ui.ACCENT);
        knobRing.setColor(Ui.BG);
        label.setColor(Ui.MUTED);
        label.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
        label.setTypeface(Ui.medium);
        label.setTextAlign(Paint.Align.CENTER);
        bubble.setColor(Ui.SURFACE3);
        setFocusable(true);
        setContentDescription("Equaliser curve. Drag the points up or down to change the sound.");
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setBands(int[] hz, float[] levels, float rangeDb) {
        bandsHz = hz;
        db = levels.clone();
        range = rangeDb;
        invalidate();
    }

    public void setLevels(float[] levels) {
        if (levels.length == db.length) {
            db = levels.clone();
            invalidate();
        }
    }

    public void setActive(boolean on) {
        enabled = on;
        setAlpha(on ? 1f : 0.45f);
        invalidate();
    }

    private float d(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float top() {
        return d(18);
    }

    private float bottom() {
        return getHeight() - d(26);
    }

    private float yFor(float v) {
        v = Math.max(-range, Math.min(range, v));
        float t = top(), b = bottom();
        return t + (b - t) * (1 - (v + range) / (2 * range));
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = db.length;
        if (n == 0 || getWidth() == 0) return;
        float padX = d(22);
        float w = getWidth() - padX * 2;
        if (xs.length != n) xs = new float[n];
        for (int i = 0; i < n; i++) xs[i] = padX + (n == 1 ? w / 2 : w * i / (n - 1));

        for (float g : new float[] {-range, -range / 2, range / 2, range}) c.drawLine(padX, yFor(g), padX + w, yFor(g), grid);
        c.drawLine(padX, yFor(0), padX + w, yFor(0), zero);
        for (float x : xs) c.drawLine(x, top(), x, bottom(), grid);

        // Smooth curve through the points (Catmull-Rom), extended flat to the edges.
        path.reset();
        path.moveTo(0, yFor(db[0]));
        path.lineTo(xs[0], yFor(db[0]));
        for (int i = 0; i < n - 1; i++) {
            float x0 = i > 0 ? xs[i - 1] : xs[i], y0 = yFor(i > 0 ? db[i - 1] : db[i]);
            float x1 = xs[i], y1 = yFor(db[i]);
            float x2 = xs[i + 1], y2 = yFor(db[i + 1]);
            float x3 = i + 2 < n ? xs[i + 2] : x2, y3 = yFor(i + 2 < n ? db[i + 2] : db[i + 1]);
            path.cubicTo(x1 + (x2 - x0) / 6, y1 + (y2 - y0) / 6, x2 - (x3 - x1) / 6, y2 - (y3 - y1) / 6, x2, y2);
        }
        path.lineTo(getWidth(), yFor(db[n - 1]));
        fillPath.set(path);
        fillPath.lineTo(getWidth(), bottom());
        fillPath.lineTo(0, bottom());
        fillPath.close();
        area.setShader(new LinearGradient(0, top(), 0, bottom(), Ui.alpha(Ui.ACCENT, 0.38f), Ui.alpha(Ui.ACCENT, 0.0f), Shader.TileMode.CLAMP));
        c.drawPath(fillPath, area);
        c.drawPath(path, line);

        for (int i = 0; i < n; i++) {
            float y = yFor(db[i]);
            boolean active = i == dragging;
            c.drawCircle(xs[i], y, d(active ? 11 : 9), knobRing);
            c.drawCircle(xs[i], y, d(active ? 9 : 7), knob);
            c.drawText(EqPresets.hzLabel(bandsHz[i]), xs[i], getHeight() - d(6), label);
            if (active) {
                String s = String.format(Locale.ROOT, "%+.1f dB", db[i]);
                float tw = label.measureText(s) + d(14);
                float by = y - d(30);
                if (by < 0) by = y + d(16);
                c.drawRoundRect(xs[i] - tw / 2, by, xs[i] + tw / 2, by + d(20), d(10), d(10), bubble);
                int old = label.getColor();
                label.setColor(Ui.TEXT);
                c.drawText(s, xs[i], by + d(14), label);
                label.setColor(old);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!enabled || xs.length == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int best = 0;
                for (int i = 1; i < xs.length; i++) if (Math.abs(xs[i] - e.getX()) < Math.abs(xs[best] - e.getX())) best = i;
                dragging = best;
                getParent().requestDisallowInterceptTouchEvent(true);
                move(e.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) move(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging >= 0) {
                    dragging = -1;
                    invalidate();
                    if (listener != null) listener.onCommit(db.clone());
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                }
                return true;
            default:
                return false;
        }
    }

    private void move(float y) {
        float t = top(), b = bottom();
        float v = range - (Math.max(t, Math.min(b, y)) - t) / (b - t) * 2 * range;
        v = Math.round(v * 2) / 2f; // half-dB steps
        if (Math.abs(v) < 0.75f) v = 0; // a little snap at flat
        if (v != db[dragging]) {
            db[dragging] = v;
            if (listener != null) listener.onBand(dragging, v);
        }
        invalidate();
    }
}
