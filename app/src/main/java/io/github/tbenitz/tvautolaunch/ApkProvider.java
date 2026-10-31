package io.github.tbenitz.tvautolaunch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only URI for exactly one confirmed, downloaded APK in app-private cache. */
public final class ApkProvider extends ContentProvider {
    private File apk(Uri uri) throws FileNotFoundException {
        if (uri == null || !"/current.apk".equals(uri.getPath()))
            throw new FileNotFoundException("Unknown APK");
        File file = new File(getContext().getCacheDir(), "apk/current.apk");
        if (!file.isFile()) throw new FileNotFoundException("APK not present");
        return file;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return "/current.apk".equals(uri.getPath()) ? "application/vnd.android.package-archive" : null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(apk(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = apk(uri);
            String[] fields = projection == null
                    ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                    : projection;
            MatrixCursor result = new MatrixCursor(fields);
            Object[] row = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(fields[i])) row[i] = "download.apk";
                if (OpenableColumns.SIZE.equals(fields[i])) row[i] = file.length();
            }
            result.addRow(row);
            return result;
        } catch (FileNotFoundException ignored) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { return 0; }
    @Override public int delete(Uri uri, String where, String[] args) { return 0; }
}
