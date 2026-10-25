package com.kadian.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给系统相机提供拍照输出文件的最小 ContentProvider。
 * 文件放在 getExternalCacheDir()/uploads/，无需任何存储权限。
 */
public class WebAssetProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kadian.app.fileprovider";
    private static final String DIR = "uploads";
    private static final String TAG = "KadianProvider";

    public static Uri uriForFile(Context ctx, File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(DIR)
                .appendPath(f.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        File dir = new File(getContext().getExternalCacheDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return true;
    }

    private File resolve(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) return null;
        return new File(new File(getContext().getExternalCacheDir(), DIR), name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null) throw new FileNotFoundException(uri.toString());
        boolean write = mode != null && mode.contains("w");
        if (!f.exists()) {
            if (!write) throw new FileNotFoundException(uri.toString());
            try {
                f.getParentFile().mkdirs();
                if (!f.createNewFile()) throw new FileNotFoundException(uri.toString());
            } catch (Exception e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        int pfdMode;
        if (write) {
            pfdMode = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            pfdMode = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(f, pfdMode);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }
}
