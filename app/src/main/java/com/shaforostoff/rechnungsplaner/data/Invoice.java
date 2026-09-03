package com.shaforostoff.rechnungsplaner.data;

import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.List;

/**
 * An issued invoice.
 *
 * <p>The party details are snapshotted as JSON at issue time rather than joined from the customer
 * row. Correcting a club's address next year must not silently rewrite an invoice already sent to
 * them and filed with the tax office. The language is snapshotted for the same reason, so
 * re-exporting an old invoice reproduces the document that was actually sent.
 */
public class Invoice {

    public long id = -1L;

    /** BT-1. Unique across the whole series. */
    public String number;
    public String issueDate;
    public String dueDate;

    public long customerId = -1L;
    public String currency = "EUR";

    public TaxMode taxMode = TaxMode.KLEINUNTERNEHMER;
    public int ratePermille;
    public String exemptionText;
    public String exemptionCode;

    public String buyerReference;
    public String language = "de";

    /** BT-72, set when the invoice covers a single gig. */
    public String deliveryDate;
    /** BG-14, set when it covers several. */
    public String periodStart;
    public String periodEnd;

    public String note;
    public String paymentTerms;

    /**
     * The year the money arrived, or zero to take it from the work.
     *
     * <p>Not on the document and never printed: the invoice says when the work happened and when
     * payment was due, which is what the customer needs. This is for the other side of the desk,
     * where income is declared in the year it was received -- so a set played in December and paid
     * in January belongs to two different years at once, and only one of them is on the paper.
     *
     * <p>Zero rather than a boxed null, matching how the rest of these records spell "not set",
     * and it means every invoice that predates the column already answers correctly.
     */
    public int paidYear;

    public long lineTotalCents;
    public long taxBasisCents;
    public long taxTotalCents;
    public long grandTotalCents;
    public long prepaidCents;
    public long duePayableCents;

    public String issuerSnapshot;
    public String customerSnapshot;
    public long createdAt;

    public List<InvoiceLine> lines = new ArrayList<InvoiceLine>();

    /**
     * The invoice this one corrects, when it was issued to replace one already sent.
     *
     * <p>The number and date are copied rather than looked up, for the same reason the party
     * details are: the document states what it corrects, and that statement must not change if
     * the earlier invoice is later touched.
     */
    public long replacesId = -1L;
    public String replacesNumber;
    public String replacesDate;

    /**
     * Makes this freshly built invoice a correction of {@code original} rather than a new
     * document: same row, same number, same creation time.
     *
     * <p>Only those three. Everything else on a correction is deliberately the newly computed
     * value -- the totals, the party snapshots, the dates derived from current settings -- because
     * being wrong or stale is the reason for redoing one at all. Section 14 UStG wants the number
     * series unique, which a correction reusing its own number does not break; issuing a second
     * document under the same number would.
     */
    /**
     * The year this invoice counts in.
     *
     * <p>{@link #paidYear} when it has been set, otherwise the year of the work: the delivery date
     * for a single DJ-set, the start of the period for several, and the issue date only when
     * neither is recorded. The work is a better default than the issue date because an invoice
     * written on 2 January for a gig on 28 December is income of the year it was paid in, not of
     * the year it happens to be dated.
     *
     * @return the year, or 0 when no date on this invoice can be read
     */
    public int taxYear() {
        if (paidYear > 0) return paidYear;
        return serviceYear();
    }

    /**
     * The year the work falls in, ignoring any payment year set by hand.
     *
     * <p>The fixed point the payment year is offered relative to: an invoice may be marked paid in
     * this year or the one after, and never drifts further because this does not move.
     */
    public int serviceYear() {
        if (Dates.isValid(deliveryDate)) return Dates.year(deliveryDate);
        if (Dates.isValid(periodStart)) return Dates.year(periodStart);
        return Dates.isValid(issueDate) ? Dates.year(issueDate) : 0;
    }

    public void takeIdentityFrom(Invoice original) {
        this.id = original.id;
        this.number = original.number;
        this.createdAt = original.createdAt;
    }

    /**
     * Records that this invoice supersedes {@code sent} under a new number.
     *
     * <p>The opposite of {@link #takeIdentityFrom}: that one corrects a document in place, this
     * one issues a second document that says what the first got wrong. Which is right depends on
     * whether the first has left the building -- a question only the user can answer.
     */
    public void supersede(Invoice sent) {
        this.replacesId = sent.id;
        this.replacesNumber = sent.number;
        this.replacesDate = sent.issueDate;
    }
}
