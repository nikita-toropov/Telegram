package org.telegram.ui.Components.Dispersion;

import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

public interface DispersionEffect {
    float getProgress();

    void setProgress(float progress);

    void draw(Canvas canvas);

    @NonNull
    Rect getBounds();

    void clear();

    default RectF getClipRect(RectF outRect) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            outRect.setEmpty();
        } else {
            float factor = clamp(/* value */ getProgress() * 2f, /* maxValue */ 1f, /* minValue */ 0f);
            float offset = AndroidUtilities.density * 6f; // ???
            outRect.set(getBounds());
            outRect.left = MathUtils.clamp(lerp(outRect.left - offset, outRect.right, factor), outRect.left, outRect.right);
        }
        return outRect;
    }
}
