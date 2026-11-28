package com.bbgboard.keyboard;

import android.view.inputmethod.InputConnection;

import java.util.function.Supplier;

/**
 * Live Latin-to-Bangla typing for Bangla mode.
 *
 * <p>Every consumed key goes into a roman buffer whose transliteration is shown as the
 * composing text, so the output updates on each key and backspace edits the roman word
 * rather than the Bangla letters. A word boundary ends the word with {@link #finish()};
 * {@link #reset()} drops the buffer without touching the text on screen.
 */
public final class BanglaComposer {

    /** The punctuation keys the phonetic engine reads. Any other non-letter ends a word. */
    private static final String PUNCTUATION_KEYS = ".,$^:%/\\`";

    private final Supplier<InputConnection> inputConnection;
    private final StringBuilder roman = new StringBuilder();
    private boolean composing = false;
    /** Fixed Bengali mode: keys are Bangla letters and the buffer holds Bangla text. */
    private boolean fixed = false;

    public BanglaComposer(Supplier<InputConnection> ic) {
        this.inputConnection = ic;
    }

    /** Switches between phonetic (false) and a fixed Bengali layout (true). */
    public void setFixedLayout(boolean on) {
        if (this.fixed != on) {
            finish(); // commit the buffer with the mode that built it
            this.fixed = on;
        }
    }

    /**
     * Appends one roman key and shows its Bangla. Returns false, and changes nothing, for
     * anything that is not a single ASCII letter, digit or phonetic punctuation key. In
     * fixed-layout mode, a single Bengali character is appended as-is instead.
     */
    public boolean handleCharacter(String text) {
        if (text == null || text.length() != 1) return false;
        char c = text.charAt(0);
        if (fixed) {
            // Fixed layout: Bangla keys (and explicit joiners) are already the final output.
            if (!isBanglaLetter(c) && c != '\u200C' && c != '\u200D') return false;
            InputConnection ic = connection();
            if (ic == null) return false;
            roman.append(c);
            ic.setComposingText(roman.toString(), 1);
            composing = true;
            return true;
        }
        if (!isPhoneticKey(c)) return false;
        InputConnection ic = connection();
        if (ic == null) return false;
        roman.append(text.charAt(0));
        render(ic);
        return true;
    }

    /** Removes the last roman key and re-renders. False when no word is in progress. */
    public boolean handleBackspace() {
        if (roman.length() == 0) return false;
        InputConnection ic = connection();
        if (ic == null) return false;
        roman.deleteCharAt(roman.length() - 1);
        if (roman.length() == 0) {
            ic.setComposingText("", 1);
            ic.finishComposingText();
            composing = false;
        } else if (fixed) {
            ic.setComposingText(roman.toString(), 1);
        } else {
            render(ic);
        }
        return true;
    }

    /** Commits the word on screen and forgets the buffer. */
    public void finish() {
        if (roman.length() == 0) {
            composing = false;
            return;
        }
        InputConnection ic = connection();
        roman.setLength(0);
        composing = false;
        if (ic != null) ic.finishComposingText();
    }

    /** Forgets the buffer without touching the text on screen. */
    public void reset() {
        roman.setLength(0);
        composing = false;
    }

    /** True while a word is being built, i.e. while backspace should edit the roman buffer. */
    public boolean isComposing() {
        return composing;
    }

    private void render(InputConnection ic) {
        String bangla = BanglaPhoneticEngine.transliterate(PhoneticCorrections.correct(roman.toString()));
        if (bangla == null || bangla.isEmpty()) {
            // The keys so far write nothing yet ("`"): keep the screen empty but the buffer
            // alive, so the next key can still finish the word.
            ic.setComposingText("", 1);
            ic.finishComposingText();
        } else {
            ic.setComposingText(bangla, 1);
        }
        composing = true;
    }

    private InputConnection connection() {
        return inputConnection != null ? inputConnection.get() : null;
    }

    private static boolean isPhoneticKey(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        return PUNCTUATION_KEYS.indexOf(c) >= 0;
    }

    /** A single Bengali character, U+0980..U+09FF (letters, signs and Bengali digits). */
    private static boolean isBanglaLetter(char c) {
        return c >= 0x0980 && c <= 0x09FF;
    }
}
