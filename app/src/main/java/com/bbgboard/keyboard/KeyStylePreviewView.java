package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * معاينة مصغّرة لشكل أزرار الكيبورد (زرّان حرفيان + زر إدخال) بألوان السمة الحالية،
 * ترسم نفس تفاصيل كل شكل (ظل، إطار، لمعة...) بدل مربعات متشابهة.
 */
final class KeyStylePreviewView extends View {
    private final int style;
    private final int shell, keyBg, special, shadow, divider, outline, label;
    private final int enterColor; // 0 = لون الأزرار الخاصة
    private final boolean light;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF r2 = new RectF();

    KeyStylePreviewView(Context c, int style, int themeMode, int enterColor) {
        super(c);
        this.style = style;
        this.enterColor = enterColor;
        this.d = c.getResources().getDisplayMetrics().density;
        light = themeMode == KeyboardPreferences.THEME_GBOARD_WHITE;
        if (light) {
            shell = col("#EAEEF3"); keyBg = col("#FFFFFF"); special = col("#DCE2EA");
            shadow = col("#C5CDD8"); divider = col("#DCE2EA"); outline = col("#94A3B8"); label = col("#1E293B");
        } else if (themeMode == KeyboardPreferences.THEME_MIDNIGHT_SLATE) {
            shell = col("#0B1120"); keyBg = col("#1E293B"); special = col("#151F32");
            shadow = col("#090D16"); divider = col("#172236"); outline = col("#475569"); label = Color.WHITE;
        } else {
            shell = col("#111111"); keyBg = col("#262626"); special = col("#181818");
            shadow = col("#090909"); divider = col("#363636"); outline = col("#525B6C"); label = Color.WHITE;
        }
    }

    private static int col(String h) { return Color.parseColor(h); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(shell);
        canvas.drawRoundRect(0, 0, w, h, 14 * d, 14 * d, p);

        float pad = 7 * d, gap = 0;
        float kw = (w - 2 * pad) / 2f, kh = (h - 2 * pad) * 0.52f;
        // صف الحروف
        cell(canvas, pad, pad, kw, kh, false, "A");
        cell(canvas, pad + kw, pad, kw, kh, false, "B");
        // زر الإدخال (عريض)
        cell(canvas, pad, pad + kh, w - 2 * pad, h - 2 * pad - kh, true, "↵");
    }

