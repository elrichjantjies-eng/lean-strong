package za.saradio.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A modal bottom sheet in the app's own style (the old app used plain system dialogs). */
final class Sheet {
    final MainActivity a;
    final FrameLayout overlay;
    final LinearLayout panel;
    final LinearLayout body;
    final LinearLayout titleRow;
    final ScrollView scroll;
    private final View scrim;
    private boolean closing;
    Runnable onDismiss;

    /** Caps the panel at 92% of the screen. */
    private static final class Panel extends LinearLayout {
        Panel(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            View parent = (View) getParent();
            int max = parent == null ? MeasureSpec.getSize(h) : (int) ((parent.getHeight() - parent.getPaddingTop()) * 0.92f);
            if (max > 0) h = MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(h), max), MeasureSpec.AT_MOST);
            super.onMeasure(w, h);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    Sheet(MainActivity act, String title) {
        a = act;
        Context c = act;
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        scrim = new View(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(v -> dismiss());
        scrim.setContentDescription("Close");
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new Panel(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(c, 18);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.PAPER);
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Ui.line(), Ui.INK);
        // Hide the outline's bottom edge below the screen.
        android.graphics.drawable.LayerDrawable framed = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] {bg});
        framed.setLayerInset(0, -Ui.line(), 0, -Ui.line(), -Ui.line());
        panel.setBackground(framed);
        panel.setElevation(Ui.dp(c, 8));
        panel.setClickable(true);

        LinearLayout header = Ui.column(c);
        View handle = new View(c);
        handle.setBackground(Ui.round(Ui.INK, Ui.dp(c, 3)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        header.addView(handle, hp);
        titleRow = Ui.row(c);
        titleRow.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
        TextView t = Ui.display(c, title, 27, Ui.INK);
        Ui.heading(t);
        titleRow.addView(t, Ui.weight(1));
        header.addView(titleRow, Ui.lp(-1, Ui.dp(c, 52)));
        panel.addView(header);
        dragToClose(header);

        scroll = new ScrollView(c);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        body = Ui.column(c);
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 20));
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        overlay.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    }

    void addAction(View v) {
        titleRow.addView(v);
    }

    void show() {
        a.root().addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        applyInsets(a.navInset(), a.imeInset(), a.topInset());
        a.pushSheet(this);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(220).start();
        panel.setTranslationY(a.root().getHeight() > 0 ? a.root().getHeight() : 2000);
        panel.post(() -> panel.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start());
        overlay.announceForAccessibility(((TextView) titleRow.getChildAt(0)).getText());
    }

    void applyInsets(int nav, int ime, int top) {
        overlay.setPadding(0, top, 0, ime);
        scroll.setPadding(0, 0, 0, ime > 0 ? 0 : nav);
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        a.popSheet(this);
        a.hideKeyboard();
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationY(panel.getHeight() + Ui.dp(a, 40)).setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    ViewGroup p = (ViewGroup) overlay.getParent();
                    if (p != null) p.removeView(overlay);
                    if (onDismiss != null) onDismiss.run();
                })
                .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void dragToClose(View grip) {
        grip.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            boolean drag;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        drag = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (drag) panel.setTranslationY(Math.max(0, e.getRawY() - startY));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        drag = false;
                        if (panel.getTranslationY() > panel.getHeight() * 0.25f) dismiss();
                        else panel.animate().translationY(0).setDuration(180).start();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    // ---- Building blocks for sheet content --------------------------------------------------

    TextView section(String label) {
        TextView t = Ui.text(a, label.toUpperCase(java.util.Locale.ROOT), 12, Ui.INK, Ui.W_BOLD);
        t.setLetterSpacing(0.14f);
        Ui.heading(t);
        body.addView(t, Ui.lp(-2, -2, Ui.dp(a, 2), Ui.dp(a, 18), 0, Ui.dp(a, 10)));
        return t;
    }

    /** A tappable settings row: icon tile, title, subtitle, chevron. */
    LinearLayout row(int icon, String title, String subtitle, View.OnClickListener click) {
        Context c = a;
        LinearLayout row = Ui.row(c);
        int sh = Ui.dp(c, 3);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12) + sh, Ui.dp(c, 12) + sh);
        float r = Ui.dp(c, 10);
        row.setBackground(Ui.ripple(Ui.plate(Ui.PAPER, r, sh, Ui.INK), r));
        android.widget.ImageView ic = new android.widget.ImageView(c);
        ic.setImageResource(icon);
        ic.setColorFilter(Ui.INK);
        int p = Ui.dp(c, 9);
        ic.setPadding(p, p, p, p);
        ic.setBackground(Ui.roundStroke(Ui.YELLOW, Ui.dp(c, 8), Ui.INK, Ui.line()));
        row.addView(ic, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));
        LinearLayout txt = Ui.column(c);
        txt.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        txt.addView(Ui.text(c, title, 15.5f, Ui.TEXT, Ui.W_BOLD));
        if (subtitle != null) {
            TextView s = Ui.text(c, subtitle, 13, Ui.TEXT2, Ui.W_MEDIUM);
            s.setPadding(0, Ui.dp(c, 3), 0, 0);
            txt.addView(s);
        }
        row.addView(txt, Ui.weight(1));
        if (click != null) {
            android.widget.ImageView chev = new android.widget.ImageView(c);
            chev.setImageResource(R.drawable.ic_chevron);
            chev.setColorFilter(Ui.INK);
            row.addView(chev, Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22)));
            row.setOnClickListener(click);
            Ui.press(row, 0.98f);
        }
        body.addView(row, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10)));
        return row;
    }

    TextView note(String s) {
        TextView t = Ui.text(a, s, 13.5f, Ui.TEXT2, Ui.W_MEDIUM);
        t.setLineSpacing(0, 1.3f);
        body.addView(t, Ui.lp(-1, -2, Ui.dp(a, 2), Ui.dp(a, 10), Ui.dp(a, 2), 0));
        return t;
    }
}
