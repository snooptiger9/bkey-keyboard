package com.bbgboard.keyboard;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** يحمّل ملف GIF كامل (مو المصغّرة) لمجلد كاش مؤقت حتى يُرسل للمحادثة عبر commitContent. */
final class GifDownloadCache {
    private GifDownloadCache() {}

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "gif_send");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** يحمّل الـGIF من رابطه لملف محلي ويرجع اسم الملف، أو null لو فشل. يُستدعى بخيط خلفي. */
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
            return name;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
