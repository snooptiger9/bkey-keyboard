package com.bbgboard.keyboard;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Not a GIF cache/library in the GIF-provider sense — this only exists because Android's
 * commitContent()/ClipData APIs need an actual content:// file to hand to the receiving
 * app (WhatsApp, SMS, etc.), and that app may read it a short while after the user acts.
 * Every call here re-downloads a fresh copy under a unique name (no reuse of a previous
 * download for the same GIF, ever), and anything older than a short grace window is swept
 * away automatically — so nothing here is kept or reused as a stored library of the GIF provider media.
 */
final class GifDownloadCache {
    private GifDownloadCache() {}

    // Long enough for the receiving app to read a just-sent/just-copied GIF (including a
    // clipboard paste the user does a little while later), short enough that this never
    // becomes a standing collection of GIF files.
    private static final long MAX_AGE_MS = 30L * 60 * 1000;

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "gif_send");
        if (!d.exists()) d.mkdirs();
        sweepStale(d);
        return d;
    }

    static String downloadBlocking(Context c, String url) {
        HttpURLConnection conn = null;
        try {
            File cacheDir = dir(c);
            // Unique name every time — never reused/served from a prior download of the
            // same URL, so nothing here is treated as a cached/stored copy of the GIF provider media.
            String name = "g" + System.nanoTime() + ".gif";
            File dest = new File(cacheDir, name);
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
            return name;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void sweepStale(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (now - f.lastModified() > MAX_AGE_MS) f.delete();
            }
        } catch (Exception e) {
            AppLogger.log("GifDownloadCache", "sweepStale()", e);
        }
    }
}
