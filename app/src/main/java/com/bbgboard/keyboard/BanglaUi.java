package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Bangla text for the keyboard's own drawers (clipboard, emoji, GIF), keyed by the English
 * string the drawer code already carries. Anything missing falls back to the English text, so
 * a new drawer string is never blank in Bangla, just untranslated until it is added here.
 */
final class BanglaUi {
    private BanglaUi() {}

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        TEXT.put("Screenshot copied — paste it here", "স্ক্রিনশট কপি হয়েছে — এখানে পেস্ট করুন");
        TEXT.put("Delete", "মুছুন");
        TEXT.put("Screenshot", "স্ক্রিনশট");
        TEXT.put("Incognito mode is on\nClipboard is hidden and nothing new is being saved",
                "ইনকগনিটো মোড চালু আছে\nক্লিপবোর্ড লুকানো আছে এবং নতুন কিছু সংরক্ষণ করা হচ্ছে না");
        TEXT.put("Nothing copied yet", "এখনও কিছু কপি করা হয়নি");
        TEXT.put("Unpin", "আনপিন করুন");
        TEXT.put("Pin", "পিন করুন");
        TEXT.put("Expand", "প্রসারিত করুন");
        TEXT.put("Collapse", "সংকুচিত করুন");
        TEXT.put("Powered by KLIPY", "KLIPY দ্বারা চালিত");
        TEXT.put("Search KLIPY...", "KLIPY-তে খুঁজুন...");
        TEXT.put("Undo", "পূর্বাবস্থায় ফিরুন");
        TEXT.put("No recent emojis yet", "এখনও কোনো সাম্প্রতিক ইমোজি নেই");
        TEXT.put("  •  Long-press any emoji to delete it", "  •  যেকোনো ইমোজি মুছতে দীর্ঘক্ষণ চাপুন");
        TEXT.put("  •  Long-press any GIF to delete it", "  •  যেকোনো GIF মুছতে দীর্ঘক্ষণ চাপুন");
        TEXT.put("Loading...", "লোড হচ্ছে...");
        TEXT.put("Couldn't load: ", "লোড করা যায়নি: ");
        TEXT.put("No results", "কোনো ফলাফল নেই");
        TEXT.put("Recently used", "সম্প্রতি ব্যবহৃত");
        TEXT.put("Search results", "অনুসন্ধানের ফলাফল");
        TEXT.put("Trending now", "এখন ট্রেন্ডিং");
        TEXT.put("This app doesn't allow sending GIFs here", "এই অ্যাপ এখানে GIF পাঠানোর অনুমতি দেয় না");
        TEXT.put("This app doesn't allow sending screenshots here", "এই অ্যাপ এখানে স্ক্রিনশট পাঠানোর অনুমতি দেয় না");
        TEXT.put("Couldn't load GIF", "GIF লোড করা যায়নি");
        TEXT.put("Microphone permission is needed", "মাইক্রোফোনের অনুমতি প্রয়োজন");
        TEXT.put("No internet connection for voice typing", "ভয়েস টাইপিংয়ের জন্য ইন্টারনেট সংযোগ নেই");
        TEXT.put("Voice typing failed", "ভয়েস টাইপিং ব্যর্থ হয়েছে");
        TEXT.put("Voice typing isn't available on this device", "এই ডিভাইসে ভয়েস টাইপিং উপলব্ধ নয়");
        TEXT.put("GIF copied — paste it here", "GIF কপি হয়েছে — এখানে পেস্ট করুন");
    }

    /** Bangla for the given English drawer text, or the English text itself if not translated yet. */
    static String get(String en) {
        return TEXT.containsKey(en) ? TEXT.get(en) : en;
    }

    static final String[] EMOJI_CATEGORIES = {
        "সম্প্রতি ব্যবহৃত",
        "মুখ ও অনুভূতি",
        "মানুষ ও শরীর",
        "প্রকৃতি, অনুষ্ঠান ও ইসলামিক",
        "প্রাণী",
        "খাবার ও পানীয়",
        "ভ্রমণ ও স্থান",
        "চিহ্ন ও পতাকা"
    };
}