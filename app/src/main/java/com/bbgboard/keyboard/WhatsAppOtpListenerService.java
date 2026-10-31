package com.bbgboard.keyboard;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * يقرأ إشعارات واتساب فقط (يتجاهل كل تطبيق آخر فوراً) للبحث عن رمز تحقق بداخلها، ويحفظه مؤقتاً
 * (5 دقائق) ليُعرض كاقتراح بالكيبورد بنفس طريقة رمز SMS تماماً. يحتاج صلاحية "الوصول للإشعارات"
 * يفعّلها المستخدم يدوياً من إعدادات أندرويد — صلاحية حساسة نطلبها فقط لهذا الاستخدام المحدد، ولا
 * نرسل أي بيانات لأي سيرفر (كل المعالجة محلية بالجهاز).
 */
public class WhatsAppOtpListenerService extends NotificationListenerService {

    private static final String[] WHATSAPP_PACKAGES = {"com.whatsapp", "com.whatsapp.w4b"};

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;
        boolean isWhatsApp = false;
        for (String pkg : WHATSAPP_PACKAGES) {
            if (pkg.equals(sbn.getPackageName())) {
                isWhatsApp = true;
                break;
            }
        }
        if (!isWhatsApp) return;

        KeyboardPreferences prefs = new KeyboardPreferences(this);
        if (!prefs.isWhatsAppOtpSuggestEnabled()) return;

        try {
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) return;
            Bundle extras = n.extras;
            StringBuilder text = new StringBuilder();
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body = extras.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (title != null) text.append(title).append(' ');
            if (body != null) text.append(body).append(' ');
            if (bigText != null) text.append(bigText);

            String code = OtpDetector.extract(text.toString());
            if (code != null) {
                prefs.setPendingWhatsAppOtp(code);
                BlackBerryImeService.notifyWhatsAppOtpDetected();
            }
        } catch (Exception ignored) {
        }
    }
}
