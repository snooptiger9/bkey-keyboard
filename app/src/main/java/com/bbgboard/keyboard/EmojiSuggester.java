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
        // ===== توسعة القاموس (كلمة|إيموجي) =====
        addAll(
            // مشاعر وأحوال - عربي
            "سعيد|😊 سعيده|😊 فرحان|😄 فرحانه|😄 زعلان|😞 زعلانه|😞 غضبان|😠 معصب|😡 خايف|😨 مستحي|😳 مصدوم|😱 متحمس|🤩 مبسوط|😁 مبسوطه|😁 ضايق|😔 قلقان|😟 مشتاق|🥺 اشتقت|🥺 وحشتني|🥺 وحشتيني|🥺 بكيت|😭 ضحكت|😂 يضحك|😂 اضحك|😂 حلو|😍 حلوه|😍 جميله|😍 روعه|🤩 خرافي|🤯 مذهل|🤩 ابداع|👏 برافو|👏 كفو|👏 شكرا|🙏 شكراً|🙏 مشكور|🙏 يسلمو|🙏 يعطيك|💪 تسلم|🙏 عفوا|🤝 اسف|🙏 آسف|🙏 سامحني|🙏 تعال|👋 اهلا|👋 هلا|👋 مرحبا|👋 يا هلا|👋 مع السلامه|👋 تصبح على خير|🌙 صباح النور|☀️ مساء النور|🌙 الله يسلمك|🤲 ان شاء الله|🤲 الله يرحمه|🤲 الله يهديك|🤲 بالتوفيق|🍀 موفق|🍀 ناجح|🏆 نجاح|🏆 فوز|🏆 مبروك النجاح|🎓 تخرج|🎓 زواج|💍 عروس|👰 عريس|🤵 مولود|👶 طفل|👶 قلبي|❤️ عمري|❤️ روحي|❤️ حياتي|❤️ غالي|❤️ غاليه|❤️ يا قلبي|💕 وردة|🌹 ورد|🌹 زهرة|🌸 قمر|🌙 نجمة|⭐ نجوم|✨ شمس|☀️ سماء|☁️ غيم|☁️ ثلج|❄️ برد|🥶 حر|🥵 حار|🥵 رياح|💨 عاصفه|⛈️ رعد|⚡ سيل|🌊 جبل|⛰️ صحراء|🏜️ شجرة|🌳 زرع|🌱 ",
            // أكل وشرب - عربي
            "اكل|🍽️ اكلة|🍽️ غداء|🍛 عشاء|🍽️ فطور|🍳 رز|🍚 دجاج|🍗 لحم|🥩 سمك|🐟 برجر|🍔 بيتزا|🍕 شورما|🌯 مندي|🍖 كبسة|🍛 معجنات|🥐 خبز|🍞 جبن|🧀 بيض|🥚 حليب|🥛 عصير|🧃 ماء|💧 مويه|💧 كولا|🥤 ايس كريم|🍦 حلا|🍰 حلى|🍰 كيك|🎂 كيكة|🎂 شوكولاتة|🍫 شوكلاته|🍫 تمر|🌴 موز|🍌 تفاح|🍎 برتقال|🍊 فراولة|🍓 عنب|🍇 بطيخ|🍉 كرز|🍒 سلطة|🥗 شوربة|🍲 ",
            // أماكن ونشاطات - عربي
            "مدرسة|🏫 جامعة|🎓 شغل|💼 دوام|💼 عمل|💼 مكتب|🏢 اجتماع|📅 مستشفى|🏥 دكتور|👨‍⚕️ طبيب|👨‍⚕️ دواء|💊 صيدلية|💊 سوق|🛒 مول|🛍️ مطعم|🍴 كافيه|☕ مسجد|🕌 صلاة|🕌 صلي|🕌 قرآن|📖 قران|📖 دعاء|🤲 مكة|🕋 الكعبه|🕋 عمرة|🕋 حج|🕋 مطار|✈️ طيارة|✈️ طائرة|✈️ قطار|🚆 باص|🚌 سيارتي|🚗 بنزين|⛽ سفره|🧳 اجازة|🏖️ فندق|🏨 شاطئ|🏖️ مسبح|🏊 جيم|🏋️ تمرين|💪 كرة|⚽ مباراة|⚽ هدف|🥅 لعب|🎮 العاب|🎮 فيلم|🎬 مسلسل|📺 اغنية|🎵 صوره|📷 تصوير|📸 كتاب|📚 قراءة|📖 دراسة|📚 امتحان|📝 اختبار|📝 واجب|📝 نوم|😴 احلام|💭 ",
            // اشياء - عربي
            "جوال|📱 هاتف|📱 اتصل|📞 اتصال|📞 رسالة|💬 ايميل|📧 بريد|📧 هدية|🎁 هديه|🎁 هدايا|🎁 مفتاح|🔑 قفل|🔒 فكرة|💡 مهم|❗ تنبيه|⚠️ خطر|⚠️ ممنوع|🚫 صح|✅ خطأ|❌ غلط|❌ سؤال|❓ وقت|⏰ ساعة|⏰ منبه|⏰ تاريخ|📅 موعد|📅 كمبيوتر|💻 لابتوب|💻 انترنت|🌐 واي فاي|📶 شاحن|🔌 بطارية|🔋 ",
            // حيوانات - عربي
            "قطه|🐱 قط|🐱 كلب|🐶 حصان|🐴 جمل|🐪 ناقه|🐪 اسد|🦁 ذئب|🐺 طير|🐦 صقر|🦅 سمكه|🐠 دب|🐻 ",
            // English feelings
            "happy|😊 glad|😊 excited|🤩 angry|😠 mad|😡 upset|😞 scared|😨 afraid|😨 shocked|😱 surprised|😮 nervous|😬 embarrassed|😳 lonely|😔 bored|😐 sorry|🙏 please|🙏 welcome|🤝 hello|👋 hi|👋 hey|👋 bye|👋 goodbye|👋 goodnight|🌙 good night|🌙 good morning|☀️ good evening|🌆 great|👍 awesome|🤩 amazing|🤩 wow|😮 perfect|👌 nice|👍 beautiful|😍 cute|🥰 lovely|😍 miss you|🥺 miss|🥺 hug|🤗 kiss|😘 laugh|😂 funny|😂 smile|😊 cry|😭 crying|😭 tears|😢 sick|🤒 ill|🤒 hurt|🤕 pain|🤕 headache|🤕 dead|💀 dying|💀 hot|🥵 cold|🥶 hurry|🏃 run|🏃 fast|⚡ slow|🐢 busy|⏳ ready|✅ done|✅ finished|✅ welcome back|🤗 ",
            // English things
            "food|🍽️ eat|🍽️ lunch|🍛 dinner|🍽️ breakfast|🍳 pizza|🍕 burger|🍔 fries|🍟 chicken|🍗 rice|🍚 fish|🐟 meat|🥩 bread|🍞 cheese|🧀 egg|🥚 milk|🥛 juice|🧃 water|💧 drink|🥤 beer|🍺 wine|🍷 cake|🍰 cookie|🍪 chocolate|🍫 candy|🍬 ice cream|🍦 apple|🍎 banana|🍌 orange|🍊 strawberry|🍓 grapes|🍇 watermelon|🍉 salad|🥗 soup|🍲 school|🏫 college|🎓 university|🎓 graduation|🎓 work|💼 job|💼 office|🏢 meeting|📅 boss|👔 hospital|🏥 doctor|👨‍⚕️ medicine|💊 pharmacy|💊 shop|🛒 shopping|🛍️ mall|🛍️ restaurant|🍴 cafe|☕ mosque|🕌 prayer|🕌 pray|🤲 quran|📖 mecca|🕋 hajj|🕋 umrah|🕋 airport|✈️ plane|✈️ flight|✈️ train|🚆 bus|🚌 taxi|🚕 gas|⛽ trip|🧳 vacation|🏖️ holiday|🏖️ hotel|🏨 beach|🏖️ pool|🏊 gym|🏋️ workout|💪 football|⚽ soccer|⚽ goal|🥅 game|🎮 games|🎮 movie|🎬 film|🎬 show|📺 song|🎵 photo|📷 picture|📸 book|📚 read|📖 study|📚 exam|📝 test|📝 homework|📝 dream|💭 phone|📱 call|📞 text|💬 message|💬 email|📧 mail|📧 gift|🎁 present|🎁 key|🔑 lock|🔒 idea|💡 important|❗ warning|⚠️ danger|⚠️ stop|🛑 correct|✅ wrong|❌ question|❓ time|⏰ clock|⏰ alarm|⏰ date|📅 computer|💻 laptop|💻 internet|🌐 wifi|📶 charger|🔌 battery|🔋 sun|☀️ moon|🌙 star|⭐ stars|✨ rain|🌧️ snow|❄️ storm|⛈️ wind|💨 cloud|☁️ flower|🌸 rose|🌹 tree|🌳 mountain|⛰️ ocean|🌊 sea|🌊 dog|🐶 cat|🐱 horse|🐴 bird|🐦 lion|🦁 bear|🐻 wedding|💍 married|💍 baby|👶 kid|👶 heart|❤️ darling|❤️ honey|❤️ sweet|🍯 sweetheart|💕 luck|🍀 good luck|🍀 success|🏆 win|🏆 winner|🏆 champion|🏆 trophy|🏆 prize|🏆 celebrate|🎉 celebration|🎉 congrats|🎊 new year|🎆 christmas|🎄 ramadan|🌙 eid|🎉 pray for|🤲 god|🙏 amen|🤲 peace|☮️ victory|✌️ strong|💪 power|💪 secret|🤫 shh|🤫 think|🤔 thinking|🤔 maybe|🤔 idk|🤷 sure|👍 of course|👍 okay|👌 fine|👌 agree|🤝 deal|🤝 promise|🤞 hope|🤞 wish|🤞 pray|🙏 bless|🙏 blessed|🙏 thank|🙏 thx|🙏 ty|🙏 omg|😱 oops|🙈 ugh|😩 yay|🎉 yeah|🙌 cheers|🥂 bravo|👏 clap|👏 applause|👏 "
        );
    }

    /** يقرأ نصاً بصيغة "كلمة|إيموجي" مفصولة بمسافات (الكلمة قد تحوي مسافة)، ولا يستبدل الموجود. */
    private static void addAll(String... chunks) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([^|]+?)\\|(\\S+)(?:\\s+|$)");
        for (String chunk : chunks) {
            java.util.regex.Matcher m = p.matcher(chunk);
            while (m.find()) {
                String k = fold(m.group(1));
                if (!k.isEmpty() && !MAP.containsKey(k)) MAP.put(k, m.group(2));
            }
        }
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
