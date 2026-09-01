package com.shaforostoff.rechnungsplaner.ui;

import com.shaforostoff.rechnungsplaner.data.Customer;

/**
 * How far a customer is from being invoiceable under XRechnung.
 *
 * <p>Deliberately the same set of fields BR-DE-9 to BR-DE-11 and BR-DE-15 enforce, so the count
 * shown in the list agrees with what {@code EnValidator} will report on the invoice screen. Anything
 * else would be worse than no count at all.
 */
public final class CustomerReadiness {

    private CustomerReadiness() {
    }

    public static int missingCount(Customer c) {
        int missing = 0;
        if (isEmpty(c.officialName)) missing++;
        if (isEmpty(c.street)) missing++;
        if (isEmpty(c.postcode)) missing++;
        if (isEmpty(c.city)) missing++;
        if (isEmpty(c.email)) missing++;
        if (isEmpty(c.buyerReference)) missing++;
        return missing;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
