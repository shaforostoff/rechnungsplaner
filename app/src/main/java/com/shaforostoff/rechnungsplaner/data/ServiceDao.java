package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes the kinds of work the user offers. */
public class ServiceDao {

    private final Db db;

    public ServiceDao(Context ctx) {
        this.db = Db.get(ctx);
    }

    public List<Service> all(boolean includeArchived) {
        List<Service> out = new ArrayList<Service>();
        Cursor c = db.getReadableDatabase().query(Db.T_SERVICE, null,
                includeArchived ? null : "archived = 0", null, null, null,
                "sort_order, name COLLATE NOCASE");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Service byId(long id) {
        if (id <= 0L) return null;
        Cursor c = db.getReadableDatabase().query(Db.T_SERVICE, null, "_id = ?",
                new String[]{Long.toString(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * The name to show for a job's service, or null when it has none.
     *
     * <p>Reads the archived ones too: a job keeps the name of what it was even after that kind of
     * work has been retired from the buttons.
     */
    public String nameOf(long id) {
        Service service = byId(id);
        return service == null ? null : service.displayName();
    }

    public long save(Service service) {
        ContentValues v = new ContentValues();
        v.put("name", service.name);
        v.put("multi_day", service.multiDay ? 1 : 0);
        v.put("sync_calendar", service.syncToCalendar ? 1 : 0);
        v.put("sort_order", service.sortOrder);
        v.put("archived", service.archived ? 1 : 0);
        SQLiteDatabase w = db.getWritableDatabase();
        if (service.id > 0L) {
            w.update(Db.T_SERVICE, v, "_id = ?", new String[]{Long.toString(service.id)});
        } else {
            // Appended, so a new kind of work does not jump ahead of the ones already in use.
            if (service.sortOrder == 0) v.put("sort_order", nextSortOrder(w));
            service.id = w.insert(Db.T_SERVICE, null, v);
        }
        return service.id;
    }

    /**
     * Removes the service, or archives it when a job still names it.
     *
     * <p>Deleting outright would leave those jobs pointing at nothing, and a job that no longer
     * knows what was done is not a record of anything. Archiving keeps the name reachable while
     * taking the button away, which is what "delete" means for a service that has been used.
     *
     * @return true when the row was actually deleted
     */
    public boolean deleteOrArchive(long id) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            boolean inUse = countUsing(w, id) > 0;
            if (inUse) {
                ContentValues v = new ContentValues();
                v.put("archived", 1);
                w.update(Db.T_SERVICE, v, "_id = ?", new String[]{Long.toString(id)});
            } else {
                w.delete(Db.T_SERVICE, "_id = ?", new String[]{Long.toString(id)});
            }
            w.setTransactionSuccessful();
            return !inUse;
        } finally {
            w.endTransaction();
        }
    }

    private static int countUsing(SQLiteDatabase r, long id) {
        Cursor c = r.rawQuery("SELECT count(*) FROM " + Db.T_GIG + " WHERE service_id = ?",
                new String[]{Long.toString(id)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static int nextSortOrder(SQLiteDatabase r) {
        Cursor c = r.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM " + Db.T_SERVICE, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 1;
        } finally {
            c.close();
        }
    }

    private static Service read(Cursor c) {
        Service s = new Service();
        s.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.multiDay = c.getInt(c.getColumnIndexOrThrow("multi_day")) != 0;
        s.syncToCalendar = c.getInt(c.getColumnIndexOrThrow("sync_calendar")) != 0;
        s.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
        s.archived = c.getInt(c.getColumnIndexOrThrow("archived")) != 0;
        return s;
    }
}
