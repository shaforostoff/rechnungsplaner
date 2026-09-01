package com.shaforostoff.rechnungsplaner.calendar;

import com.shaforostoff.rechnungsplaner.data.Gig;

/**
 * Encodes a gig into a calendar event's visible fields, and finds it again afterwards.
 *
 * <p>The obvious place for a link back to the app's own row is
 * {@code CalendarContract.ExtendedProperties}, but that table is writable only by a sync adapter,
 * which this app is not. So the identity is appended to the description as a marker line instead.
 *
 * <p>That turns out to be better than it sounds. It survives a database restore, so a reinstalled
 * app can re-link to events already in the user's Google Calendar instead of duplicating them, and
 * it travels with an event that gets copied to another calendar. The cost is one faintly technical
 * line at the bottom of the description, which is why it is separated by blank lines and reads as
 * a footnote.
 */
public final class GigEventCodec {

    private static final String MARKER_PREFIX = "[rp:";
    private static final String MARKER_SUFFIX = "]";

    private GigEventCodec() {
    }

    /** Event title: the venue and city, falling back to whatever is known. */
    public static String title(Gig gig, String customerName) {
        String where = joined(gig.placeName, gig.city);
        if (where.isEmpty()) where = customerName == null ? "" : customerName;
        return where.isEmpty() ? "DJ-Set" : "DJ-Set — " + where;
    }

    public static String location(Gig gig) {
        return joined(gig.placeName, gig.city);
    }

    /** The gig's notes with the identity marker appended. */
    public static String description(Gig gig) {
        StringBuilder sb = new StringBuilder();
        if (notEmpty(gig.notes)) sb.append(gig.notes.trim()).append("\n\n");
        sb.append(MARKER_PREFIX).append(gig.syncUuid == null ? "" : gig.syncUuid)
                .append(MARKER_SUFFIX);
        return sb.toString();
    }

    /** The identity written by {@link #description}, or null for an event the app did not create. */
    public static String uuidIn(String description) {
        if (description == null) return null;
        int start = description.lastIndexOf(MARKER_PREFIX);
        if (start < 0) return null;
        int end = description.indexOf(MARKER_SUFFIX, start);
        if (end < 0) return null;
        String uuid = description.substring(start + MARKER_PREFIX.length(), end).trim();
        return uuid.isEmpty() ? null : uuid;
    }

    /** The description with the marker taken back off, for showing the user their own notes. */
    public static String notesIn(String description) {
        if (description == null) return "";
        int start = description.lastIndexOf(MARKER_PREFIX);
        if (start < 0) return description.trim();
        return description.substring(0, start).trim();
    }

    private static String joined(String place, String city) {
        boolean p = notEmpty(place);
        boolean c = notEmpty(city);
        if (p && c) return place.trim() + ", " + city.trim();
        if (p) return place.trim();
        return c ? city.trim() : "";
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
