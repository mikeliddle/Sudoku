package com.quietgrid.sudoku;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/** A short, deterministic cascade of the nine digits and confetti. */
final class CelebrationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity.Palette palette;
    private final Runnable finished;
    private final float[] direction = new float[72], speed = new float[72], spin = new float[72];
    private ValueAnimator animator;
    private float progress;

    CelebrationView(Context context, MainActivity.Palette palette, Runnable finished) {
        super(context);
        this.palette = palette; this.finished = finished;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false); setFocusable(false);
        Random random = new Random(81);
        for (int i = 0; i < direction.length; i++) {
            direction[i] = (float) (Math.PI * (1.1 + random.nextFloat() * 0.8));
            speed[i] = 0.25f + random.nextFloat() * 0.65f;
            spin[i] = random.nextFloat() * 720 - 360;
        }
    }

    void start() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> { progress = (float) value.getAnimatedValue(); invalidate(); });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);
                finished.run();
            }
        });
        animator.start();
    }

    void stop() {
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        int[] colors = {palette.accent, palette.line, palette.ink};
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        for (int i = 0; i < direction.length; i++) {
            float t = Math.max(0, (progress - i % 9 * 0.015f) / 0.88f);
            float x = getWidth() / 2f + (float) Math.cos(direction[i]) * speed[i] * size * 2 * t;
            float y = getHeight() * 0.48f + (float) Math.sin(direction[i]) * speed[i] * size * 2 * t + size * 1.8f * t * t;
            paint.setColor(colors[i % colors.length]);
            paint.setAlpha(Math.round(255 * Math.max(0, Math.min(1, (1 - progress) * 4))));
            canvas.save();
            canvas.translate(x, y); canvas.rotate(spin[i] * t);
            if (i < 9) {
                paint.setTextSize(size * 0.08f);
                canvas.drawText(Integer.toString(i + 1), 0, 0, paint);
            } else {
                float width = size * 0.009f;
                canvas.drawRect(-width, -width * 2, width, width * 2, paint);
            }
            canvas.restore();
        }
    }
}
