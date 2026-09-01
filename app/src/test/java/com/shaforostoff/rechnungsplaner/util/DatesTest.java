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
