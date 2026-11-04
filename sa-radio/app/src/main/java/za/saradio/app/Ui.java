package za.saradio.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

/** Design tokens and small view factories. The whole UI is built in code, like the original. */
final class Ui {
    private Ui() {}

    // Surfaces: a near-black with a blue cast, lifted in steps.
    static final int BG = 0xFF0B0D12;
    static final int SURFACE = 0xFF141821;
    static final int SURFACE2 = 0xFF1B202B;
    static final int SURFACE3 = 0xFF252B38;
    static final int STROKE = 0x14FFFFFF;
    static final int STROKE2 = 0x24FFFFFF;
    // Text.
    static final int TEXT = 0xFFF5F6F8;
    static final int TEXT2 = 0xFFC8CDD6;
    static final int MUTED = 0xFF8F98A9;
    // Accent: the gold of the South African flag, plus status colours.
    static final int GOLD = 0xFFFFB81C;
    static final int GOLD_DEEP = 0xFFF28C0F;
    static final int ON_GOLD = 0xFF1C1400;
    static final int LIVE = 0xFFFF4D4D;
    static final int GREEN = 0xFF34D399;
    static final int RED = 0xFFFF6B5E;
    // Flag colours, used sparingly for atmosphere.
    static final int SA_GREEN = 0xFF007A4D;
    static final int SA_RED = 0xFFDE3831;
    static final int SA_BLUE = 0xFF002395;

    static Typeface regular = Typeface.DEFAULT;
    static Typeface medium = Typeface.DEFAULT;
    static Typeface bold = Typeface.DEFAULT_BOLD;
    static Typeface heavy = Typeface.DEFAULT_BOLD;
    private static boolean loaded;

    static final int W_REGULAR = 0;
    static final int W_MEDIUM = 1;
    static final int W_BOLD = 2;
    static final int W_HEAVY = 3;

    static void init(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            regular = c.getResources().getFont(R.font.jakarta_regular);
            medium = c.getResources().getFont(R.font.jakarta_medium);
            bold = c.getResources().getFont(R.font.jakarta_bold);
            heavy = c.getResources().getFont(R.font.jakarta_extrabold);
        } catch (Exception e) {
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            bold = Typeface.create("sans-serif-medium", Typeface.BOLD);
            heavy = Typeface.create("sans-serif-black", Typeface.BOLD);
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

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable roundStroke(int color, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = round(color, radiusPx);
        g.setStroke(strokePx, stroke);
        return g;
    }

    static GradientDrawable gradient(GradientDrawable.Orientation o, float radiusPx, int... colors) {
        GradientDrawable g = new GradientDrawable(o, colors);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static Drawable ripple(Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, round(0xFFFFFFFF, radiusPx));
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

    /** Pulls a logo colour into a range that reads well as a dark, rich background tint. */
    static int moodColor(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[1] < 0.18f) return 0xFF3A4A6B; // grey/white logos: a calm blue
        hsv[1] = Math.min(0.85f, Math.max(0.45f, hsv[1]));
        hsv[2] = Math.min(0.72f, Math.max(0.42f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    /** A round icon button with a 48dp touch target and an accessible label. */
    static ImageView iconButton(Context c, int icon, int tint, int bg, int sizeDp, String label) {
        ImageView v = new ImageView(c);
        v.setImageResource(icon);
        v.setColorFilter(tint);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(sizeDp * 0.27f);
        v.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(bg);
        if (Color.alpha(bg) < 0xF0) shape.setStroke(dp(c, 1), STROKE);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        v.setContentDescription(label);
        v.setTooltipText(label);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Pill-shaped chip. */
    static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 13.5f, selected ? ON_GOLD : TEXT2, W_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 38));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        styleChip(t, selected);
        return t;
    }

    static void styleChip(TextView t, boolean selected) {
        Context c = t.getContext();
        float r = dp(c, 19);
        t.setTextColor(selected ? ON_GOLD : TEXT2);
        t.setBackground(ripple(selected
                ? gradient(GradientDrawable.Orientation.TL_BR, r, 0xFFFFC94A, GOLD)
                : roundStroke(SURFACE2, r, STROKE, dp(c, 1)), r));
        t.setSelected(selected);
    }

    /** Big gold pill button. */
    static TextView primaryButton(Context c, String label) {
        TextView t = text(c, label, 15, ON_GOLD, W_HEAVY);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        float r = dp(c, 26);
        t.setBackground(ripple(gradient(GradientDrawable.Orientation.TL_BR, r, 0xFFFFCF5A, GOLD, GOLD_DEEP), r));
        press(t);
        return t;
    }

    static TextView secondaryButton(Context c, String label) {
        TextView t = text(c, label, 15, TEXT, W_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        float r = dp(c, 26);
        t.setBackground(ripple(roundStroke(SURFACE2, r, STROKE2, dp(c, 1)), r));
        press(t);
        return t;
    }

    static void roundClip(View v, final float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
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
