package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

/**
 * Golden-file coverage for both syntaxes across the four VAT situations. These files are the
 * artefacts that go to the desktop validators (KoSIT, Mustang, veraPDF) and into the three target
 * accounting systems, so any diff here is a change of behaviour that needs a human to look at it.
 */
public class WritersGoldenTest {

    private static String ubl(EnInvoice inv, Profile p) {
        return new UblWriter(p).write(inv);
    }

    private static String cii(EnInvoice inv, Profile p) {
        return new CiiWriter(p).write(inv);
    }

    @Test
    public void kleinunternehmerUbl() throws IOException {
        Golden.assertMatches("kleinunternehmer-ubl.xml",
                ubl(Fixtures.kleinunternehmer(), Profile.XRECHNUNG_30));
    }

    @Test
    public void kleinunternehmerCii() throws IOException {
        Golden.assertMatches("kleinunternehmer-cii.xml",
                cii(Fixtures.kleinunternehmer(), Profile.XRECHNUNG_30));
    }

    @Test
    public void standard19TwoGigsUbl() throws IOException {
        Golden.assertMatches("standard19-two-gigs-ubl.xml",
                ubl(Fixtures.standard19TwoGigs(), Profile.XRECHNUNG_30));
    }

    @Test
    public void standard19TwoGigsCii() throws IOException {
        Golden.assertMatches("standard19-two-gigs-cii.xml",
                cii(Fixtures.standard19TwoGigs(), Profile.XRECHNUNG_30));
    }

    @Test
    public void reduced7Ubl() throws IOException {
        Golden.assertMatches("reduced7-ubl.xml", ubl(Fixtures.reduced7(), Profile.XRECHNUNG_30));
    }

    @Test
    public void reduced7Cii() throws IOException {
        Golden.assertMatches("reduced7-cii.xml", cii(Fixtures.reduced7(), Profile.XRECHNUNG_30));
    }

    @Test
    public void reverseChargeUbl() throws IOException {
        Golden.assertMatches("reverse-charge-ubl.xml",
                ubl(Fixtures.reverseCharge(), Profile.XRECHNUNG_30));
    }

    @Test
    public void reverseChargeCii() throws IOException {
        Golden.assertMatches("reverse-charge-cii.xml",
                cii(Fixtures.reverseCharge(), Profile.XRECHNUNG_30));
    }

    /** The ZUGFeRD EN 16931 profile differs from XRechnung only in BT-24. */
    @Test
    public void zugferdEn16931Cii() throws IOException {
        Golden.assertMatches("zugferd-en16931-cii.xml",
                cii(Fixtures.standard19TwoGigs(), Profile.EN16931));
    }

    @Test
    public void profileOnlyChangesTheSpecificationIdentifier() {
        String a = cii(Fixtures.standard19TwoGigs(), Profile.XRECHNUNG_30);
        String b = cii(Fixtures.standard19TwoGigs(), Profile.EN16931);
        // Masking BT-24 out of both leaves the identical document, which is the whole reason one
        // model can serve XRechnung and every ZUGFeRD profile.
        assertEquals(a.replace(Profile.XRECHNUNG_30.customizationId, "SPEC"),
                b.replace(Profile.EN16931.customizationId, "SPEC"));
    }
}
