package za.saradio.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The full-screen player, styled as a taxi destination board: the station's sign in its own
 * colour with livery stripes, then the controls, sound presets and the data "gears".
 * Swipe down or press back to close.
 */
final class NowPlaying {
    private final MainActivity a;
    private final DragFrame view;
    private final View statusBand;
    private final ScrollView scroll;
    private final LinearLayout col;
    private final FrameLayout board;
    private final Livery.Board boardBg;
    private final LinearLayout boardCol;
    private final LogoView art;
    private final TextView tag;
    private final TextView where;
    private final TextView name;
    private final TextView plate;
    private final TextView sub;
    private final TextView song;
    private final BarsView bars;
    private final LinearLayout statusRow;
    private final View liveDot;
    private final ProgressBar spinner;
    private final TextView status;
    private final ImageView play;
    private final LinearLayout favBtn;
    private final ImageView fav;
    private final TextView favLabel;
    private final LinearLayout sleepBtn;
    private final Dial dial;
    private final Segmented band;
    private final TextView bandNote;
    private final TextView sleepLabel;
    private final LinearLayout presetRow;
    private final TextView soundLink;
    private final Segmented quality;
    private final TextView qualityNote;
    private final TextView qualityStats;
    private boolean open;
    private String boundKey;
    /** Keeps the station name clear of the livery stripes. */
    private static final int NAME_GAP_DP = 80;

