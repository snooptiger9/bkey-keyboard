package com.bbgboard.keyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Exact-match phonetic corrections loaded from {@code assets/bn_phonetic_fixes.txt}.
 * Each line is {@code roman=corrected}; only the first {@code =} separates the pair.
 */
public final class PhoneticCorrections {
    private static volatile HashMap<String, String> corrections = new HashMap<>();

    /**
     * Replaces the current table with the pairs read from {@code in} as UTF-8.
     * The caller owns the stream; this method never closes it.
     */
    public static void load(InputStream in) throws IOException {
        HashMap<String, String> loaded = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            int length = line.length();
            if (length > 0 && line.charAt(length - 1) == '\r') {
                line = line.substring(0, length - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            loaded.put(line.substring(0, separator), line.substring(separator + 1));
        }
        corrections = loaded;
    }

    /**
     * Returns the correction for {@code roman}, or the same {@code roman} instance when the
     * table has no entry. Returns {@code null} for a {@code null} input.
     */
    public static String correct(String roman) {
        if (roman == null) {
            return null;
        }
        String corrected = corrections.get(roman);
        return corrected != null ? corrected : roman;
    }
}
