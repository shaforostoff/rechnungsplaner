package com.shaforostoff.rechnungsplaner.data;

/** A file generated for an invoice, kept in the app-private archive so it can be re-shared. */
public class InvoiceFile {

    public long id = -1L;
    public long invoiceId = -1L;
    /** The {@code OutputFormat} name this file was produced for. */
    public String format;
    public String fileName;
    /** Path relative to the archive directory. */
    public String relPath;
    public long createdAt;

    public boolean isPdf() {
        return fileName != null && fileName.toLowerCase(java.util.Locale.US).endsWith(".pdf");
    }
}
