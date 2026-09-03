package com.shaforostoff.rechnungsplaner.util;

import java.io.File;
import java.io.IOException;

/**
 * The two path questions that have to be answered the same way in more than one place.
 *
 * <p>Both compare canonical paths, so a {@code ../} in a crafted Uri and a symlinked cache
 * directory get the answer the file system would give rather than the one the string suggests.
 * Plain {@code java.io}, so the answers are tested rather than assumed.
 */
public final class Paths {

    private Paths() {
    }

    /**
     * Whether the two paths name one file.
     *
     * @return false when either path cannot be resolved, which keeps a caller that is deciding
     *         whether to skip work doing the work instead
     */
    public static boolean isSameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Whether {@code file} resolves to something inside {@code dir}.
     *
     * <p>The directory itself is not inside itself, and the separator is appended before the
     * comparison so a sibling directory sharing a name prefix -- {@code share-old} against
     * {@code share} -- does not read as contained.
     *
     * @return false when either path cannot be resolved, which is the safe answer for a caller
     *         deciding whether to serve a file
     */
    public static boolean isInside(File dir, File file) {
        if (dir == null || file == null) return false;
        try {
            String parent = dir.getCanonicalPath();
            if (!parent.endsWith(File.separator)) parent += File.separator;
            return file.getCanonicalPath().startsWith(parent);
        } catch (IOException e) {
            return false;
        }
    }
}
