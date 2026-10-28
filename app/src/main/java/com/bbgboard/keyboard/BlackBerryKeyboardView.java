package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

/**
 * واجهة الكيبورد المتقدمة (الإصدار 7.0):
 * 1. إبطاء أنميشن طيران الكلمة (520ms) وزيادة وضوح الكلمة الطائرة عبر خلفية كبسولة بارزة وخط عريض واضح.
 * 2. حذف الأرقام العربية من أزرار الحروف العربية.
 * 3. تصغير لوقو الرمز التعبيري (😊) في الصف السفلي ليتناسق مع الأزرار.
 * 4. إضافة بوب أب معاينة الحرف عند اللمس (Key Press Popup) مع خيار لتفعيله أو إيقافه.
 * 5. إضافة بوب أب الخيارات المتعددة عند الضغط المطول على الحروف العربية لاختيار التشكيل أو الهمزات باللمس أو التمرير.
 */
public class BlackBerryKeyboardView extends View {

    public enum KeyType {
        CHAR, BACKSPACE, SHIFT, SYMBOLS, EMOJI, SPACE, PERIOD, ENTER
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
        void onBackspaceSingle();
        boolean onSwipeLeftDeleteWord();
        void onSwipeUpSuggestion(int zoneIndex);
        void onEnterKey();
        void onToggleClipboardMenu();
        void onToggleEmoji();
        void onLanguageChanged(boolean isArabic);
        void onKeyboardScaleChanged(float newScale);
        void onSuggestionLongPress(int slotIndex, String word);
        /** خطوة واحدة لتحريك المؤشر من سحب زر المسافة (true = يمين بصرياً، false = يسار بصرياً). */
        void onCursorStep(boolean moveRight);
        /** المستخدم لمس زر الإيموجي وهو يعرض اقتراحاً — يُدرج هذا الإيموجي مباشرة بدل فتح قائمة الإيموجي. */
        void onEmojiSuggestionAccepted(String emoji);
    }

    private final KeyboardPreferences prefs;
    private final SoundController soundController;
    private KeyboardActionListener listener;

    private boolean isArabic = true;
    private boolean isSymbols = false;
    private boolean isShift = false;
    private boolean isCapsLock = false;
    private boolean isResizeDragMode = false;
    private boolean isNumericFieldMode = false;
    private long lastShiftTapTime = 0L;

    private List<String> suggestions = Arrays.asList("السلام", "بسم", "مرحبا");
    private final RectF menuButtonRect = new RectF();
    private final RectF[] suggestionRects = new RectF[]{new RectF(), new RectF(), new RectF()};
    private final RectF resizeHandleBarRect = new RectF();
    private boolean menuButtonPressed = false;

    // 1. متغيرات أنميشن الكلمة الطائرة (أبطأ وأكثر وضوحاً: 520ms)
    private PopupWindow topOverlayPopup;
    private TopOverlayCanvasView topOverlayView;
    private String flyingWordText = null;
    private float flyingWordCenterX = 0f;
    private float overlayOffsetX = 0f;
    private long flyingStartTime = 0L;
    private int animatingSlotIndex = -1;
    private static final long FLY_OUTSIDE_DURATION_MS = 650L;

    // 7. متغيرات بوب أب معاينة الحرف عند الضغط (Key Press Preview Popup)
    private KeyDef activePreviewKey = null;

    // 8. متغيرات بوب أب الخيارات عند الضغط المطول (Long-Press Alternatives Popup)
    private boolean isLongPressPopupVisible = false;
    private boolean longPressAwaitingTap = false;
    private boolean hasSlidDuringLongPress = false;
    private List<String> longPressOptions = Collections.emptyList();
    private final List<RectF> longPressCellRects = new ArrayList<>();
    private final RectF longPressPopupBoxRect = new RectF();
    /** مستطيل مؤقت يُعاد استخدامه بدل تخصيص RectF جديد مع كل مفتاح في كل رسمة، لتفادي تهنيج/تقطّع
     * الرسم (خصوصاً أثناء سحب إصبع فوق بوب أب الضغط المطول، حيث invalidate() يتكرر بسرعة عالية). */
    private final RectF scratchKeyRect = new RectF();
    // مستطيلات مؤقتة للرسم: تُعاد استخدامها بدل إنشاء كائنات جديدة في كل إطار (تخفف الضغط على الذاكرة)
    private final RectF drawRectA = new RectF();
    private final RectF drawRectB = new RectF();
    private final RectF drawRectC = new RectF();
    private int longPressHighlightedIndex = 0;
    /** عرض خلية واحدة ونقطة انطلاق السحب (مركز الزر المضغوط، أي مكان الإصبع فعلياً)، والخلية اللي
     * تقع تحت الإصبع في تلك اللحظة (longPressBaseIndex) — الاختيار أثناء السحب يُحسب بالمسافة
     * النسبية من هذي النقطة بدل موضع مطلق أو افتراض إن الإصبع يبدأ فوق أول خلية (وهو خطأ، لأن
     * البوب أب يكون متمركزاً فوق الزر لا بادئاً من حافته). */
    private float longPressCellW = 44f;
    private float longPressDragBaseX = 0f;
    private int longPressBaseIndex = 0;

    private final List<List<KeyDef>> rows = new ArrayList<>();
    /** نسخ إضافية من الحروف المكررة على طرفي فجوة المنتصف (مثل Gboard)؛ تُحسب فقط عند تفعيل وضع التقسيم. */
    private final List<KeyDef> splitDuplicateKeys = new ArrayList<>();
    // فجوة إضافية بين الأزرار تُفعَّل فقط في وضع التابلت (الكيبورد المقسوم)
    private float tabletKeyGapX = 0f;
    private float tabletKeyGapY = 0f;
    private static final java.util.Set<String> SPLIT_GAP_DUPLICATE_LETTERS =
            new java.util.HashSet<>(Arrays.asList("ا", "غ", "ة", "g", "v", "G", "V"));

    private final float density;
    private final float scaledDensity;
    private float keyboardScale = 1.0f;
    private float keyFontScale = 1.12f;
    private float suggestionBarScale = 0.85f;
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
    private final Paint sugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sugPrimaryTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingWordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flyingBadgeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupCardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupCardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint enterArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shiftActiveAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float startX = 0f;
    private float startY = 0f;
    private float resizeStartScale = 1.0f;
    private boolean isDragging = false;
    private boolean isResizingDrag = false;
    private boolean longPressConsumed = false;

    private KeyDef touchDownKey = null;
    private KeyDef visualPressedKey = null;

    private int touchDownSuggestionSlot = -1;
    private boolean suggestionLongPressConsumed = false;

