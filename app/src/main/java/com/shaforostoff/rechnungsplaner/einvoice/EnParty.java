package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * BG-4 Seller or BG-7 Buyer, plus their address (BG-5/BG-8) and contact (BG-6/BG-9).
 *
 * <p>The two syntaxes disagree about which name goes where, so both are kept apart:
 * {@link #name} is the registered name (BT-27/BT-44) and {@link #tradingName} the trading name
 * (BT-28/BT-45). UBL maps the former to {@code PartyLegalEntity/RegistrationName} and the latter to
 * {@code PartyName/Name}; CII maps the former to {@code ram:Name} and the latter to
 * {@code SpecifiedLegalOrganization/TradingBusinessName}.
 */
public class EnParty {

    /** BT-27 / BT-44, the registered name. Mandatory. */
    public String name;
    /** BT-28 / BT-45, the trading name, when it differs from the registered one. */
    public String tradingName;
    /** BT-29 / BT-46, an identifier the parties use for each other (e.g. a customer number). */
    public String identifier;
    /** BT-30 / BT-47, the legal registration identifier, e.g. an HRB number. */
    public String legalId;
    /** Scheme of {@link #legalId}, ISO 6523 (e.g. {@code 0002}). */
    public String legalIdScheme;
    /** BT-31 / BT-48, the VAT identifier (USt-IdNr). */
    public String vatId;
    /** BT-32, the seller's tax registration (Steuernummer). Seller only. */
    public String taxNumber;
    /** BT-34 / BT-49, the electronic address. Mandatory for the seller under XRechnung. */
    public String electronicAddress;
    /** Scheme of {@link #electronicAddress}; {@code EM} for an email address. */
    public String electronicAddressScheme = "EM";

    /** BT-35 / BT-50. */
    public String line1;
    /** BT-36 / BT-51. */
    public String line2;
    /** BT-37 / BT-52. Mandatory. */
    public String city;
    /** BT-38 / BT-53. */
    public String postcode;
    /** BT-39 / BT-54. */
    public String countrySubdivision;
    /** BT-40 / BT-55, ISO 3166-1 alpha-2. Mandatory. */
    public String countryCode = "DE";

    /** BT-41 / BT-56. */
    public String contactName;
    /** BT-42 / BT-57. */
    public String contactPhone;
    /** BT-43 / BT-58. */
    public String contactEmail;

    /** True when any of the BG-6/BG-9 contact fields is set, so the writers can skip the group. */
    public boolean hasContact() {
        return Str.notEmpty(contactName) || Str.notEmpty(contactPhone) || Str.notEmpty(contactEmail);
    }
}
