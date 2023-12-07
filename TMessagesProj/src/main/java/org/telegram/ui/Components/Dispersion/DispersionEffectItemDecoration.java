package org.telegram.ui.Components.Dispersion;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public final class DispersionEffectItemDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            DispersionEffect effect = DispersionEffects.getInstance().get(child);
            if (effect != null) {
                int saveCount = c.save();
                c.translate(child.getX(), child.getY());
                effect.draw(c);
                c.restoreToCount(saveCount);
            }
        }
    }
}
