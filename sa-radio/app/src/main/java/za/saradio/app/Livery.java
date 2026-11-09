package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Minibus-taxi livery: the slanted stripes painted down the side of a Quantum. */
final class Livery {
    private Livery() {}

    /** A thin band of slanted stripes, used across the top of screens. */
    static final class Band extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int color;

        Band(Context c, int stripeColor) {
            super(c);
            color = stripeColor;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setStripeColor(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = Ui.dp(getContext(), 1);
            c.drawColor(Ui.INK);
            p.setColor(color);
            float pitch = 40 * d, bar = 20 * d, slant = h;
            path.reset();
            for (float x = -pitch; x < w + pitch; x += pitch) {
                path.moveTo(x, h);
                path.lineTo(x + slant, 0);
                path.lineTo(x + slant + bar, 0);
                path.lineTo(x + bar, h);
                path.close();
            }
            c.drawPath(path, p);
        }
    }

    /**
     * A card with an ink outline and a run of slanted livery stripes up its right-hand end, like
     * the destination board in a taxi window.
     */
    static final class Board extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path clip = new Path();
        private final Path stripe = new Path();
        private final float density;
        private int fill;
        private int[] stripes;
        private float stripesWidthPx;
        private float radiusPx;
        private float shadowPx;
        private int shadowColor = Ui.INK;

        Board(Context c, int fill, int[] stripes, float stripesWidthDp) {
            density = c.getResources().getDisplayMetrics().density;
            this.fill = fill;
            this.stripes = stripes;
            stripesWidthPx = stripesWidthDp * density;
            radiusPx = 14 * density;
        }

        void setFill(int c, int[] stripeColors) {
            fill = c;
            stripes = stripeColors;
            invalidateSelf();
        }

        void setShadow(float dp, int color) {
            shadowPx = dp * density;
            shadowColor = color;
            invalidateSelf();
        }

        void setRadius(float dp) {
            radiusPx = dp * density;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float lw = Ui.line();
            if (shadowPx > 0) {
                p.setColor(shadowColor);
                r.set(b.left + shadowPx, b.top + shadowPx, b.right, b.bottom);
                c.drawRoundRect(r, radiusPx, radiusPx, p);
            }
            r.set(b.left, b.top, b.right - shadowPx, b.bottom - shadowPx);
            p.setColor(Ui.INK);
            c.drawRoundRect(r, radiusPx, radiusPx, p);
            r.inset(lw, lw);
            float ir = Math.max(0, radiusPx - lw);
            p.setColor(fill);
            c.drawRoundRect(r, ir, ir, p);
            if (stripes == null || stripes.length == 0 || stripesWidthPx <= 0) return;
            c.save();
            clip.reset();
            clip.addRoundRect(r, ir, ir, Path.Direction.CW);
            c.clipPath(clip);
            float h = r.height();
            float bar = 18 * density, pitch = 24 * density, lean = h * 0.23f;
            float x = r.right - stripesWidthPx + lean;
            for (int i = 0; x < r.right + bar + lean; i++, x += pitch) {
                stripe.reset();
                stripe.moveTo(x, r.top);
                stripe.lineTo(x + bar, r.top);
                stripe.lineTo(x + bar - lean, r.bottom);
                stripe.lineTo(x - lean, r.bottom);
                stripe.close();
                p.setColor(stripes[i % stripes.length]);
                c.drawPath(stripe, p);
            }
            c.restore();
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter cf) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
