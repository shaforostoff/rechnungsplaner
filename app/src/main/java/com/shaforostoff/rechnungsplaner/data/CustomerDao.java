package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes {@link Customer} rows. */
public class CustomerDao {

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

    public long save(Customer customer) {
        ContentValues v = values(customer);
        SQLiteDatabase w = db.getWritableDatabase();
        if (customer.id > 0L) {
            w.update(Db.T_CUSTOMER, v, "_id = ?", new String[]{Long.toString(customer.id)});
        } else {
            customer.id = w.insert(Db.T_CUSTOMER, null, v);
        }
        return customer.id;
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
        out.note = c.getString(c.getColumnIndexOrThrow("note"));
        out.lexofficeId = c.getString(c.getColumnIndexOrThrow("lexoffice_id"));
        out.archived = c.getInt(c.getColumnIndexOrThrow("archived")) != 0;
        return out;
    }
}
