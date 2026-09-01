package com.shaforostoff.rechnungsplaner.einvoice;

import java.util.ArrayList;
import java.util.List;

/**
 * An offline subset of the EN 16931 and XRechnung rule sets, run before anything is written so the
 * user sees what is still missing instead of discovering it after sending the invoice.
 *
 * <p>This is deliberately <em>not</em> a validator in the KoSIT sense. Android has no usable
 * {@code javax.xml.validation} W3C-Schema factory and shipping Schematron would mean shipping an
 * XSLT engine, so the real validation is a desktop step. The UI must therefore never claim a file
 * is "valid" on the strength of this class — only that no known problem was found.
 *
 * <p>Rules covered: EN 16931 BR-01..BR-16, the BR-CO arithmetic rules, the per-category BR-S/BR-E/
 * BR-AE/BR-IC rules, and the German BR-DE-1..11, 15, 16 and 23. The Peppol addressing rules are
 * reported as warnings because they bind only when the file is validated as a Peppol BIS document.
 */
public final class EnValidator {

    private EnValidator() {
    }

    public static List<Problem> validate(EnInvoice inv, Profile profile) {
        List<Problem> out = new ArrayList<Problem>();
        boolean de = profile.xrechnungRules;

        header(inv, out);
        seller(inv, de, out);
        buyer(inv, de, out);
        payment(inv, de, out);
        taxes(inv, out);
        arithmetic(inv, out);

        if (de && Str.isEmpty(inv.buyerReference)) {
            // The one that bites for private-sector customers: XRechnung makes BT-10 mandatory,
            // but a club has no Leitweg-ID and usually no purchase-order reference either.
            error(out, "BT-10", "BR-DE-15", Problem.INVOICE_BUYER_REFERENCE,
                    "Buyer reference is required by XRechnung");
        }
        return out;
    }

    private static void header(EnInvoice inv, List<Problem> out) {
        if (Str.isEmpty(inv.number)) {
            error(out, "BT-1", "BR-02", Problem.INVOICE_NUMBER, "Invoice number is missing");
        }
        if (Str.isEmpty(inv.issueDate)) {
            error(out, "BT-2", "BR-03", Problem.INVOICE_ISSUE_DATE, "Issue date is missing");
        }
        if (Str.isEmpty(inv.currency)) {
            error(out, "BT-5", "BR-05", Problem.INVOICE_CURRENCY, "Currency is missing");
        }
        if (inv.lines.isEmpty()) {
            error(out, "BG-25", "BR-16", Problem.INVOICE_LINES, "The invoice has no lines");
        }
        // Either the delivery date or an invoicing period must be present, both to satisfy the
        // validators and because section 14 UStG requires the Leistungsdatum on the document.
        if (Str.isEmpty(inv.deliveryDate) && Str.isEmpty(inv.periodStart)) {
            error(out, "BT-72", null, Problem.INVOICE_DELIVERY_DATE,
                    "Neither a delivery date nor an invoicing period is set");
        }
    }

    private static void seller(EnInvoice inv, boolean de, List<Problem> out) {
        EnParty s = inv.seller;
        if (Str.isEmpty(s.name)) {
            error(out, "BT-27", "BR-06", Problem.SELLER_NAME, "Seller name is missing");
        }
        if (Str.isEmpty(s.countryCode)) {
            error(out, "BT-40", "BR-09", Problem.SELLER_COUNTRY, "Seller country is missing");
        }
        if (Str.isEmpty(s.vatId) && Str.isEmpty(s.taxNumber)) {
            error(out, "BT-31", "BR-DE-16", Problem.SELLER_TAX_ID,
                    "Seller needs either a VAT identifier or a tax number");
        }
        if (de) {
            if (Str.isEmpty(s.line1)) {
                error(out, "BT-35", "BR-DE-3", Problem.SELLER_STREET, "Seller street is missing");
            }
            if (Str.isEmpty(s.city)) {
                error(out, "BT-37", "BR-DE-4", Problem.SELLER_CITY, "Seller city is missing");
            }
            if (Str.isEmpty(s.postcode)) {
                error(out, "BT-38", "BR-DE-5", Problem.SELLER_POSTCODE,
                        "Seller post code is missing");
            }
            if (Str.isEmpty(s.contactName)) {
                error(out, "BT-41", "BR-DE-6", Problem.SELLER_CONTACT_NAME,
                        "Seller contact name is missing");
            }
            if (Str.isEmpty(s.contactPhone)) {
                error(out, "BT-42", "BR-DE-7", Problem.SELLER_CONTACT_PHONE,
                        "Seller contact telephone is missing");
            }
            if (Str.isEmpty(s.contactEmail)) {
                error(out, "BT-43", "BR-DE-8", Problem.SELLER_CONTACT_EMAIL,
                        "Seller contact email is missing");
            }
        }
        if (Str.isEmpty(s.electronicAddress)) {
            warn(out, "BT-34", "PEPPOL-EN16931-R020", Problem.SELLER_ENDPOINT,
                    "Seller electronic address is missing");
        }
    }

