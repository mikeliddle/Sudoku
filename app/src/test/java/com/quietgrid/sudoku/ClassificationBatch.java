package com.quietgrid.sudoku;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Reproducible, unfiltered sample; never calls tier-oriented generate(). */
public final class ClassificationBatch {
    private static final long FIRST_SEED = 2026111100L;
    private static final int COUNT = 100;

    private ClassificationBatch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an output directory.");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        int[] counts = new int[3], clueMin = {81, 81, 81}, clueMax = new int[3];
        int[] hiddenMin = {81, 81, 81}, hiddenMax = new int[3], nakedMin = {81, 81, 81}, nakedMax = new int[3];
        int[] residualMin = {81, 81, 81}, residualMax = new int[3], passMin = {81, 81, 81}, passMax = new int[3];
        Set<String> seen = new HashSet<>();
        long start = System.nanoTime();
        try (BufferedWriter rows = Files.newBufferedWriter(output.resolve("puzzles.csv"), StandardCharsets.UTF_8);
             BufferedWriter traces = Files.newBufferedWriter(output.resolve("traces.csv"), StandardCharsets.UTF_8)) {
            rows.write("index,seed,id,givens,classification,clues,unique_solutions,hidden_only_placements,hidden_only_passes,hidden_only_remaining,combined_hidden,combined_naked,combined_passes,combined_remaining,generation_ms,verification_ms\n");
            traces.write("index,stage,step,pass,technique,house,cell,digit\n");
            for (int index = 1; index <= COUNT; index++) {
                long seed = FIRST_SEED + index - 1, before = System.nanoTime();
                Sudoku.Puzzle puzzle = Sudoku.generateCandidate(new Random(seed));
                long generated = System.nanoTime();
                String id = PuzzleLibrary.id(puzzle.clues);
                if (!seen.add(id)) throw new AssertionError("Duplicate puzzle at index " + index);
                DifficultyTests.verifyPuzzle(puzzle);
                Sudoku.Puzzle classified = Sudoku.classify(puzzle.clues);
                if (classified.difficulty != puzzle.difficulty) throw new AssertionError("Classification mismatch");
                Sudoku.Rating hidden = Sudoku.rate(puzzle.clues, false), combined = Sudoku.rate(puzzle.clues, true);
                long verified = System.nanoTime();
                int clues = (int) Arrays.stream(puzzle.clues).filter(value -> value != 0).count();
                int tier = puzzle.difficulty.ordinal();
                counts[tier]++;
                clueMin[tier] = Math.min(clueMin[tier], clues); clueMax[tier] = Math.max(clueMax[tier], clues);
                Sudoku.Rating reported = puzzle.rating;
                hiddenMin[tier] = Math.min(hiddenMin[tier], reported.hiddenSingles);
                hiddenMax[tier] = Math.max(hiddenMax[tier], reported.hiddenSingles);
                nakedMin[tier] = Math.min(nakedMin[tier], reported.nakedSingles);
                nakedMax[tier] = Math.max(nakedMax[tier], reported.nakedSingles);
                residualMin[tier] = Math.min(residualMin[tier], reported.remaining);
                residualMax[tier] = Math.max(residualMax[tier], reported.remaining);
                passMin[tier] = Math.min(passMin[tier], reported.passes);
                passMax[tier] = Math.max(passMax[tier], reported.passes);
                rows.write(String.format(Locale.ROOT, "%d,%d,%s,%s,%s,%d,1,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f%n",
                        index, seed, id, PuzzleLibrary.givens(puzzle.clues), puzzle.difficulty.label, clues,
                        hidden.hiddenSingles, hidden.passes, hidden.remaining, combined.hiddenSingles,
                        combined.nakedSingles, combined.passes, combined.remaining,
                        (generated - before) / 1e6, (verified - generated) / 1e6));
                trace(traces, index, "hidden_only", hidden);
                trace(traces, index, "combined", combined);
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        StringBuilder json = new StringBuilder("{\n  \"count\": 100,\n  \"firstSeed\": " + FIRST_SEED
                + ",\n  \"lastSeed\": " + (FIRST_SEED + COUNT - 1)
                + ",\n  \"sampling\": \"One java.util.Random(seed) per randomized complete solution; shuffled greedy uniqueness-preserving removal through all 81 cells. No tier filter, quotas, resampling or post-split tuning.\","
                + "\n  \"verification\": \"Each puzzle has an independent uniqueness proof and both technique traces replayed with independent peer rules; snapshot-wave fixed points equal production immediate-placement fixed points.\","
                + "\n  \"rangeSemantics\": \"Easy uses hidden-only metrics; Medium and Hard use combined metrics. Passes include the terminal no-change pass. Zero-count tier ranges are null.\","
                + "\n  \"elapsedSeconds\": " + String.format(Locale.ROOT, "%.3f", seconds) + ",\n  \"tiers\": [\n");
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            int tier = difficulty.ordinal();
            String range = counts[tier] == 0 ? "null" : "[" + clueMin[tier] + "," + clueMax[tier] + "]";
            json.append("    {\"classification\":\"").append(difficulty.label).append("\",\"count\":").append(counts[tier])
                    .append(",\"percent\":").append(counts[tier]).append(",\"clues\":").append(range)
                    .append(",\"hiddenPlacements\":").append(range(counts[tier], hiddenMin[tier], hiddenMax[tier]))
                    .append(",\"nakedPlacements\":").append(range(counts[tier], nakedMin[tier], nakedMax[tier]))
                    .append(",\"passes\":").append(range(counts[tier], passMin[tier], passMax[tier]))
                    .append(",\"remaining\":").append(range(counts[tier], residualMin[tier], residualMax[tier])).append("}");
            json.append(tier == 2 ? "\n" : ",\n");
            System.out.printf(Locale.ROOT, "%s: %d (%d%%), clues %s, hidden %s, naked %s, remaining %s%n",
                    difficulty.label, counts[tier], counts[tier], range,
                    range(counts[tier], hiddenMin[tier], hiddenMax[tier]),
                    range(counts[tier], nakedMin[tier], nakedMax[tier]),
                    range(counts[tier], residualMin[tier], residualMax[tier]));
        }
        json.append("  ]\n}\n");
        Files.writeString(output.resolve("summary.json"), json, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Verified 100 distinct unique-solution puzzles in %.3f s; %s%n", seconds, output);
    }

    private static String range(int count, int min, int max) {
        return count == 0 ? "null" : "[" + min + "," + max + "]";
    }

    private static void trace(BufferedWriter output, int index, String stage, Sudoku.Rating rating) throws Exception {
        int number = 0;
        for (Sudoku.Step step : rating.steps) {
            output.write(index + "," + stage + "," + (++number) + "," + step.pass + ","
                    + (step.hidden() ? "hidden" : "naked") + "," + step.house + "," + step.cell + "," + step.digit + "\n");
        }
    }
}
