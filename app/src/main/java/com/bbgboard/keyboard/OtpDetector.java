package com.bbgboard.keyboard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OtpDetector {
    private OtpDetector() {}

    private static final String[] CODE_HINTS = {
            "رمز", "كود", "رمزك", "كودك", "code", "otp", "pin", "verification",
            "تحقق", "التحقق", "password", "كلمة المرور المؤقتة", "passcode", "رمز التحقق",
            "سري", "السري", "الرقم السري",
            // Spanish
            "código", "codigo", "verificación", "verificacion", "contraseña", "clave",
            // French
            "vérification", "mot de passe", "code secret",
            // Hindi
            "कोड", "ओटीपी", "सत्यापन", "वेरिफिकेशन", "पासवर्ड",
            // Urdu
            "کوڈ", "تصدیق", "تصدیقی", "پاس ورڈ", "خفیہ",
            // Indonesian
            "kode", "verifikasi", "kata sandi",
            // Bangla
            "কোড", "কোডটি", "ওটিপি", "ও.টি.পি", "ভেরিফিকেশন", "সত্যায়ন",
            "পিন", "পাসওয়ার্ড", "নিরাপত্তা কোড", "এক্সেস কোড", "কনফার্মেশন"
    };

    private static final String[] NOT_CODE_HINTS = {
            "بطاقة", "card", "تنتهي", "ending", "مبلغ", "amount", "ريال", "sar", "rs.",
            "رصيد", "balance", "حساب", "account", "هاتف", "phone", "%", "دقيقة", "دقائق",
            "ثانية", "ثواني", "minute", "second", "usd", "$", "aed", "kwd", "bhd", "qar",
            // Spanish
            "tarjeta", "termina en", "importe", "monto", "saldo", "cuenta", "teléfono",
            "telefono", "minuto", "segundo", "eur", "mxn", "€",
            // French
            "carte", "se termine", "montant", "solde", "compte", "téléphone",
            "telephone", "seconde", "gbp", "£",
            // Hindi
            "कार्ड", "राशि", "रुपये", "₹", "बैलेंस", "शेष", "खाता", "फोन", "फ़ोन",
            "मिनट", "सेकंड", "inr",
            // Urdu
            "کارڈ", "رقم", "روپے", "بیلنس", "اکاؤنٹ", "فون", "منٹ", "سیکنڈ", "pkr",
            // Indonesian
            "kartu", "berakhiran", "jumlah", "rekening", "telepon", "menit", "detik", "idr", "rp ", "rp.",
            // Bangla
            "কার্ড", "টাকা", "ব্যালেন্স", "হিসাব", "ফোন", "মিনিট", "সেকেন্ড", "৳"
    };

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+(?:[ \\-]\\d+)*");

    static String extract(String body) {
        if (body == null || body.isEmpty()) return null;
        // Bengali digits are BMP characters; normalising them to ASCII keeps indices aligned.
        String normalized = body;
        for (int d = 0; d < 10; d++) {
            normalized = normalized.replace((char) (0x09E6 + d), (char) ('0' + d));
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, CODE_HINTS)) return null;

        Matcher m = DIGIT_RUN.matcher(normalized);
        while (m.find()) {
            String digitsOnly = m.group().replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 4 || digitsOnly.length() > 8) continue;

            int start = Math.max(0, m.start() - 80);
            int end = Math.min(body.length(), m.end() + 80);
            String window = normalized.substring(start, end).toLowerCase(Locale.ROOT);

            int narrowStart = Math.max(0, m.start() - 15);
            int narrowEnd = Math.min(body.length(), m.end() + 15);
            String narrowWindow = normalized.substring(narrowStart, narrowEnd).toLowerCase(Locale.ROOT);

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
