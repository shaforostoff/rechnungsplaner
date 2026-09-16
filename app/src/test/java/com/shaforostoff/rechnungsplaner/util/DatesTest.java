package com.shaforostoff.rechnungsplaner.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DatesTest {

    @Test
    public void addsDaysAcrossMonthAndYearBoundaries() {
        assertEquals("2026-10-05", Dates.plusDays("2026-09-05", 30));
        assertEquals("2026-09-01", Dates.plusDays("2026-08-31", 1));
        assertEquals("2027-01-01", Dates.plusDays("2026-12-31", 1));
        assertEquals("2026-08-31", Dates.plusDays("2026-09-05", -5));
    }

    @Test
    public void knowsAboutLeapYears() {
        assertEquals(29, Dates.daysInMonth(2028, 2));
        assertEquals(28, Dates.daysInMonth(2026, 2));
        assertTrue(Dates.isValid("2028-02-29"));
        assertFalse(Dates.isValid("2026-02-29"));
    }

    @Test
    public void rejectsMalformedDates() {
        assertFalse(Dates.isValid(null));
        assertFalse(Dates.isValid("2026-9-5"));
        assertFalse(Dates.isValid("2026-13-01"));
        assertFalse(Dates.isValid("not a date"));
        assertTrue(Dates.isValid("2026-09-05"));
    }

    @Test
    public void extractsParts() {
        assertEquals(2026, Dates.year("2026-09-05"));
        assertEquals(9, Dates.month("2026-09-05"));
        assertEquals(5, Dates.day("2026-09-05"));
    }

    @Test
    public void formatsPerInvoiceLanguageNotDeviceLocale() {
        assertEquals("05.09.2026", Dates.forLanguage("2026-09-05", "de"));
        assertEquals("05.09.2026", Dates.forLanguage("2026-09-05", "es"));
        assertEquals("2026-09-05", Dates.forLanguage("2026-09-05", "en"));
    }

    @Test
    public void isoStringsSortChronologically() {
        // The stores rely on this to range-filter and order without parsing.
        assertTrue("2026-08-31".compareTo("2026-09-01") < 0);
        assertTrue("2026-09-05".compareTo("2026-10-05") < 0);
        assertTrue("2026-12-31".compareTo("2027-01-01") < 0);
    }

    @Test
    public void countsWeekdaysFromMonday() {
        // 2026-09-05 is a Saturday.
        assertEquals(5, Dates.mondayBasedDayOfWeek(2026, 9, 5));
        assertEquals(6, Dates.mondayBasedDayOfWeek(2026, 9, 6));
        assertEquals(0, Dates.mondayBasedDayOfWeek(2026, 9, 7));
    }

    /**
     * The century rule, which is what separates the arithmetic {@code daysInMonth} does from a
     * plain divisible-by-four test. 1900 and 2100 are not leap years; 2000 is.
     */
    @Test
    public void appliesTheCenturyLeapRule() {
        assertEquals(28, Dates.daysInMonth(1900, 2));
        assertEquals(29, Dates.daysInMonth(2000, 2));
        assertEquals(28, Dates.daysInMonth(2100, 2));
        assertEquals(29, Dates.daysInMonth(2400, 2));
        assertFalse(Dates.isValid("1900-02-29"));
        assertTrue(Dates.isValid("2000-02-29"));
    }

    @Test
    public void knowsTheLengthOfEveryMonth() {
        int[] expected = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        for (int month = 1; month <= 12; month++) {
            assertEquals("month " + month, expected[month - 1], Dates.daysInMonth(2026, month));
        }
        // Not a month, so not a length -- the grid never asks, but nothing should roll over.
        assertEquals(0, Dates.daysInMonth(2026, 0));
        assertEquals(0, Dates.daysInMonth(2026, 13));
    }

    @Test
    public void weekdaysAgreeAcrossMonthAndCenturyBoundaries() {
        assertEquals(0, Dates.mondayBasedDayOfWeek(2024, 1, 1));   // Monday
        assertEquals(1, Dates.mondayBasedDayOfWeek(2030, 1, 1));   // Tuesday
        assertEquals(5, Dates.mondayBasedDayOfWeek(2000, 1, 1));   // Saturday
        assertEquals(3, Dates.mondayBasedDayOfWeek(2026, 12, 31)); // Thursday
        assertEquals(6, Dates.mondayBasedDayOfWeek(2026, 3, 1));   // Sunday
        assertEquals(1, Dates.mondayBasedDayOfWeek(2028, 2, 29));  // Tuesday, a leap day
    }

    /** Digits are read in place now, so anything that is not a digit has to fail the same way. */
    @Test
    public void rejectsNonDigitsInDateParts() {
        assertFalse(Dates.isValid("20x6-09-05"));
        assertFalse(Dates.isValid("2026-0a-05"));
        assertFalse(Dates.isValid("2026-09-0 "));
        assertFalse(Dates.isValid("2026-09-+5"));
        assertFalse(Dates.isValid("2026-09-05 "));
        assertFalse(Dates.isValid("0999-09-05"));
        assertFalse(Dates.isValid("2026-00-05"));
        assertFalse(Dates.isValid("2026-09-00"));
        assertFalse(Dates.isValid("2026-09-31"));
    }

    @Test
    public void roundTripsThroughMillis() {
        String date = "2026-09-05";
        assertEquals(date, Dates.fromMillis(Dates.startOfDayMillis(date)));
    }

    @Test
    public void findsTheFirstOfTheMonth() {
        assertEquals("2026-09-01", Dates.firstOfMonth("2026-09-30"));
    }
}
