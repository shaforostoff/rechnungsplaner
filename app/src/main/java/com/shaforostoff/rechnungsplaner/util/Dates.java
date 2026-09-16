package com.shaforostoff.rechnungsplaner.util;

import java.util.Calendar;
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
        if (isoDate == null || isoDate.length() != 10) return false;
        if (isoDate.charAt(4) != '-' || isoDate.charAt(7) != '-') return false;
        int y = part(isoDate, 0, 4);
        int m = part(isoDate, 5, 7);
        int d = part(isoDate, 8, 10);
        return y >= 1000 && m >= 1 && m <= 12 && d >= 1 && d <= daysInMonth(y, m);
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

    /**
     * PDF date syntax, {@code D:YYYYMMDDHHmmSS+HH'mm'}, for stream /ModDate entries.
     */
    public static String pdfTimestamp(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        StringBuilder sb = new StringBuilder(24);
        sb.append("D:").append(c.get(Calendar.YEAR));
        two(sb, c.get(Calendar.MONTH) + 1);
        two(sb, c.get(Calendar.DAY_OF_MONTH));
        two(sb, c.get(Calendar.HOUR_OF_DAY));
        two(sb, c.get(Calendar.MINUTE));
        two(sb, c.get(Calendar.SECOND));
        int offsetMinutes = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 60000;
        sb.append(offsetMinutes < 0 ? '-' : '+');
        int abs = Math.abs(offsetMinutes);
        two(sb, abs / 60);
        sb.append('\'');
        two(sb, abs % 60);
        sb.append('\'');
        return sb.toString();
    }

    /** ISO-8601 with offset, for the XMP packet. */
    public static String iso8601(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        StringBuilder sb = new StringBuilder(26);
        sb.append(c.get(Calendar.YEAR)).append('-');
        two(sb, c.get(Calendar.MONTH) + 1);
        sb.append('-');
        two(sb, c.get(Calendar.DAY_OF_MONTH));
        sb.append('T');
        two(sb, c.get(Calendar.HOUR_OF_DAY));
        sb.append(':');
        two(sb, c.get(Calendar.MINUTE));
        sb.append(':');
        two(sb, c.get(Calendar.SECOND));
        int offsetMinutes = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 60000;
        sb.append(offsetMinutes < 0 ? '-' : '+');
        int abs = Math.abs(offsetMinutes);
        two(sb, abs / 60);
        sb.append(':');
        two(sb, abs % 60);
        return sb.toString();
    }

    private static void two(StringBuilder sb, int value) {
        if (value < 10) sb.append('0');
        sb.append(value);
    }

    /** The first day of the month containing the given date. */
    public static String firstOfMonth(String isoDate) {
        return isValid(isoDate) ? isoDate.substring(0, 8) + "01" : isoDate;
    }

    /**
     * Days in the month containing the given date, honouring leap years.
     *
     * <p>Arithmetic rather than a {@code GregorianCalendar}, because this is called for every cell
     * of the month grid on every frame of a paging drag, and constructing a calendar there was
     * allocating on the draw path. The leap rule is the proleptic Gregorian one, which differs
     * from {@code GregorianCalendar} only for Februaries before the 1582 cutover -- outside the
     * range {@link #isValid} accepts anything useful from, and centuries away from an invoice.
     *
     * @param month 1 through 12; anything else is not a month and gets 0
     */
    public static int daysInMonth(int year, int month) {
        switch (month) {
            case 1: case 3: case 5: case 7: case 8: case 10: case 12:
                return 31;
            case 4: case 6: case 9: case 11:
                return 30;
            case 2:
                return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 ? 29 : 28;
            default:
                return 0;
        }
    }

    /** Per-month offsets for {@link #mondayBasedDayOfWeek}. */
    private static final int[] SAKAMOTO = {0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4};

    /**
     * Day of week as 0 for Monday through 6 for Sunday, which is the order a German month grid is
     * drawn in, unlike {@link Calendar}'s Sunday-first numbering.
     */
    public static int mondayBasedDayOfWeek(int year, int month, int day) {
        // Sakamoto's method: no calendar to build, which matters because the month grid asks this
        // once per drawn month per frame. Sunday-based internally, then shifted to Monday.
        int y = month < 3 ? year - 1 : year;
        int sundayBased = (y + y / 4 - y / 100 + y / 400 + SAKAMOTO[month - 1] + day) % 7;
        return (sundayBased + 6) % 7;
    }

    /** Digits read in place: {@code substring} plus {@code parseInt} allocated on every check. */
    private static int part(String isoDate, int from, int to) {
        if (isoDate == null || isoDate.length() < to) return 0;
        int value = 0;
        for (int i = from; i < to; i++) {
            char c = isoDate.charAt(i);
            if (c < '0' || c > '9') return 0;
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static Calendar calendarFor(String isoDate) {
        if (!isValid(isoDate)) return null;
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.clear();
        c.set(part(isoDate, 0, 4), part(isoDate, 5, 7) - 1, part(isoDate, 8, 10), 0, 0, 0);
        return c;
    }
}
