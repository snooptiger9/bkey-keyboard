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
    public static final int ICON_PUSHPIN = 18;
    public static final int ICON_TRASH = 19;
    public static final int ICON_MIC = 20;
    public static final int ICON_CLOCK_RECENT = 21;
    public static final int ICON_HEART = 22;
    public static final int ICON_CRESCENT_MOON = 23;
    public static final int ICON_PAW_PRINT = 24;
    public static final int ICON_CUP_DRINK = 25;
    public static final int ICON_CAR = 26;
    public static final int ICON_HASH_SYMBOLS = 27;
    public static final int ICON_SUN = 28;
    public static final int ICON_REFRESH_UPDATE = 29;

    private boolean filled = false;
    private float iconScale = 1f;
    private boolean pillBackground = false;

    public void setPillBackground(boolean pill) {
        this.pillBackground = pill;
        invalidate();
    }

    public void setStyle(float scale, float strokeDp) {
        this.iconScale = scale;
        strokePaint.setStrokeWidth(strokeDp * density);
        invalidate();
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
        invalidate();
    }

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
            case ICON_REFRESH_UPDATE: {
                // Classic "update/refresh" mark: a circular arrow, most of the way around
                // a circle with an open gap, finished off with a small triangular arrowhead
                // -- not the gear used for other settings rows, since this one is specifically
                // about fetching a newer version, not configuring something.
                float ar = r * 0.82f;
                RectF arcRect = new RectF(cx - ar, cy - ar, cx + ar, cy + ar);
                float startAngle = -200f;
                float sweep = 250f;
                canvas.drawArc(arcRect, startAngle, sweep, false, strokePaint);

                // The triangle's flat base straddles the exact point the arc stroke ends
                // at (not behind it), and its point extends further out past the curve in
                // the direction of travel -- a base placed behind the arc's end (as before)
                // let the triangle overlap back onto the stroke itself, so the head read as
                // a blurry blob instead of a clean, distinct arrowhead.
                float endAngle = startAngle + sweep;
                double endRad = Math.toRadians(endAngle);
                float baseCx = cx + (float) Math.cos(endRad) * ar;
                float baseCy = cy + (float) Math.sin(endRad) * ar;
                double tangentRad = Math.toRadians(endAngle + 90f);
                float dirX = (float) Math.cos(tangentRad);
                float dirY = (float) Math.sin(tangentRad);
                float perpX = -dirY;
                float perpY = dirX;
                float headLen = r * 0.62f;
                float headWidth = r * 0.44f;
                float tipX = baseCx + dirX * headLen;
                float tipY = baseCy + dirY * headLen;
                Path arrowHead = new Path();
                arrowHead.moveTo(tipX, tipY);
                arrowHead.lineTo(baseCx + perpX * headWidth, baseCy + perpY * headWidth);
                arrowHead.lineTo(baseCx - perpX * headWidth, baseCy - perpY * headWidth);
                arrowHead.close();
                canvas.drawPath(arrowHead, fillPaint);
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
                // Lid + body as ONE continuous path (corners are joins, not separate caps),
                // and the handle floats with a small gap above the lid instead of touching
                // it. Two separate stroked shapes meeting at the exact same point each get
                // their own round end-cap stacked on the other's, which is what made this
                // look like it was drawn twice before -- a single path has no such seam.
                float lidY = cy - r * 0.5f;
                float bodyBottomY = cy + r * 0.9f;
                Path can = new Path();
                can.moveTo(cx - r * 0.62f, bodyBottomY);
                can.lineTo(cx - r * 0.85f, lidY);
                can.lineTo(cx + r * 0.85f, lidY);
                can.lineTo(cx + r * 0.62f, bodyBottomY);
                canvas.drawPath(can, strokePaint);

                float handleGap = r * 0.14f;
                float handleTopY = cy - r * 0.95f;
                Path handle = new Path();
                handle.moveTo(cx - r * 0.28f, lidY - handleGap);
                handle.lineTo(cx - r * 0.28f, handleTopY);
                handle.lineTo(cx + r * 0.28f, handleTopY);
                handle.lineTo(cx + r * 0.28f, lidY - handleGap);
                canvas.drawPath(handle, strokePaint);

                canvas.drawLine(cx - r * 0.2f, cy - r * 0.1f, cx - r * 0.2f, cy + r * 0.55f, strokePaint);
                canvas.drawLine(cx + r * 0.2f, cy - r * 0.1f, cx + r * 0.2f, cy + r * 0.55f, strokePaint);
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

                Path arrowF = new Path();
                arrowF.moveTo(cx - r * 0.75f, cy);
                arrowF.lineTo(cx + r * 0.65f, cy);
                arrowF.moveTo(cx + r * 0.15f, cy - r * 0.52f);
                arrowF.lineTo(cx + r * 0.68f, cy);
                arrowF.lineTo(cx + r * 0.15f, cy + r * 0.52f);
                canvas.drawPath(arrowF, strokePaint);
                break;
            }
            case ICON_CLOCK_RECENT: {
                canvas.drawCircle(cx, cy, r * 0.88f, strokePaint);
                canvas.drawLine(cx, cy, cx, cy - r * 0.52f, strokePaint);
                canvas.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.18f, strokePaint);
                break;
            }
            case ICON_HEART: {
                // Same curve proportions as the familiar filled "favorite" heart icon used
                // across most apps (two round top lobes + two side curves meeting at a bottom
                // point) — proven not to look thin or odd, unlike a hand-tuned 2-curve outline.
                Path heart = new Path();
                heart.moveTo(cx, cy + r * 0.935f);
                heart.lineTo(cx - r * 0.145f, cy + r * 0.803f);
                heart.cubicTo(cx - r * 0.66f, cy + r * 0.336f, cx - r * 1.0f, cy + r * 0.028f, cx - r * 1.0f, cy - r * 0.35f);
                heart.cubicTo(cx - r * 1.0f, cy - r * 0.658f, cx - r * 0.758f, cy - r * 0.9f, cx - r * 0.45f, cy - r * 0.9f);
                heart.cubicTo(cx - r * 0.276f, cy - r * 0.9f, cx - r * 0.109f, cy - r * 0.819f, cx, cy - r * 0.691f);
                heart.cubicTo(cx + r * 0.109f, cy - r * 0.819f, cx + r * 0.276f, cy - r * 0.9f, cx + r * 0.45f, cy - r * 0.9f);
                heart.cubicTo(cx + r * 0.758f, cy - r * 0.9f, cx + r * 1.0f, cy - r * 0.658f, cx + r * 1.0f, cy - r * 0.35f);
                heart.cubicTo(cx + r * 1.0f, cy + r * 0.028f, cx + r * 0.66f, cy + r * 0.336f, cx + r * 0.145f, cy + r * 0.803f);
                heart.lineTo(cx, cy + r * 0.935f);
                heart.close();
                canvas.drawPath(heart, fillPaint);
                break;
            }
            case ICON_CRESCENT_MOON: {
                Path full = new Path();
                full.addCircle(cx, cy, r * 0.85f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + r * 0.42f, cy - r * 0.30f, r * 0.72f, Path.Direction.CW);
                full.op(cut, Path.Op.DIFFERENCE);
                // Outline-only in light mode, solid in dark mode — same crescent either way.
                canvas.drawPath(full, filled ? fillPaint : strokePaint);
                break;
            }
            case ICON_PAW_PRINT: {
                // Toe-circle layout matches the standard "pets" paw-print icon's proportions
                // (outer toes lower/wider, inner toes higher/closer together).
                RectF pad = new RectF(cx - r * 0.70f, cy - r * 0.10f, cx + r * 0.70f, cy + r * 1.0f);
                canvas.drawRoundRect(pad, r * 0.45f, r * 0.45f, fillPaint);
                float outerToeR = r * 0.25f;
                float innerToeR = r * 0.25f;
                canvas.drawCircle(cx - r * 0.75f, cy - r * 0.25f, outerToeR, fillPaint);
                canvas.drawCircle(cx - r * 0.30f, cy - r * 0.65f, innerToeR, fillPaint);
                canvas.drawCircle(cx + r * 0.30f, cy - r * 0.65f, innerToeR, fillPaint);
                canvas.drawCircle(cx + r * 0.75f, cy - r * 0.25f, outerToeR, fillPaint);
                break;
            }
            case ICON_CUP_DRINK: {
                RectF cup = new RectF(cx - r * 0.58f, cy - r * 0.40f, cx + r * 0.30f, cy + r * 0.80f);
                canvas.drawRoundRect(cup, 3f * density, 3f * density, strokePaint);
                RectF handle = new RectF(cx + r * 0.10f, cy - r * 0.05f, cx + r * 0.72f, cy + r * 0.55f);
                canvas.drawArc(handle, -90f, 180f, false, strokePaint);
                canvas.drawLine(cx - r * 0.18f, cy - r * 0.95f, cx - r * 0.08f, cy - r * 0.62f, strokePaint);
                canvas.drawLine(cx + r * 0.12f, cy - r * 0.95f, cx + r * 0.02f, cy - r * 0.62f, strokePaint);
                break;
            }
            case ICON_CAR: {
                Path body = new Path();
                body.moveTo(cx - r * 0.85f, cy + r * 0.25f);
                body.lineTo(cx - r * 0.85f, cy - r * 0.05f);
                body.lineTo(cx - r * 0.55f, cy - r * 0.45f);
                body.lineTo(cx + r * 0.35f, cy - r * 0.45f);
                body.lineTo(cx + r * 0.70f, cy - r * 0.05f);
                body.lineTo(cx + r * 0.85f, cy - r * 0.05f);
                body.lineTo(cx + r * 0.85f, cy + r * 0.25f);
                body.close();
                canvas.drawPath(body, strokePaint);
                canvas.drawCircle(cx - r * 0.45f, cy + r * 0.32f, r * 0.20f, strokePaint);
                canvas.drawCircle(cx + r * 0.45f, cy + r * 0.32f, r * 0.20f, strokePaint);
                break;
            }
            case ICON_HASH_SYMBOLS: {
                float v = r * 0.32f;
                canvas.drawLine(cx - v, cy - r * 0.78f, cx - v, cy + r * 0.78f, strokePaint);
                canvas.drawLine(cx + v, cy - r * 0.78f, cx + v, cy + r * 0.78f, strokePaint);
                canvas.drawLine(cx - r * 0.78f, cy - v, cx + r * 0.78f, cy - v, strokePaint);
                canvas.drawLine(cx - r * 0.78f, cy + v, cx + r * 0.78f, cy + v, strokePaint);
                break;
            }
            case ICON_SUN: {
                canvas.drawCircle(cx, cy, r * 0.46f, strokePaint);
                float rayInner = r * 0.72f;
                float rayOuter = r * 0.98f;
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0);
                    float x1 = cx + (float) Math.cos(a) * rayInner;
                    float y1 = cy + (float) Math.sin(a) * rayInner;
                    float x2 = cx + (float) Math.cos(a) * rayOuter;
                    float y2 = cy + (float) Math.sin(a) * rayOuter;
                    canvas.drawLine(x1, y1, x2, y2, strokePaint);
                }
                break;
            }
        }
    }
}
