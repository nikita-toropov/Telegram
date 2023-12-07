package org.telegram.ui.Components.Dispersion;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Dispersion.SingleDispersionEffect.Particles;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

public final class DispersionEffectFactory {

    private static final boolean USE_COMPOSITE_EFFECT = true;
    private static final float SCALE_FACTOR = 0.5f;

    private static HandlerThread thread;
    private static Handler handler;

    private DispersionEffectFactory() {
    }

    private static final class BitmapWithBounds {
        private final Bitmap bitmap;
        private final ArrayList<Rect> bounds;
        private final float scaleFactor;
        @Nullable
        private final Rect overrideBounds;

        private BitmapWithBounds(Bitmap bitmap, ArrayList<Rect> bounds, float scaleFactor, @Nullable Rect overrideBounds) {
            this.bitmap = bitmap;
            this.bounds = bounds;
            this.scaleFactor = scaleFactor;
            this.overrideBounds = overrideBounds;
        }
    }

    @UiThread
    @Nullable
    public static DispersionEffect create(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        float scaleFactor = getScaleFactor(view);
        Bitmap bitmap = snapshot(view, scaleFactor);
        BitmapWithBounds input = createBitmapWithBounds(view, bitmap, scaleFactor);
        DispersionEffect effect = create(input);
        bitmap.recycle();
        return effect;
    }

