package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Word search: drag across the letters to mark a word. Themes come from the word list. */
final class WordSearchGame extends Game {
    private Puzzles.WordSearch w;
    private String theme = "";
    private final List<Integer> found = new ArrayList<>();
    /** Found words as {start, dx, dy, len}. */
    private final List<int[]> lines = new ArrayList<>();
    private int dragFrom = -1, dragTo = -1;
    private final int[] COLORS = {Ui.YELLOW, Ui.PINK, Ui.COBALT, Ui.LIVERY_GREEN, 0xFFF28C0F, 0xFF61107D, 0xFF13A89E};

    WordSearchGame(Context c) {
        super(c);
    }

    @Override
    String help() {
        return "Find the words hidden in the grid. Drag your finger across a word in a straight line.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        this.level = level;
        List<Words.Theme> themes = Words.themes(getContext());
        Words.Theme t = themes.get((int) (Math.abs(seed) % themes.size()));
        theme = t.name;
        w = Puzzles.wordSearch(seed, 10, t.words, 8);
        found.clear();
        lines.clear();
        won = false;
        invalidate();
        status(theme + " · " + w.words.size() + " words");
    }

    @Override
    String save() {
        try {
            JSONArray f = new JSONArray();
            for (int i : found) f.put(i);
            JSONArray l = new JSONArray();
            for (int[] x : lines) l.put(new JSONArray().put(x[0]).put(x[1]).put(x[2]).put(x[3]));
            return new JSONObject().put("seed", seed).put("found", f).put("lines", l).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), 0);
            JSONArray f = o.getJSONArray("found");
            for (int i = 0; i < f.length(); i++) found.add(f.getInt(i));
            JSONArray l = o.getJSONArray("lines");
            for (int i = 0; i < l.length(); i++) {
                JSONArray x = l.getJSONArray(i);
                lines.add(new int[] {x.getInt(0), x.getInt(1), x.getInt(2), x.getInt(3)});
            }
            won = o.optBoolean("won");
            progress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected float aspect() {
        return 1.34f;
    }

    private float cell() {
        return (getWidth() - 4 * d) / w.size;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (w == null) return;
        float s = cell(), x0 = 2 * d, y0 = 2 * d, n = w.size;
        plate(c, 0, 0, getWidth(), s * n + 4 * d, Ui.PAPER, 8 * d, 2 * d);
        // Found lines, as fat coloured strokes under the letters.
        ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        for (int i = 0; i < lines.size(); i++) {
            int[] l = lines.get(i);
            strokeLine(c, l[0], l[1], l[2], l[3], Ui.alpha(COLORS[i % COLORS.length], 0.75f), s);
        }
        if (dragFrom >= 0 && dragTo >= 0) {
            int[] l = line(dragFrom, dragTo);
            if (l != null) strokeLine(c, l[0], l[1], l[2], l[3], Ui.alpha(Ui.INK, 0.18f), s);
        }
        for (int i = 0; i < n * n; i++) {
            centred(c, String.valueOf(w.grid[i]), x0 + (i % (int) n) * s + s / 2, y0 + (i / (int) n) * s + s / 2, s * 0.5f, Ui.INK, Ui.display);
        }
        // Word list under the board.
        TextPaint tp = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Ui.bold);
        tp.setTextSize(12.5f * d);
        float top = s * n + 12 * d, colW = getWidth() / 3f, rowH = 18 * d;
        for (int i = 0; i < w.words.size(); i++) {
            boolean got = found.contains(i);
            tp.setColor(got ? Ui.MUTED : Ui.INK);
            tp.setStrikeThruText(got);
            float x = (i % 3) * colW + 6 * d, y = top + (i / 3) * rowH + 13 * d;
            c.drawText(w.words.get(i), x, y, tp);
        }
    }

    private void strokeLine(Canvas c, int start, int dx, int dy, int len, int color, float s) {
        int n = w.size;
        float x0 = 2 * d + (start % n) * s + s / 2, y0 = 2 * d + (start / n) * s + s / 2;
        float x1 = x0 + dx * (len - 1) * s, y1 = y0 + dy * (len - 1) * s;
        ink.setColor(color);
        ink.setStrokeWidth(s * 0.72f);
        c.drawLine(x0, y0, x1, y1, ink);
        ink.setColor(Ui.INK);
        ink.setStrokeWidth(0);
    }

    /** The straight line between two cells, or null if they're not in a line. */
    private int[] line(int a, int b) {
        int n = w.size;
        int dx = Integer.signum(b % n - a % n), dy = Integer.signum(b / n - a / n);
        int ax = Math.abs(b % n - a % n), ay = Math.abs(b / n - a / n);
        if (ax != 0 && ay != 0 && ax != ay) return null;
        return new int[] {a, dx, dy, Math.max(ax, ay) + 1};
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (w == null || won) return true;
        float s = cell();
        int col = (int) ((e.getX() - 2 * d) / s), r = (int) ((e.getY() - 2 * d) / s);
        boolean in = col >= 0 && col < w.size && r >= 0 && r < w.size;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragFrom = in ? r * w.size + col : -1;
                dragTo = dragFrom;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragFrom >= 0 && in) {
                    dragTo = r * w.size + col;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragFrom >= 0 && dragTo >= 0) check(line(dragFrom, dragTo));
                dragFrom = dragTo = -1;
                invalidate();
                break;
            default:
        }
        return true;
    }

    private void check(int[] l) {
        if (l == null) return;
        int n = w.size;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l[3]; i++) sb.append(w.grid[(l[0] / n + l[2] * i) * n + l[0] % n + l[1] * i]);
        String s = sb.toString(), rev = sb.reverse().toString();
        for (int i = 0; i < w.words.size(); i++) {
            if (found.contains(i)) continue;
            if (w.words.get(i).equals(s) || w.words.get(i).equals(rev)) {
                found.add(i);
                lines.add(l);
                moved();
                progress();
                return;
            }
        }
    }

    private void progress() {
        int left = w.words.size() - found.size();
        if (left == 0) {
            status(theme + " · all " + w.words.size() + " found");
            won();
        } else {
            status(theme + " · " + left + " to find");
        }
    }
}
