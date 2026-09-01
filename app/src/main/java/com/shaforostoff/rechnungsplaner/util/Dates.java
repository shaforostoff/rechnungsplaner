package com.shaforostoff.rechnungsplaner.util;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Local-date helpers over {@code yyyy-MM-dd} strings.
 *
 * <p>{@code java.time} needs API 26 and this app targets 24 without core-library desugaring, so
 * dates are ISO strings and arithmetic goes through {@link Calendar}. Strings sort chronologically,
 * which is why the stores can order and range-filter on them directly.
 *
 * <p>A gig that runs 23:00 to 04:00 belongs to the date it <em>started</em>. Keeping the date as a
 * separate field from the start/end instants is what makes that unambiguous, rather than deriving
 * it from an instant and having the answer depend on the reader's time zone.
 */
public final class Dates {

    private Dates() {
    }

    public static String today() {
        return fromMillis(System.currentTimeMillis());
    }

    /** The local calendar date containing the given instant. */
    public static String fromMillis(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static String iso(int year, int month, int day) {
        StringBuilder sb = new StringBuilder(10);
        sb.append(year).append('-');
        if (month < 10) sb.append('0');
        sb.append(month).append('-');
        if (day < 10) sb.append('0');
        sb.append(day);
        return sb.toString();
    }

    /** Midnight at the start of the given local date. */
    public static long startOfDayMillis(String isoDate) {
        Calendar c = calendarFor(isoDate);
        return c == null ? 0L : c.getTimeInMillis();
    }

    public static String plusDays(String isoDate, int days) {
        Calendar c = calendarFor(isoDate);
        if (c == null) return isoDate;
        c.add(Calendar.DAY_OF_MONTH, days);
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean isValid(String isoDate) {
        return calendarFor(isoDate) != null;
    }

    public static int year(String isoDate) {
        return part(isoDate, 0, 4);
    }

    public static int month(String isoDate) {
        return part(isoDate, 5, 7);
    }

    public static int day(String isoDate) {
        return part(isoDate, 8, 10);
    }

    /** {@code 2026-09-05} to {@code 05.09.2026}, for German invoice text. */
    public static String german(String isoDate) {
        if (!isValid(isoDate)) return isoDate == null ? "" : isoDate;
        return isoDate.substring(8, 10) + "." + isoDate.substring(5, 7) + "."
                + isoDate.substring(0, 4);
    }

    /** Formats a date in the given language, for the invoice document rather than the UI. */
    public static String forLanguage(String isoDate, String languageTag) {
        if (!isValid(isoDate)) return isoDate == null ? "" : isoDate;
        String lang = languageTag == null ? "" : languageTag.toLowerCase(Locale.US);
        if (lang.startsWith("de") || lang.startsWith("es")) return german(isoDate);
        return isoDate;
    }

    /** The first day of the month containing the given date. */
    public static String firstOfMonth(String isoDate) {
        return isValid(isoDate) ? isoDate.substring(0, 8) + "01" : isoDate;
    }

    /** Days in the month containing the given date, honouring leap years. */
    public static int daysInMonth(int year, int month) {
        Calendar c = new GregorianCalendar(year, month - 1, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * Day of week as 0 for Monday through 6 for Sunday, which is the order a German month grid is
     * drawn in, unlike {@link Calendar}'s Sunday-first numbering.
     */
    public static int mondayBasedDayOfWeek(int year, int month, int day) {
        Calendar c = new GregorianCalendar(year, month - 1, day);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        return (dow + 5) % 7;
    }

    private static int part(String isoDate, int from, int to) {
        if (isoDate == null || isoDate.length() < to) return 0;
        try {
            return Integer.parseInt(isoDate.substring(from, to));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Calendar calendarFor(String isoDate) {
        if (isoDate == null || isoDate.length() != 10) return null;
        if (isoDate.charAt(4) != '-' || isoDate.charAt(7) != '-') return null;
        int y = part(isoDate, 0, 4);
        int m = part(isoDate, 5, 7);
        int d = part(isoDate, 8, 10);
        if (y < 1000 || m < 1 || m > 12 || d < 1 || d > daysInMonth(y, m)) return null;
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.clear();
        c.set(y, m - 1, d, 0, 0, 0);
        return c;
    }
}
