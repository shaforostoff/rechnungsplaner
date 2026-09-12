package com.shaforostoff.rechnungsplaner.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PatternFormatterTest {

    private static PatternFormatter formatter() {
        return new PatternFormatter()
                .put(PatternFormatter.ISSUER_NAME, "Nick Shaforostov")
                .put(PatternFormatter.CUSTOMER_NAME, "Club Muster GmbH")
                .put(PatternFormatter.PLACE, "Muster Club")
                .put(PatternFormatter.CITY, "Hamburg")
                .put(PatternFormatter.INVOICE_NO, "2026-001")
                .putSequence(1)
                .putDate("2026-09-05")
                .putGigDate("2026-08-15");
    }

    @Test
    public void expandsTheDefaultFileNamePattern() {
        // The pattern the user actually asked for, mixing wrapped and bare tokens.
        assertEquals("Nick Shaforostov-2026-09-05",
                formatter().format("%issuername%-%Y-%M-%D"));
    }

    @Test
    public void treatsWrappedAndBareTokensAlike() {
        assertEquals(formatter().format("%Y-%M-%D"), formatter().format("%Y%-%M%-%D%"));
    }

    @Test
    public void expandsTheDefaultInvoiceNumberPattern() {
        assertEquals("2026-001", formatter().format("%Y%-%seq3%"));
    }

    @Test
    public void prefersTheLongestMatchingToken() {
        // %seq3 must not be read as %seq followed by a literal 3.
        assertEquals("001", formatter().format("%seq3"));
        assertEquals("1", formatter().format("%seq"));
        assertEquals("000001", formatter().format("%seq6"));
    }

    @Test
    public void widensRatherThanWrapsPastTheSequenceWidth() {
        PatternFormatter f = new PatternFormatter().putSequence(1234).putDate("2026-09-05");
        assertEquals("2026-1234", f.format("%Y%-%seq3%"));
    }

    @Test
    public void keepsUnknownTokensVisibleSoTyposAreObvious() {
        assertEquals("%nope-2026", formatter().format("%nope-%Y"));
    }

    @Test
    public void usesGigDatesSeparatelyFromTheInvoiceDate() {
        assertEquals("2026-08-15 invoiced 2026-09-05",
                formatter().format("%gigY-%gigM-%gigD invoiced %Y-%M-%D"));
    }

    @Test
    public void sanitisesTheResultForUseAsAFileName() {
        PatternFormatter f = new PatternFormatter()
                .put(PatternFormatter.CUSTOMER_NAME, "Club / Bar \"Grün\"")
                .putDate("2026-09-05");
        assertEquals("Club-Bar Gruen-2026", f.formatFileName("%customername%-%Y"));
    }

    @Test
    public void expandsAnEmptyValueToNothing() {
        PatternFormatter f = new PatternFormatter()
                .put(PatternFormatter.PLACE, "")
                .putDate("2026-09-05");
        assertEquals("-2026", f.format("%place%-%Y"));
    }

    @Test
    public void readsTheSequenceBackOutOfANumberItWrote() {
        assertEquals(38, PatternFormatter.extractSequence("%Y%-%seq3%", "2026-038"));
        assertEquals(1, PatternFormatter.extractSequence("%Y%-%seq3%", "2026-001"));
        // Past the padding width: the series widens rather than wrapping, so this has to read.
        assertEquals(1000, PatternFormatter.extractSequence("%Y%-%seq3%", "2026-1000"));
        assertEquals(7, PatternFormatter.extractSequence("%seq%", "7"));
    }

    @Test
    public void readsTheSequenceWhateverElseThePatternHolds() {
        assertEquals(38, PatternFormatter.extractSequence("RE-%Y%-%seq3%", "RE-2026-038"));
        assertEquals(38, PatternFormatter.extractSequence("%seq3%-%Y%", "038-2026"));
        assertEquals(38, PatternFormatter.extractSequence("%y%%M%-%seq4%", "2609-0038"));
        assertEquals(4, PatternFormatter.extractSequence("%issuername%-%seq%", "Nick Shaf-4"));
    }

    @Test
    public void aNumberThatDoesNotFitThePatternYieldsNothing() {
        // The point of failing here rather than guessing: the caller has to be able to tell the
        // user the series will not follow a number it cannot place.
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%seq3%", "RE-2026-038"));
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%seq3%", "2026/038"));
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%seq3%", "2026-03a"));
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%seq3%", ""));
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%seq3%", null));
    }

    @Test
    public void aPatternWithNoSequenceHasNoneToRead() {
        assertEquals(-1, PatternFormatter.extractSequence("%Y%-%M%", "2026-09"));
        assertEquals(-1, PatternFormatter.extractSequence("FIXED", "FIXED"));
    }

    @Test
    public void whatItWritesIsWhatItReads() {
        // The round trip is the actual contract: any sequence the formatter can render has to come
        // back as itself, or a hand-typed number would move the series to the wrong place.
        String[] patterns = {"%Y%-%seq3%", "%seq%", "RE-%Y%%M%-%seq4%", "%seq6%/%y%"};
        for (String pattern : patterns) {
            for (int seq : new int[]{1, 9, 10, 99, 100, 999, 1000, 123456}) {
                String written = new PatternFormatter().putDate("2026-09-03").putSequence(seq)
                        .format(pattern);
                assertEquals(pattern + " -> " + written, seq,
                        PatternFormatter.extractSequence(pattern, written));
            }
        }
    }

    @Test
    public void expandsAMessageTheUserWroteWithoutEverFormatting() {
        // The share subject and body are user-editable prose, which is why they go through here
        // rather than String.format: a stray percent in a sentence would be a crash there.
        PatternFormatter f = new PatternFormatter()
                .put(PatternFormatter.INVOICE_NO, "2026-038")
                .put(PatternFormatter.ISSUER_NAME, "Nick Shaforostov")
                .put(PatternFormatter.CUSTOMER_NAME, "Club Muster GmbH");

        assertEquals("Dear Sir or Madam,\n\nplease find invoice 2026-038 attached.\n\n"
                        + "Kind regards\nNick Shaforostov",
                f.format("Dear Sir or Madam,\n\nplease find invoice %invoiceno% attached.\n\n"
                        + "Kind regards\n%issuername%"));

        assertEquals("100% analog, Club Muster GmbH", f.format("100% analog, %customername%"));
        assertEquals("50%% still reads as typed", f.format("50%% still reads as typed"));
        assertEquals("a trailing percent survives %", f.format("a trailing percent survives %"));
    }

    @Test
    public void expandsTheShareSubjectFromTheWorkAndItsDate() {
        // What the booker sees in their inbox: which job, on which night. The invoice number is
        // still available, it just no longer has to carry the subject on its own.
        PatternFormatter f = new PatternFormatter()
                .put(PatternFormatter.SERVICE, "DJ-Set")
                .put(PatternFormatter.GIG_DATE, "15.08.2026")
                .put(PatternFormatter.INVOICE_NO, "2026-038");

        assertEquals("Rechnung: DJ-Set am 15.08.2026",
                f.format("Rechnung: %service% am %gigdate%"));
        assertEquals("Invoice: DJ-Set on 15.08.2026",
                f.format("Invoice: %service% on %gigdate%"));
    }

    @Test
    public void readsGigDateWholeRatherThanAsTheGigDayToken() {
        // %gigdate% and %gigD overlap in spelling but not in case, and longest-match is what
        // settles them for a formatter holding both. "15ate" is the alternative.
        PatternFormatter f = formatter().put(PatternFormatter.GIG_DATE, "15.08.2026");
        assertEquals("15.08.2026", f.format("%gigdate%"));
        assertEquals("15", f.format("%gigD%"));
    }

    @Test
    public void keepsShareTokensOutOfTheFileNameAndNumberLegend() {
        // Only the share text is ever given these two, so listing them under the file-name field
        // would offer the user a token that expands to itself -- which is what it does here.
        List<String> legend = Arrays.asList(PatternFormatter.TOKENS);
        assertFalse(legend.contains(PatternFormatter.SERVICE));
        assertFalse(legend.contains(PatternFormatter.GIG_DATE));
        assertEquals("%service% %gigdate%", formatter().format("%service% %gigdate%"));
    }
}
