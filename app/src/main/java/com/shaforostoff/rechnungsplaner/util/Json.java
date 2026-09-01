package com.shaforostoff.rechnungsplaner.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small JSON writer and parser.
 *
 * <p>{@code android.util.JsonWriter} would do the writing, and the sibling projects use it, but it
 * is Android-only. The contacts archive has to match a documented external shape -- the body
 * lexoffice's {@code /v1/contacts} accepts -- and that is worth pinning with golden-file tests on
 * the JVM. The same codec then serves the invoice party snapshots, which need reading back as well
 * as writing.
 *
 * <p>Insertion order is preserved so the output is stable and diffable between exports.
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- writing

    /** A JSON object under construction. Null values are skipped rather than written as null. */
    public static final class Obj {
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();

        public Obj put(String key, String value) {
            if (value != null && !value.trim().isEmpty()) values.put(key, value.trim());
            return this;
        }

        /** Writes the value even when blank, for fields a consumer expects to always be present. */
        public Obj putAlways(String key, String value) {
            values.put(key, value == null ? "" : value);
            return this;
        }

        public Obj put(String key, long value) {
            values.put(key, Long.valueOf(value));
            return this;
        }

        public Obj put(String key, boolean value) {
            values.put(key, Boolean.valueOf(value));
            return this;
        }

        public Obj put(String key, Obj value) {
            if (value != null) values.put(key, value);
            return this;
        }

        public Obj put(String key, Arr value) {
            if (value != null) values.put(key, value);
            return this;
        }

        /** An empty object, which lexoffice uses to mean "has this role". */
        public Obj putEmptyObject(String key) {
            values.put(key, new Obj());
            return this;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder(512);
            writeObject(sb, this, 0);
            return sb.append('\n').toString();
        }
    }

    /** A JSON array under construction. */
    public static final class Arr {
        private final List<Object> items = new ArrayList<Object>();

        public Arr add(String value) {
            if (value != null && !value.trim().isEmpty()) items.add(value.trim());
            return this;
        }

        public Arr add(Obj value) {
            if (value != null && !value.isEmpty()) items.add(value);
            return this;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private static void writeObject(StringBuilder sb, Obj obj, int depth) {
        if (obj.values.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : obj.values.entrySet()) {
            indent(sb, depth + 1);
            writeString(sb, e.getKey());
            sb.append(": ");
            writeValue(sb, e.getValue(), depth + 1);
            if (++i < obj.values.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, depth);
        sb.append('}');
    }

    private static void writeValue(StringBuilder sb, Object value, int depth) {
        if (value instanceof Obj) {
            writeObject(sb, (Obj) value, depth);
        } else if (value instanceof Arr) {
            Arr arr = (Arr) value;
            if (arr.items.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < arr.items.size(); i++) {
                indent(sb, depth + 1);
                writeValue(sb, arr.items.get(i), depth + 1);
                if (i + 1 < arr.items.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer) {
            sb.append(value.toString());
        } else if (value instanceof Double) {
            sb.append(String.format(Locale.US, "%s", value));
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    // ---------------------------------------------------------------- reading

    /** Thrown for input that is not JSON at all, so an import can report it rather than crash. */
    public static class MalformedJsonException extends Exception {
        public MalformedJsonException(String message) {
            super(message);
        }
    }

    /**
     * Parses into {@code Map}, {@code List}, {@code String}, {@code Double}, {@code Boolean} or
     * null.
     */
    public static Object parse(String text) throws MalformedJsonException {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (!p.atEnd()) throw new MalformedJsonException("trailing content at " + p.position());
        return value;
    }

    /** Walks a path of object keys, returning null the moment anything does not match. */
    public static Object at(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map)) return null;
            current = ((Map<?, ?>) current).get(key);
        }
        return current;
    }

    public static String string(Object node, String... path) {
        Object v = at(node, path);
        return v instanceof String ? (String) v : null;
    }

    public static boolean bool(Object node, boolean fallback, String... path) {
        Object v = at(node, path);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : fallback;
    }

    public static long number(Object node, long fallback, String... path) {
        Object v = at(node, path);
        if (v instanceof Double) return Math.round(((Double) v).doubleValue());
        if (v instanceof String) {
            try {
                return Long.parseLong(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object node, String... path) {
        Object v = at(node, path);
        return v instanceof List ? (List<Object>) v : null;
    }

    /** The first string in an array of strings, e.g. {@code emailAddresses.business[0]}. */
    public static String firstString(Object node, String... path) {
        List<Object> list = array(node, path);
        if (list == null) return null;
        for (Object o : list) {
            if (o instanceof String && !((String) o).trim().isEmpty()) return ((String) o).trim();
        }
        return null;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        int position() {
            return i;
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                // A leading BOM is common in files exported by other tools; skip it as whitespace.
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') i++;
                else break;
            }
        }

        Object readValue() throws MalformedJsonException {
            skipWhitespace();
            if (atEnd()) throw new MalformedJsonException("unexpected end of input");
            char c = s.charAt(i);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return readNumber();
        }

        Map<String, Object> readObject() throws MalformedJsonException {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') { i++; return out; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                out.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == '}') return out;
                if (c != ',') throw new MalformedJsonException("expected , or } at " + i);
            }
        }

        List<Object> readArray() throws MalformedJsonException {
            List<Object> out = new ArrayList<Object>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') { i++; return out; }
            while (true) {
                out.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') return out;
                if (c != ',') throw new MalformedJsonException("expected , or ] at " + i);
            }
        }

        String readString() throws MalformedJsonException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new MalformedJsonException("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) throw new MalformedJsonException("unterminated escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new MalformedJsonException("short \\u");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw new MalformedJsonException("bad escape \\" + e);
                }
            }
        }

        Double readNumber() throws MalformedJsonException {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e'
                        || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            if (start == i) throw new MalformedJsonException("expected a value at " + i);
            try {
                return Double.valueOf(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new MalformedJsonException("bad number at " + start);
            }
        }

        char peek() {
            return atEnd() ? '\0' : s.charAt(i);
        }

        char next() throws MalformedJsonException {
            if (atEnd()) throw new MalformedJsonException("unexpected end of input");
            return s.charAt(i++);
        }

        void expect(char c) throws MalformedJsonException {
            if (next() != c) throw new MalformedJsonException("expected " + c + " at " + (i - 1));
        }
    }
}
