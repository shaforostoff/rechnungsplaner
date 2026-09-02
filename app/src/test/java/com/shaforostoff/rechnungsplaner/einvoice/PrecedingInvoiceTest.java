package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * BG-3, the reference a corrected invoice makes to the one it replaces.
 *
 * <p>What makes this worth its own test is the position. Both syntaxes put the reference in a fixed
 * place in a sequence -- and in opposite halves of the document -- so getting it wrong produces a
 * file that still contains every value, reads correctly to a human, and is rejected by a schema
 * check the app cannot run on a phone.
 */
public class PrecedingInvoiceTest {

    private static EnInvoice corrected() {
        EnInvoice inv = Fixtures.standard19TwoGigs();
        inv.precedingNumber = "2026-001";
        inv.precedingIssueDate = "2026-09-05";
        return inv;
    }

    @Test
    public void ublPutsTheReferenceBetweenThePeriodAndTheParties() {
        String xml = Syntax.UBL.write(corrected(), Profile.XRECHNUNG_30);

        assertTrue(xml.contains("<cac:BillingReference>"));
        assertTrue(xml.contains("<cbc:ID>2026-001</cbc:ID>"));
        assertTrue(xml.contains("<cbc:IssueDate>2026-09-05</cbc:IssueDate>"));

        int reference = xml.indexOf("<cac:BillingReference>");
        int period = xml.indexOf("<cac:InvoicePeriod>");
        int supplier = xml.indexOf("<cac:AccountingSupplierParty>");
        assertTrue("after the invoice period", period < reference);
        assertTrue("and before the parties", reference < supplier);
    }

    @Test
    public void ciiPutsTheReferenceAfterTheTotals() {
        String xml = Syntax.CII.write(corrected(), Profile.XRECHNUNG_30);

        assertTrue(xml.contains("<ram:InvoiceReferencedDocument>"));
        assertTrue(xml.contains("<ram:IssuerAssignedID>2026-001</ram:IssuerAssignedID>"));
        // CII dates are a DateTimeString in format 102, even inside this group.
        assertTrue(xml.contains("<udt:DateTimeString format=\"102\">20260905</udt:DateTimeString>"));

        int reference = xml.indexOf("<ram:InvoiceReferencedDocument>");
        int summation = xml.indexOf("<ram:SpecifiedTradeSettlementHeaderMonetarySummation>");
        int settlementEnd = xml.indexOf("</ram:ApplicableHeaderTradeSettlement>");
        assertTrue("after the monetary summation", summation < reference);
        assertTrue("and still inside the settlement group", reference < settlementEnd);
    }

    @Test
    public void anInvoiceThatCorrectsNothingSaysNothing() {
        // The group is optional, and an empty one is a schema violation rather than a harmless
        // blank -- so absent has to mean absent.
        EnInvoice plain = Fixtures.standard19TwoGigs();
        String ubl = Syntax.UBL.write(plain, Profile.XRECHNUNG_30);
        String cii = Syntax.CII.write(plain, Profile.XRECHNUNG_30);

        assertFalse(ubl.contains("BillingReference"));
        assertFalse(cii.contains("InvoiceReferencedDocument"));
    }

    @Test
    public void aReferenceWithoutADateIsStillValid() {
        // BT-26 is optional. An invoice imported from elsewhere may not carry the original's date.
        EnInvoice inv = Fixtures.standard19TwoGigs();
        inv.precedingNumber = "2026-001";
        String ubl = Syntax.UBL.write(inv, Profile.XRECHNUNG_30);

        assertTrue(ubl.contains("<cbc:ID>2026-001</cbc:ID>"));
        assertFalse("no empty IssueDate element", ubl.contains("<cbc:IssueDate></cbc:IssueDate>"));
    }

    @Test
    public void theCorrectedInvoiceIsStillAnInvoiceRatherThanACreditNote() {
        // Deliberately 380 and not 384: all three target systems import 380 without special
        // handling, and the replacement is expressed by BG-3 rather than by the type code.
        // Credit notes (381) are a separate document this app does not write.
        assertEquals("380", corrected().typeCode);
        assertTrue(Syntax.UBL.write(corrected(), Profile.XRECHNUNG_30)
                .contains("<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"));
    }
}
