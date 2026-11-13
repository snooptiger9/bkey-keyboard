package com.bbgboard.keyboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ينزّل تحديث ميزة OTP (bkey-full-latest.apk) ويفتحه مباشرة بمثبّت الحزم، بدون الحاجة للمتصفح. */
final class AppUpdater {
    private AppUpdater() {}

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bkey-update-download");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String UPDATE_URL =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.apk";
    // ترويسة متصفح حقيقية — بعض خوادم GitHub ترفض طلبات الأدوات البرمجية (404/403) إذا ما شافت user-agent يشبه متصفح
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int MAX_REDIRECTS = 5;

    interface Callback {
        void onFinished(boolean success);
    }

    static void downloadAndInstall(final Context appContext, final Callback cb) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                File extDir = appContext.getExternalFilesDir(null);
                if (extDir == null) throw new IOException("external files dir not available");
                File dest = new File(extDir, "bkey-update.apk");
                if (dest.exists()) dest.delete();

                // نتبع إعادة التوجيه يدوياً (github.com/.../raw/ يحول إلى سيرفر المحتوى الفعلي)
                // بدل الاعتماد على اتباع تلقائي قد لا يشتغل بنفس طريقة المتصفح
                String currentUrl = UPDATE_URL;
                int code;
                for (int i = 0; ; i++) {
                    if (i > MAX_REDIRECTS) throw new IOException("too many redirects");
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    code = conn.getResponseCode();
                    boolean isRedirect = code == HttpURLConnection.HTTP_MOVED_PERM
                            || code == HttpURLConnection.HTTP_MOVED_TEMP
                            || code == HttpURLConnection.HTTP_SEE_OTHER
                            || code == 307 || code == 308;
                    if (!isRedirect) break;
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) throw new IOException("redirect with no Location header");
                    currentUrl = location;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code + " from update server");
                }

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                MAIN.post(() -> {
                    boolean installed = tryInstall(appContext, dest);
                    if (cb != null) cb.onFinished(installed);
                });
            } catch (Exception e) {
                AppLogger.log("AppUpdater", "downloadAndInstall()", e);
                MAIN.post(() -> {
                    if (cb != null) cb.onFinished(false);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static boolean tryInstall(Context context, File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            AppLogger.log("AppUpdater", "tryInstall()", e);
            return false;
        }
    }
}
