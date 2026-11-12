package za.saradio.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The puzzle generators: valid, solvable, and the same from the same seed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PuzzlesTest {

    @Test
    public void sudokuHasOneSolutionAtEveryLevel() {
        for (int level = 0; level < 3; level++) {
            Puzzles.Sudoku s = Puzzles.sudoku(42 + level, level);
            assertTrue(Puzzles.sudokuSolved(s.solution.clone()));
            assertEquals(1, Puzzles.countSolutions(s.puzzle.clone(), 2));
            for (int i = 0; i < 81; i++) assertTrue(s.puzzle[i] == 0 || s.puzzle[i] == s.solution[i]);
            int target = level == 0 ? 40 : level == 1 ? 32 : 26;
            assertTrue("level " + level + " givens " + s.givens, s.givens >= target && s.givens <= target + 8);
        }
        Puzzles.Sudoku a = Puzzles.sudoku(7, 1), b = Puzzles.sudoku(7, 1);
        assertTrue(Arrays.equals(a.puzzle, b.puzzle));
        assertFalse(Arrays.equals(a.puzzle, Puzzles.sudoku(8, 1).puzzle));
        int[] wrong = a.solution.clone();
        wrong[0] = wrong[1];
        assertFalse(Puzzles.sudokuSolved(wrong));
    }

    @Test
    public void wordSearchPlacesEveryWordItLists() {
        List<String> pool = new ArrayList<>(Arrays.asList("BRAAI", "BILTONG", "BOEREWORS", "KOEKSISTER", "MELKTERT", "BOBOTIE",
                "CHAKALAKA", "VETKOEK", "RUSKS", "SAMOOSA", "POTJIE", "MIELIE"));
        Puzzles.WordSearch w = Puzzles.wordSearch(3, 10, pool, 8);
        assertEquals(8, w.words.size());
        for (int k = 0; k < w.placed.size(); k++) {
            // words are sorted after placing, so check the placement list against the grid directly
            int[] at = w.placed.get(k);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int x = at[0] % w.size + at[1] * i, y = at[0] / w.size + at[2] * i;
                if (x < 0 || y < 0 || x >= w.size || y >= w.size) break;
                sb.append(w.grid[y * w.size + x]);
            }
            boolean found = false;
            for (String word : w.words) if (sb.toString().startsWith(word)) found = true;
            assertTrue(sb.toString(), found);
        }
        for (char c : w.grid) assertTrue(c >= 'A' && c <= 'Z');
    }

    @Test
    public void wordGuessScoring() {
        assertTrue(Arrays.equals(new int[] {2, 2, 2, 2, 2}, Puzzles.score("RADIO", "RADIO")));
        // Double letters: only as many CLOSE marks as the answer has.
        int[] s = Puzzles.score("ALLEY", "LLAMA");
        assertEquals(Puzzles.CLOSE, s[0]); // A is in LLAMA
        assertEquals(Puzzles.HIT, s[1]);   // L in place
        assertEquals(Puzzles.CLOSE, s[2]); // second L elsewhere
        assertEquals(Puzzles.MISS, s[3]);
        assertEquals(Puzzles.MISS, s[4]);
    }

    @Test
    public void pictureCluesMatchThePicture() {
        Puzzles.Picture p = Puzzles.picture(11);
        assertEquals(10, p.size);
        assertTrue(Puzzles.pictureSolved(p, p.cells.clone()));
        boolean[] blank = new boolean[100];
        assertFalse(Puzzles.pictureSolved(p, blank));
        assertTrue(Arrays.equals(new int[] {2, 1}, Puzzles.clues(new boolean[] {true, true, false, true, false})));
        assertTrue(Arrays.equals(new int[] {0}, Puzzles.clues(new boolean[] {false, false})));
    }

    @Test
    public void slideIsSolvableAndShuffled() {
        int[] g = Puzzles.slide(5, 4, 200);
        assertFalse(Puzzles.slideSolved(g));
        int[] sorted = g.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < 16; i++) assertEquals(i, sorted[i]);
        // Parity: a shuffle made by sliding is always solvable.
        int inv = 0;
        for (int i = 0; i < 16; i++) for (int j = i + 1; j < 16; j++) if (g[i] != 0 && g[j] != 0 && g[i] > g[j]) inv++;
        int blankRowFromBottom = 4 - (indexOf(g, 0) / 4);
        assertTrue((inv + blankRowFromBottom) % 2 == 1);
        int[] solved = new int[16];
        for (int i = 0; i < 15; i++) solved[i] = i + 1;
        assertTrue(Puzzles.slideSolved(solved));
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    @Test
    public void memoryDeckHasPairs() {
        int[] deck = Puzzles.memory(9, 8);
        assertEquals(16, deck.length);
        int[] count = new int[8];
        for (int v : deck) count[v]++;
        for (int c : count) assertEquals(2, c);
    }

    @Test
    public void wordListLoads() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        assertTrue(Words.five(c).size() > 400);
        assertTrue(Words.isWord(c, "braai"));
        assertFalse(Words.isWord(c, "zzzzz"));
        assertEquals(10, Words.themes(c).size());
        for (Words.Theme t : Words.themes(c)) assertTrue(t.name, t.words.size() >= 12);
    }

    @Test
    public void dailySeedIsTheSameAllDay() {
        long day = 20_770L * 86_400_000L;
        assertEquals(Puzzles.dailySeed(0, day + 1000), Puzzles.dailySeed(0, day + 80_000_000L));
        assertFalse(Puzzles.dailySeed(0, day) == Puzzles.dailySeed(1, day));
        assertFalse(Puzzles.dailySeed(0, day) == Puzzles.dailySeed(0, day + 86_400_000L));
    }
}
