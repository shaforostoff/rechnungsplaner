package com.shaforostoff.rechnungsplaner.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Exercises the packer against real PDFs of both cross-reference flavours, produced by Ghostscript
 * at compatibility levels 1.4 and 1.7. Between them they cover a classic table, a cross-reference
 * stream, an object stream the packer must leave alone, and a catalog that already carries a
 * {@code /Metadata} key that has to be replaced rather than duplicated.
 *
 * <p>The packed results are also written to {@code build/packed} so external validators can be
 * pointed at them.
 */
public class PdfA3PackerTest {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final String XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rsm:CrossIndustryInvoice/>\n";

    private static File resource(String name) {
        File local = new File("src/test/resources/pdf/" + name);
        return local.isFile() ? local : new File("app/src/test/resources/pdf/" + name);
    }

    private static byte[] pack(String fixture) throws Exception {
        byte[] source = Files.readAllBytes(resource(fixture).toPath());
        byte[] packed = PdfA3Packer.pack(source, XML.getBytes("UTF-8"), "XRECHNUNG",
                "Rechnung 2026-001", "Nick Shaforostov",
                "D:20260905120000+02'00'", "2026-09-05T12:00:00+02:00");

        write(fixture, packed);
        return packed;
    }

    /** Keeps a copy where veraPDF, Mustang or pdfdetach can be pointed at it. */
    private static void write(String name, byte[] packed) throws IOException {
        File out = new File("build/packed");
        out.mkdirs();
        OutputStream os = new FileOutputStream(new File(out, name));
        try {
            os.write(packed);
        } finally {
            os.close();
        }
    }

    @Test
    public void anAttachmentWithUmlautsIsEmbeddedByteForByte() throws Exception {
        // The packer handles the PDF as an ISO-8859-1 string so that bytes round-trip, which is
        // only safe because that mapping is one-to-one over 0x00-0xFF. This is the test of that
        // claim: a UTF-8 invoice is multi-byte, and /Length has to be the byte count rather than
        // the character count or a reader truncates the attachment mid-address.
        byte[] xml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rsm:CrossIndustryInvoice>Stresemannstra\u00dfe, \u0141\u00f3d\u017a"
                + "</rsm:CrossIndustryInvoice>\n").getBytes("UTF-8");
        byte[] source = Files.readAllBytes(resource("classic-xref.pdf").toPath());
        byte[] packed = PdfA3Packer.pack(source, xml, "XRECHNUNG", "Rechnung 2026-001",
                "M\u00fcller & S\u00f6hne", "D:20260905120000+02'00'",
                "2026-09-05T12:00:00+02:00");

        write("umlauts.pdf", packed);

        String text = new String(packed, LATIN1);
        int dict = text.indexOf("/Type /EmbeddedFile");
        assertTrue("no embedded file", dict > 0);
        assertTrue("the length must count bytes, not characters",
                text.substring(dict, dict + 120).contains("/Length " + xml.length));

