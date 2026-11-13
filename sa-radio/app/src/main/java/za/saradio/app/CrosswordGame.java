package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Quick crossword: a 5×5 lattice of six words, like the small one in a daily paper. Tap a square
 * to pick a word (tap again to switch between across and down), then type.
 */
final class CrosswordGame extends Game {
    private static final String[] KEYS = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM⌫"};
    /** Slots in order: 1A, 4A, 5A, 1D, 2D, 3D. {startCell, step}. */
    private static final int[][] SLOTS = {{0, 1}, {10, 1}, {20, 1}, {0, 5}, {2, 5}, {4, 5}};
    private static final String[] NAMES = {"1 across", "4 across", "5 across", "1 down", "2 down", "3 down"};

    static final class Puzzle {
        final String[] answers = new String[6];
        final String[] clues = new String[6];
    }

    private static List<Puzzle> all;

    static synchronized List<Puzzle> puzzles(Context c) {
        if (all != null) return all;
        all = new ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getAssets().open("crosswords.txt"), "UTF-8"))) {
            Puzzle cur = null;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equals("@")) {
                    cur = new Puzzle();
                    all.add(cur);
                    continue;
                }
                if (cur == null) continue;
                int sp = line.indexOf(' '), bar = line.indexOf('|');
                String key = line.substring(0, sp);
                int slot = key.equals("A1") ? 0 : key.equals("A4") ? 1 : key.equals("A5") ? 2 : key.equals("D1") ? 3 : key.equals("D2") ? 4 : 5;
                cur.answers[slot] = line.substring(sp + 1, bar).trim().toUpperCase(java.util.Locale.ROOT);
                cur.clues[slot] = line.substring(bar + 1).trim();
            }
        } catch (Exception ignored) {
        }
        return all;
    }

    private Puzzle p;
    private final char[] letters = new char[25];
    private int sel = 0;
    private boolean down;
    private boolean checked;

    CrosswordGame(Context c) {
        super(c);
    }

    @Override
    String help() {
        return "Fill the grid from the clues. Tap a square for its clue; tap again to switch between across and down. The grid checks itself when it's full.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        List<Puzzle> ps = puzzles(getContext());
        p = ps.get((int) (Math.abs(seed) % ps.size()));
        java.util.Arrays.fill(letters, (char) 0);
        sel = 0;
        down = false;
        checked = false;
        won = false;
        invalidate();
        status(clue());
    }

    @Override
    String save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (char ch : letters) sb.append(ch == 0 ? '.' : ch);
            return new JSONObject().put("seed", seed).put("letters", sb.toString()).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), 0);
            String l = o.getString("letters");
            for (int i = 0; i < 25; i++) letters[i] = l.charAt(i) == '.' ? 0 : l.charAt(i);
            won = o.optBoolean("won");
            progress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean block(int i) {
        return (i / 5) % 2 == 1 && (i % 5) % 2 == 1;
    }

    private int slotOf(int cell, boolean wantDown) {
        for (int s = 0; s < 6; s++) {
            boolean isDown = s >= 3;
            if (isDown != wantDown) continue;
            int[] sl = SLOTS[s];
            for (int k = 0; k < 5; k++) if (sl[0] + sl[1] * k == cell) return s;
        }
        return -1;
    }

    private int curSlot() {
        int s = slotOf(sel, down);
        return s >= 0 ? s : slotOf(sel, !down);
    }

    private String clue() {
        int s = curSlot();
        return s < 0 ? "" : NAMES[s] + ": " + p.clues[s];
    }

    @Override
    protected float aspect() {
        return 1.62f;
    }

    private float cell() {
        return (getWidth() - 4 * d) / 5f;
    }

    private float clueTop() {
        return cell() * 5 + 12 * d;
    }

    private float keysTop() {
        return clueTop() + 58 * d;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (p == null) return;
        float s = cell(), x0 = 2 * d, y0 = 2 * d;
        plate(c, 0, 0, getWidth(), s * 5 + 4 * d, Ui.PAPER, 8 * d, 2 * d);
        int cs = curSlot();
        boolean[] wrong = checked ? wrongCells() : null;
        for (int i = 0; i < 25; i++) {
            float l = x0 + (i % 5) * s, t = y0 + (i / 5) * s;
            if (block(i)) {
                fill.setColor(Ui.INK);
                c.drawRect(l, t, l + s, t + s, fill);
                continue;
            }
            boolean inSlot = cs >= 0 && inSlot(cs, i);
            if (i == sel) {
                fill.setColor(Ui.YELLOW);
                c.drawRect(l, t, l + s, t + s, fill);
            } else if (inSlot) {
                fill.setColor(Ui.alpha(Ui.YELLOW, 0.4f));
                c.drawRect(l, t, l + s, t + s, fill);
            }
            int num = i == 0 ? 1 : i == 2 ? 2 : i == 4 ? 3 : i == 10 ? 4 : i == 20 ? 5 : 0;
            if (num > 0) {
                text.setTextAlign(android.graphics.Paint.Align.LEFT);
                centred(c, String.valueOf(num), l + 4 * d, t + 7 * d, 9 * d, Ui.INK, Ui.bold);
                text.setTextAlign(android.graphics.Paint.Align.CENTER);
            }
            if (letters[i] != 0) {
                int color = won ? Ui.COBALT : wrong != null && wrong[i] ? Ui.SIGN_RED : Ui.INK;
                centred(c, String.valueOf(letters[i]), l + s / 2, t + s / 2 + 2 * d, s * 0.52f, color, Ui.display);
            }
        }
        ink.setStrokeWidth(1.2f * d);
        for (int i = 0; i <= 5; i++) {
            c.drawLine(x0 + i * s, y0, x0 + i * s, y0 + 5 * s, ink);
            c.drawLine(x0, y0 + i * s, x0 + 5 * s, y0 + i * s, ink);
        }
        // The clue
        float ct = clueTop();
        plate(c, 0, ct, getWidth(), ct + 50 * d, Ui.INK, 6 * d, 2 * d);
        TextPaint tp = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Ui.bold);
        tp.setTextSize(13 * d);
        tp.setColor(Ui.PAPER);
        StaticLayout lay = StaticLayout.Builder.obtain(clue(), 0, clue().length(), tp, (int) (getWidth() - 24 * d))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        c.save();
        c.translate(12 * d, ct + (50 * d - lay.getHeight()) / 2);
        lay.draw(c);
        c.restore();
        // Keyboard
        float kt = keysTop(), kh = (getHeight() - kt - 2 * 4 * d) / 3f, kw = (getWidth() - 9 * 3 * d) / 10f;
        for (int r = 0; r < 3; r++) {
            String row = KEYS[r];
            float rowW = row.length() * kw + (row.length() - 1) * 3 * d + (r == 2 ? kw * 0.5f : 0);
            float x = (getWidth() - rowW) / 2;
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                float w = ch == '⌫' ? kw * 1.5f : kw, y = kt + r * (kh + 4 * d);
                plate(c, x, y, x + w, y + kh, Ui.PAPER, 5 * d, 1.5f * d);
                centred(c, String.valueOf(ch), x + w / 2, y + kh / 2, kh * 0.46f, Ui.INK, Ui.bold);
                x += w + 3 * d;
            }
        }
    }

    private boolean inSlot(int slot, int cell) {
        int[] sl = SLOTS[slot];
        for (int k = 0; k < 5; k++) if (sl[0] + sl[1] * k == cell) return true;
        return false;
    }

    private boolean[] wrongCells() {
        boolean[] w = new boolean[25];
        for (int s = 0; s < 6; s++) {
            int[] sl = SLOTS[s];
            for (int k = 0; k < 5; k++) {
                int i = sl[0] + sl[1] * k;
                if (letters[i] != 0 && letters[i] != p.answers[s].charAt(k)) w[i] = true;
            }
        }
        return w;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (p == null || won || e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float s = cell();
        if (e.getY() < s * 5 + 4 * d) {
            int col = (int) ((e.getX() - 2 * d) / s), r = (int) ((e.getY() - 2 * d) / s);
            if (col < 0 || col > 4 || r < 0 || r > 4) return true;
            int i = r * 5 + col;
            if (block(i)) return true;
            if (i == sel) down = !down;
            else sel = i;
            if (slotOf(sel, down) < 0) down = !down;
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            status(clue());
            invalidate();
            return true;
        }
        float kt = keysTop();
        if (e.getY() < kt) return true;
        float kh = (getHeight() - kt - 2 * 4 * d) / 3f, kw = (getWidth() - 9 * 3 * d) / 10f;
        int r = (int) ((e.getY() - kt) / (kh + 4 * d));
        if (r < 0 || r > 2) return true;
        String row = KEYS[r];
        float rowW = row.length() * kw + (row.length() - 1) * 3 * d + (r == 2 ? kw * 0.5f : 0);
        float x = (getWidth() - rowW) / 2;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            float w = ch == '⌫' ? kw * 1.5f : kw;
            if (e.getX() >= x && e.getX() < x + w) {
                key(ch);
                return true;
            }
            x += w + 3 * d;
        }
        return true;
    }

    void key(char ch) {
        if (won) return;
        int step = down ? 5 : 1;
        if (ch == '⌫') {
            if (letters[sel] == 0) {
                int prev = sel - step;
                if (prev >= 0 && !block(prev) && slotOf(prev, down) == slotOf(sel, down)) sel = prev;
            }
            letters[sel] = 0;
        } else {
            letters[sel] = ch;
            int next = sel + step;
            if (next < 25 && !block(next) && slotOf(next, down) == slotOf(sel, down)) sel = next;
        }
        checked = false;
        moved();
        progress();
    }

    private void progress() {
        int empty = 0;
        for (int i = 0; i < 25; i++) if (!block(i) && letters[i] == 0) empty++;
        if (empty > 0) {
            status(clue());
            return;
        }
        checked = true;
        boolean[] w = wrongCells();
        int bad = 0;
        for (boolean b : w) if (b) bad++;
        if (bad == 0) {
            status("All six words right");
            won();
        } else {
            status(bad + (bad == 1 ? " letter" : " letters") + " not right, shown in red");
            invalidate();
        }
    }
}
