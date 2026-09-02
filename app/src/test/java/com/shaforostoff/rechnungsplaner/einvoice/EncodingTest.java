package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * That a party's real name and address survive being written and read back.
 *
 * <p>The golden fixtures are deliberately ASCII -- "Musterstrasse", "Clubstrasse" -- so that they
 * stay readable in a diff, which means nothing there exercises an umlaut. A German invoice is full
 * of them, and the failure mode is not a crash: a mis-declared encoding produces a document that
 * parses cleanly and says the wrong street.
 *
 * <p>These tests parse the emitted bytes with a real parser rather than inspecting the string, so
 * the declared encoding has to agree with the bytes for them to pass. Comparing the string with
 * itself would prove nothing.
 */
public class EncodingTest {

    /** Two-byte UTF-8, and the character the question always comes up about. */
    private static final String STREET = "Stresemannstraße 27a";
    /** An ampersand next to umlauts: escaping and encoding in one value. */
    private static final String SELLER = "Müller & Söhne Tonträger";
    /** Outside Latin-1 entirely, so a Latin-1 fallback would replace these with question marks. */
    private static final String CITY = "Łódź";
    /** Beyond the basic plane: one character, two Java chars, four UTF-8 bytes. */
    private static final String NOTE = "Sylwester 🎧 2026";

    @Test
    public void ublCarriesGermanAddressesIntact() throws Exception {
        assertRoundTrips(Syntax.UBL);
    }

    @Test
    public void ciiCarriesGermanAddressesIntact() throws Exception {
        assertRoundTrips(Syntax.CII);
    }

    @Test
    public void theDeclaredEncodingIsTheOneActuallyUsed() throws Exception {
        byte[] bytes = write(Syntax.UBL);
        String head = new String(bytes, 0, 60, "UTF-8");
        assertTrue(head, head.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));

        // The point of the declaration: these bytes are not the characters. If the writer ever
        // emitted one byte per char, this would hold by accident and the assertion would not.
        int marker = indexOf(bytes, "Stresemannstra".getBytes("UTF-8"));
        assertTrue("street not found in the bytes", marker >= 0);
        assertEquals("UTF-8 encodes eszett in two bytes",
                0xC3, bytes[marker + "Stresemannstra".length()] & 0xFF);
        assertEquals(0x9F, bytes[marker + "Stresemannstra".length() + 1] & 0xFF);
    }

    @Test
    public void anAmpersandIsEscapedRatherThanEncoded() throws Exception {
        // Escaping and non-ASCII are separate jobs and must not interfere: the & becomes an
        // entity, the umlauts stay as themselves.
        String text = new String(write(Syntax.UBL), "UTF-8");
        assertTrue(text, text.contains("Müller &amp; Söhne"));
    }

    private void assertRoundTrips(Syntax syntax) throws Exception {
        List<String> texts = allText(parse(write(syntax)));
        for (String expected : new String[]{STREET, SELLER, CITY, NOTE}) {
            assertTrue(syntax + " lost " + expected, texts.contains(expected));
        }
    }

    private static byte[] write(Syntax syntax) throws UnsupportedEncodingException {
        EnInvoice inv = Fixtures.kleinunternehmer();
        inv.seller.name = SELLER;
        inv.seller.line1 = STREET;
        inv.buyer.line1 = STREET;
        inv.buyer.city = CITY;
        inv.note = NOTE;
        // The same path InvoiceWriter takes.
        return syntax.write(inv, Profile.XRECHNUNG_30).getBytes("UTF-8");
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // No encoding is passed: the parser has to work it out from the declaration, which is
        // exactly what a receiving system does.
        return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
    }

    private static List<String> allText(Document doc) {
        List<String> out = new ArrayList<String>();
        collect(doc.getDocumentElement(), out);
        return out;
    }

    private static void collect(Node node, List<String> out) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String value = child.getNodeValue().trim();
                if (!value.isEmpty()) out.add(value);
            } else {
                collect(child, out);
            }
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
