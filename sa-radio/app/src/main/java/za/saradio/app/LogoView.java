package za.saradio.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Station artwork. In roundel mode (the default) the logo sits in a white circle with an ink
 * ring, like the roundel on a painted shop sign; with no logo it shows a painted monogram. The
 * view also works out the station's brand colour from its logo, which the tiles and the player
 * paint themselves with.
 */
public class LogoView extends View {
    /** Monogram colours for stations without a logo: the taxi-rank palette. */
    private static final int[] MONO = {
        Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN, Ui.SIGN_RED, 0xFF61107D, 0xFFF28C0F, 0xFF13A89E, 0xFF8E44AD, 0xFF0B6E4F,
        0xFFE4572E
    };

    public interface ArtListener {
        void onArt(LogoView v);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    /** The logo's own background colour when it has one (an opaque corner), else white. */
    private int baseColor = Color.WHITE;
    /** The colour behind the logo inside the roundel. */
    private int plateColor = Color.WHITE;
    private int brand = Ui.INK;
    private boolean square;
    private boolean playing;
    private boolean fav;
    private float pulse;
    private float cornerFrac = 0.22f;
    private ValueAnimator anim;
    private ArtListener artListener;

    public LogoView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        letters.setTextAlign(Paint.Align.CENTER);
        letters.setTypeface(Ui.sign);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Kept for callers from the old design: circles are now the default. */
    public void setCircle(boolean c) {
        square = !c;
        layoutArt(getWidth(), getHeight());
        invalidate();
    }

    /** A rounded square instead of the roundel. */
    public void setSquare(boolean s) {
        square = s;
        layoutArt(getWidth(), getHeight());
        invalidate();
    }

    public void setCornerFraction(float f) {
        cornerFrac = f;
        invalidate();
    }

    /** No longer used: the new design has no glow. */
    public void setGlow(boolean g) {}

    public void setArtListener(ArtListener l) {
        artListener = l;
    }

    public String getUrl() {
        return url;
    }

    /** The monogram colour a station gets when it has no logo. */
    static int monoColor(String name) {
        String n = name == null ? "" : name.trim();
        return MONO[(n.hashCode() & Integer.MAX_VALUE) % MONO.length];
    }

    /** The painted letter a station gets when it has no logo. */
    static String monoLetter(String name) {
        String n = name == null ? "" : name.trim();
        for (String w : n.split("\\s+")) {
            if (w.length() > 0 && Character.isLetterOrDigit(w.charAt(0))) return String.valueOf(Character.toUpperCase(w.charAt(0)));
        }
        return "♪";
    }

    public void setStation(String name, String logoUrl) {
        url = logoUrl;
        logo = null;
        baseColor = Color.WHITE;
        plateColor = Color.WHITE;
        String n = name == null ? "" : name.trim();
        StringBuilder sb = new StringBuilder();
        for (String w : n.split("\\s+")) {
            if (w.length() == 0) continue;
            char ch = w.charAt(0);
            // One painted letter, like a signwriter's roundel.
            if (Character.isLetterOrDigit(ch)) sb.append(Character.toUpperCase(ch));
            if (sb.length() >= 1) break;
        }
        mono = sb.length() > 0 ? sb.toString() : "♪";
        monoColor = MONO[(n.hashCode() & Integer.MAX_VALUE) % MONO.length];
        brand = monoColor;
        invalidate();
        if (artListener != null) artListener.onArt(this);
    }

    public void setLogo(Bitmap b) {
        logo = b;
        baseColor = Color.WHITE;
        plateColor = Color.WHITE;
        brand = monoColor;
        if (b != null) analyse(b);
        invalidate();
        if (artListener != null) artListener.onArt(this);
    }

    /** True once a real logo (not a monogram) is showing. */
    public boolean hasLogo() {
        return logo != null;
    }

    /** The colour this station's sign is painted in. */
    public int getBrandColor() {
        return brand;
    }

    /** Kept for older callers: the brand colour. */
    public int getAccentColor() {
        return brand;
    }

