package com.shaforostoff.rechnungsplaner.output;

import android.content.Context;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.Invoice;
import com.shaforostoff.rechnungsplaner.data.InvoiceDao;
import com.shaforostoff.rechnungsplaner.data.InvoiceFile;
import com.shaforostoff.rechnungsplaner.data.InvoiceMapper;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.OutputFormat;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.einvoice.EnInvoice;
import com.shaforostoff.rechnungsplaner.einvoice.EnValidator;
import com.shaforostoff.rechnungsplaner.einvoice.Problem;
import com.shaforostoff.rechnungsplaner.einvoice.Profile;
import com.shaforostoff.rechnungsplaner.einvoice.Syntax;
import com.shaforostoff.rechnungsplaner.pdf.InvoiceRenderer;
import com.shaforostoff.rechnungsplaner.pdf.PdfA3Packer;
import com.shaforostoff.rechnungsplaner.pdf.UnsupportedPdfException;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.PatternFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the files for an invoice and files them in the app-private archive.
 *
 * <p>The archive is the source of truth. "Save" copies out of it to a folder the user picked, and
 * "share" copies out of it into the share directory; both can be repeated years later without
 * regenerating anything, which matters because regenerating would pick up today's issuer details
 * rather than the ones the invoice was actually sent with.
 */
public class InvoiceWriter {

    /** What was produced, and anything the user should know about it. */
    public static class Result {
        public final List<File> files = new ArrayList<File>();
        public final List<Problem> problems = new ArrayList<Problem>();
        /**
         * True when the hybrid PDF could not be built and a separate XML was written instead. Not
         * a failure -- the invoice is still complete and valid -- but the user should be told,
         * because they now have two files to send rather than one.
         */
        public boolean hybridDegraded;
        /** Why the hybrid was not possible, for the log and the message shown to the user. */
        public String degradedReason;

        public boolean hasErrors() {
            for (Problem p : problems) {
                if (p.isError()) return true;
            }
            return false;
        }
    }

    private final Context ctx;
    private final SettingsStore settings;
    private final InvoiceDao invoices;

    public InvoiceWriter(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.settings = new SettingsStore(this.ctx);
        this.invoices = new InvoiceDao(this.ctx);
    }

    /**
     * The archive every invoice's files live under, one subdirectory per invoice.
     *
     * <p>Named here rather than at each caller because a backup has to find the whole tree, and a
     * directory spelled twice is a directory that eventually gets spelled two ways.
     */
    public static File archiveRoot(Context ctx) {
        return new File(ctx.getFilesDir(), "invoices");
    }

    /** The archive directory for one invoice. */
    public File archiveDir(long invoiceId) {
        File dir = new File(archiveRoot(ctx), Long.toString(invoiceId));
        dir.mkdirs();
        return dir;
    }

    /**
     * Renders, validates and writes every file the format calls for.
     *
     * <p>Validation problems are reported, never enforced. A draft for a club that has not yet sent
     * its address is exactly the thing the user wants to be able to produce.
     */
    public Result write(Issuer issuer, Customer customer, Invoice invoice, OutputFormat format)
            throws IOException {
        return write(issuer, customer, invoice, format, false);
    }

