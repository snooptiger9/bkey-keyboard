package com.bbgboard.keyboard;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class GifDownloadCache {
    private GifDownloadCache() {}

    private static final int MAX_FILES = 60;
    private static final long MAX_BYTES = 40L * 1024 * 1024;

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "gif_send");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static String downloadBlocking(Context c, String url) {
        HttpURLConnection conn = null;
        try {
            String name = "g" + Math.abs(url.hashCode()) + ".gif";
            File dest = new File(dir(c), name);
            if (dest.exists() && dest.length() > 0) return name;
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            pruneIfNeeded(dir(c));
            return name;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void pruneIfNeeded(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            long total = 0;
            for (File f : files) total += f.length();
            if (files.length <= MAX_FILES && total <= MAX_BYTES) return;
            java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            int i = 0;
            while (i < files.length && (files.length - i > MAX_FILES || total > MAX_BYTES)) {
                total -= files[i].length();
                files[i].delete();
                i++;
            }
        } catch (Exception ignored) {}
    }
}
