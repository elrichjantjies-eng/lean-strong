package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Five letters, six tries. Green is in place, yellow is in the word, grey is out. */
final class WordGuessGame extends Game {
    private static final int ROWS = 6;
    private static final String[] KEYS = {"QWERTYUIOP", "ASDFGHJKL", "⏎ZXCVBNM⌫"};
    private String answer = "";
    private final List<String> guesses = new ArrayList<>();
    private String typing = "";
    private boolean lost;
    private final int[] keyState = new int[26]; // -1 unknown, 0 miss, 1 close, 2 hit

    WordGuessGame(Context c) {
        super(c);
    }

    @Override
    String help() {
        return "Guess the five-letter word in six tries. Green: right place. Yellow: in the word. Grey: not in it.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        List<String> five = Words.five(getContext());
        answer = five.isEmpty() ? "RADIO" : five.get((int) (Math.abs(seed) % five.size()));
        guesses.clear();
        typing = "";
        won = false;
        lost = false;
        java.util.Arrays.fill(keyState, -1);
        invalidate();
        status("6 tries");
    }

    @Override
    String save() {
        try {
            JSONArray g = new JSONArray();
            for (String s : guesses) g.put(s);
            return new JSONObject().put("seed", seed).put("guesses", g).put("typing", typing).put("won", won).put("lost", lost).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), 0);
            JSONArray g = o.getJSONArray("guesses");
            for (int i = 0; i < g.length(); i++) {
                guesses.add(g.getString(i));
                mark(g.getString(i));
            }
            typing = o.optString("typing");
            won = o.optBoolean("won");
            lost = o.optBoolean("lost");
            progress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String answer() {
        return answer;
    }

    @Override
    protected float aspect() {
        return 1.42f;
    }

    private float tile() {
        return Math.min((getWidth() - 4 * 6 * d) / 5f, getHeight() * 0.62f / ROWS - 6 * d);
    }

    private float keysTop() {
        return tile() * ROWS + 6 * d * ROWS + 16 * d;
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = tile(), gap = 6 * d, left = (getWidth() - 5 * t - 4 * gap) / 2;
        for (int r = 0; r < ROWS; r++) {
            String word = r < guesses.size() ? guesses.get(r) : r == guesses.size() ? typing : "";
            int[] sc = r < guesses.size() ? Puzzles.score(word, answer) : null;
            for (int i = 0; i < 5; i++) {
                float l = left + i * (t + gap), top = r * (t + gap);
                int color = sc == null ? Ui.PAPER : sc[i] == Puzzles.HIT ? Ui.LIVERY_GREEN : sc[i] == Puzzles.CLOSE ? Ui.YELLOW : 0xFFBDBDBD;
                plate(c, l, top, l + t, top + t, color, 6 * d, 2 * d);
                if (i < word.length()) centred(c, String.valueOf(word.charAt(i)), l + t / 2, top + t / 2, t * 0.56f, Ui.INK, Ui.display);
            }
        }
        // Keyboard
        float kt = keysTop(), kh = (getHeight() - kt - 2 * 4 * d) / 3f, kw = (getWidth() - 9 * 3 * d) / 10f;
        for (int r = 0; r < 3; r++) {
            String row = KEYS[r];
            float rowW = row.length() * kw + (row.length() - 1) * 3 * d + (r == 2 ? kw : 0);
            float x = (getWidth() - rowW) / 2;
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                boolean wide = ch == '⏎' || ch == '⌫';
                float w = wide ? kw * 1.5f : kw, y = kt + r * (kh + 4 * d);
                int st = ch >= 'A' && ch <= 'Z' ? keyState[ch - 'A'] : -1;
                int color = st == 2 ? Ui.LIVERY_GREEN : st == 1 ? Ui.YELLOW : st == 0 ? 0xFFBDBDBD : Ui.PAPER;
                plate(c, x, y, x + w, y + kh, color, 5 * d, 1.5f * d);
                centred(c, String.valueOf(ch), x + w / 2, y + kh / 2, kh * (wide ? 0.42f : 0.46f), Ui.INK, Ui.bold);
                x += w + 3 * d;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN || won || lost) return true;
        float kt = keysTop();
        if (e.getY() < kt) return true;
        float kh = (getHeight() - kt - 2 * 4 * d) / 3f, kw = (getWidth() - 9 * 3 * d) / 10f;
        int r = (int) ((e.getY() - kt) / (kh + 4 * d));
        if (r < 0 || r > 2) return true;
        String row = KEYS[r];
        float rowW = row.length() * kw + (row.length() - 1) * 3 * d + (r == 2 ? kw : 0);
        float x = (getWidth() - rowW) / 2;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            float w = ch == '⏎' || ch == '⌫' ? kw * 1.5f : kw;
            if (e.getX() >= x && e.getX() < x + w) {
                key(ch);
                return true;
            }
            x += w + 3 * d;
        }
        return true;
    }

    void key(char ch) {
        if (won || lost) return;
        if (ch == '⌫') {
            if (!typing.isEmpty()) typing = typing.substring(0, typing.length() - 1);
        } else if (ch == '⏎') {
            if (typing.length() < 5) {
                status("Five letters, then ⏎");
                return;
            }
            if (!Words.isWord(getContext(), typing)) {
                status(typing + " isn't in the word list");
                performHapticFeedback(android.view.HapticFeedbackConstants.REJECT);
                return;
            }
            guesses.add(typing);
            mark(typing);
            typing = "";
            moved();
            progress();
            return;
        } else if (typing.length() < 5) {
            typing += ch;
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    private void mark(String g) {
        int[] sc = Puzzles.score(g, answer);
        for (int i = 0; i < 5; i++) {
            int k = g.charAt(i) - 'A';
            if (k >= 0 && k < 26 && sc[i] > keyState[k]) keyState[k] = sc[i];
        }
    }

    private void progress() {
        if (!guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(answer)) {
            status("Got it in " + guesses.size() + (guesses.size() == 1 ? " try" : " tries"));
            won();
        } else if (guesses.size() >= ROWS) {
            lost = true;
            status("Out of tries. It was " + answer + ".");
            invalidate();
        } else {
            status((ROWS - guesses.size()) + (ROWS - guesses.size() == 1 ? " try left" : " tries left"));
        }
    }
}
