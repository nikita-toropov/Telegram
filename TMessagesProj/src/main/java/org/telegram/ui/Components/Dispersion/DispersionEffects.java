package org.telegram.ui.Components.Dispersion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;

import java.util.HashMap;
import java.util.Map;

@UiThread
public final class DispersionEffects {

    private static final TimeInterpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private static final DispersionEffects instance = new DispersionEffects();

    private final Map<Object, DispersionEffect> runningEffects = new HashMap<>();
    private final Map<Object, Consumer<DispersionEffect>> pendingEffects = new HashMap<>();
    private final Map<DispersionEffect, Animator> animators = new HashMap<>();

    private DispersionEffects() {
    }

    public static DispersionEffects getInstance() {
        return instance;
    }

    @Nullable
    public DispersionEffect get(@Nullable Object key) {
        return runningEffects.get(key);
    }

    public boolean isRunning(@Nullable Object key) {
        return runningEffects.containsKey(key);
    }

    public boolean isPending(@Nullable Object key) {
        return pendingEffects.containsKey(key);
    }

    public boolean isPendingOrRunning(@Nullable Object key) {
        return isPending(key) || isRunning(key);
    }

    public void setProgress(@Nullable Object key, @FloatRange(from = 0.0, to = 1.0) float progress) {
        DispersionEffect effect = runningEffects.get(key);
        if (effect != null) {
            effect.setProgress(MathUtils.clamp(progress, 0f, 1f));
        }
    }

    public float getProgress(@Nullable Object key, float defaultValue) {
        DispersionEffect effect = runningEffects.get(key);
        if (effect != null) {
            return effect.getProgress();
        }
        return defaultValue;
    }

    public void clear(View view) {
        pendingEffects.remove(view);
        DispersionEffect effect = runningEffects.remove(view);
        if (effect != null) {
            effect.clear();
            Animator animator = animators.remove(effect);
            if (animator != null) {
                animator.cancel();
            }
        }
    }

    public void animate(View view) {
        if (isRunning(view)) return;
        prepare(view, (effect) -> {
            if (effect != null) {
                Animator animator = createAnimator(view);
                animator.setDuration(2_000);
                animator.start();
                animators.put(effect, animator);
            }
        });
    }

    @Nullable
    public DispersionEffect prepare(View view) {
        pendingEffects.remove(view);
        DispersionEffect effect = DispersionEffectFactory.create(view);
        if (effect != null) {
            runningEffects.put(view, effect);
            return effect;
        }
        return null;
    }

    public void prepare(View view, Consumer<DispersionEffect> onPrepared) {
        Consumer<DispersionEffect> callback = new Consumer<DispersionEffect>() {
            @Override
            public void accept(DispersionEffect effect) {
                if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                    throw new IllegalThreadStateException();
                }
                if (pendingEffects.get(view) == this) {
                    pendingEffects.remove(view);
                    if (effect != null) {
                        runningEffects.put(view, effect);
                    }
                    onPrepared.accept(effect);
                } else {
                    onPrepared.accept(null);
                }
            }
        };
        pendingEffects.put(view, callback);
        DispersionEffectFactory.createAsync(view, callback);
    }

    @NonNull
    public Animator createAnimator(View view) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setInterpolator(INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setProgress(view, progress);
            View parent = (View) view.getParent();
            if (parent != null) {
                view.invalidate();
                parent.invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                animator.removeListener(this);
                clear(view);
            }
        });
        return animator;
    }
}
