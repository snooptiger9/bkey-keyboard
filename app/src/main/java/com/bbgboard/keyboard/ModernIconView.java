package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class ModernIconView extends View {

    public static final int ICON_BACK_KEYBOARD = 1;
    public static final int ICON_RESIZE_HEIGHT = 2;
    public static final int ICON_SETTINGS_GEAR = 3;
    public static final int ICON_THEME_PALETTE = 4;
    public static final int ICON_GLOBE_LANG = 5;
    public static final int ICON_SLIDERS_PREFS = 6;
    public static final int ICON_TEXT_CORRECTION = 7;
    public static final int ICON_GESTURE_SWIPE = 8;
    public static final int ICON_SOUND_WAVE = 9;
    public static final int ICON_EMOJI_SMILE = 10;
    public static final int ICON_CLIPBOARD_PAD = 11;
    public static final int ICON_DICTIONARY_BOOK = 12;
    public static final int ICON_BACK_ARROW = 13;
    public static final int ICON_PIN = 14;
    public static final int ICON_CLOSE = 15;
    public static final int ICON_FORWARD_ARROW = 16;
    public static final int ICON_BACKSPACE = 17;
    public static final int ICON_PUSHPIN = 18;   // دبوس مائل (تثبيت نص الحافظة)
    public static final int ICON_TRASH = 19;     // سلة محذوفات (حذف نص الحافظة)
    public static final int ICON_MIC = 20;       // مايكروفون (الكتابة بالصوت داخل قائمة الحافظة)

    private boolean filled = false;
    private float iconScale = 1f;
    private boolean pillBackground = false;

    /** يجعل خلفية الأيقونة كبسولة بيضاوية كاملة (نصف الارتفاع) بدل مربع بزوايا دائرية،
     * مثل تضليل مفتاح "123" في سمة Gboard. */
    public void setPillBackground(boolean pill) {
        this.pillBackground = pill;
        invalidate();
    }

    /** تصغير/تكبير الرسم داخل المساحة، وسماكة الخط بالـ dp (لتطابق أيقونة الحافظة الرئيسية). */
    public void setStyle(float scale, float strokeDp) {
        this.iconScale = scale;
        strokePaint.setStrokeWidth(strokeDp * density);
        invalidate();
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
        invalidate();
    }

    /** يغيّر شكل الأيقونة المرسومة بعد الإنشاء (مثل تبديل اتجاه السهم). */
    public void setIconType(int type) {
        this.iconType = type;
        invalidate();
    }

    private int iconType;
    private int iconColor;
    private int bgCircleColor;
    private final float density;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ModernIconView(Context context, int iconType, int iconColor, int bgCircleColor) {
        super(context);
        this.iconType = iconType;
        this.iconColor = iconColor;
        this.bgCircleColor = bgCircleColor;
        this.density = context.getResources().getDisplayMetrics().density;

        strokePaint.setColor(iconColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.2f * density);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(iconColor);
        fillPaint.setStyle(Paint.Style.FILL);

        bgPaint.setColor(bgCircleColor);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int newIconColor, int newBgColor) {
        this.iconColor = newIconColor;
        this.bgCircleColor = newBgColor;
        strokePaint.setColor(newIconColor);
        fillPaint.setColor(newIconColor);
        bgPaint.setColor(newBgColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.36f * iconScale;

        if (bgCircleColor != Color.TRANSPARENT) {
            RectF bgRect = new RectF(2f * density, 2f * density, w - 2f * density, h - 2f * density);
            float radius = pillBackground ? bgRect.height() / 2f : 10f * density;
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
        }

        switch (iconType) {
            case ICON_BACK_KEYBOARD: {
                float kr = Math.min(w, h) * 0.39f;
                RectF kb = new RectF(cx - kr, cy - kr * 0.72f, cx + kr, cy + kr * 0.72f);
                canvas.drawRoundRect(kb, 3.5f * density, 3.5f * density, strokePaint);
                float dotR = 1.4f * density;
                for (int i = -1; i <= 1; i++) {
                    canvas.drawCircle(cx + i * kr * 0.48f, cy - kr * 0.22f, dotR, fillPaint);
                }
                canvas.drawLine(cx - kr * 0.46f, cy + kr * 0.32f, cx + kr * 0.46f, cy + kr * 0.32f, strokePaint);
                break;
            }
            case ICON_RESIZE_HEIGHT: {
                // سهم مزدوج عمودي بين خطين أفقيين (ارتفاع)
                canvas.drawLine(cx, cy - r * 0.62f, cx, cy + r * 0.62f, strokePaint);
                Path arrows = new Path();
                arrows.moveTo(cx - r * 0.38f, cy - r * 0.28f);
                arrows.lineTo(cx, cy - r * 0.66f);
                arrows.lineTo(cx + r * 0.38f, cy - r * 0.28f);
                arrows.moveTo(cx - r * 0.38f, cy + r * 0.28f);
                arrows.lineTo(cx, cy + r * 0.66f);
                arrows.lineTo(cx + r * 0.38f, cy + r * 0.28f);
                canvas.drawPath(arrows, strokePaint);
                canvas.drawLine(cx - r * 0.62f, cy - r * 0.92f, cx + r * 0.62f, cy - r * 0.92f, strokePaint);
                canvas.drawLine(cx - r * 0.62f, cy + r * 0.92f, cx + r * 0.62f, cy + r * 0.92f, strokePaint);
                break;
            }
            case ICON_SETTINGS_GEAR: {
                Path gear = new Path();
                float outer = r * 0.95f;
                float inner = r * 0.74f;
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0);
                    double[] offs = {-15.0, -8.5, 8.5, 15.0};
                    float[] rads = {inner, outer, outer, inner};
                    for (int k = 0; k < 4; k++) {
                        double ang = a + Math.toRadians(offs[k]);
                        float px = cx + (float) Math.cos(ang) * rads[k];
                        float py = cy + (float) Math.sin(ang) * rads[k];
                        if (i == 0 && k == 0) gear.moveTo(px, py); else gear.lineTo(px, py);
                    }
                }
                gear.close();
                canvas.drawPath(gear, strokePaint);
                canvas.drawCircle(cx, cy, r * 0.30f, strokePaint);
                break;
            }
            case ICON_THEME_PALETTE: {
                canvas.drawCircle(cx, cy, r * 0.88f, strokePaint);
                float dr = 1.8f * density;
                canvas.drawCircle(cx - r * 0.35f, cy - r * 0.25f, dr, fillPaint);
                canvas.drawCircle(cx + r * 0.1f, cy - r * 0.42f, dr, fillPaint);
                canvas.drawCircle(cx + r * 0.42f, cy - r * 0.05f, dr, fillPaint);
                canvas.drawCircle(cx + r * 0.15f, cy + r * 0.38f, dr, fillPaint);
                break;
            }
            case ICON_GLOBE_LANG: {
                canvas.drawCircle(cx, cy, r * 0.88f, strokePaint);
                canvas.drawLine(cx - r * 0.85f, cy, cx + r * 0.85f, cy, strokePaint);
                RectF oval = new RectF(cx - r * 0.45f, cy - r * 0.88f, cx + r * 0.45f, cy + r * 0.88f);
                canvas.drawOval(oval, strokePaint);
                break;
            }
            case ICON_SLIDERS_PREFS: {
                float y1 = cy - r * 0.55f;
                float y2 = cy;
                float y3 = cy + r * 0.55f;
                canvas.drawLine(cx - r * 0.85f, y1, cx + r * 0.85f, y1, strokePaint);
                canvas.drawLine(cx - r * 0.85f, y2, cx + r * 0.85f, y2, strokePaint);
                canvas.drawLine(cx - r * 0.85f, y3, cx + r * 0.85f, y3, strokePaint);
                canvas.drawCircle(cx - r * 0.3f, y1, 2.5f * density, fillPaint);
                canvas.drawCircle(cx + r * 0.35f, y2, 2.5f * density, fillPaint);
                canvas.drawCircle(cx - r * 0.15f, y3, 2.5f * density, fillPaint);
                break;
            }
            case ICON_TEXT_CORRECTION: {
                Path aPath = new Path();
                aPath.moveTo(cx - r * 0.75f, cy + r * 0.55f);
                aPath.lineTo(cx - r * 0.25f, cy - r * 0.70f);
                aPath.lineTo(cx + r * 0.25f, cy + r * 0.55f);
                aPath.moveTo(cx - r * 0.55f, cy + r * 0.12f);
                aPath.lineTo(cx + r * 0.05f, cy + r * 0.12f);
                aPath.moveTo(cx + r * 0.15f, cy + r * 0.45f);
                aPath.lineTo(cx + r * 0.42f, cy + r * 0.72f);
                aPath.lineTo(cx + r * 0.90f, cy + r * 0.15f);
                canvas.drawPath(aPath, strokePaint);
                break;
            }
            case ICON_GESTURE_SWIPE: {
                Path wave = new Path();
                wave.moveTo(cx - r * 0.85f, cy + r * 0.45f);
                wave.cubicTo(cx - r * 0.3f, cy - r * 0.95f, cx + r * 0.3f, cy + r * 0.95f, cx + r * 0.85f, cy - r * 0.45f);
                canvas.drawPath(wave, strokePaint);
                break;
            }
            case ICON_SOUND_WAVE: {
                Path spk = new Path();
                spk.moveTo(cx - r * 0.75f, cy - r * 0.28f);
                spk.lineTo(cx - r * 0.35f, cy - r * 0.28f);
                spk.lineTo(cx + r * 0.10f, cy - r * 0.70f);
                spk.lineTo(cx + r * 0.10f, cy + r * 0.70f);
                spk.lineTo(cx - r * 0.35f, cy + r * 0.28f);
                spk.lineTo(cx - r * 0.75f, cy + r * 0.28f);
                spk.close();
                canvas.drawPath(spk, strokePaint);
                RectF arc = new RectF(cx - r * 0.2f, cy - r * 0.55f, cx + r * 0.75f, cy + r * 0.55f);
                canvas.drawArc(arc, -45f, 90f, false, strokePaint);
                break;
            }
            case ICON_MIC: {
                RectF body = new RectF(cx - r * 0.32f, cy - r * 0.92f, cx + r * 0.32f, cy + r * 0.20f);
                canvas.drawRoundRect(body, r * 0.32f, r * 0.32f, strokePaint);
                RectF cup = new RectF(cx - r * 0.62f, cy - r * 0.30f, cx + r * 0.62f, cy + r * 0.42f);
                canvas.drawArc(cup, 0f, 180f, false, strokePaint);
                canvas.drawLine(cx, cy + r * 0.42f, cx, cy + r * 0.92f, strokePaint);
                canvas.drawLine(cx - r * 0.38f, cy + r * 0.92f, cx + r * 0.38f, cy + r * 0.92f, strokePaint);
                break;
            }
            case ICON_EMOJI_SMILE: {
                canvas.drawCircle(cx, cy, r * 0.88f, strokePaint);
                canvas.drawCircle(cx - r * 0.32f, cy - r * 0.20f, 1.6f * density, fillPaint);
                canvas.drawCircle(cx + r * 0.32f, cy - r * 0.20f, 1.6f * density, fillPaint);
                RectF smile = new RectF(cx - r * 0.48f, cy - r * 0.25f, cx + r * 0.48f, cy + r * 0.50f);
                canvas.drawArc(smile, 20f, 140f, false, strokePaint);
                break;
            }
            case ICON_CLIPBOARD_PAD: {
                RectF pad = new RectF(cx - r * 0.68f, cy - r * 0.65f, cx + r * 0.68f, cy + r * 0.85f);
                canvas.drawRoundRect(pad, 3f * density, 3f * density, strokePaint);
                RectF clipTop = new RectF(cx - r * 0.32f, cy - r * 0.88f, cx + r * 0.32f, cy - r * 0.48f);
                canvas.drawRoundRect(clipTop, 2f * density, 2f * density, fillPaint);
                canvas.drawLine(cx - r * 0.38f, cy - r * 0.05f, cx + r * 0.38f, cy - r * 0.05f, strokePaint);
                canvas.drawLine(cx - r * 0.38f, cy + r * 0.35f, cx + r * 0.25f, cy + r * 0.35f, strokePaint);
                break;
            }
            case ICON_DICTIONARY_BOOK: {
                RectF book = new RectF(cx - r * 0.72f, cy - r * 0.80f, cx + r * 0.72f, cy + r * 0.80f);
                canvas.drawRoundRect(book, 3f * density, 3f * density, strokePaint);
                canvas.drawLine(cx - r * 0.32f, cy - r * 0.80f, cx - r * 0.32f, cy + r * 0.80f, strokePaint);
                canvas.drawLine(cx - r * 0.05f, cy - r * 0.25f, cx + r * 0.42f, cy - r * 0.25f, strokePaint);
                canvas.drawLine(cx - r * 0.05f, cy + r * 0.15f, cx + r * 0.32f, cy + r * 0.15f, strokePaint);
                break;
            }
            case ICON_PIN: {
                Path pin = new Path();
                pin.moveTo(cx - r * 0.34f, cy - r * 0.85f);
                pin.lineTo(cx + r * 0.34f, cy - r * 0.85f);
                pin.lineTo(cx + r * 0.34f, cy - r * 0.10f);
                pin.lineTo(cx + r * 0.68f, cy + r * 0.28f);
                pin.lineTo(cx - r * 0.68f, cy + r * 0.28f);
                pin.lineTo(cx - r * 0.34f, cy - r * 0.10f);
                pin.close();
                if (filled) canvas.drawPath(pin, fillPaint);
                canvas.drawPath(pin, strokePaint);
                canvas.drawLine(cx, cy + r * 0.28f, cx, cy + r * 0.95f, strokePaint);
                break;
            }
            case ICON_PUSHPIN: {
                // دبوس مائل 45° بخطوط فقط (يُملأ لما يكون مثبّتاً)
                canvas.save();
                canvas.rotate(45f, cx, cy);
                Path pin = new Path();
                pin.moveTo(cx - r * 0.32f, cy - r * 0.95f);
                pin.lineTo(cx + r * 0.32f, cy - r * 0.95f);
                pin.lineTo(cx + r * 0.32f, cy - r * 0.15f);
                pin.lineTo(cx + r * 0.62f, cy + r * 0.2f);
                pin.lineTo(cx - r * 0.62f, cy + r * 0.2f);
                pin.lineTo(cx - r * 0.32f, cy - r * 0.15f);
                pin.close();
                if (filled) canvas.drawPath(pin, fillPaint);
                canvas.drawPath(pin, strokePaint);
                canvas.drawLine(cx, cy + r * 0.2f, cx, cy + r * 0.95f, strokePaint);
                canvas.restore();
                break;
            }
            case ICON_TRASH: {
                // سلة: غطاء بمقبض، جسم مائل قليلاً، وخطان داخليان
                float top = cy - r * 0.5f;
                canvas.drawLine(cx - r * 0.85f, top, cx + r * 0.85f, top, strokePaint);
                Path handle = new Path();
                handle.moveTo(cx - r * 0.3f, top);
                handle.lineTo(cx - r * 0.3f, cy - r * 0.85f);
                handle.lineTo(cx + r * 0.3f, cy - r * 0.85f);
                handle.lineTo(cx + r * 0.3f, top);
                canvas.drawPath(handle, strokePaint);
                Path body = new Path();
                body.moveTo(cx - r * 0.62f, top);
                body.lineTo(cx - r * 0.52f, cy + r * 0.9f);
                body.lineTo(cx + r * 0.52f, cy + r * 0.9f);
                body.lineTo(cx + r * 0.62f, top);
                canvas.drawPath(body, strokePaint);
                canvas.drawLine(cx - r * 0.2f, cy - r * 0.15f, cx - r * 0.2f, cy + r * 0.55f, strokePaint);
                canvas.drawLine(cx + r * 0.2f, cy - r * 0.15f, cx + r * 0.2f, cy + r * 0.55f, strokePaint);
                break;
            }
            case ICON_CLOSE: {
                float k = r * 0.55f;
                canvas.drawLine(cx - k, cy - k, cx + k, cy + k, strokePaint);
                canvas.drawLine(cx - k, cy + k, cx + k, cy - k, strokePaint);
                break;
            }
            case ICON_BACK_ARROW: {
                Path arrow = new Path();
                arrow.moveTo(cx + r * 0.75f, cy);
                arrow.lineTo(cx - r * 0.65f, cy);
                arrow.moveTo(cx - r * 0.15f, cy - r * 0.52f);
                arrow.lineTo(cx - r * 0.68f, cy);
                arrow.lineTo(cx - r * 0.15f, cy + r * 0.52f);
                canvas.drawPath(arrow, strokePaint);
                break;
            }
            case ICON_BACKSPACE: {
                // أيقونة حذف مرسومة (بدل رمز يونيكود "⌫" اللي يطلع مشوّهاً على بعض الأجهزة):
                // جسم خماسي الشكل متجه لليسار مع علامة X بالمنتصف، نفس شكل زر الحذف المعروف.
                Path body = new Path();
                body.moveTo(cx + r * 0.72f, cy - r * 0.55f);
                body.lineTo(cx - r * 0.05f, cy - r * 0.55f);
                body.lineTo(cx - r * 0.75f, cy);
                body.lineTo(cx - r * 0.05f, cy + r * 0.55f);
                body.lineTo(cx + r * 0.72f, cy + r * 0.55f);
                body.close();
                canvas.drawPath(body, strokePaint);
                float k = r * 0.22f;
                float xc = cx + r * 0.18f;
                canvas.drawLine(xc - k, cy - k, xc + k, cy + k, strokePaint);
                canvas.drawLine(xc - k, cy + k, xc + k, cy - k, strokePaint);
                break;
            }
            case ICON_FORWARD_ARROW: {
                // نفس سهم الرجوع لكن معكوس بالكامل (متجه لليمين)، لزر الرجوع لكيبورد الحروف
                // من قائمتي الإيموجي والحافظة، أنسب للقراءة من اليمين لليسار.
                Path arrowF = new Path();
                arrowF.moveTo(cx - r * 0.75f, cy);
                arrowF.lineTo(cx + r * 0.65f, cy);
                arrowF.moveTo(cx + r * 0.15f, cy - r * 0.52f);
                arrowF.lineTo(cx + r * 0.68f, cy);
                arrowF.lineTo(cx + r * 0.15f, cy + r * 0.52f);
                canvas.drawPath(arrowF, strokePaint);
                break;
            }
        }
    }
}