    private static void buyer(EnInvoice inv, boolean de, List<Problem> out) {
        EnParty b = inv.buyer;
        if (Str.isEmpty(b.name)) {
            error(out, "BT-44", "BR-07", Problem.BUYER_NAME, "Buyer name is missing");
        }
        if (Str.isEmpty(b.countryCode)) {
            error(out, "BT-55", "BR-11", Problem.BUYER_COUNTRY, "Buyer country is missing");
        }
        if (de) {
            if (Str.isEmpty(b.line1)) {
                error(out, "BT-50", "BR-DE-9", Problem.BUYER_STREET, "Buyer street is missing");
            }
            if (Str.isEmpty(b.city)) {
                error(out, "BT-52", "BR-DE-10", Problem.BUYER_CITY, "Buyer city is missing");
            }
            if (Str.isEmpty(b.postcode)) {
                error(out, "BT-53", "BR-DE-11", Problem.BUYER_POSTCODE,
                        "Buyer post code is missing");
            }
        }
        if (Str.isEmpty(b.electronicAddress)) {
            warn(out, "BT-49", "PEPPOL-EN16931-R010", Problem.BUYER_ENDPOINT,
                    "Buyer electronic address is missing");
        }
    }

    private static void payment(EnInvoice inv, boolean de, List<Problem> out) {
        if (de && Str.isEmpty(inv.paymentMeansCode)) {
            error(out, "BG-16", "BR-DE-1", Problem.SELLER_IBAN,
                    "Payment instructions are required");
        }
        // A SEPA credit transfer without an account to credit is useless and BR-DE-23 rejects it.
        if ("58".equals(inv.paymentMeansCode) && Str.isEmpty(inv.iban)) {
            error(out, "BT-84", "BR-DE-23", Problem.SELLER_IBAN,
                    "SEPA credit transfer needs an IBAN");
        }
        if (inv.duePayableCents > 0
                && Str.isEmpty(inv.dueDate) && Str.isEmpty(inv.paymentTerms)) {
            error(out, "BT-9", "BR-CO-25", Problem.INVOICE_PAYMENT_DUE,
                    "An amount is due, so either a due date or payment terms must be given");
        }
    }

    private static void taxes(EnInvoice inv, List<Problem> out) {
        for (TaxBreakdown g : inv.taxBreakdowns) {
            if (g.category.needsExemptionReason && Str.isEmpty(g.exemptionReason)
                    && Str.isEmpty(g.exemptionReasonCode)) {
                error(out, "BT-120", ruleFor(g.category), Problem.TAX_EXEMPTION_REASON,
                        "Category " + g.category.code + " needs an exemption reason");
            }
            if (g.category.isZeroRated() && g.ratePermille != 0) {
                error(out, "BT-119", ruleFor(g.category), Problem.TAX_RATE,
                        "Category " + g.category.code + " must carry rate 0");
            }
            if (g.category == TaxCategory.S && g.ratePermille <= 0) {
                error(out, "BT-119", "BR-S-05", Problem.TAX_RATE,
                        "Standard-rated lines must carry a rate above 0");
            }
            if (g.category == TaxCategory.AE || g.category == TaxCategory.K) {
                if (Str.isEmpty(inv.buyer.vatId)) {
                    error(out, "BT-48", ruleFor(g.category), Problem.BUYER_VAT_ID,
                            "Reverse charge and intra-community supply need the buyer's VAT id");
                }
            }
        }
    }

    private static void arithmetic(EnInvoice inv, List<Problem> out) {
        long lineSum = 0L;
        for (EnLine line : inv.lines) {
            lineSum += Money.lineNet(line.quantityMilli, line.unitPriceCents);
        }
        long taxSum = 0L;
        long basisSum = 0L;
        for (TaxBreakdown g : inv.taxBreakdowns) {
            taxSum += g.taxAmountCents;
            basisSum += g.basisCents;
            long expected = Money.taxOf(g.basisCents, g.ratePermille);
            if (g.taxAmountCents != expected) {
                error(out, "BT-117", "BR-CO-17", Problem.INVOICE_TOTALS,
                        "VAT for category " + g.category.code + " should be "
                                + Money.amount(expected) + " but is "
                                + Money.amount(g.taxAmountCents));
            }
        }
        check(out, inv.lineTotalCents == lineSum, "BT-106", "BR-CO-10",
                "Sum of line net amounts does not match the lines");
        check(out, basisSum == inv.taxBasisCents, "BT-109", "BR-CO-13",
                "VAT breakdown bases do not add up to the invoice total without VAT");
        check(out, inv.taxTotalCents == taxSum, "BT-110", "BR-CO-14",
                "Total VAT does not match the VAT breakdown");
        check(out, inv.grandTotalCents == inv.taxBasisCents + inv.taxTotalCents, "BT-112",
                "BR-CO-15", "Total with VAT does not match total without VAT plus VAT");
        check(out, inv.duePayableCents == inv.grandTotalCents - inv.prepaidCents, "BT-115",
                "BR-CO-16", "Amount due does not match total with VAT minus prepaid");
    }

    private static String ruleFor(TaxCategory c) {
        switch (c) {
            case E: return "BR-E-10";
            case AE: return "BR-AE-10";
            case K: return "BR-IC-10";
            case G: return "BR-G-10";
            case O: return "BR-O-10";
            case Z: return "BR-Z-10";
            default: return null;
        }
    }

    private static void check(List<Problem> out, boolean ok, String term, String rule, String msg) {
        if (!ok) error(out, term, rule, Problem.INVOICE_TOTALS, msg);
    }

    private static void error(List<Problem> out, String term, String rule, String key, String d) {
        out.add(new Problem(Problem.Severity.ERROR, term, rule, key, d));
    }

    private static void warn(List<Problem> out, String term, String rule, String key, String d) {
        out.add(new Problem(Problem.Severity.WARNING, term, rule, key, d));
    }
}
