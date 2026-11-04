package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The background: deep near-black with soft glows. On the main screens the glows borrow the
 * flag's gold, green and blue; on Now Playing they take the station's own colour.
 */
public class BackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] colors = {Ui.GOLD, Ui.SA_GREEN, Ui.SA_BLUE};
    private float[] strength = {0.16f, 0.20f, 0.34f};
    private int from = Ui.BG;
    private ValueAnimator anim;
    private int[] targetColors;

    public BackdropView(Context c) {
        super(c);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Tints the glows towards one colour (smoothly). */
    public void setMood(final int color) {
        final int[] start = colors.clone();
        final int[] end = {Ui.lighten(color, 0.1f), color, Ui.darken(color, 0.25f)};
        targetColors = end;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < 3; i++) colors[i] = Ui.blend(start[i], end[i], t);
            invalidate();
        });
        strength = new float[] {0.42f, 0.55f, 0.30f};
        if (getWidth() == 0) {
            colors = end.clone();
            invalidate();
        } else {
            anim.start();
        }
    }

    public void setBase(int color) {
        from = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(from);
        glow(c, w * 0.08f, h * 0.02f, Math.max(w, h) * 0.55f, colors[0], strength[0]);
        glow(c, w * 1.0f, h * 0.20f, Math.max(w, h) * 0.50f, colors[1], strength[1]);
        glow(c, w * 0.30f, h * 1.02f, Math.max(w, h) * 0.60f, colors[2], strength[2]);
    }

    private void glow(Canvas c, float x, float y, float r, int color, float a) {
        paint.setShader(new RadialGradient(x, y, r, new int[] {Ui.alpha(color, a), Ui.alpha(color, a * 0.35f), 0},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, paint);
    }
}
