package com.shaforostoff.rechnungsplaner.einvoice;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A small namespace-agnostic XML writer: elements are written with their prefix already attached,
 * and the prefixes themselves are declared as ordinary attributes on the root.
 *
 * <p>This exists instead of {@code android.util.Xml} so that the writers stay pure Java and can be
 * covered by golden-file tests on the JVM, where no Android framework is present.
 */
public final class XmlBuilder {

    private static final class Frame {
        final String name;
        boolean hasText;
        boolean hasChild;

        Frame(String name) {
            this.name = name;
        }
    }

    private final StringBuilder sb = new StringBuilder(8192);
    private final Deque<Frame> stack = new ArrayDeque<Frame>();
    private boolean tagOpen;

    public XmlBuilder() {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    }

    /** Opens an element. Attributes may be added until the next structural call. */
    public XmlBuilder start(String qname) {
        finishOpenTag();
        Frame parent = stack.peek();
        if (parent != null) {
            parent.hasChild = true;
            newline();
        }
        sb.append('<').append(qname);
        stack.push(new Frame(qname));
        tagOpen = true;
        return this;
    }

    /** Adds an attribute to the element just opened. Skipped when the value is empty. */
    public XmlBuilder attr(String name, String value) {
        if (!tagOpen) throw new IllegalStateException("attr() after the start tag was closed");
        if (Str.notEmpty(value)) {
            sb.append(' ').append(name).append("=\"").append(escape(value, true)).append('"');
        }
        return this;
    }

    /** Writes character data into the current element. */
    public XmlBuilder text(String value) {
        finishOpenTag();
        Frame f = stack.peek();
        if (f != null) f.hasText = true;
        sb.append(escape(value, false));
        return this;
    }

    /** Closes the current element. */
    public XmlBuilder end() {
        Frame f = stack.pop();
        if (tagOpen && !f.hasText && !f.hasChild) {
            sb.append("/>");
            tagOpen = false;
            return this;
        }
        finishOpenTag();
        if (f.hasChild && !f.hasText) newline();
        sb.append("</").append(f.name).append('>');
        return this;
    }

    /** Element with text content, written on one line. Nothing is written for an empty value. */
    public XmlBuilder leaf(String qname, String value) {
        if (Str.isEmpty(value)) return this;
        return start(qname).text(value.trim()).end();
    }

    /** Element with text content and one attribute; written even when the attribute is empty. */
    public XmlBuilder leaf(String qname, String value, String attrName, String attrValue) {
        if (Str.isEmpty(value)) return this;
        return start(qname).attr(attrName, attrValue).text(value.trim()).end();
    }

    public String toXml() {
        if (!stack.isEmpty()) throw new IllegalStateException("unclosed element " + stack.peek().name);
        return sb.append('\n').toString();
    }

    private void finishOpenTag() {
        if (tagOpen) {
            sb.append('>');
            tagOpen = false;
        }
    }

    private void newline() {
        sb.append('\n');
        for (int i = stack.size(); i > 0; i--) sb.append("  ");
    }

    private static String escape(String s, boolean attribute) {
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String repl = null;
            if (c == '&') repl = "&amp;";
            else if (c == '<') repl = "&lt;";
            else if (c == '>') repl = "&gt;";
            else if (attribute && c == '"') repl = "&quot;";
            else if (attribute && (c == '\n' || c == '\r' || c == '\t')) {
                repl = "&#" + (int) c + ";";
            } else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                // Not representable in XML 1.0 at all; drop it rather than emit a broken document.
                repl = "";
            }
            if (repl == null) {
                if (out != null) out.append(c);
            } else {
                if (out == null) out = new StringBuilder(s.length() + 16).append(s, 0, i);
                out.append(repl);
            }
        }
        return out == null ? s : out.toString();
    }
}
