package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads and writes {@link Gig} rows. */
public class GigDao {

    private final Db db;

    public GigDao(Context ctx) {
        this.db = Db.get(ctx);
    }

    /** Gigs whose date falls in {@code [from, to]}, inclusive. ISO dates sort chronologically. */
    public List<Gig> between(String fromIso, String toIso) {
        return query("date >= ? AND date <= ?", new String[]{fromIso, toIso}, "date, start_millis");
    }

    /** Everything from today onwards, for the tour-list export. */
    public List<Gig> upcoming() {
        return query("date >= ?", new String[]{Dates.today()}, "date, start_millis");
    }

    public List<Gig> forCustomer(long customerId) {
        return query("customer_id = ?", new String[]{Long.toString(customerId)}, "date DESC");
    }

    /** Played but not yet billed, which is what the invoice screen offers to bill together. */
    public List<Gig> billableFor(long customerId) {
        return query("customer_id = ? AND invoice_id <= 0",
                new String[]{Long.toString(customerId)}, "date");
    }

    public List<Gig> forInvoice(long invoiceId) {
        return query("invoice_id = ?", new String[]{Long.toString(invoiceId)}, "date");
    }

    public Gig byId(long id) {
        List<Gig> list = query("_id = ?", new String[]{Long.toString(id)}, null);
        return list.isEmpty() ? null : list.get(0);
    }

    public Gig bySyncUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        List<Gig> list = query("sync_uuid = ?", new String[]{uuid}, null);
        return list.isEmpty() ? null : list.get(0);
    }

    /** How many gigs fall on each date in the range, for the month grid's day markers. */
    public Map<String, Integer> countsByDate(String fromIso, String toIso) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT date, COUNT(*) FROM " + Db.T_GIG + " WHERE date >= ? AND date <= ?"
                        + " GROUP BY date", new String[]{fromIso, toIso});
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        } finally {
            c.close();
        }
        return out;
    }

    public long save(Gig gig) {
        if (gig.syncUuid == null || gig.syncUuid.isEmpty()) {
            gig.syncUuid = UUID.randomUUID().toString();
        }
        SQLiteDatabase w = db.getWritableDatabase();
        if (gig.id > 0L) {
            w.update(Db.T_GIG, editableValues(gig), "_id = ?",
                    new String[]{Long.toString(gig.id)});
        } else {
            gig.id = w.insert(Db.T_GIG, null, values(gig));
        }
        return gig.id;
    }

    public void delete(long id) {
        db.getWritableDatabase().delete(Db.T_GIG, "_id = ?", new String[]{Long.toString(id)});
    }

    /**
     * Points a gig at an invoice without touching anything else.
     *
     * <p>The way back for a gig that lost its link. Targeted rather than a whole-row write for
     * the same reason {@link #editableValues} exists: the caller's copy of the gig may be older
     * than the row.
     */
    public void setInvoice(long gigId, long invoiceId, Gig.Status status) {
        ContentValues v = new ContentValues();
        v.put("invoice_id", invoiceId);
        v.put("status", status.name());
        db.getWritableDatabase().update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gigId)});
    }

    /** Records the mirrored calendar event without touching anything else. */
    public void setCalendarEvent(long gigId, long calendarId, long eventId) {
        ContentValues v = new ContentValues();
        v.put("calendar_id", calendarId);
        v.put("calendar_event_id", eventId);
        db.getWritableDatabase().update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gigId)});
    }

    private List<Gig> query(String selection, String[] args, String order) {
        List<Gig> out = new ArrayList<Gig>();
        Cursor c = db.getReadableDatabase().query(Db.T_GIG, null, selection, args, null, null,
                order);
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * What an update may write: everything except the fields another screen owns.
     *
     * <p>A {@link Gig} in hand is easily older than the row it came from. The gig screen stays on
     * the back stack while an invoice is created from it, so a gig invoiced a moment ago is still
     * un-invoiced in the object that screen is holding -- and writing every column back from it
     * unbilled the gig, reset its status, and orphaned its calendar event. The invoice link
     * belongs to {@link InvoiceDao}, the calendar link to {@link #setCalendarEvent}, and the sync
     * uuid never changes once set, so an update leaves all four alone. {@link #setInvoice} is the
     * one other way the link moves, and it writes that column and nothing else.
     *
     * <p>Derived from {@link #values} rather than listed again, so a new column is covered by
     * default and only has to be named here if it turns out to be someone else's.
     */
    private static ContentValues editableValues(Gig g) {
        ContentValues v = values(g);
        v.remove("invoice_id");
        v.remove("calendar_id");
        v.remove("calendar_event_id");
        v.remove("sync_uuid");
        return v;
    }

    /** Every column, for an insert. */
    private static ContentValues values(Gig g) {
        ContentValues v = new ContentValues();
        v.put("date", g.date);
        v.put("start_millis", g.startMillis);
        v.put("end_millis", g.endMillis);
        v.put("customer_id", g.customerId);
        v.put("place_name", g.placeName);
        v.put("city", g.city);
        v.put("fee_cents", g.feeCents);
        v.put("travel_cents", g.travelCents);
        v.put("tax_mode", g.taxMode == null ? null : g.taxMode.name());
        v.put("title", g.title);
        v.put("notes", g.notes);
        v.put("status", g.status == null ? Gig.Status.PLANNED.name() : g.status.name());
        v.put("service_id", g.serviceId);
        v.put("end_date", g.endDate);
        v.put("invoice_id", g.invoiceId);
        v.put("calendar_id", g.calendarId);
        v.put("calendar_event_id", g.calendarEventId);
        v.put("sync_uuid", g.syncUuid);
        return v;
    }

    static Gig read(Cursor c) {
        Gig g = new Gig();
        g.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        g.date = c.getString(c.getColumnIndexOrThrow("date"));
        g.startMillis = c.getLong(c.getColumnIndexOrThrow("start_millis"));
        g.endMillis = c.getLong(c.getColumnIndexOrThrow("end_millis"));
        g.customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
        g.placeName = c.getString(c.getColumnIndexOrThrow("place_name"));
        g.city = c.getString(c.getColumnIndexOrThrow("city"));
        g.feeCents = c.getLong(c.getColumnIndexOrThrow("fee_cents"));
        g.travelCents = c.getLong(c.getColumnIndexOrThrow("travel_cents"));
        g.taxMode = TaxMode.fromName(c.getString(c.getColumnIndexOrThrow("tax_mode")), null);
        g.title = c.getString(c.getColumnIndexOrThrow("title"));
        g.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        g.status = Gig.Status.fromName(c.getString(c.getColumnIndexOrThrow("status")),
                Gig.Status.PLANNED);
        g.serviceId = c.getLong(c.getColumnIndexOrThrow("service_id"));
        g.endDate = c.getString(c.getColumnIndexOrThrow("end_date"));
        g.invoiceId = c.getLong(c.getColumnIndexOrThrow("invoice_id"));
        g.calendarId = c.getLong(c.getColumnIndexOrThrow("calendar_id"));
        g.calendarEventId = c.getLong(c.getColumnIndexOrThrow("calendar_event_id"));
        g.syncUuid = c.getString(c.getColumnIndexOrThrow("sync_uuid"));
        return g;
    }
}
