package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * يقترح إيموجي مناسب بعد إكمال كلمة معروفة (عربي أو إنجليزي)، بالاعتماد على قاموس ثابت بسيط.
 * لا يستبدل شيئاً أبداً: فقط يُضاف كخيار إضافي ضمن شريط الاقتراحات ليُلمس يدوياً.
 */
final class EmojiSuggester {
    private EmojiSuggester() {}

    private static final Map<String, String> MAP = new HashMap<>();
    static {
        // عربي
        put("مبروك", "🎉");
        put("مبارك", "🎉");
        put("الحمدلله", "🤲");
        put("الحمدالله", "🤲");
        put("حبيبي", "❤️");
        put("حبيبتي", "❤️");
        put("احبك", "❤️");
        put("ضحك", "😂");
        put("هههه", "😂");
        put("ههههه", "😂");
        put("حزين", "😢");
        put("حزينة", "😢");
        put("ابكي", "😭");
        put("يعطيك العافيه", "💪");
        put("يعطيك العافية", "💪");
        put("الله يحفظك", "🤲");
        put("الله يعافيك", "🤲");
        put("مطر", "🌧️");
        put("قهوة", "☕");
        put("قهوه", "☕");
        put("شاي", "🍵");
        put("نار", "🔥");
        put("تمام", "👍");
        put("ممتاز", "👌");
        put("موافق", "✅");
        put("سلام", "👋");
        put("صباح الخير", "☀️");
        put("مساء الخير", "🌙");
        put("عيد", "🎉");
        put("رمضان", "🌙");
        put("جميل", "😍");
        put("رهيب", "🔥");
        put("تعبان", "😩");
        put("تعبانه", "😩");
        put("نايم", "😴");
        put("جوعان", "🍽️");
        put("فلوس", "💰");
        put("سيارة", "🚗");
        put("سياره", "🚗");
        put("بيت", "🏠");
        put("سفر", "✈️");
        put("بحر", "🌊");
        put("موسيقى", "🎵");
        put("رياضة", "⚽");
        put("رياضه", "⚽");

        // إنجليزي
        put("congrats", "🎉");
        put("congratulations", "🎉");
        put("love", "❤️");
        put("happy", "😊");
        put("sad", "😢");
        put("haha", "😂");
        put("lol", "😂");
        put("thanks", "🙏");
        put("thank you", "🙏");
        put("good", "👍");
        put("ok", "👌");
        put("yes", "✅");
        put("no", "❌");
        put("fire", "🔥");
        put("cool", "😎");
        put("party", "🎉");
        put("birthday", "🎂");
        put("coffee", "☕");
        put("tea", "🍵");
        put("car", "🚗");
        put("home", "🏠");
        put("travel", "✈️");
        put("music", "🎵");
        put("tired", "😩");
        put("sleep", "😴");
        put("hungry", "🍽️");
        put("money", "💰");
    }

    private static void put(String word, String emoji) {
        MAP.put(fold(word), emoji);
    }

    /** يطابق الكلمة (بعد تبسيط بسيط للمسافات والتشكيل) ويرجع الإيموجي المناسب، أو null لو ما لقى شيء. */
    static String suggestFor(String word) {
        if (word == null || word.isEmpty()) return null;
        return MAP.get(fold(word));
    }

    private static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'أ' || c == 'إ' || c == 'آ') c = 'ا';
            else if (c == 'ى') c = 'ي';
            else if (c == 'ة') c = 'ه';
            else if (c >= 0x064B && c <= 0x0652) continue; // تشكيل
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().trim();
    }
}
