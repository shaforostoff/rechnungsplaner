package com.shaforostoff.rechnungsplaner.data;

import static org.junit.Assert.assertEquals;

import com.shaforostoff.rechnungsplaner.util.Dates;

import org.junit.Test;

/** The status a newly entered gig should start in, derived from its date. */
public class GigStatusTest {

    @Test
    public void aSetInThePastHasBeenPlayed() {
        assertEquals(Gig.Status.PLAYED, Gig.defaultStatusFor("2020-03-14"));
        assertEquals(Gig.Status.PLAYED, Gig.defaultStatusFor(Dates.plusDays(Dates.today(), -1)));
    }

    @Test
    public void todayAndLaterIsStillPlanned() {
        // A set is entered before it happens, so today's gig is not retroactively played.
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor(Dates.today()));
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor(Dates.plusDays(Dates.today(), 1)));
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor("2099-12-31"));
    }

    @Test
    public void aDateItCannotReadLeavesTheStatusAlone() {
        // Better a wrong-but-editable default than an exception while opening the editor.
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor(null));
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor(""));
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor("14.03.2020"));
    }

    @Test
    public void theComparisonHoldsAcrossAYearBoundary() {
        // Comparing yyyy-MM-dd as text is only correct because the fields are zero-padded and
        // most-significant first. Offsets rather than literal years, so the test does not start
        // failing on some future 1 January.
        assertEquals(Gig.Status.PLAYED, Gig.defaultStatusFor(Dates.plusDays(Dates.today(), -400)));
        assertEquals(Gig.Status.PLANNED, Gig.defaultStatusFor(Dates.plusDays(Dates.today(), 400)));
    }
}