    // اقتراح إيموجي يظهر مكان أيقونة زر الإيموجي نفسه (بدل شريط الاقتراحات)، ويختفي تلقائياً لو ما لُمس.
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
                if ((touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD) && !touchDownKey.alts.isEmpty()) {
                    longPressConsumed = true;
                    activePreviewKey = null;
                    vibrateTap(true);
                    soundController.playKeyClick();
                    openLongPressPopupForKey(touchDownKey);
                }
            }
        }
    };

    private final Runnable repeatBackspaceRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchDownKey != null && touchDownKey.type == KeyType.BACKSPACE && !isDragging) {
                vibrateTap(false);
                soundController.playBackspaceClick();
                if (listener != null) listener.onBackspaceSingle();
                handler.postDelayed(this, 60L);
            }
        }
    };

    // --- التحكم بالمؤشر عبر سحب زر المسافة (بدون التأثير إطلاقاً على السحب السريع لتبديل اللغة) ---
    // الفكرة: تبديل اللغة يبقى يعمل بالضبط كما كان (سحبة سريعة وتحرير الإصبع). لكن لو استمر الإصبع
    // ضاغطاً على المسافة لمدة قصيرة (أطول من الفليك العادي)، يتفعّل "وضع تحريك المؤشر" تلقائياً، وعندها
    // تحريك الإصبع يمين/يسار يحرّك المؤشر حرفاً حرفاً، ورفع الإصبع بعدها لا يبدّل اللغة إطلاقاً.
    private boolean spaceCursorModeActive = false;
    private float spaceCursorStepBaseX = 0f;
    private final Runnable spaceCursorHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchDownKey != null && touchDownKey.type == KeyType.SPACE) {
                spaceCursorModeActive = true;
                spaceCursorStepBaseX = startX;
                vibrateTap(true);
            }
        }
    };

    /**
     * نافذة علوية شفافة فوق الكيبورد ترسم الكلمة الطائرة بوضوح عالٍ وبسرعة متزنة (520ms) خارج حدود الكيبورد.
     */
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

    /**
     * أنميشن اختيار الكلمة المقترحة (مطابق لكيبورد بلاك بيري القديم):
     * الكلمة تظهر كنص شفاف خفيف (بدون كبسولة) من مكانها في شريط الاقتراحات وترتفع للأعلى
     * بحركة تبدأ سريعة ثم تتباطأ، وتتلاشى تدريجياً قبل أن تصل لحقل الكتابة.
     */
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

    public BlackBerryKeyboardView(Context context, KeyboardPreferences prefs, SoundController soundController) {
        super(context);
        this.prefs = prefs;
        this.soundController = soundController;
        this.density = getResources().getDisplayMetrics().density;
        this.scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        mainTextPaint.setTextAlign(Paint.Align.CENTER);
        mainTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

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

        popupHighlightPaint.setStyle(Paint.Style.FILL);
        popupHighlightPaint.setColor(Color.parseColor("#2563EB"));

        popupTextPaint.setTextAlign(Paint.Align.CENTER);
        popupTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

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

        resizeBarPaint.setColor(Color.parseColor("#059669"));
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
        dismissFlyingPopup();
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
            } catch (Exception ignored) {}
        }
    }

    public void reloadAppearanceFromPrefs() {
        this.keyboardScale = prefs.getKeyboardScale();
        this.keyFontScale = prefs.getKeyFontScale();
        this.suggestionBarScale = prefs.getSuggestionBarScale();
        this.themeMode = prefs.getThemeMode();
        this.keyboardStyle = prefs.getKeyboardStyle();

        applyThemeColors();
        requestLayout();
        computeKeyBounds(getWidth());
        rebuildKeys();
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
            popupCardBgPaint.setColor(Color.parseColor("#FFFFFF"));
            popupCardBorderPaint.setColor(Color.parseColor("#94A3B8"));
            popupTextPaint.setColor(Color.parseColor("#0F172A"));
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
            popupCardBgPaint.setColor(Color.parseColor("#1E293B"));
            popupCardBorderPaint.setColor(Color.parseColor("#475569"));
            popupTextPaint.setColor(Color.WHITE);
            iconStrokePaint.setColor(Color.parseColor("#E2E8F0"));
            enterArrowPaint.setColor(Color.WHITE);
        } else {
            bgShellPaint.setColor(Color.parseColor("#111111"));
            sugBarPaint.setColor(Color.parseColor("#171717"));
            sugCenterHighlightPaint.setColor(Color.parseColor("#20232A"));
            sugFlashPaint.setColor(Color.parseColor("#283242"));
            keyBgPaint.setColor(Color.parseColor("#262626"));
            keyShadowPaint.setColor(Color.parseColor("#090909"));
            keySpecialBgPaint.setColor(Color.parseColor("#181818"));
            keyPressedPaint.setColor(Color.parseColor("#323846"));
            fretLinePaint.setColor(Color.parseColor("#3B3B3B"));
            keyDividerPaint.setColor(Color.parseColor("#363636"));
            mainTextPaint.setColor(Color.WHITE);
            subTextPaint.setColor(Color.parseColor("#9CA3AF"));
            sugTextPaint.setColor(Color.parseColor("#E5E7EB"));
            sugPrimaryTextPaint.setColor(Color.WHITE);
            flyingWordPaint.setColor(Color.WHITE);
            flyingBadgeBgPaint.setColor(Color.parseColor("#222631"));
            flyingBadgeBorderPaint.setColor(Color.parseColor("#10B981"));
            popupCardBgPaint.setColor(Color.parseColor("#262B36"));
            popupCardBorderPaint.setColor(Color.parseColor("#525B6C"));
            popupTextPaint.setColor(Color.WHITE);
            iconStrokePaint.setColor(Color.parseColor("#E5E7EB"));
            enterArrowPaint.setColor(Color.WHITE);
        }
    }

    /** لون خلفية الكيبورد الحالي (يُستخدم لتلوين شريط التنقل أسفل الشاشة). */
    public int getShellColor() {
        return bgShellPaint.getColor();
    }

    public boolean isLightTheme() {
        return themeMode == KeyboardPreferences.THEME_GBOARD_WHITE;
    }

    private float getResizeBarHeight() {
        return isResizeDragMode ? (30f * density) : 0f;
    }

    private float getSuggestionBarHeight() {
        return 36f * density * suggestionBarScale;
    }

    private float getKeyRowHeight() {
        return 52f * density * keyboardScale;
    }

    private float getBottomRowHeight() {
        return 50f * density * keyboardScale;
    }

    private void vibrateTap(boolean longPress) {
        if (!prefs.isVibrationEnabled()) return;
        performHapticFeedback(longPress ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public void setKeyboardActionListener(KeyboardActionListener l) {
        this.listener = l;
    }

    public boolean isArabic() {
        return isArabic;
    }

    /**
     * دوال مساعدة عامة (public) تكشف شكل/أبعاد زر "؟123" الحقيقي في الكيبورد الرئيسي، حتى تقدر
     * قوائم الحافظة والإيموجي ترسم زر "أبجد"/الرجوع بنفس شكله ومكانه بالضبط بدل تقليد تقريبي.
     */
    public int getActiveKeyboardStyle() {
        return keyboardStyle;
    }

    public int getSuggestionBarColor() {
        return sugBarPaint.getColor();
    }

    /**
     * يرسم زر حرف عادي بنفس ستايل وألوان الكيبورد الحالي على صورة بالحجم المطلوب بالضبط.
     * تُستخدم كخلفية لأزرار اقتراحات الملء التلقائي حتى تطلع متناسقة مع أزرار الكيبورد.
     */
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

    public int getKeyDividerColor() {
        return keyDividerPaint.getColor();
    }

    public int getKeyOutlineColor() {
        return popupCardBorderPaint.getColor();
    }

    public int getKeyLabelColor() {
        return mainTextPaint.getColor();
    }

    /** ارتفاع شريط الاقتراحات العلوي (px) ومكانه، يستخدمهم الشريط المدمج لاقتراحات الملء التلقائي. */
    public float getSuggestionBarHeightPx() {
        return getSuggestionBarHeight();
    }

    public float getSuggestionBarTopPx() {
        return getResizeBarHeight();
    }

    public float getBottomRowHeightPx() {
        return getBottomRowHeight();
    }

    /** الفجوة الفارغة على طرفي الصف السفلي (يسار ويمين) عند تفعيل وضع الكيبورد المقسوم (تابلت). */
    public float getBottomRowEdgeInsetPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? widthPx * 0.03f : 0f;
    }

    /** عرض زر "؟123" الحقيقي في الصف السفلي بالضبط، بنفس نسبة وزنه من إجمالي أوزان الصف. */
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
        this.isResizeDragMode = enabled;
        requestLayout();
        computeKeyBounds(getWidth());
        invalidate();
    }

    public boolean isResizeDragMode() {
        return isResizeDragMode;
    }

    /** يلغي الشفت وتثبيت الكابتال (يستخدم عند إخفاء الكيبورد أو تبديل اللغة حتى ما يبقى الكابتال معلّق). */
    public void resetShiftState() {
        if (!isShift && !isCapsLock) return;
        isShift = false;
        isCapsLock = false;
        rebuildKeys();
    }

    public void updateAutoCapitalization(boolean shouldCapitalizeNext) {
        if (isArabic || isSymbols || isCapsLock) return;
        if (prefs.isAutoCapitalizeSentences() && shouldCapitalizeNext != isShift) {
            isShift = shouldCapitalizeNext;
            rebuildKeys();
        }
    }

    public void setSuggestions(List<String> newSuggestions) {
        setSuggestions(newSuggestions, false);
    }

    /** clipIcon: يرسم أيقونة الحافظة (خطوط بدون لون) بجانب نص الخانة الوسطى — لعرض آخر نص منسوخ. */
    public void setSuggestions(List<String> newSuggestions, boolean clipIcon) {
        if (newSuggestions != null && newSuggestions.size() >= 3) {
            suggestions = new ArrayList<>(newSuggestions.subList(0, 3));
            middleClipIcon = clipIcon;
            invalidate();
        }
    }

    private boolean middleClipIcon = false;

    /**
     * التعديل رقم 1: إطلاق الكلمة من صندوق الاقتراحات لأعلى الشاشة خارج الكيبورد بسرعة هادئة (520ms) ووضوح عالٍ.
     */
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
                int sugBottomY = loc[1] + (int) (getResizeBarHeight() + getSuggestionBarHeight());
                int popupTopY = sugBottomY - popupH;
                topOverlayPopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, popupTopY);
            }
            topOverlayView.invalidate();
        } catch (Exception ignored) {}

        postInvalidateOnAnimation();
    }

    /**
     * التعديل رقم 8: فتح بوب أب الخيارات المتعددة فوق الحرف عند الضغط المطول.
     */
    private void openLongPressPopupForKey(KeyDef key) {
        if (key == null || key.alts == null || key.alts.isEmpty()) return;
        longPressOptions = new ArrayList<>(key.alts);
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

        // يوضع البوب أب فوق الزر مباشرة، مع ضمان بقائه داخل حدود الكيبورد القابلة للمس (y >= 2dp)
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
        // نقطة انطلاق السحب = مركز الزر المضغوط فعلياً (مكان الإصبع الحقيقي)، والخلية الأساسية هي
        // الخلية اللي تقع تحت الإصبع في تلك اللحظة — وليست بالضرورة الخلية الأولى، لأن البوب أب
        // يُرسم متمركزاً فوق الزر لا بادئاً من حافته. هذا يصحح القفز المفاجئ عند أول حركة سحب بسيطة.
        longPressCellW = cellW;
        longPressDragBaseX = key.rect.centerX();
        int centerIdx = findLongPressOptionIndexAtX(longPressDragBaseX);
        longPressBaseIndex = (centerIdx >= 0) ? centerIdx : 0;
        // ملاحظة: longPressBaseIndex يُستخدم فقط كأساس لحساب خطوات السحب (لضمان دقتها)، لكن الخيار
        // المميّز افتراضياً قبل أي حركة سحب يبقى دائماً الخيار الأول (index 0) — هو الحرف الأصلي
        // للزر، ومن المتوقع إنه يكون المختار لو المستخدم رفع إصبعه فوراً بدون أي سحب.
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

    /** يُستدعى من الخدمة عند فتح حقل كلمة مرور لإظهار صف الأرقام (إن كان الخيار مفعلاً). */
    public void setPasswordFieldMode(boolean enabled) {
        this.passwordFieldMode = enabled;
    }

    /** يُستدعى من الخدمة عند فتح حقل يقبل أرقاماً فقط (رقم، هاتف، تاريخ/وقت) لعرض لوحة أرقام مخصصة بدل الحروف. */
    public void setNumericFieldMode(boolean enabled) {
        if (this.isNumericFieldMode == enabled) return;
        this.isNumericFieldMode = enabled;
        if (enabled) {
            // امسح فوراً أي اقتراحات متبقية من الحقل السابق، بدل انتظار تحديث غير متزامن من الخدمة —
            // بعض الحقول المخصصة (مثل خانات رمز التحقق المنفصلة) لا تستدعي onStartInputView بشكل
            // موثوق مع كل تبديل تركيز، فكانت اقتراحات الحقل القديم تبقى ظاهرة فوق لوحة الأرقام.
            suggestions = Arrays.asList("", "", "");
            emojiKeySuggestion = null;
        }
        rebuildKeys();
    }

    /** يعرض إيموجي مقترح مكان أيقونة زر الإيموجي نفسه (null يرجعه لأيقونته العادية). */
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

    /**
     * لوحة أرقام مخصصة تظهر تلقائياً في الحقول التي تقبل أرقاماً فقط (رقم/هاتف/تاريخ ووقت)،
     * مبنية بنفس نظام KeyDef/KeyType لتأخذ ألوان وشكل السمة والنمط المختارين تلقائياً.
     */
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
            new KeyDef("-")
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("7"),
            new KeyDef("8"),
            new KeyDef("9"),
            new KeyDef(".")
        );
        List<KeyDef> row4 = Arrays.asList(
            new KeyDef(","),
            new KeyDef("0"),
            new KeyDef("00"),
            new KeyDef("↵", "", Collections.<String>emptyList(), KeyType.ENTER, 1f)
        );
        return Arrays.asList(row1, row2, row3, row4);
    }

    /**
     * التعديل رقم 3 و 8:
     * - حذف الأرقام العربية من أزرار الحروف العربية نهائياً.
     * - تزويد كل حرف عربي بخياراته (الهمزات، الحركات، التشكيل، والحروف البديلة) لتظهر في بوب أب عند الضغط المطول.
     */
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
            new KeyDef("ل", "", Arrays.asList("لأ", "لإ", "لآ")),
            new KeyDef("ا", "", Arrays.asList("أ", "إ", "آ")),
            new KeyDef("ت"),
            new KeyDef("ن"),
            new KeyDef("م"),
            new KeyDef("ك", "", Arrays.asList("گ")),
            new KeyDef("ط", "", Arrays.asList("ظ"))
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("ذ"),
            new KeyDef("ء"),
            new KeyDef("ؤ"),
            new KeyDef("ر"),
            new KeyDef("ى", "", Arrays.asList("ئ", "ي")),
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

    private List<List<KeyDef>> buildEnglishQwertyRows() {
        boolean showNums = false; // الأرقام المطبوعة على صف الحروف الأول محذوفة نهائياً
        List<KeyDef> row1 = Arrays.asList(
            new KeyDef(getTypedEnglishChar("Q"), showNums ? "1" : "", Collections.singletonList("1")),
            new KeyDef(getTypedEnglishChar("W"), showNums ? "2" : "", Collections.singletonList("2")),
            new KeyDef(getTypedEnglishChar("E"), showNums ? "3" : "", Arrays.asList("3", "é", "è", "ê")),
            new KeyDef(getTypedEnglishChar("R"), showNums ? "4" : "", Collections.singletonList("4")),
            new KeyDef(getTypedEnglishChar("T"), showNums ? "5" : "", Collections.singletonList("5")),
            new KeyDef(getTypedEnglishChar("Y"), showNums ? "6" : "", Collections.singletonList("6")),
            new KeyDef(getTypedEnglishChar("U"), showNums ? "7" : "", Arrays.asList("7", "ú", "ü", "û")),
            new KeyDef(getTypedEnglishChar("I"), showNums ? "8" : "", Arrays.asList("8", "í", "î")),
            new KeyDef(getTypedEnglishChar("O"), showNums ? "9" : "", Arrays.asList("9", "ó", "ö", "ô")),
            new KeyDef(getTypedEnglishChar("P"), showNums ? "0" : "", Collections.singletonList("0"))
        );
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef(getTypedEnglishChar("A"), "", Arrays.asList("@", "á", "ä", "â")),
            new KeyDef(getTypedEnglishChar("S"), "", Arrays.asList("#", "ß", "$")),
            new KeyDef(getTypedEnglishChar("D"), "", Collections.singletonList("$")),
            new KeyDef(getTypedEnglishChar("F"), "", Collections.singletonList("%")),
            new KeyDef(getTypedEnglishChar("G"), "", Collections.singletonList("&")),
            new KeyDef(getTypedEnglishChar("H"), "", Collections.singletonList("-")),
            new KeyDef(getTypedEnglishChar("J"), "", Collections.singletonList("+")),
            new KeyDef(getTypedEnglishChar("K"), "", Collections.singletonList("(")),
            new KeyDef(getTypedEnglishChar("L"), "", Collections.singletonList(")"))
        );
        List<KeyDef> row3 = Arrays.asList(
            new KeyDef("⬆", "", Collections.<String>emptyList(), KeyType.SHIFT, 1.35f),
            new KeyDef(getTypedEnglishChar("Z"), "", Collections.singletonList("*")),
            new KeyDef(getTypedEnglishChar("X"), "", Collections.singletonList("\"")),
            new KeyDef(getTypedEnglishChar("C"), "", Arrays.asList("'", "ç")),
            new KeyDef(getTypedEnglishChar("V"), "", Collections.singletonList(":")),
            new KeyDef(getTypedEnglishChar("B"), "", Collections.singletonList(";")),
            new KeyDef(getTypedEnglishChar("N"), "", Arrays.asList("!", "ñ")),
            new KeyDef(getTypedEnglishChar("M"), "", Collections.singletonList("?")),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private List<List<KeyDef>> buildSymbolsRows() {
        List<KeyDef> row1 = Arrays.asList(
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
        List<KeyDef> row2 = Arrays.asList(
            new KeyDef("@", "~", Collections.singletonList("~")),
            new KeyDef("#", "^", Collections.singletonList("^")),
            new KeyDef("$", "€", Arrays.asList("€", "£", "¥", "﷼")),
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
            new KeyDef("؛", ";", Collections.singletonList(";")),
            new KeyDef("!", "¡", Collections.singletonList("¡")),
            new KeyDef("؟", "?", Collections.singletonList("?")),
            new KeyDef("،", ",", Collections.singletonList(",")),
            new KeyDef("%", "‰", Collections.singletonList("‰")),
            new KeyDef("=", "≈", Collections.singletonList("≈")),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private List<KeyDef> buildBottomRow() {
        String symLabel = isSymbols ? (isArabic ? "أبجد" : "ABC") : "?123";
        String spaceLabel = isArabic ? "‹  العربية  ›" : "‹  English  ›";
        return Arrays.asList(
            new KeyDef(symLabel, "", Collections.<String>emptyList(), KeyType.SYMBOLS, 1.25f),
            new KeyDef("😀", "", Collections.<String>emptyList(), KeyType.EMOJI, 1.0f),
            new KeyDef(spaceLabel, "", Collections.<String>emptyList(), KeyType.SPACE, 4.7f),
            new KeyDef(".", "؟", Arrays.asList("؟", "،", "!", "؛", ":", "…"), KeyType.PERIOD, 1.0f),
            new KeyDef("↵", "", Collections.<String>emptyList(), KeyType.ENTER, 1.35f)
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int keyRowCount = Math.max(3, rows.size() - 1);
        int totalHeight = (int) (getResizeBarHeight() + getSuggestionBarHeight() + getKeyRowHeight() * keyRowCount + getBottomRowHeight());
        setMeasuredDimension(w, totalHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeKeyBounds(w);
    }

    private void computeKeyBounds(int w) {
        if (w <= 0) return;
        float resizeH = getResizeBarHeight();
        float sugH = getSuggestionBarHeight();
        float keyH = getKeyRowHeight();
        float botH = getBottomRowHeight();

        resizeHandleBarRect.set(0f, 0f, w, resizeH);

        // في وضع التابلت يأخذ شريط الاقتراحات نفس فجوتي الطرفين (يمين ويسار) اللي تأخذها صفوف الكيبورد
        float sugEdge = getBottomRowEdgeInsetPx(w);
        float menuW = 42f * density;
        menuButtonRect.set(sugEdge, resizeH, sugEdge + menuW, resizeH + sugH);

        float remW = w - sugEdge * 2f - menuW;
        float thirdW = remW / 3f;
        for (int i = 0; i < 3; i++) {
            suggestionRects[i].set(sugEdge + menuW + i * thirdW, resizeH, sugEdge + menuW + (i + 1) * thirdW, resizeH + sugH);
        }

        boolean splitActive = isSplitKeyboardActive(w);
        tabletKeyGapX = splitActive ? 2.2f * density : 0f;
        tabletKeyGapY = splitActive ? 0.8f * density : 0f;
        splitDuplicateKeys.clear();
        // فجوة المنتصف 25% من العرض، وفجوتان أصغر على طرفي الكيبورد (يسار ويمين) نفس شكل الكيبورد
        // الأصلي على الشاشة الكبيرة بدل ما يلتصق الحروف بحافة الشاشة تماماً.
        float gapW = splitActive ? w * 0.25f : 0f;
        float edgeW = splitActive ? w * 0.03f : 0f;
        float halfW = (w - gapW - edgeW * 2f) / 2f;
        float leftStartX = edgeW;
        float rightStartX = edgeW + halfW + gapW;

        float topY = resizeH + sugH;
        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            List<KeyDef> row = rows.get(rIdx);
            float rowH = (rIdx == rows.size() - 1) ? botH : keyH;
            // الصف الأخير (الأسفل: الرموز، الإيموجي، المسافة، الإدخال) يبقى بشريط واحد متصل بدون فاصل —
            // بالضبط زي كيبورد الشاشات الكبيرة المعروف؛ الفاصل فقط في صفوف الحروف والأرقام فوقه.
            if (splitActive && rIdx != rows.size() - 1) {
                layoutSplitRow(row, topY, rowH, leftStartX, halfW, rightStartX);
            } else {
                // الصف الأخير (المسافة/الإدخال) يبقى شريط واحد متصل بدون فاصل بالمنتصف، لكن لو وضع
                // التقسيم مفعّل يأخذ نفس فجوتي الطرفين (يمين ويسار) مثل بقية الصفوف فوقه تماماً.
                float rowEdge = splitActive ? edgeW : 0f;
                float availRowW = w - rowEdge * 2f;
                boolean bottomRowTablet = splitActive && rIdx == rows.size() - 1;
                float totalWeight = 0f;
                for (KeyDef k : row) totalWeight += bottomRowWeight(k, bottomRowTablet);
                float leftX = rowEdge;
                for (KeyDef k : row) {
                    float kw = (bottomRowWeight(k, bottomRowTablet) / totalWeight) * availRowW;
                    k.rect.set(leftX, topY, leftX + kw, topY + rowH);
                    leftX += kw;
                }
            }
            topY += rowH;
        }
    }

    /**
     * وزن مفتاح في الصف السفلي. في وضع التابلت زر المسافة أطول والأزرار اللي على جنبيه (؟123، الإيموجي،
     * النقطة، الإدخال) أصغر شوي، لأن العرض الكبير يخلي الأزرار الجانبية ضخمة بلا فايدة.
     */
    private static float bottomRowWeight(KeyDef k, boolean tablet) {
        if (!tablet) return k.weight;
        switch (k.type) {
            case SYMBOLS: return 1.05f;
            case EMOJI: return 0.80f;
            case SPACE: return 6.10f;
            case PERIOD: return 0.80f;
            case ENTER: return 1.10f;
            default: return k.weight;
        }
    }

    /** الفجوة الإضافية الأفقية بين الأزرار (px) في وضع التابلت، أو 0 خارجه. */
    public float getTabletKeyGapXPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? 2.2f * density : 0f;
    }

    /** الفجوة الإضافية العمودية بين الأزرار (px) في وضع التابلت، أو 0 خارجه. */
    public float getTabletKeyGapYPx(int widthPx) {
        return isSplitKeyboardActive(widthPx) ? 0.8f * density : 0f;
    }

    /** هل يجب تفعيل وضع الكيبورد المقسوم لنصفين (بفاصل بالمنتصف) عند هذا العرض الحالي؟ */
    private boolean isSplitKeyboardActive(int widthPx) {
        int mode = prefs.getSplitKeyboardMode();
        if (mode == 1) return true;
        if (mode == 2) return false;
        float widthDp = widthPx / density;
        return widthDp >= 600f; // عتبة الشاشات الكبيرة القياسية (تابلت أو الشاشة الداخلية لجوال قابل للطي)
    }

    /**
     * يوزّع مفاتيح صف واحد على نصفين منفصلين بفاصل فاضٍ بالمنتصف. الحرف القريب من حافة الفجوة
     * (ا، غ، ة بالعربي، وg، v بالإنجليزي) يتكرر على الجهتين — لكن بدل ما تكون النسخة المكررة مفتاح
     * عائم بحجم مختلف فوق الفجوة، هي تُحسب كعضو حقيقي ضمن توزيع أوزان نصفها الجديد، فتاخذ بالضبط
     * نفس حجم بقية مفاتيح ذلك النصف — هذا يحل مشكلة تفاوت حجم الحروف بين الجهتين.
     */
    private void layoutSplitRow(List<KeyDef> row, float topY, float rowH, float leftStartX, float halfW, float rightStartX) {
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
            float kw = leftWeight > 0f ? (k.weight / leftWeight) * halfW : 0f;
            k.rect.set(leftX, topY, leftX + kw, topY + rowH);
            leftX += kw;
            if (k == dupForLeft) splitDuplicateKeys.add(k);
        }
        float rightX = rightStartX;
        for (KeyDef k : rightKeys) {
            float kw = rightWeight > 0f ? (k.weight / rightWeight) * halfW : 0f;
            k.rect.set(rightX, topY, rightX + kw, topY + rowH);
            rightX += kw;
            if (k == dupForRight) splitDuplicateKeys.add(k);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), bgShellPaint);

        float baseLetterSp = (!isArabic && !isSymbols) ? 23.0f : 22.5f;
        mainTextPaint.setTextSize(baseLetterSp * scaledDensity * keyFontScale);
        subTextPaint.setTextSize(10.5f * scaledDensity * Math.min(1.25f, keyFontScale));
        sugTextPaint.setTextSize(14.5f * scaledDensity);
        sugPrimaryTextPaint.setTextSize(15.5f * scaledDensity);

        if (isResizeDragMode) {
            canvas.drawRect(resizeHandleBarRect, resizeBarPaint);
            String label = "↕ اسحب للأعلى أو الأسفل لتغيير الحجم (" + Math.round(keyboardScale * 100) + "%) — اضغط للحفظ ✓";
            sugPrimaryTextPaint.setTextSize(12f * scaledDensity);
            int prevColor = sugPrimaryTextPaint.getColor();
            sugPrimaryTextPaint.setColor(Color.WHITE);
            float ty = resizeHandleBarRect.centerY() - (sugPrimaryTextPaint.descent() + sugPrimaryTextPaint.ascent()) / 2f;
            canvas.drawText(label, resizeHandleBarRect.centerX(), ty, sugPrimaryTextPaint);
            sugPrimaryTextPaint.setColor(prevColor);
            sugPrimaryTextPaint.setTextSize(15.5f * scaledDensity);
        }

        float sugTop = getResizeBarHeight();
        float sugBottom = sugTop + getSuggestionBarHeight();
        canvas.drawRect(0f, sugTop, getWidth(), sugBottom, sugBarPaint);

        if (menuButtonPressed) {
            canvas.drawRect(menuButtonRect, sugFlashPaint);
        }
        drawClipboardMenuIcon(canvas, menuButtonRect);
        canvas.drawLine(menuButtonRect.right, sugTop + 5f * density, menuButtonRect.right, sugBottom - 5f * density, keyDividerPaint);

        long now = SystemClock.uptimeMillis();
        boolean isFlyingActive = (flyingWordText != null && (now - flyingStartTime) < FLY_OUTSIDE_DURATION_MS);

        for (int i = 0; i < 3; i++) {
            RectF r = suggestionRects[i];
            if (i < 2) {
                canvas.drawLine(r.right, sugTop + 5f * density, r.right, sugBottom - 5f * density, keyDividerPaint);
            }

            String word = (i < suggestions.size()) ? suggestions.get(i) : "";
            Paint paint = sugTextPaint;
            float textY = r.centerY() - (paint.descent() + paint.ascent()) / 2f;

            if (isFlyingActive && i == animatingSlotIndex) {
                float prog = (float) (now - flyingStartTime) / (float) FLY_OUTSIDE_DURATION_MS;
                int prevAlpha = paint.getAlpha();
                paint.setAlpha((int) (Math.min(1f, 0.35f + prog * 2.2f) * 255f));
                canvas.drawText(ellipsizeWord(word, r.width() - 10f * density, paint), r.centerX(), textY, paint);
                paint.setAlpha(prevAlpha);
            } else if (i == 1 && middleClipIcon && !word.isEmpty()) {
                // أيقونة الحافظة نفسها (نفس شكل أيقونة القائمة) على يسار النص، والمجموعة كلها في منتصف الخانة
                float iconW = 18f * density;
                float gap = 6f * density;
                String shown = ellipsizeWord(word, r.width() - 14f * density - iconW - gap, paint);
                float textW = paint.measureText(shown);
                float startX = r.centerX() - (iconW + gap + textW) / 2f;
                drawRectB.set(startX, r.top, startX + iconW, r.bottom);
                drawClipboardMenuIcon(canvas, drawRectB);
                Paint.Align prevAlign = paint.getTextAlign();
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(shown, startX + iconW + gap, textY, paint);
                paint.setTextAlign(prevAlign);
            } else {
                canvas.drawText(ellipsizeWord(word, r.width() - 10f * density, paint), r.centerX(), textY, paint);
            }

        }
        canvas.drawLine(0f, sugBottom, getWidth(), sugBottom, fretLinePaint);

        // 2. رسم صفوف المفاتيح
        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            List<KeyDef> row = rows.get(rIdx);
            for (KeyDef k : row) {
                boolean isSpecial = (k.type != KeyType.CHAR && k.type != KeyType.SPACE);
                Paint bg;
                if (k == visualPressedKey && !isDragging) {
                    bg = keyPressedPaint;
                } else if (isSpecial) {
                    bg = keySpecialBgPaint;
                } else {
                    bg = keyBgPaint;
                }

                drawStyledKeyCell(canvas, k, bg);

                if (!k.sub.isEmpty()) {
                    canvas.drawText(k.sub, k.rect.right - 5.5f * density, k.rect.top + 13.5f * density, subTextPaint);
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
                } else if (k.type == KeyType.EMOJI) {
                    // التعديل رقم 6: تصغير لوقو الرمز التعبيري في الصف السفلي ليكون متناسقاً وأنيقاً
                    float prevSize = mainTextPaint.getTextSize();
                    String glyph = k.main;
                    if (emojiKeySuggestion != null) {
                        // اقتراح إيموجي جاهز: دائرة تمييز خلف الإيموجي نفسه بدل أيقونة زر الإيموجي الأساسية،
                        // حتى يكون واضح إنه مختلف عن شكل الزر العادي 😊 وملفت للانتباه.
                        float ringR = Math.min(k.rect.width(), k.rect.height()) * 0.40f;
                        canvas.drawCircle(k.rect.centerX(), k.rect.centerY(), ringR, popupHighlightPaint);
                        glyph = emojiKeySuggestion;
                        mainTextPaint.setTextSize(17.5f * scaledDensity);
                    } else {
                        mainTextPaint.setTextSize(15.5f * scaledDensity);
                    }
                    float textY = k.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.0f * density;
                    canvas.drawText(glyph, k.rect.centerX(), textY, mainTextPaint);
                    mainTextPaint.setTextSize(prevSize);
                } else {
                    float prevSize = mainTextPaint.getTextSize();
                    if (k.type == KeyType.SYMBOLS) {
                        mainTextPaint.setTextSize(15f * scaledDensity);
                    }
                    String displayLabel = k.main;
                    if (!isArabic && !isSymbols && k.type == KeyType.CHAR && prefs.isAlwaysShowUppercaseKeycapsEn()) {
                        displayLabel = k.main.toUpperCase(Locale.ROOT);
                    }
                    float textY = k.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
                    canvas.drawText(displayLabel, k.rect.centerX(), textY, mainTextPaint);
                    mainTextPaint.setTextSize(prevSize);
                }
            }
            if (keyboardStyle == KeyboardPreferences.STYLE_BLACKBERRY_FRETS && !row.isEmpty() && rIdx < rows.size() - 1) {
                float rowBottom = row.get(0).rect.bottom;
                canvas.drawLine(0f, rowBottom, getWidth(), rowBottom, fretLinePaint);
            }
        }

        // 2ب. رسم نسخ الحروف المكررة على طرفي فجوة المنتصف (وضع الكيبورد المقسوم على الشاشات الكبيرة)
        for (KeyDef dup : splitDuplicateKeys) {
            Paint dupBg = (dup == visualPressedKey && !isDragging) ? keyPressedPaint : keyBgPaint;
            drawStyledKeyCell(canvas, dup, dupBg);
            float prevSize = mainTextPaint.getTextSize();
            String displayLabel = dup.main;
            if (!isArabic && !isSymbols && prefs.isAlwaysShowUppercaseKeycapsEn()) {
                displayLabel = dup.main.toUpperCase(Locale.ROOT);
            }
            float textY = dup.rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
            canvas.drawText(displayLabel, dup.rect.centerX(), textY, mainTextPaint);
            mainTextPaint.setTextSize(prevSize);
        }

        // 3. التعديل رقم 7: رسم بوب أب معاينة الحرف عند اللمس (Key Press Preview Popup)
        if (activePreviewKey != null) {
            drawKeyPreviewBubble(canvas, activePreviewKey);
        }

        // 4. التعديل رقم 8: رسم بوب أب الخيارات المتعددة عند الضغط المطول (Long-Press Alternatives Popup)
        if (isLongPressPopupVisible && !longPressOptions.isEmpty()) {
            drawLongPressPopupMenu(canvas);
        }

        // رسم الكلمة الطائرة على الكانفاس إذا لم تكن النافذة العلوية نشطة
        if (isFlyingActive && (topOverlayPopup == null || !topOverlayPopup.isShowing())) {
            float progress = (float) (now - flyingStartTime) / (float) FLY_OUTSIDE_DURATION_MS;
            float startYCanvas = sugTop + getSuggestionBarHeight() * 0.62f;
            drawGhostFlyingWord(canvas, flyingWordText, flyingWordCenterX, startYCanvas, progress);
            postInvalidateOnAnimation();
        } else if (!isFlyingActive && flyingWordText != null) {
            flyingWordText = null;
            animatingSlotIndex = -1;
        }
    }

    /** بوب أب صغير يظهر فوراً فوق الزر المضغوط حالياً ليعاين حرفه مكبّراً، بنفس ألوان البطاقات الحالية حسب السمة. */
    private void drawKeyPreviewBubble(Canvas canvas, KeyDef key) {
        float bubbleW = Math.max(key.rect.width() * 0.95f, 36f * density);
        float bubbleH = key.rect.height() * 1.05f;
        float cx = key.rect.centerX();
        float left = cx - bubbleW / 2f;
        if (left < 4f * density) left = 4f * density;
        if (left + bubbleW > getWidth() - 4f * density) left = getWidth() - 4f * density - bubbleW;
        float top = key.rect.top - bubbleH - 2f * density;
        if (top < 2f * density) top = 2f * density;
        RectF bubbleRect = drawRectA;
        bubbleRect.set(left, top, left + bubbleW, top + bubbleH);

        canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, popupCardBgPaint);
        canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, popupCardBorderPaint);

        float prevSize = popupTextPaint.getTextSize();
        popupTextPaint.setTextSize(19f * scaledDensity);
        float ty = bubbleRect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f;
        canvas.drawText(key.main, bubbleRect.centerX(), ty, popupTextPaint);
        popupTextPaint.setTextSize(prevSize);
    }

    private void drawLongPressPopupMenu(Canvas canvas) {
        canvas.drawRoundRect(longPressPopupBoxRect, 12f * density, 12f * density, popupCardBgPaint);
        canvas.drawRoundRect(longPressPopupBoxRect, 12f * density, 12f * density, popupCardBorderPaint);

        popupTextPaint.setTextSize(19.5f * scaledDensity);
        int normalTextColor = popupTextPaint.getColor();

        for (int i = 0; i < longPressOptions.size() && i < longPressCellRects.size(); i++) {
            RectF cell = longPressCellRects.get(i);
            String opt = longPressOptions.get(i);
            if (i == longPressHighlightedIndex) {
                canvas.drawRoundRect(cell, 8f * density, 8f * density, popupHighlightPaint);
                popupTextPaint.setColor(Color.WHITE);
            } else {
                popupTextPaint.setColor(normalTextColor);
            }
            float ty = cell.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f;
            canvas.drawText(opt, cell.centerX(), ty, popupTextPaint);
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
        final float gy = tabletKeyGapY;

        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_MATERIAL
                || keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND) {
            // الشكل الذي في صورة Gboard: أزرار مستديرة بفواصل، ومفاتيح ?123 والإدخال على شكل كبسولة كاملة
            boolean soft = (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND);
            RectF r = insetRect(rawRect, (soft ? 2.6f : 3.0f) * density + gx, 4.0f * density + gy);
            float radius = pillKey ? r.height() / 2f : (soft ? 17f : 9.5f) * density;
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_BORDERLESS) {
            // بدون حدود للأزرار (مثل خيار Gboard بدون إطار): الحروف تظهر بلا خلفية، والمفاتيح الخاصة كبسولات
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
                canvas.drawRoundRect(r, radius, radius, keySpecialBgPaint);
            }
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(1.3f * density);
            outlinePaint.setColor(popupCardBorderPaint.getColor());
            outlinePaint.setAlpha(150);
            canvas.drawRoundRect(r, radius, radius, outlinePaint);
            return;
        }
        if (keyboardStyle == KeyboardPreferences.STYLE_KEYCAP_3D) {
            // زر بارز بحافة سفلية سميكة تعطي إحساس الزر الميكانيكي
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

    /** أيقونة الحافظة الحديثة (لوح بمشبك علوي وسطور نص) بدل أيقونة الخطوط الثلاثة. */
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

    private void drawShiftKeyIcon(Canvas canvas, RectF rect) {
        float prevSize = mainTextPaint.getTextSize();
        int prevColor = mainTextPaint.getColor();
        mainTextPaint.setTextSize(19f * scaledDensity);
        if (isCapsLock || isShift) {
            mainTextPaint.setColor(Color.parseColor("#3B82F6"));
        }
        float textY = rect.centerY() - (mainTextPaint.descent() + mainTextPaint.ascent()) / 2f + 1.5f * density;
        canvas.drawText("⬆", rect.centerX(), textY, mainTextPaint);

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

    private void drawLargeEnterIcon(Canvas canvas, RectF rect) {
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
        // لمس في فجوة الطرف (وضع التابلت): أقرب خانة
        return x < suggestionRects[0].left ? 0 : (x > suggestionRects[2].right ? 2 : 1);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float resizeH = getResizeBarHeight();
        float sugBottom = resizeH + getSuggestionBarHeight();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // إذا كان بوب أب الخيارات المطولة مفتوحاً وبانتظار اختيار المستخدم باللمس:
                if (isLongPressPopupVisible && longPressAwaitingTap) {
                    if (longPressPopupBoxRect.contains(x, y)) {
                        int idx = findLongPressOptionIndexAtX(x);
                        if (idx >= 0 && idx < longPressOptions.size()) {
                            String chosenAlt = longPressOptions.get(idx);
                            vibrateTap(false);
                            soundController.playKeyClick();
                            if (listener != null) listener.onCharacterTyped(chosenAlt);
                        }
                        isLongPressPopupVisible = false;
                        longPressAwaitingTap = false;
                        invalidate();
                        return true;
                    } else {
                        // الضغط خارج البوب أب يغلقه ويكمل ضغطة الزر الجديد بشكل طبيعي
                        isLongPressPopupVisible = false;
                        longPressAwaitingTap = false;
                        invalidate();
                    }
                }

                startX = x;
                startY = y;
                isDragging = false;
                isResizingDrag = false;
                longPressConsumed = false;
                suggestionLongPressConsumed = false;
                touchDownSuggestionSlot = -1;
                menuButtonPressed = false;
                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                spaceCursorModeActive = false;
                handler.removeCallbacks(spaceCursorHoldRunnable);

                if (isResizeDragMode && y <= resizeH) {
                    isResizingDrag = true;
                    resizeStartScale = keyboardScale;
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
                    handler.postDelayed(suggestionLongPressRunnable, prefs.getLongPressDelayMs());
                    return true;
                }

                touchDownKey = findKeyAt(x, y);
                if (touchDownKey != null) {
                    visualPressedKey = touchDownKey;
                    if (prefs.isKeyPreviewPopupEnabled()
                            && (touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD)) {
                        // يظهر فوراً بدون أي تأخير (زي Gboard بالضبط)، ويُلغى فوراً أول ما يبدأ سحب حقيقي.
                        activePreviewKey = touchDownKey;
                    }
                    invalidate();

                    if (touchDownKey.type == KeyType.BACKSPACE) {
                        handler.postDelayed(repeatBackspaceRunnable, 380L);
                    } else if (touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD || touchDownKey.type == KeyType.SHIFT) {
                        handler.postDelayed(longPressRunnable, prefs.getLongPressDelayMs());
                    } else if (touchDownKey.type == KeyType.SPACE && prefs.isSpaceCursorDragEnabled()) {
                        spaceCursorModeActive = false;
                        // مهلة قصيرة جداً تميّز بين "فليك" سريع لتبديل اللغة و"ضغط مستمر" لتحريك المؤشر،
                        // بدون أي علاقة بعتبة التبديل السريع نفسها، فالسلوكان مستقلان تماماً عن بعض.
                        handler.postDelayed(spaceCursorHoldRunnable, 180L);
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dxMove = x - startX;
                float dyMove = y - startY;

                if (isResizingDrag) {
                    float deltaScale = -dyMove / (220f * density);
                    float updatedScale = Math.max(0.70f, Math.min(1.45f, resizeStartScale + deltaScale));
                    if (Math.abs(updatedScale - keyboardScale) > 0.01f) {
                        keyboardScale = updatedScale;
                        prefs.setKeyboardScale(keyboardScale);
                        requestLayout();
                        computeKeyBounds(getWidth());
                        invalidate();
                        if (listener != null) listener.onKeyboardScaleChanged(keyboardScale);
                    }
                    return true;
                }

                // إذا كان بوب أب الضغط المطول مفتوحاً والمستخدم يحرك إصبعه لاختيار الحرف البديل:
                if (isLongPressPopupVisible && !longPressAwaitingTap) {
                    if (Math.abs(dxMove) > 8f * density || longPressPopupBoxRect.contains(x, y)) {
                        hasSlidDuringLongPress = true;
                        // الخيار يُحسب بالمسافة النسبية من نقطة انطلاق السحب (مكان الإصبع الحقيقي)
                        // بدءاً من الخلية اللي تقع تحته أصلاً، خطوة واحدة = عرض خلية واحدة — بدل
                        // اختبار موضع مطلق أو افتراض بداية من الخلية الأولى، حتى ما يصير قفز مفاجئ.
                        int steps = longPressCellW > 0f
                                ? Math.round((x - longPressDragBaseX) / longPressCellW)
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
                    handler.removeCallbacks(longPressRunnable);
                    handler.removeCallbacks(repeatBackspaceRunnable);
                    handler.removeCallbacks(suggestionLongPressRunnable);
                    if (visualPressedKey != null || activePreviewKey != null) {
                        visualPressedKey = null;
                        activePreviewKey = null;
                        invalidate();
                    }
                }

                // وضع تحريك المؤشر مفعّل (بعد الضغط المستمر على المسافة): كل خطوة أفقية صغيرة = خطوة مؤشر واحدة.
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
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(repeatBackspaceRunnable);
                handler.removeCallbacks(suggestionLongPressRunnable);
                handler.removeCallbacks(spaceCursorHoldRunnable);

                if (spaceCursorModeActive) {
                    // كان في وضع تحريك المؤشر: ارفع الإصبع بدون أي تبديل لغة إطلاقاً، فقط إنهاء الوضع.
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
                        if (listener != null) listener.onCharacterTyped(chosen);
                        invalidate();
                        return true;
                    } else {
                        // المستخدم رفع إصبعه بعد ظهور البوب أب لكي يضغط على الخيار الذي يريده!
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
                boolean wasResizing = isResizingDrag;

                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                menuButtonPressed = false;
                isDragging = false;
                isResizingDrag = false;
                invalidate();

                if (wasResizing) {
                    if (Math.abs(dx) < 12f * density && Math.abs(dy) < 12f * density) {
                        setResizeDragMode(false);
                        soundController.playKeyClick();
                    }
                    return true;
                }

                if (longPressConsumed) return true;

                if (suggestionLongPressConsumed) {
                    suggestionLongPressConsumed = false;
                    touchDownSuggestionSlot = -1;
                    return true;
                }
                touchDownSuggestionSlot = -1;

                if (wasMenuPressed && menuButtonRect.contains(x, y)) {
                    if (listener != null) listener.onToggleClipboardMenu();
                    return true;
                }

                if (startY > resizeH && startY <= sugBottom && Math.abs(dx) < 22f * density && Math.abs(dy) < 22f * density) {
                    int slot = getClickedSuggestionSlot(x);
                    vibrateTap(false);
                    soundController.playSwipeUpSound();
                    launchWordOutsideKeyboard(slot);
                    if (listener != null) listener.onSwipeUpSuggestion(slot);
                    return true;
                }

                if (key != null && key.type == KeyType.SPACE && prefs.isSpaceSwipeLangEnabled()) {
                    float spaceSwipeThreshold = 28f * density;
                    if (Math.abs(dx) > spaceSwipeThreshold && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        isSymbols = false;
                        isArabic = !isArabic;
                        isShift = false;
                        isCapsLock = false;
                        vibrateTap(false);
                        soundController.playLanguageSwitchSound();
                        rebuildKeys();
                        if (listener != null) listener.onLanguageChanged(isArabic);
                        return true;
                    }
                }

                // السحب للأعلى لاختيار الكلمة المقترحة: تنطلق الكلمة لأعلى الشاشة خارج الكيبورد بوضوح وسرعة متزنة
                float swipeUpThreshold = 32f * density;
                if (dy < -swipeUpThreshold && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    int zone = getHorizontalSuggestionZone(startX);
                    vibrateTap(false);
                    soundController.playSwipeUpSound();
                    launchWordOutsideKeyboard(zone);
                    if (listener != null) listener.onSwipeUpSuggestion(zone);
                    return true;
                }

                // السحب لمسح كلمة كاملة (الاتجاه من الإعدادات: الافتراضي من اليسار لليمين)
                float swipeLeftThreshold = 40f * density;
                boolean deleteDirOk = (prefs.getSwipeDeleteDirection() == 1) ? (dx < -swipeLeftThreshold) : (dx > swipeLeftThreshold);
                if (prefs.isSwipeDeleteWordEnabled() && deleteDirOk && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    boolean deleted = listener != null && listener.onSwipeLeftDeleteWord();
                    if (deleted) {
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
                    handleKeyTap(key);
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
                listener.onCharacterTyped(".");
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
