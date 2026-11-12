package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import org.json.JSONObject;

/** Picture logic: the numbers say how many cells in a row are filled, in order. Fill them in to reveal a picture. */
final class PictureGame extends Game {
    private Puzzles.Picture p;
    private int[] marks; // 0 blank, 1 filled, 2 crossed out
    private boolean crossMode;
    private int paintValue = -1, lastCell = -1;

    PictureGame(Context c) {
        super(c);
    }

    @Override
    String help() {
        return "The numbers give the runs of filled cells in each row and column, in order, with a gap between runs. Tap to fill; tap FILL/X to switch to crossing out.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        p = Puzzles.picture(seed);
        marks = new int[p.size * p.size];
        won = false;
        crossMode = false;
        invalidate();
        status(p.size + "×" + p.size + " picture");
    }

    @Override
    String save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int m : marks) sb.append(m);
            return new JSONObject().put("seed", seed).put("marks", sb.toString()).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), 0);
            String m = o.getString("marks");
            for (int i = 0; i < marks.length && i < m.length(); i++) marks[i] = m.charAt(i) - '0';
            won = o.optBoolean("won");
            progress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected float aspect() {
        return 1.18f;
    }

    private float clueW() {
        return getWidth() * 0.22f;
    }

    private float cell() {
        return (getWidth() - clueW() - 4 * d) / p.size;
    }

    private float gridTop() {
        return clueW() * 0.9f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (p == null) return;
        float s = cell(), gx = clueW() + 2 * d, gy = gridTop(), n = p.size;
        plate(c, clueW(), gy - 2 * d, getWidth(), gy + s * n + 2 * d, Ui.PAPER, 6 * d, 2 * d);
        for (int i = 0; i < n * n; i++) {
            float l = gx + (i % (int) n) * s, t = gy + (i / (int) n) * s;
            if (marks[i] == 1) {
                fill.setColor(won ? Ui.COBALT : Ui.INK);
                c.drawRect(l + d, t + d, l + s - d, t + s - d, fill);
            } else if (marks[i] == 2 && !won) {
                ink.setStrokeWidth(1.5f * d);
                c.drawLine(l + s * 0.3f, t + s * 0.3f, l + s * 0.7f, t + s * 0.7f, ink);
                c.drawLine(l + s * 0.7f, t + s * 0.3f, l + s * 0.3f, t + s * 0.7f, ink);
            }
        }
        for (int i = 0; i <= n; i++) {
            ink.setStrokeWidth(i % 5 == 0 ? 2 * d : 0.6f * d);
            c.drawLine(gx + i * s, gy, gx + i * s, gy + n * s, ink);
            c.drawLine(gx, gy + i * s, gx + n * s, gy + i * s, ink);
        }
        // Clues
        float cs = Math.min(s * 0.5f, 11 * d);
        for (int r = 0; r < n; r++) {
            int[] cl = p.rowClues[r];
            boolean done = rowDone(r);
            float x = clueW() - 4 * d, y = gy + r * s + s / 2;
            text.setTextAlign(android.graphics.Paint.Align.RIGHT);
            for (int i = cl.length - 1; i >= 0; i--) {
                centred(c, String.valueOf(cl[i]), x - 4 * d, y, cs, done ? Ui.MUTED : Ui.INK, Ui.bold);
                x -= cs * 1.3f;
            }
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        for (int col = 0; col < n; col++) {
            int[] cl = p.colClues[col];
            boolean done = colDone(col);
            float x = gx + col * s + s / 2, y = gy - 8 * d;
            for (int i = cl.length - 1; i >= 0; i--) {
                centred(c, String.valueOf(cl[i]), x, y, cs, done ? Ui.MUTED : Ui.INK, Ui.bold);
                y -= cs * 1.25f;
            }
        }
        // Mode switch
        float bt = gy + s * n + 12 * d, bh = getHeight() - bt - 2 * d, bw = getWidth() * 0.42f;
        plate(c, 0, bt, bw, bt + bh, crossMode ? Ui.PAPER : Ui.YELLOW, 6 * d, 2 * d);
        centred(c, "FILL", bw / 2, bt + bh / 2, bh * 0.42f, Ui.INK, Ui.display);
        plate(c, getWidth() - bw, bt, getWidth(), bt + bh, crossMode ? Ui.YELLOW : Ui.PAPER, 6 * d, 2 * d);
        centred(c, "X", getWidth() - bw / 2, bt + bh / 2, bh * 0.42f, Ui.INK, Ui.display);
    }

    private boolean rowDone(int r) {
        boolean[] line = new boolean[p.size];
        for (int i = 0; i < p.size; i++) line[i] = marks[r * p.size + i] == 1;
        return java.util.Arrays.equals(Puzzles.clues(line), p.rowClues[r]);
    }

    private boolean colDone(int col) {
        boolean[] line = new boolean[p.size];
        for (int i = 0; i < p.size; i++) line[i] = marks[i * p.size + col] == 1;
        return java.util.Arrays.equals(Puzzles.clues(line), p.colClues[col]);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (p == null || won) return true;
        float s = cell(), gx = clueW() + 2 * d, gy = gridTop();
        int a = e.getActionMasked();
        float bt = gy + s * p.size + 12 * d;
        if (a == MotionEvent.ACTION_DOWN && e.getY() >= bt) {
            crossMode = e.getX() > getWidth() / 2f;
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            status(crossMode ? "Crossing out cells you know are empty" : "Filling cells");
            invalidate();
            return true;
        }
        int col = (int) ((e.getX() - gx) / s), r = (int) ((e.getY() - gy) / s);
        boolean in = col >= 0 && col < p.size && r >= 0 && r < p.size && e.getX() >= gx && e.getY() >= gy;
        int cell = in ? r * p.size + col : -1;
        if (a == MotionEvent.ACTION_DOWN) {
            if (!in) return true;
            getParent().requestDisallowInterceptTouchEvent(true);
            int want = crossMode ? 2 : 1;
            paintValue = marks[cell] == want ? 0 : want;
            marks[cell] = paintValue;
            lastCell = cell;
            moved();
            progress();
        } else if (a == MotionEvent.ACTION_MOVE && in && cell != lastCell && paintValue >= 0) {
            // Dragging paints a line of cells.
            marks[cell] = paintValue;
            lastCell = cell;
            invalidate();
            progress();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            paintValue = -1;
            lastCell = -1;
        }
        return true;
    }

    private void progress() {
        boolean[] filled = new boolean[marks.length];
        for (int i = 0; i < marks.length; i++) filled[i] = marks[i] == 1;
        if (Puzzles.pictureSolved(p, filled)) {
            for (int i = 0; i < marks.length; i++) if (marks[i] == 2) marks[i] = 0;
            status("Picture complete");
            won();
            return;
        }
        int rows = 0, cols = 0;
        for (int i = 0; i < p.size; i++) {
            if (rowDone(i)) rows++;
            if (colDone(i)) cols++;
        }
        status(rows + " rows and " + cols + " columns right");
    }
}
