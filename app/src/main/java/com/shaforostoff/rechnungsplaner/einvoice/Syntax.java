package com.shaforostoff.rechnungsplaner.einvoice;

/** The two syntaxes EN 16931 permits. Everything this app writes is one of them. */
public enum Syntax {

    /** OASIS UBL 2.1. Preferred for bare XML: sevDesk's own XRechnung export uses it. */
    UBL("xml"),

    /** UN/CEFACT Cross Industry Invoice D16B. What goes inside a ZUGFeRD PDF. */
    CII("xml");

    public final String extension;

    Syntax(String extension) {
        this.extension = extension;
    }

    public String write(EnInvoice invoice, Profile profile) {
        return this == UBL ? new UblWriter(profile).write(invoice)
                : new CiiWriter(profile).write(invoice);
    }
}
