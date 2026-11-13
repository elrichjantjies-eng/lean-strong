package za.saradio.app;

import android.content.Context;

/** The puzzles on the Games tab. */
final class Games {
    private Games() {}

    static final class Kind {
        final String id;
        final String name;
        final String blurb;
        final int color;

        Kind(String id, String name, String blurb, int color) {
            this.id = id;
            this.name = name;
            this.blurb = blurb;
            this.color = color;
        }

        Game make(Context c) {
            switch (id) {
                case "sudoku": return new SudokuGame(c);
                case "search": return new WordSearchGame(c);
                case "guess": return new WordGuessGame(c);
                case "picture": return new PictureGame(c);
                case "slide": return new SlideGame(c);
                case "crossword": return new CrosswordGame(c);
                case "quiz": return new QuizGame(c);
                default: return new MemoryGame(c);
            }
        }
    }

    static final Kind[] ALL = {
        new Kind("sudoku", "Sudoku", "1 to 9 in every row, column and box", Ui.COBALT),
        new Kind("guess", "Five Letters", "Guess the word in six tries", Ui.LIVERY_GREEN),
        new Kind("search", "Word Search", "Mzansi words hidden in a grid", Ui.PINK),
        new Kind("picture", "Picture Logic", "Fill the cells the numbers describe", 0xFF61107D),
        new Kind("slide", "Number Plates", "Slide the plates back into order", Ui.YELLOW),
        new Kind("pairs", "Pairs", "Find the matching signs", 0xFFF28C0F),
        new Kind("crossword", "Quick Crossword", "Six clues, five by five", Ui.INK),
        new Kind("quiz", "Daily Quiz", "Ten questions on Mzansi", Ui.SIGN_RED),
    };

    static Kind find(String id) {
        for (Kind k : ALL) if (k.id.equals(id)) return k;
        return ALL[0];
    }

    static int index(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return 0;
    }

    static long best(Context c, String id, int level) {
        return Prefs.get(c).getLong("best_" + id + "_" + level, 0);
    }

    static void saveBest(Context c, String id, int level, long ms) {
        long cur = best(c, id, level);
        if (cur == 0 || ms < cur) Prefs.get(c).edit().putLong("best_" + id + "_" + level, ms).apply();
    }

    /** Whether today's puzzle of a game has been finished. */
    static boolean dailyDone(Context c, String id, long now) {
        return Prefs.get(c).getLong("daily_" + id, 0) == now / 86_400_000L;
    }

    static void markDaily(Context c, String id, long now) {
        Prefs.get(c).edit().putLong("daily_" + id, now / 86_400_000L).apply();
        // Streak: consecutive days with at least one daily puzzle finished.
        long day = now / 86_400_000L, last = Prefs.get(c).getLong("streakDay", 0);
        int streak = Prefs.get(c).getInt("streak", 0);
        if (last == day) return;
        streak = last == day - 1 ? streak + 1 : 1;
        Prefs.get(c).edit().putLong("streakDay", day).putInt("streak", streak).apply();
    }

    static int streak(Context c, long now) {
        long day = now / 86_400_000L, last = Prefs.get(c).getLong("streakDay", 0);
        return last == day || last == day - 1 ? Prefs.get(c).getInt("streak", 0) : 0;
    }
}
