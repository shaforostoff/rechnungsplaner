package com.shaforostoff.rechnungsplaner.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Hands generated invoices to other apps as {@code content://} URIs.
 *
 * <p>The framework has no FileProvider -- that lives in {@code androidx.core}, which would be the
 * only dependency this app takes, for one class. Seventy lines of ContentProvider is the better
 * trade, and it is the same kind of thing the sibling projects hand-roll.
 *
 * <p>Serves read-only from one directory under {@code cacheDir} and refuses anything that resolves
 * outside it, so a crafted Uri cannot walk into app-private storage.
 */
public class ShareProvider extends ContentProvider {

    private static final String AUTHORITY = "com.shaforostoff.rechnungsplaner.share";
    private static final String DIRECTORY = "share";

    /** The directory whose contents this provider is willing to serve. */
    public static File shareDir(android.content.Context ctx) {
        File dir = new File(ctx.getCacheDir(), DIRECTORY);
        dir.mkdirs();
        return dir;
    }

    public static Uri uriFor(String fileName) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(fileName)
                .build();
    }

    public static String mimeOf(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return mimeOf(uri.getLastPathSegment());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) && !"rw".equals(mode) && mode != null && mode.contains("w")) {
            throw new FileNotFoundException("read-only provider");
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Mail clients and messengers read the display name and size from here before attaching, and a
     * provider that returns nothing shows up as a nameless zero-byte file.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException("no file named");
        File dir = shareDir(getContext());
        File file = new File(dir, name);
        try {
            // Canonical paths, so "../" in a crafted Uri cannot escape the share directory.
            if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                throw new FileNotFoundException("outside the share directory");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("unresolvable path");
        }
        if (!file.isFile()) throw new FileNotFoundException(name);
        return file;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
