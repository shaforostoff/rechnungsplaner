package com.shaforostoff.rechnungsplaner.data;

import android.content.Context;
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
    private static final int VERSION = 1;

    public static final String T_ISSUER = "issuer";
    public static final String T_CUSTOMER = "customer";
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
                + "note TEXT, lexoffice_id TEXT,"
                + "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_customer_city ON " + T_CUSTOMER + "(city)");

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
                + "line_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_basis_cents INTEGER NOT NULL DEFAULT 0,"
                + "tax_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "grand_total_cents INTEGER NOT NULL DEFAULT 0,"
                + "prepaid_cents INTEGER NOT NULL DEFAULT 0,"
                + "due_payable_cents INTEGER NOT NULL DEFAULT 0,"
                + "issuer_snapshot TEXT, customer_snapshot TEXT,"
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

        // Invoice-number sequences, one row per year, bumped inside the same transaction that
        // inserts the invoice so two quick taps cannot mint the same number twice.
        db.execSQL("CREATE TABLE " + T_COUNTER + " ("
                + "key TEXT PRIMARY KEY, value INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first release; migrations get added here as ALTER TABLE steps rather
        // than a drop-and-recreate, because this database holds issued invoices that must be
        // retained for the statutory ten years.
    }
}
