package org.telegram.ui.Components.Dispersion;

import android.graphics.Canvas;
import android.graphics.Rect;

import androidx.annotation.NonNull;

final class EmptyDispersionEffect implements DispersionEffect {

    private final Rect bounds;

    private float progress;

    EmptyDispersionEffect(Rect bounds) {
        this.bounds = bounds;
    }

    @Override
    public float getProgress() {
        return progress;
    }

    @Override
    public void setProgress(float progress) {
        this.progress = progress;
    }

    @Override
    public void draw(Canvas canvas) {

    }

    @NonNull
    @Override
    public Rect getBounds() {
        return bounds;
    }

    @Override
    public void clear() {
        progress = 0f;
        bounds.setEmpty();
    }
}
