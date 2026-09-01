package com.shaforostoff.rechnungsplaner.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SlugTest {

    @Test
    public void transliteratesGermanAndSpanishLetters() {
        assertEquals("Muenchen", Slug.transliterate("München"));
        assertEquals("Strasse", Slug.transliterate("Straße"));
        assertEquals("Espana", Slug.transliterate("España"));
        assertEquals("Malaga", Slug.transliterate("Málaga"));
    }

    @Test
    public void replacesCharactersFileSystemsReject() {
        assertEquals("a-b-c-d", Slug.fileName("a/b\\c:d"));
        assertEquals("what", Slug.fileName("what?"));
        assertEquals("a-b", Slug.fileName("a<b>"));
    }

    @Test
    public void collapsesRunsOfSeparators() {
        assertEquals("a-b", Slug.fileName("a///b"));
        assertEquals("a b", Slug.fileName("a    b"));
    }

    @Test
    public void trimsTrailingDotsAndSpacesThatWindowsRejects() {
        assertEquals("invoice", Slug.fileName("invoice..."));
        assertEquals("invoice", Slug.fileName("invoice   "));
        assertEquals("invoice", Slug.fileName("invoice-"));
    }

    @Test
    public void neverReturnsAnEmptyName() {
        assertEquals("invoice", Slug.fileName(""));
        assertEquals("invoice", Slug.fileName(null));
        assertEquals("invoice", Slug.fileName("///"));
    }

    @Test
    public void capsLength() {
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 300; i++) long_.append('x');
        assertEquals(120, Slug.fileName(long_.toString()).length());
    }

    @Test
    public void buildsContactSlugs() {
        assertEquals("club-muster-gmbh", Slug.slug("Club Muster GmbH"));
        assertEquals("sala-ejemplo-s-l", Slug.slug("Sala Ejemplo, S.L."));
        assertEquals("cafe-koeln", Slug.slug("Café Köln"));
        assertEquals("contact", Slug.slug("---"));
    }
}
