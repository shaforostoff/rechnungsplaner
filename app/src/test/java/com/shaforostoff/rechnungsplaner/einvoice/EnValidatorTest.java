package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class EnValidatorTest {

    private static List<String> keys(List<Problem> problems, Problem.Severity severity) {
        List<String> out = new ArrayList<String>();
        for (Problem p : problems) {
            if (p.severity == severity) out.add(p.fieldKey);
        }
        return out;
    }

    private static boolean hasRule(List<Problem> problems, String rule) {
        for (Problem p : problems) {
            if (rule.equals(p.rule)) return true;
        }
        return false;
    }

    @Test
    public void fullyFilledInvoicesPass() {
        List<Problem> p = EnValidator.validate(Fixtures.standard19TwoGigs(), Profile.XRECHNUNG_30);
        assertEquals("unexpected problems: " + p, 0, keys(p, Problem.Severity.ERROR).size());
        assertEquals("unexpected warnings: " + p, 0, keys(p, Problem.Severity.WARNING).size());
    }

    @Test
    public void kleinunternehmerPasses() {
        List<Problem> p = EnValidator.validate(Fixtures.kleinunternehmer(), Profile.XRECHNUNG_30);
        assertEquals("unexpected problems: " + p, 0, keys(p, Problem.Severity.ERROR).size());
    }

    @Test
    public void reverseChargePasses() {
        List<Problem> p = EnValidator.validate(Fixtures.reverseCharge(), Profile.XRECHNUNG_30);
        assertEquals("unexpected problems: " + p, 0, keys(p, Problem.Severity.ERROR).size());
    }

    /**
     * The case this app is built around: a gig has happened, the club has not yet sent its billing
     * details, and all we know is a venue name. The user must be able to see the exact shortfall.
     */
    @Test
    public void venueOnlyCustomerReportsEveryMissingBuyerField() {
        EnInvoice inv = Fixtures.kleinunternehmer();
        inv.buyer = new EnParty();
        inv.buyer.name = "Muster Club";
        inv.buyer.countryCode = "DE";
        inv.buyerReference = null;

        List<Problem> p = EnValidator.validate(inv, Profile.XRECHNUNG_30);
        List<String> errors = keys(p, Problem.Severity.ERROR);
        assertTrue(errors.toString(), errors.contains(Problem.BUYER_STREET));
        assertTrue(errors.toString(), errors.contains(Problem.BUYER_CITY));
        assertTrue(errors.toString(), errors.contains(Problem.BUYER_POSTCODE));
        assertTrue(errors.toString(), errors.contains(Problem.INVOICE_BUYER_REFERENCE));
        assertTrue(keys(p, Problem.Severity.WARNING).contains(Problem.BUYER_ENDPOINT));
    }

    @Test
    public void buyerReferenceOnlyMandatoryUnderXrechnung() {
        EnInvoice inv = Fixtures.standard19TwoGigs();
        inv.buyerReference = null;

        assertTrue(hasRule(EnValidator.validate(inv, Profile.XRECHNUNG_30), "BR-DE-15"));
        assertFalse(hasRule(EnValidator.validate(inv, Profile.EN16931), "BR-DE-15"));
    }

    @Test
    public void sellerNeedsAVatIdOrATaxNumber() {
        EnInvoice inv = Fixtures.kleinunternehmer();
        inv.seller.taxNumber = null;
        inv.seller.vatId = null;
        assertTrue(keys(EnValidator.validate(inv, Profile.XRECHNUNG_30), Problem.Severity.ERROR)
                .contains(Problem.SELLER_TAX_ID));
    }

    @Test
    public void zeroRatedCategoryNeedsAnExemptionReason() {
        EnInvoice inv = Fixtures.kleinunternehmer();
        inv.taxBreakdowns.get(0).exemptionReason = null;
        inv.taxBreakdowns.get(0).exemptionReasonCode = null;
        assertTrue(hasRule(EnValidator.validate(inv, Profile.XRECHNUNG_30), "BR-E-10"));
    }

    @Test
    public void reverseChargeNeedsTheBuyerVatId() {
        EnInvoice inv = Fixtures.reverseCharge();
        inv.buyer.vatId = null;
        assertTrue(keys(EnValidator.validate(inv, Profile.XRECHNUNG_30), Problem.Severity.ERROR)
                .contains(Problem.BUYER_VAT_ID));
    }

    @Test
    public void sepaCreditTransferNeedsAnIban() {
        EnInvoice inv = Fixtures.standard19TwoGigs();
        inv.iban = null;
        assertTrue(hasRule(EnValidator.validate(inv, Profile.XRECHNUNG_30), "BR-DE-23"));
    }

    @Test
    public void catchesTamperedTotals() {
        EnInvoice inv = Fixtures.standard19TwoGigs();
        inv.grandTotalCents += 1L;
        assertTrue(hasRule(EnValidator.validate(inv, Profile.XRECHNUNG_30), "BR-CO-15"));
    }

    @Test
    public void requiresADeliveryDateOrAnInvoicingPeriod() {
        EnInvoice inv = Fixtures.kleinunternehmer();
        inv.deliveryDate = null;
        inv.periodStart = null;
        inv.periodEnd = null;
        assertTrue(keys(EnValidator.validate(inv, Profile.XRECHNUNG_30), Problem.Severity.ERROR)
                .contains(Problem.INVOICE_DELIVERY_DATE));
    }
}
