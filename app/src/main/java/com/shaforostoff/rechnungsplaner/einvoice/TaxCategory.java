package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * BT-118/BT-151 VAT category codes (UNTDID 5305) in the subset a German DJ actually needs.
 *
 * <p>{@link #needsExemptionReason} drives BR-E-10, BR-AE-10, BR-IC-10 and friends: a zero-rated
 * category without a stated reason is rejected by the validators.
 */
public enum TaxCategory {

    /** Standard rate — 19 % or the reduced 7 % of section 12(2)7a UStG. */
    S("S", false),
    /** Zero rated goods. */
    Z("Z", true),
    /** Exempt from tax — this is what section 19 UStG (Kleinunternehmer) uses. */
    E("E", true),
    /** VAT reverse charge. */
    AE("AE", true),
    /** VAT exempt for intra-community supply of goods. */
    K("K", true),
    /** Free export item, VAT not charged. */
    G("G", true),
    /** Services outside the scope of tax. */
    O("O", true);

    public final String code;
    public final boolean needsExemptionReason;

    TaxCategory(String code, boolean needsExemptionReason) {
        this.code = code;
        this.needsExemptionReason = needsExemptionReason;
    }

    /** A category that must carry rate 0; emitting anything else trips BR-E-05 and friends. */
    public boolean isZeroRated() {
        return this != S;
    }

    public static TaxCategory fromCode(String code, TaxCategory fallback) {
        if (code != null) {
            for (TaxCategory c : values()) {
                if (c.code.equals(code)) return c;
            }
        }
        return fallback;
    }
}
