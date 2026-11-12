package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Pairs: turn over two cards at a time and find the matching pairs. Cards are painted signs in the taxi palette. */
final class MemoryGame extends Game {
    private static final int COLS = 4;
    private static final String GLYPHS = "ABCDEFGHJKLMNPRSTUVW";
    private static final int[] COLORS = {Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN, Ui.SIGN_RED, 0xFF61107D, 0xFFF28C0F, 0xFF13A89E,
        0xFF8E44AD, 0xFF0B6E4F, 0xFFE4572E, Ui.INK, 0xFF2D6A4F};
    private int[] deck;
    private boolean[] up;
    private boolean[] done;
    private int first = -1, second = -1;
    private int turns;
    private int pairs = 8;

    MemoryGame(Context c) {
        super(c);
    }

    @Override
    int levels() {
        return 3;
    }

    @Override
    String help() {
        return "Turn two cards. If they match they stay up. Clear the board in as few turns as you can.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        this.level = level;
        pairs = level <= 0 ? 6 : level == 1 ? 8 : 12;
        deck = Puzzles.memory(seed, pairs);
        up = new boolean[deck.length];
        done = new boolean[deck.length];
        first = second = -1;
        turns = 0;
        won = false;
        invalidate();
        status(pairs + " pairs · 0 turns");
    }

    @Override
    String save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (boolean b : done) sb.append(b ? '1' : '0');
            return new JSONObject().put("seed", seed).put("level", level).put("done", sb.toString()).put("turns", turns).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), o.getInt("level"));
            String dn = o.getString("done");
            for (int i = 0; i < done.length; i++) done[i] = up[i] = dn.charAt(i) == '1';
            turns = o.optInt("turns");
            won = o.optBoolean("won");
            status(left() + " pairs left · " + turns + " turns");
            invalidate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int rows() {
        return (deck.length + COLS - 1) / COLS;
    }

    @Override
    protected float aspect() {
        return deck == null ? 1f : rows() / (float) COLS * 1.08f;
    }

    private float cw() {
        return (getWidth() - (COLS + 1) * 6 * d) / COLS;
    }

    private float ch() {
        return (getHeight() - (rows() + 1) * 6 * d) / rows();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (deck == null) return;
        float w = cw(), h = ch();
        for (int i = 0; i < deck.length; i++) {
            float l = 6 * d + (i % COLS) * (w + 6 * d), t = 6 * d + (i / COLS) * (h + 6 * d);
            if (up[i] || done[i]) {
                int color = COLORS[deck[i] % COLORS.length];
                plate(c, l, t, l + w, t + h, color, 8 * d, 2 * d);
                fill.setColor(Ui.PAPER);
                float r = Math.min(w, h) * 0.3f;
                c.drawCircle(l + w / 2, t + h / 2, r, fill);
                centred(c, String.valueOf(GLYPHS.charAt(deck[i] % GLYPHS.length())), l + w / 2, t + h / 2, r * 1.1f, color == Ui.INK ? Ui.INK : color, Ui.sign);
                if (done[i]) {
                    fill.setColor(Ui.alpha(Ui.PAPER, 0.35f));
                    rect.set(l, t, l + w, t + h);
                    c.drawRoundRect(rect, 8 * d, 8 * d, fill);
                }
            } else {
                plate(c, l, t, l + w, t + h, Ui.YELLOW, 8 * d, 2 * d);
                // The back: taxi livery stripes.
                c.save();
                rect.set(l + 2 * d, t + 2 * d, l + w - 2 * d, t + h - 2 * d);
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(rect, 6 * d, 6 * d, android.graphics.Path.Direction.CW);
                c.clipPath(clip);
                fill.setColor(Ui.INK);
                for (float x = l - h; x < l + w + h; x += 16 * d) {
                    android.graphics.Path s = new android.graphics.Path();
                    s.moveTo(x, t + h);
                    s.lineTo(x + h * 0.5f, t);
                    s.lineTo(x + h * 0.5f + 5 * d, t);
                    s.lineTo(x + 5 * d, t + h);
                    s.close();
                    c.drawPath(s, fill);
                }
                c.restore();
            }
        }
    }

    private final Runnable flipBack = () -> {
        if (first >= 0 && second >= 0 && !done[first]) {
            up[first] = up[second] = false;
        }
        first = second = -1;
        invalidate();
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (deck == null || won || e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float w = cw(), h = ch();
        int col = (int) ((e.getX() - 6 * d) / (w + 6 * d)), r = (int) ((e.getY() - 6 * d) / (h + 6 * d));
        int i = r * COLS + col;
        if (col < 0 || col >= COLS || r < 0 || i >= deck.length || up[i] || done[i]) return true;
        if (second >= 0) {
            removeCallbacks(flipBack);
            flipBack.run();
        }
        up[i] = true;
        if (first < 0) {
            first = i;
        } else {
            second = i;
            turns++;
            if (deck[first] == deck[second]) {
                done[first] = done[second] = true;
                first = second = -1;
                if (left() == 0) {
                    status("All pairs in " + turns + " turns");
                    moved();
                    won();
                    return true;
                }
            } else {
                postDelayed(flipBack, 900);
            }
            status(left() + " pairs left · " + turns + (turns == 1 ? " turn" : " turns"));
        }
        moved();
        return true;
    }

    private int left() {
        int n = 0;
        for (boolean b : done) if (!b) n++;
        return n / 2;
    }
}
