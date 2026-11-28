package com.bbgboard.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * National (Jatiya) Bangla keyboard, BDS 1738:2018.
 *
 * <p>Derived from Ekushey's {@code bn-jatiya.mim} (CC-BY-4.0,
 * <a href="https://github.com/ekushey-org/bangla-bsti-m17n">bangla-bsti-m17n</a>), keeping the
 * standard's base and Shift columns and the AltGr conjuncts, which become long-press options
 * here. Three letter rows only: the symbols page carries the digits, and the view appends its
 * own bottom row. Each key's {@code sub} is the Latin key it sits on, so people who know the
 * standard layout can find the letters on a QWERTY keyboard.
 */
public final class JatiyoLayout {

    /** U+200C ZERO WIDTH NON-JOINER: keeps a hasanta visible or blocks a conjunct. */
    private static final String ZWNJ = "\u200C";
    /** U+200D ZERO WIDTH JOINER: forces an explicit conjunct (ya-phala, ra-phala). */
    private static final String ZWJ = "\u200D";

    // Top row (q..p).
    private static final String[] ROW1_BASE = {"ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "\u09DC"};
    private static final String[] ROW1_SHIFT = {"ং", "\u09DF", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "\u09DD"};
    private static final String HINTS1_BASE = "qwertyuiop";
    private static final String HINTS1_SHIFT = "QWERTYUIOP";

    // Home row (a..l). g is the hasanta, exactly as in the .mim.
    private static final String[] ROW2_BASE = {"ৃ", "ু", "ি", "া", "্", "ব", "ক", "ত", "দ"};
    private static final String[] ROW2_SHIFT = {"র্", "ূ", "ী", "অ", "।", "ভ", "খ", "থ", "ধ"};
    private static final String HINTS2_BASE = "asdfghjkl";
    private static final String HINTS2_SHIFT = "ASDFGHJKL";

    // Bottom row (z..m), in the row that also holds Shift and Backspace.
    private static final String[] ROW3_BASE = {"্র", "ো", "ে", "র", "ন", "স", "ম"};
    private static final String[] ROW3_SHIFT = {"্য", "ৌ", "ৈ", "ল", "ণ", "ষ", "শ"};
    private static final String HINTS3_BASE = "zxcvbnm";
    private static final String HINTS3_SHIFT = "ZXCVBNM";

    /** Long-press options taken from the .mim AltGr level, keyed by the unshifted letter. */
    private static final Map<String, List<String>> ALT_EXTRA;

    static {
        Map<String, List<String>> extra = new HashMap<>();
        extra.put("ঙ", alts("ঙ্ক"));
        extra.put("চ", alts("ঞ্চ"));
        extra.put("জ", alts("ঞ্জ"));
        extra.put("হ", alts("জ্ঞ", "হ্ম"));
        extra.put("গ", alts("ঙ্গ"));
        extra.put("ক", alts("ক্ষ"));
        extra.put("র", alts("\u09AC\u09BC\u09CD\u09AF")); // .mim G-v: BA + NUKTA + HASANTA + YA
        extra.put("স", alts("ষ্ণ"));
        extra.put("ম", alts("হ্ম"));
        extra.put("্", alts(ZWNJ, ZWJ));
        ALT_EXTRA = Collections.unmodifiableMap(extra);
    }

    private static final List<List<BlackBerryKeyboardView.KeyDef>> BASE_ROWS;
    private static final List<List<BlackBerryKeyboardView.KeyDef>> SHIFT_ROWS;

    static {
        BASE_ROWS = buildRows(false);
        SHIFT_ROWS = buildRows(true);
    }

    private JatiyoLayout() {
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
