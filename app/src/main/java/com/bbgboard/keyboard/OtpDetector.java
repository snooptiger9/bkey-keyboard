package com.bbgboard.keyboard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OtpDetector {
    private OtpDetector() {}

    private static final String[] CODE_HINTS = {
            "رمز", "كود", "رمزك", "كودك", "code", "otp", "pin", "verification",
            "تحقق", "التحقق", "password", "كلمة المرور المؤقتة", "passcode", "رمز التحقق",
            "سري", "السري", "الرقم السري"
    };

    private static final String[] NOT_CODE_HINTS = {
            "بطاقة", "card", "تنتهي", "ending", "مبلغ", "amount", "ريال", "sar", "rs.",
            "رصيد", "balance", "حساب", "account", "هاتف", "phone", "%", "دقيقة", "دقائق",
            "ثانية", "ثواني", "minute", "second", "usd", "$", "aed", "kwd", "bhd", "qar"
    };

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+(?:[ \\-]\\d+)*");

    static String extract(String body) {
        if (body == null || body.isEmpty()) return null;
        String lower = body.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, CODE_HINTS)) return null;

        Matcher m = DIGIT_RUN.matcher(body);
        while (m.find()) {
            String digitsOnly = m.group().replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 4 || digitsOnly.length() > 8) continue;

            int start = Math.max(0, m.start() - 80);
            int end = Math.min(body.length(), m.end() + 80);
            String window = body.substring(start, end).toLowerCase(Locale.ROOT);

            int narrowStart = Math.max(0, m.start() - 15);
            int narrowEnd = Math.min(body.length(), m.end() + 15);
            String narrowWindow = body.substring(narrowStart, narrowEnd).toLowerCase(Locale.ROOT);

            if (containsAny(narrowWindow, NOT_CODE_HINTS)) continue;
            if (containsAny(window, CODE_HINTS)) return digitsOnly;
        }
        return null;
    }

    private static boolean containsAny(String haystackLower, String[] needles) {
        for (String n : needles) {
            if (haystackLower.contains(n)) return true;
        }
        return false;
    }
}
