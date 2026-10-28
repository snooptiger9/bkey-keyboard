package com.bbgboard.keyboard;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** يحتفظ بصورة مصغّرة واحدة فقط لآخر لقطة شاشة؛ كل لقطة جديدة تحل محل السابقة. */
public final class ScreenshotClip {
    private static final int MAX_SIDE = 720;

    private ScreenshotClip() {}

    public static File file(Context c) { return new File(c.getFilesDir(), "screenshot_clip.jpg"); }

    public static boolean exists(Context c) { return file(c).exists(); }

    public static void clear(Context c) { file(c).delete(); }

    /** نسخة صغيرة جداً (الضلع الأكبر maxSide بكسل) من المصغّرة المحفوظة لعرضها داخل شريط الاقتراحات. */
    public static Bitmap loadSmallThumb(Context c, int maxSide) {
        try {
            File f = file(c);
            if (!f.exists()) return null;
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), b);
            if (b.outWidth <= 0 || b.outHeight <= 0) return null;
            int sample = 1;
            int big = Math.max(b.outWidth, b.outHeight);
            while (big / (sample * 2) >= maxSide) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    /** يحذف المصغّرة لو مرّ عليها أكثر من المدة المحددة (0 أو أقل = بدون حذف). يرجع true إذا حذفها. */
    public static boolean pruneIfOld(Context c, long maxAgeMs) {
        if (maxAgeMs <= 0) return false;
        File f = file(c);
        if (f.exists() && System.currentTimeMillis() - f.lastModified() > maxAgeMs) {
            return f.delete();
        }
        return false;
    }

    public static String neededPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(neededPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static Uri contentUri(Context c) {
        return Uri.parse("content://" + ScreenshotProvider.AUTHORITY + "/latest.jpg?v=" + file(c).lastModified());
    }

    /** يبحث عن أحدث لقطة شاشة التُقطت قبل لحظات ويحفظ مصغّرتها. يرجع true إذا تغيّرت المصغّرة. */
    public static boolean captureLatest(Context c) {
        Cursor cur = null;
        try {
            if (!hasPermission(c)) return false;
            String[] proj = Build.VERSION.SDK_INT >= 29
                    ? new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH}
                    : new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATA};
            // نطلب فقط الصور المضافة خلال آخر ٣٠ ثانية وبحد أقصى ٦ صفوف (بدل مسح مكتبة الصور كاملة)
            long sinceSec = System.currentTimeMillis() / 1000L - 30L;
            String sel = MediaStore.Images.Media.DATE_ADDED + " >= ?";
            String[] selArgs = new String[]{String.valueOf(sinceSec)};
            if (Build.VERSION.SDK_INT >= 26) {
                android.os.Bundle q = new android.os.Bundle();
                q.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, sel);
                q.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selArgs);
                q.putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        new String[]{MediaStore.Images.Media.DATE_ADDED});
                q.putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
                q.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 6);
                cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, q, null);
            } else {
                cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                        sel, selArgs, MediaStore.Images.Media.DATE_ADDED + " DESC");
            }
            if (cur == null) return false;
            long nowSec = System.currentTimeMillis() / 1000L;
            long lastId = c.getSharedPreferences("bb_keyboard_clipboard_prefs", Context.MODE_PRIVATE)
                    .getLong("shot_last_id", -1);
            int checked = 0;
            while (cur.moveToNext() && checked++ < 6) {
                long id = cur.getLong(0);
                String name = cur.getString(1);
                long added = cur.getLong(2);
                String path = cur.getString(3);
                if (nowSec - added > 25) break;
                String hay = ((name == null ? "" : name) + " " + (path == null ? "" : path)).toLowerCase(Locale.ROOT);
                if (!(hay.contains("screenshot") || hay.contains("screen_shot") || hay.contains("screen shot")
                        || hay.contains("لقطة"))) continue;
                if (id == lastId) return false;
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                if (saveThumb(c, uri)) {
                    c.getSharedPreferences("bb_keyboard_clipboard_prefs", Context.MODE_PRIVATE)
                            .edit().putLong("shot_last_id", id).apply();
                    return true;
                }
                return false;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return false;
    }

    private static boolean saveThumb(Context c, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int sample = 1;
            int big = Math.max(bounds.outWidth, bounds.outHeight);
            while (big / (sample * 2) >= MAX_SIDE) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            in.close();
            if (bmp == null) return false;
            int bigger = Math.max(bmp.getWidth(), bmp.getHeight());
            if (bigger > MAX_SIDE) {
                float k = MAX_SIDE / (float) bigger;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * k), Math.round(bmp.getHeight() * k), true);
                if (scaled != bmp) bmp.recycle();
                bmp = scaled;
            }
            File tmp = new File(c.getFilesDir(), "screenshot_clip.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.close();
            bmp.recycle();
            File dest = file(c);
            if (dest.exists()) dest.delete();
            return tmp.renameTo(dest);
        } catch (Exception e) {
            return false;
        }
    }
}
