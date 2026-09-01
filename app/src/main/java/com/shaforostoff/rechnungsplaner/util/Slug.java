package com.shaforostoff.rechnungsplaner.util;

import java.util.Locale;

/**
 * Turns free text into something safe to use as a file name.
 *
 * <p>Invoices get shared into whatever the user picks from the share sheet, so the name has to
 * survive Windows shares, FAT volumes and mail clients. Umlauts are transliterated rather than
 * stripped, because "Muenchen" is still readable where "Mnchen" is not.
 */
public final class Slug {

    private static final int MAX_LENGTH = 120;

    private Slug() {
    }

    /** Transliterates German and Spanish accents to ASCII, leaving other letters alone. */
    public static String transliterate(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ä': out.append("ae"); break;
                case 'ö': out.append("oe"); break;
                case 'ü': out.append("ue"); break;
                case 'Ä': out.append("Ae"); break;
                case 'Ö': out.append("Oe"); break;
                case 'Ü': out.append("Ue"); break;
                case 'ß': out.append("ss"); break;
                case 'á': case 'à': case 'â': out.append('a'); break;
                case 'é': case 'è': case 'ê': out.append('e'); break;
                case 'í': case 'ì': case 'î': out.append('i'); break;
                case 'ó': case 'ò': case 'ô': out.append('o'); break;
                case 'ú': case 'ù': case 'û': out.append('u'); break;
                case 'ñ': out.append('n'); break;
                case 'Á': out.append('A'); break;
                case 'É': out.append('E'); break;
                case 'Í': out.append('I'); break;
                case 'Ó': out.append('O'); break;
                case 'Ú': out.append('U'); break;
                case 'Ñ': out.append('N'); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A file name without an extension: reserved characters handled, runs collapsed, and trailing
     * dots and spaces removed because Windows silently rejects those.
     *
     * <p>Reserved characters split into two kinds. Path separators and angle brackets stand between
     * words, so they become a hyphen and "Club / Bar" reads as "Club-Bar". Quotes, asterisks and
     * question marks are decoration, so they are dropped outright rather than turned into
     * punctuation nobody asked for.
     */
    public static String fileName(String s) {
        String t = transliterate(s == null ? "" : s);
        StringBuilder out = new StringBuilder(t.length());
        char pending = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '<' || c == '>' || c == '|') {
                // A structural separator outranks a pending space, so "a / b" collapses to "a-b".
                pending = '-';
            } else if (c == '"' || c == '*' || c == '?' || c < 0x20) {
                // Decoration: drop it without creating a separator.
                continue;
            } else if (c == ' ' || c == '\t') {
                if (pending == 0) pending = ' ';
            } else {
                if (pending != 0 && out.length() > 0) out.append(pending);
                pending = 0;
                out.append(c);
            }
        }
        while (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last == '.' || last == ' ' || last == '-') {
                out.setLength(out.length() - 1);
            } else {
                break;
            }
        }
        if (out.length() > MAX_LENGTH) out.setLength(MAX_LENGTH);
        return out.length() == 0 ? "invoice" : out.toString();
    }

    /** A lowercase hyphenated identifier, for the per-customer files inside the contacts archive. */
    public static String slug(String s) {
        String t = transliterate(s == null ? "" : s).toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder(t.length());
        boolean lastWasHyphen = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                lastWasHyphen = false;
            } else if (!lastWasHyphen) {
                out.append('-');
                lastWasHyphen = true;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        if (out.length() > 60) out.setLength(60);
        return out.length() == 0 ? "contact" : out.toString();
    }
}
