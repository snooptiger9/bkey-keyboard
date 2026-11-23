package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.PopupWindow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BlackBerryKeyboardView extends View {

    public enum KeyType {
        CHAR, BACKSPACE, SHIFT, SYMBOLS, EMOJI, LANGUAGE, SPACE, PERIOD, ENTER
    }

    public static class KeyDef {
        public final String main;
        public final String sub;
        public final List<String> alts;
        public final KeyType type;
        public final float weight;
        public final RectF rect = new RectF();

        public KeyDef(String main, String sub, List<String> alts, KeyType type, float weight) {
            this.main = main;
            this.sub = sub != null ? sub : "";
            this.alts = alts != null ? alts : Collections.<String>emptyList();
            this.type = type;
            this.weight = weight;
        }

        public KeyDef(String main, String sub, List<String> alts) {
            this(main, sub, alts, KeyType.CHAR, 1f);
        }

        public KeyDef(String main, String sub) {
            this(main, sub, Collections.<String>emptyList(), KeyType.CHAR, 1f);
        }

        public KeyDef(String main) {
            this(main, "", Collections.<String>emptyList(), KeyType.CHAR, 1f);
        }
    }

    public interface KeyboardActionListener {
        void onCharacterTyped(String text);
        /** @return true if something was actually deleted (or undone); false if there was
         *  nothing before the cursor to delete, so the caller should stop repeating instead
         *  of continuing to vibrate/click on an empty field as if still actively deleting. */
        boolean onBackspaceSingle();
        boolean onSwipeLeftDeleteWord();

        boolean onSwipeSelectAll();

        boolean onSwipeDownCopyPaste();
        void onSwipeUpSuggestion(int zoneIndex);
        void onEnterKey();
        void onToggleClipboardMenu();

        void onVoiceInputRequested();
        void onToggleEmoji();
        void onLanguageChanged(boolean isArabic);
        void onSuggestionLongPress(int slotIndex, String word);

        void onCursorStep(boolean moveRight);

        void onEmojiSuggestionAccepted(String emoji);
    }

    private final KeyboardPreferences prefs;
    private final SoundController soundController;
    private KeyboardActionListener listener;

    private boolean isArabic = true;
    // Hindi (Devanagari): left-to-right like Latin, but with its own letter layout, a Shift
    // layer instead of capitals, and no auto-capitalisation.
    private boolean isHindi = false;
    // The single source of truth for which language is active; isArabic above is just
    // "== LANG_AR", kept as its own field since almost all RTL/UI logic already keys off it.
    private String currentLangCode = KeyboardPreferences.LANG_AR;

    public String getCurrentLangCode() {
        return currentLangCode;
    }
    private boolean isSymbols = false;
    private boolean isShift = false;
    private boolean isCapsLock = false;
    private boolean isResizeDragMode = false;
    // Must match the Math.min() clamp on updatedScale in ACTION_MOVE below -- this is the
    // largest scale the resize-drag bar can ever reach.
    private static final float RESIZE_DRAG_MAX_SCALE = 1.45f;
    private boolean isNumericFieldMode = false;
    private long lastShiftTapTime = 0L;

    private List<String> suggestions = Arrays.asList("السلام", "بسم", "مرحبا");
    private final RectF menuButtonRect = new RectF();
    private final RectF[] suggestionRects = new RectF[]{new RectF(), new RectF(), new RectF()};
    private boolean menuButtonPressed = false;

    private final RectF micButtonRect = new RectF();
    private boolean micButtonPressed = false;
    private boolean voiceEnabled = true;
    private boolean voiceListening = false;

    private boolean toolbarIdleMode = false;

    private PopupWindow topOverlayPopup;
    private TopOverlayCanvasView topOverlayView;

    // The resize-drag preview: a genuinely separate floating popup window, matching
    // Gboard's own mechanism (see commitResizePreview()/showResizePreviewPopup() below)
    // rather than an overlay drawn inside this same view's window.
    private PopupWindow resizePreviewPopup;
    private ResizePreviewOverlayView resizePreviewView;
    private String flyingWordText = null;
    private float flyingWordCenterX = 0f;
    private float overlayOffsetX = 0f;
    private long flyingStartTime = 0L;
    private int animatingSlotIndex = -1;
    private static final long FLY_OUTSIDE_DURATION_MS = 820L;

    private KeyDef activePreviewKey = null;

    private Runnable afterDrawAction = null;

    private boolean isLongPressPopupVisible = false;
    private boolean longPressAwaitingTap = false;

    private boolean longPressOpensEmoji = false;
    private boolean hasSlidDuringLongPress = false;
    private List<String> longPressOptions = Collections.emptyList();
    private final List<RectF> longPressCellRects = new ArrayList<>();
    private final RectF longPressPopupBoxRect = new RectF();

    private final RectF scratchKeyRect = new RectF();

    private final RectF drawRectA = new RectF();
    private final RectF drawRectB = new RectF();
    private final RectF drawRectC = new RectF();
    private int longPressHighlightedIndex = 0;

    private float longPressCellW = 44f;
    private float longPressDragBaseX = 0f;
    private int longPressBaseIndex = 0;

    private final List<List<KeyDef>> rows = new ArrayList<>();

    private final List<KeyDef> splitDuplicateKeys = new ArrayList<>();

    private float tabletKeyGapX = 0f;
    private float tabletKeyGapY = 0f;
    private static final java.util.Set<String> SPLIT_GAP_DUPLICATE_LETTERS =
            new java.util.HashSet<>(Arrays.asList("ا", "غ", "ة", "g", "v", "G", "V"));

    private final float density;
    private final float scaledDensity;
    private float keyboardScale = 1.0f;
    private float keyFontScale = 1.12f;
    private float suggestionBarScale = 0.85f;
    private float sugTextScale = 1f;
    private int themeMode = KeyboardPreferences.THEME_BLACKBERRY_DARK;
    private int keyboardStyle = KeyboardPreferences.STYLE_BLACKBERRY_FRETS;

    private final Paint bgShellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sugBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sugCenterHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sugFlashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keySpecialBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fretLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect scratchSubBounds = new Rect();
    private final Paint sugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sugPrimaryTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingWordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingBadgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupCardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupCardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint popupEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint enterArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int enterActionId = android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
    private final Paint enterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stylePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean enterCustom = false;
    private final Paint shiftActiveAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeBarPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizePillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeResetBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeResetBarPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // The dim "wash" drawn over the whole frozen keyboard while resize-drag mode is on,
    // and the blue frame marking where the keyboard will actually end up once "Done" is
    // pressed -- the same two-layer look as the system resize overlay in the reference
    // video (dimmed/frozen content behind a live preview boundary).
    private final Paint resizeScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeFrameBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float startX = 0f;
    private float startY = 0f;
    private float lastTouchX = 0f;
    private boolean isDragging = false;
    private boolean longPressConsumed = false;

    // Tracks the single finger that is allowed to drive the tap/swipe gesture state
    // (startX/startY/touchDownKey/isDragging...). When a second finger touches down
    // while the first is still on the screen -- which happens when typing fast with
    // two keys in the same row, the touches briefly overlapping -- both touches are
    // resolved as independent taps right away and multiTouchResolved is set so every
    // further move/lift from either finger is ignored until a fresh single-finger
    // press begins. Without this, Android can reassign pointer index 0 from the
    // finger that lifted to the finger still down, and the raw event.getX()/getY()
    // (no pointer index) used everywhere below would then jump from one key's
    // position to the other key's position with no matching ACTION_DOWN in between --
    // which reads exactly like one long swipe across the row, misfiring swipe-delete-
    // word or swipe-select-all and causing the random delete/selection.
    private int activePointerId = -1;
    private boolean multiTouchResolved = false;

    private KeyDef touchDownKey = null;
    private KeyDef visualPressedKey = null;

    private KeyDef bounceKey = null;
    private long bounceStartTime = 0L;
    private static final long KEY_BOUNCE_MS = 240L;

    private int touchDownSuggestionSlot = -1;
    private boolean suggestionLongPressConsumed = false;
    private long suggestionTouchDownTime = 0L;
    // A touch landing on the suggestion bar for less than this is treated as the finger
    // grazing it in transit toward the top key row while typing fast, not a deliberate tap —
    // it's ignored instead of instantly committing the word. This also gates the OTP-code
    // and screenshot chips, which live in the same bar, so it's kept short enough that a
    // real tap on any of them still feels instant.
    private static final long MIN_SUGGESTION_TAP_HOLD_MS = 40L;

    private String emojiKeySuggestion = null;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable suggestionLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchDownSuggestionSlot >= 0 && !isDragging) {
                suggestionLongPressConsumed = true;
                vibrateTap(true);
                soundController.playKeyClick();
                if (listener != null && touchDownSuggestionSlot < suggestions.size()) {
                    listener.onSuggestionLongPress(touchDownSuggestionSlot, suggestions.get(touchDownSuggestionSlot));
                }
            }
        }
    };

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchDownKey != null && !isDragging) {
                if (touchDownKey.type == KeyType.SHIFT) {
                    longPressConsumed = true;
                    activePreviewKey = null;
                    isCapsLock = !isCapsLock;
                    isShift = isCapsLock;
                    vibrateTap(true);
                    soundController.playKeyClick();
                    rebuildKeys();
                    return;
                }
                if ((touchDownKey.type == KeyType.EMOJI || touchDownKey.type == KeyType.LANGUAGE) && emojiKeySuggestion != null) {
                    // A quick tap accepts the suggested emoji, but that leaves no way to
                    // reach the full picker while a suggestion is showing. A long-press
                    // opens the drawer instead, so a different emoji is always reachable.
                    longPressConsumed = true;
                    activePreviewKey = null;
                    vibrateTap(true);
                    soundController.playKeyClick();
                    if (listener != null) listener.onToggleEmoji();
                    return;
                }
                if ((touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD || touchDownKey.type == KeyType.LANGUAGE) && !touchDownKey.alts.isEmpty()) {
                    longPressConsumed = true;
                    activePreviewKey = null;
                    vibrateTap(true);
                    soundController.playKeyClick();
                    openLongPressPopupForKey(touchDownKey);
                }
            }
        }
    };

    private long backspaceRepeatStartUptime = 0L;
    // Repeat stays at a steady pace for a safe margin after it kicks in, so a normal
    // press-and-hold never feels like it's "running away" -- only a deliberately long
    // hold ramps up, and even then it eases toward a floor instead of jumping there.
    private static final long BACKSPACE_ACCEL_SAFE_PERIOD_MS = 1200L;
    private static final long BACKSPACE_ACCEL_STEP_MS = 40L;
    private static final long BACKSPACE_REPEAT_BASE_MS = 60L;
    private static final long BACKSPACE_REPEAT_FLOOR_MS = 15L;
    private final Runnable repeatBackspaceRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchDownKey != null && touchDownKey.type == KeyType.BACKSPACE && !isDragging) {
                boolean deleted = listener != null && listener.onBackspaceSingle();
                if (!deleted) {
                    // Nothing left before the cursor -- stop repeating instead of carrying on
                    // with haptic/sound ticks every frame as if still actively deleting.
                    return;
                }
                vibrateTap(false);
                soundController.playBackspaceClick();

                long heldMs = SystemClock.uptimeMillis() - backspaceRepeatStartUptime;
                long nextDelay = BACKSPACE_REPEAT_BASE_MS;
                if (heldMs > BACKSPACE_ACCEL_SAFE_PERIOD_MS) {
                    long accelMs = heldMs - BACKSPACE_ACCEL_SAFE_PERIOD_MS;
                    nextDelay = Math.max(BACKSPACE_REPEAT_FLOOR_MS,
                            BACKSPACE_REPEAT_BASE_MS - (accelMs / BACKSPACE_ACCEL_STEP_MS));
                }
                handler.postDelayed(this, nextDelay);
            }
        }
    };

    private boolean spaceCursorModeActive = false;
    private float spaceCursorStepBaseX = 0f;
    private final Runnable spaceCursorHoldRunnable = new Runnable() {
        @Override
        public void run() {

            if (touchDownKey != null && touchDownKey.type == KeyType.SPACE
                    && Math.abs(lastTouchX - startX) < 14f * density) {
                spaceCursorModeActive = true;
                spaceCursorStepBaseX = startX;
                vibrateTap(true);
            }
        }
    };

    private class TopOverlayCanvasView extends View {
        public TopOverlayCanvasView(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (flyingWordText == null) return;

            long elapsed = SystemClock.uptimeMillis() - flyingStartTime;
            if (elapsed >= FLY_OUTSIDE_DURATION_MS) {
                flyingWordText = null;
                animatingSlotIndex = -1;
                BlackBerryKeyboardView.this.invalidate();
                return;
            }

            float progress = (float) elapsed / (float) FLY_OUTSIDE_DURATION_MS;
            float startYInPopup = getHeight() - (getSuggestionBarHeight() * 0.45f);
            drawGhostFlyingWord(canvas, flyingWordText, flyingWordCenterX + overlayOffsetX, startYInPopup, progress);

            postInvalidateOnAnimation();
            BlackBerryKeyboardView.this.postInvalidateOnAnimation();
        }
    }

    // The resize-drag preview, matching Gboard's real mechanism: a genuinely separate
    // floating window (see showResizePreviewPopup()/commitResizePreview() above) that
    // sits ON TOP of the real keyboard -- which never resizes or redraws for any of
    // this -- rather than an overlay drawn inside the keyboard's own view/window. Its
    // background is fully transparent, so the real keyboard underneath shows through
    // naturally below the dimmed/boundary area this draws, with no painted fill of its
    // own standing in for it.
    private class ResizePreviewOverlayView extends View {
        float previewScale = 1.0f;
        // The scale the current drag started from -- always previewScale's value at
        // ACTION_DOWN, never the committed keyboardScale directly.
        float resizeStartScale = 1.0f;
        boolean isResizingDrag = false;
        boolean resizeDoneButtonPressed = false;
        boolean resizeResetButtonPressed = false;
        float dragStartY = 0f;

        // The fixed height this popup was created at (the largest size the preview
        // could ever reach, reserved once up front -- see showResizePreviewPopup()).
        // Used instead of getHeight() for every rect/boundary calculation so none of it
        // depends on this view's own layout pass having already landed.
        int totalPreviewHeightPx = 0;

        final RectF resizeHandleBarRect = new RectF();
        final RectF resizeDoneButtonRect = new RectF();
        final RectF resizeResetButtonRect = new RectF();
        final RectF resizeDragHandlePillRect = new RectF();

        ResizePreviewOverlayView(Context ctx) {
            super(ctx);
        }

        // Positions the floating boundary band (the handle bar represented by
        // resizeHandleBarRect, with Reset/move-handle/Done riding on it) at the height
        // the keyboard would be at `scale`. Cheap, pure arithmetic off
        // totalPreviewHeightPx -- safe to call on every single ACTION_MOVE of a drag.
        void computeOverlayRects(int w, float scale) {
            float barH = 36f * density;
            float boundaryTopY = Math.max(0f, totalPreviewHeightPx - contentHeightForScale(scale));

            resizeHandleBarRect.set(0f, boundaryTopY, w, boundaryTopY + barH);

            float btnH = barH - 8f * density;
            // Reset and Done are deliberately the exact same width -- not sized to fit
            // their own label -- so the two pills always read as a matched pair however
            // long either language's word for "Reset" turns out to be (Arabic
            // "استعادة" is much longer than "تم").
            float btnW = 78f * density;
            float btnTop = boundaryTopY + (barH - btnH) / 2f;

            resizeResetButtonRect.set(8f * density, btnTop, 8f * density + btnW, btnTop + btnH);
            resizeDoneButtonRect.set(w - 8f * density - btnW, btnTop, w - 8f * density, btnTop + btnH);

            float pillSize = 34f * density;
            float pillTop = boundaryTopY + (barH - pillSize) / 2f;
            resizeDragHandlePillRect.set((w - pillSize) / 2f, pillTop, (w + pillSize) / 2f, pillTop + pillSize);
        }

        // One modern pill: rounded fill, hairline ring, a drawn icon (check or reset arrow)
        // and a bold label, centred as a group. Text shrinks if a language's word is long.
        private void drawResizePill(Canvas canvas, RectF r, Paint fill, boolean check, String label) {
            float rad = r.height() / 2f;
            canvas.drawRoundRect(r, rad, rad, fill);
            resizeRingPaint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(r, rad, rad, resizeRingPaint);

            float iconSize = 12f * density, gap = 5f * density;
            float maxText = r.width() - iconSize - gap - 18f * density;
            float size = 11.5f * scaledDensity;
            sugPrimaryTextPaint.setTextSize(size);
            float tw = sugPrimaryTextPaint.measureText(label);
            if (tw > maxText) {
                size = size * maxText / tw;
                sugPrimaryTextPaint.setTextSize(size);
                tw = sugPrimaryTextPaint.measureText(label);
            }
            float total = iconSize + gap + tw;
            float startX = r.centerX() - total / 2f;
            float cx = startX + iconSize / 2f, cy = r.centerY();
            float ir = iconSize / 2f;
            resizeIconPaint.setStrokeWidth(1.9f * density);
            if (check) {
                Path ck = new Path();
                ck.moveTo(cx - ir * 0.75f, cy + ir * 0.05f);
                ck.lineTo(cx - ir * 0.2f, cy + ir * 0.6f);
                ck.lineTo(cx + ir * 0.8f, cy - ir * 0.55f);
                canvas.drawPath(ck, resizeIconPaint);
            } else {
                RectF arc = new RectF(cx - ir * 0.8f, cy - ir * 0.8f, cx + ir * 0.8f, cy + ir * 0.8f);
                canvas.drawArc(arc, 30f, 290f, false, resizeIconPaint);
                // arrowhead at the arc's end (330 degrees), pointing along the sweep
                double a = Math.toRadians(320);
                float px = cx + (float) (ir * 0.8f * Math.cos(a)), py = cy + (float) (ir * 0.8f * Math.sin(a));
                float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
                float nx = -dy, ny = dx, hl = ir * 0.65f;
                canvas.drawLine(px, py, px - dx * hl + nx * hl * 0.8f, py - dy * hl + ny * hl * 0.8f, resizeIconPaint);
                canvas.drawLine(px, py, px - dx * hl - nx * hl * 0.8f, py - dy * hl - ny * hl * 0.8f, resizeIconPaint);
            }
            float ty = r.centerY() - (sugPrimaryTextPaint.descent() + sugPrimaryTextPaint.ascent()) / 2f;
            canvas.drawText(label, startX + iconSize + gap, ty, sugPrimaryTextPaint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float boundaryTop = resizeHandleBarRect.top;
            float w = getWidth();
            float h = totalPreviewHeightPx;

            // Dim everything ABOVE the preview boundary only -- never below it. Below
            // the line is the real keyboard showing through this popup's transparent
            // background, which must stay at full brightness; above the line is either
            // bare space the keyboard hasn't grown into yet (growing) or the part about
            // to be cut off (shrinking) -- dimming it either way is the "frozen/inactive"
            // look the reference video has.
            canvas.drawRect(0f, 0f, w, boundaryTop, resizeScrimPaint);

            canvas.drawLine(0f, boundaryTop, w, boundaryTop, resizeFrameBorderPaint);
            canvas.drawLine(0f, boundaryTop, 0f, h, resizeFrameBorderPaint);
            canvas.drawLine(w, boundaryTop, w, h, resizeFrameBorderPaint);

            int prevHandleColor = sugPrimaryTextPaint.getColor();
            Paint.Align prevHandleAlign = sugPrimaryTextPaint.getTextAlign();
            float prevHandleSize = sugPrimaryTextPaint.getTextSize();
            Typeface prevHandleFace = sugPrimaryTextPaint.getTypeface();
            sugPrimaryTextPaint.setTextAlign(Paint.Align.LEFT);
            sugPrimaryTextPaint.setColor(Color.WHITE);
            sugPrimaryTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

            String resetLabel = isArabic ? "استعادة"
                    : KeyboardPreferences.LANG_ES.equals(currentLangCode) ? "Restablecer"
                    : KeyboardPreferences.LANG_HI.equals(currentLangCode) ? "रीसेट"
                    : "Reset";
            drawResizePill(canvas, resizeResetButtonRect, resizeResetButtonPressed ? resizeResetBarPressedPaint : resizeResetBarPaint,
                    false, resetLabel);

            String doneLabel = isArabic ? "تم"
                    : KeyboardPreferences.LANG_ES.equals(currentLangCode) ? "Listo"
                    : KeyboardPreferences.LANG_HI.equals(currentLangCode) ? "हो गया"
                    : "Done";
            drawResizePill(canvas, resizeDoneButtonRect, resizeDoneButtonPressed ? resizeBarPressedPaint : resizeBarPaint,
                    true, doneLabel);

            // Centre grip: glass pill with an up/down chevron pair (drawn, not a text glyph).
            float gr = resizeDragHandlePillRect.height() / 2f;
            canvas.drawRoundRect(resizeDragHandlePillRect, gr, gr, resizePillPaint);
            resizeRingPaint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(resizeDragHandlePillRect, gr, gr, resizeRingPaint);
            float gx = resizeDragHandlePillRect.centerX(), gy = resizeDragHandlePillRect.centerY();
            float cw = 5f * density, ch = 3f * density, gap = 2.2f * density;
            resizeIconPaint.setStrokeWidth(1.8f * density);
            canvas.drawLine(gx - cw, gy - gap, gx, gy - gap - ch, resizeIconPaint);
            canvas.drawLine(gx, gy - gap - ch, gx + cw, gy - gap, resizeIconPaint);
            canvas.drawLine(gx - cw, gy + gap, gx, gy + gap + ch, resizeIconPaint);
            canvas.drawLine(gx, gy + gap + ch, gx + cw, gy + gap, resizeIconPaint);

            sugPrimaryTextPaint.setTypeface(prevHandleFace);
            sugPrimaryTextPaint.setColor(prevHandleColor);
            sugPrimaryTextPaint.setTextAlign(prevHandleAlign);
            sugPrimaryTextPaint.setTextSize(prevHandleSize);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    resizeDoneButtonPressed = false;
                    resizeResetButtonPressed = false;
                    isResizingDrag = false;
                    if (y >= resizeHandleBarRect.top && y <= resizeHandleBarRect.bottom) {
                        if (resizeDoneButtonRect.contains(x, y)) {
                            resizeDoneButtonPressed = true;
                            invalidate();
                            return true;
                        }
                        if (resizeResetButtonRect.contains(x, y)) {
                            resizeResetButtonPressed = true;
                            invalidate();
                            return true;
                        }
                        isResizingDrag = true;
                        dragStartY = y;
                        resizeStartScale = previewScale;
                        return true;
                    }
                    // A touch anywhere else in the popup (above the boundary, on the
                    // dimmed area) is still swallowed here so it can never reach the
                    // real keyboard window underneath -- the whole point of this being
                    // a separate, touchable popup on top of it.
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isResizingDrag) {
                        float dyMove = y - dragStartY;
                        float deltaScale = -dyMove / (220f * density);
                        float updatedScale = Math.max(0.70f, Math.min(RESIZE_DRAG_MAX_SCALE, resizeStartScale + deltaScale));
                        if (Math.abs(updatedScale - previewScale) > 0.004f) {
                            // Nothing here ever touches the real keyboard's window or
                            // key bounds -- only this popup's own previewScale and the
                            // lightweight boundary/controls rects computed from it,
                            // which is what keeps even a fast drag completely free of
                            // any flicker. The real resize happens exactly once, only
                            // when Done is pressed (see commitResizePreview()).
                            previewScale = updatedScale;
                            computeOverlayRects(getWidth(), previewScale);
                            invalidate();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean wasDonePressed = resizeDoneButtonPressed;
                    boolean wasResetPressed = resizeResetButtonPressed;
                    boolean wasResizing = isResizingDrag;
                    resizeDoneButtonPressed = false;
                    resizeResetButtonPressed = false;
                    isResizingDrag = false;
                    invalidate();

                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        return true;
                    }

                    if (wasDonePressed && resizeDoneButtonRect.contains(x, y)) {
                        BlackBerryKeyboardView.this.commitResizePreview(previewScale);
                        return true;
                    }
                    if (wasResetPressed && resizeResetButtonRect.contains(x, y)) {
                        // Resets the live preview back to the default size -- same as
                        // the reference video's "Reset" pill -- but stays open so the
                        // new size is only actually applied once Done is pressed, like
                        // every other change made while this popup is open.
                        previewScale = KeyboardPreferences.DEFAULT_KEYBOARD_SCALE;
                        computeOverlayRects(getWidth(), previewScale);
                        soundController.playKeyClick();
                        invalidate();
                        return true;
                    }
                    if (wasResizing) {
                        // Committing is reserved for "Done" alone -- lifting the finger
                        // (or a tap on the handle itself) just ends this particular drag
                        // gesture and leaves the preview exactly where it is, so the
                        // user can keep adjusting before pressing Done.
                        return true;
                    }
                    return true;
                }

                default:
                    return true;
            }
        }
    }

    private void drawGhostFlyingWord(Canvas canvas, String word, float cx, float startBaselineY, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        float inv = 1f - p;
        float eased = 1f - (inv * inv * inv);
        float y = startBaselineY - eased * 78f * density;

        float alphaFactor;
        if (p < 0.12f) {
            alphaFactor = p / 0.12f;
        } else {
            float t = (p - 0.12f) / 0.88f;
            alphaFactor = 1f - t * t;
        }
        int alpha = Math.max(0, Math.min(255, (int) (alphaFactor * 0.62f * 255f)));
        float scale = 1.0f + 0.10f * eased;

        flyingWordPaint.setTextSize(16.5f * scaledDensity * scale);
        flyingWordPaint.setAlpha(alpha);
        flyingWordPaint.setShadowLayer(3f * density, 0f, 1f * density, Color.argb((int) (alpha * 0.5f), 0, 0, 0));
        canvas.drawText(word, cx, y, flyingWordPaint);
        flyingWordPaint.clearShadowLayer();
        flyingWordPaint.setAlpha(255);
    }

    private static Typeface loadKeyTypeface(Context context, int fontId) {
        String assetPath;
        switch (fontId) {
            case KeyboardPreferences.FONT_TAJAWAL: assetPath = "fonts/tajawal_bold.ttf"; break;
            case KeyboardPreferences.FONT_ALMARAI: assetPath = "fonts/almarai_bold.ttf"; break;
            case KeyboardPreferences.FONT_TAJAWAL_REGULAR: assetPath = "fonts/tajawal_regular.ttf"; break;
            case KeyboardPreferences.FONT_ALMARAI_REGULAR: assetPath = "fonts/almarai_regular.ttf"; break;
            case KeyboardPreferences.FONT_INTER: assetPath = "fonts/inter_semibold.ttf"; break;
            case KeyboardPreferences.FONT_POPPINS: assetPath = "fonts/poppins_medium.ttf"; break;
            case KeyboardPreferences.FONT_OPEN_SANS: assetPath = "fonts/opensans_semibold.ttf"; break;
            case KeyboardPreferences.FONT_LATO: assetPath = "fonts/lato_bold.ttf"; break;
            case KeyboardPreferences.FONT_SYSTEM_DEFAULT_BOLD:
                return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
            default: return Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        }
        try {
            return Typeface.createFromAsset(context.getAssets(), assetPath);
        } catch (Exception e) {
            return Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        }
    }

    private Typeface popupTypefaceFor(int fontId) {
        return loadKeyTypeface(getContext(), fontId);
    }

    public BlackBerryKeyboardView(Context context, KeyboardPreferences prefs, SoundController soundController) {
        super(context);
        this.prefs = prefs;
        this.soundController = soundController;
        this.density = getResources().getDisplayMetrics().density;
        this.scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        mainTextPaint.setTextAlign(Paint.Align.CENTER);
        mainTextPaint.setTypeface(loadKeyTypeface(context, prefs.getKeyFont()));
        sugTextPaint.setTypeface(mainTextPaint.getTypeface());

        subTextPaint.setTextAlign(Paint.Align.RIGHT);

        sugTextPaint.setTextAlign(Paint.Align.CENTER);

        sugPrimaryTextPaint.setTextAlign(Paint.Align.CENTER);
        sugPrimaryTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        flyingWordPaint.setTextAlign(Paint.Align.CENTER);
        flyingWordPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        flyingBadgeBorderPaint.setStyle(Paint.Style.STROKE);
        flyingBadgeBorderPaint.setStrokeWidth(1.6f * density);

        popupCardBorderPaint.setStyle(Paint.Style.STROKE);
        popupCardBorderPaint.setStrokeWidth(1.5f * density);
        popupEdgePaint.setStyle(Paint.Style.STROKE);
        popupEdgePaint.setStrokeWidth(1f * density);

        popupHighlightPaint.setStyle(Paint.Style.FILL);
        popupHighlightPaint.setColor(Color.parseColor("#2563EB"));

        popupTextPaint.setTextAlign(Paint.Align.CENTER);
        popupTextPaint.setTypeface(popupTypefaceFor(prefs.getKeyFont()));

        iconStrokePaint.setStyle(Paint.Style.STROKE);
        iconStrokePaint.setStrokeWidth(2.1f * density);
        iconStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        enterArrowPaint.setStyle(Paint.Style.STROKE);
        enterArrowPaint.setStrokeWidth(3.0f * density);
        enterArrowPaint.setStrokeCap(Paint.Cap.ROUND);
        enterArrowPaint.setStrokeJoin(Paint.Join.ROUND);

        shiftActiveAccentPaint.setColor(Color.parseColor("#3B82F6"));
        shiftActiveAccentPaint.setStrokeWidth(2.6f * density);
        shiftActiveAccentPaint.setStrokeCap(Paint.Cap.ROUND);

        resizeRingPaint.setStyle(Paint.Style.STROKE);
        resizeRingPaint.setColor(Color.argb(90, 255, 255, 255));
        resizeIconPaint.setStyle(Paint.Style.STROKE);
        resizeIconPaint.setColor(Color.WHITE);
        resizeIconPaint.setStrokeCap(Paint.Cap.ROUND);
        resizeIconPaint.setStrokeJoin(Paint.Join.ROUND);
        resizeBarPaint.setColor(Color.parseColor("#059669"));
        resizeBarPressedPaint.setColor(Color.parseColor("#047857"));
        resizePillPaint.setColor(Color.parseColor("#B34B5563"));
        resizeResetBarPaint.setColor(Color.parseColor("#4B5563"));
        resizeResetBarPressedPaint.setColor(Color.parseColor("#374151"));
        // A near-black translucent scrim is indistinguishable from the keyboard's own
        // already-dark background -- it just reads as "nothing happened". A visibly
        // lighter slate tint shows clearly as a deliberate panel instead, on any theme.
        resizeScrimPaint.setColor(Color.parseColor("#99334155"));
        resizeFrameBorderPaint.setStyle(Paint.Style.STROKE);
        resizeFrameBorderPaint.setStrokeWidth(2.4f * density);
        resizeFrameBorderPaint.setColor(Color.parseColor("#3B82F6"));
        fretLinePaint.setStrokeWidth(2.4f * density);
        keyDividerPaint.setStrokeWidth(0.8f * density);

        setHapticFeedbackEnabled(true);
        reloadAppearanceFromPrefs();
        rebuildKeys();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        disableParentClipping();
    }

    @Override
    protected void onDetachedFromWindow() {
        // Defensive: if the view is torn down without Android first delivering an
        // ACTION_CANCEL (which is what normally cancels these), a self-rescheduling
        // runnable like the backspace-repeat timer could otherwise keep firing after
        // the view is gone.
        handler.removeCallbacksAndMessages(null);
        dismissFlyingPopup();
        dismissResizePreviewPopup();
        super.onDetachedFromWindow();
    }

    private void disableParentClipping() {
        ViewParent p = getParent();
        while (p instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) p;
            vg.setClipChildren(false);
            vg.setClipToPadding(false);
            p = vg.getParent();
        }
    }

    public void dismissFlyingPopup() {
        isLongPressPopupVisible = false;
        longPressAwaitingTap = false;
        activePreviewKey = null;
        if (topOverlayPopup != null) {
            try {
                if (topOverlayPopup.isShowing()) {
                    topOverlayPopup.dismiss();
                }
            } catch (Exception e) {
                AppLogger.log("BlackBerryKeyboardView", "dismissFlyingPopup()", e);
            }
        }
    }

    public void reloadAppearanceFromPrefs() {
        this.keyboardScale = prefs.getKeyboardScale();
        this.keyFontScale = prefs.getKeyFontScale();
        this.suggestionBarScale = prefs.getSuggestionBarScale();
        this.sugTextScale = 1f + 0.08f * prefs.getSuggestionTextLevel();
        this.themeMode = prefs.getThemeMode();
        this.keyboardStyle = prefs.getKeyboardStyle();
        mainTextPaint.setTypeface(loadKeyTypeface(getContext(), prefs.getKeyFont()));
        sugTextPaint.setTypeface(mainTextPaint.getTypeface());
        popupTextPaint.setTypeface(popupTypefaceFor(prefs.getKeyFont()));
        applyLanguageMode();

        applyThemeColors();
        applyEnterColor();
        requestLayout();
        computeKeyBounds(getWidth());
        rebuildKeys();
    }

    private void applyEnterColor() {
        int c = prefs.getEnterColor();
        enterCustom = c != 0;
        if (enterCustom) {
            enterFillPaint.setColor(c);
            enterArrowPaint.setColor(KeyStylePreviewView.isLightColor(c) ? Color.parseColor("#0B1B3A") : Color.WHITE);
        }
    }

    private void applyThemeColors() {
        if (themeMode == KeyboardPreferences.THEME_GBOARD_WHITE) {
            bgShellPaint.setColor(Color.parseColor("#EAEEF3"));
            sugBarPaint.setColor(Color.parseColor("#F5F7FA"));
            sugCenterHighlightPaint.setColor(Color.parseColor("#E8EEF5"));
            sugFlashPaint.setColor(Color.parseColor("#DBEAFE"));
            keyBgPaint.setColor(Color.parseColor("#FFFFFF"));
            keyShadowPaint.setColor(Color.parseColor("#C5CDD8"));
            keySpecialBgPaint.setColor(Color.parseColor("#DCE2EA"));
            keyPressedPaint.setColor(Color.parseColor("#CBD5E1"));
            fretLinePaint.setColor(Color.parseColor("#D5DCE6"));
            keyDividerPaint.setColor(Color.parseColor("#DCE2EA"));
            mainTextPaint.setColor(Color.parseColor("#1E293B"));
            subTextPaint.setColor(Color.parseColor("#64748B"));
            sugTextPaint.setColor(Color.parseColor("#334155"));
            sugPrimaryTextPaint.setColor(Color.parseColor("#0F172A"));
            flyingWordPaint.setColor(Color.parseColor("#0F172A"));
            flyingBadgeBgPaint.setColor(Color.parseColor("#FFFFFF"));
            flyingBadgeBorderPaint.setColor(Color.parseColor("#2563EB"));
            popupCardBgPaint.setColor(keyBgPaint.getColor());
            popupCardBorderPaint.setColor(Color.parseColor("#94A3B8"));
            popupTextPaint.setColor(Color.parseColor("#0F172A"));
            popupEdgePaint.setColor(Color.parseColor("#C5CDD8"));
            iconStrokePaint.setColor(Color.parseColor("#334155"));
            enterArrowPaint.setColor(Color.parseColor("#1E293B"));
        } else if (themeMode == KeyboardPreferences.THEME_MIDNIGHT_SLATE) {
            bgShellPaint.setColor(Color.parseColor("#0B1120"));
            sugBarPaint.setColor(Color.parseColor("#111827"));
            sugCenterHighlightPaint.setColor(Color.parseColor("#1E293B"));
            sugFlashPaint.setColor(Color.parseColor("#1E3A8A"));
            keyBgPaint.setColor(Color.parseColor("#1E293B"));
            keyShadowPaint.setColor(Color.parseColor("#090D16"));
            keySpecialBgPaint.setColor(Color.parseColor("#151F32"));
            keyPressedPaint.setColor(Color.parseColor("#334155"));
            fretLinePaint.setColor(Color.parseColor("#26334D"));
            keyDividerPaint.setColor(Color.parseColor("#172236"));
            mainTextPaint.setColor(Color.parseColor("#F8FAFC"));
            subTextPaint.setColor(Color.parseColor("#94A3B8"));
            sugTextPaint.setColor(Color.parseColor("#E2E8F0"));
            sugPrimaryTextPaint.setColor(Color.WHITE);
            flyingWordPaint.setColor(Color.WHITE);
            flyingBadgeBgPaint.setColor(Color.parseColor("#1E293B"));
            flyingBadgeBorderPaint.setColor(Color.parseColor("#38BDF8"));
            popupCardBgPaint.setColor(keyBgPaint.getColor());
            popupCardBorderPaint.setColor(Color.parseColor("#475569"));
            popupTextPaint.setColor(Color.WHITE);
            popupEdgePaint.setColor(Color.parseColor("#334155"));
            iconStrokePaint.setColor(Color.parseColor("#E2E8F0"));
            enterArrowPaint.setColor(Color.WHITE);
        } else if (themeMode == KeyboardPreferences.THEME_FOREST_GREEN) {
            bgShellPaint.setColor(Color.parseColor("#0A1511"));
            sugBarPaint.setColor(Color.parseColor("#0F1D17"));
            sugCenterHighlightPaint.setColor(Color.parseColor("#17291F"));
            sugFlashPaint.setColor(Color.parseColor("#14532D"));
            keyBgPaint.setColor(Color.parseColor("#1B3126"));
            keyShadowPaint.setColor(Color.parseColor("#07100C"));
            keySpecialBgPaint.setColor(Color.parseColor("#12231B"));
            keyPressedPaint.setColor(Color.parseColor("#2A4A39"));
            fretLinePaint.setColor(Color.parseColor("#264034"));
            keyDividerPaint.setColor(Color.parseColor("#16281F"));
            mainTextPaint.setColor(Color.parseColor("#F0FDF4"));
            subTextPaint.setColor(Color.parseColor("#86A896"));
            sugTextPaint.setColor(Color.parseColor("#DCFCE7"));
            sugPrimaryTextPaint.setColor(Color.WHITE);
            flyingWordPaint.setColor(Color.WHITE);
            flyingBadgeBgPaint.setColor(Color.parseColor("#1B3126"));
            flyingBadgeBorderPaint.setColor(Color.parseColor("#34D399"));
            popupCardBgPaint.setColor(keyBgPaint.getColor());
            popupCardBorderPaint.setColor(Color.parseColor("#3F6B52"));
            popupTextPaint.setColor(Color.WHITE);
            popupEdgePaint.setColor(Color.parseColor("#2A4A39"));
            iconStrokePaint.setColor(Color.parseColor("#DCFCE7"));
            enterArrowPaint.setColor(Color.WHITE);
        } else {
            bgShellPaint.setColor(Color.parseColor("#111111"));
            sugBarPaint.setColor(Color.parseColor("#171717"));
            sugCenterHighlightPaint.setColor(Color.parseColor("#20232A"));
            sugFlashPaint.setColor(Color.parseColor("#2E2E2E"));
            keyBgPaint.setColor(Color.parseColor("#262626"));
            keyShadowPaint.setColor(Color.parseColor("#090909"));
            keySpecialBgPaint.setColor(Color.parseColor("#181818"));
            keyPressedPaint.setColor(Color.parseColor("#3A3A3A"));
            fretLinePaint.setColor(Color.parseColor("#3B3B3B"));
            keyDividerPaint.setColor(Color.parseColor("#363636"));
            mainTextPaint.setColor(Color.WHITE);
            subTextPaint.setColor(Color.parseColor("#9CA3AF"));
            sugTextPaint.setColor(Color.parseColor("#E5E7EB"));
            sugPrimaryTextPaint.setColor(Color.WHITE);
            flyingWordPaint.setColor(Color.WHITE);
            flyingBadgeBgPaint.setColor(Color.parseColor("#222631"));
            flyingBadgeBorderPaint.setColor(Color.parseColor("#10B981"));
            popupCardBgPaint.setColor(keyBgPaint.getColor());
            popupCardBorderPaint.setColor(Color.parseColor("#525B6C"));
            popupTextPaint.setColor(Color.WHITE);
            popupEdgePaint.setColor(Color.parseColor("#3A3A3A"));
            iconStrokePaint.setColor(Color.parseColor("#E5E7EB"));
            enterArrowPaint.setColor(Color.WHITE);
        }
    }

    public int getShellColor() {
        return bgShellPaint.getColor();
    }

    public boolean isLightTheme() {
        return themeMode == KeyboardPreferences.THEME_GBOARD_WHITE;
    }

    private float getSuggestionBarHeight() {
        return 36f * density * suggestionBarScale;
    }

    private float getKeyRowHeight() {
        return keyRowHeightForScale(keyboardScale);
    }

    private float getBottomRowHeight() {
        return bottomRowHeightForScale(keyboardScale);
    }

    private float keyRowHeightForScale(float scale) {
        return 52f * density * scale;
    }

    private float bottomRowHeightForScale(float scale) {
        return 50f * density * scale;
    }

    private float extraBottomRowGapPx() {
        return 0f;
    }

    private int keyRowCount() {
        return Math.max(3, rows.size() - 1);
    }

    // Total height the real (committed) keyboard content needs at the given scale --
    // same formula onMeasure() uses, just parameterized so the resize-preview overlay
    // can ask "where would the boundary be at scale X" without touching onMeasure.
    private float contentHeightForScale(float scale) {
        return getSuggestionBarHeight() + keyRowHeightForScale(scale) * keyRowCount() + bottomRowHeightForScale(scale) + extraBottomRowGapPx();
    }

    private void vibrateTap(boolean longPress) {
        if (!prefs.isVibrationEnabled()) return;
        int ms = prefs.getVibrationMs();
        if (ms > 0) {
            try {
                android.os.Vibrator vib = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null && vib.hasVibrator()) {
                    int d = longPress ? Math.min(60, ms * 3 / 2) : ms;
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        vib.vibrate(android.os.VibrationEffect.createOneShot(d, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vib.vibrate(d);
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }
        performHapticFeedback(longPress ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public void setKeyboardActionListener(KeyboardActionListener l) {
        this.listener = l;
    }

    public boolean isArabic() {
        return isArabic;
    }

    public boolean isHindi() {
        return isHindi;
    }

    public int getActiveKeyboardStyle() {
        return keyboardStyle;
    }

    public int getSuggestionBarColor() {
        return sugBarPaint.getColor();
    }

    public android.graphics.Bitmap renderKeyChipBitmap(int w, int h) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(Math.max(1, w), Math.max(1, h),
                android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        KeyDef chip = new KeyDef("a");
        chip.rect.set(0f, 0f, w, h);
        drawStyledKeyCell(c, chip, keyBgPaint);
        return bmp;
    }

    public int getKeySpecialBgColor() {
        return keySpecialBgPaint.getColor();
    }

    public int getKeyShadowColor() {
        return keyShadowPaint.getColor();
    }

    public int getKeyBgColor() {
        return keyBgPaint.getColor();
    }

    public int getKeyPressedColor() {
        return keyPressedPaint.getColor();
    }

    public int getKeyDividerColor() {
        return keyDividerPaint.getColor();
    }

    public int getKeyOutlineColor() {
        return popupCardBorderPaint.getColor();
    }

    public int getKeyLabelColor() {
        return mainTextPaint.getColor();
    }

    public float getSuggestionBarHeightPx() {
        return getSuggestionBarHeight();
    }

    public float getSuggestionBarTopPx() {
        return 0f;
    }

    public float getBottomRowHeightPx() {
        return getBottomRowHeight();
    }

    public float getBottomRowEdgeInsetPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? widthPx * 0.03f : SIDE_MARGIN_DP * density;
    }

    private static final float SIDE_MARGIN_DP = 1.5f;

    private static final float ENGLISH_ROW2_INDENT_KEYS = 0.35f;

    public float getSymbolsKeyWidthPx(int widthPx) {
        List<KeyDef> bottomRow = buildBottomRow();
        if (bottomRow.isEmpty()) return 0f;
        boolean tablet = isSplitKeyboardActive(widthPx);
        float totalWeight = 0f;
        for (KeyDef k : bottomRow) totalWeight += bottomRowWeight(k, tablet);
        float rowEdge = getBottomRowEdgeInsetPx(widthPx);
        float availRowW = widthPx - rowEdge * 2f;
        return totalWeight > 0f ? (bottomRowWeight(bottomRow.get(0), tablet) / totalWeight) * availRowW : 0f;
    }

    public void setResizeDragMode(boolean enabled) {
        if (this.isResizeDragMode == enabled) return;
        this.isResizeDragMode = enabled;
        // Unlike every earlier version of this feature, turning resize-drag mode on or
        // off never touches this view's own size or layout any more -- the live preview
        // lives entirely in a separate floating popup window (see
        // showResizePreviewPopup()/dismissResizePreviewPopup()), which can sit above the
        // real keyboard without it (or the real IME window behind it) ever resizing.
        // The real content stays exactly as it was: still fully visible, still its own
        // real size -- only now something else floats above it while this is open.
        if (enabled) {
            showResizePreviewPopup();
        } else {
            dismissResizePreviewPopup();
        }
    }

    public boolean isResizeDragMode() {
        return isResizeDragMode;
    }

    // Commits the final chosen scale -- the single moment the real keyboard window ever
    // actually resizes in this whole feature, called only when "Done" is pressed in the
    // resize-preview popup.
    void commitResizePreview(float finalScale) {
        keyboardScale = Math.max(0.70f, Math.min(RESIZE_DRAG_MAX_SCALE, finalScale));
        prefs.setKeyboardScale(keyboardScale);
        isResizeDragMode = false;
        dismissResizePreviewPopup();
        requestLayout();
        computeKeyBounds(getWidth());
        invalidate();
        soundController.playKeyClick();
    }

    private void showResizePreviewPopup() {
        try {
            int w = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
            int maxPreviewHeight = (int) contentHeightForScale(RESIZE_DRAG_MAX_SCALE);
            if (resizePreviewView == null) {
                resizePreviewView = new ResizePreviewOverlayView(getContext());
            }
            resizePreviewView.totalPreviewHeightPx = maxPreviewHeight;
            resizePreviewView.previewScale = keyboardScale;
            resizePreviewView.resizeStartScale = keyboardScale;
            resizePreviewView.isResizingDrag = false;
            resizePreviewView.resizeDoneButtonPressed = false;
            resizePreviewView.resizeResetButtonPressed = false;
            resizePreviewView.computeOverlayRects(w, keyboardScale);

            if (resizePreviewPopup == null) {
                resizePreviewPopup = new PopupWindow(resizePreviewView, w, maxPreviewHeight, false);
                resizePreviewPopup.setTouchable(true);
                resizePreviewPopup.setFocusable(false);
                resizePreviewPopup.setClippingEnabled(false);
                resizePreviewPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            } else {
                resizePreviewPopup.setWidth(w);
                resizePreviewPopup.setHeight(maxPreviewHeight);
            }

            if (!resizePreviewPopup.isShowing() && getWindowToken() != null) {
                int[] loc = new int[2];
                getLocationInWindow(loc);
                int kbBottomInWindow = loc[1] + getHeight();
                int popupTopY = kbBottomInWindow - maxPreviewHeight;
                resizePreviewPopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, popupTopY);
            }
            resizePreviewView.invalidate();
        } catch (Exception e) {
            AppLogger.log("BlackBerryKeyboardView", "showResizePreviewPopup()", e);
        }
    }

    private void dismissResizePreviewPopup() {
        if (resizePreviewPopup != null) {
            try {
                if (resizePreviewPopup.isShowing()) {
                    resizePreviewPopup.dismiss();
                }
            } catch (Exception e) {
                AppLogger.log("BlackBerryKeyboardView", "dismissResizePreviewPopup()", e);
            }
        }
    }

    public void setToolbarIdleMode(boolean idle) {
        if (this.toolbarIdleMode == idle) return;
        this.toolbarIdleMode = idle;
        invalidate();
    }

    public boolean isToolbarIdleMode() {
        return toolbarIdleMode;
    }

    public void resetShiftState() {
        if (!isShift && !isCapsLock) return;
        isShift = false;
        isCapsLock = false;
        rebuildKeys();
    }

    public void updateAutoCapitalization(boolean shouldCapitalizeNext) {
        if (isArabic || isHindi || isSymbols || isCapsLock) return;
        if (prefs.isAutoCapitalizeSentences() && shouldCapitalizeNext != isShift) {
            isShift = shouldCapitalizeNext;
            rebuildKeys();
        }
    }

    public void setSuggestions(List<String> newSuggestions) {
        setSuggestions(newSuggestions, false);
    }

    public void setSuggestions(List<String> newSuggestions, boolean clipIcon) {
        setSuggestions(newSuggestions, clipIcon ? 1 : 0);
    }

    public void setSuggestions(List<String> newSuggestions, int iconType) {

        List<String> padded = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            padded.add((newSuggestions != null && i < newSuggestions.size()) ? newSuggestions.get(i) : "");
        }
        suggestions = padded;
        middleIconType = iconType;
        invalidate();
    }

    private int middleIconType = 0;

    // The swipe-up sound should only play when there is actually something in the slot to
    // accept -- tapping or swiping an empty suggestion bar (nothing typed, no suggestions)
    // must stay silent.
    private boolean hasSuggestionInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= suggestions.size()) return false;
        String w = suggestions.get(slotIndex);
        return w != null && !w.isEmpty();
    }

    private void launchWordOutsideKeyboard(int slotIndex) {
        if (!prefs.isSwipeUpAnimationEnabled()) return;
        if (slotIndex < 0 || slotIndex >= suggestions.size()) return;
        String word = suggestions.get(slotIndex);
        if (word == null || word.isEmpty()) return;

        disableParentClipping();
        flyingWordText = word;
        flyingWordCenterX = suggestionRects[slotIndex].centerX();
        flyingStartTime = SystemClock.uptimeMillis();
        animatingSlotIndex = slotIndex;

        try {
            int popupH = (int) (135f * density);
            int w = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
            if (topOverlayView == null) {
                topOverlayView = new TopOverlayCanvasView(getContext());
            }
            if (topOverlayPopup == null) {
                topOverlayPopup = new PopupWindow(topOverlayView, w, popupH, false);
                topOverlayPopup.setTouchable(false);
                topOverlayPopup.setFocusable(false);
                topOverlayPopup.setClippingEnabled(false);
                topOverlayPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            } else {
                topOverlayPopup.setWidth(w);
                topOverlayPopup.setHeight(popupH);
            }

            if (!topOverlayPopup.isShowing() && getWindowToken() != null) {
                int[] loc = new int[2];
                getLocationInWindow(loc);
                overlayOffsetX = loc[0];
                int sugBottomY = loc[1] + (int) getSuggestionBarHeight();
                int popupTopY = sugBottomY - popupH;
                topOverlayPopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, popupTopY);
            }
            topOverlayView.invalidate();
        } catch (Exception e) {
            AppLogger.log("BlackBerryKeyboardView", "launchWordOutsideKeyboard()", e);
        }

        postInvalidateOnAnimation();
    }

    private void openLongPressPopupForKey(KeyDef key) {
        if (key == null || key.alts == null || key.alts.isEmpty()) return;
        longPressOptions = new ArrayList<>(key.alts);
        longPressOpensEmoji = key.type == KeyType.LANGUAGE;
        longPressCellRects.clear();
        isLongPressPopupVisible = true;
        longPressAwaitingTap = false;
        hasSlidDuringLongPress = false;
        longPressHighlightedIndex = 0;

        int count = longPressOptions.size();
        float cellW = Math.max(38f * density, Math.min(48f * density, (getWidth() - 20f * density) / count));
        float cellH = 44f * density;
        float pad = 4f * density;
        float totalW = count * cellW + pad * 2f;
        float totalH = cellH + pad * 2f;

        float left = key.rect.centerX() - totalW / 2f;
        if (left < 6f * density) left = 6f * density;
        if (left + totalW > getWidth() - 6f * density) left = getWidth() - 6f * density - totalW;

        float top = key.rect.top - totalH - 4f * density;
        if (top < 2f * density) {
            top = 2f * density;
        }

        longPressPopupBoxRect.set(left, top, left + totalW, top + totalH);
        for (int i = 0; i < count; i++) {
            float cl = left + pad + i * cellW;
            RectF cellRect = new RectF(cl, top + pad, cl + cellW, top + pad + cellH);
            longPressCellRects.add(cellRect);
        }

        longPressCellW = cellW;
        longPressDragBaseX = key.rect.centerX();
        int centerIdx = findLongPressOptionIndexAtX(longPressDragBaseX);
        longPressBaseIndex = (centerIdx >= 0) ? centerIdx : 0;

        longPressHighlightedIndex = 0;
        invalidate();
    }

    private int findLongPressOptionIndexAtX(float x) {
        if (longPressCellRects.isEmpty()) return -1;
        for (int i = 0; i < longPressCellRects.size(); i++) {
            RectF r = longPressCellRects.get(i);
            if (x >= r.left && x <= r.right) {
                return i;
            }
        }
        if (x < longPressCellRects.get(0).left) return 0;
        return longPressCellRects.size() - 1;
    }

    private int lastRowCount = 0;
    private boolean passwordFieldMode = false;

    public void setPasswordFieldMode(boolean enabled) {
        this.passwordFieldMode = enabled;
    }

    public void setNumericFieldMode(boolean enabled) {
        if (this.isNumericFieldMode == enabled) return;
        this.isNumericFieldMode = enabled;
        if (enabled) {

            suggestions = Arrays.asList("", "", "");
            emojiKeySuggestion = null;
        }
        rebuildKeys();
    }

    public void setEmojiKeySuggestion(String emoji) {
        if (java.util.Objects.equals(emojiKeySuggestion, emoji)) return;
        emojiKeySuggestion = emoji;
        invalidate();
    }

    private boolean isNumberRowActive() {
        return prefs.isNumberRowEnabled() || (passwordFieldMode && prefs.isNumberRowForPasswords());
    }

    private List<KeyDef> buildNumberRow() {
        boolean indic = isArabic && prefs.isArabicIndicDigits() && !passwordFieldMode;
        String western = "1234567890";
        String eastern = "١٢٣٤٥٦٧٨٩٠";
        List<KeyDef> row = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String d = String.valueOf((indic ? eastern : western).charAt(i));
            row.add(new KeyDef(d));
        }
        return row;
    }

    private void rebuildKeys() {
        rows.clear();
        if (isNumericFieldMode) {
            rows.addAll(buildNumericFieldRows());
        } else {
            if (isSymbols) {
                rows.addAll(buildSymbolsRows());
            } else if (isArabic) {
                rows.addAll(buildArabicGboardRows());
            } else if (isHindi) {
                rows.addAll(buildHindiInscriptRows());
            } else {
                rows.addAll(buildEnglishQwertyRows());
            }
            if (isNumberRowActive() && !isSymbols) {
                rows.add(0, buildNumberRow());
            }
            rows.add(buildBottomRow());
        }
        if (rows.size() != lastRowCount) {
            lastRowCount = rows.size();
            requestLayout();
        }
        computeKeyBounds(getWidth());
        invalidate();
    }

    private List<List<KeyDef>> buildNumericFieldRows() {
        List<KeyDef> row1 = Arrays.asList(
            new KeyDef("1"),
            new KeyDef("2"),
            new KeyDef("3"),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1f)
        );
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef("4"),
            new KeyDef("5"),
            new KeyDef("6"),
            new KeyDef("-", "", Arrays.asList("_", "/"))
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("7"),
            new KeyDef("8"),
            new KeyDef("9"),
            new KeyDef(".", "#", Arrays.asList("#", "(", ")"))
        );
        List<KeyDef> row4 = Arrays.asList(
            new KeyDef(",", "*", Arrays.asList("*", ";")),
            new KeyDef("0", "+", Collections.singletonList("+")),
            new KeyDef("00"),
            new KeyDef("↵", "", Collections.<String>emptyList(), KeyType.ENTER, 1f)
        );
        return Arrays.asList(row1, row2, row3, row4);
    }

    private List<List<KeyDef>> buildArabicGboardRows() {
        List<KeyDef> row1 = Arrays.asList(
            new KeyDef("ض"),
            new KeyDef("ص"),
            new KeyDef("ث"),
            new KeyDef("ق"),
            new KeyDef("ف"),
            new KeyDef("غ"),
            new KeyDef("ع"),
            new KeyDef("ه", "", Arrays.asList("ـ")),
            new KeyDef("خ"),
            new KeyDef("ح"),
            new KeyDef("ج")
        );
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef("ش"),
            new KeyDef("س"),
            new KeyDef("ي"),
            new KeyDef("ب"),
            new KeyDef("ل", "", Arrays.asList("لأ", "لإ")),
            new KeyDef("ا", "", Arrays.asList("أ", "إ")),
            new KeyDef("ت"),
            new KeyDef("ن"),
            new KeyDef("م"),
            new KeyDef("ك", "", Arrays.asList("گ")),
            new KeyDef("ط")
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("ذ"),
            new KeyDef("ء"),
            new KeyDef("ؤ"),
            new KeyDef("ر"),
            new KeyDef("ى", "", Arrays.asList("ئ")),
            new KeyDef("ة"),
            new KeyDef("و"),
            new KeyDef("ز"),
            new KeyDef("ظ"),
            new KeyDef("د"),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private String getTypedEnglishChar(String letter) {
        return (isShift || isCapsLock) ? letter.toUpperCase(Locale.ROOT) : letter.toLowerCase(Locale.ROOT);
    }

    /** Symbols that already live on the numbers/symbols page: not repeated in letter long-presses. */
    private static final String SYMBOL_PAGE_CHARS = "@#$%&-+()*\"':;!?";

    /**
     * Long-press options for a Latin letter key: digits stay; symbols already on the symbols page
     * are dropped; accented letters only appear on the Spanish layout; and while Shift/Caps is
     * off, the capital version of the letter is offered (so it never conflicts with Shift).
     */
    private List<String> latinAlts(String letter, List<String> raw) {
        boolean spanish = KeyboardPreferences.LANG_ES.equals(currentLangCode);
        boolean shifted = isShift || isCapsLock;
        // English: lowercase keys long-press to their capital only; the numbers show up only
        // while Shift/Caps is on.
        if (!spanish && !shifted) {
            List<String> only = new ArrayList<>();
            only.add(letter.toUpperCase(Locale.ROOT));
            return only;
        }
        List<String> out = new ArrayList<>();
        for (String a : raw) {
            if (a.length() == 1 && SYMBOL_PAGE_CHARS.indexOf(a.charAt(0)) >= 0) continue;
            if (!spanish && a.length() > 0 && Character.isLetter(a.charAt(0))) continue;
            out.add(a);
        }
        if (!isShift && !isCapsLock) out.add(letter.toUpperCase(Locale.ROOT));
        return out;
    }

    /** Marks that can't stand alone get a dotted-circle base so their key doesn't look empty or
     *  render oddly; what is typed is still the bare mark. */
    private static String hindiDisplay(String s) {
        if (s == null || s.isEmpty()) return s;
        char c = s.charAt(0);
        boolean combining = (c >= 0x0900 && c <= 0x0903) || (c >= 0x093A && c <= 0x093C)
                || (c >= 0x093E && c <= 0x094F) || (c >= 0x0951 && c <= 0x0957)
                || c == 0x0962 || c == 0x0963;
        return combining ? "◌" + s : s;
    }

    /** Long-press options for a Hindi key: the extra letters/conjuncts given, plus the Western and
     *  Devanagari digit on the top row. */
    private static List<String> hi(String... alts) {
        return Arrays.asList(alts);
    }

    /**
     * Devanagari layout following the standard InScript arrangement (the one Windows, macOS and
     * Android's built-in Hindi keyboards use), so existing muscle memory carries over. Shift
     * switches to the second layer (the aspirated/long forms); conjuncts and nukta letters live
     * on long-press.
     */
    private List<List<KeyDef>> buildHindiInscriptRows() {
        final boolean up = isShift || isCapsLock;
        final String[] r1 = up ? new String[]{"औ", "ऐ", "आ", "ई", "ऊ", "भ", "ङ", "घ", "ध", "झ", "ढ"}
                : new String[]{"ौ", "ै", "ा", "ी", "ू", "ब", "ह", "ग", "द", "ज", "ड"};
        final String[] r2 = up ? new String[]{"ओ", "ए", "अ", "इ", "उ", "फ", "ऱ", "ख", "थ", "छ", "ठ"}
                : new String[]{"ो", "े", "्", "ि", "ु", "प", "र", "क", "त", "च", "ट"};
        final String[] r3 = up ? new String[]{"ँ", "ण", "ऩ", "ऴ", "ळ", "श", "ष", "ञ"}
                : new String[]{"ं", "म", "न", "व", "ल", "स", "य", "़"};
        final String[] western = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        final String[] devanagari = {"१", "२", "३", "४", "५", "६", "७", "८", "९", "०"};
        final java.util.Map<String, List<String>> extra = new java.util.HashMap<>();
        extra.put("क", hi("क्ष", "क़")); extra.put("त", hi("त्र")); extra.put("ज", hi("ज्ञ", "ज़"));
        extra.put("श", hi("श्र")); extra.put("ड", hi("ड़")); extra.put("ढ", hi("ढ़"));
        extra.put("फ", hi("फ़")); extra.put("ख", hi("ख़")); extra.put("ग", hi("ग़"));
        extra.put("र", hi("ऋ", "ृ", "्र")); extra.put("ो", hi("ॉ")); extra.put("ओ", hi("ऑ"));
        extra.put("े", hi("ॅ")); extra.put("ए", hi("ऍ")); extra.put("ं", hi("ः", "ँ"));
        extra.put("्", hi("ॐ", "ऽ")); extra.put("अ", hi("ॐ"));

        List<KeyDef> row1 = new ArrayList<>();
        for (int i = 0; i < r1.length; i++) {
            List<String> alts = new ArrayList<>();
            if (i < 10) { alts.add(western[i]); alts.add(devanagari[i]); }
            List<String> more = extra.get(r1[i]);
            if (more != null) alts.addAll(more);
            row1.add(new KeyDef(r1[i], "", alts));
        }
        List<KeyDef> row2 = new ArrayList<>();
        for (String k : r2) {
            List<String> more = extra.get(k);
            row2.add(new KeyDef(k, "", more != null ? more : Collections.<String>emptyList()));
        }
        List<KeyDef> row3 = new ArrayList<>();
        row3.add(new KeyDef("⬆", "", Collections.<String>emptyList(), KeyType.SHIFT, 1.35f));
        for (String k : r3) {
            List<String> more = extra.get(k);
            row3.add(new KeyDef(k, "", more != null ? more : Collections.<String>emptyList()));
        }
        row3.add(new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f));
        return Arrays.asList(row1, row2, row3);
    }

    private List<List<KeyDef>> buildEnglishQwertyRows() {
        boolean showNums = false;
        List<KeyDef> row1 = Arrays.asList(
            new KeyDef(getTypedEnglishChar("Q"), showNums ? "1" : "", latinAlts("Q", Collections.singletonList("1"))),
            new KeyDef(getTypedEnglishChar("W"), showNums ? "2" : "", latinAlts("W", Collections.singletonList("2"))),
            new KeyDef(getTypedEnglishChar("E"), showNums ? "3" : "", latinAlts("E", Arrays.asList("3", "é", "è", "ê"))),
            new KeyDef(getTypedEnglishChar("R"), showNums ? "4" : "", latinAlts("R", Collections.singletonList("4"))),
            new KeyDef(getTypedEnglishChar("T"), showNums ? "5" : "", latinAlts("T", Collections.singletonList("5"))),
            new KeyDef(getTypedEnglishChar("Y"), showNums ? "6" : "", latinAlts("Y", Collections.singletonList("6"))),
            new KeyDef(getTypedEnglishChar("U"), showNums ? "7" : "", latinAlts("U", Arrays.asList("7", "ú", "ü", "û"))),
            new KeyDef(getTypedEnglishChar("I"), showNums ? "8" : "", latinAlts("I", Arrays.asList("8", "í", "î"))),
            new KeyDef(getTypedEnglishChar("O"), showNums ? "9" : "", latinAlts("O", Arrays.asList("9", "ó", "ö", "ô"))),
            new KeyDef(getTypedEnglishChar("P"), showNums ? "0" : "", latinAlts("P", Collections.singletonList("0")))
        );
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef(getTypedEnglishChar("A"), "", latinAlts("A", Arrays.asList("@", "á", "ä", "â"))),
            new KeyDef(getTypedEnglishChar("S"), "", latinAlts("S", Arrays.asList("#", "ß", "$"))),
            new KeyDef(getTypedEnglishChar("D"), "", latinAlts("D", Collections.singletonList("$"))),
            new KeyDef(getTypedEnglishChar("F"), "", latinAlts("F", Collections.singletonList("%"))),
            new KeyDef(getTypedEnglishChar("G"), "", latinAlts("G", Collections.singletonList("&"))),
            new KeyDef(getTypedEnglishChar("H"), "", latinAlts("H", Collections.singletonList("-"))),
            new KeyDef(getTypedEnglishChar("J"), "", latinAlts("J", Collections.singletonList("+"))),
            new KeyDef(getTypedEnglishChar("K"), "", latinAlts("K", Collections.singletonList("("))),
            new KeyDef(getTypedEnglishChar("L"), "", latinAlts("L", Collections.singletonList(")")))
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("⬆", "", Collections.<String>emptyList(), KeyType.SHIFT, 1.35f),
            new KeyDef(getTypedEnglishChar("Z"), "", latinAlts("Z", Collections.singletonList("*"))),
            new KeyDef(getTypedEnglishChar("X"), "", latinAlts("X", Collections.singletonList("\""))),
            new KeyDef(getTypedEnglishChar("C"), "", latinAlts("C", Arrays.asList("'", "ç"))),
            new KeyDef(getTypedEnglishChar("V"), "", latinAlts("V", Collections.singletonList(":"))),
            new KeyDef(getTypedEnglishChar("B"), "", latinAlts("B", Collections.singletonList(";"))),
            new KeyDef(getTypedEnglishChar("N"), "", latinAlts("N", Arrays.asList("!", "ñ"))),
            new KeyDef(getTypedEnglishChar("M"), "", latinAlts("M", Collections.singletonList("?"))),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private List<List<KeyDef>> buildSymbolsRows() {

        boolean arabicOff = !prefs.getEnabledLanguages().contains(KeyboardPreferences.LANG_AR);
        final boolean hindiOn = isHindi || prefs.getEnabledLanguages().contains(KeyboardPreferences.LANG_HI);
        List<KeyDef> row1 = (arabicOff && hindiOn) ? Arrays.asList(
            new KeyDef("1", "१", Collections.singletonList("१")),
            new KeyDef("2", "२", Collections.singletonList("२")),
            new KeyDef("3", "३", Collections.singletonList("३")),
            new KeyDef("4", "४", Collections.singletonList("४")),
            new KeyDef("5", "५", Collections.singletonList("५")),
            new KeyDef("6", "६", Collections.singletonList("६")),
            new KeyDef("7", "७", Collections.singletonList("७")),
            new KeyDef("8", "८", Collections.singletonList("८")),
            new KeyDef("9", "९", Collections.singletonList("९")),
            new KeyDef("0", "०", Collections.singletonList("०"))
        ) : arabicOff ? Arrays.asList(
            new KeyDef("1"), new KeyDef("2"), new KeyDef("3"), new KeyDef("4"), new KeyDef("5"),
            new KeyDef("6"), new KeyDef("7"), new KeyDef("8"), new KeyDef("9"), new KeyDef("0")
        ) : Arrays.asList(
            new KeyDef("1", "١", Collections.singletonList("١")),
            new KeyDef("2", "٢", Collections.singletonList("٢")),
            new KeyDef("3", "٣", Collections.singletonList("٣")),
            new KeyDef("4", "٤", Collections.singletonList("٤")),
            new KeyDef("5", "٥", Collections.singletonList("٥")),
            new KeyDef("6", "٦", Collections.singletonList("٦")),
            new KeyDef("7", "٧", Collections.singletonList("٧")),
            new KeyDef("8", "٨", Collections.singletonList("٨")),
            new KeyDef("9", "٩", Collections.singletonList("٩")),
            new KeyDef("0", "٠", Collections.singletonList("٠"))
        );
        // Arabic-only symbols (﷼, ؟) are offered only when Arabic is an enabled language.
        final boolean arabicEnabled = isArabic
                || prefs.getEnabledLanguages().contains(KeyboardPreferences.LANG_AR);
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef("@", "~", Collections.singletonList("~")),
            new KeyDef("#", "^", Collections.singletonList("^")),
            new KeyDef("$", hindiOn ? "₹" : "€",
                    hindiOn ? (arabicEnabled ? Arrays.asList("₹", "€", "£", "¥", "﷼") : Arrays.asList("₹", "€", "£", "¥"))
                            : (arabicEnabled ? Arrays.asList("€", "£", "¥", "﷼") : Arrays.asList("€", "£", "¥"))),
            new KeyDef("_", "|", Collections.singletonList("|")),
            new KeyDef("&", "\\", Collections.singletonList("\\")),
            new KeyDef("-", "—", Collections.singletonList("—")),
            new KeyDef("+", "±", Collections.singletonList("±")),
            new KeyDef("(", "[", Arrays.asList("[", "{", "<")),
            new KeyDef(")", "]", Arrays.asList("]", "}", ">")),
            new KeyDef("/", "÷", Collections.singletonList("÷"))
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("*", "×", Collections.singletonList("×")),
            new KeyDef("\"", "«", Collections.singletonList("«")),
            new KeyDef("'", "»", Collections.singletonList("»")),
            new KeyDef(":", "", Collections.singletonList(":")),
            arabicEnabled ? new KeyDef("؛", ";", Collections.singletonList(";"))
                          : new KeyDef(";", "", Collections.singletonList(";")),
            new KeyDef("!", "¡", Collections.singletonList("¡")),

            new KeyDef(isArabic ? "؟" : "?", isArabic ? "?" : (arabicEnabled ? "؟" : ""),
                    isArabic ? Collections.singletonList("?")
                            : (arabicEnabled ? Collections.singletonList("؟") : Collections.<String>emptyList())),
            arabicEnabled ? new KeyDef("،", ",", Collections.singletonList(","))
                          : new KeyDef(",", "", Collections.singletonList(",")),
            new KeyDef("%", "‰", Collections.singletonList("‰")),
            new KeyDef("=", "≈", Collections.singletonList("≈")),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private List<KeyDef> buildBottomRow() {
        String symLabel = isSymbols ? (isArabic ? "أبجد" : (isHindi ? "अआइ" : "ABC")) : (isArabic ? "؟123" : "?123");

        boolean showSwipeArrows = prefs.isSpaceSwipeLangEnabled()
                && prefs.getEnabledLanguages().size() >= 2;
        String currentLangName = languageDisplayName(currentLangCode);
        String spaceLabel = showSwipeArrows ? ("‹  " + currentLangName + "  ›") : currentLangName;
        return Arrays.asList(
            new KeyDef(symLabel, "", Collections.<String>emptyList(), KeyType.SYMBOLS, 1.25f),

            languageKeyShown()
                ? new KeyDef("🌐", "", Collections.singletonList("😀"), KeyType.LANGUAGE, 1.0f)
                : new KeyDef("😀", "", Collections.<String>emptyList(), KeyType.EMOJI, 1.0f),
            new KeyDef(spaceLabel, "", Collections.<String>emptyList(), KeyType.SPACE, 4.7f),
            new KeyDef(isHindi ? "।" : ".", isArabic ? "؟" : "?",
                    isArabic ? Arrays.asList("؟", "،", "!", "؛", ":", "…")
                            : (isHindi ? Arrays.asList("?", ".", ",", "!", ";", ":", "…", "॥")
                            : Arrays.asList("?", ",", "!", ";", ":", "…")),
                    KeyType.PERIOD, 1.0f),
            new KeyDef("↵", "", Collections.<String>emptyList(), KeyType.ENTER, 1.35f)
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // The real keyboard window is never resized just by turning resize-drag mode on
        // or off: the live preview now lives entirely in its own separate floating popup
        // (see ResizePreviewOverlayView/resizePreviewPopup below), which can grow above
        // the keyboard's own bounds without this view -- or the real IME window behind
        // it -- changing size at all. This view's own height only ever changes once,
        // when commitResizePreview() applies the final chosen scale.
        int totalHeight = (int) contentHeightForScale(keyboardScale);
        setMeasuredDimension(w, totalHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeKeyBounds(w);
    }

    private void computeKeyBounds(int w) {
        if (w <= 0) return;
        // Content is always anchored flush at the top of the view, at the committed
        // keyboardScale -- this view's own height always matches that scale exactly now
        // (see onMeasure()), since the resize-drag preview lives entirely in its own
        // separate floating popup and never grows this view itself.
        float contentTopOffset = 0f;
        float sugH = getSuggestionBarHeight();
        float keyH = getKeyRowHeight();
        float botH = getBottomRowHeight();

        float sugEdge = getBottomRowEdgeInsetPx(w);
        float menuW = 42f * density;
        menuButtonRect.set(sugEdge, contentTopOffset, sugEdge + menuW, contentTopOffset + sugH);
        voiceEnabled = prefs.isVoiceButtonEnabled();
        if (voiceEnabled) {
            micButtonRect.set(w - sugEdge - menuW, contentTopOffset, w - sugEdge, contentTopOffset + sugH);
        } else {
            micButtonRect.setEmpty();
        }

        float remW = w - sugEdge * 2f - menuW - (voiceEnabled ? menuW : 0f);
        float thirdW = remW / 3f;
        for (int i = 0; i < 3; i++) {
            suggestionRects[i].set(sugEdge + menuW + i * thirdW, contentTopOffset, sugEdge + menuW + (i + 1) * thirdW, contentTopOffset + sugH);
        }

        boolean splitActive = isSplitKeyboardActive(w);
        tabletKeyGapX = splitActive ? 2.2f * density : 0f;
        tabletKeyGapY = splitActive ? 0.8f * density : 0f;
        splitDuplicateKeys.clear();

        float gapW = splitActive ? w * 0.25f : 0f;
        float edgeW = splitActive ? w * 0.03f : 0f;
        float halfW = (w - gapW - edgeW * 2f) / 2f;
        float leftStartX = edgeW;
        float rightStartX = edgeW + halfW + gapW;

        float topY = contentTopOffset + sugH;
        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            List<KeyDef> row = rows.get(rIdx);
            float rowH = (rIdx == rows.size() - 1) ? botH : keyH;
            if (rIdx == rows.size() - 1) topY += extraBottomRowGapPx();

            if (splitActive && rIdx != rows.size() - 1) {

                float indent = (rIdx == 1 && !isArabic && !isSymbols && row.size() == 9)
                        ? ENGLISH_ROW2_INDENT_KEYS * halfW / 5.5f : 0f;
                layoutSplitRow(row, topY, rowH, leftStartX + indent, halfW - indent, rightStartX, halfW - indent);
            } else {

                float rowEdge;
                if (splitActive) {
                    rowEdge = edgeW;
                } else if (isNumericFieldMode) {
                    rowEdge = 5f * density;
                } else {
                    rowEdge = SIDE_MARGIN_DP * density;
                }
                float availRowW = w - rowEdge * 2f;
                boolean bottomRowTablet = splitActive && rIdx == rows.size() - 1;
                float totalWeight = 0f;
                for (KeyDef k : row) totalWeight += bottomRowWeight(k, bottomRowTablet);
                float leftX = rowEdge;
                if (!splitActive && rIdx == 1 && !isArabic && !isSymbols && row.size() == 9) {

                    float pad = availRowW * ENGLISH_ROW2_INDENT_KEYS / (totalWeight + 2f * ENGLISH_ROW2_INDENT_KEYS);
                    leftX += pad;
                    availRowW -= 2f * pad;
                }
                for (KeyDef k : row) {
                    float kw = (bottomRowWeight(k, bottomRowTablet) / totalWeight) * availRowW;
                    k.rect.set(leftX, topY, leftX + kw, topY + rowH);
                    leftX += kw;
                }
            }
            topY += rowH;
        }
    }

    private static float bottomRowWeight(KeyDef k, boolean tablet) {
        if (!tablet) return k.weight;
        switch (k.type) {
            case SYMBOLS: return 1.05f;
            case EMOJI: return 0.80f;
            case LANGUAGE: return 0.80f;
            case SPACE: return 6.10f;
            case PERIOD: return 0.80f;
            case ENTER: return 1.10f;
            default: return k.weight;
        }
    }

    public float getTabletKeyGapXPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? 2.2f * density : 0f;
    }

    public float getTabletKeyGapYPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? 0.8f * density : 0f;
    }

    private boolean isSplitKeyboardActive(int widthPx) {
        // smallestScreenWidthDp is the device's shorter axis, which stays the same
        // across rotation -- unlike a live widthPx check, which on a tall-and-narrow
        // screen (a regular phone, or a foldable's outer/cover screen) can exceed
        // 600dp in landscape simply because that's the screen's long dimension, not
        // because the device is actually tablet-sized. A real tablet (or a
        // foldable's inner screen when unfolded) stays >= 600dp either way.
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private void layoutSplitRow(List<KeyDef> row, float topY, float rowH, float leftStartX, float leftW, float rightStartX, float rightW) {
        float totalWeight = 0f;
        for (KeyDef k : row) totalWeight += k.weight;
        float half = totalWeight / 2f;
        int splitIndex = row.size();
        float running = 0f;
        for (int i = 0; i < row.size(); i++) {
            running += row.get(i).weight;
            if (running >= half) { splitIndex = i + 1; break; }
        }
        List<KeyDef> leftKeys = new ArrayList<>(row.subList(0, splitIndex));
        List<KeyDef> rightKeys = new ArrayList<>(row.subList(splitIndex, row.size()));

        KeyDef dupForRight = null;
        KeyDef dupForLeft = null;
        if (!leftKeys.isEmpty()) {
            KeyDef edge = leftKeys.get(leftKeys.size() - 1);
            if (edge.type == KeyType.CHAR && SPLIT_GAP_DUPLICATE_LETTERS.contains(edge.main)) {
                dupForRight = new KeyDef(edge.main, edge.sub, edge.alts, edge.type, edge.weight);
            }
        }
        if (!rightKeys.isEmpty()) {
            KeyDef edge = rightKeys.get(0);
            if (edge.type == KeyType.CHAR && SPLIT_GAP_DUPLICATE_LETTERS.contains(edge.main)) {
                dupForLeft = new KeyDef(edge.main, edge.sub, edge.alts, edge.type, edge.weight);
            }
        }
        if (dupForRight != null) rightKeys.add(0, dupForRight);
        if (dupForLeft != null) leftKeys.add(dupForLeft);

        float leftWeight = 0f;
        for (KeyDef k : leftKeys) leftWeight += k.weight;
        float rightWeight = 0f;
        for (KeyDef k : rightKeys) rightWeight += k.weight;

        float leftX = leftStartX;
        for (KeyDef k : leftKeys) {
            float kw = leftWeight > 0f ? (k.weight / leftWeight) * leftW : 0f;
            k.rect.set(leftX, topY, leftX + kw, topY + rowH);
            leftX += kw;
            if (k == dupForLeft) splitDuplicateKeys.add(k);
        }
        float rightX = rightStartX;
        for (KeyDef k : rightKeys) {
            float kw = rightWeight > 0f ? (k.weight / rightWeight) * rightW : 0f;
            k.rect.set(rightX, topY, rightX + kw, topY + rowH);
            rightX += kw;
            if (k == dupForRight) splitDuplicateKeys.add(k);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (afterDrawAction != null) {
            Runnable pending = afterDrawAction;
            afterDrawAction = null;
            post(pending);
        }
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), bgShellPaint);

        float baseLetterSp = isHindi && !isSymbols ? 21.0f : ((!isArabic && !isSymbols) ? 23.0f : 22.5f);
        mainTextPaint.setTextSize(baseLetterSp * scaledDensity * keyFontScale);
        subTextPaint.setTextSize(10.5f * scaledDensity * Math.min(1.25f, keyFontScale));
        sugTextPaint.setTextSize(14.5f * scaledDensity * sugTextScale);
        sugPrimaryTextPaint.setTextSize(15.5f * scaledDensity * sugTextScale);

        float sugTop = 0f;
        float sugBottom = sugTop + getSuggestionBarHeight();
        canvas.drawRect(0f, sugTop, getWidth(), sugBottom, sugBarPaint);

        if (menuButtonPressed) {
            canvas.drawRect(menuButtonRect, sugFlashPaint);
        }

        drawToolbarIcon(canvas, menuButtonRect);
        canvas.drawLine(menuButtonRect.right, sugTop + 5f * density, menuButtonRect.right, sugBottom - 5f * density, keyDividerPaint);
        if (voiceEnabled) {
            if (micButtonPressed || voiceListening) {
                canvas.drawRect(micButtonRect, sugFlashPaint);
            }
            drawMicIcon(canvas, micButtonRect);
            canvas.drawLine(micButtonRect.left, sugTop + 5f * density, micButtonRect.left, sugBottom - 5f * density, keyDividerPaint);
        }

        long now = SystemClock.uptimeMillis();
        boolean isFlyingActive = (flyingWordText != null && (now - flyingStartTime) < FLY_OUTSIDE_DURATION_MS);

        for (int i = 0; i < 3; i++) {
            RectF r = suggestionRects[i];
            if (i < 2 && !toolbarIdleMode) {
                canvas.drawLine(r.right, sugTop + 5f * density, r.right, sugBottom - 5f * density, keyDividerPaint);
            }

            String word = (!toolbarIdleMode && i < suggestions.size()) ? suggestions.get(i) : "";
            Paint paint = sugTextPaint;
            float textY = r.centerY() - (paint.descent() + paint.ascent()) / 2f;

            if (isFlyingActive && i == animatingSlotIndex) {
                float prog = (float) (now - flyingStartTime) / (float) FLY_OUTSIDE_DURATION_MS;
                int prevAlpha = paint.getAlpha();
                paint.setAlpha((int) (Math.min(1f, 0.35f + prog * 2.2f) * 255f));
                canvas.drawText(ellipsizeWord(word, r.width() - 10f * density, paint), r.centerX(), textY, paint);
                paint.setAlpha(prevAlpha);
            } else if (i == 1 && middleIconType == 2 && shotThumb != null && !word.isEmpty()) {
                drawScreenshotChip(canvas, r, word, paint);
            } else if (i == 1 && middleIconType == 3 && !word.isEmpty()) {
                drawTextChip(canvas, r, word, paint);
            } else if (i == 1 && middleIconType != 0 && !word.isEmpty()) {

                float iconW = 18f * density;
                float gap = 6f * density;
                String shown = ellipsizeWord(word, r.width() - 14f * density - iconW - gap, paint);
                float textW = paint.measureText(shown);
                float startX = r.centerX() - (iconW + gap + textW) / 2f;
                drawRectB.set(startX, r.top, startX + iconW, r.bottom);
                if (middleIconType == 2) drawImageIcon(canvas, drawRectB); else drawClipboardMenuIcon(canvas, drawRectB);
                Paint.Align prevAlign = paint.getTextAlign();
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(shown, startX + iconW + gap, textY, paint);
                paint.setTextAlign(prevAlign);
            } else {
                canvas.drawText(ellipsizeWord(word, r.width() - 10f * density, paint), r.centerX(), textY, paint);
            }

        }

        canvas.drawLine(0f, sugBottom, getWidth(), sugBottom, fretLinePaint);

        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            List<KeyDef> row = rows.get(rIdx);
            for (KeyDef k : row) {
                boolean bounceActive = (k == bounceKey) && (now - bounceStartTime < KEY_BOUNCE_MS);
                int savedCount = -1;
                if (bounceActive) {
                    long el = now - bounceStartTime;
                    float scale = keyBounceScale(el);
                    float lift = keyBounceLiftPx(el);
                    savedCount = canvas.save();
                    canvas.translate(0f, lift);
                    canvas.scale(scale, scale, k.rect.centerX(), k.rect.centerY());
                }
                boolean isSpecial = (k.type != KeyType.CHAR && k.type != KeyType.SPACE);
                Paint bg;
                if (k == visualPressedKey && !isDragging) {
                    bg = keyPressedPaint;
                } else if (k.type == KeyType.ENTER && enterCustom) {
                    bg = enterFillPaint;
                } else if (isSpecial) {
                    bg = keySpecialBgPaint;
                } else {
                    bg = keyBgPaint;
                }

                drawStyledKeyCell(canvas, k, bg);

                if (!k.sub.isEmpty()) {
                    // Same style-aware corner math as the globe key's badge: the old fallback for
                    // every style except Material/Soft-round just offset from the RAW cell corner,
                    // ignoring each style's own inset and corner rounding — so on Pill, Glass,
                    // Outline, Soft raised, Circles and the rest, this little alt-char hint was
                    // drawn outside the key's actual visible edge almost every time.
                    float visLeft, visTop, visRight, visBottom, cornerRadius;
                    if (keyboardStyle == KeyboardPreferences.STYLE_CIRCLES) {
                        float w = k.rect.width(), h = k.rect.height();
                        float d = Math.min(w, h) - 6f * density;
                        cornerRadius = d / 2f;
                        float cx2 = k.rect.centerX(), cy2 = k.rect.centerY();
                        if (w <= h * 1.1f) {
                            visLeft = cx2 - d / 2f; visTop = cy2 - d / 2f;
                            visRight = cx2 + d / 2f; visBottom = cy2 + d / 2f;
                        } else {
                            float hpad = 3f * density;
                            visLeft = k.rect.left + hpad; visTop = cy2 - d / 2f;
                            visRight = k.rect.right - hpad; visBottom = cy2 + d / 2f;
                        }
                    } else {
                        float[] inset = keyVisibleInsetTopRight(k.type);
                        visLeft = k.rect.left + inset[0]; visTop = k.rect.top + inset[1];
                        visRight = k.rect.right - inset[0]; visBottom = k.rect.bottom - inset[1];
                        cornerRadius = inset[2];
                    }

                    float safeMargin = cornerRadius * 0.38f + 1.0f * density;
                    float visibleRight = visRight;
                    float visibleTop = visTop;

                    subTextPaint.getTextBounds(k.sub, 0, k.sub.length(), scratchSubBounds);
                    float desiredInkRight = visibleRight - safeMargin;
                    float desiredInkTop = visibleTop + safeMargin;
                    float x = desiredInkRight - scratchSubBounds.right;
                    float baselineY = desiredInkTop - scratchSubBounds.top;
                    Paint.Align prevAlign = subTextPaint.getTextAlign();
                    subTextPaint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(k.sub, x, baselineY, subTextPaint);
                    subTextPaint.setTextAlign(prevAlign);
                }

                if (k.type == KeyType.SPACE) {
                    subTextPaint.setTextAlign(Paint.Align.CENTER);
                    subTextPaint.setTextSize(11.5f * scaledDensity);
                    canvas.drawText(k.main, k.rect.centerX(), k.rect.centerY() - 1f * density, subTextPaint);
                    subTextPaint.setTextAlign(Paint.Align.RIGHT);
                } else if (k.type == KeyType.ENTER) {
                    drawLargeEnterIcon(canvas, k.rect);
                } else if (k.type == KeyType.SHIFT) {
                    drawShiftKeyIcon(canvas, k.rect);
                } else if (k.type == KeyType.LANGUAGE && emojiKeySuggestion == null) {
                    drawGlobeIcon(canvas, k.rect);
                } else if (k.type == KeyType.EMOJI || k.type == KeyType.LANGUAGE) {

                    float prevSize = mainTextPaint.getTextSize();
                    String glyph = (k.type == KeyType.EMOJI) ? "😀" : k.main;

                    if (emojiKeySuggestion != null) glyph = emojiKeySuggestion;
                    mainTextPaint.setTextSize(15.5f * scaledDensity);
                    float textY = k.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.0f * density;
                    canvas.drawText(glyph, k.rect.centerX(), textY, mainTextPaint);
                    mainTextPaint.setTextSize(prevSize);
                } else {
                    float prevSize = mainTextPaint.getTextSize();
                    if (k.type == KeyType.SYMBOLS) {
                        mainTextPaint.setTextSize(15f * scaledDensity);
                    } else if (k.type == KeyType.BACKSPACE) {
                        mainTextPaint.setTextSize(prevSize * 0.9f);
                    }
                    String displayLabel = hindiDisplay(k.main);
                    if (!isArabic && !isHindi && !isSymbols && k.type == KeyType.CHAR && prefs.isAlwaysShowUppercaseKeycapsEn()) {
                        displayLabel = k.main.toUpperCase(Locale.ROOT);
                    }
                    float textY = k.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
                    canvas.drawText(displayLabel, k.rect.centerX(), textY, mainTextPaint);
                    mainTextPaint.setTextSize(prevSize);
                }
                if (bounceActive) {
                    canvas.restoreToCount(savedCount);
                }
            }
            if (keyboardStyle == KeyboardPreferences.STYLE_BLACKBERRY_FRETS && !row.isEmpty() && rIdx < rows.size() - 1) {
                float rowBottom = row.get(0).rect.bottom;
                canvas.drawLine(0f, rowBottom, getWidth(), rowBottom, fretLinePaint);
            }
        }
        if (bounceKey != null && now - bounceStartTime < KEY_BOUNCE_MS) {
            postInvalidateOnAnimation();
        } else {
            bounceKey = null;
        }

        for (KeyDef dup : splitDuplicateKeys) {
            Paint dupBg = (dup == visualPressedKey && !isDragging) ? keyPressedPaint : keyBgPaint;
            drawStyledKeyCell(canvas, dup, dupBg);
            float prevSize = mainTextPaint.getTextSize();
            String displayLabel = hindiDisplay(dup.main);
            if (!isArabic && !isHindi && !isSymbols && prefs.isAlwaysShowUppercaseKeycapsEn()) {
                displayLabel = dup.main.toUpperCase(Locale.ROOT);
            }
            float textY = dup.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
            canvas.drawText(displayLabel, dup.rect.centerX(), textY, mainTextPaint);
            mainTextPaint.setTextSize(prevSize);
        }

        if (activePreviewKey != null) {
            drawKeyPreviewBubble(canvas, activePreviewKey);
        }

        if (isLongPressPopupVisible && !longPressOptions.isEmpty()) {
            drawLongPressPopupMenu(canvas);
        }

        drawDeleteTagAnimation(canvas, now);

        if (isFlyingActive && (topOverlayPopup == null || !topOverlayPopup.isShowing())) {
            float progress = (float) (now - flyingStartTime) / (float) FLY_OUTSIDE_DURATION_MS;
            float startYCanvas = sugTop + getSuggestionBarHeight() * 0.62f;
            drawGhostFlyingWord(canvas, flyingWordText, flyingWordCenterX, startYCanvas, progress);
            postInvalidateOnAnimation();
        } else if (!isFlyingActive && flyingWordText != null) {
            flyingWordText = null;
            animatingSlotIndex = -1;
        }

        // The resize-drag preview (scrim/boundary/Reset/handle/Done) is no longer drawn
        // here at all -- it is drawn by the separate ResizePreviewOverlayView riding in
        // its own popup window above this one, so this view's own onDraw has nothing
        // left to do for resize-drag mode.
    }

    private static final long DELETE_TAG_MS = 380L;
    private long deleteTagStart = 0L;
    private float deleteTagX0 = 0f, deleteTagY = 0f, deleteTagDir = 1f;
    private final android.graphics.Path deleteTagPath = new android.graphics.Path();
    private final Paint deleteTagFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deleteTagCross = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float keyBounceScale(long elapsedMs) {
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) KEY_BOUNCE_MS));
        if (t < 0.3f) {

            float u = t / 0.3f;
            return 1f - 0.26f * u;
        }

        float u = (t - 0.3f) / 0.7f;
        float tension = 4.2f;
        float um1 = u - 1f;
        float overshoot = um1 * um1 * ((tension + 1f) * um1 + tension) + 1f;
        return 0.74f + 0.26f * overshoot;
    }

    private float keyBounceLiftPx(long elapsedMs) {
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) KEY_BOUNCE_MS));
        float maxDown = 4.0f * density;
        float maxUp = 13.0f * density;
        if (t < 0.3f) {
            float u = t / 0.3f;
            return maxDown * u;
        }
        float u = (t - 0.3f) / 0.7f;
        float tension = 4.2f;
        float um1 = u - 1f;
        float overshoot = um1 * um1 * ((tension + 1f) * um1 + tension) + 1f;

        return maxDown * (1f - overshoot) - maxUp * Math.max(0f, overshoot - 1f);
    }

    private void startDeleteTagAnimation(float x, float y, float dx) {
        deleteTagStart = SystemClock.uptimeMillis();
        deleteTagX0 = x;
        deleteTagY = y;
        deleteTagDir = dx >= 0f ? 1f : -1f;
        invalidate();
    }

    private void drawDeleteTagAnimation(Canvas canvas, long now) {
        if (deleteTagStart == 0L) return;
        float t = (now - deleteTagStart) / (float) DELETE_TAG_MS;
        if (t >= 1f) {
            deleteTagStart = 0L;
            return;
        }
        float w = getWidth();
        float smooth = t * t * (3f - 2f * t);
        float x1 = Math.max(0.04f * w, Math.min(0.96f * w, deleteTagX0 + deleteTagDir * 0.5f * w));
        float cx = deleteTagX0 + (x1 - deleteTagX0) * smooth;
        float scale = 0.4f + 0.6f * t;
        float alpha;
        if (t < 0.55f) alpha = 0.4f + 0.6f * (t / 0.55f);
        else if (t < 0.7f) alpha = 1f;
        else alpha = 1f - (t - 0.7f) / 0.3f;

        float tw = 30f * density * scale;
        float th = 14f * density * scale;
        float tip = th * 0.5f;
        float d = deleteTagDir;
        float left = cx - d * tw / 2f;
        float right = cx + d * tw / 2f;
        float top = deleteTagY - th / 2f;
        float bottom = deleteTagY + th / 2f;
        deleteTagPath.reset();
        deleteTagPath.moveTo(left, top);
        deleteTagPath.lineTo(right - d * tip, top);
        deleteTagPath.lineTo(right, deleteTagY);
        deleteTagPath.lineTo(right - d * tip, bottom);
        deleteTagPath.lineTo(left, bottom);
        deleteTagPath.close();

        deleteTagFill.setStyle(Paint.Style.FILL);
        deleteTagFill.setColor(Color.argb((int) (230 * alpha), 232, 232, 232));
        canvas.drawPath(deleteTagPath, deleteTagFill);

        deleteTagCross.setStyle(Paint.Style.STROKE);
        deleteTagCross.setStrokeCap(Paint.Cap.ROUND);
        deleteTagCross.setStrokeWidth(Math.max(1f, 1.6f * density * scale));
        deleteTagCross.setColor(Color.argb((int) (255 * alpha), 40, 40, 40));
        float ccx = cx - d * tip * 0.25f;
        float r = th * 0.2f;
        canvas.drawLine(ccx - r, deleteTagY - r, ccx + r, deleteTagY + r, deleteTagCross);
        canvas.drawLine(ccx - r, deleteTagY + r, ccx + r, deleteTagY - r, deleteTagCross);
        postInvalidateOnAnimation();
    }

    private void drawKeyPreviewBubble(Canvas canvas, KeyDef key) {
        float bubbleW = key.rect.width() - 5f * density;
        float bubbleH = key.rect.height() - 5f * density;
        float cx = key.rect.centerX();
        float left = cx - bubbleW / 2f;
        if (left < 4f * density) left = 4f * density;
        if (left + bubbleW > getWidth() - 4f * density) left = getWidth() - 4f * density - bubbleW;
        float top = key.rect.top - bubbleH - 2f * density;
        if (top < 2f * density) top = 2f * density;
        RectF bubbleRect = drawRectA;
        bubbleRect.set(left, top, left + bubbleW, top + bubbleH);

        canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, popupCardBgPaint);
        canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, popupEdgePaint);

        float prevSize = popupTextPaint.getTextSize();
        popupTextPaint.setTextSize(19f * scaledDensity);
        float ty = bubbleRect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f;
        canvas.drawText(hindiDisplay(key.main), bubbleRect.centerX(), ty, popupTextPaint);
        popupTextPaint.setTextSize(prevSize);
    }

    private void drawLongPressPopupMenu(Canvas canvas) {
        canvas.drawRoundRect(longPressPopupBoxRect, 12f * density, 12f * density, popupCardBgPaint);
        canvas.drawRoundRect(longPressPopupBoxRect, 12f * density, 12f * density, popupEdgePaint);

        popupTextPaint.setTextSize(19.5f * scaledDensity);
        int normalTextColor = popupTextPaint.getColor();

        for (int i = 0; i < longPressOptions.size() && i < longPressCellRects.size(); i++) {
            RectF cell = longPressCellRects.get(i);
            String opt = longPressOptions.get(i);
            if (i == longPressHighlightedIndex) {
                canvas.drawRoundRect(cell, 8f * density, 8f * density, keyPressedPaint);
            } else {
                popupTextPaint.setColor(normalTextColor);
            }
            float ty = cell.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f;
            canvas.drawText(isHindi ? hindiDisplay(opt) : opt, cell.centerX(), ty, popupTextPaint);
        }
        popupTextPaint.setColor(normalTextColor);
    }

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RectF insetRect(RectF raw, float dx, float dy) {
        scratchKeyRect.set(raw.left + dx, raw.top + dy, raw.right - dx, raw.bottom - dy);
        return scratchKeyRect;
    }

    private void drawStyledKeyCell(Canvas canvas, KeyDef key, Paint fillPaint) {
        RectF rawRect = key.rect;
        boolean pressed = (fillPaint == keyPressedPaint);
        boolean isSpecialKey = (key.type != KeyType.CHAR && key.type != KeyType.SPACE);
        boolean pillKey = (key.type == KeyType.SYMBOLS || key.type == KeyType.ENTER);
        final float gx = tabletKeyGapX;

        final float gy = tabletKeyGapY + (isNumericFieldMode ? -1.9f * density : 0f);

        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_MATERIAL
                || keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND) {

            boolean soft = (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND);
            float hInset = (soft ? 2.6f : 2.0f) * density + gx;
            float topInset = materialTopInsetDp(key.type, soft) * density + gy;
            float bottomInset = materialBottomInsetDp(key.type, soft) * density + gy;
            scratchKeyRect.set(rawRect.left + hInset, rawRect.top + topInset, rawRect.right - hInset, rawRect.bottom - bottomInset);
            RectF r = scratchKeyRect;
            float radius = pillKey ? r.height() / 2f : (soft ? 17f : 9.5f) * density;
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_BORDERLESS) {

            RectF r = insetRect(rawRect, 3.0f * density + gx, 4.5f * density + gy);
            float radius = pillKey ? r.height() / 2f : 13f * density;
            if (pressed || isSpecialKey) {
                canvas.drawRoundRect(r, radius, radius, fillPaint);
            }
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_OUTLINED) {
            RectF r = insetRect(rawRect, 3.2f * density + gx, 4.2f * density + gy);
            float radius = 11f * density;
            if (pressed) {
                canvas.drawRoundRect(r, radius, radius, fillPaint);
            } else if (isSpecialKey) {
                canvas.drawRoundRect(r, radius, radius, fillPaint);
            }
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(1.3f * density);
            outlinePaint.setColor(popupCardBorderPaint.getColor());
            outlinePaint.setAlpha(150);
            canvas.drawRoundRect(r, radius, radius, outlinePaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_KEYCAP_3D) {

            float edge = pressed ? 1.2f * density : 3.4f * density;
            RectF cap = drawRectA;
            cap.set(rawRect.left + 2.8f * density + gx, rawRect.top + 3.2f * density + gy,
                    rawRect.right - 2.8f * density - gx, rawRect.bottom - 3.4f * density - edge + 3.4f * density - gy);
            RectF base = drawRectB;
            base.set(cap.left, cap.top + edge, cap.right, cap.bottom + edge);
            float radius = 7.5f * density;
            canvas.drawRoundRect(base, radius, radius, keyShadowPaint);
            RectF top = drawRectC;
            top.set(cap.left, cap.top + (pressed ? edge : 0f), cap.right, cap.bottom + (pressed ? edge : 0f));
            canvas.drawRoundRect(top, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_SOFT_RAISED) {

            RectF r = insetRect(rawRect, 2.6f * density + gx, 3.6f * density + gy);
            float radius = 12f * density;
            RectF shadow = drawRectB;
            shadow.set(r.left, r.top + 1.6f * density, r.right, r.bottom + 1.6f * density);
            canvas.drawRoundRect(shadow, radius, radius, keyShadowPaint);
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            stylePaint.setStyle(Paint.Style.STROKE);
            stylePaint.setStrokeWidth(1f * density);
            stylePaint.setStrokeCap(Paint.Cap.ROUND);
            stylePaint.setColor(Color.WHITE);
            stylePaint.setAlpha(isLightTheme() ? 230 : 45);
            canvas.drawLine(r.left + radius * 0.7f, r.top + 1f * density, r.right - radius * 0.7f, r.top + 1f * density, stylePaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_CIRCLES) {

            float w = rawRect.width(), h = rawRect.height();
            float d = Math.min(w, h) - 6f * density;
            RectF r = drawRectA;
            if (w <= h * 1.1f) {
                r.set(rawRect.centerX() - d / 2f, rawRect.centerY() - d / 2f, rawRect.centerX() + d / 2f, rawRect.centerY() + d / 2f);
            } else {
                r.set(rawRect.left + 3f * density + gx, rawRect.centerY() - d / 2f, rawRect.right - 3f * density - gx, rawRect.centerY() + d / 2f);
            }
            canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GLASS) {

            RectF r = insetRect(rawRect, 2.8f * density + gx, 3.8f * density + gy);
            float radius = 10f * density;
            int fillColor = fillPaint.getColor();
            stylePaint.setStyle(Paint.Style.FILL);
            stylePaint.setColor(fillColor);
            stylePaint.setAlpha(pressed ? 255 : 190);
            canvas.drawRoundRect(r, radius, radius, stylePaint);
            stylePaint.setStyle(Paint.Style.STROKE);
            stylePaint.setStrokeWidth(1f * density);
            stylePaint.setColor(isLightTheme() ? Color.parseColor("#94A3B8") : Color.WHITE);
            stylePaint.setAlpha(isLightTheme() ? 150 : 70);
            canvas.drawRoundRect(r, radius, radius, stylePaint);
            stylePaint.setStrokeCap(Paint.Cap.ROUND);
            stylePaint.setColor(Color.WHITE);
            stylePaint.setAlpha(isLightTheme() ? 230 : 90);
            canvas.drawLine(r.left + radius * 0.8f, r.top + 1.6f * density, r.right - radius * 0.8f, r.top + 1.6f * density, stylePaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_ROUNDED) {
            float insetX = 2.6f * density + gx;
            float insetY = 3.6f * density + gy;
            RectF cardRect = drawRectA;
            cardRect.set(
                    rawRect.left + insetX,
                    rawRect.top + insetY,
                    rawRect.right - insetX,
                    rawRect.bottom - insetY
            );
            RectF shadowRect = drawRectB;
            shadowRect.set(
                    cardRect.left,
                    cardRect.top + 1.5f * density,
                    cardRect.right,
                    cardRect.bottom + 1.5f * density
            );
            float radius = 6.5f * density;
            canvas.drawRoundRect(shadowRect, radius, radius, keyShadowPaint);
            canvas.drawRoundRect(cardRect, radius, radius, fillPaint);
        } else if (keyboardStyle == KeyboardPreferences.STYLE_MODERN_PILL) {
            float insetX = 3.2f * density + gx;
            float insetY = 4.5f * density + gy;
            RectF pillRect = drawRectA;
            pillRect.set(
                    rawRect.left + insetX,
                    rawRect.top + insetY,
                    rawRect.right - insetX,
                    rawRect.bottom - insetY
            );
            float radius = 14f * density;
            canvas.drawRoundRect(pillRect, radius, radius, fillPaint);
        } else {
            canvas.drawRect(rawRect, fillPaint);
            canvas.drawLine(rawRect.right, rawRect.top, rawRect.right, rawRect.bottom, keyDividerPaint);
        }
    }

    private android.graphics.Bitmap shotThumb = null;
    private android.graphics.BitmapShader shotShader = null;
    private final Paint shotThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shotChipFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shotChipStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix shotMatrix = new android.graphics.Matrix();
    private final RectF shotChipRect = new RectF();
    private final RectF shotThumbRect = new RectF();

    public void setScreenshotThumb(android.graphics.Bitmap b) {
        shotThumb = b;
        shotShader = b == null ? null
                : new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        invalidate();
    }

    private void drawTextChip(Canvas canvas, RectF slot, String label, Paint textPaint) {
        float chipH = Math.min(slot.height() - 12f * density, 38f * density);
        float padH = 14f * density;
        String shown = ellipsizeWord(label, slot.width() - 2f * padH, textPaint);
        float textW = textPaint.measureText(shown);
        float chipW = textW + 2f * padH;
        float cx = slot.centerX();
        float cy = slot.centerY();
        RectF chipRect = drawRectA;
        chipRect.set(cx - chipW / 2f, cy - chipH / 2f, cx + chipW / 2f, cy + chipH / 2f);
        float radius = 11f * density;

        shotChipFill.setStyle(Paint.Style.FILL);
        shotChipFill.setColor(keySpecialBgPaint.getColor());
        canvas.drawRoundRect(chipRect, radius, radius, shotChipFill);
        shotChipStroke.setStyle(Paint.Style.STROKE);
        shotChipStroke.setStrokeWidth(1f * density);
        shotChipStroke.setColor(getKeyOutlineColor());
        shotChipStroke.setAlpha(140);
        canvas.drawRoundRect(chipRect, radius, radius, shotChipStroke);

        float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(shown, cx, textY, textPaint);
    }

    private void drawScreenshotChip(Canvas canvas, RectF slot, String label, Paint textPaint) {
        float chipH = Math.min(slot.height() - 12f * density, 38f * density);
        float thumbW = chipH - 8f * density;
        float pad = 6f * density;
        float gap = 8f * density;
        String shown = ellipsizeWord(label, slot.width() - 2f * pad - thumbW - gap - 10f * density, textPaint);
        float textW = textPaint.measureText(shown);
        float chipW = pad + thumbW + gap + textW + pad + 4f * density;
        float cx = slot.centerX();
        float cy = slot.centerY();
        shotChipRect.set(cx - chipW / 2f, cy - chipH / 2f, cx + chipW / 2f, cy + chipH / 2f);
        float radius = 11f * density;

        shotChipFill.setStyle(Paint.Style.FILL);
        shotChipFill.setColor(keySpecialBgPaint.getColor());
        canvas.drawRoundRect(shotChipRect, radius, radius, shotChipFill);
        shotChipStroke.setStyle(Paint.Style.STROKE);
        shotChipStroke.setStrokeWidth(1f * density);
        shotChipStroke.setColor(getKeyOutlineColor());
        shotChipStroke.setAlpha(140);
        canvas.drawRoundRect(shotChipRect, radius, radius, shotChipStroke);

        boolean thumbRight = isArabic;
        float thumbLeft = thumbRight ? shotChipRect.right - pad - thumbW : shotChipRect.left + pad;
        shotThumbRect.set(thumbLeft, cy - thumbW * 0.5f - (chipH - 8f * density - thumbW) / 2f, thumbLeft + thumbW, cy + chipH / 2f - 4f * density);
        shotThumbRect.top = shotChipRect.top + 4f * density;
        shotThumbRect.bottom = shotChipRect.bottom - 4f * density;

        float sw = shotThumb.getWidth(), sh = shotThumb.getHeight();
        float tw = shotThumbRect.width(), th = shotThumbRect.height();
        float scale = Math.max(tw / sw, th / sh);
        shotMatrix.reset();
        shotMatrix.setScale(scale, scale);
        shotMatrix.postTranslate(shotThumbRect.left + (tw - sw * scale) / 2f, shotThumbRect.top + (th - sh * scale) / 2f);
        shotShader.setLocalMatrix(shotMatrix);
        shotThumbPaint.setShader(shotShader);
        float tr = 7f * density;
        canvas.drawRoundRect(shotThumbRect, tr, tr, shotThumbPaint);

        float textX = thumbRight ? shotThumbRect.left - gap - textW : shotThumbRect.right + gap;
        Paint.Align prevAlign = textPaint.getTextAlign();
        textPaint.setTextAlign(Paint.Align.LEFT);
        float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(shown, textX, textY, textPaint);
        textPaint.setTextAlign(prevAlign);
    }

    private boolean languageKeyShown() {
        return !prefs.isSpaceSwipeLangEnabled()
                && prefs.getEnabledLanguages().size() >= 2;
    }

    public void exitSymbolsIfActive() {
        if (isSymbols) {
            isSymbols = false;
            rebuildKeys();
        }
    }

    /** The language currently active ("ar"/"en"/"es"), for anything that needs the actual
     *  code rather than just the Arabic/not-Arabic split isArabic() gives. */
    public String getActiveLanguageCode() {
        return currentLangCode;
    }

    private String languageDisplayName(String code) {
        if (KeyboardPreferences.LANG_AR.equals(code)) return "العربية";
        if (KeyboardPreferences.LANG_ES.equals(code)) return "Español";
        if (KeyboardPreferences.LANG_HI.equals(code)) return "हिन्दी";
        return "English";
    }

    private void setCurrentLanguage(String code) {
        currentLangCode = code;
        isArabic = KeyboardPreferences.LANG_AR.equals(code);
        isHindi = KeyboardPreferences.LANG_HI.equals(code);
    }

    private void toggleLanguage() {
        toggleLanguage(1);
    }

    /** direction: +1 cycles forward, -1 cycles backward through the enabled-languages order.
     *  With only two languages enabled both directions land on the same (other) language, so
     *  this only matters once three or more are enabled. */
    private void toggleLanguage(int direction) {
        List<String> enabled = prefs.getEnabledLanguages();
        int idx = enabled.indexOf(currentLangCode);
        int size = enabled.size();
        int base = idx < 0 ? 0 : idx;
        int nextIdx = ((base + direction) % size + size) % size;
        String next = enabled.get(nextIdx);

        isSymbols = false;
        setCurrentLanguage(next);
        isShift = false;
        isCapsLock = false;
        vibrateTap(false);
        soundController.playLanguageSwitchSound();
        rebuildKeys();
        if (listener != null) listener.onLanguageChanged(isArabic);
    }

    private void commitLongPressChoice(String chosen) {
        if (listener == null) return;
        if (longPressOpensEmoji) listener.onToggleEmoji();
        else listener.onCharacterTyped(chosen);
    }

    private void drawGlobeIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = 8.2f * density;
        float prevW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStrokeWidth(1.7f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);

        canvas.drawCircle(cx, cy, r, iconStrokePaint);
        RectF meridian = drawRectA;
        meridian.set(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r);
        canvas.drawOval(meridian, iconStrokePaint);
        canvas.drawLine(cx - r, cy, cx + r, cy, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);

        // Tiny drawn face in the corner hints that a long-press on this key opens the emoji panel
        // — a small drawing like the key's other corner marks, not a real emoji character.
        // Placed in the top-right corner, well inside the key's edges so it never pokes outside it.
        // rect is the key's raw layout cell, before the per-style gap/inset AND corner rounding
        // that the visible key surface actually draws with — several styles (Pill, Glass, Outline,
        // Soft raised, Circles) round that corner enough that just clearing the straight edges
        // isn't enough; badgeCornerPos() follows the corner's own curve instead.
        float badgeR = 3.0f * density;
        float marginIn = 1.6f * density;
        float visLeft, visTop, visRight, visBottom, cornerR;
        if (keyboardStyle == KeyboardPreferences.STYLE_CIRCLES) {
            float w = rect.width(), h = rect.height();
            float d = Math.min(w, h) - 6f * density;
            cornerR = d / 2f;
            if (w <= h * 1.1f) {
                visLeft = cx - d / 2f; visTop = cy - d / 2f; visRight = cx + d / 2f; visBottom = cy + d / 2f;
            } else {
                float hpad = 3f * density;
                visLeft = rect.left + hpad; visTop = cy - d / 2f; visRight = rect.right - hpad; visBottom = cy + d / 2f;
            }
        } else {
            float[] inset = keyVisibleInsetTopRight(KeyType.LANGUAGE);
            visLeft = rect.left + inset[0]; visTop = rect.top + inset[1];
            visRight = rect.right - inset[0]; visBottom = rect.bottom - inset[1];
            cornerR = inset[2];
        }
        float[] badgePos = badgeCornerPos(visLeft, visTop, visRight, visBottom, cornerR, badgeR, marginIn);
        float bcx = badgePos[0], bcy = badgePos[1];
        float prevBadgeW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevBadgeStyle = iconStrokePaint.getStyle();
        int prevBadgeColor = iconStrokePaint.getColor();
        // Colored like the small alt-character hint (k.sub) instead of the main key-glyph
        // color, so this badge reads as a subtle hint mark rather than a full icon.
        iconStrokePaint.setColor(subTextPaint.getColor());
        iconStrokePaint.setStrokeWidth(1.1f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(bcx, bcy, badgeR, iconStrokePaint);

        iconStrokePaint.setStyle(Paint.Style.FILL);
        float eyeR = 0.45f * density;
        float eyeOffX = badgeR * 0.38f;
        float eyeOffY = badgeR * 0.22f;
        canvas.drawCircle(bcx - eyeOffX, bcy - eyeOffY, eyeR, iconStrokePaint);
        canvas.drawCircle(bcx + eyeOffX, bcy - eyeOffY, eyeR, iconStrokePaint);

        RectF smileRect = drawRectA;
        float smR = badgeR * 0.6f;
        smileRect.set(bcx - smR, bcy - smR * 0.2f, bcx + smR, bcy + smR * 1.1f);
        canvas.drawArc(smileRect, 20f, 140f, false, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevBadgeW);
        iconStrokePaint.setStyle(prevBadgeStyle);
        iconStrokePaint.setColor(prevBadgeColor);
    }

    /**
     * Drawn arrow-box + small "x" instead of the plain ⌫ glyph, so the box stays full key size
     * while only the x mark (a fixed small fraction of it) stays small — the two can't be sized
     * independently when it's a single rendered character.
     */
    private void drawBackspaceIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = 8.6f * density;
        float prevW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStrokeWidth(1.8f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);

        Path body = new Path();
        body.moveTo(cx + r * 0.72f, cy - r * 0.55f);
        body.lineTo(cx - r * 0.05f, cy - r * 0.55f);
        body.lineTo(cx - r * 0.75f, cy);
        body.lineTo(cx - r * 0.05f, cy + r * 0.55f);
        body.lineTo(cx + r * 0.72f, cy + r * 0.55f);
        body.close();
        canvas.drawPath(body, iconStrokePaint);

        float k = r * 0.22f;
        float xc = cx + r * 0.18f;
        canvas.drawLine(xc - k, cy - k, xc + k, cy + k, iconStrokePaint);
        canvas.drawLine(xc - k, cy + k, xc + k, cy - k, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);
    }

    private void drawEmojiIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = 8.2f * density;
        float prevW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStrokeWidth(1.7f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);

        canvas.drawCircle(cx, cy, r, iconStrokePaint);

        iconStrokePaint.setStyle(Paint.Style.FILL);
        float eyeR = 0.9f * density;
        float eyeOffsetX = r * 0.42f;
        float eyeOffsetY = r * 0.28f;
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeR, iconStrokePaint);
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeR, iconStrokePaint);

        iconStrokePaint.setStyle(Paint.Style.FILL);
        RectF smile = drawRectA;
        float smileR = r * 0.62f;
        smile.set(cx - smileR, cy - smileR * 0.35f, cx + smileR, cy + smileR * 1.15f);
        canvas.drawArc(smile, 25f, 130f, false, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);
    }

    private float materialTopInsetDp(KeyType type, boolean soft) {
        if (soft) return 4.0f;
        boolean letterRowKey = type == KeyType.CHAR || type == KeyType.SHIFT || type == KeyType.BACKSPACE;
        boolean bottomRowKey = type == KeyType.SYMBOLS || type == KeyType.EMOJI || type == KeyType.LANGUAGE
                || type == KeyType.SPACE || type == KeyType.PERIOD || type == KeyType.ENTER;

        if (letterRowKey) return 5.9f;
        if (bottomRowKey) return 5.9f;
        return 4.0f;
    }

    private float materialBottomInsetDp(KeyType type, boolean soft) {
        if (soft) return 4.0f;
        boolean letterRowKey = type == KeyType.CHAR || type == KeyType.SHIFT || type == KeyType.BACKSPACE;
        return letterRowKey ? 5.9f : 4.0f;
    }

    /**
     * How far the key's actually-drawn surface sits inside its raw layout cell for the current
     * keyboard style, as {horizontalInset, topInset, cornerRadius} — mirrors the per-style insets
     * AND corner radius used in drawStyledKeyCell(). The radius matters on its own: several styles
     * (Pill, Glass, Outline, Soft raised) round their corners enough that a badge sitting just
     * inside the straight inset bounds can still fall outside the actual curved corner, since the
     * visible surface cuts inward near the corner by more than the plain edge margin. Not valid
     * for STYLE_CIRCLES, whose visible shape is a circle/pill rather than an inset rect — that
     * style is handled separately by its caller.
     */
    private float[] keyVisibleInsetTopRight(KeyType type) {
        final float gx = tabletKeyGapX;
        final float gy = tabletKeyGapY + (isNumericFieldMode ? -1.9f * density : 0f);
        switch (keyboardStyle) {
            case KeyboardPreferences.STYLE_GBOARD_MATERIAL:
                return new float[]{2.0f * density + gx, materialTopInsetDp(type, false) * density + gy, 9.5f * density};
            case KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND:
                return new float[]{2.6f * density + gx, materialTopInsetDp(type, true) * density + gy, 17f * density};
            case KeyboardPreferences.STYLE_GBOARD_BORDERLESS:
                return new float[]{3.0f * density + gx, 4.5f * density + gy, 13f * density};
            case KeyboardPreferences.STYLE_OUTLINED:
                return new float[]{3.2f * density + gx, 4.2f * density + gy, 11f * density};
            case KeyboardPreferences.STYLE_KEYCAP_3D:
                return new float[]{2.8f * density + gx, 3.2f * density + gy, 7.5f * density};
            case KeyboardPreferences.STYLE_SOFT_RAISED:
                return new float[]{2.6f * density + gx, 3.6f * density + gy, 12f * density};
            case KeyboardPreferences.STYLE_GLASS:
                return new float[]{2.8f * density + gx, 3.8f * density + gy, 10f * density};
            case KeyboardPreferences.STYLE_GBOARD_ROUNDED:
                return new float[]{2.6f * density + gx, 3.6f * density + gy, 6.5f * density};
            case KeyboardPreferences.STYLE_MODERN_PILL:
                return new float[]{3.2f * density + gx, 4.5f * density + gy, 14f * density};
            default:
                return new float[]{0f, 0f, 0f};
        }
    }

    /**
     * Where to center a circular badge of radius badgeR so it sits just inside the top-right
     * corner of a rounded rect [visLeft,visTop,visRight,visBottom] with corner radius cornerR —
     * following the corner's own curve (via its arc center) rather than just the straight edges,
     * so it works whether the corner is barely rounded or the shape is almost a full circle.
     */
    private float[] badgeCornerPos(float visLeft, float visTop, float visRight, float visBottom,
                                    float cornerR, float badgeR, float marginIn) {
        float visW = visRight - visLeft, visH = visBottom - visTop;
        float r = Math.min(cornerR, Math.min(visW, visH) / 2f);
        float bx, by;
        if (r <= badgeR + marginIn) {
            bx = visRight - badgeR - marginIn;
            by = visTop + badgeR + marginIn;
        } else {
            float arcCx = visRight - r, arcCy = visTop + r;
            float dist = r - badgeR - marginIn;
            float k = 0.70710678f; // sin(45°) == cos(45°)
            bx = arcCx + dist * k;
            by = arcCy - dist * k;
        }
        return new float[]{bx, by};
    }

    public void setVoiceListening(boolean listening) {
        if (voiceListening == listening) return;
        voiceListening = listening;
        invalidate();
    }

    private void applyLanguageMode() {
        List<String> enabled = prefs.getEnabledLanguages();
        // Only one language enabled -- it must be that one, however many languages used to
        // be enabled. Otherwise, if the active language got disabled from under it (settings
        // changed while the keyboard was open), fall back to the first enabled one; if the
        // active language is still enabled, leave it exactly as the user left it.
        String want = enabled.size() == 1 ? enabled.get(0)
                : (enabled.contains(currentLangCode) ? currentLangCode : enabled.get(0));
        if (want.equals(currentLangCode)) return;
        setCurrentLanguage(want);
        isShift = false;
        isCapsLock = false;
        if (listener != null) listener.onLanguageChanged(isArabic);
    }

    private void drawMicIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float prevW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStrokeWidth(1.7f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);

        RectF body = drawRectA;
        body.set(cx - 3.5f * density, cy - 9.2f * density, cx + 3.5f * density, cy + 2.6f * density);
        canvas.drawRoundRect(body, 3.5f * density, 3.5f * density, iconStrokePaint);

        RectF arc = drawRectC;
        arc.set(cx - 6.8f * density, cy - 5.4f * density, cx + 6.8f * density, cy + 8.0f * density);
        canvas.drawArc(arc, 0f, 180f, false, iconStrokePaint);

        canvas.drawLine(cx, cy + 8.0f * density, cx, cy + 9.8f * density, iconStrokePaint);
        canvas.drawLine(cx - 3.6f * density, cy + 9.8f * density, cx + 3.6f * density, cy + 9.8f * density, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);
    }

    private void drawImageIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float hw = 8.2f * density;
        float hh = 6.6f * density;
        float prevW = iconStrokePaint.getStrokeWidth();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStrokeWidth(1.7f * density);
        iconStrokePaint.setStyle(Paint.Style.STROKE);
        RectF frame = drawRectA;
        frame.set(cx - hw, cy - hh, cx + hw, cy + hh);
        canvas.drawRoundRect(frame, 2.4f * density, 2.4f * density, iconStrokePaint);
        iconStrokePaint.setStrokeWidth(1.4f * density);
        canvas.drawCircle(cx - hw * 0.42f, cy - hh * 0.35f, 1.5f * density, iconStrokePaint);
        canvas.drawLine(cx - hw + 1.5f * density, cy + hh - 1.8f * density, cx - hw * 0.1f, cy - 0.2f * density, iconStrokePaint);
        canvas.drawLine(cx - hw * 0.1f, cy - 0.2f * density, cx + hw * 0.35f, cy + hh * 0.4f, iconStrokePaint);
        canvas.drawLine(cx + hw * 0.1f, cy + hh * 0.1f, cx + hw * 0.5f, cy - hh * 0.2f, iconStrokePaint);
        canvas.drawLine(cx + hw * 0.5f, cy - hh * 0.2f, cx + hw - 1.5f * density, cy + hh - 1.8f * density, iconStrokePaint);
        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);
    }

    private void drawClipboardMenuIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float hw = 7.6f * density;
        float hh = 9.2f * density;
        float prevW = iconStrokePaint.getStrokeWidth();
        iconStrokePaint.setStrokeWidth(1.7f * density);

        RectF board = drawRectA;
        board.set(cx - hw, cy - hh + 2f * density, cx + hw, cy + hh);
        canvas.drawRoundRect(board, 2.6f * density, 2.6f * density, iconStrokePaint);

        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStyle(Paint.Style.FILL);
        RectF clip = drawRectA;
        clip.set(cx - 3.6f * density, cy - hh, cx + 3.6f * density, cy - hh + 4.6f * density);
        canvas.drawRoundRect(clip, 1.8f * density, 1.8f * density, iconStrokePaint);
        iconStrokePaint.setStyle(prevStyle);

        iconStrokePaint.setStrokeWidth(1.4f * density);
        canvas.drawLine(cx - 3.8f * density, cy + 0.2f * density, cx + 3.8f * density, cy + 0.2f * density, iconStrokePaint);
        canvas.drawLine(cx - 3.8f * density, cy + 3.6f * density, cx + 1.8f * density, cy + 3.6f * density, iconStrokePaint);
        iconStrokePaint.setStrokeWidth(prevW);
    }

    private void drawToolbarIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float prevW = iconStrokePaint.getStrokeWidth();
        iconStrokePaint.setStrokeWidth(1.6f * density);
        float halfW = 8f * density;
        float gap = 4.6f * density;
        canvas.drawLine(cx - halfW, cy - gap, cx + halfW, cy - gap, iconStrokePaint);
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, iconStrokePaint);
        canvas.drawLine(cx - halfW, cy + gap, cx + halfW, cy + gap, iconStrokePaint);
        iconStrokePaint.setStrokeWidth(prevW);
    }

    private void drawShiftKeyIcon(Canvas canvas, RectF rect) {
        float prevSize = mainTextPaint.getTextSize();
        int prevColor = mainTextPaint.getColor();
        Typeface prevTypeface = mainTextPaint.getTypeface();
        mainTextPaint.setTextSize(23f * scaledDensity);
        mainTextPaint.setTypeface(Typeface.create(prevTypeface, Typeface.BOLD));
        mainTextPaint.setFakeBoldText(true);
        if (isCapsLock || isShift) {
            mainTextPaint.setColor(Color.parseColor("#3B82F6"));
        }
        float textY = rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
        Paint.Style prevStyle = mainTextPaint.getStyle();
        float prevStroke = mainTextPaint.getStrokeWidth();
        mainTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mainTextPaint.setStrokeWidth(1.3f * density);
        canvas.drawText("⬆", rect.centerX(), textY, mainTextPaint);
        mainTextPaint.setStyle(prevStyle);
        mainTextPaint.setStrokeWidth(prevStroke);
        mainTextPaint.setTypeface(prevTypeface);
        mainTextPaint.setFakeBoldText(false);

        if (isCapsLock) {
            float lineHalfW = 9f * density;
            canvas.drawLine(rect.centerX() - lineHalfW, rect.top + 8f * density, rect.centerX() + lineHalfW, rect.top + 8f * density, shiftActiveAccentPaint);
        } else if (isShift) {
            float dotR = 2.2f * density;
            canvas.drawCircle(rect.centerX(), rect.top + 8f * density, dotR, shiftActiveAccentPaint);
        }

        mainTextPaint.setColor(prevColor);
        mainTextPaint.setTextSize(prevSize);
    }

    private String ellipsizeWord(String text, float maxWidth, Paint paint) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "…";
        int len = text.length();
        while (len > 1 && paint.measureText(text.substring(0, len) + ellipsis) > maxWidth) {
            len--;
        }
        return text.substring(0, len) + ellipsis;
    }

    public void setEnterActionIcon(int actionId) {
        if (this.enterActionId != actionId) {
            this.enterActionId = actionId;
            invalidate();
        }
    }

    private void drawLargeEnterIcon(Canvas canvas, RectF rect) {
        switch (enterActionId) {
            case android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH:
                drawSearchIcon(canvas, rect); return;
            case android.view.inputmethod.EditorInfo.IME_ACTION_GO:
                drawArrowRightIcon(canvas, rect); return;
            case android.view.inputmethod.EditorInfo.IME_ACTION_DONE:
                drawCheckIcon(canvas, rect); return;
            case android.view.inputmethod.EditorInfo.IME_ACTION_SEND:
                drawSendIcon(canvas, rect); return;
            case android.view.inputmethod.EditorInfo.IME_ACTION_NEXT:
                drawArrowRightIcon(canvas, rect); return;
            default:
                break;
        }
        float cx = rect.centerX();
        float cy = rect.centerY();
        float halfW = 12.0f * density;
        float halfH = 7.8f * density;
        float arrowHead = 5.8f * density;

        Path path = new Path();
        path.moveTo(cx + halfW, cy - halfH);
        path.lineTo(cx + halfW, cy + 2f * density);
        path.lineTo(cx - halfW, cy + 2f * density);

        path.moveTo(cx - halfW + arrowHead, cy + 2f * density - arrowHead);
        path.lineTo(cx - halfW, cy + 2f * density);
        path.lineTo(cx - halfW + arrowHead, cy + 2f * density + arrowHead);

        canvas.drawPath(path, enterArrowPaint);
    }

    private void drawSearchIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX() - 1.5f * density;
        float cy = rect.centerY() - 1.5f * density;
        float r = 6.2f * density;
        canvas.drawCircle(cx, cy, r, enterArrowPaint);
        float hx1 = cx + r * 0.72f, hy1 = cy + r * 0.72f;
        canvas.drawLine(hx1, hy1, hx1 + 5.5f * density, hy1 + 5.5f * density, enterArrowPaint);
    }

    private void drawCheckIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        Path path = new Path();
        path.moveTo(cx - 9f * density, cy);
        path.lineTo(cx - 2.5f * density, cy + 6.5f * density);
        path.lineTo(cx + 9f * density, cy - 7f * density);
        canvas.drawPath(path, enterArrowPaint);
    }

    private void drawArrowRightIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float halfW = 9f * density;
        float head = 6f * density;
        boolean rtl = isArabic;
        float dir = rtl ? -1f : 1f;
        Path path = new Path();
        path.moveTo(cx - dir * halfW, cy);
        path.lineTo(cx + dir * halfW, cy);
        path.moveTo(cx + dir * (halfW - head), cy - head);
        path.lineTo(cx + dir * halfW, cy);
        path.lineTo(cx + dir * (halfW - head), cy + head);
        canvas.drawPath(path, enterArrowPaint);
    }

    private void drawSendIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        boolean rtl = isArabic;
        float dir = rtl ? -1f : 1f;
        Path path = new Path();
        path.moveTo(cx - dir * 8.5f * density, cy - 6.5f * density);
        path.lineTo(cx + dir * 9f * density, cy);
        path.lineTo(cx - dir * 8.5f * density, cy + 6.5f * density);
        path.lineTo(cx - dir * 2.5f * density, cy);
        path.close();
        Paint.Style prevStyle = enterArrowPaint.getStyle();
        enterArrowPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, enterArrowPaint);
        enterArrowPaint.setStyle(prevStyle);
    }

    private KeyDef findKeyAt(float x, float y) {
        for (KeyDef dup : splitDuplicateKeys) {
            if (dup.rect.contains(x, y)) return dup;
        }
        for (List<KeyDef> row : rows) {
            for (KeyDef k : row) {
                if (k.rect.contains(x, y)) return k;
            }
        }
        return null;
    }

    private int getHorizontalSuggestionZone(float x) {
        if (getWidth() <= 0) return 1;
        float ratio = Math.max(0f, Math.min(0.999f, x / (float) getWidth()));
        return (int) Math.floor(ratio * 3f);
    }

    private int getClickedSuggestionSlot(float x) {
        for (int i = 0; i < 3; i++) {
            if (x >= suggestionRects[i].left && x <= suggestionRects[i].right) {
                return i;
            }
        }

        return x < suggestionRects[0].left ? 0 : (x > suggestionRects[2].right ? 2 : 1);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float sugBottom = getSuggestionBarHeight();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:

                activePointerId = event.getPointerId(0);
                multiTouchResolved = false;

                if (isLongPressPopupVisible && longPressAwaitingTap) {
                    if (longPressPopupBoxRect.contains(x, y)) {
                        int idx = findLongPressOptionIndexAtX(x);
                        if (idx >= 0 && idx < longPressOptions.size()) {
                            String chosenAlt = longPressOptions.get(idx);
                            vibrateTap(false);
                            soundController.playKeyClick();
                            commitLongPressChoice(chosenAlt);
                        }
                        isLongPressPopupVisible = false;
                        longPressAwaitingTap = false;
                        invalidate();
                        return true;
                    } else {

                        isLongPressPopupVisible = false;
                        longPressAwaitingTap = false;
                        invalidate();
                    }
                }

                startX = x;
                startY = y;
                lastTouchX = x;
                isDragging = false;
                longPressConsumed = false;
                suggestionLongPressConsumed = false;
                touchDownSuggestionSlot = -1;
                menuButtonPressed = false;
                micButtonPressed = false;
                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                spaceCursorModeActive = false;
                handler.removeCallbacks(spaceCursorHoldRunnable);

                // Resize-drag mode is modal: once it's on, nothing else on the keyboard
                // responds to touch -- never a key, the mic/menu buttons, or a suggestion
                // tap. The real content is genuinely frozen, not just visually dimmed. All
                // of the handle/Reset/Done/drag handling itself now lives entirely in the
                // separate resize-preview popup window floating above this view (see
                // ResizePreviewOverlayView), which sits on top and should normally
                // intercept these touches before they ever reach here -- this is kept as a
                // defensive fallback in case that popup isn't showing for some reason.
                // never a key, the mic/menu buttons, or a suggestion tap. The real content
                // is genuinely frozen, not just visually dimmed.
                if (isResizeDragMode) {
                    return true;
                }

                if (voiceEnabled && micButtonRect.contains(x, y)) {
                    micButtonPressed = true;
                    vibrateTap(false);
                    soundController.playKeyClick();
                    invalidate();
                    return true;
                }

                if (menuButtonRect.contains(x, y)) {
                    menuButtonPressed = true;
                    vibrateTap(false);
                    soundController.playKeyClick();
                    invalidate();
                    return true;
                }

                if (y <= sugBottom) {
                    touchDownSuggestionSlot = getClickedSuggestionSlot(x);
                    suggestionTouchDownTime = SystemClock.uptimeMillis();
                    handler.postDelayed(suggestionLongPressRunnable, prefs.getLongPressDelayMs());
                    return true;
                }

                touchDownKey = findKeyAt(x, y);
                if (touchDownKey != null) {
                    visualPressedKey = touchDownKey;
                    if (prefs.isKeyBounceAnimationEnabled()) {
                        bounceKey = touchDownKey;
                        bounceStartTime = SystemClock.uptimeMillis();
                    }
                    if (prefs.isKeyPreviewPopupEnabled()
                            && (touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD)) {

                        activePreviewKey = touchDownKey;
                    }
                    invalidate();

                    if (touchDownKey.type == KeyType.BACKSPACE) {
                        backspaceRepeatStartUptime = SystemClock.uptimeMillis();
                        handler.postDelayed(repeatBackspaceRunnable, 380L);
                    } else if (touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD || touchDownKey.type == KeyType.SHIFT || touchDownKey.type == KeyType.LANGUAGE || touchDownKey.type == KeyType.EMOJI) {
                        handler.postDelayed(longPressRunnable, prefs.getLongPressDelayMs());
                    } else if (touchDownKey.type == KeyType.SPACE && prefs.isSpaceCursorDragEnabled()) {
                        spaceCursorModeActive = false;

                        handler.postDelayed(spaceCursorHoldRunnable, 180L);
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN: {
                if (multiTouchResolved || isResizeDragMode) {
                    return true;
                }

                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(repeatBackspaceRunnable);
                handler.removeCallbacks(suggestionLongPressRunnable);
                handler.removeCallbacks(spaceCursorHoldRunnable);

                if (isLongPressPopupVisible) {
                    isLongPressPopupVisible = false;
                    longPressAwaitingTap = false;
                } else if (touchDownKey != null && !isDragging && !longPressConsumed) {
                    KeyDef primaryKey = touchDownKey;
                    vibrateTap(false);
                    if (primaryKey.type == KeyType.BACKSPACE) {
                        soundController.playBackspaceClick();
                    } else {
                        soundController.playKeyClick();
                    }
                    handleKeyTap(primaryKey);
                }

                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                isDragging = false;
                spaceCursorModeActive = false;

                int secondaryIndex = event.getActionIndex();
                float sx = event.getX(secondaryIndex);
                float sy = event.getY(secondaryIndex);
                if (sy > sugBottom) {
                    KeyDef secondaryKey = findKeyAt(sx, sy);
                    if (secondaryKey != null) {
                        bounceKey = secondaryKey;
                        bounceStartTime = SystemClock.uptimeMillis();
                        vibrateTap(false);
                        if (secondaryKey.type == KeyType.BACKSPACE) {
                            soundController.playBackspaceClick();
                        } else {
                            soundController.playKeyClick();
                        }
                        handleKeyTap(secondaryKey);
                    }
                }

                multiTouchResolved = true;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP:
                // The lifted finger was already resolved as its own tap the moment the
                // overlap was first detected (ACTION_POINTER_DOWN above); there is
                // nothing left to do for it now, and reading its position here would
                // risk the same pointer-index mixup this is meant to avoid.
                return true;

            case MotionEvent.ACTION_MOVE:
                if (multiTouchResolved) {
                    return true;
                }
                lastTouchX = x;
                float dxMove = x - startX;
                float dyMove = y - startY;

                if (isLongPressPopupVisible && !longPressAwaitingTap) {
                    if (Math.abs(dxMove) > 8f * density || longPressPopupBoxRect.contains(x, y)) {
                        hasSlidDuringLongPress = true;

                        int steps = longPressCellW > 0f
                                ? Math.round((x - longPressDragBaseX) / (longPressCellW * 1.4f))
                                : 0;
                        int newIdx = Math.max(0, Math.min(longPressOptions.size() - 1, longPressBaseIndex + steps));
                        if (newIdx != longPressHighlightedIndex) {
                            longPressHighlightedIndex = newIdx;
                            vibrateTap(false);
                            invalidate();
                        }
                    }
                    return true;
                }

                if (Math.hypot(dxMove, dyMove) > 9f * density) {
                    isDragging = true;
                    menuButtonPressed = false;
                    micButtonPressed = false;
                    handler.removeCallbacks(longPressRunnable);
                    handler.removeCallbacks(repeatBackspaceRunnable);
                    handler.removeCallbacks(suggestionLongPressRunnable);
                    if (visualPressedKey != null || activePreviewKey != null) {
                        visualPressedKey = null;
                        activePreviewKey = null;
                        invalidate();
                    }
                }

                if (spaceCursorModeActive && listener != null) {
                    float cursorStepPx = 14f * density;
                    while (x - spaceCursorStepBaseX >= cursorStepPx) {
                        listener.onCursorStep(true);
                        spaceCursorStepBaseX += cursorStepPx;
                    }
                    while (x - spaceCursorStepBaseX <= -cursorStepPx) {
                        listener.onCursorStep(false);
                        spaceCursorStepBaseX -= cursorStepPx;
                    }
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (multiTouchResolved) {
                    // Both touches of the overlap were already committed as their own
                    // taps in ACTION_POINTER_DOWN. This final lift (now the only pointer
                    // left) carries no gesture info we can trust -- its x/y may belong to
                    // whichever finger Android kept, not to a matching ACTION_DOWN -- so
                    // it is swallowed here instead of being run through the swipe checks.
                    multiTouchResolved = false;
                    touchDownKey = null;
                    visualPressedKey = null;
                    activePreviewKey = null;
                    isDragging = false;
                    spaceCursorModeActive = false;
                    invalidate();
                    return true;
                }
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(repeatBackspaceRunnable);
                handler.removeCallbacks(suggestionLongPressRunnable);
                handler.removeCallbacks(spaceCursorHoldRunnable);

                if (spaceCursorModeActive) {

                    spaceCursorModeActive = false;
                    touchDownKey = null;
                    visualPressedKey = null;
                    return true;
                }

                if (isLongPressPopupVisible && !longPressAwaitingTap) {
                    if (longPressHighlightedIndex >= 0 && longPressHighlightedIndex < longPressOptions.size()) {
                        String chosen = longPressOptions.get(longPressHighlightedIndex);
                        isLongPressPopupVisible = false;
                        longPressAwaitingTap = false;
                        touchDownKey = null;
                        visualPressedKey = null;
                        activePreviewKey = null;
                        vibrateTap(false);
                        soundController.playKeyClick();
                        commitLongPressChoice(chosen);
                        invalidate();
                        return true;
                    } else {

                        longPressAwaitingTap = true;
                        touchDownKey = null;
                        visualPressedKey = null;
                        activePreviewKey = null;
                        invalidate();
                        return true;
                    }
                }

                float dx = x - startX;
                float dy = y - startY;
                KeyDef key = touchDownKey;
                boolean wasMenuPressed = menuButtonPressed;
                boolean wasMicPressed = micButtonPressed;

                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                menuButtonPressed = false;
                micButtonPressed = false;
                isDragging = false;
                invalidate();

                if (longPressConsumed) return true;

                if (suggestionLongPressConsumed) {
                    suggestionLongPressConsumed = false;
                    touchDownSuggestionSlot = -1;
                    return true;
                }
                touchDownSuggestionSlot = -1;

                if (wasMicPressed && micButtonRect.contains(x, y)) {
                    if (listener != null) listener.onVoiceInputRequested();
                    return true;
                }

                if (wasMenuPressed && menuButtonRect.contains(x, y)) {
                    if (listener != null) listener.onToggleClipboardMenu();
                    return true;
                }

                if (startY > 0f && startY <= sugBottom && Math.abs(dx) < 22f * density && Math.abs(dy) < 22f * density) {
                    if (SystemClock.uptimeMillis() - suggestionTouchDownTime >= MIN_SUGGESTION_TAP_HOLD_MS) {
                        int slot = getClickedSuggestionSlot(x);
                        vibrateTap(false);
                        if (hasSuggestionInSlot(slot)) soundController.playSwipeUpSound();
                        launchWordOutsideKeyboard(slot);
                        if (listener != null) listener.onSwipeUpSuggestion(slot);
                    }
                    return true;
                }

                if (key != null && key.type == KeyType.SPACE && prefs.isSpaceSwipeLangEnabled()
                        && prefs.getEnabledLanguages().size() >= 2) {
                    float spaceSwipeThreshold = 26f * density;
                    if (Math.abs(dx) > spaceSwipeThreshold && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        toggleLanguage(dx > 0 ? 1 : -1);
                        return true;
                    }
                }

                float swipeUpThreshold = 42f * density;
                if (dy < -swipeUpThreshold && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    int zone = getHorizontalSuggestionZone(startX);
                    vibrateTap(false);
                    if (hasSuggestionInSlot(zone)) soundController.playSwipeUpSound();
                    launchWordOutsideKeyboard(zone);
                    if (listener != null) listener.onSwipeUpSuggestion(zone);
                    return true;
                }

                float swipeDownThreshold = 42f * density;
                if (prefs.isSwipeDownSymbolsEnabled() && dy > swipeDownThreshold && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    // Same switch the ?123 / ABC key makes, so it behaves identically for every
                    // language (each language has its own symbols layout) and swiping down again
                    // on the symbols layout returns to the letters.
                    vibrateTap(false);
                    soundController.playKeyClick();
                    isSymbols = !isSymbols;
                    rebuildKeys();
                    return true;
                }
                if (prefs.isSwipeDownCopyPasteEnabled() && dy > swipeDownThreshold && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    boolean done = listener != null && listener.onSwipeDownCopyPaste();
                    if (done) {
                        vibrateTap(true);
                        soundController.playSwipeDeleteSound();
                    }
                    return true;
                }

                float swipeLeftThreshold = 75f * density;
                boolean deleteDirOk = (prefs.getSwipeDeleteDirection() == 1) ? (dx < -swipeLeftThreshold) : (dx > swipeLeftThreshold);
                if (prefs.isSwipeDeleteWordEnabled() && deleteDirOk && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    boolean deleted = listener != null && listener.onSwipeLeftDeleteWord();
                    if (deleted) {
                        startDeleteTagAnimation(startX, startY, dx);
                        vibrateTap(true);
                        soundController.playSwipeDeleteSound();
                    }
                    return true;
                }

                boolean selectAllDirOk = (prefs.getSwipeDeleteDirection() == 1) ? (dx > swipeLeftThreshold) : (dx < -swipeLeftThreshold);
                if (prefs.isSwipeSelectAllEnabled() && selectAllDirOk && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    boolean selected = listener != null && listener.onSwipeSelectAll();
                    if (selected) {
                        vibrateTap(true);
                        soundController.playSwipeDeleteSound();
                    }
                    return true;
                }

                if (key != null) {
                    vibrateTap(false);
                    if (key.type == KeyType.BACKSPACE) {
                        soundController.playBackspaceClick();
                    } else {
                        soundController.playKeyClick();
                    }
                    if (key.type == KeyType.CHAR || key.type == KeyType.PERIOD) {

                        afterDrawAction = () -> handleKeyTap(key);
                        invalidate();
                    } else {
                        handleKeyTap(key);
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleKeyTap(KeyDef key) {
        if (listener == null) return;
        switch (key.type) {
            case CHAR:
                listener.onCharacterTyped(key.main);
                if (!isArabic && isShift && !isCapsLock) {
                    isShift = false;
                    rebuildKeys();
                }
                break;
            case SPACE:
                listener.onCharacterTyped(" ");
                break;
            case PERIOD:
                listener.onCharacterTyped(isHindi ? "।" : ".");
                break;
            case BACKSPACE:
                listener.onBackspaceSingle();
                break;
            case ENTER:
                listener.onEnterKey();
                break;
            case SHIFT:
                long now = SystemClock.uptimeMillis();
                if (now - lastShiftTapTime < 340L) {
                    isCapsLock = true;
                    isShift = true;
                } else if (isCapsLock) {
                    isCapsLock = false;
                    isShift = false;
                } else {
                    isShift = !isShift;
                }
                lastShiftTapTime = now;
                rebuildKeys();
                break;
            case SYMBOLS:
                isSymbols = !isSymbols;
                rebuildKeys();
                break;
            case LANGUAGE:
                if (emojiKeySuggestion != null) {
                    String chosenEmoji = emojiKeySuggestion;
                    emojiKeySuggestion = null;
                    listener.onEmojiSuggestionAccepted(chosenEmoji);
                    invalidate();
                } else {
                    toggleLanguage();
                }
                break;
            case EMOJI:
                if (emojiKeySuggestion != null) {
                    String chosen = emojiKeySuggestion;
                    emojiKeySuggestion = null;
                    listener.onEmojiSuggestionAccepted(chosen);
                    invalidate();
                } else {
                    listener.onToggleEmoji();
                }
                break;
        }
    }
}
