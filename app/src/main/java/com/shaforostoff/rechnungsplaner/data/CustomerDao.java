package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.PatternFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes {@link Customer} rows. */
public class CustomerDao {

    private static final String COUNTER_KEY = "customer_seq";

    /** How far the sequence may step past taken numbers before the attempt is abandoned. */
    private static final int COLLISION_ATTEMPTS = 100;

    private final Db db;

    public CustomerDao(Context ctx) {
        this.db = Db.get(ctx);
    }

    public List<Customer> all(boolean includeArchived) {
        List<Customer> out = new ArrayList<Customer>();
        Cursor c = db.getReadableDatabase().query(Db.T_CUSTOMER, null,
                includeArchived ? null : "archived = 0", null, null, null,
                "COALESCE(NULLIF(official_name, ''), place_name, city) COLLATE NOCASE");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Customer byId(long id) {
        if (id <= 0L) return null;
        Cursor c = db.getReadableDatabase().query(Db.T_CUSTOMER, null, "_id = ?",
                new String[]{Long.toString(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Writes the customer, numbering it first when it is new and has no number of its own.
     *
     * @param numberPattern the pattern from settings, or null/empty to leave numbering to the user
     */
    public long save(Customer customer, String numberPattern) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            boolean isNew = customer.id <= 0L;
            if (isNew && isBlank(customer.customerNumber) && !isBlank(numberPattern)) {
                customer.customerNumber = allocateNumber(w, numberPattern);
            }
            ContentValues v = values(customer);
            if (isNew) {
                customer.id = w.insert(Db.T_CUSTOMER, null, v);
            } else {
                w.update(Db.T_CUSTOMER, v, "_id = ?", new String[]{Long.toString(customer.id)});
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        return customer.id;
    }

    /**
     * The customer holding this number, or null when it is free.
     *
     * <p>Archived customers count: they still hold their number in the books the numbering has to
     * stay consistent with. Matching is case-insensitive so {@code k-007} cannot slip past
     * {@code K-007}.
     *
     * @param excludeId the record being edited, so a customer never clashes with itself
     */
    public Customer holderOfNumber(String number, long excludeId) {
        return holderOf(db.getReadableDatabase(), number, excludeId);
    }

    private Customer holderOf(SQLiteDatabase r, String number, long excludeId) {
        if (isBlank(number)) return null;
        Cursor c = r.query(Db.T_CUSTOMER, null,
                "customer_number = ? COLLATE NOCASE AND _id <> ?",
                new String[]{number.trim(), Long.toString(excludeId)}, null, null, null, "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Consumes the next customer number.
     *
     * <p>Allocating inside the caller's transaction is what stops two inserts in flight from
     * being handed the same number -- the same reason invoice numbers are allocated that way.
     *
     * <p>The sequence is stepped past anything already taken. The {@code MAX} scan puts it beyond
     * every plain number on file, but a pattern like {@code K-%seq3%} produces numbers that scan
     * cannot see, so a hand-typed {@code K-005} sitting mid-series would otherwise be minted a
     * second time.
     *
     * @return the number, or null when the pattern cannot produce a free one
     */
    private String allocateNumber(SQLiteDatabase w, String pattern) {
        int next = nextSequence(w);
        for (int attempt = 0; attempt < COLLISION_ATTEMPTS; attempt++, next++) {
            String candidate = formatNumber(pattern, next);
            if (holderOf(w, candidate, 0L) != null) continue;
            ContentValues v = new ContentValues();
            v.put("key", COUNTER_KEY);
            v.put("value", next);
            w.replace(Db.T_COUNTER, null, v);
            return candidate;
        }
        // A pattern holding no sequence token produces one number and no more, so stepping the
        // sequence never gets past a collision. Leaving the field empty is the recoverable
        // outcome: the user can type a number, where a duplicate would need finding first.
        return null;
    }

    /**
     * Expands a customer-number pattern for one place in the series.
     *
     * <p>Static and shared with the settings preview, so what the preview promises and what the
     * next customer actually gets cannot drift apart. Only the sequence and creation-date tokens
     * are registered: anything else -- a city, a customer name -- would need a series of its own
     * to be meaningful, and PatternFormatter leaves an unregistered token visible so a pattern
     * reaching for one shows up wrong in the preview rather than quietly later.
     */
    public static String formatNumber(String pattern, int sequence) {
        return new PatternFormatter().putSequence(sequence).putDate(Dates.today()).format(pattern);
    }

    /** Where the series stands, for the preview in settings. Consumes nothing. */
    public int peekNextSequence() {
        return nextSequence(db.getReadableDatabase());
    }

    /**
     * One past whichever is higher: the counter, or the highest all-digit number already on file.
     *
     * <p>Taking the file into account is the point of the whole feature. The numbers that matter
     * arrive from the previous invoicing software through a contacts import, which writes them
     * without touching the counter, so a counter consulted alone would start at 1 and collide
     * with the imported series immediately. Numbers in a shape the sequence cannot continue --
     * {@code K-007} and the like -- are ignored here and left to the counter.
     */
    private int nextSequence(SQLiteDatabase r) {
        return Math.max(counterValue(r), highestPlainNumber(r)) + 1;
    }

    private int counterValue(SQLiteDatabase r) {
        Cursor c = r.query(Db.T_COUNTER, new String[]{"value"}, "key = ?",
                new String[]{COUNTER_KEY}, null, null, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private int highestPlainNumber(SQLiteDatabase r) {
        // Starts with a digit and holds nothing but digits; CAST then reads it as the number it is,
        // so a padded "0042" and a bare "42" count as the same place in the series.
        Cursor c = r.rawQuery("SELECT MAX(CAST(customer_number AS INTEGER)) FROM " + Db.T_CUSTOMER
                + " WHERE customer_number GLOB '[0-9]*'"
                + " AND customer_number NOT GLOB '*[^0-9]*'", null);
        try {
            // getInt of a NULL max -- no numeric customer numbers yet -- is 0, which is the floor.
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Deletes only when nothing references the customer; otherwise archives.
     *
     * @return true when the row was deleted, false when it was archived instead
     */
    public boolean deleteOrArchive(long id) {
        SQLiteDatabase w = db.getWritableDatabase();
        if (countReferencing(w, Db.T_GIG, id) == 0 && countReferencing(w, Db.T_INVOICE, id) == 0) {
            w.delete(Db.T_CUSTOMER, "_id = ?", new String[]{Long.toString(id)});
            return true;
        }
        ContentValues v = new ContentValues();
        v.put("archived", 1);
        w.update(Db.T_CUSTOMER, v, "_id = ?", new String[]{Long.toString(id)});
        return false;
    }

    /**
     * Cities where gigs belong to more than one distinct customer.
     *
     * <p>The tour-list export names those gigs by venue rather than by city, because two lines
     * reading "Hamburg" would be indistinguishable to the reader.
     */
    public Map<String, Integer> customerCountByCity() {
        Map<String, Integer> out = new HashMap<String, Integer>();
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT LOWER(TRIM(city)) AS k, COUNT(DISTINCT customer_id) AS n FROM " + Db.T_GIG
                        + " WHERE city IS NOT NULL AND TRIM(city) <> '' GROUP BY k", null);
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        } finally {
            c.close();
        }
        return out;
    }

    /** Matches an imported contact to an existing row: lexoffice id first, then name and city. */
    public Customer findMatch(String lexofficeId, String name, String city) {
        SQLiteDatabase r = db.getReadableDatabase();
        if (lexofficeId != null && !lexofficeId.trim().isEmpty()) {
            Cursor c = r.query(Db.T_CUSTOMER, null, "lexoffice_id = ?",
                    new String[]{lexofficeId.trim()}, null, null, null, "1");
            try {
                if (c.moveToFirst()) return read(c);
            } finally {
                c.close();
            }
        }
        if (name == null || name.trim().isEmpty()) return null;
        String n = name.trim().toLowerCase(Locale.US);
        String ci = city == null ? "" : city.trim().toLowerCase(Locale.US);
        Cursor c = r.rawQuery("SELECT * FROM " + Db.T_CUSTOMER
                + " WHERE LOWER(TRIM(COALESCE(NULLIF(official_name,''), place_name))) = ?"
                + " AND LOWER(TRIM(COALESCE(city,''))) = ? LIMIT 1", new String[]{n, ci});
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    private static int countReferencing(SQLiteDatabase r, String table, long customerId) {
        Cursor c = r.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE customer_id = ?",
                new String[]{Long.toString(customerId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static ContentValues values(Customer c) {
        ContentValues v = new ContentValues();
        v.put("official_name", c.officialName);
        v.put("place_name", c.placeName);
        v.put("street", c.street);
        v.put("postcode", c.postcode);
        v.put("city", c.city);
        v.put("country_code", c.countryCode == null ? "DE" : c.countryCode);
        v.put("email", c.email);
        v.put("contact_name", c.contactName);
        v.put("phone", c.phone);
        v.put("vat_id", c.vatId);
        v.put("buyer_reference", c.buyerReference);
        v.put("customer_number", c.customerNumber);
        v.put("default_fee_cents", c.defaultFeeCents);
        v.put("default_tax_mode", c.defaultTaxMode == null ? null : c.defaultTaxMode.name());
        v.put("invoice_language", c.invoiceLanguage);
        v.put("share_subject", c.shareSubject);
        v.put("share_message", c.shareMessage);
        v.put("note", c.note);
        v.put("lexoffice_id", c.lexofficeId);
        v.put("archived", c.archived ? 1 : 0);
        return v;
    }

    static Customer read(Cursor c) {
        Customer out = new Customer();
        out.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        out.officialName = c.getString(c.getColumnIndexOrThrow("official_name"));
        out.placeName = c.getString(c.getColumnIndexOrThrow("place_name"));
        out.street = c.getString(c.getColumnIndexOrThrow("street"));
        out.postcode = c.getString(c.getColumnIndexOrThrow("postcode"));
        out.city = c.getString(c.getColumnIndexOrThrow("city"));
        out.countryCode = c.getString(c.getColumnIndexOrThrow("country_code"));
        out.email = c.getString(c.getColumnIndexOrThrow("email"));
        out.contactName = c.getString(c.getColumnIndexOrThrow("contact_name"));
        out.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        out.vatId = c.getString(c.getColumnIndexOrThrow("vat_id"));
        out.buyerReference = c.getString(c.getColumnIndexOrThrow("buyer_reference"));
        out.customerNumber = c.getString(c.getColumnIndexOrThrow("customer_number"));
        out.defaultFeeCents = c.getLong(c.getColumnIndexOrThrow("default_fee_cents"));
        out.defaultTaxMode = TaxMode.fromName(
                c.getString(c.getColumnIndexOrThrow("default_tax_mode")), null);
        out.invoiceLanguage = c.getString(c.getColumnIndexOrThrow("invoice_language"));
        out.shareSubject = c.getString(c.getColumnIndexOrThrow("share_subject"));
        out.shareMessage = c.getString(c.getColumnIndexOrThrow("share_message"));
        out.note = c.getString(c.getColumnIndexOrThrow("note"));
        out.lexofficeId = c.getString(c.getColumnIndexOrThrow("lexoffice_id"));
        out.archived = c.getInt(c.getColumnIndexOrThrow("archived")) != 0;
        return out;
    }
}