        // Read it back the way a consumer does: from the stream keyword, for /Length bytes.
        int start = text.indexOf("stream\n", dict) + "stream\n".length();
        byte[] extracted = new byte[xml.length];
        System.arraycopy(packed, start, extracted, 0, xml.length);
        assertArrayEquals("the attachment came back changed", xml, extracted);
        String decoded = new String(extracted, "UTF-8");
        assertTrue("and it still reads as the UTF-8 it claims to be",
                decoded.contains("Stresemannstra\u00dfe, \u0141\u00f3d\u017a"));
    }

    private static String catalogOf(byte[] pdf) throws Exception {
        String text = new String(pdf, LATIN1);
        PdfTrailer t = PdfTrailer.parse(text);
        long offset = t.offsets.get(t.rootNumber);
        return PdfSyntax.dictAt(text, text.indexOf("<<", (int) offset));
    }

    private void assertPackedCorrectly(String fixture) throws Exception {
        byte[] packed = pack(fixture);
        String text = new String(packed, LATIN1);

        // The result must still parse, and the trailer must now resolve the catalog to the
        // rewritten copy at the end of the file rather than the original one.
        PdfTrailer reparsed = PdfTrailer.parse(text);
        assertTrue("catalog should be found", reparsed.offsets.containsKey(reparsed.rootNumber));
        assertTrue("catalog should now live in the appended section",
                reparsed.offsets.get(reparsed.rootNumber) > Files.size(resource(fixture).toPath()));

        String catalog = catalogOf(packed);
        assertNotNull("/AF is what marks the attachment as the invoice itself",
                PdfSyntax.value(catalog, "/AF"));
        assertNotNull(PdfSyntax.value(catalog, "/Names"));
        assertNotNull(PdfSyntax.value(catalog, "/Metadata"));
        assertNotNull(PdfSyntax.value(catalog, "/OutputIntents"));
        assertTrue(PdfSyntax.value(catalog, "/Names").contains("/EmbeddedFiles"));
        assertTrue(PdfSyntax.value(catalog, "/Names").contains("factur-x.xml"));

        assertTrue("the XML must actually be in the file", text.contains("CrossIndustryInvoice"));
        assertTrue(text.contains("/AFRelationship /Alternative"));
        assertTrue(text.contains("/Subtype /text#2Fxml"));
        assertTrue(text.contains("<pdfaid:part>3</pdfaid:part>"));
        assertTrue(text.contains("<fx:ConformanceLevel>XRECHNUNG</fx:ConformanceLevel>"));
        assertTrue(text.contains("/S /GTS_PDFA1"));
        assertTrue("file must end with a usable trailer", text.trim().endsWith("%%EOF"));
    }

    @Test
    public void packsAPdfWithAClassicXrefTable() throws Exception {
        assertPackedCorrectly("classic-xref.pdf");
        String text = new String(pack("classic-xref.pdf"), LATIN1);
        assertTrue("a classic file gets a classic section appended",
                text.lastIndexOf("xref\n") > text.lastIndexOf("endobj"));
        assertTrue(text.contains("trailer\n<< /Size "));
    }

    @Test
    public void packsAPdfWithACrossReferenceStream() throws Exception {
        assertPackedCorrectly("stream-xref.pdf");
        String text = new String(pack("stream-xref.pdf"), LATIN1);
        // Appending a classic table to a 1.5+ file is what readers reject, so the flavour has to
        // be carried over.
        int lastXrefObj = text.lastIndexOf("/Type /XRef");
        assertTrue("a stream file gets a stream section appended", lastXrefObj > 0);
        assertTrue(lastXrefObj > text.indexOf("/Type /Metadata"));
    }

    @Test
    public void leavesEveryOriginalByteWhereItWas() throws Exception {
        // The whole point of an incremental update: offsets recorded in the original xref must
        // still be correct, so nothing before the appended section may move.
        byte[] source = Files.readAllBytes(resource("classic-xref.pdf").toPath());
        byte[] packed = pack("classic-xref.pdf");
        assertTrue(packed.length > source.length);
        for (int i = 8; i < source.length; i++) {
            assertEquals("byte " + i + " moved", source[i], packed[i]);
        }
    }

    @Test
    public void upgradesTheHeaderToTheVersionPdfA3IsDefinedAgainst() throws Exception {
        byte[] packed = pack("classic-xref.pdf");
        assertEquals("%PDF-1.7", new String(packed, 0, 8, LATIN1));
    }

    @Test
    public void replacesAnExistingMetadataKeyRatherThanDuplicatingIt() throws Exception {
        // Both fixtures already carry /Metadata; a second key would leave the reader a coin toss.
        String original = catalogOf(Files.readAllBytes(resource("classic-xref.pdf").toPath()));
        assertNotNull("fixture should already have /Metadata",
                PdfSyntax.value(original, "/Metadata"));

        String catalog = catalogOf(pack("classic-xref.pdf"));
        int first = catalog.indexOf("/Metadata");
        assertTrue(first >= 0);
        assertEquals("only one /Metadata key", -1, catalog.indexOf("/Metadata", first + 1));
    }

    @Test
    public void appendsTwiceWithoutCorruption() throws Exception {
        // Re-exporting an invoice must not compound damage, and it exercises the /Prev chain and
        // the merge paths for /AF and /Names that a fresh file never reaches.
        byte[] once = pack("classic-xref.pdf");
        byte[] twice = PdfA3Packer.pack(once, XML.getBytes("UTF-8"), "EN 16931",
                "Rechnung 2026-002", "Nick Shaforostov",
                "D:20260905120000+02'00'", "2026-09-05T12:00:00+02:00");

        String catalog = catalogOf(twice);
        assertTrue("both attachments should be associated",
                PdfSyntax.value(catalog, "/AF").split("0 R").length >= 2);
        assertEquals("still only one /Metadata", -1,
                catalog.indexOf("/Metadata", catalog.indexOf("/Metadata") + 1));
        assertTrue(new String(twice, LATIN1)
                .contains("<fx:ConformanceLevel>EN 16931</fx:ConformanceLevel>"));
    }

    @Test
    public void reportsRatherThanCorruptsWhenTheCatalogCannotBeRewritten() throws IOException {
        byte[] notAPdf = "hello, this is not a PDF at all".getBytes(LATIN1);
        try {
            PdfA3Packer.pack(notAPdf, XML.getBytes("UTF-8"), "XRECHNUNG", "t", "a", "D:2026", "x");
            org.junit.Assert.fail("should have refused");
        } catch (UnsupportedPdfException expected) {
            // The output pipeline degrades to a plain PDF plus a separate XML on this.
            assertNotNull(expected.getMessage());
        }
    }
}
