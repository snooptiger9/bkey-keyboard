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

    public static final long OTP_VALIDITY_MS = 3 * 60 * 1000L;

    public static final int THEME_BLACKBERRY_DARK = 0;
    public static final int THEME_GBOARD_WHITE = 1;
    public static final int THEME_MIDNIGHT_SLATE = 2;
    public static final int THEME_FOREST_GREEN = 3;
    /** Follows Android's day/night: white by day, Matte black at night. */
    public static final int THEME_AUTO = 4;

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

    // Kept only so getEnabledLanguages() can migrate anyone still on the old binary setting.
    private static final int LANGUAGE_MODE_BOTH = 0;
    private static final int LANGUAGE_MODE_ARABIC_ONLY = 1;
    private static final int LANGUAGE_MODE_ENGLISH_ONLY = 2;

    public static final String LANG_AR = "ar";
    public static final String LANG_EN = "en";
    public static final String LANG_ES = "es";
    // Every language the keyboard knows how to type in, in the order they're offered in settings.
    public static final String[] ALL_LANGUAGES = {LANG_AR, LANG_EN, LANG_ES};

    public static final int SWIPE_SOUND_BB_AIR_FLICK = 0;
    public static final int SWIPE_SOUND_VELVET_BRUSH = 1;
    public static final int SWIPE_SOUND_MICRO_THOCK = 2;
    public static final int SWIPE_SOUND_PAPER_SWIPE = 3;
    public static final int SWIPE_SOUND_SOFT_WHOOSH = 4;
    public static final int SWIPE_SOUND_SILENT = 5;

    private static final String PREFS_NAME = "bb_keyboard_settings_v7";
    private final SharedPreferences prefs;

    private Context appCtx;

    public KeyboardPreferences(Context context) {
        this.appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
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

    public boolean isPendingReturnToOtpScreen() {
        return prefs.getBoolean("pending_return_to_otp_screen", false);
    }

    public void setPendingReturnToOtpScreen(boolean value) {
        prefs.edit().putBoolean("pending_return_to_otp_screen", value).apply();
    }

    public boolean wasOtpFullBuildEverInstalled() {
        return prefs.getBoolean("otp_full_build_ever_installed", false);
    }

    public void setOtpFullBuildEverInstalled(boolean value) {
        prefs.edit().putBoolean("otp_full_build_ever_installed", value).apply();
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "getRecentEmojis()", e);
        }
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "recordUsedEmoji()", e);
        }
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "getRecentGifs()", e);
        }
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "saveRecentGifs()", e);
        }
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
        float clamped = Math.max(0.50f, Math.min(1.50f, scale));
        prefs.edit().putFloat("key_font_scale", clamped).apply();
    }

    /** The keyboard size shown as the default (105%) in Settings; Reset in the resize popup returns to it. */
    public static final float DEFAULT_KEYBOARD_SCALE = 1.05f;

    public float getKeyboardScale() {
        return prefs.getFloat("keyboard_scale", DEFAULT_KEYBOARD_SCALE);
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

    /** Words picked via swipe-up on a suggestion (a plain counter, no text is stored). */
    public int getFlickWordCount() {
        return prefs.getInt("stat_flick_words", 0);
    }

    public void incrementFlickWordCount() {
        prefs.edit().putInt("stat_flick_words", prefs.getInt("stat_flick_words", 0) + 1).apply();
    }

    /** Plain numeric counters for the typing stats page (no text is ever stored). */
    public int getStat(String key) {
        return prefs.getInt(key, 0);
    }

    public void addStat(String key, int delta) {
        if (delta <= 0) return;
        long v = (long) prefs.getInt(key, 0) + delta;
        prefs.edit().putInt(key, (int) Math.min(v, 2_000_000_000L)).apply();
    }

    /** One finished word, typed, corrected or picked from the bar, credited to a language. */
    public void recordWord(String langCode) {
        addStat("stat_words_total", 1);
        if (langCode != null) addStat("stat_lang_" + langCode, 1);
    }

    /** The theme the user picked, ignoring "follow system". */
    public int getRawThemeMode() {
        return prefs.getInt("theme_mode", THEME_GBOARD_WHITE);
    }

    public boolean isThemeAuto() {
        return getRawThemeMode() == THEME_AUTO;
    }

    private boolean systemIsNight() {
        try {
            int ui = appCtx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception e) {
            return false;
        }
    }

    /** Effective theme: "Auto" resolves to white (system light) or Matte black (system dark). */
    public int getThemeMode() {
        int raw = getRawThemeMode();
        if (raw != THEME_AUTO) return raw;
        return systemIsNight() ? THEME_BLACKBERRY_DARK : THEME_GBOARD_WHITE;
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

    // The floating ABC/Delete capsule color used to be user-pickable here (stored as
    // "floating_capsule_color"/a fixed-85%-then-28% opacity), but its black default was
    // nearly invisible on the dark themes, so it's now fixed per theme directly in
    // BlackBerryImeService.floatingPillC() instead of read from a preference.

    /** "lite" or "full" -- whichever build was last known to be installed, so MainActivity can
     *  tell when the user has switched builds (bkey sideloaded over Plus or vice versa) and
     *  show a one-time reassurance that their settings/data were kept, not lost. Null the very
     *  first time the app ever runs. */
    public String getLastKnownFlavor() {
        return prefs.getString("last_known_flavor", null);
    }

    public void setLastKnownFlavor(String flavor) {
        prefs.edit().putString("last_known_flavor", flavor).apply();
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

    public static final int SOUND_PROFILE_ORIGINAL_RECORDED = 10;

    public int getSoundProfile() {
        return prefs.getInt("sound_profile", SOUND_PROFILE_ORIGINAL_RECORDED);
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

    public static final int SWIPE_SOUND_ORIGINAL_RECORDED = 10;

    public int getSwipeSoundProfile() {
        return prefs.getInt("swipe_sound_profile", SWIPE_SOUND_ORIGINAL_RECORDED);
    }

    public void setSwipeSoundProfile(int profile) {
        prefs.edit().putInt("swipe_sound_profile", profile).apply();
    }

    public int getSoundVolume() {
        return prefs.getInt("sound_volume", 35);
    }

    public void setSoundVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        prefs.edit().putInt("sound_volume", clamped).apply();
    }

    public int getSwipeSoundVolume() {
        return prefs.getInt("swipe_sound_volume", 35);
    }

    public void setSwipeSoundVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        prefs.edit().putInt("swipe_sound_volume", clamped).apply();
    }

    /** 0 = system default haptic; otherwise a custom buzz length in ms (5..50). */
    public int getVibrationMs() {
        int v = prefs.getInt("vibration_ms", 0);
        return v <= 0 ? 0 : Math.max(5, Math.min(50, v));
    }

    public void setVibrationMs(int ms) {
        prefs.edit().putInt("vibration_ms", ms <= 0 ? 0 : Math.max(5, Math.min(50, ms))).apply();
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

    // Swipe down is a single gesture, so copy/paste and "symbols" can't both own it: turning
    // either one on turns the other one off (the settings screen mirrors this in its switches).
    public boolean isSwipeDownSymbolsEnabled() {
        return prefs.getBoolean("swipe_down_symbols_enabled", false);
    }

    public void setSwipeDownSymbolsEnabled(boolean enabled) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("swipe_down_symbols_enabled", enabled);
        if (enabled) e.putBoolean("swipe_down_copy_paste_enabled", false);
        e.apply();
    }

    public void setSwipeDownCopyPasteEnabled(boolean enabled) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("swipe_down_copy_paste_enabled", enabled);
        if (enabled) e.putBoolean("swipe_down_symbols_enabled", false);
        e.apply();
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "removeRecentEmoji()", e);
        }
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
        // bkey (lite) build never requests photos/storage access, so this
        // stays off there even if the pref was left on from before an update
        // (the setting UI for it is hidden in that build too).
        if (!BuildConfig.OTP_FEATURES_ENABLED) return false;
        return prefs.getBoolean("screenshot_clip_enabled", false);
    }

    public void setScreenshotClipEnabled(boolean enabled) {
        prefs.edit().putBoolean("screenshot_clip_enabled", enabled).apply();
    }

    /**
     * The languages the keyboard can type in, in the order they should be cycled through
     * (tap the language key, or swipe the space bar). Always at least one.
     *
     * Reads the new ordered-list setting once it exists; the very first time, it migrates
     * whatever the old two-language on/off setting had (Arabic+English only -- Spanish was
     * never selectable under that scheme, so it never appears from a migration).
     */
    public List<String> getEnabledLanguages() {
        String stored = prefs.getString("enabled_languages", null);
        if (stored == null) {
            int mode = prefs.getInt("language_mode", LANGUAGE_MODE_BOTH);
            if (mode == LANGUAGE_MODE_ARABIC_ONLY) return Arrays.asList(LANG_AR);
            if (mode == LANGUAGE_MODE_ENGLISH_ONLY) return Arrays.asList(LANG_EN);
            return Arrays.asList(LANG_AR, LANG_EN);
        }
        List<String> out = new ArrayList<>();
        for (String code : stored.split(",")) {
            String c = code.trim();
            if (!c.isEmpty() && !out.contains(c)) out.add(c);
        }
        if (out.isEmpty()) out.add(LANG_AR);
        return out;
    }

    public void setEnabledLanguages(List<String> codes) {
        List<String> clean = new ArrayList<>();
        if (codes != null) {
            for (String c : codes) if (c != null && !clean.contains(c)) clean.add(c);
        }
        if (clean.isEmpty()) clean.add(LANG_AR);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(clean.get(i));
        }
        prefs.edit().putString("enabled_languages", sb.toString()).apply();
    }

    public boolean isVoiceButtonEnabled() {
        return prefs.getBoolean("voice_button_enabled", true);
    }

    public void setVoiceButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean("voice_button_enabled", enabled).apply();
    }

    public boolean isClipChipEnabled() {
        // Needs no special permission (just the system clipboard listener),
        // so unlike screenshot-to-clipboard this works the same in both the
        // bkey (lite) and Plus (full) builds.
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

    /** Dark/light mode for the app's own settings screens (not the keyboard's color theme). */
    public boolean isAppDarkMode() {
        return prefs.getBoolean("app_dark_mode", false);
    }

    public void setAppDarkMode(boolean enabled) {
        prefs.edit().putBoolean("app_dark_mode", enabled).apply();
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

    // Words the user reversed with Backspace right after an auto-correction. They are never
    // auto-corrected again (stored lowercase, on this device only, capped so it can't grow forever).
    private static final String KEY_AUTOCORRECT_EXCEPTIONS = "autocorrect_exceptions";
    private static final int MAX_AUTOCORRECT_EXCEPTIONS = 500;

    public boolean isAutoCorrectException(String word) {
        if (word == null || word.isEmpty()) return false;
        java.util.Set<String> set = prefs.getStringSet(KEY_AUTOCORRECT_EXCEPTIONS, null);
        return set != null && set.contains(word.toLowerCase(java.util.Locale.ROOT));
    }

    public void addAutoCorrectException(String word) {
        if (word == null || word.isEmpty()) return;
        java.util.Set<String> old = prefs.getStringSet(KEY_AUTOCORRECT_EXCEPTIONS, null);
        java.util.Set<String> set = old == null ? new java.util.HashSet<>() : new java.util.HashSet<>(old);
        if (set.size() >= MAX_AUTOCORRECT_EXCEPTIONS) return;
        set.add(word.toLowerCase(java.util.Locale.ROOT));
        prefs.edit().putStringSet(KEY_AUTOCORRECT_EXCEPTIONS, set).apply();
    }

    public void clearAutoCorrectExceptions() {
        prefs.edit().remove(KEY_AUTOCORRECT_EXCEPTIONS).apply();
    }

    // Sub-option of silent auto-correct: false (default) only corrects when the best candidate
    // clearly beats the runner-up; true also corrects when it is a close call.
    public boolean isAutoCorrectStrong() {
        return prefs.getBoolean("auto_correct_strong", false);
    }

    public void setAutoCorrectStrong(boolean strong) {
        prefs.edit().putBoolean("auto_correct_strong", strong).apply();
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
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "getShortcuts()", e);
        }
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
        String typed = typedWord.trim();
        // An exact match always wins (so "Omw" and "omw" can still be two different shortcuts).
        String exact = map.get(typed);
        if (exact != null) return exact;
        // Otherwise ignore the letter case: with auto-capitalization on, "omw" at the start of a
        // sentence is typed as "Omw" and used to miss the shortcut saved as "omw".
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(typed)) {
                String full = e.getValue();
                // Typed with a capital first letter -> the expansion starts with a capital too.
                if (!full.isEmpty() && Character.isUpperCase(typed.charAt(0))) {
                    full = full.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + full.substring(1);
                }
                return full;
            }
        }
        return null;
    }

    private void saveShortcutsMap(Map<String, String> map) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : map.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString("shortcuts_json_v7", obj.toString()).apply();
        } catch (Exception e) {
            AppLogger.log("KeyboardPreferences", "saveShortcutsMap()", e);
        }
    }

    // Keyed by language code (LANG_AR/LANG_EN/LANG_ES) rather than a bare Arabic/not-Arabic
    // boolean -- English and Spanish each need their own dismissed-default-words set, since
    // they're both "not Arabic" but otherwise unrelated languages.
    public java.util.Set<String> getDismissedDefaultWords(String languageCode) {
        String key = "dismissed_defaults_" + languageCode;
        String raw = prefs.getString(key, "");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (!raw.isEmpty()) {
            for (String w : raw.split("\u0001")) {
                if (!w.isEmpty()) out.add(w);
            }
        }
        return out;
    }

    public void dismissDefaultWord(String languageCode, String word) {
        if (word == null || word.isEmpty()) return;
        java.util.Set<String> cur = getDismissedDefaultWords(languageCode);
        cur.add(word);
        String key = "dismissed_defaults_" + languageCode;
        prefs.edit().putString(key, String.join("\u0001", cur)).apply();
    }
}
