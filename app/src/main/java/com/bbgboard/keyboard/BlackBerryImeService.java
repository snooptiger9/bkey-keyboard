package com.bbgboard.keyboard;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlackBerryImeService extends InputMethodService implements BlackBerryKeyboardView.KeyboardActionListener {

    private KeyboardPreferences keyboardPrefs;
    private SoundController soundController;
    private PredictionEngine predictionEngine;
    private ClipboardRepository clipboardRepo;
    // True while the settings training field has focus: nothing may be pasted into it.
    private boolean noPasteField = false;
    private ClipboardManager clipboardManager;

    private FrameLayout rootContainer;
    private BlackBerryKeyboardView keyboardView;

    private static final java.util.Set<String> APPEARANCE_PREF_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "theme_mode", "theme_follow_system", "keyboard_style", "key_font", "enter_color",
            "key_font_scale", "keyboard_scale", "suggestion_bar_scale", "suggestion_text_level"));
    private final SharedPreferences.OnSharedPreferenceChangeListener appearanceChangeListener = (sp, key) -> {
        if ("enabled_languages".equals(key) || "hide_emoji_key".equals(key) || "space_swipe_lang_enabled".equals(key)
                || "split_keyboard_mode".equals(key) || "french_layout".equals(key)) {
            // Languages turned on/off or reordered in Settings apply to the open keyboard at once.
            if (keyboardView == null) return;
            keyboardView.post(() -> {
                if (keyboardView == null) return;
                keyboardView.reloadAppearanceFromPrefs();
                keyboardView.requestLayout();
                keyboardView.invalidate();
                if (predictionEngine != null) {
                    predictionEngine.setActiveLatinLanguage(keyboardView.getActiveLanguageCode());
                    refreshPredictions();
                }
            });
            return;
        }
        if (key == null || !APPEARANCE_PREF_KEYS.contains(key)) return;
        if (keyboardView != null) {
            keyboardView.reloadAppearanceFromPrefs();
            keyboardView.invalidate();
            try {
                keyboardView.animate().cancel();
                keyboardView.setAlpha(0.55f);
                keyboardView.animate().alpha(1f).setDuration(220).start();
            } catch (Exception ignored) {}
        }
        rebuildDrawersIfThemeChanged();
        // Repaint the area around the keyboard and the one-hand buttons right away.
        applyNavigationBarColor();
        styleOneHandedButtons();
        if (predictionEngine != null) updateLearningPolicy(getCurrentInputEditorInfo());
    };

    private FrameLayout oneHandedSwitchBtn;
    private ModernIconView oneHandedSwitchIcon;
    private FrameLayout oneHandedRestoreBtn;
    private LinearLayout privacyPill;
    private View oneHandHeaderBtn;
    private View privacyDot;
    private TextView privacyText;
    private android.animation.ObjectAnimator privacyPulse;
    private boolean privateFieldNow = false;
    private LinearLayout clipboardDrawerView;
    private LinearLayout clipsListContainer;
    private TextView clipCountTv;
    private LinearLayout clipHeaderRow;

    private FrameLayout inlineStrip;
    private LinearLayout inlineStripRow;
    private boolean inlineHasSuggestions = false;
    private int inlineGen = 0;
    private LinearLayout emojiHeaderRow;
    private ModernIconView emojiBackBtn;
    private int emojiGridCols = 8;
    private int emojiGridCellW = 0;
    private LinearLayout emojiDrawerView;
    private FrameLayout emojiContentFrameView;
    private ScrollView emojiScroll;
    private LinearLayout emojiSectionsHost;
    private GridLayout emojiRecentGrid;
    private TextView emojiRecentEmptyText;
    private final List<View> emojiSectionHeaders = new ArrayList<>();
    private final List<View> emojiTabViews = new ArrayList<>();
    private int activeEmojiCategory = 0;

    private boolean suppressEmojiTabScrollSync = true;
    private TextView clipAbcBackLabel;
    private TextView emojiAbcBackLabel;

    private TextView gifTabView;
    private FrameLayout gifPanel;
    private TextView gifSearchBox;
    private TextView gifSearchClearBtn;
    private LinearLayout gifGrid;
    private ScrollView gifScroll;
    private TextView gifStatusText;
    private boolean gifPanelShown = false;
    private String gifLastQuery = null;
    // Language code ("ar"/"en"/"es") the GIF panel's text was last rendered for, so switching
    // between any two of the three languages (not just Arabic<->not) re-renders it.
    private String gifLastLoadedLangCode = "";

    private boolean gifSearchFocused = false;
    private final StringBuilder gifQueryBuilder = new StringBuilder();
    private FrameLayout gifSearchBar;
    private TextView gifAttributionLabel;
    private TextView gifKlipyPillLabel;
    private LinearLayout gifAttributionBar;
    private TextView gifQueryDisplay;
    private LinearLayout gifBarRowView;
    private TextView gifCancelBtnView;
    private long lastSpaceTapTime = 0L;
    private boolean numericFieldActive = false;
    private boolean passwordFieldActive = false;

    private static final long CLIP_CHIP_MS = 60 * 1000L;
    private static final long SHOT_CHIP_MS = 60 * 1000L;
    private String clipChipText = null;
    private long clipChipTime = 0L;

    private boolean clipChipSensitive = false;
    private final Runnable clipChipExpiry = this::refreshPredictions;

    // Undo after deleting (optional): text removed by swipe-delete or a held backspace can be
    // brought back from a card in the suggestion bar. Kept in memory only, never in password fields.
    private final StringBuilder undoText = new StringBuilder();
    private boolean undoPending = false;
    private int undoCursor = -1;
    private int undoAnchor = -1;
    private long undoRecordTime = 0L;

    private boolean undoAllowed() {
        return keyboardPrefs != null && keyboardPrefs.isUndoAfterDeleteEnabled() && !passwordFieldActive;
    }

    private int cursorNow(InputConnection ic) {
        try {
            android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
            if (et != null && et.selectionStart >= 0) return et.startOffset + et.selectionStart;
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "cursorNow()", e);
        }
        return -1;
    }

    /** Called right before deleting: cursorBefore is where the deleted text ends. */
    private void recordUndo(String deleted, int cursorBefore) {
        if (!undoAllowed() || deleted == null || deleted.isEmpty() || cursorBefore < 0) return;
        if (undoPending && cursorBefore == undoCursor) {
            undoText.insert(0, deleted);
        } else {
            undoText.setLength(0);
            undoText.append(deleted);
            undoAnchor = cursorBefore;
        }
        undoCursor = cursorBefore - deleted.length();
        undoPending = true;
        undoRecordTime = SystemClock.uptimeMillis();
    }

    private void clearUndo() {
        if (!undoPending) return;
        undoPending = false;
        undoText.setLength(0);
        undoCursor = -1;
        undoAnchor = -1;
        refreshPredictions();
    }

    private boolean hasValidClipChip() {
        return clipChipText != null
                && keyboardPrefs != null && keyboardPrefs.isClipChipEnabled()
                && !keyboardPrefs.isIncognitoMode()
                && !passwordFieldActive
                && (SystemClock.elapsedRealtime() - clipChipTime) < CLIP_CHIP_MS;
    }

    private boolean screenshotChipActive = false;
    private long screenshotChipTime = 0L;

    private boolean hasValidShotChip() {
        return screenshotChipActive
                && keyboardPrefs != null && keyboardPrefs.isScreenshotClipEnabled()
                && keyboardPrefs.isClipChipEnabled()
                && !keyboardPrefs.isIncognitoMode()
                && !passwordFieldActive
                && (SystemClock.elapsedRealtime() - screenshotChipTime) < SHOT_CHIP_MS
                && ScreenshotClip.exists(this);
    }

    /** True only when the whole field is empty (not just the current word). */
    private boolean fieldIsEmpty(InputConnection ic) {
        if (ic == null) return true;
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        CharSequence a = ic.getTextAfterCursor(1, 0);
        return (b == null || b.length() == 0) && (a == null || a.length() == 0);
    }

    // True from the moment something is copied / a screenshot is taken until the next typed
    // character: in that window the chips skip the "field must be empty" rule.
    private boolean chipsFresh = false;

    /** True when the cursor's own line is empty (nothing before or after it on that line). */
    private boolean lineIsEmpty(InputConnection ic) {
        if (ic == null) return true;
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        CharSequence a = ic.getTextAfterCursor(1, 0);
        boolean beforeOk = b == null || b.length() == 0 || b.charAt(0) == '\n';
        boolean afterOk = a == null || a.length() == 0 || a.charAt(0) == '\n';
        return beforeOk && afterOk;
    }

    private boolean chipFieldOk(InputConnection ic) {
        return chipsFresh || lineIsEmpty(ic);
    }

    private void clearClipChip() {
        screenshotChipActive = false;
        clipChipText = null;
        clipChipSensitive = false;
        uiHandler.removeCallbacks(clipChipExpiry);
    }

    private String clipChipDisplay(String full) {
        if (clipChipSensitive) {
            int len = Math.max(4, Math.min(full.trim().length(), 10));
            StringBuilder dots = new StringBuilder(len);
            for (int i = 0; i < len; i++) dots.append('•');
            return dots.toString();
        }
        String flat = full.replaceAll("\\s+", " ").trim();
        return flat.length() > 40 ? flat.substring(0, 40) + "…" : flat;
    }

    private String lastAutoCorrectOriginal = null;
    private String lastAutoCorrectResult = null;

    private String rejectedAutoCorrectWord = null;

    private boolean hasValidPendingOtp() {
        return currentOtpCode() != null;
    }

    /**
     * The code chip shows unless a screenshot or copied-text chip arrived after the code
     * (whichever is newest takes the single chip slot; the older one returns once it's gone).
     */
    private boolean otpChipWins(InputConnection ic) {
        if (!hasValidPendingOtp()) return false;
        if (numericFieldActive || !chipFieldOk(ic)) return true;
        long otpAge = System.currentTimeMillis() - keyboardPrefs.getPendingWhatsAppOtpTime();
        long now = SystemClock.elapsedRealtime();
        if (hasValidShotChip() && now - screenshotChipTime < otpAge) return false;
        if (hasValidClipChip() && now - clipChipTime < otpAge) return false;
        return true;
    }

    private String currentOtpCode() {
        if (keyboardPrefs != null && keyboardPrefs.isIncognitoMode()) return null;
        return keyboardPrefs != null ? keyboardPrefs.getPendingWhatsAppOtp() : null;
    }

    private void clearPendingOtp() {
        if (keyboardPrefs != null) keyboardPrefs.clearPendingWhatsAppOtp();
    }

    private static final String[] EMOJI_CAT_TITLES = new String[]{
        "مستخدم حديثاً",
        "الوجوه والابتسامات",
        "الأيدي والقلوب والأشخاص",
        "الطبيعة والمناسبات والإسلاميات",
        "الحيوانات",
        "الطعام والمشروبات",
        "السفر والأشياء",
        "الرموز والأعلام"
    };

    private static final String[] EMOJI_CAT_TITLES_EN = new String[]{
        "Recently used",
        "Smileys & Emotion",
        "People & Body",
        "Nature, Events & Islamic",
        "Animals",
        "Food & Drink",
        "Travel & Places",
        "Symbols & Flags"
    };

    private static final String[] EMOJI_CAT_TITLES_ES = new String[]{
        "Usados recientemente",
        "Caras y emociones",
        "Personas y cuerpo",
        "Naturaleza, eventos e islámico",
        "Animales",
        "Comida y bebida",
        "Viajes y lugares",
        "Símbolos y banderas"
    };

    private String[] emojiCatTitles() {
        if (keyboardView == null) return EMOJI_CAT_TITLES_EN;
        String code = keyboardView.getActiveLanguageCode();
        if (KeyboardPreferences.LANG_AR.equals(code)) return EMOJI_CAT_TITLES;
        if (KeyboardPreferences.LANG_ES.equals(code)) return EMOJI_CAT_TITLES_ES;
        if (KeyboardPreferences.LANG_HI.equals(code)) return HindiUi.EMOJI_CATEGORIES;
        if (KeyboardPreferences.LANG_UR.equals(code)) return UrduUi.EMOJI_CATEGORIES;
        if (KeyboardPreferences.LANG_FR.equals(code)) return FrenchUi.EMOJI_CATEGORIES;
        return EMOJI_CAT_TITLES_EN;
    }

    // Index 0 (Recently used) keeps the drawn clock icon; the rest went back to plain emoji
    // glyphs (null marks the one index that isn't an emoji tab).
    private static final String[] EMOJI_CAT_TAB_EMOJI = new String[]{
        null, "🙂", "🤍", "🌒", "🐶", "🧋", "🏎️", "⚠️"
    };

    private static final int[] EMOJI_CAT_ICON_TYPES = new int[]{
        ModernIconView.ICON_CLOCK_RECENT,
        ModernIconView.ICON_EMOJI_SMILE,
        ModernIconView.ICON_HEART,
        ModernIconView.ICON_CRESCENT_MOON,
        ModernIconView.ICON_PAW_PRINT,
        ModernIconView.ICON_CUP_DRINK,
        ModernIconView.ICON_CAR,
        ModernIconView.ICON_HASH_SYMBOLS
    };

    private static final String[][] EMOJI_CATEGORIES = new String[][]{
        new String[]{},
        new String[]{
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌",
            "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛",
            "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸",
            "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕",
            "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢",
            "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳",
            "🥵", "🥶", "😱", "😨", "😰", "😥", "😓",
            "🤗", "🤔", "🤭", "🤫", "🫡", "🫠",
            "😶", "😶‍🌫️", "😐", "😑", "😬", "🙄", "😯",
            "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪",
            "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷",
            "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺",
            "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖",
            "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀",
            "😿", "😾", "🙈", "🙉", "🙊"
        },
        new String[]{
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "❤️‍🔥", "❤️‍🩹", "💔", "❣️", "💕", "💞", "💓",
            "💗", "💖", "💘", "💝", "💟", "💌", "💋", "👍",
            "👎", "👊", "✊", "🤛", "🤜", "👏", "🙌", "🫶",
            "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪",
            "🦾", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷",
            "👀", "👁️", "👅", "👄", "👌", "🤌", "🤏", "✌️",
            "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆",
            "🖕", "👇", "☝️", "👋", "🤚", "🖐️", "✋",
            "🖖", "💯", "💥", "💫",
            "💦", "💨", "🕳️", "💬", "💭", "💤", "👶", "🧒",
            "👦", "👧", "🧑", "👨", "👩", "🧓", "👴", "👵",
            "👨‍👩‍👧‍👦", "🧔", "👳", "🧕", "🥷", "🤴", "👸", "🦸",
            "🦹", "🧙", "🧞", "🚶", "🏃", "💃", "🕺"
        },
        new String[]{
            "🌙", "🌛", "🌜", "🌝", "🌞", "🌕", "🌖", "🌗",
            "🌘", "🌑", "🌒", "🌓", "🌔", "🌟", "⭐", "✨",
            "⚡", "🔥", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️",
            "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️",
            "🌪️", "🌫️", "🌈", "☔", "☂️", "💧", "🌊", "🌍",
            "🌎", "🌏", "🪐", "🌠", "🌌", "🕌", "🕋", "📿",
            "🤲", "🕯️", "🪔", "🎉", "🎊", "🎈", "🎁", "🎂",
            "🎀", "🎄", "🎆", "🎇", "🧨", "🏆", "🥇", "🥈",
            "🥉", "🏅", "🎖️", "👑", "💎", "🔔", "🎵", "🎶",
            "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂",
            "🍃", "🌱", "🌲", "🌳", "🪴", "🌸", "🌹", "🥀",
            "🌺", "🌻", "🌼", "🌷", "💐", "🏜️",
            "🏝️", "🏔️", "⛰️", "🌋", "🏕️"
        },
        new String[]{
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸",
            "🐵", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉",
            "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛",
            "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🦟", "🦗",
            "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙",
            "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬",
            "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍",
            "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃",
            "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐",
            "🦌", "🐕", "🐩", "🐈", "🐈‍⬛", "🐓", "🦃", "🦚",
            "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡",
            "🦫", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔", "🐾",
            "🐉", "🐲"
        },
        new String[]{
            "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺",
            "🍻", "🥂", "🍷", "🥛", "🍼", "🍯", "🧊", "🍎",
            "🍏", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
            "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
            "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑",
            "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯",
            "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞",
            "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟",
            "🍕", "🥪", "🥙", "🧆", "🌮", "🌯", "🥗", "🥘",
            "🍲", "🍝", "🍜", "🍛", "🍚", "🍙", "🍘", "🍥",
            "🍣", "🍤", "🍱", "🥟", "🍦", "🍧", "🍨", "🍩",
            "🍪", "🎂", "🍰", "🧁", "🥧", "🍫", "🍬", "🍭",
            "🍮", "🍿", "🥜", "🌰", "🥣", "🍴", "🥄",
            "🔪", "🥢"
        },
        new String[]{
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵",
            "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "🚆",
            "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🚀", "🛸",
            "🚁", "⛵", "🚤", "🛥️", "🚢", "⚓", "⛽", "🚦",
            "🏠", "🏡", "🏢", "🏣", "🏥", "🏦", "🏨", "🏪",
            "🏫", "🏭", "🏰", "🗼", "🗽", "🗺️", "🧭", "📱",
            "📞", "☎️", "💻", "🖥️", "⌨️", "🖨️", "🖱️", "🔋",
            "🔌", "💡", "🔦", "⌚", "⏰", "⏳", "⌛", "📡",
            "📷", "📸", "📹", "🎥", "📺", "📻", "🎙️", "🎧",
            "🎤", "🎮", "🕹️", "🎯", "🎲", "🧩", "⚽", "🏀",
            "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸",
            "🥊", "🥋", "⛳", "🏹", "🎣", "🔑", "🗝️", "🔒",
            "🔓", "🔐", "📌", "📍", "📎", "🖇️", "✏️", "✒️",
            "🖊️", "🖋️", "📝", "📚", "📖", "📒", "📓", "📔",
            "📕", "📗", "📘", "📙", "📂", "📁", "📅", "📆",
            "📈", "📉", "📊", "💰", "💵", "💴", "💶", "💷",
            "💳", "🧾", "🛠️", "🔧", "🔨", "⚙️", "🧰", "🧲",
            "💣", "🧪", "💊", "💉", "🩺", "🧬", "🔬", "🔭",
            "🛒", "🛍️", "🎒", "👜", "👛", "👓", "🕶️", "👕",
            "👖", "👗", "👟", "👠", "🧢", "👒", "💍", "🌂",
            "🧹", "🧺", "🛏️", "🚪", "🪑", "🚿", "🛁", "🧼",
            "🪥"
        },
        new String[]{
            "✅", "❌", "❎", "❓", "❔", "❗", "❕", "‼️",
            "⁉️", "⚠️", "⛔", "🚫", "🚭", "🔞", "📵", "♻️",
            "✔️", "➕", "➖", "➗", "✖️", "💲", "™️", "©️",
            "®️", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫",
            "⚪", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷",
            "⬛", "⬜", "◼️", "◻️", "▶️", "⏸️", "⏹️", "⏺️",
            "⏭️", "⏮️", "⏩", "⏪", "🔀", "🔁", "🔂", "🔄",
            "🔃", "⬆️", "⬇️", "⬅️", "➡️", "↗️", "↘️", "↙️",
            "↖️", "↕️", "↔️", "↩️", "↪️", "🔙", "🔚", "🔛",
            "🔜", "🔝", "☑️", "🔘", "🔳", "🔲", "ℹ️", "Ⓜ️",
            "🆗", "🆒", "🆕", "🆓", "🆙", "🆘", "🔤", "🔠",
            "🔡", "🔢", "#️⃣", "*️⃣", "0️⃣", "1️⃣", "2️⃣", "3️⃣",
            "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟", "☪️",
            "✡️", "☮️", "✝️", "☯️", "♾️", "🔱", "⚜️", "🏁",
            "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🇸🇦", "🇦🇪", "🇰🇼",
            "🇶🇦", "🇧🇭", "🇴🇲", "🇾🇪", "🇯🇴", "🇮🇶", "🇸🇾", "🇱🇧",
            "🇵🇸", "🇪🇬", "🇸🇩", "🇱🇾", "🇹🇳", "🇩🇿", "🇲🇦", "🇲🇷",
            "🇸🇴", "🇹🇷", "🇮🇷", "🇵🇰", "🇮🇳", "🇺🇸", "🇬🇧", "🇫🇷",
            "🇩🇪", "🇪🇸", "🇮🇹", "🇧🇷", "🇦🇷", "🇨🇳", "🇯🇵", "🇰🇷",
            "🇷🇺", "🇨🇦", "🇦🇺", "🇺🇳",
            "🇿🇦", "🇳🇬", "🇰🇪", "🇪🇹", "🇬🇭", "🇸🇳", "🇨🇮", "🇨🇲",
            "🇺🇬", "🇹🇿", "🇿🇼", "🇮🇩", "🇲🇾", "🇸🇬", "🇹🇭", "🇻🇳",
            "🇵🇭", "🇧🇩", "🇱🇰", "🇳🇵", "🇦🇫", "🇰🇿", "🇺🇿", "🇦🇿",
            "🇦🇲", "🇬🇪", "🇳🇱", "🇧🇪", "🇨🇭", "🇦🇹", "🇸🇪", "🇳🇴",
            "🇩🇰", "🇫🇮", "🇵🇱", "🇵🇹", "🇬🇷", "🇮🇪", "🇨🇿", "🇭🇺",
            "🇷🇴", "🇺🇦", "🇲🇽", "🇨🇱", "🇨🇴", "🇵🇪", "🇻🇪", "🇨🇺",
            "🇳🇿"
        }
    };

    private static boolean isSensitiveClip(ClipData clip) {
        try {
            android.content.ClipDescription d = clip.getDescription();
            if (d == null) return false;
            android.os.PersistableBundle extras = d.getExtras();
            if (extras == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                if (extras.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, false)) return true;
            }
            return extras.getBoolean("android.content.extra.IS_SENSITIVE", false);
        } catch (Exception e) {
            return false;
        }
    }

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            if (clipboardManager == null) return;
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0
                    && clip.getDescription() != null
                    && !clip.getDescription().hasMimeType("text/*")
                    && !clip.getDescription().hasMimeType("text/plain")) {
                return;
            }
            boolean sensitive = clip != null && isSensitiveClip(clip);
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence cs = clip.getItemAt(0).coerceToText(BlackBerryImeService.this);
                if (cs != null) {
                    String copiedText = cs.toString().trim();

                    boolean sensitiveLooksLikeCode = sensitive && copiedText.replaceAll("\\s+", "").matches("\\d{4,10}");
                    boolean sensitiveBlocked = sensitive && !sensitiveLooksLikeCode;
                    if (!copiedText.isEmpty() && !keyboardPrefs.isIncognitoMode() && !sensitiveBlocked) {

                        if (!sensitive) {
                            clipboardRepo.addClip(copiedText, false);
                            refreshClipboardDrawerList();
                        }
                        if (keyboardPrefs.isClipChipEnabled()) {
                            screenshotChipActive = false;
                            chipsFresh = true;
                            clipChipText = copiedText;
                            clipChipSensitive = sensitive;
                            clipChipTime = SystemClock.elapsedRealtime();
                            uiHandler.removeCallbacks(clipChipExpiry);
                            uiHandler.postDelayed(clipChipExpiry, CLIP_CHIP_MS + 100L);
                            refreshPredictions();
                            updateInlineStripVisibility();
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        // Glide's default on-disk cache is 250 MB, which GIF previews filled to ~90 MB
        // of app cache. Cap it before Glide's first use; it can only be set once.
        try {
            Glide.init(this, new com.bumptech.glide.GlideBuilder().setDiskCache(
                    new com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory(
                            this, 10L * 1024 * 1024)));
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onCreate() Glide.init", e);
        }
        activeInstance = this;
        lastOrientation = getResources().getConfiguration().orientation;
        keyboardPrefs = new KeyboardPreferences(this);
        keyboardPrefs.autoSetupLanguagesOnFirstRun(this);
        keyboardPrefs.registerChangeListener(appearanceChangeListener);
        soundController = new SoundController(this, keyboardPrefs);

        predictionEngine = new PredictionEngine(this, keyboardPrefs, true);
        predictionEngine.setIdleSave(true);
        predictionEngine.setOnReadyListener(() -> uiHandler.post(this::refreshPredictions));
        clipboardRepo = new ClipboardRepository(this);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(clipListener);
        }
    }

    @Override
    public void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (predictionEngine != null) {
            predictionEngine.flushPendingSaveBlocking();
        }
        unregisterShotObserver();
        if (voiceController != null) voiceController.destroy();
        uiHandler.removeCallbacks(clipChipExpiry);
        predictionExecutor.shutdownNow();
        ioExecutor.shutdown();
        if (soundController != null) soundController.release();
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        if (keyboardPrefs != null) keyboardPrefs.unregisterChangeListener(appearanceChangeListener);
        if (activeInstance == this) activeInstance = null;
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            releaseReclaimableCaches();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        releaseReclaimableCaches();
    }

    private void releaseReclaimableCaches() {
        try {
            Glide.get(this).clearMemory();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "releaseReclaimableCaches()", e);
        }
        if (soundController != null) soundController.releaseSounds();
    }

    private static BlackBerryImeService activeInstance;

    static void notifyWhatsAppOtpDetected() {
        BlackBerryImeService instance = activeInstance;
        if (instance == null) return;
        instance.uiHandler.post(() -> {
            instance.refreshPredictions();
            instance.updateInlineStripVisibility();
        });
        // Bring the system suggestions back once the code has expired.
        instance.uiHandler.postDelayed(instance::updateInlineStripVisibility,
                KeyboardPreferences.OTP_VALIDITY_MS + 500);
    }

    @Override
    public View onCreateInputView() {
        rootContainer = new FrameLayout(this);
        rootContainer.setClipChildren(false);
        rootContainer.setClipToPadding(false);
        rootContainer.setBackgroundColor(Color.parseColor("#111111"));
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        keyboardView = new BlackBerryKeyboardView(this, keyboardPrefs, soundController);
        keyboardView.setKeyboardActionListener(this);

        clipboardDrawerView = buildClipboardAndResizeDrawer();
        emojiDrawerView = buildCategorizedEmojiDrawer();

        rootContainer.addView(keyboardView);
        rootContainer.addView(clipboardDrawerView);
        rootContainer.addView(emojiDrawerView);
        drawersTheme = keyboardPrefs.getThemeMode();

        oneHandedSwitchIcon = new ModernIconView(this, ModernIconView.ICON_FORWARD_ARROW, Color.WHITE, Color.parseColor("#33FFFFFF"));
        oneHandedSwitchIcon.setStyle(0.85f, 2.0f);
        oneHandedSwitchBtn = new FrameLayout(this);
        GradientDrawable oneHandedBtnBg = new GradientDrawable();
        oneHandedBtnBg.setShape(GradientDrawable.OVAL);
        oneHandedBtnBg.setColor(Color.parseColor("#33FFFFFF"));
        oneHandedSwitchBtn.setBackground(oneHandedBtnBg);
        FrameLayout.LayoutParams lpOneHandedIcon = new FrameLayout.LayoutParams(dp(22), dp(22));
        lpOneHandedIcon.gravity = Gravity.CENTER;
        oneHandedSwitchBtn.addView(oneHandedSwitchIcon, lpOneHandedIcon);
        oneHandedSwitchBtn.setVisibility(View.GONE);
        oneHandedSwitchBtn.setOnClickListener(v -> {
            int mode = keyboardPrefs.getOneHandedMode();
            keyboardPrefs.setOneHandedMode(mode == 2 ? 1 : 2);
            applyOneHandedMode();
            soundController.playKeyClick();
        });
        FrameLayout.LayoutParams lpOneHandedBtn = new FrameLayout.LayoutParams(dp(40), dp(40));
        rootContainer.addView(oneHandedSwitchBtn, lpOneHandedBtn);

        ModernIconView oneHandedRestoreIcon = new ModernIconView(this, ModernIconView.ICON_EXPAND_CORNERS, Color.WHITE, Color.parseColor("#33FFFFFF"));
        oneHandedRestoreIcon.setStyle(0.85f, 2.0f);
        oneHandedRestoreBtn = new FrameLayout(this);
        GradientDrawable oneHandedRestoreBg = new GradientDrawable();
        oneHandedRestoreBg.setShape(GradientDrawable.OVAL);
        oneHandedRestoreBg.setColor(Color.parseColor("#33FFFFFF"));
        oneHandedRestoreBtn.setBackground(oneHandedRestoreBg);
        FrameLayout.LayoutParams lpRestoreIcon = new FrameLayout.LayoutParams(dp(22), dp(22));
        lpRestoreIcon.gravity = Gravity.CENTER;
        oneHandedRestoreBtn.addView(oneHandedRestoreIcon, lpRestoreIcon);
        oneHandedRestoreBtn.setVisibility(View.GONE);
        oneHandedRestoreBtn.setOnClickListener(v -> {
            keyboardPrefs.setOneHandedMode(0);
            applyOneHandedMode();
            soundController.playKeyClick();
        });
        rootContainer.addView(oneHandedRestoreBtn, new FrameLayout.LayoutParams(dp(40), dp(40)));

        // Tiny shield badge (icon only) in the very top corner, so it never covers suggestions.
        privacyPill = new LinearLayout(this);
        privacyPill.setOrientation(LinearLayout.HORIZONTAL);
        privacyPill.setGravity(Gravity.CENTER);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.OVAL);
        pillBg.setColor(Color.parseColor("#E60F172A"));
        pillBg.setStroke(dp(1), Color.parseColor("#9922C55E"));
        privacyPill.setBackground(pillBg);
        privacyPill.setElevation(dp(2));
        privacyDot = privacyPill; // the whole badge breathes softly
        ModernIconView privacyIcon = new ModernIconView(this, ModernIconView.ICON_SHIELD_CHECK,
                Color.parseColor("#4ADE80"), Color.TRANSPARENT);
        privacyIcon.setStyle(0.8f, 1.7f);
        privacyPill.addView(privacyIcon, new LinearLayout.LayoutParams(dp(9), dp(9)));
        privacyText = new TextView(this); // kept only for the accessibility label
        FrameLayout.LayoutParams lpPill = new FrameLayout.LayoutParams(dp(14), dp(14));
        lpPill.gravity = Gravity.TOP | Gravity.END;
        lpPill.topMargin = dp(1);
        lpPill.rightMargin = dp(3);
        privacyPill.setVisibility(View.GONE);
        rootContainer.addView(privacyPill, lpPill);

        gifSearchBar = new FrameLayout(this);
        gifSearchBar.setBackgroundColor(Color.TRANSPARENT); // floating: the app shows around the pill
        gifSearchBar.setVisibility(View.GONE);
        LinearLayout gifBarRow = new LinearLayout(this);
        gifBarRow.setOrientation(LinearLayout.HORIZONTAL);
        gifBarRow.setGravity(Gravity.CENTER_VERTICAL);
        gifBarRow.setBackground(gifFloatingPillBg());
        gifBarRowView = gifBarRow;

        gifQueryDisplay = new TextView(this);
        gifQueryDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        gifQueryDisplay.setSingleLine(true);
        gifQueryDisplay.setGravity(Gravity.CENTER_VERTICAL);
        gifQueryDisplay.setPadding(dp(14), 0, dp(14), 0);
        gifQueryDisplay.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        gifBarRow.addView(gifQueryDisplay);

        TextView gifCancelBtn = new TextView(this);
        gifCancelBtn.setText("✕");
        gifCancelBtn.setTextColor(labelC());
        gifCancelBtnView = gifCancelBtn;
        gifCancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        gifCancelBtn.setGravity(Gravity.CENTER);
        gifCancelBtn.setPadding(dp(14), 0, dp(14), 0);
        gifCancelBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            gifQueryBuilder.setLength(0);
            closeGifSearchKeyboard(true);
        });
        gifBarRow.addView(gifCancelBtn);

        FrameLayout.LayoutParams lpBarRow = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lpBarRow.setMargins(dp(10), dp(6), dp(10), dp(6)); // floating pill, like the settings test field.
        gifSearchBar.addView(gifBarRow, lpBarRow);
        FrameLayout.LayoutParams lpGifBar = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lpGifBar.gravity = Gravity.TOP;
        rootContainer.addView(gifSearchBar, lpGifBar);

        showKeyboardMain();
        refreshPredictions();
        applyNavigationBarColor();
        applyOneHandedMode();
        return rootContainer;
    }

    private int drawersTheme = -1;

    private void rebuildDrawersIfThemeChanged() {
        int theme = keyboardPrefs.getThemeMode();
        if (rootContainer == null || clipboardDrawerView == null || emojiDrawerView == null || theme == drawersTheme) return;
        drawersTheme = theme;
        rootContainer.removeView(clipboardDrawerView);
        rootContainer.removeView(emojiDrawerView);
        clipboardDrawerView = buildClipboardAndResizeDrawer();
        emojiDrawerView = buildCategorizedEmojiDrawer();
        rootContainer.addView(clipboardDrawerView);
        rootContainer.addView(emojiDrawerView);
        refreshGifSearchBarColors();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (lastOrientation != -1 && lastOrientation != newConfig.orientation) {
            orientationJustChanged = true;
        }
        lastOrientation = newConfig.orientation;
        // Day/night switch of the system: re-resolve the theme when "follow system" is on.
        try {
            if (keyboardPrefs != null && keyboardPrefs.isThemeAuto() && keyboardView != null) {
                keyboardView.reloadAppearanceFromPrefs();
                keyboardView.invalidate();
                rebuildDrawersIfThemeChanged();
            }
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onConfigurationChanged() follow system theme", e);
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        noPasteField = info != null && "bkey.trainfield".equals(info.privateImeOptions);
        lastLearnedEmail = "";
        clearUndo();

        if (orientationJustChanged) {
            // Just a screen rotation, not a real close/reopen or field switch -- keep
            // whatever layout (symbols vs. alphabet) was already showing.
            orientationJustChanged = false;
        } else if (keyboardView != null) {
            keyboardView.exitSymbolsIfActive();
        }

        if (gifSearchFocused) {
            gifSearchFocused = false;
            if (gifSearchBar != null) gifSearchBar.setVisibility(View.GONE);
            if (keyboardView != null) {
                ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) lp).topMargin = 0;
                    keyboardView.setLayoutParams(lp);
                }
            }
        }
        invalidateTextCache();
        if (info != null) {
            curSelStart = info.initialSelStart;
            curSelEnd = info.initialSelEnd;
        }
        updateLearningPolicy(info);
        try {
            if (keyboardView != null) {
                keyboardView.reloadAppearanceFromPrefs();
                int actionId = info != null ? (info.imeOptions & EditorInfo.IME_MASK_ACTION) : EditorInfo.IME_ACTION_NONE;
                boolean noEnterAction = info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
                keyboardView.setEnterActionIcon(noEnterAction ? EditorInfo.IME_ACTION_NONE : actionId);
            }
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() appearance/enterIcon", e);
        }

        if (soundController != null) soundController.ensureLoaded();

        // Everything below rebuilds or re-colors views every single time the keyboard is shown
        // again (closing a drawer, switching apps, coming back from background) -- this is the
        // path that runs on EVERY reopen, so any uncaught exception here is exactly what leaves
        // the keyboard window stuck on a blank/black surface with nothing in the error log,
        // since onStartInputView() itself wasn't wrapped before. Each step now fails on its own
        // without taking the rest of the reopen sequence down with it.
        try {
            rebuildDrawersIfThemeChanged();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() rebuildDrawersIfThemeChanged()", e);
        }
        try {
            applyNavigationBarColor();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() applyNavigationBarColor()", e);
        }
        try {
            applyOneHandedMode();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() applyOneHandedMode()", e);
        }
        showKeyboardMain();
        try {
            // Reported separately from the resize-corner bug: after closing the keyboard and
            // reopening it in a different app, it has sometimes come back fully black with no
            // keys drawn, until something else forced a fresh frame (the user found that just
            // taking a screenshot "woke" it back up). That points at the same family of issue
            // as the resize corners -- a stale window surface that isn't getting repainted on
            // its own -- just triggered by switching apps/input fields instead of a resize.
            // onStartInputView() is exactly the method that runs every time the keyboard
            // reappears in a (possibly different) app, so nudge a redraw here too.
            forceWindowRepaintAfterResize();
            if (keyboardView != null) keyboardView.invalidate();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() forceWindowRepaintAfterResize()", e);
        }
        try {
            if (!keyboardPrefs.isWhatsAppOtpSuggestEnabled()) {
                keyboardPrefs.clearPendingWhatsAppOtp();
            }
            refreshPredictions();
            registerShotObserverIfNeeded();
            tryCaptureScreenshotClip(false);
            setExtractViewShown(false);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onStartInputView() tail", e);
        }
    }

    private VoiceInputController voiceController;

    private VoiceInputController voice() {
        if (voiceController == null) {
            voiceController = new VoiceInputController(this, new VoiceInputController.Callback() {
                @Override public void onPartial(String text) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.setComposingText(text, 1);
                }
                @Override public void onResult(String text) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(text + " ", 1);
                    refreshPredictions();
                }
                @Override public void onListeningChanged(boolean listening) {
                    if (keyboardView != null) keyboardView.setVoiceListening(listening);
                    if (!listening) {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) ic.finishComposingText();
                    }
                }
                @Override public void onError(String message) {
                    if (message != null) showKbToast(voiceText(message));
                }
            });
        }
        return voiceController;
    }

    @Override
    public void onVoiceInputRequested() {
        if (!VoiceInputController.hasPermission(this)) {

            Intent i = new Intent(this, MicPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "onVoiceInputRequested()", e);
            }
            return;
        }
        if (!VoiceInputController.isAvailable(this)) {
            showKbToast(voiceText("Voice typing isn't available on this device"));
            return;
        }
        String voiceLang = keyboardView != null ? keyboardView.getActiveLanguageCode() : KeyboardPreferences.LANG_EN;
        voice().toggle(KeyboardPreferences.LANG_AR.equals(voiceLang) ? "ar-SA"
                : KeyboardPreferences.LANG_ES.equals(voiceLang) ? "es-ES"
                : KeyboardPreferences.LANG_FR.equals(voiceLang) ? "fr-FR"
                : KeyboardPreferences.LANG_HI.equals(voiceLang) ? "hi-IN"
                : KeyboardPreferences.LANG_UR.equals(voiceLang) ? "ur-PK" : "en-US");
    }

    private final ExecutorService predictionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-predict");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-io");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private volatile int predictionGen = 0;
    private long lastShotCheckTime = 0L;

    private String cachedBefore90 = null;
    private long cachedBeforeTime = 0L;

    // Lets onStartInputView() tell a real close/reopen (or switching fields) apart from
    // just a screen rotation: the symbols/alphabet layout should reset on the former but
    // stay put on the latter, like other keyboards do.
    private int lastOrientation = -1;
    private boolean orientationJustChanged = false;

    private static final long CURSOR_SESSION_MS = 600L;
    private int curSelStart = -1;
    private int curSelEnd = -1;
    private long lastCursorStepTime = 0L;
    private boolean cursorRtl = false;

    private void invalidateTextCache() {
        cachedBefore90 = null;
    }

    private String textBefore90(InputConnection ic) {
        long now = SystemClock.uptimeMillis();
        String c = cachedBefore90;
        if (c != null && now - cachedBeforeTime < 1500L) return c;
        CharSequence cs = ic.getTextBeforeCursor(90, 0);
        String s = cs == null ? "" : cs.toString();
        // Apps like Samsung Notes keep a placeholder char (U+FFFC "OBJ") in the text for each
        // inserted image/GIF; treat it as a plain space (same length) so it is never read as a word.
        if (s.indexOf('\uFFFC') >= 0) s = s.replace('\uFFFC', ' ');
        cachedBefore90 = s;
        cachedBeforeTime = now;
        return s;
    }

    private CharSequence textBefore(InputConnection ic, int n) {
        if (n > 90) return ic.getTextBeforeCursor(n, 0);
        String all = textBefore90(ic);
        return all.length() <= n ? all : all.substring(all.length() - n);
    }

    private final class CacheInvalidatingConnection extends InputConnectionWrapper {
        CacheInvalidatingConnection(InputConnection target) { super(target, true); }
        @Override public boolean commitText(CharSequence text, int newCursorPosition) { invalidateTextCache(); return super.commitText(text, newCursorPosition); }
        @Override public boolean deleteSurroundingText(int before, int after) { invalidateTextCache(); return super.deleteSurroundingText(before, after); }
        @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) { invalidateTextCache(); return super.deleteSurroundingTextInCodePoints(before, after); }
        @Override public boolean setComposingText(CharSequence text, int newCursorPosition) { invalidateTextCache(); return super.setComposingText(text, newCursorPosition); }
        @Override public boolean setComposingRegion(int start, int end) { invalidateTextCache(); return super.setComposingRegion(start, end); }
        @Override public boolean finishComposingText() { invalidateTextCache(); return super.finishComposingText(); }
        @Override public boolean setSelection(int start, int end) { invalidateTextCache(); return super.setSelection(start, end); }
        @Override public boolean sendKeyEvent(KeyEvent event) { invalidateTextCache(); return super.sendKeyEvent(event); }
        @Override public boolean performEditorAction(int editorAction) { invalidateTextCache(); return super.performEditorAction(editorAction); }
        @Override public boolean commitCompletion(android.view.inputmethod.CompletionInfo text) { invalidateTextCache(); return super.commitCompletion(text); }
        @Override public boolean commitCorrection(android.view.inputmethod.CorrectionInfo correctionInfo) { invalidateTextCache(); return super.commitCorrection(correctionInfo); }
        @Override public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) { invalidateTextCache(); return super.commitContent(inputContentInfo, flags, opts); }
    }

    private InputConnection icTarget = null;
    private InputConnection icWrapped = null;

    @Override
    public InputConnection getCurrentInputConnection() {
        InputConnection ic = super.getCurrentInputConnection();
        if (ic == null) {
            icTarget = null;
            icWrapped = null;
            return null;
        }
        if (ic != icTarget) {
            icTarget = ic;
            icWrapped = new CacheInvalidatingConnection(ic);
        }
        return icWrapped;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.R)
    public android.view.inputmethod.InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        try {
            int barH = keyboardView != null ? Math.round(keyboardView.getSuggestionBarHeightPx()) : dp(40);
            if (barH <= 0) barH = dp(40);

            androidx.autofill.inline.v1.InlineSuggestionUi.Style.Builder styleBuilder =
                    androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder();
            if (keyboardView != null) {
                android.graphics.Bitmap chipBg = keyboardView.renderKeyChipBitmap(dp(170), barH);
                androidx.autofill.inline.common.ViewStyle chipStyle =
                        new androidx.autofill.inline.common.ViewStyle.Builder()
                                .setBackground(android.graphics.drawable.Icon.createWithBitmap(chipBg))
                                .setPadding(dp(12), 0, dp(12), 0)
                                .build();
                int label = keyboardView.getKeyLabelColor();
                androidx.autofill.inline.common.TextViewStyle titleStyle =
                        new androidx.autofill.inline.common.TextViewStyle.Builder()
                                .setTextColor(label)
                                .setTextSize(14f)
                                .build();
                androidx.autofill.inline.common.TextViewStyle subStyle =
                        new androidx.autofill.inline.common.TextViewStyle.Builder()
                                .setTextColor((label & 0x00FFFFFF) | 0xB0000000)
                                .setTextSize(11f)
                                .build();
                styleBuilder.setChipStyle(chipStyle)
                        .setSingleIconChipStyle(chipStyle)
                        .setTitleStyle(titleStyle)
                        .setSubtitleStyle(subStyle);
            } else {
                // The keyboard view isn't built yet: send a dark style anyway so the system
                // doesn't fall back to its default white cards.
                int fbBg, fbText;
                switch (keyboardPrefs.getThemeMode()) {
                    case KeyboardPreferences.THEME_GBOARD_WHITE:
                        fbBg = Color.parseColor("#FFFFFF"); fbText = Color.parseColor("#1E293B"); break;
                    case KeyboardPreferences.THEME_MIDNIGHT_SLATE:
                        fbBg = Color.parseColor("#1E293B"); fbText = Color.parseColor("#F8FAFC"); break;
                    case KeyboardPreferences.THEME_FOREST_GREEN:
                        fbBg = Color.parseColor("#1B3126"); fbText = Color.parseColor("#F0FDF4"); break;
                    default:
                        fbBg = Color.parseColor("#262626"); fbText = Color.WHITE;
                }
                androidx.autofill.inline.common.ViewStyle fallbackChip =
                        new androidx.autofill.inline.common.ViewStyle.Builder()
                                .setBackgroundColor(fbBg)
                                .setPadding(dp(12), 0, dp(12), 0)
                                .build();
                androidx.autofill.inline.common.TextViewStyle fallbackTitle =
                        new androidx.autofill.inline.common.TextViewStyle.Builder()
                                .setTextColor(fbText)
                                .setTextSize(14f)
                                .build();
                androidx.autofill.inline.common.TextViewStyle fallbackSub =
                        new androidx.autofill.inline.common.TextViewStyle.Builder()
                                .setTextColor((fbText & 0x00FFFFFF) | 0xB0000000)
                                .setTextSize(11f)
                                .build();
                styleBuilder.setChipStyle(fallbackChip)
                        .setSingleIconChipStyle(fallbackChip)
                        .setTitleStyle(fallbackTitle)
                        .setSubtitleStyle(fallbackSub);
            }
            Bundle styles = androidx.autofill.inline.UiVersions.newStylesBuilder()
                    .addStyle(styleBuilder.build())
                    .build();
            int screenW = getResources().getDisplayMetrics().widthPixels;
            android.util.Size min = new android.util.Size(dp(80), Math.min(barH, dp(30)));
            android.util.Size max = new android.util.Size(screenW, barH);
            android.widget.inline.InlinePresentationSpec spec =
                    new android.widget.inline.InlinePresentationSpec.Builder(min, max).setStyle(styles).build();
            return new android.view.inputmethod.InlineSuggestionsRequest.Builder(
                    java.util.Collections.singletonList(spec)).setMaxSuggestionCount(6).build();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.R)
    public boolean onInlineSuggestionsResponse(android.view.inputmethod.InlineSuggestionsResponse response) {
        if (response == null) return false;
        try {
            List<android.view.inputmethod.InlineSuggestion> list = response.getInlineSuggestions();
            ensureInlineStrip();
            if (inlineStrip == null) return false;
            final int gen = ++inlineGen;
            inlineStripRow.removeAllViews();
            inlineHasSuggestions = false;
            updateInlineStripVisibility();
            if (list == null || list.isEmpty()) return true;
            int barH = keyboardView != null ? Math.round(keyboardView.getSuggestionBarHeightPx()) : dp(40);
            if (barH <= 0) barH = dp(40);
            final android.util.Size size = new android.util.Size(dp(170), barH);
            for (android.view.inputmethod.InlineSuggestion suggestion : list) {
                suggestion.inflate(this, size, getMainExecutor(), view -> {
                    if (view == null || gen != inlineGen || inlineStripRow == null) return;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size.getWidth(), size.getHeight());
                    lp.rightMargin = 0;
                    inlineStripRow.addView(view, lp);
                    inlineHasSuggestions = true;
                    updateInlineStripVisibility();
                });
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void ensureInlineStrip() {
        if (inlineStrip != null || rootContainer == null) return;
        inlineStrip = new FrameLayout(this);
        inlineStrip.setBackgroundColor(Color.parseColor("#161920"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        inlineStripRow = new LinearLayout(this);
        inlineStripRow.setOrientation(LinearLayout.HORIZONTAL);
        inlineStripRow.setGravity(Gravity.CENTER_VERTICAL);
        inlineStripRow.setPadding(dp(2), 0, dp(2), 0);
        hs.addView(inlineStripRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        inlineStrip.addView(hs, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        inlineStrip.setVisibility(View.GONE);
        rootContainer.addView(inlineStrip);
    }

    private void updateInlineStripVisibility() {
        if (inlineStrip == null || keyboardView == null) return;
        boolean show = inlineHasSuggestions && keyboardView.getVisibility() == View.VISIBLE;
        // While a fresh OTP chip is showing, it must not be covered by the system's suggestion.
        if (show && otpChipWins(getCurrentInputConnection()) && fieldIsEmpty(getCurrentInputConnection())) show = false;
        if (show) {
            inlineStrip.setBackgroundColor(keyboardView.getSuggestionBarColor());
            int barH = Math.round(keyboardView.getSuggestionBarHeightPx());
            if (barH <= 0) barH = dp(40);
            ViewGroup.LayoutParams kbLp = keyboardView.getLayoutParams();
            int w = ViewGroup.LayoutParams.MATCH_PARENT;
            int hGravity = Gravity.NO_GRAVITY;
            if (kbLp instanceof FrameLayout.LayoutParams) {
                w = kbLp.width;
                int g = ((FrameLayout.LayoutParams) kbLp).gravity;
                if (g != Gravity.NO_GRAVITY && g != -1) hGravity = g & Gravity.HORIZONTAL_GRAVITY_MASK;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, barH);
            lp.topMargin = Math.round(keyboardView.getSuggestionBarTopPx());
            lp.gravity = Gravity.TOP | hGravity;
            inlineStrip.setLayoutParams(lp);
        }
        inlineStrip.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void clearInlineSuggestions() {
        inlineGen++;
        inlineHasSuggestions = false;
        if (inlineStripRow != null) inlineStripRow.removeAllViews();
        updateInlineStripVisibility();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {

        return false;
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isInputViewShown()) return;
        outInsets.contentTopInsets = outInsets.visibleTopInsets;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        dismissClipMenu();
        clearUndo();
        // An address typed into a field and left by tapping Next/Login or switching app never
        // got a space or Enter, so it was never saved. Save it now if it is correctly written.
        try {
            if (predictionEngine != null && keyboardView != null && !gifSearchFocused) {
                invalidateTextCache();
                String[] ctx = getTypingContext();
                if (!ctx[0].isEmpty() && ctx[0].indexOf('@') > 0) learnEmailIfValid(ctx[0]);
            }
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onFinishInputView() learnEmail", e);
        }
        if (keyboardView != null) {
            keyboardView.dismissFlyingPopup();

            // Defensive: don't let a resize-preview popup survive the keyboard being
            // hidden -- if resize-drag mode were somehow still on when this fires,
            // leaving it open would leak that separate floating window.
            if (keyboardView.isResizeDragMode()) {
                keyboardView.setResizeDragMode(false);
            }

            keyboardView.setEmojiKeySuggestion(null);

            keyboardView.resetShiftState();

            if (!hasValidShotChip()) keyboardView.setScreenshotThumb(null);
        }
        clearInlineSuggestions();
        unregisterShotObserver();
        if (voiceController != null) voiceController.cancel();
        invalidateTextCache();
        if (predictionEngine != null) {
            predictionEngine.flushPendingSave();
        }

        resetEmojiAndGifToFirstTab();

        // Sweeps sent-GIF temp files older than the grace window (and enforces the size cap).
        try { GifDownloadCache.dir(this); } catch (Exception ignored) {}

        try {
            Glide.get(this).clearMemory();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onFinishInputView()", e);
        }
        super.onFinishInputView(finishingInput);
    }

    private void resetEmojiAndGifToFirstTab() {
        activeEmojiCategory = 0;
        gifPanelShown = false;
        gifSearchFocused = false;
        applyNavigationBarColor();
        gifQueryBuilder.setLength(0);
        gifLastQuery = null;
        updateGifSearchBoxDisplay(null);
        suppressEmojiTabScrollSync = true;
        if (gifPanel != null) gifPanel.setVisibility(View.GONE);
        if (emojiContentFrameView != null) emojiContentFrameView.setVisibility(View.VISIBLE);
        if (gifTabView != null) {
            gifTabView.setAlpha(0.60f);
            gifTabView.setBackgroundColor(Color.TRANSPARENT);
        }
        if (gifSearchBar != null) gifSearchBar.setVisibility(View.GONE);
        if (keyboardView != null) {
            ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).topMargin = 0;
                keyboardView.setLayoutParams(lp);
            }
        }
        scrollEmojiToStart();
        if (gifScroll != null) gifScroll.scrollTo(0, 0);
        if (!emojiTabViews.isEmpty()) updateEmojiTabHighlight(firstEmojiTab());
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        invalidateTextCache();
        if (undoPending) {
            boolean collapsed = newSelStart == newSelEnd;
            boolean atUndo = collapsed && newSelStart == undoCursor;
            // While a held backspace is still running, older cursor updates arrive late.
            boolean lateUpdate = collapsed && newSelStart > undoCursor && newSelStart <= undoAnchor
                    && SystemClock.uptimeMillis() - undoRecordTime < 400L;
            if (!atUndo && !lateUpdate) clearUndo();
        }

        if (SystemClock.uptimeMillis() - lastCursorStepTime > CURSOR_SESSION_MS) {
            curSelStart = newSelStart;
            curSelEnd = newSelEnd;
        }
        refreshPredictions();
    }

    /** Rounded, theme-colored pill with a hairline border: same look as the settings test field. */
    private android.graphics.drawable.Drawable gifFloatingPillBg() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(sugBarC());
        d.setCornerRadius(dp(18));
        d.setStroke(Math.max(1, dp(1)), (labelC() & 0x00FFFFFF) | 0x33000000);
        return d;
    }

    private void applyNavigationBarColor() {
        if (keyboardView == null) return;
        try {
            int color = keyboardView.getShellColor();
            // While the floating GIF search field is open, nothing is painted around the pill.
            int bg = gifSearchFocused ? Color.TRANSPARENT : color;
            if (rootContainer != null) rootContainer.setBackgroundColor(bg);
            android.view.Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w == null) return;
            w.setNavigationBarColor(color);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (keyboardView.isLightTheme()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "applyNavigationBarColor()", e);
        }
    }

    // Switching between the keyboard and the clipboard/emoji drawer, or opening/closing the
    // GIF search field, changes how tall the IME window needs to be, which on some devices
    // (seen on Samsung/One UI) can leave a stale, unpainted sliver at the window's rounded
    // corners after the resize settles -- it shows the app behind the keyboard through those
    // corners until something forces a full repaint. A plain invalidate() right after the
    // layout change isn't enough since the stale pixels are at the very edge the view may no
    // longer cover; posting it lets the resize/relayout pass finish first, then nudges the
    // whole window (decor view, not just our content) to redraw so the system corner mask gets
    // repainted over those pixels instead of leaving whatever was there before.
    private void forceWindowRepaintAfterResize() {
        try {
            android.view.Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w == null) return;
            View decor = w.getDecorView();
            decor.post(decor::invalidate);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "forceWindowRepaintAfterResize()", e);
        }
    }

    // onStartInputView() runs BEFORE the keyboard window is actually on screen, so any
    // repaint requested only from there can be consumed by a window that isn't visible
    // yet. When the first visible frame is lost (seen after switching apps, locking and
    // unlocking, or reopening in another app) the keyboard sits there as a blank/black
    // box with no keys until something else forces a frame -- a screenshot, a theme
    // change, or closing and reopening. onWindowShown() fires once the window really
    // is visible, so the repaint belongs here, aimed at the keyboard view itself and
    // not only the decor view, and repeated shortly after to catch a slow first frame.
    @Override
    public void onWindowShown() {
        super.onWindowShown();
        try {
            repaintKeyboardSurface();
            if (rootContainer != null) {
                rootContainer.postDelayed(this::repaintKeyboardSurface, 120);
                rootContainer.postDelayed(this::repaintKeyboardSurface, 450);
            }
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onWindowShown()", e);
        }
    }

    private void repaintKeyboardSurface() {
        try {
            if (keyboardView != null) {
                // A cancelled/interrupted fade can leave the view partly or fully
                // transparent, which looks exactly like an empty box.
                if (keyboardView.getVisibility() == View.VISIBLE
                        && keyboardView.getAlpha() < 1f && !drawerOrGifShowing()) {
                    keyboardView.animate().cancel();
                    keyboardView.setAlpha(1f);
                    keyboardView.setTranslationY(0f);
                }
                keyboardView.requestLayout();
                keyboardView.invalidate();
            }
            if (rootContainer != null) {
                rootContainer.requestLayout();
                rootContainer.invalidate();
            }
            forceWindowRepaintAfterResize();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "repaintKeyboardSurface()", e);
        }
    }

    private boolean drawerOrGifShowing() {
        return (clipboardDrawerView != null && clipboardDrawerView.getVisibility() == View.VISIBLE)
                || (emojiDrawerView != null && emojiDrawerView.getVisibility() == View.VISIBLE);
    }

    private void updateLearningPolicy(EditorInfo info) {
        boolean allowed = true;
        boolean passwordField = false;
        boolean numericOnly = false;
        if (info != null) {
            int type = info.inputType;
            int cls = type & android.text.InputType.TYPE_MASK_CLASS;
            int var = type & android.text.InputType.TYPE_MASK_VARIATION;
            if (cls == android.text.InputType.TYPE_CLASS_TEXT) {
                if (var == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_URI) {
                    allowed = false;
                }
                passwordField = var == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            } else if (cls == android.text.InputType.TYPE_CLASS_NUMBER) {
                if (var == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    allowed = false;
                }
                numericOnly = true;
            } else if (cls == android.text.InputType.TYPE_CLASS_PHONE
                    || cls == android.text.InputType.TYPE_CLASS_DATETIME) {
                numericOnly = true;
            }
            if ((info.imeOptions & 0x1000000) != 0) {
                allowed = false;
            }
        }
        privateFieldNow = !allowed;
        boolean emailField = false;
        if (info != null && (info.inputType & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT) {
            int v = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
            emailField = v == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        }
        autoSpaceBlockedField = !allowed || emailField || numericOnly;
        if (keyboardPrefs.isIncognitoMode()) allowed = false;
        predictionEngine.setLearningAllowed(allowed);
        updatePrivacyIndicator();
        numericFieldActive = numericOnly;
        passwordFieldActive = passwordField;
        if (keyboardView != null) {
            keyboardView.setPasswordFieldMode(passwordField);
            keyboardView.setNumericFieldMode(numericOnly);
        }
    }

    private void applyOneHandedMode() {
        if (keyboardView == null) return;
        int mode = keyboardPrefs.getOneHandedMode();
        FrameLayout.LayoutParams lp;
        if (mode == 0) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
            lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = (mode == 1) ? Gravity.LEFT : Gravity.RIGHT;
        }
        keyboardView.setLayoutParams(lp);
        // The emoji and clipboard panels follow the keyboard sideways.
        matchDrawerHeightToKeyboard(clipboardDrawerView);
        matchDrawerHeightToKeyboard(emojiDrawerView);
        // The "one-hand" shortcut in the clipboard header is pointless once it is already on.
        if (oneHandHeaderBtn != null) oneHandHeaderBtn.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        updateOneHandedSwitchButton();
    }

    private void updatePrivacyIndicator() {
        if (privacyPill == null) return;
        boolean incognito = keyboardPrefs.isIncognitoMode();
        boolean show = incognito;
        if (!show) {
            if (privacyPulse != null) { privacyPulse.cancel(); privacyPulse = null; }
            privacyPill.setVisibility(View.GONE);
            return;
        }
        boolean ar = "ar".equals(java.util.Locale.getDefault().getLanguage());
        privacyPill.setContentDescription(incognito ? (ar ? "وضع الخصوصية" : "Incognito") : (ar ? "حقل محمي" : "Protected field"));
        if (privacyPill.getVisibility() != View.VISIBLE) {
            privacyPill.setAlpha(0f);
            privacyPill.setVisibility(View.VISIBLE);
            privacyPill.animate().alpha(1f).setDuration(220).start();
        }
        if (privacyPulse == null) {
            privacyPulse = android.animation.ObjectAnimator.ofFloat(privacyDot, "alpha", 1f, 0.45f);
            privacyPulse.setDuration(1100);
            privacyPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            privacyPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            privacyPulse.start();
        }
    }

    /** Keeps the two side buttons readable on any theme: dark icon on light shells, white on dark. */
    private void styleOneHandedButtons() {
        int shell = shellC();
        double lum = (0.299 * Color.red(shell) + 0.587 * Color.green(shell) + 0.114 * Color.blue(shell)) / 255.0;
        int ink = lum > 0.55 ? Color.parseColor("#374151") : Color.WHITE;
        int fill = (ink & 0x00FFFFFF) | 0x26000000;
        int ring = (ink & 0x00FFFFFF) | 0x80000000;
        FrameLayout[] btns = {oneHandedSwitchBtn, oneHandedRestoreBtn};
        for (FrameLayout b : btns) {
            if (b == null) continue;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(fill);
            bg.setStroke(dp(1), ring);
            b.setBackground(bg);
            View icon = b.getChildAt(0);
            if (icon instanceof ModernIconView) ((ModernIconView) icon).setColors(ink, Color.TRANSPARENT);
        }
    }

    private void updateOneHandedSwitchButton() {
        if (oneHandedSwitchBtn == null) return;
        styleOneHandedButtons();
        int mode = keyboardPrefs.getOneHandedMode();
        boolean keyboardShown = keyboardView != null && keyboardView.getVisibility() == View.VISIBLE;
        if (mode == 0 || !keyboardShown) {
            oneHandedSwitchBtn.setVisibility(View.GONE);
            if (oneHandedRestoreBtn != null) oneHandedRestoreBtn.setVisibility(View.GONE);
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) oneHandedSwitchBtn.getLayoutParams();
        lp.gravity = Gravity.CENTER_VERTICAL | (mode == 1 ? Gravity.RIGHT : Gravity.LEFT);
        lp.rightMargin = mode == 1 ? dp(10) : 0;
        lp.leftMargin = mode == 1 ? 0 : dp(10);
        oneHandedSwitchBtn.setLayoutParams(lp);
        oneHandedSwitchIcon.setIconType(mode == 1 ? ModernIconView.ICON_FORWARD_ARROW : ModernIconView.ICON_BACK_ARROW);
        oneHandedSwitchBtn.setVisibility(View.VISIBLE);
        if (oneHandedRestoreBtn != null) {
            FrameLayout.LayoutParams lpR = new FrameLayout.LayoutParams(dp(40), dp(40));
            lpR.gravity = lp.gravity;
            lpR.rightMargin = lp.rightMargin;
            lpR.leftMargin = lp.leftMargin;
            oneHandedRestoreBtn.setLayoutParams(lpR);
            oneHandedRestoreBtn.setTranslationY(-dp(48));
            oneHandedRestoreBtn.setVisibility(View.VISIBLE);
        }
    }

    private void matchDrawerHeightToKeyboard(View drawer) {
        if (keyboardView == null || drawer == null) return;
        ViewGroup.LayoutParams kbLp = keyboardView.getLayoutParams();
        ViewGroup.LayoutParams dLp = drawer.getLayoutParams();
        if (kbLp instanceof FrameLayout.LayoutParams && dLp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams k = (FrameLayout.LayoutParams) kbLp;
            FrameLayout.LayoutParams d = (FrameLayout.LayoutParams) dLp;
            if (d.width != k.width || d.gravity != k.gravity) {
                d.width = k.width;
                d.gravity = k.gravity;
                drawer.setLayoutParams(d);
            }
        }
        int h = keyboardView.getHeight();
        if (h <= 0) h = keyboardView.getMeasuredHeight();
        if (h <= 0) return;
        ViewGroup.LayoutParams lp = drawer.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            drawer.setLayoutParams(lp);
        }
    }

    private void fadeIn(View v, int slideDp) {
        fadeIn(v, slideDp, 180);
    }

    private void fadeIn(View v, int slideDp, int durationMs) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dp(slideDp));
        v.animate().alpha(1f).translationY(0f).setDuration(durationMs)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void showKeyboardMain() {
        if (keyboardView == null) return;
        try {
            dismissClipMenu();
            if (gifSearchFocused) closeGifSearchKeyboard(false);
            if (gifPanelShown) hideGifPanel();
            boolean wasDrawer = clipboardDrawerView.getVisibility() == View.VISIBLE || emojiDrawerView.getVisibility() == View.VISIBLE;
            keyboardView.setVisibility(View.VISIBLE);
            clipboardDrawerView.setVisibility(View.GONE);
            emojiDrawerView.setVisibility(View.GONE);
            if (wasDrawer) fadeIn(keyboardView, 0);
            updateInlineStripVisibility();
            updateOneHandedSwitchButton();
            if (wasDrawer) forceWindowRepaintAfterResize();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "showKeyboardMain()", e);
            // Whatever failed above, never leave the keyboard surface blank/black for the user --
            // force the main keyboard visible as a last resort so there's always something drawn.
            try {
                keyboardView.setVisibility(View.VISIBLE);
                clipboardDrawerView.setVisibility(View.GONE);
                emojiDrawerView.setVisibility(View.GONE);
            } catch (Exception ignored) {
            }
        }
    }

    private void openAppSettingsActivity() {
        try {
            showKeyboardMain();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "openAppSettingsActivity()", e);
        }
    }

    @Override
    public void onToggleClipboardMenu() {
        try {
            dismissClipMenu();
            if (gifSearchFocused) closeGifSearchKeyboard(false);
            if (gifPanelShown) hideGifPanel();

            updateAbcBackLabels(keyboardView == null || keyboardView.isArabic());
            refreshClipboardDrawerList();
            matchDrawerHeightToKeyboard(clipboardDrawerView);
            refreshBottomBarGeometry();
            keyboardView.setVisibility(View.GONE);
            emojiDrawerView.setVisibility(View.GONE);
            clipboardDrawerView.setVisibility(View.VISIBLE);
            fadeIn(clipboardDrawerView, 10, 260);
            updateInlineStripVisibility();
            updateOneHandedSwitchButton();
            forceWindowRepaintAfterResize();
            tryCaptureScreenshotClip(true);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onToggleClipboardMenu()", e);
            // Don't leave the keyboard stuck mid-transition (blank/black) -- fall back to the
            // normal keyboard view, which is now its own crash-safe path too.
            showKeyboardMain();
        }
    }

    private boolean shotObserverRegistered = false;
    private final Runnable shotObserverRunnable = () -> tryCaptureScreenshotClip(true);
    private final android.database.ContentObserver shotObserver =
            new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {

                    uiHandler.removeCallbacks(shotObserverRunnable);
                    uiHandler.postDelayed(shotObserverRunnable, 700L);
                }
            };

    private void registerShotObserverIfNeeded() {
        if (shotObserverRegistered) return;
        if (!keyboardPrefs.isScreenshotClipEnabled() || !ScreenshotClip.hasPermission(this)) return;
        try {
            getContentResolver().registerContentObserver(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, shotObserver);
            shotObserverRegistered = true;
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "registerShotObserverIfNeeded()", e);
        }
    }

    private void unregisterShotObserver() {
        uiHandler.removeCallbacks(shotObserverRunnable);
        if (!shotObserverRegistered) return;
        try {
            getContentResolver().unregisterContentObserver(shotObserver);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "unregisterShotObserver()", e);
        }
        shotObserverRegistered = false;
    }

    private void tryCaptureScreenshotClip(boolean force) {
        if (!keyboardPrefs.isScreenshotClipEnabled()) return;
        if (!ScreenshotClip.hasPermission(this)) return;

        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastShotCheckTime < 3000L) return;
        lastShotCheckTime = now;
        final android.content.Context appCtx = getApplicationContext();
        try {
            ioExecutor.execute(() -> {
                boolean changed = ScreenshotClip.captureLatest(appCtx);
                final android.graphics.Bitmap thumb = changed ? ScreenshotClip.loadSmallThumb(appCtx, 160) : null;
                if (changed) {
                    uiHandler.post(() -> {
                        if (keyboardView != null) keyboardView.setScreenshotThumb(thumb);
                        if (keyboardPrefs.isClipChipEnabled()) {
                            clipChipText = null;
                            screenshotChipActive = true;
                            chipsFresh = true;
                            screenshotChipTime = SystemClock.elapsedRealtime();
                            uiHandler.removeCallbacks(clipChipExpiry);
                            uiHandler.postDelayed(clipChipExpiry, SHOT_CHIP_MS + 100L);
                            refreshPredictions();
                            updateInlineStripVisibility();
                        }
                        if (clipboardDrawerView != null && clipboardDrawerView.getVisibility() == View.VISIBLE) {
                            refreshClipboardDrawerList();
                        }
                    });
                }
            });
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "tryCaptureScreenshotClip()", e);
        }
    }

    private View kbToastView;
    private final Runnable kbToastHide = () -> {
        View v = kbToastView;
        if (v == null) return;
        kbToastView = null;
        v.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (rootContainer != null) rootContainer.removeView(v);
        }).start();
    };

    /** Small themed message shown inside the keyboard window (a system toast can end up hidden behind it). */
    private void showKbToast(CharSequence msg) {
        if (rootContainer == null) return;
        uiHandler.removeCallbacks(kbToastHide);
        if (kbToastView != null) { rootContainer.removeView(kbToastView); kbToastView = null; }
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(3);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2111827"));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.parseColor("#4D60A5FA"));
        tv.setBackground(bg);
        tv.setElevation(dp(10));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        rootContainer.addView(tv, lp);
        kbToastView = tv;
        tv.setAlpha(0f);
        tv.animate().alpha(1f).setDuration(180).start();
        uiHandler.postDelayed(kbToastHide, 2200);
    }

    /** True when the focused field accepts GIFs (image/gif or image/*). Unknown (old Android or no
     *  editor info) counts as yes so nothing that worked before is blocked. */
    private boolean editorAcceptsGif() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null || Build.VERSION.SDK_INT < 25) return true;
        if (info.contentMimeTypes == null) return false;
        for (String mime : info.contentMimeTypes) {
            if (mime != null && (mime.equalsIgnoreCase("image/gif") || mime.equalsIgnoreCase("image/*"))) return true;
        }
        return false;
    }

    private boolean editorAcceptsScreenshot() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null || Build.VERSION.SDK_INT < 25) return true;
        if (info.contentMimeTypes == null) return false;
        for (String mime : info.contentMimeTypes) {
            if (mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/png")
                    || mime.equalsIgnoreCase("image/*"))) return true;
        }
        return false;
    }

    private void useScreenshotClip() {
        if (!ScreenshotClip.exists(this)) return;
        if (!editorAcceptsScreenshot()) {
            showKbToast(uiText("هذا التطبيق لا يتيح إرسال لقطة شاشة هنا",
                    "This app doesn't allow sending screenshots here",
                    "Esta aplicación no permite enviar capturas aquí"));
            return;
        }
        Uri uri = ScreenshotClip.contentUri(this);
        boolean committedInline = false;
        EditorInfo info = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && info != null && info.contentMimeTypes != null && Build.VERSION.SDK_INT >= 25) {
            for (String mime : info.contentMimeTypes) {
                if (mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/*"))) {
                    try {
                        grantUriPermission(info.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        InputContentInfo ici = new InputContentInfo(uri,
                                new ClipDescription("Screenshot", new String[]{"image/jpeg"}), null);
                        committedInline = ic.commitContent(ici, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                    } catch (Exception e) {
                        AppLogger.log("BlackBerryImeService", "useScreenshotClip()", e);
                    }
                    break;
                }
            }
        }
        if (!committedInline) {
            try {
                ClipData cd = ClipData.newUri(getContentResolver(), "Screenshot", uri);
                if (clipboardManager != null) clipboardManager.setPrimaryClip(cd);
                showKbToast(uiText("تم نسخ لقطة الشاشة — اضغط لصق هنا",
                        "Screenshot copied — paste it here",
                        "Captura copiada — toca para pegarla aquí"));
            } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "useScreenshotClip()", e);
            }
        }
        soundController.playKeyClick();
        showKeyboardMain();
    }

    /** Just the screenshot preview itself — no title/subtitle text, sized by whatever slot the caller gives it. */
    private View buildScreenshotThumbRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        GradientDrawable cardBg = roundedBg(keyBgC(), 14);
        cardBg.setStroke(dp(1), keyDividerC());
        row.setBackground(cardBg);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        ImageView thumb = new ImageView(this);
        // Taller than wide, like the portrait screenshot it's previewing — not a flat landscape strip.
        thumb.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(roundedBg(shellC(), 12));
        thumb.setClipToOutline(true);
        try {
            Bitmap bmp = BitmapFactory.decodeFile(ScreenshotClip.file(this).getAbsolutePath());
            if (bmp != null) thumb.setImageBitmap(bmp);
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "buildScreenshotThumbRow()", e);
        }

        row.setOnClickListener(v -> useScreenshotClip());
        row.setOnLongClickListener(v -> {
            showThemedMenu(v, buildMenuItemView(ModernIconView.ICON_TRASH, uiText("حذف", "Delete", "Eliminar"), () -> {
                ScreenshotClip.clear(this);
                clearClipChip();
                refreshClipboardDrawerList();
                refreshPredictions();
            }));
            return true;
        });
        row.addView(thumb);
        return row;
    }

    @Override
    public void onToggleEmoji() {
        dismissClipMenu();
        if (gifSearchFocused) closeGifSearchKeyboard(false);
        refreshBottomBarGeometry();

        suppressEmojiTabScrollSync = true;
        rebuildEmojiSections();
        updateAbcBackLabels(keyboardView == null || keyboardView.isArabic());
        scrollEmojiToStart();
        matchDrawerHeightToKeyboard(emojiDrawerView);
        keyboardView.setVisibility(View.GONE);
        clipboardDrawerView.setVisibility(View.GONE);
        if (gifTabView != null) gifTabView.setVisibility(keyboardPrefs.isGifTabEnabled() ? View.VISIBLE : View.GONE);
        emojiDrawerView.setVisibility(View.VISIBLE);
        fadeIn(emojiDrawerView, 10, 260);
        updateInlineStripVisibility();
        updateOneHandedSwitchButton();
        forceWindowRepaintAfterResize();
    }

    @Override
    public void onLanguageChanged(boolean isArabic) {
        if (keyboardView != null) predictionEngine.setActiveLatinLanguage(keyboardView.getActiveLanguageCode());
        refreshPredictions();

        if (emojiDrawerView != null && emojiDrawerView.getVisibility() == View.VISIBLE) {
            rebuildEmojiSections();
        }
        updateAbcBackLabels(isArabic);

        updateGifSearchBoxDisplay(gifLastQuery);
        updateGifAttributionBar();
        if (gifSearchFocused) {
            updateGifQueryDisplay();
        } else if (gifPanelShown && gifLastQuery != null) {
            loadGifResults(gifLastQuery.isEmpty(), gifLastQuery);
        }
    }

    /** Picks text for the keyboard's current active language (ar/en/es) instead of just
     *  Arabic-vs-not: the clipboard/emoji/GIF drawers used to fall back to English for anything
     *  that wasn't Arabic, so Spanish mode silently showed English text in these drawers. Layout
     *  direction/alignment is untouched by this -- Spanish still follows English there, same as
     *  everywhere else in the keyboard. */
    private String uiText(String ar, String en, String es) {
        if (keyboardView == null) return en;
        String code = keyboardView.getActiveLanguageCode();
        if (KeyboardPreferences.LANG_AR.equals(code)) return ar;
        if (KeyboardPreferences.LANG_ES.equals(code)) return es;
        if (KeyboardPreferences.LANG_HI.equals(code)) return HindiUi.get(en);
        if (KeyboardPreferences.LANG_UR.equals(code)) return UrduUi.get(en);
        if (KeyboardPreferences.LANG_FR.equals(code)) return FrenchUi.get(en);
        return en;
    }

    /** Voice typing messages (English key) in the keyboard's active language. */
    private String voiceText(String en) {
        switch (en) {
            case "Microphone permission is needed":
                return uiText("إذن الميكروفون مطلوب", en, "Se necesita permiso del micrófono");
            case "No internet connection for voice typing":
                return uiText("لا يوجد اتصال بالإنترنت للكتابة بالصوت", en, "No hay conexión a internet para el dictado por voz");
            case "Voice typing failed":
                return uiText("تعذّرت الكتابة بالصوت", en, "No se pudo usar el dictado por voz");
            case "Voice typing isn't available on this device":
                return uiText("الكتابة بالصوت غير متوفرة على هذا الجهاز", en, "El dictado por voz no está disponible en este dispositivo");
            default:
                return en;
        }
    }

    private String lettersKeyLabel(boolean isArabic) {
        if (isArabic) return "أبجد";
        if (keyboardView != null && keyboardView.isUrdu()) return "ابپ";
        return keyboardView != null && keyboardView.isHindi() ? "अआइ" : "ABC";
    }

    private void updateAbcBackLabels(boolean isArabic) {
        String label = lettersKeyLabel(isArabic);
        if (clipAbcBackLabel != null) clipAbcBackLabel.setText(label);
        if (emojiAbcBackLabel != null) emojiAbcBackLabel.setText(label);
    }

    @Override
    public void onCursorStep(boolean moveRight) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        long now = SystemClock.uptimeMillis();
        boolean newSession = (now - lastCursorStepTime) > CURSOR_SESSION_MS;
        lastCursorStepTime = now;

        if (newSession) {

            cursorRtl = isRtlAroundCursor(textBefore(ic, 2), ic.getTextAfterCursor(2, 0));
            if (curSelStart < 0 || curSelEnd < 0) {

                android.view.inputmethod.ExtractedTextRequest req = new android.view.inputmethod.ExtractedTextRequest();
                android.view.inputmethod.ExtractedText et = ic.getExtractedText(req, 0);
                if (et == null) return;
                curSelStart = et.startOffset + et.selectionStart;
                curSelEnd = et.startOffset + et.selectionEnd;
            }
        }

        boolean forward = cursorRtl ? !moveRight : moveRight;

        CharSequence neighbor = forward ? ic.getTextAfterCursor(2, 0) : ic.getTextBeforeCursor(2, 0);
        if (neighbor == null || neighbor.length() == 0) return;
        int step = 1;
        if (forward && neighbor.length() >= 2 && Character.isHighSurrogate(neighbor.charAt(0))
                && Character.isLowSurrogate(neighbor.charAt(1))) {
            step = 2;
        } else if (!forward && neighbor.length() >= 2
                && Character.isLowSurrogate(neighbor.charAt(neighbor.length() - 1))
                && Character.isHighSurrogate(neighbor.charAt(neighbor.length() - 2))) {
            step = 2;
        }
        int base = forward ? curSelEnd : curSelStart;
        int target = forward ? base + step : base - step;
        if (target < 0) target = 0;
        ic.setSelection(target, target);
        curSelStart = target;
        curSelEnd = target;
    }

    private boolean isRtlAroundCursor(CharSequence before, CharSequence after) {
        if (before != null) {
            for (int i = before.length() - 1; i >= 0; i--) {
                byte d = Character.getDirectionality(before.charAt(i));
                if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
                if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
            }
        }
        if (after != null) {
            for (int i = 0; i < after.length(); i++) {
                byte d = Character.getDirectionality(after.charAt(i));
                if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
                if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
            }
        }
        return keyboardView != null && keyboardView.isRtlScript();
    }

    @Override
    public void onEmojiSuggestionAccepted(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || emoji == null) return;
        clearUndo();

        CharSequence beforeCs = textBefore(ic, 1);
        String before = beforeCs != null ? beforeCs.toString() : "";
        String prefixSpace = (!before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1))) ? " " : "";
        ic.commitText(prefixSpace + emoji + " ", 1);

        refreshPredictions();
    }

    // Last email saved through learnEmailIfValid(), so the same address isn't counted twice
    // when e.g. Enter and then leaving the field both see it.
    private String lastLearnedEmail = "";

    /** Saves an address to the learned words, but only if it is a correctly written email
     *  (valid shape, real-looking domain, not a known typo like "gmial.com"). Anything else is ignored. */
    private void learnEmailIfValid(String token) {
        if (token == null || predictionEngine == null || keyboardView == null) return;
        String t = token.trim().replaceAll("^[\\u060C.\\u061F!\\u061B:\",()]+|[\\u060C.\\u061F!\\u061B:\",()]+$", "");
        if (!PredictionEngine.looksLikeEmail(t)) return;
        if (t.equals(lastLearnedEmail)) return;
        lastLearnedEmail = t;
        predictionEngine.learnWordAndTransition(t, "", "", false);
    }

    private String[] getTypingContext() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return new String[]{"", "", ""};
        String before = textBefore90(ic);
        if (before.isEmpty()) return new String[]{"", "", ""};

        if (before.charAt(before.length() - 1) == '\n') return new String[]{"", "", ""};

        boolean endsWithSpace = Character.isWhitespace(before.charAt(before.length() - 1));
        String trimmed = before.trim();
        if (trimmed.isEmpty()) return new String[]{"", "", ""};
        String[] parts = trimmed.split("\\s+");

        if (endsWithSpace) {
            String lastCompleted = parts.length > 0 ? parts[parts.length - 1] : "";
            String beforeLast = parts.length >= 2 ? parts[parts.length - 2] : "";
            return new String[]{"", lastCompleted, beforeLast};
        } else {
            String currentPrefix = parts.length > 0 ? parts[parts.length - 1] : "";
            String prevCompleted = parts.length >= 2 ? parts[parts.length - 2] : "";
            String prevCompleted2 = parts.length >= 3 ? parts[parts.length - 3] : "";
            return new String[]{currentPrefix, prevCompleted, prevCompleted2};
        }
    }

    private void applyAutoCapitalizeAtStart() {
        try {
            if (keyboardView == null || keyboardView.isArabic() || keyboardView.isHindi() || keyboardView.isUrdu() || numericFieldActive) return;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null) {
                int type = ei.inputType;
                if ((type & android.text.InputType.TYPE_MASK_CLASS) != android.text.InputType.TYPE_CLASS_TEXT) return;
                int var = type & android.text.InputType.TYPE_MASK_VARIATION;
                // Addresses, links and passwords are not sentences: never start them with a capital.
                if (var == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_URI
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || var == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return;
            }
            keyboardView.updateAutoCapitalization(shouldAutoCapitalizeEnglish());
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "applyAutoCapitalizeAtStart()", e);
        }
    }

    private static final String LEAD_PUNCT = "¿¡«(\"'“‘[";
    private static final String TRAIL_PUNCT = ".,!?;:؟،؛۔)»\"”…।॥]";

    static String keepEdgePunctuation(String typed, String correction) {
        if (typed == null || correction == null) return correction;
        int i = 0;
        while (i < typed.length() && LEAD_PUNCT.indexOf(typed.charAt(i)) >= 0) i++;
        int j = typed.length();
        while (j > i && TRAIL_PUNCT.indexOf(typed.charAt(j - 1)) >= 0) j--;
        String lead = typed.substring(0, i), trail = typed.substring(j);
        String out = correction;
        if (!lead.isEmpty() && !out.startsWith(lead)) out = lead + out;
        if (!trail.isEmpty() && !out.endsWith(trail)) out = out + trail;
        return out;
    }

    private boolean shouldAutoCapitalizeEnglish() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return true;
        CharSequence cs = textBefore(ic, 40);
        if (cs == null || cs.length() == 0) return true;
        String before = cs.toString();
        if (before.endsWith("\n")) return true;
        String trimmed = before.trim();
        if (trimmed.isEmpty()) return true;
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return (lastChar == '.' || lastChar == '!' || lastChar == '?') && Character.isWhitespace(before.charAt(before.length() - 1));
    }

    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean predictionsPending = false;

    private void refreshPredictions() {
        if (predictionsPending) return;
        predictionsPending = true;
        uiHandler.post(() -> {
            predictionsPending = false;
            refreshPredictionsNow();
        });
    }

    private void refreshPredictionsNow() {
        if (keyboardView == null) return;
        final int gen = ++predictionGen;
        String[] ctx = getTypingContext();

        // Auto-capitalization used to run only at the very end of this method, so every early
        // return below (an empty field, an OTP/screenshot/clipboard chip, a numeric field) skipped
        // it -- which is exactly why the first letter in an empty field never came out capital.
        // Run it up front instead, so it applies on every path.
        applyAutoCapitalizeAtStart();

        final InputConnection chipIc = getCurrentInputConnection();
        keyboardView.setMiddleBlocked(false);
        if (undoPending) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", uiText("تراجع", "Undo", "Deshacer"), ""), 5);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (otpChipWins(chipIc) && ctx[0].isEmpty() && fieldIsEmpty(chipIc)) {

            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", currentOtpCode(), ""), 3);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (!numericFieldActive && hasValidShotChip() && ctx[0].isEmpty() && chipFieldOk(chipIc)) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setMiddleBlocked(!editorAcceptsScreenshot());
            keyboardView.setSuggestions(Arrays.asList("", uiText("لقطة شاشة", "Screenshot", "Captura"), ""), 2);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (!noPasteField && !numericFieldActive && hasValidClipChip() && ctx[0].isEmpty() && chipFieldOk(chipIc)) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", clipChipDisplay(clipChipText), ""), 4);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (numericFieldActive) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(java.util.Collections.<String>emptyList());
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }

        if (ctx[0].isEmpty() && (ctx[1] == null || ctx[1].isEmpty())) {
            keyboardView.setToolbarIdleMode(true);
            keyboardView.setSuggestions(java.util.Collections.<String>emptyList());
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        keyboardView.setToolbarIdleMode(false);

        // After the word is finished, the emoji suggestion lives for one space only: a second
        // space (or anything else) before the next word hides it.
        boolean singleSpaceAfterWord = false;
        if (ctx[0].isEmpty() && chipIc != null) {
            CharSequence two = textBefore(chipIc, 2);
            singleSpaceAfterWord = two != null && two.length() == 2
                    && !Character.isWhitespace(two.charAt(0)) && two.charAt(1) == ' ';
        }
        String emojiSuggestion = !ctx[0].isEmpty() ? EmojiSuggester.suggestFor(ctx[0], keyboardView.getActiveLanguageCode())
                : (singleSpaceAfterWord && ctx[1] != null && !ctx[1].isEmpty() ? EmojiSuggester.suggestFor(ctx[1], keyboardView.getActiveLanguageCode()) : null);
        keyboardView.setEmojiKeySuggestion(emojiSuggestion);

        final String c0 = ctx[0], c1 = ctx[1], c2 = ctx[2];
        final boolean arabic = keyboardView.isArabic();
        try {
            predictionExecutor.execute(() -> {
                if (gen != predictionGen) return;
                final List<String> suggestions = predictionEngine.getThreeSuggestions(c0, c1, c2, arabic);
                uiHandler.post(() -> {
                    if (gen != predictionGen || keyboardView == null) return;
                    keyboardView.setSuggestions(suggestions);
                    if (!arabic) {
                        keyboardView.updateAutoCapitalization(shouldAutoCapitalizeEnglish());
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            AppLogger.log("BlackBerryImeService", "refreshPredictionsNow()", e);
        }
    }

    private boolean autoSpaceBlockedField = false;

    /**
     * Optional: a letter or digit typed straight after "word?" or "word!" gets a space first.
     * Deliberately narrow so it never touches anything else: only right after a letter/digit
     * followed by one ? ! or ؟, never in links, emails, passwords or number fields, and never
     * when the word looks like an address or code (contains / . @ : = and similar).
     */
    private void maybeAutoSpaceAfterPunctuation(InputConnection ic, String text) {
        if (text == null || text.isEmpty() || autoSpaceBlockedField || passwordFieldActive) return;
        if (!keyboardPrefs.isAutoSpaceAfterPunctuation()) return;
        if (!Character.isLetterOrDigit(text.codePointAt(0))) return;
        try {
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) return;
            CharSequence cs = textBefore(ic, 64);
            if (cs == null || cs.length() < 2) return;
            String b = cs.toString();
            int n = b.length();
            char p = b.charAt(n - 1);
            if (p != '?' && p != '!' && p != '\u061F') return;
            if (!Character.isLetterOrDigit(b.charAt(n - 2))) return;
            int s = n - 1;
            while (s > 0 && !Character.isWhitespace(b.charAt(s - 1))) s--;
            if (s == 0 && n >= 64) return; // the word may be longer than what we can see
            String token = b.substring(s, n - 1);
            for (int i = 0; i < token.length(); i++) {
                if ("/.@:=#&%\\_<>*+~|^$".indexOf(token.charAt(i)) >= 0) return;
            }
            ic.commitText(" ", 1);
            invalidateTextCache();
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "maybeAutoSpaceAfterPunctuation()", e);
        }
    }

    @Override
    public void onCharacterTyped(String text) {
        if (gifSearchFocused) {
            gifQueryBuilder.append(text);
            updateGifQueryDisplay();
            return;
        }
        clearUndo();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        maybeAutoSpaceAfterPunctuation(ic, text);
        String[] ctx = getTypingContext();
        String currentPrefix = ctx[0];
        String prevWord = ctx[1];
        String prevWord2 = ctx[2];

        // The "fresh" state of the copied-text / screenshot chips ends on a typed character;
        // spaces keep it.
        if (!" ".equals(text)) chipsFresh = false;
        // Typing only hides the code / screenshot / clipboard chips; they stay valid and show
        // again whenever their normal conditions hold, until their time window ends.

        if (" ".equals(text)) {
            long now = SystemClock.uptimeMillis();
            if (!currentPrefix.isEmpty() && keyboardPrefs.isAutoExpandShortcuts()) {
                String expanded = keyboardPrefs.findShortcutExpansion(currentPrefix);
                if (expanded != null && !expanded.isEmpty()) {
                    ic.deleteSurroundingText(currentPrefix.length(), 0);
                    ic.commitText(expanded + " ", 1);
                    predictionEngine.learnFullSentence(expanded, keyboardView != null && keyboardView.isArabic());
                    lastSpaceTapTime = 0L;
                    refreshPredictions();
                    return;
                }
            }
            if (currentPrefix.isEmpty() && keyboardPrefs.isDoubleSpacePeriod() && (now - lastSpaceTapTime < 320L)) {
                CharSequence beforeTwo = textBefore(ic, 2);
                if (beforeTwo != null && beforeTwo.length() >= 2 && beforeTwo.charAt(1) == ' ' && !Character.isWhitespace(beforeTwo.charAt(0))) {
                    ic.deleteSurroundingText(1, 0);
                    ic.commitText(keyboardView != null && keyboardView.isHindi() ? "। "
                            : keyboardView != null && keyboardView.isUrdu() ? "۔ " : ". ", 1);
                    lastSpaceTapTime = 0L;
                    refreshPredictions();
                    return;
                }
            }

            if (!currentPrefix.isEmpty() && keyboardPrefs.isAutoCorrectEnabled() && currentPrefix.indexOf('@') < 0
                    && !currentPrefix.equalsIgnoreCase(rejectedAutoCorrectWord)
                    && !keyboardPrefs.isAutoCorrectException(currentPrefix)) {
                String correction = predictionEngine.findAutoCorrection(currentPrefix, keyboardView.isArabic(), keyboardPrefs.isAutoCorrectStrong());
                if (correction != null) {
                    // Keep punctuation typed around the word ("¿qué", "hello,"): the correction
                    // only covers the letters.
                    correction = keepEdgePunctuation(currentPrefix, correction);
                    ic.deleteSurroundingText(currentPrefix.length(), 0);
                    ic.commitText(correction + " ", 1);
                    predictionEngine.learnWordAndTransition(correction, prevWord, prevWord2, keyboardView.isArabic());
                    keyboardPrefs.recordWord(keyboardView.getCurrentLangCode());
                    keyboardPrefs.addStat("stat_autocorrects", 1);
                    if (!keyboardPrefs.isIncognitoMode() && !passwordFieldActive) keyboardPrefs.addTypo(currentPrefix, 1);
                    lastAutoCorrectOriginal = currentPrefix;
                    lastAutoCorrectResult = correction;
                    lastSpaceTapTime = 0L;
                    refreshPredictions();
                    return;
                }
            }
            lastSpaceTapTime = now;
        } else {
            lastSpaceTapTime = 0L;
            // The user kept going after a correction, so they accepted it: forget earlier undos
            // of that word (only 2 undos in a row switch its correction off).
            if (lastAutoCorrectOriginal != null && !keyboardPrefs.isIncognitoMode()) {
                keyboardPrefs.resetAutoCorrectUndo(lastAutoCorrectOriginal);
            }
            lastAutoCorrectOriginal = null;
            lastAutoCorrectResult = null;
        }

        if (" ".equals(text) || ".".equals(text) || "۔".equals(text) || "؟".equals(text) || "?".equals(text)
                || "،".equals(text) || ",".equals(text) || "!".equals(text)) {
            boolean midEmail = ".".equals(text) && currentPrefix.indexOf('@') > 0;
            if (!currentPrefix.isEmpty() && !midEmail) {
                predictionEngine.learnWordAndTransition(currentPrefix, prevWord, prevWord2, keyboardView.isArabic());
                keyboardPrefs.recordWord(keyboardView.getCurrentLangCode());
            }
        }

        // An address that ended a sentence ("write to me@x.com.") is never learned at the
        // period itself (the dot could still be part of the domain), so catch it here once the
        // space proves the address really ended.
        if (" ".equals(text) && currentPrefix.isEmpty() && prevWord != null
                && prevWord.indexOf('@') > 0 && prevWord.endsWith(".")) {
            learnEmailIfValid(prevWord);
        }

        ic.commitText(text, 1);
        refreshPredictions();
    }

    @Override
    public boolean onBackspaceSingle() {
        return backspaceOnce(false);
    }

    @Override
    public boolean onBackspaceRepeat() {
        return backspaceOnce(true);
    }

    /** held: part of a press-and-hold, so the removed text can be undone; a single tap hides the undo card. */
    private boolean backspaceOnce(boolean held) {
        if (gifSearchFocused) {
            int len = gifQueryBuilder.length();
            if (len <= 0) return false;
            gifQueryBuilder.deleteCharAt(len - 1);
            updateGifQueryDisplay();
            return true;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        if (!held) clearUndo();
        CharSequence selected = ic.getSelectedText(0);
        boolean deleted;
        if (selected != null && selected.length() > 0) {
            if (held && undoAllowed()) {
                int c = cursorNow(ic);
                if (c >= 0) recordUndo(selected.toString(), c + selected.length());
            }
            ic.commitText("", 1);
            deleted = true;
        } else if (lastAutoCorrectResult != null && undoAutoCorrectIfImmediate(ic)) {
            clearUndo();
            deleted = true;
        } else {
            CharSequence before = textBefore(ic, 1);
            if (before == null || before.length() == 0) {
                // Nothing before the cursor at all -- there's genuinely nothing to delete,
                // so report that instead of calling deleteSurroundingText() as a no-op and
                // refreshing predictions for no reason on every repeat tick.
                deleted = false;
            } else {
                int len = lastGraphemeLength(ic);
                if (held && undoAllowed()) {
                    CharSequence gone = textBefore(ic, len);
                    int c = undoPending ? undoCursor : cursorNow(ic);
                    if (gone != null) recordUndo(gone.toString(), c);
                }
                ic.deleteSurroundingText(len, 0);
                deleted = true;
            }
        }
        if (deleted) {
            lastAutoCorrectOriginal = null;
            lastAutoCorrectResult = null;
            refreshPredictions();
        }
        return deleted;
    }

    private boolean undoAutoCorrectIfImmediate(InputConnection ic) {
        String expected = lastAutoCorrectResult + " ";
        CharSequence before = textBefore(ic, expected.length());
        if (before == null || !expected.equals(before.toString())) return false;
        ic.deleteSurroundingText(expected.length(), 0);
        ic.commitText(lastAutoCorrectOriginal, 1);
        rejectedAutoCorrectWord = lastAutoCorrectOriginal;
        // Remember it for good (not just until the next correction), unless nothing is being saved.
        // It only becomes permanent after the 2nd reversal of the same word.
        if (!keyboardPrefs.isIncognitoMode()) {
            keyboardPrefs.addTypo(lastAutoCorrectOriginal, -1);
            if (keyboardPrefs.bumpAutoCorrectUndo(lastAutoCorrectOriginal) >= KeyboardPreferences.AUTOCORRECT_UNDOS_TO_STOP) {
                keyboardPrefs.addAutoCorrectException(lastAutoCorrectOriginal);
            }
        }
        return true;
    }

    private int lastGraphemeLength(InputConnection ic) {
        CharSequence before = textBefore(ic, 32);
        if (before == null || before.length() == 0) return 1;
        String str = before.toString();
        try {
            java.text.BreakIterator it = java.text.BreakIterator.getCharacterInstance();
            it.setText(str);
            int end = str.length();
            int start = it.preceding(end);
            if (start == java.text.BreakIterator.DONE || start >= end) start = end - 1;
            int len = end - start;

            int cp = str.codePointBefore(end);
            boolean emojiLike = cp > 0xFFFF || (cp >= 0x2190 && cp <= 0x2BFF) || cp == 0xFE0F || cp == 0x200D || (cp >= 0x20E3 && cp <= 0x20E3);
            if (!emojiLike && len > 1) {
                len = Character.charCount(cp);
            }
            return Math.max(1, Math.min(len, 32));
        } catch (Exception e) {
            return Character.charCount(str.codePointBefore(str.length()));
        }
    }

    @Override
    public boolean onSwipeLeftDeleteWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;

        android.view.inputmethod.ExtractedTextRequest selReq = new android.view.inputmethod.ExtractedTextRequest();
        android.view.inputmethod.ExtractedText selEt = ic.getExtractedText(selReq, 0);
        if (selEt != null && selEt.selectionEnd > selEt.selectionStart) {
            if (undoAllowed() && selEt.text != null) {
                try {
                    String sel = selEt.text.subSequence(selEt.selectionStart, selEt.selectionEnd).toString();
                    recordUndo(sel, selEt.startOffset + selEt.selectionEnd);
                } catch (Exception e) {
                    AppLogger.log("BlackBerryImeService", "swipe undo", e);
                }
            }
            ic.commitText("", 1);
            refreshPredictions();
            return true;
        }

        // Same as backspace: right after an auto-correction, the swipe brings the original word back.
        if (lastAutoCorrectResult != null && undoAutoCorrectIfImmediate(ic)) {
            clearUndo();
            lastAutoCorrectOriginal = null;
            lastAutoCorrectResult = null;
            refreshPredictions();
            return true;
        }

        CharSequence cs = textBefore(ic, 64);
        if (cs == null || cs.length() == 0) return false;
        String before = cs.toString();

        int i = before.length();
        while (i > 0 && Character.isWhitespace(before.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(before.charAt(i - 1))) {
            i--;
        }
        int deleteLength = before.length() - i;
        if (deleteLength > 0) {
            if (undoAllowed()) {
                int c = undoPending ? undoCursor
                        : (selEt != null && selEt.selectionStart >= 0 ? selEt.startOffset + selEt.selectionStart : cursorNow(ic));
                recordUndo(before.substring(i), c);
            }
            ic.deleteSurroundingText(deleteLength, 0);
            keyboardPrefs.addStat("stat_swipe_deletes", 1);
        }
        refreshPredictions();
        return deleteLength > 0;
    }

    @Override
    public boolean onSwipeSelectAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;

        try {
            if (ic.performContextMenuAction(android.R.id.selectAll)) return true;
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onSwipeSelectAll()", e);
        }

        android.view.inputmethod.ExtractedTextRequest req = new android.view.inputmethod.ExtractedTextRequest();
        android.view.inputmethod.ExtractedText et = ic.getExtractedText(req, 0);
        if (et == null || et.text == null || et.text.length() == 0) return false;
        return ic.setSelection(0, et.text.length());
    }

    private long lastCopyTime = 0L;
    private static final long PASTE_WINDOW_MS = 60_000L;

    @Override
    public boolean onSwipeDownCopyPaste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        long now = android.os.SystemClock.uptimeMillis();
        boolean pasteMode = lastCopyTime != 0L && (now - lastCopyTime) < PASTE_WINDOW_MS;

        if (pasteMode) {
            try {
                if (ic.performContextMenuAction(android.R.id.paste)) {
                    lastCopyTime = 0L;
                    return true;
                }
            } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "onSwipeDownCopyPaste()", e);
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (!noPasteField && cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (text != null) {
                    ic.commitText(text, 1);
                    lastCopyTime = 0L;
                    return true;
                }
            }
            return false;
        }

        try {
            if (ic.performContextMenuAction(android.R.id.copy)) {
                lastCopyTime = now;
                return true;
            }
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "onSwipeDownCopyPaste()", e);
        }
        return false;
    }

    @Override
    public void onSwipeUpSuggestion(int zoneIndex) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (undoPending) {
            if (zoneIndex == 1) {
                String back = undoText.toString();
                undoPending = false;
                undoText.setLength(0);
                undoCursor = -1;
                undoAnchor = -1;
                ic.commitText(back, 1);
                refreshPredictions();
            }
            return;
        }
        String[] ctx = getTypingContext();
        String currentPrefix = ctx[0];
        String lastCompletedWord = ctx[1];
        String lastCompletedWord2 = ctx[2];

        if (otpChipWins(ic) && currentPrefix.isEmpty() && fieldIsEmpty(ic)) {

            if (zoneIndex == 1) {
                ic.commitText(currentOtpCode(), 1);
                clearPendingOtp();
                refreshPredictions();
            }
            return;
        }

        if (!numericFieldActive && hasValidShotChip() && currentPrefix.isEmpty() && chipFieldOk(ic)) {
            if (zoneIndex == 1) {
                if (!editorAcceptsScreenshot()) {
                    useScreenshotClip();
                    return;
                }
                clearClipChip();
                useScreenshotClip();
                refreshPredictions();
            }
            return;
        }

        if (!noPasteField && !numericFieldActive && hasValidClipChip() && currentPrefix.isEmpty() && chipFieldOk(ic)) {

            if (zoneIndex == 1) {
                ic.commitText(clipChipText, 1);
                clearClipChip();
                refreshPredictions();
            }
            return;
        }

        if (currentPrefix.isEmpty() && (lastCompletedWord == null || lastCompletedWord.isEmpty())) return;

        List<String> suggestions = predictionEngine.getThreeSuggestions(currentPrefix, lastCompletedWord, lastCompletedWord2, keyboardView.isArabic());
        if (suggestions.isEmpty()) return;
        String chosen = (zoneIndex >= 0 && zoneIndex < suggestions.size()) ? suggestions.get(zoneIndex) : suggestions.get(0);

        if (chosen == null || chosen.isEmpty()) return;
        boolean learnableChoice = chosen.indexOf(' ') < 0 && containsLetter(chosen);

        if (!currentPrefix.isEmpty()) {
            ic.deleteSurroundingText(currentPrefix.length(), 0);
            ic.commitText(chosen + " ", 1);
            if (learnableChoice) predictionEngine.learnWordAndTransition(chosen, lastCompletedWord, lastCompletedWord2, keyboardView.isArabic());
            keyboardPrefs.incrementFlickWordCount();
            keyboardPrefs.recordWord(keyboardView.getCurrentLangCode());
            keyboardPrefs.addStat("stat_saved_keys", Math.max(0, chosen.length() - currentPrefix.length() + 1));
        } else {
            CharSequence beforeCs = textBefore(ic, 2);
            String before = beforeCs != null ? beforeCs.toString() : "";
            String prefixSpace = (!before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1))) ? " " : "";
            ic.commitText(prefixSpace + chosen + " ", 1);
            if (learnableChoice) predictionEngine.learnWordAndTransition(chosen, lastCompletedWord, lastCompletedWord2, keyboardView.isArabic());
            keyboardPrefs.incrementFlickWordCount();
            keyboardPrefs.recordWord(keyboardView.getCurrentLangCode());
            keyboardPrefs.addStat("stat_saved_keys", chosen.length() + 1);
        }
        refreshPredictions();
    }

    private static boolean containsLetter(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    @Override
    public void onSuggestionLongPress(int slotIndex, String word) {
        if (word == null || word.isEmpty()) return;
        boolean isArabic = keyboardView.isArabic();
        if (predictionEngine.isDefaultPlaceholderWord(word, isArabic)) {
            keyboardPrefs.dismissDefaultWord(keyboardView.getActiveLanguageCode(), word);
            refreshPredictions();
        }
    }

    @Override
    public void onEnterKey() {
        if (gifSearchFocused) {
            closeGifSearchKeyboard(true);
            return;
        }
        clearUndo();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] ctx = getTypingContext();
        if (!ctx[0].isEmpty()) {
            predictionEngine.learnWordAndTransition(ctx[0], ctx[1], ctx[2], keyboardView.isArabic());
            if (ctx[0].indexOf('@') > 0) lastLearnedEmail = ctx[0];
        }

        EditorInfo info = getCurrentInputEditorInfo();
        int actionId = info != null ? (info.imeOptions & EditorInfo.IME_MASK_ACTION) : EditorInfo.IME_ACTION_NONE;
        boolean noEnterAction = info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;

        if (!noEnterAction && actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        refreshPredictions();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int shellC()      { return keyboardView != null ? keyboardView.getShellColor() : Color.parseColor("#111111"); }
    private int sugBarC()     { return keyboardView != null ? keyboardView.getSuggestionBarColor() : Color.parseColor("#171717"); }
    private int keyBgC()      { return keyboardView != null ? keyboardView.getKeyBgColor() : Color.parseColor("#262626"); }
    private int labelC()     { return keyboardView != null ? keyboardView.getKeyLabelColor() : Color.WHITE; }
    private int subC()       { return (labelC() & 0x00FFFFFF) | 0xA0000000; }
    private int pillC()      { return (labelC() & 0x00FFFFFF) | 0x33000000; }
    private int pillCLight() { return (labelC() & 0x00FFFFFF) | 0x18000000; }

    private int floatingPillC() {
        // Fixed per theme instead of a user-picked color: the old default (black) was
        // nearly invisible against the Matte Black/Midnight Slate themes' own dark
        // background once the extra dark base layer below was added on top of it too.
        int theme = keyboardPrefs != null ? keyboardPrefs.getThemeMode() : KeyboardPreferences.THEME_GBOARD_WHITE;
        if (theme == KeyboardPreferences.THEME_GBOARD_WHITE) {
            // White theme: match the exact pill style used by the back-arrow buttons in
            // the clipboard/emoji drawers (pillC()) instead of the old black-tinted look.
            return (labelC() & 0x00FFFFFF) | 0x39000000; // pillC() alpha 0x33 + 2%
        }
        int alpha = Math.round(255 * 30 / 100f); // was 28%: 2% less transparent
        return (0x9CA3AF) | (alpha << 24);
    }

    private android.graphics.drawable.Drawable buildFloatingCapsuleDrawable() {
        // A single solid pill now -- the previous version layered this color over an
        // always-present, mostly-opaque black base, which is what made the capsule read
        // as plain black (and vanish into a dark keyboard background) regardless of the
        // color chosen above it.
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(floatingPillC());
        pill.setCornerRadius(dp(40));
        return new android.graphics.drawable.InsetDrawable(pill, dp(3), dp(6), dp(3), dp(6));
    }
    private int pressedC()    { return keyboardView != null ? keyboardView.getKeyPressedColor() : Color.parseColor("#323846"); }
    private int keyDividerC() { return keyboardView != null ? keyboardView.getKeyDividerColor() : Color.parseColor("#363636"); }
    private int keyOutlineC() { return keyboardView != null ? keyboardView.getKeyOutlineColor() : Color.parseColor("#525B6C"); }

    private GradientDrawable roundedBg(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private GradientDrawable roundedBg(String hexColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(hexColor));
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private static class BottomBarRefs {
        LinearLayout bar;
        FrameLayout abcWrap;
        TextView abcLabel;
        FrameLayout delWrap;
        KeyShapeBackgroundView abcBg;
        KeyShapeBackgroundView delBg;
    }

    private BottomBarRefs clipBottomBarRefs;
    private BottomBarRefs emojiBottomBarRefs;

    private void refreshFloatingCapsuleColors(BottomBarRefs refs) {
        if (refs == null) return;
        if (refs.abcWrap != null) {
            refs.abcWrap.setBackground(buildFloatingCapsuleDrawable());
        }
        if (refs.delWrap != null) {
            refs.delWrap.setBackground(buildFloatingCapsuleDrawable());
        }
    }

    private BottomBarRefs buildBottomLettersBar(final boolean withBackspace) {
        BottomBarRefs refs = new BottomBarRefs();

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        bar.setBackgroundColor(Color.TRANSPARENT);
        bar.setPadding(dp(10), 0, dp(10), 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        refs.bar = bar;

        int style = (keyboardView != null) ? keyboardView.getActiveKeyboardStyle() : KeyboardPreferences.STYLE_BLACKBERRY_FRETS;
        int fillColor = (keyboardView != null) ? keyboardView.getKeySpecialBgColor() : Color.parseColor("#262B35");
        int shadowColor = (keyboardView != null) ? keyboardView.getKeyShadowColor() : Color.parseColor("#0F1115");
        int outlineColor = (keyboardView != null) ? keyboardView.getKeyOutlineColor() : Color.parseColor("#475569");
        int dividerColor = (keyboardView != null) ? keyboardView.getKeyDividerColor() : Color.parseColor("#262B35");
        int labelColor = (keyboardView != null) ? keyboardView.getKeyLabelColor() : Color.WHITE;

        FrameLayout abcWrap = new FrameLayout(this);
        abcWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(40)));

        KeyShapeBackgroundView abcBg = new KeyShapeBackgroundView(this, style, fillColor, shadowColor, outlineColor, dividerColor);
        abcBg.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        abcWrap.setBackground(buildFloatingCapsuleDrawable());
        refs.abcBg = abcBg;

        TextView abcLabel = new TextView(this);
        abcLabel.setText(keyboardView == null ? "أبجد" : lettersKeyLabel(keyboardView.isArabic()));
        abcLabel.setTextColor(labelC());
        abcLabel.setTypeface(Typeface.DEFAULT_BOLD);
        abcLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        abcLabel.setGravity(Gravity.CENTER);
        abcLabel.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        abcWrap.addView(abcLabel);
        abcWrap.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
        });

        refs.abcWrap = abcWrap;
        refs.abcLabel = abcLabel;
        bar.addView(abcWrap);

        android.widget.Space spacer = new android.widget.Space(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        bar.addView(spacer);

        if (withBackspace) {
            FrameLayout delWrap = new FrameLayout(this);
            delWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(40)));
            refs.delBg = null;

            delWrap.setBackground(buildFloatingCapsuleDrawable());

            TextView delIcon = new TextView(this);
            delIcon.setText("⌫");
            delIcon.setTextColor(labelColor);
            delIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            delIcon.setGravity(Gravity.CENTER);
            delIcon.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            delWrap.addView(delIcon);
            delWrap.setOnClickListener(v -> {
                soundController.playKeyClick();
                onBackspaceSingle();
            });

            refs.delWrap = delWrap;
            bar.addView(delWrap);
        }

        return refs;
    }

    private void refreshBottomBarGeometry() {
        if (keyboardView == null) return;
        int width = keyboardView.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        if (width <= 0) return;

        int edgeGap = Math.round(keyboardView.getBottomRowEdgeInsetPx(width));
        int symW = Math.round(keyboardView.getSymbolsKeyWidthPx(width));
        int rowH = Math.round(keyboardView.getBottomRowHeightPx());
        float gapX = keyboardView.getTabletKeyGapXPx(width);
        float gapY = keyboardView.getTabletKeyGapYPx(width);
        boolean tablet = gapX > 0f;

        applyBottomBarGeometry(clipBottomBarRefs, edgeGap, symW, rowH, gapX, gapY);
        applyBottomBarGeometry(emojiBottomBarRefs, edgeGap, symW, rowH, gapX, gapY);

        int contentSidePad = dp(4) + edgeGap;
        if (clipsListContainer != null) {
            clipsListContainer.setPadding(contentSidePad, clipsListContainer.getPaddingTop(),
                    contentSidePad, clipsListContainer.getPaddingBottom());
        }
        if (emojiSectionsHost != null) {
            emojiSectionsHost.setPadding(contentSidePad, emojiSectionsHost.getPaddingTop(),
                    contentSidePad, emojiSectionsHost.getPaddingBottom());
        }

        if (emojiHeaderRow != null) {
            emojiHeaderRow.setPadding(tablet ? edgeGap : dp(8), dp(5), tablet ? edgeGap : dp(8), dp(5));
        }
        if (clipHeaderRow != null) {
            clipHeaderRow.setPadding(tablet ? edgeGap + dp(6) : dp(14), dp(6),
                    tablet ? edgeGap + dp(6) : dp(12), dp(6));
        }

        if (emojiBackBtn != null) {
            ViewGroup.LayoutParams lp = emojiBackBtn.getLayoutParams();
            if (lp != null) {
                int target = tablet ? symW : dp(62);
                if (lp.width != target) {
                    lp.width = target;
                    emojiBackBtn.setLayoutParams(lp);
                }
            }
        }

        int availW = width - contentSidePad * 2;
        int cols = Math.max(8, Math.min(16, availW / dp(46)));
        emojiGridCols = cols;
        emojiGridCellW = Math.max(dp(30), availW / cols);
    }

    private void applyBottomBarGeometry(BottomBarRefs refs, int edgeGap, int symW, int rowH, float gapX, float gapY) {
        if (refs == null || refs.bar == null) return;
        refreshFloatingCapsuleColors(refs);
        if (refs.abcBg != null) refs.abcBg.setExtraInset(gapX, gapY);
        if (refs.delBg != null) refs.delBg.setExtraInset(gapX, gapY);

        int leftPad = edgeGap;
        int rightPad = (refs.delWrap != null) ? Math.max(edgeGap, dp(8)) : edgeGap;
        refs.bar.setPadding(leftPad, 0, rightPad, 0);
        ViewGroup.LayoutParams barLp = refs.bar.getLayoutParams();
        if (barLp != null) {
            barLp.height = rowH;
            refs.bar.setLayoutParams(barLp);
        }
        if (refs.abcWrap != null) {
            ViewGroup.LayoutParams lp = refs.abcWrap.getLayoutParams();
            if (lp != null) {
                lp.width = symW;
                lp.height = rowH;
                refs.abcWrap.setLayoutParams(lp);
            }
        }
        if (refs.delWrap != null) {
            ViewGroup.LayoutParams lp = refs.delWrap.getLayoutParams();
            if (lp != null) {
                lp.width = symW;
                lp.height = rowH;
                refs.delWrap.setLayoutParams(lp);
            }
        }
    }

    private LinearLayout buildClipboardAndResizeDrawer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(shellC());
        container.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(304)));
        container.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(sugBarC());
        header.setPadding(dp(14), dp(6), dp(12), dp(6));
        clipHeaderRow = header;

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpTitleBox = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitleBox.leftMargin = 0;
        lpTitleBox.rightMargin = dp(6);
        titleBox.setLayoutParams(lpTitleBox);

        titleBox.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        titleBox.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // Slightly dimmed from full brightness (its stroke was also noticeably thicker/
        // bolder-looking than the other icons here), but kept clearly more opaque than the
        // mic/settings/resize icons' own dimmed color, so it still reads as the primary icon.
        int clipIconC = (labelC() & 0x00FFFFFF) | 0xCC000000;
        ModernIconView clipTitleIcon = new ModernIconView(this, ModernIconView.ICON_CLIPBOARD_PAD,
                clipIconC, Color.TRANSPARENT);
        clipTitleIcon.setStyle(1f, 1.6f);
        LinearLayout.LayoutParams lpClipIcon = new LinearLayout.LayoutParams(dp(32), dp(32));
        lpClipIcon.rightMargin = dp(6);
        clipTitleIcon.setLayoutParams(lpClipIcon);
        titleRow.addView(clipTitleIcon);

        clipCountTv = new TextView(this);
        clipCountTv.setTextColor(labelC());
        clipCountTv.setTypeface(Typeface.DEFAULT_BOLD);
        clipCountTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        clipCountTv.setGravity(Gravity.LEFT);
        titleRow.addView(clipCountTv);

        titleBox.addView(titleRow);

        // Half-bright instead of the fully-opaque label color -- these three are
        // secondary actions next to the clipboard title, not the main focus.
        int dimIconC = (labelC() & 0x00FFFFFF) | 0x80000000;

        ModernIconView settingsIconBtn = new ModernIconView(this, ModernIconView.ICON_SETTINGS_GEAR,
                dimIconC, pillCLight());
        settingsIconBtn.setStyle(0.64f, 1.7f);
        LinearLayout.LayoutParams lpSet = new LinearLayout.LayoutParams(dp(38), dp(38));
        lpSet.leftMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        lpSet.rightMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        settingsIconBtn.setLayoutParams(lpSet);
        settingsIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            openAppSettingsActivity();
        });

        ModernIconView micIconBtn = new ModernIconView(this, ModernIconView.ICON_MIC,
                dimIconC, pillCLight());
        micIconBtn.setStyle(0.64f, 1.7f);
        LinearLayout.LayoutParams lpMic = new LinearLayout.LayoutParams(dp(38), dp(38));
        lpMic.leftMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        lpMic.rightMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        micIconBtn.setLayoutParams(lpMic);
        micIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
            try {
                onVoiceInputRequested();
            } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "micIconBtn.onClick()", e);
            }
        });

        ModernIconView resizeIconBtn = new ModernIconView(this, ModernIconView.ICON_RESIZE_HEIGHT,
                dimIconC, pillCLight());
        resizeIconBtn.setStyle(0.64f, 1.7f);
        LinearLayout.LayoutParams lpRes = new LinearLayout.LayoutParams(dp(38), dp(38));
        lpRes.leftMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        lpRes.rightMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        resizeIconBtn.setLayoutParams(lpRes);
        resizeIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
            try {
                keyboardView.setResizeDragMode(!keyboardView.isResizeDragMode());
            } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "resizeIconBtn.onClick()", e);
            }
        });

        ModernIconView backIconBtn = new ModernIconView(this, ModernIconView.ICON_FORWARD_ARROW,
                labelC(), pillC());
        backIconBtn.setPillBackground(true);
        backIconBtn.setStyle(0.85f, 2.3f);
        LinearLayout.LayoutParams lpBackClip = new LinearLayout.LayoutParams(dp(58), dp(42));

        lpBackClip.leftMargin = dp(10);
        backIconBtn.setLayoutParams(lpBackClip);
        backIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
        });

        ModernIconView oneHandIconBtn = new ModernIconView(this, ModernIconView.ICON_ONE_HAND,
                dimIconC, pillCLight());
        oneHandIconBtn.setStyle(0.64f, 1.7f);
        LinearLayout.LayoutParams lpOne = new LinearLayout.LayoutParams(dp(38), dp(38));
        lpOne.leftMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        lpOne.rightMargin = (int) (5.5f * getResources().getDisplayMetrics().density);
        oneHandIconBtn.setLayoutParams(lpOne);
        oneHandHeaderBtn = oneHandIconBtn;
        oneHandIconBtn.setVisibility(keyboardPrefs.getOneHandedMode() == 0 ? View.VISIBLE : View.GONE);
        oneHandIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            keyboardPrefs.setOneHandedMode(2);
            applyOneHandedMode();
            showKeyboardMain();
        });

        header.addView(titleBox);
        header.addView(oneHandIconBtn);
        header.addView(settingsIconBtn);
        header.addView(resizeIconBtn);
        header.addView(micIconBtn);
        header.addView(backIconBtn);
        container.addView(header);

        FrameLayout clipContentFrame = new FrameLayout(this);
        clipContentFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);

        clipsListContainer = new LinearLayout(this);
        clipsListContainer.setOrientation(LinearLayout.VERTICAL);
        clipsListContainer.setPadding(dp(10), dp(10), dp(10), dp(56));

        scroll.addView(clipsListContainer);
        clipContentFrame.addView(scroll);

        clipBottomBarRefs = buildBottomLettersBar(false);
        FrameLayout.LayoutParams clipBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clipBarLp.gravity = Gravity.BOTTOM;
        clipBottomBarRefs.bar.setLayoutParams(clipBarLp);
        clipContentFrame.addView(clipBottomBarRefs.bar);
        clipAbcBackLabel = clipBottomBarRefs.abcLabel;

        container.addView(clipContentFrame);

        return container;
    }

    private void refreshClipboardDrawerList() {
        if (clipsListContainer == null) return;
        clipsListContainer.removeAllViews();

        if (keyboardPrefs.isIncognitoMode()) {
            if (clipCountTv != null) clipCountTv.setText("");
            LinearLayout incog = new LinearLayout(this);
            incog.setOrientation(LinearLayout.VERTICAL);
            incog.setGravity(Gravity.CENTER);
            incog.setPadding(dp(16), dp(26), dp(16), dp(26));

            ModernIconView incogIcon = new ModernIconView(this, ModernIconView.ICON_CLIPBOARD_PAD,
                    subC(), Color.TRANSPARENT);
            incogIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(54)));

            TextView incogTv = new TextView(this);
            incogTv.setText(uiText(
                    "وضع التخفي مفعّل\nالحافظة مخفية الآن ولا تُحفظ عناصر جديدة",
                    "Incognito mode is on\nClipboard is hidden and nothing new is being saved",
                    "El modo de incógnito está activado\nEl portapapeles está oculto y no se guarda nada nuevo"));
            incogTv.setTextColor(subC());
            incogTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            incogTv.setGravity(Gravity.CENTER);
            incogTv.setPadding(0, dp(6), 0, 0);

            incog.addView(incogIcon);
            incog.addView(incogTv);
            clipsListContainer.addView(incog);
            return;
        }

        ScreenshotClip.pruneIfOld(this, keyboardPrefs.getClipRetentionHours() * 3600L * 1000L);
        boolean hasShotThumb = keyboardPrefs.isScreenshotClipEnabled() && ScreenshotClip.exists(this);

        List<ClipboardRepository.ClipEntry> clips = clipboardRepo.getClips();
        if (clipCountTv != null) {
            if (clips.isEmpty()) {
                clipCountTv.setText("");
            } else {
                clipCountTv.setText(uiText(
                        clips.size() + " عنصر",
                        clips.size() + (clips.size() == 1 ? " item" : " items"),
                        clips.size() + (clips.size() == 1 ? " elemento" : " elementos")));
            }
        }
        if (clips.isEmpty() && !hasShotThumb) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(26), dp(16), dp(26));

            ModernIconView emptyIcon = new ModernIconView(this, ModernIconView.ICON_CLIPBOARD_PAD,
                    subC(), Color.TRANSPARENT);
            emptyIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(54)));

            TextView emptyTv = new TextView(this);
            emptyTv.setText(uiText("ما فيه شي منسوخ بعد", "Nothing copied yet", "Nada copiado todavía"));
            emptyTv.setTextColor(subC());
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            emptyTv.setGravity(Gravity.CENTER);
            emptyTv.setPadding(0, dp(6), 0, 0);

            empty.addView(emptyIcon);
            empty.addView(emptyTv);
            clipsListContainer.addView(empty);
            return;
        }

        // Two independent columns, each card keeping its own height (the screenshot preview, when
        // there is one, sits at the top of the right column).
        {
            // Two independent columns (text on the left, screenshot on the right, fixed LTR),
            // so a tall screenshot doesn't stretch a shared row and leave an empty gap beside
            // it: each column simply stacks its own cards. Text cards go to whichever column
            // is estimated shorter; the screenshot counts as roughly four card-heights.
            LinearLayout cols = new LinearLayout(this);
            cols.setOrientation(LinearLayout.HORIZONTAL);
            cols.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout.LayoutParams colsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            colsLp.bottomMargin = dp(7);
            cols.setLayoutParams(colsLp);

            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.rightMargin = dp(24);
            leftCol.setLayoutParams(leftLp);
            LinearLayout rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            cols.addView(leftCol);
            cols.addView(rightCol);

            float leftH = 0f, rightH = 0f;
            if (hasShotThumb) {
                View shotCard = buildScreenshotThumbRow();
                LinearLayout.LayoutParams shotLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                shotLp.bottomMargin = dp(7);
                shotCard.setLayoutParams(shotLp);
                rightCol.addView(shotCard);
                rightH = 4f;
            }
            for (int i = 0; i < clips.size(); i++) {
                View card = buildClipCard(clips.get(i));
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = dp(7);
                card.setLayoutParams(cardLp);
                float units = Math.min(2f, (float) Math.ceil(Math.max(1, clips.get(i).text.length()) / 25f));
                if (leftH <= rightH) { leftCol.addView(card); leftH += units; }
                else { rightCol.addView(card); rightH += units; }
            }
            clipsListContainer.addView(cols);
            return;
        }
    }

    private View buildClipCard(final ClipboardRepository.ClipEntry clip) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final boolean arabicUi = keyboardView == null || keyboardView.isRtlScript();

        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cardBg = roundedBg(keyBgC(), 14);
        cardBg.setStroke(dp(1), clip.pinned ? keyOutlineC() : keyDividerC());
        row.setBackground(cardBg);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView textTv = new TextView(this);
        textTv.setText(clip.text);
        textTv.setTextColor(labelC());
        textTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        textTv.setGravity((arabicUi ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textTv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        textTv.setMaxLines(2);
        textTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textTv.setPadding(dp(10), dp(6), dp(10), dp(6));
        final String phone = keyboardPrefs.isClipPhoneButtonEnabled() ? phoneNumberOrNull(clip.text) : null;
        final String email = (phone == null && keyboardPrefs.isClipMailButtonEnabled()) ? emailOrNull(clip.text) : null;
        textTv.setLayoutParams(phone == null && email == null
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textTv.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && !noPasteField) ic.commitText(clip.text + " ", 1);
            soundController.playKeyClick();
            showKeyboardMain();
        });
        textTv.setOnLongClickListener(v -> {
            final boolean pinned = clip.pinned;
            showThemedMenu(v,
                    buildMenuItemView(ModernIconView.ICON_PUSHPIN,
                            pinned ? uiText("إلغاء التثبيت", "Unpin", "Desanclar")
                                   : uiText("تثبيت", "Pin", "Anclar"),
                            () -> {
                                clipboardRepo.togglePin(clip.text);
                                refreshClipboardDrawerList();
                            }),
                    buildMenuItemView(ModernIconView.ICON_TRASH, uiText("حذف", "Delete", "Eliminar"),
                            () -> {
                                clipboardRepo.removeClip(clip.text);
                                refreshClipboardDrawerList();
                            }));
            return true;
        });

        row.addView(textTv);
        if (phone != null) {
            // Phone numbers only: a quick button that opens the dialer with the number filled in.
            // It never places the call itself (ACTION_DIAL needs no permission); the user decides.
            TextView callBtn = new TextView(this);
            callBtn.setText("📞");
            callBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            callBtn.setGravity(Gravity.CENTER);
            callBtn.setBackground(roundedBg(pressedC(), 12));
            LinearLayout.LayoutParams lpCall = new LinearLayout.LayoutParams(dp(40), dp(36));
            lpCall.setMargins(dp(4), 0, dp(6), 0);
            callBtn.setLayoutParams(lpCall);
            callBtn.setOnClickListener(v -> {
                soundController.playKeyClick();
                try {
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
                    dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(dial);
                } catch (Exception e) {
                    AppLogger.log("BlackBerryImeService", "buildClipCard() dial", e);
                }
            });
            row.addView(callBtn);
        }
        if (email != null) {
            // Email addresses only: opens a new message with just the address in "To".
            // ACTION_SENDTO with a mailto: URI sends nothing by itself; the user writes and sends.
            TextView mailBtn = new TextView(this);
            mailBtn.setText("✉️");
            mailBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            mailBtn.setGravity(Gravity.CENTER);
            mailBtn.setBackground(roundedBg(pressedC(), 12));
            LinearLayout.LayoutParams lpMail = new LinearLayout.LayoutParams(dp(40), dp(36));
            lpMail.setMargins(dp(4), 0, dp(6), 0);
            mailBtn.setLayoutParams(lpMail);
            mailBtn.setOnClickListener(v -> {
                soundController.playKeyClick();
                try {
                    Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(email, "@")));
                    mail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mail);
                } catch (Exception e) {
                    AppLogger.log("BlackBerryImeService", "buildClipCard() mail", e);
                }
            });
            row.addView(mailBtn);
        }
        return row;
    }

    /** Returns the address if the whole text is a single plain email address, else null. */
    static String emailOrNull(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.length() < 6 || t.length() > 254) return null;
        return t.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}") ? t : null;
    }

    /** Returns the number (digits, optional leading +) if the whole text is a phone number, else null. */
    static String phoneNumberOrNull(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.length() < 7 || t.length() > 24) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c >= '\u0660' && c <= '\u0669') sb.append((char) ('0' + (c - '\u0660')));
            else if (c == '+' && i == 0) sb.append(c);
            else if (c == ' ' || c == '-' || c == '(' || c == ')' || c == '.') continue;
            else return null;
        }
        String n = sb.toString();
        int digits = n.startsWith("+") ? n.length() - 1 : n.length();
        return (digits >= 7 && digits <= 15) ? n : null;
    }

    /**
     * Themed long-press menu added directly into the keyboard's own window (rootContainer)
     * instead of a separate PopupWindow — a focusable popup window was briefly stealing the
     * input window's focus and making the keyboard glitch/disappear on long-press.
     */
    private FrameLayout activeClipMenuOverlay;

    private void dismissClipMenu() {
        if (activeClipMenuOverlay != null && rootContainer != null) {
            rootContainer.removeView(activeClipMenuOverlay);
        }
        activeClipMenuOverlay = null;
    }

    /**
     * A view's offset relative to rootContainer, walking up the parent chain and accounting for
     * any scrollable ancestor (the clip list sits in a ScrollView) — unlike getLocationOnScreen(),
     * this never depends on the IME window's own position, which the system can still be animating
     * (resizing/sliding into place) right when a long-press fires. Using screen coordinates there
     * was the cause of the menu sometimes landing below, sometimes above, and sometimes stuck
     * pinned to the very top of the keyboard: the window's on-screen position was briefly stale.
     */
    private int[] offsetWithinRoot(View v) {
        int x = 0, y = 0;
        View cur = v;
        while (cur != null && cur != rootContainer) {
            x += cur.getLeft();
            y += cur.getTop();
            android.view.ViewParent parentObj = cur.getParent();
            if (!(parentObj instanceof View)) break;
            View parent = (View) parentObj;
            x -= parent.getScrollX();
            y -= parent.getScrollY();
            cur = parent;
        }
        return new int[]{x, y};
    }

    private void showThemedMenu(final View anchor, View... items) {
        if (rootContainer == null) return;
        dismissClipMenu();

        final LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable menuBg = roundedBg(keyBgC(), 14);
        menuBg.setStroke(dp(1), keyDividerC());
        menu.setBackground(menuBg);
        menu.setElevation(dp(6));
        for (int i = 0; i < items.length; i++) {
            menu.addView(items[i]);
            if (i < items.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(keyDividerC());
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                menu.addView(divider);
            }
        }

        // Fixed to the keyboard's CURRENT measured size rather than MATCH_PARENT: a match_parent
        // child added to a wrap_content FrameLayout forces a second measure pass that can briefly
        // resolve to a different height than the content actually needs, which is what made the
        // keyboard window's own height flicker (quickly resize then snap back) right as the menu
        // appeared. A fixed, already-known size never changes what rootContainer resolves to.
        int rootW = rootContainer.getWidth();
        int rootH0 = rootContainer.getHeight();
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(rootW, rootH0));
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissClipMenu());

        int[] anchorOff = offsetWithinRoot(anchor);
        final int anchorTop = anchorOff[1];
        final int anchorBottom = anchorTop + anchor.getHeight();
        int anchorLeft = anchorOff[0];

        int menuWidth = dp(176);
        int maxLeft = Math.max(dp(6), rootContainer.getWidth() - menuWidth - dp(6));
        int clampedLeft = Math.max(dp(6), Math.min(anchorLeft, maxLeft));

        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuLp.leftMargin = clampedLeft;
        menuLp.topMargin = anchorBottom + dp(4);
        // Measure the menu now so its final position is known before it is ever shown: flip
        // above the anchor when it would overflow below, then clamp inside the keyboard so
        // neither row can be clipped (the post-layout height was unreliable for lower items).
        menu.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int menuH = menu.getMeasuredHeight();
        int rootH = rootH0;
        int gap = dp(4);
        int top = anchorBottom + gap;
        if (top + menuH > rootH - gap) {
            top = anchorTop - menuH - gap;
        }
        top = Math.max(gap, Math.min(top, rootH - menuH - gap));
        menuLp.topMargin = top;
        menu.setLayoutParams(menuLp);

        overlay.addView(menu);
        rootContainer.addView(overlay);
        activeClipMenuOverlay = overlay;
        menu.setAlpha(0f);
        menu.setScaleX(0.92f);
        menu.setScaleY(0.92f);
        menu.setPivotX(menuWidth / 2f);
        menu.setPivotY(top > anchorTop ? 0f : menuH);
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private View buildMenuItemView(int iconType, String label, final Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), dp(12), dp(16), dp(12));

        ModernIconView icon = new ModernIconView(this, iconType, labelC(), Color.TRANSPARENT);
        icon.setStyle(0.85f, 1.9f);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(22), dp(22));
        lpIcon.rightMargin = dp(12);
        icon.setLayoutParams(lpIcon);
        item.addView(icon);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(labelC());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        item.addView(tv);

        item.setOnClickListener(v -> {
            action.run();
            dismissClipMenu();
        });
        return item;
    }

    private LinearLayout buildCategorizedEmojiDrawer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(shellC());
        container.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(309)));
        container.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(sugBarC());
        header.setPadding(dp(8), dp(5), dp(8), dp(5));
        emojiHeaderRow = header;

        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsRow.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        emojiTabViews.clear();
        for (int i = 0; i < EMOJI_CAT_ICON_TYPES.length; i++) {
            final int catIdx = i;
            View tab;
            String emoji = EMOJI_CAT_TAB_EMOJI[i];
            if (emoji != null) {
                TextView emojiTab = new TextView(this);
                emojiTab.setText(emoji);
                emojiTab.setGravity(Gravity.CENTER);
                emojiTab.setIncludeFontPadding(false);
                emojiTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
                emojiTab.setPadding(dp(2), dp(5), dp(2), dp(5));
                tab = emojiTab;
            } else {
                ModernIconView iconTab = new ModernIconView(this, EMOJI_CAT_ICON_TYPES[i], labelC(), Color.TRANSPARENT);
                iconTab.setStyle(0.86f, 2.0f);
                tab = iconTab;
            }
            LinearLayout.LayoutParams lpTab = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lpTab.leftMargin = dp(2);
            lpTab.rightMargin = dp(2);
            tab.setLayoutParams(lpTab);
            tab.setOnClickListener(v -> {
                soundController.playKeyClick();
                scrollToEmojiSection(catIdx);
            });
            emojiTabViews.add(tab);
            tabsRow.addView(tab);
        }

        // GIF search needs INTERNET, which the bkey (lite) flavor's manifest strips entirely
        // (see app/src/lite/AndroidManifest.xml) -- so the tab is skipped there instead of
        // showing a tab that can only ever fail to load. gifTabView stays null in that build;
        // every other use of it already null-checks before touching it.
        if (BuildConfig.OTP_FEATURES_ENABLED) {
            TextView gifTab = new TextView(this);
            gifTab.setText("GIF");
            gifTab.setGravity(Gravity.CENTER);
            gifTab.setIncludeFontPadding(false);
            gifTab.setTextColor(labelC());
            gifTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            gifTab.setTypeface(Typeface.DEFAULT_BOLD);
            gifTab.setPadding(dp(2), dp(5), dp(2), dp(5));
            LinearLayout.LayoutParams lpGifTab = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lpGifTab.leftMargin = dp(2);
            lpGifTab.rightMargin = dp(2);
            gifTab.setLayoutParams(lpGifTab);
            gifTab.setAlpha(0.60f);
            gifTabView = gifTab;
            gifTab.setOnClickListener(v -> {
                soundController.playKeyClick();
                if (!editorAcceptsGif()) {
                    showKbToast(uiText("هذا التطبيق لا يتيح إرسال GIF هنا",
                            "This app doesn't allow sending GIFs here",
                            "Esta aplicación no permite enviar GIF aquí"));
                    return;
                }
                showGifPanel();
            });
            tabsRow.addView(gifTab);
        }

        ModernIconView backIconBtn = new ModernIconView(
                this,
                ModernIconView.ICON_FORWARD_ARROW,
                labelC(),
                pillC()
        );
        backIconBtn.setPillBackground(true);
        backIconBtn.setStyle(0.85f, 2.3f);
        LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams(dp(62), dp(42));
        lpBack.leftMargin = dp(10);
        backIconBtn.setLayoutParams(lpBack);
        emojiBackBtn = backIconBtn;
        backIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
        });

        header.addView(tabsRow);
        header.addView(backIconBtn);
        container.addView(header);

        FrameLayout emojiContentFrame = new FrameLayout(this);
        emojiContentFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        emojiContentFrameView = emojiContentFrame;

        emojiScroll = new ScrollView(this);
        emojiScroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emojiScroll.setVerticalScrollBarEnabled(false);
        emojiScroll.setOnScrollChangeListener((v, sx, sy, ox, oy) -> updateActiveEmojiTabFromScroll(sy));

        emojiScroll.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                suppressEmojiTabScrollSync = false;
            }
            return false;
        });

        emojiSectionsHost = new LinearLayout(this);
        emojiSectionsHost.setOrientation(LinearLayout.VERTICAL);
        emojiSectionsHost.setPadding(dp(6), dp(2), dp(6), dp(56));
        emojiScroll.addView(emojiSectionsHost);
        emojiContentFrame.addView(emojiScroll);

        emojiBottomBarRefs = buildBottomLettersBar(true);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        emojiBottomBarRefs.bar.setLayoutParams(barLp);
        emojiContentFrame.addView(emojiBottomBarRefs.bar);
        emojiAbcBackLabel = emojiBottomBarRefs.abcLabel;

        container.addView(emojiContentFrame);

        boolean gifArabic = keyboardView == null || keyboardView.isArabic();
        gifPanel = new FrameLayout(this);
        gifPanel.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        gifPanel.setVisibility(View.GONE);

        LinearLayout gifCol = new LinearLayout(this);
        gifCol.setOrientation(LinearLayout.VERTICAL);
        gifCol.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout gifSearchRow = new LinearLayout(this);
        gifSearchRowView = gifSearchRow;
        gifSearchRow.setOrientation(LinearLayout.HORIZONTAL);
        gifSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        gifSearchRow.setBackground(roundedBg(gifSearchFieldC(), 16));
        // Small oval magnifier button floating in the top-right corner of the GIF grid, so GIFs
        // scroll under it. Tapping it opens the real search field above the keyboard.
        FrameLayout.LayoutParams lpSearchRow = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32), Gravity.TOP | Gravity.RIGHT);
        lpSearchRow.setMargins(dp(8), dp(6), dp(8), dp(6));
        gifSearchRow.setLayoutParams(lpSearchRow);
        gifSearchRow.setMinimumWidth(dp(52));
        gifSearchRow.setGravity(Gravity.CENTER);

        // Kept only as the holder of the current query text (not shown).
        gifSearchBox = new TextView(this);

        gifSearchIconView = new ModernIconView(this, ModernIconView.ICON_SEARCH, labelC(), Color.TRANSPARENT);
        gifSearchIconView.setStyle(0.9f, 2.1f);
        LinearLayout.LayoutParams lpIc = new LinearLayout.LayoutParams(dp(22), dp(22));
        lpIc.setMargins(dp(14), 0, dp(14), 0);
        gifSearchIconView.setLayoutParams(lpIc);
        gifKlipyPillLabel = new TextView(this);
        gifKlipyPillLabel.setText(uiText("مقدَّم من KLIPY", "Powered by KLIPY", "Ofrecido por KLIPY"));
        gifKlipyPillLabel.setTextColor((labelC() & 0x00FFFFFF) | 0xE6000000);
        gifKlipyPillLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        gifKlipyPillLabel.setSingleLine(true);
        LinearLayout.LayoutParams lpKl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpKl.setMargins(dp(12), 0, 0, 0);
        gifKlipyPillLabel.setLayoutParams(lpKl);
        gifSearchRow.addView(gifKlipyPillLabel);
        gifSearchRow.addView(gifSearchIconView);
        gifSearchRow.setOnClickListener(v -> {
            soundController.playKeyClick();
            openGifSearchKeyboard();
        });

        gifSearchClearBtn = new TextView(this);
        gifSearchClearBtn.setText("✕");
        gifSearchClearBtn.setTextColor(labelC());
        gifSearchClearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        gifSearchClearBtn.setGravity(Gravity.CENTER);
        gifSearchClearBtn.setPadding(dp(4), 0, dp(12), 0);
        gifSearchClearBtn.setVisibility(View.GONE);
        gifSearchClearBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            gifLastQuery = "";
            updateGifSearchBoxDisplay("");
            loadGifResults(true, "");
        });
        gifSearchRow.addView(gifSearchClearBtn);


        gifStatusText = new TextView(this);
        gifStatusText.setGravity(Gravity.CENTER);
        gifStatusText.setTextColor(subC());
        gifStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        gifStatusText.setPadding(dp(16), dp(24 + 44), dp(16), dp(16));
        gifStatusText.setVisibility(View.GONE);
        gifCol.addView(gifStatusText);

        gifScroll = new ScrollView(this);
        gifScroll.setVerticalScrollBarEnabled(false);
        gifScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        gifGrid = new LinearLayout(this);
        gifGrid.setOrientation(LinearLayout.VERTICAL);
        gifGrid.setPadding(dp(4), dp(44), dp(4), dp(6));
        gifScroll.addView(gifGrid);
        gifCol.addView(gifScroll);

        gifPanel.addView(gifCol);
        gifPanel.addView(gifSearchRow);
        // The KLIPY credit now lives inside the floating search pill (no bar over the GIFs).
        buildGifAttributionBar();
        container.addView(gifPanel);

        rebuildEmojiSections();
        return container;
    }

    private void rebuildEmojiSections() {
        if (emojiSectionsHost == null) return;
        emojiSectionsHost.removeAllViews();
        emojiSectionHeaders.clear();

        String[] catTitles = emojiCatTitles();
        for (int c = 0; c < catTitles.length; c++) {
            TextView title = new TextView(this);
            title.setText(catTitles[c]);
            title.setTextColor(subC());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setPadding(dp(8), dp(10), dp(8), dp(4));
            emojiSectionsHost.addView(title);
            emojiSectionHeaders.add(title);

            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(emojiGridCols);
            if (c == 0) emojiRecentGrid = grid;
            fillEmojiGrid(grid, c == 0 ? keyboardPrefs.getRecentEmojis() : Arrays.asList(EMOJI_CATEGORIES[c]), c == 0);
            emojiSectionsHost.addView(grid);
            if (c == 0) {
                TextView empty = new TextView(this);
                empty.setText(uiText("لا توجد إيموجي مستخدمة حديثاً", "No recent emojis yet", "Aún no hay emojis recientes"));
                empty.setTextColor(subC());
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(8), dp(18), dp(8), dp(22));
                emojiRecentEmptyText = empty;
                emojiSectionsHost.addView(empty);
            }
        }
        applyRecentEmojiVisibility();
        updateEmojiTabHighlight(firstEmojiTab());
    }

    /** Empty "Recently used": the section stays (its clock tab scrolls to it) and shows a short message. */
    private void applyRecentEmojiVisibility() {
        boolean has = !keyboardPrefs.getRecentEmojis().isEmpty();
        if (!emojiSectionHeaders.isEmpty() && emojiSectionHeaders.get(0) instanceof TextView) {
            String title = emojiCatTitles()[0];
            if (has) title += uiText("  •  اضغط مطولاً على أي رمز لحذفه",
                    "  •  Long-press any emoji to delete it",
                    "  •  Mantén pulsado un emoji para eliminarlo");
            ((TextView) emojiSectionHeaders.get(0)).setText(title);
        }
        if (emojiRecentGrid != null) emojiRecentGrid.setVisibility(has ? View.VISIBLE : View.GONE);
        if (emojiRecentEmptyText != null) emojiRecentEmptyText.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    /** Opens at the top; with nothing recent it starts at Smileys, the empty section sits just above. */
    private void scrollEmojiToStart() {
        if (emojiScroll == null) return;
        emojiScroll.scrollTo(0, 0);
        if (!keyboardPrefs.getRecentEmojis().isEmpty() || emojiSectionHeaders.size() < 2) return;
        final View faces = emojiSectionHeaders.get(1);
        final ViewTreeObserver vto = emojiScroll.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver live = emojiScroll.getViewTreeObserver();
                if (live.isAlive()) live.removeOnPreDrawListener(this);
                int y = faces.getTop();
                if (y > 0 && emojiScroll.getScrollY() != y) {
                    emojiScroll.scrollTo(0, y);
                    return false;
                }
                return true;
            }
        });
    }

    private int firstEmojiTab() {
        return keyboardPrefs.getRecentEmojis().isEmpty() && emojiTabViews.size() > 1 ? 1 : 0;
    }

    private void fillEmojiGrid(final GridLayout grid, List<String> emojis, final boolean recent) {
        grid.removeAllViews();
        int cellW = emojiGridCellW > 0 ? emojiGridCellW
                : Math.max(dp(38), (getResources().getDisplayMetrics().widthPixels - dp(20)) / 8);
        for (final String em : emojis) {
            final boolean toned = !recent && supportsSkinTone(em);
            final android.graphics.Paint markPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            markPaint.setColor(subC());
            final android.graphics.Path markPath = new android.graphics.Path();
            TextView tv = new TextView(this) {
                @Override protected void onDraw(android.graphics.Canvas canvas) {
                    super.onDraw(canvas);
                    if (!toned) return;
                    // Tiny corner triangle: this emoji has skin tones (long-press).
                    float s = dp(6), r = getWidth() - dp(5), b = getHeight() - dp(5);
                    markPath.reset();
                    markPath.moveTo(r, b - s);
                    markPath.lineTo(r, b);
                    markPath.lineTo(r - s, b);
                    markPath.close();
                    canvas.drawPath(markPath, markPaint);
                }
            };
            tv.setText(em);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, keyboardPrefs.getEmojiSizeSp());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellW;
            lp.height = dp(Math.max(42, Math.round(keyboardPrefs.getEmojiSizeSp()) + 19));
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(em, 1);
                soundController.playKeyClick();
                keyboardPrefs.recordUsedEmoji(em);
            });
            if (recent) {
                tv.setOnLongClickListener(v -> {
                    keyboardPrefs.removeRecentEmoji(em);
                    soundController.playKeyClick();
                    fillEmojiGrid(grid, keyboardPrefs.getRecentEmojis(), true);
                    applyRecentEmojiVisibility();
                    return true;
                });
            } else if (supportsSkinTone(em)) {
                tv.setOnLongClickListener(v -> {
                    showSkinTonePopup(tv, em);
                    return true;
                });
            }
            grid.addView(tv);
        }
    }

    // ---- Skin tones: long-press an emoji that has tones to pick medium (wheat) or dark.
    private static final int[][] SKIN_BASES = {
        {0x261D,0x261D},{0x26F9,0x26F9},{0x270A,0x270D},{0x1F385,0x1F385},{0x1F3C2,0x1F3C4},
        {0x1F3C7,0x1F3C7},{0x1F3CA,0x1F3CC},{0x1F442,0x1F443},{0x1F446,0x1F450},{0x1F466,0x1F469},
        {0x1F46B,0x1F46E},{0x1F470,0x1F478},{0x1F47C,0x1F47C},{0x1F481,0x1F483},{0x1F485,0x1F487},
        {0x1F4AA,0x1F4AA},{0x1F574,0x1F575},{0x1F57A,0x1F57A},{0x1F590,0x1F590},{0x1F595,0x1F596},
        {0x1F645,0x1F647},{0x1F64B,0x1F64F},{0x1F6A3,0x1F6A3},{0x1F6B4,0x1F6B6},{0x1F6C0,0x1F6C0},
        {0x1F6CC,0x1F6CC},{0x1F90C,0x1F90C},{0x1F90F,0x1F90F},{0x1F918,0x1F91F},{0x1F926,0x1F926},
        {0x1F930,0x1F939},{0x1F93C,0x1F93E},{0x1F977,0x1F977},{0x1F9B5,0x1F9B6},{0x1F9B8,0x1F9B9},
        {0x1F9BB,0x1F9BB},{0x1F9CD,0x1F9CF},{0x1F9D1,0x1F9DD},{0x1FAC3,0x1FAC5},{0x1FAF0,0x1FAF8}
    };
    private static final int SKIN_MEDIUM = 0x1F3FD; // wheat
    private static final int SKIN_DARK = 0x1F3FF;

    private static boolean isSkinBase(int cp) {
        for (int[] r : SKIN_BASES) if (cp >= r[0] && cp <= r[1]) return true;
        return false;
    }

    private static boolean supportsSkinTone(String em) {
        if (em == null || em.isEmpty()) return false;
        int i = 0;
        while (i < em.length()) {
            int cp = em.codePointAt(i);
            if (cp >= 0x1F3FB && cp <= 0x1F3FF) return false; // already toned
            i += Character.charCount(cp);
        }
        return isSkinBase(em.codePointAt(0));
    }

    private static String withSkinTone(String em, int tone) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < em.length()) {
            int cp = em.codePointAt(i);
            i += Character.charCount(cp);
            sb.appendCodePoint(cp);
            if (isSkinBase(cp)) {
                if (i < em.length() && em.charAt(i) == 0xFE0F) i++; // drop the variation selector
                sb.appendCodePoint(tone);
            }
        }
        return sb.toString();
    }

    private void showSkinTonePopup(final View anchor, final String em) {
        try {
            final android.widget.PopupWindow[] pw = new android.widget.PopupWindow[1];
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setPadding(dp(6), dp(4), dp(6), dp(4));
            box.setBackground(roundedBg(pressedC(), 16));
            String[] options = {em, withSkinTone(em, SKIN_MEDIUM), withSkinTone(em, SKIN_DARK)};
            for (final String opt : options) {
                TextView t = new TextView(this);
                t.setText(opt);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
                t.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
                t.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(opt, 1);
                    soundController.playKeyClick();
                    keyboardPrefs.recordUsedEmoji(opt);
                    if (pw[0] != null) pw[0].dismiss();
                });
                box.addView(t);
            }
            int w = dp(48) * 3 + dp(12), h = dp(56);
            pw[0] = new android.widget.PopupWindow(box, w, h, false);
            pw[0].setOutsideTouchable(true);
            pw[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            pw[0].setElevation(dp(8));
            pw[0].showAsDropDown(anchor, (anchor.getWidth() - w) / 2, -(anchor.getHeight() + h));
        } catch (Exception e) {
            AppLogger.log("BlackBerryImeService", "showSkinTonePopup()", e);
        }
    }

    private void scrollToEmojiSection(int index) {
        if (gifPanelShown) hideGifPanel();
        if (emojiScroll == null || index < 0 || index >= emojiSectionHeaders.size()) return;
        updateEmojiTabHighlight(index);
        emojiScroll.smoothScrollTo(0, emojiSectionHeaders.get(index).getTop());
    }

    /** شريط إسناد "Powered by KLIPY" — ثابت وظاهر دائماً فوق نتائج/بحث الـ GIF، كما تشترط سياسة KLIPY.
     *  ملاحظة: نص فقط مؤقتاً بدون شعار KLIPY الرسمي — شوف إرشادات العلامة التجارية الخاصة بهم
     *  وأضف الشعار المناسب لاحقاً. */
    private View buildGifAttributionBar() {
        boolean arabic = keyboardView == null || keyboardView.isRtlScript();

        LinearLayout bar = new LinearLayout(this);
        gifAttributionBar = bar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL | (arabic ? Gravity.RIGHT : Gravity.LEFT));
        // ~25% transparent so GIFs scroll visibly behind it; the label stays fully readable.
        bar.setBackground(new android.graphics.drawable.ColorDrawable((sugBarC() & 0x00FFFFFF) | 0xBF000000));
        bar.setPadding(dp(10), 0, dp(10), 0);

        TextView label = new TextView(this);
        gifAttributionLabel = label;
        label.setText(uiText("مقدَّم من KLIPY", "Powered by KLIPY", "Ofrecido por KLIPY"));
        label.setTextColor((labelC() & 0x00FFFFFF) | 0xF2000000);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        label.setPadding(dp(6), 0, dp(6), 0);
        label.setIncludeFontPadding(false);
        bar.addView(label);

        return bar;
    }

    private void updateGifAttributionBar() {
        if (gifAttributionBar == null || gifAttributionLabel == null) return;
        boolean arabic = keyboardView == null || keyboardView.isRtlScript();
        gifAttributionBar.setGravity(Gravity.CENTER_VERTICAL | (arabic ? Gravity.RIGHT : Gravity.LEFT));
        gifAttributionLabel.setText(uiText("مقدَّم من KLIPY", "Powered by KLIPY", "Ofrecido por KLIPY"));
        if (gifKlipyPillLabel != null) gifKlipyPillLabel.setText(gifAttributionLabel.getText());
    }

    private void showGifPanel() {
        if (gifPanel == null) return;
        gifPanelShown = true;
        if (emojiContentFrameView != null) emojiContentFrameView.setVisibility(View.GONE);
        gifPanel.setVisibility(View.VISIBLE);

        if (gifTabView != null) {
            gifTabView.setAlpha(1.0f);
            gifTabView.setBackground(roundedBg(pressedC(), 8));
        }
        for (View t : emojiTabViews) {
            t.setAlpha(0.60f);
            t.setBackgroundColor(Color.TRANSPARENT);
        }
        String activeLangCode = keyboardView != null ? keyboardView.getActiveLanguageCode() : KeyboardPreferences.LANG_EN;
        if (gifLastQuery == null) {
            gifLastQuery = "";
            updateGifSearchBoxDisplay("");
            loadGifResults(true, "");
        } else if (!gifLastLoadedLangCode.equals(activeLangCode)) {
            loadGifResults(gifLastQuery.isEmpty(), gifLastQuery);
        }
    }

    private void hideGifPanel() {
        if (gifSearchFocused) closeGifSearchKeyboard(false);
        if (!gifPanelShown) return;
        gifPanelShown = false;
        if (gifPanel != null) gifPanel.setVisibility(View.GONE);
        if (emojiContentFrameView != null) emojiContentFrameView.setVisibility(View.VISIBLE);
        if (gifTabView != null) {
            gifTabView.setAlpha(0.60f);
            gifTabView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    // The search bar's colors used to be fixed once, when the keyboard view was first built, so
    // after any theme change the bar kept the old theme's background while its text followed the
    // current theme (dark text on a dark bar with the white theme, a leftover tint after
    // switching back to black). Re-applying them every time the bar opens keeps it in sync.
    private LinearLayout gifSearchRowView;
    private ModernIconView gifSearchIconView;

    /** The (non-typing) GIF search field: the suggestion-bar color at 85% opacity. */
    private int gifSearchFieldC() {
        return (sugBarC() & 0x00FFFFFF) | 0xC7000000;
    }

    private void refreshGifSearchBarColors() {
        if (gifSearchRowView != null) gifSearchRowView.setBackground(roundedBg(gifSearchFieldC(), 16));
        if (gifSearchIconView != null) gifSearchIconView.setColors(labelC(), Color.TRANSPARENT);
        if (gifKlipyPillLabel != null) gifKlipyPillLabel.setTextColor((labelC() & 0x00FFFFFF) | 0xE6000000);
        if (gifSearchClearBtn != null) gifSearchClearBtn.setTextColor(labelC());
        // The full-width strip behind the rounded search pill had no background of its own, so it
        // showed the window's old color (black) until the keyboard was closed and reopened.
        if (gifSearchBar != null) gifSearchBar.setBackgroundColor(Color.TRANSPARENT);
        if (gifBarRowView != null) gifBarRowView.setBackground(gifFloatingPillBg());
        if (gifCancelBtnView != null) gifCancelBtnView.setTextColor(labelC());
        updateGifQueryDisplay();
    }

    private void openGifSearchKeyboard() {
        if (keyboardView == null || gifSearchBar == null) return;
        gifSearchFocused = true;
        refreshGifSearchBarColors();
        gifQueryBuilder.setLength(0);
        if (gifLastQuery != null) gifQueryBuilder.append(gifLastQuery);
        updateGifQueryDisplay();

        keyboardView.exitSymbolsIfActive();
        if (emojiDrawerView != null) emojiDrawerView.setVisibility(View.GONE);
        keyboardView.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).topMargin = dp(48);
            keyboardView.setLayoutParams(lp);
        }
        gifSearchBar.setVisibility(View.VISIBLE);
        applyNavigationBarColor();
        updateOneHandedSwitchButton();
        forceWindowRepaintAfterResize();
    }

    private void closeGifSearchKeyboard(boolean runSearch) {
        if (!gifSearchFocused) return;
        gifSearchFocused = false;
        applyNavigationBarColor();
        String typed = gifQueryBuilder.toString().trim();
        if (gifSearchBar != null) gifSearchBar.setVisibility(View.GONE);
        if (keyboardView != null) {
            ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).topMargin = 0;
                keyboardView.setLayoutParams(lp);
            }
            keyboardView.setVisibility(View.GONE);
        }
        if (emojiDrawerView != null) emojiDrawerView.setVisibility(View.VISIBLE);
        if (runSearch) {
            gifLastQuery = typed;
            loadGifResults(typed.isEmpty(), typed);
        }
        updateGifSearchBoxDisplay(gifLastQuery);
        updateOneHandedSwitchButton();
        forceWindowRepaintAfterResize();
    }

    private void updateGifQueryDisplay() {
        if (gifQueryDisplay == null) return;
        String q = gifQueryBuilder.toString();
        if (q.isEmpty()) {
            gifQueryDisplay.setText(uiText("ابحث في KLIPY...", "Search KLIPY...", "Buscar en KLIPY..."));
            gifQueryDisplay.setTextColor(subC());
        } else {
            gifQueryDisplay.setText(q);
            gifQueryDisplay.setTextColor(labelC());
        }
    }

    private void updateGifSearchBoxDisplay(String query) {
        if (gifSearchBox == null) return;
        boolean hasQuery = query != null && !query.isEmpty();
        if (hasQuery) {
            gifSearchBox.setText(query);
            gifSearchBox.setTextColor(labelC());
        } else {
            gifSearchBox.setText(uiText("ابحث في KLIPY...", "Search KLIPY...", "Buscar en KLIPY..."));
            gifSearchBox.setTextColor((labelC() & 0x00FFFFFF) | 0xD0000000);
        }
        if (gifSearchClearBtn != null) gifSearchClearBtn.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
    }

    private void loadGifResults(boolean trending, String query) {
        gifLastLoadedLangCode = keyboardView != null ? keyboardView.getActiveLanguageCode() : KeyboardPreferences.LANG_EN;
        if (gifStatusText != null) {
            gifStatusText.setText(uiText("جاري التحميل...", "Loading...", "Cargando..."));
            gifStatusText.setVisibility(View.VISIBLE);
        }
        if (gifGrid != null) gifGrid.removeAllViews();
        boolean showRecents = trending || query.isEmpty();
        KlipyClient.Callback cb = (results, error) -> {
            if (gifGrid == null || gifStatusText == null) return;
            if (error != null) {
                gifStatusText.setText(uiText("تعذّر التحميل: ", "Couldn't load: ", "No se pudo cargar: ") + error);
                gifStatusText.setVisibility(View.VISIBLE);
                return;
            }
            List<KlipyClient.GifResult> recents = new ArrayList<>();
            List<KlipyClient.GifResult> main = results;
            if (showRecents) {
                List<String[]> recentPairs = keyboardPrefs.getRecentGifs();

                int recentLimit = GIF_GRID_COLUMNS * 3;
                for (int i = 0; i < recentPairs.size() && i < recentLimit; i++) {
                    String[] g = recentPairs.get(i);
                    recents.add(new KlipyClient.GifResult(g[0], g[1]));
                }
                if (!recents.isEmpty()) {
                    main = new ArrayList<>();
                    for (KlipyClient.GifResult g : results) {
                        boolean dup = false;
                        for (KlipyClient.GifResult r : recents) {
                            if (r.fullUrl.equals(g.fullUrl)) { dup = true; break; }
                        }
                        if (!dup) main.add(g);
                    }
                }
            }
            if (recents.isEmpty() && main.isEmpty()) {
                gifStatusText.setText(uiText("ما فيه نتائج", "No results", "Sin resultados"));
                gifStatusText.setVisibility(View.VISIBLE);
                return;
            }
            gifStatusText.setVisibility(View.GONE);
            fillGifSections(recents, main, !showRecents);
        };
        if (showRecents) {
            KlipyClient.trending(cb);
        } else {
            KlipyClient.search(query, cb);
        }
    }

    private static final int GIF_GRID_COLUMNS = 4;

    private void fillGifSections(List<KlipyClient.GifResult> recents, List<KlipyClient.GifResult> main, boolean isSearch) {
        gifGrid.removeAllViews();
        if (!recents.isEmpty()) {
            TextView recentHeader = gifSectionHeader(uiText("آخر ما استخدمته", "Recently used", "Usados recientemente"));
            recentHeader.append(uiText("  •  اضغط مطولاً على أي GIF لحذفه",
                    "  •  Long-press any GIF to delete it",
                    "  •  Mantén pulsado un GIF para eliminarlo"));
            gifGrid.addView(recentHeader);
            gifGrid.addView(buildGifRow(recents, true));
        }
        if (!main.isEmpty()) {
            String title = isSearch
                    ? uiText("نتائج البحث", "Search results", "Resultados de búsqueda")
                    : uiText("شائع الآن", "Trending now", "Tendencias");
            gifGrid.addView(gifSectionHeader(title));
            gifGrid.addView(buildGifRow(main, false));
        }
    }

    private TextView gifSectionHeader(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(subC());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), dp(10), dp(8), dp(4));
        return title;
    }

    private GridLayout buildGifRow(List<KlipyClient.GifResult> results, boolean isRecent) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(GIF_GRID_COLUMNS);
        grid.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int panelW = (gifPanel != null && gifPanel.getWidth() > 0) ? gifPanel.getWidth()
                : (rootContainer != null && rootContainer.getWidth() > 0 ? rootContainer.getWidth() : getResources().getDisplayMetrics().widthPixels);
        // grid padding is dp(4) on each side, so subtract exactly that: equal gutters left and right
        int cellW = Math.max(dp(64), (panelW - dp(8)) / GIF_GRID_COLUMNS);
        for (final KlipyClient.GifResult gif : results) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            // Equal-weight columns that fill the row exactly, so left and right gutters match
            // regardless of rounding or the window width.
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.height = dp(70);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            iv.setLayoutParams(lp);
            iv.setBackgroundColor(sugBarC());
            try {
                Glide.with(this).load(gif.previewUrl)
                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(160))
                        .into(iv);
            } catch (Exception e) {
                AppLogger.log("BlackBerryImeService", "buildGifRow()", e);
            }
            iv.setOnClickListener(v -> {
                keyboardPrefs.recordUsedGif(gif.previewUrl, gif.fullUrl);
                sendGif(gif.fullUrl);
            });
            if (isRecent) {
                iv.setOnLongClickListener(v -> {
                    soundController.playKeyClick();
                    keyboardPrefs.removeRecentGif(gif.fullUrl);
                    loadGifResults(true, "");
                    return true;
                });
            }
            grid.addView(iv);
        }
        // Invisible fillers so a short last row keeps the same four equal-width columns.
        int rem = results.size() % GIF_GRID_COLUMNS;
        if (rem != 0 && results.size() > 0) {
            for (int i = rem; i < GIF_GRID_COLUMNS; i++) {
                View filler = new View(this);
                GridLayout.LayoutParams flp = new GridLayout.LayoutParams();
                flp.width = 0;
                flp.height = dp(70);
                flp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                flp.setMargins(dp(3), dp(3), dp(3), dp(3));
                filler.setLayoutParams(flp);
                filler.setVisibility(View.INVISIBLE);
                grid.addView(filler);
            }
        }
        return grid;
    }

    private void sendGif(String url) {
        soundController.playKeyClick();
        ioExecutor.execute(() -> {
            String fileName = GifDownloadCache.downloadBlocking(this, url);
            uiHandler.post(() -> {
                if (fileName == null) {
                    showKbToast(uiText("تعذّر تحميل الصورة المتحركة", "Couldn't load GIF", "No se pudo cargar el GIF"));
                    return;
                }
                Uri uri = Uri.parse("content://" + GifCacheProvider.AUTHORITY + "/" + fileName);
                boolean committedInline = false;
                EditorInfo info = getCurrentInputEditorInfo();
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && info != null && info.contentMimeTypes != null && Build.VERSION.SDK_INT >= 25) {
                    for (String mime : info.contentMimeTypes) {
                        if (mime != null && (mime.equalsIgnoreCase("image/gif") || mime.equalsIgnoreCase("image/*"))) {
                            try {
                                grantUriPermission(info.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                InputContentInfo ici = new InputContentInfo(uri,
                                        new ClipDescription("GIF", new String[]{"image/gif"}), null);
                                committedInline = ic.commitContent(ici, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                            } catch (Exception e) {
                                AppLogger.log("BlackBerryImeService", "sendGif()", e);
                            }
                            break;
                        }
                    }
                }
                if (!committedInline) {
                    try {
                        ClipData cd = ClipData.newUri(getContentResolver(), "GIF", uri);
                        if (clipboardManager != null) clipboardManager.setPrimaryClip(cd);
                        showKbToast(uiText("تم نسخ الصورة المتحركة — اضغط لصق هنا",
                                "GIF copied — paste it here",
                                "GIF copiado — toca para pegarlo aquí"));
                    } catch (Exception e) {
                        AppLogger.log("BlackBerryImeService", "sendGif()", e);
                    }
                }
            });
        });
    }

    private void updateActiveEmojiTabFromScroll(int scrollY) {
        if (suppressEmojiTabScrollSync) return;
        int active = 0;
        for (int i = 0; i < emojiSectionHeaders.size(); i++) {
            if (emojiSectionHeaders.get(i).getTop() <= scrollY + dp(24)) active = i;
        }
        if (active != activeEmojiCategory) updateEmojiTabHighlight(active);
    }

    private void updateEmojiTabHighlight(int index) {
        activeEmojiCategory = index;
        for (int i = 0; i < emojiTabViews.size(); i++) {
            View tab = emojiTabViews.get(i);
            if (i == index) {
                tab.setBackground(roundedBg(pressedC(), 8));
                tab.setAlpha(1.0f);
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT);
                tab.setAlpha(0.60f);
            }
        }

        if (gifTabView != null) {
            gifTabView.setBackgroundColor(Color.TRANSPARENT);
            gifTabView.setAlpha(0.60f);
        }
    }
}
