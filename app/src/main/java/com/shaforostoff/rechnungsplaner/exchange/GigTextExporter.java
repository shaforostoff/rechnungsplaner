package com.shaforostoff.rechnungsplaner.exchange;

import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The plain-text tour list: one line per upcoming gig, "date city".
 *
 * <p>With one exception, which is the whole point of the format. Where a city has gigs for more
 * than one distinct customer, those lines name the venue instead -- two lines both reading
 * "Hamburg" tell the reader nothing about which booking is which. The test is per city, not global,
 * so a tour with one ambiguous city keeps plain city names everywhere else.
 *
 * <p>Pure Java, so the disambiguation rule is unit-tested rather than eyeballed.
 */
public final class GigTextExporter {

    public static final String FORMAT_ISO = "yyyy-MM-dd";
    public static final String FORMAT_GERMAN = "dd.MM.yyyy";
    public static final String FORMAT_SHORT = "dd.MM.";

    private GigTextExporter() {
    }

    /**
     * @param gigs       the gigs to list; sorted here, so the caller's order does not matter
     * @param dateFormat one of the {@code FORMAT_*} constants
     */
    public static String export(List<Gig> gigs, String dateFormat) {
        List<Gig> sorted = sortedByDate(gigs);
        Set<String> ambiguous = citiesWithSeveralCustomers(sorted);

        StringBuilder out = new StringBuilder(sorted.size() * 24);
        for (Gig gig : sorted) {
            out.append(formatDate(gig.date, dateFormat)).append(' ')
                    .append(label(gig, ambiguous)).append('\n');
        }
        return out.toString();
    }

    /** What a single gig is called in the list. */
    public static String label(Gig gig, Set<String> ambiguousCities) {
        boolean cityIsAmbiguous = notEmpty(gig.city)
                && ambiguousCities.contains(normalise(gig.city));
        if (cityIsAmbiguous && notEmpty(gig.placeName)) return gig.placeName.trim();
        if (notEmpty(gig.city)) return gig.city.trim();
        // No city at all: the venue is better than nothing, and nothing is better than a blank line.
        if (notEmpty(gig.placeName)) return gig.placeName.trim();
        return notEmpty(gig.title) ? gig.title.trim() : "?";
    }

    /**
     * Cities hosting gigs for more than one customer.
     *
     * <p>Counted by customer, not by gig: three gigs at the same club in one city stay "city",
     * because there is nothing to tell apart.
     */
    public static Set<String> citiesWithSeveralCustomers(List<Gig> gigs) {
        Map<String, Set<Long>> customersByCity = new HashMap<String, Set<Long>>();
        for (Gig gig : gigs) {
            if (!notEmpty(gig.city)) continue;
            String key = normalise(gig.city);
            Set<Long> customers = customersByCity.get(key);
            if (customers == null) {
                customers = new HashSet<Long>();
                customersByCity.put(key, customers);
            }
            customers.add(Long.valueOf(gig.customerId));
        }
        Set<String> out = new HashSet<String>();
        for (Map.Entry<String, Set<Long>> e : customersByCity.entrySet()) {
            if (e.getValue().size() > 1) out.add(e.getKey());
        }
        return out;
    }

    private static String formatDate(String isoDate, String format) {
        if (!Dates.isValid(isoDate)) return isoDate == null ? "" : isoDate;
        if (FORMAT_GERMAN.equals(format)) return Dates.german(isoDate);
        if (FORMAT_SHORT.equals(format)) {
            return isoDate.substring(8, 10) + "." + isoDate.substring(5, 7) + ".";
        }
        return isoDate;
    }

    private static List<Gig> sortedByDate(List<Gig> gigs) {
        List<Gig> out = new ArrayList<Gig>(gigs == null ? new ArrayList<Gig>() : gigs);
        for (int i = 1; i < out.size(); i++) {
            Gig g = out.get(i);
            int j = i - 1;
            while (j >= 0 && compare(out.get(j), g) > 0) {
                out.set(j + 1, out.get(j));
                j--;
            }
            out.set(j + 1, g);
        }
        return out;
    }

    private static int compare(Gig a, Gig b) {
        String x = a.date == null ? "" : a.date;
        String y = b.date == null ? "" : b.date;
        int byDate = x.compareTo(y);
        if (byDate != 0) return byDate;
        return Long.compare(a.startMillis, b.startMillis);
    }

    private static String normalise(String city) {
        return city.trim().toLowerCase(Locale.US);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
