package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Urdu text for the keyboard's own drawers (clipboard, emoji, GIF), keyed by the English
 * string the drawer code already carries. Anything missing falls back to the English text.
 */
final class UrduUi {
    private UrduUi() {}

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        TEXT.put("Screenshot copied — paste it here", "اسکرین شاٹ کاپی ہو گیا — یہاں پیسٹ کریں");
        TEXT.put("Delete", "حذف کریں");
        TEXT.put("Screenshot", "اسکرین شاٹ");
        TEXT.put("Incognito mode is on\nClipboard is hidden and nothing new is being saved",
                "انکوگنیٹو موڈ آن ہے\nکلپ بورڈ چھپا ہوا ہے اور کچھ نیا محفوظ نہیں ہو رہا");
        TEXT.put("Nothing copied yet", "ابھی تک کچھ کاپی نہیں ہوا");
        TEXT.put("Unpin", "ان پن کریں");
        TEXT.put("Pin", "پن کریں");
        TEXT.put("Powered by KLIPY", "KLIPY کے تعاون سے");
        TEXT.put("Search KLIPY...", "KLIPY میں تلاش کریں...");
        TEXT.put("No recent emojis yet", "ابھی تک کوئی حالیہ ایموجی نہیں");
        TEXT.put("  •  Long-press any emoji to delete it", "  •  کسی بھی ایموجی کو حذف کرنے کے لیے دیر تک دبائیں");
        TEXT.put("  •  Long-press any GIF to delete it", "  •  کسی بھی GIF کو حذف کرنے کے لیے دیر تک دبائیں");
        TEXT.put("Loading...", "لوڈ ہو رہا ہے...");
        TEXT.put("Couldn't load: ", "لوڈ نہیں ہو سکا: ");
        TEXT.put("No results", "کوئی نتیجہ نہیں");
        TEXT.put("Recently used", "حال ہی میں استعمال شدہ");
        TEXT.put("Search results", "تلاش کے نتائج");
        TEXT.put("Trending now", "ابھی مقبول");
        TEXT.put("This app doesn't allow sending GIFs here", "یہ ایپ یہاں GIF بھیجنے کی اجازت نہیں دیتی");
        TEXT.put("This app doesn't allow sending screenshots here", "یہ ایپ یہاں اسکرین شاٹ بھیجنے کی اجازت نہیں دیتی");
        TEXT.put("Couldn't load GIF", "GIF لوڈ نہیں ہو سکا");
        TEXT.put("Microphone permission is needed", "مائیکروفون کی اجازت درکار ہے");
        TEXT.put("No internet connection for voice typing", "وائس ٹائپنگ کے لیے انٹرنیٹ کنکشن نہیں ہے");
        TEXT.put("Voice typing failed", "وائس ٹائپنگ نہیں ہو سکی");
        TEXT.put("Voice typing isn't available on this device", "اس ڈیوائس پر وائس ٹائپنگ دستیاب نہیں ہے");
        TEXT.put("GIF copied — paste it here", "GIF کاپی ہو گیا — یہاں پیسٹ کریں");
    }

    /** Urdu for the given English drawer text, or the English text itself if not translated yet. */
    static String get(String english) {
        String ur = TEXT.get(english);
        return ur != null ? ur : english;
    }

    static final String[] EMOJI_CATEGORIES = {
        "حال ہی میں استعمال شدہ",
        "چہرے اور جذبات",
        "لوگ اور جسم",
        "فطرت، تقریبات اور اسلامی",
        "جانور",
        "کھانا اور مشروبات",
        "سفر اور مقامات",
        "علامات اور جھنڈے"
    };
}
