package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Sudoku: a 9×9 grid with a number pad underneath. Tap a cell, tap a number; pencil marks on long tap of the pad. */
final class SudokuGame extends Game {
    private Puzzles.Sudoku p;
    private final int[] grid = new int[81];
    private final int[] notes = new int[81]; // bit n-1 set = pencil mark n
    private int sel = -1;
    private boolean pencil;
    private int mistakes;

    SudokuGame(Context c) {
        super(c);
    }

    @Override
    int levels() {
        return 3;
    }

    @Override
    String help() {
        return "Fill every row, column and 3×3 box with 1 to 9. Tap a cell, then a number. Hold a number for pencil marks.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        this.level = level;
        p = Puzzles.sudoku(seed, level);
        System.arraycopy(p.puzzle, 0, grid, 0, 81);
        java.util.Arrays.fill(notes, 0);
        sel = -1;
        won = false;
        mistakes = 0;
        pencil = false;
        invalidate();
        status(p.givens + " given · " + (81 - p.givens) + " to fill");
    }

    @Override
    String save() {
        try {
            StringBuilder g = new StringBuilder(), n = new StringBuilder();
            for (int i = 0; i < 81; i++) {
                g.append(grid[i]);
                n.append((char) ('A' + (notes[i] & 31))).append((char) ('A' + (notes[i] >> 5)));
            }
            return new JSONObject().put("seed", seed).put("level", level).put("grid", g.toString()).put("notes", n.toString())
                    .put("mistakes", mistakes).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), o.getInt("level"));
            String g = o.getString("grid"), n = o.optString("notes");
            for (int i = 0; i < 81; i++) {
                grid[i] = g.charAt(i) - '0';
                if (n.length() == 162) notes[i] = (n.charAt(i * 2) - 'A') | ((n.charAt(i * 2 + 1) - 'A') << 5);
            }
            mistakes = o.optInt("mistakes");
            won = o.optBoolean("won");
            invalidate();
            progress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected float aspect() {
        return 1f + 0.26f;
    }

    private float cell() {
        return (getWidth() - 4 * d) / 9f;
    }

    private float padTop() {
        return cell() * 9 + 14 * d;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (p == null) return;
        float s = cell(), x0 = 2 * d, y0 = 2 * d;
        // Board
        plate(c, 0, 0, getWidth(), s * 9 + 4 * d, Ui.PAPER, 8 * d, 2 * d);
        boolean[] bad = conflicts();
        for (int i = 0; i < 81; i++) {
            int r = i / 9, col = i % 9;
            float l = x0 + col * s, t = y0 + r * s;
            boolean given = p.puzzle[i] != 0;
            if (i == sel) {
                fill.setColor(Ui.YELLOW);
                c.drawRect(l, t, l + s, t + s, fill);
            } else if (sel >= 0 && grid[sel] != 0 && grid[i] == grid[sel]) {
                fill.setColor(Ui.alpha(Ui.YELLOW, 0.45f));
                c.drawRect(l, t, l + s, t + s, fill);
            } else if (sel >= 0 && (r == sel / 9 || col == sel % 9 || (r / 3 == sel / 9 / 3 && col / 3 == sel % 9 / 3))) {
                fill.setColor(Ui.SURFACE3);
                c.drawRect(l, t, l + s, t + s, fill);
            }
            if (grid[i] != 0) {
                int color = bad[i] ? Ui.SIGN_RED : given ? Ui.INK : Ui.COBALT;
                centred(c, String.valueOf(grid[i]), l + s / 2, t + s / 2, s * 0.58f, color, given ? Ui.display : Ui.bold);
            } else if (notes[i] != 0) {
                for (int n = 1; n <= 9; n++) {
                    if ((notes[i] & (1 << (n - 1))) == 0) continue;
                    float nx = l + s * (0.2f + ((n - 1) % 3) * 0.3f), ny = t + s * (0.22f + ((n - 1) / 3) * 0.3f);
                    centred(c, String.valueOf(n), nx, ny, s * 0.24f, Ui.MUTED, Ui.bold);
                }
            }
        }
        // Lines
        for (int i = 0; i <= 9; i++) {
            float w = i % 3 == 0 ? 2.5f * d : 0.8f * d;
            ink.setStrokeWidth(w);
            c.drawLine(x0 + i * s, y0, x0 + i * s, y0 + 9 * s, ink);
            c.drawLine(x0, y0 + i * s, x0 + 9 * s, y0 + i * s, ink);
        }
        // Pad: 1–9, erase
        float pt = padTop(), pw = (getWidth() - 9 * 4 * d) / 10f, ph = getHeight() - pt - 2 * d;
        for (int n = 1; n <= 10; n++) {
            float l = (n - 1) * (pw + 4 * d);
            boolean done = n <= 9 && count(n) >= 9;
            int color = n == 10 ? Ui.PAPER : pencil ? Ui.SURFACE3 : done ? Ui.SURFACE3 : Ui.YELLOW;
            plate(c, l, pt, l + pw, pt + ph, color, 6 * d, 2 * d);
            if (n == 10) {
                centred(c, "⌫", l + pw / 2, pt + ph / 2, ph * 0.42f, Ui.INK, Ui.bold);
            } else {
                centred(c, String.valueOf(n), l + pw / 2, pt + ph / 2, ph * (pencil ? 0.36f : 0.5f), done ? Ui.MUTED : Ui.INK,
                        pencil ? Ui.bold : Ui.display);
            }
        }
        if (pencil) centred(c, "PENCIL", getWidth() / 2f, pt - 6 * d, 9 * d, Ui.COBALT, Ui.bold);
    }

