package com.shaforostoff.rechnungsplaner.exchange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.TaxMode;
import com.shaforostoff.rechnungsplaner.util.Json;

import org.junit.Test;

public class LexofficeContactsTest {

    private static Customer club() {
        Customer c = new Customer();
        c.id = 7L;
        c.officialName = "Club Muster GmbH";
        c.placeName = "Muster Club";
        c.street = "Clubstrasse 5";
        c.postcode = "20095";
        c.city = "Hamburg";
        c.countryCode = "DE";
        c.email = "buchhaltung@club-muster.de";
        c.phone = "+49 40 1234567";
        c.contactName = "Erika Musterfrau";
        c.vatId = "DE987654321";
        c.buyerReference = "CLUB-2026-07";
        c.customerNumber = "K-0007";
        c.defaultFeeCents = 35000L;
        c.defaultTaxMode = TaxMode.STANDARD_19;
        c.invoiceLanguage = "de";
        c.note = "Load-in via the back door.";
        return c;
    }

    @Test
    public void writesTheShapeTheLexofficeApiAccepts() throws Exception {
        Object node = Json.parse(LexofficeContacts.customerToJson(club(), false));

        assertEquals("Club Muster GmbH", Json.string(node, "company", "name"));
        assertEquals("DE987654321", Json.string(node, "company", "vatRegistrationId"));
        assertEquals("Clubstrasse 5",
                Json.string(Json.array(node, "addresses", "billing").get(0), "street"));
        assertEquals("20095", Json.string(Json.array(node, "addresses", "billing").get(0), "zip"));
        assertEquals("Hamburg", Json.string(Json.array(node, "addresses", "billing").get(0), "city"));
        assertEquals("DE",
                Json.string(Json.array(node, "addresses", "billing").get(0), "countryCode"));
        assertEquals("buchhaltung@club-muster.de",
                Json.firstString(node, "emailAddresses", "business"));
        assertEquals("CLUB-2026-07", Json.string(node, "xRechnung", "buyerReference"));
        assertTrue("the customer role marks it as a customer",
                Json.at(node, "roles", "customer") != null);
    }

    @Test
    public void splitsTheContactPersonIntoFirstAndLastName() throws Exception {
        Object node = Json.parse(LexofficeContacts.customerToJson(club(), false));
        Object person = Json.array(node, "company", "contactPersons").get(0);
        assertEquals("Erika", Json.string(person, "firstName"));
        assertEquals("Musterfrau", Json.string(person, "lastName"));
        assertTrue(Json.bool(person, false, "primary"));
    }

    @Test
    public void appSpecificFieldsLiveInTheirOwnBlock() throws Exception {
        // Not smuggled into note, where they would be unparseable coming back.
        Object node = Json.parse(LexofficeContacts.customerToJson(club(), false));
        assertEquals("Muster Club", Json.string(node, "_rechnungsplaner", "placeName"));
        assertEquals(35000L, Json.number(node, 0L, "_rechnungsplaner", "defaultFeeCents"));
        assertEquals("STANDARD_19", Json.string(node, "_rechnungsplaner", "defaultTaxMode"));
        assertEquals("Load-in via the back door.", Json.string(node, "note"));
    }

    @Test
    public void strictModeOmitsTheExtensionBlock() throws Exception {
        Object node = Json.parse(LexofficeContacts.customerToJson(club(), true));
        assertNull(Json.at(node, "_rechnungsplaner"));
        assertEquals("Club Muster GmbH", Json.string(node, "company", "name"));
    }

    @Test
    public void customerSurvivesARoundTrip() throws Exception {
        Customer before = club();
        Customer after = LexofficeContacts.customerFrom(
                Json.parse(LexofficeContacts.customerToJson(before, false)));

        assertEquals(before.officialName, after.officialName);
        assertEquals(before.placeName, after.placeName);
        assertEquals(before.street, after.street);
        assertEquals(before.postcode, after.postcode);
        assertEquals(before.city, after.city);
        assertEquals(before.countryCode, after.countryCode);
        assertEquals(before.email, after.email);
        assertEquals(before.phone, after.phone);
        assertEquals(before.contactName, after.contactName);
        assertEquals(before.vatId, after.vatId);
        assertEquals(before.buyerReference, after.buyerReference);
        assertEquals(before.customerNumber, after.customerNumber);
        assertEquals(before.defaultFeeCents, after.defaultFeeCents);
        assertEquals(before.defaultTaxMode, after.defaultTaxMode);
        assertEquals(before.invoiceLanguage, after.invoiceLanguage);
        assertEquals(before.note, after.note);
    }

    @Test
    public void venueOnlyCustomerSurvivesARoundTrip() throws Exception {
        // The half-filled customer is the normal case here, so it must not be lost on export.
        Customer before = new Customer();
        before.id = 9L;
        before.placeName = "Muster Club";
        before.city = "Hamburg";

        Customer after = LexofficeContacts.customerFrom(
                Json.parse(LexofficeContacts.customerToJson(before, false)));
        assertEquals("Muster Club", after.placeName);
        assertEquals("Hamburg", after.city);
        assertEquals("Muster Club", after.billingName());
    }

