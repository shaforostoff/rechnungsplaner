package com.shaforostoff.rechnungsplaner.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.shaforostoff.rechnungsplaner.einvoice.EnInvoice;
import com.shaforostoff.rechnungsplaner.einvoice.EnValidator;
import com.shaforostoff.rechnungsplaner.einvoice.Problem;
import com.shaforostoff.rechnungsplaner.einvoice.Profile;
import com.shaforostoff.rechnungsplaner.einvoice.TaxCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class InvoiceBuilderTest {

    private static Issuer issuer() {
        Issuer i = new Issuer();
        i.name = "Nick Shaforostov";
        i.street = "Musterstrasse 1";
        i.postcode = "10115";
        i.city = "Berlin";
        i.countryCode = "DE";
        i.contactName = "Nick Shaforostov";
        i.phone = "+49 30 1234567";
        i.email = "dj@example.de";
        i.taxNumber = "12/345/67890";
        i.iban = "DE89370400440532013000";
        i.accountHolder = "Nick Shaforostov";
        i.defaultTaxMode = TaxMode.KLEINUNTERNEHMER;
        i.defaultDueDays = 30;
        i.defaultInvoiceLanguage = "de";
        return i;
    }

    private static Customer club() {
        Customer c = new Customer();
        c.id = 7L;
        c.officialName = "Club Muster GmbH";
        c.placeName = "Muster Club";
        c.street = "Clubstrasse 5";
        c.postcode = "20095";
        c.city = "Hamburg";
        c.countryCode = "DE";
        c.email = "buchhaltung@club-muster.de";
        c.buyerReference = "CLUB-2026-07";
        return c;
    }

    private static Gig gig(String date, long feeCents) {
        Gig g = new Gig();
        g.date = date;
        g.feeCents = feeCents;
        g.placeName = "Muster Club";
        g.city = "Hamburg";
        return g;
    }

    @Test
    public void singleGigStatesItsDateInTheDeliveryDate() {
        Invoice inv = InvoiceBuilder.build(issuer(), club(),
                Arrays.asList(gig("2026-08-15", 35000L)), "2026-09-05");

        assertEquals("2026-08-15", inv.deliveryDate);
        assertNull(inv.periodStart);
        assertEquals(1, inv.lines.size());
        assertNull("a single-gig line needs no period", inv.lines.get(0).periodStart);
    }

    @Test
    public void severalGigsUseLinePeriodsAndAHeaderSpan() {
        // EN 16931 has no per-line delivery date, so this is the only lawful way to say when each
        // of several gigs happened.
        Invoice inv = InvoiceBuilder.build(issuer(), club(),
                Arrays.asList(gig("2026-08-29", 42500L), gig("2026-08-15", 35000L)), "2026-09-05");

        assertNull(inv.deliveryDate);
        assertEquals("2026-08-15", inv.periodStart);
        assertEquals("2026-08-29", inv.periodEnd);
        assertEquals("2026-08-15", inv.lines.get(0).periodStart);
        assertEquals("2026-08-15", inv.lines.get(0).periodEnd);
        assertEquals("2026-08-29", inv.lines.get(1).periodStart);
    }

    @Test
    public void ordersLinesByGigDateRegardlessOfSelectionOrder() {
        Invoice inv = InvoiceBuilder.build(issuer(), club(),
                Arrays.asList(gig("2026-08-29", 42500L), gig("2026-08-15", 35000L)), "2026-09-05");
        assertEquals(35000L, inv.lines.get(0).unitPriceCents);
        assertEquals(42500L, inv.lines.get(1).unitPriceCents);
    }

    @Test
    public void travelCostsBecomeTheirOwnLine() {
        Gig g = gig("2026-08-15", 35000L);
        g.travelCents = 4500L;
        Invoice inv = InvoiceBuilder.build(issuer(), club(), Arrays.asList(g), "2026-09-05");

        assertEquals(2, inv.lines.size());
        assertEquals("Reisekosten", inv.lines.get(1).description);
        assertEquals(39500L, inv.lineTotalCents);
    }

    @Test
    public void gigTaxModeBeatsCustomerWhichBeatsIssuer() {
        Issuer i = issuer();
        Customer c = club();

        assertEquals(TaxMode.KLEINUNTERNEHMER,
                InvoiceBuilder.resolveTaxMode(i, c, Arrays.asList(gig("2026-08-15", 1L))));

        c.defaultTaxMode = TaxMode.STANDARD_19;
        assertEquals(TaxMode.STANDARD_19,
                InvoiceBuilder.resolveTaxMode(i, c, Arrays.asList(gig("2026-08-15", 1L))));

        Gig g = gig("2026-08-15", 1L);
        g.taxMode = TaxMode.REDUCED_7;
        assertEquals(TaxMode.REDUCED_7, InvoiceBuilder.resolveTaxMode(i, c, Arrays.asList(g)));
    }

    @Test
    public void customerLanguageBeatsIssuerDefault() {
        Issuer i = issuer();
        Customer c = club();
        assertEquals("de", InvoiceBuilder.resolveLanguage(i, c));

        c.invoiceLanguage = "es";
        assertEquals("es", InvoiceBuilder.resolveLanguage(i, c));

        c.invoiceLanguage = null;
        i.defaultInvoiceLanguage = "en";
        assertEquals("en", InvoiceBuilder.resolveLanguage(i, c));
    }

    @Test
    public void lineDescriptionFollowsTheInvoiceLanguage() {
        Gig g = gig("2026-08-15", 35000L);
        assertEquals("DJ-Set am 15.08.2026, Muster Club, Hamburg",
                InvoiceBuilder.describeGig(g, "de"));
        assertEquals("DJ set on 2026-08-15, Muster Club, Hamburg",
                InvoiceBuilder.describeGig(g, "en"));
        assertEquals("Sesión de DJ el 15.08.2026, Muster Club, Hamburg",
                InvoiceBuilder.describeGig(g, "es"));
    }

    @Test
    public void kleinunternehmerGetsTheStatutoryGermanReasonEvenInEnglish() {
        Issuer i = issuer();
        Customer c = club();
        c.invoiceLanguage = "en";
        Invoice inv = InvoiceBuilder.build(i, c, Arrays.asList(gig("2026-08-15", 35000L)),
                "2026-09-05");

        assertEquals("en", inv.language);
        assertEquals("Kleinunternehmer gemäß § 19 UStG", inv.exemptionText);
        assertEquals(TaxCategory.E, inv.lines.get(0).taxCategory);
        assertEquals(0L, inv.taxTotalCents);
    }

    @Test
    public void issuerWordingOverridesTheStatutoryDefault() {
        Issuer i = issuer();
        i.exemptionText = "Umsatzsteuerbefreit nach § 19 UStG (Kleinunternehmer)";
        assertEquals("Umsatzsteuerbefreit nach § 19 UStG (Kleinunternehmer)",
                InvoiceBuilder.resolveExemptionText(i, TaxMode.KLEINUNTERNEHMER));
    }

    @Test
    public void computesDueDateFromTheIssuerTerms() {
        Issuer i = issuer();
        i.defaultDueDays = 14;
        Invoice inv = InvoiceBuilder.build(i, club(), Arrays.asList(gig("2026-08-15", 35000L)),
                "2026-09-05");
        assertEquals("2026-09-19", inv.dueDate);
    }

    @Test
    public void storedTotalsAgreeWithTheEmittedXml() {
        // The stored header and the XML are computed by different code paths (InvoiceBuilder and
        // Totals). If they ever drift the importers will notice, so pin them together here.
        Issuer i = issuer();
        i.defaultTaxMode = TaxMode.STANDARD_19;
        i.vatId = "DE123456789";
        Invoice inv = InvoiceBuilder.build(i, club(),
                Arrays.asList(gig("2026-08-15", 33333L), gig("2026-08-29", 33333L)), "2026-09-05");

        EnInvoice en = InvoiceMapper.toEnInvoice(i, club(), inv);
        assertEquals(inv.lineTotalCents, en.lineTotalCents);
        assertEquals(inv.taxTotalCents, en.taxTotalCents);
        assertEquals(inv.grandTotalCents, en.grandTotalCents);
        assertEquals(inv.duePayableCents, en.duePayableCents);
    }

    @Test
    public void aFullyConfiguredDraftPassesTheValidator() {
        Invoice inv = InvoiceBuilder.build(issuer(), club(),
                Arrays.asList(gig("2026-08-15", 35000L)), "2026-09-05");
        inv.number = "2026-001";

        EnInvoice en = InvoiceMapper.toEnInvoice(issuer(), club(), inv);
        List<Problem> errors = new ArrayList<Problem>();
        for (Problem p : EnValidator.validate(en, Profile.XRECHNUNG_30)) {
            if (p.isError()) errors.add(p);
        }
        assertTrue("unexpected: " + errors, errors.isEmpty());
    }

    @Test
    public void aVenueOnlyCustomerStillProducesADraftAndNamesWhatIsMissing() {
        // The workflow the app exists for: bill later, chase details afterwards.
        Customer venueOnly = new Customer();
        venueOnly.id = 9L;
        venueOnly.placeName = "Muster Club";
        venueOnly.city = "Hamburg";

        Invoice inv = InvoiceBuilder.build(issuer(), venueOnly,
                Arrays.asList(gig("2026-08-15", 35000L)), "2026-09-05");
        inv.number = "2026-001";

        EnInvoice en = InvoiceMapper.toEnInvoice(issuer(), venueOnly, inv);
        assertEquals("falls back to the venue name", "Muster Club", en.buyer.name);

        List<String> keys = new ArrayList<String>();
        for (Problem p : EnValidator.validate(en, Profile.XRECHNUNG_30)) keys.add(p.fieldKey);
        assertTrue(keys.contains(Problem.BUYER_STREET));
        assertTrue(keys.contains(Problem.BUYER_POSTCODE));
        assertTrue(keys.contains(Problem.INVOICE_BUYER_REFERENCE));
    }
}
