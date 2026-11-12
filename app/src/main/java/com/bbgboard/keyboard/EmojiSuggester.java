package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

final class EmojiSuggester {
    private EmojiSuggester() {}

    private static final Map<String, String> MAP = new HashMap<>();
    static {

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
        put("سكين", "🔪");
        put("بيت", "🏠");
        put("سفر", "✈️");
        put("بحر", "🌊");
        put("موسيقى", "🎵");
        put("رياضة", "⚽");
        put("رياضه", "⚽");

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
        put("knife", "🔪");
        put("home", "🏠");
        put("travel", "✈️");
        put("music", "🎵");
        put("tired", "😩");
        put("sleep", "😴");
        put("hungry", "🍽️");
        put("money", "💰");

        addAll(

            "سعيد|😊 سعيده|😊 فرحان|😄 فرحانه|😄 زعلان|😞 زعلانه|😞 غضبان|😠 معصب|😡 خايف|😨 مستحي|😳 مصدوم|😱 متحمس|🤩 مبسوط|😁 مبسوطه|😁 ضايق|😔 قلقان|😟 مشتاق|🥺 اشتقت|🥺 وحشتني|🥺 وحشتيني|🥺 بكيت|😭 ضحكت|😂 يضحك|😂 اضحك|😂 حلو|😍 حلوه|😍 جميله|😍 روعه|🤩 خرافي|🤯 مذهل|🤩 ابداع|👏 برافو|👏 كفو|👏 شكرا|🙏 شكراً|🙏 مشكور|🙏 يسلمو|🙏 يعطيك|💪 تسلم|🙏 عفوا|🤝 اسف|🙏 آسف|🙏 سامحني|🙏 تعال|👋 اهلا|👋 هلا|👋 مرحبا|👋 يا هلا|👋 مع السلامه|👋 تصبح على خير|🌙 صباح النور|☀️ مساء النور|🌙 الله يسلمك|🤲 ان شاء الله|🤲 الله يرحمه|🤲 الله يهديك|🤲 بالتوفيق|🍀 موفق|🍀 ناجح|🏆 نجاح|🏆 فوز|🏆 مبروك النجاح|🎓 تخرج|🎓 زواج|💍 عروس|👰 عريس|🤵 مولود|👶 طفل|👶 قلبي|❤️ عمري|❤️ روحي|❤️ حياتي|❤️ غالي|❤️ غاليه|❤️ يا قلبي|💕 وردة|🌹 ورد|🌹 زهرة|🌸 قمر|🌙 نجمة|⭐ نجوم|✨ شمس|☀️ سماء|☁️ غيم|☁️ ثلج|❄️ برد|🥶 حر|🥵 حار|🥵 رياح|💨 عاصفه|⛈️ رعد|⚡ سيل|🌊 جبل|⛰️ صحراء|🏜️ شجرة|🌳 زرع|🌱 ",

            "اكل|🍽️ اكلة|🍽️ غداء|🍛 عشاء|🍽️ فطور|🍳 رز|🍚 دجاج|🍗 لحم|🥩 سمك|🐟 برجر|🍔 بيتزا|🍕 شورما|🌯 مندي|🍖 كبسة|🍛 معجنات|🥐 خبز|🍞 جبن|🧀 بيض|🥚 حليب|🥛 عصير|🧃 ماء|💧 مويه|💧 كولا|🥤 ايس كريم|🍦 حلا|🍰 حلى|🍰 كيك|🎂 كيكة|🎂 شوكولاتة|🍫 شوكلاته|🍫 تمر|🌴 موز|🍌 تفاح|🍎 برتقال|🍊 فراولة|🍓 عنب|🍇 بطيخ|🍉 كرز|🍒 سلطة|🥗 شوربة|🍲 ",

            "مدرسة|🏫 جامعة|🎓 شغل|💼 دوام|💼 عمل|💼 مكتب|🏢 اجتماع|📅 مستشفى|🏥 دكتور|👨‍⚕️ طبيب|👨‍⚕️ دواء|💊 صيدلية|💊 سوق|🛒 مول|🛍️ مطعم|🍴 كافيه|☕ مسجد|🕌 صلاة|🕌 صلي|🕌 قرآن|📖 قران|📖 دعاء|🤲 مكة|🕋 الكعبه|🕋 عمرة|🕋 حج|🕋 مطار|✈️ طيارة|✈️ طائرة|✈️ قطار|🚆 باص|🚌 سيارتي|🚗 بنزين|⛽ سفره|🧳 اجازة|🏖️ فندق|🏨 شاطئ|🏖️ مسبح|🏊 جيم|🏋️ تمرين|💪 كرة|⚽ مباراة|⚽ هدف|🥅 لعب|🎮 العاب|🎮 فيلم|🎬 مسلسل|📺 اغنية|🎵 صوره|📷 تصوير|📸 كتاب|📚 قراءة|📖 دراسة|📚 امتحان|📝 اختبار|📝 واجب|📝 نوم|😴 احلام|💭 ",

            "جوال|📱 هاتف|📱 اتصل|📞 اتصال|📞 رسالة|💬 ايميل|📧 بريد|📧 هدية|🎁 هديه|🎁 هدايا|🎁 مفتاح|🔑 قفل|🔒 فكرة|💡 مهم|❗ تنبيه|⚠️ خطر|⚠️ ممنوع|🚫 صح|✅ خطأ|❌ غلط|❌ سؤال|❓ وقت|⏰ ساعة|⏰ منبه|⏰ تاريخ|📅 موعد|📅 كمبيوتر|💻 لابتوب|💻 انترنت|🌐 واي فاي|📶 شاحن|🔌 بطارية|🔋 ",

            "قطه|🐱 قط|🐱 كلب|🐶 حصان|🐴 جمل|🐪 ناقه|🐪 اسد|🦁 ذئب|🐺 طير|🐦 صقر|🦅 سمكه|🐠 دب|🐻 ",

            "happy|😊 glad|😊 excited|🤩 angry|😠 mad|😡 upset|😞 scared|😨 afraid|😨 shocked|😱 surprised|😮 nervous|😬 embarrassed|😳 lonely|😔 bored|😐 sorry|🙏 please|🙏 welcome|🤝 hello|👋 hi|👋 hey|👋 bye|👋 goodbye|👋 goodnight|🌙 good night|🌙 good morning|☀️ good evening|🌆 great|👍 awesome|🤩 amazing|🤩 wow|😮 perfect|👌 nice|👍 beautiful|😍 cute|🥰 lovely|😍 miss you|🥺 miss|🥺 hug|🤗 kiss|😘 laugh|😂 funny|😂 smile|😊 cry|😭 crying|😭 tears|😢 sick|🤒 ill|🤒 hurt|🤕 pain|🤕 headache|🤕 dead|💀 dying|💀 hot|🥵 cold|🥶 hurry|🏃 run|🏃 fast|⚡ slow|🐢 busy|⏳ ready|✅ done|✅ finished|✅ welcome back|🤗 ",

            "food|🍽️ eat|🍽️ lunch|🍛 dinner|🍽️ breakfast|🍳 pizza|🍕 burger|🍔 fries|🍟 chicken|🍗 rice|🍚 fish|🐟 meat|🥩 bread|🍞 cheese|🧀 egg|🥚 milk|🥛 juice|🧃 water|💧 drink|🥤 beer|🍺 wine|🍷 cake|🍰 cookie|🍪 chocolate|🍫 candy|🍬 ice cream|🍦 apple|🍎 banana|🍌 orange|🍊 strawberry|🍓 grapes|🍇 watermelon|🍉 salad|🥗 soup|🍲 school|🏫 college|🎓 university|🎓 graduation|🎓 work|💼 job|💼 office|🏢 meeting|📅 boss|👔 hospital|🏥 doctor|👨‍⚕️ medicine|💊 pharmacy|💊 shop|🛒 shopping|🛍️ mall|🛍️ restaurant|🍴 cafe|☕ mosque|🕌 prayer|🕌 pray|🤲 quran|📖 mecca|🕋 hajj|🕋 umrah|🕋 airport|✈️ plane|✈️ flight|✈️ train|🚆 bus|🚌 taxi|🚕 gas|⛽ trip|🧳 vacation|🏖️ holiday|🏖️ hotel|🏨 beach|🏖️ pool|🏊 gym|🏋️ workout|💪 football|⚽ soccer|⚽ goal|🥅 game|🎮 games|🎮 movie|🎬 film|🎬 show|📺 song|🎵 photo|📷 picture|📸 book|📚 read|📖 study|📚 exam|📝 test|📝 homework|📝 dream|💭 phone|📱 call|📞 text|💬 message|💬 email|📧 mail|📧 gift|🎁 present|🎁 key|🔑 lock|🔒 idea|💡 important|❗ warning|⚠️ danger|⚠️ stop|🛑 correct|✅ wrong|❌ question|❓ time|⏰ clock|⏰ alarm|⏰ date|📅 computer|💻 laptop|💻 internet|🌐 wifi|📶 charger|🔌 battery|🔋 sun|☀️ moon|🌙 star|⭐ stars|✨ rain|🌧️ snow|❄️ storm|⛈️ wind|💨 cloud|☁️ flower|🌸 rose|🌹 tree|🌳 mountain|⛰️ ocean|🌊 sea|🌊 dog|🐶 cat|🐱 horse|🐴 bird|🐦 lion|🦁 bear|🐻 wedding|💍 married|💍 baby|👶 kid|👶 heart|❤️ darling|❤️ honey|❤️ sweet|🍯 sweetheart|💕 luck|🍀 good luck|🍀 success|🏆 win|🏆 winner|🏆 champion|🏆 trophy|🏆 prize|🏆 celebrate|🎉 celebration|🎉 congrats|🎊 new year|🎆 christmas|🎄 ramadan|🌙 eid|🎉 pray for|🤲 god|🙏 amen|🤲 peace|☮️ victory|✌️ strong|💪 power|💪 secret|🤫 shh|🤫 think|🤔 thinking|🤔 maybe|🤔 idk|🤷 sure|👍 of course|👍 okay|👌 fine|👌 agree|🤝 deal|🤝 promise|🤞 hope|🤞 wish|🤞 pray|🙏 bless|🙏 blessed|🙏 thank|🙏 thx|🙏 ty|🙏 omg|😱 oops|🙈 ugh|😩 yay|🎉 yeah|🙌 cheers|🥂 bravo|👏 clap|👏 applause|👏 "
        );

        addAll(
            "بوسه|😘 بوس|😘 بوسة|😘 قبلة|😘 قبله|😘 مستغرب|😲 مستغربه|😲 مستغربة|😲 استغراب|😲 يستغرب|😲 صدمه|😱 صدمة|😱 مصدومه|😲 مصدومة|😲 اوك|👌 اوكي|👌 اوكى|👌 تك|👌 يب|👍 ايوه|👍 ايوا|👍 خلاص|✅ يلا|🏃 يالله|🏃 هيا|🏃 زين|👍 طيب|👌 طيّب|👌 حركات|🙄 طق|🙄 فل|🔥 احم|😅 هاه|😮 اها|🤔 امم|🤔 اووف|😩 اوف|😩 يع|🤢 مقرف|🤢 قرف|🤢 بشع|🤮 فظيع|😖 ملل|😐 زهقان|😒 زهقانه|😒 استانس|😌 مستانس|😌 فرحتني|🥹 محبط|😞 محبطه|😞 عتاب|😕 مشتاقلك|🥺 اشتقتلك|🥺 يقهر|😤 يضايق|😤 شكله|🤨 يادوب|😅 ماشاءالله|🤍 ماشاء الله|🤍 تبارك الله|🤍 حسبي الله|🙏 استغفرالله|🙏 استغفر الله|🙏 سبحان الله|🙏 الله اكبر|🙏 لا اله الا الله|🙏 يختي|😅 يخوي|😅 والله ضحكتني|😂 موت من الضحك|😂 مت ضحك|😂 اقسم بالله|🙏 عادي|😌 ولا يهمك|😉 خلك قوي|💪 الحمدلله على كل حال|🤲 ",
            "kiss|😘 xoxo|😘 smooch|😘 mwah|😘 confused|😕 confusing|🤔 weird|😳 ok|👌 okay|👌 kk|👌 meh|😐 idk|🤷 lmao|😂 rofl|🤣 bruh|😑 oof|😬 yikes|😬 ugh|😩 nvm|🤷 lowkey|😅 highkey|😤 same|🤝 mood|😌 vibe|✨ slay|💅 facts|💯 bet|👍 no cap|💯 based|😎 extra|🙄 shade|😏 tea|☕ shook|😲 gross|🤢 yuck|🤮 bored|😐 relax|😌 chill|😌 whatever|🤷 "
        );

        addAll(
            "السعودية|🇸🇦 السعوديه|🇸🇦 سعودية|🇸🇦 سعوديه|🇸🇦 الامارات|🇦🇪 امارات|🇦🇪 الكويت|🇰🇼 كويت|🇰🇼 قطر|🇶🇦 البحرين|🇧🇭 بحرين|🇧🇭 عمان|🇴🇲 اليمن|🇾🇪 يمن|🇾🇪 الاردن|🇯🇴 اردن|🇯🇴 العراق|🇮🇶 عراق|🇮🇶 سوريا|🇸🇾 سوريه|🇸🇾 لبنان|🇱🇧 فلسطين|🇵🇸 مصر|🇪🇬 السودان|🇸🇩 سودان|🇸🇩 ليبيا|🇱🇾 تونس|🇹🇳 الجزائر|🇩🇿 جزائر|🇩🇿 المغرب|🇲🇦 مغرب|🇲🇦 موريتانيا|🇲🇷 الصومال|🇸🇴 صومال|🇸🇴 تركيا|🇹🇷 ايران|🇮🇷 باكستان|🇵🇰 الهند|🇮🇳 هند|🇮🇳 امريكا|🇺🇸 الولايات المتحدة|🇺🇸 بريطانيا|🇬🇧 انجلترا|🇬🇧 فرنسا|🇫🇷 المانيا|🇩🇪 اسبانيا|🇪🇸 ايطاليا|🇮🇹 البرازيل|🇧🇷 برازيل|🇧🇷 الارجنتين|🇦🇷 ارجنتين|🇦🇷 الصين|🇨🇳 صين|🇨🇳 اليابان|🇯🇵 يابان|🇯🇵 كوريا|🇰🇷 روسيا|🇷🇺 كندا|🇨🇦 استراليا|🇦🇺 اوستراليا|🇦🇺 جنوب افريقيا|🇿🇦 نيجيريا|🇳🇬 كينيا|🇰🇪 اثيوبيا|🇪🇹 غانا|🇬🇭 السنغال|🇸🇳 سنغال|🇸🇳 ساحل العاج|🇨🇮 الكاميرون|🇨🇲 كاميرون|🇨🇲 اوغندا|🇺🇬 تنزانيا|🇹🇿 زيمبابوي|🇿🇼 اندونيسيا|🇮🇩 ماليزيا|🇲🇾 سنغافورة|🇸🇬 تايلاند|🇹🇭 فيتنام|🇻🇳 الفلبين|🇵🇭 فلبين|🇵🇭 بنغلاديش|🇧🇩 سريلانكا|🇱🇰 نيبال|🇳🇵 افغانستان|🇦🇫 كازاخستان|🇰🇿 اوزبكستان|🇺🇿 اذربيجان|🇦🇿 ارمينيا|🇦🇲 جورجيا|🇬🇪 هولندا|🇳🇱 بلجيكا|🇧🇪 سويسرا|🇨🇭 النمسا|🇦🇹 نمسا|🇦🇹 السويد|🇸🇪 سويد|🇸🇪 النرويج|🇳🇴 نرويج|🇳🇴 الدنمارك|🇩🇰 دنمارك|🇩🇰 فنلندا|🇫🇮 بولندا|🇵🇱 البرتغال|🇵🇹 برتغال|🇵🇹 اليونان|🇬🇷 يونان|🇬🇷 ايرلندا|🇮🇪 التشيك|🇨🇿 المجر|🇭🇺 رومانيا|🇷🇴 اوكرانيا|🇺🇦 المكسيك|🇲🇽 مكسيك|🇲🇽 تشيلي|🇨🇱 كولومبيا|🇨🇴 بيرو|🇵🇪 فنزويلا|🇻🇪 كوبا|🇨🇺 نيوزيلندا|🇳🇿 ",
            "saudi|🇸🇦 saudi arabia|🇸🇦 uae|🇦🇪 emirates|🇦🇪 kuwait|🇰🇼 qatar|🇶🇦 bahrain|🇧🇭 oman|🇴🇲 yemen|🇾🇪 jordan|🇯🇴 iraq|🇮🇶 syria|🇸🇾 lebanon|🇱🇧 palestine|🇵🇸 egypt|🇪🇬 sudan|🇸🇩 libya|🇱🇾 tunisia|🇹🇳 algeria|🇩🇿 morocco|🇲🇦 mauritania|🇲🇷 somalia|🇸🇴 turkey|🇹🇷 iran|🇮🇷 pakistan|🇵🇰 india|🇮🇳 usa|🇺🇸 america|🇺🇸 uk|🇬🇧 britain|🇬🇧 england|🇬🇧 france|🇫🇷 germany|🇩🇪 spain|🇪🇸 italy|🇮🇹 brazil|🇧🇷 argentina|🇦🇷 china|🇨🇳 japan|🇯🇵 korea|🇰🇷 russia|🇷🇺 canada|🇨🇦 australia|🇦🇺 south africa|🇿🇦 nigeria|🇳🇬 kenya|🇰🇪 ethiopia|🇪🇹 ghana|🇬🇭 senegal|🇸🇳 cameroon|🇨🇲 uganda|🇺🇬 tanzania|🇹🇿 zimbabwe|🇿🇼 indonesia|🇮🇩 malaysia|🇲🇾 singapore|🇸🇬 thailand|🇹🇭 vietnam|🇻🇳 philippines|🇵🇭 bangladesh|🇧🇩 sri lanka|🇱🇰 nepal|🇳🇵 afghanistan|🇦🇫 kazakhstan|🇰🇿 uzbekistan|🇺🇿 azerbaijan|🇦🇿 armenia|🇦🇲 georgia|🇬🇪 netherlands|🇳🇱 belgium|🇧🇪 switzerland|🇨🇭 austria|🇦🇹 sweden|🇸🇪 norway|🇳🇴 denmark|🇩🇰 finland|🇫🇮 poland|🇵🇱 portugal|🇵🇹 greece|🇬🇷 ireland|🇮🇪 czech republic|🇨🇿 hungary|🇭🇺 romania|🇷🇴 ukraine|🇺🇦 mexico|🇲🇽 chile|🇨🇱 colombia|🇨🇴 peru|🇵🇪 venezuela|🇻🇪 cuba|🇨🇺 new zealand|🇳🇿 "
        );

        for (int i = 2; i <= 25; i++) {
            char[] rep = new char[i];
            java.util.Arrays.fill(rep, 'ه');
            put(new String(rep), "😂");
        }
    }

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
            else if (c >= 0x064B && c <= 0x0652) continue;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().trim();
    }
}
