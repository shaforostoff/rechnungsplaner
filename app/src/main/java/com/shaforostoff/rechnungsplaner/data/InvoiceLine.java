package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.einvoice.TaxCategory;

/** One line of an invoice: usually a gig, sometimes travel costs or a manual entry. */
public class InvoiceLine {

    public long id = -1L;
    public long invoiceId = -1L;
    /** The gig this line bills, or -1 for a manual line. */
    public long gigId = -1L;

    public int lineNo;
    public String name;
    public String description;

    public long quantityMilli = 1000L;
    /** UN/ECE Rec 20: {@code C62} for a piece, {@code HUR} when billed by the hour. */
    public String unitCode = "C62";
    public long unitPriceCents;
    public long netCents;

    public TaxCategory taxCategory = TaxCategory.S;
    public int ratePermille;

    /** BG-26, the gig's date as a one-day period on a multi-gig invoice. */
    public String periodStart;
    public String periodEnd;
}
