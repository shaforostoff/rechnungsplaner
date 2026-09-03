package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shaforostoff.rechnungsplaner.einvoice.TaxCategory;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.PatternFormatter;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes invoices, their lines, their generated files, and the number sequence. */
public class InvoiceDao {

    private final Db db;

    public InvoiceDao(Context ctx) {
        this.db = Db.get(ctx);
    }

    /**
     * Issues an invoice: allocates its number, writes the header and lines, and marks the gigs as
     * billed, all in one transaction.
     *
     * <p>Doing the number allocation inside the transaction is the point. Two quick taps on
     * "create" would otherwise read the same counter and mint the same number twice, and a
     * duplicate invoice number is exactly the kind of thing a Betriebsprüfung notices.
     *
     * <p>A number already set on the invoice is honoured rather than replaced, and the year's
     * series is moved up to it. That pair is what a mid-year switch needs: the first invoice is
     * given the number following the last one the old software issued, and every invoice after it
     * carries on from there without being told again.
     *
     * @param numberPattern the user's pattern, e.g. {@code %Y%-%seq3%}
     * @param gigIds        the gigs this invoice bills; may be empty for a manual invoice
     */
    public long issue(Invoice invoice, String numberPattern, List<Long> gigIds) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            if (invoice.number == null || invoice.number.trim().isEmpty()) {
                invoice.number = allocateNumber(w, numberPattern, invoice.issueDate);
            } else {
                invoice.number = invoice.number.trim();
                adoptSequence(w, numberPattern, invoice.issueDate, invoice.number);
            }
            invoice.createdAt = System.currentTimeMillis();
            invoice.id = w.insertOrThrow(Db.T_INVOICE, null, values(invoice));

            int lineNo = 1;
            for (InvoiceLine line : invoice.lines) {
                line.invoiceId = invoice.id;
                line.lineNo = lineNo++;
                line.id = w.insertOrThrow(Db.T_INVOICE_LINE, null, values(line));
            }

