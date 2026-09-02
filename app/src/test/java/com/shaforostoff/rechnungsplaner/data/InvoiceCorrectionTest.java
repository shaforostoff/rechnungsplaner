package com.shaforostoff.rechnungsplaner.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Redoing an invoice that already exists.
 *
 * <p>The rule is narrow and worth stating exactly: a correction keeps the document's identity and
 * recomputes everything else. Keeping too much would defeat the point -- the reasons to redo an
 * invoice are all "a number or an address was wrong when it was first written". Keeping too little
 * would issue a second document under a number already used.
 */
public class InvoiceCorrectionTest {

    private static Issuer issuerBeforeMoving() {
        Issuer i = new Issuer();
        i.name = "Nick Shaforostov";
        i.street = "Alte Strasse 1";
        i.postcode = "10115";
        i.city = "Berlin";
        i.countryCode = "DE";
        i.taxNumber = "12/345/67890";
        i.iban = "DE89370400440532013000";
        i.defaultTaxMode = TaxMode.STANDARD_19;
        i.defaultDueDays = 60;
        i.defaultInvoiceLanguage = "de";
        return i;
    }

    private static Customer club() {
        Customer c = new Customer();
        c.id = 7L;
        c.placeName = "Muster Club";
        c.city = "Hamburg";
        c.countryCode = "DE";
        return c;
    }

    private static Gig gig(long feeCents) {
        Gig g = new Gig();
        g.id = 3L;
        g.date = "2026-08-15";
        g.customerId = 7L;
        g.placeName = "Muster Club";
        g.city = "Hamburg";
        g.feeCents = feeCents;
        return g;
    }

