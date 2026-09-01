package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * Writes an {@link EnInvoice} as OASIS UBL 2.1 {@code Invoice}.
 *
 * <p>This is the preferred bare-XML output: all three target systems read it, and sevDesk's own
 * XRechnung export is UBL, which makes it that importer's best-trodden path.
 *
 * <p>UBL validates against a sequence, so element order below is not cosmetic — moving an element
 * breaks schema validation even though the content is unchanged.
 */
public final class UblWriter {

    private static final String NS_INV = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String NS_CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private final Profile profile;

    public UblWriter(Profile profile) {
        this.profile = profile;
    }

    public String write(EnInvoice inv) {
        XmlBuilder x = new XmlBuilder();
        x.start("ubl:Invoice")
                .attr("xmlns:ubl", NS_INV)
                .attr("xmlns:cac", NS_CAC)
                .attr("xmlns:cbc", NS_CBC);

        x.leaf("cbc:CustomizationID", profile.customizationId);
        x.leaf("cbc:ProfileID", profile.profileId);
        x.leaf("cbc:ID", inv.number);
        x.leaf("cbc:IssueDate", inv.issueDate);
        x.leaf("cbc:DueDate", inv.dueDate);
        x.leaf("cbc:InvoiceTypeCode", inv.typeCode);
        x.leaf("cbc:Note", inv.note);
        x.leaf("cbc:DocumentCurrencyCode", inv.currency);
        x.leaf("cbc:BuyerReference", inv.buyerReference);

        if (Str.notEmpty(inv.periodStart) || Str.notEmpty(inv.periodEnd)) {
            x.start("cac:InvoicePeriod");
            x.leaf("cbc:StartDate", inv.periodStart);
            x.leaf("cbc:EndDate", inv.periodEnd);
            x.end();
        }

        x.start("cac:AccountingSupplierParty");
        party(x, inv.seller, true);
        x.end();

        x.start("cac:AccountingCustomerParty");
        party(x, inv.buyer, false);
        x.end();

        if (Str.notEmpty(inv.deliveryDate)) {
            x.start("cac:Delivery");
            x.leaf("cbc:ActualDeliveryDate", inv.deliveryDate);
            x.end();
        }

        if (Str.notEmpty(inv.paymentMeansCode)) {
            x.start("cac:PaymentMeans");
            x.leaf("cbc:PaymentMeansCode", inv.paymentMeansCode);
            x.leaf("cbc:PaymentID", inv.remittanceInformation);
            if (Str.notEmpty(inv.iban)) {
                x.start("cac:PayeeFinancialAccount");
                x.leaf("cbc:ID", inv.iban);
                x.leaf("cbc:Name", inv.accountName);
                if (Str.notEmpty(inv.bic)) {
                    x.start("cac:FinancialInstitutionBranch");
                    x.leaf("cbc:ID", inv.bic);
                    x.end();
                }
                x.end();
            }
            x.end();
        }

        if (Str.notEmpty(inv.paymentTerms)) {
            x.start("cac:PaymentTerms");
            x.leaf("cbc:Note", inv.paymentTerms);
            x.end();
        }

        x.start("cac:TaxTotal");
        x.leaf("cbc:TaxAmount", Money.amount(inv.taxTotalCents), "currencyID", inv.currency);
        for (TaxBreakdown g : inv.taxBreakdowns) {
            x.start("cac:TaxSubtotal");
            x.leaf("cbc:TaxableAmount", Money.amount(g.basisCents), "currencyID", inv.currency);
            x.leaf("cbc:TaxAmount", Money.amount(g.taxAmountCents), "currencyID", inv.currency);
            x.start("cac:TaxCategory");
            x.leaf("cbc:ID", g.category.code);
            x.leaf("cbc:Percent", Money.percent(g.ratePermille));
            x.leaf("cbc:TaxExemptionReasonCode", g.exemptionReasonCode);
            x.leaf("cbc:TaxExemptionReason", g.exemptionReason);
            x.start("cac:TaxScheme").leaf("cbc:ID", "VAT").end();
            x.end();
            x.end();
        }
        x.end();

        x.start("cac:LegalMonetaryTotal");
        x.leaf("cbc:LineExtensionAmount", Money.amount(inv.lineTotalCents), "currencyID", inv.currency);
        x.leaf("cbc:TaxExclusiveAmount", Money.amount(inv.taxBasisCents), "currencyID", inv.currency);
        x.leaf("cbc:TaxInclusiveAmount", Money.amount(inv.grandTotalCents), "currencyID", inv.currency);
        if (inv.prepaidCents != 0) {
            x.leaf("cbc:PrepaidAmount", Money.amount(inv.prepaidCents), "currencyID", inv.currency);
        }
        x.leaf("cbc:PayableAmount", Money.amount(inv.duePayableCents), "currencyID", inv.currency);
        x.end();

        for (EnLine line : inv.lines) {
            x.start("cac:InvoiceLine");
            x.leaf("cbc:ID", line.id);
            x.leaf("cbc:InvoicedQuantity", Money.quantity(line.quantityMilli),
                    "unitCode", line.unitCode);
            x.leaf("cbc:LineExtensionAmount", Money.amount(line.netCents),
                    "currencyID", inv.currency);
            if (Str.notEmpty(line.periodStart) || Str.notEmpty(line.periodEnd)) {
                x.start("cac:InvoicePeriod");
                x.leaf("cbc:StartDate", line.periodStart);
                x.leaf("cbc:EndDate", line.periodEnd);
                x.end();
            }
            x.start("cac:Item");
            x.leaf("cbc:Description", line.description);
            x.leaf("cbc:Name", line.name);
            x.start("cac:ClassifiedTaxCategory");
            x.leaf("cbc:ID", line.taxCategory.code);
            x.leaf("cbc:Percent", Money.percent(line.ratePermille));
            x.start("cac:TaxScheme").leaf("cbc:ID", "VAT").end();
            x.end();
            x.end();
            x.start("cac:Price");
            x.leaf("cbc:PriceAmount", Money.amount(line.unitPriceCents), "currencyID", inv.currency);
            x.end();
            x.end();
        }

        x.end();
        return x.toXml();
    }

