package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.einvoice.TaxCategory;

/**
 * The VAT situations a DJ invoicing from Germany runs into, each mapped to the EN 16931 category
 * and rate it implies.
 *
 * <p>The default reasons stay in German even on an English or Spanish invoice. They are citations
 * of German statute, not phrases to translate, and an auditor reading "small business according to
 * section 19" would have to translate it back. The renderer may append a gloss.
 */
public enum TaxMode {

    /** No VAT charged. The seller then needs a Steuernummer rather than a USt-IdNr. */
    KLEINUNTERNEHMER(TaxCategory.E, 0, "Kleinunternehmer gemäß § 19 UStG", null),

    /** The standard German rate. */
    STANDARD_19(TaxCategory.S, 190, null, null),

    /** Reduced rate for an artistic performance under § 12 Abs. 2 Nr. 7a UStG. */
    REDUCED_7(TaxCategory.S, 70, null, null),

    /** Gig abroad for a business customer: the customer accounts for the VAT. */
    REVERSE_CHARGE(TaxCategory.AE, 0,
            "Steuerschuldnerschaft des Leistungsempfängers / Reverse charge", "vatex-eu-ae"),

    /** Intra-community supply, rate 0, buyer VAT id mandatory. */
    INTRA_EU(TaxCategory.K, 0, "Innergemeinschaftliche Lieferung", "vatex-eu-ic");

    public final TaxCategory category;
    public final int ratePermille;
    /** German statutory wording, or null when the category needs no reason. */
    public final String defaultReason;
    /** VATEX code, or null when free text alone satisfies the rules. */
    public final String vatexCode;

    TaxMode(TaxCategory category, int ratePermille, String defaultReason, String vatexCode) {
        this.category = category;
        this.ratePermille = ratePermille;
        this.defaultReason = defaultReason;
        this.vatexCode = vatexCode;
    }

    public boolean chargesVat() {
        return ratePermille > 0;
    }

    /** True when the seller must supply a VAT identifier rather than just a tax number. */
    public boolean needsSellerVatId() {
        return this == REVERSE_CHARGE || this == INTRA_EU;
    }

    /** True when the buyer's VAT identifier becomes mandatory. */
    public boolean needsBuyerVatId() {
        return this == REVERSE_CHARGE || this == INTRA_EU;
    }

    public static TaxMode fromName(String name, TaxMode fallback) {
        if (name != null) {
            for (TaxMode m : values()) {
                if (m.name().equals(name)) return m;
            }
        }
        return fallback;
    }
}
