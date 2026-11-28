package com.bbgboard.keyboard;

/**
 * Jatiyo (BDS 1738:2018) composing helpers, written for bkey from the behaviour of
 * Ekushey's {@code bn-jatiya.mim} (CC-BY-4.0, bangla-bsti-m17n). No m17n code copied.
 */
public final class JatiyoRules {
    public static final String HASANTA = "্";

    private JatiyoRules() {
    }

    public static String push(String current, String typedChar) {
        if (typedChar == null) {
            typedChar = "";
        }
        if (current == null || current.isEmpty()) {
            return typedChar;
        }
        if (typedChar.isEmpty()) {
            return current;
        }
        if (current.endsWith(HASANTA)) {
            String independent = independentVowel(typedChar);
            if (independent != null) {
                return current.substring(0, current.length() - HASANTA.length()) + independent;
            }
        }
        return current + typedChar;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        int len = text.length();
        if (len < 3) {
            return text;
        }
        StringBuilder out = null;
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (isVowelSign(c) && i + 2 < len
                    && text.charAt(i + 1) == HASANTA.charAt(0)
                    && isPhola(text.charAt(i + 2))) {
                if (out == null) {
                    out = new StringBuilder(len);
                    out.append(text, 0, i);
                }
                out.append(text.charAt(i + 1));
                out.append(text.charAt(i + 2));
                out.append(c);
                i += 3;
            } else {
                if (out != null) {
                    out.append(c);
                }
                i++;
            }
        }
        return out == null ? text : out.toString();
    }

    private static boolean isPhola(char c) {
        return c == 'র' || c == 'য';
    }

    private static boolean isVowelSign(char c) {
        return independentVowel(String.valueOf(c)) != null;
    }

    private static String independentVowel(String sign) {
        if (sign == null || sign.length() != 1) {
            return null;
        }
        switch (sign.charAt(0)) {
            case 'া':
                return "আ";
            case 'ি':
                return "ই";
            case 'ী':
                return "ঈ";
            case 'ু':
                return "উ";
            case 'ূ':
                return "ঊ";
            case 'ৃ':
                return "ঋ";
            case 'ে':
                return "এ";
            case 'ৈ':
                return "ঐ";
            case 'ো':
                return "ও";
            case 'ৌ':
                return "ঔ";
            case 'ৗ':
                return "ঔ";
            default:
                return null;
        }
    }
}
