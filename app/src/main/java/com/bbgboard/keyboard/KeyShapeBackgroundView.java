package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * خلفية زر ترسم نفس شكل زر "؟123"/الإدخال الحقيقي في الكيبورد الرئيسي بالضبط (حسب ستايل الكيبورد
 * المختار حالياً من الإعدادات)، تُستخدم لأزرار خارج الكيبورد الرئيسي (زر "أبجد"/الحذف في قوائم
 * الحافظة والإيموجي) حتى يكون شكلها مطابقاً تماماً لشكل زر "؟123" بدل تقليد تقريبي بزاوية ثابتة.
 * هذا الكلاس نسخة مطابقة لمنطق BlackBerryKeyboardView.drawStyledKeyCell() لكن بافتراض pillKey=true
 * دائماً (لأن زر "؟123" نفسه دائماً pillKey)، ومرسومة على حدود الـView نفسها بدل مستطيل مفتاح داخل
 * الكانفس الكبير.
 */
public class KeyShapeBackgroundView extends View {

    private final float density;
    private final int keyboardStyle;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rawRect = new RectF();
    // فجوة إضافية بين الأزرار في وضع التابلت (نفس القيم اللي يستخدمها الكيبورد الرئيسي) حتى يتطابق الشكل
    private float extraX = 0f;
    private float extraY = 0f;

    public void setExtraInset(float x, float y) {
        if (x == extraX && y == extraY) return;
        extraX = x;
        extraY = y;
        invalidate();
    }

    public KeyShapeBackgroundView(Context ctx, int keyboardStyle,
                                   int fillColor, int shadowColor, int outlineColor, int dividerColor) {
        super(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.keyboardStyle = keyboardStyle;
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(shadowColor);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(outlineColor);
        dividerPaint.setColor(dividerColor);
        dividerPaint.setStrokeWidth(0.8f * density);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rawRect.set(0f, 0f, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (rawRect.width() <= 0f || rawRect.height() <= 0f) return;

        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_MATERIAL
                || keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND) {
            boolean soft = (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND);
            float insetX = (soft ? 2.6f : 3.0f) * density + extraX;
            float insetY = 4.0f * density + extraY;
            RectF r = new RectF(rawRect.left + insetX, rawRect.top + insetY,
                    rawRect.right - insetX, rawRect.bottom - insetY);
            float radius = r.height() / 2f;
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_BORDERLESS) {
            float insetX = 3.0f * density + extraX;
            float insetY = 4.5f * density + extraY;
            RectF r = new RectF(rawRect.left + insetX, rawRect.top + insetY,
                    rawRect.right - insetX, rawRect.bottom - insetY);
            float radius = r.height() / 2f;
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_OUTLINED) {
            float insetX = 3.2f * density + extraX;
            float insetY = 4.2f * density + extraY;
            RectF r = new RectF(rawRect.left + insetX, rawRect.top + insetY,
                    rawRect.right - insetX, rawRect.bottom - insetY);
            float radius = 11f * density;
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            outlinePaint.setStrokeWidth(1.3f * density);
            outlinePaint.setAlpha(150);
            canvas.drawRoundRect(r, radius, radius, outlinePaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_KEYCAP_3D) {
            float edge = 3.4f * density;
            RectF cap = new RectF(rawRect.left + 2.8f * density + extraX, rawRect.top + 3.2f * density + extraY,
                    rawRect.right - 2.8f * density - extraX, rawRect.bottom - edge - extraY);
            RectF base = new RectF(cap.left, cap.top + edge, cap.right, cap.bottom + edge);
            float radius = 7.5f * density;
            canvas.drawRoundRect(base, radius, radius, shadowPaint);
            canvas.drawRoundRect(cap, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_ROUNDED) {
            float insetX = 2.6f * density + extraX;
            float insetY = 3.6f * density + extraY;
            RectF cardRect = new RectF(rawRect.left + insetX, rawRect.top + insetY,
                    rawRect.right - insetX, rawRect.bottom - insetY);
            RectF shadowRect = new RectF(cardRect.left, cardRect.top + 1.5f * density,
                    cardRect.right, cardRect.bottom + 1.5f * density);
            float radius = 6.5f * density;
            canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint);
            canvas.drawRoundRect(cardRect, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_MODERN_PILL) {
            float insetX = 3.2f * density + extraX;
            float insetY = 4.5f * density + extraY;
            RectF pillRect = new RectF(rawRect.left + insetX, rawRect.top + insetY,
                    rawRect.right - insetX, rawRect.bottom - insetY);
            float radius = 14f * density;
            canvas.drawRoundRect(pillRect, radius, radius, fillPaint);
            return;
        }
        // النمط الافتراضي (BLACKBERRY_FRETS وغيره): مستطيل متصل بخط فاصل بدون استدارة، بالضبط زي
        // شكل زر "؟123" في الكيبورد الرئيسي بهذا النمط.
        canvas.drawRect(rawRect, fillPaint);
        canvas.drawLine(rawRect.right, rawRect.top, rawRect.right, rawRect.bottom, dividerPaint);
    }
}
