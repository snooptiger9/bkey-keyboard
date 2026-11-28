package com.bbgboard.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JatiyoRulesTest {

    @Test
    public void pushAppendsNormally() {
        assertEquals("ক", JatiyoRules.push("", "ক"));
        assertEquals("কি", JatiyoRules.push("ক", "ি"));
        assertEquals("ক্", JatiyoRules.push("ক", "্"));
        assertEquals("ক্র", JatiyoRules.push("ক্", "র"));
    }

    @Test
    public void pushReplacesHasantaWithIndependentVowel() {
        assertEquals("কআ", JatiyoRules.push("ক্", "া"));
        assertEquals("কই", JatiyoRules.push("ক্", "ি"));
        assertEquals("কঈ", JatiyoRules.push("ক্", "ী"));
        assertEquals("কউ", JatiyoRules.push("ক্", "ু"));
        assertEquals("কঊ", JatiyoRules.push("ক্", "ূ"));
        assertEquals("কঋ", JatiyoRules.push("ক্", "ৃ"));
        assertEquals("কএ", JatiyoRules.push("ক্", "ে"));
        assertEquals("কঐ", JatiyoRules.push("ক্", "ৈ"));
        assertEquals("কও", JatiyoRules.push("ক্", "ো"));
        assertEquals("কঔ", JatiyoRules.push("ক্", "ৌ"));
        assertEquals("কঔ", JatiyoRules.push("ক্", "ৗ"));
    }

    @Test
    public void pushHandlesNullAndEmpty() {
        assertEquals("", JatiyoRules.push(null, null));
        assertEquals("", JatiyoRules.push("", null));
        assertEquals("ক", JatiyoRules.push(null, "ক"));
        assertEquals("ক", JatiyoRules.push("ক", null));
        assertEquals("ক", JatiyoRules.push("ক", ""));
    }

    @Test
    public void normalizeReordersPholaAfterVowelSign() {
        assertEquals("ক্রি", JatiyoRules.normalize("কি্র"));
        assertEquals("ক্যে", JatiyoRules.normalize("কে\u09CDয"));
    }

    @Test
    public void normalizeLeavesUnchangedWhenNoReorderNeeded() {
        assertEquals("ক্রি", JatiyoRules.normalize("ক্রি"));
    }

    @Test
    public void normalizeReordersOnlyWithinWord() {
        assertEquals("আমি ক্রি", JatiyoRules.normalize("আমি কি্র"));
    }

    @Test
    public void normalizeHandlesNull() {
        assertEquals("", JatiyoRules.normalize(null));
    }
}
