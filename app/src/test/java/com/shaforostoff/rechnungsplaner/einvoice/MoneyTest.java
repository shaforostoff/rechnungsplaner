package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void formatsAmountsWithTwoFractionDigits() {
        assertEquals("0.00", Money.amount(0L));
        assertEquals("3.05", Money.amount(305L));
        assertEquals("350.00", Money.amount(35000L));
        assertEquals("1234.56", Money.amount(123456L));
        assertEquals("-12.30", Money.amount(-1230L));
    }

    @Test
    public void neverGroupsThousands() {
        // Grouping separators are what break importers that parse the amount themselves.
        assertEquals("1000000.00", Money.amount(100000000L));
    }

    @Test
    public void formatsRatesFromPermille() {
        assertEquals("19.00", Money.percent(190));
        assertEquals("7.00", Money.percent(70));
        assertEquals("7.50", Money.percent(75));
        assertEquals("0.00", Money.percent(0));
    }

    @Test
    public void trimsQuantities() {
        assertEquals("1", Money.quantity(1000L));
        assertEquals("2", Money.quantity(2000L));
        assertEquals("1.5", Money.quantity(1500L));
        assertEquals("0.25", Money.quantity(250L));
    }

    @Test
    public void roundsTaxHalfUp() {
        // 19 % of 8.15 is 1.5485, which must land on 1.55 and not 1.54.
        assertEquals(155L, Money.taxOf(815L, 190));
        assertEquals(0L, Money.taxOf(35000L, 0));
        assertEquals(6650L, Money.taxOf(35000L, 190));
        assertEquals(2450L, Money.taxOf(35000L, 70));
    }

    @Test
    public void roundsExactHalvesAwayFromZero() {
        assertEquals(3L, Money.roundHalfUp(5L, 2L));
        assertEquals(-3L, Money.roundHalfUp(-5L, 2L));
    }
}
