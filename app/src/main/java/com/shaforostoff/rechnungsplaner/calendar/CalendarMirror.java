package com.shaforostoff.rechnungsplaner.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Mirrors gigs into a device calendar.
 *
 * <p>{@code CalendarContract} rather than the Google Calendar REST API. If the chosen calendar
 * belongs to a Google account, the platform's own sync adapter pushes events up to Google within a
 * cycle -- which is what the user actually asked for -- and it costs two runtime permissions
 * instead of an OAuth consent screen, a Cloud project and the Play Services auth libraries.
 *
 * <p>The app's database stays the source of truth. The calendar is a projection: edits here go out,
 * and {@link #scan} brings things back in, but nothing in the app depends on the calendar being
 * present or correct.
 */
public class CalendarMirror {

    /** A calendar the user could mirror into. */
    public static class CalendarInfo {
        public final long id;
        public final String displayName;
        public final String accountName;

        CalendarInfo(long id, String displayName, String accountName) {
            this.id = id;
            this.displayName = displayName;
            this.accountName = accountName;
        }

        /** True when this calendar syncs to Google, which is the case worth telling the user about. */
        public boolean isGoogle() {
            return accountName != null && accountName.contains("@")
                    && (accountName.endsWith("gmail.com") || accountName.endsWith("googlemail.com"));
        }

        @Override
        public String toString() {
            return accountName == null || accountName.equals(displayName)
                    ? displayName : displayName + " (" + accountName + ")";
        }
    }

    /** An event in the calendar that the app does not have a gig for. */
    public static class AdoptableEvent {
        public long eventId;
        public String title;
        public String location;
        public String notes;
        public String date;
        public long startMillis;
        public long endMillis;
        /** Set when the event carries a marker whose gig is missing: a re-link, not a new gig. */
        public String orphanedUuid;
    }

    private final Context ctx;
    private final GigDao gigs;

    public CalendarMirror(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.gigs = new GigDao(this.ctx);
    }

    public boolean hasPermission() {
        return ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ctx.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Calendars the app is allowed to add events to. */
    public List<CalendarInfo> writableCalendars() {
        List<CalendarInfo> out = new ArrayList<CalendarInfo>();
        if (!hasPermission()) return out;
        String[] columns = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
        };
        Cursor c = ctx.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, columns,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?",
                new String[]{Integer.toString(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
        if (c == null) return out;
        try {
            while (c.moveToNext()) out.add(new CalendarInfo(c.getLong(0), c.getString(1),
                    c.getString(2)));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Creates or updates the event for a gig and records the link.
     *
     * @return the event id, or -1 when mirroring is off, not permitted, or the write failed
     */
    public long upsert(Gig gig, String serviceName, String customerName, long calendarId) {
        if (calendarId <= 0L || !hasPermission() || !Dates.isValid(gig.date)) return -1L;

        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        v.put(CalendarContract.Events.TITLE,
                GigEventCodec.title(gig, serviceName, customerName));
        v.put(CalendarContract.Events.EVENT_LOCATION, GigEventCodec.location(gig));
        v.put(CalendarContract.Events.DESCRIPTION, GigEventCodec.description(gig));

        if (gig.startMillis > 0L) {
            long end = gig.endMillis > gig.startMillis ? gig.endMillis
                    : gig.startMillis + 3 * 3600_000L;
            v.put(CalendarContract.Events.DTSTART, gig.startMillis);
            v.put(CalendarContract.Events.DTEND, end);
            v.put(CalendarContract.Events.ALL_DAY, 0);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        } else {
            // An all-day event is anchored to midnight UTC by the provider's contract, not to
            // midnight locally; getting this wrong shifts the event by a day either side of GMT.
            long utcMidnight = utcMidnightOf(gig.date);
            v.put(CalendarContract.Events.DTSTART, utcMidnight);
            v.put(CalendarContract.Events.DTEND, utcMidnight + 86_400_000L);
            v.put(CalendarContract.Events.ALL_DAY, 1);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        }

        ContentResolver resolver = ctx.getContentResolver();
        try {
            if (gig.calendarEventId > 0L && gig.calendarId == calendarId && exists(gig.calendarEventId)) {
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,
                        gig.calendarEventId);
                resolver.update(uri, v, null, null);
                return gig.calendarEventId;
            }
            // A gig moved to another calendar leaves its old event behind; remove it first so the
            // user is not left with a duplicate they have to notice.
            if (gig.calendarEventId > 0L) delete(gig);

            Uri created = resolver.insert(CalendarContract.Events.CONTENT_URI, v);
            if (created == null) return -1L;
            long eventId = ContentUris.parseId(created);
            gig.calendarId = calendarId;
            gig.calendarEventId = eventId;
            gigs.setCalendarEvent(gig.id, calendarId, eventId);
            return eventId;
        } catch (SecurityException e) {
            // The permission can be revoked between the check and the write.
            return -1L;
        }
    }

    /** Removes the mirrored event, if there is one. Silent when there is not. */
    public void delete(Gig gig) {
        if (gig.calendarEventId <= 0L || !hasPermission()) return;
        try {
            ctx.getContentResolver().delete(ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, gig.calendarEventId), null, null);
        } catch (SecurityException ignored) {
            // Nothing useful to do; the gig itself is unaffected.
        }
        gig.calendarEventId = -1L;
        gigs.setCalendarEvent(gig.id, gig.calendarId, -1L);
    }

    /**
     * Events in the chosen calendar that no gig points at.
     *
     * <p>Two cases, and the difference matters to the user. An event carrying a marker whose gig is
     * gone is a <em>re-link</em>, which happens after restoring a backup and should not create a
     * second gig. An event with no marker is something the user made in their calendar app, which
     * they may want to <em>adopt</em> as a gig.
     */
    public List<AdoptableEvent> scan(long calendarId, String fromIso, String toIso) {
        List<AdoptableEvent> out = new ArrayList<AdoptableEvent>();
        if (calendarId <= 0L || !hasPermission()) return out;

        String[] columns = {
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
        };
        Cursor c = ctx.getContentResolver().query(CalendarContract.Events.CONTENT_URI, columns,
                CalendarContract.Events.CALENDAR_ID + " = ? AND "
                        + CalendarContract.Events.DTSTART + " >= ? AND "
                        + CalendarContract.Events.DTSTART + " <= ? AND "
                        + CalendarContract.Events.DELETED + " = 0",
                new String[]{Long.toString(calendarId),
                        Long.toString(Dates.startOfDayMillis(fromIso)),
                        Long.toString(Dates.startOfDayMillis(toIso) + 86_400_000L)},
                CalendarContract.Events.DTSTART);
        if (c == null) return out;
        try {
            while (c.moveToNext()) {
                long eventId = c.getLong(0);
                String description = c.getString(3);
                String uuid = GigEventCodec.uuidIn(description);
                if (uuid != null && gigs.bySyncUuid(uuid) != null) continue;

                AdoptableEvent e = new AdoptableEvent();
                e.eventId = eventId;
                e.title = c.getString(1);
                e.location = c.getString(2);
                e.notes = GigEventCodec.notesIn(description);
                e.startMillis = c.getLong(4);
                e.endMillis = c.getLong(5);
                boolean allDay = c.getInt(6) != 0;
                // An all-day event's DTSTART is midnight UTC, so reading it in the local zone would
                // land on the previous day west of Greenwich.
                e.date = allDay ? isoFromUtcMidnight(e.startMillis) : Dates.fromMillis(e.startMillis);
                if (allDay) {
                    e.startMillis = 0L;
                    e.endMillis = 0L;
                }
                e.orphanedUuid = uuid;
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private boolean exists(long eventId) {
        Cursor c = ctx.getContentResolver().query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                new String[]{CalendarContract.Events._ID}, null, null, null);
        if (c == null) return false;
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static long utcMidnightOf(String isoDate) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Dates.year(isoDate), Dates.month(isoDate) - 1, Dates.day(isoDate), 0, 0, 0);
        return c.getTimeInMillis();
    }

    private static String isoFromUtcMidnight(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(millis);
        return Dates.iso(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }
}
