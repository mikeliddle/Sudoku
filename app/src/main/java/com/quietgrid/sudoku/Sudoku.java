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
        public final int hiddenSingles, nakedSingles, passes, remaining;
        public final List<Step> steps;
        public final int[] values;

        Rating(int[] values, int hidden, int naked, int passes, List<Step> steps) {
            this.values = values.clone();
            this.remaining = (int) Arrays.stream(values).filter(value -> value == 0).count();
            this.solved = remaining == 0;
            this.hiddenSingles = hidden; this.nakedSingles = naked; this.passes = passes;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
    }

    public static final class Step {
        public final int cell, digit, house, pass;
        Step(int cell, int digit, int house, int pass) {
            this.cell = cell; this.digit = digit; this.house = house; this.pass = pass;
        }
        public boolean hidden() { return house >= 0; }
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
            Puzzle candidate = generateCandidate(random);
            if (candidate.difficulty == difficulty) return candidate;
        }
        throw new IllegalStateException("Could not certify this difficulty. Please try generating again.");
    }

    /** A common pool: one shuffled greedy removal pass, retaining uniqueness, with no tier filter. */
    public static Puzzle generateCandidate(Random random) {
        int[] solution = new int[81];
        search(solution, 1, random);
        int[] clues = solution.clone();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 81; i++) order.add(i);
        Collections.shuffle(order, random);
        for (int cell : order) {
            int old = clues[cell];
            clues[cell] = 0;
            if (solutions(clues, 2) != 1) clues[cell] = old;
        }
        return measuredPuzzle(clues, solution);
    }

    /** Search establishes validity/uniqueness only; it never contributes logical placements. */
    public static Puzzle classify(int[] clues) {
        return measuredPuzzle(clues, uniqueSolution(clues));
    }

    private static Puzzle measuredPuzzle(int[] clues, int[] solution) {
        Rating hidden = rate(clues, false);
        if (hidden.solved) return new Puzzle(clues, solution, Difficulty.FOCUSED, hidden);
        Rating combined = rate(clues, true);
        return new Puzzle(clues, solution, combined.solved ? Difficulty.MEDIUM : Difficulty.HARD, combined);
    }

    /** False: hidden singles only. True: hidden plus naked singles, from the original givens. */
    public static Rating rate(int[] clues, boolean includeNakedSingles) {
        if (!valid(clues)) throw new IllegalArgumentException("Invalid givens.");
        int[] v = clues.clone();
        List<Step> steps = new ArrayList<>();
        int hidden = 0, naked = 0, pass = 0;
        boolean changed;
        do {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            changed = false;
            pass++;
            for (int d = 1; d <= 9; d++) for (int h = 0; h < HOUSES.length; h++) {
                int count = 0, last = -1;
                for (int cell : HOUSES[h]) {
                    if (v[cell] == 0 && (candidates(v, cell) & bit(d)) != 0) {
                        count++; last = cell;
                    }
                }
                if (count == 1) {
                    v[last] = d;
                    steps.add(new Step(last, d, h, pass));
                    hidden++; changed = true;
                }
            }
            if (includeNakedSingles) {
                for (int cell = 0; cell < 81; cell++) {
                    if (v[cell] != 0) continue;
                    int mask = candidates(v, cell);
                    if (Integer.bitCount(mask) == 1) {
                        int digit = Integer.numberOfTrailingZeros(mask) + 1;
                        v[cell] = digit;
                        steps.add(new Step(cell, digit, -1, pass));
                        naked++; changed = true;
                    }
                }
            }
            for (int i = 0; i < 81; i++) {
                if (v[i] == 0 && candidates(v, i) == 0) {
                    throw new IllegalArgumentException("Givens lead to a contradiction.");
                }
            }
        } while (changed);
        return new Rating(v, hidden, naked, pass, steps);
    }
}
