package com.shaforostoff.rechnungsplaner.exchange;

import com.shaforostoff.rechnungsplaner.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of a full backup, and nothing else.
 *
 * <p>No {@code android.*} imports on purpose, so the part that has to be lossless is testable
 * under plain JUnit. {@link Backup} does the SQLite reading, the zip and the files; this decides
 * what a row looks like on disk and reads it back.
 *
 * <p>Tables are dumped column by column rather than field by field. A hand-written POJO writer
 * would have to be revisited every time a column is added, and the one that gets forgotten is the
 * one that silently vanishes from every backup made afterwards. Asking the database what its
 * columns are means the backup is complete by construction.
 *
 * <p>Three distinctions the format has to keep, because getting any of them wrong loses data
 * quietly rather than loudly:
 *
 * <ul>
 *   <li>A column holding SQL NULL writes a JSON {@code null}. A column the writing build did not
 *       have is absent from the row. On the way back in, null means write null and absent means
 *       let the column's default decide -- which is what lets a backup from an older build
 *       restore into a newer one without tripping a {@code NOT NULL}.</li>
 *   <li>Integers stay JSON numbers and text stays a JSON string, so the file says which is which
 *       and a number-looking customer number like {@code "10042"} does not come back as an
 *       integer. Every integer this app stores -- cents, permille, ids, epoch millis -- is far
 *       inside the range a double holds exactly, which is what JSON numbers parse as.</li>
 *   <li>Preferences carry their type with them. {@link android.content.SharedPreferences} throws
 *       when asked for a long it stored as an int, so "3" is not enough information.</li>
 * </ul>
 */
public final class BackupFormat {

    /** Bumped only if a reader would need to behave differently, which has not happened yet. */
    public static final int VERSION = 1;

    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String DATABASE_ENTRY = "database.json";
    public static final String SETTINGS_ENTRY = "settings.json";
    /** Everything under here is a file from the invoice archive, path and all. */
    public static final String FILES_PREFIX = "files/";

    /** What tells a full backup apart from the contacts archive, which is also a zip. */
    public static final String KIND = "full-backup";

    private BackupFormat() {
    }

    /** One table's rows. Column names are carried alongside so the file explains itself. */
    public static final class Table {
        public final String name;
        public final List<String> columns = new ArrayList<String>();
        /** Values are null, {@code Long}, {@code Double} or {@code String}, and nothing else. */
        public final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();

        public Table(String name) {
            this.name = name;
        }
    }

    /** One preference, with the type it has to be given back as. */
    public static final class Setting {
        public final String key;
        public final Object value;

