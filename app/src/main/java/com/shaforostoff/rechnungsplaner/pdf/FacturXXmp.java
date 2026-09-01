package com.shaforostoff.rechnungsplaner.pdf;

import java.io.UnsupportedEncodingException;

/**
 * The XMP metadata packet for a hybrid invoice PDF.
 *
 * <p>Two things live here that consumers actually look for. {@code pdfaid} declares PDF/A-3b, and
 * the {@code fx} block names the embedded XML so a reader knows the attachment is the invoice
 * rather than an incidental file. The {@code pdfaExtension} block is not decoration: PDF/A forbids
 * XMP properties from schemas it does not know about, so the Factur-X schema has to describe itself
 * inline or the file fails validation for the very metadata that makes it a ZUGFeRD invoice.
 */
final class FacturXXmp {

    static final String ATTACHMENT_NAME = "factur-x.xml";
    private static final String FX_NAMESPACE =
            "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";

    private FacturXXmp() {
    }

    /**
     * @param conformanceLevel the ZUGFeRD profile name, e.g. {@code XRECHNUNG} or {@code EN 16931}
     * @param timestamp        ISO-8601 with offset, e.g. {@code 2026-09-05T12:00:00+02:00}
     */
    static byte[] build(String title, String author, String conformanceLevel, String timestamp) {
        StringBuilder x = new StringBuilder(4096);
        x.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
        x.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
        x.append("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");

        x.append("    <rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n")
                .append("      <pdfaid:part>3</pdfaid:part>\n")
                .append("      <pdfaid:conformance>B</pdfaid:conformance>\n")
                .append("    </rdf:Description>\n");

        x.append("    <rdf:Description rdf:about=\"\"")
                .append(" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                .append("      <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">")
                .append(escape(title))
                .append("</rdf:li></rdf:Alt></dc:title>\n")
                .append("      <dc:creator><rdf:Seq><rdf:li>").append(escape(author))
                .append("</rdf:li></rdf:Seq></dc:creator>\n")
                .append("    </rdf:Description>\n");

        x.append("    <rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n")
                .append("      <pdf:Producer>Rechnungsplaner</pdf:Producer>\n")
                .append("    </rdf:Description>\n");

        x.append("    <rdf:Description rdf:about=\"\"")
                .append(" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n")
                .append("      <xmp:CreatorTool>Rechnungsplaner</xmp:CreatorTool>\n")
                .append("      <xmp:CreateDate>").append(escape(timestamp))
                .append("</xmp:CreateDate>\n")
                .append("      <xmp:ModifyDate>").append(escape(timestamp))
                .append("</xmp:ModifyDate>\n")
                .append("    </rdf:Description>\n");

        x.append("    <rdf:Description rdf:about=\"\"")
                .append(" xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"")
                .append(" xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"")
                .append(" xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n")
                .append("      <pdfaExtension:schemas>\n")
                .append("        <rdf:Bag>\n")
                .append("          <rdf:li rdf:parseType=\"Resource\">\n")
                .append("            <pdfaSchema:schema>Factur-X PDFA Extension Schema")
                .append("</pdfaSchema:schema>\n")
                .append("            <pdfaSchema:namespaceURI>").append(FX_NAMESPACE)
                .append("</pdfaSchema:namespaceURI>\n")
                .append("            <pdfaSchema:prefix>fx</pdfaSchema:prefix>\n")
                .append("            <pdfaSchema:property>\n")
                .append("              <rdf:Seq>\n");
        property(x, "DocumentFileName", "name of the embedded XML invoice file");
        property(x, "DocumentType", "INVOICE");
        property(x, "Version", "version of the Factur-X schema");
        property(x, "ConformanceLevel", "conformance level of the embedded invoice");
        x.append("              </rdf:Seq>\n")
                .append("            </pdfaSchema:property>\n")
                .append("          </rdf:li>\n")
                .append("        </rdf:Bag>\n")
                .append("      </pdfaExtension:schemas>\n")
                .append("    </rdf:Description>\n");

        x.append("    <rdf:Description rdf:about=\"\" xmlns:fx=\"").append(FX_NAMESPACE)
                .append("\">\n")
                .append("      <fx:DocumentType>INVOICE</fx:DocumentType>\n")
                .append("      <fx:DocumentFileName>").append(ATTACHMENT_NAME)
                .append("</fx:DocumentFileName>\n")
                .append("      <fx:Version>1.0</fx:Version>\n")
                .append("      <fx:ConformanceLevel>").append(escape(conformanceLevel))
                .append("</fx:ConformanceLevel>\n")
                .append("    </rdf:Description>\n");

        x.append("  </rdf:RDF>\n</x:xmpmeta>\n");
        // The trailing padding is conventional: it lets a tool rewrite the packet in place without
        // shifting every byte after it.
        for (int i = 0; i < 20; i++) {
            x.append("                                                                    \n");
        }
        x.append("<?xpacket end=\"w\"?>");

        try {
            return x.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always available", e);
        }
    }

    private static void property(StringBuilder x, String name, String description) {
        x.append("                <rdf:li rdf:parseType=\"Resource\">\n")
                .append("                  <pdfaProperty:name>").append(name)
                .append("</pdfaProperty:name>\n")
                .append("                  <pdfaProperty:valueType>Text")
                .append("</pdfaProperty:valueType>\n")
                .append("                  <pdfaProperty:category>external")
                .append("</pdfaProperty:category>\n")
                .append("                  <pdfaProperty:description>").append(escape(description))
                .append("</pdfaProperty:description>\n")
                .append("                </rdf:li>\n");
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') out.append("&amp;");
            else if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '"') out.append("&quot;");
            else if (c < 0x20 && c != '\n' && c != '\t') continue;
            else out.append(c);
        }
        return out.toString();
    }
}
