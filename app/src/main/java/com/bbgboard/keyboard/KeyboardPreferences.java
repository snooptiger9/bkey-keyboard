package com.bbgboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * إدارة تفضيلات الكيبورد الشاملة (الإصدار 7.0):
 * - إضافة خيار تفعيل أو إيقاف البوب أب عند الضغط على الحروف (Key Press Popup).
 * - حفظ الرموز التعبيرية المستخدمة حديثاً (Recent Emojis).
 * - حفظ إعدادات الصوت والسمات والأشكال والاختصارات.
 */
public class KeyboardPreferences {

    public static final int THEME_BLACKBERRY_DARK = 0;
    public static final int THEME_GBOARD_WHITE = 1;
    public static final int THEME_MIDNIGHT_SLATE = 2;

    public static final int STYLE_BLACKBERRY_FRETS = 0;
    public static final int STYLE_GBOARD_ROUNDED = 1;
    public static final int STYLE_MODERN_PILL = 2;
    public static final int STYLE_GBOARD_MATERIAL = 3;
    public static final int STYLE_GBOARD_BORDERLESS = 4;
    public static final int STYLE_GBOARD_SOFT_ROUND = 5;
    public static final int STYLE_OUTLINED = 6;
    public static final int STYLE_KEYCAP_3D = 7;
    public static final int STYLE_SOFT_RAISED = 8;
    public static final int STYLE_CIRCLES = 9;
    public static final int STYLE_GLASS = 10;
    // (رقم 5 "مستدير جداً" كان مكرراً مع "كبسولات" فأُزيل، ومن كان مختاره ينتقل لكبسولات)

    public static final int SOUND_PROFILE_BB_CLASSIC = 0;
    public static final int SOUND_PROFILE_BB_Q10_SOFT = 1;
    public static final int SOUND_PROFILE_GBOARD_POP = 2;
    public static final int SOUND_PROFILE_MECHANICAL = 3;
    public static final int SOUND_PROFILE_IOS_TICK = 4;
    public static final int SOUND_PROFILE_SYSTEM = 5;

    public static final int LANGUAGE_MODE_BOTH = 0;
    public static final int LANGUAGE_MODE_ARABIC_ONLY = 1;
    public static final int LANGUAGE_MODE_ENGLISH_ONLY = 2;

    public static final int SWIPE_SOUND_BB_AIR_FLICK = 0;
    public static final int SWIPE_SOUND_VELVET_BRUSH = 1;
    public static final int SWIPE_SOUND_MICRO_THOCK = 2;
    public static final int SWIPE_SOUND_PAPER_SWIPE = 3;
    public static final int SWIPE_SOUND_SOFT_WHOOSH = 4;
    public static final int SWIPE_SOUND_SILENT = 5;

    private static final String PREFS_NAME = "bb_keyboard_settings_v7";
    private final SharedPreferences prefs;

