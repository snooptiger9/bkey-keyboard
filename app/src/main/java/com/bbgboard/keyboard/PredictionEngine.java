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
    private final Map<String, Integer> baseFrWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseFrBigrams = new HashMap<>();
    private final Map<String, Integer> baseHiWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseHiBigrams = new HashMap<>();
    private final Map<String, Integer> baseBnWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseBnBigrams = new HashMap<>();

    private final java.util.TreeMap<String, List<String>> baseArByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseEnByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseEsByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseFrByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseHiByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> baseBnByFold = new java.util.TreeMap<>();
    private final Map<String, Integer> baseUrWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseUrBigrams = new HashMap<>();
    private final java.util.TreeMap<String, List<String>> baseUrByFold = new java.util.TreeMap<>();
    private final Map<String, Integer> baseIdWords = new HashMap<>();
    private final Map<String, Map<String, Integer>> baseIdBigrams = new HashMap<>();
    private final java.util.TreeMap<String, List<String>> baseIdByFold = new java.util.TreeMap<>();
    private final java.util.TreeMap<String, List<String>> learnedByFold = new java.util.TreeMap<>();

    // Which Latin-script language is active right now (everything above is keyed on isArabic
    // vs not-Arabic; this picks which non-Arabic dataset "not Arabic" actually means).
    private volatile boolean latinIsSpanish = false;
    // Hindi (Devanagari) is not Latin and not Arabic: it rides the same "not Arabic" branch as
    // English/Spanish, with its own dictionary, learned-word tag, typo map and default words.
    private volatile boolean latinIsHindi = false;
    private volatile boolean latinIsFrench = false;
    // Urdu is written in Arabic script but is its own language: it rides the "not Arabic" branch
    // with its own dictionary and learned-word tag, like Hindi, so Arabic and Urdu words never mix.
    private volatile boolean latinIsUrdu = false;
    // Indonesian: Latin letters like English, kept apart by its own dictionary and learned-word tag.
    private volatile boolean latinIsIndonesian = false;
    // Bangla (Bengali script) rides the same "not Arabic" branch as Hindi, with its own
    // dictionary, learned-word tag and default words. Its own script keeps it apart from Latin.
    private volatile boolean latinIsBangla = false;

    public void setActiveLatinLanguage(String languageCode) {
        activeLangCode = languageCode;
        // A language that was not loaded at start (turned on later) loads in the background now.
        if (ready && languageCode != null && !loadedLangs.contains(languageCode)) {
            final String lang = languageCode;
            Thread t = new Thread(() -> loadLanguage(lang), "bkey-dict-load");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
        latinIsFrench = KeyboardPreferences.LANG_FR.equals(languageCode);
        latinIsSpanish = KeyboardPreferences.LANG_ES.equals(languageCode);
        latinIsHindi = KeyboardPreferences.LANG_HI.equals(languageCode);
        latinIsUrdu = KeyboardPreferences.LANG_UR.equals(languageCode);
        latinIsIndonesian = KeyboardPreferences.LANG_ID.equals(languageCode);
        latinIsBangla = KeyboardPreferences.LANG_BN.equals(languageCode);
    }

    /** The language code of the active non-Arabic language (en / es / hi). */
    private String activeLatinTag() {
        return latinIsHindi ? KeyboardPreferences.LANG_HI
                : latinIsBangla ? KeyboardPreferences.LANG_BN
                : latinIsUrdu ? KeyboardPreferences.LANG_UR
                : latinIsFrench ? KeyboardPreferences.LANG_FR
                : latinIsIndonesian ? KeyboardPreferences.LANG_ID
                : (latinIsSpanish ? KeyboardPreferences.LANG_ES : KeyboardPreferences.LANG_EN);
    }

    private Map<String, Integer> currentLatinWords() { return latinIsHindi ? baseHiWords : latinIsBangla ? baseBnWords : latinIsUrdu ? baseUrWords : latinIsFrench ? baseFrWords : latinIsIndonesian ? baseIdWords : (latinIsSpanish ? baseEsWords : baseEnWords); }
    private Map<String, Map<String, Integer>> currentLatinBigrams() { return latinIsHindi ? baseHiBigrams : latinIsBangla ? baseBnBigrams : latinIsUrdu ? baseUrBigrams : latinIsFrench ? baseFrBigrams : latinIsIndonesian ? baseIdBigrams : (latinIsSpanish ? baseEsBigrams : baseEnBigrams); }
    private java.util.TreeMap<String, List<String>> currentLatinByFold() { return latinIsHindi ? baseHiByFold : latinIsBangla ? baseBnByFold : latinIsUrdu ? baseUrByFold : latinIsFrench ? baseFrByFold : latinIsIndonesian ? baseIdByFold : (latinIsSpanish ? baseEsByFold : baseEnByFold); }

    // learnedWords/learnedBigrams/learnedByFold below are shared across every language --
    // that's fine between Arabic and Latin (different Unicode ranges keep them apart
    // naturally), but English and Spanish share the same letters, so a word learned under
    // one would otherwise show up as a suggestion under the other. This tags each learned
    // Latin word with which one it was learned under, so suggestions can filter by it.
    // Untagged (anything learned before Spanish existed, or not yet re-typed since) defaults
    // to English -- the only Latin language that could have written it.
    private final Map<String, String> learnedWordLatinTag = new HashMap<>();

    // A word can be learned in more than one language (e.g. "hotel" in English and Spanish, or a
    // word shared by Arabic and Urdu), so the tag is a comma-separated list of language codes
    // ("es,fr"; "ur,ar"). No tag = learned before tags existed: Latin means English, Arabic
    // script means Arabic.
    private static boolean tagHas(String tag, String code) {
        return tag != null && ("," + tag + ",").contains("," + code + ",");
    }

    // Set during learnTrainingSentence(): a word that is already saved keeps its languages.
    private boolean keepKnownTags = false;

    private void tagLatinWord(String word, boolean isArabic, boolean wasKnown) {
        if (keepKnownTags && wasKnown) return;
        String code = isArabic ? KeyboardPreferences.LANG_AR : activeLatinTag();
        String existing = learnedWordLatinTag.get(word);
        if (existing == null) {
            int sc = scriptOf(word);
            // An untagged word typed in Arabic mode stays untagged (untagged Arabic script = Arabic).
            if (isArabic && sc != SC_LATIN) return;
            if (wasKnown) {
                // Already saved without a tag (learned before tags existed): Latin = English, Arabic script = Arabic.
                existing = sc == SC_LATIN ? KeyboardPreferences.LANG_EN
                        : sc == SC_ARABIC ? KeyboardPreferences.LANG_AR : "";
            } else {
                existing = "";
            }
        }
        if (!tagHas(existing, code)) existing = existing.isEmpty() ? code : existing + "," + code;
        learnedWordLatinTag.put(word, existing);
    }

    /** Learned Arabic-script word that may be offered in Arabic mode (hides Urdu-only words). */
    private boolean arabicTagOk(String word) {
        String tag = learnedWordLatinTag.get(word);
        return tag == null || tagHas(tag, KeyboardPreferences.LANG_AR);
    }

    /** Learned words that may be offered in the current mode: Arabic mode keeps out words learned
     *  while typing Urdu (same script, other language); the other modes go by latinWordAllowed. */
    // Writing system of a word, so learned words and word pairs from one language never show up
    // while another one is active (Arabic/Urdu script, Devanagari, Bengali, Latin).
    private static final int SC_NEUTRAL = 0, SC_LATIN = 1, SC_ARABIC = 2, SC_DEVA = 3, SC_BANGLA = 4;

    private static int scriptOf(String w) {
        if (w == null) return SC_NEUTRAL;
        // A saved email address belongs to the user, not to a language.
        if (w.indexOf('@') > 0 && looksLikeEmail(w)) return SC_NEUTRAL;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F)
                    || (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) return SC_ARABIC;
            if (c >= 0x0900 && c <= 0x097F) return SC_DEVA;
            // Bengali must be tested before isLetter: otherwise it would fall into SC_LATIN.
            if (c >= 0x0980 && c <= 0x09FF) return SC_BANGLA;
            if (Character.isLetter(c)) return SC_LATIN;
        }
        return SC_NEUTRAL;
    }

    private int expectedScript(boolean isArabic) {
        if (isArabic || latinIsUrdu) return SC_ARABIC;
        if (latinIsHindi) return SC_DEVA;
        if (latinIsBangla) return SC_BANGLA;
        return SC_LATIN;
    }

    private boolean scriptOk(String word, boolean isArabic) {
        int s = scriptOf(word);
        return s == SC_NEUTRAL || s == expectedScript(isArabic);
    }

    private boolean learnedAllowed(String word, boolean isArabic) {
        if (!scriptOk(word, isArabic)) return false;
        if (isArabic) return arabicTagOk(word);
        return latinWordAllowed(word);
    }

    private boolean latinWordAllowed(String word) {
        if (!scriptOk(word, false)) return false;
        if (looksLikeEmail(word)) return true;
        String tag = learnedWordLatinTag.get(word);
        String active = activeLatinTag();
        // Untagged words predate Spanish/Hindi/French and could only have been English.
        return tag == null ? KeyboardPreferences.LANG_EN.equals(active) : tagHas(tag, active);
    }

    // Only the languages the user has turned on keep their dictionary in memory.
    private final java.util.Set<String> loadedLangs = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private volatile String activeLangCode;
    private Context appContext;
    private final java.util.Set<String> knownFolded =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    // Indonesian words count as known only while Indonesian is active, so turning Indonesian on
    // never stops an English/Spanish/French typo from being fixed just because it is an Indonesian word.
    private final java.util.Set<String> knownFoldedId =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
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
    private static final Pattern EMAIL_EDGE_PUNCT = Pattern.compile("^[،.؟!؛:\\\",()।॥]+|[،.؟!؛:\\\",()।॥]+$");
    private static final Pattern ALL_PUNCT = Pattern.compile("[،.؟!؛:\\\",()।॥¿¡«»۔]");
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
        // The one-time retag of old words runs only in the keyboard's engine, which starts on a
        // background thread; the settings screen's engine starts on the main thread and skips it.
        Runnable init = () -> initialize(appCtx, asyncInit);
        if (asyncInit) {
            Thread t = new Thread(init, "bkey-engine-init");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        } else {
            init.run();
        }
    }

    private void initialize(Context context, boolean runRetag) {
        initBaseDictionaries();
        appContext = context;
        for (String lang : keyboardPrefs.getEnabledLanguages()) loadLanguage(lang);
        String act = activeLangCode;
        if (act != null) loadLanguage(act);
        synchronized (this) {
            loadFromPrefs();
            dirty = false;
            cleanupOldLearnedDataOnce();
            if (runRetag) retagLegacyWordsOnce(context);
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        ready = true;
        Runnable l = onReadyListener;
        if (l != null) l.run();
    }

    /** Loads one language's bundled dictionary and fold index (once). The heavy work happens on a
     *  private copy and is swapped in under the engine lock, so suggestions never see half a list. */
    private void loadLanguage(String lang) {
        if (appContext == null || lang == null) return;
        String asset; Map<String, Integer> words; java.util.TreeMap<String, List<String>> idx;
        if (KeyboardPreferences.LANG_AR.equals(lang)) { asset = "dict_ar.txt"; words = baseArWords; idx = baseArByFold; }
        else if (KeyboardPreferences.LANG_EN.equals(lang)) { asset = "dict_en.txt"; words = baseEnWords; idx = baseEnByFold; }
        else if (KeyboardPreferences.LANG_ES.equals(lang)) { asset = "dict_es.txt"; words = baseEsWords; idx = baseEsByFold; }
        else if (KeyboardPreferences.LANG_HI.equals(lang)) { asset = "dict_hi.txt"; words = baseHiWords; idx = baseHiByFold; }
        else if (KeyboardPreferences.LANG_BN.equals(lang)) { asset = "dict_bn.txt"; words = baseBnWords; idx = baseBnByFold; }
        else if (KeyboardPreferences.LANG_FR.equals(lang)) { asset = "dict_fr.txt"; words = baseFrWords; idx = baseFrByFold; }
        else if (KeyboardPreferences.LANG_UR.equals(lang)) { asset = "dict_ur.txt"; words = baseUrWords; idx = baseUrByFold; }
        else if (KeyboardPreferences.LANG_ID.equals(lang)) { asset = "dict_id.txt"; words = baseIdWords; idx = baseIdByFold; }
        else return;
        if (!loadedLangs.add(lang)) return;
        try {
            Map<String, Integer> copy;
            synchronized (this) { copy = new HashMap<>(words); }
            loadBundledDictionary(appContext, asset, copy, 60, 20);
            java.util.TreeMap<String, List<String>> tmpIdx = new java.util.TreeMap<>();
            buildFoldIndex(copy, tmpIdx);
            synchronized (this) {
                words.putAll(copy);
                idx.clear();
                idx.putAll(tmpIdx);
            }
            if (KeyboardPreferences.LANG_ID.equals(lang)) knownFoldedId.addAll(tmpIdx.keySet());
            else knownFolded.addAll(tmpIdx.keySet());
        } catch (Throwable t) {
            loadedLangs.remove(lang);
            AppLogger.log("PredictionEngine", "loadLanguage(" + lang + ")", t instanceof Exception ? (Exception) t : new Exception(t));
        }
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

    private static java.util.Set<String> readFoldedDictionary(Context context, String asset) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(context.getAssets().open(asset), "UTF-8"), 32 * 1024);
            String line;
            while ((line = r.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty()) out.add(fold(w));
            }
            r.close();
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "readFoldedDictionary(" + asset + ")", e);
            return null;
        }
        return out;
    }

    /** One-time: Latin words learned before language tags existed were all treated as English.
     *  A word that is in the Spanish or French dictionary but not in the English one gets its real
     *  language, so it stops showing up while English is active. */
    private void retagLegacyWordsOnce(Context context) {
        if (prefs.getBoolean("retag_v1_done", false)) return;
        try {
            java.util.Set<String> en = readFoldedDictionary(context, "dict_en.txt");
            java.util.Set<String> es = readFoldedDictionary(context, "dict_es.txt");
            java.util.Set<String> fr = readFoldedDictionary(context, "dict_fr.txt");
            if (en == null || es == null || fr == null) return;
            boolean changed = false;
            for (String w : learnedWords.keySet()) {
                if (learnedWordLatinTag.containsKey(w)) continue;
                if (scriptOf(w) != SC_LATIN || looksLikeEmail(w)) continue;
                String f = fold(w);
                if (en.contains(f)) continue;
                boolean inEs = es.contains(f), inFr = fr.contains(f);
                if (!inEs && !inFr) continue;
                learnedWordLatinTag.put(w, inEs && inFr ? KeyboardPreferences.LANG_ES + "," + KeyboardPreferences.LANG_FR
                        : inEs ? KeyboardPreferences.LANG_ES : KeyboardPreferences.LANG_FR);
                changed = true;
            }
            if (changed) saveToPrefs();
            prefs.edit().putBoolean("retag_v1_done", true).apply();
        } catch (Exception e) {
            AppLogger.log("PredictionEngine", "retagLegacyWordsOnce()", e);
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

    /** Incognito + "hide learned words": suggestions come from the base dictionary only. */
    private boolean personalOff() {
        return keyboardPrefs != null && keyboardPrefs.isIncognitoMode() && keyboardPrefs.isHideLearnedInIncognito();
    }

    private boolean isTrustedWord(String w) {
        if (looksLikeEmail(w)) return true;
        if (knownFolded.contains(fold(w))) return true;
        if (latinIsIndonesian && knownFoldedId.contains(fold(w))) return true;
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
            // Devanagari: nukta, zero-width joiners and the chandrabindu/anusvara split are
            // spelling variants of the same word, so they fold together for matching.
            if (c == 0x093C || c == 0x200C || c == 0x200D) continue;
            if (c == 0x0901) { sb.append((char) 0x0902); continue; }
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

        addHiWord("नमस्ते", 110); addHiWord("धन्यवाद", 105); addHiWord("शुक्रिया", 90);
        addHiWord("कृपया", 95); addHiWord("आप", 100); addHiWord("मैं", 100); addHiWord("हम", 90);
        addHiWord("कैसे", 95); addHiWord("हैं", 100); addHiWord("हूँ", 95); addHiWord("ठीक", 95);
        addHiWord("शुभ", 90); addHiWord("सुप्रभात", 90); addHiWord("शुभकामनाएँ", 88);
        addHiBigram("नमस्ते", "दोस्त", 60); addHiBigram("आप", "कैसे", 95); addHiBigram("कैसे", "हैं", 95);
        addHiBigram("कैसे", "हो", 90); addHiBigram("आप", "कहाँ", 70); addHiBigram("मैं", "ठीक", 85);
        addHiBigram("ठीक", "हूँ", 95); addHiBigram("ठीक", "है", 90); addHiBigram("शुभ", "रात्रि", 85);
        addHiBigram("शुभ", "प्रभात", 85); addHiBigram("शुभ", "दिन", 80); addHiBigram("शुभ", "संध्या", 70);
        addHiBigram("बहुत", "बहुत", 60); addHiBigram("बहुत", "धन्यवाद", 90); addHiBigram("बहुत", "अच्छा", 85);
        addHiBigram("क्या", "हाल", 85); addHiBigram("क्या", "आप", 70); addHiBigram("क्या", "हुआ", 70);
        addHiBigram("आपका", "नाम", 85); addHiBigram("मेरा", "नाम", 90); addHiBigram("मुझे", "पता", 80);
        addHiBigram("मुझे", "नहीं", 75); addHiBigram("जन्मदिन", "की", 90); addHiBigram("की", "हार्दिक", 60);
        addHiBigram("हार्दिक", "शुभकामनाएँ", 95); addHiBigram("हार्दिक", "बधाई", 90);
        addUrBigrams();
    }

    // Urdu next-word seeds: everyday phrases, then the most common pairs of the Makhzan corpus.
    private static final String[] UR_BIGRAMS = {
            "السلام","علیکم","98", "وعلیکم","السلام","98", "آپ","کیسے","95", "آپ","کیسی","80", "کیسے","ہیں","95",
            "کیسی","ہیں","90", "میں","ٹھیک","85", "ٹھیک","ہوں","95", "ٹھیک","ہے","92", "بہت","شکریہ","95",
            "بہت","اچھا","85", "خدا","حافظ","98", "اللہ","حافظ","95", "جزاک","اللہ","98", "صبح","بخیر","95",
            "شب","بخیر","95", "کیا","حال","88", "حال","ہے","85", "سالگرہ","مبارک","98", "عید","مبارک","98",
            "ان","شاء","70", "شاء","اللہ","98", "ماشاء","اللہ","98", "آپ","کا","70", "کوئی","بات","80", "بات","نہیں","85",
            "کے","لیے","88", "کے","ساتھ","85", "کے","بعد","84", "ہے","کہ","90", "ہے","اور","85", "ہے","جو","81",
            "میں","سے","80", "میں","اس","80", "میں","بھی","80", "کی","طرف","81", "کی","وجہ","80", "کی","ہے","79",
            "سے","زیادہ","78", "سے","بھی","78", "سے","پہلے","78", "اور","اس","84", "اور","ان","81", "اور","وہ","77",
            "اس","کے","86", "اس","کی","84", "اس","کا","83", "کو","بھی","79", "کو","اس","78", "کو","ایک","76",
            "کا","کہنا","79", "کا","ایک","78", "کا","یہ","77", "ہیں","کہ","82", "ہیں","اور","82", "ہیں","جو","77",
            "نے","کہا","81", "نے","اس","80", "نے","اپنے","78", "نہیں","ہے","81", "نہیں","کیا","77", "نہیں","ہو","76",
            "کہ","وہ","82", "کہ","اس","81", "کہ","یہ","80", "ایک","دوسرے","75", "ایک","ہی","74", "ایک","اور","74",
            "یہ","ہے","81", "یہ","بات","77", "یہ","بھی","77", "پر","بھی","75", "پر","اس","73", "پر","ایک","73",
            "کیا","ہے","82", "کیا","گیا","81", "کیا","جا","77", "کر","دیا","82", "کر","کے","81", "کر","دی","77",
            "ہو","گیا","78", "ہو","گا","77", "ہو","کر","76", "وہ","اس","76", "وہ","اپنے","74", "وہ","بھی","73",
            "بھی","اس","76", "بھی","نہیں","75", "بھی","ہے","75", "ان","کے","85", "ان","کی","84", "ان","کا","81",
            "تو","اس","79", "تو","وہ","76", "تو","یہ","76", "تھا","کہ","82", "تھا","اور","76", "تھا","جس","71",
            "تم","نے","68", "تم","سے","66", "ہم","نے","77", "ہم","اس","71", "ہم","ان","68", "ہوں","گے","75",
            "ہوں","کہ","72", "ہوں","تو","70", "کرنے","کے","80", "کرنے","کی","79", "کرنے","کا","77", "گیا","ہے","82",
            "گیا","تھا","76", "گیا","کہ","74", "جو","اس","74", "جو","ان","72", "جو","کہ","71", "لیے","کہ","73",
            "لیے","اس","72", "لیے","ایک","72", "مجھے","اس","66", "ساتھ","ساتھ","74", "ساتھ","ہی","70", "ساتھ","اس","69",
            "اپنے","اپنے","69", "اپنے","گھر","67", "اپنے","اندر","66", "ہی","نہیں","75", "ہی","میں","72", "ہی","کی","71",
            "گا","کہ","73", "گا","اور","72", "گا","تو","68", "رہے","ہیں","81", "رہے","کہ","74", "رہے","گا","70",
            "کچھ","نہیں","71", "کچھ","بھی","68", "کچھ","اور","66", "لیکن","اس","75", "لیکن","یہ","72", "لیکن","ان","71",
            "دیا","گیا","79", "دیا","ہے","79", "دیا","تھا","73", "رہا","ہے","80", "رہا","تھا","74", "رہا","ہوں","70",
            "کسی","بھی","75", "کسی","کو","73", "کسی","نے","70", "سب","سے","79", "سب","کچھ","71", "سب","کو","69",
            "طرح","کے","72", "طرح","کی","72", "طرح","سے","68", "نہ","ہو","76", "نہ","صرف","74", "نہ","ہی","72",
            "اسے","ایک","66", "بات","کی","74", "بات","کا","72", "بات","ہے","72", "اب","تک","72", "اب","بھی","70",
            "اب","یہ","68", "تھے","اور","75", "تھے","کہ","72", "تھے","جو","67", "یا","اس","71", "یا","پھر","71",
            "یا","نہیں","69", "یہاں","تک","71", "یہاں","یہ","66", "جس","میں","79", "جس","کے","78", "جس","کی","77",
            "صرف","ایک","71", "صرف","اس","68", "صرف","یہ","68", "گے","اور","72", "گے","تو","70", "گے","کہ","70",
            "تک","کہ","71", "تک","محدود","70", "تک","اس","68", "کرتے","ہیں","81", "کرتے","تھے","73", "کرتے","رہے","68",
            "کہا","کہ","81", "کہا","ہے","74", "کہا","گیا","73", "بہت","سے","74", "بہت","سی","70", "بہت","زیادہ","69",
            "جا","سکتا","78", "جا","رہا","76", "جا","سکتی","74", "اپنی","کتاب","68", "اپنی","جگہ","67",
            "اپنی","زندگی","67", "جب","کہ","76", "جب","تک","73", "جب","وہ","71", "سکتا","ہے","80", "سکتا","تھا","69",
            "سکتا","کہ","66", "زیادہ","تر","70", "زیادہ","سے","68", "زیادہ","ہے","66", "بعد","میں","73", "بعد","اس","69",
            "بعد","بھی","69", "اگر","وہ","71", "اگر","یہ","70", "اگر","اس","70", "اللہ","علیہ","80", "اللہ","کے","75",
            "اللہ","کی","74", "سکتے","ہیں","77", "سکتے","تھے","67", "دو","ہزار","67", "تھی","کہ","74", "تھی","اور","73",
            "تھی","جس","68", "کرنا","ہے","72", "کرنا","چاہیے","70", "کرنا","چاہتے","68", "پھر","اس","71", "پھر","بھی","68",
            "پھر","یہ","68", "وقت","کے","70", "وقت","تک","69", "وقت","میں","68", "طور","پر","83", "ہوا","ہے","77",
            "ہوا","کہ","73", "ہوا","تھا","72", "ہر","شخص","69", "ہر","ایک","68", "ہر","چیز","67", "بارے","میں","80",
            "حاصل","کر","72", "حاصل","ہے","71", "حاصل","کرنے","71", "کریں","گے","76", "کریں","اور","69", "کریں","تو","68",
            "کام","کرنے","70", "کام","کر","69", "کام","ہے","66", "پاکستان","کے","77", "پاکستان","میں","76",
            "پاکستان","کی","75", "والے","افراد","66", "والے","اس","66", "والے","ایک","66", "پہلے","ہی","70",
            "پہلے","سے","69", "ہونے","والے","76", "ہونے","کے","75", "ہونے","کی","74", "طرف","سے","77", "طرف","اشارہ","66",
            "ہوتا","ہے","82", "ہوتا","تھا","68", "ہوتا","تو","68", "جاتا","ہے","82", "جاتا","تھا","69", "ہمارے","ہاں","69",
            "ہمارے","لیے","67", "کرتا","ہے","79", "کرتا","ہوں","69", "کرتا","تھا","68", "لے","کر","76", "لے","لیا","66",
            "ضرورت","ہے","73", "ضرورت","نہیں","69", "لوگوں","کو","75", "لوگوں","کے","74", "لوگوں","کی","73",
            "تمام","تر","68", "اسی","طرح","78", "اسی","لیے","70", "اسی","کے","67", "دے","کر","73", "دے","دیا","71",
            "دے","دی","70", "نام","سے","73", "نام","پر","72", "نام","ہے","68", "وہاں","سے","67", "دی","ہے","74",
            "دی","جاتی","70", "دی","تھی","69", "کیوں","نہیں","71", "کیوں","نہ","69", "کیوں","کہ","67", "انہوں","نے","80",
            "گی","اور","70", "گی","کہ","66", "گی","تو","66", "صاحب","نے","74", "صاحب","کے","73", "صاحب","کی","71",
            "دنیا","میں","76", "دنیا","کے","74", "دنیا","کی","72", "کبھی","بھی","66", "لوگ","اس","67", "لوگ","ہیں","66",
            "وجہ","سے","80", "وجہ","ہے","72", "وجہ","یہ","69", "رہی","ہے","78", "رہی","ہیں","73", "رہی","تھی","71"
    };

    private void addUrBigrams() {
        for (int i = 0; i + 2 < UR_BIGRAMS.length; i += 3) {
            String prev = fold(UR_BIGRAMS[i]);
            Map<String, Integer> m = baseUrBigrams.get(prev);
            if (m == null) { m = new HashMap<>(); baseUrBigrams.put(prev, m); }
            m.put(UR_BIGRAMS[i + 1], Integer.parseInt(UR_BIGRAMS[i + 2]));
        }
    }

    private void addHiWord(String w, int weight) { baseHiWords.put(w, weight); }

    private void addHiBigram(String prev, String next, int weight) {
        prev = fold(prev);
        Map<String, Integer> m = baseHiBigrams.get(prev);
        if (m == null) { m = new HashMap<>(); baseHiBigrams.put(prev, m); }
        m.put(next, weight);
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
        // Never store a word under a language whose writing system it isn't in.
        if (!scriptOk(curr, isArabic)) return;
        // Urdu-only letters never belong to an untagged Arabic word.
        if (isArabic && hasUrduOnlyLetters(curr)) return;

        String existing = findExistingKey(curr);
        String key = existing != null ? existing : curr;
        boolean wasKnown = learnedWords.containsKey(key);
        int oldCount = wasKnown ? learnedWords.get(key) : 0;
        learnedWords.put(key, oldCount + 1);
        tagLatinWord(key, isArabic, wasKnown);
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

    private static boolean hasUrduOnlyLetters(String t) {
        for (int i = 0; i < t.length(); i++) {
            if ("\u0679\u0688\u0691\u06BA\u06D2\u06BE\u06C1\u06AF\u0686\u067E\u06A9\u06CC\u0698".indexOf(t.charAt(i)) >= 0) return true;
        }
        return false;
    }

    /** Training sentence typed in the settings screen (no keyboard mode there): each word is
     *  learned under the language its writing system belongs to, and only if that language is
     *  the active one, so Arabic and Urdu (same script) and the Latin languages never mix. */
    public synchronized void learnFullSentence(String sentence) {
        if (sentence == null || !ready) return;
        boolean urduSentence = hasUrduOnlyLetters(sentence);
        if (urduSentence && !latinIsUrdu) return;
        String[] rawTokens = sentence.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String r : rawTokens) {
            String n = normalize(r);
            if (!n.isEmpty()) tokens.add(n);
        }
        for (int i = 0; i < tokens.size(); i++) {
            String curr = tokens.get(i);
            String prev = i > 0 ? tokens.get(i - 1) : "";
            boolean arabicScript = scriptOf(curr) == SC_ARABIC;
            learnWordAndTransition(curr, prev, arabicScript && !urduSentence);
        }
    }

    /** Save button of the settings training field. bkey already learned each word as it was typed,
     *  in the language it was typed in, so saved words are only reinforced and keep their languages;
     *  a word not saved yet (the last one, with no space after it) is learned under langCode, the
     *  language the keyboard was typing in. */
    public synchronized void learnTrainingSentence(String sentence, String langCode) {
        if (sentence == null || !ready || langCode == null) return;
        String before = activeLangCode;
        setActiveLatinLanguage(langCode);
        boolean arabic = KeyboardPreferences.LANG_AR.equals(langCode);
        String[] rawTokens = sentence.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String r : rawTokens) {
            String n = normalize(r);
            if (!n.isEmpty()) tokens.add(n);
        }
        keepKnownTags = true;
        try {
            for (int i = 0; i < tokens.size(); i++) {
                String prev = i > 0 ? tokens.get(i - 1) : "";
                String prev2 = i > 1 ? tokens.get(i - 2) : "";
                learnWordAndTransition(tokens.get(i), prev, prev2, arabic);
            }
        } finally {
            keepKnownTags = false;
            setActiveLatinLanguage(before);
        }
    }

    public synchronized void learnFullSentence(String sentence, boolean arabicMode) {
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
            // Learn under the language that is actually active; words in another writing system
            // are skipped (learnWordAndTransition also refuses them).
            learnWordAndTransition(curr, prev, arabicMode);
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
        // A pasted link, a number or a run of symbols is not a word to complete or to offer back
        // as a suggestion (it would also let a swipe replace it). Saved shortcuts still work.
        // Checked on the raw text: normalize() strips the ":" and "." that give a link away.
        if (!prefix.isEmpty() && looksLikeLinkOrHasNoLetters(currentPrefixRaw == null ? "" : currentPrefixRaw.trim())
                && (keyboardPrefs == null || keyboardPrefs.findShortcutExpansion(prefix) == null)) {
            return Arrays.asList("", "", "");
        }
        if (!isArabic && latinIsFrench && !noSlotPadding) {
            // "l'hom" -> "l'homme": when no word starts with the whole thing, suggest for the
            // word after the apostrophe and keep the elision in front.
            String[] el = splitFrenchElision(prefix);
            if (el != null && !el[1].isEmpty() && !hasWordStartingWith(fold(prefix.replace('\u2019', '\'')))) {
                List<String> inner;
                noSlotPadding = true;
                try {
                    inner = getThreeSuggestions(el[1], lastCompletedWordRaw, lastCompletedWord2Raw, false);
                } finally {
                    noSlotPadding = false;
                }
                List<String> out = new ArrayList<>(inner.size());
                for (String sug : inner) out.add(sug == null || sug.isEmpty() ? "" : el[0] + sug);
                return out;
            }
        }
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
                Map<String, Integer> lNext = personalOff() ? null : learnedBigrams.get(prevKey);
                if (lNext == null && !personalOff()) lNext = learnedBigrams.get(lastWord);
                if (lNext != null) {
                    for (Map.Entry<String, Integer> e : lNext.entrySet()) {
                        String nw = e.getKey();
                        if (fold(nw).startsWith(prefixLower) && !nw.equals(prefix) && isTrustedWord(nw)
                                && learnedAllowed(nw, isArabic)
                                && !(prefixLower.length() < 3 && nw.indexOf('@') >= 0)) {
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
            for (List<String> candidates : (personalOff() ? java.util.Collections.<List<String>>emptyList() : learnedByFold.subMap(prefixLower, prefixUpperBound).values())) {
                for (String w : candidates) {
                    if (!isTrustedWord(w)) continue;
                    if (!learnedAllowed(w, isArabic)) continue;
                    // Saved email addresses only show once at least three characters are typed (a two-letter name plus "@" counts).
                    if (prefixLower.length() < 3 && w.indexOf('@') >= 0) continue;
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
                if (!personalOff()) collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, scored, 30, !isArabic);
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
                Map<String, Integer> triNext = personalOff() ? null : learnedTrigrams.get(triKey);
                if (triNext != null) {
                    int bestCount = -1;
                    for (Map.Entry<String, Integer> e : triNext.entrySet()) {
                        String nw = e.getKey();
                        if (!isTrustedWord(nw)) continue;
                        if (!learnedAllowed(nw, isArabic)) continue;
                        int cur = scored.containsKey(nw) ? scored.get(nw) : 0;
                        scored.put(nw, cur + e.getValue() * 80 + 400);
                        if (e.getValue() > bestCount) {
                            bestCount = e.getValue();
                            bestTrigramWord = nw;
                        }
                    }
                }
            }

            Map<String, Integer> lNext = personalOff() ? null : learnedBigrams.get(prevKey);
            if (lNext == null && !personalOff()) lNext = learnedBigrams.get(lastWord);
            if (lNext != null) {
                for (Map.Entry<String, Integer> e : lNext.entrySet()) {
                    String nw = e.getKey();
                    if (!isTrustedWord(nw)) continue;
                    if (!learnedAllowed(nw, isArabic)) continue;
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
                            && learnedAllowed(chainBestWord, isArabic)) {
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

    /** http(s)://..., www.... , or text with no letter at all (digits, symbols, emoji). */
    static boolean looksLikeLinkOrHasNoLetters(String w) {
        if (w == null || w.isEmpty()) return false;
        String lower = w.toLowerCase(Locale.ROOT);
        if (lower.contains("://") || lower.startsWith("www.")) return true;
        for (int i = 0; i < w.length(); ) {
            int cp = w.codePointAt(i);
            if (Character.isLetter(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    public synchronized String findAutoCorrection(String wordRaw, boolean isArabic) {
        return findAutoCorrection(wordRaw, isArabic, false);
    }

    private static final Pattern FR_ELISION =
            Pattern.compile("^(?i)(jusqu|lorsqu|puisqu|quoiqu|quelqu|aujourd|qu|c|j|l|d|n|m|t|s)(['\u2019])(.*)$");

    /** French elision ("l'homme", "j'ai"): the part up to and including the apostrophe, and the
     *  word after it. Null when the word has no elision in front. */
    private static String[] splitFrenchElision(String w) {
        if (w == null) return null;
        java.util.regex.Matcher m = FR_ELISION.matcher(w);
        if (!m.matches()) return null;
        return new String[]{m.group(1) + m.group(2), m.group(3)};
    }

    /** True when some dictionary or learned word starts with this folded prefix. */
    private boolean hasWordStartingWith(String foldedPrefix) {
        String k = currentLatinByFold().ceilingKey(foldedPrefix);
        if (k != null && k.startsWith(foldedPrefix)) return true;
        String l = learnedByFold.ceilingKey(foldedPrefix);
        return l != null && l.startsWith(foldedPrefix);
    }

    /** strong = also correct close calls (best candidate not clearly ahead of the runner-up). */
    public synchronized String findAutoCorrection(String wordRaw, boolean isArabic, boolean strong) {
        if (!ready) return null;
        if (isProtectedToken(wordRaw)) return null;
        String word = normalize(wordRaw);
        if (word.length() < 3) return null;
        if (!isArabic && latinIsFrench) {
            // "c'est", "l'homme": a known word is left alone; otherwise only the word after the
            // apostrophe is corrected, and the elision in front is kept as typed.
            String[] el = splitFrenchElision(word);
            if (el != null) {
                if (isTrustedWord(word.replace('\u2019', '\'')) || isTrustedWord(word)) return null;
                if (el[1].length() < 3) return null;
                String inner = findAutoCorrection(el[1], false, strong);
                return inner == null ? null : el[0] + inner;
            }
        }
        String wordLower = fold(word);
        String sure = isArabic ? COMMON_AR_TYPOS.get(wordLower)
                : latinIsUrdu ? COMMON_UR_TYPOS.get(wordLower)
                : (latinIsHindi ? COMMON_HI_TYPOS.get(wordLower)
                : latinIsFrench ? COMMON_FR_TYPOS.get(wordLower)
                : latinIsSpanish ? COMMON_ES_TYPOS.get(wordLower)
                // Indonesian has no certain-typo list: English fixes must never touch its words.
                : latinIsIndonesian ? null
                : (EN_NOT_SURE.contains(wordLower) ? null : COMMON_EN_TYPOS.get(wordLower)));
        if (sure != null) return isArabic ? sure : matchCase(word, sure);
        if (isTrustedWord(word)) return null;
        // Hindi, Urdu, Bangla and Indonesian: their bundled lists leave out many real words, so a
        // real word they don't know must never be silently swapped for a "close" one. Only the
        // certain-typo map above corrects them on its own; everything else stays a suggestion.
        if ((latinIsHindi || latinIsUrdu || latinIsBangla || latinIsIndonesian) && !isArabic) return null;
        if (!isArabic && !latinIsSpanish && !latinIsHindi && !latinIsFrench && !latinIsIndonesian) {
            String fixed = COMMON_EN_TYPOS.get(wordLower);
            if (fixed != null) return matchCase(word, fixed);
        }
        Map<String, Integer> candidates = new HashMap<>();
        if (!personalOff()) collectNearMissCandidates(wordLower, learnedByFold, learnedWords, candidates, 30, !isArabic);
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
    private static final String[] AZERTY_ROWS = {"azertyuiop", "qsdfghjklm", "wxcvbn"};
    private static final String[] SPANISH_ROWS = {"qwertyuiop", "asdfghjklñ", "zxcvbnm"};

    private boolean keysAdjacent(char a, char b) {
        String[] layout = (latinIsFrench && keyboardPrefs != null && keyboardPrefs.isFrenchAzerty())
                ? AZERTY_ROWS : latinIsSpanish ? SPANISH_ROWS : QWERTY_ROWS;
        int ra = -1, ca = -1, rb = -1, cb = -1;
        for (int r = 0; r < layout.length; r++) {
            int i = layout[r].indexOf(a);
            if (i >= 0) { ra = r; ca = i; }
            int j = layout[r].indexOf(b);
            if (j >= 0) { rb = r; cb = j; }
        }
        if (ra < 0 || rb < 0 || a == b) return false;
        return Math.abs(ra - rb) <= 1 && Math.abs(ca - cb) <= 1;
    }

    /** Same length, exactly one differing letter, and the two letters sit on neighbouring keys. */
    private boolean isNeighborKeySlip(String a, String b) {
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
    private static final Map<String, String> COMMON_HI_TYPOS = new HashMap<>();
    private static final Map<String, String> COMMON_FR_TYPOS = new HashMap<>();
    // Urdu: only spellings that are never right (ھ starting a word instead of ہ, and a few
    // well-known misspellings). Everything else stays a suggestion, as with Hindi.
    private static final Map<String, String> COMMON_UR_TYPOS = new HashMap<>();
    static {
        String[] ur = {
            "ھیں","ہیں", "ھوں","ہوں", "ھوا","ہوا", "ھاں","ہاں", "ھمیں","ہمیں", "ھمارا","ہمارا", "ھماری","ہماری",
            "ھمارے","ہمارے", "ھوگا","ہوگا", "ھوگی","ہوگی", "ھوتا","ہوتا", "ھوتی","ہوتی", "ھوتے","ہوتے", "ھوئے","ہوئے",
            "ھوئی","ہوئی", "ھونا","ہونا", "ھونے","ہونے", "ھاتھ","ہاتھ", "ھمیشہ","ہمیشہ", "ھزار","ہزار", "ھجرت","ہجرت",
            "ھندوستان","ہندوستان", "بلکل","بالکل", "ذیادہ","زیادہ", "شکریا","شکریہ", "غلتی","غلطی", "زروری","ضروری",
            "زرور","ضرور", "مھربانی","مہربانی", "صحیع","صحیح", "کیونکے","کیونکہ", "حالانکے","حالانکہ"
        };
        for (int i = 0; i + 1 < ur.length; i += 2) COMMON_UR_TYPOS.put(foldUncached(ur[i]), ur[i + 1]);
    }
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
        String[] fr = {
            "tres","très", "deja","déjà", "apres","après", "etre","être", "etait","était", "etaient","étaient",
            "ete","été", "ecole","école", "eleve","élève", "equipe","équipe", "etude","étude", "etudes","études",
            "etat","état", "etranger","étranger", "elle-meme","elle-même", "lui-meme","lui-même", "aujourdhui","aujourd'hui", "aujourd'hui","aujourd'hui",
            "quelquun","quelqu'un", "jusquau","jusqu'au", "pourqoui","pourquoi", "pourquio","pourquoi", "bonjoure","bonjour", "bonjuor","bonjour",
            "merci beaucoup","merci beaucoup", "beacoup","beaucoup", "beaucou","beaucoup", "baucoup","beaucoup", "vraiement","vraiment", "vraimant","vraiment",
            "evidement","évidemment", "evidemment","évidemment", "completement","complètement", "probleme","problème", "problemes","problèmes",
            "systeme","système", "theatre","théâtre", "arret","arrêt", "fenetre","fenêtre", "tete","tête", "fete","fête",
            "foret","forêt", "interet","intérêt", "hopital","hôpital", "role","rôle", "plutot","plutôt", "bientot","bientôt",
            "ca va","ça va", "francais","français", "francaise","française", "garcon","garçon", "lecon","leçon", "facon","façon",
            "recu","reçu", "decu","déçu", "ca","ça", "voila","voilà", "colere","colère", "mere","mère",
            "pere","père", "frere","frère", "soeur","sœur", "coeur","cœur", "oeuvre","œuvre", "oeil","œil",
            "soeurs","sœurs", "coeurs","cœurs", "oeuvres","œuvres", "oeuf","œuf", "oeufs","œufs", "voeu","vœu", "voeux","vœux",
            "noeud","nœud", "boeuf","bœuf", "cest","c'est", "jai","j'ai", "quil","qu'il", "daccord","d'accord", "cetait","c'était",
            "jetais","j'étais", "jaime","j'aime", "sil","s'il", "nest","n'est", "jusqua","jusqu'à",
            "developpement","développement", "dévelopement","développement", "developper","développer", "telephone","téléphone", "numero","numéro",
            "societe","société", "securite","sécurité", "qualite","qualité", "realite","réalité", "liberte","liberté", "verite","vérité",
            "identite","identité", "activite","activité", "universite","université", "difficulte","difficulté", "possibilite","possibilité", "generalement","généralement",
            "reponse","réponse", "repondre","répondre", "message recu","message reçu", "desole","désolé", "desolee","désolée", 
            "mauvaie","mauvaise", "diférent","différent", "diferent","différent", "different","différent", "interessant","intéressant",
            "interessante","intéressante", "enchante","enchanté", "ecouter","écouter", "ecrire","écrire", "ecrit","écrit", "eviter","éviter",
            "presenter","présenter", "preferer","préférer", "prefere","préféré", "premiere","première", "derniere","dernière", "entiere","entière",
            "maniere","manière", "matiere","matière", "lumiere","lumière", "riviere","rivière", "frontiere","frontière", "grace","grâce",
            "ame","âme", "gout","goût", "diner","dîner", "ile","île", "maitre","maître",
            "connaitre","connaître", "paraitre","paraître", "naitre","naître", "reussir","réussir", "reussi","réussi", "reveil","réveil",
            "reunion","réunion", "region","région", "decision","décision", "decider","décider", 
            "demain matin","demain matin", "mecontent","mécontent", "medecin","médecin", "medicament","médicament", "memoire","mémoire", "menage","ménage",
            "cinema","cinéma", "bebe","bébé", "cafe","café", "ecran","écran", "energie","énergie",
            "annee","année", "annees","années", "journee","journée", "soiree","soirée", "idee","idée", "idees","idées",
            "arrivee","arrivée", "entree","entrée", "denree","denrée", "armee","armée", "musee","musée", "lycee","lycée"
        };
        for (int i = 0; i + 1 < fr.length; i += 2) {
            if (!fr[i].equals(fr[i + 1]) && fr[i].indexOf(' ') < 0) COMMON_FR_TYPOS.put(foldUncached(fr[i]), fr[i + 1]);
        }
        String[] hi = {
            "कृप्या","कृपया", "परिक्षा","परीक्षा", "बिमार","बीमार", "उज्जवल","उज्ज्वल", "अत्याधिक","अत्यधिक",
            "आर्शीवाद","आशीर्वाद", "आशिर्वाद","आशीर्वाद", "श्रीमति","श्रीमती", "पूर्ती","पूर्ति", "निरिक्षण","निरीक्षण",
            "प्रतिक्षा","प्रतीक्षा", "सुचना","सूचना", "शुभकामनाए","शुभकामनाएँ", "धन्यवाद्","धन्यवाद", "ब्रम्हा","ब्रह्मा",
            "अतिथी","अतिथि", "प्रार्थाना","प्रार्थना", "कार्यकृम","कार्यक्रम"
        };
        for (int i = 0; i + 1 < hi.length; i += 2) {
            COMMON_HI_TYPOS.put(foldUncached(hi[i]), hi[i + 1]);
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
        if (!personalOff()) collectNearMissCandidates(prefixLower, learnedByFold, learnedWords, cand, 30, !isArabic);
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
        collectNearMissImpl(prefixLower, byFold, wordCounts, scored, baseScore, 0);
    }

    // filterLatin restricts to the active Latin sub-language (English vs Spanish) -- only
    // meaningful for the learned-words map, since that's the one shared between them; base
    // dictionary lookups already pass the right language-specific map and are never tagged.
    private void collectNearMissCandidates(String prefixLower, java.util.TreeMap<String, List<String>> byFold,
                                            Map<String, Integer> wordCounts, Map<String, Integer> scored, int baseScore,
                                            boolean filterLatin) {
        collectNearMissImpl(prefixLower, byFold, wordCounts, scored, baseScore, filterLatin ? 1 : 2);
    }

    // mode 0 = base dictionary of the active language (no filtering needed), 1 = learned words
    // while a Latin/Hindi/Urdu language is active, 2 = learned words while Arabic is active.
    private void collectNearMissImpl(String prefixLower, java.util.TreeMap<String, List<String>> byFold,
                                            Map<String, Integer> wordCounts, Map<String, Integer> scored, int baseScore,
                                            int mode) {
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
                if (mode == 1 && !latinWordAllowed(w)) continue;
                if (mode == 2 && (!scriptOk(w, true) || !arabicTagOk(w))) continue;
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

    private List<String> getTopLearnedWords(boolean isArabic, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(learnedWords.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : list) {
            if (out.size() >= limit) break;
            String w = e.getKey();
            if (e.getValue() < 2 || w.indexOf('@') >= 0) continue;
            if (!scriptOk(w, isArabic)) continue;
            if (!learnedAllowed(w, isArabic)) continue;
            out.add(w);
        }
        return out;
    }

    private List<String> activeDefaultWords(boolean isArabic) {
        List<String> all = isArabic
                ? Arrays.asList("السلام", "بسم", "مرحبا", "إن", "صباح", "الحمد")
                : latinIsHindi
                    ? Arrays.asList("मैं", "आप", "नमस्ते", "धन्यवाद", "क्या", "ठीक")
                : latinIsBangla
                    ? Arrays.asList("আমি", "আপনি", "নমস্কার", "ধন্যবাদ", "কি", "ঠিক")
                : latinIsUrdu
                    ? Arrays.asList("میں", "آپ", "السلام", "شکریہ", "کیا", "ٹھیک")
                : latinIsFrench
                    ? Arrays.asList("Le", "Je", "Et", "Bonjour", "Merci", "Salut")
                : latinIsIndonesian
                    ? Arrays.asList("Saya", "Aku", "Ini", "Halo", "Terima", "Selamat")
                : latinIsSpanish
                    ? Arrays.asList("El", "Yo", "En", "Hola", "Gracias", "Bueno")
                    : Arrays.asList("The", "I", "On", "Hello", "Thanks", "Good");
        String defaultsLangCode = isArabic ? KeyboardPreferences.LANG_AR : activeLatinTag();
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

    /** Set while suggesting for the word after a French elision: no unrelated learned words. */
    private boolean noSlotPadding = false;

    private List<String> placeholderSlots(boolean isArabic) {
        return Arrays.asList("", "", "");
    }

    private List<String> formatToThreeSlots(List<String> candidates, boolean isArabic) {
        List<String> combined = new ArrayList<>();
        java.util.Set<String> seenFolded = new java.util.HashSet<>();
        for (String c : candidates) {
            if (seenFolded.add(fold(c))) combined.add(c);
        }

        for (String t : (personalOff() || noSlotPadding ? java.util.Collections.<String>emptyList() : getTopLearnedWords(isArabic, 8))) {
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
                if (okKey(k) && v != null && v.length() <= 24 && !learnedWordLatinTag.containsKey(k)) learnedWordLatinTag.put(k, v);
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

    // The keyboard saves what it learned once typing pauses (not after every word): writing the
    // whole learned list costs battery, and hiding the keyboard flushes it anyway. Settings keep
    // the short delay so an edit there reaches the keyboard right away.
    private volatile boolean idleSave = false;
    private long firstPendingSaveAt = 0L;
    private static final long SAVE_DELAY_MS = 900L;
    private static final long IDLE_SAVE_DELAY_MS = 8000L;
    private static final long IDLE_SAVE_MAX_WAIT_MS = 60000L;

    /** Used by the keyboard service: save after 8 s without typing (at most a minute late). */
    public void setIdleSave(boolean on) {
        idleSave = on;
    }

    private void scheduleSave() {
        long now = android.os.SystemClock.uptimeMillis();
        if (!idleSave) {
            if (saveScheduled) return;
            saveScheduled = true;
            saveHandler.postDelayed(saveRunnable, SAVE_DELAY_MS);
            return;
        }
        if (!saveScheduled) {
            saveScheduled = true;
            firstPendingSaveAt = now;
        } else if (now - firstPendingSaveAt >= IDLE_SAVE_MAX_WAIT_MS) {
            return; // already waiting long enough: let the pending save run
        }
        saveHandler.removeCallbacks(saveRunnable);
        long wait = Math.min(IDLE_SAVE_DELAY_MS, Math.max(0L, firstPendingSaveAt + IDLE_SAVE_MAX_WAIT_MS - now));
        saveHandler.postDelayed(saveRunnable, wait);
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
