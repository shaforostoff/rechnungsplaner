package com.shaforostoff.rechnungsplaner.pdf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns a plain PDF into a ZUGFeRD/Factur-X hybrid by appending an incremental update.
 *
 * <p>{@code android.graphics.pdf.PdfDocument} can draw a page but cannot attach a file, write XMP
 * metadata or declare an output intent, and no dependency-free library on Android can either. So
 * the visual PDF is rendered by the framework and this class appends what the standard additionally
 * requires: the XML as an embedded file, an {@code /AF} association so a reader knows the
 * attachment <em>is</em> the invoice, the Factur-X XMP packet, and an sRGB output intent.
 *
 * <p>An incremental update rather than a rewrite. Every original byte stays where it was, so
 * nothing depends on this code understanding the parts of the document it did not write -- fonts,
 * content streams, object streams. Only the catalog is reissued, and only by splicing keys into the
 * dictionary text.
 *
 * <p>Pure Java over byte arrays with no {@code android.*} imports, so it is unit-testable on the
 * JVM against real PDFs of both cross-reference flavours.
 */
public final class PdfA3Packer {

    /** What a reader shows for the attachment. */
    private static final String DESCRIPTION = "Factur-X/ZUGFeRD invoice";

    private PdfA3Packer() {
    }

    /**
     * @param pdf              the rendered document
     * @param invoiceXml       the CII payload, attached as {@code factur-x.xml}
     * @param conformanceLevel ZUGFeRD profile name for the XMP, e.g. {@code XRECHNUNG}
     * @param pdfDate          PDF-syntax timestamp, {@code D:YYYYMMDDHHmmSS+HH'mm'}
     * @param xmpDate          ISO-8601 timestamp for the XMP packet
     * @throws PdfTrailer.UnsupportedPdfException when the document is shaped in a way that cannot
     *                                            be updated safely; the caller should fall back to
     *                                            a plain PDF plus a separate XML file
     */
    public static byte[] pack(byte[] pdf, byte[] invoiceXml, String conformanceLevel,
                              String title, String author, String pdfDate, String xmpDate)
            throws PdfTrailer.UnsupportedPdfException {

        String text = upgradeHeader(PdfSyntax.toText(pdf));
        PdfTrailer trailer = PdfTrailer.parse(text);

        String catalog = readCatalog(text, trailer);
        boolean needsOutputIntent = PdfSyntax.value(catalog, "/OutputIntents") == null;

        int next = Math.max(trailer.size, highestObjectNumber(trailer) + 1);
        int embeddedFileNo = next++;
        int filespecNo = next++;
        int metadataNo = next++;
        int iccNo = needsOutputIntent ? next++ : -1;
        int outputIntentNo = needsOutputIntent ? next++ : -1;

        String newCatalog = rewriteCatalog(catalog, filespecNo, metadataNo, outputIntentNo);

        byte[] xmp = FacturXXmp.build(title, author, conformanceLevel, xmpDate);

        StringBuilder appended = new StringBuilder(invoiceXml.length + xmp.length + 8192);
        List<int[]> written = new ArrayList<int[]>();
        long base = text.length();

        written.add(new int[]{embeddedFileNo, (int) (base + appended.length())});
        appended.append(embeddedFileNo).append(" 0 obj\n")
                .append("<< /Type /EmbeddedFile /Subtype /text#2Fxml /Length ")
                .append(invoiceXml.length)
                .append(" /Params << /Size ").append(invoiceXml.length)
                .append(" /ModDate (").append(pdfDate).append(") >> >>\nstream\n")
                .append(PdfSyntax.toText(invoiceXml))
                .append("\nendstream\nendobj\n");

        written.add(new int[]{filespecNo, (int) (base + appended.length())});
        appended.append(filespecNo).append(" 0 obj\n")
                .append("<< /Type /Filespec /F (").append(FacturXXmp.ATTACHMENT_NAME)
                .append(") /UF (").append(FacturXXmp.ATTACHMENT_NAME)
                .append(") /AFRelationship /Alternative /Desc (").append(DESCRIPTION)
                .append(") /EF << /F ").append(embeddedFileNo).append(" 0 R /UF ")
                .append(embeddedFileNo).append(" 0 R >> >>\nendobj\n");

        written.add(new int[]{metadataNo, (int) (base + appended.length())});
        // PDF/A forbids filtering the document metadata stream, so this one stays uncompressed.
        appended.append(metadataNo).append(" 0 obj\n")
                .append("<< /Type /Metadata /Subtype /XML /Length ").append(xmp.length)
                .append(" >>\nstream\n")
                .append(PdfSyntax.toText(xmp))
                .append("\nendstream\nendobj\n");

        if (needsOutputIntent) {
            byte[] icc = SrgbIcc.profile();
            written.add(new int[]{iccNo, (int) (base + appended.length())});
            appended.append(iccNo).append(" 0 obj\n")
                    .append("<< /N 3 /Length ").append(icc.length).append(" >>\nstream\n")
                    .append(PdfSyntax.toText(icc))
                    .append("\nendstream\nendobj\n");

            written.add(new int[]{outputIntentNo, (int) (base + appended.length())});
            appended.append(outputIntentNo).append(" 0 obj\n")
                    .append("<< /Type /OutputIntent /S /GTS_PDFA1")
                    .append(" /OutputConditionIdentifier (sRGB)")
                    .append(" /Info (sRGB approximation)")
                    .append(" /DestOutputProfile ").append(iccNo).append(" 0 R >>\nendobj\n");
        }

        written.add(new int[]{trailer.rootNumber, (int) (base + appended.length())});
        appended.append(trailer.rootNumber).append(" 0 obj\n").append(newCatalog)
                .append("\nendobj\n");

        Collections.sort(written, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[0] - b[0];
            }
        });

        String tail = trailer.usesXrefStream
                ? xrefStreamSection(written, next, trailer, base + appended.length())
                : classicXrefSection(written, next, trailer, base + appended.length());

        ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + appended.length() + 2048);
        byte[] head = PdfSyntax.toBytes(text);
        out.write(head, 0, head.length);
        byte[] body = PdfSyntax.toBytes(appended.toString());
        out.write(body, 0, body.length);
        byte[] end = PdfSyntax.toBytes(tail);
        out.write(end, 0, end.length);
        return out.toByteArray();
    }

    /**
     * PDF/A-3 is defined against PDF 1.7. The header is a fixed-width field, so bumping 1.4 to 1.7
     * is a same-length edit and every byte offset in the file stays valid.
     */
    private static String upgradeHeader(String text) {
        if (text.length() < 8 || !text.startsWith("%PDF-1.")) return text;
        char minor = text.charAt(7);
        if (minor >= '0' && minor < '7') {
            return text.substring(0, 7) + '7' + text.substring(8);
        }
        return text;
    }

    private static String readCatalog(String text, PdfTrailer trailer)
            throws PdfTrailer.UnsupportedPdfException {
        long offset = trailer.offsets.get(trailer.rootNumber);
        if (offset < 0 || offset >= text.length()) {
            throw new PdfTrailer.UnsupportedPdfException("catalog offset out of range");
        }
        int dictAt = text.indexOf("<<", (int) offset);
        if (dictAt < 0) throw new PdfTrailer.UnsupportedPdfException("catalog has no dictionary");
        // Guard against the offset being stale and pointing at some earlier object.
        int objAt = text.indexOf("obj", (int) offset);
        if (objAt < 0 || objAt > dictAt) {
            throw new PdfTrailer.UnsupportedPdfException("catalog offset does not name an object");
        }
        return PdfSyntax.dictAt(text, dictAt);
    }

    private static String rewriteCatalog(String catalog, int filespecNo, int metadataNo,
                                         int outputIntentNo)
            throws PdfTrailer.UnsupportedPdfException {

        String names = PdfSyntax.value(catalog, "/Names");
        if (names != null && !names.startsWith("<<")) {
            // An indirect name tree would have to be rewritten in place, which this incremental
            // approach cannot do. Degrade rather than corrupt.
            throw new PdfTrailer.UnsupportedPdfException("/Names is an indirect reference");
        }
        String af = PdfSyntax.value(catalog, "/AF");
        if (af != null && !af.startsWith("[")) {
            throw new PdfTrailer.UnsupportedPdfException("/AF is an indirect reference");
        }

        String embeddedFiles = "<< /Names [ (" + FacturXXmp.ATTACHMENT_NAME + ") "
                + filespecNo + " 0 R ] >>";
        String newNames;
        if (names == null) {
            newNames = "<< /EmbeddedFiles " + embeddedFiles + " >>";
        } else {
            newNames = PdfSyntax.withEntries(PdfSyntax.withoutKey(names, "/EmbeddedFiles"),
                    " /EmbeddedFiles " + embeddedFiles + " ");
        }

        String newAf;
        if (af == null) {
            newAf = "[ " + filespecNo + " 0 R ]";
        } else {
            newAf = af.substring(0, af.lastIndexOf(']')) + " " + filespecNo + " 0 R ]";
        }

        String out = catalog;
        out = PdfSyntax.withoutKey(out, "/Names");
        out = PdfSyntax.withoutKey(out, "/AF");
        out = PdfSyntax.withoutKey(out, "/Metadata");

        StringBuilder entries = new StringBuilder(256);
        entries.append(" /Names ").append(newNames)
                .append(" /AF ").append(newAf)
                .append(" /Metadata ").append(metadataNo).append(" 0 R");
        if (outputIntentNo > 0) {
            entries.append(" /OutputIntents [ ").append(outputIntentNo).append(" 0 R ]");
        }
        entries.append(' ');
        return PdfSyntax.withEntries(out, entries.toString());
    }

    private static String classicXrefSection(List<int[]> written, int size, PdfTrailer trailer,
                                             long xrefOffset) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("xref\n");
        int i = 0;
        while (i < written.size()) {
            // Entries must be grouped into runs of consecutive object numbers.
            int runStart = i;
            while (i + 1 < written.size() && written.get(i + 1)[0] == written.get(i)[0] + 1) i++;
            int count = i - runStart + 1;
            sb.append(written.get(runStart)[0]).append(' ').append(count).append('\n');
            for (int n = runStart; n <= i; n++) {
                sb.append(pad10(written.get(n)[1])).append(" 00000 n \n");
            }
            i++;
        }
        sb.append("trailer\n<< /Size ").append(size)
                .append(" /Root ").append(trailer.rootNumber).append(" 0 R");
        if (trailer.infoRef != null) sb.append(" /Info ").append(trailer.infoRef);
        sb.append(" /ID ").append(trailer.idArray != null ? trailer.idArray : syntheticId(size));
        sb.append(" /Prev ").append(trailer.startxref).append(" >>\n");
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        return sb.toString();
    }

    private static String xrefStreamSection(List<int[]> written, int size, PdfTrailer trailer,
                                            long xrefOffset) {
        int selfNo = size;
        List<int[]> all = new ArrayList<int[]>(written);
        all.add(new int[]{selfNo, (int) xrefOffset});
        Collections.sort(all, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[0] - b[0];
            }
        });

        StringBuilder index = new StringBuilder(64);
        StringBuilder rows = new StringBuilder(all.size() * 7);
        int i = 0;
        while (i < all.size()) {
            int runStart = i;
            while (i + 1 < all.size() && all.get(i + 1)[0] == all.get(i)[0] + 1) i++;
            index.append(all.get(runStart)[0]).append(' ').append(i - runStart + 1).append(' ');
            for (int n = runStart; n <= i; n++) {
                int offset = all.get(n)[1];
                rows.append((char) 1);
                rows.append((char) ((offset >> 24) & 0xFF)).append((char) ((offset >> 16) & 0xFF))
                        .append((char) ((offset >> 8) & 0xFF)).append((char) (offset & 0xFF));
                rows.append((char) 0).append((char) 0);
            }
            i++;
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append(selfNo).append(" 0 obj\n<< /Type /XRef /Size ").append(size + 1)
                .append(" /Root ").append(trailer.rootNumber).append(" 0 R");
        if (trailer.infoRef != null) sb.append(" /Info ").append(trailer.infoRef);
        sb.append(" /ID ").append(trailer.idArray != null ? trailer.idArray : syntheticId(size));
        sb.append(" /Prev ").append(trailer.startxref)
                .append(" /Index [ ").append(index.toString().trim()).append(" ]")
                .append(" /W [ 1 4 2 ] /Length ").append(rows.length()).append(" >>\nstream\n")
                .append(rows).append("\nendstream\nendobj\n");
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        return sb.toString();
    }

    /** PDF/A requires a trailer /ID; derive a stable one when the original had none. */
    private static String syntheticId(int seed) {
        StringBuilder hex = new StringBuilder(32);
        long h = 0x9E3779B97F4A7C15L ^ seed;
        for (int i = 0; i < 16; i++) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            int b = (int) ((h >>> 33) & 0xFF);
            hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }
        String s = hex.toString().toUpperCase(java.util.Locale.US);
        return "[<" + s + "><" + s + ">]";
    }

    private static int highestObjectNumber(PdfTrailer trailer) {
        int max = 0;
        for (Integer n : trailer.offsets.keySet()) max = Math.max(max, n);
        return max;
    }

    private static String pad10(int value) {
        String s = Integer.toString(value);
        StringBuilder sb = new StringBuilder(10);
        for (int i = s.length(); i < 10; i++) sb.append('0');
        return sb.append(s).toString();
    }
}
