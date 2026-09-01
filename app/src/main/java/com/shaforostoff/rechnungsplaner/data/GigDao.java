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
        ContentValues v = values(gig);
        SQLiteDatabase w = db.getWritableDatabase();
        if (gig.id > 0L) {
            w.update(Db.T_GIG, v, "_id = ?", new String[]{Long.toString(gig.id)});
        } else {
            gig.id = w.insert(Db.T_GIG, null, v);
        }
        return gig.id;
    }

    public void delete(long id) {
        db.getWritableDatabase().delete(Db.T_GIG, "_id = ?", new String[]{Long.toString(id)});
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
        g.invoiceId = c.getLong(c.getColumnIndexOrThrow("invoice_id"));
        g.calendarId = c.getLong(c.getColumnIndexOrThrow("calendar_id"));
        g.calendarEventId = c.getLong(c.getColumnIndexOrThrow("calendar_event_id"));
        g.syncUuid = c.getString(c.getColumnIndexOrThrow("sync_uuid"));
        return g;
    }
}
