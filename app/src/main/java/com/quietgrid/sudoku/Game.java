package com.quietgrid.sudoku;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Note intent is independent of temporary constraints; no peer edit destroys it. */
public final class Game {
    public final int[] clues, solution;
    public final int[] values = new int[81], notes = new int[81];
    public final Sudoku.Difficulty difficulty;
    public boolean autoNotes, showMistakes, noteMode;
    public int selected = -1;
    public int armedDigit;
    public long elapsedMillis;
    private final Deque<Snapshot> history = new ArrayDeque<>();

    public Game(Sudoku.Puzzle puzzle) {
        clues = puzzle.clues.clone(); solution = puzzle.solution.clone();
        difficulty = puzzle.difficulty;
        System.arraycopy(clues, 0, values, 0, 81);
    }

    public int visibleNotes(int cell) {
        return values[cell] == 0 ? notes[cell] & Sudoku.candidates(values, cell) : 0;
    }

    public void toggleDigit(int digit) {
        if (digit < 1 || digit > 9) throw new IllegalArgumentException("Digit must be 1 through 9");
        if (won() || remaining(digit) == 0) { normalizeArmedDigit(); return; }
        selected = -1;
        armedDigit = armedDigit == digit ? 0 : digit;
    }

    public boolean chooseDigit(int digit) {
        if (digit < 1 || digit > 9) throw new IllegalArgumentException("Digit must be 1 through 9");
        if (remaining(digit) == 0 || won()) { selected = -1; normalizeArmedDigit(); return false; }
        if (selected >= 0 && clues[selected] == 0) {
            if (!noteMode && conflicts(selected, digit)) { selected = -1; return false; }
            armedDigit = 0;
            boolean entered = enter(digit);
            selected = -1;
            return entered;
        }
        toggleDigit(digit);
        return false;
    }

    public void setNoteMode(boolean enabled) {
        noteMode = enabled;
        normalizeArmedDigit();
    }

    public void normalizeArmedDigit() {
        if (armedDigit != 0 && (won() || remaining(armedDigit) == 0)) {
            armedDigit = 0;
            selected = -1;
        }
    }

    public boolean selectCell(int cell) {
        if (cell < 0 || cell >= 81) throw new IllegalArgumentException("Cell must be 0 through 80");
        selected = cell;
        if (armedDigit == 0) return false;
        boolean entered = enter(armedDigit);
        selected = -1;
        return entered;
    }

    public boolean enter(int digit) {
        if (selected < 0 || clues[selected] != 0 || digit < 1 || digit > 9 || won() || remaining(digit) == 0) return false;
        if (!noteMode && conflicts(selected, digit)) return false;
        if (noteMode) {
            if (values[selected] != 0 || (Sudoku.candidates(values, selected) & Sudoku.bit(digit)) == 0) return false;
            checkpoint();
            notes[selected] ^= Sudoku.bit(digit);
        } else {
            if (values[selected] == digit) return false;
            checkpoint();
            values[selected] = digit;
        }
        normalizeArmedDigit();
        return true;
    }

    public boolean conflicts(int cell, int digit) {
        return (Sudoku.candidates(values, cell) & Sudoku.bit(digit)) == 0;
    }

    public boolean clear() {
        if (selected < 0 || clues[selected] != 0 || (values[selected] == 0 && notes[selected] == 0)) return false;
        checkpoint();
        if (values[selected] != 0) values[selected] = 0;
        else notes[selected] = 0;
        normalizeArmedDigit();
        return true;
    }

    public void setAutoNotes(boolean enabled) {
        if (autoNotes == enabled) return;
        checkpoint();
        autoNotes = enabled;
        if (enabled) {
            for (int i = 0; i < 81; i++) notes[i] = clues[i] == 0 ? Sudoku.ALL : 0;
        }
    }

    public boolean canUndo() { return !history.isEmpty(); }
    public boolean undo() {
        if (history.isEmpty()) return false;
        Snapshot s = history.removeLast();
        System.arraycopy(s.values, 0, values, 0, 81);
        System.arraycopy(s.notes, 0, notes, 0, 81);
        autoNotes = s.autoNotes; selected = s.selected;
        normalizeArmedDigit();
        if (armedDigit != 0) selected = -1;
        return true;
    }

