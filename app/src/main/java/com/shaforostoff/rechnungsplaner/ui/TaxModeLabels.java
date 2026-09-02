package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.InvoiceBuilder;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.TaxMode;

/**
 * Spinner labels for {@link TaxMode}, including the "inherit" entry.
 *
 * <p>Shared by the gig, customer and issuer editors, which all name the same five modes and had
 * grown three copies of the switch. Keeping it in one place also keeps the wording identical, which
 * matters here: these are tax categories, and two screens calling the same thing by two names would
 * invite picking the wrong one.
 *
 * <p>The inherit entry names the mode that inheriting would actually produce, resolved through the
 * same {@link InvoiceBuilder#resolveTaxMode} the invoice itself uses. Reusing that function rather
 * than restating the chain is the point: a label that explains what will happen is only useful if
 * it cannot drift from what does happen.
 */
public final class TaxModeLabels {

    private TaxModeLabels() {
    }

    /** The five modes in enum order, for a field that has to name one. */
    public static String[] modes(Context ctx) {
        TaxMode[] all = TaxMode.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = of(ctx, all[i]);
        return labels;
    }

    /**
     * The five modes with an inherit entry first, so index 0 means "not set here".
     *
     * @param customer the customer to inherit from, or null to inherit from the issuer alone --
     *                 which is what the customer editor itself needs
     */
    public static String[] withInherit(Context ctx, Issuer issuer, Customer customer) {
        String[] modes = modes(ctx);
        String[] labels = new String[modes.length + 1];
        // Passing no gigs asks the chain what it would resolve to *without* an override at this
        // level, which is precisely what inheriting means.
        labels[0] = ctx.getString(R.string.taxmode_inherit_value,
                of(ctx, InvoiceBuilder.resolveTaxMode(issuer, customer, null)));
        System.arraycopy(modes, 0, labels, 1, modes.length);
        return labels;
    }

    public static String of(Context ctx, TaxMode mode) {
        switch (mode) {
            case STANDARD_19: return ctx.getString(R.string.taxmode_standard_19);
            case REDUCED_7: return ctx.getString(R.string.taxmode_reduced_7);
            case REVERSE_CHARGE: return ctx.getString(R.string.taxmode_reverse_charge);
            case INTRA_EU: return ctx.getString(R.string.taxmode_intra_eu);
            case KLEINUNTERNEHMER:
            default: return ctx.getString(R.string.taxmode_kleinunternehmer);
        }
    }
}
