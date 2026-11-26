package com.bbgboard.keyboard;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class WhatsAppOtpListenerService extends NotificationListenerService {

    private static final String[] WHATSAPP_PACKAGES = {"com.whatsapp", "com.whatsapp.w4b"};

    private String defaultSmsPackage() {
        try {
            return android.provider.Telephony.Sms.getDefaultSmsPackage(this);
        } catch (Exception e) {
            return null;
        }
    }

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
        // The default messages app too: its notifications carry the SMS text, so the code
        // comes from the notification instead of an SMS permission.
        boolean isMessages = !isWhatsApp && sbn.getPackageName().equals(defaultSmsPackage());
        if (!isWhatsApp && !isMessages) return;

        KeyboardPreferences prefs = new KeyboardPreferences(this);
        if (!prefs.isWhatsAppOtpSuggestEnabled() || prefs.isIncognitoMode()) return;

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
        } catch (Exception e) {
            AppLogger.log("WhatsAppOtpListenerService", "onNotificationPosted()", e);
        }
    }
}