    /** The first version of the invoice, as issued. */
    private static Invoice asIssued() {
        Invoice original = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(35000L)), "2026-09-05");
        original.id = 12L;
        original.number = "2026-001";
        original.createdAt = 1_757_000_000_000L;
        return original;
    }

    @Test
    public void aCorrectionKeepsTheNumberRowAndCreationTime() {
        Invoice original = asIssued();
        Invoice corrected = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(40000L)), original.issueDate);
        corrected.takeIdentityFrom(original);

        assertEquals("2026-001", corrected.number);
        assertEquals(12L, corrected.id);
        assertEquals(1_757_000_000_000L, corrected.createdAt);
    }

    @Test
    public void aCorrectedFeeReachesTheTotals() {
        Invoice original = asIssued();
        assertEquals(35000L, original.taxBasisCents);
        assertEquals(41650L, original.grandTotalCents);

        Invoice corrected = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(40000L)), original.issueDate);
        corrected.takeIdentityFrom(original);

        // Identity is all that carries over; the money is recomputed.
        assertEquals(40000L, corrected.taxBasisCents);
        assertEquals(7600L, corrected.taxTotalCents);
        assertEquals(47600L, corrected.grandTotalCents);
    }

    @Test
    public void theOriginalIssueDateSurvivesSoTheDeadlineDoesNotMove() {
        // The address correction case: the invoice was written weeks ago and is being reissued
        // today. Rebuilding it with today's date would quietly grant the club another 60 days.
        Invoice original = asIssued();
        Invoice corrected = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(35000L)), original.issueDate);
        corrected.takeIdentityFrom(original);

        assertEquals("2026-09-05", corrected.issueDate);
        assertEquals(original.dueDate, corrected.dueDate);
        assertEquals("2026-11-04", corrected.dueDate);
    }

    @Test
    public void identityDoesNotDragTheOldContentAlongWithIt() {
        // takeIdentityFrom copies three fields. If it ever grew to copy the snapshots or the
        // totals, a correction would reproduce exactly the document it was meant to fix.
        Invoice original = asIssued();
        original.issuerSnapshot = "{\"before\":\"Alte Strasse 1\"}";
        original.customerSnapshot = "{\"before\":\"unknown\"}";

        Issuer moved = issuerBeforeMoving();
        moved.street = "Neue Strasse 9";
        Invoice corrected = InvoiceBuilder.build(moved, club(),
                Arrays.<Gig>asList(gig(35000L)), original.issueDate);
        corrected.issuerSnapshot = "{\"after\":\"Neue Strasse 9\"}";
        corrected.takeIdentityFrom(original);

        assertEquals("{\"after\":\"Neue Strasse 9\"}", corrected.issuerSnapshot);
        assertNotEquals(original.customerSnapshot, corrected.customerSnapshot);
    }

    @Test
    public void addingAGigToACorrectionBillsBothWithTheirOwnPeriods() {
        // A second gig turns the single delivery date into a header period plus per-line periods,
        // the same as it would on a new invoice -- EN 16931 has no per-line delivery date.
        Invoice original = asIssued();
        Gig second = gig(30000L);
        second.id = 4L;
        second.date = "2026-08-22";

        List<Gig> both = Arrays.asList(gig(35000L), second);
        Invoice corrected = InvoiceBuilder.build(issuerBeforeMoving(), club(), both,
                original.issueDate);
        corrected.takeIdentityFrom(original);

        assertEquals(2, corrected.lines.size());
        assertEquals(65000L, corrected.taxBasisCents);
        assertEquals("2026-001", corrected.number);
        assertEquals("2026-08-15", corrected.periodStart);
        assertEquals("2026-08-22", corrected.periodEnd);
        assertEquals("2026-08-22", corrected.lines.get(1).periodStart);
        assertFalse("a multi-gig invoice states the span, not one delivery date",
                "2026-08-15".equals(corrected.deliveryDate));
    }

    @Test
    public void supersedingRecordsWhatIsBeingReplacedAndTakesANewIdentity() {
        Invoice sent = asIssued();
        Invoice replacement = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(40000L)), "2026-11-20");
        replacement.supersede(sent);

        // A second document: its own row and its own number, to be allocated on issue.
        assertEquals(-1L, replacement.id);
        assertEquals(12L, replacement.replacesId);
        assertEquals("2026-001", replacement.replacesNumber);
        assertEquals("2026-09-05", replacement.replacesDate);

        // Dated today, so the payment period restarts from the bill that will actually be paid.
        assertEquals("2026-11-20", replacement.issueDate);
        assertEquals("2027-01-19", replacement.dueDate);
    }

    @Test
    public void theCorrectionNoteNamesTheReplacedInvoiceInTheDocumentLanguage() {
        // Printed on the page, for the bookkeeper who will otherwise see two bills for one gig.
        assertEquals("Korrigierte Rechnung, ersetzt Rechnung 2026-001 vom 05.09.2026.",
                InvoiceBuilder.correctionNote("2026-001", "2026-09-05", "de"));
        assertEquals("Corrected invoice, replacing invoice 2026-001 of 2026-09-05.",
                InvoiceBuilder.correctionNote("2026-001", "2026-09-05", "en"));
        assertEquals("Factura rectificativa, sustituye a la factura 2026-001 del 05.09.2026.",
                InvoiceBuilder.correctionNote("2026-001", "2026-09-05", "es"));
    }

    @Test
    public void theReferenceReachesTheEmittedInvoice() {
        // The whole point of recording it: BG-3 in the XML, so an importer files the replacement
        // against the original instead of counting the revenue twice.
        Invoice sent = asIssued();
        Invoice replacement = InvoiceBuilder.build(issuerBeforeMoving(), club(),
                Arrays.<Gig>asList(gig(40000L)), "2026-11-20");
        replacement.supersede(sent);
        replacement.number = "2026-004";

        com.shaforostoff.rechnungsplaner.einvoice.EnInvoice en =
                InvoiceMapper.toEnInvoice(issuerBeforeMoving(), club(), replacement);
        assertEquals("2026-001", en.precedingNumber);
        assertEquals("2026-09-05", en.precedingIssueDate);
    }
}
