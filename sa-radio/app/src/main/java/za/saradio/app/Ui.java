package za.saradio.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Design tokens and small view factories for the "Number Plate" look: taxi-rank yellow, black
 * outlines, hard offset shadows, plates and painted signs. The whole UI is built in code.
 */
final class Ui {
    private Ui() {}

    // The palette: taxi livery colours on a yellow ground.
    static final int INK = 0xFF111111;
    static final int PAPER = 0xFFFFFFFF;
    static final int YELLOW = 0xFFFFD21F;
    static final int PINK = 0xFFFF4F8B;
    static final int COBALT = 0xFF2340FF;
    static final int LIVERY_GREEN = 0xFF00A86B;
    static final int SIGN_RED = 0xFFD7263D;
    static final int ROAD = 0xFF222222;

    // Surfaces.
    static final int BG = YELLOW;
    static final int SURFACE = PAPER;
    static final int SURFACE2 = PAPER;
    static final int SURFACE3 = 0xFFF2EFE8;
    static final int STROKE = INK;
    static final int STROKE2 = INK;
    // Text.
    static final int TEXT = INK;
    static final int TEXT2 = 0xFF2B2B2B;
    static final int MUTED = 0xFF555555;
    // Accent for links, small highlights and tints; readable on yellow and on white.
    static final int ACCENT = COBALT;
    static final int ACCENT_DEEP = 0xFF1A2FCC;
    static final int ON_ACCENT = PAPER;
    static final int LIVE = SIGN_RED;
    static final int GREEN = 0xFF00875A;
    static final int RED = SIGN_RED;
    // Flag colours.
    static final int SA_GREEN = 0xFF007A4D;
    static final int SA_GOLD = 0xFFFFB612;
    static final int SA_RED = 0xFFDE3831;
    static final int SA_BLUE = 0xFF002395;

    static Typeface regular = Typeface.DEFAULT;
    static Typeface medium = Typeface.DEFAULT;
    static Typeface bold = Typeface.DEFAULT_BOLD;
    static Typeface heavy = Typeface.DEFAULT_BOLD;
    /** Anton: tall condensed headlines. */
    static Typeface display = Typeface.DEFAULT_BOLD;
    /** Bungee: signwriter lettering for station names. */
    static Typeface sign = Typeface.DEFAULT_BOLD;
    /** Caveat Brush: hand-painted asides. */
    static Typeface brush = Typeface.DEFAULT;
    private static boolean loaded;
    /** Largest corner radius: plates and signs are nearly square. */
    private static float rMax = 36;
    /** Outline width for ink strokes. */
    private static int line = 6;

    static final int W_REGULAR = 0;
    static final int W_MEDIUM = 1;
    static final int W_BOLD = 2;
    static final int W_HEAVY = 3;

    static void init(Context c) {
        rMax = dp(c, 12);
        line = Math.max(2, dp(c, 2.5f));
        if (loaded) return;
        loaded = true;
        try {
            regular = c.getResources().getFont(R.font.grotesk_regular);
            medium = c.getResources().getFont(R.font.grotesk_medium);
            bold = c.getResources().getFont(R.font.grotesk_bold);
            heavy = bold;
            display = c.getResources().getFont(R.font.anton);
            sign = c.getResources().getFont(R.font.bungee);
            brush = c.getResources().getFont(R.font.caveat_brush);
        } catch (Exception e) {
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            bold = Typeface.create("sans-serif-medium", Typeface.BOLD);
            heavy = Typeface.create("sans-serif-black", Typeface.BOLD);
            display = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            sign = heavy;
            brush = Typeface.create("casual", Typeface.NORMAL);
        }
    }

    static Typeface face(int weight) {
        switch (weight) {
            case W_MEDIUM: return medium;
            case W_BOLD: return bold;
            case W_HEAVY: return heavy;
            default: return regular;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static int line() {
        return line;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face(weight));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    /** A headline in Anton, upper case. */
    static TextView display(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s == null ? "" : s.toString().toUpperCase(Locale.ROOT), sp, color, W_HEAVY);
        t.setTypeface(display);
        t.setLineSpacing(0, 0.98f);
        t.setAllCaps(true);
        return t;
    }

    /** Signwriter lettering in Bungee. */
    static TextView sign(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color, W_HEAVY);
        t.setTypeface(sign);
        t.setAllCaps(true);
        t.setLineSpacing(0, 1.0f);
        return t;
    }

    /** A painted drop shadow under lettering (hard, no blur). */
    static void paintedShadow(TextView t, float dx, float dy, int color) {
        t.setShadowLayer(0.01f, dx, dy, color);
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Math.min(radiusPx, rMax));
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable roundStroke(int color, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = round(color, radiusPx);
        // Ink outlines are the look: never hairlines.
        if (stroke == INK) strokePx = Math.max(strokePx, line);
        g.setStroke(strokePx, stroke);
        return g;
    }

