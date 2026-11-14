package com.bbgboard.keyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A hand-drawn pill toggle matching the "icon inside the thumb" look some OEM skins use
 * (a check mark in the thumb when on, a dash when off) — plain android.widget.Switch can't
 * render that consistently across devices, so it's drawn the same way everywhere instead.
 */
public class ModernToggleSwitch extends View {

    public interface OnToggleListener {
        void onToggle(boolean checked);
    }

    private boolean checked;
    private float thumbPos; // 0f = off, 1f = on
    private final float density;
    private OnToggleListener listener;

    private int trackOnColor = Color.parseColor("#2563EB");
    private int trackOffColor = Color.parseColor("#D1D5DB");
    private int thumbColor = Color.WHITE;
    private int iconOnColor = Color.parseColor("#2563EB");
    private int iconOffColor = Color.parseColor("#6B7280");

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private ValueAnimator animator;

    public ModernToggleSwitch(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeWidth(1.7f * density);
        thumbPaint.setColor(thumbColor);
    }

    public void setColors(int trackOnColor, int trackOffColor, int iconOnColor, int iconOffColor) {
        this.trackOnColor = trackOnColor;
        this.trackOffColor = trackOffColor;
        this.iconOnColor = iconOnColor;
        this.iconOffColor = iconOffColor;
        invalidate();
    }

    public void setOnToggleListener(OnToggleListener l) {
        this.listener = l;
    }

    public void setChecked(boolean isChecked) {
        setChecked(isChecked, false);
    }

    public void setChecked(boolean isChecked, boolean animate) {
        boolean changed = this.checked != isChecked;
        this.checked = isChecked;
        if (animator != null) animator.cancel();
        if (animate && changed) {
            animator = ValueAnimator.ofFloat(thumbPos, isChecked ? 1f : 0f);
            animator.setDuration(160);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                thumbPos = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            thumbPos = isChecked ? 1f : 0f;
            invalidate();
        }
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) (50 * density), (int) (30 * density));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            boolean newChecked = !checked;
            setChecked(newChecked, true);
            if (listener != null) listener.onToggle(newChecked);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = 1.5f * density;
        trackRect.set(pad, h * 0.08f, w - pad, h * 0.92f);

        int trackColor = blendColor(trackOffColor, trackOnColor, thumbPos);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.FILL);
        float trackRadius = trackRect.height() / 2f;
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint);

        // Keep the thumb strictly inside the track (same inset on every side at both ends),
        // so its rounded edge never pokes past the track's own rounded cap.
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float thumbInset = 2.2f * density;
        float thumbRadius = trackRadius - thumbInset;
        float travel = trackRect.width() - thumbInset * 2f - thumbRadius * 2f;
        float startX = trackRect.left + thumbInset + thumbRadius;
        float logicalX = startX + travel * thumbPos;
        float thumbCx = rtl ? (w - logicalX) : logicalX;
        float thumbCy = h / 2f;

        thumbPaint.setColor(thumbColor);
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint);

        int iconColor = blendColor(iconOffColor, iconOnColor, thumbPos);
        iconPaint.setColor(iconColor);
        float iconR = thumbRadius * 0.42f;
        if (thumbPos > 0.5f) {
            canvas.drawLine(thumbCx - iconR, thumbCy + iconR * 0.05f, thumbCx - iconR * 0.1f, thumbCy + iconR * 0.85f, iconPaint);
            canvas.drawLine(thumbCx - iconR * 0.1f, thumbCy + iconR * 0.85f, thumbCx + iconR, thumbCy - iconR * 0.6f, iconPaint);
        } else {
            canvas.drawLine(thumbCx - iconR, thumbCy, thumbCx + iconR, thumbCy, iconPaint);
        }
    }

    private int blendColor(int from, int to, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + ratio * (Color.alpha(to) - Color.alpha(from)));
        int r = (int) (Color.red(from) + ratio * (Color.red(to) - Color.red(from)));
        int g = (int) (Color.green(from) + ratio * (Color.green(to) - Color.green(from)));
        int b = (int) (Color.blue(from) + ratio * (Color.blue(to) - Color.blue(from)));
        return Color.argb(a, r, g, b);
    }
}
