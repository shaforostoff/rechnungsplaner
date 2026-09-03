package com.shaforostoff.rechnungsplaner.exchange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.shaforostoff.rechnungsplaner.util.Json;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a backup must not lose on the way out and back.
 *
 * <p>Everything here is a round trip rather than a check against expected text: the question is
 * never what the file looks like, it is whether the value that went in is the value that comes
 * out. A backup that reads back almost right is worse than one that fails outright, because the
 * damage is only noticed when the data is needed.
 */
public class BackupFormatTest {

    private static BackupFormat.Document withRow(String table, String[] columns,
                                                 Map<String, Object> row) {
        BackupFormat.Document doc = new BackupFormat.Document();
        BackupFormat.Table t = new BackupFormat.Table(table);
        for (String c : columns) t.columns.add(c);
        t.rows.add(row);
        doc.tables.add(t);
        return doc;
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    private static BackupFormat.Document reread(BackupFormat.Document doc) throws Exception {
        BackupFormat.Document back = new BackupFormat.Document();
        BackupFormat.readDatabase(BackupFormat.writeDatabase(doc), back);
        return back;
    }

    private static Map<String, Object> firstRow(BackupFormat.Document doc, String table) {
        return doc.table(table).rows.get(0);
    }

    @Test
    public void everyKindOfStoredValueSurvives() throws Exception {
        Map<String, Object> before = row(
                "_id", Long.valueOf(7L),
                "number", "2026-008",
                "note", "Zwei Zeilen\nund ein \"Zitat\"",
                "city", "Bremen",
                "fee_cents", Long.valueOf(45000L),
                "created_at", Long.valueOf(1757000000000L),
                "balance", Long.valueOf(-1250L),
                "due_date", null,
                "payment_terms", "");
        String[] columns = {"_id", "number", "note", "city", "fee_cents", "created_at",
                "balance", "due_date", "payment_terms"};

        Map<String, Object> after = firstRow(reread(withRow("invoice", columns, before)),
                "invoice");

        assertEquals(before, after);
        assertTrue("an epoch millisecond value must not go through a float",
                after.get("created_at") instanceof Long);
        assertEquals("an empty string is a value, not a null", "", after.get("payment_terms"));
        assertTrue(after.containsKey("due_date"));
        assertNull(after.get("due_date"));
    }

    @Test
    public void aNumberLikeTextValueStaysText() throws Exception {
        // The customer numbers carried over from the old software are all digits. Reading one back
        // as an integer would renumber the customer, and drop a leading zero while doing it.
        Map<String, Object> after = firstRow(reread(withRow("customer",
                new String[]{"customer_number", "postcode"},
                row("customer_number", "10042", "postcode", "07749"))), "customer");

        assertEquals("10042", after.get("customer_number"));
        assertEquals("07749", after.get("postcode"));
    }

    @Test
    public void aStoredNullAndAnAbsentColumnAreDifferentThings() throws Exception {
        // The distinction the restore depends on: null is a value to write, absent means this
        // backup predates the column and its default has to decide. Writing null for an absent
        // column would fail every NOT NULL column added since.
        BackupFormat.Document doc = withRow("service",
                new String[]{"name", "note", "sync_calendar"},
                row("name", "Workshop", "note", null));

        Map<String, Object> after = firstRow(reread(doc), "service");

        assertTrue("null was stored", after.containsKey("note"));
        assertNull(after.get("note"));
        assertFalse("the column this backup never had", after.containsKey("sync_calendar"));
    }

    @Test
    public void anEmptyTableComesBackAsATableWithNoRows() throws Exception {
        BackupFormat.Document doc = new BackupFormat.Document();
        BackupFormat.Table t = new BackupFormat.Table("gig");
        t.columns.add("_id");
        doc.tables.add(t);

        BackupFormat.Document back = reread(doc);

        assertEquals(1, back.tables.size());
        assertEquals(0, back.rowCount("gig"));
    }

    @Test
    public void aFractionalNumberIsNotRoundedToALong() throws Exception {
        // No column in this schema stores one, which is exactly why it is worth pinning: a future
        // REAL column must not be quietly turned into an integer by the backup.
        Map<String, Object> after = firstRow(reread(withRow("t", new String[]{"rate"},
                row("rate", Double.valueOf(1.5)))), "t");

        assertEquals(Double.valueOf(1.5), after.get("rate"));
    }

    @Test
    public void aHandEditedBooleanBecomesTheOneOrZeroSqliteWouldHold() throws Exception {
        String json = "{\"tables\":{\"service\":{\"columns\":[\"multi_day\"],"
                + "\"rows\":[{\"multi_day\":true},{\"multi_day\":false}]}}}";
        BackupFormat.Document doc = new BackupFormat.Document();

        BackupFormat.readDatabase(json, doc);

        assertEquals(Long.valueOf(1L), doc.table("service").rows.get(0).get("multi_day"));
        assertEquals(Long.valueOf(0L), doc.table("service").rows.get(1).get("multi_day"));
    }

    @Test
    public void anUnreadableRowIsReportedRatherThanFailingTheWholeRestore() throws Exception {
        String json = "{\"tables\":{\"gig\":{\"columns\":[\"_id\"],"
                + "\"rows\":[{\"_id\":1},7]}}}";
        BackupFormat.Document doc = new BackupFormat.Document();

        BackupFormat.readDatabase(json, doc);

        assertEquals("the good row is kept", 1, doc.rowCount("gig"));
        assertEquals(1, doc.warnings.size());
        assertTrue(doc.warnings.get(0).contains("gig"));
    }

    @Test
    public void aFileWithNoTablesIsRefused() {
        try {
            BackupFormat.readDatabase("{\"formatVersion\":1}", new BackupFormat.Document());
            org.junit.Assert.fail("expected a malformed-json failure");
        } catch (Json.MalformedJsonException expected) {
            assertTrue(expected.getMessage().contains(BackupFormat.DATABASE_ENTRY));
        }
    }

    // ------------------------------------------------------------------ settings

    private static BackupFormat.Document rereadSettings(BackupFormat.Document doc)
            throws Exception {
        BackupFormat.Document back = new BackupFormat.Document();
        BackupFormat.readSettings(BackupFormat.writeSettings(doc), back);
        return back;
    }

    @Test
    public void aPreferenceKeepsTheTypeSharedPreferencesStoredItAs() throws Exception {
        // getLong on a value written with putInt throws, so "3" is not enough to restore with.
        BackupFormat.Document doc = new BackupFormat.Document();
        doc.settings.add(new BackupFormat.Setting("calendar_id", Long.valueOf(3L)));
        doc.settings.add(new BackupFormat.Setting("due_days", Integer.valueOf(60)));
        doc.settings.add(new BackupFormat.Setting("strict", Boolean.TRUE));
        doc.settings.add(new BackupFormat.Setting("scale", Float.valueOf(1.25f)));
        doc.settings.add(new BackupFormat.Setting("mail_body", "Dear Sir or Madam,\n\nregards"));
        doc.settings.add(new BackupFormat.Setting("mail_subject", ""));

        BackupFormat.Document back = rereadSettings(doc);

        Map<String, Object> byKey = new LinkedHashMap<String, Object>();
        for (BackupFormat.Setting s : back.settings) byKey.put(s.key, s.value);
        assertEquals(Long.valueOf(3L), byKey.get("calendar_id"));
        assertEquals(Integer.valueOf(60), byKey.get("due_days"));
        assertEquals(Boolean.TRUE, byKey.get("strict"));
        assertEquals(Float.valueOf(1.25f), byKey.get("scale"));
        assertEquals("Dear Sir or Madam,\n\nregards", byKey.get("mail_body"));
        assertEquals("a blank setting is still a setting", "", byKey.get("mail_subject"));
    }

    @Test
    public void aPreferenceTypeThatCannotBeWrittenIsNamedInTheWarnings() {
        BackupFormat.Document doc = new BackupFormat.Document();
        doc.settings.add(new BackupFormat.Setting("filters", new java.util.HashSet<String>()));

        String json = BackupFormat.writeSettings(doc);

        assertFalse(json.contains("filters"));
        assertEquals(1, doc.warnings.size());
        assertTrue(doc.warnings.get(0).contains("filters"));
    }

    // ------------------------------------------------------------------ manifest

    @Test
    public void theManifestSaysWhatIsInTheBackup() throws Exception {
        BackupFormat.Document doc = withRow("customer", new String[]{"_id"},
                row("_id", Long.valueOf(1L)));
        doc.createdAt = "2026-09-04T10:00:00+02:00";
        doc.appVersion = "1.0";
        doc.files = 4;
        doc.schemaVersion = 1;
        doc.settings.add(new BackupFormat.Setting("k", "v"));

        String json = BackupFormat.writeManifest(doc);
        Object node = Json.parse(json);

        assertTrue(BackupFormat.isFullBackup(json));
        assertEquals("2026-09-04T10:00:00+02:00", Json.string(node, "createdAt"));
        assertEquals(1L, Json.number(node, -1L, "schemaVersion"));
        assertEquals(4L, Json.number(node, -1L, "files"));
        assertEquals(1L, Json.number(node, -1L, "settings"));
        assertEquals(1L, Json.number(node, -1L, "rows", "customer"));

        BackupFormat.Document back = new BackupFormat.Document();
        BackupFormat.readManifest(json, back);
        assertEquals("1.0", back.appVersion);
        assertEquals(1, back.schemaVersion);
    }

    @Test
    public void aContactsArchiveIsNotMistakenForABackup() {
        // Both are zips with a manifest.json, and restoring a contacts archive would empty
        // everything the app holds rather than importing anybody.
        assertFalse(BackupFormat.isFullBackup(
                "{\"app\":\"Rechnungsplaner\",\"schemaVersion\":1,\"contacts\":2}"));
        assertFalse(BackupFormat.isFullBackup("not json at all"));
    }

    @Test
    public void aBackupThatHadToSkipSomethingSaysSoInTheFile() {
        BackupFormat.Document doc = new BackupFormat.Document();
        doc.warnings.add("customer.photo: binary, not backed up");

        assertTrue(BackupFormat.writeManifest(doc).contains("binary, not backed up"));
    }
}
