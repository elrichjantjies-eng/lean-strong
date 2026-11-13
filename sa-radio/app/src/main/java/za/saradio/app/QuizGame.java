package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/** Daily quiz: ten South African general-knowledge questions, four choices each. */
final class QuizGame extends Game {
    private static final int COUNT = 10;

    static final class Q {
        String text;
        final String[] options = new String[4];
        int answer;
    }

    private static List<Q> bank;

    static synchronized List<Q> bank(Context c) {
        if (bank != null) return bank;
        bank = new ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getAssets().open("quiz.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 6) continue;
                Q q = new Q();
                q.text = parts[0].trim();
                for (int i = 0; i < 4; i++) q.options[i] = parts[i + 1].trim();
                q.answer = parts[5].trim().charAt(0) - 'A';
                if (q.answer < 0 || q.answer > 3) continue;
                bank.add(q);
            }
        } catch (Exception ignored) {
        }
        return bank;
    }

    private final List<Integer> picked = new ArrayList<>();
    private final List<Integer> answered = new ArrayList<>(); // chosen option per question so far
    private int score;
    private int at;

    QuizGame(Context c) {
        super(c);
    }

    @Override
    String help() {
        return "Ten questions about South Africa. Tap an answer; the right one turns green. Then tap NEXT.";
    }

    @Override
    void start(long seed, int level) {
        this.seed = seed;
        List<Q> b = bank(getContext());
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < b.size(); i++) idx.add(i);
        Collections.shuffle(idx, new Random(seed));
        picked.clear();
        picked.addAll(idx.subList(0, Math.min(COUNT, idx.size())));
        answered.clear();
        score = 0;
        at = 0;
        won = false;
        invalidate();
        status("Question 1 of " + picked.size());
    }

    @Override
    String save() {
        try {
            JSONArray a = new JSONArray();
            for (int v : answered) a.put(v);
            return new JSONObject().put("seed", seed).put("answered", a).put("won", won).toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    boolean restore(String state) {
        try {
            JSONObject o = new JSONObject(state);
            start(o.getLong("seed"), 0);
            JSONArray a = o.getJSONArray("answered");
            for (int i = 0; i < a.length(); i++) {
                answered.add(a.getInt(i));
                if (a.getInt(i) == bank(getContext()).get(picked.get(i)).answer) score++;
            }
            at = Math.min(answered.size(), picked.size() - 1);
            won = o.optBoolean("won");
            if (won) status("Scored " + score + " out of " + picked.size());
            else status("Question " + (at + 1) + " of " + picked.size() + (score > 0 ? " · " + score + " right" : ""));
            invalidate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected float aspect() {
        return 1.3f;
    }

    private Q q() {
        return bank(getContext()).get(picked.get(at));
    }

    private boolean answeredThis() {
        return answered.size() > at;
    }

    private float optionTop() {
        return getHeight() * 0.36f;
    }

    private float optionH() {
        return (getHeight() - optionTop() - 46 * d - 3 * 8 * d) / 4f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (picked.isEmpty()) return;
        if (won) {
            plate(c, 0, 0, getWidth(), getHeight(), Ui.INK, 10 * d, 2 * d);
            centred(c, score + " / " + picked.size(), getWidth() / 2f, getHeight() * 0.38f, 64 * d, Ui.YELLOW, Ui.display);
            String verdict = score == picked.size() ? "Full marks. Sharp sharp!" : score >= 8 ? "Lekker. You know your Mzansi."
                    : score >= 5 ? "Not bad. A few to brush up on." : "Eish. Try tomorrow's.";
            centred(c, verdict, getWidth() / 2f, getHeight() * 0.62f, 15 * d, Ui.PAPER, Ui.bold);
            return;
        }
        Q q = q();
        // Question card
        float qt = 0, qh = optionTop() - 12 * d;
        plate(c, 0, qt, getWidth(), qh, Ui.INK, 8 * d, 2 * d);
        text.setTextAlign(android.graphics.Paint.Align.LEFT);
        centred(c, "QUESTION " + (at + 1) + " OF " + picked.size(), 14 * d, 16 * d, 10 * d, Ui.YELLOW, Ui.bold);
        text.setTextAlign(android.graphics.Paint.Align.CENTER);
        TextPaint tp = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Ui.bold);
        tp.setTextSize(16 * d);
        tp.setColor(Ui.PAPER);
        StaticLayout lay = StaticLayout.Builder.obtain(q.text, 0, q.text.length(), tp, (int) (getWidth() - 28 * d))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.15f).setMaxLines(5)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        c.save();
        c.translate(14 * d, 28 * d);
        lay.draw(c);
        c.restore();
        // Options
        float oh = optionH();
        for (int i = 0; i < 4; i++) {
            float t = optionTop() + i * (oh + 8 * d);
            int color = Ui.PAPER;
            if (answeredThis()) {
                if (i == q.answer) color = Ui.LIVERY_GREEN;
                else if (i == answered.get(at)) color = Ui.SIGN_RED;
            }
            plate(c, 0, t, getWidth(), t + oh, color, 8 * d, 2 * d);
            text.setTextAlign(android.graphics.Paint.Align.LEFT);
            centred(c, String.valueOf((char) ('A' + i)), 14 * d, t + oh / 2, oh * 0.42f, color == Ui.SIGN_RED ? Ui.PAPER : Ui.INK, Ui.display);
            TextPaint op = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            op.setTypeface(Ui.bold);
            op.setTextSize(14 * d);
            op.setColor(color == Ui.SIGN_RED ? Ui.PAPER : Ui.INK);
            StaticLayout ol = StaticLayout.Builder.obtain(q.options[i], 0, q.options[i].length(), op, (int) (getWidth() - 50 * d))
                    .setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            c.save();
            c.translate(38 * d, t + (oh - ol.getHeight()) / 2);
            ol.draw(c);
            c.restore();
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        // Next
        float nt = getHeight() - 42 * d;
        boolean last = at == picked.size() - 1;
        plate(c, 0, nt, getWidth(), getHeight(), answeredThis() ? Ui.YELLOW : Ui.SURFACE3, 8 * d, 2 * d);
        centred(c, answeredThis() ? (last ? "SEE MY SCORE" : "NEXT") : "PICK AN ANSWER", getWidth() / 2f, nt + 21 * d, 13 * d,
                answeredThis() ? Ui.INK : Ui.MUTED, Ui.bold);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (picked.isEmpty() || won || e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        float y = e.getY();
        if (y >= getHeight() - 42 * d) {
            if (!answeredThis()) return true;
            if (at == picked.size() - 1) {
                status("Scored " + score + " out of " + picked.size());
                moved();
                won();
            } else {
                at++;
                status("Question " + (at + 1) + " of " + picked.size() + (score > 0 ? " · " + score + " right" : ""));
                moved();
            }
            return true;
        }
        if (answeredThis() || y < optionTop()) return true;
        float oh = optionH();
        int i = (int) ((y - optionTop()) / (oh + 8 * d));
        if (i < 0 || i > 3) return true;
        answered.add(i);
        if (i == q().answer) {
            score++;
            status("Right · " + score + " so far");
        } else {
            status("Not that one · " + score + " so far");
        }
        moved();
        return true;
    }
}
