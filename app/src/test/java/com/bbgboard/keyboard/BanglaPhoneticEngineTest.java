package com.bbgboard.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Borno Phonetic vectors, grouped by what they exercise. Expected values are
 * fixed: a failing vector means the engine is wrong, not the expectation.
 */
public class BanglaPhoneticEngineTest {

    private static void check(String expected, String input) {
        assertEquals("input: " + input, expected, BanglaPhoneticEngine.transliterate(input));
    }

    @Test
    public void vowelsAndSigns() {
        check("আমি", "ami");
        check("কি", "ki");
        check("ঐ", "OI");
        check("কৌ", "kOU");
        check("ঈদ", "eed");
        check("ঋ", "rri");
        check("কলম", "kolom");
        check("চা", "ca");
        check("তোমাকে", "tOmake");
        check("ভালো", "bhalO");
    }

    @Test
    public void bornoKeys() {
        check("ছা", "cha");
        check("য", "z");
        check("ক্ষ", "kkh");
        check("জ্ঞান", "ggan");
        check("ঞ", "NG");
        check("ঙ", "Ng");
        check("ং", "ng");
        check("এক্স", "x");
    }

    @Test
    public void capitals() {
        check("ক", "K");
        check("খ", "KH");
        check("ট", "T");
        check("ত", "t");
        check("ষ", "Sh");
        check("শ", "S");
    }

    @Test
    public void marks() {
        check("কাঁ", "kaA");
        check("কাঁ", "kaxx");
        check("উৎ", "uX");
        check("উৎ", "uTH");
        check("উৎ", "ut``");
    }

    @Test
    public void conjuncts() {
        check("বন্ধু", "bondhu");
        check("কথা", "kotha");
        check("বাংলা", "bangla");
        check("স্কুল", "skul");
        check("ধন্যবাদ", "dhonyobad");
        check("রর", "rr");
    }

    @Test
    public void punctuationDigitsAndEscape() {
        check("।", ".");
        check("।।", "..");
        check("১", "1");
        check("facebook", "facebook\\\\");
        check("facebook-এর", "facebook\\\\-er");
        check("কি\\", "ki\\");
        check("ki\\", "ki\\\\\\");
        check("ক\\", "k\\");
    }
}
