package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.einvoice.Money;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a set of gigs into a draft {@link Invoice}.
 *
 * <p>Pure Java with no {@code android.*} imports, so the rules below are unit-testable: which VAT
 * mode wins, how the Leistungsdatum is expressed, and what a line looks like.
 */
public final class InvoiceBuilder {

    private InvoiceBuilder() {
    }

    /**
     * Builds an unsaved draft. The number is left blank; {@link InvoiceDao#issue} allocates it.
     *
     * @param gigs one or more gigs, all for the same customer
     */
    public static Invoice build(Issuer issuer, Customer customer, List<Gig> gigs,
                                String issueDate) {
        Invoice inv = new Invoice();
        inv.issueDate = Dates.isValid(issueDate) ? issueDate : Dates.today();
        inv.customerId = customer == null ? -1L : customer.id;
        inv.currency = "EUR";
        inv.dueDate = Dates.plusDays(inv.issueDate, Math.max(0, issuer.defaultDueDays));
        inv.buyerReference = customer == null ? null : customer.buyerReference;
        inv.language = resolveLanguage(issuer, customer);
        inv.paymentTerms = issuer.paymentTermsText;

        TaxMode mode = resolveTaxMode(issuer, customer, gigs);
        inv.taxMode = mode;
        inv.ratePermille = mode.ratePermille;
        inv.exemptionText = resolveExemptionText(issuer, mode);
        inv.exemptionCode = mode.vatexCode;

        List<Gig> sorted = sortedByDate(gigs);
        boolean single = sorted.size() == 1;

        if (single) {
            // A single gig states its date once, in BT-72.
            inv.deliveryDate = sorted.get(0).date;
        } else if (!sorted.isEmpty()) {
            // Several gigs cannot: EN 16931 has no per-line delivery date, so the header carries
            // the span and each line carries its own one-day period below.
            inv.periodStart = sorted.get(0).date;
            inv.periodEnd = sorted.get(sorted.size() - 1).date;
        }

        for (Gig gig : sorted) {
            TaxMode gigMode = firstNonNull(gig.taxMode, mode);
            if (gig.feeCents != 0L) {
                inv.lines.add(line(gig, gigMode, single, describeGig(gig, inv.language),
                        gig.feeCents));
            }
            if (gig.travelCents != 0L) {
                inv.lines.add(line(gig, gigMode, single, travelLabel(inv.language), gig.travelCents));
            }
        }

        recomputeTotals(inv);
        return inv;
    }

    /** Recomputes line nets and the document totals after an edit in the invoice screen. */
    public static void recomputeTotals(Invoice inv) {
        long lineTotal = 0L;
        for (InvoiceLine l : inv.lines) {
            l.netCents = Money.lineNet(l.quantityMilli, l.unitPriceCents);
            lineTotal += l.netCents;
        }
        inv.lineTotalCents = lineTotal;
        inv.taxBasisCents = lineTotal;
        // Grouped per category and rate, matching Totals in the einvoice package, so the stored
        // header totals and the emitted XML can never disagree.
        long tax = 0L;
        List<String> seen = new ArrayList<String>();
        for (InvoiceLine l : inv.lines) {
            String key = l.taxCategory.code + ":" + l.ratePermille;
            if (seen.contains(key)) continue;
            seen.add(key);
            long basis = 0L;
            for (InvoiceLine other : inv.lines) {
                if ((other.taxCategory.code + ":" + other.ratePermille).equals(key)) {
                    basis += other.netCents;
                }
            }
            tax += Money.taxOf(basis, l.ratePermille);
        }
        inv.taxTotalCents = tax;
        inv.grandTotalCents = inv.taxBasisCents + tax;
        inv.duePayableCents = inv.grandTotalCents - inv.prepaidCents;
    }

