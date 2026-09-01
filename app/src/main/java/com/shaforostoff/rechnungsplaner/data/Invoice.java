package com.shaforostoff.rechnungsplaner.data;

import java.util.ArrayList;
import java.util.List;

/**
 * An issued invoice.
 *
 * <p>The party details are snapshotted as JSON at issue time rather than joined from the customer
 * row. Correcting a club's address next year must not silently rewrite an invoice already sent to
 * them and filed with the tax office. The language is snapshotted for the same reason, so
 * re-exporting an old invoice reproduces the document that was actually sent.
 */
public class Invoice {

    public long id = -1L;

    /** BT-1. Unique across the whole series. */
    public String number;
    public String issueDate;
    public String dueDate;

    public long customerId = -1L;
    public String currency = "EUR";

    public TaxMode taxMode = TaxMode.KLEINUNTERNEHMER;
    public int ratePermille;
    public String exemptionText;
    public String exemptionCode;

    public String buyerReference;
    public String language = "de";

    /** BT-72, set when the invoice covers a single gig. */
    public String deliveryDate;
    /** BG-14, set when it covers several. */
    public String periodStart;
    public String periodEnd;

    public String note;
    public String paymentTerms;

    public long lineTotalCents;
    public long taxBasisCents;
    public long taxTotalCents;
    public long grandTotalCents;
    public long prepaidCents;
    public long duePayableCents;

    public String issuerSnapshot;
    public String customerSnapshot;
    public long createdAt;

    public List<InvoiceLine> lines = new ArrayList<InvoiceLine>();
}
