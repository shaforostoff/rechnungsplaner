package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.einvoice.Problem;

/**
 * Turns a {@link Problem} into the sentence the user reads.
 *
 * <p>The mapping lives here rather than in the validator because that package has no access to
 * Android resources -- deliberately, so it can be unit-tested -- and because the same problem has
 * to be phrased in whichever of the three languages the app is running in.
 *
 * <p>The BT code is kept in the message on purpose. It looks technical, but it is the thing a
 * booker's accountant can search for, and the reference app this borrows from does the same.
 */
public final class ProblemLabels {

    private ProblemLabels() {
    }

    public static String describe(Context ctx, Problem problem) {
        String field = fieldLabel(ctx, problem.fieldKey);
        String code = problem.term != null ? problem.term : problem.rule;
        if (code == null) return field;
        // Warnings are not "missing" -- the file is accepted, some validators just object -- so
        // they get the neutral phrasing.
        return ctx.getString(problem.isError() ? R.string.problem_missing
                : R.string.problem_generic, field, code);
    }

    public static String fieldLabel(Context ctx, String fieldKey) {
        int res = resourceFor(fieldKey);
        return res == 0 ? fieldKey : ctx.getString(res);
    }

    private static int resourceFor(String key) {
        if (Problem.SELLER_NAME.equals(key)) return R.string.field_seller_name;
        if (Problem.SELLER_STREET.equals(key)) return R.string.field_seller_street;
        if (Problem.SELLER_CITY.equals(key)) return R.string.field_seller_city;
        if (Problem.SELLER_POSTCODE.equals(key)) return R.string.field_seller_postcode;
        if (Problem.SELLER_COUNTRY.equals(key)) return R.string.field_seller_country;
        if (Problem.SELLER_TAX_ID.equals(key)) return R.string.field_seller_tax_id;
        if (Problem.SELLER_CONTACT_NAME.equals(key)) return R.string.field_seller_contact_name;
        if (Problem.SELLER_CONTACT_PHONE.equals(key)) return R.string.field_seller_contact_phone;
        if (Problem.SELLER_CONTACT_EMAIL.equals(key)) return R.string.field_seller_contact_email;
        if (Problem.SELLER_ENDPOINT.equals(key)) return R.string.field_seller_endpoint;
        if (Problem.SELLER_IBAN.equals(key)) return R.string.field_seller_iban;
        if (Problem.BUYER_NAME.equals(key)) return R.string.field_buyer_name;
        if (Problem.BUYER_STREET.equals(key)) return R.string.field_buyer_street;
        if (Problem.BUYER_CITY.equals(key)) return R.string.field_buyer_city;
        if (Problem.BUYER_POSTCODE.equals(key)) return R.string.field_buyer_postcode;
        if (Problem.BUYER_COUNTRY.equals(key)) return R.string.field_buyer_country;
        if (Problem.BUYER_ENDPOINT.equals(key)) return R.string.field_buyer_endpoint;
        if (Problem.BUYER_VAT_ID.equals(key)) return R.string.field_buyer_vat_id;
        if (Problem.INVOICE_NUMBER.equals(key)) return R.string.field_invoice_number;
        if (Problem.INVOICE_ISSUE_DATE.equals(key)) return R.string.field_invoice_issue_date;
        if (Problem.INVOICE_CURRENCY.equals(key)) return R.string.field_invoice_currency;
        if (Problem.INVOICE_BUYER_REFERENCE.equals(key)) {
            return R.string.field_invoice_buyer_reference;
        }
        if (Problem.INVOICE_DELIVERY_DATE.equals(key)) return R.string.field_invoice_delivery_date;
        if (Problem.INVOICE_PAYMENT_DUE.equals(key)) return R.string.field_invoice_payment_due;
        if (Problem.INVOICE_LINES.equals(key)) return R.string.field_invoice_lines;
        if (Problem.INVOICE_TOTALS.equals(key)) return R.string.field_invoice_totals;
        if (Problem.TAX_EXEMPTION_REASON.equals(key)) return R.string.field_tax_exemption_reason;
        if (Problem.TAX_RATE.equals(key)) return R.string.field_tax_rate;
        return 0;
    }
}
