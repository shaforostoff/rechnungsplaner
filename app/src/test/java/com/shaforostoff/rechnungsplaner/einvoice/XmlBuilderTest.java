package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XmlBuilderTest {

    @Test
    public void writesNestedElements() {
        XmlBuilder x = new XmlBuilder();
        x.start("a").attr("xmlns:a", "urn:x");
        x.leaf("a:b", "text");
        x.end();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<a xmlns:a=\"urn:x\">\n"
                + "  <a:b>text</a:b>\n"
                + "</a>\n", x.toXml());
    }

    @Test
    public void skipsEmptyLeaves() {
        XmlBuilder x = new XmlBuilder();
        x.start("a");
        x.leaf("b", null);
        x.leaf("c", "  ");
        x.leaf("d", "v");
        x.end();
        String out = x.toXml();
        assertTrue(out.contains("<d>v</d>"));
        assertFalse(out.contains("<b"));
        assertFalse(out.contains("<c"));
    }

    @Test
    public void escapesMarkupInTextAndAttributes() {
        XmlBuilder x = new XmlBuilder();
        x.start("a").attr("t", "a\"b&c");
        x.text("x < y & z");
        x.end();
        String out = x.toXml();
        assertTrue(out.contains("t=\"a&quot;b&amp;c\""));
        assertTrue(out.contains("x &lt; y &amp; z"));
    }

    @Test
    public void dropsControlCharactersXmlCannotRepresent() {
        // A stray NUL or bell in a pasted club name must not produce a document no parser accepts.
        String dirty = "Club" + (char) 0x00 + "Muster" + (char) 0x07;
        XmlBuilder x = new XmlBuilder();
        x.start("a").text(dirty).end();
        assertTrue(x.toXml().contains("<a>ClubMuster</a>"));
    }

    @Test
    public void keepsSelfClosingForEmptyElements() {
        XmlBuilder x = new XmlBuilder();
        x.start("a").start("b").end().end();
        assertTrue(x.toXml().contains("<b/>"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnclosedElements() {
        XmlBuilder x = new XmlBuilder();
        x.start("a");
        x.toXml();
    }
}
