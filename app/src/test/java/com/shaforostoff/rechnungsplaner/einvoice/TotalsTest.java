package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TotalsTest {

    private static EnLine line(long priceCents, TaxCategory cat, int permille) {
        EnLine l = new EnLine();
        l.name = "DJ-Set";
        l.unitPriceCents = priceCents;
        l.taxCategory = cat;
        l.ratePermille = permille;
        return l;
    }

    @Test
    public void groupsLinesByCategoryAndRate() {
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(35000L, TaxCategory.S, 190));
        inv.addLine(line(42500L, TaxCategory.S, 190));
        inv.addLine(line(10000L, TaxCategory.S, 70));
        Totals.compute(inv);

        assertEquals(2, inv.taxBreakdowns.size());
        assertEquals(77500L, inv.taxBreakdowns.get(0).basisCents);
        assertEquals(190, inv.taxBreakdowns.get(0).ratePermille);
        assertEquals(10000L, inv.taxBreakdowns.get(1).basisCents);
    }

    @Test
    public void roundsPerBreakdownNotPerLine() {
        // Three lines of 3.33 at 19 %: rounded per line that is 3 x 0.63 = 1.89, but the group
        // basis of 9.99 yields 1.8981, which rounds to 1.90. BR-CO-17 wants the latter, and every
        // target importer recomputes it the same way, so a per-line sum would be rejected.
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(333L, TaxCategory.S, 190));
        inv.addLine(line(333L, TaxCategory.S, 190));
        inv.addLine(line(333L, TaxCategory.S, 190));
        Totals.compute(inv);

        assertEquals(999L, inv.taxBreakdowns.get(0).basisCents);
        assertEquals(190L, inv.taxBreakdowns.get(0).taxAmountCents);
        assertEquals(190L, inv.taxTotalCents);
    }

    @Test
    public void computesDocumentTotals() {
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(35000L, TaxCategory.S, 190));
        inv.addLine(line(42500L, TaxCategory.S, 190));
        Totals.compute(inv);

        assertEquals(77500L, inv.lineTotalCents);
        assertEquals(77500L, inv.taxBasisCents);
        assertEquals(14725L, inv.taxTotalCents);
        assertEquals(92225L, inv.grandTotalCents);
        assertEquals(92225L, inv.duePayableCents);
    }

    @Test
    public void subtractsPrepaidFromAmountDue() {
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(35000L, TaxCategory.S, 190));
        inv.prepaidCents = 10000L;
        Totals.compute(inv);

        assertEquals(41650L, inv.grandTotalCents);
        assertEquals(31650L, inv.duePayableCents);
    }

    @Test
    public void zeroRatedProducesNoTax() {
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(35000L, TaxCategory.E, 0));
        Totals.compute(inv);

        assertEquals(0L, inv.taxTotalCents);
        assertEquals(35000L, inv.grandTotalCents);
    }

    @Test
    public void keepsExemptionReasonAcrossRecompute() {
        EnInvoice inv = new EnInvoice();
        inv.addLine(line(35000L, TaxCategory.E, 0));
        Totals.compute(inv);
        Totals.setExemptionReason(inv, "Kleinunternehmer", null);

        inv.addLine(line(5000L, TaxCategory.E, 0));
        Totals.compute(inv);

        assertEquals("Kleinunternehmer", inv.taxBreakdowns.get(0).exemptionReason);
    }
}
