package com.bbgboard.keyboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
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

    interface Callback {
        void onFinished(boolean success);
    }

    static void downloadAndInstall(final Context appContext, final Callback cb) {
        EXECUTOR.execute(() -> {
            File dest = new File(appContext.getExternalFilesDir(null), "bkey-update.apk");
            HttpURLConnection conn = null;
            try {
                if (dest.exists()) dest.delete();
                URL url = new URL(UPDATE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
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
