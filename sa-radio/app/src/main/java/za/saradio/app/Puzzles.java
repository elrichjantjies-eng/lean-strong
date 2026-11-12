package za.saradio.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Puzzle generators, all plain logic so they can be tested without Android. Every puzzle is made
 * from a seed, so "today's puzzle" is the same on every phone and a puzzle can be rebuilt from its
 * seed when the app is reopened.
 */
final class Puzzles {
    private Puzzles() {}

    /** A seed for today's puzzle of a given game: days since 1970, mixed with the game's number. */
    static long dailySeed(int game, long now) {
        long day = now / 86_400_000L;
        return day * 7919L + game * 104_729L;
    }

    // ---- Sudoku -----------------------------------------------------------------------------

    /** A 9×9 sudoku: {@code solution} is complete, {@code puzzle} has 0 for the blanks. */
    static final class Sudoku {
        final int[] solution = new int[81];
        final int[] puzzle = new int[81];
        int givens;
    }

    /** Difficulty 0 easy (about 40 givens), 1 medium (32), 2 hard (26). Every puzzle has one solution. */
    static Sudoku sudoku(long seed, int difficulty) {
        Random r = new Random(seed);
        Sudoku s = new Sudoku();
        fill(s.solution, 0, r);
        System.arraycopy(s.solution, 0, s.puzzle, 0, 81);
        int target = difficulty <= 0 ? 40 : difficulty == 1 ? 32 : 26;
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < 81; i++) cells.add(i);
        Collections.shuffle(cells, r);
        int left = 81;
        for (int c : cells) {
            if (left <= target) break;
            int keep = s.puzzle[c];
            s.puzzle[c] = 0;
            if (countSolutions(s.puzzle.clone(), 2) != 1) s.puzzle[c] = keep;
            else left--;
        }
        s.givens = left;
        return s;
    }

    private static boolean fill(int[] g, int pos, Random r) {
        if (pos == 81) return true;
        if (g[pos] != 0) return fill(g, pos + 1, r);
        List<Integer> nums = new ArrayList<>();
        for (int n = 1; n <= 9; n++) nums.add(n);
        Collections.shuffle(nums, r);
        for (int n : nums) {
            if (fits(g, pos, n)) {
                g[pos] = n;
                if (fill(g, pos + 1, r)) return true;
                g[pos] = 0;
            }
        }
        return false;
    }

    static boolean fits(int[] g, int pos, int n) {
        int row = pos / 9, col = pos % 9;
        for (int i = 0; i < 9; i++) {
            if (g[row * 9 + i] == n || g[i * 9 + col] == n) return false;
        }
        int br = row / 3 * 3, bc = col / 3 * 3;
        for (int y = br; y < br + 3; y++) for (int x = bc; x < bc + 3; x++) if (g[y * 9 + x] == n) return false;
        return true;
    }

    /** Counts solutions, stopping at {@code limit}. */
    static int countSolutions(int[] g, int limit) {
        int pos = -1;
        for (int i = 0; i < 81; i++) {
            if (g[i] == 0) {
                pos = i;
                break;
            }
        }
        if (pos < 0) return 1;
        int count = 0;
        for (int n = 1; n <= 9 && count < limit; n++) {
            if (fits(g, pos, n)) {
                g[pos] = n;
                count += countSolutions(g, limit - count);
                g[pos] = 0;
            }
        }
        return count;
    }

    /** True when the grid is full and every row, column and box holds 1–9. */
    static boolean sudokuSolved(int[] g) {
        for (int i = 0; i < 81; i++) {
            if (g[i] < 1 || g[i] > 9) return false;
            int v = g[i];
            g[i] = 0;
            boolean ok = fits(g, i, v);
            g[i] = v;
            if (!ok) return false;
        }
        return true;
    }

    // ---- Word search ------------------------------------------------------------------------

    static final class WordSearch {
        int size;
        char[] grid;
        final List<String> words = new ArrayList<>();
        /** For each word: {startIndex, dx, dy}. */
        final List<int[]> placed = new ArrayList<>();
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {-1, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}};

    /** Places up to {@code count} words from {@code pool} in a {@code size}×{@code size} grid. */
    static WordSearch wordSearch(long seed, int size, List<String> pool, int count) {
        Random r = new Random(seed);
        WordSearch w = new WordSearch();
        w.size = size;
        w.grid = new char[size * size];
        List<String> cands = new ArrayList<>();
        for (String s : pool) if (s.length() >= 4 && s.length() <= size) cands.add(s.toUpperCase());
        Collections.shuffle(cands, r);
        // Longer words first: they're the hard ones to fit.
        Collections.sort(cands, (a, b) -> b.length() - a.length());
        for (String word : cands) {
            if (w.words.size() >= count) break;
            boolean prefix = false;
            for (String p : w.words) if (p.contains(word) || word.contains(p)) prefix = true;
            if (prefix) continue;
            int[] at = place(w, word, r);
            if (at != null) {
                w.words.add(word);
                w.placed.add(at);
            }
        }
        String letters = "EEEEAAAARRRIIIOOONNNSSSTTTLLCUDPMHGBFYWKVXZJQ";
        for (int i = 0; i < w.grid.length; i++) if (w.grid[i] == 0) w.grid[i] = letters.charAt(r.nextInt(letters.length()));
        Collections.sort(w.words);
        return w;
    }

    private static int[] place(WordSearch w, String word, Random r) {
        int n = w.size;
        for (int attempt = 0; attempt < 80; attempt++) {
            int[] d = DIRS[r.nextInt(DIRS.length)];
            int x = r.nextInt(n), y = r.nextInt(n);
            int ex = x + d[0] * (word.length() - 1), ey = y + d[1] * (word.length() - 1);
            if (ex < 0 || ey < 0 || ex >= n || ey >= n) continue;
            boolean ok = true;
            for (int i = 0; i < word.length() && ok; i++) {
                char c = w.grid[(y + d[1] * i) * n + x + d[0] * i];
                if (c != 0 && c != word.charAt(i)) ok = false;
            }
            if (!ok) continue;
            for (int i = 0; i < word.length(); i++) w.grid[(y + d[1] * i) * n + x + d[0] * i] = word.charAt(i);
            return new int[] {y * n + x, d[0], d[1]};
        }
        return null;
    }

    // ---- Word guess -------------------------------------------------------------------------

    static final int MISS = 0;
    static final int CLOSE = 1;
    static final int HIT = 2;

    /** Marks each letter of a guess: HIT in place, CLOSE elsewhere in the word, else MISS. */
    static int[] score(String guess, String answer) {
        int n = answer.length();
        int[] out = new int[n];
        int[] left = new int[26];
        for (int i = 0; i < n; i++) {
            if (guess.charAt(i) == answer.charAt(i)) out[i] = HIT;
            else left[answer.charAt(i) - 'A']++;
        }
        for (int i = 0; i < n; i++) {
            if (out[i] == HIT) continue;
            int k = guess.charAt(i) - 'A';
            if (k >= 0 && k < 26 && left[k] > 0) {
                out[i] = CLOSE;
                left[k]--;
            }
        }
        return out;
    }

    // ---- Picture logic (nonogram) -----------------------------------------------------------

    static final class Picture {
        int size;
        boolean[] cells;
        int[][] rowClues;
        int[][] colClues;
    }

    private static final String[] PICTURES = {
        // 10×10 pictures, row by row: '#' filled.
        "..#....#..|..#....#..|.########.|##.####.##|##########|##.####.##|.########.|..##..##..|..#....#..|.##....##.", // a bakkie? no: a face
        "....##....|...####...|..######..|.########.|####..####|###....###|###....###|###....###|.##....##.|..#....#..", // house
        "..##..##..|.####.####|##########|##########|.########.|..######..|...####...|....##....|..........|..........", // heart
        "....#.....|...###....|..#####...|.#######..|....#.....|....#.....|....#.....|..#####...|.#######..|##########", // tree
        "#........#|##......##|#.#....#.#|#..#..#..#|#...##...#|#...##...#|#..#..#..#|#.#....#.#|##......##|#........#", // cross
        "...####...|..#....#..|.#.####.#.|#.#.##.#.#|#.#.##.#.#|#.#....#.#|#..####..#|.#......#.|..#....#..|...####...", // wheel
        "##########|#........#|#.######.#|#.#....#.#|#.#.##.#.#|#.#.##.#.#|#.#....#.#|#.######.#|#........#|##########", // frames
        "....##....|...#..#...|..#....#..|.#......#.|##########|.#......#.|.#......#.|.#......#.|.#......#.|.########.", // bell
    };

    static Picture picture(long seed) {
        Random r = new Random(seed);
        String[] rows = PICTURES[r.nextInt(PICTURES.length)].split("\\|");
        Picture p = new Picture();
        p.size = rows.length;
        p.cells = new boolean[p.size * p.size];
        for (int y = 0; y < p.size; y++) for (int x = 0; x < p.size; x++) p.cells[y * p.size + x] = rows[y].charAt(x) == '#';
        // A random flip or mirror so the same picture isn't always the same puzzle.
        if (r.nextBoolean()) mirror(p);
        p.rowClues = new int[p.size][];
        p.colClues = new int[p.size][];
        for (int i = 0; i < p.size; i++) {
            boolean[] row = new boolean[p.size], col = new boolean[p.size];
            for (int j = 0; j < p.size; j++) {
                row[j] = p.cells[i * p.size + j];
                col[j] = p.cells[j * p.size + i];
            }
            p.rowClues[i] = clues(row);
            p.colClues[i] = clues(col);
        }
        return p;
    }

    private static void mirror(Picture p) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size / 2; x++) {
                boolean t = p.cells[y * p.size + x];
                p.cells[y * p.size + x] = p.cells[y * p.size + p.size - 1 - x];
                p.cells[y * p.size + p.size - 1 - x] = t;
            }
        }
    }

    static int[] clues(boolean[] line) {
        List<Integer> out = new ArrayList<>();
        int run = 0;
        for (boolean b : line) {
            if (b) run++;
            else if (run > 0) {
                out.add(run);
                run = 0;
            }
        }
        if (run > 0) out.add(run);
        if (out.isEmpty()) out.add(0);
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** True when the marked cells make the same clues as the picture (the picture itself, or another that fits). */
    static boolean pictureSolved(Picture p, boolean[] marked) {
        for (int i = 0; i < p.size; i++) {
            boolean[] row = new boolean[p.size], col = new boolean[p.size];
            for (int j = 0; j < p.size; j++) {
                row[j] = marked[i * p.size + j];
                col[j] = marked[j * p.size + i];
            }
            if (!Arrays.equals(clues(row), p.rowClues[i]) || !Arrays.equals(clues(col), p.colClues[i])) return false;
        }
        return true;
    }

    // ---- Number slide (15-puzzle) -----------------------------------------------------------

    /** A solvable shuffle of 1..n²-1 and a blank (0), made by sliding from the solved state. */
    static int[] slide(long seed, int n, int moves) {
        Random r = new Random(seed);
        int[] g = new int[n * n];
        for (int i = 0; i < g.length - 1; i++) g[i] = i + 1;
        int blank = g.length - 1, last = -1;
        for (int m = 0; m < moves; m++) {
            List<Integer> opts = new ArrayList<>();
            int bx = blank % n, by = blank / n;
            if (bx > 0) opts.add(blank - 1);
            if (bx < n - 1) opts.add(blank + 1);
            if (by > 0) opts.add(blank - n);
            if (by < n - 1) opts.add(blank + n);
            opts.remove(Integer.valueOf(last));
            int pick = opts.get(r.nextInt(opts.size()));
            g[blank] = g[pick];
            g[pick] = 0;
            last = blank;
            blank = pick;
        }
        return g;
    }

    static boolean slideSolved(int[] g) {
        for (int i = 0; i < g.length - 1; i++) if (g[i] != i + 1) return false;
        return g[g.length - 1] == 0;
    }

    // ---- Memory pairs -----------------------------------------------------------------------

    /** Pairs 0..pairs-1, each twice, shuffled. */
    static int[] memory(long seed, int pairs) {
        Random r = new Random(seed);
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            deck.add(i);
            deck.add(i);
        }
        Collections.shuffle(deck, r);
        int[] out = new int[deck.size()];
        for (int i = 0; i < out.length; i++) out[i] = deck.get(i);
        return out;
    }

    // ---- Timing -----------------------------------------------------------------------------

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
