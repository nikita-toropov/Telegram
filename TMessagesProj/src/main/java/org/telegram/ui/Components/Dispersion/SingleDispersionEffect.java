package org.telegram.ui.Components.Dispersion;

import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.multiplyAlphaComponent;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.SparseArray;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

import java.util.Arrays;

final class SingleDispersionEffect implements DispersionEffect {

    private static final boolean DEBUG = false;

    @Nullable
    private static Paint debugPaint;

    private Paint getDebugPaint() {
        if (debugPaint == null) {
            debugPaint = new Paint();
            debugPaint.setAntiAlias(false);
            debugPaint.setStyle(Paint.Style.STROKE);
            debugPaint.setColor(0xFF3D7FFF);
        }
        return debugPaint;
    }

    public static class Particles {
        int pointCount = 0;
        float[] points = new float[0];
        volatile float[] delta;

        public void append(float x, float y) {
            if (pointCount * 2 == points.length) {
                if (points.length == 0) {
                    points = new float[10];
                } else {
                    points = Arrays.copyOf(points, points.length * 2);
                }
            }
            points[pointCount * 2] = x;
            points[pointCount * 2 + 1] = y;
            pointCount++;
        }

        public void prepareToDraw() {
            if (this.delta == null) {
                float[] delta = new float[pointCount * 2];
                for (int index = 0; index < pointCount; index++) {
                    double direction = Math.random() * Math.PI * 2.0;
                    double velocity = 0.2 * (1.0 + Math.random()) * AndroidUtilities.density;
                    int x = index * 2;
                    int y = x + 1;
                    delta[x] = (float) (Math.cos(direction) * velocity);
                    delta[y] = (float) (Math.sin(direction) * velocity);
                    points[x] += delta[x];
                    points[y] += delta[y];
                }
                this.delta = delta;
            }
        }
    }

    private final SparseArray<Particles> particlesByColor;
    private final Rect bounds;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] pointCountToDraw;
    private final float strokeWidth;

    @FloatRange(from = 0.0, to = 1.0)
    private float progress = 0f;

    {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    SingleDispersionEffect(Rect bounds, SparseArray<Particles> particlesByColor, float scaleFactor) {
        this.bounds = bounds;
        this.particlesByColor = particlesByColor;
        this.pointCountToDraw = new int[particlesByColor.size()];
        this.strokeWidth = AndroidUtilities.density * 1.5f * MathUtils.clamp(0.5f / scaleFactor, 1f, 1.15f);
    }

    @Override
    public void setProgress(float progress) {
        moveToState(MathUtils.clamp(progress, 0f, 1f));
    }

    @Override
    public float getProgress() {
        return progress;
    }

    @NonNull
    @Override
    public Rect getBounds() {
        return bounds;
    }

    @Override
    public void clear() {
        particlesByColor.clear();
    }

    @Override
    public void draw(Canvas canvas) {
        final int colorCount = particlesByColor.size();
        if (colorCount == 0 || bounds.isEmpty()) return;

        if (DEBUG) {
            canvas.drawRect(bounds, getDebugPaint());
        }

        float alpha = progress <= 0.5f ? 1f : (1f - progress) * 2f;
        float pointSize = alpha < 1f ? AndroidUtilities.lerp(strokeWidth * 0.5f, strokeWidth, alpha) : strokeWidth;
        paint.setStrokeWidth(pointSize);

        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            Particles particles = particlesByColor.valueAt(colorIndex);
            int countToDraw = pointCountToDraw[colorIndex];
            if (countToDraw > 0) {
                int color = particlesByColor.keyAt(colorIndex);
                if (alpha < 1f) {
                    paint.setColor(multiplyAlphaComponent(color, alpha));
                } else {
                    paint.setColor(color);
                }
                canvas.drawPoints(particles.points, 0, countToDraw * 2, paint);
            }
        }
        nextFrame();
    }

    private void moveToState(float progress) {
        this.progress = progress;
        if (progress <= 0f || progress > 1f) return;
        float maxX = lerp(bounds.left, bounds.right, MathUtils.clamp(progress * 2.0f, 0.0f, 1.0f));
        final int colorCount = particlesByColor.size();
        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            Particles value = particlesByColor.valueAt(colorIndex);
            int pointIndex = Math.max(pointCountToDraw[colorIndex] - 1, 0);
            for (; pointIndex < value.pointCount; pointIndex++) {
                float pointX = value.points[pointIndex * 2];
                if (pointX > maxX) {
                    break;
                }
            }
            pointCountToDraw[colorIndex] = pointIndex;
        }
    }

    private void nextFrame() {
        float dy;
        if (progress > 0.5f) {
            dy = -(progress - 0.5f) / (1f - 0.5f) * AndroidUtilities.density * 0.12f;
        } else {
            dy = 0.0f;
        }

        final int colorCount = particlesByColor.size();
        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            Particles particles = particlesByColor.valueAt(colorIndex);
            particles.prepareToDraw();
            for (int pointIndex = 0; pointIndex < pointCountToDraw[colorIndex]; pointIndex++) {
                int x = pointIndex * 2;
                int y = pointIndex * 2 + 1;
                if (dy != 0f) {
                    particles.delta[y] += dy;
                }
                particles.points[x] += particles.delta[x];
                particles.points[y] += particles.delta[y];
            }
        }
    }
}
