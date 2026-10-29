package com.bbgboard.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/**
 * الكتابة بالصوت: يغلّف SpeechRecognizer حتى تبقى منطقته خارج خدمة الكيبورد.
 * كل الاستدعاءات على الخيط الرئيسي. النتيجة الجزئية تُعرض نصاً مؤقتاً، والنهائية تُدخَل في الحقل.
 */
final class VoiceInputController {

    interface Callback {
        /** نص جزئي أثناء الكلام (يُعرض كنص مؤقت). */
        void onPartial(String text);
        /** النص النهائي عند انتهاء الكلام. */
        void onResult(String text);
        /** تغيّرت حالة الاستماع (لتمييز زر المايك). */
        void onListeningChanged(boolean listening);
        /** رسالة خطأ للمستخدم (null = لا شيء يُعرض، مثل الصمت). */
        void onError(String message);
    }

    private final Context context;
    private final Callback callback;
    private SpeechRecognizer recognizer;
    private boolean listening = false;

    VoiceInputController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isAvailable(Context c) {
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    boolean isListening() { return listening; }

    /** يبدأ الاستماع، أو يوقفه (فتصل النتيجة النهائية) لو كان يستمع. */
    void toggle(boolean arabic) {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
        } else {
            start(arabic);
        }
    }

    /** يلغي الاستماع فوراً بدون إدخال أي نص (مثلاً عند اختفاء الكيبورد). */
    void cancel() {
        if (recognizer != null) recognizer.cancel();
        setListening(false);
    }

    void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        listening = false;
    }

    private void start(boolean arabic) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(listener);
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, arabic ? "ar-SA" : "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        setListening(true);
        recognizer.startListening(intent);
    }

    private void setListening(boolean value) {
        if (listening == value) return;
        listening = value;
        callback.onListeningChanged(value);
    }

    private static String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override
        public void onPartialResults(Bundle partialResults) {
            String text = firstResult(partialResults);
            if (text != null && !text.isEmpty()) callback.onPartial(text);
        }

        @Override
        public void onResults(Bundle results) {
            setListening(false);
            String text = firstResult(results);
            if (text != null && !text.isEmpty()) callback.onResult(text);
        }

        @Override
        public void onError(int error) {
            setListening(false);
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                case SpeechRecognizer.ERROR_CLIENT:
                    callback.onError(null); // صمت أو إلغاء: لا نزعج المستخدم
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    callback.onError("إذن الميكروفون مطلوب");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    callback.onError("لا يوجد اتصال بالإنترنت للكتابة بالصوت");
                    break;
                default:
                    callback.onError("تعذّرت الكتابة بالصوت");
            }
        }
    };
}