    private int count(int n) {
        int k = 0;
        for (int v : grid) if (v == n) k++;
        return k;
    }

    private boolean[] conflicts() {
        boolean[] bad = new boolean[81];
        for (int i = 0; i < 81; i++) {
            if (grid[i] == 0) continue;
            int v = grid[i];
            grid[i] = 0;
            if (!Puzzles.fits(grid, i, v)) bad[i] = true;
            grid[i] = v;
        }
        return bad;
    }

    private long downAt;
    private int downKey = -1;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (p == null || won) return true;
        float s = cell();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (e.getY() < s * 9 + 4 * d) {
                int col = (int) ((e.getX() - 2 * d) / s), r = (int) ((e.getY() - 2 * d) / s);
                if (col >= 0 && col < 9 && r >= 0 && r < 9) {
                    sel = r * 9 + col;
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                }
                downKey = -1;
            } else if (e.getY() >= padTop()) {
                float pw = (getWidth() - 9 * 4 * d) / 10f;
                downKey = (int) (e.getX() / (pw + 4 * d)) + 1;
                downAt = System.currentTimeMillis();
                // Holding a number toggles pencil mode.
                postDelayed(() -> {
                    if (downKey > 0 && downKey <= 9 && System.currentTimeMillis() - downAt >= 380) {
                        pencil = !pencil;
                        downKey = -1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        status(pencil ? "Pencil marks on: tap numbers to note them" : "Pencil marks off");
                        invalidate();
                    }
                }, 400);
            }
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && downKey > 0) {
            int k = downKey;
            downKey = -1;
            if (sel < 0 || p.puzzle[sel] != 0) {
                status(sel < 0 ? "Tap a cell first" : "That one is given");
                return true;
            }
            if (k == 10) {
                grid[sel] = 0;
                notes[sel] = 0;
            } else if (pencil) {
                notes[sel] ^= 1 << (k - 1);
                grid[sel] = 0;
            } else {
                grid[sel] = grid[sel] == k ? 0 : k;
                notes[sel] = 0;
                if (grid[sel] != 0 && grid[sel] != p.solution[sel]) mistakes++;
            }
            moved();
            progress();
        }
        return true;
    }

    private void progress() {
        int empty = 0;
        for (int v : grid) if (v == 0) empty++;
        if (empty == 0 && Puzzles.sudokuSolved(grid.clone())) {
            status("Solved" + (mistakes > 0 ? " · " + mistakes + (mistakes == 1 ? " slip" : " slips") : " · no slips"));
            won();
        } else {
            status(empty + " to fill" + (mistakes > 0 ? " · " + mistakes + (mistakes == 1 ? " slip" : " slips") : ""));
        }
    }
}