    /** Works out the logo's background, whether it needs a dark roundel, and the brand colour. */
    private void analyse(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            int corner = b.getPixel(Math.min(2, w - 1), Math.min(2, h - 1));
            boolean opaqueCorner = Color.alpha(corner) > 200;
            if (opaqueCorner) baseColor = corner | 0xFF000000;
            long r = 0, g = 0, bl = 0, n = 0, opaque = 0, light = 0;
            float[] hsv = new float[3];
            int step = Math.max(1, Math.min(w, h) / 32);
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    opaque++;
                    if (Ui.luminance(p | 0xFF000000) > 0.8) light++;
                    Color.colorToHSV(p, hsv);
                    if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                    n++;
                }
            }
            int dominant = n >= 4 && n * 12 >= opaque ? Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)) : 0;
            if (opaqueCorner && baseColor != Color.WHITE && Ui.luminance(baseColor) < 0.9) {
                brand = baseColor;
                plateColor = baseColor;
            } else if (dominant != 0) {
                brand = dominant;
            } else {
                brand = Ui.INK;
            }
            // White lettering on a see-through background would vanish on a white roundel.
            if (!opaqueCorner && opaque > 0 && light * 10 >= opaque * 7) plateColor = Ui.INK;
        } catch (Exception e) {
            brand = Ui.INK;
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

    private float lineWidth() {
        return Math.max(Ui.line() * 0.8f, Math.min(getWidth(), getHeight()) * 0.035f);
    }

    private void layoutArt(int w, int h) {
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        float inset = playing ? size * 0.06f : size * 0.02f;
        inset = Math.max(inset, size * 0.06f); // room for the playing ring either way
        art.set(inset, inset, w - inset, h - inset);
        float r = square ? art.width() * cornerFrac : art.width() / 2;
        clip.reset();
        clip.addRoundRect(art, r, r, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        if (w <= 0) return;
        float r = square ? art.width() * cornerFrac : art.width() / 2;
        float lw = lineWidth();

        if (playing) {
            float grow = art.width() * 0.07f * pulse;
            tmp.set(art.left - grow, art.top - grow, art.right + grow, art.bottom + grow);
            ring.setStrokeWidth(lw);
            ring.setColor(Ui.alpha(Ui.INK, 0.8f * (1 - pulse)));
            c.drawRoundRect(tmp, r + grow, r + grow, ring);
        }

        c.save();
        c.clipPath(clip);
        if (logo != null) {
            fill.setColor(plateColor);
            c.drawRect(art, fill);
            float lwid = logo.getWidth(), lh = logo.getHeight();
            float aspect = lwid / lh;
            boolean fullBleed = baseColor != Color.WHITE && aspect > 0.8f && aspect < 1.25f;
            float box = art.width() * (square ? 0.8f : 0.7f);
            float s = fullBleed ? Math.max(art.width() / lwid, art.height() / lh) : Math.min(box / lwid, box / lh);
            float dw = lwid * s, dh = lh * s;
            tmp.set(art.centerX() - dw / 2, art.centerY() - dh / 2, art.centerX() + dw / 2, art.centerY() + dh / 2);
            c.drawBitmap(logo, null, tmp, img);
        } else {
            fill.setColor(Color.WHITE);
            c.drawRect(art, fill);
            letters.setColor(monoColor);
            letters.setTextSize(art.width() * (mono.length() > 1 ? 0.34f : 0.46f));
            c.drawText(mono, art.centerX(), art.centerY() - (letters.descent() + letters.ascent()) / 2, letters);
        }
        c.restore();

        // The ink ring.
        ring.setStrokeWidth(lw);
        ring.setColor(Ui.INK);
        float o = lw / 2;
        tmp.set(art.left + o, art.top + o, art.right - o, art.bottom - o);
        c.drawRoundRect(tmp, r - o, r - o, ring);

        if (fav) {
            float br = art.width() * 0.14f;
            float bx = art.right - br * 0.55f, by = art.top + br * 0.55f;
            badge.setColor(Ui.INK);
            c.drawCircle(bx, by, br + lw * 0.8f, badge);
            badge.setColor(Ui.YELLOW);
            c.drawCircle(bx, by, br, badge);
            badge.setColor(Ui.INK);
            starPath(bx, by, br * 0.66f);
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
