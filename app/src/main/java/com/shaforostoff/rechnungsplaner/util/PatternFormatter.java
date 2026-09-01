package com.shaforostoff.rechnungsplaner.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expands the user's file-name and invoice-number patterns.
 *
 * <p>Both spellings of a token work: the wrapped {@code %issuername%} form and the bare
 * {@code %Y} form. That is not indecision — the default file-name pattern the user asked for is
 * literally {@code %issuername%-%Y-%M-%D}, which mixes them, and refusing one half of their own
 * default would be unhelpful. Matching is longest-token-first so {@code %seq3} wins over
 * {@code %seq}, and an unrecognised {@code %} is emitted as itself rather than swallowed.
 */
public final class PatternFormatter {

    /** Invoice date parts. */
    public static final String YEAR = "Y";
    public static final String YEAR_SHORT = "y";
    public static final String MONTH = "M";
    public static final String DAY = "D";
    /** First gig date on the invoice. */
    public static final String GIG_YEAR = "gigY";
    public static final String GIG_MONTH = "gigM";
    public static final String GIG_DAY = "gigD";

    public static final String ISSUER_NAME = "issuername";
    public static final String CUSTOMER_NAME = "customername";
    public static final String PLACE = "place";
    public static final String CITY = "city";
    public static final String INVOICE_NO = "invoiceno";
    public static final String FORMAT = "format";
    public static final String SEQ = "seq";

    /** Every token the UI should offer in its legend, in the order it should list them. */
    public static final String[] TOKENS = {
            ISSUER_NAME, CUSTOMER_NAME, PLACE, CITY, INVOICE_NO,
            SEQ, "seq3", "seq4", "seq5", "seq6",
            YEAR, YEAR_SHORT, MONTH, DAY, GIG_YEAR, GIG_MONTH, GIG_DAY, FORMAT,
    };

    private final Map<String, String> values = new HashMap<String, String>();

    public PatternFormatter put(String token, String value) {
        values.put(token, value == null ? "" : value);
        return this;
    }

    /** Registers {@code seq}, {@code seq3} .. {@code seq6} from one counter value. */
    public PatternFormatter putSequence(int sequence) {
        put(SEQ, Integer.toString(sequence));
        for (int width = 3; width <= 6; width++) {
            put(SEQ + width, pad(sequence, width));
        }
        return this;
    }

    /** Registers {@code Y}, {@code y}, {@code M} and {@code D} from an ISO date. */
    public PatternFormatter putDate(String isoDate) {
        return putDate(isoDate, YEAR, YEAR_SHORT, MONTH, DAY);
    }

    /** Registers {@code gigY}, {@code gigM} and {@code gigD} from an ISO date. */
    public PatternFormatter putGigDate(String isoDate) {
        return putDate(isoDate, GIG_YEAR, null, GIG_MONTH, GIG_DAY);
    }

    private PatternFormatter putDate(String isoDate, String yearKey, String shortYearKey,
                                     String monthKey, String dayKey) {
        if (!Dates.isValid(isoDate)) return this;
        put(yearKey, isoDate.substring(0, 4));
        if (shortYearKey != null) put(shortYearKey, isoDate.substring(2, 4));
        put(monthKey, isoDate.substring(5, 7));
        put(dayKey, isoDate.substring(8, 10));
        return this;
    }

    /** Expands the pattern. Unknown tokens are left as written so the user can see the typo. */
    public String format(String pattern) {
        if (pattern == null) return "";
        List<String> tokens = new ArrayList<String>(values.keySet());
        Collections.sort(tokens, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });

        StringBuilder out = new StringBuilder(pattern.length() + 32);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c != '%') {
                out.append(c);
                i++;
                continue;
            }
            String matched = null;
            for (String token : tokens) {
                if (pattern.startsWith(token, i + 1)) {
                    matched = token;
                    break;
                }
            }
            if (matched == null) {
                out.append('%');
                i++;
                continue;
            }
            out.append(values.get(matched));
            i += 1 + matched.length();
            // Consume the closing %, so both %Y and %Y% expand to the same thing.
            if (i < pattern.length() && pattern.charAt(i) == '%') i++;
        }
        return out.toString();
    }

    /** Expands the pattern and makes the result safe to use as a file name. */
    public String formatFileName(String pattern) {
        return Slug.fileName(format(pattern));
    }

    private static String pad(int value, int width) {
        String s = Integer.toString(value);
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        for (int i = s.length(); i < width; i++) sb.append('0');
        return sb.append(s).toString();
    }
}
