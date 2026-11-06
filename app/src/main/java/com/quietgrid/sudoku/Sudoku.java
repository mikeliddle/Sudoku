package com.quietgrid.sudoku;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Pure Java rules, uniqueness search and human-technique difficulty certification. */
public final class Sudoku {
    public static final int ALL = 511;
    public static final int[][] HOUSES = new int[27][9];
    public static final boolean[][] PEERS = new boolean[81][81];

    static {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                HOUSES[r][c] = r * 9 + c;
                HOUSES[9 + c][r] = r * 9 + c;
                HOUSES[18 + r / 3 * 3 + c / 3][r % 3 * 3 + c % 3] = r * 9 + c;
            }
        }
        for (int[] house : HOUSES) {
            for (int a : house) for (int b : house) if (a != b) PEERS[a][b] = true;
        }
    }

    private Sudoku() {}

    public static int bit(int digit) { return 1 << (digit - 1); }

    public static int candidates(int[] values, int cell) {
        int mask = ALL;
        for (int i = 0; i < 81; i++) {
            if (PEERS[cell][i] && values[i] != 0) mask &= ~bit(values[i]);
        }
        return mask;
    }

    public static boolean valid(int[] values) {
        if (values.length != 81) return false;
        for (int[] house : HOUSES) {
            int seen = 0;
            for (int cell : house) {
                int d = values[cell];
                if (d < 0 || d > 9 || (d != 0 && (seen & bit(d)) != 0)) return false;
                if (d != 0) seen |= bit(d);
            }
        }
        return true;
    }

    public static int solutions(int[] values, int limit) {
        if (!valid(values)) return 0;
        return search(values.clone(), limit, null);
    }

    private static int search(int[] v, int limit, Random random) {
        return search(v, limit, random, null);
    }

    private static int search(int[] v, int limit, Random random, int[] budget) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (budget != null && --budget[0] < 0) {
            throw new IllegalArgumentException("Puzzle validation exceeded the search limit.");
        }
        int best = -1, mask = 0, size = 10;
        for (int i = 0; i < 81; i++) {
            if (v[i] != 0) continue;
            int m = candidates(v, i), n = Integer.bitCount(m);
            if (n == 0) return 0;
            if (n < size) {
                best = i; mask = m; size = n;
                if (n == 1) break;
            }
        }
        if (best == -1) return 1;
        int found = 0;
        List<Integer> digits = new ArrayList<>();
        for (int d = 1; d <= 9; d++) if ((mask & bit(d)) != 0) digits.add(d);
        if (random != null) Collections.shuffle(digits, random);
        for (int d : digits) {
            v[best] = d;
            found += search(v, limit - found, random, budget);
            if (found >= limit) {
                if (random == null) v[best] = 0;
                return found;
            }
        }
        v[best] = 0;
        return found;
    }

    public enum Difficulty {
        FOCUSED("Easy"),
        MEDIUM("Medium"),
        HARD("Hard");

        public final String label;
        Difficulty(String label) {
            this.label = label;
        }
    }

    public static int[] uniqueSolution(int[] clues) {
        if (!valid(clues) || Arrays.stream(clues).noneMatch(value -> value == 0)) {
            throw new IllegalArgumentException("A puzzle must have valid givens and at least one empty cell.");
        }
        if (search(clues.clone(), 2, null, new int[]{200000}) != 1) {
            throw new IllegalArgumentException("Each puzzle must have exactly one solution.");
        }
        int[] solution = clues.clone();
        search(solution, 1, new Random(0), new int[]{200000});
        return solution;
    }

    public static final class Rating {
        public final boolean solved;
        public final int singles, hiddenSingles, deductions, eliminations;
        Rating(boolean solved, int singles, int hidden, int deductions, int eliminations) {
            this.solved = solved; this.singles = singles; this.hiddenSingles = hidden;
            this.deductions = deductions; this.eliminations = eliminations;
        }
    }

    public static final class Puzzle {
        public final int[] clues, solution;
        public final Difficulty difficulty;
        public final Rating rating;
        Puzzle(int[] clues, int[] solution, Difficulty difficulty, Rating rating) {
            this.clues = clues.clone(); this.solution = solution.clone();
            this.difficulty = difficulty; this.rating = rating;
        }
    }

    public static Puzzle generate(Difficulty difficulty, Random random) {
        for (int attempt = 0; attempt < 180; attempt++) {
            int[] solution = new int[81];
            search(solution, 1, random);
            int[] clues = solution.clone();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < 81; i++) order.add(i);
            Collections.shuffle(order, random);
            int remaining = 81;
            int targetClues = difficulty == Difficulty.FOCUSED ? 36 + random.nextInt(5)
                    : difficulty == Difficulty.MEDIUM ? 35 : 34;
            for (int cell : order) {
                int old = clues[cell];
                clues[cell] = 0;
                if (solutions(clues, 2) != 1) { clues[cell] = old; continue; }
                remaining--;
                if (remaining > targetClues) continue;
                Rating singles = rate(clues, false);
                if (difficulty == Difficulty.FOCUSED) {
                    int directStarts = 0;
                    for (int i = 0; i < 81; i++) {
                        if (clues[i] == 0 && Integer.bitCount(candidates(clues, i)) == 1) directStarts++;
                    }
                    if (singles.solved && singles.hiddenSingles <= 2 && directStarts >= 3) {
                        return new Puzzle(clues, solution, difficulty, singles);
                    }
                    if (remaining == 36) break;
                } else if (difficulty == Difficulty.MEDIUM) {
                    if (singles.solved && singles.hiddenSingles >= 3) {
                        return new Puzzle(clues, solution, difficulty, singles);
                    }
                } else if (!singles.solved) {
                    Rating advanced = rate(clues, true);
                    if (advanced.solved && advanced.deductions >= 6 && advanced.eliminations >= 12) {
                        return new Puzzle(clues, solution, difficulty, advanced);
                    }
                }
            }
        }
        throw new IllegalStateException("Could not certify this difficulty. Please try generating again.");
    }

    public static Rating rate(int[] clues, boolean advanced) {
        int[] v = clues.clone(), masks = new int[81];
        if (!valid(v)) return new Rating(false, 0, 0, 0, 0);
        for (int i = 0; i < 81; i++) if (v[i] == 0) masks[i] = candidates(v, i);
        int singles = 0, hidden = 0, deductions = 0, eliminations = 0;
        while (true) {
            int cell = -1, digit = 0, empty = 0;
            for (int i = 0; i < 81; i++) {
                if (v[i] != 0) continue;
                empty++;
                if (masks[i] == 0) return new Rating(false, singles, hidden, deductions, eliminations);
                if (cell == -1 && Integer.bitCount(masks[i]) == 1) {
                    cell = i; digit = Integer.numberOfTrailingZeros(masks[i]) + 1;
                }
            }
            if (empty == 0) return new Rating(valid(v), singles, hidden, deductions, eliminations);
            if (cell == -1) {
                outer: for (int[] house : HOUSES) {
                    for (int d = 1; d <= 9; d++) {
                        int count = 0, last = -1;
                        for (int i : house) if ((masks[i] & bit(d)) != 0) { count++; last = i; }
                        if (count == 1) { cell = last; digit = d; hidden++; break outer; }
                    }
                }
            }
            if (cell != -1) {
                singles++;
                v[cell] = digit; masks[cell] = 0;
                for (int i = 0; i < 81; i++) if (PEERS[cell][i]) masks[i] &= ~bit(digit);
                continue;
            }
            if (!advanced) return new Rating(false, singles, hidden, deductions, eliminations);
            int removed = locked(masks);
            if (removed == 0) removed = subsets(masks);
            if (removed == 0) removed = xWing(masks);
            if (removed == 0) return new Rating(false, singles, hidden, deductions, eliminations);
            deductions++; eliminations += removed;
        }
    }

    private static int remove(int[] masks, int cell, int bits) {
        int removed = Integer.bitCount(masks[cell] & bits);
        masks[cell] &= ~bits;
        return removed;
    }

    private static int locked(int[] masks) {
        for (int a = 0; a < 27; a++) for (int d = 1; d <= 9; d++) {
            int count = 0;
            for (int cell : HOUSES[a]) if ((masks[cell] & bit(d)) != 0) count++;
            if (count < 2) continue;
            for (int b = 0; b < 27; b++) {
                if (a == b) continue;
                boolean allInside = true;
                for (int cell : HOUSES[a]) {
                    if ((masks[cell] & bit(d)) != 0 && !contains(HOUSES[b], cell)) {
                        allInside = false; break;
                    }
                }
                if (!allInside) continue;
                int removed = 0;
                for (int cell : HOUSES[b]) if (!contains(HOUSES[a], cell)) removed += remove(masks, cell, bit(d));
                if (removed > 0) return removed;
            }
        }
        return 0;
    }

    private static boolean contains(int[] array, int value) {
        for (int i : array) if (i == value) return true;
        return false;
    }

    private static int subsets(int[] masks) {
        for (int[] house : HOUSES) {
            for (int a = 0; a < 9; a++) {
                if (Integer.bitCount(masks[house[a]]) < 2 || Integer.bitCount(masks[house[a]]) > 3) continue;
                for (int b = a + 1; b < 9; b++) {
                    if (masks[house[b]] == 0) continue;
                    int union = masks[house[a]] | masks[house[b]];
                    if (Integer.bitCount(union) == 2) {
                        int removed = 0;
                        for (int j = 0; j < 9; j++) if (j != a && j != b) removed += remove(masks, house[j], union);
                        if (removed > 0) return removed;
                    }
                    if (Integer.bitCount(union) > 3) continue;
                    for (int c = b + 1; c < 9; c++) {
                        if (masks[house[c]] == 0) continue;
                        int triple = union | masks[house[c]];
                        if (Integer.bitCount(triple) != 3) continue;
                        int removed = 0;
                        for (int j = 0; j < 9; j++) {
                            if (j != a && j != b && j != c) removed += remove(masks, house[j], triple);
                        }
                        if (removed > 0) return removed;
                    }
                }
            }
            for (int a = 1; a <= 9; a++) for (int b = a + 1; b <= 9; b++) {
                int placesA = 0, placesB = 0;
                for (int j = 0; j < 9; j++) {
                    if ((masks[house[j]] & bit(a)) != 0) placesA |= 1 << j;
                    if ((masks[house[j]] & bit(b)) != 0) placesB |= 1 << j;
                }
                if (placesA != placesB || Integer.bitCount(placesA) != 2) continue;
                int removed = 0;
                for (int j = 0; j < 9; j++) {
                    if ((placesA & 1 << j) != 0) removed += remove(masks, house[j], ALL & ~(bit(a) | bit(b)));
                }
                if (removed > 0) return removed;
            }
        }
        return 0;
    }

    private static int xWing(int[] masks) {
        for (int axis = 0; axis < 2; axis++) for (int d = 1; d <= 9; d++) {
            for (int a = 0; a < 9; a++) {
                int places = positions(masks, axis, a, d);
                if (Integer.bitCount(places) != 2) continue;
                for (int b = a + 1; b < 9; b++) {
                    if (positions(masks, axis, b, d) != places) continue;
                    int removed = 0;
                    for (int row = 0; row < 9; row++) if (row != a && row != b) {
                        for (int col = 0; col < 9; col++) if ((places & 1 << col) != 0) {
                            removed += remove(masks, axis == 0 ? row * 9 + col : col * 9 + row, bit(d));
                        }
                    }
                    if (removed > 0) return removed;
                }
            }
        }
        return 0;
    }

    private static int positions(int[] masks, int axis, int row, int d) {
        int positions = 0;
        for (int c = 0; c < 9; c++) {
            if ((masks[axis == 0 ? row * 9 + c : c * 9 + row] & bit(d)) != 0) positions |= 1 << c;
        }
        return positions;
    }
}
