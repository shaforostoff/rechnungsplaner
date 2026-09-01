package com.shaforostoff.rechnungsplaner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

/** The single-row {@link Issuer} table. */
public class IssuerDao {

    private static final long ROW_ID = 1L;

    private final Db db;

    public IssuerDao(Context ctx) {
        this.db = Db.get(ctx);
    }

    /** Never null; an unconfigured app returns an empty issuer so the editor has something to fill. */
    public Issuer load() {
        Cursor c = db.getReadableDatabase().query(Db.T_ISSUER, null, "_id = ?",
                new String[]{Long.toString(ROW_ID)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : new Issuer();
        } finally {
            c.close();
        }
    }

    public void save(Issuer i) {
        ContentValues v = new ContentValues();
        v.put("_id", ROW_ID);
        v.put("name", i.name);
        v.put("street", i.street);
        v.put("postcode", i.postcode);
        v.put("city", i.city);
        v.put("country_code", i.countryCode == null ? "DE" : i.countryCode);
        v.put("contact_name", i.contactName);
        v.put("phone", i.phone);
        v.put("email", i.email);
        v.put("vat_id", i.vatId);
        v.put("tax_number", i.taxNumber);
        v.put("iban", i.iban);
        v.put("bic", i.bic);
        v.put("account_holder", i.accountHolder);
        v.put("default_tax_mode",
                (i.defaultTaxMode == null ? TaxMode.KLEINUNTERNEHMER : i.defaultTaxMode).name());
        v.put("exemption_text", i.exemptionText);
        v.put("default_due_days", i.defaultDueDays);
        v.put("payment_terms_text", i.paymentTermsText);
        v.put("default_invoice_language",
                i.defaultInvoiceLanguage == null ? "de" : i.defaultInvoiceLanguage);
        db.getWritableDatabase().replace(Db.T_ISSUER, null, v);
    }

    static Issuer read(Cursor c) {
        Issuer i = new Issuer();
        i.name = str(c, "name");
        i.street = str(c, "street");
        i.postcode = str(c, "postcode");
        i.city = str(c, "city");
        i.countryCode = str(c, "country_code");
        i.contactName = str(c, "contact_name");
        i.phone = str(c, "phone");
        i.email = str(c, "email");
        i.vatId = str(c, "vat_id");
        i.taxNumber = str(c, "tax_number");
        i.iban = str(c, "iban");
        i.bic = str(c, "bic");
        i.accountHolder = str(c, "account_holder");
        i.defaultTaxMode = TaxMode.fromName(str(c, "default_tax_mode"), TaxMode.KLEINUNTERNEHMER);
        i.exemptionText = str(c, "exemption_text");
        i.defaultDueDays = c.getInt(c.getColumnIndexOrThrow("default_due_days"));
        i.paymentTermsText = str(c, "payment_terms_text");
        i.defaultInvoiceLanguage = str(c, "default_invoice_language");
        if (i.countryCode.isEmpty()) i.countryCode = "DE";
        if (i.defaultInvoiceLanguage.isEmpty()) i.defaultInvoiceLanguage = "de";
        return i;
    }

    private static String str(Cursor c, String column) {
        String s = c.getString(c.getColumnIndexOrThrow(column));
        return s == null ? "" : s;
    }
}
