package com.shaforostoff.rechnungsplaner.pdf;

/**
 * Raised when a document is shaped in a way {@link PdfA3Packer} will not risk rewriting -- a
 * catalog stored inside an object stream, an indirect name tree, an unreadable trailer.
 *
 * <p>Checked on purpose. Producing a subtly corrupt invoice is worse than producing two files, so
 * the caller is made to decide: the output pipeline degrades to a plain PDF plus a separate XML and
 * tells the user, rather than a swallowed exception silently shipping something broken.
 */
public class UnsupportedPdfException extends Exception {

    public UnsupportedPdfException(String message) {
        super(message);
    }
}
