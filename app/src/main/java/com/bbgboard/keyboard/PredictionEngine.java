package com.bbgboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class PredictionEngine {

    private final SharedPreferences prefs;
    private final KeyboardPreferences keyboardPrefs;
    private final Map<String, Integer> learnedWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> learnedBigrams = new HashMap<>();
    // توقّع أدق للكلمة التالية بالاعتماد على آخر كلمتين (لا كلمة واحدة فقط)، ولاقتراح عبارة من كلمتين دفعة
    // واحدة. مفتاحها "fold(كلمة قبل الأخيرة)\u001Ffold(آخر كلمة)". تُحفظ بشكل دائم مثل باقي الكلمات المتعلمة
    // (trigrams_v1)، وفي حال غيابها (أول استخدام، أو ما توفر سياق كافٍ بعد) يرجع التوقع تلقائياً لنظام
    // الكلمة الواحدة الحالي بدون أي تأثير.
    private final Map<String, Map<String, Integer>> learnedTrigrams = new HashMap<>();

    private final Map<String, Integer> baseArWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseArBigrams = new HashMap<>();
    private final Map<String, Integer> baseEnWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseEnBigrams = new HashMap<>();

    // فهارس مرتّبة حسب الشكل المطبّع (fold) لإيجاد كلمات تبدأ ببادئة معينة بسرعة (بحث فرعي)
    // بدل مسح كل القاموس حرفاً حرفاً أثناء الكتابة — هذا هو الفرق الحقيقي بسرعة الاستجابة.
    private final java.util.TreeMap<String, List<String>> baseArByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseEnByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> learnedByFold = new java.util.TreeMap<>();

    private final java.util.Set<String> knownFolded = new java.util.HashSet<>();
    private volatile boolean learningAllowed = true;
    private volatile boolean dirty = true;
    private volatile String lastSavedWords = "";
    private volatile String lastSavedBigrams = "";
    private volatile String lastSavedTrigrams = "";

    // المحرك جاهز فقط بعد تحميل القواميس والكلمات المتعلمة (قد يتم على خيط خلفي عند بدء الكيبورد حتى لا يتأخر ظهوره).
    // قبل ذلك ترجع الدوال العامة نتائج فارغة بدون أن تلمس أي بنية بيانات.
    private volatile boolean ready = false;
    private volatile Runnable onReadyListener;

    // خيط حفظ واحد مشترك بدل إنشاء Thread جديد مع كل عملية حفظ، وقفل يمنع تداخل كتابتين على نفس الملف.
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-prefs-save");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Object SAVE_LOCK = new Object();
    private static final int BASE_TOP_K = 14;
    private static final Pattern EMAIL_EDGE_PUNCT = Pattern.compile("^[،.؟!؛:\\\",()]+|[،.؟!؛:\\\",()]+$");
    private static final Pattern ALL_PUNCT = Pattern.compile("[،.؟!؛:\\\",()]");
    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean saveScheduled = false;
    private final Runnable saveRunnable = this::performScheduledSave;

    // مرجع قوي للمستمع حتى لا يُحذف تلقائياً
    private final OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        String w = sp.getString("words_v1", "");
        String b = sp.getString("bigrams_v1", "");
        String t = sp.getString("trigrams_v1", "");
        if (!w.equals(lastSavedWords) || !b.equals(lastSavedBigrams) || !t.equals(lastSavedTrigrams)) dirty = true;
    };

    public PredictionEngine(Context context, KeyboardPreferences keyboardPrefs) {
        this(context, keyboardPrefs, false);
    }

    /**
     * @param asyncInit لو true يحمّل القواميس والكلمات المتعلمة على خيط خلفي (للكيبورد، حتى لا يتأخر ظهوره)،
     *                  ولو false يحمّلها قبل أن ينتهي الإنشاء (لشاشة الإعدادات التي تحتاج الأعداد فوراً).
     */
    public PredictionEngine(Context context, KeyboardPreferences keyboardPrefs, boolean asyncInit) {
        this.prefs = context.getSharedPreferences("bb_keyboard_learned_prefs", Context.MODE_PRIVATE);
        this.keyboardPrefs = keyboardPrefs;
        final Context appCtx = context.getApplicationContext();
        Runnable init = () -> initialize(appCtx);
        if (asyncInit) {
            Thread t = new Thread(init, "bkey-engine-init");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        } else {
            init.run();
        }
    }

    private void initialize(Context context) {
        initBaseDictionaries();
        loadBundledDictionary(context, "dict_ar.txt", baseArWords, 60, 20);
        loadBundledDictionary(context, "dict_en.txt", baseEnWords, 60, 20);
        buildFoldIndex(baseArWords, baseArByFold);
        buildFoldIndex(baseEnWords, baseEnByFold);
        // نستخدم نفس نصوص الفهرس (بدون إعادة حسابها ولا نسخها) لتقليل الذاكرة وزمن التحميل
        knownFolded.addAll(baseArByFold.keySet());
        knownFolded.addAll(baseEnByFold.keySet());
        synchronized (this) {
            loadFromPrefs();
            dirty = false;
            cleanupOldLearnedDataOnce();
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        ready = true;
        Runnable l = onReadyListener;
        if (l != null) l.run();
    }

    /** يُستدعى (على خيط المحرك أو فوراً لو جاهز) عند اكتمال التحميل؛ الكيبورد يستخدمه لتحديث الاقتراحات. */
    public void setOnReadyListener(Runnable listener) {
        this.onReadyListener = listener;
        if (ready && listener != null) listener.run();
    }

    /** قاموس عربي/إنجليزي مدمج بقوة عادية: أوزانه أقل من الكلمات التي تعلمها الكيبورد من كتابتك. */
    private void loadBundledDictionary(Context context, String assetName, Map<String, Integer> target, int maxWeight, int minWeight) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(context.getAssets().open(assetName), "UTF-8"), 32 * 1024);
            List<String> words = new ArrayList<>(32768);
            String line;
            while ((line = r.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty()) words.add(w);
            }
            r.close();
            // الملف مرتب من الأكثر استخداماً للأقل؛ الوزن ينزل بمنحنى لوغاريتمي مع الترتيب (بدل تساوي آلاف
            // الكلمات بنفس الوزن)، وكله أقل من وزن الكلمات التي تعلّمها الكيبورد من كتابتك.
            double denom = Math.log(1.0 + Math.max(words.size(), 2));
            for (int i = 0; i < words.size(); i++) {
                String w = words.get(i);
                if (target.containsKey(w)) continue;
                int weight = (int) Math.round(maxWeight - (maxWeight - minWeight) * (Math.log(1.0 + i) / denom));
                target.put(w, Math.max(minWeight, weight));
            }
        } catch (Exception ignored) {}
    }

    /** تنظيف لمرة واحدة: يحذف مما تعلمه سابقاً الأرقام والرموز والإيميلات الخاطئة والكلمات غير الصالحة. */
    private void cleanupOldLearnedDataOnce() {
        if (prefs.getBoolean("cleanup_v17_done", false)) return;
        try {
            Iterator<String> wi = learnedWords.keySet().iterator();
            while (wi.hasNext()) {
                if (!isLearnable(wi.next())) wi.remove();
            }
            Iterator<Map.Entry<String, Map<String, Integer>>> bi = learnedBigrams.entrySet().iterator();
            while (bi.hasNext()) {
                Map.Entry<String, Map<String, Integer>> e = bi.next();
                if (!isLearnable(e.getKey())) { bi.remove(); continue; }
                Iterator<String> ni = e.getValue().keySet().iterator();
                while (ni.hasNext()) {
                    if (!isLearnable(ni.next())) ni.remove();
                }
                if (e.getValue().isEmpty()) bi.remove();
            }
            rebuildLearnedIndex();
            saveToPrefs();
        } catch (Exception ignored) {}
        prefs.edit().putBoolean("cleanup_v17_done", true).apply();
    }

    /** كلمة موثوقة: من القاموس، أو استُخدمت مرتين فأكثر، أو بريد إلكتروني صحيح. */
    private boolean isTrustedWord(String w) {
        if (looksLikeEmail(w)) return true;
        if (knownFolded.contains(fold(w))) return true;
        Integer c = learnedWords.get(w);
        return c != null && c >= 2;
    }

    /** يُمنع التعلم في حقول كلمات المرور والبريد والروابط ووضع التخفي. */
    public void setLearningAllowed(boolean allowed) {
        this.learningAllowed = allowed;
    }

    /** تطبيع للمطابقة فقط: إزالة التشكيل والتطويل وتوحيد الألف والياء والهمزات وتصغير الإنجليزي. */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> FOLD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static String fold(String w) {
        if (w == null) return "";
        String cached = FOLD_CACHE.get(w);
        if (cached != null) return cached;
        String result = foldUncached(w);
        if (FOLD_CACHE.size() > 20000) FOLD_CACHE.clear();
        FOLD_CACHE.put(w, result);
        return result;
    }

    private static String foldUncached(String w) {
        StringBuilder sb = new StringBuilder(w.length());
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if ((c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640) continue;
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': sb.append('ا'); break;
                case 'ى': case 'ئ': sb.append('ي'); break;
                case 'ؤ': sb.append('و'); break;
                default: sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static boolean isLearnable(String w) {
        if (w == null) return false;
        if (looksLikeEmail(w)) return true;
        int len = w.length();
        if (len < 2 || len > 24) return false;
        for (int i = 0; i < len; i++) {
            char c = w.charAt(i);
            if (Character.isDigit(c)) return false;
            if (c == '@' || c == '/' || c == '\\' || c == ':' || c == '_' || c == '#' || c == '=' || c == '+' || c == '*') return false;
            if (c >= 0x0660 && c <= 0x0669) return false;
        }
        return true;
    }

    private String findExistingKey(String word) {
        if (learnedWords.containsKey(word)) return word;
        String f = fold(word);
        List<String> candidates = learnedByFold.get(f);
        if (candidates != null && !candidates.isEmpty()) return candidates.get(0);
        return null;
    }

    /** يبني فهرس (الشكل المطبّع ← الكلمات الأصلية) لقاموس كامل، مرة واحدة فقط عند التحميل. */
    private void buildFoldIndex(Map<String, Integer> words, java.util.TreeMap<String, List<String>> index) {
        index.clear();
        for (String w : words.keySet()) {
            String f = foldUncached(w); // بدون الكاش: عشرات الآلاف من الكلمات كانت تفرّغ كاش الكلمات المستخدمة باستمرار
            List<String> list = index.get(f);
            if (list == null) {
                list = new ArrayList<>(2);
                index.put(f, list);
            }
            list.add(w);
        }
    }

    /** يعيد بناء فهرس الكلمات المتعلمة بالكامل من learnedWords الحالية (يُستخدم بعد تحميل/تقليم/مسح جماعي فقط). */
    private void rebuildLearnedIndex() {
        learnedByFold.clear();
        for (String w : learnedWords.keySet()) {
            String f = fold(w);
            List<String> list = learnedByFold.get(f);
            if (list == null) {
                list = new ArrayList<>(1);
                learnedByFold.put(f, list);
            }
            list.add(w);
        }
    }

    private void initBaseDictionaries() {
        addArWord("السلام", 120); addArWord("عليكم", 115); addArWord("ورحمة", 100);
        addArWord("الله", 150); addArWord("وبركاته", 95); addArWord("بسم", 110);
        addArWord("الرحمن", 105); addArWord("الرحيم", 105); addArWord("الحمد", 100);
        addArWord("لله", 100); addArWord("مرحبا", 90); addArWord("أهلا", 88);
        addArWord("وسهلا", 85); addArWord("صباح", 95); addArWord("الخير", 98);
        addArWord("النور", 90); addArWord("مساء", 92); addArWord("الورد", 80);
        addArWord("كيف", 95); addArWord("حالك", 90); addArWord("الأحوال", 80);
        addArWord("أخبارك", 88); addArWord("إن", 110); addArWord("ان", 105);
        addArWord("شاء", 105); addArWord("ما", 95); addArWord("تبارك", 85);
        addArWord("جزاك", 88); addArWord("خيرا", 85); addArWord("شكرا", 95);
        addArWord("لك", 90); addArWord("جزيلا", 85); addArWord("يعطيك", 92);
        addArWord("العافية", 92); addArWord("ابغا", 90); addArWord("كيبورد", 85);
        addArWord("بلاك", 80); addArWord("بيري", 80); addArWord("اليوم", 88);
        addArWord("غدا", 82); addArWord("أنا", 95); addArWord("أنت", 85);
        addArWord("نحن", 80); addArWord("هذا", 92); addArWord("هذه", 88);
        addArWord("الذي", 82); addArWord("في", 110); addArWord("من", 110);
        addArWord("على", 105); addArWord("إلى", 100); addArWord("عن", 85);
        addArWord("مع", 90); addArWord("كل", 88); addArWord("عام", 85);
        addArWord("وأنتم", 84); addArWord("بخير", 90); addArWord("تمام", 88);
        addArWord("طيب", 85); addArWord("حياك", 86); addArWord("بشرني", 78);
        addArWord("عنك", 82); addArWord("الآن", 84); addArWord("بعد", 82);
        addArWord("ممتاز", 84); addArWord("رائع", 80); addArWord("جدا", 85);
        addArWord("اللهم", 92); addArWord("صل", 88); addArWord("وسلم", 88);
        addArWord("محمد", 95);

        addArBigram("السلام", "عليكم", 95); addArBigram("السلام", "الداخلي", 25); addArBigram("السلام", "في", 15);
        addArBigram("عليكم", "ورحمة", 90); addArBigram("عليكم", "جميعا", 45); addArBigram("عليكم", "كيف", 35);
        addArBigram("ورحمة", "الله", 98); addArBigram("ورحمة", "ربك", 25); addArBigram("ورحمة", "واسعة", 15);
        addArBigram("الله", "وبركاته", 85); addArBigram("الله", "يعطيك", 70); addArBigram("الله", "يجزاك", 65);
        addArBigram("بسم", "الله", 98); addArBigram("بسم", "ربك", 20);
        addArBigram("الرحمن", "الرحيم", 98); addArBigram("الرحمن", "على", 20);
        addArBigram("الحمد", "لله", 98); addArBigram("الحمد", "رب", 35);
        addArBigram("لله", "رب", 85); addArBigram("لله", "على", 60); addArBigram("لله", "دائما", 45);
        addArBigram("صباح", "الخير", 95); addArBigram("صباح", "النور", 80); addArBigram("صباح", "الورد", 70);
        addArBigram("مساء", "الخير", 95); addArBigram("مساء", "النور", 80); addArBigram("مساء", "الورد", 65);
        addArBigram("كيف", "حالك", 95); addArBigram("كيف", "أخبارك", 80); addArBigram("كيف", "الأمور", 65);
        addArBigram("حالك", "اليوم", 75); addArBigram("حالك", "يا", 65); addArBigram("حالك", "إن", 50);
        addArBigram("ان", "شاء", 95); addArBigram("ان", "كان", 45); addArBigram("ان", "الله", 40);
        addArBigram("إن", "شاء", 95); addArBigram("إن", "كان", 45); addArBigram("إن", "الله", 40);
        addArBigram("شاء", "الله", 99); addArBigram("شاء", "ربك", 30); addArBigram("شاء", "القدر", 20);
        addArBigram("ما", "شاء", 92); addArBigram("ما", "هو", 45); addArBigram("ما", "رأيك", 40);
        addArBigram("جزاك", "الله", 96); addArBigram("جزاك", "ربي", 45); addArBigram("جزاك", "خيرا", 40);
        addArBigram("يعطيك", "العافية", 96); addArBigram("يعطيك", "ألف", 60); addArBigram("يعطيك", "الصحة", 55);
        addArBigram("شكرا", "لك", 92); addArBigram("شكرا", "جزيلا", 85); addArBigram("شكرا", "على", 70);
        addArBigram("حياك", "الله", 95); addArBigram("حياك", "يا", 65); addArBigram("حياك", "في", 40);
        addArBigram("كل", "عام", 90); addArBigram("كل", "شيء", 65); addArBigram("كل", "يوم", 55);
        addArBigram("عام", "وأنتم", 90); addArBigram("عام", "وأنت", 85); addArBigram("عام", "جديد", 40);
        addArBigram("وأنتم", "بخير", 95); addArBigram("وأنتم", "بصحة", 60);
        addArBigram("اللهم", "صل", 92); addArBigram("اللهم", "آمين", 75); addArBigram("اللهم", "لك", 60);
        addArBigram("صل", "وسلم", 92); addArBigram("صل", "على", 80);

        addEnWord("The", 120); addEnWord("I", 115); addEnWord("On", 95);
        addEnWord("Hello", 90); addEnWord("How", 88); addEnWord("are", 85);
        addEnWord("you", 95); addEnWord("good", 85); addEnWord("morning", 82);
        addEnWord("thank", 90); addEnWord("thanks", 85); addEnWord("please", 85);
        addEnWord("keyboard", 80); addEnWord("BlackBerry", 85); addEnWord("to", 100);
        addEnWord("the", 110); addEnWord("and", 105); addEnWord("for", 95);
        addEnWord("with", 88); addEnWord("this", 90); addEnWord("that", 85);

        addEnBigram("the", "best", 70); addEnBigram("the", "next", 65); addEnBigram("the", "same", 60);
        addEnBigram("i", "am", 85); addEnBigram("i", "want", 80); addEnBigram("i", "have", 75);
        addEnBigram("on", "the", 85); addEnBigram("on", "my", 70); addEnBigram("on", "time", 60);
        addEnBigram("how", "are", 92); addEnBigram("how", "is", 75); addEnBigram("how", "about", 60);
        addEnBigram("are", "you", 95); addEnBigram("are", "we", 60); addEnBigram("are", "they", 55);
        addEnBigram("thank", "you", 98); addEnBigram("thank", "god", 60);
    }

    private void addArWord(String w, int weight) { baseArWords.put(w, weight); }
    private void addEnWord(String w, int weight) { baseEnWords.put(w, weight); }

    private void addArBigram(String prev, String next, int weight) {
        prev = fold(prev);
        Map<String, Integer> m = baseArBigrams.get(prev);
        if (m == null) { m = new HashMap<>(); baseArBigrams.put(prev, m); }
        m.put(next, weight);
    }

    private void addEnBigram(String prev, String next, int weight) {
        prev = fold(prev);
        Map<String, Integer> m = baseEnBigrams.get(prev);
        if (m == null) { m = new HashMap<>(); baseEnBigrams.put(prev, m); }
        m.put(next, weight);
    }

    private String normalize(String word) {
        if (word == null) return "";
        String t = word.trim();
        // البريد الإلكتروني: نحافظ على النقاط داخله ونزيل علامات الترقيم من أطرافه فقط
        if (t.indexOf('@') > 0) {
            return EMAIL_EDGE_PUNCT.matcher(t).replaceAll("");
        }
        return ALL_PUNCT.matcher(t).replaceAll("");
    }

    private static final java.util.Set<String> COMMON_TLDS = new java.util.HashSet<>(Arrays.asList(
            "com", "net", "org", "edu", "gov", "mil", "int", "info", "biz", "name", "pro",
            "io", "co", "me", "app", "dev", "xyz", "online", "site", "tech", "cloud", "ai", "store", "shop"));
    private static final java.util.Set<String> TYPO_DOMAINS = new java.util.HashSet<>(Arrays.asList(
            "gmial.com", "gmai.com", "gmail.con", "gmail.cm", "gmail.co", "gmail.om", "gmaill.com", "gamil.com",
            "gnail.com", "gmal.com", "gmail.vom", "gmail.xom", "hotmial.com", "hotmal.com", "hotmail.con",
            "hotmail.cm", "yaho.com", "yahooo.com", "yahoo.con", "outlok.com", "outllook.com", "outlook.con",
            "iclod.com", "icoud.com", "icloud.con"));

    /** بريد إلكتروني صحيح الصيغة فقط (يرفض الأخطاء الشائعة مثل gmail.con). */
    static boolean looksLikeEmail(String w) {
        if (w == null || w.length() > 64 || w.indexOf('@') < 1) return false;
        if (!w.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")) return false;
        int at = w.indexOf('@');
        String local = w.substring(0, at);
        String domain = w.substring(at + 1).toLowerCase(Locale.ROOT);
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) return false;
        if (domain.startsWith(".") || domain.startsWith("-") || domain.contains("..") || domain.contains(".-") || domain.contains("-.")) return false;
        if (TYPO_DOMAINS.contains(domain)) return false;
        String tld = domain.substring(domain.lastIndexOf('.') + 1);
        return tld.length() == 2 || COMMON_TLDS.contains(tld);
    }

    public synchronized void reloadFromStorage() {
        if (!ready) return;
        loadFromPrefs();
        dirty = false;
    }

    public void learnWordAndTransition(String currentWordRaw, String previousWordRaw, boolean isArabic) {
        learnWordAndTransition(currentWordRaw, previousWordRaw, "", isArabic);
    }

    /** نفس الدالة أعلاه، مع كلمة إضافية قبل previousWordRaw لتعلّم سياق أوسع (كلمتين سابقتين بدل واحدة). */
    public synchronized void learnWordAndTransition(String currentWordRaw, String previousWordRaw, String previousWord2Raw, boolean isArabic) {
        if (!ready) return;
        if (!learningAllowed || !keyboardPrefs.isAutoLearnEnabled() || keyboardPrefs.isIncognitoMode()) return;
        String curr = normalize(currentWordRaw);
        if (!isLearnable(curr)) return;

        String existing = findExistingKey(curr);
        String key = existing != null ? existing : curr;
        int oldCount = learnedWords.containsKey(key) ? learnedWords.get(key) : 0;
        learnedWords.put(key, oldCount + 1);
        if (existing == null) {
            // كلمة جديدة فعلاً (لم تكن موجودة بأي شكل مطبّع سابقاً): نضيفها لفهرس البحث السريع مباشرة.
            String kf = fold(key);
            List<String> list = learnedByFold.get(kf);
            if (list == null) {
                list = new ArrayList<>(1);
                learnedByFold.put(kf, list);
            }
            list.add(key);
        }

        String prev = normalize(previousWordRaw);
        if (isLearnable(prev)) {
            String prevKey = fold(prev);
            Map<String, Integer> transitions = learnedBigrams.get(prevKey);
            if (transitions == null) {
                transitions = new HashMap<>();
                learnedBigrams.put(prevKey, transitions);
            }
            String nextKey = key;
            for (String k : transitions.keySet()) {
                if (fold(k).equals(fold(curr))) { nextKey = k; break; }
            }
            int oldTrans = transitions.containsKey(nextKey) ? transitions.get(nextKey) : 0;
            transitions.put(nextKey, oldTrans + 1);
            pruneTransitions(transitions);

            String prev2 = normalize(previousWord2Raw);
            if (isLearnable(prev2)) {
                String triKey = fold(prev2) + "\u001F" + prevKey;
                Map<String, Integer> triTransitions = learnedTrigrams.get(triKey);
                if (triTransitions == null) {
                    if (learnedTrigrams.size() >= 1500) evictWeakestTrigram();
                    triTransitions = new HashMap<>();
                    learnedTrigrams.put(triKey, triTransitions);
                }
                String triNextKey = key;
                for (String k : triTransitions.keySet()) {
                    if (fold(k).equals(fold(curr))) { triNextKey = k; break; }
                }
                int oldTri = triTransitions.containsKey(triNextKey) ? triTransitions.get(triNextKey) : 0;
                triTransitions.put(triNextKey, oldTri + 1);
                pruneTransitions(triTransitions);
            }
        }
        pruneWords();
        scheduleSave();
    }

    /** يحذف أضعف عبارة ثلاثية (الأقل مجموع استخدام) عند وصول الفهرس لحده الأقصى، لمنع نموه بلا حدود. */
    private void evictWeakestTrigram() {
        String weakestKey = null;
        int weakestTotal = Integer.MAX_VALUE;
        for (Map.Entry<String, Map<String, Integer>> e : learnedTrigrams.entrySet()) {
            int total = 0;
            for (int v : e.getValue().values()) total += v;
            if (total < weakestTotal) {
                weakestTotal = total;
                weakestKey = e.getKey();
            }
        }
        if (weakestKey != null) learnedTrigrams.remove(weakestKey);
    }

    private void pruneTransitions(Map<String, Integer> t) {
        if (t.size() <= 16) return;
        String minKey = null; int min = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            if (e.getValue() < min) { min = e.getValue(); minKey = e.getKey(); }
        }
        if (minKey != null) t.remove(minKey);
    }

    private void pruneWords() {
        if (learnedWords.size() <= 8000) return;
        List<Map.Entry<String, Integer>> list = new ArrayList<>(learnedWords.entrySet());
        Collections.sort(list, (x, y) -> Integer.compare(y.getValue(), x.getValue()));
        Map<String, Integer> keep = new HashMap<>();
        for (int i = 0; i < 6000 && i < list.size(); i++) keep.put(list.get(i).getKey(), list.get(i).getValue());
        learnedWords.clear();
        learnedWords.putAll(keep);
        rebuildLearnedIndex();
    }

    public synchronized void learnFullSentence(String sentence) {
        if (sentence == null || !ready) return;
        String[] rawTokens = sentence.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String r : rawTokens) {
            String n = normalize(r);
            if (!n.isEmpty()) tokens.add(n);
        }
        for (int i = 0; i < tokens.size(); i++) {
            String curr = tokens.get(i);
            String prev = i > 0 ? tokens.get(i - 1) : "";
            boolean isAr = true;
            for (int c = 0; c < curr.length(); c++) {
                char ch = curr.charAt(c);
                if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                    isAr = false;
                    break;
                }
            }
            learnWordAndTransition(curr, prev, isAr);
        }
    }

    public List<String> getThreeSuggestions(String currentPrefixRaw, String lastCompletedWordRaw, boolean isArabic) {
        return getThreeSuggestions(currentPrefixRaw, lastCompletedWordRaw, "", isArabic);
    }

    /** نفس الدالة أعلاه، مع كلمة إضافية (ما قبل آخر كلمة) لتوقّع أدق واقتراح عبارات، واقتراح إيموجي للكلمة. */
    public synchronized List<String> getThreeSuggestions(String currentPrefixRaw, String lastCompletedWordRaw, String lastCompletedWord2Raw, boolean isArabic) {
        if (!ready) return placeholderSlots(isArabic);
        if (dirty) { loadFromPrefs(); dirty = false; }
        String prefix = normalize(currentPrefixRaw);
        String lastWord = normalize(lastCompletedWordRaw);
        Map<String, Integer> baseWords = isArabic ? baseArWords : baseEnWords;
        Map<String, Map<String, Integer>> baseBigrams = isArabic ? baseArBigrams : baseEnBigrams;

        final Map<String, Integer> scored = new HashMap<>();

        if (!prefix.isEmpty()) {
            String prefixLower = fold(prefix);

            String exactShortcutExpansion = keyboardPrefs.findShortcutExpansion(prefix);
            if (exactShortcutExpansion != null && !exactShortcutExpansion.isEmpty()) {
                scored.put(exactShortcutExpansion, 999999);
            }

            if (!lastWord.isEmpty()) {
                String prevKey = fold(lastWord);
                Map<String, Integer> lNext = learnedBigrams.get(prevKey);
                if (lNext == null) lNext = learnedBigrams.get(lastWord);
                if (lNext != null) {
                    for (Map.Entry<String, Integer> e : lNext.entrySet()) {
                        String nw = e.getKey();
                        if (fold(nw).startsWith(prefixLower) && !nw.equals(prefix) && isTrustedWord(nw)) {
                            int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                            scored.put(nw, cur + e.getValue() * 60 + 250);
                        }
                    }
                }
                Map<String, Integer> bNext = baseBigrams.get(prevKey);
                if (bNext != null) {
                    for (Map.Entry<String, Integer> e : bNext.entrySet()) {
                        String nw = e.getKey();
                        if (fold(nw).startsWith(prefixLower) && !nw.equals(prefix)) {
                            int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                            scored.put(nw, cur + e.getValue() * 3);
                        }
                    }
                }
            }

            // بحث فرعي (subMap) بدل مسح كل القاموس حرفياً: يعيد فقط الكلمات التي تبدأ فعلياً بنفس البادئة،
            // بنفس النتيجة تماماً كالمسح الكامل السابق لكن بدون فحص كل كلمة في الذاكرة مع كل حرف تكتبه.
            String prefixUpperBound = prefixLower + "￿";
            for (List<String> candidates : learnedByFold.subMap(prefixLower, prefixUpperBound).values()) {
                for (String w : candidates) {
                    if (!isTrustedWord(w)) continue;
                    Integer cnt = learnedWords.get(w);
                    if (cnt == null) continue;
                    int cur = scored.containsKey(w) ? scored.get(w) : 0;
                    scored.put(w, cur + cnt * 40 + 200);
                }
            }

            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : baseEnByFold;
            addTopBaseCandidates(baseByFold.subMap(prefixLower, prefixUpperBound).values(), baseWords, scored);

            // لو ما في ولا مطابقة ببادئة الكلمة (يعني غالباً خطأ إملائي)، نفتش عن كلمة قريبة جداً
            // (فرق حرف واحد فقط) من نفس القاموس، ونعرضها كاقتراح يُلمس يدوياً — بدون أي استبدال صامت.
            boolean hadCompletions = !scored.isEmpty();
            if (scored.isEmpty() && prefixLower.length() >= 3) {
                collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, scored, 30);
                if (scored.isEmpty()) {
                    collectNearMissCandidates(prefixLower, baseByFold, baseWords, scored, 1);
                }
            }

            List<String> sorted = sortKeysByScoreDesc(scored);
            // نضمن صراحة أن أعلى كلمة نقاطاً (الأكثر اختياراً) تبقى أول عنصر دائماً، فتستقر في منتصف الشريط
            // بلا شرط ولا استثناء حتى لو تغيّر ترتيب القائمة حولها؛ ما كتبته حرفياً يُضاف كخيار جانبي فقط،
            // ولا يُسمح له أبداً بإزاحة أفضل كلمة من مكانها الأول.
            List<String> ordered = new ArrayList<>();
            String prefixFolded = fold(prefix);
            if (!sorted.isEmpty()) ordered.add(sorted.get(0));
            // حتى لو في كلمات تكمل ما كتبته، نعرض التصحيح المحتمل (إن وُجد) في الخانة التالية
            if (hadCompletions) {
                String fix = findInlineFix(prefix, prefixLower, isArabic);
                if (fix != null && (ordered.isEmpty() || !fold(fix).equals(fold(ordered.get(0))))) {
                    ordered.add(fix);
                }
            }
            boolean prefixIsTop = !sorted.isEmpty() && fold(sorted.get(0)).equals(prefixFolded);
            if (!prefixIsTop) ordered.add(prefix);
            for (int i = 1; i < sorted.size() && ordered.size() < 3; i++) {
                String w = sorted.get(i);
                if (!fold(w).equals(prefixFolded)) ordered.add(w);
            }
            if (ordered.isEmpty()) ordered.add(prefix);
            return formatToThreeSlots(ordered, isArabic);
        } else if (!lastWord.isEmpty() && keyboardPrefs.isNextWordPredictionEnabled()) {
            String prevKey = fold(lastWord);
            String lastWord2 = normalize(lastCompletedWord2Raw);

            // تصحيح/توقّع أدق بالاعتماد على السياق (آخر كلمتين معاً لا كلمة واحدة فقط). لو ما توفر سياق
            // ثلاثي معروف (عبارة ما تعلّمها الكيبورد بعد)، يرجع تلقائياً لتوقّع الكلمة الواحدة أدناه بدون أي تغيير.
            String bestTrigramWord = null;
            if (!lastWord2.isEmpty()) {
                String triKey = fold(lastWord2) + "\u001F" + prevKey;
                Map<String, Integer> triNext = learnedTrigrams.get(triKey);
                if (triNext != null) {
                    int bestCount = -1;
                    for (Map.Entry<String, Integer> e : triNext.entrySet()) {
                        String nw = e.getKey();
                        if (!isTrustedWord(nw)) continue;
                        int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                        scored.put(nw, cur + e.getValue() * 80 + 400);
                        if (e.getValue() > bestCount) {
                            bestCount = e.getValue();
                            bestTrigramWord = nw;
                        }
                    }
                }
            }

            Map<String, Integer> lNext = learnedBigrams.get(prevKey);
                if (lNext == null) lNext = learnedBigrams.get(lastWord);
            if (lNext != null) {
                for (Map.Entry<String, Integer> e : lNext.entrySet()) {
                    String nw = e.getKey();
                    if (!isTrustedWord(nw)) continue;
                    int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                    scored.put(nw, cur + e.getValue() * 40 + 200);
                }
            }
            Map<String, Integer> bNext = baseBigrams.get(prevKey);
            if (bNext != null) {
                for (Map.Entry<String, Integer> e : bNext.entrySet()) {
                    String nw = e.getKey();
                    int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                    scored.put(nw, cur + e.getValue());
                }
            }

            // اقتراح عبارة من كلمتين دفعة واحدة: لو أقوى توقّع ثلاثي له امتداد شبه أكيد (كلمة ثالثة تتكرر
            // بوضوح بعده)، نعرض الكلمتين معاً كاقتراح واحد يُلمس فيُدخلهما دفعة واحدة.
            if (bestTrigramWord != null) {
                Map<String, Integer> chain = learnedBigrams.get(fold(bestTrigramWord));
                if (chain != null && !chain.isEmpty()) {
                    String chainBestWord = null; int chainBestCount = -1;
                    for (Map.Entry<String, Integer> e : chain.entrySet()) {
                        if (e.getValue() > chainBestCount) { chainBestCount = e.getValue(); chainBestWord = e.getKey(); }
                    }
                    if (chainBestWord != null && chainBestCount >= 3 && isTrustedWord(chainBestWord)) {
                        String phrase = bestTrigramWord + " " + chainBestWord;
                        scored.put(phrase, 100000); // عبارة شبه مؤكدة: أعلى أولوية من أي كلمة مفردة
                    }
                }
            }

            // (اقتراح الإيموجي ما عاد يدخل شريط الاقتراحات هنا؛ يظهر على زر الإيموجي نفسه بدل ذلك — راجع
            // BlackBerryImeService.refreshPredictionsNow و setEmojiKeySuggestion.)

            List<String> sorted = sortKeysByScoreDesc(scored);
            return formatToThreeSlots(sorted, isArabic);
        }

        return formatToThreeSlots(Collections.<String>emptyList(), isArabic);
    }

    /**
     * يضيف من قاموس كبير أفضل BASE_TOP_K كلمة فقط (بحسب وزنها + نقاطها الحالية) بدل إدخال آلاف الكلمات في
     * خريطة النقاط وترتيبها كلها مع كل حرف، خصوصاً مع البادئات القصيرة (حرف أو حرفين) في قاموس بحجم 30 ألف.
     */
    private void addTopBaseCandidates(Collection<List<String>> groups, Map<String, Integer> baseWords, Map<String, Integer> scored) {
        PriorityQueue<Map.Entry<String, Integer>> heap = new PriorityQueue<>(BASE_TOP_K + 1,
                (a, b) -> Integer.compare(a.getValue(), b.getValue()));
        for (List<String> candidates : groups) {
            for (String w : candidates) {
                Integer cnt = baseWords.get(w);
                if (cnt == null) continue;
                Integer existing = scored.get(w);
                int total = (existing != null ? existing : 0) + cnt;
                if (heap.size() < BASE_TOP_K) {
                    heap.add(new java.util.AbstractMap.SimpleEntry<>(w, total));
                } else if (total > heap.peek().getValue()) {
                    heap.poll();
                    heap.add(new java.util.AbstractMap.SimpleEntry<>(w, total));
                }
            }
        }
        for (Map.Entry<String, Integer> e : heap) scored.put(e.getKey(), e.getValue());
    }

    /** يفتش في فهرس كلمات معيّن عن كلمات قريبة جداً (فرق حرف واحد فقط: استبدال/حذف/إضافة) من البادئة
     *  المكتوبة، ويضيفها لقائمة النقاط بوزن منخفض ثابت حتى تظهر كاقتراح فقط دون أن تطغى على أي مطابقة حقيقية. */
    /** يرجع تصحيحاً تلقائياً للكلمة لو كانت قريبة جداً (فرق حرف واحد فقط) من كلمة معروفة موثوقة، أو null
     *  لو الكلمة نفسها معروفة أصلاً (ما تحتاج تصحيح) أو ما لقينا تصحيحاً واضحاً. يُستخدم فقط عند تفعيل
     *  التصحيح التلقائي الصامت من الإعدادات؛ لا علاقة له بشريط الاقتراحات العادي. */
    public synchronized String findAutoCorrection(String wordRaw, boolean isArabic) {
        if (!ready) return null;
        String word = normalize(wordRaw);
        if (word.length() < 3 || isTrustedWord(word)) return null;
        String wordLower = fold(word);
        Map<String, Integer> candidates = new HashMap<>();
        collectNearMissCandidates(wordLower, learnedByFold, learnedWords, candidates, 30);
        if (candidates.isEmpty()) {
            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : baseEnByFold;
            Map<String, Integer> baseWords = isArabic ? baseArWords : baseEnWords;
            collectNearMissCandidates(wordLower, baseByFold, baseWords, candidates, 1);
        }
        if (candidates.isEmpty()) return null;
        return sortKeysByScoreDesc(candidates).get(0);
    }

    /**
     * تصحيح محتمل للكلمة المكتوبة حتى لو كانت تبدأ بها كلمات أخرى (يعني ما نقدر نعرف إنها خطأ إلا بالمقارنة).
     * لتجنب الإزعاج: لا يُعرض لو الكلمة المكتوبة معروفة أصلاً، ولا يُعرض ما هو مجرد إكمال لما كتبته (يظهر أصلاً)،
     * والكلمات من 3 حروف لا يُقترح لها إلا قلب حرفين متجاورين، ومن 4 فأكثر تُقبل كل أنواع الخطأ.
     */
    private String findInlineFix(String prefix, String prefixLower, boolean isArabic) {
        if (prefixLower.length() < 3 || isTrustedWord(prefix)) return null;
        Map<String, Integer> cand = new HashMap<>();
        collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, cand, 30);
        if (cand.isEmpty()) {
            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : baseEnByFold;
            Map<String, Integer> baseWords = isArabic ? baseArWords : baseEnWords;
            collectNearMissCandidates(prefixLower, baseByFold, baseWords, cand, 1);
        }
        for (java.util.Iterator<String> it = cand.keySet().iterator(); it.hasNext(); ) {
            String w = it.next();
            String wf = fold(w);
            boolean drop = wf.startsWith(prefixLower)
                    || (prefixLower.length() == 3 && !isAdjacentSwap(prefixLower, wf));
            if (drop) it.remove();
        }
        if (cand.isEmpty()) return null;
        return sortKeysByScoreDesc(cand).get(0);
    }

    /** صحيح لو الكلمتان بنفس الطول وتختلفان فقط بقلب حرفين متجاورين. */
    private static boolean isAdjacentSwap(String a, String b) {
        if (a.length() != b.length()) return false;
        int i = 0, n = a.length();
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        if (i >= n - 1) return false;
        if (a.charAt(i) != b.charAt(i + 1) || a.charAt(i + 1) != b.charAt(i)) return false;
        return a.regionMatches(i + 2, b, i + 2, n - i - 2);
    }

    /**
     * يجمع كلمات قريبة من الكلمة المكتوبة (احتمال خطأ إملائي):
     * - فرق تعديل واحد: استبدال حرف، حذف، إضافة، أو حرفان متجاوران مقلوبان (مثل "الىن" بدل "الني").
     * - فرق تعديلين: فقط للكلمات من 5 حروف فأكثر وبشرط أن يتطابق الحرف الأول (الخطأ في أول حرف نادر)،
     *   وتأتي بعد كل كلمة من فرق واحد في الترتيب. داخل نفس الفرق يتقدم الأكثر استخداماً.
     */
    private void collectNearMissCandidates(String prefixLower, java.util.TreeMap<String, List<String>> byFold,
                                            Map<String, Integer> wordCounts, Map<String, Integer> scored, int baseScore) {
        final int plen = prefixLower.length();
        final int maxDist = plen >= 5 ? 2 : 1;
        for (Map.Entry<String, List<String>> entry : byFold.entrySet()) {
            String key = entry.getKey();
            if (Math.abs(key.length() - plen) > maxDist) continue;
            int dist = editDistance(prefixLower, key, maxDist);
            if (dist < 1 || dist > maxDist) continue;
            if (dist == 2 && key.charAt(0) != prefixLower.charAt(0)) continue;
            for (String w : entry.getValue()) {
                Integer cnt = wordCounts.get(w);
                if (cnt == null) continue;
                if (!isTrustedWord(w)) continue;
                int cur = scored.containsKey(w) ? scored.get(w) : 0;
                scored.put(w, cur + baseScore + (dist == 1 ? 100000 : 0) + Math.min(cnt, 99999));
            }
        }
    }

    /**
     * مسافة التعديل (Damerau-Levenshtein المقيّدة): استبدال/حذف/إضافة/قلب حرفين متجاورين كلٌّ بتعديل واحد.
     * ترجع max+1 لو المسافة أكبر من max (مع خروج مبكر لتوفير الوقت).
     */
    private static int editDistance(String a, String b, int max) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > max) return max + 1;
        int[] prev2 = new int[lb + 1];
        int[] prev = new int[lb + 1];
        int[] cur = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > max) return max + 1;
            int[] t = prev2; prev2 = prev; prev = cur; cur = t;
        }
        return prev[lb];
    }

    private List<String> sortKeysByScoreDesc(final Map<String, Integer> scored) {
        List<String> keys = new ArrayList<>(scored.keySet());
        Collections.sort(keys, (a, b) -> {
            int sa = scored.containsKey(a) ? scored.get(a) : 0;
            int sb = scored.containsKey(b) ? scored.get(b) : 0;
            if (sa != sb) return Integer.compare(sb, sa);
            // تعادل: الأقصر أولاً ثم أبجدياً (ترتيب ثابت بدل ترتيب عشوائي حسب الـHashMap)
            if (a.length() != b.length()) return Integer.compare(a.length(), b.length());
            return a.compareTo(b);
        });
        return keys;
    }

    private static boolean hasArabicLetters(String w) {
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    /** أكثر الكلمات المتعلمة استخداماً باللغة الحالية (يستثني ما استُخدم مرة واحدة والإيميلات). */
    private List<String> getTopLearnedWords(boolean isArabic, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(learnedWords.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= limit) break;
            String w = e.getKey();
            if (e.getValue() < 2 || w.indexOf('@') >= 0) continue;
            if (hasArabicLetters(w) != isArabic) continue;
            out.add(w);
        }
        return out;
    }

    /** الكلمات الثابتة الافتراضية لهذه اللغة، باستثناء ما أزاله المستخدم بضغطة مطولة. */
    private List<String> activeDefaultWords(boolean isArabic) {
        List<String> all = isArabic
                ? Arrays.asList("السلام", "بسم", "مرحبا", "إن", "صباح", "الحمد")
                : Arrays.asList("The", "I", "On", "Hello", "Thanks", "Good");
        java.util.Set<String> dismissed = keyboardPrefs.getDismissedDefaultWords(isArabic);
        if (dismissed.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String w : all) if (!dismissed.contains(w)) out.add(w);
        return out;
    }

    /** true إذا كانت الكلمة من القائمة الثابتة (وليست كلمة حقيقية اقترحها التعلّم أو القاموس)، ليقرر الضغط المطول إزالتها. */
    public boolean isDefaultPlaceholderWord(String word, boolean isArabic) {
        if (word == null) return false;
        for (String d : activeDefaultWords(isArabic)) {
            if (d.equals(word)) return true;
        }
        return false;
    }

    /** اقتراحات افتراضية فقط (قبل اكتمال تحميل المحرك)، بدون لمس أي بيانات متعلمة قيد التحميل. */
    private List<String> placeholderSlots(boolean isArabic) {
        return Arrays.asList("", "", "");
    }

    private List<String> formatToThreeSlots(List<String> candidates, boolean isArabic) {
        List<String> combined = new ArrayList<>();
        java.util.Set<String> seenFolded = new java.util.HashSet<>();
        for (String c : candidates) {
            if (seenFolded.add(fold(c))) combined.add(c);
        }
        // الخانات الناقصة تُملأ بكلماتك الأكثر استخداماً (الأعلى استخداماً أولاً) قبل القائمة الثابتة
        for (String t : getTopLearnedWords(isArabic, 8)) {
            if (combined.size() >= 3) break;
            if (seenFolded.add(fold(t))) combined.add(t);
        }
        // لا كلمات ثابتة: الخانات الناقصة تبقى فاضية (تظهر فقط كلماتك المتعلمة وما يناسب السياق)
        String primaryCenter = combined.size() > 0 ? combined.get(0) : "";
        String leftSlot = combined.size() > 1 ? combined.get(1) : "";
        String rightSlot = combined.size() > 2 ? combined.get(2) : "";
        return Arrays.asList(leftSlot, primaryCenter, rightSlot);
    }

    public synchronized int getLearnedWordCount() {
        reloadFromStorage();
        return learnedWords.size();
    }

    public synchronized List<Map.Entry<String, Integer>> getLearnedWordsSorted() {
        reloadFromStorage();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(learnedWords.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    public synchronized List<String> getLearnedBigramsFormatted() {
        reloadFromStorage();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : learnedBigrams.entrySet()) {
            String prev = e.getKey();
            Map<String, Integer> nextMap = e.getValue();
            if (nextMap == null || nextMap.isEmpty()) continue;
            List<Map.Entry<String, Integer>> sortedNext = new ArrayList<>(nextMap.entrySet());
            Collections.sort(sortedNext, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(4, sortedNext.size()); i++) {
                if (i > 0) sb.append(" ، ");
                sb.append(sortedNext.get(i).getKey()).append(" (×").append(sortedNext.get(i).getValue()).append(")");
            }
            result.add(prev + "  ⬅  " + sb.toString());
        }
        return result;
    }

    public static class LearnedEntry {
        public final String word;
        public final int count;
        public final String nextSummary;
        LearnedEntry(String word, int count, String nextSummary) {
            this.word = word;
            this.count = count;
            this.nextSummary = nextSummary;
        }
    }

    /** قائمة موحدة: كل كلمة متعلمة مع عدد مرات استخدامها وأكثر كلمات تأتي بعدها. */
    public synchronized List<LearnedEntry> getLearnedEntries() {
        loadFromPrefs();
        dirty = false;
        List<LearnedEntry> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : learnedWords.entrySet()) {
            Map<String, Integer> nexts = learnedBigrams.get(fold(e.getKey()));
            if (nexts == null) nexts = learnedBigrams.get(e.getKey());
            String summary = "";
            if (nexts != null && !nexts.isEmpty()) {
                List<Map.Entry<String, Integer>> sn = new ArrayList<>(nexts.entrySet());
                Collections.sort(sn, (x, y) -> Integer.compare(y.getValue(), x.getValue()));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, sn.size()); i++) {
                    if (i > 0) sb.append(" ، ");
                    sb.append(sn.get(i).getKey());
                }
                summary = sb.toString();
            }
            out.add(new LearnedEntry(e.getKey(), e.getValue(), summary));
        }
        Collections.sort(out, (x, y) -> Integer.compare(y.count, x.count));
        return out;
    }

    public synchronized void removeLearnedWord(String word) {
        String f = fold(word);
        learnedWords.remove(word);
        learnedBigrams.remove(f);
        learnedBigrams.remove(word);
        for (Map<String, Integer> t : learnedBigrams.values()) {
            Iterator<String> it = t.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals(word) || fold(k).equals(f)) it.remove();
            }
        }
        rebuildLearnedIndex();
        saveToPrefs();
    }

    public synchronized void clearAllLearnedData() {
        learnedWords.clear();
        learnedBigrams.clear();
        learnedTrigrams.clear();
        learnedByFold.clear();
        saveHandler.removeCallbacks(saveRunnable);
        saveScheduled = false;
        prefs.edit().clear().apply();
    }

    /**
     * حفظ فوري ومتزامن (يُستخدم فقط عند إجراءات نادرة: حذف كلمة، التنظيف لمرة واحدة، أو عند إغلاق
     * الكيبورد عبر flushPendingSave — وليس أثناء الكتابة العادية حتى لا يجمّد الاستجابة).
     */
    private void saveToPrefs() {
        writeSnapshotToPrefs(new HashMap<>(learnedWords), deepCopyBigrams(learnedBigrams), deepCopyBigrams(learnedTrigrams));
    }

    private Map<String, Map<String, Integer>> deepCopyBigrams(Map<String, Map<String, Integer>> source) {
        Map<String, Map<String, Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : source.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }

    /** يؤجل الحفظ قليلاً (ويجمع عدة كلمات متتالية في عملية حفظ واحدة) بدل الحفظ مع كل كلمة أثناء الكتابة. */
    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        saveHandler.postDelayed(saveRunnable, 900L);
    }

    private void performScheduledSave() {
        saveScheduled = false;
        saveToPrefsAsync();
    }

    /** كل شيء على خيط الحفظ المشترك: النسخ (تحت القفل لحظياً) ثم بناء الـ JSON وكتابته خارج القفل وبعيداً عن خيط الكتابة. */
    private void saveToPrefsAsync() {
        SAVE_EXECUTOR.execute(() -> {
            final Map<String, Integer> wordsSnapshot;
            final Map<String, Map<String, Integer>> bigramsSnapshot;
            final Map<String, Map<String, Integer>> trigramsSnapshot;
            synchronized (PredictionEngine.this) {
                wordsSnapshot = new HashMap<>(learnedWords);
                bigramsSnapshot = deepCopyBigrams(learnedBigrams);
                trigramsSnapshot = deepCopyBigrams(learnedTrigrams);
            }
            writeSnapshotToPrefs(wordsSnapshot, bigramsSnapshot, trigramsSnapshot);
        });
    }

    private JSONObject bigramsToJson(Map<String, Map<String, Integer>> bigrams) throws org.json.JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Map<String, Integer>> e : bigrams.entrySet()) {
            JSONObject inner = new JSONObject();
            for (Map.Entry<String, Integer> ne : e.getValue().entrySet()) {
                inner.put(ne.getKey(), ne.getValue());
            }
            json.put(e.getKey(), inner);
        }
        return json;
    }

    private void writeSnapshotToPrefs(Map<String, Integer> words, Map<String, Map<String, Integer>> bigrams,
                                       Map<String, Map<String, Integer>> trigrams) {
        synchronized (SAVE_LOCK) {
        try {
            JSONObject wordsJson = new JSONObject();
            for (Map.Entry<String, Integer> e : words.entrySet()) {
                wordsJson.put(e.getKey(), e.getValue());
            }

            String ws = wordsJson.toString();
            String bs = bigramsToJson(bigrams).toString();
            String ts = bigramsToJson(trigrams).toString();
            lastSavedWords = ws;
            lastSavedBigrams = bs;
            lastSavedTrigrams = ts;
            prefs.edit()
                    .putString("words_v1", ws)
                    .putString("bigrams_v1", bs)
                    .putString("trigrams_v1", ts)
                    .apply();
        } catch (Exception ignored) {}
        }
    }

    /** يحفظ أي بيانات مؤجلة على خيط الحفظ (بدون ما يوقف الخيط الرئيسي)؛ يُستدعى عند إخفاء الكيبورد. */
    public synchronized void flushPendingSave() {
        if (!saveScheduled) return;
        saveHandler.removeCallbacks(saveRunnable);
        saveScheduled = false;
        saveToPrefsAsync();
    }

    /** نفس الدالة لكن متزامنة تماماً؛ تُستخدم فقط عند إغلاق الخدمة نفسها حتى لا يضيع شيء. */
    public synchronized void flushPendingSaveBlocking() {
        if (!saveScheduled) return;
        saveHandler.removeCallbacks(saveRunnable);
        saveScheduled = false;
        saveToPrefs();
    }

    private void loadFromPrefs() {
        try {
            learnedWords.clear();
            learnedBigrams.clear();
            learnedTrigrams.clear();
            String wordsStr = prefs.getString("words_v1", null);
            if (wordsStr != null && !wordsStr.isEmpty()) {
                JSONObject obj = new JSONObject(wordsStr);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    learnedWords.put(k, obj.optInt(k, 1));
                }
            }
            String bigramsStr = prefs.getString("bigrams_v1", null);
            if (bigramsStr != null && !bigramsStr.isEmpty()) {
                JSONObject obj = new JSONObject(bigramsStr);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String prev = keys.next();
                    JSONObject inner = obj.optJSONObject(prev);
                    if (inner != null) {
                        Map<String, Integer> map = new HashMap<>();
                        Iterator<String> nKeys = inner.keys();
                        while (nKeys.hasNext()) {
                            String nw = nKeys.next();
                            map.put(nw, inner.optInt(nw, 1));
                        }
                        learnedBigrams.put(prev, map);
                    }
                }
            }
            String trigramsStr = prefs.getString("trigrams_v1", null);
            if (trigramsStr != null && !trigramsStr.isEmpty()) {
                JSONObject obj = new JSONObject(trigramsStr);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String triKey = keys.next();
                    JSONObject inner = obj.optJSONObject(triKey);
                    if (inner != null) {
                        Map<String, Integer> map = new HashMap<>();
                        Iterator<String> nKeys = inner.keys();
                        while (nKeys.hasNext()) {
                            String nw = nKeys.next();
                            map.put(nw, inner.optInt(nw, 1));
                        }
                        learnedTrigrams.put(triKey, map);
                    }
                }
            }
        } catch (Exception ignored) {}
        rebuildLearnedIndex();
    }
}
