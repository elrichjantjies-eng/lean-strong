package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Icon-only bottom navigation drawn as a stretch of road: a yellow edge line, a dashed white lane
 * line, and one number plate per tab. The current tab's plate is yellow and lifted off the road.
 * Each plate has a spoken label and a long-press tooltip.
 */
public class NavBar extends LinearLayout {

    public interface Listener {
        void onTab(int index);
    }

    /** The coloured band across the top of each white plate. */
    private static final int[] BANDS = {Ui.LIVERY_GREEN, Ui.PINK, Ui.COBALT, Ui.SIGN_RED};

    private final Plate[] plates;
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected;
    private int bottomInset;
    private Listener listener;

    public NavBar(Context c, int[] iconRes, String[] labels) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        setBackgroundColor(Ui.ROAD);
        edge.setColor(Ui.YELLOW);
        lane.setColor(0xFFFFFFFF);
        applyPadding();
        plates = new Plate[iconRes.length];
        for (int i = 0; i < iconRes.length; i++) {
            final int idx = i;
            Plate p = new Plate(c, iconRes[i], BANDS[i % BANDS.length]);
            p.setContentDescription(labels[i]);
            p.setTooltipText(labels[i]);
            p.setClickable(true);
            p.setFocusable(true);
            p.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) listener.onTab(idx);
            });
            plates[i] = p;
            FrameLayout slot = new FrameLayout(c);
            slot.setClipChildren(false);
            slot.addView(p, new FrameLayout.LayoutParams(Ui.dp(c, iconRes.length > 3 ? 72 : 88), Ui.dp(c, 50), Gravity.CENTER));
            addView(slot, new LayoutParams(0, Ui.dp(c, 64), 1f));
        }
        setClipChildren(false);
    }

    /** The road runs under the system navigation bar; keep the plates above it. */
    public void setBottomInset(int px) {
        if (px == bottomInset) return;
        bottomInset = px;
        applyPadding();
    }

    private void applyPadding() {
        Context c = getContext();
        setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 8) + bottomInset);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void select(int index, boolean animate) {
        selected = index;
        for (int i = 0; i < plates.length; i++) {
            boolean on = i == index;
            Plate p = plates[i];
            p.setSelected(on);
            p.setActive(on);
            CharSequence label = p.getTooltipText();
            p.setContentDescription(label + (on ? ", selected" : ""));
            float lift = on ? -Ui.dp(getContext(), 5) : 0;
            if (animate) {
                p.animate().translationY(lift).setDuration(260).setInterpolator(new OvershootInterpolator(2f)).start();
            } else {
                p.setTranslationY(lift);
            }
        }
    }

    public int selected() {
        return selected;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float d = Ui.dp(getContext(), 1);
        // Yellow edge line along the top of the road.
        c.drawRect(0, 0, w, 5 * d, edge);
        // Dashed lane line through the middle of the plates.
        float y = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f - 2 * d;
        float dash = 24 * d, gap = 20 * d;
        for (float x = -dash / 2; x < w; x += dash + gap) c.drawRect(x, y, x + dash, y + 4 * d, lane);
    }

    /** One number plate: white (or yellow when current), ink border, coloured band and two screws. */
    static final class Plate extends FrameLayout {
        private final ImageView icon;
        private final int band;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private boolean active;

        Plate(Context c, int iconRes, int band) {
            super(c);
            this.band = band;
            setWillNotDraw(false);
            icon = new ImageView(c);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Ui.INK);
            addView(icon, new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
            Ui.press(this, 0.92f);
        }

        void setActive(boolean on) {
            active = on;
            icon.setScaleX(on ? 1.1f : 1f);
            icon.setScaleY(on ? 1.1f : 1f);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = Ui.dp(getContext(), 1);
            float w = getWidth(), h = getHeight();
            float rad = 8 * d, lw = Ui.line();
            float sh = active ? 5 * d : 0;
            if (!active) {
                // A thin white ring so the plate's ink border reads against the dark road.
                p.setColor(0xFFFFFFFF);
                r.set(0, 0, w, h);
                c.drawRoundRect(r, rad + 2 * d, rad + 2 * d, p);
            } else {
                p.setColor(0xFF000000);
                r.set(2 * d, 2 * d + sh, w - 2 * d, h);
                c.drawRoundRect(r, rad, rad, p);
            }
            float in = 2 * d;
            r.set(in, in, w - in, h - in - sh);
            p.setColor(Ui.INK);
            c.drawRoundRect(r, rad, rad, p);
            r.inset(lw, lw);
            p.setColor(active ? Ui.YELLOW : 0xFFFFFFFF);
            c.drawRoundRect(r, rad - lw / 2, rad - lw / 2, p);
            // Coloured band across the top.
            c.save();
            c.clipRect(r.left, r.top, r.right, r.top + 5 * d);
            p.setColor(active ? Ui.INK : band);
            c.drawRoundRect(r, rad - lw / 2, rad - lw / 2, p);
            c.restore();
            // Two screws.
            p.setColor(active ? Ui.INK : 0xFF9A9A9A);
            float cy = (r.top + 5 * d + r.bottom) / 2f;
            c.drawCircle(r.left + 7 * d, cy, 2.6f * d, p);
            c.drawCircle(r.right - 7 * d, cy, 2.6f * d, p);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int rr, int b) {
            super.onLayout(changed, l, t, rr, b);
            // Keep the icon centred on the plate face (above the lift shadow).
            int sh = active ? Ui.dp(getContext(), 5) : 0;
            View v = icon;
            int cx = (getWidth() - v.getWidth()) / 2, cy = (getHeight() - sh - v.getHeight()) / 2 + Ui.dp(getContext(), 2);
            v.layout(cx, cy, cx + v.getWidth(), cy + v.getHeight());
        }
    }
}
