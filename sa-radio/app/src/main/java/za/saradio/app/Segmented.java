package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A segmented switch (e.g. Auto · Saver · Balanced · Best) with a sliding gold thumb. */
public class Segmented extends LinearLayout {

    public interface Listener {
        void onPick(int index);
    }

    private final TextView[] items;
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float pos;
    private int selected = -1;
    private ValueAnimator anim;
    private Listener listener;

    public Segmented(Context c, String[] labels) {
        super(c);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        int p = Ui.dp(c, 4);
        setPadding(p, p, p, p);
        setBackground(Ui.roundStroke(Ui.SURFACE2, Ui.dp(c, 16), Ui.STROKE, Ui.dp(c, 1)));
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 13.5f, Ui.TEXT2, Ui.W_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(c, 40));
            t.setSingleLine(true);
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
            items[i].setTextColor(i == index ? Ui.ON_GOLD : Ui.TEXT2);
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
        float rad = Ui.dp(getContext(), 12);
        thumb.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0xFFFFCF5A, Ui.GOLD, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, thumb);
    }
}
