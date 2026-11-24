package com.bbgboard.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The user's own saved stickers: up to MAX images copied from other apps, kept in app-private storage. */
public final class StickerStore {
    public static final int MAX = 20;
    private static final int MAX_SIDE = 512;

    private StickerStore() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "stickers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File file(Context c, String name) {
        if (name == null || name.contains("/") || name.contains("..")) return null;
        return new File(dir(c), name);
    }

    /** Newest first. */
    public static List<File> list(Context c) {
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".png"));
        if (files == null) return new ArrayList<>();
        List<File> out = new ArrayList<>(Arrays.asList(files));
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        return out;
    }

    public static boolean isFull(Context c) { return list(c).size() >= MAX; }

    /** Decodes the stream, scales it down if needed and saves it as a PNG. Returns false on failure. */
    public static boolean add(Context c, InputStream in) {
        try {
            byte[] data = readAll(in);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (bmp == null) return false;
            int side = Math.max(bmp.getWidth(), bmp.getHeight());
            if (side > MAX_SIDE) {
                float f = MAX_SIDE / (float) side;
                bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * f)),
                        Math.max(1, Math.round(bmp.getHeight() * f)), true);
            }
            File out = new File(dir(c), System.currentTimeMillis() + ".png");
            try (FileOutputStream fo = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            return true;
        } catch (Throwable t) {
            AppLogger.log("StickerStore", "add()", t instanceof Exception ? (Exception) t : new Exception(t));
            return false;
        }
    }

    public static void remove(Context c, File f) {
        if (f != null && f.getParentFile() != null && f.getParentFile().equals(dir(c))) f.delete();
    }

    public static Bitmap thumb(File f, int px) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), b);
        int sample = 1;
        while (b.outWidth / (sample * 2) >= px) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            total += n;
            if (total > 12L * 1024 * 1024) throw new java.io.IOException("too large");
        }
        return bo.toByteArray();
    }
}
