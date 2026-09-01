package com.shaforostoff.rechnungsplaner.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
}
