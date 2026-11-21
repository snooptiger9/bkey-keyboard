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

    private final Map<String, Map<String, Integer>> learnedTrigrams = new HashMap<>();

    private final Map<String, Integer> baseArWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseArBigrams = new HashMap<>();
    private final Map<String, Integer> baseEnWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseEnBigrams = new HashMap<>();
    private final Map<String, Integer> baseEsWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseEsBigrams = new HashMap<>();

    private final java.util.TreeMap<String, List<String>> baseArByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseEnByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseEsByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> learnedByFold = new java.util.TreeMap<>();

    // Which Latin-script language is active right now (everything above is keyed on isArabic
    // vs not-Arabic; this picks which non-Arabic dataset "not Arabic" actually means).
    private volatile boolean latinIsSpanish = false;

    public void setActiveLatinLanguage(String languageCode) {
        latinIsSpanish = KeyboardPreferences.LANG_ES.equals(languageCode);
    }

    private Map<String, Integer> currentLatinWords() { return latinIsSpanish ? baseEsWords : baseEnWords; }
    private Map<String, Map<String, Integer>> currentLatinBigrams() { return latinIsSpanish ? baseEsBigrams : baseEnBigrams; }
    private java.util.TreeMap<String, List<String>> currentLatinByFold() { return latinIsSpanish ? baseEsByFold : baseEnByFold; }

    // learnedWords/learnedBigrams/learnedByFold below are shared across every language --
    // that's fine between Arabic and Latin (different Unicode ranges keep them apart
    // naturally), but English and Spanish share the same letters, so a word learned under
    // one would otherwise show up as a suggestion under the other. This tags each learned
    // Latin word with which one it was learned under, so suggestions can filter by it.
    // Untagged (anything learned before Spanish existed, or not yet re-typed since) defaults
    // to English -- the only Latin language that could have written it.
    private final Map<String, String> learnedWordLatinTag = new HashMap<>();

    private void tagLatinWord(String word, boolean isArabic) {
        if (isArabic) return;
        learnedWordLatinTag.put(word, latinIsSpanish ? KeyboardPreferences.LANG_ES : KeyboardPreferences.LANG_EN);
    }

    private boolean latinWordAllowed(String word) {
        String tag = learnedWordLatinTag.get(word);
        String active = latinIsSpanish ? KeyboardPreferences.LANG_ES : KeyboardPreferences.LANG_EN;
        return tag == null ? !latinIsSpanish : tag.equals(active);
    }

    private final java.util.Set<String> knownFolded = new java.util.HashSet<>();
    private volatile boolean learningAllowed = true;
    private volatile boolean dirty = true;
    private volatile String lastSavedWords = "";
    private volatile String lastSavedBigrams = "";
    private volatile String lastSavedTrigrams = "";

    private volatile boolean ready = false;
    private volatile Runnable onReadyListener;

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

    private final OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        String w = sp.getString("words_v1", "");
        String b = sp.getString("bigrams_v1", "");
        String t = sp.getString("trigrams_v1", "");
        if (!w.equals(lastSavedWords) || !b.equals(lastSavedBigrams) || !t.equals(lastSavedTrigrams)) dirty = true;
    };

    public PredictionEngine(Context context, KeyboardPreferences keyboardPrefs) {
        this(context, keyboardPrefs, false);
    }

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
        loadBundledDictionary(context, "dict_es.txt", baseEsWords, 60, 20);
        buildFoldIndex(baseArWords, baseArByFold);
        buildFoldIndex(baseEnWords, baseEnByFold);
        buildFoldIndex(baseEsWords, baseEsByFold);

        knownFolded.addAll(baseArByFold.keySet());
        knownFolded.addAll(baseEnByFold.keySet());
        knownFolded.addAll(baseEsByFold.keySet());
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

    public void setOnReadyListener(Runnable listener) {
        this.onReadyListener = listener;
        if (ready && listener != null) listener.run();
    }

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

            double denom = Math.log(1.0 + Math.max(words.size(), 2));
            for (int i = 0; i < words.size(); i++) {
                String w = words.get(i);
                if (target.containsKey(w)) continue;
                int weight = (int) Math.round(maxWeight - (maxWeight - minWeight) * (Math.log(1.0 + i) / denom));
                target.put(w, Math.max(minWeight, weight));
            }
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "loadBundledDictionary()", e);
        }
    }

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
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "cleanupOldLearnedDataOnce()", e);
        }
        prefs.edit().putBoolean("cleanup_v17_done", true).apply();
    }

    private boolean isTrustedWord(String w) {
        if (looksLikeEmail(w)) return true;
        if (knownFolded.contains(fold(w))) return true;
        Integer c = learnedWords.get(w);
        return c != null && c >= 2;
    }

    public void setLearningAllowed(boolean allowed) {
        this.learningAllowed = allowed;
    }

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

    private void buildFoldIndex(Map<String, Integer> words, java.util.TreeMap<String, List<String>> index) {
        index.clear();
        for (String w : words.keySet()) {
            String f = foldUncached(w);
            List<String> list = index.get(f);
            if (list == null) {
                list = new ArrayList<>(2);
                index.put(f, list);
            }
            list.add(w);
        }
    }

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

    public synchronized void learnWordAndTransition(String currentWordRaw, String previousWordRaw, String previousWord2Raw, boolean isArabic) {
        if (!ready) return;
        if (!learningAllowed || !keyboardPrefs.isAutoLearnEnabled() || keyboardPrefs.isIncognitoMode()) return;
        String curr = normalize(currentWordRaw);
        if (!isLearnable(curr)) return;

        String existing = findExistingKey(curr);
        String key = existing != null ? existing : curr;
        int oldCount = learnedWords.containsKey(key) ? learnedWords.get(key) : 0;
        learnedWords.put(key, oldCount + 1);
        tagLatinWord(key, isArabic);
        if (existing == null) {

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
        if (t.size() <= 32) return;
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
        learnedWordLatinTag.keySet().retainAll(keep.keySet());
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

    public synchronized List<String> getThreeSuggestions(String currentPrefixRaw, String lastCompletedWordRaw, String lastCompletedWord2Raw, boolean isArabic) {
        if (!ready) return placeholderSlots(isArabic);
        if (dirty) { loadFromPrefs(); dirty = false; }
        String prefix = normalize(currentPrefixRaw);
        String lastWord = normalize(lastCompletedWordRaw);
        Map<String, Integer> baseWords = isArabic ? baseArWords : currentLatinWords();
        Map<String, Map<String, Integer>> baseBigrams = isArabic ? baseArBigrams : currentLatinBigrams();

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
                        if (fold(nw).startsWith(prefixLower) && !nw.equals(prefix) && isTrustedWord(nw)
                                && (isArabic || latinWordAllowed(nw))) {
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

            String prefixUpperBound = prefixLower + "￿";
            for (List<String> candidates : learnedByFold.subMap(prefixLower, prefixUpperBound).values()) {
                for (String w : candidates) {
                    if (!isTrustedWord(w)) continue;
                    if (!isArabic && !latinWordAllowed(w)) continue;
                    Integer cnt = learnedWords.get(w);
                    if (cnt == null) continue;
                    int cur = scored.containsKey(w) ? scored.get(w) : 0;
                    scored.put(w, cur + cnt * 40 + 200);
                }
            }

            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : currentLatinByFold();
            addTopBaseCandidates(baseByFold.subMap(prefixLower, prefixUpperBound).values(), baseWords, scored);

            boolean hadCompletions = !scored.isEmpty();
            if (scored.isEmpty() && prefixLower.length() >= 3) {
                collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, scored, 30, !isArabic);
                if (scored.isEmpty()) {
                    collectNearMissCandidates(prefixLower, baseByFold, baseWords, scored, 1);
                }
            }

            List<String> sorted = sortKeysByScoreDesc(scored);

            List<String> ordered = new ArrayList<>();
            String prefixFolded = fold(prefix);
            if (!sorted.isEmpty()) ordered.add(sorted.get(0));

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

            String bestTrigramWord = null;
            if (!lastWord2.isEmpty()) {
                String triKey = fold(lastWord2) + "\u001F" + prevKey;
                Map<String, Integer> triNext = learnedTrigrams.get(triKey);
                if (triNext != null) {
                    int bestCount = -1;
                    for (Map.Entry<String, Integer> e : triNext.entrySet()) {
                        String nw = e.getKey();
                        if (!isTrustedWord(nw)) continue;
                        if (!isArabic && !latinWordAllowed(nw)) continue;
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
                    if (!isArabic && !latinWordAllowed(nw)) continue;
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

            if (bestTrigramWord != null) {
                Map<String, Integer> chain = learnedBigrams.get(fold(bestTrigramWord));
                if (chain != null && !chain.isEmpty()) {
                    String chainBestWord = null; int chainBestCount = -1;
                    for (Map.Entry<String, Integer> e : chain.entrySet()) {
                        if (e.getValue() > chainBestCount) { chainBestCount = e.getValue(); chainBestWord = e.getKey(); }
                    }
                    if (chainBestWord != null && chainBestCount >= 3 && isTrustedWord(chainBestWord)
                            && (isArabic || latinWordAllowed(chainBestWord))) {
                        String phrase = bestTrigramWord + " " + chainBestWord;
                        scored.put(phrase, 100000);
                    }
                }
            }

            List<String> sorted = sortKeysByScoreDesc(scored);
            return formatToThreeSlots(sorted, isArabic);
        }

        return formatToThreeSlots(Collections.<String>emptyList(), isArabic);
    }

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

    /** Tokens the user typed on purpose in a non-standard form: NASA, iPhone, @mentions, #tags,
     *  anything with digits or underscores. A single leading capital ("Teh" at a sentence start)
     *  is normal and does not protect the word. */
    static boolean isProtectedToken(String word) {
        if (word == null || word.isEmpty()) return false;
        char first = word.charAt(0);
        if (first == '#' || first == '@') return true;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isDigit(c) || c == '_') return true;
            if (i > 0 && Character.isUpperCase(c)) return true;
        }
        return false;
    }

    public synchronized String findAutoCorrection(String wordRaw, boolean isArabic) {
        return findAutoCorrection(wordRaw, isArabic, false);
    }

    /** strong = also correct close calls (best candidate not clearly ahead of the runner-up). */
    public synchronized String findAutoCorrection(String wordRaw, boolean isArabic, boolean strong) {
        if (!ready) return null;
        if (isProtectedToken(wordRaw)) return null;
        String word = normalize(wordRaw);
        if (word.length() < 3) return null;
        String wordLower = fold(word);
        String sure = isArabic ? COMMON_AR_TYPOS.get(wordLower)
                : (latinIsSpanish ? COMMON_ES_TYPOS.get(wordLower)
                : (EN_NOT_SURE.contains(wordLower) ? null : COMMON_EN_TYPOS.get(wordLower)));
        if (sure != null) return isArabic ? sure : matchCase(word, sure);
        if (isTrustedWord(word)) return null;
        if (!isArabic && !latinIsSpanish) {
            String fixed = COMMON_EN_TYPOS.get(wordLower);
            if (fixed != null) return matchCase(word, fixed);
        }
        Map<String, Integer> candidates = new HashMap<>();
        collectNearMissCandidates(wordLower, learnedByFold, learnedWords, candidates, 30, !isArabic);
        int baseScore = 30;
        if (candidates.isEmpty()) {
            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : currentLatinByFold();
            Map<String, Integer> baseWords = isArabic ? baseArWords : currentLatinWords();
            collectNearMissCandidates(wordLower, baseByFold, baseWords, candidates, 1);
            baseScore = 1;
        }
        if (candidates.isEmpty()) return null;
        List<String> ranked = sortKeysByScoreDesc(candidates);
        String top = ranked.get(0);
        // 3-letter words are too ambiguous for silent correction: only fix a letter swap or a
        // slip onto a neighbouring key (Latin QWERTY); everything else stays a suggestion.
        if (wordLower.length() == 3) {
            String tf = fold(top);
            if (isAdjacentSwap(wordLower, tf)) return top;
            if (!isArabic && isNeighborKeySlip(wordLower, tf)) return top;
            return null;
        }
        if (ranked.size() == 1 || strong) return top;
        // Only correct silently when the winner is clear. Letter-swap typos ("hte") always are;
        // otherwise the winner must clearly beat the runner-up. An ambiguous word is left alone
        // and its candidates simply stay in the suggestion bar for the user to pick.
        if (isAdjacentSwap(wordLower, fold(top))) return top;
        int topScore = candidates.get(top), secondScore = candidates.get(ranked.get(1));
        final int DIST1 = 100000;
        boolean topClose = topScore >= DIST1, secondClose = secondScore >= DIST1;
        if (topClose && !secondClose) return top;
        int offset = topClose ? DIST1 + baseScore : baseScore;
        int topCnt = Math.max(topScore - offset, 1), secondCnt = Math.max(secondScore - offset, 1);
        return topCnt >= 2 * secondCnt ? top : null;
    }

    private static final String[] QWERTY_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

    private static boolean keysAdjacent(char a, char b) {
        int ra = -1, ca = -1, rb = -1, cb = -1;
        for (int r = 0; r < QWERTY_ROWS.length; r++) {
            int i = QWERTY_ROWS[r].indexOf(a);
            if (i >= 0) { ra = r; ca = i; }
            int j = QWERTY_ROWS[r].indexOf(b);
            if (j >= 0) { rb = r; cb = j; }
        }
        if (ra < 0 || rb < 0 || a == b) return false;
        return Math.abs(ra - rb) <= 1 && Math.abs(ca - cb) <= 1;
    }

    /** Same length, exactly one differing letter, and the two letters sit on neighbouring keys. */
    private static boolean isNeighborKeySlip(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = -1;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                if (diff >= 0) return false;
                diff = i;
            }
        }
        return diff >= 0 && keysAdjacent(a.charAt(diff), b.charAt(diff));
    }

    private static String matchCase(String typed, String fixed) {
        if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))) return fixed.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(typed.charAt(0))) return Character.toUpperCase(fixed.charAt(0)) + fixed.substring(1);
        return fixed;
    }

    /** Only certain, unambiguous English slips. Never a real word on the left side. */
    private static final Map<String, String> COMMON_EN_TYPOS = new HashMap<>();
    static {
        String[] p = {
            "teh","the", "hte","the", "taht","that", "thsi","this", "tihs","this", "nad","and", "adn","and",
            "wiht","with", "waht","what", "jsut","just", "juts","just",
            "becuase","because", "becasue","because", "beacuse","because", "recieve","receive",
            "recieved","received", "definately","definitely", "definitly","definitely",
            "seperate","separate", "occured","occurred", "occurence","occurrence",
            "untill","until", "wich","which", "whihc","which", "thier","their", "freind","friend",
            "frist","first", "firts","first", "togehter","together", "tommorow","tomorrow",
            "tomorow","tomorrow", "tommorrow","tomorrow", "alot","a lot", "doesnt","doesn't",
            "dont","don't", "cant","can't", "wont","won't", "didnt","didn't", "isnt","isn't",
            "wasnt","wasn't", "couldnt","couldn't", "wouldnt","wouldn't", "shouldnt","shouldn't",
            "youre","you're", "thats","that's", "whats","what's",
            "adress","address", "begining","beginning", "beleive","believe", "calender","calendar",
            "comming","coming", "diffrent","different", "enviroment","environment",
            "goverment","government", "happend","happened", "knowlege","knowledge",
            "mispell","misspell", "neccessary","necessary", "noticable","noticeable",
            "reciept","receipt", "refered","referred", "succesful","successful",
            "tounge","tongue", "wierd","weird", "yuo","you", "yoru","your", "ahve","have", "hvae","have",
            "coudl","could", "woudl","would", "shoudl","should", "peice","piece", "thnak","thank",
            "thansk","thanks", "pleae","please", "plase","please", "realy","really",
            "reallly","really", "abuot","about", "aobut","about"
        };
        for (int i = 0; i + 1 < p.length; i += 2) {
            COMMON_EN_TYPOS.put(p[i], p[i + 1]);
        }
    }

    /**
     * More certain misspellings. Every entry is a spelling that is never right on its own. The
     * Spanish and Arabic maps are keyed on the folded form (Arabic hamza/ya variants merged).
     * They are checked before the "known word" shortcut because the frequency dictionaries also
     * contain some of these spellings (typos that show up in real text).
     */
    private static final Map<String, String> COMMON_ES_TYPOS = new HashMap<>();
    private static final Map<String, String> COMMON_AR_TYPOS = new HashMap<>();
    // Real words that sit in the English map: they only use the old trusted-word path.
    private static final java.util.Set<String> EN_NOT_SURE = new java.util.HashSet<>(Arrays.asList("cant", "wont"));
    static {
        String[] en = {
            "absense","absence", "accomodate","accommodate", "accomodation","accommodation", "acheive","achieve", "acheived","achieved", "achievment","achievement",
            "acknowlege","acknowledge", "acquaintence","acquaintance", "agressive","aggressive", "aggresive","aggressive", "aparent","apparent", "apparantly","apparently",
            "appearence","appearance", "arguement","argument", "assasinate","assassinate", "athiest","atheist", "basicly","basically", "beautifull","beautiful",
            "belive","believe", "beleived","believed", "buisness","business", "bussiness","business", "catagory","category", "cemetary","cemetery",
            "changable","changeable", "collegue","colleague", "commited","committed", "comitted","committed", "comittee","committee", "committe","committee",
            "concious","conscious", "congradulations","congratulations", "conveniant","convenient", "definate","definite", "definatly","definitely", "definitley","definitely",
            "desparate","desperate", "dissapoint","disappoint", "dissapointed","disappointed", "disapear","disappear", "dissapear","disappear", "embarass","embarrass",
            "embarassed","embarrassed", "embarassing","embarrassing", "equiptment","equipment", "excercise","exercise", "existance","existence", "experiance","experience",
            "familar","familiar", "finaly","finally", "foriegn","foreign", "gaurd","guard", "garantee","guarantee", "guarentee","guarantee",
            "grammer","grammar", "harrass","harass", "hieght","height", "heigth","height", "immediatly","immediately", "imediately","immediately",
            "independant","independent", "intresting","interesting", "intersting","interesting", "interupt","interrupt", "lenght","length", "liason","liaison",
            "libary","library", "lisence","license", "maintainance","maintenance", "millenium","millennium", "minature","miniature", "mischevious","mischievous",
            "neccesary","necessary", "necesary","necessary", "neice","niece", "ocasion","occasion", "occassion","occasion", "occurance","occurrence",
            "occuring","occurring", "oppurtunity","opportunity", "oportunity","opportunity", "parliment","parliament", "persistant","persistent", "posession","possession",
            "possesion","possession", "prefered","preferred", "presance","presence", "privelege","privilege", "probaly","probably", "professer","professor",
            "pronounciation","pronunciation", "publically","publicly", "questionaire","questionnaire", "recomend","recommend", "reccomend","recommend", "relevent","relevant",
            "religous","religious", "remeber","remember", "resistence","resistance", "responsability","responsibility", "restaraunt","restaurant", "resturant","restaurant",
            "rythm","rhythm", "scedule","schedule", "shedule","schedule", "sentance","sentence", "similiar","similar", "sincerly","sincerely",
            "speach","speech", "strenght","strength", "succesfully","successfully", "suprise","surprise", "surprize","surprise", "suprised","surprised",
            "tendancy","tendency", "threshhold","threshold", "truely","truly", "unforseen","unforeseen", "unfortunatly","unfortunately", "usefull","useful",
            "vaccum","vacuum", "vegtable","vegetable", "vehical","vehicle", "visable","visible", "writting","writing", "writen","written",
            "yesturday","yesterday", "becomming","becoming", "begginning","beginning", "beggining","beginning", "runing","running", "geting","getting",
            "stoped","stopped", "tryed","tried", "ofcourse","of course", "infact","in fact", "aswell","as well", "eachother","each other",
            "alittle","a little", "atleast","at least", "alrady","already", "allready","already", "recieving","receiving", "freinds","friends",
            "hapen","happen", "diferent","different", "elligible","eligible", "sucess","success", "succes","success", "sucessful","successful",
            "wouldve","would've", "couldve","could've", "shouldve","should've", "theyre","they're", "theyve","they've", "weve","we've",
            "youve","you've", "youll","you'll", "theyll","they'll", "hasnt","hasn't", "havent","haven't", "hadnt","hadn't",
            "arent","aren't", "werent","weren't", "theres","there's", "wheres","where's", "heres","here's", "reciving","receiving",
            "recieves","receives", "beleive","believe", "beleives","believes", "calender","calendar", "wierdo","weirdo", "accross","across",
            "agressively","aggressively", "alchohol","alcohol", "apparant","apparent", "assistent","assistant", "bizzare","bizarre", "brillant","brilliant",
            "cieling","ceiling", "compatability","compatibility", "completly","completely", "concensus","consensus", "critisism","criticism", "decison","decision",
            "disatisfied","dissatisfied", "ecstacy","ecstasy", "enterance","entrance", "entrepeneur","entrepreneur", "exagerate","exaggerate", "facinating","fascinating",
            "febuary","february", "flourescent","fluorescent", "gratefull","grateful", "greatful","grateful", "happyness","happiness", "humerous","humorous",
            "hygeine","hygiene", "ignorence","ignorance", "incidently","incidentally", "indispensible","indispensable", "intellegent","intelligent", "jewlery","jewelry",
            "knowlegable","knowledgeable", "labratory","laboratory", "largly","largely", "lazyness","laziness", "mathmatics","mathematics", "medcine","medicine",
            "medeval","medieval", "mysterous","mysterious", "naturaly","naturally", "nieghbor","neighbor", "noticably","noticeably", "ocurred","occurred",
            "ocurrence","occurrence", "offical","official", "paralell","parallel", "pasttime","pastime", "peculier","peculiar", "percieve","perceive",
            "perseverence","perseverance", "personel","personnel", "playwrite","playwright", "politican","politician", "potatos","potatoes", "practicly","practically",
            "presense","presence", "proffesional","professional", "promiss","promise", "propably","probably", "quantaty","quantity", "reccommend","recommend",
            "refrence","reference", "religon","religion", "repitition","repetition", "resemblence","resemblance", "sandwhich","sandwich", "seige","siege",
            "sieze","seize", "sence","sense", "shcool","school", "skillfull","skillful", "sophmore","sophomore", "souvenier","souvenir",
            "spagetti","spaghetti", "stategy","strategy", "straigth","straight", "stubborness","stubbornness", "substancial","substantial", "sucessor","successor",
            "sufficent","sufficient", "suposed","supposed", "supposably","supposedly", "temperture","temperature", "temprature","temperature", "tounament","tournament",
            "tradgedy","tragedy", "transfered","transferred", "tyrany","tyranny", "unecessary","unnecessary", "unnecesary","unnecessary", "valueable","valuable",
            "varient","variant", "volonteer","volunteer", "wensday","wednesday", "wendesday","wednesday", "wellcome","welcome", "wilfull","willful",
            "withold","withhold"
        };
        for (int i = 0; i + 1 < en.length; i += 2) {
            if (!COMMON_EN_TYPOS.containsKey(en[i])) COMMON_EN_TYPOS.put(en[i], en[i + 1]);
        }
        String[] es = {
            "tambien","también", "ademas","además", "despues","después", "aqui","aquí", "alli","allí", "ahi","ahí",
            "asi","así", "adios","adiós", "quizas","quizás", "ultimamente","últimamente", "facil","fácil", "dificil","difícil",
            "rapido","rápido", "util","útil", "inutil","inútil", "movil","móvil", "pelicula","película", "espanol","español",
            "manana","mañana", "senor","señor", "senora","señora", "companero","compañero", "cumpleanos","cumpleaños", "pequeno","pequeño",
            "diseno","diseño", "dueno","dueño", "extrano","extraño", "pequena","pequeña", "espana","españa", "informacion","información",
            "atencion","atención", "educacion","educación", "situacion","situación", "relacion","relación", "organizacion","organización", "comunicacion","comunicación",
            "cancion","canción", "corazon","corazón", "razon","razón", "conversacion","conversación", "direccion","dirección", "habitacion","habitación",
            "reunion","reunión", "aplicacion","aplicación", "funcion","función", "nacion","nación", "estacion","estación", "invitacion","invitación",
            "pasion","pasión", "presion","presión", "excepcion","excepción", "operacion","operación", "produccion","producción", "construccion","construcción",
            "solucion","solución", "tradicion","tradición", "jamas","jamás", "ojala","ojalá", "porfavor","por favor", "aveces","a veces",
            "apesar","a pesar", "osea","o sea", "atravez","a través", "haiga","haya", "bamos","vamos", "estubo","estuvo",
            "estubieron","estuvieron", "exelente","excelente", "perdon","perdón", "compania","compañía", "tecnologia","tecnología", "psicologia","psicología",
            "biologia","biología", "energia","energía", "economia","economía", "alegria","alegría", "categoria","categoría", "ingenieria","ingeniería",
            "filosofia","filosofía", "geografia","geografía", "fotografia","fotografía", "biografia","biografía", "telefono","teléfono", "numeros","números",
            "todavia","todavía", "dia","día", "sabado","sábado", "miercoles","miércoles", "electrico","eléctrico", "musico","músico",
            "basico","básico", "clasico","clásico", "historico","histórico", "politico","político", "economico","económico", "tipico","típico",
            "unico","único"
        };
        for (int i = 0; i + 1 < es.length; i += 2) {
            COMMON_ES_TYPOS.put(foldUncached(es[i]), es[i + 1]);
        }
        String[] ar = {
            "لاكن","لكن", "هاذا","هذا", "هاذه","هذه", "ذالك","ذلك", "كذالك","كذلك", "اولائك","أولئك",
            "اولاءك","أولئك", "اللذي","الذي", "اللتي","التي", "ماشاءالله","ما شاء الله", "انشاءالله","إن شاء الله", "مسؤل","مسؤول",
            "المسؤل","المسؤول", "اللة","الله", "لاكنه","لكنه", "لاكنها","لكنها", "هاكذا","هكذا", "هاذين","هذين",
            "لاكني","لكني", "لاكنني","لكنني", "هاذان","هذان", "لذالك","لذلك", "بذالك","بذلك", "وذالك","وذلك"
        };
        for (int i = 0; i + 1 < ar.length; i += 2) {
            COMMON_AR_TYPOS.put(foldUncached(ar[i]), ar[i + 1]);
        }
    }

    private String findInlineFix(String prefix, String prefixLower, boolean isArabic) {
        if (prefixLower.length() < 3 || isProtectedToken(prefix) || isTrustedWord(prefix)) return null;
        Map<String, Integer> cand = new HashMap<>();
        collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, cand, 30, !isArabic);
        if (cand.isEmpty()) {
            java.util.TreeMap<String, List<String>> baseByFold = isArabic ? baseArByFold : currentLatinByFold();
            Map<String, Integer> baseWords = isArabic ? baseArWords : currentLatinWords();
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

    private static boolean isAdjacentSwap(String a, String b) {
        if (a.length() != b.length()) return false;
        int i = 0, n = a.length();
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        if (i >= n - 1) return false;
        if (a.charAt(i) != b.charAt(i + 1) || a.charAt(i + 1) != b.charAt(i)) return false;
        return a.regionMatches(i + 2, b, i + 2, n - i - 2);
    }

    private void collectNearMissCandidates(String prefixLower, java.util.TreeMap<String, List<String>> byFold,
                                            Map<String, Integer> wordCounts, Map<String, Integer> scored, int baseScore) {
        collectNearMissCandidates(prefixLower, byFold, wordCounts, scored, baseScore, false);
    }

    // filterLatin restricts to the active Latin sub-language (English vs Spanish) -- only
    // meaningful for the learned-words map, since that's the one shared between them; base
    // dictionary lookups already pass the right language-specific map and are never tagged.
    private void collectNearMissCandidates(String prefixLower, java.util.TreeMap<String, List<String>> byFold,
                                            Map<String, Integer> wordCounts, Map<String, Integer> scored, int baseScore,
                                            boolean filterLatin) {
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
                if (filterLatin && !latinWordAllowed(w)) continue;
                int cur = scored.containsKey(w) ? scored.get(w) : 0;
                scored.put(w, cur + baseScore + (dist == 1 ? 100000 : 0) + Math.min(cnt, 99999));
            }
        }
    }

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

    private List<String> getTopLearnedWords(boolean isArabic, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(learnedWords.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= limit) break;
            String w = e.getKey();
            if (e.getValue() < 2 || w.indexOf('@') >= 0) continue;
            if (hasArabicLetters(w) != isArabic) continue;
            if (!isArabic && !latinWordAllowed(w)) continue;
            out.add(w);
        }
        return out;
    }

    private List<String> activeDefaultWords(boolean isArabic) {
        List<String> all = isArabic
                ? Arrays.asList("السلام", "بسم", "مرحبا", "إن", "صباح", "الحمد")
                : latinIsSpanish
                    ? Arrays.asList("El", "Yo", "En", "Hola", "Gracias", "Bueno")
                    : Arrays.asList("The", "I", "On", "Hello", "Thanks", "Good");
        String defaultsLangCode = isArabic ? KeyboardPreferences.LANG_AR
                : (latinIsSpanish ? KeyboardPreferences.LANG_ES : KeyboardPreferences.LANG_EN);
        java.util.Set<String> dismissed = keyboardPrefs.getDismissedDefaultWords(defaultsLangCode);
        if (dismissed.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String w : all) if (!dismissed.contains(w)) out.add(w);
        return out;
    }

    public boolean isDefaultPlaceholderWord(String word, boolean isArabic) {
        if (word == null) return false;
        for (String d : activeDefaultWords(isArabic)) {
            if (d.equals(word)) return true;
        }
        return false;
    }

    private List<String> placeholderSlots(boolean isArabic) {
        return Arrays.asList("", "", "");
    }

    private List<String> formatToThreeSlots(List<String> candidates, boolean isArabic) {
        List<String> combined = new ArrayList<>();
        java.util.Set<String> seenFolded = new java.util.HashSet<>();
        for (String c : candidates) {
            if (seenFolded.add(fold(c))) combined.add(c);
        }

        for (String t : getTopLearnedWords(isArabic, 8)) {
            if (combined.size() >= 3) break;
            if (seenFolded.add(fold(t))) combined.add(t);
        }

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
        learnedWordLatinTag.remove(word);
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

    /** Everything the keyboard learned (words, next-word links, language tags) as JSON, for backup. */
    public synchronized JSONObject exportBackup() {
        loadFromPrefs();
        dirty = false;
        JSONObject out = new JSONObject();
        try {
            JSONObject words = new JSONObject();
            for (Map.Entry<String, Integer> e : learnedWords.entrySet()) words.put(e.getKey(), e.getValue());
            JSONObject tags = new JSONObject();
            for (Map.Entry<String, String> e : learnedWordLatinTag.entrySet()) tags.put(e.getKey(), e.getValue());
            out.put("words", words);
            out.put("bigrams", bigramsToJson(learnedBigrams));
            out.put("trigrams", bigramsToJson(learnedTrigrams));
            out.put("tags", tags);
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "exportBackup()", e);
        }
        return out;
    }

    private static final int BACKUP_MAX_WORDS = 20000;
    private static final int BACKUP_MAX_KEY = 64;
    private static final int BACKUP_MAX_NEXT = 64;

    private static int clampCount(int v) {
        return Math.max(1, Math.min(99999, v));
    }

    private static boolean okKey(String k) {
        return k != null && !k.isEmpty() && k.length() <= BACKUP_MAX_KEY;
    }

    private void mergeNested(JSONObject src, Map<String, Map<String, Integer>> dst) {
        if (src == null) return;
        Iterator<String> keys = src.keys();
        int outer = 0;
        while (keys.hasNext() && outer++ < BACKUP_MAX_WORDS) {
            String k = keys.next();
            JSONObject inner = src.optJSONObject(k);
            if (!okKey(k) || inner == null) continue;
            Map<String, Integer> map = dst.get(k);
            if (map == null) { map = new HashMap<>(); dst.put(k, map); }
            Iterator<String> nk = inner.keys();
            int n = 0;
            while (nk.hasNext() && n++ < BACKUP_MAX_NEXT) {
                String w = nk.next();
                if (!okKey(w)) continue;
                int c = clampCount(inner.optInt(w, 1));
                Integer old = map.get(w);
                map.put(w, old == null ? c : Math.max(old, c));
            }
        }
    }

    /**
     * Merges a backup into what is already learned (never deletes anything; for a word in both,
     * the higher count wins). Input is validated and size-limited. Returns words in the backup.
     */
    public synchronized int importBackup(JSONObject backup) {
        if (backup == null) return 0;
        loadFromPrefs();
        int seen = 0;
        JSONObject words = backup.optJSONObject("words");
        if (words != null) {
            Iterator<String> keys = words.keys();
            while (keys.hasNext() && seen < BACKUP_MAX_WORDS) {
                String k = keys.next();
                if (!okKey(k)) continue;
                int c = clampCount(words.optInt(k, 1));
                Integer old = learnedWords.get(k);
                learnedWords.put(k, old == null ? c : Math.max(old, c));
                seen++;
            }
        }
        mergeNested(backup.optJSONObject("bigrams"), learnedBigrams);
        mergeNested(backup.optJSONObject("trigrams"), learnedTrigrams);
        JSONObject tags = backup.optJSONObject("tags");
        if (tags != null) {
            Iterator<String> keys = tags.keys();
            int n = 0;
            while (keys.hasNext() && n++ < BACKUP_MAX_WORDS) {
                String k = keys.next();
                String v = tags.optString(k, null);
                if (okKey(k) && v != null && v.length() <= 8 && !learnedWordLatinTag.containsKey(k)) learnedWordLatinTag.put(k, v);
            }
        }
        saveToPrefs();
        loadFromPrefs();
        dirty = false;
        return seen;
    }

    public synchronized void clearAllLearnedData() {
        learnedWords.clear();
        learnedWordLatinTag.clear();
        learnedBigrams.clear();
        learnedTrigrams.clear();
        learnedByFold.clear();
        saveHandler.removeCallbacks(saveRunnable);
        saveScheduled = false;
        prefs.edit().clear().apply();
    }

    private void saveToPrefs() {
        writeSnapshotToPrefs(new HashMap<>(learnedWords), deepCopyBigrams(learnedBigrams), deepCopyBigrams(learnedTrigrams),
                new HashMap<>(learnedWordLatinTag));
    }

    private Map<String, Map<String, Integer>> deepCopyBigrams(Map<String, Map<String, Integer>> source) {
        Map<String, Map<String, Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : source.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        saveHandler.postDelayed(saveRunnable, 900L);
    }

    private void performScheduledSave() {
        saveScheduled = false;
        saveToPrefsAsync();
    }

    private void saveToPrefsAsync() {
        SAVE_EXECUTOR.execute(() -> {
            final Map<String, Integer> wordsSnapshot;
            final Map<String, Map<String, Integer>> bigramsSnapshot;
            final Map<String, Map<String, Integer>> trigramsSnapshot;
            final Map<String, String> tagsSnapshot;
            synchronized (PredictionEngine.this) {
                wordsSnapshot = new HashMap<>(learnedWords);
                bigramsSnapshot = deepCopyBigrams(learnedBigrams);
                trigramsSnapshot = deepCopyBigrams(learnedTrigrams);
                tagsSnapshot = new HashMap<>(learnedWordLatinTag);
            }
            writeSnapshotToPrefs(wordsSnapshot, bigramsSnapshot, trigramsSnapshot, tagsSnapshot);
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
                                       Map<String, Map<String, Integer>> trigrams, Map<String, String> wordLangTags) {
        synchronized (SAVE_LOCK) {
        try {
            JSONObject wordsJson = new JSONObject();
            for (Map.Entry<String, Integer> e : words.entrySet()) {
                wordsJson.put(e.getKey(), e.getValue());
            }
            JSONObject tagsJson = new JSONObject();
            for (Map.Entry<String, String> e : wordLangTags.entrySet()) {
                tagsJson.put(e.getKey(), e.getValue());
            }

            String ws = wordsJson.toString();
            String bs = bigramsToJson(bigrams).toString();
            String ts = bigramsToJson(trigrams).toString();
            String tgs = tagsJson.toString();
            lastSavedWords = ws;
            lastSavedBigrams = bs;
            lastSavedTrigrams = ts;
            prefs.edit()
                    .putString("words_v1", ws)
                    .putString("bigrams_v1", bs)
                    .putString("trigrams_v1", ts)
                    .putString("word_lang_v1", tgs)
                    .apply();
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "writeSnapshotToPrefs()", e);
        }
        }
    }

    public synchronized void flushPendingSave() {
        if (!saveScheduled) return;
        saveHandler.removeCallbacks(saveRunnable);
        saveScheduled = false;
        saveToPrefsAsync();
    }

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
            learnedWordLatinTag.clear();
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
            String tagsStr = prefs.getString("word_lang_v1", null);
            if (tagsStr != null && !tagsStr.isEmpty()) {
                JSONObject obj = new JSONObject(tagsStr);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    String v = obj.optString(k, null);
                    if (v != null) learnedWordLatinTag.put(k, v);
                }
            }
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "loadFromPrefs()", e);
        }
        rebuildLearnedIndex();
    }
}
