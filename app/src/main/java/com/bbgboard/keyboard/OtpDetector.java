package com.bbgboard.keyboard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * يحاول يلقط رمز التحقق (OTP) من نص رسالة SMS بذكاء، ويتجاهل أرقام أخرى موجودة بنفس الرسالة
 * مثل آخر أربع أرقام من بطاقة، أو مبلغ، أو رقم حساب/هاتف، أو تاريخ.
 *
 * القاعدة: لازم يكون في الرسالة كلمة تدل على "رمز تحقق" أصلاً، ولازم يكون الرقم المرشح قريباً
 * من تلك الكلمة (ضمن نافذة أحرف قصيرة) وبعيداً عن كلمات تدل على أنه رقم آخر (بطاقة/مبلغ/حساب...).
 * لو ما قدر يحدد بثقة، يرجع null بدل ما يخمّن رقماً خاطئاً.
 */
final class OtpDetector {
    private OtpDetector() {}

    private static final String[] CODE_HINTS = {
            "رمز", "كود", "رمزك", "كودك", "code", "otp", "pin", "verification",
            "تحقق", "التحقق", "password", "كلمة المرور المؤقتة", "passcode", "رمز التحقق",
            "سري", "السري", "الرقم السري" // مثل رسائل "نفاذ": "الرقم السري المؤقت للدخول..."
    };

    private static final String[] NOT_CODE_HINTS = {
            "بطاقة", "card", "تنتهي", "ending", "مبلغ", "amount", "ريال", "sar", "rs.",
            "رصيد", "balance", "حساب", "account", "هاتف", "phone", "%", "دقيقة", "دقائق",
            "ثانية", "ثواني", "minute", "second", "usd", "$", "aed", "kwd", "bhd", "qar"
    };

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+(?:[ \\-]\\d+)*");

    /** يحاول استخراج رمز تحقق من نص الرسالة، أو يرجع null إذا ما وجد رقماً واضحاً وموثوقاً. */
    static String extract(String body) {
        if (body == null || body.isEmpty()) return null;
        String lower = body.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, CODE_HINTS)) return null;

        Matcher m = DIGIT_RUN.matcher(body);
        while (m.find()) {
            String digitsOnly = m.group().replaceAll("[^0-9]", "");
            if (digitsOnly.length() < 4 || digitsOnly.length() > 8) continue;

            // النافذة كانت 25 حرف بس رسائل رسمية زي "نفاذ" تحط كلمة الدلالة بأول الجملة والرقم
            // بآخرها (أكثر من 60 حرف بينهم)، فوسّعناها حتى تلقطها بدون ما تضيع الدقة (لسه نتحقق
            // من NOT_CODE_HINTS بنفس النافذة).
            int start = Math.max(0, m.start() - 80);
            int end = Math.min(body.length(), m.end() + 80);
            String window = body.substring(start, end).toLowerCase(Locale.ROOT);

            if (containsAny(window, NOT_CODE_HINTS)) continue;
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
