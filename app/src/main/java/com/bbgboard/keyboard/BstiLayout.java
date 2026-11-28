package com.bbgboard.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bangla (BSTI Mobile) keyboard: the National Keyboard Standard (revised) BDS 1738:2018,
 * adapted for touch by the Ekushey project.
 *
 * <p>Derived from Ekushey's <em>Bangla BSTI Mobile</em> layout (CC-BY-4.0,
 * <a href="https://github.com/ekushey-org/bangla-bsti-mobile">bangla-bsti-mobile</a>).
 * Typing follows the Unicode logical order (consonant, then its kar). Three letter rows only:
 * the view appends its own bottom row, and each key's {@code sub} is the Latin key it sits on.
 * Full vowels sit on the long-press of their kar; the Shift layer puts the common conjuncts on
 * long-press. Precomposed letters and conjuncts are written as explicit Unicode escapes so their
 * codepoint order does not depend on how this file was normalised.
 */
public final class BstiLayout {

    // Top row (q..p).
    private static final String[] ROW1_BASE = {"ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "\u09DC"};
    private static final String[] ROW1_SHIFT = {"ং", "\u09DF", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "\u09DD"};
    private static final String HINTS1_BASE = "qwertyuiop";
    private static final String HINTS1_SHIFT = "QWERTYUIOP";

    // Home row (a..l). g is the hasanta.
    private static final String[] ROW2_BASE = {"ৃ", "ু", "ি", "া", "\u09CD", "ব", "ক", "ত", "দ"};
    private static final String[] ROW2_SHIFT = {"\u09B0\u09CD", "ূ", "ী", "অ", "ঁ", "ভ", "খ", "থ", "ধ"};
    private static final String HINTS2_BASE = "asdfghjkl";
    private static final String HINTS2_SHIFT = "ASDFGHJKL";

    // Bottom row (z..m), in the row that also holds Shift and Backspace.
    // \u09CB O-KAR and \u09CC AU-KAR are escaped: both decompose canonically into two signs.
    private static final String[] ROW3_BASE = {"\u09CD\u09AF", "\u09CB", "ে", "র", "ন", "স", "ম"};
    private static final String[] ROW3_SHIFT = {"\u09CD\u09AF", "\u09CC", "ৈ", "ল", "ণ", "ষ", "শ"};
    private static final String HINTS3_BASE = "zxcvbnm";
    private static final String HINTS3_SHIFT = "ZXCVBNM";

    /**
     * Long-press options keyed by the key's typed (Bengali) character. The two layers share only
     * ya-phala on z, which carries no options, so one table serves both.
     */
    private static final Map<String, List<String>> ALT_EXTRA;

    static {
        Map<String, List<String>> extra = new HashMap<>();
        // Normal layer: independent vowels on their kar, khanda-ta on ta, visarga on ma.
        extra.put("ৃ", alts("\u098B")); // ঋ
        extra.put("ু", alts("\u0989")); // উ
        extra.put("ি", alts("\u0987")); // ই
        extra.put("া", alts("\u0986")); // আ
        extra.put("ত", alts("\u09CE")); // ৎ
        extra.put("\u09CB", alts("\u0993")); // ও
        extra.put("ে", alts("\u098F")); // এ
        extra.put("ম", alts("\u0983")); // ঃ

        // Shift layer: the remaining independent vowels and the high-frequency conjuncts.
        extra.put("ং", alts("\u0999\u09CD\u0995")); // ঙ্ক
        extra.put("\u09DF", alts("\u099E\u09CD\u099D")); // ঞ্ঝ
        extra.put("ছ", alts("\u099E\u09CD\u099B")); // ঞ্ছ
        extra.put("ঝ", alts("\u099C\u09CD\u099E")); // জ্ঞ
        extra.put("ঞ", alts("\u099E\u09CD\u099C")); // ঞ্জ
        extra.put("ঘ", alts("\u0999\u09CD\u0997")); // ঙ্গ
        extra.put("ূ", alts("\u098A")); // ঊ
        extra.put("ী", alts("\u0988")); // ঈ
        extra.put("খ", alts("\u0995\u09CD\u09B7")); // ক্ষ
        extra.put("\u09CC", alts("\u0994")); // ঔ
        extra.put("ৈ", alts("\u0990")); // ঐ
        extra.put("ল", alts("\u09AC\u09BC\u09CD\u09AF")); // ব়্য: BA + NUKTA + HASANTA + YA
        extra.put("ণ", alts("\u099E\u09CD\u099A")); // ঞ্চ
        extra.put("ষ", alts("\u09B7\u09CD\u09A3")); // ষ্ণ
        extra.put("শ", alts("\u09B9\u09CD\u09AE")); // হ্ম
        ALT_EXTRA = Collections.unmodifiableMap(extra);
    }

    private static final List<List<BlackBerryKeyboardView.KeyDef>> BASE_ROWS;
    private static final List<List<BlackBerryKeyboardView.KeyDef>> SHIFT_ROWS;

    static {
        BASE_ROWS = buildRows(false);
        SHIFT_ROWS = buildRows(true);
    }

    private BstiLayout() {
    }

    /** The three letter rows for the requested layer, ready to be drawn as-is. */
    public static List<List<BlackBerryKeyboardView.KeyDef>> rows(boolean shifted) {
        return shifted ? SHIFT_ROWS : BASE_ROWS;
    }

    private static List<List<BlackBerryKeyboardView.KeyDef>> buildRows(boolean shifted) {
        List<List<BlackBerryKeyboardView.KeyDef>> rows = new ArrayList<>(3);
        rows.add(letterRow(shifted ? ROW1_SHIFT : ROW1_BASE,
                shifted ? HINTS1_SHIFT : HINTS1_BASE, false));
        rows.add(letterRow(shifted ? ROW2_SHIFT : ROW2_BASE,
                shifted ? HINTS2_SHIFT : HINTS2_BASE, false));
        rows.add(letterRow(shifted ? ROW3_SHIFT : ROW3_BASE,
                shifted ? HINTS3_SHIFT : HINTS3_BASE, true));
        return Collections.unmodifiableList(rows);
    }

    /** One letter row; the third also gets the Shift and Backspace keys, like the other scripts. */
    private static List<BlackBerryKeyboardView.KeyDef> letterRow(String[] mains, String hints,
                                                                boolean withShiftKeys) {
        List<BlackBerryKeyboardView.KeyDef> row = new ArrayList<>(mains.length + 2);
        if (withShiftKeys) {
            row.add(new BlackBerryKeyboardView.KeyDef("⬆", "",
                    Collections.<String>emptyList(), BlackBerryKeyboardView.KeyType.SHIFT, 1.35f));
        }
        for (int i = 0; i < mains.length; i++) {
            row.add(new BlackBerryKeyboardView.KeyDef(mains[i], String.valueOf(hints.charAt(i)),
                    altsFor(mains[i])));
        }
        if (withShiftKeys) {
            row.add(new BlackBerryKeyboardView.KeyDef("⌫", "",
                    Collections.<String>emptyList(), BlackBerryKeyboardView.KeyType.BACKSPACE, 1.35f));
        }
        return Collections.unmodifiableList(row);
    }

    private static List<String> altsFor(String main) {
        List<String> extra = ALT_EXTRA.get(main);
        return extra != null ? extra : Collections.<String>emptyList();
    }

    private static List<String> alts(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
