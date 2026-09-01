package com.shaforostoff.rechnungsplaner.einvoice;

import java.util.ArrayList;
import java.util.List;

/**
 * The EN 16931 semantic invoice: one model, from which {@link UblWriter} and {@link CiiWriter}
 * produce every format this app offers.
 *
 * <p>Deliberately free of {@code android.*} imports so the whole package runs under plain JUnit.
 * Dates are {@code yyyy-MM-dd} strings throughout — {@code java.time} needs API 26 and this app
 * targets 24 without desugaring.
 */
public class EnInvoice {

    /** BT-1, the invoice number. */
    public String number;
    /** BT-2, {@code yyyy-MM-dd}. */
    public String issueDate;
    /** BT-9, {@code yyyy-MM-dd}. */
    public String dueDate;
    /** BT-3; {@code 380} is a commercial invoice. */
    public String typeCode = "380";
    /** BT-5, ISO 4217. */
    public String currency = "EUR";
    /** BT-10. Mandatory under XRechnung (BR-DE-15), which clubs rarely know about. */
    public String buyerReference;
    /** BT-22, a free-text note. */
    public String note;

    /** BT-72, the delivery/performance date. Used when the invoice covers a single gig. */
    public String deliveryDate;
    /** BT-73, the start of BG-14. Used when the invoice covers several gigs. */
    public String periodStart;
    /** BT-74. */
    public String periodEnd;

    /** BT-20, the payment terms as text. */
    public String paymentTerms;
    /** BT-81; {@code 58} is a SEPA credit transfer. */
    public String paymentMeansCode = "58";
    /** BT-83, the remittance information the payer should quote. */
    public String remittanceInformation;
    /** BT-84. */
    public String iban;
    /** BT-86. */
    public String bic;
    /** BT-85. */
    public String accountName;

    public EnParty seller = new EnParty();
    public EnParty buyer = new EnParty();

    public List<EnLine> lines = new ArrayList<EnLine>();
    /** BG-23, filled by {@link Totals#compute}. */
    public List<TaxBreakdown> taxBreakdowns = new ArrayList<TaxBreakdown>();

    /** BT-106. */
    public long lineTotalCents;
    /** BT-109. */
    public long taxBasisCents;
    /** BT-110. */
    public long taxTotalCents;
    /** BT-112. */
    public long grandTotalCents;
    /** BT-113. */
    public long prepaidCents;
    /** BT-115. */
    public long duePayableCents;

    /**
     * The invoice document's language tag ({@code de}, {@code en}, {@code es}).
     *
     * <p>Not an EN 16931 term — the XML is language-neutral. It travels with the model only so the
     * PDF renderer knows which label set to draw, independently of the device locale.
     */
    public String languageTag = "de";

    public EnLine addLine(EnLine line) {
        if (Str.isEmpty(line.id)) line.id = Integer.toString(lines.size() + 1);
        lines.add(line);
        return line;
    }
}
