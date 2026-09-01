package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.einvoice.Profile;
import com.shaforostoff.rechnungsplaner.einvoice.Syntax;

/**
 * What the user gets when they tap "create".
 *
 * <p>The default is the hybrid ZUGFeRD PDF: one file that a human can read and that Lexware Office,
 * easybill and sevDesk all parse. {@link #MAX_COMPAT} additionally writes a bare XRechnung UBL
 * alongside it, for the case where an importer refuses the PDF and you want something else to hand
 * without regenerating anything.
 */
public enum OutputFormat {

    /** Hybrid PDF/A-3 carrying XRechnung CII. The default. */
    ZUGFERD_XRECHNUNG(Profile.XRECHNUNG_30, Syntax.CII, true, true, false),

    /** Hybrid PDF/A-3 carrying plain EN 16931 CII: fewer rules, widest acceptance. */
    ZUGFERD_EN16931(Profile.EN16931, Syntax.CII, true, true, false),

    /** Bare XRechnung 3.0 as UBL. */
    XRECHNUNG_UBL(Profile.XRECHNUNG_30, Syntax.UBL, false, false, true),

    /** Bare XRechnung 3.0 as CII. */
    XRECHNUNG_CII(Profile.XRECHNUNG_30, Syntax.CII, false, false, true),

    /** Bare XRechnung 2.3 as UBL, which easybill still lists among the formats it accepts. */
    XRECHNUNG_23_UBL(Profile.XRECHNUNG_23, Syntax.UBL, false, false, true),

    /** A plain readable PDF with no embedded XML. Not an e-invoice. */
    PDF_ONLY(null, null, true, false, false),

    /** The hybrid PDF plus a separate XRechnung UBL file, shared together. */
    MAX_COMPAT(Profile.XRECHNUNG_30, Syntax.CII, true, true, false);

    public final Profile profile;
    public final Syntax syntax;
    public final boolean producesPdf;
    public final boolean embedsXmlInPdf;
    public final boolean producesStandaloneXml;

    OutputFormat(Profile profile, Syntax syntax, boolean producesPdf, boolean embedsXmlInPdf,
                 boolean producesStandaloneXml) {
        this.profile = profile;
        this.syntax = syntax;
        this.producesPdf = producesPdf;
        this.embedsXmlInPdf = embedsXmlInPdf;
        this.producesStandaloneXml = producesStandaloneXml;
    }

    /** The extra file {@link #MAX_COMPAT} writes next to its PDF, or null for every other format. */
    public OutputFormat companion() {
        return this == MAX_COMPAT ? XRECHNUNG_UBL : null;
    }

    public static OutputFormat fromName(String name, OutputFormat fallback) {
        if (name != null) {
            for (OutputFormat f : values()) {
                if (f.name().equals(name)) return f;
            }
        }
        return fallback;
    }
}
