package com.bbgboard.keyboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AppUpdater {
    private AppUpdater() {}

    private static final String UPDATE_URL_LITE =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-latest.apk";
    private static final String UPDATE_URL_FULL =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.apk";
    private static final String VERSION_URL_LITE =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-latest.version";
    private static final String VERSION_URL_FULL =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.version";
    private static final String UPDATE_FILE_NAME = "bkey-update.apk";

    interface Callback {
        void onFinished(boolean success);
    }

    interface VersionCheckCallback {
        /** remoteVersionCode is null if the check itself failed (e.g. no network). */
        void onChecked(Integer remoteVersionCode);
    }

    /** Reads the tiny .version file published alongside the APK matching the currently installed flavor. */
    static void fetchRemoteVersionCode(final VersionCheckCallback cb) {
        final String versionUrl = BuildConfig.OTP_FEATURES_ENABLED ? VERSION_URL_FULL : VERSION_URL_LITE;
        new Thread(() -> {
            Integer result = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(versionUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    result = Integer.parseInt(line.trim());
                }
            } catch (Exception e) {
                AppLogger.log("AppUpdater", "fetchRemoteVersionCode()", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            final Integer finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> cb.onChecked(finalResult));
        }).start();
    }

    /**
     * Always fetches whichever build (lite or full) is currently installed on this device,
     * so a routine "check for updates" tap can never silently switch flavor and drop the
     * OTP feature/permissions — only the dedicated OTP-activation button does that on purpose.
     */
    static void downloadAndInstall(final Context appContext, final Callback cb) {
        downloadAndInstall(appContext, BuildConfig.OTP_FEATURES_ENABLED, cb);
    }

    // Downloads the APK ourselves on a plain background thread instead of handing it to
    // DownloadManager. DownloadManager's own ACTION_DOWNLOAD_COMPLETE broadcast used to be
    // the trigger for opening the installer automatically, but Android 10+ silently blocks
    // starting an activity from inside a BroadcastReceiver once the app isn't guaranteed to
    // be in the foreground at the exact moment the broadcast arrives -- no crash, no log,
    // the installer just never opens, which is exactly why it only ever worked after
    // manually tapping the "download finished" system notification. Downloading in-process
    // and calling tryInstall() straight from the main-thread completion callback, while the
    // settings screen that started this is still the one open on screen, opens the
    // installer every time with nothing left to tap.
    static void downloadAndInstall(final Context appContext, final boolean fetchFullBuild, final Callback cb) {
        final String fallbackUrl = fetchFullBuild ? UPDATE_URL_FULL : UPDATE_URL_LITE;
        final String versionUrl = fetchFullBuild ? VERSION_URL_FULL : VERSION_URL_LITE;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean success = false;
            try {
                File extDir = appContext.getExternalFilesDir(null);
                if (extDir == null) throw new IOException("no external files dir");
                File dest = new File(extDir, UPDATE_FILE_NAME);

                // Prefer the GitHub Release asset: unlike the raw repo file, downloading the
                // Release asset is what GitHub's per-asset download counter counts, so in-app
                // updates show up in the Releases download numbers. The raw file stays as a
                // fallback for the short window where the version file is already updated but
                // the Release itself isn't published yet (or any other failure).
                String releaseUrl = null;
                try {
                    int code = readVersionCodeBlocking(versionUrl);
                    if (code > 0) {
                        releaseUrl = RELEASE_DOWNLOAD_BASE + (fetchFullBuild ? "bkey-plus-v" : "bkey-clean-v") + code + ".apk";
                    }
                } catch (Exception e) {
                    AppLogger.log("AppUpdater", "downloadAndInstall() version lookup", e);
                }
                if (releaseUrl != null) {
                    success = downloadToFile(releaseUrl, dest);
                }
                if (!success) {
                    success = downloadToFile(fallbackUrl, dest);
                }
            } catch (Exception e) {
                AppLogger.log("AppUpdater", "downloadAndInstall()", e);
                success = false;
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                if (finalSuccess) {
                    File dest = new File(appContext.getExternalFilesDir(null), UPDATE_FILE_NAME);
                    boolean installed = tryInstall(appContext, dest);
                    if (cb != null) cb.onFinished(installed);
                } else {
                    if (cb != null) cb.onFinished(false);
                }
            });
        }).start();
    }

    private static final String RELEASE_DOWNLOAD_BASE =
            "https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/";

    private static int readVersionCodeBlocking(String versionUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(versionUrl).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            reader.close();
            return line == null ? -1 : Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Downloads url into dest (replacing it). Returns true only on HTTP 200 with a non-empty file. */
    private static boolean downloadToFile(String url, File dest) {
        HttpURLConnection conn = null;
        try {
            if (dest.exists()) dest.delete();
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            boolean ok = dest.length() > 0;
            if (!ok) dest.delete();
            return ok;
        } catch (Exception e) {
            AppLogger.log("AppUpdater", "downloadToFile()", e);
            if (dest.exists()) dest.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
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
