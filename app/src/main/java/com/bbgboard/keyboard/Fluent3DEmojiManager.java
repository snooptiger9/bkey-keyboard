package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * يدير نمط رسوم الرموز التعبيرية "Fluent 3D" (من مايكروسوفت، مرخّصة MIT، مفتوحة المصدر بالكامل):
 * - قائمة الترميز (manifest.json) مرفقة بالتطبيق نفسه، وهي خفيفة (نص فقط، بدون صور).
 * - الصور الفعلية تُنزَّل اختيارياً فقط لو فعّل المستخدم هذا النمط من الإعدادات، من مستودع
 *   مايكروسوفت الرسمي على GitHub، وتُخزَّن محلياً بذاكرة التطبيق الداخلية.
 * - أعلام الدول وبعض الرموز المركّبة غير متوفرة بهذا النمط أصلاً؛ ترجع تلقائياً للرمز النصي
 *   العادي (نظام التشغيل) تلقائياً دون أي كسر بالعرض.
 */
final class Fluent3DEmojiManager {
    private static final String BASE_URL = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/";
    private static final String MANIFEST_ASSET = "fluent3d_manifest.json";
    private static final String DIR_NAME = "fluent3d";
    private static final String DONE_MARKER = ".complete";

    interface ProgressListener {
        /** يُستدعى على خيط التنزيل الخلفي، لا على خيط الواجهة — المستدعي يرحّله لو احتاج. */
        void onProgress(int done, int total);
        void onDone(boolean success, String error);
    }

    private final Context appContext;
    private Map<String, String> manifest; // رمز الإيموجي -> مسار نسبي بمستودع Fluent Emoji
    private final Map<String, Bitmap> bitmapCache = new ConcurrentHashMap<>();

    Fluent3DEmojiManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private synchronized Map<String, String> manifest() {
        if (manifest != null) return manifest;
        Map<String, String> m = new HashMap<>();
        try (InputStream is = appContext.getAssets().open(MANIFEST_ASSET)) {
            byte[] buf = new byte[is.available()];
            int read = is.read(buf);
            String json = new String(buf, 0, Math.max(read, 0), "UTF-8");
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                m.put(k, obj.getString(k));
            }
        } catch (Exception e) {
            // تجاهل: لو فشلت قراءة القائمة ترجع خريطة فاضية، وكل الرموز تستخدم النص العادي تلقائياً
        }
        manifest = m;
        return manifest;
    }

    private File dir() {
        return new File(appContext.getFilesDir(), DIR_NAME);
    }

    private File markerFile() {
        return new File(dir(), DONE_MARKER);
    }

    /** اسم ملف محلي آمن لأي رمز، معتمد على أكواد يونيكود الفعلية بعيداً عن مسافات/رموز خاصة. */
    private String localFileName(String glyph) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glyph.length()) {
            int cp = glyph.codePointAt(i);
            sb.append(Integer.toHexString(cp)).append('_');
            i += Character.charCount(cp);
        }
        sb.append(".png");
        return sb.toString();
    }

    /** هل الحزمة مُنزَّلة بالكامل وجاهزة؟ */
    boolean isDownloaded() {
        return markerFile().isFile();
    }

    boolean hasAsset(String glyph) {
        return manifest().containsKey(glyph);
    }

    /** الملف المحلي للرمز لو منزّل وجاهز، أو null (فيرجع المستدعي للرمز النصي العادي). */
    File getLocalFile(String glyph) {
        if (!isDownloaded() || glyph == null) return null;
        if (!manifest().containsKey(glyph)) return null;
        File f = new File(dir(), localFileName(glyph));
        return f.isFile() ? f : null;
    }

    /** Bitmap مُخزَّن مؤقتاً بالذاكرة للرمز (يُحمَّل من الملف المحلي لمرة واحدة فقط)، أو null. */
    Bitmap getBitmap(String glyph) {
        Bitmap cached = bitmapCache.get(glyph);
        if (cached != null) return cached;
        File f = getLocalFile(glyph);
        if (f == null) return null;
        try {
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) bitmapCache.put(glyph, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** يحذف كل الصور المنزّلة ويرجع النمط للنص العادي تلقائياً. تنفيذ فوري وخفيف (ملفات محلية فقط). */
    void deleteAll() {
        bitmapCache.clear();
        deleteRecursive(dir());
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    /** ينزّل كل صور الحزمة بخيط خلفي (~27 ميجا لمرة واحدة). progress.onProgress/onDone يُستدعيان
     *  على خيط التنزيل نفسه، لا خيط الواجهة — المستدعي (الشاشة) مسؤول عن الترحيل لـ runOnUiThread. */
    void download(final ProgressListener progress) {
        new Thread(() -> {
            Map<String, String> m = manifest();
            File d = dir();
            if (!d.exists()) d.mkdirs();
            File marker = markerFile();
            if (marker.exists()) marker.delete();
            int total = Math.max(1, m.size());
            int done = 0;
            boolean anyFailure = false;
            for (Map.Entry<String, String> entry : m.entrySet()) {
                String glyph = entry.getKey();
                String relPath = entry.getValue();
                File outFile = new File(d, localFileName(glyph));
                if (!outFile.isFile()) {
                    try {
                        downloadOne(relPath, outFile);
                    } catch (Exception e) {
                        anyFailure = true; // نتجاوز الملف الفاشل، هذا الرمز سيرجع للنص العادي تلقائياً
                    }
                }
                done++;
                if (progress != null) progress.onProgress(done, total);
            }
            try {
                marker.createNewFile();
            } catch (IOException ignored) {
            }
            if (progress != null) {
                progress.onDone(true, anyFailure ? "بعض الرموز لم تُنزَّل (شبكة ضعيفة)، ستبقى بشكلها العادي" : null);
            }
        }).start();
    }

    private void downloadOne(String relPath, File outFile) throws IOException {
        URL url = new URL(BASE_URL + encodePathSegments(relPath));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            File tmp = new File(outFile.getParentFile(), outFile.getName() + ".part");
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(outFile)) {
                tmp.delete();
                throw new IOException("rename failed");
            }
        } finally {
            conn.disconnect();
        }
    }

    /** يرمّز كل جزء من المسار (قد يحوي مسافات/رموز خاصة بأسماء المجلدات) ليصير رابطاً صالحاً. */
    private String encodePathSegments(String relPath) {
        String[] parts = relPath.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            try {
                sb.append(java.net.URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }
}
