package com.shaforostoff.rechnungsplaner.exchange;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.shaforostoff.rechnungsplaner.data.Db;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.output.InvoiceWriter;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.Json;
import com.shaforostoff.rechnungsplaner.util.Paths;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Everything the app holds, out to one zip and back again.
 *
 * <pre>
 * manifest.json                what made this, when, and how much of it there is
 * database.json                every table, every column, every row
 * settings.json                every preference, each with its type
 * files/invoices/12/&hellip;.pdf     the invoices as they were actually sent
 * </pre>
 *
 * <p>Distinct from the contacts archive, which is a lexoffice-shaped export of the address book
 * for other software to read. This one is for getting this app back, and includes the things the
 * contacts archive deliberately leaves out: the jobs, the invoices with their frozen party
 * snapshots, the number counters, the settings, and the generated PDFs and XML themselves. Those
 * last ones matter because section 147 AO retains the document that was sent, and a rebuilt one
 * is a different file even when it says the same thing.
 *
 * <p>The database is copied row by row rather than by lifting the {@code .db} file. Two reasons:
 * a live SQLite database has a journal beside it that a plain file copy would leave behind, and a
 * restore that swaps files has to reach underneath an open connection. Reading rows needs neither,
 * survives a schema that has moved on, and produces a file the user can open and read.
 */
public class Backup {

    private final Context ctx;
    private final Db db;
    private final SettingsStore settings;

    public Backup(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = Db.get(this.ctx);
        this.settings = new SettingsStore(this.ctx);
    }

    public String suggestedFileName() {
        return "rechnungsplaner-backup-" + Dates.today() + ".zip";
    }

    // ----------------------------------------------------------------- backup

    /** Writes the backup into {@code targetDir} and returns it. */
    public File export(File targetDir) throws IOException {
        targetDir.mkdirs();
        File file = new File(targetDir, suggestedFileName());
        OutputStream raw = new FileOutputStream(file);
        try {
            writeTo(raw);
        } finally {
            raw.close();
        }
        return file;
    }

