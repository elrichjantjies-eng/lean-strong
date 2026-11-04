package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Icon-only bottom navigation. The selected tab sits on a gold pill that glides between icons,
 * so the current tab is obvious without text labels. Each icon still has a spoken label and a
 * long-press tooltip.
 */
public class NavBar extends LinearLayout {

    public interface Listener {
        void onTab(int index);
    }

    private final ImageView[] icons;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float pos;
    private int selected;
    private ValueAnimator anim;
    private Listener listener;

    public NavBar(Context c, int[] iconRes, String[] labels) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        int pad = Ui.dp(c, 6);
        setPadding(pad, pad, pad, pad);
        float radius = Ui.dp(c, 30);
        setBackground(Ui.roundStroke(Ui.alpha(Ui.SURFACE2, 0.97f), radius, Ui.STROKE2, Ui.dp(c, 1)));
        setElevation(Ui.dp(c, 12));
        icons = new ImageView[iconRes.length];
        for (int i = 0; i < iconRes.length; i++) {
            final int idx = i;
            FrameLayout slot = new FrameLayout(c);
            ImageView iv = new ImageView(c);
            iv.setImageResource(iconRes[i]);
            iv.setColorFilter(Ui.MUTED);
            slot.addView(iv, new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.CENTER));
            slot.setContentDescription(labels[i]);
            slot.setTooltipText(labels[i]);
            slot.setClickable(true);
            slot.setFocusable(true);
            slot.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) listener.onTab(idx);
            });
            Ui.press(slot, 0.88f);
            icons[i] = iv;
            addView(slot, new LayoutParams(0, Ui.dp(c, 48), 1f));
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void select(int index, boolean animate) {
        selected = index;
        for (int i = 0; i < icons.length; i++) {
            icons[i].setColorFilter(i == index ? Ui.ON_GOLD : Ui.MUTED);
            View slot = (View) icons[i].getParent();
            slot.setSelected(i == index);
            slot.setContentDescription(slot.getTooltipText() + (i == index ? ", selected" : ""));
        }
        if (anim != null) anim.cancel();
        if (!animate || getWidth() == 0) {
            pos = index;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(340);
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (icons.length == 0) return;
        float inner = getWidth() - getPaddingLeft() - getPaddingRight();
        float slotW = inner / icons.length;
        float pillW = Math.min(slotW - Ui.dp(getContext(), 8), Ui.dp(getContext(), 76));
        float cx = getPaddingLeft() + slotW * (pos + 0.5f);
        float h = Ui.dp(getContext(), 42);
        float cy = getHeight() / 2f;
        r.set(cx - pillW / 2, cy - h / 2, cx + pillW / 2, cy + h / 2);
        pill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0xFFFFCF5A, Ui.GOLD, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, h / 2, h / 2, pill);
    }

    public int selected() {
        return selected;
    }
}
