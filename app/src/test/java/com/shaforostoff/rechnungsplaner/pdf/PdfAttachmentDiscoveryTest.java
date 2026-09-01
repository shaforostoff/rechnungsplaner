package com.shaforostoff.rechnungsplaner.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Checks the hybrid PDF with a consumer that is not this codebase.
 *
 * <p>The packer's own parser accepting the packer's own output proves very little. What matters is
 * whether an unrelated implementation can walk {@code /Names -> /EmbeddedFiles} and pull the invoice
 * out, because that is exactly what Lexware Office, easybill and sevDesk do on import. Poppler's
 * {@code pdfdetach} is a good stand-in for all three.
 *
 * <p>Skipped where poppler is not installed, so the build stays green on a bare machine. It is not
 * a substitute for the desktop validation step (KoSIT, Mustang, veraPDF), which checks conformance
 * rather than mere discoverability.
 */
public class PdfAttachmentDiscoveryTest {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final String XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<rsm:CrossIndustryInvoice><ram:ID>2026-001</ram:ID></rsm:CrossIndustryInvoice>\n";

    private static File resource(String name) {
        File local = new File("src/test/resources/pdf/" + name);
        return local.isFile() ? local : new File("app/src/test/resources/pdf/" + name);
    }

    private static boolean hasPdfdetach() {
        try {
            return new ProcessBuilder("pdfdetach", "-v").redirectErrorStream(true).start()
                    .waitFor() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        p.waitFor();
        return new String(out.toByteArray(), LATIN1);
    }

    private void assertAttachmentIsDiscoverable(String fixture) throws Exception {
        assumeTrue("poppler's pdfdetach is not installed", hasPdfdetach());

        byte[] packed = PdfA3Packer.pack(Files.readAllBytes(resource(fixture).toPath()),
                XML.getBytes("UTF-8"), "XRECHNUNG", "Rechnung 2026-001", "Nick Shaforostov",
                "D:20260905120000+02'00'", "2026-09-05T12:00:00+02:00");

        File dir = new File("build/discovery");
        dir.mkdirs();
        File pdf = new File(dir, fixture);
        OutputStream os = new FileOutputStream(pdf);
        try {
            os.write(packed);
        } finally {
            os.close();
        }

        String listing = run("pdfdetach", "-list", pdf.getPath());
        assertTrue("poppler should see one attachment, got: " + listing,
                listing.contains("1 embedded files"));
        assertTrue("and it should be the invoice: " + listing, listing.contains("factur-x.xml"));

        File extracted = new File(dir, fixture + ".xml");
        run("pdfdetach", "-save", "1", "-o", extracted.getPath(), pdf.getPath());
        assertEquals("the payload must survive byte-intact", XML,
                new String(Files.readAllBytes(extracted.toPath()), "UTF-8"));
    }

    @Test
    public void anUnrelatedReaderFindsTheInvoiceInAClassicXrefFile() throws Exception {
        assertAttachmentIsDiscoverable("classic-xref.pdf");
    }

    @Test
    public void anUnrelatedReaderFindsTheInvoiceInAnXrefStreamFile() throws Exception {
        assertAttachmentIsDiscoverable("stream-xref.pdf");
    }
}
