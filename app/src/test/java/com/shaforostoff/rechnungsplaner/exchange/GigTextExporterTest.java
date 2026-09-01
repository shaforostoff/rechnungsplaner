package com.shaforostoff.rechnungsplaner.exchange;

import static org.junit.Assert.assertEquals;

import com.shaforostoff.rechnungsplaner.data.Gig;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class GigTextExporterTest {

    private static Gig gig(String date, long customerId, String place, String city) {
        Gig g = new Gig();
        g.date = date;
        g.customerId = customerId;
        g.placeName = place;
        g.city = city;
        return g;
    }

    @Test
    public void listsDateAndCity() {
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", "Hamburg"),
                gig("2026-09-19", 2L, "Sala Ejemplo", "Barcelona"));
        assertEquals("2026-09-12 Hamburg\n2026-09-19 Barcelona\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void sortsByDateRegardlessOfInputOrder() {
        List<Gig> gigs = Arrays.asList(
                gig("2026-10-03", 1L, "C", "Koeln"),
                gig("2026-09-12", 2L, "A", "Hamburg"),
                gig("2026-09-30", 3L, "B", "Berlin"));
        assertEquals("2026-09-12 Hamburg\n2026-09-30 Berlin\n2026-10-03 Koeln\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void namesTheVenueWhereOneCityHasSeveralCustomers() {
        // The rule the format exists for: two different Hamburg bookers would otherwise produce
        // two indistinguishable lines.
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", "Hamburg"),
                gig("2026-09-13", 2L, "Andere Bar", "Hamburg"),
                gig("2026-09-19", 3L, "Sala Ejemplo", "Barcelona"));
        assertEquals("2026-09-12 Muster Club\n2026-09-13 Andere Bar\n2026-09-19 Barcelona\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void appliesTheRulePerCityNotGlobally() {
        // Barcelona stays a city name even though Hamburg had to fall back to venues.
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", "Hamburg"),
                gig("2026-09-13", 2L, "Andere Bar", "Hamburg"),
                gig("2026-09-19", 3L, "Sala Ejemplo", "Barcelona"));
        String out = GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO);
        assertEquals(true, out.contains("Barcelona"));
        assertEquals(false, out.contains("Hamburg"));
    }

    @Test
    public void severalGigsForOneCustomerInACityAreNotAmbiguous() {
        // Nothing to tell apart, so the city name stays.
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", "Hamburg"),
                gig("2026-09-26", 1L, "Muster Club", "Hamburg"));
        assertEquals("2026-09-12 Hamburg\n2026-09-26 Hamburg\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void matchesCityNamesCaseInsensitively() {
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", "Hamburg"),
                gig("2026-09-13", 2L, "Andere Bar", "hamburg "));
        assertEquals("2026-09-12 Muster Club\n2026-09-13 Andere Bar\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void fallsBackWhenTheCityIsUnknown() {
        List<Gig> gigs = Arrays.asList(
                gig("2026-09-12", 1L, "Muster Club", null),
                gig("2026-09-13", 2L, null, null));
        assertEquals("2026-09-12 Muster Club\n2026-09-13 ?\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_ISO));
    }

    @Test
    public void offersTheOtherDateFormats() {
        List<Gig> gigs = Arrays.asList(gig("2026-09-12", 1L, "Muster Club", "Hamburg"));
        assertEquals("12.09.2026 Hamburg\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_GERMAN));
        assertEquals("12.09. Hamburg\n",
                GigTextExporter.export(gigs, GigTextExporter.FORMAT_SHORT));
    }

    @Test
    public void anEmptyTourProducesAnEmptyList() {
        assertEquals("", GigTextExporter.export(Arrays.<Gig>asList(), GigTextExporter.FORMAT_ISO));
    }
}
