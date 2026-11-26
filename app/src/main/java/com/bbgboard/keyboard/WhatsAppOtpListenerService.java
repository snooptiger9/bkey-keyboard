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
            appendText(text, extras.getCharSequence(Notification.EXTRA_TITLE));
            appendText(text, extras.getCharSequence(Notification.EXTRA_TEXT));
            appendText(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            appendText(text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            appendText(text, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (CharSequence line : lines) appendText(text, line);
            }
            // Messaging-style notifications keep the message text here.
            android.os.Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null) {
                for (android.os.Parcelable m : messages) {
                    if (m instanceof Bundle) appendText(text, ((Bundle) m).getCharSequence("text"));
                }
            }
            appendText(text, n.tickerText);

            String code = OtpDetector.extract(text.toString());
            if (code != null) {
                prefs.setPendingWhatsAppOtp(code);
                BlackBerryImeService.notifyWhatsAppOtpDetected();
            }
        } catch (Exception e) {
            AppLogger.log("WhatsAppOtpListenerService", "onNotificationPosted()", e);
        }
    }

    private static void appendText(StringBuilder sb, CharSequence cs) {
        if (cs != null && cs.length() > 0) sb.append(cs).append('\n');
    }
}
