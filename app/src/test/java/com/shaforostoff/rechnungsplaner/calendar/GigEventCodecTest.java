package com.shaforostoff.rechnungsplaner.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.shaforostoff.rechnungsplaner.data.Gig;

import org.junit.Test;

public class GigEventCodecTest {

    private static Gig gig() {
        Gig g = new Gig();
        g.date = "2026-08-15";
        g.placeName = "Muster Club";
        g.city = "Hamburg";
        g.syncUuid = "9f1c2b7e-0000-4000-8000-abcdefabcdef";
        return g;
    }

    @Test
    public void titleNamesTheServiceThenTheVenueAndCity() {
        assertEquals("DJ-Set — Muster Club, Hamburg",
                GigEventCodec.title(gig(), "DJ-Set", "Club Muster GmbH"));
        // Whatever the user calls their work, unchanged: this is read in a calendar app, and the
        // words are theirs.
        assertEquals("Haarschnitt — Muster Club, Hamburg",
                GigEventCodec.title(gig(), "Haarschnitt", "Club Muster GmbH"));
    }

    @Test
    public void titleFallsBackWhenTheVenueIsUnknown() {
        Gig g = gig();
        g.placeName = null;
        assertEquals("DJ-Set — Hamburg", GigEventCodec.title(g, "DJ-Set", "Club Muster GmbH"));
        g.city = null;
        assertEquals("DJ-Set — Club Muster GmbH",
                GigEventCodec.title(g, "DJ-Set", "Club Muster GmbH"));
        assertEquals("DJ-Set", GigEventCodec.title(g, "DJ-Set", null));
    }

    @Test
    public void titleIsJustThePlaceWhenTheServiceIsGone() {
        // A job recorded before the service list existed, or one whose service was deleted.
        assertEquals("Muster Club, Hamburg", GigEventCodec.title(gig(), null, "Club Muster GmbH"));
        Gig bare = gig();
        bare.placeName = null;
        bare.city = null;
        assertEquals("", GigEventCodec.title(bare, null, null));
    }

    @Test
    public void identityRoundTripsThroughTheDescription() {
        Gig g = gig();
        g.notes = "Bring the second CDJ.";
        String description = GigEventCodec.description(g);

        assertEquals(g.syncUuid, GigEventCodec.uuidIn(description));
        assertEquals("Bring the second CDJ.", GigEventCodec.notesIn(description));
    }

    @Test
    public void identitySurvivesWithoutNotes() {
        String description = GigEventCodec.description(gig());
        assertEquals(gig().syncUuid, GigEventCodec.uuidIn(description));
        assertEquals("", GigEventCodec.notesIn(description));
    }

    @Test
    public void anEventTheAppDidNotCreateHasNoIdentity() {
        assertNull(GigEventCodec.uuidIn("Birthday party"));
        assertNull(GigEventCodec.uuidIn(null));
        assertNull(GigEventCodec.uuidIn(""));
    }

    @Test
    public void reencodingDoesNotStackMarkers() {
        // The user edits notes in their calendar app and the app writes back: exactly one marker
        // must survive, or the description grows a marker per edit.
        Gig g = gig();
        g.notes = GigEventCodec.notesIn(GigEventCodec.description(g));
        String twice = GigEventCodec.description(g);
        assertEquals(g.syncUuid, GigEventCodec.uuidIn(twice));
        assertEquals(twice.indexOf("[rp:"), twice.lastIndexOf("[rp:"));
    }

    @Test
    public void notesEditedAroundTheMarkerAreStillRecovered() {
        Gig g = gig();
        g.notes = "Load-in 20:00";
        String edited = GigEventCodec.description(g) + "\nand a line the user added after";
        assertEquals(g.syncUuid, GigEventCodec.uuidIn(edited));
        assertTrue(GigEventCodec.notesIn(edited).startsWith("Load-in 20:00"));
    }
}
