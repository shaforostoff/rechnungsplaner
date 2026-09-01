package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * One finding from {@link EnValidator}.
 *
 * <p>The message is not built here. This package has no access to Android resources, and the
 * invoice can be rendered in a different language from the UI anyway, so a {@link #fieldKey} is
 * carried instead and the UI maps it to a localised label. {@link #detail} is a plain-English
 * fallback that keeps test output and logs readable.
 */
public class Problem {

    public enum Severity {
        /** Every target validator rejects the file. */
        ERROR,
        /** Accepted by the schema, but some validators or importers will complain. */
        WARNING
    }

    public static final String SELLER_NAME = "seller.name";
    public static final String SELLER_STREET = "seller.street";
    public static final String SELLER_CITY = "seller.city";
    public static final String SELLER_POSTCODE = "seller.postcode";
    public static final String SELLER_COUNTRY = "seller.country";
    public static final String SELLER_TAX_ID = "seller.taxId";
    public static final String SELLER_CONTACT_NAME = "seller.contactName";
    public static final String SELLER_CONTACT_PHONE = "seller.contactPhone";
    public static final String SELLER_CONTACT_EMAIL = "seller.contactEmail";
    public static final String SELLER_ENDPOINT = "seller.endpoint";
    public static final String SELLER_IBAN = "seller.iban";

    public static final String BUYER_NAME = "buyer.name";
    public static final String BUYER_STREET = "buyer.street";
    public static final String BUYER_CITY = "buyer.city";
    public static final String BUYER_POSTCODE = "buyer.postcode";
    public static final String BUYER_COUNTRY = "buyer.country";
    public static final String BUYER_ENDPOINT = "buyer.endpoint";
    public static final String BUYER_VAT_ID = "buyer.vatId";

    public static final String INVOICE_NUMBER = "invoice.number";
    public static final String INVOICE_ISSUE_DATE = "invoice.issueDate";
    public static final String INVOICE_CURRENCY = "invoice.currency";
    public static final String INVOICE_BUYER_REFERENCE = "invoice.buyerReference";
    public static final String INVOICE_DELIVERY_DATE = "invoice.deliveryDate";
    public static final String INVOICE_PAYMENT_DUE = "invoice.paymentDue";
    public static final String INVOICE_LINES = "invoice.lines";
    public static final String INVOICE_TOTALS = "invoice.totals";
    public static final String TAX_EXEMPTION_REASON = "tax.exemptionReason";
    public static final String TAX_RATE = "tax.rate";

    public final Severity severity;
    /** The EN 16931 business term, e.g. {@code BT-10}. Null when the finding spans several. */
    public final String term;
    /** The rule identifier, e.g. {@code BR-DE-15}. */
    public final String rule;
    /** Stable key the UI maps to a localised field label. */
    public final String fieldKey;
    /** Plain-English fallback description. */
    public final String detail;

    public Problem(Severity severity, String term, String rule, String fieldKey, String detail) {
        this.severity = severity;
        this.term = term;
        this.rule = rule;
        this.fieldKey = fieldKey;
        this.detail = detail;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity).append(' ').append(detail);
        if (term != null) sb.append(" (").append(term).append(')');
        if (rule != null) sb.append(" [").append(rule).append(']');
        return sb.toString();
    }
}
