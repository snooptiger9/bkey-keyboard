package com.bbgboard.keyboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** يقدّم ملف GIF المحمّل مؤقتاً بمجلد الكاش عند إرساله للمحادثة (نفس فكرة ScreenshotProvider). */
public class GifCacheProvider extends ContentProvider {
    public static final String AUTHORITY = "com.bbgboard.keyboard.gifcache";

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException();
        // لمنع الهروب من مجلد الكاش (مثلاً ".." داخل الاسم بعد فك ترميز %2F)، نأخذ اسم الملف فقط
        // ونتأكد أن المسار النهائي يبقى داخل مجلد الكاش قبل فتحه.
        String safeName = new File(name).getName();
        if (safeName.isEmpty() || safeName.contains("..")) throw new FileNotFoundException();
        File dir = GifDownloadCache.dir(getContext());
        File f = new File(dir, safeName);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                throw new FileNotFoundException();
            }
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "image/gif"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
