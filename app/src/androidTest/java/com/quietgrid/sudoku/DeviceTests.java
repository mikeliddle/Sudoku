package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.ImageButton;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

/** Device fixtures are isolated and the user's game/settings are restored after every test. */
@SuppressWarnings("deprecation")
public final class DeviceTests extends InstrumentationTestCase {
    private Activity activity;
    private byte[] originalSave;
    private Map<String, ?> originalSettings;

    @Override protected void setUp() throws Exception {
        super.setUp();
        File file = saveFile();
        originalSave = file.exists() ? Files.readAllBytes(file.toPath()) : null;
        originalSettings = settings().getAll();
        assertTrue(settings().edit().clear().commit());
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null && !activity.isDestroyed()) {
                getInstrumentation().runOnMainSync(() -> activity.finish());
                getInstrumentation().waitForIdleSync();
            }
            if (originalSave != null) Files.write(saveFile().toPath(), originalSave);
            else if (saveFile().exists()) assertTrue(saveFile().delete());
            SharedPreferences.Editor editor = settings().edit().clear();
            for (Map.Entry<String, ?> entry : originalSettings.entrySet()) {
                if (entry.getValue() instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                else if (entry.getValue() instanceof Integer) editor.putInt(entry.getKey(), (Integer) entry.getValue());
                else if (entry.getValue() instanceof Long) editor.putLong(entry.getKey(), (Long) entry.getValue());
            }
            assertTrue(editor.commit());
        } finally { super.tearDown(); }
    }

    public void testNativeInputNotesRotationAndPersistence() throws Throwable {
        Game original = focused();
        original.elapsedMillis = 123000;
        write(original);
        launch();
        assertNotNull(findText("Choose your difficulty"));
        assertNull(findType(activity.getWindow().getDecorView(), BoardView.class));
        SystemClock.sleep(1200);
        assertEquals("Menu never advances saved play time", 123000L, saved().elapsedMillis);
        capture("sudoku-menu.png");
        clickPrefix("Resume ");
        int target = firstEmpty(original);
        clickText("Settings");
        assertNotNull(findText("Colors"));
        long pausedTime = saved().elapsedMillis;
        SystemClock.sleep(1200);
        clickText("Auto notes: Easy");
        assertEquals("Settings pauses time", pausedTime, saved().elapsedMillis);
        assertTrue(saved().autoNotes);
        capture("sudoku-settings.png");
        clickText("Blue");
        assertEquals(4, settings().getInt("accentColor", -1));
        clickText("Show mistakes");
        assertTrue(saved().showMistakes);
        clickText("Show mistakes");
        clickText("Close settings");
        BoardView board = board();
        Game auto = saved();
        int digit = original.solution[target];
        clickNumber(digit);
        assertTrue("Bank selection alone never enters", Arrays.equals(original.values, saved().values));
        assertEquals(digit, saved().armedDigit);
        clickNumber(digit);
        assertEquals("Same digit cancels", 0, saved().armedDigit);
        clickNumber(digit == 9 ? 1 : digit + 1);
        clickNumber(digit);
        assertEquals("Different digit switches", digit, saved().armedDigit);
        int given = -1;
        for (int i = 0; i < 81; i++) if (original.clues[i] != 0) { given = i; break; }
        selectCell(board, given);
        assertEquals("Given tap cannot change clue", original.clues[given], saved().values[given]);
        assertEquals("Given tap keeps armed input", digit, saved().armedDigit);
        clickNumber(digit == 9 ? 1 : digit + 1);
        assertEquals("Bank switch clears given square highlight", -1, saved().selected);
        clickNumber(digit);
        selectCell(board, target);
        assertEquals(digit, saved().values[target]);
        assertEquals(original.remaining(digit) - 1, saved().remaining(digit));
        clickText("Sudoku");
        assertEquals("Outside tap cancels", 0, saved().armedDigit);
        assertEquals("Outside tap clears square and house highlight", -1, saved().selected);
        selectCell(board, target);
        clickNumber(digit);
        assertEquals("Bank tap clears editable square highlight", -1, saved().selected);
        clickNumber(digit);
        assertEquals("After one-shot entry the next bank tap arms", digit, saved().armedDigit);
        clickNumber(digit);
        assertEquals("Bank cancellation leaves no square highlight", -1, saved().selected);
        selectCell(board, target);
        clickText("Erase");
        assertEquals(0, saved().values[target]);
        assertEquals(auto.visibleNotes(target), saved().visibleNotes(target));
        clickText("Undo");
        assertEquals(digit, saved().values[target]);
        clickText("Undo");
        assertEquals(0, saved().values[target]);
        selectCell(board, target);
        clickText("Sudoku");
        assertEquals("Unarmed outside tap also clears square highlight", -1, saved().selected);

        clickNumber(digit);
        clickText("Notes: off");
        assertEquals("Notes button preserves armed digit", digit, saved().armedDigit);
        assertTrue(saved().noteMode);
        assertEquals("Switching mode itself never writes", 0, saved().values[target]);
        selectCell(board, target);
        assertEquals(0, saved().visibleNotes(target) & Sudoku.bit(digit));
        clickText("Notes: on");
        assertEquals("Switching back preserves digit", digit, saved().armedDigit);
        assertEquals("Number-first notes never select a square", -1, saved().selected);
        assertFalse(saved().noteMode);
        assertEquals(0, saved().values[target]);
        selectCell(board, target);
        assertEquals(digit, saved().values[target]);
        clickText("Sudoku");
        selectCell(board, target);
        clickText("Erase");
        assertEquals("Clear does not resurrect manual removal", 0, saved().visibleNotes(target) & Sudoku.bit(digit));
        clickText("Settings");
        clickText("Auto notes: Easy");
        clickText("Auto notes: Easy");
        assertTrue((saved().visibleNotes(target) & Sudoku.bit(digit)) != 0);
        clickText("Close settings");
        clickNumber(digit);
        Game beforeRotation = saved();
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        assertStateEquals(beforeRotation, saved());
        assertBankFits();
        capture("sudoku-landscape.png");
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertStateEquals(beforeRotation, saved());
        assertBankFits();
        capture("sudoku-portrait.png");
        recreate();
        assertStateEquals(beforeRotation, saved());
        assertTrue(saved().canUndo());
        assertFalse(board().onCheckIsTextEditor());
        clickText("Menu");
        long stoppedTime = saved().elapsedMillis;
        SystemClock.sleep(1200);
        finishActivity();
        launch();
        assertNotNull(findText("Choose your difficulty"));
        assertEquals(stoppedTime, saved().elapsedMillis);
        clickPrefix("Resume ");
        assertTrue(Arrays.equals(beforeRotation.values, saved().values));
        assertTrue(Arrays.equals(beforeRotation.notes, saved().notes));
        assertEquals(4, settings().getInt("accentColor", -1));
        runTestOnUiThread(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
    }

    public void testExhaustionAndWinFlow() throws Throwable {
        Game game = focused();
        int digit = 0;
        for (int d = 1; d <= 9 && digit == 0; d++) {
            for (int i = 0; i < 81; i++) if (game.clues[i] == 0 && game.solution[i] == d) { digit = d; break; }
        }
        int last = -1;
        for (int i = 0; i < 81; i++) if (game.solution[i] == digit) {
            if (game.clues[i] == 0 && last == -1) last = i;
            else game.values[i] = digit;
        }
        assertEquals(1, game.remaining(digit));
        write(game); launch(); clickPrefix("Resume ");
        clickNumber(digit); selectCell(board(), last);
        assertEquals(0, saved().remaining(digit));
        assertEquals("Last number disarms", 0, saved().armedDigit);
        assertEquals("Last number clears all interaction highlights", -1, saved().selected);
        View exhausted = findText(digit + "\n0");
        assertNotNull(exhausted); assertFalse("Exhausted button actually disabled", exhausted.isEnabled());
        tap(exhausted);
        assertEquals(0, saved().armedDigit);
        assertEquals("Even disabled bank taps clear square highlight", -1, saved().selected);
        selectCell(board(), last);
        clickText("Erase");
        assertEquals(1, saved().remaining(digit));
        assertTrue(findText(digit + "\n1").isEnabled());
        clickNumber(digit); clickText("Undo");
        assertEquals(0, saved().armedDigit);
        assertEquals("Re-exhausting through undo clears selection", -1, saved().selected);
        assertFalse(findText(digit + "\n0").isEnabled());
        clickText("Undo");
        assertTrue(findText(digit + "\n1").isEnabled());
        finishActivity();

        Game almost = focused();
        System.arraycopy(almost.solution, 0, almost.values, 0, 81);
        int finalCell = firstEmpty(almost);
        almost.values[finalCell] = 0;
        almost.elapsedMillis = 183000;
        write(almost); launch(); clickPrefix("Resume ");
        clickNumber(almost.solution[finalCell]);
        selectCell(board(), finalCell);
        assertTrue("Win requires genuine correct board", saved().won());
        assertEquals(0, saved().armedDigit);
        SystemClock.sleep(1900);
        assertNotNull(findText("Beautifully solved!"));
        assertNull(findText("Every square, worked out by you."));
        TextView result = (TextView) findPrefix(activity.getWindow().getDecorView(), "Easy  ");
        assertNotNull("Win details include difficulty", result);
        assertTrue("Win details include time", result.getText().toString().contains("3:"));
        long finishedTime = saved().elapsedMillis;
        assertEquals("Completion records the actual active-play timer", finishedTime,
                settings().getLong("bestTime.FOCUSED", -1));
        capture("sudoku-win.png");
        SystemClock.sleep(1200);
        assertNull(findText("New game"));
        clickText("Done");
        assertNotNull(findText("Choose your difficulty"));
        assertNull(findText("A fresh challenge. Your way to solve."));
        assertNull("Solved game not offered as resume", findPrefix(activity.getWindow().getDecorView(), "Resume "));
        assertEquals("Win stops timer", finishedTime, saved().elapsedMillis);
        assertNull(findText("How to play"));
        assertNull(findText("Last result"));
        assertTrue("Hiding menu entries preserves completed save", saved().won());
        capture("sudoku-clean-menu.png");
        clickPrefix("Easy");
        waitForGame();
        Game generated = saved();
        assertFalse(generated.won());
        assertEquals(1, Sudoku.solutions(generated.clues, 2));
        assertTrue(Sudoku.rate(generated.clues, false).solved);
    }

    public void testHardGenerationCertificateOnPhone() {
        Sudoku.Puzzle puzzle = Sudoku.generate(Sudoku.Difficulty.HARD, new Random(41100));
        assertEquals(1, Sudoku.solutions(puzzle.clues, 2));
        assertFalse(Sudoku.rate(puzzle.clues, false).solved);
        Sudoku.Rating rating = Sudoku.rate(puzzle.clues, true);
        assertTrue(rating.solved);
        assertTrue(rating.deductions >= 6 && rating.eliminations >= 12);
    }

    public void testPersonalBestTimes() throws Throwable {
        Game fixture = focused();
        fixture.elapsedMillis = 272000;
        write(fixture); launch();
        assertFalse("Unfinished games never create a best", settings().contains("bestTime.FOCUSED"));
        assertNotNull(findText("Easy"));
        assertNotNull(findText("Hard"));
        finishActivity();

        System.arraycopy(fixture.solution, 0, fixture.values, 0, 81);
        int target = firstEmpty(fixture);
        fixture.values[target] = fixture.solution[target] % 9 + 1;
        write(fixture); launch();
        assertFalse("Full but invalid boards never create a best", settings().contains("bestTime.FOCUSED"));
        finishActivity();

        fixture.values[target] = fixture.solution[target];
        write(fixture); launch();
        assertEquals(272000L, settings().getLong("bestTime.FOCUSED", -1));
        assertNotNull(findText("Easy    Best 4:32"));
        assertNotNull(findNamed(activity.getWindow().getDecorView(), "Easy. Best solve time: 4:32"));
        assertNotNull(findText("Hard"));
        finishActivity();

        fixture.elapsedMillis = 300000;
        write(fixture); launch();
        assertEquals("Slower solve cannot replace best", 272000L, settings().getLong("bestTime.FOCUSED", -1));
        finishActivity();
        fixture.elapsedMillis = 200000;
        write(fixture); launch();
        assertEquals("Faster solve replaces best", 200000L, settings().getLong("bestTime.FOCUSED", -1));
        finishActivity();

        Game hard = new Game(new Sudoku.Puzzle(fixture.clues, fixture.solution, Sudoku.Difficulty.HARD, null));
        System.arraycopy(hard.solution, 0, hard.values, 0, 81);
        hard.elapsedMillis = 400000;
        write(hard); launch();
        assertEquals(200000L, settings().getLong("bestTime.FOCUSED", -1));
        assertEquals(400000L, settings().getLong("bestTime.HARD", -1));
        assertNotNull(findText("Easy    Best 3:20"));
        assertNotNull(findText("Hard    Best 6:40"));
        recreate();
        assertEquals("Recreation does not rewrite records", 400000L, settings().getLong("bestTime.HARD", -1));
        clickText("Settings");
        for (String appearance : new String[]{"Dark", "Light"}) {
            chooseAppearance(appearance);
            clickText("Close settings");
            assertNotNull(findText("Easy    Best 3:20"));
            assertNotNull(findText("Hard    Best 6:40"));
            capture("sudoku-best-times-" + appearance.toLowerCase(java.util.Locale.ROOT) + ".png");
            clickText("Settings");
        }
        clickText("Close settings");
        clickPrefix("Easy");
        waitForGame();
        assertFalse(saved().won());
        assertEquals("New games keep existing records", 200000L, settings().getLong("bestTime.FOCUSED", -1));
        finishActivity(); launch();
        assertNotNull(findText("Easy    Best 3:20"));
        assertNotNull(findText("Hard    Best 6:40"));
        clickPrefix("Resume ");
        clickText("Menu");
        assertEquals("Resume cannot create or change a best", 200000L, settings().getLong("bestTime.FOCUSED", -1));
        assertEquals(400000L, settings().getLong("bestTime.HARD", -1));
    }

    public void testNumberFirstSwitchingAndEntryFeedback() throws Throwable {
        int[] solution = new int[81];
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
            solution[r * 9 + c] = (r * 3 + r / 3 + c) % 9 + 1;
        Game fixture = new Game(new Sudoku.Puzzle(new int[81], solution, Sudoku.Difficulty.FOCUSED, null));
        fixture.setAutoNotes(true);
        write(fixture);
        launch(); clickPrefix("Resume ");
        clickNumber(6); selectCell(board(), 0);
        assertEquals(6, saved().values[0]);
        assertEquals(-1, saved().selected);
        clickNumber(7);
        assertEquals(7, saved().armedDigit);
        assertEquals(6, saved().values[0]);
        selectCell(board(), 1);
        assertEquals(7, saved().values[1]);
        assertEquals(-1, saved().selected);
        selectCell(board(), 2);
        assertEquals(0, saved().values[2]);
        assertEquals(-1, saved().selected);
        assertEquals(7, saved().armedDigit);
        clickNumber(7);
        selectCell(board(), 2);
        clickNumber(8);
        assertEquals(8, saved().values[2]);
        assertEquals(0, saved().armedDigit);
        assertEquals(-1, saved().selected);
        clickNumber(9); clickText("Notes: off");
        selectCell(board(), 3);
        assertEquals(0, saved().visibleNotes(3) & Sudoku.bit(9));
        assertEquals(-1, saved().selected);
        clickText("Notes: on");
        assertEquals(9, saved().armedDigit);
        selectCell(board(), 3);
        assertEquals(9, saved().values[3]);
        assertEquals(-1, saved().selected);
        recreate();
        assertEquals(-1, saved().selected);
        assertEquals(9, saved().armedDigit);
        assertEquals(-1, board().animatedCell());

        for (boolean dark : new boolean[]{false, true}) {
            for (int color = 0; color < MainActivity.COLOR_NAMES.length; color++) {
                final int accent = color;
                Bitmap[] frames = new Bitmap[2];
                runTestOnUiThread(() -> {
                    Game model = new Game(new Sudoku.Puzzle(new int[81], solution, Sudoku.Difficulty.FOCUSED, null));
                    BoardView preview = new BoardView(activity, new MainActivity.Palette(dark, accent));
                    android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
                    host.addView(preview);
                    preview.layout(0, 0, 900, 900);
                    preview.setGame(model);
                    model.chooseDigit(6);
                    preview.getAccessibilityNodeProvider().performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null);
                    assertEquals(-1, model.selected);
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertEquals(0, preview.animatedCell());
                    frames[0] = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888);
                    preview.draw(new android.graphics.Canvas(frames[0]));
                    preview.clearEntryFeedback();
                    frames[1] = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888);
                    preview.draw(new android.graphics.Canvas(frames[1]));
                    assertEquals(-1, preview.animatedCell());
                    if (android.animation.ValueAnimator.areAnimatorsEnabled())
                        assertFalse("Feedback changes rendering without selecting", frames[0].sameAs(frames[1]));
                    model.chooseDigit(7);
                    preview.getAccessibilityNodeProvider().performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null);
                    model.chooseDigit(8);
                    preview.getAccessibilityNodeProvider().performAction(2, AccessibilityNodeInfo.ACTION_CLICK, null);
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertEquals(2, preview.animatedCell());
                    preview.clearEntryFeedback();
                    model.chooseDigit(8);
                    preview.getAccessibilityNodeProvider().performAction(3, AccessibilityNodeInfo.ACTION_CLICK, null);
                    preview.chooseDigit(9);
                    assertEquals(9, model.values[3]);
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertEquals(3, preview.animatedCell());
                    preview.chooseDigit(9);
                    assertEquals(-1, preview.animatedCell());
                    model.setNoteMode(true);
                    preview.getAccessibilityNodeProvider().performAction(4, AccessibilityNodeInfo.ACTION_CLICK, null);
                    assertEquals(-1, preview.animatedCell());
                });
                File directory = getInstrumentation().getTargetContext().getExternalFilesDir(null);
                try (FileOutputStream stream = new FileOutputStream(new File(directory,
                        "sudoku-entry-" + (dark ? "dark" : "light") + "-" + color + ".png"))) {
                    assertTrue(frames[0].compress(Bitmap.CompressFormat.PNG, 100, stream));
                }
                for (Bitmap frame : frames) frame.recycle();
            }
        }
        runTestOnUiThread(() -> {
            board().chooseDigit(1);
            board().getAccessibilityNodeProvider().performAction(4, AccessibilityNodeInfo.ACTION_CLICK, null);
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertEquals(4, board().animatedCell());
        });
        SystemClock.sleep(400);
        assertEquals("Animation clears naturally", -1, board().animatedCell());
        clickNumber(2); selectCell(board(), 5);
        clickText("Sudoku");
        assertEquals("Outside cancellation clears feedback", -1, board().animatedCell());
        assertEquals(0, saved().armedDigit);
        assertEquals(-1, saved().selected);
        String originalScale = android.provider.Settings.Global.getString(
                activity.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE);
        try {
            shell("settings put global animator_duration_scale 0");
            SystemClock.sleep(200);
            runTestOnUiThread(() -> {
                assertFalse(android.animation.ValueAnimator.areAnimatorsEnabled());
                board().chooseDigit(3);
                board().getAccessibilityNodeProvider().performAction(6, AccessibilityNodeInfo.ACTION_CLICK, null);
                assertEquals(-1, board().animatedCell());
            });
            assertEquals(3, saved().values[6]);
        } finally {
            shell(originalScale == null ? "settings delete global animator_duration_scale"
                    : "settings put global animator_duration_scale " + originalScale);
        }
    }

    public void testCellFirstAndDifficultyPreferences() throws Throwable {
        Game fixture = focused();
        fixture.setAutoNotes(true);
        int target = firstEmpty(fixture), digit = fixture.solution[target];
        int[] kept = fixture.notes.clone();
        kept[target] &= ~Sudoku.bit(digit == 9 ? 1 : digit + 1);
        fixture.notes[target] = kept[target];
        write(fixture);
        assertTrue(settings().edit().putBoolean("autoNotes", false).commit());
        launch();
        assertTrue("Migration respects current game instead of old global default",
                settings().getBoolean("autoNotes.FOCUSED", false));
        assertFalse(settings().getBoolean("autoNotes.HARD", true));
        assertTrue(Arrays.equals(kept, saved().notes));
        assertNotNull(findPrefix(activity.getWindow().getDecorView(), "Resume easy"));
        assertFlatButtons(activity.getWindow().getDecorView());
        clickPrefix("Resume ");
        selectCell(board(), target);
        clickNumber(digit);
        assertEquals("Real square-first touch enters", digit, saved().values[target]);
        assertEquals(-1, saved().selected);
        assertEquals(0, saved().armedDigit);
        clickText("Undo");
        clickText("Sudoku");
        clickText("Notes: off");
        selectCell(board(), target);
        clickNumber(digit);
        assertEquals(0, saved().values[target]);
        assertEquals(0, saved().visibleNotes(target) & Sudoku.bit(digit));
        assertEquals(0, saved().armedDigit);
        clickText("Notes: on");
        clickText("Settings");
        clickText("Auto notes: Hard");
        assertTrue(settings().getBoolean("autoNotes.HARD", false));
        assertTrue("Other difficulty change never resets current manual notes",
                (saved().notes[target] & Sudoku.bit(digit)) == 0);
        clickText("Auto notes: Easy");
        assertFalse(settings().getBoolean("autoNotes.FOCUSED", true));
        assertFlatButtons(activity.getWindow().getDecorView());
        capture("sudoku-consistent-settings.png");
        recreate();
        assertFalse(saved().autoNotes);
        assertTrue(settings().getBoolean("autoNotes.HARD", false));
        clickText("Close settings"); clickText("Menu");
        capture("sudoku-consistent-menu.png");
        clickText("Hard"); confirmReplacement(); waitForGame();
        assertEquals(Sudoku.Difficulty.HARD, saved().difficulty);
        assertTrue("Hard generation uses its own preference", saved().autoNotes);
        clickText("Menu"); clickText("Easy"); confirmReplacement(); waitForGame();
        assertFalse("Easy generation keeps its own preference", saved().autoNotes);
        finishActivity(); launch(); clickPrefix("Resume ");
        assertFalse("Resuming retains per-difficulty preference", saved().autoNotes);
        assertFlatButtons(activity.getWindow().getDecorView());
        capture("sudoku-consistent-controls.png");
    }

    private void assertFlatButtons(View view) {
        if ((view instanceof android.widget.Button && !(view instanceof android.widget.CompoundButton))
                || view instanceof android.widget.ImageButton) {
            assertEquals("Custom buttons have no conflicting native elevation", 0f, view.getElevation());
            assertEquals(0f, view.getTranslationZ());
            assertNull(view.getStateListAnimator());
            assertTrue("Buttons retain ripple feedback", view.getBackground() instanceof android.graphics.drawable.RippleDrawable);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertFlatButtons(group.getChildAt(i));
        }
    }

    private void assertNavigationIcon(String name) throws Throwable {
        View control = findNamed(activity.getWindow().getDecorView(), name);
        assertTrue(control instanceof android.widget.ImageButton);
        assertEquals(name, control.getTooltipText());
        float density = activity.getResources().getDisplayMetrics().density;
        assertTrue(control.getWidth() >= 48 * density);
        assertTrue(control.getHeight() >= 48 * density);
        assertEquals(0f, control.getElevation());
        runTestOnUiThread(() -> {
            android.graphics.drawable.Drawable background = control.getBackground();
            assertTrue(background instanceof android.graphics.drawable.RippleDrawable);
            Bitmap bitmap = Bitmap.createBitmap(control.getWidth(), control.getHeight(), Bitmap.Config.ARGB_8888);
            background.draw(new android.graphics.Canvas(bitmap));
            for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                assertEquals("Navigation surface has no resting fill or border", 0, android.graphics.Color.alpha(bitmap.getPixel(x, y)));
            bitmap.recycle();
        });
    }

    private void confirmReplacement() {
        android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> buttons =
                root.findAccessibilityNodeInfosByText("Start new game");
        assertFalse("Replacement confirmation is in the separate dialog window", buttons.isEmpty());
        assertTrue(buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync();
    }

    public void testPaddedEraseControls() throws Throwable {
        for (int appearance : new int[]{1, 2}) {
            Game fixture = focused();
            assertTrue(settings().edit().putInt("appearance", appearance).commit());
            write(fixture); launch(); clickPrefix("Resume ");
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotate(orientation);
                float density = activity.getResources().getDisplayMetrics().density;
                Rect screen = new Rect();
                activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);
                for (View control : new View[]{
                        findNamed(activity.getWindow().getDecorView(), "Undo"),
                        findNamed(activity.getWindow().getDecorView(), "Erase"),
                        findPrefix(activity.getWindow().getDecorView(), "Notes: ")}) {
                    assertNotNull(control);
                    int padding = Math.round(16 * density);
                    assertEquals(padding, control.getPaddingLeft());
                    assertEquals(padding, control.getPaddingRight());
                    assertEquals(padding, control.getPaddingTop());
                    assertEquals(padding, control.getPaddingBottom());
                    assertTrue(control.getWidth() >= Math.round(48 * density));
                    assertEquals(Math.round(56 * density), control.getHeight());
                    Rect bounds = new Rect();
                    assertTrue(control.getGlobalVisibleRect(bounds));
                    assertTrue("Padded control fits on screen", screen.contains(bounds));
                    assertEquals("Control is not clipped", control.getHeight(), bounds.height());
                }
                TextView notes = (TextView) findPrefix(activity.getWindow().getDecorView(), "Notes: ");
                assertTrue("Notes label fits inside its padding",
                        notes.getPaint().measureText(notes.getText().toString())
                                <= notes.getWidth() - notes.getPaddingLeft() - notes.getPaddingRight());
                ImageButton erase = (ImageButton) findNamed(activity.getWindow().getDecorView(), "Erase");
                assertEquals("Erase", erase.getTooltipText().toString());
                assertFalse("Erase disabled with no selected cell", erase.isEnabled());
                int cell = firstEmpty(saved());
                selectCell(board(), cell);
                clickNumber(fixture.solution[cell]);
                Game entered = saved();
                selectCell(board(), cell);
                assertTrue(erase.isEnabled());
                clickText("Erase");
                Game cleared = saved();
                assertEquals(0, cleared.values[cell]);
                for (int other = 0; other < 81; other++) {
                    if (other != cell) assertEquals(entered.values[other], cleared.values[other]);
                }
                assertTrue(Arrays.equals(entered.clues, cleared.clues));
                clickText("Undo");
                assertTrue(Arrays.equals(entered.values, saved().values));
                clickText("Sudoku");
                capture("sudoku-padded-controls-" + appearance + "-"
                        + (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? "landscape" : "portrait") + ".png");
            }
            finishActivity();
        }
    }

    public void testDuplicateFeedbackAndQuietControls() throws Throwable {
        for (boolean mistakes : new boolean[]{false, true}) {
            int[] solution = new int[81];
            for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
                solution[r * 9 + c] = (r * 3 + r / 3 + c) % 9 + 1;
            Game fixture = new Game(new Sudoku.Puzzle(new int[81], solution, Sudoku.Difficulty.FOCUSED, null));
            fixture.setAutoNotes(true);
            fixture.values[0] = 9;
            fixture.showMistakes = mistakes;
            write(fixture); launch(); clickPrefix("Resume ");
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Choose a number"));
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Number bank"));
            assertNull(findText("Undo"));
            assertNull(findText("Erase"));
            assertTrue(findNamed(activity.getWindow().getDecorView(), "Undo") instanceof android.widget.ImageButton);
            assertTrue(findNamed(activity.getWindow().getDecorView(), "Erase") instanceof android.widget.ImageButton);
            TextView timer = (TextView) findPrefix(activity.getWindow().getDecorView(), "Easy ");
            assertNotNull(timer);
            assertEquals(android.view.Gravity.CENTER, timer.getGravity());
            assertFalse(timer.getText().toString().contains("filled"));
            clickNumber(9);
            assertNull(findPrefix(activity.getWindow().getDecorView(), "9 selected."));
            for (int target : new int[]{8, 72, 10}) {
                Game before = saved();
                BoardView currentBoard = board();
                runTestOnUiThread(() -> {
                    currentBoard.getAccessibilityNodeProvider().performAction(target, AccessibilityNodeInfo.ACTION_CLICK, null);
                    assertEquals("Red feedback starts on the attempted square", target, currentBoard.rejectedCell());
                });
                Game after = saved();
                assertTrue(Arrays.equals(before.values, after.values));
                assertTrue(Arrays.equals(before.notes, after.notes));
                assertEquals(9, after.armedDigit);
                assertEquals(before.remaining(9), after.remaining(9));
                SystemClock.sleep(650);
                getInstrumentation().waitForIdleSync();
                assertEquals("Red feedback clears automatically", -1, board().rejectedCell());
            }
            clickText("Sudoku");
            selectCell(board(), 8);
            BoardView currentBoard = board();
            runTestOnUiThread(() -> {
                currentBoard.chooseDigit(9);
                assertEquals("Cell-first conflicts also flash red", 8, currentBoard.rejectedCell());
            });
            assertEquals(0, saved().values[8]);
            assertEquals(0, saved().armedDigit);
            clickNumber(8);
            selectCell(board(), 8);
            assertEquals("Legal but incorrect solution value accepted", 8, saved().values[8]);
            finishActivity();
        }
    }

    public void testSelectedNotesRendering() throws Exception {
        int[] solution = new int[81];
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++)
            solution[r * 9 + c] = (r * 3 + r / 3 + c) % 9 + 1;
        for (boolean auto : new boolean[]{false, true}) {
            Game fixture = new Game(new Sudoku.Puzzle(new int[81], solution, Sudoku.Difficulty.FOCUSED, null));
            fixture.setAutoNotes(auto);
            fixture.values[0] = 9;
            if (!auto) {
                fixture.notes[40] = Sudoku.bit(2) | Sudoku.bit(5) | Sudoku.bit(9);
                fixture.notes[1] = fixture.notes[40];
            }
            int[] notesBefore = fixture.notes.clone(), valuesBefore = fixture.values.clone();
            for (boolean dark : new boolean[]{false, true}) for (int color = 0; color < MainActivity.COLOR_NAMES.length; color++) {
                MainActivity.Palette palette = new MainActivity.Palette(dark, color);
                double foreground = android.graphics.Color.luminance(palette.surface);
                double background = android.graphics.Color.luminance(palette.accent);
                assertTrue("Highlighted notes retain readable contrast for every accent/theme",
                        (Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05) >= 4.5);
                Bitmap plain = renderBoard(fixture, palette);
                fixture.toggleDigit(2);
                Bitmap two = renderBoard(fixture, palette);
                assertFalse("Selected note glyph is highlighted", sameNoteSlot(plain, two, 40, 2));
                assertTrue("Other notes in the same cell are unchanged", sameNoteSlot(plain, two, 40, 5));
                if (!auto) assertTrue("Absent manual notes stay absent", sameNoteSlot(plain, two, 41, 2));
                fixture.toggleDigit(5);
                Bitmap five = renderBoard(fixture, palette);
                assertTrue("Switching clears the previous note highlight", sameNoteSlot(plain, five, 40, 2));
                assertFalse(sameNoteSlot(plain, five, 40, 5));
                fixture.toggleDigit(5);
                Bitmap cancelled = renderBoard(fixture, palette);
                assertTrue("Cancellation clears all matching highlights", plain.sameAs(cancelled));
                fixture.toggleDigit(9);
                Bitmap nine = renderBoard(fixture, palette);
                assertTrue("Suppressed notes are never revealed", sameNoteSlot(plain, nine, 1, 9));
                assertFalse(sameNoteSlot(plain, nine, 40, 9));
                fixture.toggleDigit(9);
                assertTrue(Arrays.equals(notesBefore, fixture.notes));
                assertTrue(Arrays.equals(valuesBefore, fixture.values));
                plain.recycle(); two.recycle(); five.recycle(); cancelled.recycle(); nine.recycle();
            }
            write(fixture); launch(); clickPrefix("Resume ");
            clickNumber(2);
            assertEquals(2, saved().armedDigit);
            clickText("Notes: off");
            assertTrue(saved().noteMode);
            assertEquals("Notes mode keeps note highlighting active", 2, saved().armedDigit);
            clickNumber(5);
            assertEquals(5, saved().armedDigit);
            clickNumber(5);
            assertEquals(0, saved().armedDigit);
            clickNumber(2);
            clickText("Sudoku");
            assertEquals("Outside taps clear note highlighting", 0, saved().armedDigit);
            assertTrue(Arrays.equals(notesBefore, saved().notes));
            finishActivity();
        }
    }

    private Bitmap renderBoard(Game state, MainActivity.Palette palette) {
        Bitmap bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888);
        getInstrumentation().runOnMainSync(() -> {
            BoardView preview = new BoardView(getInstrumentation().getTargetContext(), palette);
            preview.setGame(state);
            preview.layout(0, 0, 1080, 1080);
            preview.draw(new android.graphics.Canvas(bitmap));
        });
        return bitmap;
    }

    private boolean sameNoteSlot(Bitmap before, Bitmap after, int cell, int digit) {
        float density = getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density;
        float side = 1080 - 12 * density, size = side / 9, offset = (1080 - side) / 2;
        float x = offset + (cell % 9 + ((digit - 1) % 3 + .5f) / 3) * size;
        float y = offset + (cell / 9 + ((digit - 1) / 3 + .5f) / 3) * size;
        int radius = (int) (size * .15f);
        for (int px = (int) x - radius; px <= x + radius; px++)
            for (int py = (int) y - radius; py <= y + radius; py++)
                if (before.getPixel(px, py) != after.getPixel(px, py)) return false;
        return true;
    }

    public void testAppearanceChoicesAndButtonContrast() throws Throwable {
        Game fixture = focused();
        fixture.setAutoNotes(true);
        fixture.selected = firstEmpty(fixture);
        fixture.enter(fixture.solution[fixture.selected]);
        fixture.setNoteMode(true);
        fixture.elapsedMillis = 63000;
        write(fixture);
        assertTrue(settings().edit().putInt("color", 1).commit());
        launch();
        assertEquals("Existing installs default to System", 0, settings().getInt("appearance", 0));
        clickPrefix("Resume ");
        clickText("Settings");
        Game paused = saved();
        int systemNight = getInstrumentation().getTargetContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        for (boolean dark : new boolean[]{true, false}) {
            chooseAppearance(dark ? "Dark" : "Light");
            assertAppAppearance(dark);
            assertNotNull(findText("Auto notes: Easy"));
            assertNotNull(findNamed(activity.getWindow().getDecorView(), "Auto notes: Easy"));
            assertNotNull(findText("Auto notes: Hard"));
            assertNull(findText("Auto notes"));
            assertNull(findText("Done"));
            assertNavigationIcon("Close settings");
            assertStateEquals(paused, saved());
            assertEquals("Appearance changes do not advance the paused timer",
                    paused.elapsedMillis, saved().elapsedMillis);
            assertTrue("Undo survives recreation", saved().canUndo());
            assertEquals("Accent choice remains independent", 1, settings().getInt("color", -1));
            assertEquals("App overrides never change the OS", systemNight,
                    getInstrumentation().getTargetContext().getResources().getConfiguration().uiMode
                            & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
            capture("sudoku-appearance-" + (dark ? "dark" : "light") + "-settings.png");
            clickText("Close settings");
            assertNotNull(board());
            assertAppAppearance(dark);
            clickText("Sudoku");
            clickNumber(5);
            Game armed = saved();
            recreate();
            assertStateEquals(armed, saved());
            assertAppAppearance(dark);
            assertFlatButtons(activity.getWindow().getDecorView());
            assertNavigationIcon("Menu");
            assertNavigationIcon("Settings");
            capture("sudoku-appearance-" + (dark ? "dark" : "light") + "-game.png");
            clickText("Menu");
            assertAppAppearance(dark);
            assertNotNull(findText("Easy"));
            assertNotNull(findText("Hard"));
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Naked and hidden"));
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Requires multiple candidate"));
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Easy  \u2014"));
            assertNull(findPrefix(activity.getWindow().getDecorView(), "Hard  \u2014"));
            assertNull(findText("How to play"));
            assertNull(findText("Last result"));
            assertNavigationIcon("Settings");
            capture("sudoku-clean-menu-" + (dark ? "dark" : "light") + ".png");
            clickText("Settings");
            assertNavigationIcon("Close settings");
            clickText("Close settings");
            assertNotNull("Closing menu settings returns to menu", findText("Choose your difficulty"));
            clickText("Easy");
            assertNotNull(getInstrumentation().getUiAutomation().getRootInActiveWindow());
            capture("sudoku-appearance-" + (dark ? "dark" : "light") + "-dialog.png");
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            finishActivity();
            launch();
            assertAppAppearance(dark);
            assertTrue("Persisted history survives relaunch", saved().canUndo());
            clickPrefix("Resume "); clickText("Settings");
            paused = saved();
        }
        chooseAppearance("System");
        assertEquals(systemNight, activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        assertEquals(0, settings().getInt("appearance", -1));
        finishActivity();
        Game won = focused();
        System.arraycopy(won.solution, 0, won.values, 0, 81);
        int lastCell = firstEmpty(won);
        won.values[lastCell] = 0;
        for (boolean dark : new boolean[]{true, false}) {
            write(won);
            assertTrue(settings().edit().putInt("appearance", dark ? 2 : 1).commit());
            launch(); clickPrefix("Resume ");
            clickNumber(won.solution[lastCell]);
            selectCell(board(), lastCell);
            SystemClock.sleep(1900);
            assertAppAppearance(dark);
            assertNotNull(findText("Beautifully solved!"));
            capture("sudoku-appearance-" + (dark ? "dark" : "light") + "-win.png");
            finishActivity();
        }
        for (boolean dark : new boolean[]{true, false}) for (int color = 0; color < MainActivity.COLOR_NAMES.length; color++) {
            MainActivity.Palette palette = new MainActivity.Palette(dark, color);
            assertContrast("Button outlines against canvas", palette.accent, palette.background, 3);
            assertContrast("Button outlines against surface", palette.accent, palette.surface, 3);
            assertContrast("Primary labels and counts", palette.onPrimary, palette.primary, 4.5);
            assertContrast("Secondary labels and icons", palette.accent, palette.surface, 4.5);
            assertContrast("Armed digit labels", palette.ink, palette.tint, 4.5);
            assertContrast("Armed outlines", palette.accent, palette.tint, 3);
            assertContrast("Disabled labels/counts", palette.muted, palette.peer, 4.5);
            assertContrast("Disabled outlines", palette.line, palette.background, 3);
            assertContrast("Pressed secondary labels", palette.accent,
                    pressedSurface(palette.ink, palette.surface), 4.5);
            assertContrast("Pressed primary labels/counts", palette.onPrimary,
                    pressedSurface(palette.ink, palette.primary), 4.5);
            assertContrast("Pressed selected labels/counts", palette.ink,
                    pressedSurface(palette.ink, palette.tint), 4.5);
        }
    }

    public void testDarkWarmPaletteRefinement() throws Exception {
        String[] lightAccents = {"#9D2832", "#8A4300", "#705600", "#21642F",
                "#245B9F", "#9C2866", "#77458C", "#73513B"};
        String[] lightTints = {"#F6D6D9", "#F8E0C0", "#F5E9AE", "#D2EAD3",
                "#D1E4FF", "#F5D5E6", "#EDD8F4", "#EBDCCF"};
        String[] oldDarkAccents = {"#FFB9BD", "#FFC080", "#EFDC7B", "#A2D8A5",
                "#A9CBFF", "#F4B4D7", "#E1B8EF", "#DDBDA4"};
        String[] oldDarkTints = {"#572F34", "#583E26", "#4F4728", "#2D4D35",
                "#2B4565", "#563148", "#503359", "#4B3B30"};
        for (int color = 0; color < MainActivity.COLOR_NAMES.length; color++) {
            MainActivity.Palette light = new MainActivity.Palette(false, color);
            assertEquals(android.graphics.Color.parseColor(lightAccents[color]), light.accent);
            assertEquals(android.graphics.Color.parseColor(lightTints[color]), light.tint);
            assertEquals(color == 1 ? 0xFFF5A742 : color == 2 ? 0xFFF5D64A : light.accent, light.primary);
            assertEquals(color == 1 || color == 2 ? light.ink : light.background, light.onPrimary);
            MainActivity.Palette dark = new MainActivity.Palette(true, color);
            if (color != 0 && color != 1 && color != 7) {
                assertEquals(android.graphics.Color.parseColor(oldDarkAccents[color]), dark.accent);
                assertEquals(android.graphics.Color.parseColor(oldDarkTints[color]), dark.tint);
                assertEquals(dark.accent, dark.primary);
                assertEquals(dark.background, dark.onPrimary);
            } else {
                float[] hsv = new float[3];
                android.graphics.Color.colorToHSV(dark.primary, hsv);
                if (color == 1) {
                    assertEquals("Pumpkin orange stays bright, not burnt", 0xFFFF8500, dark.primary);
                    assertTrue("Pumpkin orange is vivid, not pastel", hsv[1] > .95f && hsv[2] > .95f);
                    assertEquals("Bright orange uses dark ink", dark.background, dark.onPrimary);
                } else {
                    assertTrue("Red and brown remain deep and saturated", hsv[1] > .6f && hsv[2] < .7f);
                    assertEquals(color == 0 ? 0xFFA72132 : 0xFF69401F, dark.primary);
                    assertEquals(color == 0 ? 0xFFFF9DA3 : 0xFFE3AF85, dark.accent);
                    assertEquals(color == 0 ? 0xFF57232E : 0xFF49321F, dark.tint);
                }
                assertContrast("Rich dark button text", dark.onPrimary, dark.primary, 4.5);
                assertContrast("Rich dark pressed text", dark.onPrimary, pressedSurface(dark.ink, dark.primary), 4.5);
                assertContrast("Rich note glyphs", dark.surface, dark.accent, 4.5);
            }
        }
        Game fixture = focused();
        fixture.setAutoNotes(true);
        fixture.elapsedMillis = 63000;
        for (int color : new int[]{0, 1, 7}) {
            write(fixture);
            assertTrue(settings().edit().putInt("appearance", 2).putInt("accentColor", color)
                    .putLong("bestTime.HARD", 290000).commit());
            launch(); clickPrefix("Resume ");
            clickNumber(2);
            capture("sudoku-rich-dark-" + MainActivity.COLOR_NAMES[color].toLowerCase(java.util.Locale.ROOT) + ".png");
            clickText("Settings");
            assertTrue(findNamed(activity.getWindow().getDecorView(), MainActivity.COLOR_NAMES[color]).isSelected());
            if (color == 1) {
                ImageButton swatch = (ImageButton) findNamed(activity.getWindow().getDecorView(), "Orange");
                android.graphics.drawable.LayerDrawable icon =
                        (android.graphics.drawable.LayerDrawable) swatch.getDrawable();
                android.graphics.drawable.GradientDrawable fill =
                        (android.graphics.drawable.GradientDrawable) icon.getDrawable(1);
                assertEquals(0xFFFF8500, fill.getColor().getDefaultColor());
                getInstrumentation().runOnMainSync(() -> swatch.requestRectangleOnScreen(
                        new Rect(0, 0, swatch.getWidth(), swatch.getHeight()), true));
                capture("sudoku-pumpkin-dark-settings.png");
            }
            assertEquals(color, settings().getInt("accentColor", -1));
            assertEquals(2, settings().getInt("appearance", -1));
            assertEquals(290000L, settings().getLong("bestTime.HARD", -1));
            assertTrue(Arrays.equals(fixture.values, saved().values));
            assertTrue(Arrays.equals(fixture.notes, saved().notes));
            recreate();
            assertTrue(findNamed(activity.getWindow().getDecorView(), MainActivity.COLOR_NAMES[color]).isSelected());
            finishActivity();
        }
    }

    public void testSwatchColorsStayStableAcrossSelections() throws Throwable {
        Game fixture = focused();
        fixture.setAutoNotes(true);
        fixture.elapsedMillis = 63000;
        for (boolean dark : new boolean[]{false, true}) {
            write(fixture);
            assertTrue(settings().edit().putInt("appearance", dark ? 2 : 1)
                    .putInt("accentColor", 3).putLong("bestTime.HARD", 290000).commit());
            launch(); clickPrefix("Resume "); clickText("Settings");
            Game paused = saved();
            assertStableSwatches(dark, 3);
            for (int from = 0; from < MainActivity.COLOR_NAMES.length; from++) {
                for (int to = 0; to < MainActivity.COLOR_NAMES.length; to++) {
                    selectSwatch(from);
                    assertStableSwatches(dark, from);
                    selectSwatch(to);
                    assertStableSwatches(dark, to);
                    assertStateEquals(paused, saved());
                    assertEquals(paused.elapsedMillis, saved().elapsedMillis);
                    assertEquals(dark ? 2 : 1, settings().getInt("appearance", -1));
                    assertEquals(290000L, settings().getLong("bestTime.HARD", -1));
                }
            }
            recreate();
            assertStableSwatches(dark, 7);
            ImageButton orange = (ImageButton) findNamed(activity.getWindow().getDecorView(), "Orange");
            getInstrumentation().runOnMainSync(() -> orange.requestRectangleOnScreen(
                    new Rect(0, 0, orange.getWidth(), orange.getHeight()), true));
            capture("sudoku-stable-swatches-" + (dark ? "dark" : "light") + ".png");
            finishActivity();
        }
    }

    private void selectSwatch(int color) throws Throwable {
        View option = findNamed(activity.getWindow().getDecorView(), MainActivity.COLOR_NAMES[color]);
        assertNotNull(option);
        runTestOnUiThread(() -> assertTrue(option.performClick()));
        getInstrumentation().waitForIdleSync();
        assertEquals(color, settings().getInt("accentColor", -1));
    }

    private void assertStableSwatches(boolean dark, int selected) throws Throwable {
        int[] expected = {0xFFC83B46, dark ? 0xFFFF8500 : 0xFFE68A22, 0xFFE1BE19, 0xFF368449,
                0xFF3675C3, 0xFFCE5B96, 0xFF965EB0, 0xFF926949};
        runTestOnUiThread(() -> {
            android.graphics.drawable.Drawable previousFill = null;
            for (int color = 0; color < MainActivity.COLOR_NAMES.length; color++) {
                ImageButton option = (ImageButton) findNamed(activity.getWindow().getDecorView(),
                        MainActivity.COLOR_NAMES[color]);
                assertNotNull(option);
                assertEquals(color == selected, option.isSelected());
                assertNull("Swatch artwork must not inherit an accent image tint", option.getImageTintList());
                android.graphics.drawable.LayerDrawable icon =
                        (android.graphics.drawable.LayerDrawable) option.getDrawable();
                android.graphics.drawable.GradientDrawable fill =
                        (android.graphics.drawable.GradientDrawable) icon.getDrawable(1);
                assertNotSame("Swatches have independent fill drawables", previousFill, fill);
                previousFill = fill;
                for (int[] state : new int[][]{
                        {}, {android.R.attr.state_selected}, {android.R.attr.state_pressed},
                        {-android.R.attr.state_enabled}}) {
                    assertEquals(MainActivity.COLOR_NAMES[color] + " fill is independent of accent and state",
                            expected[color], fill.getColor().getColorForState(state, 0));
                }
                int size = Math.round(40 * activity.getResources().getDisplayMetrics().density);
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Rect originalBounds = new Rect(icon.getBounds());
                try {
                    icon.setBounds(0, 0, size, size);
                    icon.draw(new android.graphics.Canvas(bitmap));
                    assertEquals(MainActivity.COLOR_NAMES[color] + " rendered center remains its own hue",
                            expected[color], bitmap.getPixel(size / 2, size / 2));
                } finally {
                    icon.setBounds(originalBounds);
                    bitmap.recycle();
                }
            }
        });
    }

    public void testColorChoicesPersistenceAndMigration() throws Exception {
        Game fixture = focused();
        fixture.setAutoNotes(true);
        fixture.toggleDigit(2);
        fixture.elapsedMillis = 63000;
        int[] mapped = {3, 4, 6};
        for (int legacy = 0; legacy < mapped.length; legacy++) {
            write(fixture);
            assertTrue(settings().edit().clear().putInt("color", legacy)
                    .putLong("bestTime.FOCUSED", 290000).commit());
            launch();
            assertEquals("Legacy palette maps to its corresponding hue", mapped[legacy],
                    settings().getInt("accentColor", -1));
            assertEquals(legacy, settings().getInt("color", -1));
            assertEquals(290000L, settings().getLong("bestTime.FOCUSED", -1));
            assertStateEquals(fixture, saved());
            finishActivity();
        }
        String[] names = {"Red", "Orange", "Yellow", "Green", "Blue", "Pink", "Purple", "Brown"};
        assertTrue(Arrays.equals(names, MainActivity.COLOR_NAMES));
        for (boolean dark : new boolean[]{false, true}) {
            write(fixture);
            assertTrue(settings().edit().putInt("appearance", dark ? 2 : 1).commit());
            launch();
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            clickPrefix("Resume ");
            clickText("Settings");
            Game paused = saved();
            for (int color = 0; color < names.length; color++) {
                for (int i = 0; i < names.length; i++) {
                    ImageButton option = (ImageButton) findNamed(activity.getWindow().getDecorView(), names[i]);
                    assertNotNull(option);
                    assertNull("Color names are accessible, not visible labels", findText(names[i]));
                    assertEquals(1000 + i, option.getId());
                    assertEquals(names[i], option.getTooltipText().toString());
                    ViewGroup row = (ViewGroup) option.getParent();
                    assertEquals("Four swatches per row", 4, row.getChildCount());
                    assertSame(option, row.getChildAt(i % 4));
                    assertTrue("Every color has an accessible 48dp target",
                            option.getHeight() >= 48 * activity.getResources().getDisplayMetrics().density);
                    assertTrue(option.getWidth() >= 48 * activity.getResources().getDisplayMetrics().density);
                }
                assertNull(findText("Forest"));
                assertNull(findText("Ocean"));
                assertNull(findText("Orchid"));
                clickText(names[color]);
                assertEquals(color, settings().getInt("accentColor", -1));
                for (int i = 0; i < names.length; i++) {
                    View option = findNamed(activity.getWindow().getDecorView(), names[i]);
                    assertEquals("Exactly one swatch is selected", color == i, option.isSelected());
                    android.view.accessibility.AccessibilityNodeInfo node = option.createAccessibilityNodeInfo();
                    assertEquals(color == i, node.isSelected());
                    node.recycle();
                }
                assertAppAppearance(dark);
                assertStateEquals(paused, saved());
                assertEquals(paused.elapsedMillis, saved().elapsedMillis);
                clickText("Close settings");
                clickNumber(2);
                assertEquals(2, saved().armedDigit);
                capture("sudoku-color-" + (dark ? "dark-" : "light-") + names[color].toLowerCase(java.util.Locale.ROOT) + ".png");
                clickText("Settings");
                paused = saved();
            }
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotate(orientation);
                for (String name : names) {
                    View option = findNamed(activity.getWindow().getDecorView(), name);
                    getInstrumentation().runOnMainSync(() -> option.requestRectangleOnScreen(
                            new Rect(0, 0, option.getWidth(), option.getHeight()), true));
                    getInstrumentation().waitForIdleSync();
                    Rect visible = new Rect();
                    assertTrue(name + " is reachable by scrolling", option.getGlobalVisibleRect(visible));
                    assertEquals("No clipped color labels or touch targets", option.getHeight(), visible.height());
                    assertEquals(option.getWidth(), visible.width());
                }
                capture("sudoku-color-picker-" + (dark ? "dark-" : "light-")
                        + (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? "landscape" : "portrait") + ".png");
            }
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            recreate();
            assertTrue(findNamed(activity.getWindow().getDecorView(), "Brown").isSelected());
            assertEquals("Selected palette does not remigrate", 7, settings().getInt("accentColor", -1));
            finishActivity(); launch();
            assertEquals("Palette persists through relaunch", 7, settings().getInt("accentColor", -1));
            assertEquals(290000L, settings().getLong("bestTime.FOCUSED", -1));
            assertStateEquals(paused, saved());
            finishActivity();
        }
    }

    private int pressedSurface(int ink, int surface) {
        float alpha = 48f / 255;
        return android.graphics.Color.rgb(
                Math.round(android.graphics.Color.red(ink) * alpha + android.graphics.Color.red(surface) * (1 - alpha)),
                Math.round(android.graphics.Color.green(ink) * alpha + android.graphics.Color.green(surface) * (1 - alpha)),
                Math.round(android.graphics.Color.blue(ink) * alpha + android.graphics.Color.blue(surface) * (1 - alpha)));
    }

    private void chooseAppearance(String name) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        clickText(name);
        Activity replacement = monitor.waitForActivityWithTimeout(5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull("Appearance switch recreates the native theme", replacement);
        activity = replacement;
        getInstrumentation().waitForIdleSync();
        assertTrue(((android.widget.RadioButton) findText(name)).isChecked());
    }

    private void assertAppAppearance(boolean dark) {
        assertEquals(dark ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Configuration.UI_MODE_NIGHT_NO,
                activity.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        android.util.TypedValue nativeTheme = new android.util.TypedValue();
        assertTrue(activity.getTheme().resolveAttribute(android.R.attr.windowLightStatusBar, nativeTheme, true));
        assertEquals("Native dialogs/system bars share the app theme", !dark, nativeTheme.data != 0);
        ViewGroup content = activity.findViewById(android.R.id.content);
        android.graphics.drawable.ColorDrawable background =
                (android.graphics.drawable.ColorDrawable) content.getChildAt(0).getBackground();
        assertEquals(new MainActivity.Palette(dark, 1).background, background.getColor());
    }

    private void assertContrast(String label, int foreground, int background, double minimum) {
        double a = android.graphics.Color.luminance(foreground), b = android.graphics.Color.luminance(background);
        double ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        assertTrue(label + ": " + ratio, ratio >= minimum);
    }

    public void testSystemThemeChangesPreserveGame() throws Exception {
        android.app.UiModeManager modes = getInstrumentation().getTargetContext()
                .getSystemService(android.app.UiModeManager.class);
        int originalMode = modes.getNightMode();
        Game fixture = focused();
        fixture.setAutoNotes(true);
        fixture.toggleDigit(5);
        fixture.elapsedMillis = 63000;
        write(fixture);
        assertTrue(settings().edit().putInt("color", 1).commit());
        launch(); clickPrefix("Resume ");
        try {
            for (String mode : new String[]{"no", "yes"}) {
                Game before = saved();
                Instrumentation.ActivityMonitor monitor = getInstrumentation()
                        .addMonitor(MainActivity.class.getName(), null, false);
                shell("cmd uimode night " + mode);
                Activity replacement = monitor.waitForActivityWithTimeout(1500);
                getInstrumentation().removeMonitor(monitor);
                if (replacement != null) activity = replacement;
                getInstrumentation().waitForIdleSync();
                int night = activity.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                int expected = mode.equals("yes") ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Configuration.UI_MODE_NIGHT_NO;
                if (night != expected) {
                    assertTrue("Only tolerate a system setting that refused the requested change",
                            modes.getNightMode() != (mode.equals("yes")
                                    ? android.app.UiModeManager.MODE_NIGHT_YES : android.app.UiModeManager.MODE_NIGHT_NO));
                    android.util.Log.w("SudokuTests", "Device locks system night mode; validating configured theme rendering instead");
                }
                assertThemeRendering(mode.equals("yes"));
                assertStateEquals(before, saved());
                assertEquals(1, settings().getInt("color", -1));
                assertTrue(saved().elapsedMillis >= before.elapsedMillis);
                assertNotNull(findNamed(activity.getWindow().getDecorView(), "Undo"));
                capture("sudoku-theme-" + mode + ".png");
            }
        } finally {
            finishActivity();
            shell("cmd uimode night " + (originalMode == android.app.UiModeManager.MODE_NIGHT_YES
                    ? "yes" : originalMode == android.app.UiModeManager.MODE_NIGHT_NO ? "no" : "auto"));
            SystemClock.sleep(500);
        }
    }

    private void assertThemeRendering(boolean dark) throws Exception {
        android.content.res.Configuration configuration = new android.content.res.Configuration(
                activity.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                | (dark ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                : android.content.res.Configuration.UI_MODE_NIGHT_NO);
        android.content.Context themed = new android.view.ContextThemeWrapper(
                activity.createConfigurationContext(configuration), R.style.AppTheme);
        MainActivity.Palette palette = new MainActivity.Palette(dark, 1);
        double ink = android.graphics.Color.luminance(palette.ink);
        double surface = android.graphics.Color.luminance(palette.surface);
        assertTrue("Board digits have readable contrast", (Math.max(ink, surface) + .05)
                / (Math.min(ink, surface) + .05) >= 4.5);
        double notes = android.graphics.Color.luminance(palette.muted);
        assertTrue("Notes have readable contrast", (Math.max(notes, surface) + .05)
                / (Math.min(notes, surface) + .05) >= 4.5);
        Bitmap bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888);
        Game state = saved();
        getInstrumentation().runOnMainSync(() -> {
            BoardView preview = new BoardView(themed, palette);
            preview.setGame(state);
            preview.layout(0, 0, 1080, 1080);
            preview.draw(new android.graphics.Canvas(bitmap));
        });
        File directory = getInstrumentation().getTargetContext().getExternalFilesDir(null);
        try (FileOutputStream stream = new FileOutputStream(new File(directory,
                dark ? "sudoku-dark-board.png" : "sudoku-light-board.png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }

    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor result = getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream input = new FileInputStream(result.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        }
    }

    private Game focused() { return new Game(Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(41000))); }

    private int firstEmpty(Game game) {
        for (int i = 0; i < 81; i++) if (game.clues[i] == 0) return i;
        throw new AssertionError("Fixture has no editable cell");
    }

    private File saveFile() { return new File(getInstrumentation().getTargetContext().getFilesDir(), "current-game.bin"); }
    private SharedPreferences settings() {
        return getInstrumentation().getTargetContext().getSharedPreferences("settings", 0);
    }

    private void write(Game game) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(saveFile()))) { game.write(out); }
    }

    private void launch() {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        getInstrumentation().waitForIdleSync();
    }

    private void finishActivity() {
        getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
        activity = null;
    }

    private void assertStateEquals(Game before, Game after) {
        assertTrue(Arrays.equals(before.clues, after.clues));
        assertTrue(Arrays.equals(before.values, after.values));
        assertTrue(Arrays.equals(before.notes, after.notes));
        assertEquals(before.autoNotes, after.autoNotes);
        assertEquals(before.showMistakes, after.showMistakes);
        assertEquals(before.noteMode, after.noteMode);
        assertEquals(before.selected, after.selected);
        assertEquals(before.armedDigit, after.armedDigit);
    }

    private void waitForGame() throws Exception {
        for (int i = 0; i < 150; i++) {
            getInstrumentation().waitForIdleSync();
            if (findType(activity.getWindow().getDecorView(), BoardView.class) != null) return;
            SystemClock.sleep(100);
        }
        fail("No generated game within 15 seconds");
    }

    private Game saved() throws Exception {
        getInstrumentation().waitForIdleSync();
        try (DataInputStream in = new DataInputStream(new FileInputStream(saveFile()))) { return Game.read(in); }
    }

    private BoardView board() {
        BoardView board = (BoardView) findType(activity.getWindow().getDecorView(), BoardView.class);
        assertNotNull(board); return board;
    }

    private void clickText(String text) {
        View view = findText(text);
        if (view == null) view = findNamed(activity.getWindow().getDecorView(), text);
        assertNotNull("Native control exists: " + text, view);
        tap(view);
    }

    private void clickPrefix(String prefix) {
        View view = findPrefix(activity.getWindow().getDecorView(), prefix);
        assertNotNull("Native control exists: " + prefix, view);
        tap(view);
    }

    private void clickNumber(int digit) throws Exception {
        View view = findText(digit + "\n" + saved().remaining(digit));
        assertNotNull(view); assertTrue("Digit available", view.isEnabled()); tap(view);
    }

    private void selectCell(BoardView board, int cell) throws Throwable {
        runTestOnUiThread(() -> board.getAccessibilityNodeProvider().performAction(cell, AccessibilityNodeInfo.ACTION_CLICK, null));
        getInstrumentation().waitForIdleSync();
    }

    private void tap(View view) {
        getInstrumentation().runOnMainSync(() ->
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        getInstrumentation().waitForIdleSync();
        int[] location = new int[2]; view.getLocationOnScreen(location);
        float x = location[0] + view.getWidth() / 2f, y = location[1] + view.getHeight() / 2f;
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(down); getInstrumentation().sendPointerSync(up);
        down.recycle(); up.recycle();
        getInstrumentation().waitForIdleSync();
    }

    private View findText(String text) { return findText(activity.getWindow().getDecorView(), text); }

    private static View findNamed(View view, String name) {
        if (name.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findNamed(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findPrefix(View view, String prefix) {
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findPrefix(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findType(View view, Class<? extends View> type) {
        if (type.isInstance(view)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findType(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void assertBankFits() throws Exception {
        Game g = saved();
        Rect screen = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);
        int previousRight = -1, bankTop = -1;
        for (int d = 1; d <= 9; d++) {
            View view = findText(d + "\n" + g.remaining(d));
            assertNotNull(view);
            Rect bounds = new Rect(); assertTrue(view.getGlobalVisibleRect(bounds));
            assertTrue("Bank entry inside screen", screen.contains(bounds));
            assertTrue("Nine entries have distinct space", bounds.left >= previousRight);
            assertTrue("Bank entry legible", bounds.width() >= 90 && bounds.height() >= 140);
            if (bankTop < 0) bankTop = bounds.top;
            assertEquals("One bank row", bankTop, bounds.top);
            previousRight = bounds.right;
        }
        assertTrue("Board legible in both orientations", Math.min(board().getWidth(), board().getHeight()) >= 760);
    }

    private void rotate(int orientation) throws Exception {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(orientation));
        Activity replacement = monitor.waitForActivityWithTimeout(5000);
        getInstrumentation().removeMonitor(monitor);
        if (replacement != null) activity = replacement;
        SystemClock.sleep(500); getInstrumentation().waitForIdleSync();
        int expected = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ? android.content.res.Configuration.ORIENTATION_LANDSCAPE : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        assertEquals(expected, activity.getResources().getConfiguration().orientation);
    }

    private void recreate() {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> activity.recreate());
        activity = monitor.waitForActivityWithTimeout(5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull("Recreated instance observed", activity);
        getInstrumentation().waitForIdleSync();
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(500);
        getInstrumentation().waitForIdleSync();
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File directory = getInstrumentation().getTargetContext().getExternalFilesDir(null);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        screenshot.recycle();
    }
}
