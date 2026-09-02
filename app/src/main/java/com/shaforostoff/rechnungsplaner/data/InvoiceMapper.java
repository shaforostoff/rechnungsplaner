package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.einvoice.EnInvoice;
import com.shaforostoff.rechnungsplaner.einvoice.EnLine;
import com.shaforostoff.rechnungsplaner.einvoice.EnParty;
import com.shaforostoff.rechnungsplaner.einvoice.Totals;

/**
 * Bridges the stored records to the EN 16931 semantic model.
 *
 * <p>The only place the app's own vocabulary meets the standard's. Pure Java with no
 * {@code android.*} imports, so the mapping is unit-testable against the golden files.
 */
public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    /**
     * @param issuer   the seller, ideally restored from the invoice's snapshot rather than live
     * @param customer the buyer, likewise
     */
    public static EnInvoice toEnInvoice(Issuer issuer, Customer customer, Invoice invoice) {
        EnInvoice out = new EnInvoice();
        out.number = invoice.number;
        out.issueDate = invoice.issueDate;
        out.dueDate = invoice.dueDate;
        out.currency = invoice.currency == null ? "EUR" : invoice.currency;
        out.buyerReference = invoice.buyerReference;
        out.note = invoice.note;
        out.precedingNumber = invoice.replacesNumber;
        out.precedingIssueDate = invoice.replacesDate;
        out.deliveryDate = invoice.deliveryDate;
        out.periodStart = invoice.periodStart;
        out.periodEnd = invoice.periodEnd;
        out.paymentTerms = invoice.paymentTerms;
        out.paymentMeansCode = "58";
        out.remittanceInformation = invoice.number;
        out.iban = trimToNull(issuer.iban);
        out.bic = trimToNull(issuer.bic);
        out.accountName = trimToNull(issuer.accountHolder);
        out.prepaidCents = invoice.prepaidCents;
        out.languageTag = invoice.language == null ? "de" : invoice.language;

        out.seller = seller(issuer);
        out.buyer = buyer(customer);

        for (InvoiceLine l : invoice.lines) {
            EnLine line = new EnLine();
            line.id = Integer.toString(l.lineNo > 0 ? l.lineNo : out.lines.size() + 1);
            line.name = l.name;
            line.description = l.description;
            line.quantityMilli = l.quantityMilli;
            line.unitCode = l.unitCode == null ? "C62" : l.unitCode;
            line.unitPriceCents = l.unitPriceCents;
            line.taxCategory = l.taxCategory;
            line.ratePermille = l.ratePermille;
            line.periodStart = l.periodStart;
            line.periodEnd = l.periodEnd;
            out.addLine(line);
        }

        // Recomputed from the lines rather than copied from the stored header, so the XML can never
        // carry totals that disagree with its own line items.
        Totals.compute(out);
        Totals.setExemptionReason(out, invoice.exemptionText, invoice.exemptionCode);
        return out;
    }

    private static EnParty seller(Issuer i) {
        EnParty p = new EnParty();
        p.name = trimToNull(i.name);
        p.line1 = trimToNull(i.street);
        p.postcode = trimToNull(i.postcode);
        p.city = trimToNull(i.city);
        p.countryCode = orDefault(i.countryCode, "DE");
        p.contactName = trimToNull(i.contactName);
        p.contactPhone = trimToNull(i.phone);
        p.contactEmail = trimToNull(i.email);
        p.vatId = trimToNull(i.vatId);
        p.taxNumber = trimToNull(i.taxNumber);
        p.electronicAddress = trimToNull(i.email);
        p.electronicAddressScheme = "EM";
        return p;
    }

    private static EnParty buyer(Customer c) {
        EnParty p = new EnParty();
        if (c == null) return p;
        // Falls back to the venue name, so an invoice can be drafted before the club sends its
        // registered name. EnValidator reports the shortfall; it is not this mapper's job.
        p.name = trimToNull(c.billingName());
        p.line1 = trimToNull(c.street);
        p.postcode = trimToNull(c.postcode);
        p.city = trimToNull(c.city);
        p.countryCode = orDefault(c.countryCode, "DE");
        p.contactName = trimToNull(c.contactName);
        p.contactPhone = trimToNull(c.phone);
        p.contactEmail = trimToNull(c.email);
        p.vatId = trimToNull(c.vatId);
        p.identifier = trimToNull(c.customerNumber);
        p.electronicAddress = trimToNull(c.email);
        p.electronicAddressScheme = "EM";
        return p;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String orDefault(String s, String fallback) {
        String t = trimToNull(s);
        return t == null ? fallback : t;
    }
}
