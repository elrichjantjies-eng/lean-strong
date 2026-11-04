package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Station artwork: the logo on a rounded card (or a colourful monogram when there's no logo),
 * with a gold ring and pulse while playing, a favourite badge, and an optional colour glow.
 */
public class LogoView extends View {
    private static final int[] MONO = {
        0xFFE4572E, 0xFF13A89E, 0xFFE8A317, 0xFF5BA33B, 0xFF2E86AB, 0xFFB5367F, 0xFFF18F01, 0xFF6A4C93,
        0xFF00A878, 0xFFD7263D
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path star = new Path();
    private final RectF art = new RectF();
    private final RectF tmp = new RectF();

    private String url;
    private String mono = "";
    private int monoColor = MONO[0];
    private Bitmap logo;
    private int baseColor = Color.WHITE;
    private boolean circle;
    private boolean glow;
    private boolean playing;
    private boolean fav;
    private float pulse;
    private float cornerFrac = 0.22f;
    private ValueAnimator anim;

    public LogoView(Context c) {
        super(c);
        edge.setStyle(Paint.Style.STROKE);
        ring.setStyle(Paint.Style.STROKE);
        letters.setColor(Color.WHITE);
        letters.setTextAlign(Paint.Align.CENTER);
        letters.setTypeface(Ui.heavy);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setCircle(boolean c) {
        circle = c;
        layoutArt(getWidth(), getHeight());
        invalidate();
    }

    public void setCornerFraction(float f) {
        cornerFrac = f;
        invalidate();
    }

    /** A soft glow in the logo's colour behind the card (Now Playing). */
    public void setGlow(boolean g) {
        glow = g;
        layoutArt(getWidth(), getHeight());
        invalidate();
    }

    public String getUrl() {
        return url;
    }

    public void setStation(String name, String logoUrl) {
        url = logoUrl;
        logo = null;
        baseColor = Color.WHITE;
        String n = name == null ? "" : name.trim();
        StringBuilder sb = new StringBuilder();
        for (String w : n.split("\\s+")) {
            if (w.length() == 0) continue;
            char ch = w.charAt(0);
            if (Character.isLetterOrDigit(ch)) sb.append(Character.toUpperCase(ch));
            if (sb.length() >= 2) break;
        }
        mono = sb.length() > 0 ? sb.toString() : "♪";
        monoColor = MONO[(n.hashCode() & Integer.MAX_VALUE) % MONO.length];
        invalidate();
    }

    public void setLogo(Bitmap b) {
        logo = b;
        baseColor = Color.WHITE;
        if (b != null) {
            try {
                // An opaque corner means the logo has its own background colour; continue it.
                int px = b.getPixel(Math.min(2, b.getWidth() - 1), Math.min(2, b.getHeight() - 1));
                if (Color.alpha(px) > 200) baseColor = px | 0xFF000000;
            } catch (Exception ignored) {
            }
        }
        invalidate();
    }

    /** The colour the screen tints itself with when this station plays. */
    public int getAccentColor() {
        if (logo == null) return monoColor;
        if (baseColor != Color.WHITE) return baseColor;
        return dominant(logo);
    }

    /** Average of the saturated pixels of a logo on a white background. */
    private static int dominant(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            long r = 0, g = 0, bl = 0, n = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < h; y += Math.max(1, h / 24)) {
                for (int x = 0; x < w; x += Math.max(1, w / 24)) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                    n++;
                }
            }
            if (n < 3) return 0xFF3A4A6B;
            return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        } catch (Exception e) {
            return 0xFF3A4A6B;
        }
    }

    public void setFav(boolean f) {
        if (fav != f) {
            fav = f;
            invalidate();
        }
    }

    public void setPlaying(boolean p) {
        if (playing == p) return;
        playing = p;
        if (p) {
            if (anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(1800);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setInterpolator(new LinearInterpolator());
                anim.addUpdateListener(a -> {
                    pulse = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (isAttachedToWindow()) anim.start();
        } else if (anim != null) {
            anim.cancel();
            pulse = 0;
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing && anim != null && !anim.isRunning()) anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) w = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutArt(w, h);
    }

    private void layoutArt(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        float inset = glow ? size * 0.09f : size * 0.045f;
        art.set(inset, inset, w - inset, h - inset);
        float r = circle ? art.width() / 2 : art.width() * cornerFrac;
        clip.reset();
        clip.addRoundRect(art, r, r, Path.Direction.CW);
        sheen.setShader(new LinearGradient(0, art.top, 0, art.centerY(), 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        edge.setStrokeWidth(Math.max(1f, size * 0.008f));
        edge.setColor(0x1FFFFFFF);
        ring.setStrokeWidth(Math.max(2f, size * 0.022f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        if (w <= 0) return;
        float r = circle ? art.width() / 2 : art.width() * cornerFrac;

        if (glow) {
            int g = Ui.moodColor(getAccentColor());
            glowP.setShader(new RadialGradient(art.centerX(), art.centerY() + art.height() * 0.08f, art.width() * 0.72f,
                    new int[] {Ui.alpha(g, 0.75f), Ui.alpha(g, 0.25f), 0}, new float[] {0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(art.centerX(), art.centerY() + art.height() * 0.08f, art.width() * 0.72f, glowP);
        }

        if (playing) {
            float grow = art.width() * 0.06f * pulse;
            tmp.set(art.left - grow, art.top - grow, art.right + grow, art.bottom + grow);
            ring.setColor(Ui.alpha(Ui.GOLD, 0.7f * (1 - pulse)));
            c.drawRoundRect(tmp, r + grow, r + grow, ring);
        }

        c.save();
        c.clipPath(clip);
        if (logo != null) {
            fill.setShader(null);
            fill.setColor(baseColor);
            c.drawRect(art, fill);
            float lw = logo.getWidth(), lh = logo.getHeight();
            float box = art.width() * 0.78f;
            float s = Math.min(box / lw, box / lh);
            float aspect = lw / lh;
            if (aspect > 0.8f && aspect < 1.25f && baseColor != Color.WHITE) {
                s = Math.max(art.width() / lw, art.height() / lh); // full-bleed square logos
            }
            float dw = lw * s, dh = lh * s;
            tmp.set(art.centerX() - dw / 2, art.centerY() - dh / 2, art.centerX() + dw / 2, art.centerY() + dh / 2);
            c.drawBitmap(logo, null, tmp, img);
        } else {
            fill.setShader(new LinearGradient(art.left, art.top, art.right, art.bottom,
                    Ui.lighten(monoColor, 0.18f), Ui.darken(monoColor, 0.35f), Shader.TileMode.CLAMP));
            c.drawRect(art, fill);
            letters.setTextSize(art.width() * (mono.length() > 1 ? 0.36f : 0.44f));
            c.drawText(mono, art.centerX(), art.centerY() - (letters.descent() + letters.ascent()) / 2, letters);
        }
        c.drawRect(art, sheen);
        c.restore();
        c.drawRoundRect(art, r, r, edge);

        if (playing) {
            ring.setColor(Ui.GOLD);
            float o = ring.getStrokeWidth() * 0.5f;
            tmp.set(art.left - o, art.top - o, art.right + o, art.bottom + o);
            c.drawRoundRect(tmp, r + o, r + o, ring);
        }

        if (fav) {
            float br = art.width() * 0.13f;
            float bx = art.right - br * 0.55f, by = art.top + br * 0.55f;
            badge.setShader(null);
            badge.setColor(Ui.BG);
            c.drawCircle(bx, by, br + Math.max(2f, art.width() * 0.02f), badge);
            badge.setColor(Ui.GOLD);
            c.drawCircle(bx, by, br, badge);
            badge.setColor(Ui.ON_GOLD);
            starPath(bx, by, br * 0.62f);
            c.drawPath(star, badge);
        }
    }

    private void starPath(float cx, float cy, float rOuter) {
        star.reset();
        float rInner = rOuter * 0.45f;
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? rOuter : rInner;
            float x = cx + (float) (Math.cos(a) * rr), y = cy + (float) (Math.sin(a) * rr);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
    }
}
