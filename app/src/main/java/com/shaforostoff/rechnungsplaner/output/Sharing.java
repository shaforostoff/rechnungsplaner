package com.shaforostoff.rechnungsplaner.output;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.shaforostoff.rechnungsplaner.util.Paths;
import com.shaforostoff.rechnungsplaner.util.ShareProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the share-sheet intents for generated invoices.
 *
 * <p>{@code ACTION_SEND} with an attachment is used even for email, rather than a {@code mailto:}
 * {@code ACTION_SENDTO}, because the latter cannot reliably carry a file. One chooser then covers
 * mail apps, Telegram and everything else, with the customer's address pre-filled for the mail
 * ones.
 */
public final class Sharing {

    private Sharing() {
    }

    /**
     * Copies a file out of the archive into the directory {@link ShareProvider} serves.
     *
     * <p>Copying rather than exposing the archive keeps the provider's reach to a single directory,
     * so a granted read cannot be turned into a read of the whole invoice history.
     */
    public static Uri stage(Context ctx, File file) throws IOException {
        File target = new File(ShareProvider.shareDir(ctx), file.getName());
        // Some things are generated straight into the served directory -- the contacts archive is.
        // Copying one onto itself opens the source and truncates it through the target in the same
        // breath, so the read finds nothing: that is how the contacts export became a zero-byte
        // zip. Already staged is already staged.
        if (Paths.isSameFile(file, target)) return ShareProvider.uriFor(target.getName());

        InputStream in = new FileInputStream(file);
        try {
            OutputStream out = new FileOutputStream(target);
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
        return ShareProvider.uriFor(target.getName());
    }

    /**
     * A chooser for one or more generated files.
     *
     * @param toEmail  the customer's address, pre-filled for mail apps; may be null
     * @param subject  the mail subject; may be null
     * @param body     the mail body; may be null
     */
    public static Intent share(Context ctx, List<File> files, String toEmail, String subject,
                               String body, String chooserTitle) throws IOException {
        ArrayList<Uri> uris = new ArrayList<Uri>(files.size());
        for (File f : files) uris.add(stage(ctx, f));

        Intent send;
        if (uris.size() == 1) {
            send = new Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, uris.get(0))
                    .setType(ShareProvider.mimeOf(files.get(0).getName()));
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    // A hybrid PDF plus its XML have different mime types, so the chooser needs a
                    // type broad enough to cover both.
                    .setType("*/*");
        }
        if (notEmpty(toEmail)) send.putExtra(Intent.EXTRA_EMAIL, new String[]{toEmail.trim()});
        if (notEmpty(subject)) send.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (notEmpty(body)) send.putExtra(Intent.EXTRA_TEXT, body);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // FLAG_GRANT_READ_URI_PERMISSION only reaches URIs the system finds via getData() or
        // ClipData. Those in EXTRA_STREAM are NOT granted by the flag alone, so every one has to be
        // attached as ClipData as well or the receiving app gets a SecurityException.
        ClipData clip = null;
        for (int i = 0; i < uris.size(); i++) {
            ClipData.Item item = new ClipData.Item(uris.get(i));
            if (clip == null) {
                clip = new ClipData("invoice",
                        new String[]{ShareProvider.mimeOf(files.get(i).getName())}, item);
            } else {
                clip.addItem(item);
            }
        }
        if (clip != null) send.setClipData(clip);

        return Intent.createChooser(send, chooserTitle);
    }

    /** Opens a single generated file in a viewer. */
    public static Intent view(Context ctx, File file) throws IOException {
        Uri uri = stage(ctx, file);
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, ShareProvider.mimeOf(file.getName()))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    /** Plain text, for the tour list. */
    public static Intent shareText(String text, String subject, String chooserTitle) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        if (notEmpty(subject)) send.putExtra(Intent.EXTRA_SUBJECT, subject);
        return Intent.createChooser(send, chooserTitle);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
