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
        void onBackspaceSingle();
        boolean onSwipeLeftDeleteWord();
        /** سحبة من اليمين لليسار (الاتجاه المعاكس تماماً لسحبة المسح) لتحديد كل النص المكتوب. */
        boolean onSwipeSelectAll();
        /** سحبة للأسفل: نسخ النص المحدد (أو لصق إذا كنا بعد النسخ مباشرة بفترة اللصق المؤقتة). */
        boolean onSwipeDownCopyPaste();
        void onSwipeUpSuggestion(int zoneIndex);
        void onEnterKey();
        void onToggleClipboardMenu();
        /** ضغط زر المايك (الكتابة بالصوت) في صف الاقتراحات. */
        void onVoiceInputRequested();
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
    // زر المايك (الكتابة بالصوت) على يمين صف الاقتراحات، بنفس مساحة زر الحافظة على اليسار
    private final RectF micButtonRect = new RectF();
    private boolean micButtonPressed = false;
    private boolean voiceEnabled = true;
    private boolean voiceListening = false;

    // الشريط المختصر (toolbar) قبل بدء الكتابة: أيقونة الحافظة تتبدّل لأيقونة أدوات، وتبين أيقونة
    // ضبط الارتفاع بدل الخانة الوسطى من الاقتراحات، بنفس فكرة جي بورد قبل أي كتابة بالحقل.
    private boolean toolbarIdleMode = false;
    private long toolbarModeChangedAt = 0L;
    private static final long TOOLBAR_ANIM_MS = 160L;

    // 1. متغيرات أنميشن الكلمة الطائرة (أبطأ وأكثر وضوحاً: 520ms)
    private PopupWindow topOverlayPopup;
    private TopOverlayCanvasView topOverlayView;
    private String flyingWordText = null;
    private float flyingWordCenterX = 0f;
    private float overlayOffsetX = 0f;
    private long flyingStartTime = 0L;
    private int animatingSlotIndex = -1;
    private static final long FLY_OUTSIDE_DURATION_MS = 820L;

    // 7. متغيرات بوب أب معاينة الحرف عند الضغط (Key Press Preview Popup)
    private KeyDef activePreviewKey = null;
    /** عمل يُنفَّذ مباشرة بعد رسم الإطار الحالي (انظر ACTION_UP). */
    private Runnable afterDrawAction = null;

    // 8. متغيرات بوب أب الخيارات عند الضغط المطول (Long-Press Alternatives Popup)
    private boolean isLongPressPopupVisible = false;
    private boolean longPressAwaitingTap = false;
    /** بوب أب زر اللغة: اختيار 😀 يفتح لوحة الإيموجي بدل كتابته كحرف. */
    private boolean longPressOpensEmoji = false;
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
    /** إطار فقاعات المعاينة والبوب أب: بلون فواصل الكيبورد (نفس درجة الأزرار) بدل رمادي مزرق. */
    private final Paint popupEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint enterArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int enterActionId = android.view.inputmethod.EditorInfo.IME_ACTION_NONE; // نوع أيقونة زر الإدخال (بحث/تم/إرسال..)
    private final Paint enterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stylePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean enterCustom = false;
    private final Paint shiftActiveAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resizeBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float startX = 0f;
    private float startY = 0f;
    private float lastTouchX = 0f; // آخر موضع لمسة فعلي، يُستخدم لمعرفة هل بدأ سحب حقيقي قبل تفعيل وضع تحريك المؤشر
    private float resizeStartScale = 1.0f;
    private boolean isDragging = false;
    private boolean isResizingDrag = false;
    private boolean longPressConsumed = false;

    private KeyDef touchDownKey = null;
    private KeyDef visualPressedKey = null;
    // ===== أنيميشن ارتداد الزر (اختياري من الإعدادات): الزر ينضغط شوي ثم يرتد لحجمه الطبيعي =====
    private KeyDef bounceKey = null;
    private long bounceStartTime = 0L;
    private static final long KEY_BOUNCE_MS = 240L;

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
            // لا نفعّل وضع تحريك المؤشر إذا كان الإصبع تحرك أفقياً بشكل واضح قبل انتهاء المهلة —
            // هذا يعني أن المستخدم يسحب لتبديل اللغة (خصوصاً عند بداية السحب قرب حافة الزر، حيث
            // المسافة المتاحة أقل وتأخذ وقت أطول من المعتاد لتتجاوز عتبة التبديل).
            if (touchDownKey != null && touchDownKey.type == KeyType.SPACE
                    && Math.abs(lastTouchX - startX) < 14f * density) {
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

    /** خط حروف المفاتيح المختار من الإعدادات (يدعم العربي والإنجليزي)، أو خط النظام الافتراضي لو
     * تعذّر تحميل ملف الخط لأي سبب. */
    private static Typeface loadKeyTypeface(Context context, int fontId) {
        String assetPath;
        switch (fontId) {
            case KeyboardPreferences.FONT_TAJAWAL: assetPath = "fonts/tajawal_bold.ttf"; break;
            case KeyboardPreferences.FONT_ALMARAI: assetPath = "fonts/almarai_bold.ttf"; break;
            case KeyboardPreferences.FONT_TAJAWAL_REGULAR: assetPath = "fonts/tajawal_regular.ttf"; break;
            case KeyboardPreferences.FONT_ALMARAI_REGULAR: assetPath = "fonts/almarai_regular.ttf"; break;
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

    /** خط نافذة معاينة الحرف وقائمة الضغط المطول: نفس خط المفاتيح المختار بالضبط الآن إن كل خط
     * (افتراضي/Tajawal/Almarai) له نسخة Regular وBold صريحة يختارها المستخدم بنفسه. */
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
        this.sugTextScale = 1f + 0.08f * prefs.getSuggestionTextLevel();
        this.themeMode = prefs.getThemeMode();
        this.keyboardStyle = prefs.getKeyboardStyle();
        mainTextPaint.setTypeface(loadKeyTypeface(getContext(), prefs.getKeyFont()));
        popupTextPaint.setTypeface(popupTypefaceFor(prefs.getKeyFont()));
        applyLanguageMode();

        applyThemeColors();
        applyEnterColor();
        requestLayout();
        computeKeyBounds(getWidth());
        rebuildKeys();
    }

    /** لون كبسولة زر الإدخال المخصص (من الإعدادات)، وسهمه أبيض ليبقى واضحاً فوق أي لون. */
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

    /** غير مستخدمة حالياً: الفراغ فوق صف المسافة صار يُضبط عبر materialTopInsetDp نفسه (6.8dp)
     * بدل مساحة تخطيط إضافية منفصلة. أبقيناها 0 حتى لا نضيف ارتفاع زيادة غير مقصود للوحة. */
    private float extraBottomRowGapPx() {
        return 0f;
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
        return isSplitKeyboardActive(widthPx) ? widthPx * 0.03f : SIDE_MARGIN_DP * density;
    }

    /** هامش صغير على يمين ويسار كل الصفوف (كل اللغات) خارج وضع التابلت، بدل التصاق الأزرار بحافة الشاشة. */
    private static final float SIDE_MARGIN_DP = 1.5f;

    /** إزاحة صف الإنجليزي الثاني (ASDF...) من الجانبين بنسبة من عرض مفتاح (جي بورد = 0.5، هنا أقل شوي). */
    private static final float ENGLISH_ROW2_INDENT_KEYS = 0.35f;

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

    /** يبدّل بين شريط الاقتراحات العادي والشريط المختصر (قبل بدء الكتابة، مثل جي بورد). */
    public void setToolbarIdleMode(boolean idle) {
        if (this.toolbarIdleMode == idle) return;
        this.toolbarIdleMode = idle;
        toolbarModeChangedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public boolean isToolbarIdleMode() {
        return toolbarIdleMode;
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
        setSuggestions(newSuggestions, clipIcon ? 1 : 0);
    }

    /** iconType: 0 بدون أيقونة، 1 أيقونة الحافظة (نص منسوخ)، 2 أيقونة صورة (لقطة شاشة). */
    public void setSuggestions(List<String> newSuggestions, int iconType) {
        // كانت الدالة تتجاهل أي قائمة أقل من 3 عناصر بالكامل (حتى القائمة الفاضية المقصودة لمسح
        // الاقتراحات)، فتبقى الكلمة القديمة ظاهرة. الآن تُكمَّل بفراغات بدل ما تُرفض.
        List<String> padded = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            padded.add((newSuggestions != null && i < newSuggestions.size()) ? newSuggestions.get(i) : "");
        }
        suggestions = padded;
        middleIconType = iconType;
        invalidate();
    }

    private int middleIconType = 0;

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
    // لوحة أرقام بأربعة أعمدة بنفس شكل لوحة جي بورد الأصلية للأرقام (4 صفوف فقط حتى ما يتغير
    // ارتفاع الكيبورد)، والعمود الرابع فيه أزرار وظيفية تحمل رموز بديلة بنفس أسلوب كيبوردنا
    // (رمز صغير على الزاوية يُكتب بالضغط المطول).
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
            // زر الاستفهام يتبع لغة الكيبورد الحالية: عربي يطلع "؟" كبير و"?" صغير، وإنجليزي بالعكس.
            new KeyDef(isArabic ? "؟" : "?", isArabic ? "?" : "؟", Collections.singletonList(isArabic ? "?" : "؟")),
            new KeyDef("،", ",", Collections.singletonList(",")),
            new KeyDef("%", "‰", Collections.singletonList("‰")),
            new KeyDef("=", "≈", Collections.singletonList("≈")),
            new KeyDef("⌫", "", Collections.<String>emptyList(), KeyType.BACKSPACE, 1.35f)
        );
        return Arrays.asList(row1, row2, row3);
    }

    private List<KeyDef> buildBottomRow() {
        String symLabel = isSymbols ? (isArabic ? "أبجد" : "ABC") : (isArabic ? "؟123" : "?123");
        // لغة واحدة فقط: بدون أسهم التبديل لأن السحب للتبديل معطّل
        boolean singleLanguage = prefs.getLanguageMode() != KeyboardPreferences.LANGUAGE_MODE_BOTH;
        String spaceLabel = singleLanguage ? (isArabic ? "العربية" : "English")
                : (isArabic ? "‹  العربية  ›" : "‹  English  ›");
        return Arrays.asList(
            new KeyDef(symLabel, "", Collections.<String>emptyList(), KeyType.SYMBOLS, 1.25f),
            // لو السحب لتبديل اللغة مقفول واللغتين مفعّلتين: زر تبديل اللغة (🌐) مكان الإيموجي، والإيموجي بالضغط المطوّل
            languageKeyShown()
                ? new KeyDef("🌐", "", Collections.singletonList("😀"), KeyType.LANGUAGE, 1.0f)
                : new KeyDef("😀", "", Collections.<String>emptyList(), KeyType.EMOJI, 1.0f),
            new KeyDef(spaceLabel, "", Collections.<String>emptyList(), KeyType.SPACE, 4.7f),
            new KeyDef(".", "؟", Arrays.asList("؟", "،", "!", "؛", ":", "…"), KeyType.PERIOD, 1.0f),
            new KeyDef("↵", "", Collections.<String>emptyList(), KeyType.ENTER, 1.35f)
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int keyRowCount = Math.max(3, rows.size() - 1);
        int totalHeight = (int) (getResizeBarHeight() + getSuggestionBarHeight() + getKeyRowHeight() * keyRowCount + getBottomRowHeight() + extraBottomRowGapPx());
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
        voiceEnabled = prefs.isVoiceButtonEnabled();
        if (voiceEnabled) {
            micButtonRect.set(w - sugEdge - menuW, resizeH, w - sugEdge, resizeH + sugH);
        } else {
            micButtonRect.setEmpty();
        }

        float remW = w - sugEdge * 2f - menuW - (voiceEnabled ? menuW : 0f);
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
            if (rIdx == rows.size() - 1) topY += extraBottomRowGapPx();
            // الصف الأخير (الأسفل: الرموز، الإيموجي، المسافة، الإدخال) يبقى بشريط واحد متصل بدون فاصل —
            // بالضبط زي كيبورد الشاشات الكبيرة المعروف؛ الفاصل فقط في صفوف الحروف والأرقام فوقه.
            if (splitActive && rIdx != rows.size() - 1) {
                // صف ASDF... بالإنجليزي: نفس إزاحة الجوال من الطرفين الخارجيين (مفتاح النصف ≈ halfW/5.5)
                float indent = (rIdx == 1 && !isArabic && !isSymbols && row.size() == 9)
                        ? ENGLISH_ROW2_INDENT_KEYS * halfW / 5.5f : 0f;
                layoutSplitRow(row, topY, rowH, leftStartX + indent, halfW - indent, rightStartX, halfW - indent);
            } else {
                // الصف الأخير (المسافة/الإدخال) يبقى شريط واحد متصل بدون فاصل بالمنتصف، لكن لو وضع
                // التقسيم مفعّل يأخذ نفس فجوتي الطرفين (يمين ويسار) مثل بقية الصفوف فوقه تماماً.
                float rowEdge;
                if (splitActive) {
                    rowEdge = edgeW;
                } else if (isNumericFieldMode) {
                    rowEdge = 5f * density; // هامش الحافة للوحة الأرقام فقط
                } else {
                    rowEdge = SIDE_MARGIN_DP * density;
                }
                float availRowW = w - rowEdge * 2f;
                boolean bottomRowTablet = splitActive && rIdx == rows.size() - 1;
                float totalWeight = 0f;
                for (KeyDef k : row) totalWeight += bottomRowWeight(k, bottomRowTablet);
                float leftX = rowEdge;
                if (!splitActive && rIdx == 1 && !isArabic && !isSymbols && row.size() == 9) {
                    // صف ASDF... بالإنجليزي: نفس عرض مفاتيح الصف الأول مع إزاحة من الجانبين (مثل جي بورد)
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

    /**
     * وزن مفتاح في الصف السفلي. في وضع التابلت زر المسافة أطول والأزرار اللي على جنبيه (؟123، الإيموجي،
     * النقطة، الإدخال) أصغر شوي، لأن العرض الكبير يخلي الأزرار الجانبية ضخمة بلا فايدة.
     */
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

        float baseLetterSp = (!isArabic && !isSymbols) ? 23.0f : 22.5f;
        mainTextPaint.setTextSize(baseLetterSp * scaledDensity * keyFontScale);
        subTextPaint.setTextSize(10.5f * scaledDensity * Math.min(1.25f, keyFontScale));
        sugTextPaint.setTextSize(14.5f * scaledDensity * sugTextScale);
        sugPrimaryTextPaint.setTextSize(15.5f * scaledDensity * sugTextScale);

        if (isResizeDragMode) {
            canvas.drawRect(resizeHandleBarRect, resizeBarPaint);
            String label = "↕ اسحب للأعلى أو الأسفل لتغيير الحجم (" + Math.round(keyboardScale * 100) + "%) — اضغط للحفظ ✓";
            sugPrimaryTextPaint.setTextSize(12f * scaledDensity);
            int prevColor = sugPrimaryTextPaint.getColor();
            sugPrimaryTextPaint.setColor(Color.WHITE);
            float ty = resizeHandleBarRect.centerY() - (sugPrimaryTextPaint.descent() + sugPrimaryTextPaint.ascent()) / 2f;
            canvas.drawText(label, resizeHandleBarRect.centerX(), ty, sugPrimaryTextPaint);
            sugPrimaryTextPaint.setColor(prevColor);
            sugPrimaryTextPaint.setTextSize(15.5f * scaledDensity * sugTextScale);
        }

        float sugTop = getResizeBarHeight();
        float sugBottom = sugTop + getSuggestionBarHeight();
        canvas.drawRect(0f, sugTop, getWidth(), sugBottom, sugBarPaint);

        if (menuButtonPressed) {
            canvas.drawRect(menuButtonRect, sugFlashPaint);
        }
        long tbNow = SystemClock.uptimeMillis();
        long tbElapsed = tbNow - toolbarModeChangedAt;
        float tbAnimT = tbElapsed >= TOOLBAR_ANIM_MS ? 1f : Math.max(0f, tbElapsed / (float) TOOLBAR_ANIM_MS);
        if (tbAnimT < 1f) postInvalidateOnAnimation();
        // idleVisual = 1 يعني الشريط المختصر ظاهر بالكامل، 0 يعني شريط الاقتراحات العادي ظاهر بالكامل
        float idleVisual = toolbarIdleMode ? tbAnimT : (1f - tbAnimT);
        int prevMenuIconAlpha = iconStrokePaint.getAlpha();
        if (idleVisual > 0.02f) {
            iconStrokePaint.setAlpha((int) (idleVisual * 255f));
            drawClipboardMenuIcon(canvas, menuButtonRect);
        }
        if (idleVisual < 0.98f) {
            iconStrokePaint.setAlpha((int) ((1f - idleVisual) * 255f));
            drawToolbarIcon(canvas, menuButtonRect);
        }
        iconStrokePaint.setAlpha(prevMenuIconAlpha);
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

            // أثناء الشريط المختصر (قبل الكتابة) ما يصير يظهر أي كلمة مقترحة إطلاقاً في هذي الخانات،
            // حتى لو بقيت قيمة قديمة بقائمة الاقتراحات لأي سبب — الحقيقة البصرية مربوطة بهذي الحالة فقط.
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
                // أيقونة الحافظة نفسها (نفس شكل أيقونة القائمة) على يسار النص، والمجموعة كلها في منتصف الخانة
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

        // 2. رسم صفوف المفاتيح
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
                    if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_MATERIAL
                            || keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND) {
                        // نقيس حدود الحبر الفعلية للنص (مو نقطة الإرساء فقط) ونثبّتها داخل حافة
                        // الزر المرئية بهامش أمان يغطي انحناء الزاوية، بدل تخمين إزاحة ثابتة — لأن
                        // تخمين الإزاحة كان يفشل مع بعض الخطوط (الرقم يطلع خارج الزر رغم الإزاحة).
                        boolean softSub = keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND;
                        float cornerRadius = (softSub ? 17.0f : 9.5f) * density;
                        float hInsetPx = (softSub ? 2.6f : 2.1f) * density + tabletKeyGapX;
                        float topInsetPx = materialTopInsetDp(k.type, softSub) * density + tabletKeyGapY;
                        // أقل هامش يضمن الخروج من منطقة انحناء الزاوية (المسافة القطرية اللي
                        // تلامس القوس تساوي نصف القطر × (1 − جذر2/2) ≈ 0.293)، زائد هامش بسيط —
                        // مو نصف القطر كامل، عشان يضل الرقم "بالزاوية" فعلاً مو منزاح لمنتصف الزر.
                        float safeMargin = cornerRadius * 0.38f + 1.0f * density;
                        float visibleRight = k.rect.right - hInsetPx;
                        float visibleTop = k.rect.top + topInsetPx;

                        subTextPaint.getTextBounds(k.sub, 0, k.sub.length(), scratchSubBounds);
                        float desiredInkRight = visibleRight - safeMargin;
                        float desiredInkTop = visibleTop + safeMargin;
                        float x = desiredInkRight - scratchSubBounds.right;
                        float baselineY = desiredInkTop - scratchSubBounds.top;
                        Paint.Align prevAlign = subTextPaint.getTextAlign();
                        subTextPaint.setTextAlign(Paint.Align.LEFT);
                        canvas.drawText(k.sub, x, baselineY, subTextPaint);
                        subTextPaint.setTextAlign(prevAlign);
                    } else {
                        canvas.drawText(k.sub, k.rect.right - 5.5f * density, k.rect.top + 13.5f * density, subTextPaint);
                    }
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
                } else if (k.type == KeyType.EMOJI && emojiKeySuggestion == null) {
                    // ما فيه اقتراح إيموجي حالياً: نرسم أيقونة وجه مبتسم بخط أبيض بدل رمز الإيموجي الملون.
                    drawEmojiIcon(canvas, k.rect);
                } else if (k.type == KeyType.EMOJI || k.type == KeyType.LANGUAGE) {
                    // التعديل رقم 6: تصغير لوقو الرمز التعبيري في الصف السفلي ليكون متناسقاً وأنيقاً
                    float prevSize = mainTextPaint.getTextSize();
                    String glyph = k.main;
                    // اقتراح إيموجي: يتبدّل رمز الزر نفسه فقط (بدون دائرة أو تكبير)
                    if (emojiKeySuggestion != null) glyph = emojiKeySuggestion;
                    mainTextPaint.setTextSize(15.5f * scaledDensity);
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

        drawDeleteTagAnimation(canvas, now);

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

    // ===== أنيميشن المسح بالسحب: علامة حذف تنزلق مع اتجاه السحب (مقاسة من فيديو بلاك بيري الأصلي) =====
    // المدة ~380 ملّي ثانية، حركة S سلسة (تسارع ثم تباطؤ) مسافتها نحو نصف عرض الشاشة، وتكبر العلامة من 40% إلى 100%
    // مع شفافية تبدأ باهتة ثم تصير ناصعة في المنتصف وتتلاشى في آخر الحركة.
    private static final long DELETE_TAG_MS = 380L;
    private long deleteTagStart = 0L;
    private float deleteTagX0 = 0f, deleteTagY = 0f, deleteTagDir = 1f;
    private final android.graphics.Path deleteTagPath = new android.graphics.Path();
    private final Paint deleteTagFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deleteTagCross = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** مقدار تكبير/تصغير الزر أثناء أنيميشن الارتداد: ينضغط لأصغر حجم بسرعة ثم يرتد ويتجاوز
     * حجمه الطبيعي شوي قبل ما يستقر عليه تماماً (منحنى overshoot بسيط، بدون مكتبات أنيميشن). */
    private float keyBounceScale(long elapsedMs) {
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) KEY_BOUNCE_MS));
        if (t < 0.3f) {
            // انضغاط واضح وسريع (مو خفيف) أول ما تلمس الزر
            float u = t / 0.3f;
            return 1f - 0.26f * u;
        }
        // ثم ينقز ويرتد متجاوزاً حجمه الطبيعي بوضوح قبل ما يستقر (overshoot أقوى من قبل)
        float u = (t - 0.3f) / 0.7f;
        float tension = 4.2f;
        float um1 = u - 1f;
        float overshoot = um1 * um1 * ((tension + 1f) * um1 + tension) + 1f;
        return 0.74f + 0.26f * overshoot;
    }

    /** إزاحة رأسية خفيفة (بكسل) تواكب الانضغاط/الارتداد، عشان يحس إنه "ينقز" فعلاً مو بس يتمدد
     * مكانه: ينزل شوي وقت الانضغاط ثم يقفز لأعلى شوي وقت الارتداد قبل ما يرجع لمكانه الطبيعي. */
    private float keyBounceLiftPx(long elapsedMs) {
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) KEY_BOUNCE_MS));
        float maxDown = 4.0f * density;   // ينزل شوي وقت الانضغاط
        float maxUp = 13.0f * density;    // يقفز لأعلى وقت الارتداد (نقزة أوضح بكثير)
        if (t < 0.3f) {
            float u = t / 0.3f;
            return maxDown * u; // موجب = نزول (نظام إحداثيات الكانفاس: Y يكبر لتحت)
        }
        float u = (t - 0.3f) / 0.7f;
        float tension = 4.2f;
        float um1 = u - 1f;
        float overshoot = um1 * um1 * ((tension + 1f) * um1 + tension) + 1f; // يبدأ 0 ويتجاوز 1 ثم يستقر على 1
        // نحول overshoot لإزاحة: يبدأ من maxDown (استمرار الانضغاط) وينتقل لقفزة فوق الصفر (سالب) ثم يرجع صفر
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

        float tw = 30f * density * scale;   // العرض الكلي مع الرأس المدبب
        float th = 14f * density * scale;
        float tip = th * 0.5f;              // عمق الرأس المدبب
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

    /** بوب أب صغير يظهر فوراً فوق الزر المضغوط حالياً ليعاين حرفه مكبّراً، بنفس ألوان البطاقات الحالية حسب السمة. */
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
        canvas.drawText(key.main, bubbleRect.centerX(), ty, popupTextPaint);
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
        // لوحة الأرقام فقط: نقصان بسيط بالفراغ العمودي بين الصفوف (يُطبّق على كل الأنماط بنفس
        // طريقة tabletKeyGapY، بدون ما يأثر على لوحة الحروف العادية).
        // الهامش الأساسي 5.9dp لكل جهة، ناقص 1.9dp في لوحة الأرقام فقط عشان يصير 4.0dp.
        final float gy = tabletKeyGapY + (isNumericFieldMode ? -1.9f * density : 0f);

        if (keyboardStyle == KeyboardPreferences.STYLE_GBOARD_MATERIAL
                || keyboardStyle == KeyboardPreferences.STYLE_GBOARD_SOFT_ROUND) {
            // الشكل الذي في صورة Gboard: أزرار مستديرة بفواصل، ومفاتيح ?123 والإدخال على شكل كبسولة كاملة
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
        if (keyboardStyle == KeyboardPreferences.STYLE_SOFT_RAISED) {
            // بطاقة بارزة ناعمة: ظل خفيف تحتها وخط لامع رفيع بحافتها العلوية
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
            // الحروف دوائر، والمفاتيح العريضة (مسافة، شفت...) كبسولات
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
            // زجاجي: تعبئة شبه شفافة مع إطار رفيع ولمعة علوية
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

    // ===== شريحة لقطة الشاشة: مربع بحواف ناعمة فيه معاينة صغيرة للقطة + كلمة "لقطة شاشة" =====
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

    /** نفس شكل ونافذة شريحة لقطة الشاشة (مربع بخلفية وإطار)، بدون أي أيقونة — نص فقط في المنتصف. */
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

        // خلفية بنفس لون أزرار الكيبورد الخاصة وإطار رفيع بلون إطار الأزرار
        shotChipFill.setStyle(Paint.Style.FILL);
        shotChipFill.setColor(keySpecialBgPaint.getColor());
        canvas.drawRoundRect(shotChipRect, radius, radius, shotChipFill);
        shotChipStroke.setStyle(Paint.Style.STROKE);
        shotChipStroke.setStrokeWidth(1f * density);
        shotChipStroke.setColor(getKeyOutlineColor());
        shotChipStroke.setAlpha(140);
        canvas.drawRoundRect(shotChipRect, radius, radius, shotChipStroke);

        // المعاينة على جهة الكتابة: يمين في العربي، ويسار في الإنجليزي
        boolean thumbRight = isArabic;
        float thumbLeft = thumbRight ? shotChipRect.right - pad - thumbW : shotChipRect.left + pad;
        shotThumbRect.set(thumbLeft, cy - thumbW * 0.5f - (chipH - 8f * density - thumbW) / 2f, thumbLeft + thumbW, cy + chipH / 2f - 4f * density);
        shotThumbRect.top = shotChipRect.top + 4f * density;
        shotThumbRect.bottom = shotChipRect.bottom - 4f * density;

        // قص مركزي (center-crop) للصورة داخل مستطيل المعاينة
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

    // ===== زر تبديل اللغة (يظهر لما السحب على المسافة مقفول) =====

    private boolean languageKeyShown() {
        return !prefs.isSpaceSwipeLangEnabled()
                && prefs.getLanguageMode() == KeyboardPreferences.LANGUAGE_MODE_BOTH;
    }

    private void toggleLanguage() {
        isSymbols = false;
        isArabic = !isArabic;
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

    /** أيقونة الكرة الأرضية بنفس أسلوب أيقونتي الحافظة والمايك (خطوط بدون لون). */
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
    }

    /** أيقونة وجه مبتسم بخط أبيض (دائرة + عينين + ابتسامة) بدل رمز الإيموجي الملون، بنفس أسلوب أيقونة الكرة الأرضية. */
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
        // الفم مصمت (نفس شكل أيقونة جي بورد: نصف قرص مملوء مو خط منحني رفيع)، مو خط محيط فقط.
        iconStrokePaint.setStyle(Paint.Style.FILL);
        RectF smile = drawRectA;
        float smileR = r * 0.62f;
        smile.set(cx - smileR, cy - smileR * 0.35f, cx + smileR, cy + smileR * 1.15f);
        canvas.drawArc(smile, 25f, 130f, false, iconStrokePaint);

        iconStrokePaint.setStrokeWidth(prevW);
        iconStrokePaint.setStyle(prevStyle);
    }

    /** الفراغ العمودي العلوي (بالـdp) لمفتاح معيّن في ثيم Gboard Material/Soft Round. معزولة بدالة
     * حتى نفس القيم تُستخدم في رسم شكل الزر وفي موضع الرقم الصغير أعلى الزر (sub label)، فما ينزاح
     * الرقم خارج الزر لمن نكبر الفراغ. */
    private float materialTopInsetDp(KeyType type, boolean soft) {
        if (soft) return 4.0f;
        boolean letterRowKey = type == KeyType.CHAR || type == KeyType.SHIFT || type == KeyType.BACKSPACE;
        boolean bottomRowKey = type == KeyType.SYMBOLS || type == KeyType.EMOJI || type == KeyType.LANGUAGE
                || type == KeyType.SPACE || type == KeyType.PERIOD || type == KeyType.ENTER;
        // قياس فعلي من لقطة شاشة جي بورد الحقيقية: الفراغ بين الأسطر (حروف/صف المسافة) موحّد
        // تقريباً بنسبة ~6.8dp لكل جهة (وليس 12dp كما جُرّب سابقاً، لأنها طلعت أكبر من جي بورد فعلياً).
        if (letterRowKey) return 5.9f;
        if (bottomRowKey) return 5.9f;
        return 4.0f;
    }

    /** نفس فكرة materialTopInsetDp لكن للفراغ السفلي. */
    private float materialBottomInsetDp(KeyType type, boolean soft) {
        if (soft) return 4.0f;
        boolean letterRowKey = type == KeyType.CHAR || type == KeyType.SHIFT || type == KeyType.BACKSPACE;
        return letterRowKey ? 5.9f : 4.0f;
    }

    /** يميّز زر المايك أثناء الاستماع. */
    public void setVoiceListening(boolean listening) {
        if (voiceListening == listening) return;
        voiceListening = listening;
        invalidate();
    }

    /** لو اللغة الواحدة مفعّلة نثبّت لغة الكيبورد عليها (ويُلغى تأثير السحب للتبديل). */
    private void applyLanguageMode() {
        int mode = prefs.getLanguageMode();
        if (mode == KeyboardPreferences.LANGUAGE_MODE_BOTH) return;
        boolean wantArabic = mode == KeyboardPreferences.LANGUAGE_MODE_ARABIC_ONLY;
        if (isArabic == wantArabic) return;
        isArabic = wantArabic;
        isShift = false;
        isCapsLock = false;
        if (listener != null) listener.onLanguageChanged(isArabic);
    }

    /** أيقونة المايكروفون بنفس أسلوب أيقونة الحافظة: خطوط بدون لون وبنفس الحجم تقريباً. */
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

    /** أيقونة صورة (إطار وشمس وجبل) بنفس أسلوب أيقونة الحافظة: خطوط بدون لون. */
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

    /** أيقونة "أدوات" (ثلاث نقاط) تظهر مكان أيقونة الحافظة أثناء الكتابة الفعلية، للإشارة إلى أن
     * الضغط عليها يرجّع الشريط المختصر (نفس أيقونة الحافظة من حيث الضغط، فقط شكل مختلف). */
    private void drawToolbarIcon(Canvas canvas, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        Paint.Style prevStyle = iconStrokePaint.getStyle();
        iconStrokePaint.setStyle(Paint.Style.FILL);
        float dotR = 2.1f * density;
        float gap = 7.2f * density;
        canvas.drawCircle(cx - gap, cy, dotR, iconStrokePaint);
        canvas.drawCircle(cx, cy, dotR, iconStrokePaint);
        canvas.drawCircle(cx + gap, cy, dotR, iconStrokePaint);
        iconStrokePaint.setStyle(prevStyle);
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
        canvas.drawText("⬆", rect.centerX(), textY, mainTextPaint);
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

    /** يحدد أي أيقونة تُرسم على زر الإدخال حسب نوع الحقل (بحث، تم، إرسال...) مثل جي بورد. */
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
                            commitLongPressChoice(chosenAlt);
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
                lastTouchX = x;
                isDragging = false;
                isResizingDrag = false;
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

                if (isResizeDragMode && y <= resizeH) {
                    isResizingDrag = true;
                    resizeStartScale = keyboardScale;
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
                        // يظهر فوراً بدون أي تأخير (زي Gboard بالضبط)، ويُلغى فوراً أول ما يبدأ سحب حقيقي.
                        activePreviewKey = touchDownKey;
                    }
                    invalidate();

                    if (touchDownKey.type == KeyType.BACKSPACE) {
                        handler.postDelayed(repeatBackspaceRunnable, 380L);
                    } else if (touchDownKey.type == KeyType.CHAR || touchDownKey.type == KeyType.PERIOD || touchDownKey.type == KeyType.SHIFT || touchDownKey.type == KeyType.LANGUAGE) {
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
                lastTouchX = x;
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
                        // الخطوة أطول من عرض الخلية بـ 40% عشان السحب يكون أهدأ ولا يقفز بين الحروف بحركة صغيرة
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
                        commitLongPressChoice(chosen);
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
                boolean wasMicPressed = micButtonPressed;
                boolean wasResizing = isResizingDrag;

                touchDownKey = null;
                visualPressedKey = null;
                activePreviewKey = null;
                menuButtonPressed = false;
                micButtonPressed = false;
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

                if (wasMicPressed && micButtonRect.contains(x, y)) {
                    if (listener != null) listener.onVoiceInputRequested();
                    return true;
                }

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

                if (key != null && key.type == KeyType.SPACE && prefs.isSpaceSwipeLangEnabled()
                        && prefs.getLanguageMode() == KeyboardPreferences.LANGUAGE_MODE_BOTH) {
                    float spaceSwipeThreshold = 22f * density; // كانت 28: أكثر حساسية بقليل
                    if (Math.abs(dx) > spaceSwipeThreshold && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        toggleLanguage();
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

                // السحب للأسفل: نسخ النص المحدد، وإذا تم النسخ يتحول السحب للأسفل إلى لصق لمدة دقيقة.
                float swipeDownThreshold = 32f * density;
                if (prefs.isSwipeDownCopyPasteEnabled() && dy > swipeDownThreshold && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                    boolean done = listener != null && listener.onSwipeDownCopyPaste();
                    if (done) {
                        vibrateTap(true);
                        soundController.playSwipeDeleteSound();
                    }
                    return true;
                }

                // السحب لمسح كلمة كاملة (الاتجاه من الإعدادات: الافتراضي من اليسار لليمين).
                // كبّرنا الحد الأدنى ونسبة الأفقي/العمودي عن قبل (كانت 40dp/1.25) لأن السحبة كانت
                // حساسة زيادة وتمسح بالغلط أثناء الكتابة العادية.
                float swipeLeftThreshold = 62f * density;
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

                // سحبة تحديد كل النص: نفس الحد لكن بالاتجاه المعاكس تماماً لسحبة المسح، فالحركتان
                // ما تتعارضان أبداً مهما كان اتجاه المسح المختار بالإعدادات.
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
                        // الحرف ينكتب بعد رسم الإطار التالي: فقاعة المعاينة تختفي فوراً أول ما يرتفع الإصبع
                        // بدل ما تنتظر انتهاء معالجة الحرف.
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