        public Setting(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    /** A whole backup, read or about to be written. */
    public static final class Document {
        public int formatVersion = VERSION;
        /** The schema the database was on, for a reader deciding whether it understands it. */
        public int schemaVersion;
        public String createdAt;
        public String appVersion;
        public final List<Table> tables = new ArrayList<Table>();
        public final List<Setting> settings = new ArrayList<Setting>();
        /** Files in the invoice archive; set by the writer, counted by the reader. */
        public int files;
        /** Anything skipped or unreadable, reported rather than thrown. */
        public final List<String> warnings = new ArrayList<String>();

        public Table table(String name) {
            for (Table t : tables) {
                if (t.name.equals(name)) return t;
            }
            return null;
        }

        public int rowCount(String tableName) {
            Table t = table(tableName);
            return t == null ? 0 : t.rows.size();
        }

        public int rowCount() {
            int n = 0;
            for (Table t : tables) n += t.rows.size();
            return n;
        }
    }

    // ------------------------------------------------------------------ write

    /** The summary a reader can look at without parsing the data. */
    public static String writeManifest(Document doc) {
        Json.Obj root = new Json.Obj();
        root.put("app", "Rechnungsplaner");
        root.put("kind", KIND);
        root.put("formatVersion", doc.formatVersion);
        root.put("schemaVersion", doc.schemaVersion);
        root.put("createdAt", doc.createdAt);
        root.put("appVersion", doc.appVersion);
        root.put("files", doc.files);
        root.put("settings", doc.settings.size());
        Json.Obj counts = new Json.Obj();
        for (Table t : doc.tables) counts.put(t.name, t.rows.size());
        root.put("rows", counts);
        if (!doc.warnings.isEmpty()) {
            // Recorded in the file rather than only shown once: a backup that had to leave
            // something behind should still say so when it is opened years later.
            Json.Arr warnings = new Json.Arr();
            for (String warning : doc.warnings) warnings.add(warning);
            root.put("warnings", warnings);
        }
        return root.toJson();
    }

    public static String writeDatabase(Document doc) {
        Json.Obj root = new Json.Obj();
        root.put("formatVersion", doc.formatVersion);
        root.put("schemaVersion", doc.schemaVersion);
        Json.Obj tables = new Json.Obj();
        for (Table t : doc.tables) {
            Json.Arr columns = new Json.Arr();
            for (String column : t.columns) columns.add(column);
            Json.Arr rows = new Json.Arr();
            for (Map<String, Object> row : t.rows) rows.add(rowJson(t.columns, row));
            tables.put(t.name, new Json.Obj().put("columns", columns).put("rows", rows));
        }
        root.put("tables", tables);
        return root.toJson();
    }

    private static Json.Obj rowJson(List<String> columns, Map<String, Object> row) {
        Json.Obj out = new Json.Obj();
        for (String column : columns) {
            if (!row.containsKey(column)) continue;
            Object v = row.get(column);
            if (v == null) out.putNull(column);
            else if (v instanceof Long) out.put(column, ((Long) v).longValue());
            else if (v instanceof Double) out.put(column, ((Double) v).doubleValue());
            else out.putAlways(column, String.valueOf(v));
        }
        return out;
    }

    public static String writeSettings(Document doc) {
        Json.Arr entries = new Json.Arr();
        for (Setting s : doc.settings) {
            Json.Obj entry = new Json.Obj();
            entry.put("key", s.key);
            Object v = s.value;
            if (v instanceof Boolean) {
                entry.put("type", "boolean");
                entry.put("value", ((Boolean) v).booleanValue());
            } else if (v instanceof Integer) {
                entry.put("type", "int");
                entry.put("value", ((Integer) v).intValue());
            } else if (v instanceof Long) {
                entry.put("type", "long");
                entry.put("value", ((Long) v).longValue());
            } else if (v instanceof Float) {
                entry.put("type", "float");
                entry.put("value", ((Float) v).floatValue());
            } else if (v instanceof String) {
                entry.put("type", "string");
                entry.putAlways("value", (String) v);
            } else {
                // Not a type SharedPreferences hands back, so guessing would be worse than
                // saying so: the backup is incomplete and the user should know which key.
                doc.warnings.add("setting " + s.key + ": type not backed up");
                continue;
            }
            entries.add(entry);
        }
        return new Json.Obj().put("settings", entries).toJson();
    }

    // ------------------------------------------------------------------- read

    /** Whether a manifest says this zip is a full backup rather than the contacts archive. */
    public static boolean isFullBackup(String manifestJson) {
        try {
            return KIND.equals(Json.string(Json.parse(manifestJson), "kind"));
        } catch (Json.MalformedJsonException e) {
            return false;
        }
    }

    public static void readManifest(String json, Document doc)
            throws Json.MalformedJsonException {
        Object node = Json.parse(json);
        doc.formatVersion = (int) Json.number(node, VERSION, "formatVersion");
        doc.schemaVersion = (int) Json.number(node, 0, "schemaVersion");
        doc.createdAt = Json.string(node, "createdAt");
        doc.appVersion = Json.string(node, "appVersion");
    }

    public static void readDatabase(String json, Document doc)
            throws Json.MalformedJsonException {
        Object node = Json.parse(json);
        if (doc.schemaVersion == 0) {
            doc.schemaVersion = (int) Json.number(node, 0, "schemaVersion");
        }
        Map<String, Object> tables = Json.object(Json.at(node, "tables"));
        if (tables == null) throw new Json.MalformedJsonException("no tables in " + DATABASE_ENTRY);

        for (Map.Entry<String, Object> e : tables.entrySet()) {
            Table table = new Table(e.getKey());
            List<Object> columns = Json.array(e.getValue(), "columns");
            if (columns != null) {
                for (Object c : columns) {
                    if (c instanceof String) table.columns.add((String) c);
                }
            }
            List<Object> rows = Json.array(e.getValue(), "rows");
            if (rows != null) {
                for (Object r : rows) readRow(r, table, doc);
            }
            doc.tables.add(table);
        }
    }

    private static void readRow(Object node, Table table, Document doc) {
        Map<String, Object> src = Json.object(node);
        if (src == null) {
            doc.warnings.add(table.name + ": a row was not an object and was skipped");
            return;
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> cell : src.entrySet()) {
            Object v = cell.getValue();
            if (v == null || v instanceof String) {
                row.put(cell.getKey(), v);
            } else if (v instanceof Double) {
                row.put(cell.getKey(), narrow(((Double) v).doubleValue()));
            } else if (v instanceof Boolean) {
                // Not something this app writes, but a hand-edited backup might; SQLite has no
                // boolean, so it is the 0 or 1 it would have been stored as.
                row.put(cell.getKey(), Long.valueOf(((Boolean) v).booleanValue() ? 1L : 0L));
            } else {
                doc.warnings.add(table.name + "." + cell.getKey() + ": not a value, skipped");
            }
        }
        table.rows.add(row);
    }

    /**
     * A JSON number back to the type it left as.
     *
     * <p>Every number goes through a double on the way in, so a whole one comes back as a long
     * and only a genuinely fractional one stays a double. Integers this app stores reach about
     * 1e13 at the largest -- epoch milliseconds -- against the 9e15 a double holds exactly, so
     * nothing is rounded on the way through.
     */
    private static Object narrow(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) <= 9.007199254740992E15) {
            return Long.valueOf((long) d);
        }
        return Double.valueOf(d);
    }

    public static void readSettings(String json, Document doc)
            throws Json.MalformedJsonException {
        Object node = Json.parse(json);
        List<Object> entries = Json.array(node, "settings");
        if (entries == null) return;
        for (Object entry : entries) {
            String key = Json.string(entry, "key");
            String type = Json.string(entry, "type");
            if (key == null || type == null) continue;
            Object raw = Json.at(entry, "value");
            Object value = typed(type, raw);
            if (value == null) {
                doc.warnings.add("setting " + key + ": unreadable " + type + ", skipped");
                continue;
            }
            doc.settings.add(new Setting(key, value));
        }
    }

    /** The declared type decides, not what JSON happened to parse it as. */
    private static Object typed(String type, Object raw) {
        if ("string".equals(type)) return raw instanceof String ? raw : null;
        if ("boolean".equals(type)) return raw instanceof Boolean ? raw : null;
        if (!(raw instanceof Double)) return null;
        double d = ((Double) raw).doubleValue();
        if ("int".equals(type)) return Integer.valueOf((int) Math.round(d));
        if ("long".equals(type)) return Long.valueOf(Math.round(d));
        if ("float".equals(type)) return Float.valueOf((float) d);
        return null;
    }
}
