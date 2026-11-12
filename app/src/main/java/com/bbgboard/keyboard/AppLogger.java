package com.bbgboard.keyboard;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLogger {

    private static final String LOG_FILE_NAME = "bkey_error_log.txt";
    private static final long MAX_LOG_SIZE_BYTES = 150 * 1024L;
    private static volatile Context appCtx;

    private AppLogger() {
    }

    public static void init(Context context) {
        if (appCtx == null && context != null) {
            appCtx = context.getApplicationContext();
        }
    }

    public static void log(String tag, String message, Throwable t) {
        Context ctx = appCtx;
        if (ctx == null) return;
        try {
            logInternal(ctx, tag, message, t);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void logInternal(Context ctx, String tag, String message, Throwable t) {
        try {
            File f = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (f.exists() && f.length() > MAX_LOG_SIZE_BYTES) {
                trimLogFile(f);
            }
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            StringBuilder sb = new StringBuilder();
            sb.append(time).append(" [").append(tag).append("] ").append(message);
            if (t != null) {
                sb.append(" — ").append(t.getClass().getSimpleName());
                if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
                StackTraceElement[] trace = t.getStackTrace();
                if (trace.length > 0) {
                    sb.append(" @ ").append(trace[0].toString());
                }
            }
            sb.append('\n');
            FileWriter fw = new FileWriter(f, true);
            fw.write(sb.toString());
            fw.close();
        } catch (IOException ignored) {
        }
    }

    public static synchronized String readLog(Context context) {
        Context ctx = context != null ? context : appCtx;
        if (ctx == null) return "";
        try {
            File f = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (!f.exists()) return "";
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            fis.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static synchronized void clearLog(Context context) {
        Context ctx = context != null ? context : appCtx;
        if (ctx == null) return;
        File f = new File(ctx.getFilesDir(), LOG_FILE_NAME);
        if (f.exists()) f.delete();
    }

    private static void trimLogFile(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            fis.close();
            String content = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            int cut = content.length() / 2;
            int nl = content.indexOf('\n', cut);
            String kept = nl >= 0 ? content.substring(nl + 1) : content.substring(Math.min(cut, content.length()));
            FileOutputStream fos = new FileOutputStream(f, false);
            fos.write(kept.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException ignored) {
        }
    }
}
