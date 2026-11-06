package com.quietgrid.sudoku;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local completion history is independent of the shareable curated collection. */
public final class PuzzleLibrary {
    public static final int MAX_ENTRIES = 2000;
    private final Map<String, Entry> curated = new LinkedHashMap<>();
    private final Map<String, Vote> completed = new LinkedHashMap<>();

    public PuzzleLibrary() {}

    public PuzzleLibrary(PuzzleLibrary source) {
        curated.putAll(source.curated);
        completed.putAll(source.completed);
    }

    public static String givens(int[] clues) {
        if (!Sudoku.valid(clues)) throw new IllegalArgumentException("Invalid givens.");
        StringBuilder result = new StringBuilder(81);
        for (int clue : clues) result.append((char) ('0' + clue));
        return result.toString();
    }

    public static String id(int[] clues) { return id(givens(clues)); }

    public static String id(String givens) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(givens.getBytes(StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 is required by Java and Android", ex);
        }
    }

    public static final class Entry {
        public final String id, givens;
        public final Sudoku.Difficulty difficulty;
        private final int[] clues, solution;

        public Entry(String givens, Sudoku.Difficulty difficulty) {
            if (givens == null || !givens.matches("[0-9]{81}") || difficulty == null) {
                throw new IllegalArgumentException("Givens must contain exactly 81 digits, and difficulty is required.");
            }
            this.givens = givens;
            this.difficulty = difficulty;
            this.id = id(givens);
            clues = new int[81];
            for (int i = 0; i < 81; i++) clues[i] = givens.charAt(i) - '0';
            solution = Sudoku.uniqueSolution(clues);
        }

        public Sudoku.Puzzle puzzle() {
            return new Sudoku.Puzzle(clues, solution, difficulty, Sudoku.rate(clues, true));
        }
    }

    public static final class Vote {
        public final String id, givens;
        public final Sudoku.Difficulty offeredDifficulty, classification;

        private Vote(String givens, Sudoku.Difficulty offered, Sudoku.Difficulty classification) {
            this.givens = givens;
            id = id(givens);
            offeredDifficulty = offered;
            this.classification = classification;
        }
    }

    public boolean recordCompletion(Game game) {
        if (!game.won() || !Sudoku.valid(game.values)
                || java.util.Arrays.stream(game.values).anyMatch(value -> value == 0)) {
            throw new IllegalArgumentException("Only a correctly solved puzzle can be recorded.");
        }
        String givens = givens(game.clues), id = id(givens);
        if (completed.containsKey(id)) return false;
        completed.put(id, new Vote(givens, game.difficulty, null));
        return true;
    }

    public void classify(Game game, Sudoku.Difficulty classification) {
        if (classification == null) throw new IllegalArgumentException("Choose a classification.");
        recordCompletion(game);
        String id = id(game.clues);
        Vote previous = completed.get(id);
        completed.put(id, new Vote(previous.givens, previous.offeredDifficulty, classification));
    }

    public Vote vote(Game game) { return completed.get(id(game.clues)); }

    public int curatedCount() { return curated.size(); }
    public int completedCount() { return completed.size(); }

    public Sudoku.Puzzle nextCurated(Sudoku.Difficulty difficulty) {
        for (Entry entry : curated.values()) {
            if (entry.difficulty == difficulty && !completed.containsKey(entry.id)) return entry.puzzle();
        }
        return null;
    }

    public Sudoku.Puzzle nextPuzzle(Sudoku.Difficulty difficulty, java.util.Random random) {
        Sudoku.Puzzle curated = nextCurated(difficulty);
        return curated != null ? curated : Sudoku.generate(difficulty, random);
    }

    public static final class ImportResult {
        public final PuzzleLibrary library;
        public final int added, duplicates, ignoredVotes;
        ImportResult(PuzzleLibrary library, int added, int duplicates, int ignoredVotes) {
            this.library = library; this.added = added; this.duplicates = duplicates; this.ignoredVotes = ignoredVotes;
        }
    }