    @UiThread
    public static void createAsync(View view, Consumer<DispersionEffect> consumer) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            consumer.accept(null);
            return;
        }
        float scaleFactor = getScaleFactor(view);
        Bitmap bitmap = snapshot(view, scaleFactor);
        BitmapWithBounds input = createBitmapWithBounds(view, bitmap, scaleFactor);
        if (thread == null) {
            thread = new HandlerThread("DispersionEffectFactory");
            thread.start();
        }
        if (handler == null) {
            handler = new Handler(thread.getLooper());
        }
        handler.post(() -> {
            DispersionEffect effect = create(input);
            bitmap.recycle();
            AndroidUtilities.runOnUIThread(() -> consumer.accept(effect));
        });
    }

    @UiThread
    private static Bitmap snapshot(View view, float scaleFactor) {
        int width = Math.round(view.getWidth() * scaleFactor);
        int height = Math.round(view.getHeight() * scaleFactor);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scaleFactor, scaleFactor);

        if (view instanceof ChatMessageCell) {
            ViewParent parent = view.getParent();
            ChatMessageCell cell = (ChatMessageCell) view;
            boolean invalidatesParent = cell.isInvalidatesParent();
            if (invalidatesParent) {
                cell.setInvalidatesParent(false);
            }
            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
            if (group != null) {
                float x = cell.getNonAnimationTranslationX(false);
                float l = group.transitionParams.left + x + group.transitionParams.offsetLeft;
                float t = group.transitionParams.top + group.transitionParams.offsetTop;
                float r = group.transitionParams.right + x + group.transitionParams.offsetRight;
                float b = group.transitionParams.bottom + group.transitionParams.offsetBottom;
                boolean selected = false;
                int keyboardHeight = 0;
                ViewParent viewParent = parent;
                while (viewParent != null && !(viewParent instanceof SizeNotifierFrameLayout)) {
                    viewParent = viewParent.getParent();
                }
                if (viewParent != null) {
                    keyboardHeight = ((SizeNotifierFrameLayout) viewParent).getKeyboardHeight();
                }
                int saveCount = canvas.save();
                canvas.translate(-cell.getX(), -cell.getY());
                cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, selected, keyboardHeight);
                canvas.restoreToCount(saveCount);
            }

            int saveCount;
            if (cell.getAlpha() != 1f) {
                int alpha = lerp(0x00, 0xFF, cell.getAlpha());
                saveCount = canvas.saveLayerAlpha(0, 0, bitmap.getWidth(), bitmap.getHeight(), alpha, Canvas.ALL_SAVE_FLAG);
            } else {
                saveCount = Integer.MIN_VALUE;
            }

            cell.drawForBlur = true; // skip frame update?
            if (cell.drawBackgroundInParent() && group == null) {
                cell.drawBackgroundInternal(canvas, true);
            }
            cell.draw(canvas);
            if (cell.hasOutboundsContent()) {
                cell.drawOutboundsContent(canvas);
            }
            cell.drawForBlur = false;

            if (saveCount != Integer.MIN_VALUE) {
                canvas.restoreToCount(saveCount);
            }

            if (position != null || cell.getTransitionParams().animateBackgroundBoundsInner) {
                float alpha = cell.shouldDrawAlphaLayer() ? cell.getAlpha() : 1f;
                if (position == null || position.last) {
                    cell.drawTime(canvas, alpha, true);
                }
                if (position == null || (position.flags & MessageObject.POSITION_FLAG_TOP) != 0 && cell.hasNameLayout()) {
                    cell.drawNamesLayout(canvas, alpha);
                }
                if (position == null || (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                    cell.drawCaptionLayout(canvas, /* selectionOnly */ false, alpha);
                }
            }
            ImageReceiver avatarImage = cell.getAvatarImage();
            if (avatarImage != null && avatarImage.getVisible()) {
                canvas.save();
                canvas.translate(-cell.getX(), -cell.getY());
                avatarImage.draw(canvas);
                canvas.restore();
            }

            cell.setInvalidatesParent(invalidatesParent);
        } else if (view instanceof ChatActionCell) {
            ChatActionCell cell = (ChatActionCell) view;
            if (cell.hasGradientService()) {
                canvas.save();
                canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getMeasuredWidth() / 2f, cell.getMeasuredHeight() / 2f);
                cell.drawBackground(canvas, true);
                canvas.restore();
            }
            cell.draw(canvas);
            cell.drawOutboundsContent(canvas);
        } else {
            view.draw(canvas);
        }
        canvas.setBitmap(null);
        return bitmap;
    }

    @UiThread
    private static BitmapWithBounds createBitmapWithBounds(View view, Bitmap bitmap, float scaleFactor) {
        ArrayList<Rect> boundsList;
        Rect overrideBounds = null;
        if (view instanceof ChatMessageCell && USE_COMPOSITE_EFFECT) {
            boundsList = new ArrayList<>();
            ChatMessageCell cell = (ChatMessageCell) view;

            // message
            if (cell.hasBackground()) {
                Rect bounds = new Rect();
                cell.getBackgroundRect(bounds);
                if (!bounds.isEmpty()) {
                    boundsList.add(bounds);
                }
            } else {
                if (!cell.reactionsLayoutInBubble.isEmpty) {
                    Rect bounds = new Rect();
                    cell.reactionsLayoutInBubble.getDrawingRect(bounds);
                    if (!bounds.isEmpty()) {
                        boundsList.add(bounds);
                    }
                }
            }

            MessageObject message = cell.getMessageObject();
            if (message != null && message.shouldDrawWithoutBackground()) {
                if (message.isAnyKindOfSticker() || message.isRoundVideo()) {
                    Rect contentBounds;
                    if (message.type == MessageObject.TYPE_EMOJIS) {
                        contentBounds = new Rect(
                                cell.getTextX(),
                                cell.getTextY(),
                                cell.getTextX() + message.textWidth,
                                cell.getTextY() + message.textHeight
                        );
                    } else if (cell.getPhotoImage().getVisible()) {
                        ImageReceiver photoImage = cell.getPhotoImage();
                        contentBounds = new Rect(
                                (int) photoImage.getImageX(),
                                (int) photoImage.getImageY(),
                                (int) Math.ceil(photoImage.getImageX2()),
                                (int) Math.ceil(photoImage.getImageY2())
                        );
                    } else {
                        contentBounds = new Rect();
                    }
                    if (cell.shouldDrawTimeOnMedia()) {
                        Rect mediaTimeBounds = new Rect();
                        cell.getMediaTimeBounds(mediaTimeBounds);
                        if (!mediaTimeBounds.isEmpty()) {
                            if (contentBounds.isEmpty() || !contentBounds.intersects(mediaTimeBounds.left, mediaTimeBounds.top, mediaTimeBounds.right, mediaTimeBounds.bottom)) {
                                boundsList.add(mediaTimeBounds);
                            } else {
                                contentBounds.union(mediaTimeBounds);
                            }
                        }
                    }

                    if (!contentBounds.isEmpty()) {
                        boundsList.add(contentBounds);
                    }

                    if (cell.hasForwardNameLayout()) {
                        Rect bounds = new Rect();
                        cell.getForwardNameBounds(bounds, true);
                        if (!bounds.isEmpty()) {
                            boundsList.add(bounds);
                        }
                    }
                    if (cell.hasReplyNameLayout()) {
                        Rect bounds = new Rect();
                        cell.getReplyNameLayoutBounds(bounds, true);
                        if (!bounds.isEmpty()) {
                            boundsList.add(bounds);
                        }
                    }
                }
            }

            // side button
            if (cell.hasSideButton()) {
                Rect bounds = new Rect();
                cell.getSideButtonRect(bounds);
                if (!bounds.isEmpty()) {
                    boundsList.add(bounds);
                }
            }

            // avatar
            ImageReceiver avatarImage = cell.getAvatarImage();
            if (avatarImage != null && avatarImage.getVisible()) {
                Rect bounds = new Rect(
                        (int) (avatarImage.getImageX() - cell.getX()),
                        (int) (avatarImage.getImageY() - cell.getY()),
                        (int) (Math.ceil(avatarImage.getImageX2() - cell.getX())),
                        (int) (Math.ceil(avatarImage.getImageY2() - cell.getY()))
                );
                if (!bounds.isEmpty()) {
                    boundsList.add(bounds);
                }
            }

            // transcribe button
            if (cell.hasTranscribeButtion()) {
                Rect bounds = new Rect();
                cell.getTranscribeButtonBounds(bounds);
                if (!bounds.isEmpty()) {
                    boundsList.add(bounds);
                }
            }

            // bot buttons
            if (cell.hasBotButtons()) {
                Rect bounds = new Rect();
                cell.getBotButtonsRect(bounds);
                if (!bounds.isEmpty()) {
                    boundsList.add(bounds);
                }
            }

            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            if (group != null) {
                float x = cell.getNonAnimationTranslationX(false);
                float l = group.transitionParams.left + x + group.transitionParams.offsetLeft;
                float t = group.transitionParams.top + group.transitionParams.offsetTop;
                float r = group.transitionParams.right + x + group.transitionParams.offsetRight;
                float b = group.transitionParams.bottom + group.transitionParams.offsetBottom;
                Rect bounds = new Rect((int) l, (int) t, (int) r, (int) b);
                if (!bounds.isEmpty()) {
                    bounds.offset((int) -cell.getX(), (int) -cell.getY());
                    overrideBounds = bounds;
                }
            }
        } else {
            Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
            boundsList = new ArrayList<>(1);
            boundsList.add(bounds);
        }
        return new BitmapWithBounds(bitmap, boundsList, scaleFactor, overrideBounds);
    }

    @SuppressLint("CheckResult")
    @AnyThread
    private static DispersionEffect create(BitmapWithBounds input) {
        int count = input.bounds.size();
        int capacity = input.overrideBounds != null ? count + 1 : count;
        ArrayList<DispersionEffect> effectList = new ArrayList<>(capacity);
        int right = Math.round(input.bitmap.getWidth() / input.scaleFactor);
        int bottom = Math.round(input.bitmap.getHeight() / input.scaleFactor);
        for (int index = 0; index < count; index++) {
            Rect bounds = input.bounds.get(index);
            bounds.intersect(0, 0, right, bottom);
            DispersionEffect effect = create(input.bitmap, bounds, input.scaleFactor);
            if (effect != null) {
                effectList.add(effect);
            }
        }
        if (effectList.isEmpty()) {
            return null;
        }
        if (input.overrideBounds != null) {
            effectList.add(new EmptyDispersionEffect(input.overrideBounds));
        }
        if (effectList.size() == 1) {
            return effectList.get(0);
        }
        return new CompositeDispersionEffect(effectList);
    }

    @AnyThread
    private static DispersionEffect create(Bitmap bitmap, Rect bounds, float scaleFactor) {
        if (bounds.isEmpty()) {
            return null;
        }
        SparseArray<Particles> particlesByColor = new SparseArray<>();

        int left = Math.max(Math.round(bounds.left * scaleFactor), 0);
        int top = Math.max(Math.round(bounds.top * scaleFactor), 0);
        int right = Math.min(Math.round(bounds.right * scaleFactor), bitmap.getWidth());
        int bottom = Math.min(Math.round(bounds.bottom * scaleFactor), bitmap.getHeight());

        int r, g, b;
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                r = g = 0b111; b = 0b11;
                break;
            case SharedConfig.PERFORMANCE_CLASS_LOW:
            default:
                g = 0b111; r = b = 0b11;
                break;
        }
        for (int x = left; x < right - 1; x += 2) {
            for (int y = top; y < bottom - 1; y += 2) {
                int x1 = y % 2 == 0 ? x : x + 1;
                int y1 = x % 2 == 0 ? y : y + 1;
                int color = bitmap.getPixel(x1, y1);
                int alpha = Color.alpha(color) * 0b11 / 0xFF * 0xFF / 0b11;
                if (alpha < 0x0F) continue;

                int red = Color.red(color) * r / 0xFF * 0xFF / r;
                int green = Color.green(color) * g / 0xFF * 0xFF / g;
                int blue = Color.blue(color) * b / 0xFF * 0xFF / b;
                int argb = Color.argb(alpha, red, green, blue);

                Particles particles = particlesByColor.get(argb);
                if (particles == null) {
                    particles = new Particles();
                    particlesByColor.put(argb, particles);
                }
                particles.append(x1 / scaleFactor, y1 / scaleFactor);
            }
        }
        int colorCount = particlesByColor.size();
        if (colorCount == 0) {
            return new EmptyDispersionEffect(bounds);
        }
        for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
            Particles particles = particlesByColor.valueAt(colorIndex);
            particles.prepareToDraw();
        }
        return new SingleDispersionEffect(bounds, particlesByColor, scaleFactor);
    }

    // FIXME учитывать площадь контента, а не только высоту
    private static float getScaleFactor(View view) {
        int displayHeight = AndroidUtilities.displaySize.y;
        if (displayHeight <= 0) {
            return SCALE_FACTOR;
        }
        float f = MathUtils.clamp((view.getHeight() - displayHeight * 0.15f) / (displayHeight * 0.85f), 0f, 1f);
        return SCALE_FACTOR / lerp(1f, 3f, f);
    }
}