    void writeTo(OutputStream raw) throws IOException {
        BackupFormat.Document doc = new BackupFormat.Document();
        doc.createdAt = Dates.iso8601(System.currentTimeMillis());
        doc.appVersion = appVersion();
        readTables(doc);
        readSettings(doc);

        File root = InvoiceWriter.archiveRoot(ctx);
        List<File> files = new ArrayList<File>();
        collectFiles(root, files);
        doc.files = files.size();

        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            // Rendered before the manifest so that anything the settings pass had to skip is
            // already in the warnings the manifest records; written after it, so a reader
            // skimming the zip meets the summary first.
            String settingsJson = BackupFormat.writeSettings(doc);
            putText(zip, BackupFormat.MANIFEST_ENTRY, BackupFormat.writeManifest(doc));
            putText(zip, BackupFormat.DATABASE_ENTRY, BackupFormat.writeDatabase(doc));
            putText(zip, BackupFormat.SETTINGS_ENTRY, settingsJson);
            for (File file : files) {
                putFile(zip, BackupFormat.FILES_PREFIX + relativeTo(ctx.getFilesDir(), file),
                        file);
            }
        } finally {
            zip.close();
        }
    }

    private void readTables(BackupFormat.Document doc) {
        SQLiteDatabase r = db.getReadableDatabase();
        doc.schemaVersion = r.getVersion();
        for (String name : Db.TABLES) {
            BackupFormat.Table table = new BackupFormat.Table(name);
            // rowid rather than _id: every table here is a rowid table, but `counter` is keyed by
            // a text column and has no _id of its own.
            Cursor c = r.query(name, null, null, null, null, null, "rowid");
            try {
                table.columns.addAll(Arrays.asList(c.getColumnNames()));
                while (c.moveToNext()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        row.put(c.getColumnName(i), valueOf(c, i, name, doc));
                    }
                    table.rows.add(row);
                }
            } finally {
                c.close();
            }
            doc.tables.add(table);
        }
    }

    /** The stored value with its storage class kept, which is what makes the copy lossless. */
    private static Object valueOf(Cursor c, int i, String table, BackupFormat.Document doc) {
        switch (c.getType(i)) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_INTEGER:
                return Long.valueOf(c.getLong(i));
            case Cursor.FIELD_TYPE_FLOAT:
                return Double.valueOf(c.getDouble(i));
            case Cursor.FIELD_TYPE_BLOB:
                // No column in this schema stores one. Saying so beats writing a mangled string.
                doc.warnings.add(table + "." + c.getColumnName(i) + ": binary, not backed up");
                return null;
            default:
                return c.getString(i);
        }
    }

    private void readSettings(BackupFormat.Document doc) {
        // Sorted, so two backups of unchanged settings are the same bytes and a diff is readable.
        Map<String, Object> sorted = new TreeMap<String, Object>();
        sorted.putAll(settings.all());
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            doc.settings.add(new BackupFormat.Setting(e.getKey(), e.getValue()));
        }
    }

    private static void collectFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        // Sorted for the same reason the settings are: a stable file order.
        List<File> ordered = new ArrayList<File>(Arrays.asList(children));
        Collections.sort(ordered);
        for (File child : ordered) {
            if (child.isDirectory()) collectFiles(child, out);
            else out.add(child);
        }
    }

    // ---------------------------------------------------------------- restore

    /** What a restore would replace, so the user can look before it happens. */
    public static class RestorePlan {
        /** The zip this came from; the files are read again from it when the plan is applied. */
        public Uri source;
        /** False when the zip is readable but is not one of these backups. */
        public boolean recognised;
        public BackupFormat.Document document = new BackupFormat.Document();
        /** Files under {@code files/} in the zip. */
        public int files;
        /**
         * Whether the zip carried {@code settings.json} at all.
         *
         * <p>Not the same question as whether it carried any settings. A backup of an app whose
         * preferences are all still at their defaults has an empty list, and restoring it should
         * put this phone back to defaults too. A zip with no settings document at all is a
         * different thing, and clearing every preference on the strength of a file that was never
         * there would be the restore inventing an instruction.
         */
        public boolean hasSettings;

        public int customers() {
            return document.rowCount(Db.T_CUSTOMER);
        }

        public int gigs() {
            return document.rowCount(Db.T_GIG);
        }

        public int invoices() {
            return document.rowCount(Db.T_INVOICE);
        }
    }

    /**
     * Reads the zip's three documents and works out what restoring it would do.
     *
     * <p>Nothing is written. The archive files are counted here and read again by {@link #apply},
     * which is a second pass over a local file in exchange for never staging anything that a
     * cancelled restore would leave behind.
     */
    public RestorePlan plan(Uri uri) throws IOException {
        RestorePlan plan = new RestorePlan();
        plan.source = uri;
        String manifest = null;
        String database = null;
        String settingsJson = null;

        ZipInputStream zip = new ZipInputStream(open(uri));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (BackupFormat.MANIFEST_ENTRY.equals(name)) manifest = text(zip);
                else if (BackupFormat.DATABASE_ENTRY.equals(name)) database = text(zip);
                else if (BackupFormat.SETTINGS_ENTRY.equals(name)) settingsJson = text(zip);
                else if (name.startsWith(BackupFormat.FILES_PREFIX)) plan.files++;
            }
        } finally {
            zip.close();
        }

        if (database == null) return plan;
        try {
            if (manifest != null) BackupFormat.readManifest(manifest, plan.document);
            BackupFormat.readDatabase(database, plan.document);
            if (settingsJson != null) {
                BackupFormat.readSettings(settingsJson, plan.document);
                plan.hasSettings = true;
            } else {
                plan.document.warnings.add(BackupFormat.SETTINGS_ENTRY
                        + ": missing, settings will be left alone");
            }
        } catch (Json.MalformedJsonException e) {
            throw new IOException(e.getMessage());
        }
        plan.recognised = true;
        warnAboutTables(plan);
        return plan;
    }

    /** What the two builds disagree about, said out loud before anything is replaced. */
    private static void warnAboutTables(RestorePlan plan) {
        for (String name : Db.TABLES) {
            if (plan.document.table(name) == null) {
                plan.document.warnings.add(name + ": not in this backup, will be emptied");
            }
        }
        for (BackupFormat.Table table : plan.document.tables) {
            if (!Arrays.asList(Db.TABLES).contains(table.name)) {
                plan.document.warnings.add(table.name + ": unknown table, will be ignored");
            }
        }
    }

    /**
     * Replaces everything with what the backup holds.
     *
     * <p>The database goes first and in one transaction, so a row the current schema cannot take
     * leaves the phone exactly as it was rather than half replaced. Settings follow, then the
     * archive files -- in that order because a failure late on leaves invoices whose PDFs can be
     * generated again, where the reverse would leave files belonging to invoices that no longer
     * exist.
     */
    public void apply(RestorePlan plan) throws IOException {
        restoreTables(plan.document);
        if (plan.hasSettings) restoreSettings(plan.document);
        restoreFiles(plan);
    }

    private void restoreTables(BackupFormat.Document doc) throws IOException {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            for (int i = Db.TABLES.length - 1; i >= 0; i--) {
                w.delete(Db.TABLES[i], null, null);
            }
            for (String name : Db.TABLES) {
                BackupFormat.Table table = doc.table(name);
                if (table == null) continue;
                List<String> live = Db.columnsOf(w, name);
                for (Map<String, Object> row : table.rows) {
                    w.insertOrThrow(name, null, valuesFor(live, row));
                }
            }
            w.setTransactionSuccessful();
        } catch (SQLException e) {
            // Rolled back by endTransaction below; reported as the read failure it looks like
            // from the outside, which is a file this build cannot take.
            throw new IOException("could not restore the database: " + e.getMessage());
        } finally {
            w.endTransaction();
        }
    }

    /**
     * One row, reduced to the columns this build actually has.
     *
     * <p>A column the backup carries and the schema no longer has is dropped -- restoring a newer
     * backup keeps everything the older build can hold instead of failing outright. A column the
     * schema has and the row does not mention is left unset, so its default applies; that is the
     * case that matters, because setting it to null instead would break every {@code NOT NULL}
     * column added since the backup was written.
     */
    private static ContentValues valuesFor(List<String> live, Map<String, Object> row) {
        ContentValues v = new ContentValues();
        for (String column : live) {
            if (!row.containsKey(column)) continue;
            Object value = row.get(column);
            if (value == null) v.putNull(column);
            else if (value instanceof Long) v.put(column, (Long) value);
            else if (value instanceof Double) v.put(column, (Double) value);
            else v.put(column, String.valueOf(value));
        }
        return v;
    }

    private void restoreSettings(BackupFormat.Document doc) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (BackupFormat.Setting s : doc.settings) values.put(s.key, s.value);
        settings.replaceAll(values);
        // The export folder is a grant, not a path. Restored onto a different install the grant is
        // not held, and writing into it would throw a SecurityException from deep inside the save
        // action rather than telling the user to pick a folder.
        String tree = settings.getExportTreeUri();
        if (tree != null && !holdsPermission(tree)) settings.clearExportTreeUri();
    }

    private boolean holdsPermission(String treeUri) {
        for (UriPermission held : ctx.getContentResolver().getPersistedUriPermissions()) {
            if (held.getUri().toString().equals(treeUri) && held.isWritePermission()) return true;
        }
        return false;
    }

    private void restoreFiles(RestorePlan plan) throws IOException {
        File root = InvoiceWriter.archiveRoot(ctx);
        deleteTree(root);
        root.mkdirs();

        ZipInputStream zip = new ZipInputStream(open(plan.source));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(BackupFormat.FILES_PREFIX)) continue;
                File target = new File(ctx.getFilesDir(),
                        name.substring(BackupFormat.FILES_PREFIX.length()));
                // A zip entry is a string from a file the app did not write. Anything that does
                // not land inside the invoice archive -- '../', an absolute path, a name aimed at
                // the databases directory -- is refused rather than written.
                if (!Paths.isInside(root, target)) {
                    plan.document.warnings.add(name + ": outside the archive, not restored");
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                write(zip, target);
                if (entry.getTime() > 0L) target.setLastModified(entry.getTime());
            }
        } finally {
            zip.close();
        }
    }

    // ------------------------------------------------------------------ plumbing

    private InputStream open(Uri uri) throws IOException {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("cannot open " + uri);
        return in;
    }

    private String appVersion() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    private static String relativeTo(File dir, File file) {
        String parent = dir.getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.startsWith(parent + File.separator)
                ? path.substring(parent.length() + 1) : file.getName();
    }

    private static void putText(ZipOutputStream zip, String name, String content)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // A fixed timestamp keeps two backups of unchanged data byte-identical, so a diff of two
        // of them shows what really changed. The real date is inside the manifest.
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        InputStream in = new FileInputStream(file);
        try {
            copy(in, zip);
        } finally {
            in.close();
        }
        zip.closeEntry();
    }

    private static void write(InputStream in, File target) throws IOException {
        OutputStream out = new FileOutputStream(target);
        try {
            copy(in, out);
        } finally {
            out.close();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    }

    private static String text(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        copy(in, out);
        return new String(out.toByteArray(), "UTF-8");
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
