package com.bbgboard.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class PhoneticCorrectionsTest {

    @Test
    public void loadParsesPairsAndCorrectHandlesEdgeCases() throws Exception {
        String data = "ekta=ekoTa\nok=Oke\na=b=c\n\nnoequals\ntrailing\r\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            PhoneticCorrections.load(in);
        }

        assertEquals("ekoTa", PhoneticCorrections.correct("ekta"));
        assertEquals("Oke", PhoneticCorrections.correct("ok"));
        assertEquals("b=c", PhoneticCorrections.correct("a"));

        String unknown = "unknown";
        assertSame(unknown, PhoneticCorrections.correct(unknown));
        assertNull(PhoneticCorrections.correct(null));
    }

    @Test
    public void loadReplacesPreviousTable() throws Exception {
        try (ByteArrayInputStream first = new ByteArrayInputStream("ekta=ekoTa\n".getBytes(StandardCharsets.UTF_8))) {
            PhoneticCorrections.load(first);
        }
        try (ByteArrayInputStream second = new ByteArrayInputStream("other=value\n".getBytes(StandardCharsets.UTF_8))) {
            PhoneticCorrections.load(second);
        }

        String ekta = "ekta";
        assertSame(ekta, PhoneticCorrections.correct(ekta));
        assertEquals("value", PhoneticCorrections.correct("other"));
    }

    @Test
    public void loadsRealAssetAndCorrectsKnownPairs() throws Exception {
        try (FileInputStream in = new FileInputStream("src/main/assets/bn_phonetic_fixes.txt")) {
            PhoneticCorrections.load(in);
        }

        assertEquals("ekoTa", PhoneticCorrections.correct("ekta"));
        assertEquals("Oke", PhoneticCorrections.correct("ok"));
        assertEquals("এসএসসি", PhoneticCorrections.correct("ssc"));

        String absent = "zzzz-not-in-asset";
        assertSame(absent, PhoneticCorrections.correct(absent));
    }
}