    public KeyboardPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initDefaultShortcutsIfNeeded();
    }

    private void initDefaultShortcutsIfNeeded() {
        if (!prefs.contains("shortcuts_initialized_v7")) {
            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put("سع", "السلام عليكم ورحمة الله وبركاته");
            defaults.put("جخ", "جزاك الله خيراً");
            defaults.put("اشل", "إن شاء الله");
            defaults.put("مشل", "ما شاء الله تبارك الله");
            defaults.put("يعاف", "يعطيك العافية");
            defaults.put("صلعم", "اللهم صل وسلم على نبينا محمد");
            saveShortcutsMap(defaults);
            prefs.edit().putBoolean("shortcuts_initialized_v7", true).apply();
        }
    }

    public boolean isKeyPreviewPopupEnabled() {
        return prefs.getBoolean("key_preview_popup_enabled", true);
    }

    public void setKeyPreviewPopupEnabled(boolean enabled) {
        prefs.edit().putBoolean("key_preview_popup_enabled", enabled).apply();
    }

    /** اقتراح رمز التحقق (OTP) تلقائياً من الرسائل الجديدة؛ يتطلب صلاحية قراءة الرسائل ويكون معطّلاً افتراضياً. */
    public boolean isOtpSuggestEnabled() {
        return prefs.getBoolean("otp_suggest_enabled", false);
    }

    public void setOtpSuggestEnabled(boolean enabled) {
        prefs.edit().putBoolean("otp_suggest_enabled", enabled).apply();
    }

    /** اقتراح رمز التحقق تلقائياً من إشعارات واتساب؛ يتطلب صلاحية "الوصول للإشعارات" ويكون معطّلاً افتراضياً. */
    public boolean isWhatsAppOtpSuggestEnabled() {
        return prefs.getBoolean("whatsapp_otp_suggest_enabled", false);
    }

    public void setWhatsAppOtpSuggestEnabled(boolean enabled) {
        prefs.edit().putBoolean("whatsapp_otp_suggest_enabled", enabled).apply();
    }

    /** رمز تحقق واتساب المكتشف من آخر إشعار (من WhatsAppOtpListenerService)، صالح 5 دقائق فقط ثم يختفي تلقائياً. */
    public void setPendingWhatsAppOtp(String code) {
        prefs.edit().putString("pending_wa_otp_code", code)
                .putLong("pending_wa_otp_time", System.currentTimeMillis()).apply();
    }

    public String getPendingWhatsAppOtp() {
        String code = prefs.getString("pending_wa_otp_code", null);
        if (code == null) return null;
        long time = prefs.getLong("pending_wa_otp_time", 0L);
        if (System.currentTimeMillis() - time > 5 * 60 * 1000L) return null;
        return code;
    }

    public void clearPendingWhatsAppOtp() {
        prefs.edit().remove("pending_wa_otp_code").remove("pending_wa_otp_time").apply();
    }

    public List<String> getRecentEmojis() {
        List<String> list = new ArrayList<>();
        try {
            String raw = prefs.getString("recent_emojis_v7", null);
            if (raw != null && !raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String em = arr.optString(i, "");
                    if (!em.isEmpty()) list.add(em);
                }
            }
        } catch (Exception ignored) {}
        if (list.isEmpty() && !prefs.contains("recent_emojis_v7")) {
            list.addAll(Arrays.asList(
                "😊", "😂", "❤️", "👍", "🤲", "🌹", "✨", "🔥",
                "😍", "🙏", "👏", "🥰", "😭", "👌", "🎉", "🤍"
            ));
        }
        return list;
    }

    public void recordUsedEmoji(String emoji) {
        if (emoji == null || emoji.isEmpty()) return;
        List<String> current = new ArrayList<>(getRecentEmojis());
        current.remove(emoji);
        current.add(0, emoji);
        while (current.size() > 32) {
            current.remove(current.size() - 1);
        }
        try {
            JSONArray arr = new JSONArray();
            for (String e : current) arr.put(e);
            prefs.edit().putString("recent_emojis_v7", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** حجم خط الاقتراحات: 0 = افتراضي، 1..5 = أكبر بـ 8% لكل درجة (حتى 140%). */
    public int getSuggestionTextLevel() {
        return Math.max(0, Math.min(5, prefs.getInt("suggestion_text_level", 0)));
    }

    public void setSuggestionTextLevel(int level) {
        prefs.edit().putInt("suggestion_text_level", Math.max(0, Math.min(5, level))).apply();
    }

    public float getKeyFontScale() {
        return prefs.getFloat("key_font_scale", 1.12f);
    }

    public void setKeyFontScale(float scale) {
        float clamped = Math.max(0.80f, Math.min(1.50f, scale));
        prefs.edit().putFloat("key_font_scale", clamped).apply();
    }

    public float getKeyboardScale() {
        return prefs.getFloat("keyboard_scale", 1.0f);
    }

    public void setKeyboardScale(float scale) {
        float clamped = Math.max(0.70f, Math.min(1.45f, scale));
        prefs.edit().putFloat("keyboard_scale", clamped).apply();
    }

    public float getSuggestionBarScale() {
        return prefs.getFloat("suggestion_bar_scale", 0.85f);
    }

    public void setSuggestionBarScale(float scale) {
        float clamped = Math.max(0.70f, Math.min(1.25f, scale));
        prefs.edit().putFloat("suggestion_bar_scale", clamped).apply();
    }

    public int getThemeMode() {
        return prefs.getInt("theme_mode", THEME_BLACKBERRY_DARK);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt("theme_mode", mode).apply();
    }

    public int getKeyboardStyle() {
        int style = prefs.getInt("keyboard_style", STYLE_BLACKBERRY_FRETS);
        return style == STYLE_GBOARD_SOFT_ROUND ? STYLE_MODERN_PILL : style;
    }

    /** لون كبسولة زر الإدخال (ARGB)، أو 0 = لون الكيبورد الافتراضي. */
    public int getEnterColor() {
        int c = prefs.getInt("enter_color", 0);
        return c == 0xFF2563EB ? 0xFF8AB4F8 : c; // الأزرق القديم صار أزرق فاتح (مثل جي بورد)
    }

    public void setEnterColor(int color) {
        prefs.edit().putInt("enter_color", color).apply();
    }

    /** قيمة خاصة لـ getFloatingCapsuleColor: الكبسولة تاخذ لون النص المعكوس (مثل زر الرجوع) بدل لون ثابت. */
    public static final int FLOATING_CAPSULE_FOLLOW_LABEL = -1;

    /** لون كبسولتي "أبجد"/الحذف العائمتين في نافذتي الرموز والحافظة (RGB بدون ألفا)، أو
     * FLOATING_CAPSULE_FOLLOW_LABEL لتتبع لون زر الرجوع. افتراضي: أسود. */
    public int getFloatingCapsuleColor() {
        return prefs.getInt("floating_capsule_color", 0x000000);
    }

    public void setFloatingCapsuleColor(int rgb) {
        prefs.edit().putInt("floating_capsule_color", rgb).apply();
    }

    /** مستوى عتامة الكبسولة العائمة (0-100)، يتحكم فيه المستخدم بنفسه. افتراضي 85%. */
    public int getFloatingCapsuleOpacity() {
        return prefs.getInt("floating_capsule_opacity", 85);
    }

    public void setFloatingCapsuleOpacity(int percent) {
        int clamped = Math.max(10, Math.min(100, percent));
        prefs.edit().putInt("floating_capsule_opacity", clamped).apply();
    }

    public void setKeyboardStyle(int style) {
        prefs.edit().putInt("keyboard_style", style).apply();
    }

    // خط حروف الكيبورد: 0 = افتراضي النظام، 1 = Tajawal، 2 = Almarai (كلاهما يدعم العربي والإنجليزي)
    public static final int FONT_SYSTEM_DEFAULT = 0;
    public static final int FONT_TAJAWAL = 1;
    public static final int FONT_ALMARAI = 2;

    public int getKeyFont() {
        return prefs.getInt("key_font", FONT_SYSTEM_DEFAULT);
    }

    public void setKeyFont(int font) {
        prefs.edit().putInt("key_font", font).apply();
    }

    public boolean isAutoCapitalizeSentences() {
        return prefs.getBoolean("auto_cap_sentences", true);
    }

    public void setAutoCapitalizeSentences(boolean enabled) {
        prefs.edit().putBoolean("auto_cap_sentences", enabled).apply();
    }

    public boolean isAlwaysShowUppercaseKeycapsEn() {
        return prefs.getBoolean("always_uppercase_keycaps_en", true);
    }

    public void setAlwaysShowUppercaseKeycapsEn(boolean enabled) {
        prefs.edit().putBoolean("always_uppercase_keycaps_en", enabled).apply();
    }

    public boolean isDoubleSpacePeriod() {
        return prefs.getBoolean("double_space_period", true);
    }

    public void setDoubleSpacePeriod(boolean enabled) {
        prefs.edit().putBoolean("double_space_period", enabled).apply();
    }

    public boolean isShowNumberHints() {
        return prefs.getBoolean("show_number_hints", true);
    }

    public void setShowNumberHints(boolean enabled) {
        prefs.edit().putBoolean("show_number_hints", enabled).apply();
    }

    public int getLongPressDelayMs() {
        return prefs.getInt("long_press_delay_ms", 260);
    }

    public void setLongPressDelayMs(int delayMs) {
        int clamped = Math.max(200, Math.min(700, delayMs));
        prefs.edit().putInt("long_press_delay_ms", clamped).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean("sound_enabled", true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply();
    }

    public int getSoundProfile() {
        return prefs.getInt("sound_profile", SOUND_PROFILE_BB_CLASSIC);
    }

    public void setSoundProfile(int profile) {
        prefs.edit().putInt("sound_profile", profile).apply();
    }

    public boolean isSwipeSoundEnabled() {
        return prefs.getBoolean("swipe_sound_enabled", true);
    }

    public void setSwipeSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean("swipe_sound_enabled", enabled).apply();
    }

    public int getSwipeSoundProfile() {
        return prefs.getInt("swipe_sound_profile", SWIPE_SOUND_BB_AIR_FLICK);
    }

    public void setSwipeSoundProfile(int profile) {
        prefs.edit().putInt("swipe_sound_profile", profile).apply();
    }

    public int getSoundVolume() {
        return prefs.getInt("sound_volume", 75);
    }

    public void setSoundVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        prefs.edit().putInt("sound_volume", clamped).apply();
    }

    public int getSwipeSoundVolume() {
        return prefs.getInt("swipe_sound_volume", 50);
    }

    public void setSwipeSoundVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        prefs.edit().putInt("swipe_sound_volume", clamped).apply();
    }

    public boolean isVibrationEnabled() {
        return prefs.getBoolean("vibration_enabled", true);
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply();
    }

    public boolean isSwipeUpAnimationEnabled() {
        return prefs.getBoolean("swipe_up_anim_enabled", true);
    }

    public void setSwipeUpAnimationEnabled(boolean enabled) {
        prefs.edit().putBoolean("swipe_up_anim_enabled", enabled).apply();
    }

    /** اتجاه السحب للمسح: 0 = من اليسار لليمين (الافتراضي)، 1 = من اليمين لليسار. */
    public int getSwipeDeleteDirection() {
        return prefs.getInt("swipe_delete_direction", 0);
    }

    public void setSwipeDeleteDirection(int dir) {
        prefs.edit().putInt("swipe_delete_direction", dir == 1 ? 1 : 0).apply();
    }

    public int getEmojiSizeSp() {
        return prefs.getInt("emoji_size_sp", 23);
    }

    public void setEmojiSizeSp(int sp) {
        prefs.edit().putInt("emoji_size_sp", Math.max(16, Math.min(34, sp))).apply();
    }

    public void clearRecentEmojis() {
        prefs.edit().putString("recent_emojis_v7", "[]").apply();
    }

    public void removeRecentEmoji(String emoji) {
        List<String> current = new ArrayList<>(getRecentEmojis());
        current.remove(emoji);
        try {
            JSONArray arr = new JSONArray();
            for (String e : current) arr.put(e);
            prefs.edit().putString("recent_emojis_v7", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** إظهار صف الأرقام تلقائياً في حقول كلمات المرور. */
    public boolean isNumberRowForPasswords() {
        return prefs.getBoolean("number_row_for_passwords", true);
    }

    public void setNumberRowForPasswords(boolean enabled) {
        prefs.edit().putBoolean("number_row_for_passwords", enabled).apply();
    }

    public boolean isNumberRowEnabled() {
        return prefs.getBoolean("number_row_enabled", false);
    }

    public void setNumberRowEnabled(boolean enabled) {
        prefs.edit().putBoolean("number_row_enabled", enabled).apply();
    }

    /** أرقام عربية هندية (١٢٣) في صف الأرقام العربي. */
    public boolean isArabicIndicDigits() {
        return prefs.getBoolean("arabic_indic_digits", false);
    }

    public void setArabicIndicDigits(boolean enabled) {
        prefs.edit().putBoolean("arabic_indic_digits", enabled).apply();
    }

    /** حفظ مصغّرة آخر لقطة شاشة في الحافظة (تحتاج إذن الصور). */
    public boolean isScreenshotClipEnabled() {
        return prefs.getBoolean("screenshot_clip_enabled", false);
    }

    public void setScreenshotClipEnabled(boolean enabled) {
        prefs.edit().putBoolean("screenshot_clip_enabled", enabled).apply();
    }

    // ===== اللغات =====

    /** اللغات المفعّلة: 0 العربية والإنجليزية (تبديل بالسحب)، 1 عربي فقط، 2 إنجليزي فقط (لا تبديل). */
    public int getLanguageMode() {
        return prefs.getInt("language_mode", LANGUAGE_MODE_BOTH);
    }

    public void setLanguageMode(int mode) {
        prefs.edit().putInt("language_mode", mode).apply();
    }

    // ===== الكتابة بالصوت =====

    /** زر المايك في صف الاقتراحات (يمين الصف). */
    public boolean isVoiceButtonEnabled() {
        return prefs.getBoolean("voice_button_enabled", true);
    }

    public void setVoiceButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean("voice_button_enabled", enabled).apply();
    }

    // ===== الحافظة =====

    /** إظهار آخر نص/رقم منسوخ في خانة الاقتراح الوسطى لمدة قصيرة (مثل جي بورد). */
    public boolean isClipChipEnabled() {
        return prefs.getBoolean("clip_chip_enabled", true);
    }

    public void setClipChipEnabled(boolean enabled) {
        prefs.edit().putBoolean("clip_chip_enabled", enabled).apply();
    }

    /**
     * مدة بقاء النصوص غير المثبتة ولقطة الشاشة في الحافظة بالساعات (0 = لا حذف تلقائي). الافتراضي 168 (أسبوع).
     * من اختار قبل كذا مدة بالأيام تنتقل قيمته تلقائياً.
     */
    public int getClipRetentionHours() {
        if (prefs.contains("clip_retention_hours")) return prefs.getInt("clip_retention_hours", 168);
        if (prefs.contains("clip_retention_days")) return prefs.getInt("clip_retention_days", 7) * 24;
        return 168;
    }

    public void setClipRetentionHours(int hours) {
        prefs.edit().putInt("clip_retention_hours", hours).apply();
    }

    /** وضع التخفي: لا يتعلم كلمات ولا يحفظ الحافظة. */
    public boolean isIncognitoMode() {
        return prefs.getBoolean("incognito_mode", false);
    }

    public void setIncognitoMode(boolean enabled) {
        prefs.edit().putBoolean("incognito_mode", enabled).apply();
    }

    /** وضع اليد الواحدة: 0 = معطّل، 1 = لليسار، 2 = لليمين. */
    public int getOneHandedMode() {
        return prefs.getInt("one_handed_mode", 0);
    }

    public void setOneHandedMode(int mode) {
        prefs.edit().putInt("one_handed_mode", Math.max(0, Math.min(2, mode))).apply();
    }

    public boolean isSwipeDeleteWordEnabled() {
        return prefs.getBoolean("swipe_delete_word_enabled", true);
    }

    public void setSwipeDeleteWordEnabled(boolean enabled) {
        prefs.edit().putBoolean("swipe_delete_word_enabled", enabled).apply();
    }

    public boolean isSpaceSwipeLangEnabled() {
        return prefs.getBoolean("space_swipe_lang_enabled", true);
    }

    public void setSpaceSwipeLangEnabled(boolean enabled) {
        prefs.edit().putBoolean("space_swipe_lang_enabled", enabled).apply();
    }

    /** الضغط المستمر على زر المسافة ثم السحب يحرّك المؤشر حرفاً حرفاً؛ لا يتعارض مع سحب تبديل اللغة السريع. */
    public boolean isSpaceCursorDragEnabled() {
        return prefs.getBoolean("space_cursor_drag_enabled", true);
    }

    public void setSpaceCursorDragEnabled(boolean enabled) {
        prefs.edit().putBoolean("space_cursor_drag_enabled", enabled).apply();
    }

    /** تصحيح تلقائي صامت: يستبدل الكلمة بأقرب كلمة صحيحة معروفة عند الضغط على مسافة، بدون أي لمس يدوي.
     *  معطّل افتراضياً؛ عند إيقافه يعود السلوك لاقتراح التصحيح اليدوي (تلمسه أو تسحبه لتقبله). */
    public boolean isAutoCorrectEnabled() {
        return prefs.getBoolean("auto_correct_enabled", false);
    }

    public void setAutoCorrectEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_correct_enabled", enabled).apply();
    }

    /** تقسيم الكيبورد لنصفين بفاصل بالمنتصف على الشاشات الكبيرة (الشاشة الداخلية للجوالات القابلة للطي
     *  أو التابلت)، لتسهيل الكتابة بالإبهامين. 0=تلقائي حسب عرض الشاشة، 1=مفعّل دائماً، 2=معطّل دائماً. */
    public int getSplitKeyboardMode() {
        return prefs.getInt("split_keyboard_mode", 0);
    }

    public void setSplitKeyboardMode(int mode) {
        prefs.edit().putInt("split_keyboard_mode", Math.max(0, Math.min(2, mode))).apply();
    }

    public boolean isAutoLearnEnabled() {
        return prefs.getBoolean("auto_learn_enabled", true);
    }

    public void setAutoLearnEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_learn_enabled", enabled).apply();
    }

    public boolean isNextWordPredictionEnabled() {
        return prefs.getBoolean("next_word_prediction_enabled", true);
    }

    public void setNextWordPredictionEnabled(boolean enabled) {
        prefs.edit().putBoolean("next_word_prediction_enabled", enabled).apply();
    }

    public boolean isAutoExpandShortcuts() {
        return prefs.getBoolean("auto_expand_shortcuts", true);
    }

    public void setAutoExpandShortcuts(boolean enabled) {
        prefs.edit().putBoolean("auto_expand_shortcuts", enabled).apply();
    }

    public Map<String, String> getShortcuts() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String raw = prefs.getString("shortcuts_json_v7", "{}");
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k, "");
                if (!k.isEmpty() && !v.isEmpty()) {
                    map.put(k, v);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    public void addShortcut(String shortcutKey, String fullPhrase) {
        if (shortcutKey == null || fullPhrase == null) return;
        String k = shortcutKey.trim();
        String v = fullPhrase.trim();
        if (k.isEmpty() || v.isEmpty()) return;
        Map<String, String> map = getShortcuts();
        map.put(k, v);
        saveShortcutsMap(map);
    }

    public void removeShortcut(String shortcutKey) {
        Map<String, String> map = getShortcuts();
        map.remove(shortcutKey);
        saveShortcutsMap(map);
    }

    public String findShortcutExpansion(String typedWord) {
        if (typedWord == null || typedWord.isEmpty()) return null;
        Map<String, String> map = getShortcuts();
        return map.get(typedWord.trim());
    }

    private void saveShortcutsMap(Map<String, String> map) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : map.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString("shortcuts_json_v7", obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    /**
     * الكلمات الثابتة التي تظهر كاقتراحات افتراضية قبل ما تكتب أي شيء يمكن إزالتها بضغطة مطولة،
     * فتُستبعد نهائياً ويحل محلها كلمة أخرى أكثر استخداماً.
     */
    public java.util.Set<String> getDismissedDefaultWords(boolean arabic) {
        String key = arabic ? "dismissed_defaults_ar" : "dismissed_defaults_en";
        String raw = prefs.getString(key, "");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (!raw.isEmpty()) {
            for (String w : raw.split("\u0001")) {
                if (!w.isEmpty()) out.add(w);
            }
        }
        return out;
    }

    public void dismissDefaultWord(boolean arabic, String word) {
        if (word == null || word.isEmpty()) return;
        java.util.Set<String> cur = getDismissedDefaultWords(arabic);
        cur.add(word);
        String key = arabic ? "dismissed_defaults_ar" : "dismissed_defaults_en";
        prefs.edit().putString(key, String.join("\u0001", cur)).apply();
    }
}
