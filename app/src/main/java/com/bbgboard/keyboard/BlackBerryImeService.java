package com.bbgboard.keyboard;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
    private ClipboardManager clipboardManager;

    private FrameLayout rootContainer;
    private BlackBerryKeyboardView keyboardView;

    private static final java.util.Set<String> APPEARANCE_PREF_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "theme_mode", "keyboard_style", "key_font", "enter_color",
            "key_font_scale", "keyboard_scale", "suggestion_bar_scale", "suggestion_text_level"));
    private final SharedPreferences.OnSharedPreferenceChangeListener appearanceChangeListener = (sp, key) -> {
        if (key == null || !APPEARANCE_PREF_KEYS.contains(key)) return;
        if (keyboardView != null) {
            keyboardView.reloadAppearanceFromPrefs();
            keyboardView.invalidate();
        }
        rebuildDrawersIfThemeChanged();
    };

    private FrameLayout oneHandedSwitchBtn;
    private ModernIconView oneHandedSwitchIcon;
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
    private final List<View> emojiSectionHeaders = new ArrayList<>();
    private final List<TextView> emojiTabViews = new ArrayList<>();
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
    private boolean gifLastLoadedArabic;

    private boolean gifSearchFocused = false;
    private final StringBuilder gifQueryBuilder = new StringBuilder();
    private FrameLayout gifSearchBar;
    private TextView gifQueryDisplay;
    private long lastSpaceTapTime = 0L;
    private boolean numericFieldActive = false;
    private boolean passwordFieldActive = false;

    private static final long CLIP_CHIP_MS = 60 * 1000L;
    private static final long SHOT_CHIP_MS = 80 * 1000L;
    private String clipChipText = null;
    private long clipChipTime = 0L;

    private boolean clipChipSensitive = false;
    private final Runnable clipChipExpiry = this::refreshPredictions;

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

    private String pendingOtpCode = null;
    private long pendingOtpTimestamp = 0L;
    private boolean otpReceiverRegistered = false;
    private final BroadcastReceiver smsOtpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (keyboardPrefs == null || !keyboardPrefs.isOtpSuggestEnabled() || keyboardPrefs.isIncognitoMode()) return;
            try {
                android.telephony.SmsMessage[] parts = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (parts == null || parts.length == 0) return;
                StringBuilder body = new StringBuilder();
                for (android.telephony.SmsMessage p : parts) {
                    if (p != null && p.getMessageBody() != null) body.append(p.getMessageBody());
                }
                String code = OtpDetector.extract(body.toString());
                if (code != null) {
                    pendingOtpCode = code;
                    pendingOtpTimestamp = SystemClock.elapsedRealtime();
                    refreshPredictions();
                }
            } catch (Exception ignored) {}
        }
    };

    private boolean hasValidPendingOtp() {
        return currentOtpCode() != null;
    }

    private String currentOtpCode() {
        if (keyboardPrefs != null && keyboardPrefs.isIncognitoMode()) return null;
        if (pendingOtpCode != null && (SystemClock.elapsedRealtime() - pendingOtpTimestamp) < KeyboardPreferences.OTP_VALIDITY_MS) {
            return pendingOtpCode;
        }
        return keyboardPrefs != null ? keyboardPrefs.getPendingWhatsAppOtp() : null;
    }

    private void clearPendingOtp() {
        pendingOtpCode = null;
        if (keyboardPrefs != null) keyboardPrefs.clearPendingWhatsAppOtp();
    }

    private void registerOtpReceiverIfNeeded() {
        if (otpReceiverRegistered) return;
        if (keyboardPrefs == null || !keyboardPrefs.isOtpSuggestEnabled()) return;
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            IntentFilter filter = new IntentFilter(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                registerReceiver(smsOtpReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(smsOtpReceiver, filter);
            }
            otpReceiverRegistered = true;
        } catch (Exception ignored) {}
    }

    private void unregisterOtpReceiverIfNeeded() {
        if (!otpReceiverRegistered) return;
        try {
            unregisterReceiver(smsOtpReceiver);
        } catch (Exception ignored) {}
        otpReceiverRegistered = false;
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

    private String[] emojiCatTitles() {
        return (keyboardView != null && !keyboardView.isArabic()) ? EMOJI_CAT_TITLES_EN : EMOJI_CAT_TITLES;
    }

    private static final String[] EMOJI_CAT_ICONS = new String[]{
        "🕒", "😀", "❤️", "🌙", "🐱", "☕", "🚗", "🔣"
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
                            clipChipText = copiedText;
                            clipChipSensitive = sensitive;
                            clipChipTime = SystemClock.elapsedRealtime();
                            uiHandler.removeCallbacks(clipChipExpiry);
                            uiHandler.postDelayed(clipChipExpiry, CLIP_CHIP_MS + 100L);
                            refreshPredictions();
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        keyboardPrefs = new KeyboardPreferences(this);
        keyboardPrefs.registerChangeListener(appearanceChangeListener);
        soundController = new SoundController(this, keyboardPrefs);

        predictionEngine = new PredictionEngine(this, keyboardPrefs, true);
        predictionEngine.setOnReadyListener(() -> uiHandler.post(this::refreshPredictions));
        clipboardRepo = new ClipboardRepository(this);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(clipListener);
        }
        registerOtpReceiverIfNeeded();
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
        unregisterOtpReceiverIfNeeded();
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
        } catch (Exception ignored) {}
        if (soundController != null) soundController.releaseSounds();
    }

    private static BlackBerryImeService activeInstance;

    static void notifyWhatsAppOtpDetected() {
        BlackBerryImeService instance = activeInstance;
        if (instance != null) instance.uiHandler.post(instance::refreshPredictions);
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

        gifSearchBar = new FrameLayout(this);
        gifSearchBar.setVisibility(View.GONE);
        LinearLayout gifBarRow = new LinearLayout(this);
        gifBarRow.setOrientation(LinearLayout.HORIZONTAL);
        gifBarRow.setGravity(Gravity.CENTER_VERTICAL);
        gifBarRow.setBackground(roundedBg(sugBarC(), 18));

        gifQueryDisplay = new TextView(this);
        gifQueryDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        gifQueryDisplay.setSingleLine(true);
        gifQueryDisplay.setPadding(dp(14), 0, dp(14), 0);
        gifQueryDisplay.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        gifBarRow.addView(gifQueryDisplay);

        TextView gifCancelBtn = new TextView(this);
        gifCancelBtn.setText("✕");
        gifCancelBtn.setTextColor(labelC());
        gifCancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        gifCancelBtn.setGravity(Gravity.CENTER);
        gifCancelBtn.setPadding(dp(14), 0, dp(14), 0);
        gifCancelBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            gifQueryBuilder.setLength(0);
            closeGifSearchKeyboard(true);
        });
        gifBarRow.addView(gifCancelBtn);

        FrameLayout.LayoutParams lpBarRow = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lpBarRow.setMargins(dp(8), dp(6), dp(8), dp(6));
        gifSearchBar.addView(gifBarRow, lpBarRow);
        FrameLayout.LayoutParams lpGifBar = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
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
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

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
        if (keyboardView != null) {
            keyboardView.reloadAppearanceFromPrefs();
            int actionId = info != null ? (info.imeOptions & EditorInfo.IME_MASK_ACTION) : EditorInfo.IME_ACTION_NONE;
            boolean noEnterAction = info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            keyboardView.setEnterActionIcon(noEnterAction ? EditorInfo.IME_ACTION_NONE : actionId);
        }

        if (soundController != null) soundController.ensureLoaded();
        rebuildDrawersIfThemeChanged();
        applyNavigationBarColor();
        applyOneHandedMode();
        showKeyboardMain();
        if (keyboardPrefs.isOtpSuggestEnabled()) {
            registerOtpReceiverIfNeeded();
        } else {
            unregisterOtpReceiverIfNeeded();
            pendingOtpCode = null;
        }
        if (!keyboardPrefs.isWhatsAppOtpSuggestEnabled()) {
            keyboardPrefs.clearPendingWhatsAppOtp();
        }
        refreshPredictions();
        registerShotObserverIfNeeded();
        tryCaptureScreenshotClip(false);
        setExtractViewShown(false);
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
                    if (message != null) Toast.makeText(BlackBerryImeService.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
        return voiceController;
    }

    @Override
    public void onVoiceInputRequested() {
        if (!VoiceInputController.hasPermission(this)) {

            Intent i = new Intent(this, MicPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) {}
            return;
        }
        if (!VoiceInputController.isAvailable(this)) {
            Toast.makeText(this, "الكتابة بالصوت غير متوفرة على هذا الجهاز", Toast.LENGTH_SHORT).show();
            return;
        }
        voice().toggle(keyboardView != null && keyboardView.isArabic());
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
        if (keyboardView != null) {
            keyboardView.dismissFlyingPopup();

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

        try {
            Glide.get(this).clearMemory();
        } catch (Exception ignored) {}
        super.onFinishInputView(finishingInput);
    }

    private void resetEmojiAndGifToFirstTab() {
        activeEmojiCategory = 0;
        gifPanelShown = false;
        gifSearchFocused = false;
        gifQueryBuilder.setLength(0);
        gifLastQuery = null;
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
        if (emojiScroll != null) emojiScroll.scrollTo(0, 0);
        if (gifScroll != null) gifScroll.scrollTo(0, 0);
        if (!emojiTabViews.isEmpty()) updateEmojiTabHighlight(0);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        invalidateTextCache();

        if (SystemClock.uptimeMillis() - lastCursorStepTime > CURSOR_SESSION_MS) {
            curSelStart = newSelStart;
            curSelEnd = newSelEnd;
        }
        refreshPredictions();
    }

    private void applyNavigationBarColor() {
        if (keyboardView == null) return;
        try {
            int color = keyboardView.getShellColor();
            if (rootContainer != null) rootContainer.setBackgroundColor(color);
            android.view.Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w == null) return;
            w.setNavigationBarColor(color);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color));
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (keyboardView.isLightTheme()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        } catch (Exception ignored) {}
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
        if (keyboardPrefs.isIncognitoMode()) allowed = false;
        predictionEngine.setLearningAllowed(allowed);
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
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.80f);
            lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = (mode == 1) ? Gravity.LEFT : Gravity.RIGHT;
        }
        keyboardView.setLayoutParams(lp);
        updateOneHandedSwitchButton();
    }

    private void updateOneHandedSwitchButton() {
        if (oneHandedSwitchBtn == null) return;
        int mode = keyboardPrefs.getOneHandedMode();
        boolean keyboardShown = keyboardView != null && keyboardView.getVisibility() == View.VISIBLE;
        if (mode == 0 || !keyboardShown) {
            oneHandedSwitchBtn.setVisibility(View.GONE);
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) oneHandedSwitchBtn.getLayoutParams();
        lp.gravity = Gravity.CENTER_VERTICAL | (mode == 1 ? Gravity.RIGHT : Gravity.LEFT);
        lp.rightMargin = mode == 1 ? dp(10) : 0;
        lp.leftMargin = mode == 1 ? 0 : dp(10);
        oneHandedSwitchBtn.setLayoutParams(lp);
        oneHandedSwitchIcon.setIconType(mode == 1 ? ModernIconView.ICON_FORWARD_ARROW : ModernIconView.ICON_BACK_ARROW);
        oneHandedSwitchBtn.setVisibility(View.VISIBLE);
    }

    private void matchDrawerHeightToKeyboard(View drawer) {
        if (keyboardView == null || drawer == null) return;
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
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dp(slideDp));
        v.animate().alpha(1f).translationY(0f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void showKeyboardMain() {
        if (keyboardView == null) return;
        if (gifSearchFocused) closeGifSearchKeyboard(false);
        if (gifPanelShown) hideGifPanel();
        boolean wasDrawer = clipboardDrawerView.getVisibility() == View.VISIBLE || emojiDrawerView.getVisibility() == View.VISIBLE;
        keyboardView.setVisibility(View.VISIBLE);
        clipboardDrawerView.setVisibility(View.GONE);
        emojiDrawerView.setVisibility(View.GONE);
        if (wasDrawer) fadeIn(keyboardView, 0);
        updateInlineStripVisibility();
        updateOneHandedSwitchButton();
    }

    private void openAppSettingsActivity() {
        try {
            showKeyboardMain();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onToggleClipboardMenu() {
        if (gifSearchFocused) closeGifSearchKeyboard(false);
        if (gifPanelShown) hideGifPanel();

        updateAbcBackLabels(keyboardView == null || keyboardView.isArabic());
        refreshClipboardDrawerList();
        matchDrawerHeightToKeyboard(clipboardDrawerView);
        refreshBottomBarGeometry();
        keyboardView.setVisibility(View.GONE);
        emojiDrawerView.setVisibility(View.GONE);
        clipboardDrawerView.setVisibility(View.VISIBLE);
        fadeIn(clipboardDrawerView, 10);
        updateInlineStripVisibility();
        updateOneHandedSwitchButton();
        tryCaptureScreenshotClip(true);
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
        } catch (Exception ignored) {}
    }

    private void unregisterShotObserver() {
        uiHandler.removeCallbacks(shotObserverRunnable);
        if (!shotObserverRegistered) return;
        try {
            getContentResolver().unregisterContentObserver(shotObserver);
        } catch (Exception ignored) {}
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
                            screenshotChipTime = SystemClock.elapsedRealtime();
                            uiHandler.removeCallbacks(clipChipExpiry);
                            uiHandler.postDelayed(clipChipExpiry, SHOT_CHIP_MS + 100L);
                            refreshPredictions();
                        }
                        if (clipboardDrawerView != null && clipboardDrawerView.getVisibility() == View.VISIBLE) {
                            refreshClipboardDrawerList();
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void useScreenshotClip() {
        if (!ScreenshotClip.exists(this)) return;
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
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
        if (!committedInline) {
            try {
                ClipData cd = ClipData.newUri(getContentResolver(), "Screenshot", uri);
                if (clipboardManager != null) clipboardManager.setPrimaryClip(cd);
                boolean arabicToast = keyboardView == null || keyboardView.isArabic();
                Toast.makeText(this, arabicToast ? "تم نسخ لقطة الشاشة — اضغط لصق هنا" : "Screenshot copied — paste it here", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        }
        soundController.playKeyClick();
        showKeyboardMain();
    }

    private View buildScreenshotThumbRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final boolean arabicUi = keyboardView == null || keyboardView.isArabic();
        row.setLayoutDirection(arabicUi ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cardBg = roundedBg(keyBgC(), 14);
        cardBg.setStroke(dp(1), keyDividerC());
        row.setBackground(cardBg);

        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(9);
        row.setLayoutParams(lp);

        ImageView thumb = new ImageView(this);
        int side = dp(52);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(side, side));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(roundedBg(shellC(), 10));
        try {
            Bitmap bmp = BitmapFactory.decodeFile(ScreenshotClip.file(this).getAbsolutePath());
            if (bmp != null) thumb.setImageBitmap(bmp);
        } catch (Exception ignored) {}

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpText.leftMargin = dp(10);
        lpText.rightMargin = dp(10);
        textBox.setLayoutParams(lpText);

        TextView t1 = new TextView(this);
        t1.setText(arabicUi ? "آخر لقطة شاشة" : "Last screenshot");
        t1.setTextColor(labelC());
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);

        TextView t2 = new TextView(this);
        t2.setText(arabicUi ? "اضغط للنسخ والإرسال" : "Tap to copy and send");
        t2.setTextColor(subC());
        t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);

        textBox.addView(t1);
        textBox.addView(t2);

        ModernIconView delBtn = new ModernIconView(this, ModernIconView.ICON_CLOSE,
                Color.parseColor("#F87171"), Color.TRANSPARENT);
        delBtn.setStyle(0.85f, 2.0f);
        delBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        delBtn.setOnClickListener(v -> {
            ScreenshotClip.clear(this);
            refreshClipboardDrawerList();
        });

        row.setOnClickListener(v -> useScreenshotClip());
        row.addView(thumb);
        row.addView(textBox);
        row.addView(delBtn);
        return row;
    }

    @Override
    public void onToggleEmoji() {
        if (gifSearchFocused) closeGifSearchKeyboard(false);
        refreshBottomBarGeometry();

        suppressEmojiTabScrollSync = true;
        rebuildEmojiSections();
        updateAbcBackLabels(keyboardView == null || keyboardView.isArabic());
        if (emojiScroll != null) emojiScroll.scrollTo(0, 0);
        matchDrawerHeightToKeyboard(emojiDrawerView);
        keyboardView.setVisibility(View.GONE);
        clipboardDrawerView.setVisibility(View.GONE);
        emojiDrawerView.setVisibility(View.VISIBLE);
        fadeIn(emojiDrawerView, 10);
        updateInlineStripVisibility();
        updateOneHandedSwitchButton();
    }

    @Override
    public void onLanguageChanged(boolean isArabic) {
        refreshPredictions();

        if (emojiDrawerView != null && emojiDrawerView.getVisibility() == View.VISIBLE) {
            rebuildEmojiSections();
        }
        updateAbcBackLabels(isArabic);

        updateGifSearchBoxDisplay(gifLastQuery);
        if (gifSearchFocused) {
            updateGifQueryDisplay();
        } else if (gifPanelShown && gifLastQuery != null) {
            loadGifResults(gifLastQuery.isEmpty(), gifLastQuery);
        }
    }

    private void updateAbcBackLabels(boolean isArabic) {
        String label = isArabic ? "أبجد" : "ABC";
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
        return keyboardView != null && keyboardView.isArabic();
    }

    @Override
    public void onEmojiSuggestionAccepted(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || emoji == null) return;

        CharSequence beforeCs = textBefore(ic, 1);
        String before = beforeCs != null ? beforeCs.toString() : "";
        String prefixSpace = (!before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1))) ? " " : "";
        ic.commitText(prefixSpace + emoji + " ", 1);

        refreshPredictions();
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

        if (hasValidPendingOtp() && ctx[0].isEmpty()) {

            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", currentOtpCode(), ""), 3);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (hasValidShotChip() && ctx[0].isEmpty()) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", keyboardView.isArabic() ? "لقطة شاشة" : "Screenshot", ""), 2);
            keyboardView.setEmojiKeySuggestion(null);
            return;
        }
        if (hasValidClipChip() && ctx[0].isEmpty()) {
            keyboardView.setToolbarIdleMode(false);
            keyboardView.setSuggestions(Arrays.asList("", clipChipDisplay(clipChipText), ""), true);
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

        String emojiSuggestion = !ctx[0].isEmpty() ? EmojiSuggester.suggestFor(ctx[0])
                : (ctx[1] != null && !ctx[1].isEmpty() ? EmojiSuggester.suggestFor(ctx[1]) : null);
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
        } catch (java.util.concurrent.RejectedExecutionException ignored) {}
    }

    @Override
    public void onCharacterTyped(String text) {
        if (gifSearchFocused) {
            gifQueryBuilder.append(text);
            updateGifQueryDisplay();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] ctx = getTypingContext();
        String currentPrefix = ctx[0];
        String prevWord = ctx[1];
        String prevWord2 = ctx[2];

        clearClipChip();

        if (hasValidPendingOtp() && !" ".equals(text)) {
            clearPendingOtp();
        }

        if (" ".equals(text)) {
            long now = SystemClock.uptimeMillis();
            if (!currentPrefix.isEmpty() && keyboardPrefs.isAutoExpandShortcuts()) {
                String expanded = keyboardPrefs.findShortcutExpansion(currentPrefix);
                if (expanded != null && !expanded.isEmpty()) {
                    ic.deleteSurroundingText(currentPrefix.length(), 0);
                    ic.commitText(expanded + " ", 1);
                    predictionEngine.learnFullSentence(expanded);
                    lastSpaceTapTime = 0L;
                    refreshPredictions();
                    return;
                }
            }
            if (currentPrefix.isEmpty() && keyboardPrefs.isDoubleSpacePeriod() && (now - lastSpaceTapTime < 320L)) {
                CharSequence beforeTwo = textBefore(ic, 2);
                if (beforeTwo != null && beforeTwo.length() >= 2 && beforeTwo.charAt(1) == ' ' && !Character.isWhitespace(beforeTwo.charAt(0))) {
                    ic.deleteSurroundingText(1, 0);
                    ic.commitText(". ", 1);
                    lastSpaceTapTime = 0L;
                    refreshPredictions();
                    return;
                }
            }

            if (!currentPrefix.isEmpty() && keyboardPrefs.isAutoCorrectEnabled() && currentPrefix.indexOf('@') < 0
                    && !currentPrefix.equalsIgnoreCase(rejectedAutoCorrectWord)) {
                String correction = predictionEngine.findAutoCorrection(currentPrefix, keyboardView.isArabic());
                if (correction != null) {
                    ic.deleteSurroundingText(currentPrefix.length(), 0);
                    ic.commitText(correction + " ", 1);
                    predictionEngine.learnWordAndTransition(correction, prevWord, prevWord2, keyboardView.isArabic());
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
            lastAutoCorrectOriginal = null;
            lastAutoCorrectResult = null;
        }

        if (" ".equals(text) || ".".equals(text) || "؟".equals(text) || "،".equals(text) || "!".equals(text)) {
            boolean midEmail = ".".equals(text) && currentPrefix.indexOf('@') > 0;
            if (!currentPrefix.isEmpty() && !midEmail) {
                predictionEngine.learnWordAndTransition(currentPrefix, prevWord, prevWord2, keyboardView.isArabic());
            }
        }

        ic.commitText(text, 1);
        refreshPredictions();
    }

    @Override
    public void onBackspaceSingle() {
        if (gifSearchFocused) {
            int len = gifQueryBuilder.length();
            if (len > 0) gifQueryBuilder.deleteCharAt(len - 1);
            updateGifQueryDisplay();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        } else if (lastAutoCorrectResult != null && undoAutoCorrectIfImmediate(ic)) {

        } else {
            int len = lastGraphemeLength(ic);
            ic.deleteSurroundingText(len, 0);
        }
        lastAutoCorrectOriginal = null;
        lastAutoCorrectResult = null;
        refreshPredictions();
    }

    private boolean undoAutoCorrectIfImmediate(InputConnection ic) {
        String expected = lastAutoCorrectResult + " ";
        CharSequence before = textBefore(ic, expected.length());
        if (before == null || !expected.equals(before.toString())) return false;
        ic.deleteSurroundingText(expected.length(), 0);
        ic.commitText(lastAutoCorrectOriginal, 1);
        rejectedAutoCorrectWord = lastAutoCorrectOriginal;
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
            ic.commitText("", 1);
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
            ic.deleteSurroundingText(deleteLength, 0);
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
        } catch (Exception ignored) {
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
            } catch (Exception ignored) {
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
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
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void onSwipeUpSuggestion(int zoneIndex) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] ctx = getTypingContext();
        String currentPrefix = ctx[0];
        String lastCompletedWord = ctx[1];
        String lastCompletedWord2 = ctx[2];

        if (hasValidPendingOtp() && currentPrefix.isEmpty()) {

            if (zoneIndex == 1) {
                ic.commitText(currentOtpCode(), 1);
                clearPendingOtp();
                refreshPredictions();
            }
            return;
        }

        if (hasValidShotChip() && currentPrefix.isEmpty()) {
            if (zoneIndex == 1) {
                clearClipChip();
                useScreenshotClip();
                refreshPredictions();
            }
            return;
        }

        if (hasValidClipChip() && currentPrefix.isEmpty()) {

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
        } else {
            CharSequence beforeCs = textBefore(ic, 2);
            String before = beforeCs != null ? beforeCs.toString() : "";
            String prefixSpace = (!before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1))) ? " " : "";
            ic.commitText(prefixSpace + chosen + " ", 1);
            if (learnableChoice) predictionEngine.learnWordAndTransition(chosen, lastCompletedWord, lastCompletedWord2, keyboardView.isArabic());
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
            keyboardPrefs.dismissDefaultWord(isArabic, word);
            refreshPredictions();
        }
    }

    @Override
    public void onEnterKey() {
        if (gifSearchFocused) {
            closeGifSearchKeyboard(true);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] ctx = getTypingContext();
        if (!ctx[0].isEmpty()) {
            predictionEngine.learnWordAndTransition(ctx[0], ctx[1], ctx[2], keyboardView.isArabic());
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

    private int floatingPillC() {
        int rgb = keyboardPrefs != null ? keyboardPrefs.getFloatingCapsuleColor() : 0x000000;
        if (rgb == KeyboardPreferences.FLOATING_CAPSULE_FOLLOW_LABEL) rgb = labelC() & 0x00FFFFFF;
        int percent = keyboardPrefs != null ? keyboardPrefs.getFloatingCapsuleOpacity() : 85;
        int alpha = Math.round(255 * percent / 100f);
        return (rgb & 0x00FFFFFF) | (alpha << 24);
    }

    private android.graphics.drawable.Drawable buildFloatingCapsuleDrawable() {
        int percent = keyboardPrefs != null ? keyboardPrefs.getFloatingCapsuleOpacity() : 85;
        int colorAlpha = Math.round(255 * percent / 100f);
        int blackAlpha = 255 - colorAlpha;

        android.graphics.drawable.GradientDrawable blackLayer = new android.graphics.drawable.GradientDrawable();
        blackLayer.setColor((0x000000) | (blackAlpha << 24));
        blackLayer.setCornerRadius(dp(40));

        android.graphics.drawable.GradientDrawable colorLayer = new android.graphics.drawable.GradientDrawable();
        colorLayer.setColor(floatingPillC());
        colorLayer.setCornerRadius(dp(40));

        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{blackLayer, colorLayer});
        return new android.graphics.drawable.InsetDrawable(layers, dp(3), dp(6), dp(3), dp(6));
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
        abcLabel.setText((keyboardView == null || keyboardView.isArabic()) ? "أبجد" : "ABC");
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
        lpTitleBox.leftMargin = dp(8);
        lpTitleBox.rightMargin = dp(6);
        titleBox.setLayoutParams(lpTitleBox);

        titleBox.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        titleBox.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        ModernIconView clipTitleIcon = new ModernIconView(this, ModernIconView.ICON_CLIPBOARD_PAD,
                labelC(), Color.TRANSPARENT);
        LinearLayout.LayoutParams lpClipIcon = new LinearLayout.LayoutParams(dp(28), dp(28));
        lpClipIcon.rightMargin = dp(6);
        clipTitleIcon.setLayoutParams(lpClipIcon);
        titleRow.addView(clipTitleIcon);

        clipCountTv = new TextView(this);
        clipCountTv.setTextColor(labelC());
        clipCountTv.setTypeface(Typeface.DEFAULT_BOLD);
        clipCountTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        clipCountTv.setGravity(Gravity.LEFT);
        titleRow.addView(clipCountTv);

        titleBox.addView(titleRow);

        ModernIconView settingsIconBtn = new ModernIconView(this, ModernIconView.ICON_SETTINGS_GEAR,
                labelC(), Color.TRANSPARENT);
        settingsIconBtn.setStyle(0.9f, 2.0f);
        LinearLayout.LayoutParams lpSet = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpSet.leftMargin = dp(10);
        lpSet.rightMargin = dp(10);
        settingsIconBtn.setLayoutParams(lpSet);
        settingsIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            openAppSettingsActivity();
        });

        ModernIconView micIconBtn = new ModernIconView(this, ModernIconView.ICON_MIC,
                labelC(), Color.TRANSPARENT);
        micIconBtn.setStyle(0.9f, 2.0f);
        LinearLayout.LayoutParams lpMic = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpMic.leftMargin = dp(10);
        lpMic.rightMargin = dp(10);
        micIconBtn.setLayoutParams(lpMic);
        micIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
            onVoiceInputRequested();
        });

        ModernIconView resizeIconBtn = new ModernIconView(this, ModernIconView.ICON_RESIZE_HEIGHT,
                labelC(), Color.TRANSPARENT);
        resizeIconBtn.setStyle(0.9f, 2.0f);
        LinearLayout.LayoutParams lpRes = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpRes.leftMargin = dp(10);
        lpRes.rightMargin = dp(10);
        resizeIconBtn.setLayoutParams(lpRes);
        resizeIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
            keyboardView.setResizeDragMode(!keyboardView.isResizeDragMode());
        });

        ModernIconView backIconBtn = new ModernIconView(this, ModernIconView.ICON_FORWARD_ARROW,
                labelC(), pillC());
        backIconBtn.setPillBackground(true);
        backIconBtn.setStyle(0.85f, 2.3f);
        LinearLayout.LayoutParams lpBackClip = new LinearLayout.LayoutParams(dp(62), dp(42));

        lpBackClip.leftMargin = dp(30);
        backIconBtn.setLayoutParams(lpBackClip);
        backIconBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            showKeyboardMain();
        });

        header.addView(titleBox);
        header.addView(settingsIconBtn);
        header.addView(micIconBtn);
        header.addView(resizeIconBtn);
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

        boolean arabicUi = keyboardView == null || keyboardView.isArabic();

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
            incogTv.setText(arabicUi
                    ? "وضع التخفي مفعّل\nالحافظة مخفية الآن ولا تُحفظ عناصر جديدة"
                    : "Incognito mode is on\nClipboard is hidden and nothing new is being saved");
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
        if (hasShotThumb) {
            clipsListContainer.addView(buildScreenshotThumbRow());
        }

        List<ClipboardRepository.ClipEntry> clips = clipboardRepo.getClips();
        if (clipCountTv != null) {
            if (clips.isEmpty()) {
                clipCountTv.setText("");
            } else {
                clipCountTv.setText(arabicUi
                        ? clips.size() + " عنصر"
                        : clips.size() + (clips.size() == 1 ? " item" : " items"));
            }
        }
        if (clips.isEmpty()) {
            if (hasShotThumb) return;
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(26), dp(16), dp(26));

            ModernIconView emptyIcon = new ModernIconView(this, ModernIconView.ICON_CLIPBOARD_PAD,
                    subC(), Color.TRANSPARENT);
            emptyIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(54)));

            TextView emptyTv = new TextView(this);
            emptyTv.setText(arabicUi
                    ? "ما فيه شي منسوخ بعد\nأي شي تنسخه بيظهر هنا"
                    : "Nothing copied yet\nAnything you copy will show up here");
            emptyTv.setTextColor(subC());
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            emptyTv.setGravity(Gravity.CENTER);
            emptyTv.setPadding(0, dp(6), 0, 0);

            empty.addView(emptyIcon);
            empty.addView(emptyTv);
            clipsListContainer.addView(empty);
            return;
        }

        for (int i = 0; i < clips.size(); i += 2) {
            LinearLayout pairRow = new LinearLayout(this);
            pairRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pairLp.bottomMargin = dp(7);
            pairRow.setLayoutParams(pairLp);

            View cardA = buildClipCard(clips.get(i));
            LinearLayout.LayoutParams cardALp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cardALp.setMarginEnd(dp(10));
            cardA.setLayoutParams(cardALp);
            pairRow.addView(cardA);

            if (i + 1 < clips.size()) {
                View cardB = buildClipCard(clips.get(i + 1));
                LinearLayout.LayoutParams cardBLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cardBLp.setMarginStart(dp(10));
                cardB.setLayoutParams(cardBLp);
                pairRow.addView(cardB);
            } else {
                android.widget.Space filler = new android.widget.Space(this);
                filler.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                pairRow.addView(filler);
            }

            clipsListContainer.addView(pairRow);
        }
    }

    private View buildClipCard(final ClipboardRepository.ClipEntry clip) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final boolean arabicUi = keyboardView == null || keyboardView.isArabic();

        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable cardBg = roundedBg(keyBgC(), 14);
        cardBg.setStroke(dp(1), clip.pinned ? keyOutlineC() : keyDividerC());
        row.setBackground(cardBg);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));

        ModernIconView pinBtn = new ModernIconView(this, ModernIconView.ICON_PUSHPIN,
                clip.pinned ? Color.parseColor("#60A5FA") : subC(), Color.TRANSPARENT);
        pinBtn.setFilled(clip.pinned);
        pinBtn.setStyle(0.8f, 1.9f);
        pinBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        pinBtn.setOnClickListener(v -> {
            clipboardRepo.togglePin(clip.text);
            refreshClipboardDrawerList();
        });

        TextView textTv = new TextView(this);
        textTv.setText(clip.text);
        textTv.setTextColor(labelC());
        textTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        textTv.setGravity((arabicUi ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textTv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        textTv.setMaxLines(3);
        textTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textTv.setPadding(dp(4), dp(6), dp(6), dp(6));
        textTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textTv.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(clip.text + " ", 1);
            soundController.playKeyClick();
            showKeyboardMain();
        });

        ModernIconView delBtn = new ModernIconView(this, ModernIconView.ICON_CLOSE,
                Color.parseColor("#F87171"), Color.TRANSPARENT);
        delBtn.setStyle(0.8f, 1.9f);
        delBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        delBtn.setOnClickListener(v -> {
            clipboardRepo.removeClip(clip.text);
            refreshClipboardDrawerList();
        });

        row.addView(pinBtn);
        row.addView(textTv);
        row.addView(delBtn);
        return row;
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
        for (int i = 0; i < EMOJI_CAT_ICONS.length; i++) {
            final int catIdx = i;
            TextView tab = new TextView(this);
            tab.setText(EMOJI_CAT_ICONS[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            tab.setPadding(dp(2), dp(5), dp(2), dp(5));
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

        TextView gifTab = new TextView(this);
        gifTab.setText("GIF");
        gifTab.setGravity(Gravity.CENTER);
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
            showGifPanel();
        });
        tabsRow.addView(gifTab);

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

        LinearLayout gifSearchRow = new LinearLayout(this);
        gifSearchRow.setOrientation(LinearLayout.HORIZONTAL);
        gifSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        gifSearchRow.setBackground(roundedBg(sugBarC(), 18));
        LinearLayout.LayoutParams lpSearchRow = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        lpSearchRow.setMargins(dp(8), dp(6), dp(8), dp(6));
        gifSearchRow.setLayoutParams(lpSearchRow);

        gifSearchBox = new TextView(this);
        gifSearchBox.setText(gifArabic ? "ابحث عن GIF..." : "Search GIFs...");
        gifSearchBox.setSingleLine(true);
        gifSearchBox.setTextColor(subC());
        gifSearchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        gifSearchBox.setGravity(Gravity.CENTER_VERTICAL | (gifArabic ? Gravity.RIGHT : Gravity.LEFT));
        gifSearchBox.setPadding(dp(12), dp(6), dp(12), dp(6));
        gifSearchBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        gifSearchBox.setOnClickListener(v -> {
            soundController.playKeyClick();
            openGifSearchKeyboard();
        });
        gifSearchRow.addView(gifSearchBox);

        gifSearchClearBtn = new TextView(this);
        gifSearchClearBtn.setText("✕");
        gifSearchClearBtn.setTextColor(labelC());
        gifSearchClearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        gifSearchClearBtn.setGravity(Gravity.CENTER);
        gifSearchClearBtn.setPadding(dp(12), 0, dp(12), 0);
        gifSearchClearBtn.setVisibility(View.GONE);

        gifSearchClearBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            gifLastQuery = "";
            updateGifSearchBoxDisplay("");
            loadGifResults(true, "");
        });
        gifSearchRow.addView(gifSearchClearBtn);

        gifCol.addView(gifSearchRow);

        gifStatusText = new TextView(this);
        gifStatusText.setGravity(Gravity.CENTER);
        gifStatusText.setTextColor(subC());
        gifStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        gifStatusText.setPadding(dp(16), dp(24), dp(16), dp(16));
        gifStatusText.setVisibility(View.GONE);
        gifCol.addView(gifStatusText);

        gifScroll = new ScrollView(this);
        gifScroll.setVerticalScrollBarEnabled(false);
        gifScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        gifGrid = new LinearLayout(this);
        gifGrid.setOrientation(LinearLayout.VERTICAL);
        gifGrid.setPadding(dp(4), dp(2), dp(4), dp(56));
        gifScroll.addView(gifGrid);
        gifCol.addView(gifScroll);

        gifPanel.addView(gifCol);
        container.addView(gifPanel);

        rebuildEmojiSections();
        return container;
    }

    private void rebuildEmojiSections() {
        if (emojiSectionsHost == null) return;
        emojiSectionsHost.removeAllViews();
        emojiSectionHeaders.clear();

        String[] catTitles = emojiCatTitles();
        boolean emojiArabic = keyboardView == null || keyboardView.isArabic();
        for (int c = 0; c < catTitles.length; c++) {
            TextView title = new TextView(this);
            String longPressHint = emojiArabic ? "  •  اضغط مطولاً على أي رمز لحذفه" : "  •  Long-press any emoji to delete it";
            title.setText(c == 0 ? catTitles[0] + longPressHint : catTitles[c]);
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
        }
        updateEmojiTabHighlight(0);
    }

    private void fillEmojiGrid(final GridLayout grid, List<String> emojis, final boolean recent) {
        grid.removeAllViews();
        int cellW = emojiGridCellW > 0 ? emojiGridCellW
                : Math.max(dp(38), (getResources().getDisplayMetrics().widthPixels - dp(20)) / 8);
        for (final String em : emojis) {
            TextView tv = new TextView(this);
            tv.setText(em);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, keyboardPrefs.getEmojiSizeSp());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellW;
            lp.height = dp(Math.max(42, keyboardPrefs.getEmojiSizeSp() + 19));
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
                    return true;
                });
            }
            grid.addView(tv);
        }
    }

    private void scrollToEmojiSection(int index) {
        if (gifPanelShown) hideGifPanel();
        if (emojiScroll == null || index < 0 || index >= emojiSectionHeaders.size()) return;
        updateEmojiTabHighlight(index);
        emojiScroll.smoothScrollTo(0, emojiSectionHeaders.get(index).getTop());
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
        for (TextView t : emojiTabViews) {
            t.setAlpha(0.60f);
            t.setBackgroundColor(Color.TRANSPARENT);
        }
        boolean gifArabic = keyboardView == null || keyboardView.isArabic();
        if (gifLastQuery == null) {
            gifLastQuery = "";
            loadGifResults(true, "");
        } else if (gifLastLoadedArabic != gifArabic) {

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

    private void openGifSearchKeyboard() {
        if (keyboardView == null || gifSearchBar == null) return;
        gifSearchFocused = true;
        gifQueryBuilder.setLength(0);
        if (gifLastQuery != null) gifQueryBuilder.append(gifLastQuery);
        updateGifQueryDisplay();

        keyboardView.exitSymbolsIfActive();
        if (emojiDrawerView != null) emojiDrawerView.setVisibility(View.GONE);
        keyboardView.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = keyboardView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).topMargin = dp(56);
            keyboardView.setLayoutParams(lp);
        }
        gifSearchBar.setVisibility(View.VISIBLE);
        updateOneHandedSwitchButton();
    }

    private void closeGifSearchKeyboard(boolean runSearch) {
        if (!gifSearchFocused) return;
        gifSearchFocused = false;
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
    }

    private void updateGifQueryDisplay() {
        if (gifQueryDisplay == null) return;
        boolean gifArabic = keyboardView == null || keyboardView.isArabic();
        String q = gifQueryBuilder.toString();
        if (q.isEmpty()) {
            gifQueryDisplay.setText(gifArabic ? "ابحث عن GIF..." : "Search GIFs...");
            gifQueryDisplay.setTextColor(subC());
        } else {
            gifQueryDisplay.setText(q);
            gifQueryDisplay.setTextColor(labelC());
        }
    }

    private void updateGifSearchBoxDisplay(String query) {
        if (gifSearchBox == null) return;
        boolean gifArabic = keyboardView == null || keyboardView.isArabic();
        boolean hasQuery = query != null && !query.isEmpty();
        if (hasQuery) {
            gifSearchBox.setText(query);
            gifSearchBox.setTextColor(labelC());
        } else {
            gifSearchBox.setText(gifArabic ? "ابحث عن GIF..." : "Search GIFs...");
            gifSearchBox.setTextColor(subC());
        }
        if (gifSearchClearBtn != null) gifSearchClearBtn.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
    }

    private void loadGifResults(boolean trending, String query) {
        boolean gifArabic = keyboardView == null || keyboardView.isArabic();
        gifLastLoadedArabic = gifArabic;
        String apiKey = keyboardPrefs.getGiphyApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            if (gifGrid != null) gifGrid.removeAllViews();
            if (gifStatusText != null) {
                gifStatusText.setText(gifArabic
                        ? "لازم تضيف مفتاح GIPHY API مجاني من إعدادات GIF حتى تشتغل هذي الميزة"
                        : "Add a free GIPHY API key in GIF settings to use this feature");
                gifStatusText.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (gifStatusText != null) {
            gifStatusText.setText(gifArabic ? "جاري التحميل..." : "Loading...");
            gifStatusText.setVisibility(View.VISIBLE);
        }
        if (gifGrid != null) gifGrid.removeAllViews();
        boolean showRecents = trending || query.isEmpty();
        GiphyClient.Callback cb = (results, error) -> {
            if (gifGrid == null || gifStatusText == null) return;
            if (error != null) {
                gifStatusText.setText((gifArabic ? "تعذّر التحميل: " : "Couldn't load: ") + error);
                gifStatusText.setVisibility(View.VISIBLE);
                return;
            }
            List<GiphyClient.GifResult> recents = new ArrayList<>();
            List<GiphyClient.GifResult> main = results;
            if (showRecents) {
                List<String[]> recentPairs = keyboardPrefs.getRecentGifs();

                int recentLimit = GIF_GRID_COLUMNS * 3;
                for (int i = 0; i < recentPairs.size() && i < recentLimit; i++) {
                    String[] g = recentPairs.get(i);
                    recents.add(new GiphyClient.GifResult(g[0], g[1]));
                }
                if (!recents.isEmpty()) {
                    main = new ArrayList<>();
                    for (GiphyClient.GifResult g : results) {
                        boolean dup = false;
                        for (GiphyClient.GifResult r : recents) {
                            if (r.fullUrl.equals(g.fullUrl)) { dup = true; break; }
                        }
                        if (!dup) main.add(g);
                    }
                }
            }
            if (recents.isEmpty() && main.isEmpty()) {
                gifStatusText.setText(gifArabic ? "ما فيه نتائج" : "No results");
                gifStatusText.setVisibility(View.VISIBLE);
                return;
            }
            gifStatusText.setVisibility(View.GONE);
            fillGifSections(recents, main, !showRecents, gifArabic);
        };
        if (showRecents) {
            GiphyClient.trending(apiKey, cb);
        } else {
            GiphyClient.search(apiKey, query, cb);
        }
    }

    private static final int GIF_GRID_COLUMNS = 4;

    private void fillGifSections(List<GiphyClient.GifResult> recents, List<GiphyClient.GifResult> main, boolean isSearch, boolean gifArabic) {
        gifGrid.removeAllViews();
        if (!recents.isEmpty()) {
            TextView recentHeader = gifSectionHeader(gifArabic ? "آخر ما استخدمته" : "Recently used");
            recentHeader.append(gifArabic ? "  •  اضغط مطولاً على أي GIF لحذفه" : "  •  Long-press any GIF to delete it");
            gifGrid.addView(recentHeader);
            gifGrid.addView(buildGifRow(recents, true));
        }
        if (!main.isEmpty()) {
            String title = isSearch ? (gifArabic ? "نتائج البحث" : "Search results") : (gifArabic ? "شائع الآن" : "Trending now");
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

    private GridLayout buildGifRow(List<GiphyClient.GifResult> results, boolean isRecent) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(GIF_GRID_COLUMNS);
        int cellW = Math.max(dp(64), (getResources().getDisplayMetrics().widthPixels - dp(24)) / GIF_GRID_COLUMNS);
        for (final GiphyClient.GifResult gif : results) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellW - dp(6);
            lp.height = dp(78);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            iv.setLayoutParams(lp);
            iv.setBackgroundColor(sugBarC());
            try {
                Glide.with(this).load(gif.previewUrl).into(iv);
            } catch (Exception ignored) {}
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
        return grid;
    }

    private void sendGif(String url) {
        soundController.playKeyClick();
        ioExecutor.execute(() -> {
            String fileName = GifDownloadCache.downloadBlocking(this, url);
            uiHandler.post(() -> {
                boolean arabicToast = keyboardView == null || keyboardView.isArabic();
                if (fileName == null) {
                    Toast.makeText(this, arabicToast ? "تعذّر تحميل الصورة المتحركة" : "Couldn't load GIF", Toast.LENGTH_SHORT).show();
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
                            } catch (Exception ignored) {}
                            break;
                        }
                    }
                }
                if (!committedInline) {
                    try {
                        ClipData cd = ClipData.newUri(getContentResolver(), "GIF", uri);
                        if (clipboardManager != null) clipboardManager.setPrimaryClip(cd);
                        Toast.makeText(this, arabicToast ? "تم نسخ الصورة المتحركة — اضغط لصق هنا" : "GIF copied — paste it here", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
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
            TextView tab = emojiTabViews.get(i);
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