            if (gigIds != null) {
                ContentValues v = new ContentValues();
                v.put("invoice_id", invoice.id);
                v.put("status", Gig.Status.INVOICED.name());
                for (Long gigId : gigIds) {
                    w.update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gigId)});
                }
            }

            w.setTransactionSuccessful();
            return invoice.id;
        } finally {
            w.endTransaction();
        }
    }

    /**
     * Rewrites an already-issued invoice in place, keeping its number.
     *
     * <p>A correction of one document rather than a second one: same row, same number, same
     * {@code created_at}, and the counter untouched. That is right for the case this exists for --
     * a fee that changed, a customer whose details arrived late, an address that was stale when
     * the invoice was first written -- and wrong for an invoice the customer has already paid
     * against, which German practice cancels with a credit note instead. The UI owns that
     * distinction, as it does for {@link #deleteDraft}.
     *
     * @param gigIds the gigs the corrected invoice bills, which need not be the original set
     */
    public void reissue(Invoice invoice, List<Long> gigIds) {
        if (invoice.id <= 0L) {
            throw new IllegalArgumentException("reissue needs an invoice that exists");
        }
        if (invoice.number == null || invoice.number.trim().isEmpty()) {
            throw new IllegalArgumentException("reissue must keep the original number");
        }
        List<Long> billed = gigIds == null ? new ArrayList<Long>() : gigIds;
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            String id = Long.toString(invoice.id);
            w.update(Db.T_INVOICE, values(invoice), "_id = ?", new String[]{id});
            w.delete(Db.T_INVOICE_LINE, "invoice_id = ?", new String[]{id});

            int lineNo = 1;
            for (InvoiceLine line : invoice.lines) {
                line.invoiceId = invoice.id;
                line.lineNo = lineNo++;
                line.id = w.insertOrThrow(Db.T_INVOICE_LINE, null, values(line));
            }

            List<Long> wereBilled = gigIdsFor(w, invoice.id);
            for (Long gigId : wereBilled) {
                if (billed.contains(gigId)) continue;
                // Dropped from the invoice, so back to billable -- otherwise it stays marked
                // invoiced while pointing at an invoice that no longer bills it.
                ContentValues v = new ContentValues();
                v.put("invoice_id", -1L);
                v.put("status", Gig.Status.PLAYED.name());
                w.update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gigId)});
            }
            for (Long gigId : billed) {
                // A gig that was already on this invoice is left alone: if it is marked paid, the
                // payment happened and correcting the document does not undo it.
                if (wereBilled.contains(gigId)) continue;
                ContentValues v = new ContentValues();
                v.put("invoice_id", invoice.id);
                v.put("status", Gig.Status.INVOICED.name());
                w.update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gigId)});
            }

            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    private static List<Long> gigIdsFor(SQLiteDatabase w, long invoiceId) {
        List<Long> out = new ArrayList<Long>();
        Cursor c = w.query(Db.T_GIG, new String[]{"_id"}, "invoice_id = ?",
                new String[]{Long.toString(invoiceId)}, null, null, null);
        try {
            while (c.moveToNext()) out.add(Long.valueOf(c.getLong(0)));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Consumes the next number in the year's series.
     *
     * <p>Section 14 UStG wants the series unique, not gapless, so a number burnt by a cancelled
     * draft is simply never reused. The counter is per year and resets on 1 January.
     */
    private String allocateNumber(SQLiteDatabase w, String pattern, String issueDate) {
        String key = counterKey(issueDate);
        int next = counterValue(w, key) + 1;
        setCounter(w, key, next);
        return new PatternFormatter().putDate(issueDate).putSequence(next).format(pattern);
    }

    /**
     * Moves the year's series up to a number that was entered by hand.
     *
     * <p>This is what makes a mid-year switch to this app work. The first invoice here is given
     * the number following the last one the previous software issued; without adopting it the
     * counter would still be at zero and the second invoice would restart the series at 1,
     * colliding with the old books.
     *
     * <p>Only a number the pattern can place is adopted, and the series only ever moves forward:
     * a number typed below where the series already stands is a correction to the past, not an
     * instruction to reissue everything after it. A number the pattern cannot place leaves the
     * counter alone -- the invoice screen says so before it gets this far.
     */
    private void adoptSequence(SQLiteDatabase w, String pattern, String issueDate, String number) {
        int sequence = PatternFormatter.extractSequence(pattern, number);
        String key = counterKey(issueDate);
        if (sequence < 0 || sequence <= counterValue(w, key)) return;
        setCounter(w, key, sequence);
    }

    /** What the next number would be, for the preview in the invoice screen. Consumes nothing. */
    public String peekNextNumber(String pattern, String issueDate) {
        int next = counterValue(db.getReadableDatabase(), counterKey(issueDate)) + 1;
        return new PatternFormatter().putDate(issueDate).putSequence(next).format(pattern);
    }

    /**
     * Whether a number is already spent.
     *
     * <p>The column is UNIQUE, so this exists to turn what would be a constraint exception mid
     * transaction into something the screen can say. Deliberately case-insensitive where the
     * constraint is not: a series carrying both {@code RE-2026-038} and {@code re-2026-038} is
     * unique only to SQLite.
     */
    public boolean numberExists(String number) {
        if (number == null || number.trim().isEmpty()) return false;
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE, new String[]{"_id"},
                "number = ? COLLATE NOCASE", new String[]{number.trim()}, null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static String counterKey(String issueDate) {
        String year = Dates.isValid(issueDate) ? issueDate.substring(0, 4)
                : Dates.today().substring(0, 4);
        return "invoice_seq_" + year;
    }

    private static int counterValue(SQLiteDatabase r, String key) {
        Cursor c = r.query(Db.T_COUNTER, new String[]{"value"}, "key = ?", new String[]{key},
                null, null, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static void setCounter(SQLiteDatabase w, String key, int value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        w.replace(Db.T_COUNTER, null, v);
    }

    public List<Invoice> all() {
        List<Invoice> out = new ArrayList<Invoice>();
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE, null, null, null, null, null,
                "issue_date DESC, _id DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** The header plus its lines. */
    public Invoice byId(long id) {
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE, null, "_id = ?",
                new String[]{Long.toString(id)}, null, null, null);
        Invoice invoice;
        try {
            if (!c.moveToFirst()) return null;
            invoice = read(c);
        } finally {
            c.close();
        }
        invoice.lines = linesFor(id);
        return invoice;
    }

    public List<InvoiceLine> linesFor(long invoiceId) {
        List<InvoiceLine> out = new ArrayList<InvoiceLine>();
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE_LINE, null, "invoice_id = ?",
                new String[]{Long.toString(invoiceId)}, null, null, "line_no");
        try {
            while (c.moveToNext()) out.add(readLine(c));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Deletes an unsent invoice and releases its gigs.
     *
     * <p>Only ever offered for a draft. An invoice that has left the building must be cancelled
     * with a credit note, not deleted, and the UI is responsible for not offering this then.
     */
    public void deleteDraft(long invoiceId) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("invoice_id", -1L);
            v.put("status", Gig.Status.PLAYED.name());
            w.update(Db.T_GIG, v, "invoice_id = ?", new String[]{Long.toString(invoiceId)});
            w.delete(Db.T_INVOICE, "_id = ?", new String[]{Long.toString(invoiceId)});
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    public long addFile(InvoiceFile file) {
        ContentValues v = new ContentValues();
        v.put("invoice_id", file.invoiceId);
        v.put("format", file.format);
        v.put("file_name", file.fileName);
        v.put("rel_path", file.relPath);
        v.put("created_at", file.createdAt == 0L ? System.currentTimeMillis() : file.createdAt);
        file.id = db.getWritableDatabase().insert(Db.T_INVOICE_FILE, null, v);
        return file.id;
    }

    /**
     * The number of the invoice that superseded this one, or null while it is still the current
     * one. Looked up rather than stored on the old row, so there is one fact and not two.
     */
    public String replacementNumberOf(long invoiceId) {
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE, new String[]{"number"},
                "replaces_id = ?", new String[]{Long.toString(invoiceId)}, null, null,
                "issue_date DESC, _id DESC", "1");
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    /** Forgets the files recorded for an invoice, for when they are about to be replaced. */
    public void clearFiles(long invoiceId) {
        db.getWritableDatabase().delete(Db.T_INVOICE_FILE, "invoice_id = ?",
                new String[]{Long.toString(invoiceId)});
    }

    public List<InvoiceFile> filesFor(long invoiceId) {
        List<InvoiceFile> out = new ArrayList<InvoiceFile>();
        Cursor c = db.getReadableDatabase().query(Db.T_INVOICE_FILE, null, "invoice_id = ?",
                new String[]{Long.toString(invoiceId)}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) {
                InvoiceFile f = new InvoiceFile();
                f.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                f.invoiceId = c.getLong(c.getColumnIndexOrThrow("invoice_id"));
                f.format = c.getString(c.getColumnIndexOrThrow("format"));
                f.fileName = c.getString(c.getColumnIndexOrThrow("file_name"));
                f.relPath = c.getString(c.getColumnIndexOrThrow("rel_path"));
                f.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private static ContentValues values(Invoice i) {
        ContentValues v = new ContentValues();
        v.put("number", i.number);
        v.put("issue_date", i.issueDate);
        v.put("due_date", i.dueDate);
        v.put("customer_id", i.customerId);
        v.put("currency", i.currency);
        v.put("tax_mode", i.taxMode == null ? null : i.taxMode.name());
        v.put("rate_permille", i.ratePermille);
        v.put("exemption_text", i.exemptionText);
        v.put("exemption_code", i.exemptionCode);
        v.put("buyer_reference", i.buyerReference);
        v.put("language", i.language == null ? "de" : i.language);
        v.put("delivery_date", i.deliveryDate);
        v.put("period_start", i.periodStart);
        v.put("period_end", i.periodEnd);
        v.put("note", i.note);
        v.put("payment_terms", i.paymentTerms);
        v.put("line_total_cents", i.lineTotalCents);
        v.put("tax_basis_cents", i.taxBasisCents);
        v.put("tax_total_cents", i.taxTotalCents);
        v.put("grand_total_cents", i.grandTotalCents);
        v.put("prepaid_cents", i.prepaidCents);
        v.put("due_payable_cents", i.duePayableCents);
        v.put("issuer_snapshot", i.issuerSnapshot);
        v.put("customer_snapshot", i.customerSnapshot);
        v.put("replaces_id", i.replacesId);
        v.put("replaces_number", i.replacesNumber);
        v.put("replaces_date", i.replacesDate);
        v.put("created_at", i.createdAt);
        return v;
    }

    private static ContentValues values(InvoiceLine l) {
        ContentValues v = new ContentValues();
        v.put("invoice_id", l.invoiceId);
        v.put("gig_id", l.gigId);
        v.put("line_no", l.lineNo);
        v.put("name", l.name);
        v.put("description", l.description);
        v.put("quantity_milli", l.quantityMilli);
        v.put("unit_code", l.unitCode);
        v.put("unit_price_cents", l.unitPriceCents);
        v.put("net_cents", l.netCents);
        v.put("tax_category", l.taxCategory == null ? "S" : l.taxCategory.code);
        v.put("rate_permille", l.ratePermille);
        v.put("period_start", l.periodStart);
        v.put("period_end", l.periodEnd);
        return v;
    }

    static Invoice read(Cursor c) {
        Invoice i = new Invoice();
        i.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        i.number = c.getString(c.getColumnIndexOrThrow("number"));
        i.issueDate = c.getString(c.getColumnIndexOrThrow("issue_date"));
        i.dueDate = c.getString(c.getColumnIndexOrThrow("due_date"));
        i.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
        i.currency = c.getString(c.getColumnIndexOrThrow("currency"));
        i.taxMode = TaxMode.fromName(c.getString(c.getColumnIndexOrThrow("tax_mode")),
                TaxMode.KLEINUNTERNEHMER);
        i.ratePermille = c.getInt(c.getColumnIndexOrThrow("rate_permille"));
        i.exemptionText = c.getString(c.getColumnIndexOrThrow("exemption_text"));
        i.exemptionCode = c.getString(c.getColumnIndexOrThrow("exemption_code"));
        i.buyerReference = c.getString(c.getColumnIndexOrThrow("buyer_reference"));
        i.language = c.getString(c.getColumnIndexOrThrow("language"));
        i.deliveryDate = c.getString(c.getColumnIndexOrThrow("delivery_date"));
        i.periodStart = c.getString(c.getColumnIndexOrThrow("period_start"));
        i.periodEnd = c.getString(c.getColumnIndexOrThrow("period_end"));
        i.note = c.getString(c.getColumnIndexOrThrow("note"));
        i.paymentTerms = c.getString(c.getColumnIndexOrThrow("payment_terms"));
        i.lineTotalCents = c.getLong(c.getColumnIndexOrThrow("line_total_cents"));
        i.taxBasisCents = c.getLong(c.getColumnIndexOrThrow("tax_basis_cents"));
        i.taxTotalCents = c.getLong(c.getColumnIndexOrThrow("tax_total_cents"));
        i.grandTotalCents = c.getLong(c.getColumnIndexOrThrow("grand_total_cents"));
        i.prepaidCents = c.getLong(c.getColumnIndexOrThrow("prepaid_cents"));
        i.duePayableCents = c.getLong(c.getColumnIndexOrThrow("due_payable_cents"));
        i.issuerSnapshot = c.getString(c.getColumnIndexOrThrow("issuer_snapshot"));
        i.customerSnapshot = c.getString(c.getColumnIndexOrThrow("customer_snapshot"));
        i.replacesId = c.getLong(c.getColumnIndexOrThrow("replaces_id"));
        i.replacesNumber = c.getString(c.getColumnIndexOrThrow("replaces_number"));
        i.replacesDate = c.getString(c.getColumnIndexOrThrow("replaces_date"));
        i.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return i;
    }

    static InvoiceLine readLine(Cursor c) {
        InvoiceLine l = new InvoiceLine();
        l.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        l.invoiceId = c.getLong(c.getColumnIndexOrThrow("invoice_id"));
        l.gigId = c.getLong(c.getColumnIndexOrThrow("gig_id"));
        l.lineNo = c.getInt(c.getColumnIndexOrThrow("line_no"));
        l.name = c.getString(c.getColumnIndexOrThrow("name"));
        l.description = c.getString(c.getColumnIndexOrThrow("description"));
        l.quantityMilli = c.getLong(c.getColumnIndexOrThrow("quantity_milli"));
        l.unitCode = c.getString(c.getColumnIndexOrThrow("unit_code"));
        l.unitPriceCents = c.getLong(c.getColumnIndexOrThrow("unit_price_cents"));
        l.netCents = c.getLong(c.getColumnIndexOrThrow("net_cents"));
        l.taxCategory = TaxCategory.fromCode(c.getString(c.getColumnIndexOrThrow("tax_category")),
                TaxCategory.S);
        l.ratePermille = c.getInt(c.getColumnIndexOrThrow("rate_permille"));
        l.periodStart = c.getString(c.getColumnIndexOrThrow("period_start"));
        l.periodEnd = c.getString(c.getColumnIndexOrThrow("period_end"));
        return l;
    }
}