    public ImportResult importCollection(String json) {
        Map<String, Object> document = PuzzleJson.object(PuzzleJson.parse(json));
        PuzzleJson.keys(document, "format", "version", "puzzles", "votes");
        if (!"quietgrid-sudoku".equals(document.get("format")) || !Long.valueOf(1).equals(document.get("version"))) {
            throw new IllegalArgumentException("Unsupported puzzle file format or version.");
        }
        java.util.List<Object> puzzles = PuzzleJson.array(document.get("puzzles"));
        java.util.List<Object> votes = PuzzleJson.array(document.get("votes"));
        if (puzzles.size() > MAX_ENTRIES || votes.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Import at most " + MAX_ENTRIES + " puzzles or votes per file.");
        }
        PuzzleLibrary merged = new PuzzleLibrary(this);
        int added = 0, duplicates = 0;
        for (Object value : puzzles) {
            Map<String, Object> row = PuzzleJson.object(value);
            PuzzleJson.keys(row, "id", "givens", "difficulty");
            Entry entry = new Entry(PuzzleJson.string(row.get("givens")), difficulty(row.get("difficulty")));
            requireId(row, entry.id);
            Entry existing = merged.curated.get(entry.id);
            if (existing != null) {
                if (existing.difficulty != entry.difficulty) {
                    throw new IllegalArgumentException("Conflicting classification for puzzle " + entry.id + ".");
                }
                duplicates++;
            } else {
                merged.curated.put(entry.id, entry);
                added++;
            }
        }
        if (merged.curated.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("The local collection can contain at most " + MAX_ENTRIES + " puzzles.");
        }
        Map<String, Vote> seenVotes = new LinkedHashMap<>();
        for (Object value : votes) {
            Map<String, Object> row = PuzzleJson.object(value);
            PuzzleJson.keys(row, "id", "givens", "offeredDifficulty", "classification");
            Entry entry = new Entry(PuzzleJson.string(row.get("givens")), difficulty(row.get("offeredDifficulty")));
            requireId(row, entry.id);
            Sudoku.Difficulty classification = row.get("classification") == null ? null : difficulty(row.get("classification"));
            Vote existing = seenVotes.put(entry.id, new Vote(entry.givens, entry.difficulty, classification));
            if (existing != null && (existing.offeredDifficulty != entry.difficulty || existing.classification != classification)) {
                throw new IllegalArgumentException("Conflicting votes for puzzle " + entry.id + ".");
            }
        }
        return new ImportResult(merged, added, duplicates, votes.size());
    }

    private static void requireId(Map<String, Object> row, String id) {
        if (!id.equals(row.get("id"))) throw new IllegalArgumentException("Puzzle ID does not match its exact givens.");
    }

    static Sudoku.Difficulty difficulty(Object value) {
        if ("Easy".equals(value)) return Sudoku.Difficulty.FOCUSED;
        if ("Medium".equals(value)) return Sudoku.Difficulty.MEDIUM;
        if ("Hard".equals(value)) return Sudoku.Difficulty.HARD;
        throw new IllegalArgumentException("Difficulty must be Easy, Medium or Hard.");
    }

    public String exportJson() {
        StringBuilder out = new StringBuilder("{\n  \"format\": \"quietgrid-sudoku\",\n  \"version\": 1,\n  \"puzzles\": [");
        boolean first = true;
        for (Entry entry : curated.values()) {
            if (!first) out.append(',');
            first = false;
            out.append("\n    {\"id\":\"").append(entry.id).append("\",\"givens\":\"").append(entry.givens)
                    .append("\",\"difficulty\":\"").append(entry.difficulty.label).append("\"}");
        }
        out.append("\n  ],\n  \"votes\": [");
        first = true;
        for (Vote vote : completed.values()) {
            if (!first) out.append(',');
            first = false;
            out.append("\n    {\"id\":\"").append(vote.id).append("\",\"givens\":\"").append(vote.givens)
                    .append("\",\"offeredDifficulty\":\"").append(vote.offeredDifficulty.label)
                    .append("\",\"classification\":")
                    .append(vote.classification == null ? "null" : "\"" + vote.classification.label + "\"").append('}');
        }
        return out.append("\n  ]\n}\n").toString();
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(0x51474331);
        out.writeInt(curated.size());
        for (Entry entry : curated.values()) {
            out.writeUTF(entry.givens); out.writeUTF(entry.difficulty.name());
        }
        out.writeInt(completed.size());
        for (Vote vote : completed.values()) {
            out.writeUTF(vote.givens); out.writeUTF(vote.offeredDifficulty.name());
            out.writeUTF(vote.classification == null ? "" : vote.classification.name());
        }
    }

    public static PuzzleLibrary read(DataInputStream in) throws IOException {
        if (in.readInt() != 0x51474331) throw new IOException("Unsupported local puzzle library version.");
        PuzzleLibrary result = new PuzzleLibrary();
        try {
            int count = readCount(in, MAX_ENTRIES);
            for (int i = 0; i < count; i++) {
                Entry entry = new Entry(in.readUTF(), Sudoku.Difficulty.valueOf(in.readUTF()));
                if (result.curated.put(entry.id, entry) != null) throw new IOException("Duplicate local collection entry.");
            }
            count = readCount(in, 1000000);
            for (int i = 0; i < count; i++) {
                String givens = in.readUTF();
                Sudoku.Difficulty offered = Sudoku.Difficulty.valueOf(in.readUTF());
                String classification = in.readUTF();
                if (!givens.matches("[0-9]{81}")) throw new IOException("Invalid local completion givens.");
                int[] clues = new int[81];
                for (int j = 0; j < 81; j++) clues[j] = givens.charAt(j) - '0';
                if (!Sudoku.valid(clues)) throw new IOException("Invalid local completion clues.");
                Vote vote = new Vote(givens, offered, classification.isEmpty() ? null : Sudoku.Difficulty.valueOf(classification));
                if (result.completed.put(vote.id, vote) != null) throw new IOException("Duplicate local completion entry.");
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing local library data.");
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid local puzzle library: " + ex.getMessage(), ex);
        }
        return result;
    }

    private static int readCount(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max) throw new IOException("Invalid local library count.");
        return count;
    }
}
