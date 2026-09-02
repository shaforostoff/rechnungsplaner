package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * Writes an {@link EnInvoice} as UN/CEFACT Cross Industry Invoice (D16B).
 *
 * <p>This is the syntax that goes inside a ZUGFeRD/Factur-X PDF as {@code factur-x.xml}, and it is
 * also valid as bare XRechnung XML.
 *
 * <p>Like UBL, CII validates against a sequence. Two orderings here are easy to get wrong and are
 * therefore worth stating: {@code ram:ApplicableTradeTax} runs CalculatedAmount, TypeCode,
 * ExemptionReason, BasisAmount, CategoryCode, ExemptionReasonCode, RateApplicablePercent; and
 * {@code ram:TradeParty} runs ID, Name, Description, SpecifiedLegalOrganization,
 * DefinedTradeContact, PostalTradeAddress, URIUniversalCommunication, SpecifiedTaxRegistration.
 */
public final class CiiWriter {

    private static final String NS_RSM =
            "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    private static final String NS_RAM =
            "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    private static final String NS_UDT =
            "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";

    private final Profile profile;

    public CiiWriter(Profile profile) {
        this.profile = profile;
    }

    public String write(EnInvoice inv) {
        XmlBuilder x = new XmlBuilder();
        x.start("rsm:CrossIndustryInvoice")
                .attr("xmlns:rsm", NS_RSM)
                .attr("xmlns:ram", NS_RAM)
                .attr("xmlns:udt", NS_UDT);

        x.start("rsm:ExchangedDocumentContext");
        if (Str.notEmpty(profile.profileId)) {
            x.start("ram:BusinessProcessSpecifiedDocumentContextParameter")
                    .leaf("ram:ID", profile.profileId).end();
        }
        x.start("ram:GuidelineSpecifiedDocumentContextParameter")
                .leaf("ram:ID", profile.customizationId).end();
        x.end();

        x.start("rsm:ExchangedDocument");
        x.leaf("ram:ID", inv.number);
        x.leaf("ram:TypeCode", inv.typeCode);
        dateTime(x, "ram:IssueDateTime", inv.issueDate);
        if (Str.notEmpty(inv.note)) {
            x.start("ram:IncludedNote").leaf("ram:Content", inv.note).end();
        }
        x.end();

        x.start("rsm:SupplyChainTradeTransaction");

        for (EnLine line : inv.lines) {
            x.start("ram:IncludedSupplyChainTradeLineItem");

            x.start("ram:AssociatedDocumentLineDocument").leaf("ram:LineID", line.id).end();

            x.start("ram:SpecifiedTradeProduct");
            x.leaf("ram:Name", line.name);
            x.leaf("ram:Description", line.description);
            x.end();

            x.start("ram:SpecifiedLineTradeAgreement");
            x.start("ram:NetPriceProductTradePrice")
                    .leaf("ram:ChargeAmount", Money.amount(line.unitPriceCents)).end();
            x.end();

            x.start("ram:SpecifiedLineTradeDelivery");
            x.leaf("ram:BilledQuantity", Money.quantity(line.quantityMilli),
                    "unitCode", line.unitCode);
            x.end();

            x.start("ram:SpecifiedLineTradeSettlement");
            x.start("ram:ApplicableTradeTax");
            x.leaf("ram:TypeCode", "VAT");
            x.leaf("ram:CategoryCode", line.taxCategory.code);
            x.leaf("ram:RateApplicablePercent", Money.percent(line.ratePermille));
            x.end();
            if (Str.notEmpty(line.periodStart) || Str.notEmpty(line.periodEnd)) {
                x.start("ram:BillingSpecifiedPeriod");
                dateTime(x, "ram:StartDateTime", line.periodStart);
                dateTime(x, "ram:EndDateTime", line.periodEnd);
                x.end();
            }
            x.start("ram:SpecifiedTradeSettlementLineMonetarySummation")
                    .leaf("ram:LineTotalAmount", Money.amount(line.netCents)).end();
            x.end();

            x.end();
        }

        x.start("ram:ApplicableHeaderTradeAgreement");
        x.leaf("ram:BuyerReference", inv.buyerReference);
        party(x, "ram:SellerTradeParty", inv.seller, true);
        party(x, "ram:BuyerTradeParty", inv.buyer, false);
        x.end();

        x.start("ram:ApplicableHeaderTradeDelivery");
        if (Str.notEmpty(inv.deliveryDate)) {
            x.start("ram:ActualDeliverySupplyChainEvent");
            dateTime(x, "ram:OccurrenceDateTime", inv.deliveryDate);
            x.end();
        }
        x.end();

        x.start("ram:ApplicableHeaderTradeSettlement");
        x.leaf("ram:PaymentReference", inv.remittanceInformation);
        x.leaf("ram:InvoiceCurrencyCode", inv.currency);

        if (Str.notEmpty(inv.paymentMeansCode)) {
            x.start("ram:SpecifiedTradeSettlementPaymentMeans");
            x.leaf("ram:TypeCode", inv.paymentMeansCode);
            if (Str.notEmpty(inv.iban)) {
                x.start("ram:PayeePartyCreditorFinancialAccount");
                x.leaf("ram:IBANID", inv.iban);
                x.leaf("ram:AccountName", inv.accountName);
                x.end();
            }
            if (Str.notEmpty(inv.bic)) {
                x.start("ram:PayeeSpecifiedCreditorFinancialInstitution")
                        .leaf("ram:BICID", inv.bic).end();
            }
            x.end();
        }

        for (TaxBreakdown g : inv.taxBreakdowns) {
            x.start("ram:ApplicableTradeTax");
            x.leaf("ram:CalculatedAmount", Money.amount(g.taxAmountCents));
            x.leaf("ram:TypeCode", "VAT");
            x.leaf("ram:ExemptionReason", g.exemptionReason);
            x.leaf("ram:BasisAmount", Money.amount(g.basisCents));
            x.leaf("ram:CategoryCode", g.category.code);
            x.leaf("ram:ExemptionReasonCode", g.exemptionReasonCode);
            x.leaf("ram:RateApplicablePercent", Money.percent(g.ratePermille));
            x.end();
        }

        if (Str.notEmpty(inv.periodStart) || Str.notEmpty(inv.periodEnd)) {
            x.start("ram:BillingSpecifiedPeriod");
            dateTime(x, "ram:StartDateTime", inv.periodStart);
            dateTime(x, "ram:EndDateTime", inv.periodEnd);
            x.end();
        }

        if (Str.notEmpty(inv.paymentTerms) || Str.notEmpty(inv.dueDate)) {
            x.start("ram:SpecifiedTradePaymentTerms");
            x.leaf("ram:Description", inv.paymentTerms);
            dateTime(x, "ram:DueDateDateTime", inv.dueDate);
            x.end();
        }

        x.start("ram:SpecifiedTradeSettlementHeaderMonetarySummation");
        x.leaf("ram:LineTotalAmount", Money.amount(inv.lineTotalCents));
        x.leaf("ram:TaxBasisTotalAmount", Money.amount(inv.taxBasisCents));
        x.leaf("ram:TaxTotalAmount", Money.amount(inv.taxTotalCents), "currencyID", inv.currency);
        x.leaf("ram:GrandTotalAmount", Money.amount(inv.grandTotalCents));
        x.leaf("ram:TotalPrepaidAmount", Money.amount(inv.prepaidCents));
        x.leaf("ram:DuePayableAmount", Money.amount(inv.duePayableCents));
        x.end();

        if (Str.notEmpty(inv.precedingNumber)) {
            // Sequence-critical the other way round from UBL: in CII the reference to the
            // corrected invoice comes after the totals, at the end of the settlement group.
            x.start("ram:InvoiceReferencedDocument");
            x.leaf("ram:IssuerAssignedID", inv.precedingNumber);
            dateTime(x, "ram:FormattedIssueDateTime", inv.precedingIssueDate);
            x.end();
        }

        x.end();
        x.end();
        x.end();
        return x.toXml();
    }

