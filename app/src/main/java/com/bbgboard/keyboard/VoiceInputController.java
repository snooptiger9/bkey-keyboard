package com.bbgboard.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

final class VoiceInputController {

    interface Callback {

        void onPartial(String text);

        void onResult(String text);

        void onListeningChanged(boolean listening);

        void onError(String message);
    }

    private final Context context;
    private final Callback callback;
    private SpeechRecognizer recognizer;
    private boolean listening = false;
    // Whether the current `recognizer` is the on-device (no-network) engine.
    // Needed so onError can tell apart "the offline model just doesn't cover
    // this language" (fall back to the regular online recognizer once) from
    // an online recognizer that genuinely has no network to work with.
    private boolean usingOnDevice = false;
    private String pendingLanguageTag = "en-US";

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

    void toggle(String languageTag) {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
        } else {
            start(languageTag);
        }
    }

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

    private void start(String languageTag) {
        pendingLanguageTag = languageTag;
        if (recognizer == null) {
            // Prefer the on-device engine when the device actually has one --
            // it never touches the network (and needs no INTERNET permission
            // from us), unlike the regular recognizer which normally streams
            // audio to a cloud service. Falls back to the regular recognizer
            // below (and in onError, if the on-device model turns out not to
            // cover the requested language).
            usingOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
            recognizer = usingOnDevice
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    : SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(listener);
        }
        startListening(languageTag);
    }

    private void startListening(String languageTag) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        // Hints the system to prefer an offline/on-device model if one exists,
        // instead of defaulting straight to a network round trip.
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        setListening(true);
        recognizer.startListening(intent);
    }

    private void fallBackToOnlineRecognizer() {
        usingOnDevice = false;
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(listener);
        startListening(pendingLanguageTag);
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
            if (usingOnDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                    || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                // This device's offline model doesn't cover the requested language --
                // retry once with the regular (online) recognizer instead of failing.
                fallBackToOnlineRecognizer();
                return;
            }
            setListening(false);
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                case SpeechRecognizer.ERROR_CLIENT:
                    callback.onError(null);
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    callback.onError("إذن الميكروفون مطلوب");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    callback.onError("لا يوجد اتصال بالإنترنت للكتابة بالصوت");
                    break;
                default:
                    // Logged (not just shown generically) so the exact SpeechRecognizer
                    // error code -- and whether this was the on-device or online engine --
                    // shows up in the in-app error log instead of being a dead end.
                    AppLogger.log("VoiceInputController", "onError() code=" + error
                            + " onDevice=" + usingOnDevice, null);
                    callback.onError("تعذّرت الكتابة بالصوت");
            }
        }
    };
}