    public int remaining(int digit) {
        int count = 0;
        for (int value : values) if (value == digit) count++;
        return Math.max(0, 9 - count);
    }

    public int filled() {
        int count = 0;
        for (int value : values) if (value != 0) count++;
        return count;
    }

    public boolean won() { return Arrays.equals(values, solution); }

    private void checkpoint() {
        if (history.size() >= 500) history.removeFirst();
        history.addLast(new Snapshot(values.clone(), notes.clone(), autoNotes, armedDigit == 0 ? selected : -1));
    }

    private static final class Snapshot {
        final int[] values, notes;
        final boolean autoNotes;
        final int selected;
        Snapshot(int[] values, int[] notes, boolean autoNotes, int selected) {
            this.values = values; this.notes = notes; this.autoNotes = autoNotes; this.selected = selected;
        }
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(0x53554433);
        out.writeUTF(difficulty.name());
        writeArray(out, clues); writeArray(out, solution); writeArray(out, values); writeArray(out, notes);
        out.writeBoolean(autoNotes); out.writeBoolean(showMistakes); out.writeBoolean(noteMode);
        out.writeInt(selected); out.writeInt(armedDigit); out.writeLong(elapsedMillis);
        out.writeInt(history.size());
        for (Snapshot s : history) {
            writeArray(out, s.values); writeArray(out, s.notes);
            out.writeBoolean(s.autoNotes); out.writeInt(s.selected);
        }
    }

    public static Game read(DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version != 0x53554432 && version != 0x53554433) throw new IOException("Unrecognized saved-game format");
        Sudoku.Difficulty difficulty;
        try { difficulty = Sudoku.Difficulty.valueOf(in.readUTF()); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid saved difficulty", ex); }
        int[] clues = readArray(in, 9), solution = readArray(in, 9);
        if (!Sudoku.valid(clues) || !Sudoku.valid(solution) || Arrays.stream(solution).anyMatch(v -> v == 0)) {
            throw new IOException("Invalid saved puzzle");
        }
        for (int i = 0; i < 81; i++) if (clues[i] != 0 && clues[i] != solution[i]) throw new IOException("Invalid saved clues");
        Game game = new Game(new Sudoku.Puzzle(clues, solution, difficulty, null));
        System.arraycopy(readArray(in, 9), 0, game.values, 0, 81);
        System.arraycopy(readArray(in, Sudoku.ALL), 0, game.notes, 0, 81);
        game.autoNotes = in.readBoolean(); game.showMistakes = in.readBoolean(); game.noteMode = in.readBoolean();
        game.selected = in.readInt();
        game.armedDigit = version == 0x53554433 ? in.readInt() : 0;
        game.elapsedMillis = in.readLong();
        if (game.selected < -1 || game.selected > 80 || game.armedDigit < 0 || game.armedDigit > 9
                || game.elapsedMillis < 0) throw new IOException("Invalid saved state");
        validateValues(game.values, clues);
        int count = in.readInt();
        if (count < 0 || count > 500) throw new IOException("Invalid undo history");
        for (int i = 0; i < count; i++) {
            int[] v = readArray(in, 9), n = readArray(in, Sudoku.ALL);
            boolean auto = in.readBoolean();
            int selection = in.readInt();
            validateValues(v, clues);
            if (selection < -1 || selection > 80) throw new IOException("Invalid saved selection");
            game.history.addLast(new Snapshot(v, n, auto, selection));
        }
        game.normalizeArmedDigit();
        if (game.armedDigit != 0) game.selected = -1;
        return game;
    }

    private static void validateValues(int[] values, int[] clues) throws IOException {
        for (int i = 0; i < 81; i++) if (clues[i] != 0 && values[i] != clues[i]) throw new IOException("Changed saved clue");
    }

    private static void writeArray(DataOutputStream out, int[] array) throws IOException {
        for (int value : array) out.writeShort(value);
    }

    private static int[] readArray(DataInputStream in, int max) throws IOException {
        int[] array = new int[81];
        for (int i = 0; i < 81; i++) {
            array[i] = in.readUnsignedShort();
            if (array[i] > max) throw new IOException("Invalid saved cell");
        }
        return array;
    }
}