    /**
     * A plate: fill with an ink outline and a hard ink shadow offset down and right. Views using
     * it should leave {@code shadowPx} of padding on the right and bottom.
     */
    static LayerDrawable plate(int fill, float radiusPx, int shadowPx, int shadowColor) {
        GradientDrawable shadow = round(shadowColor, radiusPx);
        GradientDrawable face = roundStroke(fill, radiusPx, INK, line);
        LayerDrawable ld = new LayerDrawable(new Drawable[] {shadow, face});
        ld.setLayerInset(0, shadowPx, shadowPx, 0, 0);
        ld.setLayerInset(1, 0, 0, shadowPx, shadowPx);
        return ld;
    }

    static GradientDrawable gradient(GradientDrawable.Orientation o, float radiusPx, int... colors) {
        GradientDrawable g = new GradientDrawable(o, colors);
        g.setCornerRadius(Math.min(radiusPx, rMax));
        return g;
    }

    static Drawable ripple(Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), content, round(0xFFFFFFFF, radiusPx));
    }

    static int alpha(int color, float a) {
        return (Math.round(Color.alpha(color) * a) << 24) | (color & 0x00FFFFFF);
    }

    static int blend(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int darken(int c, float f) {
        return Color.rgb(Math.round(Color.red(c) * (1 - f)), Math.round(Color.green(c) * (1 - f)), Math.round(Color.blue(c) * (1 - f)));
    }

    static int lighten(int c, float f) {
        return Color.rgb(Math.round(Color.red(c) + (255 - Color.red(c)) * f), Math.round(Color.green(c) + (255 - Color.green(c)) * f),
                Math.round(Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    /** Relative luminance (WCAG), 0 for black to 1 for white. */
    static double luminance(int c) {
        double r = chan(Color.red(c)), g = chan(Color.green(c)), b = chan(Color.blue(c));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double chan(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Ink or white, whichever reads better on this colour. */
    static int onColor(int bg) {
        return contrast(bg, INK) >= contrast(bg, PAPER) ? INK : PAPER;
    }

    /** Pulls a logo colour into a range that reads well as a dark, rich background tint. */
    static int moodColor(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.18f) return 0xFF3A4A6B; // grey/white logos: a calm blue
        hsv[1] = Math.min(0.85f, Math.max(0.45f, hsv[1]));
        hsv[2] = Math.min(0.72f, Math.max(0.42f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    /** A square plate button with a 48dp touch target and an accessible label. */
    static ImageView iconButton(Context c, int icon, int tint, int bg, int sizeDp, String label) {
        ImageView v = new ImageView(c);
        v.setImageResource(icon);
        v.setColorFilter(tint);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(sizeDp * 0.26f);
        v.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        float r = dp(c, 10);
        GradientDrawable shape = Color.alpha(bg) == 0 ? round(0, r) : roundStroke(bg, r, INK, line);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, round(0xFFFFFFFF, r)));
        v.setContentDescription(label);
        v.setTooltipText(label);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** A painted tag chip in signwriter lettering. */
    static TextView chip(Context c, String label, boolean selected) {
        TextView t = sign(c, label, 12, selected ? PAPER : INK);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setMinHeight(dp(c, 40));
        t.setPadding(dp(c, 13), 0, dp(c, 13), 0);
        styleChip(t, selected);
        return t;
    }

    static void styleChip(TextView t, boolean selected) {
        Context c = t.getContext();
        float r = dp(c, 5);
        t.setTextColor(selected ? PAPER : INK);
        t.setBackground(ripple(roundStroke(selected ? INK : PAPER, r, INK, line), r));
        t.setSelected(selected);
    }

    /** The main call to action: an ink plate with yellow lettering. */
    static TextView primaryButton(Context c, String label) {
        TextView t = text(c, label, 15, YELLOW, W_BOLD);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        float r = dp(c, 10);
        t.setBackground(ripple(roundStroke(INK, r, INK, line), r));
        press(t);
        return t;
    }

    static TextView secondaryButton(Context c, String label) {
        TextView t = text(c, label, 15, INK, W_BOLD);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        float r = dp(c, 10);
        t.setBackground(ripple(roundStroke(PAPER, r, INK, line), r));
        press(t);
        return t;
    }

    static void roundClip(View v, final float radiusPx) {
        final float r = Math.min(radiusPx, rMax);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** A gentle squeeze on touch. */
    @SuppressLint("ClickableViewAccessibility")
    static void press(View v) {
        press(v, 0.95f);
    }

    @SuppressLint("ClickableViewAccessibility")
    static void press(View v, final float scale) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.5f)).start();
            }
            return false;
        });
    }

    /** Marks a title as a heading for screen readers (Android 9+; ignored on 8.x). */
    static void heading(View v) {
        if (android.os.Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
}
