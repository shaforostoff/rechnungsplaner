package com.shaforostoff.rechnungsplaner.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The rule behind a mirrored field, kept as a pure function precisely so it can be checked here:
 * the screen it drives cannot be exercised without a device.
 */
public class UiTest {

    @Test
    public void anEmptyMirrorFollows() {
        assertTrue(Ui.stillMirrors("", "Nick"));
        assertTrue(Ui.stillMirrors("   ", "Nick"));
        assertTrue(Ui.stillMirrors(null, "Nick"));
    }

    @Test
    public void aMirrorShowingTheOldSourceTextFollows() {
        // Typing "Nick" one letter at a time: each keystroke sees a mirror equal to the previous
        // name, so it keeps up instead of stopping after the first character.
        assertTrue(Ui.stillMirrors("N", "N"));
        assertTrue(Ui.stillMirrors("Nic", "Nic"));
        assertTrue(Ui.stillMirrors("Nick Shaforostoff", "Nick Shaforostoff"));
    }

    @Test
    public void aMirrorTheUserChangedStaysPut() {
        // The account is in an agency's name, so correcting the DJ name must not overwrite it.
        assertFalse(Ui.stillMirrors("Booking Agency GmbH", "Nick"));
        assertFalse(Ui.stillMirrors("Nicholas", "Nick"));
    }

    @Test
    public void surroundingSpaceDoesNotBreakTheLink() {
        // The fields are read trimmed when saved, so they must be compared trimmed too or a stray
        // space silently unlinks them.
        assertTrue(Ui.stillMirrors("Nick", "Nick "));
        assertTrue(Ui.stillMirrors(" Nick ", "Nick"));
    }

    @Test
    public void anEmptySourceStillCountsAsMatchingAnEmptyMirror() {
        // Opening a fresh install: both blank, so the first thing typed into the name propagates.
        assertTrue(Ui.stillMirrors("", ""));
        assertTrue(Ui.stillMirrors(null, null));
    }
}