    /** The gig's tax mode, else the customer's, else the issuer's. */
    public static TaxMode resolveTaxMode(Issuer issuer, Customer customer, List<Gig> gigs) {
        if (gigs != null) {
            for (Gig g : gigs) {
                if (g.taxMode != null) return g.taxMode;
            }
        }
        if (customer != null && customer.defaultTaxMode != null) return customer.defaultTaxMode;
        return issuer.defaultTaxMode == null ? TaxMode.KLEINUNTERNEHMER : issuer.defaultTaxMode;
    }

    /** The customer's invoice language, else the issuer's default. */
    public static String resolveLanguage(Issuer issuer, Customer customer) {
        if (customer != null && notEmpty(customer.invoiceLanguage)) {
            return customer.invoiceLanguage.trim();
        }
        return notEmpty(issuer.defaultInvoiceLanguage) ? issuer.defaultInvoiceLanguage.trim() : "de";
    }

    /** The user's own wording when they set one, else the statutory German default. */
    public static String resolveExemptionText(Issuer issuer, TaxMode mode) {
        if (notEmpty(issuer.exemptionText)) return issuer.exemptionText.trim();
        return mode.defaultReason;
    }

    /** What a gig line says on the invoice, in the invoice's own language. */
    public static String describeGig(Gig gig, String language) {
        String where = joinPlace(gig.placeName, gig.city);
        String date = Dates.forLanguage(gig.date, language);
        String lang = language == null ? "de" : language.toLowerCase(java.util.Locale.US);
        if (lang.startsWith("en")) {
            return where.isEmpty() ? "DJ set on " + date : "DJ set on " + date + ", " + where;
        }
        if (lang.startsWith("es")) {
            return where.isEmpty() ? "Sesión de DJ el " + date
                    : "Sesión de DJ el " + date + ", " + where;
        }
        return where.isEmpty() ? "DJ-Set am " + date : "DJ-Set am " + date + ", " + where;
    }

    private static String travelLabel(String language) {
        String lang = language == null ? "de" : language.toLowerCase(java.util.Locale.US);
        if (lang.startsWith("en")) return "Travel costs";
        if (lang.startsWith("es")) return "Gastos de viaje";
        return "Reisekosten";
    }

    private static InvoiceLine line(Gig gig, TaxMode mode, boolean single, String description,
                                    long cents) {
        InvoiceLine l = new InvoiceLine();
        l.gigId = gig.id;
        l.name = shortName(description);
        l.description = description;
        l.quantityMilli = 1000L;
        l.unitCode = "C62";
        l.unitPriceCents = cents;
        l.netCents = cents;
        l.taxCategory = mode.category;
        l.ratePermille = mode.ratePermille;
        if (!single) {
            l.periodStart = gig.date;
            l.periodEnd = gig.date;
        }
        return l;
    }

    /** BT-153 wants a name, not a sentence; the sentence goes in BT-154. */
    private static String shortName(String description) {
        int comma = description.indexOf(',');
        return comma > 0 ? description.substring(0, comma) : description;
    }

    private static String joinPlace(String place, String city) {
        boolean p = notEmpty(place);
        boolean c = notEmpty(city);
        if (p && c) return place.trim() + ", " + city.trim();
        if (p) return place.trim();
        return c ? city.trim() : "";
    }

    private static List<Gig> sortedByDate(List<Gig> gigs) {
        List<Gig> out = new ArrayList<Gig>(gigs == null ? new ArrayList<Gig>() : gigs);
        // ISO dates sort chronologically as strings, so no parsing is needed.
        for (int i = 1; i < out.size(); i++) {
            Gig g = out.get(i);
            int j = i - 1;
            while (j >= 0 && compare(out.get(j).date, g.date) > 0) {
                out.set(j + 1, out.get(j));
                j--;
            }
            out.set(j + 1, g);
        }
        return out;
    }

    private static int compare(String a, String b) {
        if (a == null) return b == null ? 0 : -1;
        return b == null ? 1 : a.compareTo(b);
    }

    private static TaxMode firstNonNull(TaxMode a, TaxMode b) {
        return a != null ? a : b;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
