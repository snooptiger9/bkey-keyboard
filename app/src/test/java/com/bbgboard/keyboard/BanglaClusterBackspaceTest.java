package com.bbgboard.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BanglaClusterBackspaceTest {

    @Test
    public void nullAndEmptyNeedNoDeletion() {
        assertEquals(0, BanglaClusterBackspace.deleteLength(null));
        assertEquals(0, BanglaClusterBackspace.deleteLength(""));
    }

    @Test
    public void consonantAndVowelSignGoTogether() {
        // ি (U+09BF) is a trailing sign, so rule 2 also removes ম.
        assertEquals(2, BanglaClusterBackspace.deleteLength("আমি"));
    }

    @Test
    public void deletesTwoConsonantConjunct() {
        // জ + ্ + ঞ.
        assertEquals(3, BanglaClusterBackspace.deleteLength("জ্ঞ"));
        // ক + ্ + ক.
        assertEquals(3, BanglaClusterBackspace.deleteLength("ক্ক"));
    }

    @Test
    public void deletesRaPhalaWithVowelSign() {
        // ি + র + ্ + ক: rule 1, then rule 2, then rule 3.
        assertEquals(4, BanglaClusterBackspace.deleteLength("ক্রি"));
    }

    @Test
    public void deletesBaseConsonantWithAaSign() {
        // া is a dependent sign (U+09BE), so থ goes with it under rule 2.
        assertEquals(2, BanglaClusterBackspace.deleteLength("কথা"));
    }

    @Test
    public void deletesDanglingHasantaAndItsBase() {
        // A hasanta at the cursor still belongs to the consonant before it.
        assertEquals(2, BanglaClusterBackspace.deleteLength("ক্"));
    }

    @Test
    public void deletesThreeConsonantConjunctAsOneUnit() {
        // ক + ্ + ক + ্ + ক.
        assertEquals(5, BanglaClusterBackspace.deleteLength("ক্ক্ক"));
    }

    @Test
    public void nonBengaliEndingUsesCodePointLength() {
        assertEquals(1, BanglaClusterBackspace.deleteLength("hi"));
        assertEquals(2, BanglaClusterBackspace.deleteLength("😀"));
    }

    @Test
    public void neverCrossesSpace() {
        // Last char is ক; the space before it stops the walk.
        assertEquals(1, BanglaClusterBackspace.deleteLength("আমি ক"));
    }
}
