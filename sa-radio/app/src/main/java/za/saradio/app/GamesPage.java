package za.saradio.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The Games tab: a sign for each puzzle, today's puzzle count and the streak. */
final class GamesPage extends ScrollView {
    private final MainActivity a;
    private final LinearLayout col;
    private final TextView dailyLine;
    private final TextView[] subs = new TextView[Games.ALL.length];

    GamesPage(MainActivity act) {
        super(act);
        a = act;
        Context c = act;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setClipToPadding(false);
        col = Ui.column(c);
        col.setPadding(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 20), Ui.dp(c, 160));
        addView(col, new FrameLayout.LayoutParams(-1, -2));

        // Today's puzzles: a destination board.
        LinearLayout board = Ui.row(c);
        board.setBackground(new Livery.Board(c, Ui.INK, new int[] {Ui.YELLOW, Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN}, 80));
        board.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 90), Ui.dp(c, 14));
        LinearLayout bc = Ui.column(c);
        TextView t = Ui.text(c, "TODAY'S PUZZLES", 11, Ui.YELLOW, Ui.W_BOLD);
        t.setLetterSpacing(0.14f);
        bc.addView(t);
        dailyLine = Ui.display(c, "", 24, Ui.PAPER);
        dailyLine.setPadding(0, Ui.dp(c, 4), 0, 0);
        bc.addView(dailyLine);
        TextView note = Ui.text(c, "Everyone gets the same puzzles each day. Or tap New for a fresh one.", 12.5f, 0xFFE6E6E6, Ui.W_MEDIUM);
        note.setPadding(0, Ui.dp(c, 4), 0, 0);
        bc.addView(note);
        board.addView(bc, Ui.weight(1));
        col.addView(board, Ui.lp(-1, -2, 0, Ui.dp(c, 8), 0, Ui.dp(c, 18)));

        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.START | Gravity.TOP);
        TextView title = Ui.sign(c, "Games", 24, Ui.SIGN_RED);
        Ui.paintedShadow(title, Ui.dp(c, 2), Ui.dp(c, 2), Ui.INK);
        Ui.heading(title);
        head.addView(title);
        TextView tag = Ui.text(c, "something to do while you listen", 20, Ui.COBALT, Ui.W_REGULAR);
        tag.setTypeface(Ui.brush);
        tag.setPadding(Ui.dp(c, 10), 0, 0, 0);
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(tag, Ui.lp(-2, -2));
        col.addView(head, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 12)));

        LinearLayout row = null;
        for (int i = 0; i < Games.ALL.length; i++) {
            if (i % 2 == 0) {
                row = Ui.row(c);
                row.setGravity(Gravity.TOP);
                col.addView(row, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(c, 12)));
            }
            View sign = sign(Games.ALL[i], i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 128), 1f);
            if (i % 2 == 0) lp.rightMargin = Ui.dp(c, 12);
            row.addView(sign, lp);
        }
        refresh();
    }

    private View sign(final Games.Kind k, int i) {
        Context c = a;
        int sh = Ui.dp(c, 3);
        LinearLayout v = Ui.column(c);
        v.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12) + sh, Ui.dp(c, 12) + sh);
        int shadow = k.color == Ui.INK ? Ui.SIGN_RED : Ui.INK;
        v.setBackground(Ui.plate(k.color, Ui.dp(c, 8), sh, shadow));
        int on = Ui.onColor(k.color);
        TextView name = Ui.sign(c, k.name, 16, on);
        name.setMaxLines(2);
        v.addView(name);
        TextView blurb = Ui.text(c, k.blurb, 12, on, Ui.W_MEDIUM);
        blurb.setPadding(0, Ui.dp(c, 4), 0, 0);
        blurb.setMaxLines(2);
        v.addView(blurb);
        v.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 1f));
        subs[i] = Ui.text(c, "", 11, on, Ui.W_BOLD);
        subs[i].setLetterSpacing(0.06f);
        subs[i].setSingleLine(true);
        subs[i].setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable bg = Ui.round(on == Ui.INK ? Ui.alpha(Ui.PAPER, 0.55f) : Ui.alpha(Ui.INK, 0.35f), Ui.dp(c, 4));
        subs[i].setBackground(bg);
        subs[i].setPadding(Ui.dp(c, 6), Ui.dp(c, 3), Ui.dp(c, 6), Ui.dp(c, 3));
        v.addView(subs[i], Ui.lp(-2, -2));
        v.setOnClickListener(x -> a.openGame(k.id));
        v.setContentDescription(k.name + ". " + k.blurb);
        Ui.press(v, 0.95f);
        return v;
    }

    /** Updates the "done today" and best labels. */
    void refresh() {
        long now = System.currentTimeMillis();
        int done = 0;
        for (int i = 0; i < Games.ALL.length; i++) {
            Games.Kind k = Games.ALL[i];
            boolean d = Games.dailyDone(a, k.id, now);
            if (d) done++;
            long best = Games.best(a, k.id, 1);
            if (best == 0) best = Games.best(a, k.id, 0);
            String s = d ? "DONE TODAY" : GameHost.hasSaved(a, k.id) ? "CONTINUE" : "PLAY";
            if (best > 0) s += " · BEST " + Puzzles.clock(best);
            subs[i].setText(s);
        }
        int streak = Games.streak(a, now);
        dailyLine.setText(done + " of " + Games.ALL.length + " done" + (streak > 1 ? " · " + streak + "-day streak" : ""));
    }
}
