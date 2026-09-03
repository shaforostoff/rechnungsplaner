package com.shaforostoff.rechnungsplaner.data;

/**
 * A booker. Deliberately tolerant of missing data: before the first gig the user often knows only
 * a venue name, and the official billing details arrive afterwards. Everything except
 * {@link #placeName} may therefore be blank, and the invoice screen reports the shortfall rather
 * than the editor refusing to save.
 */
public class Customer {

    public long id = -1L;

    /** BT-44, the registered name. Null until the club sends it. */
    public String officialName;
    /** The venue. Usually the only thing known at first, and what the user recognises it by. */
    public String placeName;

    public String street;
    public String postcode;
    public String city;
    public String countryCode = "DE";

    public String email;
    public String contactName;
    public String phone;

    /** BT-48. */
    public String vatId;
    /** BT-10. Clubs have no Leitweg-ID, so this is usually a purchase-order reference or blank. */
    public String buyerReference;
    /** BT-46. */
    public String customerNumber;

    /** The usual fee, pre-filled when a gig is booked here. */
    public long defaultFeeCents;
    /** Null means fall back to the issuer's default. */
    public TaxMode defaultTaxMode;
    /** Null means fall back to the issuer's default. */
    public String invoiceLanguage;
    /**
     * Wording the invoice is shared with, overriding the global setting. Null means inherit.
     *
     * <p>Separate from {@link #invoiceLanguage} rather than derived from it: the language decides
     * what the document says, and these decide what is written to this particular booker, which
     * is a matter of how well you know them as much as which language they read.
     */
    public String shareSubject;
    public String shareMessage;

    public String note;
    /** Preserved across a lexoffice export and re-import so records match up again. */
    public String lexofficeId;
    public boolean archived;

    /** The best name available, for lists and pickers. */
    public String displayName() {
        if (notEmpty(officialName)) return officialName.trim();
        if (notEmpty(placeName)) return placeName.trim();
        if (notEmpty(city)) return city.trim();
        return "?";
    }

    /** The name to bill: falls back to the venue when the legal name is still unknown. */
    public String billingName() {
        if (notEmpty(officialName)) return officialName.trim();
        return notEmpty(placeName) ? placeName.trim() : "";
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
