package org.telegram.ui.Components.Dispersion;

import static androidx.core.math.MathUtils.clamp;
import static androidx.core.util.ObjectsCompat.requireNonNull;

import android.graphics.Canvas;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

final class CompositeDispersionEffect implements DispersionEffect {

    private float progress = 0.0f;
    private final ArrayList<DispersionEffect> effectList;
    @Nullable
    private Rect bounds;

    CompositeDispersionEffect(@NonNull ArrayList<DispersionEffect> effectList) {
        this.effectList = requireNonNull(effectList);
    }

    @Override
    public float getProgress() {
        return progress;
    }

    @Override
    public void setProgress(float progress) {
        progress = MathUtils.clamp(progress, 0f, 1f);
        if (this.progress != progress) {
            this.progress = progress;
            dispatchProgress(progress);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        for (int index = 0; index < effectList.size(); index++) {
            DispersionEffect effect = effectList.get(index);
            effect.draw(canvas);
        }
    }

    @NonNull
    @Override
    public Rect getBounds() {
        if (bounds == null) {
            bounds = new Rect(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
            for (int index = 0; index < effectList.size(); index++) {
                DispersionEffect effect = effectList.get(index);
                bounds.union(effect.getBounds());
            }
        }
        return bounds;
    }

    @Override
    public void clear() {
        bounds = null;
        progress = 0.0f;
        clearChildren();
    }

    private void dispatchProgress(float progress) {
        Rect bounds = getBounds();
        float x = AndroidUtilities.lerp(bounds.left, bounds.right, MathUtils.clamp(progress * 2f, 0f, 1f));
        for (int index = 0; index < effectList.size(); index++) {
            DispersionEffect effect = effectList.get(index);
            if (progress > 0.5f) {
                effect.setProgress(progress);
                continue;
            }
            Rect childBounds = effect.getBounds();
            float factor = (x - childBounds.left) / (childBounds.width()) * 0.5f;
            effect.setProgress(clamp(factor, 0f, 0.5f));
        }
    }

    private void clearChildren() {
        for (DispersionEffect effect : effectList) {
            effect.clear();
        }
        effectList.clear();
    }
}
