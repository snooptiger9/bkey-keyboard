package com.bbgboard.keyboard;

/**
 * Backspace helper for Bangla text: returns how many UTF-16 chars make up the
 * orthographic cluster that ends at the cursor.
 *
 * <p>The previous single-code-point deletion leaves a dangling hasanta (U+09CD) when the
 * cursor is after a conjunct such as {@code জ্ঞ} or {@code ক্ক}. This class walks a
 * Bengali cluster backwards so the whole visual unit goes at once: trailing vowel
 * signs/modifiers, the base consonant, and every {@code hasanta + consonant} pair
 * before it.
 *
 * <p>Counts follow the frozen contract:
 * <ul>
 *   <li>{@code null}/empty: 0.</li>
 *   <li>Final char outside U+0980..U+09FF: its code-point length (1, or 2 for a
 *       surrogate pair).</li>
 *   <li>Final char inside the Bengali block: one cluster, capped at 16 chars.</li>
 * </ul>
 * Because vowel signs belong to the cluster, a consonant plus a sign counts as two
 * chars (for example {@code মি}), and a conjunct with a sign counts all of its chars
 * (for example {@code ক্রি} counts 4).
 */
public final class BanglaClusterBackspace {

    private static final int MAX_DELETE = 16;

    private static final char HASANTA = '\u09CD';
    private static final char NUKTA = '\u09BC';
    private static final char ZWNJ = '\u200C';
    private static final char ZWJ = '\u200D';

    private BanglaClusterBackspace() {
    }

    /**
     * Returns the number of UTF-16 chars to delete so that one complete Bengali
     * cluster (or one non-Bengali code point) disappears.
     *
     * @param textBeforeCursor text immediately before the cursor, may be {@code null}
     * @return 0 for {@code null}/empty, otherwise 1..16
     */
    public static int deleteLength(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return 0;
        }

        int index = textBeforeCursor.length() - 1;
        char last = textBeforeCursor.charAt(index);

        if (!isBengali(last)) {
            return codePointLengthAt(textBeforeCursor, index);
        }

        int deleted = 0;

        if (isMark(last)) {
            // 1. Trailing vowel sign, modifier, nukta, or joiner.
            while (index >= 0 && deleted < MAX_DELETE && isTrailingMark(textBeforeCursor.charAt(index))) {
                deleted++;
                index--;
            }

            // 2. The consonant (or independent vowel) the marks belong to.
            while (index >= 0 && deleted < MAX_DELETE && isConsonantAttachment(textBeforeCursor.charAt(index))) {
                deleted++;
                index--;
            }
            if (index < 0 || deleted >= MAX_DELETE || !isBaseLetter(textBeforeCursor.charAt(index))) {
                return deleted;
            }
            deleted++;
            index--;
        } else if (isBaseLetter(last)) {
            deleted = 1;
            index--;
        } else {
            // Bengali digit, currency sign, punctuation, etc.: just that char.
            return 1;
        }

        // 3. Walk the hasanta chain so a whole conjunct is removed together.
        while (deleted < MAX_DELETE) {
            // ZWJ/ZWNJ can sit between a consonant and the hasanta to its left.
            while (index >= 0 && deleted < MAX_DELETE && isJoiner(textBeforeCursor.charAt(index))) {
                deleted++;
                index--;
            }
            if (index < 0 || deleted >= MAX_DELETE || textBeforeCursor.charAt(index) != HASANTA) {
                break;
            }
            deleted++;
            index--;

            // A nukta or joiner may be attached to the consonant before the hasanta.
            while (index >= 0 && deleted < MAX_DELETE && isConsonantAttachment(textBeforeCursor.charAt(index))) {
                deleted++;
                index--;
            }
            if (index < 0 || deleted >= MAX_DELETE || !isBaseLetter(textBeforeCursor.charAt(index))) {
                break;
            }
            deleted++;
            index--;
        }

        return deleted;
    }

    private static int codePointLengthAt(CharSequence text, int index) {
        if (index > 0
                && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return 2;
        }
        return 1;
    }

    private static boolean isBengali(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    private static boolean isMark(char c) {
        return (c >= '\u09BE' && c <= '\u09CD') || c == '\u09D7' || c == '\u09BC';
    }

    private static boolean isJoiner(char c) {
        return c == ZWNJ || c == ZWJ;
    }

    private static boolean isTrailingMark(char c) {
        return isMark(c) || isJoiner(c);
    }

    private static boolean isConsonantAttachment(char c) {
        return c == NUKTA || isJoiner(c);
    }

    private static boolean isBaseLetter(char c) {
        // The contract's letter range plus the precomposed letters outside it.
        return (c >= '\u0985' && c <= '\u09B9')
                || c == '\u09CE'
                || c == '\u09DC'
                || c == '\u09DD'
                || c == '\u09DF';
    }
}
