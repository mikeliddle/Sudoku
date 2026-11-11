package com.quietgrid.sudoku;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

final class CurationTests {
    private static int checks;

    static void run() throws Exception {
        Sudoku.Puzzle first = Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(52001));
        Sudoku.Puzzle second = Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(52002));
        Sudoku.Puzzle medium = Sudoku.generate(Sudoku.Difficulty.MEDIUM, new Random(52003));
        Sudoku.Puzzle hard = Sudoku.generate(Sudoku.Difficulty.HARD, new Random(52004));
        String a = entry(first, "Easy"), b = entry(second, "Easy");
        String m = entry(medium, "Medium"), h = entry(hard, "Hard");
        String collection = document(a + "," + b + "," + m + "," + h);
        check(PuzzleLibrary.id("0".repeat(81)).equals("2b85e16040791cd86bdbe5c80556a2e13fc02dfbc785aff6cc87bd3d70c7f014"),
                "Stable identity matches independent SHA-256 of exactly 81 ASCII digits");
        int[] changedGivens = first.clues.clone();
        changedGivens[firstEmpty(new Game(first))] = first.solution[firstEmpty(new Game(first))];
        check(!PuzzleLibrary.id(changedGivens).equals(PuzzleLibrary.id(first.clues)), "Different exact givens have different identities");
        String sample = row("530070000600195000098000060800060003400803001700020006060000280000419005000080079", "Easy");
        check(new PuzzleLibrary().importCollection(document(sample)).added == 1, "Documented interoperable example is valid and unique");
        check(PuzzleJson.readDocument(new ByteArrayInputStream(collection.getBytes(StandardCharsets.UTF_8))).equals(collection),
                "UTF-8 document bytes round-trip");
        try {
            PuzzleJson.readDocument(new ByteArrayInputStream(new byte[]{(byte) 0xC3, 0x28}));
            throw new AssertionError("Invalid UTF-8 accepted");
        } catch (IOException ex) { check(ex.getMessage().contains("UTF-8"), "Malformed UTF-8 reports an explicit error"); }
        try {
            PuzzleJson.readDocument(new ByteArrayInputStream(new byte[PuzzleJson.MAX_BYTES + 1]));
            throw new AssertionError("Oversized document accepted");
        } catch (IOException ex) { check(ex.getMessage().contains("2 MiB"), "Document bytes are bounded before parsing"); }
        PuzzleLibrary empty = new PuzzleLibrary();
        PuzzleLibrary.ImportResult imported = empty.importCollection(collection);
        check(empty.curatedCount() == 0 && imported.added == 4, "Merge stages without mutating existing library");
        PuzzleLibrary library = imported.library;
        check(Arrays.equals(library.nextCurated(Sudoku.Difficulty.FOCUSED).clues, first.clues), "Curated import order is preserved");
        check(Arrays.equals(library.nextPuzzle(Sudoku.Difficulty.FOCUSED, new Random(54001)).clues, first.clues),
                "Actual selection policy serves curated puzzles before generation");
        check(Arrays.equals(library.nextCurated(Sudoku.Difficulty.MEDIUM).clues, medium.clues), "Medium uses its own collection");
        check(Arrays.equals(library.nextCurated(Sudoku.Difficulty.HARD).clues, hard.clues), "Hard uses its own collection");
        Game game = new Game(library.nextCurated(Sudoku.Difficulty.FOCUSED));
        byte[] unfinished = gameBytes(game);
        expectFailure(() -> library.classify(game, Sudoku.Difficulty.HARD), "Unfinished classification rejected");
        expectFailure(() -> library.recordCompletion(game), "Abandonment does not consume puzzle");
        check(library.completedCount() == 0, "No consumption before valid completion");
        check(Arrays.equals(library.nextCurated(Sudoku.Difficulty.FOCUSED).clues, first.clues), "Resuming or selecting does not consume");
        game.setAutoNotes(true);
        int target = firstEmpty(game);
        game.selected = target; game.setNoteMode(true); game.enter(game.solution[target]);
        game.setNoteMode(false); game.showMistakes = true; game.elapsedMillis = 123456;
        byte[] beforeImport = gameBytes(game);
        PuzzleLibrary.ImportResult duplicate = library.importCollection(collection);
        check(duplicate.added == 0 && duplicate.duplicates == 4, "Identical re-import is explicit and idempotent");
        check(Arrays.equals(beforeImport, gameBytes(game)), "Import does not change timer, notes, settings, selection or history");
        Game resumed = Game.read(new DataInputStream(new ByteArrayInputStream(beforeImport)));
        check(Arrays.equals(game.notes, resumed.notes) && resumed.elapsedMillis == 123456
                && resumed.autoNotes && resumed.showMistakes && resumed.canUndo(), "Existing gameplay state survives");
        String exactGivens = PuzzleLibrary.givens(game.clues);
        System.arraycopy(game.solution, 0, game.values, 0, 81);
        check(library.recordCompletion(game), "Valid completion recorded");
        check(!library.recordCompletion(game), "Repeat completion is idempotent");
        check(Arrays.equals(library.nextCurated(Sudoku.Difficulty.FOCUSED).clues, second.clues), "Completion alone consumes puzzle");
        long elapsed = game.elapsedMillis;
        library.classify(game, Sudoku.Difficulty.HARD);
        check(library.vote(game).classification == Sudoku.Difficulty.HARD, "Human classification saved");
        check(game.difficulty == Sudoku.Difficulty.FOCUSED && game.elapsedMillis == elapsed, "Offered difficulty and best-time input unchanged");
        check(library.vote(game).givens.equals(exactGivens), "Export uses original givens, never completed board");
        check(library.vote(game).id.equals(PuzzleLibrary.id(game.clues)), "Completion uses exact-givens identity");
        Game samePuzzleOtherTier = new Game(new Sudoku.Puzzle(first.clues, first.solution, Sudoku.Difficulty.MEDIUM, null));
        System.arraycopy(samePuzzleOtherTier.solution, 0, samePuzzleOtherTier.values, 0, 81);
        library.recordCompletion(samePuzzleOtherTier);
        check(library.vote(game).offeredDifficulty == Sudoku.Difficulty.FOCUSED && library.completedCount() == 1,
                "Same puzzle offered in another tier never duplicates history or rewrites first offered difficulty");
        PuzzleLibrary restored = reload(library);
        check(restored.vote(game).classification == Sudoku.Difficulty.HARD, "Post-win classification survives restart");
        check(Arrays.equals(restored.nextCurated(Sudoku.Difficulty.FOCUSED).clues, second.clues), "Consumption survives restart");
        restored = restored.importCollection(collection).library;
        check(restored.completedCount() == 1 && Arrays.equals(restored.nextCurated(Sudoku.Difficulty.FOCUSED).clues, second.clues),
                "Re-import preserves completion history");
        Game next = new Game(restored.nextCurated(Sudoku.Difficulty.FOCUSED));
        System.arraycopy(next.solution, 0, next.values, 0, 81);
        restored.recordCompletion(next);
        check(restored.nextCurated(Sudoku.Difficulty.FOCUSED) == null, "Exhaustion requests generated fallback rather than cycling");
        Sudoku.Puzzle fallback = restored.nextPuzzle(Sudoku.Difficulty.FOCUSED, new Random(54002));
        Sudoku.Rating fallbackRating = Sudoku.rate(fallback.clues, false);
        check(fallback.difficulty == Sudoku.Difficulty.FOCUSED && Sudoku.solutions(fallback.clues, 2) == 1
                && fallbackRating.solved && fallbackRating.nakedSingles == 0, "Actual exhaustion policy generates a certified puzzle");
        check(restored.completedCount() == 2, "Generated selection does not consume before completion");
        check(restored.nextCurated(Sudoku.Difficulty.MEDIUM) != null, "Easy exhaustion leaves Medium eligible");
        Sudoku.Puzzle generated = Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(52005));
        restored = restored.importCollection(document(a + "," + b + "," + entry(generated, "Easy"))).library;
        check(Arrays.equals(restored.nextCurated(Sudoku.Difficulty.FOCUSED).clues, generated.clues), "New unseen entries become eligible after re-import");
        String export = restored.exportJson();
        PuzzleLibrary.ImportResult roundTrip = new PuzzleLibrary().importCollection(export);
        check(roundTrip.added == 5 && roundTrip.ignoredVotes == 2, "Export/import round-trip separates votes from curated entries");
        check(roundTrip.library.completedCount() == 0, "Another player's vote export never consumes local puzzles");
        check(export.equals(reload(restored).exportJson()), "Versioned local persistence retains exact interchange data");
        check(new PuzzleLibrary().importCollection("\uFEFF" + collection).added == 4, "UTF-8 BOM from PC editors accepted");
        PuzzleLibrary.ImportResult repeated = new PuzzleLibrary().importCollection(document(a + "," + a));
        check(repeated.added == 1 && repeated.duplicates == 1, "Duplicate rows inside one file are counted");
        check(new PuzzleLibrary().importCollection(document(entry(first, "Hard"))).added == 1,
                "Human classification may disagree with algorithmic rating");
        String prior = library.exportJson();
        expectFailure(() -> library.importCollection(document(b + "," + entry(first, "Hard"))), "Existing classification conflict rejected atomically");
        check(prior.equals(library.exportJson()), "Failed late merge preserves all records");
        expectFailure(() -> new PuzzleLibrary().importCollection(document(a + "," + entry(first, "Medium"))), "Conflicting duplicate in a file rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"version\":1", "\"version\":2")), "Unsupported version rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"Easy\"", "\"Expert\"")), "Unknown difficulty rejected");
        expectFailure(() -> library.importCollection(collection.replace(PuzzleLibrary.id(first.clues), "0".repeat(64))), "Mismatched identity rejected");
        expectFailure(() -> library.importCollection(collection + "garbage"), "Trailing malformed data rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"version\":1", "\"version\":1,\"version\":1")), "Duplicate JSON keys rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"version\":1", "\"version\":01")), "Invalid JSON number rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"version\":1", "\"version\":1.0")), "Version requires an integer");
        expectFailure(() -> library.importCollection("{\"format\":\"quietgrid-sudoku\",\"version\":1,\"puzzles\":[]}"), "Missing votes field rejected");
        expectFailure(() -> library.importCollection(collection.replace("\"version\":1", "\"version\":1,\"unexpected\":null")), "Unknown fields rejected");
        expectFailure(() -> library.importCollection(document(row("0".repeat(81), "Easy"))), "Nonunique grid rejected");
        expectFailure(() -> library.importCollection(document(row("11" + "0".repeat(79), "Easy"))), "Conflicting givens rejected");
        int[] unsolvable = first.clues.clone();
        boolean foundUnsolvable = false;
        outer: for (int cell = 0; cell < 81; cell++) {
            if (unsolvable[cell] != 0) continue;
            for (int digit = 1; digit <= 9; digit++) {
                if (digit == first.solution[cell] || (Sudoku.candidates(unsolvable, cell) & Sudoku.bit(digit)) == 0) continue;
                unsolvable[cell] = digit;
                if (Sudoku.solutions(unsolvable, 2) == 0) { foundUnsolvable = true; break outer; }
                unsolvable[cell] = 0;
            }
        }
        check(foundUnsolvable && Sudoku.valid(unsolvable), "Unsatisfiable fixture has no direct clue conflicts");
        expectFailure(() -> library.importCollection(document(row(PuzzleLibrary.givens(unsolvable), "Easy"))),
                "Structurally valid but unsolvable grid rejected");
        expectFailure(() -> library.importCollection(document(row("0".repeat(80), "Easy"))), "Wrong puzzle length rejected");
        expectFailure(() -> library.importCollection(document(row("x" + "0".repeat(80), "Easy"))), "Nondigit givens rejected");
        expectFailure(() -> library.importCollection(document(row(PuzzleLibrary.givens(first.solution), "Easy"))), "Already filled grids rejected");
        expectFailure(() -> library.importCollection(" ".repeat(PuzzleJson.MAX_BYTES + 1)), "Oversized files rejected");
        expectFailure(() -> library.importCollection(document(a + ",".concat(a).repeat(PuzzleLibrary.MAX_ENTRIES))),
                "Oversized entry arrays rejected");
        String badVote = export.replace("\"offeredDifficulty\":\"Easy\"", "\"offeredDifficulty\":\"Unknown\"");
        expectFailure(() -> library.importCollection(badVote), "Informational votes are still validated");
        check(prior.equals(library.exportJson()), "Invalid late vote validation does not commit earlier staged catalog changes");
        try {
            Thread.currentThread().interrupt();
            library.importCollection(collection);
            throw new AssertionError("Interrupted import was accepted");
        } catch (java.util.concurrent.CancellationException ex) {
            check(prior.equals(library.exportJson()), "Cancelled validation leaves the collection and completion history unchanged");
        } finally { Thread.interrupted(); }
        ByteArrayOutputStream local = new ByteArrayOutputStream();
        library.write(new DataOutputStream(local));
        byte[] corrupt = local.toByteArray();
        corrupt[0] = 0;
        try {
            PuzzleLibrary.read(new DataInputStream(new ByteArrayInputStream(corrupt)));
            throw new AssertionError("Corrupt local library accepted");
        } catch (IOException ex) { check(ex.getMessage() != null, "Corrupt local library fails explicitly, not as empty success"); }
        local.write(1);
        try {
            PuzzleLibrary.read(new DataInputStream(new ByteArrayInputStream(local.toByteArray())));
            throw new AssertionError("Trailing local data accepted");
        } catch (IOException ex) { check(ex.getMessage().contains("trailing"), "Unexpected local data rejected"); }
        String votesOnly = new PuzzleLibrary().exportJson();
        check(new PuzzleLibrary().importCollection(votesOnly).added == 0, "Empty document round-trip is explicit");
        legacy(first, Sudoku.Difficulty.FOCUSED);
        legacy(hard, Sudoku.Difficulty.HARD);
        Game mediumGame = new Game(medium);
        check(Game.read(new DataInputStream(new ByteArrayInputStream(gameBytes(mediumGame)))).difficulty == Sudoku.Difficulty.MEDIUM,
                "Medium save serialization is wired");
        check(Arrays.equals(first.clues, Game.read(new DataInputStream(new ByteArrayInputStream(unfinished))).clues),
                "Original saved givens survive unchanged");
        System.out.println("PASS: " + checks + " curation assertions");
    }

    private static void legacy(Sudoku.Puzzle puzzle, Sudoku.Difficulty difficulty) throws Exception {
        Game game = new Game(puzzle);
        game.elapsedMillis = 42000;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x53554432); out.writeUTF(difficulty.name());
        for (int[] array : new int[][]{game.clues, game.solution, game.values, game.notes}) {
            for (int value : array) out.writeShort(value);
        }
        out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(false);
        out.writeInt(-1); out.writeLong(game.elapsedMillis); out.writeInt(0);
        Game restored = Game.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(restored.difficulty == difficulty && restored.elapsedMillis == 42000
                && Arrays.equals(restored.clues, game.clues), "Legacy SUD2 identifiers and timer preserved");
        System.arraycopy(restored.solution, 0, restored.values, 0, 81);
        PuzzleLibrary library = new PuzzleLibrary();
        library.recordCompletion(restored);
        check(reload(library).completedCount() == 1, "Legacy completed save seeds consumption without inventing a vote");
    }

    private static int firstEmpty(Game game) {
        for (int i = 0; i < 81; i++) if (game.clues[i] == 0) return i;
        throw new AssertionError("Missing empty cell");
    }

    private static String entry(Sudoku.Puzzle puzzle, String difficulty) { return row(PuzzleLibrary.givens(puzzle.clues), difficulty); }
    private static String row(String givens, String difficulty) {
        return "{\"id\":\"" + PuzzleLibrary.id(givens) + "\",\"givens\":\"" + givens + "\",\"difficulty\":\"" + difficulty + "\"}";
    }
    private static String document(String rows) {
        return "{\"format\":\"quietgrid-sudoku\",\"version\":1,\"puzzles\":[" + rows + "],\"votes\":[]}";
    }
    private static byte[] gameBytes(Game game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        game.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    private static PuzzleLibrary reload(PuzzleLibrary library) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        library.write(new DataOutputStream(bytes));
        return PuzzleLibrary.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void expectFailure(Runnable action, String reason) {
        try { action.run(); }
        catch (IllegalArgumentException ex) {
            check(ex.getMessage() != null && !ex.getMessage().isEmpty(), reason + " gives explicit feedback");
            return;
        }
        throw new AssertionError(reason);
    }
}
