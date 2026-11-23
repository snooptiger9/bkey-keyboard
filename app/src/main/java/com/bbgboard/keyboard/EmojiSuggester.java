package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

final class EmojiSuggester {
    private EmojiSuggester() {}

    private static final Map<String, String> MAP = new HashMap<>();
    // Spanish-only: many of these words (no, sol, pan, mar...) are ordinary English words,
    // so they are only consulted while the Spanish layout is active.
    private static final Map<String, String> ES_MAP = new HashMap<>();
    // French-only, for the same reason (pain, mer, plat, sol...).
    private static final Map<String, String> FR_MAP = new HashMap<>();
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

        addAll(
            "प्यार|❤️ प्रेम|❤️ मोहब्बत|❤️ इश्क|❤️ दिल|❤️ मुस्कान|😊 खुश|😊 ख़ुश|😊 खुशी|😊 हँसी|😂 हंसी|😂 हाहा|😂 हाहाहा|😂 उदास|😢 दुखी|😢 रोना|😭 गुस्सा|😠 नाराज़|😠 नाराज|😠 डर|😨 डरा|😨 थका|😩 थकान|😩 नींद|😴 सोना|😴 ",
            "धन्यवाद|🙏 शुक्रिया|🙏 शुक्रिया|🙏 नमस्ते|🙏 नमस्कार|🙏 प्रणाम|🙏 राम|🙏 जय|🙏 भगवान|🙏 ईश्वर|🙏 प्रार्थना|🙏 आशीर्वाद|🙏 ",
            "बधाई|🎉 शुभकामनाएँ|🎉 शुभकामनाएं|🎉 मुबारक|🎉 जन्मदिन|🎂 केक|🎂 पार्टी|🎉 दिवाली|🪔 दीपावली|🪔 दीवाली|🪔 होली|🎨 राखी|🧵 ईद|🌙 रमज़ान|🌙 रमजान|🌙 क्रिसमस|🎄 त्योहार|🎊 ",
            "अच्छा|👍 बढ़िया|👍 बढ़िया|👌 ठीक|👌 हाँ|✅ हां|✅ नहीं|❌ बिल्कुल|💯 शानदार|🔥 जबरदस्त|🔥 कमाल|🤩 सुंदर|😍 खूबसूरत|😍 प्यारा|🥰 मस्त|😎 ",
            "खाना|🍽️ भोजन|🍽️ नाश्ता|🍳 दोपहर|🍛 रात|🌙 चाय|🍵 कॉफ़ी|☕ कॉफी|☕ पानी|💧 दूध|🥛 रोटी|🫓 चावल|🍚 दाल|🍲 समोसा|🥟 पिज़्ज़ा|🍕 पिज्जा|🍕 बर्गर|🍔 मिठाई|🍬 आइसक्रीम|🍦 फल|🍎 आम|🥭 केला|🍌 सेब|🍎 ",
            "घर|🏠 स्कूल|🏫 कॉलेज|🎓 दफ़्तर|🏢 दफ्तर|🏢 काम|💼 मीटिंग|📅 अस्पताल|🏥 डॉक्टर|👨‍⚕️ दवा|💊 पैसा|💰 पैसे|💰 रुपया|💰 रुपये|💰 गाड़ी|🚗 कार|🚗 बस|🚌 ट्रेन|🚆 रेल|🚆 विमान|✈️ हवाई|✈️ यात्रा|✈️ सफ़र|✈️ सफर|✈️ ",
            "फोन|📱 फ़ोन|📱 मोबाइल|📱 कॉल|📞 संदेश|💬 मैसेज|💬 ईमेल|📧 उपहार|🎁 तोहफा|🎁 तोहफ़ा|🎁 किताब|📚 पढ़ाई|📖 संगीत|🎵 गाना|🎵 गीत|🎵 फिल्म|🎬 फ़िल्म|🎬 खेल|⚽ क्रिकेट|🏏 ",
            "सुबह|☀️ सुप्रभात|☀️ शाम|🌆 शुभरात्रि|🌙 रात्रि|🌙 चाँद|🌙 चांद|🌙 सूरज|☀️ बारिश|🌧️ बादल|☁️ बर्फ|❄️ गर्मी|🥵 सर्दी|🥶 हवा|🌬️ आग|🔥 फूल|🌸 गुलाब|🌹 पेड़|🌳 समुद्र|🌊 नदी|🏞️ पहाड़|⛰️ ",
            "कुत्ता|🐶 बिल्ली|🐱 गाय|🐄 घोड़ा|🐴 शेर|🦁 हाथी|🐘 बंदर|🐒 मछली|🐟 पक्षी|🐦 चिड़िया|🐦 साँप|🐍 सांप|🐍 मोर|🦚 ",
            "भारत|🇮🇳 हिंदुस्तान|🇮🇳 इंडिया|🇮🇳 पाकिस्तान|🇵🇰 नेपाल|🇳🇵 बांग्लादेश|🇧🇩 श्रीलंका|🇱🇰 अमेरिका|🇺🇸 ब्रिटेन|🇬🇧 दुबई|🇦🇪 सऊदी|🇸🇦 "
        );

