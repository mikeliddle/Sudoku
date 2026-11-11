package com.quietgrid.sudoku;

import java.util.Arrays;

/** Independent row/column/box implementation: no production candidates, houses or solver. */
public final class DifficultyTests {
    private static int checks;

    private DifficultyTests() {}

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static int allowed(int[] values, int cell) {
        int row = cell / 9, column = cell % 9, mask = 511;
        for (int i = 0; i < 9; i++) {
            int[] peers = {row * 9 + i, i * 9 + column,
                    (row / 3 * 3 + i / 3) * 9 + column / 3 * 3 + i % 3};
            for (int peer : peers) if (values[peer] != 0) mask &= ~(1 << (values[peer] - 1));
        }
        return mask;
    }

    private static int houseCell(int house, int offset) {
        if (house < 9) return house * 9 + offset;
        if (house < 18) return offset * 9 + house - 9;
        int box = house - 18;
        return (box / 3 * 3 + offset / 3) * 9 + box % 3 * 3 + offset % 3;
    }

    private static boolean inHouse(int house, int cell) {
        for (int i = 0; i < 9; i++) if (houseCell(house, i) == cell) return true;
        return false;
    }

    private static int[] reference(int[] clues, boolean naked) {
        int[] values = clues.clone();
        while (true) {
            int[] forced = new int[81];
            // Snapshot waves deliberately differ from the production's immediate-placement passes.
            for (int house = 0; house < 27; house++) for (int digit = 1; digit <= 9; digit++) {
                int count = 0, last = -1;
                for (int i = 0; i < 9; i++) {
                    int cell = houseCell(house, i);
                    if (values[cell] == 0 && (allowed(values, cell) & (1 << (digit - 1))) != 0) {
                        count++; last = cell;
                    }
                }
                if (count == 1) {
                    check(forced[last] == 0 || forced[last] == digit, "Consistent hidden-single wave");
                    forced[last] = digit;
                }
            }
            if (naked) for (int cell = 0; cell < 81; cell++) {
                if (values[cell] != 0) continue;
                int mask = allowed(values, cell);
                if (Integer.bitCount(mask) == 1) {
                    int digit = Integer.numberOfTrailingZeros(mask) + 1;
                    check(forced[cell] == 0 || forced[cell] == digit, "Consistent naked-single wave");
                    forced[cell] = digit;
                }
            }
            boolean changed = false;
            for (int cell = 0; cell < 81; cell++) if (forced[cell] != 0) {
                values[cell] = forced[cell]; changed = true;
            }
            if (!changed) return values;
        }
    }

    private static int independentSolutions(int[] values, int limit) {
        int cell = -1, mask = 0, fewest = 10;
        for (int i = 0; i < 81; i++) if (values[i] == 0) {
            int possible = allowed(values, i), size = Integer.bitCount(possible);
            if (size == 0) return 0;
            if (size < fewest) { cell = i; mask = possible; fewest = size; }
        }
        if (cell < 0) return 1;
        int found = 0;
        for (int d = 1; d <= 9 && found < limit; d++) if ((mask & (1 << (d - 1))) != 0) {
            values[cell] = d;
            found += independentSolutions(values, limit - found);
        }
        values[cell] = 0;
        return found;
    }

    static void verify(int[] clues, Sudoku.Rating rating, boolean naked) {
        int[] values = clues.clone();
        int hiddenCount = 0, nakedCount = 0, lastPass = 0;
        for (Sudoku.Step step : rating.steps) {
            check(step.cell >= 0 && step.cell < 81 && step.digit >= 1 && step.digit <= 9, "Valid trace coordinates");
            check(step.pass >= lastPass && step.pass < rating.passes, "Trace ends before terminal no-change pass");
            lastPass = step.pass;
            check(values[step.cell] == 0, "Trace never overwrites a clue or placement");
            int mask = allowed(values, step.cell);
            check((mask & (1 << (step.digit - 1))) != 0, "Placement legal under independent peer rules");
            if (step.hidden()) {
                check(step.house < 27 && inHouse(step.house, step.cell), "Hidden single belongs to recorded house");
                int positions = 0;
                for (int i = 0; i < 9; i++) {
                    int cell = houseCell(step.house, i);
                    if (values[cell] == 0 && (allowed(values, cell) & (1 << (step.digit - 1))) != 0) positions++;
                }
                check(positions == 1, "Exactly one legal house position at each hidden placement");
                hiddenCount++;
            } else {
                check(naked && Integer.bitCount(mask) == 1, "Naked placement enabled and sole candidate");
                nakedCount++;
            }
            values[step.cell] = step.digit;
        }
        check(hiddenCount == rating.hiddenSingles && nakedCount == rating.nakedSingles, "Technique counts match trace");
        check(Arrays.equals(values, rating.values), "Trace reconstructs exact rated board");
        int[] oracle = reference(clues, naked);
        check(Arrays.equals(oracle, rating.values), "Independent snapshot-wave solver reaches identical fixed point");
        int remaining = (int) Arrays.stream(oracle).filter(value -> value == 0).count();
        check(remaining == rating.remaining && rating.solved == (remaining == 0), "Exact remaining/solved contract");
    }

