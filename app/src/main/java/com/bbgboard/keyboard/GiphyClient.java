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

final class GiphyClient {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-gif-fetch");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String BASE = "https://api.giphy.com/v1/gifs/";

    static final class GifResult {
        final String previewUrl;
        final String fullUrl;
        GifResult(String previewUrl, String fullUrl) {
            this.previewUrl = previewUrl;
            this.fullUrl = fullUrl;
        }
    }

    interface Callback {
        void onResult(List<GifResult> results, String error);
    }

    static void trending(String apiKey, Callback cb) {
        fetch(BASE + "trending?api_key=" + enc(apiKey) + "&limit=60&rating=pg-13", cb);
    }

    static void search(String apiKey, String query, Callback cb) {
        fetch(BASE + "search?api_key=" + enc(apiKey) + "&q=" + enc(query) + "&limit=60&rating=pg-13", cb);
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
            JSONArray data = root.optJSONArray("data");
            if (data == null) return list;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject images = item.optJSONObject("images");
                if (images == null) continue;
                JSONObject fixedWidth = images.optJSONObject("fixed_width");
                JSONObject original = images.optJSONObject("original");
                String previewUrl = fixedWidth != null ? fixedWidth.optString("url", null) : null;
                String fullUrl = original != null ? original.optString("url", null) : previewUrl;
                if (previewUrl != null && fullUrl != null) {
                    list.add(new GifResult(previewUrl, fullUrl));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
