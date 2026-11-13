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

/**
 * Talks to KLIPY's GIF API (klipy.com) — a single app-wide key baked in below, so no user
 * ever has to sign up for or paste their own key. KLIPY's production key has no call-rate
 * limit, unlike GIPHY's which requires a paid plan for real (non-beta) usage.
 */
final class KlipyClient {

    private KlipyClient() {}

    // App-wide key, used for every user — intentionally baked in rather than entered by the
    // person using the keyboard. Swap this for the approved "production" key once granted;
    // until then this is the free test key (100 calls/hour across the whole app).
    private static final String API_KEY = "GITFCwJBcLn2ChyGwjAVsKhxuqMirBF3GdQonG07WfT7WgdWs13kALGUPl9o7r2x";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-gif-fetch");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String BASE = "https://api.klipy.com/api/v1/" + API_KEY + "/gifs/";

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

    static void trending(Callback cb) {
        fetch(BASE + "trending?per_page=60", cb);
    }

    static void search(String query, Callback cb) {
        fetch(BASE + "search?per_page=60&q=" + enc(query), cb);
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

    // KLIPY's response shape: {"result":true,"data":{"data":[{"id":..,"file":{
    //   "xs":{"gif":{"url":..,"width":..,"height":..,"size":..}, "webp":{...}, "jpg":{...}, "mp4":{...}, "webm":{...}},
    //   "sm":{...}, "md":{...}, "hd":{...}
    // }}, ...], "current_page":1, ...}}
    // We only ever read the ".gif" entries (sendGif()/GifDownloadCache need a real .gif file
    // to hand to other apps via content://), never webp/mp4/webm.
    private static List<GifResult> parse(String json) {
        List<GifResult> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("result", false)) return list;
            JSONObject dataWrap = root.optJSONObject("data");
            JSONArray data = dataWrap != null ? dataWrap.optJSONArray("data") : null;
            if (data == null) return list;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                JSONObject file = item.optJSONObject("file");
                if (file == null) continue;
                String previewUrl = gifUrl(file, "sm");
                String fullUrl = gifUrl(file, "md");
                if (fullUrl == null) fullUrl = gifUrl(file, "hd");
                if (previewUrl == null) previewUrl = fullUrl;
                if (fullUrl == null) fullUrl = previewUrl;
                if (previewUrl != null && fullUrl != null) {
                    list.add(new GifResult(previewUrl, fullUrl));
                }
            }
        } catch (Exception e) {
            AppLogger.log("KlipyClient", "parse()", e);
        }
        return list;
    }

    private static String gifUrl(JSONObject file, String size) {
        JSONObject tier = file.optJSONObject(size);
        if (tier == null) return null;
        JSONObject gif = tier.optJSONObject("gif");
        return gif != null ? gif.optString("url", null) : null;
    }
}
