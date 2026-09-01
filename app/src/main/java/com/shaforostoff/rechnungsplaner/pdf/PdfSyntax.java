package com.shaforostoff.rechnungsplaner.pdf;

import java.nio.charset.Charset;

/**
 * Just enough PDF syntax to read a trailer and rewrite one dictionary.
 *
 * <p>The file is handled as an ISO-8859-1 string rather than a byte array. That encoding maps every
 * byte 0..255 to the character of the same value and back, so compressed streams round-trip
 * unchanged while the structural parts stay readable as text. Invoices are small enough that
 * holding the document twice costs nothing.
 *
 * <p>This is deliberately not a PDF parser. It locates a dictionary, splices keys into it, and
 * leaves every byte it does not understand exactly where it found it — which is far safer than
 * round-tripping an entire document through a model that only half-understands it.
 */
final class PdfSyntax {

    static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private PdfSyntax() {
    }

    static String toText(byte[] bytes) {
        return new String(bytes, LATIN1);
    }

    static byte[] toBytes(String text) {
        return text.getBytes(LATIN1);
    }

    static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == 0;
    }

    static boolean isDelimiter(char c) {
        return c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']'
                || c == '{' || c == '}' || c == '/' || c == '%';
    }

    static int skipWhitespace(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
            } else if (isWhitespace(c)) {
                i++;
            } else {
                return i;
            }
        }
        return i;
    }

    /** Index just past the object starting at {@code i}, whatever kind of object it is. */
    static int skipValue(String s, int i) {
        i = skipWhitespace(s, i);
        if (i >= s.length()) return i;
        char c = s.charAt(i);

        if (c == '/') {
            i++;
            while (i < s.length() && !isWhitespace(s.charAt(i)) && !isDelimiter(s.charAt(i))) i++;
            return i;
        }
        if (c == '(') return skipLiteralString(s, i);
        if (c == '[') return skipBracketed(s, i, '[', ']');
        if (c == '<') {
            if (i + 1 < s.length() && s.charAt(i + 1) == '<') return endOfDict(s, i);
            int j = s.indexOf('>', i);
            return j < 0 ? s.length() : j + 1;
        }
        if (s.startsWith("true", i)) return i + 4;
        if (s.startsWith("false", i)) return i + 5;
        if (s.startsWith("null", i)) return i + 4;

        // A number, or the "12 0 R" of an indirect reference.
        int afterFirst = skipToken(s, i);
        int probe = skipWhitespace(s, afterFirst);
        int afterSecond = skipToken(s, probe);
        if (afterSecond > probe && isInteger(s, i, afterFirst) && isInteger(s, probe, afterSecond)) {
            int third = skipWhitespace(s, afterSecond);
            if (third < s.length() && s.charAt(third) == 'R'
                    && (third + 1 >= s.length() || isWhitespace(s.charAt(third + 1))
                    || isDelimiter(s.charAt(third + 1)))) {
                return third + 1;
            }
        }
        return afterFirst;
    }

    /** Index just past the {@code >>} closing the dictionary that starts at {@code i}. */
    static int endOfDict(String s, int i) {
        int depth = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') {
                i = skipLiteralString(s, i);
                continue;
            }
            if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) == '<') {
                depth++;
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < s.length() && s.charAt(i + 1) == '>') {
                depth--;
                i += 2;
                if (depth == 0) return i;
                continue;
            }
            i++;
        }
        return s.length();
    }

    /** The complete {@code << .. >>} starting at {@code i}. */
    static String dictAt(String s, int i) {
        return s.substring(i, endOfDict(s, i));
    }

    /** The raw text of a top-level key's value, or null when the key is absent. */
    static String value(String dict, String key) {
        int i = indexOfKey(dict, key);
        if (i < 0) return null;
        int start = skipWhitespace(dict, i + key.length());
        return dict.substring(start, skipValue(dict, start)).trim();
    }

    static int intValue(String dict, String key, int fallback) {
        String v = value(dict, key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static long longValue(String dict, String key, long fallback) {
        String v = value(dict, key);
        if (v == null) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The dictionary with {@code key} and its value removed, if present. */
    static String withoutKey(String dict, String key) {
        int i = indexOfKey(dict, key);
        if (i < 0) return dict;
        int start = skipWhitespace(dict, i + key.length());
        int end = skipValue(dict, start);
        return dict.substring(0, i) + dict.substring(end);
    }

    /** The dictionary with extra entries spliced in before its closing {@code >>}. */
    static String withEntries(String dict, String entries) {
        int close = dict.lastIndexOf(">>");
        if (close < 0) return dict;
        return dict.substring(0, close) + entries + dict.substring(close);
    }

    /**
     * Index of a key at the dictionary's top level.
     *
     * <p>Scanning rather than {@code indexOf} matters: a nested dictionary can hold the same key,
     * and a literal string can contain anything at all, so a plain search would happily find
     * {@code /Metadata} inside a document title and corrupt the file.
     */
    private static int indexOfKey(String dict, String key) {
        int i = skipWhitespace(dict, 0);
        if (!dict.startsWith("<<", i)) return -1;
        i += 2;
        while (true) {
            i = skipWhitespace(dict, i);
            if (i >= dict.length() || dict.startsWith(">>", i)) return -1;
            if (dict.charAt(i) != '/') return -1;
            int nameEnd = i + 1;
            while (nameEnd < dict.length() && !isWhitespace(dict.charAt(nameEnd))
                    && !isDelimiter(dict.charAt(nameEnd))) {
                nameEnd++;
            }
            String name = dict.substring(i, nameEnd);
            if (name.equals(key)) return i;
            i = skipValue(dict, nameEnd);
        }
    }

    static int[] intArray(String arrayText) {
        if (arrayText == null) return new int[0];
        String inner = arrayText.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        String[] parts = inner.trim().split("\\s+");
        int count = 0;
        int[] out = new int[parts.length];
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                out[count++] = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
                // Non-numeric entries cannot appear in /W or /Index; skip defensively.
            }
        }
        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    /** The object number of a {@code "12 0 R"} reference, or -1. */
    static int referenceNumber(String reference) {
        if (reference == null) return -1;
        String t = reference.trim();
        int space = t.indexOf(' ');
        if (space <= 0) return -1;
        try {
            return Integer.parseInt(t.substring(0, space));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int skipLiteralString(String s, int i) {
        i++;
        int depth = 1;
        while (i < s.length() && depth > 0) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            i++;
        }
        return i;
    }

    private static int skipBracketed(String s, int i, char open, char close) {
        i++;
        int depth = 1;
        while (i < s.length() && depth > 0) {
            char c = s.charAt(i);
            if (c == '(') {
                i = skipLiteralString(s, i);
                continue;
            }
            if (c == open) depth++;
            else if (c == close) depth--;
            i++;
        }
        return i;
    }

    private static int skipToken(String s, int i) {
        int j = i;
        while (j < s.length() && !isWhitespace(s.charAt(j)) && !isDelimiter(s.charAt(j))) j++;
        return j;
    }

    private static boolean isInteger(String s, int from, int to) {
        if (to <= from) return false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (i == from && (c == '+' || c == '-')) continue;
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
}
