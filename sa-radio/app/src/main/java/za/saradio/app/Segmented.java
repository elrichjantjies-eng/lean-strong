package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A segmented switch, like a gear gate (e.g. Auto · Saver · Balanced · Best), with a sliding yellow thumb. */
public class Segmented extends LinearLayout {

    public interface Listener {
        void onPick(int index);
    }

    private final TextView[] items;
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float pos;
    private int selected = -1;
    private ValueAnimator anim;
    private Listener listener;

    public Segmented(Context c, String[] labels) {
        super(c);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        int p = Ui.line();
        setPadding(p, p, p, p);
        setBackground(Ui.roundStroke(Ui.PAPER, Ui.dp(c, 10), Ui.INK, Ui.line()));
        ink.setColor(Ui.INK);
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 13.5f, Ui.INK, Ui.W_BOLD);
            t.setGravity(Gravity.CENTER);
            // setSingleLine resets the minimum height, so it has to come first.
            t.setSingleLine(true);
            t.setMinHeight(Ui.dp(c, 44));
            t.setOnClickListener(v -> {
                if (idx != selected) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    select(idx, true);
                    if (listener != null) listener.onPick(idx);
                }
            });
            items[i] = t;
            addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void select(int index, boolean animate) {
        if (index == selected && !animate) return;
        selected = index;
        for (int i = 0; i < items.length; i++) {
            items[i].setTextColor(Ui.INK);
            items[i].setSelected(i == index);
        }
        if (anim != null) anim.cancel();
        if (!animate || getWidth() == 0) {
            pos = index;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (selected < 0 || items.length == 0) return;
        float inner = getWidth() - getPaddingLeft() - getPaddingRight();
        float w = inner / items.length;
        float left = getPaddingLeft() + w * pos;
        r.set(left, getPaddingTop(), left + w, getHeight() - getPaddingBottom());
        float rad = Ui.dp(getContext(), 7);
        // Gate lines between the gears.
        float lw = Ui.dp(getContext(), 1.5f);
        for (int i = 1; i < items.length; i++) {
            float x = getPaddingLeft() + w * i;
            c.drawRect(x - lw / 2, getPaddingTop(), x + lw / 2, getHeight() - getPaddingBottom(), ink);
        }
        thumb.setColor(Ui.INK);
        c.drawRoundRect(r, rad, rad, thumb);
        float in = Ui.line();
        r.inset(in, in);
        thumb.setColor(Ui.YELLOW);
        c.drawRoundRect(r, Math.max(0, rad - in), Math.max(0, rad - in), thumb);
    }
}
