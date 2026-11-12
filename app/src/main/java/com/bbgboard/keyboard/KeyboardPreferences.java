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

public class KeyboardPreferences {

    public static final long OTP_VALIDITY_MS = 5 * 60 * 1000L;

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
        removeDefaultShortcutsOnce();
    }

    private void removeDefaultShortcutsOnce() {
        if (prefs.contains("default_shortcuts_removed_v7")) return;
        Map<String, String> oldDefaults = new LinkedHashMap<>();
        oldDefaults.put("سع", "السلام عليكم ورحمة الله وبركاته");
        oldDefaults.put("جخ", "جزاك الله خيراً");
        oldDefaults.put("اشل", "إن شاء الله");
        oldDefaults.put("مشل", "ما شاء الله تبارك الله");
        oldDefaults.put("يعاف", "يعطيك العافية");
        oldDefaults.put("صلعم", "اللهم صل وسلم على نبينا محمد");
        Map<String, String> current = getShortcuts();
        boolean changed = false;
        for (Map.Entry<String, String> e : oldDefaults.entrySet()) {
            String currentVal = current.get(e.getKey());
            if (currentVal != null && currentVal.equals(e.getValue())) {
                current.remove(e.getKey());
                changed = true;
            }
        }
        if (changed) saveShortcutsMap(current);
        prefs.edit().putBoolean("default_shortcuts_removed_v7", true).apply();
    }

    public boolean isKeyPreviewPopupEnabled() {
        return prefs.getBoolean("key_preview_popup_enabled", true);
    }

    public void setKeyPreviewPopupEnabled(boolean enabled) {
        prefs.edit().putBoolean("key_preview_popup_enabled", enabled).apply();
    }

    public boolean isOtpSuggestEnabled() {
        return prefs.getBoolean("otp_suggest_enabled", false);
    }

    public void setOtpSuggestEnabled(boolean enabled) {
        prefs.edit().putBoolean("otp_suggest_enabled", enabled).apply();
    }

    public boolean isWhatsAppOtpSuggestEnabled() {
        return prefs.getBoolean("whatsapp_otp_suggest_enabled", false);
    }

    public void setWhatsAppOtpSuggestEnabled(boolean enabled) {
        prefs.edit().putBoolean("whatsapp_otp_suggest_enabled", enabled).apply();
    }

    public void setPendingWhatsAppOtp(String code) {
        prefs.edit().putString("pending_wa_otp_code", code)
                .putLong("pending_wa_otp_time", System.currentTimeMillis()).apply();
    }

    public String getPendingWhatsAppOtp() {
        String code = prefs.getString("pending_wa_otp_code", null);
        if (code == null) return null;
        long time = prefs.getLong("pending_wa_otp_time", 0L);
        if (System.currentTimeMillis() - time > OTP_VALIDITY_MS) return null;
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

    public List<String[]> getRecentGifs() {
        List<String[]> list = new ArrayList<>();
        try {
            String raw = prefs.getString("recent_gifs_v1", null);
            if (raw != null && !raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String preview = o.optString("p", "");
                    String full = o.optString("f", "");
                    if (!preview.isEmpty() && !full.isEmpty()) list.add(new String[]{preview, full});
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void recordUsedGif(String previewUrl, String fullUrl) {
        if (previewUrl == null || previewUrl.isEmpty() || fullUrl == null || fullUrl.isEmpty()) return;
        List<String[]> current = new ArrayList<>(getRecentGifs());
        for (int i = current.size() - 1; i >= 0; i--) {
            if (fullUrl.equals(current.get(i)[1])) current.remove(i);
        }
        current.add(0, new String[]{previewUrl, fullUrl});
        while (current.size() > 16) {
            current.remove(current.size() - 1);
        }
        saveRecentGifs(current);
    }

    public void removeRecentGif(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) return;
        List<String[]> current = new ArrayList<>(getRecentGifs());
        for (int i = current.size() - 1; i >= 0; i--) {
            if (fullUrl.equals(current.get(i)[1])) current.remove(i);
        }
        saveRecentGifs(current);
    }

    private void saveRecentGifs(List<String[]> list) {
        try {
            JSONArray arr = new JSONArray();
            for (String[] g : list) {
                JSONObject o = new JSONObject();
                o.put("p", g[0]);
                o.put("f", g[1]);
                arr.put(o);
            }
            prefs.edit().putString("recent_gifs_v1", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String getGiphyApiKey() {
        return prefs.getString("giphy_api_key", "");
    }

    public void setGiphyApiKey(String key) {
        prefs.edit().putString("giphy_api_key", key == null ? "" : key.trim()).apply();
    }

    public int getSuggestionTextLevel() {
        return Math.max(0, Math.min(5, prefs.getInt("suggestion_text_level", 3)));
    }

    public void setSuggestionTextLevel(int level) {
        prefs.edit().putInt("suggestion_text_level", Math.max(0, Math.min(5, level))).apply();
    }

    public float getKeyFontScale() {
        return prefs.getFloat("key_font_scale", 1.18f);
    }

    public void setKeyFontScale(float scale) {
        float clamped = Math.max(0.80f, Math.min(1.50f, scale));
        prefs.edit().putFloat("key_font_scale", clamped).apply();
    }

    public float getKeyboardScale() {
        return prefs.getFloat("keyboard_scale", 1.05f);
    }

    public void setKeyboardScale(float scale) {
        float clamped = Math.max(0.70f, Math.min(1.45f, scale));
        prefs.edit().putFloat("keyboard_scale", clamped).apply();
    }

    public float getSuggestionBarScale() {
        return prefs.getFloat("suggestion_bar_scale", 1.15f);
    }

    public void setSuggestionBarScale(float scale) {
        float clamped = Math.max(0.70f, Math.min(1.25f, scale));
        prefs.edit().putFloat("suggestion_bar_scale", clamped).apply();
    }

    public int getThemeMode() {
        return prefs.getInt("theme_mode", THEME_GBOARD_WHITE);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt("theme_mode", mode).apply();
    }

    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public int getKeyboardStyle() {
        int style = prefs.getInt("keyboard_style", STYLE_GBOARD_MATERIAL);
        return style == STYLE_GBOARD_SOFT_ROUND ? STYLE_MODERN_PILL : style;
    }

    public int getEnterColor() {
        int c = prefs.getInt("enter_color", 0);
        return c == 0xFF2563EB ? 0xFF8AB4F8 : c;
    }

    public void setEnterColor(int color) {
        prefs.edit().putInt("enter_color", color).apply();
    }

    public static final int FLOATING_CAPSULE_FOLLOW_LABEL = -1;

    public int getFloatingCapsuleColor() {
        return prefs.getInt("floating_capsule_color", 0x000000);
    }

    public void setFloatingCapsuleColor(int rgb) {
        prefs.edit().putInt("floating_capsule_color", rgb).apply();
    }

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

    public static final int FONT_SYSTEM_DEFAULT = 0;
    public static final int FONT_TAJAWAL = 1;
    public static final int FONT_ALMARAI = 2;
    public static final int FONT_SYSTEM_DEFAULT_BOLD = 3;
    public static final int FONT_TAJAWAL_REGULAR = 4;
    public static final int FONT_ALMARAI_REGULAR = 5;

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

    public int getSwipeDeleteDirection() {
        return prefs.getInt("swipe_delete_direction", 0);
    }

    public void setSwipeDeleteDirection(int dir) {
        prefs.edit().putInt("swipe_delete_direction", dir == 1 ? 1 : 0).apply();
    }

    public boolean isSwipeSelectAllEnabled() {
        return prefs.getBoolean("swipe_select_all_enabled", true);
    }

    public void setSwipeSelectAllEnabled(boolean enabled) {
        prefs.edit().putBoolean("swipe_select_all_enabled", enabled).apply();
    }

    public boolean isSwipeDownCopyPasteEnabled() {
        return prefs.getBoolean("swipe_down_copy_paste_enabled", true);
    }

    public void setSwipeDownCopyPasteEnabled(boolean enabled) {
        prefs.edit().putBoolean("swipe_down_copy_paste_enabled", enabled).apply();
    }

    public boolean isKeyBounceAnimationEnabled() {
        return prefs.getBoolean("key_bounce_anim_enabled", false);
    }

    public void setKeyBounceAnimationEnabled(boolean enabled) {
        prefs.edit().putBoolean("key_bounce_anim_enabled", enabled).apply();
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

    public boolean isArabicIndicDigits() {
        return prefs.getBoolean("arabic_indic_digits", false);
    }

    public void setArabicIndicDigits(boolean enabled) {
        prefs.edit().putBoolean("arabic_indic_digits", enabled).apply();
    }

    public boolean isScreenshotClipEnabled() {
        return prefs.getBoolean("screenshot_clip_enabled", false);
    }

    public void setScreenshotClipEnabled(boolean enabled) {
        prefs.edit().putBoolean("screenshot_clip_enabled", enabled).apply();
    }

    public int getLanguageMode() {
        return prefs.getInt("language_mode", LANGUAGE_MODE_BOTH);
    }

    public void setLanguageMode(int mode) {
        prefs.edit().putInt("language_mode", mode).apply();
    }

    public boolean isVoiceButtonEnabled() {
        return prefs.getBoolean("voice_button_enabled", true);
    }

    public void setVoiceButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean("voice_button_enabled", enabled).apply();
    }

    public boolean isClipChipEnabled() {
        return prefs.getBoolean("clip_chip_enabled", true);
    }

    public void setClipChipEnabled(boolean enabled) {
        prefs.edit().putBoolean("clip_chip_enabled", enabled).apply();
    }

    public int getClipRetentionHours() {
        if (prefs.contains("clip_retention_hours")) return prefs.getInt("clip_retention_hours", 168);
        if (prefs.contains("clip_retention_days")) return prefs.getInt("clip_retention_days", 7) * 24;
        return 168;
    }

    public void setClipRetentionHours(int hours) {
        prefs.edit().putInt("clip_retention_hours", hours).apply();
    }

    public boolean isIncognitoMode() {
        return prefs.getBoolean("incognito_mode", false);
    }

    public void setIncognitoMode(boolean enabled) {
        prefs.edit().putBoolean("incognito_mode", enabled).apply();
    }

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

    public boolean isSpaceCursorDragEnabled() {
        return prefs.getBoolean("space_cursor_drag_enabled", true);
    }

    public void setSpaceCursorDragEnabled(boolean enabled) {
        prefs.edit().putBoolean("space_cursor_drag_enabled", enabled).apply();
    }

    public boolean isAutoCorrectEnabled() {
        return prefs.getBoolean("auto_correct_enabled", false);
    }

    public void setAutoCorrectEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_correct_enabled", enabled).apply();
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