    private static void party(XmlBuilder x, EnParty p, boolean seller) {
        x.start("cac:Party");
        if (Str.notEmpty(p.electronicAddress)) {
            x.leaf("cbc:EndpointID", p.electronicAddress, "schemeID", p.electronicAddressScheme);
        }
        if (Str.notEmpty(p.identifier)) {
            x.start("cac:PartyIdentification").leaf("cbc:ID", p.identifier).end();
        }
        if (Str.notEmpty(p.tradingName)) {
            x.start("cac:PartyName").leaf("cbc:Name", p.tradingName).end();
        }

        x.start("cac:PostalAddress");
        x.leaf("cbc:StreetName", p.line1);
        x.leaf("cbc:AdditionalStreetName", p.line2);
        x.leaf("cbc:CityName", p.city);
        x.leaf("cbc:PostalZone", p.postcode);
        x.leaf("cbc:CountrySubentity", p.countrySubdivision);
        x.start("cac:Country").leaf("cbc:IdentificationCode", p.countryCode).end();
        x.end();

        if (Str.notEmpty(p.vatId)) {
            x.start("cac:PartyTaxScheme");
            x.leaf("cbc:CompanyID", p.vatId);
            x.start("cac:TaxScheme").leaf("cbc:ID", "VAT").end();
            x.end();
        }
        // BT-32 rides in a second PartyTaxScheme with scheme FC, and only the seller has one.
        if (seller && Str.notEmpty(p.taxNumber)) {
            x.start("cac:PartyTaxScheme");
            x.leaf("cbc:CompanyID", p.taxNumber);
            x.start("cac:TaxScheme").leaf("cbc:ID", "FC").end();
            x.end();
        }

        x.start("cac:PartyLegalEntity");
        x.leaf("cbc:RegistrationName", p.name);
        if (Str.notEmpty(p.legalId)) {
            x.leaf("cbc:CompanyID", p.legalId, "schemeID", p.legalIdScheme);
        }
        x.end();

        if (p.hasContact()) {
            x.start("cac:Contact");
            x.leaf("cbc:Name", p.contactName);
            x.leaf("cbc:Telephone", p.contactPhone);
            x.leaf("cbc:ElectronicMail", p.contactEmail);
            x.end();
        }
        x.end();
    }
}
