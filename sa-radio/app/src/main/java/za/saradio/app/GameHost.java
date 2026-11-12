package za.saradio.app;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/**
 * The full-screen page a puzzle plays on: a header with back, the puzzle's name and a timer,
 * level and New/Today buttons, the board, and a status line. Progress is saved whenever you move,
 * so a puzzle survives closing the app.
 */
final class GameHost implements Game.Host {
    private final MainActivity a;
    private final FrameLayout view;
    private final View statusBand;
    private final ScrollView scroll;
    private final LinearLayout col;
    private final TextView title;
    private final TextView timer;
    private final TextView status;
    private final TextView helpText;
    private final LinearLayout levelRow;
    private final Segmented levels;
    private final FrameLayout boardHolder;
    private final LinearLayout wonPlate;
    private final TextView wonText;
    private Games.Kind kind;
    private Game game;
    private boolean open;
    private boolean daily;
    private long elapsed;
    private long runningSince;
    private int level = 1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            timer.setText(Puzzles.clock(elapsedNow()));
            a.main().postDelayed(this, 1000);
        }
    };

    GameHost(MainActivity act) {
        a = act;
        Context c = act;
        view = new FrameLayout(c);
        view.setClickable(true);
        view.setBackgroundColor(Ui.YELLOW);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 28));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        view.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Ui.column(c);
        statusBand = new View(c);
        statusBand.setBackgroundColor(Ui.INK);
        top.addView(statusBand, Ui.lp(-1, 0));
        top.addView(new Livery.Band(c, Ui.PAPER), Ui.lp(-1, Ui.dp(c, 12)));
        view.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bar = Ui.row(c);
        ImageView back = Ui.iconButton(c, R.drawable.ic_back, Ui.YELLOW, Ui.INK, 48, "Back to games");
        back.setOnClickListener(v -> close());
        bar.addView(back);
        title = Ui.display(c, "", 28, Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        Ui.heading(title);
        bar.addView(title, Ui.weight(1));
        timer = MainActivity.numberPlate(c, "0:00");
        timer.setContentDescription("Time");
        bar.addView(timer, Ui.lp(-2, Ui.dp(c, 32)));
        col.addView(bar, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 12)));

        levelRow = Ui.row(c);
        levels = new Segmented(c, Game.levelNames());
        levels.setListener(i -> {
            level = i;
            newPuzzle(false);
        });
        levelRow.addView(levels, Ui.weight(1));
        col.addView(levelRow, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10)));

        LinearLayout actions = Ui.row(c);
        TextView today = plateButton("TODAY'S", v -> newPuzzle(true));
        actions.addView(today, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f));
        TextView fresh = plateButton("NEW", v -> newPuzzle(false));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f);
        fl.leftMargin = Ui.dp(c, 10);
        actions.addView(fresh, fl);
        TextView help = plateButton("HOW TO", v -> toggleHelp());
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f);
        hl.leftMargin = Ui.dp(c, 10);
        actions.addView(help, hl);
        col.addView(actions, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 10)));

        helpText = Ui.text(c, "", 13.5f, Ui.INK, Ui.W_MEDIUM);
        helpText.setLineSpacing(0, 1.3f);
        helpText.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 12));
        helpText.setBackground(Ui.roundStroke(Ui.PAPER, Ui.dp(c, 8), Ui.INK, Ui.line()));
        helpText.setVisibility(View.GONE);
        col.addView(helpText, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 12)));

        boardHolder = new FrameLayout(c);
        col.addView(boardHolder, Ui.lp(-1, -2));

        status = Ui.text(c, "", 13.5f, Ui.INK, Ui.W_BOLD);
        status.setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), 0);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        col.addView(status);

        wonPlate = Ui.column(c);
        int sh = Ui.dp(c, 4);
        wonPlate.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16) + sh, Ui.dp(c, 16) + sh);
        wonPlate.setBackground(Ui.plate(Ui.INK, Ui.dp(c, 10), sh, Ui.SIGN_RED));
        TextView w = Ui.display(c, "Sharp!", 30, Ui.YELLOW);
        wonPlate.addView(w);
        wonText = Ui.text(c, "", 14, Ui.PAPER, Ui.W_MEDIUM);
        wonText.setLineSpacing(0, 1.3f);
        wonText.setPadding(0, Ui.dp(c, 4), 0, 0);
        wonPlate.addView(wonText);
        LinearLayout wa = Ui.row(c);
        TextView again = plateButton("ANOTHER", v -> newPuzzle(false));
        wa.addView(again, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f));
        TextView backBtn = plateButton("ALL GAMES", v -> close());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f);
        bl.leftMargin = Ui.dp(c, 10);
        wa.addView(backBtn, bl);
        wonPlate.addView(wa, Ui.lp(-1, -2, 0, Ui.dp(c, 12), 0, 0));
        wonPlate.setVisibility(View.GONE);
        col.addView(wonPlate, Ui.lp(-1, -2, 0, Ui.dp(c, 14), 0, 0));

        view.setVisibility(View.GONE);
        view.setElevation(Ui.dp(c, 30));
        a.root().addView(view, new FrameLayout.LayoutParams(-1, -1));
        applyInsets(a.topInset(), a.navInset());
    }

    private void toggleHelp() {
        helpText.setVisibility(helpText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private TextView plateButton(String label, View.OnClickListener l) {
        TextView t = Ui.text(a, label, 12.5f, Ui.INK, Ui.W_BOLD);
        t.setLetterSpacing(0.08f);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(Ui.roundStroke(Ui.PAPER, Ui.dp(a, 8), Ui.INK, Ui.line()), Ui.dp(a, 8)));
        t.setOnClickListener(l);
        Ui.press(t, 0.94f);
        return t;
    }

    void applyInsets(int top, int nav) {
        ViewGroup.LayoutParams sb = statusBand.getLayoutParams();
        sb.height = top;
        statusBand.setLayoutParams(sb);
        col.setPadding(col.getPaddingLeft(), top + Ui.dp(a, 12 + 12), col.getPaddingRight(), nav + Ui.dp(a, 28));
    }

    boolean isOpen() {
        return open;
    }

    /** Tests: the view holding the puzzle. */
    View board() {
        return boardHolder;
    }

    static boolean hasSaved(Context c, String id) {
        return Prefs.get(c).contains("game_" + id);
    }

    /** Opens a game, continuing a saved puzzle or starting today's. */
    void open(String id) {
        kind = Games.find(id);
        boardHolder.removeAllViews();
        game = kind.make(a);
        game.setHost(this);
        boardHolder.addView(game, new FrameLayout.LayoutParams(-1, -2));
        title.setText(kind.name);
        helpText.setText(game.help());
        helpText.setVisibility(View.GONE);
        levelRow.setVisibility(game.levels() > 1 ? View.VISIBLE : View.GONE);
        String saved = Prefs.get(a).getString("game_" + id, null);
        boolean restored = false;
        if (saved != null) {
            try {
                JSONObject o = new JSONObject(saved);
                level = o.optInt("level", 1);
                daily = o.optBoolean("daily");
                elapsed = o.optLong("elapsed");
                restored = game.restore(o.getString("state"));
            } catch (Exception ignored) {
            }
        }
        if (!restored) {
            level = Math.min(game.levels() - 1, Prefs.get(a).getInt("level_" + id, 1));
            startDaily();
        }
        levels.select(level, false);
        wonPlate.setVisibility(game.isWon() ? View.VISIBLE : View.GONE);
        open = true;
        runningSince = game.isWon() ? 0 : System.currentTimeMillis();
        timer.setText(Puzzles.clock(elapsed));
        a.main().removeCallbacks(tick);
        a.main().post(tick);
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
        scroll.scrollTo(0, 0);
        view.setTranslationY(a.root().getHeight() > 0 ? a.root().getHeight() : 2000);
        view.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        view.announceForAccessibility(kind.name);
    }

    private void startDaily() {
        daily = true;
        elapsed = 0;
        game.start(Puzzles.dailySeed(Games.index(kind.id), System.currentTimeMillis()), level);
        persist();
    }

    private void newPuzzle(boolean today) {
        Prefs.get(a).edit().putInt("level_" + kind.id, level).apply();
        wonPlate.setVisibility(View.GONE);
        if (today) {
            startDaily();
        } else {
            daily = false;
            elapsed = 0;
            game.start(System.nanoTime() ^ (long) (Math.random() * Long.MAX_VALUE), level);
            persist();
        }
        runningSince = System.currentTimeMillis();
        timer.setText("0:00");
        scroll.smoothScrollTo(0, 0);
    }

    private long elapsedNow() {
        return elapsed + (runningSince > 0 ? System.currentTimeMillis() - runningSince : 0);
    }

    private void persist() {
        try {
            JSONObject o = new JSONObject().put("state", game.save()).put("level", level).put("daily", daily).put("elapsed", elapsedNow());
            Prefs.get(a).edit().putString("game_" + kind.id, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    void close() {
        if (!open) return;
        if (runningSince > 0) {
            elapsed = elapsedNow();
            runningSince = 0;
        }
        if (game != null && !game.isWon()) persist();
        open = false;
        a.main().removeCallbacks(tick);
        a.gamesChanged();
        view.animate().translationY(view.getHeight()).setDuration(220).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!open) view.setVisibility(View.GONE);
                }).start();
    }

    /** The app went to the background: stop the clock. */
    void pause() {
        if (open && runningSince > 0) {
            elapsed = elapsedNow();
            runningSince = 0;
            if (game != null) persist();
        }
    }

    void resume() {
        if (open && runningSince == 0 && game != null && !game.isWon()) runningSince = System.currentTimeMillis();
    }

    @Override
    public void onWon(Game g) {
        long time = elapsedNow();
        runningSince = 0;
        elapsed = time;
        timer.setText(Puzzles.clock(time));
        long best = Games.best(a, kind.id, level);
        Games.saveBest(a, kind.id, level, time);
        if (daily) Games.markDaily(a, kind.id, System.currentTimeMillis());
        Prefs.get(a).edit().remove("game_" + kind.id).apply();
        String s = "Finished in " + Puzzles.clock(time) + ".";
        if (best == 0) s += " Your first time on this one.";
        else if (time < best) s += " A new best, " + Puzzles.clock(best - time) + " faster.";
        else s += " Your best is " + Puzzles.clock(best) + ".";
        if (daily) {
            int streak = Games.streak(a, System.currentTimeMillis());
            s += streak > 1 ? " " + streak + " days in a row." : " Today's done.";
        }
        wonText.setText(s);
        wonPlate.setVisibility(View.VISIBLE);
        a.main().postDelayed(() -> scroll.smoothScrollTo(0, col.getHeight()), 250);
        a.gamesChanged();
    }

    @Override
    public void onStatus(Game g, String s) {
        status.setText(s);
    }

    @Override
    public void onMoved(Game g) {
        if (!g.isWon()) persist();
    }
}
