package com.shaforostoff.rechnungsplaner.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InvoiceSeriesTest {

    @Test
    public void aPatternNamingTheYearRestartsEachYear() {
        assertTrue(InvoiceDao.restartsEachYear(SettingsStore.DEFAULT_NUMBER_PATTERN));
        assertTrue(InvoiceDao.restartsEachYear("RE-%y-%seq3"));
    }

    @Test
    public void aPatternWithoutTheYearRunsStraightOn() {
        // Restarting this one in January would mint RE-0001 a second time.
        assertFalse(InvoiceDao.restartsEachYear("RE-%seq4%"));
    }
}