    /** CII wraps every date in a DateTimeString with format 102, which is {@code yyyyMMdd}. */
    private static void dateTime(XmlBuilder x, String wrapper, String isoDate) {
        if (Str.isEmpty(isoDate)) return;
        x.start(wrapper);
        x.leaf("udt:DateTimeString", isoDate.replace("-", ""), "format", "102");
        x.end();
    }

    private static void party(XmlBuilder x, String qname, EnParty p, boolean seller) {
        x.start(qname);
        x.leaf("ram:ID", p.identifier);
        x.leaf("ram:Name", p.name);

        if (Str.notEmpty(p.legalId) || Str.notEmpty(p.tradingName)) {
            x.start("ram:SpecifiedLegalOrganization");
            if (Str.notEmpty(p.legalId)) {
                x.leaf("ram:ID", p.legalId, "schemeID", p.legalIdScheme);
            }
            x.leaf("ram:TradingBusinessName", p.tradingName);
            x.end();
        }

        if (p.hasContact()) {
            x.start("ram:DefinedTradeContact");
            x.leaf("ram:PersonName", p.contactName);
            if (Str.notEmpty(p.contactPhone)) {
                x.start("ram:TelephoneUniversalCommunication")
                        .leaf("ram:CompleteNumber", p.contactPhone).end();
            }
            if (Str.notEmpty(p.contactEmail)) {
                x.start("ram:EmailURIUniversalCommunication")
                        .leaf("ram:URIID", p.contactEmail).end();
            }
            x.end();
        }

        x.start("ram:PostalTradeAddress");
        x.leaf("ram:PostcodeCode", p.postcode);
        x.leaf("ram:LineOne", p.line1);
        x.leaf("ram:LineTwo", p.line2);
        x.leaf("ram:CityName", p.city);
        x.leaf("ram:CountryID", p.countryCode);
        x.leaf("ram:CountrySubDivisionName", p.countrySubdivision);
        x.end();

        if (Str.notEmpty(p.electronicAddress)) {
            x.start("ram:URIUniversalCommunication");
            x.leaf("ram:URIID", p.electronicAddress, "schemeID", p.electronicAddressScheme);
            x.end();
        }

        if (Str.notEmpty(p.vatId)) {
            x.start("ram:SpecifiedTaxRegistration")
                    .leaf("ram:ID", p.vatId, "schemeID", "VA").end();
        }
        if (seller && Str.notEmpty(p.taxNumber)) {
            x.start("ram:SpecifiedTaxRegistration")
                    .leaf("ram:ID", p.taxNumber, "schemeID", "FC").end();
        }
        x.end();
    }
}
