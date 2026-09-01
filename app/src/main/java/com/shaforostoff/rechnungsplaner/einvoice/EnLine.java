package com.shaforostoff.rechnungsplaner.einvoice;

/** BG-25 invoice line: one DJ-set, or a travel-cost surcharge. */
public class EnLine {

    /** BT-126, the line identifier. Unique within the invoice. */
    public String id;
    /** BT-153, the item name. Mandatory. */
    public String name;
    /** BT-154, a longer description. */
    public String description;

    /** BT-129 as a milli-quantity, so {@code 1000} means one. */
    public long quantityMilli = 1000L;
    /** BT-130, UN/ECE Recommendation 20. {@code C62} for a piece, {@code HUR} for an hour. */
    public String unitCode = "C62";
    /** BT-146, the net price of one unit. */
    public long unitPriceCents;
    /** BT-131, the line net amount. Always {@code quantity * unitPrice}, rounded half-up. */
    public long netCents;

    /** BT-151. */
    public TaxCategory taxCategory = TaxCategory.S;
    /** BT-152 as permille, so {@code 190} means 19 %. */
    public int ratePermille;

    /**
     * BG-26 invoice line period, BT-134/BT-135.
     *
     * <p>EN 16931 has no per-line delivery date, so a multi-gig invoice carries each gig's date
     * here as a degenerate one-day period. A single-gig invoice uses the header BT-72 instead.
     */
    public String periodStart;
    /** BT-135. */
    public String periodEnd;

    /** Recomputes {@link #netCents} from quantity and unit price. */
    public EnLine recomputeNet() {
        netCents = Money.lineNet(quantityMilli, unitPriceCents);
        return this;
    }
}
