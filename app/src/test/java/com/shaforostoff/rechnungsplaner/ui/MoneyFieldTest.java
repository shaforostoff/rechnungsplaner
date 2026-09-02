package com.shaforostoff.rechnungsplaner.ui;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.TimeZone;

/**
 * The text-to-cents path every fee field runs through.
 *
 * <p>This is the one piece of the money path that had no coverage, and it is the first step of it:
 * whatever it reads out of the fee field is what ends up on the invoice, so a mis-parse here is a
 * wrong invoice that validates perfectly.
 *
 * <p>The default locale is pinned per test because these helpers deliberately use it, and the JVM
 * running the suite may be set to anything.
 */
public class MoneyFieldTest {

    private Locale originalLocale;
    private TimeZone originalZone;

    @Before
    public void rememberEnvironment() {
        originalLocale = Locale.getDefault();
        originalZone = TimeZone.getDefault();
    }

    @After
    public void restoreEnvironment() {
        Locale.setDefault(originalLocale);
        TimeZone.setDefault(originalZone);
    }

    // ------------------------------------------------------------------ parsing

    @Test
    public void acceptsEitherSeparatorWhicheverTheKeyboardOffers() {
        // The stated promise of the parser: a German phone's keyboard gives a comma, a numeric
        // keypad may give a dot, and both mean the same fee.
        assertEquals(35000L, Ui.editableToCents("350,00"));
        assertEquals(35000L, Ui.editableToCents("350.00"));
        assertEquals(35050L, Ui.editableToCents("350,50"));
        assertEquals(35050L, Ui.editableToCents("350.50"));
    }

    @Test
    public void readsGroupedThousandsInBothConventions() {
        assertEquals(123456L, Ui.editableToCents("1.234,56"));
        assertEquals(123456L, Ui.editableToCents("1,234.56"));
        assertEquals(123456789L, Ui.editableToCents("1.234.567,89"));
        assertEquals(123456789L, Ui.editableToCents("1,234,567.89"));
    }

    @Test
    public void aLoneSeparatorFollowedByThreeDigitsIsReadAsGrouping() {
        // Genuinely ambiguous: "1.234" is 1234 euros in German and 1,234 euros in English. The
        // grouping reading wins because that is what the German keyboard produces, and because
        // nobody enters a fee in tenths of a cent.
        assertEquals(123400L, Ui.editableToCents("1.234"));
        assertEquals(123400L, Ui.editableToCents("1,234"));
    }

    @Test
    public void oneOrTwoDigitsAfterTheSeparatorAreCents() {
        assertEquals(120L, Ui.editableToCents("1,2"));
        assertEquals(123L, Ui.editableToCents("1,23"));
        assertEquals(50L, Ui.editableToCents("0,5"));
        assertEquals(35000L, Ui.editableToCents("350,"));
    }

    @Test
    public void moreDigitsThanAnyGroupingWouldExplainAreCentsAndTruncated() {
        // No locale groups in fours, so "1,2345" cannot be a grouped number -- it is someone
        // typing past the cents. Taking the first two digits is what the code already intended.
        assertEquals(123L, Ui.editableToCents("1,2345"));
        assertEquals(123456L, Ui.editableToCents("1234.5678"));
    }

    @Test
    public void ignoresCurrencySymbolsAndSpacing() {
        assertEquals(35000L, Ui.editableToCents("350,00 €"));
        assertEquals(35000L, Ui.editableToCents("EUR 350,00"));
        assertEquals(123456L, Ui.editableToCents("1 234,56"));
        assertEquals(35000L, Ui.editableToCents("  350,00  "));
    }

    @Test
    public void keepsANegativeAmount() {
        // Not reachable from a fee field, but the parser is generic and a credit is a real thing.
        assertEquals(-15000L, Ui.editableToCents("-150,00"));
        assertEquals(-150L, Ui.editableToCents("-1,50"));
    }

    @Test
    public void emptyAndUnreadableInputIsZeroRatherThanACrash() {
        // The field is optional, and the alternative to zero is an exception while saving a gig.
        assertEquals(0L, Ui.editableToCents(null));
        assertEquals(0L, Ui.editableToCents(""));
        assertEquals(0L, Ui.editableToCents("   "));
        assertEquals(0L, Ui.editableToCents("abc"));
        assertEquals(0L, Ui.editableToCents("-"));
        assertEquals(0L, Ui.editableToCents(","));
    }

    @Test
    public void anAmountTooBigForCentsIsZeroRatherThanAWrappedNumber() {
        // A wrapped long would be a silently wrong invoice; zero is visibly wrong.
        assertEquals(0L, Ui.editableToCents("999999999999999999999999"));
    }

    // ------------------------------------------------------------------ round trip

    @Test
    public void whatIsShownInAFieldReadsBackUnchanged() {
        // The contract that actually matters: the gig editor fills the fee field with
        // centsToEditable and saves whatever editableToCents makes of it. Any disagreement between
        // the two silently rewrites a fee the user never touched.
        long[] amounts = {0L, 1L, 50L, 99L, 100L, 999L, 35000L, 123456L, 100000000L, -15000L};
        Locale[] locales = {Locale.GERMANY, Locale.US, Locale.forLanguageTag("es-ES")};
        for (Locale locale : locales) {
            Locale.setDefault(locale);
            for (long cents : amounts) {
                String shown = Ui.centsToEditable(cents);
                assertEquals(locale + " / " + shown, cents, Ui.editableToCents(shown));
            }
        }
    }

    @Test
    public void aFieldAlwaysShowsTwoDecimalsAndNoGrouping() {
        // Grouping in an editable field would be re-parsed as a separator, so it stays off.
        Locale.setDefault(Locale.GERMANY);
        assertEquals("350,00", Ui.centsToEditable(35000L));
        assertEquals("1234,56", Ui.centsToEditable(123456L));
        assertEquals("0,00", Ui.centsToEditable(0L));

        Locale.setDefault(Locale.US);
        assertEquals("350.00", Ui.centsToEditable(35000L));
        assertEquals("1234.56", Ui.centsToEditable(123456L));
    }

    // ------------------------------------------------------------------ display

    @Test
    public void displayedMoneyIsAlwaysEurosInTheUiLanguage() {
        // The app is euro-only, so the currency is forced rather than taken from the locale --
        // otherwise an English UI would offer to pay a German club in dollars.
        Locale.setDefault(Locale.GERMANY);
        assertEquals("350,00 €", Ui.money(35000L));

        Locale.setDefault(Locale.US);
        assertEquals("€350.00", Ui.money(35000L));
    }

    // ------------------------------------------------------------------ times

    @Test
    public void aStartTimeIsShownAsTheLocalWallClock() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        // 2026-08-15T23:30+02:00, a normal set start.
        assertEquals("23:30", Ui.timeOfDay(1786829400000L));
        // The same set ending at 04:15 the next morning.
        assertEquals("04:15", Ui.timeOfDay(1786846500000L));
    }

    @Test
    public void theClockIsTwentyFourHourRegardlessOfLocale() {
        // The field feeds a gig that routinely runs past midnight; am/pm would make 04:00 unclear
        // about which day it belongs to.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
        Locale.setDefault(Locale.US);
        assertEquals("23:30", Ui.timeOfDay(1786829400000L));
    }

    @Test
    public void notSetIsAnEmptyStringRatherThanTheEpoch() {
        // Zero means "no time entered", which has to render as nothing rather than 01:00.
        assertEquals("", Ui.timeOfDay(0L));
        assertEquals("", Ui.timeOfDay(-1L));
    }
}
