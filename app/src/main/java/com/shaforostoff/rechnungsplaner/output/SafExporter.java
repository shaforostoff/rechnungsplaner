package com.shaforostoff.rechnungsplaner.output;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.shaforostoff.rechnungsplaner.util.ShareProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies generated files into a folder the user picked once.
 *
 * <p>The Storage Access Framework rather than {@code MediaStore.Downloads} or a storage permission:
 * a persisted tree grant works from API 21 up, needs no runtime permission at all, and lets the
 * user put invoices somewhere they actually keep them, including a cloud provider.
 */
public final class SafExporter {

    private SafExporter() {
    }

    /** The intent that asks the user to pick the export folder. */
    public static Intent pickFolderIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    }

    /** Keeps the grant alive across reboots, so the folder only has to be picked once. */
    public static void persistPermission(Context ctx, Uri treeUri) {
        ctx.getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    /**
     * Writes one archived file into the chosen folder.
     *
     * @return the created document, or null when the tree is no longer reachable
     */
    public static Uri copyInto(Context ctx, String treeUriString, File file) throws IOException {
        if (treeUriString == null || treeUriString.isEmpty()) return null;
        Uri tree = Uri.parse(treeUriString);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));

        ContentResolver resolver = ctx.getContentResolver();
        Uri target = DocumentsContract.createDocument(resolver, parent,
                ShareProvider.mimeOf(file.getName()), file.getName());
        if (target == null) return null;

        InputStream in = new FileInputStream(file);
        try {
            OutputStream out = resolver.openOutputStream(target);
            if (out == null) return null;
            try {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        return target;
    }

    /** A readable name for the chosen folder, for the settings screen. */
    public static String displayName(Context ctx, String treeUriString) {
        if (treeUriString == null || treeUriString.isEmpty()) return null;
        try {
            Uri tree = Uri.parse(treeUriString);
            Uri document = DocumentsContract.buildDocumentUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            Cursor c = ctx.getContentResolver().query(document,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (c == null) return null;
            try {
                return c.moveToFirst() ? c.getString(0) : null;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            // A revoked or stale grant should show as "not set", not crash the settings screen.
            return null;
        }
    }
}
