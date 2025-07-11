package org.telegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp01;

public final class AvatarInkView extends View {
    private final Path path = new Path();
    private final Path path1 = new Path();
    private final Path path2 = new Path();
    private final RectF rect1 = new RectF();
    private final RectF rect2 = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PunchHole punchHole;

    private float fraction = 0f;

    public AvatarInkView(Context context) {
        super(context);
        setWillNotDraw(false);
        paint.setColor(DEBUG ? 0x80FF0000 : Color.BLACK);
        paint.setStyle(Paint.Style.FILL);

        punchHole = new PunchHole(context);
        updatePunchHole();
    }

    private void updatePunchHole() {
        if (punchHole.initialize() && punchHole.isCircle()) {
            punchHole.getCircleRect(circlePunchHoleRect);
        } else {
            circlePunchHoleRect.setEmpty();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updatePunchHole();
    }

    public void update(float fraction, @Nullable View view) {
        if (view == null || view.getPivotY() != 0f || view.getPivotX() != view.getMeasuredWidth() * 0.5f) {
            if (this.fraction != 0f) {
                this.fraction = 0f;
                invalidate();
            }
            return;
        }
        float radius = Math.min(view.getMeasuredHeight() * view.getScaleY() * 0.5f, dp(90));
        float cx = view.getX() + view.getMeasuredWidth() * 0.5f;
        float cy = view.getY() + radius;
        if (hasCirclePunchHole() && Math.abs(circlePunchHoleRect.exactCenterX() - cx) >= 1f) {
            if (this.fraction != 0f) {
                this.fraction = 0f;
                invalidate();
            }
            return;
        }
        if (DEBUG) {
            view.setAlpha(0.2f);
        }
        update(fraction, cx, cy, radius);
    }

    public void update(float fraction, float cx, float cy, float radius) {
        fraction = clamp01(fraction);
        if (this.fraction == fraction && rect2.centerX() == cx && rect2.centerY() == cy && rect2.width() == radius * 2) return;
        this.fraction = fraction;
        rect2.setEmpty();
        rect2.offsetTo(cx, cy);
        rect2.inset(-radius, -radius);

        if (hasCirclePunchHole()) {
            rect1.set(circlePunchHoleRect);
        } else {
            float r = dp(22);
            rect1.set(0, 0, r * 2, r * 2);
            rect1.offsetTo(cx - r, -r * 2);
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (fraction <= 0f || fraction > 1f) {
            return;
        }
        path.rewind();
        if (fraction <= 0f) {
            path.addCircle(rect1.centerX(), rect1.centerY(), rect1.height() * 0.5f, Path.Direction.CW);
        } else if (fraction < 0.5f) {
            path1.rewind();
            path1.moveTo(rect1.left, rect1.centerY());
            path1.arcTo(rect1, 180, 180, true);

            float distance = Math.max(0, rect2.top - rect1.bottom) * clamp01(fraction * 1.5f);

            float hr = rect1.width() * 0.24f;
            float x3 = rect1.centerX();
            float y3 = rect1.bottom + distance * 0.75f;
            float x1 = rect1.right;
            float y1 = rect1.centerY() + hr;
            float x2 = x3 + hr;
            float y2 = y3;
            path1.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);

            x1 = x3 - hr;
            y1 = y3;
            x2 = rect1.left;
            y2 = rect1.centerY() + hr;
            x3 = rect1.left;
            y3 = rect1.centerY();
            path1.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);

            path.op(path1, Path.Op.UNION);

            hr = rect2.width() * 0.25f;

            path2.rewind();
            path2.moveTo(rect2.left, rect2.centerY());
            path2.arcTo(rect2, 180, -180, true);

            x3 = rect2.centerX();
            y3 = rect2.top - distance * 0.75f;
            x1 = rect2.right;
            y1 = rect2.centerY() - hr;
            x2 = x3 + hr;
            y2 = y3;
            path2.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);

            x1 = x3 - hr;
            y1 = y3;
            x2 = rect2.left;
            y2 = rect2.centerY() - hr;
            x3 = rect2.left;
            y3 = rect2.centerY();

            path2.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);

