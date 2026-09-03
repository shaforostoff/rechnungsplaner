package com.shaforostoff.rechnungsplaner.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The app's SQLite schema. Framework {@link SQLiteOpenHelper} rather than Room: the data set is a
 * few hundred rows, the queries are simple, and this keeps the app dependency-free like its
 * siblings.
 *
 * <p>Money is stored as integer cents and rates as permille, so nothing in the money path can pick
 * up a floating-point error on the way to disk. Dates are {@code yyyy-MM-dd} text, which sorts and
 * range-filters correctly in SQLite without any parsing.
 */
public class Db extends SQLiteOpenHelper {

    private static final String NAME = "rechnungsplaner.db";
    private static final int VERSION = 6;

    public static final String T_ISSUER = "issuer";
    public static final String T_CUSTOMER = "customer";
    public static final String T_SERVICE = "service";
    public static final String T_GIG = "gig";
    public static final String T_INVOICE = "invoice";
    public static final String T_INVOICE_LINE = "invoice_line";
    public static final String T_INVOICE_FILE = "invoice_file";
    public static final String T_COUNTER = "counter";

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_ISSUER + " ("
                + "_id INTEGER PRIMARY KEY,"
                + "name TEXT, street TEXT, postcode TEXT, city TEXT,"
                + "country_code TEXT NOT NULL DEFAULT 'DE',"
                + "contact_name TEXT, phone TEXT, email TEXT,"
                + "vat_id TEXT, tax_number TEXT,"
                + "iban TEXT, bic TEXT, account_holder TEXT,"
                + "default_tax_mode TEXT NOT NULL DEFAULT 'KLEINUNTERNEHMER',"
                + "exemption_text TEXT,"
                + "default_due_days INTEGER NOT NULL DEFAULT 60,"
                + "payment_terms_text TEXT,"
                + "default_invoice_language TEXT NOT NULL DEFAULT 'de')");

        db.execSQL("CREATE TABLE " + T_CUSTOMER + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "official_name TEXT, place_name TEXT,"
                + "street TEXT, postcode TEXT, city TEXT,"
                + "country_code TEXT NOT NULL DEFAULT 'DE',"
                + "email TEXT, contact_name TEXT, phone TEXT,"
                + "vat_id TEXT, buyer_reference TEXT, customer_number TEXT,"
                + "default_fee_cents INTEGER NOT NULL DEFAULT 0,"
                + "default_tax_mode TEXT, invoice_language TEXT,"
                + "share_subject TEXT, share_message TEXT,"
                + "note TEXT, lexoffice_id TEXT,"
                + "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_customer_city ON " + T_CUSTOMER + "(city)");

        db.execSQL("CREATE TABLE " + T_SERVICE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "multi_day INTEGER NOT NULL DEFAULT 0,"
                + "sync_calendar INTEGER NOT NULL DEFAULT 1,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "archived INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_GIG + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "start_millis INTEGER NOT NULL DEFAULT 0,"
                + "end_millis INTEGER NOT NULL DEFAULT 0,"
                + "customer_id INTEGER NOT NULL DEFAULT -1,"
                + "place_name TEXT, city TEXT,"
                + "fee_cents INTEGER NOT NULL DEFAULT 0,"
                + "travel_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_mode TEXT, title TEXT, notes TEXT,"
                + "status TEXT NOT NULL DEFAULT 'PLANNED',"
                + "service_id INTEGER NOT NULL DEFAULT -1,"
                + "end_date TEXT,"
                + "invoice_id INTEGER NOT NULL DEFAULT -1,"
                + "calendar_id INTEGER NOT NULL DEFAULT -1,"
                + "calendar_event_id INTEGER NOT NULL DEFAULT -1,"
                + "sync_uuid TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX idx_gig_date ON " + T_GIG + "(date)");
        db.execSQL("CREATE INDEX idx_gig_customer ON " + T_GIG + "(customer_id)");
        db.execSQL("CREATE INDEX idx_gig_invoice ON " + T_GIG + "(invoice_id)");
        db.execSQL("CREATE UNIQUE INDEX idx_gig_uuid ON " + T_GIG + "(sync_uuid)");

        db.execSQL("CREATE TABLE " + T_INVOICE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "number TEXT NOT NULL UNIQUE,"
                + "issue_date TEXT NOT NULL, due_date TEXT,"
                + "customer_id INTEGER NOT NULL DEFAULT -1,"
                + "currency TEXT NOT NULL DEFAULT 'EUR',"
                + "tax_mode TEXT, rate_permille INTEGER NOT NULL DEFAULT 0,"
                + "exemption_text TEXT, exemption_code TEXT,"
                + "buyer_reference TEXT,"
                + "language TEXT NOT NULL DEFAULT 'de',"
                + "delivery_date TEXT, period_start TEXT, period_end TEXT,"
                + "note TEXT, payment_terms TEXT,"
                + "paid_year INTEGER NOT NULL DEFAULT 0,"
                + "line_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_basis_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "grand_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "prepaid_cents INTEGER NOT NULL DEFAULT 0,"
                + "due_payable_cents INTEGER NOT NULL DEFAULT 0,"
                + "issuer_snapshot TEXT, customer_snapshot TEXT,"
                + "replaces_id INTEGER NOT NULL DEFAULT -1,"
                + "replaces_number TEXT, replaces_date TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_invoice_customer ON " + T_INVOICE + "(customer_id)");
        db.execSQL("CREATE INDEX idx_invoice_date ON " + T_INVOICE + "(issue_date)");

        db.execSQL("CREATE TABLE " + T_INVOICE_LINE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "invoice_id INTEGER NOT NULL REFERENCES " + T_INVOICE + "(_id) ON DELETE CASCADE,"
                + "gig_id INTEGER NOT NULL DEFAULT -1,"
                + "line_no INTEGER NOT NULL DEFAULT 1,"
                + "name TEXT, description TEXT,"
                + "quantity_milli INTEGER NOT NULL DEFAULT 1000,"
                + "unit_code TEXT NOT NULL DEFAULT 'C62',"
                + "unit_price_cents INTEGER NOT NULL DEFAULT 0,"
                + "net_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_category TEXT NOT NULL DEFAULT 'S',"
                + "rate_permille INTEGER NOT NULL DEFAULT 0,"
                + "period_start TEXT, period_end TEXT)");
        db.execSQL("CREATE INDEX idx_line_invoice ON " + T_INVOICE_LINE + "(invoice_id)");

        db.execSQL("CREATE TABLE " + T_INVOICE_FILE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "invoice_id INTEGER NOT NULL REFERENCES " + T_INVOICE + "(_id) ON DELETE CASCADE,"
                + "format TEXT, file_name TEXT, rel_path TEXT,"
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_file_invoice ON " + T_INVOICE_FILE + "(invoice_id)");

        // Number sequences: one row per invoice year, plus one for customer numbers. Each is
        // bumped inside the same transaction that inserts the row it numbers, so two quick taps
        // cannot mint the same number twice.
        db.execSQL("CREATE TABLE " + T_COUNTER + " ("
                + "key TEXT PRIMARY KEY, value INTEGER NOT NULL DEFAULT 0)");
    }

    /**
     * Additive steps, each guarded by the version it arrived in.
     *
     * <p>This used to drop every table and rebuild, on the grounds that nothing was installed
     * anywhere. That stopped being true: there is a phone with an invoice series carried over
     * from other software, customers numbered to match books that predate this app, and gigs
     * linked back to their invoices by hand. Dropping the tables to add a column would be the
     * app destroying the one thing it exists to keep -- and section 147 AO requires an issued
     * invoice to survive ten years, not until the next schema change.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Per-customer share wording, overriding the global setting. Null means inherit, so
            // an existing customer needs no backfill.
            addColumn(db, T_CUSTOMER, "share_subject", "TEXT");
            addColumn(db, T_CUSTOMER, "share_message", "TEXT");
        }
        if (oldVersion < 4) {
            // The year the money arrived, when that is not the year the invoice belongs to.
            // Zero means derive it, so every existing invoice already has the right answer.
            addColumn(db, T_INVOICE, "paid_year", "INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            // The app used to know one kind of work. Everything already recorded was that one, so
            // the migration names it and points every job at it rather than leaving them with no
            // service at all -- the name is what ends up on an invoice line, and losing it would
            // quietly change what past invoices are rebuilt as. It can be renamed afterwards.
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SERVICE + " ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "sort_order INTEGER NOT NULL DEFAULT 0,"
                    + "archived INTEGER NOT NULL DEFAULT 0)");
            addColumn(db, T_GIG, "service_id", "INTEGER NOT NULL DEFAULT -1");
            // Only into an empty table: this step can be reached twice if the version travels,
            // and a second 'DJ-Set' button would be the migration's own doing.
            db.execSQL("INSERT INTO " + T_SERVICE + " (name, sort_order)"
                    + " SELECT 'DJ-Set', 1 WHERE NOT EXISTS (SELECT 1 FROM " + T_SERVICE + ")");
            db.execSQL("UPDATE " + T_GIG + " SET service_id ="
                    + " (SELECT MIN(_id) FROM " + T_SERVICE + ") WHERE service_id <= 0");
        }
        if (oldVersion < 6) {
            // Work that runs over days rather than hours, and whether it belongs in the calendar
            // at all. Both default to what the app did before: single-day, mirrored.
            addColumn(db, T_SERVICE, "multi_day", "INTEGER NOT NULL DEFAULT 0");
            addColumn(db, T_SERVICE, "sync_calendar", "INTEGER NOT NULL DEFAULT 1");
            addColumn(db, T_GIG, "end_date", "TEXT");
        }
    }

    /**
     * Nothing, deliberately.
     *
     * <p>Moving between branches walks the version backwards, which SQLiteOpenHelper treats as
     * fatal unless told otherwise. An older build reads its columns by name and is unbothered by
     * ones it has never heard of, so leaving them in place costs nothing and keeps the data --
     * where the rebuild this used to do would have thrown it away to reach an older shape.
     */
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /**
     * Adds a column unless it is already there.
     *
     * <p>Idempotent because the version can travel: a downgrade leaves the column in place, and
     * the upgrade back would otherwise re-run the step and fail on the duplicate.
     */
    private static void addColumn(SQLiteDatabase db, String table, String column, String type) {
        if (hasColumn(db, table, column)) return;
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameAt = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameAt))) return true;
            }
        } finally {
            c.close();
        }
        return false;
    }
}
