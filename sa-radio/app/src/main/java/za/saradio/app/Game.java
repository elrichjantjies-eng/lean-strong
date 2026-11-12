package za.saradio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * A puzzle on screen. Each game draws itself, handles its own taps, can save and restore its
 * state as a string, and tells the host when it's won or has something to say.
 */
abstract class Game extends View {

    interface Host {
        void onWon(Game g);

        /** A line for the status row under the board (progress, a hint, a nudge). */
        void onStatus(Game g, String s);

        void onMoved(Game g);
    }

    protected final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final RectF rect = new RectF();
    protected final float d;
    protected Host host;
    protected boolean won;
    protected long seed;
    protected int level;

    Game(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ink.setColor(Ui.INK);
        text.setColor(Ui.INK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Ui.bold);
        setClickable(true);
        setFocusable(true);
    }

    void setHost(Host h) {
        host = h;
    }

    /** Starts a fresh puzzle from a seed at a level (0 easy, 1 medium, 2 hard; games may ignore it). */
    abstract void start(long seed, int level);

    /** The game's state, to be given back to {@link #restore}. */
    abstract String save();

    /** Rebuilds the game from {@link #save}; false if it can't (then the host starts afresh). */
    abstract boolean restore(String state);

    /** Short help for the game's info line. */
    abstract String help();

    /** How many levels the game has (1 when it has no levels). */
    int levels() {
        return 1;
    }

    static String[] levelNames() {
        return new String[] {"Easy", "Medium", "Hard"};
    }

    boolean isWon() {
        return won;
    }

    protected void won() {
        if (won) return;
        won = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        invalidate();
        if (host != null) host.onWon(this);
    }

    protected void status(String s) {
        if (host != null) host.onStatus(this, s);
    }

    protected void moved() {
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
        if (host != null) host.onMoved(this);
    }

    /** A plate: fill with an ink outline. */
    protected void plate(Canvas c, float l, float t, float r, float b, int color, float radius, float line) {
        rect.set(l, t, r, b);
        fill.setColor(Ui.INK);
        c.drawRoundRect(rect, radius, radius, fill);
        rect.inset(line, line);
        fill.setColor(color);
        c.drawRoundRect(rect, Math.max(0, radius - line), Math.max(0, radius - line), fill);
    }

    /** Draws text centred in a box. */
    protected void centred(Canvas c, String s, float cx, float cy, float size, int color, android.graphics.Typeface face) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTypeface(face);
        c.drawText(s, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }

    /** Square boards: the width decides the height unless the game says otherwise. */
    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int height = (int) (width * aspect());
        if (MeasureSpec.getMode(h) == MeasureSpec.AT_MOST) height = Math.min(height, MeasureSpec.getSize(h));
        setMeasuredDimension(width, height);
    }

    /** Height as a fraction of width. */
    protected float aspect() {
        return 1f;
    }
}
