package com.quietgrid.sudoku;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    static final String[] COLOR_NAMES = {"Red", "Orange", "Yellow", "Green", "Blue", "Pink", "Purple", "Brown"};
    private static final String[] COLOR_SWATCHES = {"#C83B46", "#E68A22", "#E1BE19", "#368449",
            "#3675C3", "#CE5B96", "#965EB0", "#926949"};
    private enum Screen { MENU, PLAY, SETTINGS, WIN }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Button[] bank = new Button[9];
    private Game game;
    private BoardView board;
    private TextView clock;
    private ImageButton undoButton, eraseButton;
    private Button notesButton;
    private Palette palette;
    private Screen screen = Screen.MENU, settingsReturn = Screen.MENU;
    private boolean resumed, loading, saveFailed;
    private Future<?> generation;
    private long startedAt;
    private AlertDialog progressDialog;
    private CelebrationView celebration;
    private int generationSerial;
    private Sudoku.Difficulty requestedDifficulty;
    private SharedPreferences preferences;

    static final class Palette {
        final int background, surface, ink, muted, accent, primary, onPrimary, tint, peer, line, error, errorFill;
        Palette(boolean dark, int color) {
            background = Color.parseColor(dark ? "#121A18" : "#F5F7F5");
            surface = Color.parseColor(dark ? "#1B2522" : "#FFFFFF");
            ink = Color.parseColor(dark ? "#E3EDE8" : "#192D28");
            muted = Color.parseColor(dark ? "#B2C4BB" : "#50665D");
            String[] accents = dark
                    ? new String[]{"#FF9DA3", "#FFA41A", "#EFDC7B", "#A2D8A5", "#A9CBFF", "#F4B4D7", "#E1B8EF", "#E3AF85"}
                    : new String[]{"#9D2832", "#8A4300", "#705600", "#21642F", "#245B9F", "#9C2866", "#77458C", "#73513B"};
            String[] tints = dark
                    ? new String[]{"#57232E", "#593009", "#4F4728", "#2D4D35", "#2B4565", "#563148", "#503359", "#49321F"}
                    : new String[]{"#F6D6D9", "#F8E0C0", "#F5E9AE", "#D2EAD3", "#D1E4FF", "#F5D5E6", "#EDD8F4", "#EBDCCF"};
            accent = Color.parseColor(accents[color]);
            boolean richDark = dark && (color == 0 || color == 7);
            primary = dark && color == 1 ? Color.parseColor("#FF8500")
                    : richDark ? Color.parseColor(color == 0 ? "#A72132" : "#69401F")
                    : !dark && color == 1 ? Color.parseColor("#F5A742")
                    : !dark && color == 2 ? Color.parseColor("#F5D64A") : accent;
            onPrimary = richDark ? Color.WHITE : !dark && (color == 1 || color == 2) ? ink : background;
            tint = Color.parseColor(tints[color]);
            peer = Color.parseColor(dark ? "#23352F" : "#EDF4EF");
            line = Color.parseColor(dark ? "#648076" : "#748D82");
            error = Color.parseColor(dark ? "#FFB4AB" : "#A62C32");
            errorFill = Color.parseColor(dark ? "#5E2929" : "#FCE4E3");
        }
    }

    @Override protected void attachBaseContext(Context base) {
        int appearance = base.getSharedPreferences("settings", MODE_PRIVATE).getInt("appearance", 0);
        if (appearance == 1 || appearance == 2) {
            Configuration override = new Configuration();
            override.uiMode = (base.getResources().getConfiguration().uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (appearance == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            base = base.createConfigurationContext(override);
        }
        super.attachBaseContext(base);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        migrateColor();
        String restoreError = restore();
        migrateAutoNotes();
        recordBestSolve();
        if (state != null) {
            screen = Screen.valueOf(state.getString("screen", Screen.MENU.name()));
            settingsReturn = Screen.valueOf(state.getString("settingsReturn", Screen.MENU.name()));
        }
        if (screen == Screen.PLAY && (game == null || game.won())) screen = Screen.MENU;
        if (screen == Screen.WIN && (game == null || !game.won())) screen = Screen.MENU;
        render(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }
        if (state != null && state.containsKey("generating")) {
            generate(Sudoku.Difficulty.valueOf(state.getString("generating")));
        }
        if (restoreError != null) {
            new AlertDialog.Builder(this).setTitle("Saved game could not be opened")
                    .setMessage(restoreError + "\nChoose a difficulty to create a fresh puzzle.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void updatePalette() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int color = preferences.getInt("accentColor", 3);
        palette = new Palette(dark, color >= 0 && color < COLOR_NAMES.length ? color : 3);
    }

    private LinearLayout root(boolean horizontal) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft()); top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight()); bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            view.setPadding(left + dp(horizontal ? 12 : 8), top + dp(4),
                    right + dp(horizontal ? 12 : 8), bottom + dp(4));
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        return root;
    }

    private void render(boolean animateWin) {
        if (celebration != null) { celebration.stop(); celebration = null; }
        board = null; clock = null;
        Arrays.fill(bank, null);
        updatePalette();
        if (screen == Screen.PLAY) buildGame();
        else if (screen == Screen.SETTINGS) buildSettings();
        else if (screen == Screen.WIN) buildWin(animateWin);
        else buildMenu();
    }

    private LinearLayout scrollContent(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(16));
        content.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        return content;
    }

    private void buildMenu() {
        LinearLayout content = scrollContent(root(false));
        LinearLayout heading = row();
        TextView title = text("Sudoku", 34, palette.ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(64), 1));
        ImageButton settings = navigationButton("Settings", R.drawable.ic_settings);
        settings.setOnClickListener(v -> openSettings());
        heading.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(heading);
        if (game != null && !game.won()) {
            Button resume = button("Resume " + game.difficulty.label.toLowerCase(Locale.ROOT)
                    + "  \u00b7  " + time(game.elapsedMillis), true);
            resume.setOnClickListener(v -> navigate(Screen.PLAY));
            addControl(content, resume, 60, 16);
        }
        addCopy(content, "Choose your difficulty", 22, palette.ink, 12);
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            Button start = button(difficulty.label, false);
            long best = preferences.getLong(bestTimeKey(difficulty), -1);
            if (best >= 0) {
                android.text.SpannableString label = new android.text.SpannableString(
                        difficulty.label + "    Best " + time(best));
                label.setSpan(new android.text.style.ForegroundColorSpan(palette.muted),
                        difficulty.label.length(), label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start.setText(label);
                start.setContentDescription(difficulty.label + ". Best solve time: " + time(best));
            }
            start.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            start.setPadding(dp(16), 0, dp(16), 0);
            start.setOnClickListener(v -> requestNewGame(difficulty));
            addControl(content, start, 56, 12);
        }
        if (saveFailed) addCopy(content, getString(R.string.save_error), 14, palette.error, 0);
    }

    private void buildGame() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = root(landscape);
        board = new BoardView(this, palette);
        board.setOnSelectionChanged(this::changed);
        board.setOnEntryChanged(this::changed);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        if (landscape) {
            root.addView(board, new LinearLayout.LayoutParams(0, -1, 0.46f));
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -1, 0.54f);
            right.leftMargin = dp(16);
            root.addView(panel, right);
        } else root.addView(panel, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout header = row();
        ImageButton menu = navigationButton("Menu", R.drawable.ic_menu);
        menu.setOnClickListener(v -> navigate(Screen.MENU));
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Sudoku", 25, palette.ink);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        ImageButton settings = navigationButton("Settings", R.drawable.ic_settings);
        settings.setOnClickListener(v -> openSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);
        LinearLayout details = row();
        clock = text("", 14, palette.muted);
        clock.setGravity(Gravity.CENTER);
        details.addView(clock, new LinearLayout.LayoutParams(-1, dp(30)));
        panel.addView(details);
        if (!landscape) panel.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tools = row();
        undoButton = iconButton("Undo", R.drawable.ic_undo);
        undoButton.setOnClickListener(v -> {
            board.clearEntryFeedback();
            if (game.undo()) {
                persistPreference(autoNotesKey(game.difficulty), game.autoNotes);
                changed();
                announce("Move undone");
            }
        });
        eraseButton = iconButton("Erase", R.drawable.ic_erase);
        eraseButton.setOnClickListener(v -> { board.clearEntryFeedback(); if (game.clear()) changed(); });
        notesButton = button("Notes", false);
        notesButton.setOnClickListener(v -> {
            board.clearEntryFeedback();
            game.setNoteMode(!game.noteMode);
            changed();
            announce(game.noteMode ? "Notes mode" : "Number entry mode");
        });
        for (View tool : new View[]{undoButton, eraseButton, notesButton}) {
            tool.setPadding(dp(16), dp(16), dp(16), dp(16));
        }
        tools.addView(undoButton, weighted(56));
        tools.addView(eraseButton, weighted(56));
        LinearLayout.LayoutParams notesLayout = weighted(56);
        notesLayout.weight = 1.4f;
        tools.addView(notesButton, notesLayout);
        controls.addView(tools);
        if (landscape) {
            ScrollView scroll = new ScrollView(this);
            scroll.addView(controls);
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        } else panel.addView(controls);

        LinearLayout bankRow = row();
        for (int d = 1; d <= 9; d++) {
            final int digit = d;
            Button number = button(Integer.toString(d), true);
            number.setTextSize(22);
            number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            number.setPadding(0, 0, 0, 0);
            number.setOnClickListener(v -> board.chooseDigit(digit));
            bank[d - 1] = number;
            LinearLayout.LayoutParams lp = weighted(64);
            lp.setMargins(dp(1), dp(2), dp(1), dp(2));
            bankRow.addView(number, lp);
        }
        panel.addView(bankRow);
        refreshGame();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        boolean outside = false;
        if (screen == Screen.PLAY && game != null && board != null
                && !board.containsBoardPoint(event.getRawX(), event.getRawY())) {
            boolean bankTap = false;
            for (Button button : bank) bankTap |= hit(button, event);
            boolean inputControl = hit(notesButton, event) || bankTap;
            boolean disabledBankTap = false;
            for (Button button : bank) disabledBankTap |= hit(button, event) && !button.isEnabled();
            if (disabledBankTap && event.getAction() == MotionEvent.ACTION_DOWN && game.selected != -1) {
                game.selected = -1;
                changed();
            }
            outside = !inputControl;
            if (outside && event.getAction() == MotionEvent.ACTION_DOWN && game.armedDigit != 0) {
                game.armedDigit = 0;
                changed();
            }
            if (outside && event.getAction() == MotionEvent.ACTION_DOWN) board.clearEntryFeedback();
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (outside && event.getAction() == MotionEvent.ACTION_UP) {
            Game tappedGame = game;
            // Button clicks are posted by View; let Erase/Undo use their target first.
            handler.post(() -> {
                if (screen == Screen.PLAY && game == tappedGame && game.selected != -1) {
                    game.selected = -1;
                    changed();
                }
            });
        }
        return handled;
    }

    private boolean hit(View view, MotionEvent event) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        return view != null && view.getGlobalVisibleRect(bounds)
                && bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private void changed() {
        updateElapsed();
        game.normalizeArmedDigit();
        save();
        if (screen == Screen.PLAY && game.won()) {
            recordBestSolve();
            screen = Screen.WIN;
            render(true);
        } else if (screen == Screen.PLAY) refreshGame();
    }

    private void refreshGame() {
        board.setGame(game);
        undoButton.setEnabled(game.canUndo() && !loading);
        boolean editable = game.selected >= 0 && game.clues[game.selected] == 0 && !game.won();
        eraseButton.setEnabled(editable && (game.values[game.selected] != 0 || game.notes[game.selected] != 0));
        notesButton.setText(game.noteMode ? "Notes: on" : "Notes: off");
        notesButton.setSelected(game.noteMode);
        notesButton.setTextColor(game.noteMode ? palette.ink : palette.accent);
        GradientDrawable notesFill = buttonSurface(game.noteMode ? palette.tint : palette.surface, dp(12));
        if (game.noteMode) notesFill.setStroke(dp(2), palette.accent);
        notesButton.setBackground(new android.graphics.drawable.RippleDrawable(
                rippleColor(),
                notesFill, null));
        for (int d = 1; d <= 9; d++) {
            Button number = bank[d - 1];
            boolean available = game.remaining(d) > 0;
            android.text.SpannableString label = new android.text.SpannableString(d + "\n" + game.remaining(d));
            label.setSpan(new android.text.style.AbsoluteSizeSpan(11, true), 2, label.length(), 0);
            number.setText(label);
            boolean armed = game.armedDigit == d;
            number.setSelected(armed);
            number.setContentDescription(d + ", " + game.remaining(d) + " remaining"
                    + (!available ? ", complete. Unavailable."
                    : editable ? game.noteMode ? ". Tap to toggle this note in the selected square."
                            : ". Tap to enter in the selected square."
                    : armed ? ", selected. Tap again to cancel." : ". Tap to select."));
            number.setTextColor(!available ? palette.muted : armed ? palette.ink : palette.onPrimary);
            GradientDrawable fill = background(!available ? palette.peer : armed ? palette.tint : palette.primary, dp(10));
            if (armed) fill.setStroke(dp(2), palette.accent);
            else if (!available) fill.setStroke(dp(1), palette.line);
            else fill.setStroke(dp(1), palette.accent);
            number.setBackground(new android.graphics.drawable.RippleDrawable(
                    rippleColor(), fill, null));
            number.setEnabled(available && !loading && !game.won());
        }
        updateClock();
    }

    private void openSettings() {
        settingsReturn = screen;
        navigate(Screen.SETTINGS);
    }

    private void buildSettings() {
        LinearLayout content = scrollContent(root(false));
        LinearLayout heading = row();
        TextView title = text("Settings", 28, palette.ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        ImageButton done = navigationButton("Close settings", R.drawable.ic_close);
        done.setOnClickListener(v -> navigate(settingsReturn));
        heading.addView(done, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(heading);

        addCopy(content, "Appearance", 22, palette.ink, 8);
        RadioGroup appearance = new RadioGroup(this);
        appearance.setOrientation(RadioGroup.HORIZONTAL);
        String[] appearances = {"System", "Light", "Dark"};
        for (int i = 0; i < appearances.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(1100 + i);
            radio.setText(appearances[i]); radio.setTextSize(16); radio.setTextColor(palette.ink);
            radio.setButtonTintList(android.content.res.ColorStateList.valueOf(palette.accent));
            appearance.addView(radio, new RadioGroup.LayoutParams(0, dp(48), 1));
        }
        appearance.check(1100 + preferences.getInt("appearance", 0));
        appearance.setOnCheckedChangeListener((group, checked) -> {
            if (!preferences.edit().putInt("appearance", checked - 1100).commit()) {
                settingsSaveError();
                return;
            }
            updateElapsed();
            save();
            recreate();
        });
        content.addView(appearance);
        addCopy(content, "System follows your device. Light and Dark apply only to Sudoku.", 14, palette.muted, 16);
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            boolean current = game != null && game.difficulty == difficulty;
            Switch auto = toggle("Auto notes: " + difficulty.label,
                    current ? game.autoNotes : autoNotes(difficulty), (view, enabled) -> {
                        if (game != null && game.difficulty == difficulty) { game.setAutoNotes(enabled); save(); }
                        persistPreference(autoNotesKey(difficulty), enabled);
                    });
            auto.setContentDescription("Auto notes: " + difficulty.label);
            addControl(content, auto, 56, 0);
        }
        addCopy(content, "Turn on to reset notes to legal candidates. Turn off and on again to reset manual edits. Blocked notes return when their blockers are removed.", 14, palette.muted, 16);
        Switch mistakes = toggle("Show mistakes", game != null ? game.showMistakes : preferences.getBoolean("showMistakes", false),
                (view, enabled) -> {
                    if (game != null) { game.showMistakes = enabled; save(); }
                    persistPreference("showMistakes", enabled);
                });
        addControl(content, mistakes, 56, 0);
        addCopy(content, "Mark entries that differ from the unique solution. Off by default.", 14, palette.muted, 20);
        addCopy(content, "Colors", 22, palette.ink, 8);
        int current = preferences.getInt("accentColor", 3);
        for (int row = 0; row < 2; row++) {
            LinearLayout colors = row();
            for (int column = 0; column < 4; column++) {
                int color = row * 4 + column;
                ImageButton swatch = new ImageButton(this);
                flatButton(swatch);
                swatch.setId(1000 + color);
                swatch.setContentDescription(COLOR_NAMES[color]);
                swatch.setTooltipText(COLOR_NAMES[color]);
                swatch.setSelected(color == current);
                swatch.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setSize(dp(40), dp(40));
                ring.setColor(Color.TRANSPARENT);
                if (color == current) ring.setStroke(dp(3), palette.ink);
                GradientDrawable fill = new GradientDrawable();
                fill.setShape(GradientDrawable.OVAL);
                fill.setSize(dp(30), dp(30));
                boolean darkOrange = color == 1 && (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                fill.setColor(darkOrange ? new Palette(true, color).primary : Color.parseColor(COLOR_SWATCHES[color]));
                fill.setStroke(dp(1), palette.muted);
                android.graphics.drawable.LayerDrawable icon = new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{ring, fill});
                icon.setLayerInset(1, dp(5), dp(5), dp(5), dp(5));
                swatch.setImageDrawable(icon);
                swatch.setBackground(new android.graphics.drawable.RippleDrawable(
                        rippleColor(), null, background(Color.WHITE, dp(28))));
                swatch.setOnClickListener(v -> {
                    if (!preferences.edit().putInt("accentColor", color).commit()) settingsSaveError();
                    render(false);
                });
                LinearLayout.LayoutParams slot = weighted(56);
                slot.setMargins(dp(4), 0, dp(4), 0);
                colors.addView(swatch, slot);
            }
            addControl(content, colors, 56, 8);
        }
        addCopy(content, "Your appearance and color choices apply to all games.", 14, palette.muted, 16);
        addCopy(content, getString(R.string.ai_disclosure), 14, palette.muted, 0);
    }

    private void persistPreference(String key, boolean value) {
        if (!preferences.edit().putBoolean(key, value).commit()) settingsSaveError();
    }

    private void migrateColor() {
        if (preferences.contains("accentColor")) return;
        int legacy = preferences.getInt("color", 0);
        int color = legacy == 1 ? 4 : legacy == 2 ? 6 : 3;
        if (!preferences.edit().putInt("accentColor", color).commit()) settingsSaveError();
    }

    private static String autoNotesKey(Sudoku.Difficulty difficulty) { return "autoNotes." + difficulty.name(); }

    private static String bestTimeKey(Sudoku.Difficulty difficulty) { return "bestTime." + difficulty.name(); }

    private void recordBestSolve() {
        if (game == null || !game.won() || game.elapsedMillis < 0) return;
        String key = bestTimeKey(game.difficulty);
        long best = preferences.getLong(key, -1);
        if ((best < 0 || game.elapsedMillis < best)
                && !preferences.edit().putLong(key, game.elapsedMillis).commit()) settingsSaveError();
    }

    private boolean autoNotes(Sudoku.Difficulty difficulty) {
        return preferences.getBoolean(autoNotesKey(difficulty), false);
    }

    private void migrateAutoNotes() {
        SharedPreferences.Editor editor = preferences.edit();
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            String key = autoNotesKey(difficulty);
            if (!preferences.contains(key)) editor.putBoolean(key,
                    game != null && game.difficulty == difficulty ? game.autoNotes
                            : preferences.getBoolean("autoNotes", false));
        }
        if (!editor.commit()) settingsSaveError();
        if (game != null && game.autoNotes != autoNotes(game.difficulty)) {
            game.setAutoNotes(autoNotes(game.difficulty));
            save();
        }
    }

    private void settingsSaveError() {
        Log.e("Sudoku", "Could not save preferences");
        new AlertDialog.Builder(this).setTitle("Settings could not be saved")
                .setMessage("Check available device storage. Your change may not survive closing the app.")
                .setPositiveButton("OK", null).show();
    }

    private void buildWin(boolean animate) {
        LinearLayout base = root(false);
        FrameLayout stage = new FrameLayout(this);
        base.addView(stage, new LinearLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        stage.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(20), dp(24), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        TextView title = text("Beautifully solved!", 32, palette.ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(64)));
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL); result.setGravity(Gravity.CENTER);
        TextView details = text(game.difficulty.label + "  \u00b7  " + time(game.elapsedMillis), 24, palette.accent);
        details.setGravity(Gravity.CENTER);
        result.addView(details, new LinearLayout.LayoutParams(-1, dp(64)));
        Button next = button("Done", true);
        next.setOnClickListener(v -> navigate(Screen.MENU));
        addControl(result, next, 56, 0);
        content.addView(result, new LinearLayout.LayoutParams(-1, -2));
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            result.setAlpha(0f);
            celebration = new CelebrationView(this, palette, () -> result.animate().alpha(1f).setDuration(250).start());
            stage.addView(celebration, new FrameLayout.LayoutParams(-1, -1));
            celebration.start();
        }
        title.announceForAccessibility("Puzzle solved. " + game.difficulty.label + ". Time " + time(game.elapsedMillis));
    }

    private void requestNewGame(Sudoku.Difficulty difficulty) {
        if (loading) return;
        if (game != null && !game.won()) {
            new AlertDialog.Builder(this).setTitle("Replace this game?")
                    .setMessage("Your current game and undo history will be replaced by a new "
                            + difficulty.label.toLowerCase(Locale.ROOT) + " puzzle.")
                    .setNegativeButton("Keep game", null)
                    .setPositiveButton("Start new game", (dialog, which) -> generate(difficulty)).show();
        } else generate(difficulty);
    }

    private void navigate(Screen destination) {
        updateElapsed();
        if (game != null && destination != Screen.PLAY && destination != Screen.WIN) game.armedDigit = 0;
        save();
        screen = destination;
        startedAt = SystemClock.elapsedRealtime();
        render(false);
    }

    private void navigateBack() {
        if (screen == Screen.SETTINGS) navigate(settingsReturn);
        else if (screen == Screen.PLAY || screen == Screen.WIN) navigate(Screen.MENU);
        else finish();
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 30-32 fallback; API 33+ uses the dispatcher above.
    @Override public void onBackPressed() { navigateBack(); }

    private void generate(Sudoku.Difficulty difficulty) {
        updateElapsed();
        loading = true;
        requestedDifficulty = difficulty;
        final int request = ++generationSerial;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        content.addView(new ProgressBar(this), new LinearLayout.LayoutParams(-1, dp(48)));
        TextView explanation = text("Generating a unique puzzle and checking its logical difficulty. Hard puzzles can take a little longer.", 15, palette.ink);
        explanation.setPadding(0, dp(16), 0, dp(8));
        content.addView(explanation);
        progressDialog = new AlertDialog.Builder(this).setTitle("Creating " + difficulty.label.toLowerCase(Locale.ROOT))
                .setView(content).setNegativeButton("Cancel", (dialog, which) -> {
                    if (generation != null) generation.cancel(true);
                    generationSerial++; loading = false;
                    startedAt = SystemClock.elapsedRealtime();
                }).setCancelable(false).create();
        progressDialog.show();
        generation = worker.submit(() -> {
            try {
                Sudoku.Puzzle puzzle = Sudoku.generate(difficulty, new SecureRandom());
                handler.post(() -> {
                    if (isDestroyed() || !loading || request != generationSerial) return;
                    progressDialog.dismiss();
                    game = new Game(puzzle);
                    if (autoNotes(difficulty)) game.setAutoNotes(true);
                    game.showMistakes = preferences.getBoolean("showMistakes", false);
                    loading = false;
                    screen = Screen.PLAY;
                    startedAt = SystemClock.elapsedRealtime();
                    save(); render(false);
                });
            } catch (CancellationException ex) {
                Log.i("Sudoku", "Puzzle generation cancelled");
            } catch (IllegalStateException ex) {
                Log.e("Sudoku", "Unable to certify puzzle", ex);
                handler.post(() -> {
                    if (isDestroyed() || !loading || request != generationSerial) return;
                    loading = false; progressDialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("No puzzle created").setMessage(ex.getMessage())
                            .setPositiveButton("Try again", (dialog, which) -> generate(difficulty))
                            .setNegativeButton("Back to menu", null).show();
                });
            }
        });
    }

    private AtomicFile savedGame() { return new AtomicFile(new File(getFilesDir(), "current-game.bin")); }

    private void save() {
        if (game == null) return;
        AtomicFile file = savedGame();
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream out = new DataOutputStream(stream);
            game.write(out); out.flush(); file.finishWrite(stream);
            saveFailed = false;
        } catch (IOException ex) {
            file.failWrite(stream);
            Log.e("Sudoku", "Could not save game", ex);
            saveFailed = true;
            new AlertDialog.Builder(this).setTitle("Could not save game")
                    .setMessage(R.string.save_error).setPositiveButton("OK", null).show();
        }
    }

    private String restore() {
        AtomicFile file = savedGame();
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
        try (DataInputStream in = new DataInputStream(file.openRead())) {
            game = Game.read(in);
            return null;
        } catch (IOException ex) {
            Log.e("Sudoku", "Could not restore saved game", ex);
            return "The saved file is unreadable or damaged.";
        }
    }

    private void updateElapsed() {
        long now = SystemClock.elapsedRealtime();
        if (game != null && resumed && screen == Screen.PLAY && !loading) {
            game.elapsedMillis += Math.max(0, now - startedAt);
        }
        startedAt = now;
    }

    private static String time(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private void updateClock() {
        if (game != null && clock != null) {
            clock.setText(game.difficulty.label + " " + time(game.elapsedMillis));
            clock.setContentDescription(game.difficulty.label + ". Time played: " + game.elapsedMillis / 60000 + " minutes "
                    + game.elapsedMillis / 1000 % 60 + " seconds");
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateElapsed(); updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        resumed = true; startedAt = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    @Override protected void onPause() {
        updateElapsed(); resumed = false;
        handler.removeCallbacks(ticker);
        save();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        updateElapsed(); save();
        out.putString("screen", screen.name());
        out.putString("settingsReturn", settingsReturn.name());
        if (loading && requestedDifficulty != null) out.putString("generating", requestedDifficulty.name());
    }

    @Override protected void onDestroy() {
        if (generation != null) generation.cancel(true);
        worker.shutdownNow();
        if (progressDialog != null) progressDialog.dismiss();
        if (celebration != null) celebration.stop();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weighted(int height) {
        return new LinearLayout.LayoutParams(0, dp(height), 1);
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private void addCopy(LinearLayout parent, String copy, int size, int color, int bottom) {
        TextView view = text(copy, size, color);
        view.setPadding(0, dp(4), 0, dp(bottom));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addControl(LinearLayout parent, View view, int height, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(height));
        lp.bottomMargin = dp(bottom);
        parent.addView(view, lp);
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        flatButton(button);
        button.setText(value); button.setTextSize(14); button.setAllCaps(false);
        button.setTextColor(primary ? palette.onPrimary : palette.accent);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                rippleColor(),
                buttonSurface(primary ? palette.primary : palette.surface, dp(primary ? 10 : 12)), null));
        return button;
    }

    private ImageButton iconButton(String name, int icon) {
        ImageButton button = new ImageButton(this);
        flatButton(button);
        button.setImageResource(icon);
        button.setContentDescription(name);
        button.setTooltipText(name);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setImageTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{palette.muted, palette.accent}));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                rippleColor(),
                buttonSurface(palette.surface, dp(12)), null));
        return button;
    }

    private ImageButton navigationButton(String name, int icon) {
        ImageButton button = iconButton(name, icon);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                rippleColor(), null, background(Color.WHITE, dp(24))));
        return button;
    }

    private void flatButton(View button) {
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
    }

    private Switch toggle(String label, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        Switch toggle = new Switch(this);
        toggle.setText(label); toggle.setTextSize(16); toggle.setTextColor(palette.ink);
        toggle.setPadding(dp(4), 0, dp(8), 0);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        return toggle;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable buttonSurface(int color, int radius) {
        GradientDrawable drawable = background(color, radius);
        drawable.setStroke(dp(1), new android.content.res.ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{palette.line, palette.accent}));
        return drawable;
    }

    private android.content.res.ColorStateList rippleColor() {
        return android.content.res.ColorStateList.valueOf((palette.ink & 0x00FFFFFF) | 0x30000000);
    }

    private void announce(String value) {
        if (board != null) board.announceForAccessibility(value);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
