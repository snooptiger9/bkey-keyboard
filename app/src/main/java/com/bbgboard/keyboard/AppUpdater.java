package com.bbgboard.keyboard;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;

final class AppUpdater {
    private AppUpdater() {}

    private static final String UPDATE_URL =
            "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.apk";
    private static final String UPDATE_FILE_NAME = "bkey-update.apk";

    interface Callback {
        void onFinished(boolean success);
    }

    static void downloadAndInstall(final Context appContext, final Callback cb) {
        try {
            final DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                if (cb != null) cb.onFinished(false);
                return;
            }

            File extDir = appContext.getExternalFilesDir(null);
            if (extDir != null) {
                File old = new File(extDir, UPDATE_FILE_NAME);
                if (old.exists()) old.delete();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(UPDATE_URL));
            request.setTitle("bkey");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(appContext, null, UPDATE_FILE_NAME);
            request.setMimeType("application/vnd.android.package-archive");

            final long downloadId = dm.enqueue(request);
            final BroadcastReceiver[] receiverHolder = new BroadcastReceiver[1];

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id != downloadId) return;
                    try {
                        appContext.unregisterReceiver(receiverHolder[0]);
                    } catch (Exception ignore) {
                    }

                    boolean success = false;
                    Cursor cursor = null;
                    try {
                        cursor = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                        if (cursor != null && cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                            success = status == DownloadManager.STATUS_SUCCESSFUL;
                            if (!success) {
                                int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                                AppLogger.log("AppUpdater", "downloadAndInstall()",
                                        new IOException("DownloadManager status=" + status + " reason=" + reason));
                            }
                        }
                    } finally {
                        if (cursor != null) cursor.close();
                    }

                    if (success) {
                        File dest = new File(appContext.getExternalFilesDir(null), UPDATE_FILE_NAME);
                        boolean installed = tryInstall(appContext, dest);
                        if (cb != null) cb.onFinished(installed);
                    } else {
                        if (cb != null) cb.onFinished(false);
                    }
                }
            };
            receiverHolder[0] = receiver;

            ContextCompat.registerReceiver(appContext, receiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception e) {
            AppLogger.log("AppUpdater", "downloadAndInstall()", e);
            if (cb != null) cb.onFinished(false);
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
