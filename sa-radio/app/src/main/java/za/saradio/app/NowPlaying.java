package za.saradio.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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

/** The full-screen player. Swipe down or press back to close. */
final class NowPlaying {
    private final MainActivity a;
    private final DragFrame view;
    private final BackdropView backdrop;
    private final ScrollView scroll;
    private final LinearLayout col;
    private final LogoView art;
    private final TextView where;
    private final TextView name;
    private final TextView sub;
    private final TextView song;
    private final BarsView bars;
    private final LinearLayout statusRow;
    private final View liveDot;
    private final ProgressBar spinner;
    private final TextView status;
    private final ImageView play;
    private final ImageView fav;
    private final TextView favLabel;
    private final TextView sleepLabel;
    private final LinearLayout presetRow;
    private final TextView soundLink;
    private final Segmented quality;
    private final TextView qualityNote;
    private final TextView qualityStats;
    private boolean open;
    private String boundKey;

    @SuppressLint("ClickableViewAccessibility")
    NowPlaying(MainActivity act) {
        a = act;
        Context c = act;
        view = new DragFrame(c);
        view.setClickable(true);
        backdrop = new BackdropView(c);
        view.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = Ui.column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 28));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        view.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // Top bar
        LinearLayout top = Ui.row(c);
        ImageView down = Ui.iconButton(c, R.drawable.ic_down, Ui.TEXT, Ui.alpha(0xFF000000, 0.25f), 46, "Close player");
        down.setOnClickListener(v -> close());
        top.addView(down);
        LinearLayout mid = Ui.column(c);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView np = Ui.text(c, "NOW PLAYING", 11, Ui.alpha(Ui.TEXT, 0.7f), Ui.W_HEAVY);
        np.setLetterSpacing(0.18f);
        np.setGravity(Gravity.CENTER);
        mid.addView(np);
        where = Ui.text(c, "", 13.5f, Ui.TEXT, Ui.W_BOLD);
        where.setSingleLine(true);
        where.setEllipsize(android.text.TextUtils.TruncateAt.END);
        where.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), 0);
        where.setGravity(Gravity.CENTER);
        mid.addView(where);
        top.addView(mid, Ui.weight(1));
        ImageView share = Ui.iconButton(c, R.drawable.ic_share, Ui.TEXT, Ui.alpha(0xFF000000, 0.25f), 46, "Share station");
        share.setOnClickListener(v -> share());
        top.addView(share);
        col.addView(top, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10)));

        art = new LogoView(c);
        art.setGlow(true);
        art.setCornerFraction(0.14f);
        col.addView(art, Ui.lp(Ui.dp(c, 300), Ui.dp(c, 300)));

        name = Ui.text(c, "", 27, Ui.TEXT, Ui.W_HEAVY);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLetterSpacing(-0.01f);
        Ui.heading(name);
        col.addView(name, Ui.lp(-1, -2, 0, Ui.dp(c, 6), 0, 0));
        sub = Ui.text(c, "", 14.5f, Ui.alpha(Ui.TEXT, 0.72f), Ui.W_MEDIUM);
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(sub, Ui.lp(-1, -2, 0, Ui.dp(c, 6), 0, 0));

        song = Ui.text(c, "", 14.5f, Ui.TEXT, Ui.W_BOLD);
        song.setGravity(Gravity.CENTER);
        song.setMaxLines(2);
        song.setEllipsize(android.text.TextUtils.TruncateAt.END);
        song.setBackground(Ui.roundStroke(0x26FFFFFF, Ui.dp(c, 16), Ui.STROKE2, Ui.dp(c, 1)));
        song.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        song.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        col.addView(song, Ui.lp(-2, -2, 0, Ui.dp(c, 14), 0, 0));

        bars = new BarsView(c, BarsView.WIDE, Ui.GOLD);
        bars.setColors(0xFFFFD873, Ui.GOLD_DEEP);
        col.addView(bars, Ui.lp(-1, Ui.dp(c, 44), Ui.dp(c, 6), Ui.dp(c, 18), Ui.dp(c, 6), 0));

        statusRow = Ui.row(c);
        statusRow.setGravity(Gravity.CENTER);
        statusRow.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        liveDot = new View(c);
        liveDot.setBackground(Ui.round(Ui.LIVE, Ui.dp(c, 4)));
        statusRow.addView(liveDot, Ui.lp(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0, Ui.dp(c, 8), 0));
        spinner = new ProgressBar(c, null, android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GOLD));
        statusRow.addView(spinner, Ui.lp(Ui.dp(c, 16), Ui.dp(c, 16), 0, 0, Ui.dp(c, 8), 0));
        status = Ui.text(c, "", 13, Ui.TEXT, Ui.W_HEAVY);
        status.setLetterSpacing(0.06f);
        statusRow.addView(status);
        col.addView(statusRow, Ui.lp(-2, Ui.dp(c, 24), 0, Ui.dp(c, 10), 0, 0));

        // Controls
        LinearLayout controls = Ui.row(c);
        controls.setGravity(Gravity.CENTER);
        LinearLayout sleepCol = control(R.drawable.ic_moon, "Sleep timer", v -> Panels.sleep(a));
        sleepLabel = (TextView) sleepCol.getChildAt(1);
        controls.addView(sleepCol, Ui.lp(Ui.dp(c, 100), -2));
        play = Ui.iconButton(c, R.drawable.ic_play, Ui.ON_GOLD, Ui.GOLD, 84, "Play");
        play.setBackground(Ui.ripple(Ui.gradient(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, Ui.dp(c, 42),
                0xFFFFD465, Ui.GOLD, Ui.GOLD_DEEP), Ui.dp(c, 42)));
        int pp = Ui.dp(c, 24);
        play.setPadding(pp, pp, pp, pp);
        play.setElevation(Ui.dp(c, 12));
        play.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            a.togglePlay();
        });
        Ui.press(play, 0.9f);
        controls.addView(play, Ui.lp(Ui.dp(c, 84), Ui.dp(c, 84)));
        LinearLayout favCol = control(R.drawable.ic_star_outline, "Favourite", v -> {
            if (RadioService.current != null) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                a.toggleFav(RadioService.current);
            }
        });
        fav = (ImageView) favCol.getChildAt(0);
        favLabel = (TextView) favCol.getChildAt(1);
        controls.addView(favCol, Ui.lp(Ui.dp(c, 100), -2));
        col.addView(controls, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, Ui.dp(c, 8)));

        // Sound card: quick presets
        LinearLayout soundCard = card();
        LinearLayout sh = Ui.row(c);
        sh.addView(cardTitle(R.drawable.ic_eq, "Sound"), Ui.weight(1));
        soundLink = Ui.text(c, "Equalizer", 13.5f, Ui.GOLD, Ui.W_BOLD);
        soundLink.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        soundLink.setOnClickListener(v -> Panels.sound(a));
        soundLink.setContentDescription("Open the equalizer");
        sh.addView(soundLink);
        soundCard.addView(sh);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        presetRow = Ui.row(c);
        hs.addView(presetRow);
        soundCard.addView(hs, Ui.lp(-1, -2, 0, Ui.dp(c, 12), 0, 0));
        col.addView(soundCard, Ui.lp(-1, -2, 0, Ui.dp(c, 18), 0, 0));

        // Data & quality card
        LinearLayout qCard = card();
        qCard.addView(cardTitle(R.drawable.ic_data, "Data & quality"));
        quality = new Segmented(c, DataMode.SHORT);
        quality.setListener(i -> {
            Prefs.setDataMode(a, i);
            a.onDataModeChanged();
            refresh();
        });
        qCard.addView(quality, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, 0));
        qualityNote = Ui.text(c, "", 13, Ui.TEXT2, Ui.W_MEDIUM);
        qualityNote.setLineSpacing(0, 1.3f);
        qCard.addView(qualityNote, Ui.lp(-1, -2, Ui.dp(c, 2), Ui.dp(c, 12), Ui.dp(c, 2), 0));
        qualityStats = Ui.text(c, "", 12.5f, Ui.MUTED, Ui.W_MEDIUM);
        qualityStats.setLineSpacing(0, 1.3f);
        qCard.addView(qualityStats, Ui.lp(-1, -2, Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2), 0));
        col.addView(qCard, Ui.lp(-1, -2, 0, Ui.dp(c, 12), 0, 0));

        view.setVisibility(View.GONE);
        view.setElevation(Ui.dp(c, 30));
        a.root().addView(view, new FrameLayout.LayoutParams(-1, -1));
        applyInsets(a.topInset(), a.navInset());
        relayout();
    }

    private LinearLayout control(int icon, String label, View.OnClickListener l) {
        Context c = a;
        LinearLayout v = Ui.column(c);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView b = Ui.iconButton(c, icon, Ui.TEXT, 0x30FFFFFF, 56, label);
        b.setOnClickListener(l);
        Ui.press(b, 0.9f);
        v.addView(b);
        TextView t = Ui.text(c, label, 12, Ui.alpha(Ui.TEXT, 0.75f), Ui.W_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.addView(t, Ui.lp(-2, -2, 0, Ui.dp(c, 8), 0, 0));
        return v;
    }

    private LinearLayout card() {
        LinearLayout k = Ui.column(a);
        k.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 16));
        k.setBackground(Ui.roundStroke(0x2E0B0D12, Ui.dp(a, 24), Ui.STROKE2, Ui.dp(a, 1)));
        return k;
    }

    private View cardTitle(int icon, String t) {
        LinearLayout r = Ui.row(a);
        ImageView i = new ImageView(a);
        i.setImageResource(icon);
        i.setColorFilter(Ui.GOLD);
        r.addView(i, Ui.lp(Ui.dp(a, 20), Ui.dp(a, 20), 0, 0, Ui.dp(a, 10), 0));
        TextView tv = Ui.text(a, t, 16.5f, Ui.TEXT, Ui.W_HEAVY);
        Ui.heading(tv);
        r.addView(tv);
        return r;
    }

    /** Sizes the artwork to the screen so the controls stay visible without scrolling. */
    void relayout() {
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        // Everything from the top bar to the play button needs ~400dp besides the artwork.
        int room = h - a.topInset() - a.navInset() - Ui.dp(a, 400);
        int size = Math.max(Ui.dp(a, 150), Math.min(Math.min(w - Ui.dp(a, 40), Ui.dp(a, 380)), room));
        ViewGroup.LayoutParams lp = art.getLayoutParams();
        lp.width = size;
        lp.height = size;
        art.setLayoutParams(lp);
    }

    void applyInsets(int top, int nav) {
        col.setPadding(col.getPaddingLeft(), top + Ui.dp(a, 8), col.getPaddingRight(), nav + Ui.dp(a, 28));
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
        if (v == art) backdrop.setMood(Ui.moodColor(art.getAccentColor()));
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
            backdrop.setMood(Ui.moodColor(art.getAccentColor()));
        }
        String w = s.province.length() > 0 ? s.province : s.custom ? "Your own stream" : "South African radio";
        where.setText(w);
        String tags = s.firstTags(2);
        sub.setText(tags.length() > 0 ? tags : s.custom ? "Custom stream" : "Live radio");
        art.setPlaying(playing);

        boolean hasSong = playing && RadioService.songTitle.length() > 0;
        song.setVisibility(hasSong ? View.VISIBLE : View.GONE);
        song.setText("♪  " + RadioService.songTitle);
        bars.setRunning(open && playing);

        liveDot.setVisibility(playing ? View.VISIBLE : View.GONE);
        spinner.setVisibility(st == RadioService.BUFFERING ? View.VISIBLE : View.GONE);
        status.setText(playing ? "LIVE" : MainActivity.playerLine());
        status.setTextColor(st == RadioService.ERROR ? Ui.RED : playing ? Ui.TEXT : Ui.alpha(Ui.TEXT, 0.8f));
        status.setLetterSpacing(playing ? 0.16f : 0f);

        boolean active = RadioService.isActive();
        play.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_play);
        play.setContentDescription(active ? "Stop" : "Play");
        boolean isFav = a.isFav(s);
        fav.setImageResource(isFav ? R.drawable.ic_star : R.drawable.ic_star_outline);
        fav.setColorFilter(isFav ? Ui.GOLD : Ui.TEXT);
        favLabel.setText(isFav ? "Saved" : "Favourite");
        fav.setContentDescription(isFav ? "Remove from favourites" : "Save to favourites");

        if (RadioService.sleepAt > 0) {
            long mins = Math.max(1, (RadioService.sleepAt - System.currentTimeMillis() + 59_999) / 60_000);
            sleepLabel.setText(mins + " min left");
            sleepLabel.setTextColor(Ui.GOLD);
        } else {
            sleepLabel.setText("Sleep timer");
            sleepLabel.setTextColor(Ui.alpha(Ui.TEXT, 0.75f));
        }

        rebuildPresets();
        refreshQuality();
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
        soundLink.setText("custom".equals(cur) ? "Edit curve" : "Equalizer");
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