    static void verifyPuzzle(Sudoku.Puzzle puzzle) {
        check(independentSolutions(puzzle.clues.clone(), 2) == 1, "Independent uniqueness proof");
        Sudoku.Rating hidden = Sudoku.rate(puzzle.clues, false);
        Sudoku.Rating combined = Sudoku.rate(puzzle.clues, true);
        verify(puzzle.clues, hidden, false);
        verify(puzzle.clues, combined, true);
        Sudoku.Difficulty expected = hidden.solved ? Sudoku.Difficulty.FOCUSED
                : combined.solved ? Sudoku.Difficulty.MEDIUM : Sudoku.Difficulty.HARD;
        check(puzzle.difficulty == expected, "Exact fixed-point category");
        for (int cell = 0; cell < 81; cell++) {
            int digit = puzzle.solution[cell];
            check(digit >= 1 && digit <= 9, "Complete solution");
            int[] without = puzzle.solution.clone();
            without[cell] = 0;
            check((allowed(without, cell) & (1 << (digit - 1))) != 0, "Independently valid solution");
            check(puzzle.clues[cell] == 0 || puzzle.clues[cell] == digit, "Solution preserves givens");
        }
    }

    static void run() {
        String[] fixtures = {
                "020000060007100205054600097400780000000506004090000002000005006235000800070000000",
                "006000000180900002002087000030000040090804210400073008020058100000000000040700850",
                "900000040070180000000905800003000600050003010207000080060000300000009500400072000"
        };
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            Sudoku.Puzzle puzzle = Sudoku.classify(parse(fixtures[difficulty.ordinal()]));
            check(puzzle.difficulty == difficulty, "Frozen regression category");
            verifyPuzzle(puzzle);
            if (difficulty != Sudoku.Difficulty.HARD) {
                check(puzzle.rating.steps.stream().anyMatch(step -> step.pass > 1),
                        "Solved regression needs repeated passes, not one scan");
            }
            if (difficulty == Sudoku.Difficulty.FOCUSED) {
                check(puzzle.rating.hiddenSingles > 2 && puzzle.rating.nakedSingles == 0,
                        "Easy no longer limited by old hidden-single threshold");
                for (int kind = 0; kind < 3; kind++) {
                    int houseKind = kind;
                    check(puzzle.rating.steps.stream().anyMatch(step -> step.house / 9 == houseKind),
                            "Hidden-only regression uses rows, columns and boxes");
                }
            } else if (difficulty == Sudoku.Difficulty.MEDIUM) {
                check(!Sudoku.rate(puzzle.clues, false).solved && puzzle.rating.nakedSingles > 0,
                        "Medium needs naked singles after hidden-only stalls");
            } else {
                check(!puzzle.rating.solved && puzzle.rating.remaining > 0,
                        "Unique Hard is not misrepresented as solved by these rules");
            }
        }
        Sudoku.Puzzle curated = new PuzzleLibrary.Entry(fixtures[0], Sudoku.Difficulty.HARD).puzzle();
        check(curated.difficulty == Sudoku.Difficulty.HARD, "Stored curated category is not overwritten by new rating");
        int[] unsolvable = parse("900000020070180000000905800003000600050003010207000080060000300000009500400072000");
        check(Sudoku.valid(unsolvable) && independentSolutions(unsolvable.clone(), 1) == 0,
                "Unsolvable regression has no local duplicate but no solution");
        for (int[] invalid : new int[][]{new int[81], new int[80], conflicting(), unsolvable}) {
            boolean rejected = false;
            try { Sudoku.classify(invalid); } catch (IllegalArgumentException ex) { rejected = true; }
            check(rejected, "Ambiguous or invalid input must never be labeled Hard");
        }
        System.out.println("PASS: " + checks + " fixed-point and independent-trace assertions");
    }

    private static int[] parse(String givens) {
        return givens.chars().map(value -> value - '0').toArray();
    }

    private static int[] conflicting() {
        int[] values = new int[81]; values[0] = 1; values[1] = 1; return values;
    }
}