    private void cell(Canvas c, float x, float y, float cw, float ch, boolean isSpecial, String txt) {
        float s = 0.62f; // المعاينة أصغر من الكيبورد الحقيقي
        int fill = isSpecial ? (enterColor != 0 ? enterColor : special) : keyBg;
        RectF raw = r2; raw.set(x, y, x + cw, y + ch);
        float ix, iy, radius;
        p.setStyle(Paint.Style.FILL);
        switch (style) {
            case KeyboardPreferences.STYLE_GBOARD_ROUNDED: {
                ix = 2.6f * d * s; iy = 3.6f * d * s; radius = 6.5f * d * s;
                inset(raw, ix, iy);
                p.setColor(shadow); c.drawRoundRect(r.left, r.top + 1.5f * d * s, r.right, r.bottom + 1.5f * d * s, radius, radius, p);
                p.setColor(fill); c.drawRoundRect(r, radius, radius, p); break;
            }
            case KeyboardPreferences.STYLE_MODERN_PILL: {
                inset(raw, 3.2f * d * s, 4.5f * d * s); radius = 14f * d * s;
                p.setColor(fill); c.drawRoundRect(r, radius, radius, p); break;
            }
            case KeyboardPreferences.STYLE_GBOARD_MATERIAL: {
                inset(raw, 3f * d * s, 4f * d * s);
                radius = isSpecial ? r.height() / 2f : 9.5f * d * s;
                p.setColor(fill); c.drawRoundRect(r, radius, radius, p); break;
            }
            case KeyboardPreferences.STYLE_GBOARD_BORDERLESS: {
                inset(raw, 3f * d * s, 4.5f * d * s);
                if (isSpecial) { radius = r.height() / 2f; p.setColor(fill); c.drawRoundRect(r, radius, radius, p); }
                break;
            }
            case KeyboardPreferences.STYLE_OUTLINED: {
                inset(raw, 3.2f * d * s, 4.2f * d * s); radius = 11f * d * s;
                if (isSpecial) { p.setColor(fill); c.drawRoundRect(r, radius, radius, p); }
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, 1.3f * d * s)); p.setColor(outline); p.setAlpha(190);
                c.drawRoundRect(r, radius, radius, p); p.setAlpha(255); break;
            }
            case KeyboardPreferences.STYLE_KEYCAP_3D: {
                float edge = 3.4f * d * s;
                r.set(raw.left + 2.8f * d * s, raw.top + 3.2f * d * s, raw.right - 2.8f * d * s, raw.bottom - 3.4f * d * s - edge);
                radius = 7.5f * d * s;
                p.setColor(shadow); c.drawRoundRect(r.left, r.top + edge, r.right, r.bottom + edge, radius, radius, p);
                p.setColor(fill); c.drawRoundRect(r, radius, radius, p); break;
            }
            case KeyboardPreferences.STYLE_SOFT_RAISED: {
                inset(raw, 2.6f * d * s, 3.6f * d * s); radius = 12f * d * s;
                p.setColor(shadow); c.drawRoundRect(r.left, r.top + 1.6f * d * s, r.right, r.bottom + 1.6f * d * s, radius, radius, p);
                p.setColor(fill); c.drawRoundRect(r, radius, radius, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, d * s)); p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(Color.WHITE); p.setAlpha(light ? 230 : 70);
                c.drawLine(r.left + radius * 0.7f, r.top + d * s, r.right - radius * 0.7f, r.top + d * s, p); p.setAlpha(255); break;
            }
            case KeyboardPreferences.STYLE_CIRCLES: {
                float dia = Math.min(raw.width(), raw.height()) - 6f * d * s;
                if (!isSpecial) r.set(raw.centerX() - dia / 2f, raw.centerY() - dia / 2f, raw.centerX() + dia / 2f, raw.centerY() + dia / 2f);
                else r.set(raw.left + 3f * d * s, raw.centerY() - dia / 2f, raw.right - 3f * d * s, raw.centerY() + dia / 2f);
                p.setColor(fill); c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p); break;
            }
            case KeyboardPreferences.STYLE_GLASS: {
                inset(raw, 2.8f * d * s, 3.8f * d * s); radius = 10f * d * s;
                p.setColor(fill); p.setAlpha(190); c.drawRoundRect(r, radius, radius, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, d * s));
                p.setColor(light ? col("#94A3B8") : Color.WHITE); p.setAlpha(light ? 150 : 90); c.drawRoundRect(r, radius, radius, p);
                p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.WHITE); p.setAlpha(light ? 230 : 110);
                c.drawLine(r.left + radius * 0.8f, r.top + 1.6f * d * s, r.right - radius * 0.8f, r.top + 1.6f * d * s, p); p.setAlpha(255); break;
            }
            default: { // الكلاسيكي: مستطيلات متلاصقة بفاصل عمودي
                r.set(raw);
                p.setColor(fill); c.drawRect(r, p);
                p.setColor(divider); p.setStrokeWidth(Math.max(1f, d)); c.drawLine(r.right, r.top, r.right, r.bottom, p); break;
            }
        }
        // الحرف
        p.setStyle(Paint.Style.FILL); p.setColor(isSpecial && enterColor != 0 ? Color.WHITE : label);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize((isSpecial ? 11f : 12f) * d);
        c.drawText(txt, raw.centerX(), raw.centerY() - (p.descent() + p.ascent()) / 2f, p);
        p.setAlpha(255);
    }

    private void inset(RectF raw, float dx, float dy) {
        r.set(raw.left + dx, raw.top + dy, raw.right - dx, raw.bottom - dy);
    }
}
