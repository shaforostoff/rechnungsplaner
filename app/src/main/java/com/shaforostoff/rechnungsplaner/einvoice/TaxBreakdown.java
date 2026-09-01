package com.shaforostoff.rechnungsplaner.einvoice;

/** BG-23 VAT breakdown: one entry per distinct (category, rate) pair across the lines. */
public class TaxBreakdown {

    /** BT-118. */
    public TaxCategory category;
    /** BT-119 as permille. */
    public int ratePermille;
    /** BT-116, the summed net of every line in this group. */
    public long basisCents;
    /** BT-117, {@code basis * rate} rounded half-up once, for the whole group. */
    public long taxAmountCents;
    /** BT-120, mandatory whenever the category is zero-rated. */
    public String exemptionReason;
    /** BT-121, a VATEX code. Optional; the free text alone satisfies the rules. */
    public String exemptionReasonCode;
}
