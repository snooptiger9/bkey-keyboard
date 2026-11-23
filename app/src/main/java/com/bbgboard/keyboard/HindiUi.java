package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Hindi text for the keyboard's own drawers (clipboard, emoji, GIF), keyed by the English
 * string the drawer code already carries. Anything missing falls back to the English text, so
 * a new drawer string is never blank in Hindi, just untranslated until it is added here.
 */
final class HindiUi {
    private HindiUi() {}

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        TEXT.put("Screenshot copied — paste it here", "स्क्रीनशॉट कॉपी हो गया — यहाँ पेस्ट करें");
        TEXT.put("Delete", "हटाएँ");
        TEXT.put("Screenshot", "स्क्रीनशॉट");
        TEXT.put("Incognito mode is on\nClipboard is hidden and nothing new is being saved",
                "इनकॉग्निटो मोड चालू है\nक्लिपबोर्ड छिपा है और कुछ नया सहेजा नहीं जा रहा");
        TEXT.put("Nothing copied yet", "अभी तक कुछ कॉपी नहीं किया");
        TEXT.put("Unpin", "अनपिन करें");
        TEXT.put("Pin", "पिन करें");
        TEXT.put("Powered by KLIPY", "KLIPY द्वारा संचालित");
        TEXT.put("Search KLIPY...", "KLIPY में खोजें...");
        TEXT.put("  •  Long-press any emoji to delete it", "  •  किसी भी इमोजी को हटाने के लिए देर तक दबाएँ");
        TEXT.put("  •  Long-press any GIF to delete it", "  •  किसी भी GIF को हटाने के लिए देर तक दबाएँ");
        TEXT.put("Loading...", "लोड हो रहा है...");
        TEXT.put("Couldn't load: ", "लोड नहीं हो सका: ");
        TEXT.put("No results", "कोई परिणाम नहीं");
        TEXT.put("Recently used", "हाल में उपयोग किए गए");
        TEXT.put("Search results", "खोज परिणाम");
        TEXT.put("Trending now", "अभी ट्रेंडिंग");
        TEXT.put("This app doesn't allow sending GIFs here", "यह ऐप यहाँ GIF भेजने की अनुमति नहीं देता");
        TEXT.put("Couldn't load GIF", "GIF लोड नहीं हो सका");
        TEXT.put("GIF copied — paste it here", "GIF कॉपी हो गया — यहाँ पेस्ट करें");
    }

    /** Hindi for the given English drawer text, or the English text itself if not translated yet. */
    static String get(String english) {
        String hi = TEXT.get(english);
        return hi != null ? hi : english;
    }

    static final String[] EMOJI_CATEGORIES = {
        "हाल में उपयोग किए गए",
        "चेहरे और भाव",
        "लोग और शरीर",
        "प्रकृति, आयोजन और इस्लामी",
        "जानवर",
        "खाना और पेय",
        "यात्रा और स्थान",
        "चिह्न और झंडे"
    };
}
