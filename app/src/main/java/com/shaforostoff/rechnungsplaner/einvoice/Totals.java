package com.shaforostoff.rechnungsplaner.einvoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds BG-23 and the BG-22 document totals from the lines.
 *
 * <p>The grouping order matters more than it looks. BR-CO-17 requires the VAT of a breakdown to be
 * its <em>summed</em> basis multiplied by the rate, rounded once. Rounding per line and re-summing
 * drifts by a cent on a surprising number of real invoices, and all three target systems recompute
 * the totals on import and reject the file when they disagree.
 */
public final class Totals {

    private Totals() {
    }

    /** Recomputes every line net, then BG-23, then the document totals, in place. */
    public static void compute(EnInvoice inv) {
        Map<String, TaxBreakdown> groups = new LinkedHashMap<String, TaxBreakdown>();
        long lineTotal = 0L;

        for (EnLine line : inv.lines) {
            line.recomputeNet();
            lineTotal += line.netCents;

            String key = line.taxCategory.code + ":" + line.ratePermille;
            TaxBreakdown g = groups.get(key);
            if (g == null) {
                g = new TaxBreakdown();
                g.category = line.taxCategory;
                g.ratePermille = line.ratePermille;
                groups.put(key, g);
            }
            g.basisCents += line.netCents;
        }

        // Carry over any exemption reasons already set, so callers can fill them before or after.
        List<TaxBreakdown> previous = inv.taxBreakdowns;
        List<TaxBreakdown> result = new ArrayList<TaxBreakdown>(groups.size());
        long taxTotal = 0L;
        for (TaxBreakdown g : groups.values()) {
            g.taxAmountCents = Money.taxOf(g.basisCents, g.ratePermille);
            taxTotal += g.taxAmountCents;
            if (previous != null) {
                for (TaxBreakdown old : previous) {
                    if (old.category == g.category && old.ratePermille == g.ratePermille) {
                        if (Str.isEmpty(g.exemptionReason)) g.exemptionReason = old.exemptionReason;
                        if (Str.isEmpty(g.exemptionReasonCode)) {
                            g.exemptionReasonCode = old.exemptionReasonCode;
                        }
                        break;
                    }
                }
            }
            result.add(g);
        }

        inv.taxBreakdowns = result;
        inv.lineTotalCents = lineTotal;
        // No document-level allowances or charges, so BT-109 equals BT-106.
        inv.taxBasisCents = lineTotal;
        inv.taxTotalCents = taxTotal;
        inv.grandTotalCents = inv.taxBasisCents + taxTotal;
        inv.duePayableCents = inv.grandTotalCents - inv.prepaidCents;
    }

    /** Applies one exemption reason to every zero-rated breakdown that has none yet. */
    public static void setExemptionReason(EnInvoice inv, String reason, String code) {
        for (TaxBreakdown g : inv.taxBreakdowns) {
            if (g.category.needsExemptionReason) {
                if (Str.isEmpty(g.exemptionReason)) g.exemptionReason = reason;
                if (Str.isEmpty(g.exemptionReasonCode)) g.exemptionReasonCode = code;
            }
        }
    }
}