        for (int i = 2; i <= 25; i++) {
            char[] rep = new char[i];
            java.util.Arrays.fill(rep, 'ه');
            put(new String(rep), "😂");
        }
    }

    static {
        addAllTo(ES_MAP,
            "amor|❤️ te quiero|❤️ te amo|❤️ cariño|❤️ corazón|❤️ beso|😘 besos|😘 abrazo|🤗 feliz|😊 contento|😊 contenta|😊 alegre|😄 alegría|😄 sonrisa|😊 risa|😂 jaja|😂 jajaja|😂 jeje|😂 jajajaja|😂 gracioso|😂 triste|😢 tristeza|😢 llorar|😭 lloro|😭 enojado|😠 enojada|😠 enfadado|😠 rabia|😡 miedo|😨 susto|😱 sorpresa|😮 nervioso|😬 vergüenza|😳 cansado|😴 cansada|😴 sueño|😴 dormir|😴 aburrido|😐 enfermo|🤒 enferma|🤒 fiebre|🤒 guay|😎 genial|🤩 increíble|🤩 fantástico|🤩 perfecto|👌 excelente|🔥 bueno|👍 buena|👍 vale|👌 claro|👌 dale|👌 sí|✅ no|❌ ok|👌 bien|👍 mal|👎 cuidado|⚠️ peligro|⚠️ ojo|👀 ",
            "gracias|🙏 muchas|🙏 por favor|🙏 perdón|🙏 disculpa|🙏 lo siento|🙏 bienvenido|🤝 bienvenida|🤝 hola|👋 adiós|👋 chao|👋 hasta luego|👋 saludos|👋 buenos días|☀️ buenas noches|🌙 buenas tardes|🌆 felicidades|🎉 enhorabuena|🎉 cumpleaños|🎂 fiesta|🎉 pastel|🎂 tarta|🎂 regalo|🎁 navidad|🎄 sorpresas|🎁 bendiciones|🙏 dios|🙏 oración|🙏 rezar|🙏 amén|🙏 amigo|🤝 amiga|🤝 amigos|🤝 familia|👨‍👩‍👧 mamá|👩 papá|👨 bebé|👶 hijo|👦 hija|👧 abuela|👵 abuelo|👴 novio|💑 novia|💑 boda|💒 ",
            "comida|🍽️ comer|🍽️ desayuno|🍳 almuerzo|🍛 cena|🍽️ hambre|🍽️ pizza|🍕 hamburguesa|🍔 tacos|🌮 taco|🌮 pollo|🍗 carne|🥩 pescado|🐟 arroz|🍚 pan|🍞 queso|🧀 huevo|🥚 huevos|🥚 leche|🥛 jugo|🧃 agua|💧 bebida|🥤 cerveza|🍺 vino|🍷 café|☕ té|🍵 helado|🍦 pastel|🍰 chocolate|🍫 fruta|🍎 manzana|🍎 plátano|🍌 naranja|🍊 fresa|🍓 sandía|🍉 ensalada|🥗 sopa|🍲 postre|🍰 dulce|🍬 galleta|🍪 ",
            "casa|🏠 hogar|🏠 escuela|🏫 colegio|🏫 universidad|🎓 trabajo|💼 oficina|🏢 reunión|📅 hospital|🏥 doctor|👨‍⚕️ médico|👨‍⚕️ medicina|💊 farmacia|💊 tienda|🛒 compras|🛍️ restaurante|🍴 teléfono|📱 móvil|📱 celular|📱 llamada|📞 llamar|📞 mensaje|💬 correo|📧 llave|🔑 idea|💡 importante|❗ dinero|💰 plata|💰 pago|💳 coche|🚗 carro|🚗 auto|🚗 autobús|🚌 tren|🚆 avión|✈️ vuelo|✈️ viaje|🧳 playa|🏖️ mar|🌊 montaña|⛰️ ciudad|🏙️ ",
            "sol|☀️ calor|🥵 frío|🥶 lluvia|🌧️ llueve|🌧️ nieve|❄️ viento|💨 tormenta|⛈️ luna|🌙 estrella|⭐ noche|🌙 mañana|🌅 tarde|🌆 hoy|📅 fútbol|⚽ gol|⚽ partido|⚽ música|🎵 canción|🎶 baile|💃 bailar|💃 película|🎬 cine|🎬 libro|📚 leer|📖 estudiar|📚 examen|📝 tarea|📝 juego|🎮 jugar|🎮 foto|📸 fotos|📸 ",
            "perro|🐶 gato|🐱 caballo|🐴 vaca|🐄 pájaro|🐦 pez|🐟 león|🦁 oso|🐻 mono|🐒 conejo|🐰 flor|🌸 flores|🌹 rosa|🌹 árbol|🌳 fuego|🔥 victoria|🏆 ganar|🏆 campeón|🏆 fuerza|💪 fuerte|💪 ánimo|💪 suerte|🍀 bravo|👏 aplausos|👏 felicitaciones|👏 listo|👍 gol|⚽ ",
            "españa|🇪🇸 méxico|🇲🇽 mexico|🇲🇽 argentina|🇦🇷 colombia|🇨🇴 chile|🇨🇱 perú|🇵🇪 peru|🇵🇪 venezuela|🇻🇪 cuba|🇨🇺 ecuador|🇪🇨 bolivia|🇧🇴 uruguay|🇺🇾 paraguay|🇵🇾 brasil|🇧🇷 portugal|🇵🇹 estados unidos|🇺🇸 "
        );

    }

    static {
        addAllTo(FR_MAP,
            "amour|❤️ je t'aime|❤️ jtm|❤️ chéri|❤️ chérie|❤️ coeur|❤️ cœur|❤️ bisou|😘 bisous|😘 câlin|🤗 câlins|🤗 heureux|😊 heureuse|😊 content|😊 contente|😊 joie|😄 sourire|😊 rire|😂 mdr|😂 lol|😂 haha|😂 hahaha|😂 drôle|😂 triste|😢 tristesse|😢 pleurer|😭 pleure|😭 fâché|😠 fâchée|😠 colère|😡 peur|😨 surprise|😮 gêne|😳 fatigué|😴 fatiguée|😴 sommeil|😴 dormir|😴 ennui|😐 malade|🤒 fièvre|🤒 cool|😎 génial|🤩 super|🤩 incroyable|🤩 parfait|👌 bravo|👏",
            "merci|🙏 beaucoup|🙏 s'il te plaît|🙏 s'il vous plaît|🙏 svp|🙏 pardon|🙏 désolé|🙏 désolée|🙏 excuse|🙏 bienvenue|🤝 bonjour|👋 salut|👋 coucou|👋 au revoir|👋 adieu|👋 à bientôt|👋 bonne journée|☀️ bonsoir|🌆 bonne nuit|🌙 félicitations|🎉 anniversaire|🎂 fête|🎉 gâteau|🎂 cadeau|🎁 noël|🎄 dieu|🙏 prière|🙏 prier|🙏 ami|🤝 amie|🤝 amis|🤝 famille|👨‍👩‍👧 maman|👩 papa|👨 bébé|👶 fils|👦 fille|👧 grand-mère|👵 grand-père|👴 mariage|💒",
            "nourriture|🍽️ manger|🍽️ déjeuner|🍛 dîner|🍽️ petit-déjeuner|🍳 faim|🍽️ pizza|🍕 burger|🍔 poulet|🍗 viande|🥩 poisson|🐟 pain|🍞 baguette|🥖 croissant|🥐 fromage|🧀 gâteau|🍰 chocolat|🍫 glace|🍦 café|☕ thé|🍵 eau|💧 jus|🧃 lait|🥛 bière|🍺 vin|🍷 pomme|🍎 banane|🍌 fraise|🍓 orange|🍊 citron|🍋 pizza|🍕 frites|🍟 salade|🥗 riz|🍚 pâtes|🍝 oeuf|🥚 œuf|🥚",
            "maison|🏠 école|🏫 université|🎓 travail|💼 bureau|🏢 réunion|📅 hôpital|🏥 médecin|👨‍⚕️ docteur|👨‍⚕️ voiture|🚗 avion|✈️ train|🚆 vélo|🚲 bus|🚌 plage|🏖️ montagne|🏔️ ville|🏙️ voyage|🧳 vacances|🏝️ argent|💰 téléphone|📱 ordinateur|💻 musique|🎵 film|🎬 football|⚽ jeu|🎮 livre|📚",
            "soleil|☀️ chaud|🥵 froid|🥶 pluie|🌧️ pleut|🌧️ neige|❄️ vent|💨 orage|⛈️ lune|🌙 étoile|⭐ nuit|🌙 matin|🌅 soir|🌆 chien|🐶 chat|🐱 cheval|🐴 vache|🐄 oiseau|🐦 lion|🦁 ours|🐻 singe|🐒 lapin|🐰 fleur|🌸 fleurs|🌹 rose|🌹 arbre|🌳 feu|🔥 coeur|❤️",
            "france|🇫🇷 paris|🇫🇷 belgique|🇧🇪 suisse|🇨🇭 canada|🇨🇦 maroc|🇲🇦 algérie|🇩🇿 tunisie|🇹🇳 sénégal|🇸🇳 espagne|🇪🇸 italie|🇮🇹 allemagne|🇩🇪 arabie saoudite|🇸🇦 égypte|🇪🇬"
        );
    }

    private static void addAll(String... chunks) {
        addAllTo(MAP, chunks);
    }

    private static void addAllTo(Map<String, String> target, String... chunks) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([^|]+?)\\|(\\S+)(?:\\s+|$)");
        for (String chunk : chunks) {
            java.util.regex.Matcher m = p.matcher(chunk);
            while (m.find()) {
                String k = fold(m.group(1));
                if (!k.isEmpty() && !target.containsKey(k)) target.put(k, m.group(2));
            }
        }
    }

    private static void put(String word, String emoji) {
        MAP.put(fold(word), emoji);
    }

    static String suggestFor(String word) {
        return suggestFor(word, null);
    }

    static String suggestFor(String word, String langCode) {
        if (word == null || word.isEmpty()) return null;
        String k = fold(word);
        if (KeyboardPreferences.LANG_ES.equals(langCode)) {
            String e = ES_MAP.get(k);
            if (e != null) return e;
        }
        if (KeyboardPreferences.LANG_FR.equals(langCode)) {
            String e = FR_MAP.get(k);
            if (e != null) return e;
        }
        return MAP.get(k);
    }

    private static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c == 'أ' || c == 'إ' || c == 'آ') c = 'ا';
            else if (c == 'ى') c = 'ي';
            else if (c == 'ة') c = 'ه';
            else if (c >= 0x064B && c <= 0x0652) continue;
            else if (c == 'á') c = 'a';
            else if (c == 'é') c = 'e';
            else if (c == 'í') c = 'i';
            else if (c == 'ó') c = 'o';
            else if (c == 'ú' || c == 'ü') c = 'u';
            else if (c == 'ñ') c = 'n';
            else if (c == 'è' || c == 'ê' || c == 'ë') c = 'e';
            else if (c == 'à' || c == 'â') c = 'a';
            else if (c == 'î' || c == 'ï') c = 'i';
            else if (c == 'ô') c = 'o';
            else if (c == 'ù' || c == 'û') c = 'u';
            else if (c == 'ç') c = 'c';
            else if (c == 'œ') c = 'o';
            else if (c == '’') c = '\'';
            else if (c == 0x093C || c == 0x200C || c == 0x200D) continue;
            else if (c == 0x0901) c = (char) 0x0902;
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
