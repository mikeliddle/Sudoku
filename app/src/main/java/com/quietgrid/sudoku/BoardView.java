package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityManager;

public final class BoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity.Palette palette;
    private Game game;
    private float left, top, cellSize;
    private Runnable selectionChanged, entryChanged;
    private int accessibilityFocus = -1, hovered = -1;
    private int rejectedCell = -1;
    private long rejectionStarted;
    private android.animation.ValueAnimator entryAnimator;
    private int animatedCell = -1;
    private float entryProgress;
    private final Runnable clearRejection = () -> { rejectedCell = -1; invalidate(); };

    int rejectedCell() { return rejectedCell; }
    int animatedCell() { return animatedCell; }

    public void clearEntryFeedback() {
        if (entryAnimator != null) { entryAnimator.cancel(); entryAnimator = null; }
        animatedCell = -1;
        invalidate();
    }

    private void acknowledgeEntry(int cell) {
        clearEntryFeedback();
        removeCallbacks(clearRejection);
        rejectedCell = -1;
        if (game.noteMode || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        animatedCell = cell;
        entryProgress = 0f;
        entryAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        entryAnimator.setDuration(240);
        entryAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        entryAnimator.addUpdateListener(animation -> {
            entryProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        entryAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                animatedCell = -1;
                invalidate();
            }
        });
        entryAnimator.start();
    }

    private void reject(int cell) {
        clearEntryFeedback();
        rejectedCell = cell;
        rejectionStarted = android.os.SystemClock.uptimeMillis();
        removeCallbacks(clearRejection);
        postDelayed(clearRejection, 550);
        invalidate();
        announceForAccessibility("Cannot place that number. It is already in this row, column, or box.");
    }

    private boolean conflictingEntry(int cell, int digit) {
        return cell >= 0 && digit != 0 && !game.noteMode && game.clues[cell] == 0
                && game.values[cell] != digit && game.conflicts(cell, digit);
    }

    public void chooseDigit(int digit) {
        if (game == null) return;
        clearEntryFeedback();
        int target = game.selected;
        boolean conflict = conflictingEntry(target, digit);
        boolean entered = game.chooseDigit(digit);
        if (conflict) reject(target);
        else if (entered) acknowledgeEntry(target);
        if (entered && entryChanged != null) entryChanged.run();
        else if (selectionChanged != null) selectionChanged.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(clearRejection);
        clearEntryFeedback();
        rejectedCell = -1;
        super.onDetachedFromWindow();
    }

    public BoardView(Context context, MainActivity.Palette palette) {
        super(context);
        this.palette = palette;
        setFocusable(true); setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public BoardView(Context context) {
        this(context, new MainActivity.Palette((context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES, 3));
    }

    public void setOnSelectionChanged(Runnable listener) { selectionChanged = listener; }
    public void setOnEntryChanged(Runnable listener) { entryChanged = listener; }
    public void setGame(Game game) {
        if (this.game != game) clearEntryFeedback();
        this.game = game;
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.max(0, Math.min(getWidth() - dp(4), getHeight() - dp(12)));
        left = (getWidth() - side) / 2f; top = (getHeight() - side) / 2f;
        cellSize = side / 9;
        if (cellSize <= 0) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.surface);
        canvas.drawRect(left, top, left + side, top + side, paint);
        int selected = game == null ? -1 : game.selected;
        int selectedValue = game == null ? 0 : game.armedDigit != 0
                ? game.armedDigit : selected < 0 ? 0 : game.values[selected];
        for (int i = 0; i < 81; i++) {
            float x = left + i % 9 * cellSize, y = top + i / 9 * cellSize;
            if (game == null) continue;
            int value = game.values[i];
            boolean wrong = game.showMistakes && value != 0 && value != game.solution[i];
            int fill = i == selected ? palette.tint : wrong ? palette.errorFill
                    : selectedValue != 0 && value == selectedValue ? palette.tint
                    : selected >= 0 && Sudoku.PEERS[selected][i] ? palette.peer : palette.surface;
            paint.setColor(fill);
            canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
            if (i == animatedCell) {
                paint.setColor((palette.accent & 0x00FFFFFF) | Math.round(75 * (1f - entryProgress)) << 24);
                canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
            }
            if (i == rejectedCell) {
                float fraction = Math.min(1f, (android.os.SystemClock.uptimeMillis() - rejectionStarted) / 550f);
                int alpha = android.animation.ValueAnimator.areAnimatorsEnabled()
                        ? Math.round(220 * (1f - fraction)) : 180;
                paint.setColor((palette.error & 0x00FFFFFF) | alpha << 24);
                canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
                if (fraction < 1f && android.animation.ValueAnimator.areAnimatorsEnabled()) postInvalidateOnAnimation();
            }
            if (value != 0) {
                paint.setTypeface(Typeface.create("sans-serif", game.clues[i] != 0 ? Typeface.BOLD : Typeface.NORMAL));
                paint.setTextSize(Math.min(sp(28), cellSize * 0.64f));
                paint.setColor(wrong ? palette.error : game.clues[i] != 0 ? palette.ink : palette.accent);
                paint.setTextAlign(Paint.Align.CENTER);
                float baseline = y + cellSize / 2 - (paint.ascent() + paint.descent()) / 2;
                canvas.save();
                if (i == animatedCell) {
                    float scale = 1f + .18f * (1f - entryProgress);
                    canvas.scale(scale, scale, x + cellSize / 2, y + cellSize / 2);
                }
                canvas.drawText(Integer.toString(value), x + cellSize / 2, baseline, paint);
                canvas.restore();
                if (wrong) {
                    paint.setStrokeWidth(dp(1.5f));
                    canvas.drawLine(x + cellSize * 0.33f, y + cellSize * 0.86f,
                            x + cellSize * 0.67f, y + cellSize * 0.86f, paint);
                }
            } else {
                int notes = game.visibleNotes(i);
                paint.setTextSize(Math.min(sp(11), cellSize * 0.25f));
                paint.setTextAlign(Paint.Align.CENTER);
                for (int d = 1; d <= 9; d++) if ((notes & Sudoku.bit(d)) != 0) {
                    boolean match = game.armedDigit == d;
                    paint.setTypeface(Typeface.create("sans-serif", match ? Typeface.BOLD : Typeface.NORMAL));
                    float nx = x + ((d - 1) % 3 + 0.5f) * cellSize / 3;
                    float ny = y + ((d - 1) / 3 + 0.5f) * cellSize / 3;
                    if (match) {
                        paint.setColor(palette.accent);
                        canvas.drawCircle(nx, ny, cellSize * 0.145f, paint);
                    }
                    paint.setColor(match ? palette.surface : palette.muted);
                    canvas.drawText(Integer.toString(d), nx, ny - (paint.ascent() + paint.descent()) / 2, paint);
                }
            }
        }
        for (int line = 0; line <= 9; line++) {
            paint.setColor(line % 3 == 0 ? palette.ink : palette.line);
            paint.setStrokeWidth(dp(line % 3 == 0 ? 1.7f : 0.5f));
            canvas.drawLine(left + line * cellSize, top, left + line * cellSize, top + side, paint);
            canvas.drawLine(left, top + line * cellSize, left + side, top + line * cellSize, paint);
        }
        if (selected >= 0) {
            paint.setStyle(Paint.Style.STROKE); paint.setColor(palette.accent); paint.setStrokeWidth(dp(2.5f));
            float x = left + selected % 9 * cellSize, y = top + selected / 9 * cellSize;
            canvas.drawRect(x + dp(1.5f), y + dp(1.5f), x + cellSize - dp(1.5f), y + cellSize - dp(1.5f), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private int cellAt(float x, float y) {
        if (cellSize <= 0 || x < left || y < top || x >= left + 9 * cellSize || y >= top + 9 * cellSize) return -1;
        return (int) ((y - top) / cellSize) * 9 + (int) ((x - left) / cellSize);
    }

    public boolean containsBoardPoint(float screenX, float screenY) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return cellAt(screenX - location[0], screenY - location[1]) >= 0;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int cell = cellAt(event.getX(), event.getY());
            if (game != null && cell >= 0) select(cell);
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void select(int cell) {
        if (game == null) return;
        clearEntryFeedback();
        boolean conflict = conflictingEntry(cell, game.armedDigit);
        boolean entered = game.selectCell(cell);
        if (conflict) reject(cell);
        else if (entered) acknowledgeEntry(cell);
        requestFocus();
        if (entered && entryChanged != null) entryChanged.run();
        else if (selectionChanged != null) selectionChanged.run();
        sendCellEvent(cell, game.selected == cell ? AccessibilityEvent.TYPE_VIEW_SELECTED
                : AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (game == null) return super.onKeyDown(code, event);
        int cell = game.selected < 0 ? 0 : game.selected;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) select(Math.max(0, cell - 1));
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) select(Math.min(80, cell + 1));
        else if (code == KeyEvent.KEYCODE_DPAD_UP) select(Math.max(0, cell - 9));
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) select(Math.min(80, cell + 9));
        else if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
            int digit = code - KeyEvent.KEYCODE_0;
            int target = game.selected;
            if (conflictingEntry(game.selected, digit)) reject(game.selected);
            if (game.enter(digit)) {
                acknowledgeEntry(target);
                if (entryChanged != null) entryChanged.run();
            }
        } else if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
            clearEntryFeedback();
            if (game.clear() && entryChanged != null) entryChanged.run();
        } else return super.onKeyDown(code, event);
        return true;
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        if (!manager.isTouchExplorationEnabled()) return super.dispatchHoverEvent(event);
        int cell = event.getAction() == MotionEvent.ACTION_HOVER_EXIT ? -1 : cellAt(event.getX(), event.getY());
        if (cell != hovered) {
            if (hovered >= 0) sendCellEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            hovered = cell;
            if (cell >= 0) sendCellEvent(cell, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
        return cell >= 0;
    }

    private CharSequence description(int cell) {
        if (game == null) return "Empty board";
        StringBuilder text = new StringBuilder("Row ").append(cell / 9 + 1)
                .append(", column ").append(cell % 9 + 1).append(", ");
        int value = game.values[cell];
        if (value == 0) {
            text.append("empty");
            int notes = game.visibleNotes(cell);
            if (notes != 0) {
                text.append(", notes");
                for (int d = 1; d <= 9; d++) if ((notes & Sudoku.bit(d)) != 0) text.append(" ").append(d);
            }
        } else {
            text.append(value);
            if (game.clues[cell] != 0) text.append(", fixed clue");
            if (game.showMistakes && value != game.solution[cell]) text.append(", mistake");
        }
        return text;
    }

    private void sendCellEvent(int cell, int type) {
        if (!getContext().getSystemService(AccessibilityManager.class).isEnabled()) return;
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setSource(this, cell);
        event.setClassName("android.view.View"); event.setPackageName(getContext().getPackageName());
        event.setContentDescription(description(cell)); event.setEnabled(true);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() {
            @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                if (id == HOST_VIEW_ID) {
                    AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(BoardView.this);
                    onInitializeAccessibilityNodeInfo(node);
                    node.setClassName("android.widget.GridView");
                    node.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(9, 9, false));
                    for (int i = 0; i < 81; i++) node.addChild(BoardView.this, i);
                    return node;
                }
                if (id < 0 || id >= 81) return null;
                AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
                node.setSource(BoardView.this, id); node.setParent(BoardView.this);
                node.setClassName("android.widget.Button"); node.setPackageName(getContext().getPackageName());
                node.setContentDescription(description(id));
                node.setEnabled(game != null); node.setVisibleToUser(isShown());
                node.setFocusable(true); node.setClickable(true);
                node.setSelected(game != null && game.selected == id);
                node.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(id / 9, 1, id % 9, 1, false));
                node.setAccessibilityFocused(accessibilityFocus == id);
                node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                node.addAction(accessibilityFocus == id
                        ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
                Rect bounds = new Rect(Math.round(left + id % 9 * cellSize), Math.round(top + id / 9 * cellSize),
                        Math.round(left + (id % 9 + 1) * cellSize), Math.round(top + (id / 9 + 1) * cellSize));
                node.setBoundsInParent(bounds);
                int[] location = new int[2]; getLocationOnScreen(location);
                bounds.offset(location[0], location[1]); node.setBoundsInScreen(bounds);
                return node;
            }

            @Override public boolean performAction(int id, int action, Bundle arguments) {
                if (id < 0 || id >= 81) return false;
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { select(id); return true; }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    if (accessibilityFocus >= 0) sendCellEvent(accessibilityFocus, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    accessibilityFocus = id;
                    sendCellEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                    accessibilityFocus = -1;
                    sendCellEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED); return true;
                }
                return false;
            }
        };
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