    @Test
    public void readsAContactStraightFromTheLexofficeApi() throws Exception {
        // No extension block: everything app-specific has to degrade gracefully.
        String api = "{"
                + "\"id\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\","
                + "\"version\":3,"
                + "\"roles\":{\"customer\":{\"number\":10001}},"
                + "\"company\":{\"name\":\"Sala Ejemplo SL\",\"vatRegistrationId\":\"ESA12345674\","
                + "\"contactPersons\":[{\"firstName\":\"Ana\",\"lastName\":\"Ruiz\","
                + "\"primary\":true,\"emailAddress\":\"ana@sala.es\"}]},"
                + "\"addresses\":{\"billing\":[{\"street\":\"Calle Ejemplo 7\",\"zip\":\"08001\","
                + "\"city\":\"Barcelona\",\"countryCode\":\"ES\"}]},"
                + "\"archived\":false}";

        Customer c = LexofficeContacts.customerFrom(Json.parse(api));
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", c.lexofficeId);
        assertEquals("Sala Ejemplo SL", c.officialName);
        assertEquals("ESA12345674", c.vatId);
        assertEquals("Barcelona", c.city);
        assertEquals("ES", c.countryCode);
        assertEquals("Ana Ruiz", c.contactName);
        assertEquals("ana@sala.es", c.email);
        assertFalse(c.archived);
        assertEquals("the existing numbering has to survive the import", "10001",
                c.customerNumber);
    }

    @Test
    public void ownExtensionBlockOutranksTheLexofficeCustomerNumber() throws Exception {
        // Both present: the app's own value is the one it wrote itself and the one BT-46 carried,
        // so a re-import of its own archive must not silently switch to lexoffice's number.
        String both = "{"
                + "\"roles\":{\"customer\":{\"number\":10001}},"
                + "\"company\":{\"name\":\"Club Muster GmbH\"},"
                + "\"_rechnungsplaner\":{\"schemaVersion\":1,\"customerNumber\":\"K-0007\"}}";

        assertEquals("K-0007", LexofficeContacts.customerFrom(Json.parse(both)).customerNumber);
    }

    @Test
    public void aCustomerNumberWrittenAsAStringIsReadToo() throws Exception {
        String asText = "{\"roles\":{\"customer\":{\"number\":\"10042\"}},"
                + "\"company\":{\"name\":\"Club Muster GmbH\"}}";

        assertEquals("10042", LexofficeContacts.customerFrom(Json.parse(asText)).customerNumber);
    }

    @Test
    public void aContactWithNoNumberAnywhereGetsNone() throws Exception {
        String none = "{\"roles\":{\"customer\":{}},"
                + "\"company\":{\"name\":\"Club Muster GmbH\"}}";

        assertNull(LexofficeContacts.customerFrom(Json.parse(none)).customerNumber);
    }

    @Test
    public void issuerSurvivesARoundTripIncludingBankDetails() throws Exception {
        Issuer before = new Issuer();
        before.name = "Nick Shaforostov";
        before.street = "Musterstrasse 1";
        before.postcode = "10115";
        before.city = "Berlin";
        before.contactName = "Nick Shaforostov";
        before.email = "dj@example.de";
        before.phone = "+49 30 1234567";
        before.taxNumber = "12/345/67890";
        before.iban = "DE89370400440532013000";
        before.bic = "COBADEFFXXX";
        before.accountHolder = "Nick Shaforostov";
        before.defaultTaxMode = TaxMode.KLEINUNTERNEHMER;
        before.defaultDueDays = 14;
        before.defaultInvoiceLanguage = "en";

        String json = LexofficeContacts.issuerToJson(before, false);
        assertTrue("the issuer is a vendor, not a customer",
                LexofficeContacts.isVendor(Json.parse(json)));

        Issuer after = LexofficeContacts.issuerFrom(Json.parse(json));
        assertEquals(before.name, after.name);
        assertEquals(before.street, after.street);
        assertEquals(before.city, after.city);
        assertEquals(before.taxNumber, after.taxNumber);
        assertEquals(before.iban, after.iban);
        assertEquals(before.bic, after.bic);
        assertEquals(before.accountHolder, after.accountHolder);
        assertEquals(before.defaultTaxMode, after.defaultTaxMode);
        assertEquals(before.defaultDueDays, after.defaultDueDays);
        assertEquals(before.defaultInvoiceLanguage, after.defaultInvoiceLanguage);
    }

    @Test
    public void kleinunternehmerMapsToTheTaxFreeFlag() throws Exception {
        Issuer i = new Issuer();
        i.name = "Nick Shaforostov";
        i.defaultTaxMode = TaxMode.KLEINUNTERNEHMER;
        assertTrue(Json.bool(Json.parse(LexofficeContacts.issuerToJson(i, true)), false,
                "company", "allowTaxFreeInvoices"));

        i.defaultTaxMode = TaxMode.STANDARD_19;
        assertFalse(Json.bool(Json.parse(LexofficeContacts.issuerToJson(i, true)), true,
                "company", "allowTaxFreeInvoices"));
    }
}
