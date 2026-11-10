package com.bbgboard.keyboard;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** عميل بسيط لـ Tenor API v2 (بحث وترند صور GIF). كل الطلبات على خيط خلفي والنتيجة ترجع بخيط الواجهة. */
final class TenorClient {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String BASE = "https://tenor.googleapis.com/v2/";

    static final class GifResult {
        final String previewUrl; // نسخة صغيرة لعرضها بالشبكة (tinygif)
        final String fullUrl;    // النسخة الكاملة اللي ترسل بالمحادثة
        GifResult(String previewUrl, String fullUrl) {
            this.previewUrl = previewUrl;
            this.fullUrl = fullUrl;
        }
    }

    interface Callback {
        void onResult(List<GifResult> results, String error);
    }

    static void trending(String apiKey, Callback cb) {
        fetch(BASE + "featured?key=" + enc(apiKey) + "&limit=24&media_filter=tinygif,gif", cb);
    }

    static void search(String apiKey, String query, Callback cb) {
        fetch(BASE + "search?key=" + enc(apiKey) + "&q=" + enc(query) + "&limit=24&media_filter=tinygif,gif", cb);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void fetch(String urlStr, Callback cb) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                if (code < 200 || code >= 300) {
                    postError(cb, "HTTP " + code);
                    return;
                }
                List<GifResult> out = parse(sb.toString());
                MAIN.post(() -> cb.onResult(out, null));
            } catch (Exception e) {
                postError(cb, e.getMessage() == null ? "network error" : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void postError(Callback cb, String msg) {
        MAIN.post(() -> cb.onResult(new ArrayList<>(), msg));
    }

    private static List<GifResult> parse(String json) {
        List<GifResult> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return list;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                JSONObject formats = item.optJSONObject("media_formats");
                if (formats == null) continue;
                JSONObject tiny = formats.optJSONObject("tinygif");
                JSONObject full = formats.optJSONObject("gif");
                String previewUrl = tiny != null ? tiny.optString("url", null) : null;
                String fullUrl = full != null ? full.optString("url", null) : previewUrl;
                if (previewUrl != null && fullUrl != null) {
                    list.add(new GifResult(previewUrl, fullUrl));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
