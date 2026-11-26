package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Selection circle for option lists: same drawn check mark as ModernToggleSwitch. */
public class RadioCheckView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean on;

    public RadioCheckView(Context context, int onColor, int offRingColor, int iconColor) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(onColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(density);
        ringPaint.setColor(offRingColor);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeWidth(1.7f * density);
        iconPaint.setColor(iconColor);
    }

    public void setOn(boolean value) {
        if (on == value) return;
        on = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        if (on) {
            canvas.drawCircle(cx, cy, r, fillPaint);
            float iconR = r * 0.4f;
            canvas.drawLine(cx - iconR, cy + iconR * 0.05f, cx - iconR * 0.1f, cy + iconR * 0.85f, iconPaint);
            canvas.drawLine(cx - iconR * 0.1f, cy + iconR * 0.85f, cx + iconR, cy - iconR * 0.6f, iconPaint);
        } else {
            canvas.drawCircle(cx, cy, r - density * 0.5f, ringPaint);
        }
    }
}
