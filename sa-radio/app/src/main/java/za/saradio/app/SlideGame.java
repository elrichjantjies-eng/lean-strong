package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Number slide: put the number plates back in order by sliding them into the gap. */
final class SlideGame extends Game {
    private int n = 4;
    private int[] g;
    private int moves;

    SlideGame(Context c) {
        super(c);
    }

    @Override
    int levels() {
        return 3;
    }

    @Override
    String help() {
        return "Slide the plates into the empty space until they read 1 to " + (n * n - 1) + " in order. Fewer moves is better.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        this.level = level;
        n = level <= 0 ? 3 : level == 1 ? 4 : 5;
        g = Puzzles.slide(seed, n, n * n * 12);
        moves = 0;
        won = false;
        invalidate();
        status(n + "×" + n + " · 0 moves");
    }

    @Override
    String save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int v : g) sb.append(v).append(',');
            return new JSONObject().put("seed", seed).put("level", level).put("grid", sb.toString()).put("moves", moves).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), o.getInt("level"));
            String[] parts = o.getString("grid").split(",");
            for (int i = 0; i < g.length; i++) g[i] = Integer.parseInt(parts[i]);
            moves = o.optInt("moves");
            won = o.optBoolean("won");
            status(n + "×" + n + " · " + moves + " moves");
            invalidate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private float cell() {
        return (getWidth() - 8 * d) / n;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (g == null) return;
        float s = cell();
        plate(c, 0, 0, getWidth(), getHeight(), Ui.ROAD, 10 * d, 2 * d);
        for (int i = 0; i < g.length; i++) {
            if (g[i] == 0) continue;
            float l = 4 * d + (i % n) * s + 3 * d, t = 4 * d + (i / n) * s + 3 * d;
            boolean home = g[i] == i + 1;
            plate(c, l, t, l + s - 6 * d, t + s - 6 * d, won ? Ui.YELLOW : home ? Ui.YELLOW : Ui.PAPER, 6 * d, 2 * d);
            // Number-plate screws.
            fill.setColor(0xFF9A9A9A);
            c.drawCircle(l + 7 * d, t + 7 * d, 2 * d, fill);
            c.drawCircle(l + s - 13 * d, t + 7 * d, 2 * d, fill);
            centred(c, String.valueOf(g[i]), l + (s - 6 * d) / 2, t + (s - 6 * d) / 2, s * 0.42f, Ui.INK, Ui.display);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (g == null || won || e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float s = cell();
        int col = (int) ((e.getX() - 4 * d) / s), r = (int) ((e.getY() - 4 * d) / s);
        if (col < 0 || col >= n || r < 0 || r >= n) return true;
        int i = r * n + col, blank = -1;
        for (int k = 0; k < g.length; k++) if (g[k] == 0) blank = k;
        int bx = blank % n, by = blank / n;
        if (col != bx && r != by) return true;
        // Slide the whole run of plates between the tap and the gap: each plate moves one place
        // towards where the gap was, and the gap ends up where you tapped.
        int towardTap = col == bx ? (r < by ? -n : n) : (col < bx ? -1 : 1);
        int k = blank;
        while (k != i) {
            g[k] = g[k + towardTap];
            k += towardTap;
        }
        g[i] = 0;
        moves++;
        moved();
        if (Puzzles.slideSolved(g)) {
            status("Solved in " + moves + " moves");
            won();
        } else {
            status(n + "×" + n + " · " + moves + (moves == 1 ? " move" : " moves"));
        }
        return true;
    }
}
