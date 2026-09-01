package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * The four VAT situations this app has to get right, built as fixed data so the golden files are
 * deterministic. Every scenario is a plausible DJ invoice rather than an abstract one, because the
 * awkward parts (a gig that is billed months later, two gigs on one invoice, a foreign booker) are
 * exactly where the standard's edges show.
 */
final class Fixtures {

    private Fixtures() {
    }

    static EnParty seller(boolean smallBusiness) {
        EnParty s = new EnParty();
        s.name = "Nick Shaforostov";
        s.line1 = "Musterstrasse 1";
        s.postcode = "10115";
        s.city = "Berlin";
        s.countryCode = "DE";
        s.contactName = "Nick Shaforostov";
        s.contactPhone = "+49 30 1234567";
        s.contactEmail = "dj@example.de";
        s.electronicAddress = "dj@example.de";
        s.electronicAddressScheme = "EM";
        if (smallBusiness) {
            s.taxNumber = "12/345/67890";
        } else {
            s.vatId = "DE123456789";
        }
        return s;
    }

    static EnParty germanClub() {
        EnParty b = new EnParty();
        b.name = "Club Muster GmbH";
        b.line1 = "Clubstrasse 5";
        b.postcode = "20095";
        b.city = "Hamburg";
        b.countryCode = "DE";
        b.contactEmail = "buchhaltung@club-muster.de";
        b.electronicAddress = "buchhaltung@club-muster.de";
        b.electronicAddressScheme = "EM";
        return b;
    }

    static EnParty spanishClub() {
        EnParty b = new EnParty();
        b.name = "Sala Ejemplo SL";
        b.line1 = "Calle Ejemplo 7";
        b.postcode = "08001";
        b.city = "Barcelona";
        b.countryCode = "ES";
        b.vatId = "ESA12345674";
        b.electronicAddress = "facturas@sala-ejemplo.es";
        b.electronicAddressScheme = "EM";
        return b;
    }

    private static EnInvoice base(EnParty seller, EnParty buyer) {
        EnInvoice inv = new EnInvoice();
        inv.number = "2026-001";
        inv.issueDate = "2026-09-05";
        inv.dueDate = "2026-10-05";
        inv.currency = "EUR";
        inv.buyerReference = "CLUB-2026-07";
        inv.seller = seller;
        inv.buyer = buyer;
        inv.iban = "DE89370400440532013000";
        inv.bic = "COBADEFFXXX";
        inv.accountName = "Nick Shaforostov";
        inv.remittanceInformation = "2026-001";
        inv.paymentTerms = "Zahlbar bis 05.10.2026 ohne Abzug.";
        return inv;
    }

    private static EnLine gig(String date, String place, String city, long cents,
                              TaxCategory category, int permille) {
        EnLine l = new EnLine();
        l.name = "DJ-Set";
        l.description = "DJ-Set am " + german(date) + ", " + place + ", " + city;
        l.quantityMilli = 1000L;
        l.unitCode = "C62";
        l.unitPriceCents = cents;
        l.taxCategory = category;
        l.ratePermille = permille;
        return l;
    }

    private static String german(String iso) {
        return iso.substring(8, 10) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4);
    }

    /** Section 19 UStG: no VAT, category E, and a stated reason. One gig. */
    static EnInvoice kleinunternehmer() {
        EnInvoice inv = base(seller(true), germanClub());
        inv.deliveryDate = "2026-08-15";
        inv.addLine(gig("2026-08-15", "Muster Club", "Hamburg", 35000L, TaxCategory.E, 0));
        Totals.compute(inv);
        Totals.setExemptionReason(inv, "Kleinunternehmer gemaess Paragraf 19 UStG", null);
        return inv;
    }

    /**
     * Two gigs billed together at 19 %. EN 16931 has no per-line delivery date, so each line
     * carries a one-day BG-26 period and the header carries a BG-14 spanning both.
     */
    static EnInvoice standard19TwoGigs() {
        EnInvoice inv = base(seller(false), germanClub());
        inv.periodStart = "2026-08-15";
        inv.periodEnd = "2026-08-29";

        EnLine a = gig("2026-08-15", "Muster Club", "Hamburg", 35000L, TaxCategory.S, 190);
        a.periodStart = "2026-08-15";
        a.periodEnd = "2026-08-15";
        inv.addLine(a);

        EnLine b = gig("2026-08-29", "Muster Club", "Hamburg", 42500L, TaxCategory.S, 190);
        b.periodStart = "2026-08-29";
        b.periodEnd = "2026-08-29";
        inv.addLine(b);

        Totals.compute(inv);
        return inv;
    }

    /** A performance billed as an artistic appearance under section 12(2)7a UStG. */
    static EnInvoice reduced7() {
        EnInvoice inv = base(seller(false), germanClub());
        inv.deliveryDate = "2026-08-15";
        inv.note = "Kuenstlerische Darbietung nach Paragraf 12 Abs. 2 Nr. 7a UStG.";
        inv.addLine(gig("2026-08-15", "Muster Club", "Hamburg", 35000L, TaxCategory.S, 70));
        Totals.compute(inv);
        return inv;
    }

    /** A gig abroad: reverse charge, rate 0, and the buyer's VAT id becomes mandatory. */
    static EnInvoice reverseCharge() {
        EnInvoice inv = base(seller(false), spanishClub());
        inv.deliveryDate = "2026-08-15";
        inv.buyerReference = "SALA-2026-33";
        inv.addLine(gig("2026-08-15", "Sala Ejemplo", "Barcelona", 50000L, TaxCategory.AE, 0));
        Totals.compute(inv);
        Totals.setExemptionReason(inv,
                "Steuerschuldnerschaft des Leistungsempfaengers / Reverse charge", "vatex-eu-ae");
        return inv;
    }
}