    /**
     * @param replaceExisting clears the invoice's previous files first. A reissue keeps the invoice
     *                        number, so a leftover PDF is a second document contradicting the
     *                        first under one number -- and since sharing lists the archive
     *                        directory, it would be attached alongside the corrected one.
     */
    public Result write(Issuer issuer, Customer customer, Invoice invoice, OutputFormat format,
                        boolean replaceExisting) throws IOException {
        Result result = new Result();
        EnInvoice en = InvoiceMapper.toEnInvoice(issuer, customer, invoice);
        Profile profile = format.profile == null ? Profile.XRECHNUNG_30 : format.profile;
        result.problems.addAll(EnValidator.validate(en, profile));

        String baseName = baseName(issuer, customer, invoice, format);
        File dir = archiveDir(invoice.id);
        if (replaceExisting) {
            File[] stale = dir.listFiles();
            if (stale != null) {
                for (File file : stale) file.delete();
            }
            invoices.clearFiles(invoice.id);
        }
        long now = System.currentTimeMillis();

        if (format.producesPdf) {
            byte[] pdf = new InvoiceRenderer(ctx, en.languageTag).render(en);
            byte[] xml = format.embedsXmlInPdf ? Syntax.CII.write(en, profile).getBytes("UTF-8")
                    : null;

            if (xml != null) {
                try {
                    pdf = PdfA3Packer.pack(pdf, xml, profile.conformanceLevel,
                            title(en), issuer.name, Dates.pdfTimestamp(now), Dates.iso8601(now));
                } catch (UnsupportedPdfException e) {
                    // Never ship a PDF we could not attach to correctly; write the XML alongside so
                    // the invoice is still machine-readable, and say so.
                    result.hybridDegraded = true;
                    result.degradedReason = e.getMessage();
                    record(result, dir, baseName + ".xml", xml, invoice.id, format, now);
                }
            }
            record(result, dir, baseName + ".pdf", pdf, invoice.id, format, now);
        }

        if (format.producesStandaloneXml && format.syntax != null) {
            byte[] xml = format.syntax.write(en, profile).getBytes("UTF-8");
            record(result, dir, baseName + ".xml", xml, invoice.id, format, now);
        }

        OutputFormat companion = format.companion();
        if (companion != null && companion.syntax != null) {
            Profile companionProfile = companion.profile == null ? profile : companion.profile;
            byte[] xml = companion.syntax.write(en, companionProfile).getBytes("UTF-8");
            record(result, dir, baseName + ".xml", xml, invoice.id, companion, now);
        }

        return result;
    }

    /** Expands the user's file-name pattern for this invoice. */
    public String baseName(Issuer issuer, Customer customer, Invoice invoice,
                           OutputFormat format) {
        String firstGigDate = invoice.deliveryDate != null ? invoice.deliveryDate
                : invoice.periodStart;
        PatternFormatter f = new PatternFormatter()
                .put(PatternFormatter.ISSUER_NAME, issuer.name)
                .put(PatternFormatter.CUSTOMER_NAME,
                        customer == null ? "" : customer.displayName())
                .put(PatternFormatter.PLACE, customer == null ? "" : customer.placeName)
                .put(PatternFormatter.CITY, customer == null ? "" : customer.city)
                .put(PatternFormatter.INVOICE_NO, invoice.number)
                .put(PatternFormatter.FORMAT, format.name().toLowerCase(java.util.Locale.US))
                .putDate(invoice.issueDate)
                .putGigDate(firstGigDate);
        String sequence = trailingDigits(invoice.number);
        if (sequence != null) f.putSequence(Integer.parseInt(sequence));
        return f.formatFileName(settings.getFileNamePattern());
    }

    private void record(Result result, File dir, String fileName, byte[] bytes, long invoiceId,
                        OutputFormat format, long now) throws IOException {
        File file = uniqueIn(dir, fileName);
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        result.files.add(file);

        InvoiceFile record = new InvoiceFile();
        record.invoiceId = invoiceId;
        record.format = format.name();
        record.fileName = file.getName();
        record.relPath = invoiceId + "/" + file.getName();
        record.createdAt = now;
        invoices.addFile(record);
    }

    /** Re-exporting must not silently overwrite the file that was actually sent. */
    private static File uniqueIn(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int n = 2; n < 1000; n++) {
            candidate = new File(dir, stem + " (" + n + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, stem + "-" + System.currentTimeMillis() + extension);
    }

    private static String title(EnInvoice en) {
        return "Rechnung " + (en.number == null ? "" : en.number);
    }

    /** The sequence part of "2026-001", so %seq tokens work in the file-name pattern too. */
    private static String trailingDigits(String number) {
        if (number == null) return null;
        int end = number.length();
        int start = end;
        while (start > 0 && Character.isDigit(number.charAt(start - 1))) start--;
        if (start == end) return null;
        String digits = number.substring(start, end);
        return digits.length() > 9 ? null : digits;
    }
}
