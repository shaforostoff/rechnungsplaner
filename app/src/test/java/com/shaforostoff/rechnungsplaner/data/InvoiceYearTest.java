package com.shaforostoff.rechnungsplaner.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which year an invoice counts in, which is not always the year written on it. */
public class InvoiceYearTest {

    private static Invoice invoice() {
        Invoice i = new Invoice();
        i.number = "2027-001";
        i.issueDate = "2027-01-02";
        return i;
    }

    @Test
    public void theWorkDecidesTheYearRatherThanTheInvoiceDate() {
        // The case the whole field exists for: played between Christmas and New Year, invoiced in
        // January. The work is 2026 however late the paperwork was.
        Invoice i = invoice();
        i.deliveryDate = "2026-12-28";

        assertEquals(2026, i.serviceYear());
        assertEquals(2026, i.taxYear());
    }

    @Test
    public void severalSetsUseTheStartOfThePeriod() {
        Invoice i = invoice();
        i.periodStart = "2026-12-28";
        i.periodEnd = "2027-01-01";

        assertEquals(2026, i.serviceYear());
    }

    @Test
    public void aDeliveryDateOutranksAPeriod() {
        Invoice i = invoice();
        i.deliveryDate = "2026-11-14";
        i.periodStart = "2026-12-28";

        assertEquals(2026, i.serviceYear());
    }

    @Test
    public void theIssueDateIsTheLastResort() {
        assertEquals(2027, invoice().serviceYear());
    }

    @Test
    public void anInvoiceWithNoReadableDateHasNoYear() {
        Invoice i = new Invoice();
        i.issueDate = null;
        assertEquals(0, i.serviceYear());
        assertEquals(0, i.taxYear());
    }

    @Test
    public void aPaymentYearOverridesTheWork() {
        Invoice i = invoice();
        i.deliveryDate = "2026-12-28";
        i.paidYear = 2027;

        assertEquals("the work does not move", 2026, i.serviceYear());
        assertEquals("but the year it is declared in does", 2027, i.taxYear());
    }

    @Test
    public void zeroMeansDeriveIt() {
        // Never moved and moved back have to be the same state afterwards, so that a service date
        // corrected later takes its year with it instead of being pinned to the old one.
        Invoice i = invoice();
        i.deliveryDate = "2026-12-28";
        i.paidYear = 0;

        assertEquals(2026, i.taxYear());

        i.deliveryDate = "2025-12-28";
        assertEquals(2025, i.taxYear());
    }

    @Test
    public void aPinnedYearStaysPutWhenTheWorkIsCorrected() {
        Invoice i = invoice();
        i.deliveryDate = "2026-12-28";
        i.paidYear = 2027;

        i.deliveryDate = "2025-12-28";
        assertEquals("the money still arrived when it arrived", 2027, i.taxYear());
    }
}
