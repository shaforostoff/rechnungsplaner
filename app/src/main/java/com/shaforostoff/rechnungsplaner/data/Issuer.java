package com.shaforostoff.rechnungsplaner.data;

/** The user's own details. Exactly one row exists. */
public class Issuer {

    public String name = "";
    public String street = "";
    public String postcode = "";
    public String city = "";
    public String countryCode = "DE";

    public String contactName = "";
    public String phone = "";
    public String email = "";

    /** USt-IdNr, BT-31. */
    public String vatId = "";
    /** Steuernummer, BT-32. */
    public String taxNumber = "";

    public String iban = "";
    public String bic = "";
    public String accountHolder = "";

    public TaxMode defaultTaxMode = TaxMode.KLEINUNTERNEHMER;
    /** Overrides the statutory wording from {@link TaxMode} when the user prefers their own. */
    public String exemptionText = "";
    public int defaultDueDays = 60;
    public String paymentTermsText = "";
    /** Invoice-document language used when a customer has no preference of their own. */
    public String defaultInvoiceLanguage = "de";

    public boolean isEmpty() {
        return name == null || name.trim().isEmpty();
    }
}