            path.op(path2, Path.Op.UNION);
        } else {
            boolean circlePunchHole = hasCirclePunchHole();

            float f = fraction;
            float f2 = circlePunchHole ? (fraction - 0.2f) * 1.25f : (f - 0.5f) * 2f;

            float r1 = rect1.width() * 0.5f;
            float r2 = rect2.width() * 0.5f;
            float cy = rect1.bottom + 0.5f * Math.max(0, rect2.top - rect1.bottom);

            path.moveTo(rect1.left, rect1.centerY());
            path.arcTo(rect1, 180, 180, true);

            float a2 = lerp(dp(8), dp(32), f2);

            // down 1
            float x3 = circlePunchHole ? (rect1.centerX() + f2 * r1) : rect1.centerX() + a2;
            float y3 = cy;
            float x1 = rect1.right;
            float y1 = circlePunchHole ? (y3 - r1 * f2) : y3 - r1 * f;
            float x2 = x3;
            float y2 = circlePunchHole? (y3 - r1 * (1f - f2)) : y3 - r1 * (1f - f);

            path.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);
            if (DEBUG) {
                canvas.drawLine(x1, y1, x2, y2, DEBUG_PAINT_LINE);
                canvas.drawLine(x2, y2, x3, y3, DEBUG_PAINT_LINE);
            }

            float a1 = circlePunchHole ? 0 : dp(18) * f2;

            // down 2
            x1 = x3;
            y1 = circlePunchHole ? Math.min(y3 + (1f - f2) * r2, rect2.centerY()) : Math.max(y3, 0f);
            x2 = rect2.right + (circlePunchHole ? 0 : -a1);
            y2 = circlePunchHole ? Math.min(y3 + f2 * r2, rect2.centerY()) : Math.max(lerp(y3 + f * r2, rect2.top, f2), 0f);
            x3 = rect2.right;
            y3 = Math.max(rect2.centerY(), 0f);

            path.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);
            if (DEBUG) {
                canvas.drawLine(x1, y1, x2, y2, DEBUG_PAINT_LINE);
                canvas.drawLine(x2, y2, x3, y3, DEBUG_PAINT_LINE);
            }

            path.arcTo(rect2, 0, 180, true);

            // up 2
            x3 = circlePunchHole ? (rect1.centerX() - f2 * r1) : rect2.centerX() - a2;
            y3 = Math.max(cy, 0f);
            x1 = rect2.left + (circlePunchHole ? 0 : a1);
            y1 = circlePunchHole ? Math.min(y3 + (f2 * r2), rect2.centerY()) : Math.max(lerp(y3 + f * r2, rect2.top, f2), 0f);
            x2 = x3;
            y2 = circlePunchHole ? Math.min(y3 + (1f - f2) * r2, rect2.centerY()) : Math.max(y3, 0f);

            path.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);
            if (DEBUG) {
                canvas.drawLine(x1, y1, x2, y2, DEBUG_PAINT_LINE);
                canvas.drawLine(x2, y2, x3, y3, DEBUG_PAINT_LINE);
            }

            // up 1
            x1 = x3;
            y1 = circlePunchHole ? (y3 - (1f - f2) * r1) : (y3 - (1f - f) * r1);
            x2 = rect1.left;
            y2 = circlePunchHole ? (y3 - r1 * f2) : (y3 - r1 * f);
            x3 = rect1.left;
            y3 = rect1.centerY();

            path.cubicTo(x1, y1, x2, y2, x3, y3);

            drawDebugPoint(canvas, x1, y1);
            drawDebugPoint(canvas, x2, y2);
            if (DEBUG) {
                canvas.drawLine(x1, y1, x2, y2, DEBUG_PAINT_LINE);
                canvas.drawLine(x2, y2, x3, y3, DEBUG_PAINT_LINE);
            }
        }
        canvas.drawPath(path, paint);
    }

    private final Rect circlePunchHoleRect = new Rect();

    Rect getCirclePunchHoleRect() {
        return circlePunchHoleRect;
    }

    boolean hasCirclePunchHole() {
        return !circlePunchHoleRect.isEmpty();
    }

    private void drawDebugPoint(Canvas canvas, float x, float y) {
        if (DEBUG) {
            canvas.drawCircle(x, y, dp(1), DEBUG_PAINT);
        }
    }

    private static final Paint DEBUG_PAINT = new Paint();
    private static final Paint DEBUG_PAINT_LINE = new Paint();

    static {
        DEBUG_PAINT.setStyle(Paint.Style.FILL);
        DEBUG_PAINT.setColor(Color.MAGENTA);

        DEBUG_PAINT_LINE.setStrokeWidth(dp(2));
        DEBUG_PAINT_LINE.setColor(Color.GREEN);
        DEBUG_PAINT_LINE.setStyle(Paint.Style.STROKE);
    }
    private static final boolean DEBUG = false;
}