    @SuppressLint("ClickableViewAccessibility")
    NowPlaying(MainActivity act) {
        a = act;
        Context c = act;
        view = new DragFrame(c);
        view.setClickable(true);
        view.setBackgroundColor(Ui.PAPER);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 28));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        view.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // An ink band behind the status bar, then a run of yellow livery stripes.
        LinearLayout top = Ui.column(c);
        statusBand = new View(c);
        statusBand.setBackgroundColor(Ui.INK);
        top.addView(statusBand, Ui.lp(-1, 0));
        top.addView(new Livery.Band(c, Ui.YELLOW), Ui.lp(-1, Ui.dp(c, 12)));
        view.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // Top bar
        LinearLayout bar = Ui.row(c);
        ImageView down = Ui.iconButton(c, R.drawable.ic_down, Ui.INK, Ui.PAPER, 48, "Close player");
        down.setOnClickListener(v -> close());
        bar.addView(down);
        LinearLayout mid = Ui.column(c);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView np = Ui.text(c, "NOW PLAYING", 12, Ui.INK, Ui.W_BOLD);
        np.setLetterSpacing(0.18f);
        np.setGravity(Gravity.CENTER);
        mid.addView(np);
        where = Ui.text(c, "", 13, Ui.TEXT2, Ui.W_MEDIUM);
        where.setSingleLine(true);
        where.setEllipsize(TextUtils.TruncateAt.END);
        where.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), 0);
        where.setGravity(Gravity.CENTER);
        mid.addView(where);
        bar.addView(mid, Ui.weight(1));
        ImageView share = Ui.iconButton(c, R.drawable.ic_share, Ui.INK, Ui.PAPER, 48, "Share station");
        share.setOnClickListener(v -> share());
        bar.addView(share);
        col.addView(bar, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 16)));

        // The destination board.
        board = new FrameLayout(c);
        boardBg = new Livery.Board(c, Ui.YELLOW, new int[] {Ui.INK, Ui.PAPER}, 110);
        boardBg.setShadow(4, Ui.INK);
        board.setBackground(boardBg);
        boardCol = Ui.column(c);
        boardCol.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 18));
        LinearLayout head = Ui.row(c);
        art = new LogoView(c);
        art.setArtListener(v -> paintBoard());
        head.addView(art, Ui.lp(Ui.dp(c, 96), Ui.dp(c, 96)));
        LinearLayout labels = Ui.column(c);
        tag = Ui.text(c, "", 11, Ui.PAPER, Ui.W_BOLD);
        tag.setLetterSpacing(0.12f);
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        tag.setPadding(Ui.dp(c, 9), Ui.dp(c, 5), Ui.dp(c, 9), Ui.dp(c, 5));
        labels.addView(tag, Ui.lp(-2, -2));
        plate = MainActivity.numberPlate(c, "");
        labels.addView(plate, Ui.lp(-2, Ui.dp(c, 30), 0, Ui.dp(c, 8), 0, 0));
        head.addView(labels, Ui.lp(-2, -2, Ui.dp(c, 10), 0, Ui.dp(c, 40), 0));
        boardCol.addView(head);
        boardCol.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 1f));
        name = Ui.display(c, "", 40, Ui.INK);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        Ui.heading(name);
        boardCol.addView(name, Ui.lp(-1, -2, 0, 0, Ui.dp(c, NAME_GAP_DP), 0));
        // Leave room for the board's hard shadow on the right and bottom.
        board.addView(boardCol, new FrameLayout.LayoutParams(-1, -1));
        board.setPadding(0, 0, Ui.dp(c, 4), Ui.dp(c, 4));
        // Swipe the board sideways to tune.
        final android.view.GestureDetector swipe = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(e2.getX() - e1.getX()) < Ui.dp(a, 48)) return false;
                board.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                a.tune(vx < 0 ? 1 : -1);
                return true;
            }
        });
        board.setOnTouchListener((v, e) -> swipe.onTouchEvent(e));
        board.setContentDescription("Station board. Swipe left or right to tune.");
        col.addView(board, Ui.lp(-1, Ui.dp(c, 300)));

        song = Ui.display(c, "", 26, Ui.INK);
        song.setMaxLines(2);
        song.setEllipsize(TextUtils.TruncateAt.END);
        song.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        col.addView(song, Ui.lp(-1, -2, 0, Ui.dp(c, 18), 0, 0));
        sub = Ui.text(c, "", 13.5f, Ui.TEXT2, Ui.W_MEDIUM);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(sub, Ui.lp(-1, -2, 0, Ui.dp(c, 6), 0, 0));

        bars = new BarsView(c, BarsView.WIDE, Ui.INK);
        bars.setColors(Ui.INK, Ui.INK);
        col.addView(bars, Ui.lp(-1, Ui.dp(c, 40), 0, Ui.dp(c, 14), 0, 0));

        statusRow = Ui.row(c);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        liveDot = new View(c);
        liveDot.setBackground(Ui.oval(Ui.LIVE));
        statusRow.addView(liveDot, Ui.lp(Ui.dp(c, 9), Ui.dp(c, 9), 0, 0, Ui.dp(c, 8), 0));
        spinner = new ProgressBar(c, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK));
        statusRow.addView(spinner, Ui.lp(Ui.dp(c, 16), Ui.dp(c, 16), 0, 0, Ui.dp(c, 8), 0));
        status = Ui.text(c, "", 13, Ui.INK, Ui.W_BOLD);
        statusRow.addView(status);
        col.addView(statusRow, Ui.lp(-2, Ui.dp(c, 24), 0, Ui.dp(c, 8), 0, 0));

        // Controls: tune back, play, tune forward.
        LinearLayout controls = Ui.row(c);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(tuneButton(R.drawable.ic_tune_prev, "Previous station", -1), new LinearLayout.LayoutParams(0, Ui.dp(c, 60), 1f));
        play = new ImageView(c);
        play.setImageResource(R.drawable.ic_play);
        play.setColorFilter(Ui.YELLOW);
        play.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pp = Ui.dp(c, 30);
        play.setPadding(pp, pp, pp, pp);
        play.setBackground(playDisc(c));
        play.setContentDescription("Play");
        play.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            a.togglePlay();
        });
        Ui.press(play, 0.9f);
        controls.addView(play, Ui.lp(Ui.dp(c, 92), Ui.dp(c, 92), Ui.dp(c, 14), 0, Ui.dp(c, 14), 0));
        controls.addView(tuneButton(R.drawable.ic_tune_next, "Next station", 1), new LinearLayout.LayoutParams(0, Ui.dp(c, 60), 1f));
        col.addView(controls, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, 0));

        // The tuning dial and the band: which stations the buttons step through.
        dial = new Dial(c);
        dial.setOnClickListener(v -> a.snack("Tuning through " + Tuner.name(Tuner.band(a)).toLowerCase(Locale.ROOT)));
        col.addView(dial, Ui.lp(-1, Ui.dp(c, 96), 0, Ui.dp(c, 16), 0, 0));
        band = new Segmented(c, Tuner.LABELS);
        band.setListener(i -> {
            Tuner.setBand(a, i);
            if (i == Tuner.FAVS && Prefs.favourites(a).isEmpty()) a.snack("No favourites yet, so tuning goes A to Z");
            refreshDial(true);
        });
        col.addView(band, Ui.lp(-1, -2, 0, Ui.dp(c, 10), 0, 0));
        bandNote = Ui.text(c, "", 12.5f, Ui.TEXT2, Ui.W_MEDIUM);
        bandNote.setLineSpacing(0, 1.25f);
        col.addView(bandNote, Ui.lp(-1, -2, Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2), 0));

        // Sleep timer and favourite.
        LinearLayout extras = Ui.row(c);
        sleepBtn = plateButton(R.drawable.ic_moon, "Sleep", "Sleep timer", v -> Panels.sleep(a));
        sleepLabel = (TextView) sleepBtn.getChildAt(1);
        extras.addView(sleepBtn, new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f));
        favBtn = plateButton(R.drawable.ic_star_outline, "Save", "Save to favourites", v -> {
            if (RadioService.current != null) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                a.toggleFav(RadioService.current);
            }
        });
        fav = (ImageView) favBtn.getChildAt(0);
        favLabel = (TextView) favBtn.getChildAt(1);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f);
        fl.leftMargin = Ui.dp(c, 12);
        extras.addView(favBtn, fl);
        col.addView(extras, Ui.lp(-1, -2, 0, Ui.dp(c, 16), 0, Ui.dp(c, 4)));

        // Sound card: quick presets
        LinearLayout soundCard = card();
        LinearLayout sh = Ui.row(c);
        sh.addView(cardTitle(R.drawable.ic_eq, "Sound"), Ui.weight(1));
        soundLink = Ui.text(c, "Equaliser ›", 14, Ui.ACCENT, Ui.W_BOLD);
        soundLink.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
        soundLink.setOnClickListener(v -> Panels.sound(a));
        soundLink.setContentDescription("Open the equaliser");
        sh.addView(soundLink);
        soundCard.addView(sh);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        presetRow = Ui.row(c);
        hs.addView(presetRow);
        soundCard.addView(hs, Ui.lp(-1, -2, 0, Ui.dp(c, 10), 0, 0));
        col.addView(soundCard, Ui.lp(-1, -2, 0, Ui.dp(c, 18), 0, 0));

        // Data & quality card: the gears
        LinearLayout qCard = card();
        qCard.addView(cardTitle(R.drawable.ic_data, "Data gear"));
        quality = new Segmented(c, DataMode.SHORT);
        quality.setListener(i -> {
            Prefs.setDataMode(a, i);
            a.onDataModeChanged();
            refresh();
        });
        qCard.addView(quality, Ui.lp(-1, -2, 0, Ui.dp(c, 12), 0, 0));
        qualityNote = Ui.text(c, "", 13.5f, Ui.INK, Ui.W_MEDIUM);
        qualityNote.setLineSpacing(0, 1.3f);
        qCard.addView(qualityNote, Ui.lp(-1, -2, Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 2), 0));
        qualityStats = Ui.text(c, "", 12.5f, Ui.TEXT2, Ui.W_MEDIUM);
        qualityStats.setLineSpacing(0, 1.3f);
        qCard.addView(qualityStats, Ui.lp(-1, -2, Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2), 0));
        col.addView(qCard, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, 0));

        view.setVisibility(View.GONE);
        view.setElevation(Ui.dp(c, 30));
        a.root().addView(view, new FrameLayout.LayoutParams(-1, -1));
        applyInsets(a.topInset(), a.navInset());
        relayout();
    }

    /** A square plate with a tuning arrow. */
    private ImageView tuneButton(int icon, String label, final int dir) {
        Context c = a;
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setColorFilter(Ui.INK);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 16);
        b.setPadding(p, p, p, p);
        b.setBackground(Ui.ripple(Ui.roundStroke(Ui.YELLOW, Ui.dp(c, 10), Ui.INK, Ui.line()), Ui.dp(c, 10)));
        b.setContentDescription(label);
        b.setTooltipText(label);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            a.tune(dir);
        });
        Ui.press(b, 0.92f);
        return b;
    }

    /** The big ink disc with a yellow ring, like a hubcap. */
    private static LayerDrawable playDisc(Context c) {
        GradientDrawable outer = Ui.oval(Ui.INK);
        GradientDrawable ring = Ui.oval(Ui.YELLOW);
        GradientDrawable face = Ui.oval(Ui.INK);
        LayerDrawable ld = new LayerDrawable(new android.graphics.drawable.Drawable[] {outer, ring, face});
        int r1 = Ui.line(), r2 = Ui.line() + Ui.dp(c, 5);
        ld.setLayerInset(1, r1, r1, r1, r1);
        ld.setLayerInset(2, r2, r2, r2, r2);
        return ld;
    }

    /** A plate-shaped button with an icon and a short label. Child 0 is the icon, child 1 the label. */
    private LinearLayout plateButton(int icon, String label, String spoken, View.OnClickListener l) {
        Context c = a;
        LinearLayout v = Ui.row(c);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setColorFilter(Ui.INK);
        v.addView(i, Ui.lp(Ui.dp(c, 20), Ui.dp(c, 20), 0, 0, Ui.dp(c, 7), 0));
        TextView t = Ui.text(c, label.toUpperCase(Locale.ROOT), 13, Ui.INK, Ui.W_BOLD);
        t.setLetterSpacing(0.06f);
        t.setSingleLine(true);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.addView(t);
        v.setBackground(Ui.ripple(Ui.roundStroke(Ui.PAPER, Ui.dp(c, 10), Ui.INK, Ui.line()), Ui.dp(c, 10)));
        v.setContentDescription(spoken);
        v.setOnClickListener(l);
        Ui.press(v, 0.94f);
        return v;
    }

    private LinearLayout card() {
        int sh = Ui.dp(a, 3);
        LinearLayout k = Ui.column(a);
        k.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16) + sh, Ui.dp(a, 16) + sh);
        k.setBackground(Ui.plate(Ui.PAPER, Ui.dp(a, 12), sh, Ui.INK));
        return k;
    }

    private View cardTitle(int icon, String t) {
        LinearLayout r = Ui.row(a);
        ImageView i = new ImageView(a);
        i.setImageResource(icon);
        i.setColorFilter(Ui.INK);
        r.addView(i, Ui.lp(Ui.dp(a, 20), Ui.dp(a, 20), 0, 0, Ui.dp(a, 10), 0));
        TextView tv = Ui.display(a, t, 21, Ui.INK);
        Ui.heading(tv);
        r.addView(tv);
        return r;
    }

    /** Sizes the board to the screen so the play button stays visible without scrolling. */
    void relayout() {
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        // The top bar, song, bars and controls need ~430dp besides the board; the dial sits just below.
        int room = h - a.topInset() - a.navInset() - Ui.dp(a, 430);
        int height = Math.max(Ui.dp(a, 210), Math.min(Math.min((int) ((w - Ui.dp(a, 40)) * 0.9f), Ui.dp(a, 340)), room));
        ViewGroup.LayoutParams lp = board.getLayoutParams();
        lp.height = height;
        board.setLayoutParams(lp);
        int roundel = Math.max(Ui.dp(a, 64), Math.min(Ui.dp(a, 112), (int) (height * 0.34f)));
        LinearLayout.LayoutParams al = (LinearLayout.LayoutParams) art.getLayoutParams();
        al.width = roundel;
        al.height = roundel;
        art.setLayoutParams(al);
        fitName();
    }

    /** The largest size (up to the board's) at which the name fits in two lines without breaking a word. */
    private void fitName() {
        String text = name.getText().toString().toUpperCase(Locale.ROOT).trim();
        if (text.isEmpty()) return;
        int boardH = board.getLayoutParams().height;
        float max = boardH >= Ui.dp(a, 280) ? 44 : boardH >= Ui.dp(a, 240) ? 38 : 32;
        int w = a.getResources().getDisplayMetrics().widthPixels;
        float avail = w - Ui.dp(a, 40) - Ui.dp(a, 4) - Ui.dp(a, 32) - Ui.dp(a, NAME_GAP_DP);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTypeface(Ui.display);
        float density = a.getResources().getDisplayMetrics().scaledDensity;
        String[] words = text.split("\\s+");
        float sp = max;
        for (; sp > 20; sp -= 1) {
            p.setTextSize(sp * density);
            float space = p.measureText(" ");
            int lines = 1;
            float x = 0;
            boolean fits = true;
            for (String word : words) {
                float ww = p.measureText(word);
                if (ww > avail) {
                    fits = false;
                    break;
                }
                if (x == 0) x = ww;
                else if (x + space + ww <= avail) x += space + ww;
                else {
                    lines++;
                    x = ww;
                }
            }
            if (fits && lines <= 2) break;
        }
        name.setTextSize(sp);
    }

    void applyInsets(int top, int nav) {
        ViewGroup.LayoutParams sb = statusBand.getLayoutParams();
        sb.height = top;
        statusBand.setLayoutParams(sb);
        col.setPadding(col.getPaddingLeft(), top + Ui.dp(a, 12 + 14), col.getPaddingRight(), nav + Ui.dp(a, 28));
        relayout();
    }

    /** Tests: show the lower half (sound and data cards). */
    void scrollToEnd() {
        scroll.fullScroll(View.FOCUS_DOWN);
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
        boundKey = null;
        refresh();
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
        scroll.scrollTo(0, 0);
        view.setTranslationY(a.root().getHeight() > 0 ? a.root().getHeight() : 2000);
        view.animate().translationY(0).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).setListener(null).start();
        view.announceForAccessibility("Now playing");
    }

    void close() {
        if (!open) return;
        open = false;
        bars.setRunning(false);
        view.animate().translationY(view.getHeight()).setDuration(240).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!open) view.setVisibility(View.GONE);
                }).start();
    }

    void onArt(LogoView v) {
        if (v == art) paintBoard();
    }

    /** Paints the board in the station's own colour; ink stations get the full livery. */
    private void paintBoard() {
        int brand = art.getBrandColor();
        boolean inky = Ui.luminance(brand) < 0.03;
        boardBg.setFill(brand, inky ? new int[] {Ui.YELLOW, Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN}
                : Ui.onColor(brand) == Ui.INK ? new int[] {Ui.INK, Ui.PAPER} : new int[] {Ui.PAPER, Ui.INK});
        name.setTextColor(inky ? Ui.YELLOW : Ui.onColor(brand));
        boolean darkTag = Ui.luminance(brand) > 0.06;
        tag.setTextColor(darkTag ? Ui.PAPER : Ui.INK);
        tag.setBackground(Ui.round(darkTag ? Ui.INK : Ui.PAPER, Ui.dp(a, 4)));
        board.invalidate();
    }

    void refresh() {
        Station s = RadioService.current;
        if (s == null) {
            if (open) close();
            return;
        }
        int st = RadioService.state;
        boolean playing = st == RadioService.PLAYING;
        if (!s.key().equals(boundKey)) {
            boundKey = s.key();
            a.bindLogo(art, s, DataMode.artPx(DataMode.effective(a)));
            name.setText(s.name);
            fitName();
            paintBoard();
        }
        String w = s.province.length() > 0 ? s.province : s.custom ? "Your own stream" : "South African radio";
        where.setText(w);
        String region = s.province.length() > 0 ? StationRepo.abbreviate(s.province) : "ZA";
        tag.setText(((playing ? "● LIVE · " : st == RadioService.BUFFERING ? "TUNING IN · " : "") + w).toUpperCase(Locale.ROOT));
        String genre = s.firstTags(1);
        String fq = Freq.label(s);
        plate.setText((fq.isEmpty() ? region + (genre.length() > 0 && genre.length() <= 14 ? " · " + genre : "") : fq + " · " + region)
                .toUpperCase(Locale.ROOT));
        String tags = s.firstTags(3);
        sub.setText(tags.length() > 0 ? tags : s.custom ? "Custom stream" : "Live radio");
        art.setPlaying(playing);

        boolean hasSong = playing && RadioService.songTitle.length() > 0;
        song.setVisibility(hasSong ? View.VISIBLE : View.GONE);
        song.setText(RadioService.songTitle);
        bars.setRunning(open && playing);

        statusRow.setVisibility(playing ? View.GONE : View.VISIBLE);
        liveDot.setVisibility(playing ? View.VISIBLE : View.GONE);
        spinner.setVisibility(st == RadioService.BUFFERING ? View.VISIBLE : View.GONE);
        status.setText(playing ? "LIVE" : MainActivity.playerLine());
        status.setTextColor(st == RadioService.ERROR ? Ui.RED : Ui.INK);
        status.setLetterSpacing(playing ? 0.16f : 0f);

        boolean active = RadioService.isActive();
        play.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_play);
        play.setContentDescription(active ? "Stop" : "Play");
        boolean isFav = a.isFav(s);
        fav.setImageResource(isFav ? R.drawable.ic_star : R.drawable.ic_star_outline);
        favLabel.setText(isFav ? "SAVED" : "SAVE");
        favBtn.setBackground(Ui.ripple(Ui.roundStroke(isFav ? Ui.PINK : Ui.PAPER, Ui.dp(a, 10), Ui.INK, Ui.line()), Ui.dp(a, 10)));
        favBtn.setContentDescription(isFav ? "Remove from favourites" : "Save to favourites");

        if (RadioService.sleepAt > 0) {
            long mins = Math.max(1, (RadioService.sleepAt - System.currentTimeMillis() + 59_999) / 60_000);
            sleepLabel.setText(mins + " MIN");
            sleepBtn.setBackground(Ui.ripple(Ui.roundStroke(Ui.YELLOW, Ui.dp(a, 10), Ui.INK, Ui.line()), Ui.dp(a, 10)));
            sleepBtn.setContentDescription("Sleep timer, " + mins + " minutes left");
        } else {
            sleepLabel.setText("SLEEP");
            sleepBtn.setBackground(Ui.ripple(Ui.roundStroke(Ui.PAPER, Ui.dp(a, 10), Ui.INK, Ui.line()), Ui.dp(a, 10)));
            sleepBtn.setContentDescription("Sleep timer");
        }

        refreshDial(open);
        rebuildPresets();
        refreshQuality();
    }

    private void refreshDial(boolean animate) {
        Station s = RadioService.current;
        int b = Tuner.band(a);
        band.select(b, false);
        dial.setState(Dial.State.of(a, s), animate);
        String note;
        if (b == Tuner.FAVS) {
            int n = Prefs.favourites(a).size();
            note = n == 0 ? "You have no favourites yet, so ◀ ▶ step through all stations, A to Z."
                    : "◀ ▶ step through your " + n + (n == 1 ? " favourite." : " favourites, in the order you saved them.");
        } else if (b == Tuner.FREQ) {
            note = "◀ ▶ step through all stations by FM frequency (the main frequency in the station's home city, where known). "
                    + "Online-only stations follow, A to Z.";
        } else {
            note = "◀ ▶ step through all stations, A to Z. Swipe the board to tune too.";
        }
        bandNote.setText(note);
    }

    private String presetState;

    private void rebuildPresets() {
        String cur = Prefs.eqPreset(a);
        boolean on = Prefs.eqEnabled(a);
        String key = cur + on;
        if (key.equals(presetState)) return;
        presetState = key;
        presetRow.removeAllViews();
        if (!on) {
            TextView off = Ui.chip(a, "Sound effects are off", false);
            off.setOnClickListener(v -> Panels.sound(a));
            presetRow.addView(off);
            return;
        }
        if ("custom".equals(cur)) addPreset("custom", "Custom", true);
        for (EqPresets.Preset p : EqPresets.ALL) addPreset(p.id, p.name, p.id.equals(cur));
        soundLink.setText("custom".equals(cur) ? "Edit curve ›" : "Equaliser ›");
    }

    private void addPreset(final String id, String label, boolean selected) {
        TextView t = Ui.chip(a, label, selected);
        t.setOnClickListener(v -> {
            if (id.equals("custom")) {
                Panels.sound(a);
                return;
            }
            Prefs.get(a).edit().putString("eqPreset", id).apply();
            AudioFx.apply(a);
            rebuildPresets();
        });
        presetRow.addView(t, Ui.lp(-2, -2, 0, 0, Ui.dp(a, 8), 0));
    }

    private void refreshQuality() {
        int mode = Prefs.dataMode(a);
        quality.select(mode, false);
        int eff = DataMode.effective(a);
        String note;
        if (mode == DataMode.AUTO) {
            note = (DataMode.onMeteredNetwork(a) ? "On mobile data now, so using Balanced." : "On Wi-Fi now, so using Best quality.");
        } else {
            note = DataMode.describe(mode);
        }
        qualityNote.setText(note);

        StringBuilder sb = new StringBuilder();
        Station.Stream s = RadioService.nowStream;
        Station cur = RadioService.current;
        boolean playingThis = s != null && RadioService.isActive();
        if (playingThis) {
            int kbps = s.bitrate > 0 ? s.bitrate : RadioService.measuredKbps;
            String label = s.label();
            if (s.bitrate == 0 && RadioService.measuredKbps > 0) label = RadioService.measuredKbps + " kbps " + s.codec.replace("UNKNOWN", "");
            if (label.trim().length() > 0) sb.append("Now: ").append(label.trim());
            if (kbps > 0) sb.append(sb.length() > 0 ? " · about " : "About ").append(DataMode.mbPerHour(kbps)).append(" MB an hour");
            int count = RadioService.streamCount;
            if (count > 1) {
                sb.append("\nThis station has ").append(count).append(" streams; ")
                        .append(eff == DataMode.SAVER ? "using the lightest." : eff == DataMode.BEST ? "using the best." : "using the middle one.");
            } else if (cur != null && !cur.custom) {
                sb.append("\nThis station has one stream, so the sound stays the same; the setting still trims pictures.");
            }
        }
        long session = RadioService.sessionBytes;
        if (session > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("This session: ").append(RadioService.bytesEstimated ? "about " : "").append(Prefs.formatBytes(session))
                    .append(" · This month: ").append(Prefs.formatBytes(Prefs.monthBytes(a)));
        }
        qualityStats.setText(sb);
        qualityStats.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void share() {
        Station s = RadioService.current;
        if (s == null) return;
        String song = RadioService.songTitle.length() > 0 ? " — now playing " + RadioService.songTitle : "";
        String text = "Listening to " + s.name + " on SA Radio" + song + ".";
        a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"));
    }

    static String clock(long t) {
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(t));
    }

    /** Lets a downward swipe from the top of the content drag the player away. */
    private final class DragFrame extends FrameLayout {
        private final int slop;
        private float startY;
        private float startX;
        private boolean dragging;

        DragFrame(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = e.getRawY();
                    startX = e.getRawX();
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - startY, dx = Math.abs(e.getRawX() - startX);
                    if (scroll.getScrollY() == 0 && dy > slop * 2 && dy > dx * 1.5f) {
                        dragging = true;
                        startY = e.getRawY();
                        return true;
                    }
                    break;
                default:
            }
            return false;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!dragging) return super.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    setTranslationY(Math.max(0, e.getRawY() - startY));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    if (getTranslationY() > getHeight() * 0.2f) close();
                    else animate().translationY(0).setDuration(180).start();
                    return true;
                default:
                    return true;
            }
        }
    }
}
