package com.shaforostoff.rechnungsplaner.exchange;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.InvoiceBuilder;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.TaxMode;
import com.shaforostoff.rechnungsplaner.util.Json;

/**
 * Converts contacts to and from the body lexoffice's {@code /v1/contacts} endpoint accepts.
 *
 * <p>Each exported file is a valid POST body, so the archive doubles as something you can paste
 * straight at the API. Three of this app's fields have no lexoffice equivalent -- the venue name,
 * the usual fee, the per-customer invoice language -- and they live in a {@code _rechnungsplaner}
 * block rather than being smuggled into {@code note}, where they would be unparseable on the way
 * back. A strict mode omits that block for anyone who wants the plain shape.
 *
 * <p>Pure Java, so the shape is pinned by golden-file tests rather than by hope.
 */
public final class LexofficeContacts {

    /** Bumped if the extension block ever changes shape, so an importer can tell. */
    public static final int EXTENSION_VERSION = 1;
    private static final String EXTENSION_KEY = "_rechnungsplaner";

    private LexofficeContacts() {
    }

    // ------------------------------------------------------------------ write

    /**
     * @param issuer needed for {@code allowTaxFreeInvoices}: whether invoices to this customer
     *               carry VAT is a question about the seller as much as the customer, and most
     *               customers answer it by inheriting the issuer's default rather than setting one
     */
    public static String customerToJson(Customer c, Issuer issuer, boolean strict) {
        Json.Obj root = new Json.Obj();
        if (c.lexofficeId != null && !c.lexofficeId.trim().isEmpty()) {
            root.put("id", c.lexofficeId);
        }
        root.put("version", 0L);
        root.put("roles", new Json.Obj().putEmptyObject("customer"));

        Json.Obj company = new Json.Obj();
        company.put("name", c.billingName());
        company.put("vatRegistrationId", c.vatId);
        // The effective mode, resolved the way an invoice would resolve it. Hard-coding false here
        // told a Kleinunternehmer's importer that every one of their customers must be charged
        // VAT -- the opposite of every invoice the app then produced.
        company.put("allowTaxFreeInvoices",
                !InvoiceBuilder.resolveTaxMode(issuer, c, null).chargesVat());
        Json.Obj person = contactPerson(c.contactName, c.email, c.phone);
        if (person != null) {
            company.put("contactPersons", new Json.Arr().add(person));
        }
        root.put("company", company);

        Json.Obj billing = new Json.Obj();
        billing.put("street", c.street);
        billing.put("zip", c.postcode);
        billing.put("city", c.city);
        billing.put("countryCode", c.countryCode == null ? "DE" : c.countryCode);
        if (!billing.isEmpty()) {
            root.put("addresses", new Json.Obj().put("billing", new Json.Arr().add(billing)));
        }

        if (c.email != null && !c.email.trim().isEmpty()) {
            root.put("emailAddresses", new Json.Obj().put("business", new Json.Arr().add(c.email)));
        }
        if (c.phone != null && !c.phone.trim().isEmpty()) {
            root.put("phoneNumbers", new Json.Obj().put("business", new Json.Arr().add(c.phone)));
        }
        if (c.buyerReference != null && !c.buyerReference.trim().isEmpty()) {
            root.put("xRechnung", new Json.Obj().put("buyerReference", c.buyerReference));
        }
        root.put("note", c.note);
        root.put("archived", c.archived);

        if (!strict) {
            Json.Obj ext = new Json.Obj();
            ext.put("schemaVersion", (long) EXTENSION_VERSION);
            ext.put("localId", c.id);
            ext.put("placeName", c.placeName);
            ext.put("defaultFeeCents", c.defaultFeeCents);
            if (c.defaultTaxMode != null) ext.put("defaultTaxMode", c.defaultTaxMode.name());
            ext.put("invoiceLanguage", c.invoiceLanguage);
            ext.put("customerNumber", c.customerNumber);
            root.put(EXTENSION_KEY, ext);
        }
        return root.toJson();
    }

    /** The issuer as a vendor-role contact, so it round-trips through the same reader. */
    public static String issuerToJson(Issuer i, boolean strict) {
        Json.Obj root = new Json.Obj();
        root.put("version", 0L);
        root.put("roles", new Json.Obj().putEmptyObject("vendor"));

        Json.Obj company = new Json.Obj();
        company.put("name", i.name);
        company.put("taxNumber", i.taxNumber);
        company.put("vatRegistrationId", i.vatId);
        // Every mode at rate zero issues without VAT, which is exactly what this flag means --
        // Kleinunternehmer, but reverse charge and an intra-EU supply just as much.
        company.put("allowTaxFreeInvoices",
                !InvoiceBuilder.resolveTaxMode(i, null, null).chargesVat());
        Json.Obj person = contactPerson(i.contactName, i.email, i.phone);
        if (person != null) company.put("contactPersons", new Json.Arr().add(person));
        root.put("company", company);

        Json.Obj billing = new Json.Obj();
        billing.put("street", i.street);
        billing.put("zip", i.postcode);
        billing.put("city", i.city);
        billing.put("countryCode", i.countryCode == null ? "DE" : i.countryCode);
        if (!billing.isEmpty()) {
            root.put("addresses", new Json.Obj().put("billing", new Json.Arr().add(billing)));
        }
        if (i.email != null && !i.email.trim().isEmpty()) {
            root.put("emailAddresses", new Json.Obj().put("business", new Json.Arr().add(i.email)));
        }
        if (i.phone != null && !i.phone.trim().isEmpty()) {
            root.put("phoneNumbers", new Json.Obj().put("business", new Json.Arr().add(i.phone)));
        }

        if (!strict) {
            Json.Obj ext = new Json.Obj();
            ext.put("schemaVersion", (long) EXTENSION_VERSION);
            ext.put("iban", i.iban);
            ext.put("bic", i.bic);
            ext.put("accountHolder", i.accountHolder);
            ext.put("defaultTaxMode",
                    (i.defaultTaxMode == null ? TaxMode.KLEINUNTERNEHMER : i.defaultTaxMode).name());
            ext.put("exemptionText", i.exemptionText);
            ext.put("defaultDueDays", i.defaultDueDays);
            ext.put("paymentTermsText", i.paymentTermsText);
            ext.put("defaultInvoiceLanguage", i.defaultInvoiceLanguage);
            root.put(EXTENSION_KEY, ext);
        }
        return root.toJson();
    }

    private static Json.Obj contactPerson(String name, String email, String phone) {
        boolean any = notEmpty(name) || notEmpty(email) || notEmpty(phone);
        if (!any) return null;
        Json.Obj p = new Json.Obj();
        if (notEmpty(name)) {
            String trimmed = name.trim();
            int space = trimmed.lastIndexOf(' ');
            if (space > 0) {
                p.put("firstName", trimmed.substring(0, space));
                p.put("lastName", trimmed.substring(space + 1));
            } else {
                p.put("lastName", trimmed);
            }
        }
        p.put("primary", true);
        p.put("emailAddress", email);
        p.put("phoneNumber", phone);
        return p;
    }

    // ------------------------------------------------------------------- read

    /** Reads one contact, whether it came from this app or straight from the lexoffice API. */
    public static Customer customerFrom(Object node) {
        Customer c = new Customer();
        c.lexofficeId = Json.string(node, "id");

        String companyName = Json.string(node, "company", "name");
        if (companyName == null) {
            // A private-person contact has no company block; build a name from the parts.
            String first = Json.string(node, "person", "firstName");
            String last = Json.string(node, "person", "lastName");
            companyName = join(first, last);
        }
        c.officialName = companyName;
        c.vatId = Json.string(node, "company", "vatRegistrationId");

        Object billing = firstBillingAddress(node);
        if (billing != null) {
            c.street = Json.string(billing, "street");
            c.postcode = Json.string(billing, "zip");
            c.city = Json.string(billing, "city");
            String country = Json.string(billing, "countryCode");
            if (country != null) c.countryCode = country;
        }

        c.email = Json.firstString(node, "emailAddresses", "business");
        if (c.email == null) c.email = Json.firstString(node, "emailAddresses", "office");
        if (c.email == null) c.email = Json.firstString(node, "emailAddresses", "other");
        c.phone = Json.firstString(node, "phoneNumbers", "business");
        if (c.phone == null) c.phone = Json.firstString(node, "phoneNumbers", "mobile");

        Object person = firstContactPerson(node);
        if (person != null) {
            c.contactName = join(Json.string(person, "firstName"), Json.string(person, "lastName"));
            if (c.email == null) c.email = Json.string(person, "emailAddress");
            if (c.phone == null) c.phone = Json.string(person, "phoneNumber");
        }

        c.buyerReference = Json.string(node, "xRechnung", "buyerReference");
        c.note = Json.string(node, "note");
        c.archived = Json.bool(node, false, "archived");

        Object ext = Json.at(node, EXTENSION_KEY);
        if (ext != null) {
            c.placeName = Json.string(ext, "placeName");
            c.defaultFeeCents = Json.number(ext, 0L, "defaultFeeCents");
            c.defaultTaxMode = TaxMode.fromName(Json.string(ext, "defaultTaxMode"), null);
            c.invoiceLanguage = Json.string(ext, "invoiceLanguage");
            c.customerNumber = Json.string(ext, "customerNumber");
        }
        // lexoffice keeps its own customer number under the customer role, as a JSON number.
        // Reading it is what lets an export straight from the API bring an existing numbering
        // across instead of leaving every contact to be renumbered by hand.
        if (c.customerNumber == null) {
            c.customerNumber = Json.text(node, "roles", "customer", "number");
        }
        // A contact with no name at all is still usable if it names a venue.
        if (c.officialName == null && c.placeName == null) c.placeName = c.city;
        return c;
    }

    public static Issuer issuerFrom(Object node) {
        Issuer i = new Issuer();
        i.name = orEmpty(Json.string(node, "company", "name"));
        i.taxNumber = orEmpty(Json.string(node, "company", "taxNumber"));
        i.vatId = orEmpty(Json.string(node, "company", "vatRegistrationId"));

        Object billing = firstBillingAddress(node);
        if (billing != null) {
            i.street = orEmpty(Json.string(billing, "street"));
            i.postcode = orEmpty(Json.string(billing, "zip"));
            i.city = orEmpty(Json.string(billing, "city"));
            String country = Json.string(billing, "countryCode");
            if (country != null) i.countryCode = country;
        }
        i.email = orEmpty(Json.firstString(node, "emailAddresses", "business"));
        i.phone = orEmpty(Json.firstString(node, "phoneNumbers", "business"));

        Object person = firstContactPerson(node);
        if (person != null) {
            i.contactName = orEmpty(join(Json.string(person, "firstName"),
                    Json.string(person, "lastName")));
            if (i.email.isEmpty()) i.email = orEmpty(Json.string(person, "emailAddress"));
            if (i.phone.isEmpty()) i.phone = orEmpty(Json.string(person, "phoneNumber"));
        }

        Object ext = Json.at(node, EXTENSION_KEY);
        if (ext != null) {
            i.iban = orEmpty(Json.string(ext, "iban"));
            i.bic = orEmpty(Json.string(ext, "bic"));
            i.accountHolder = orEmpty(Json.string(ext, "accountHolder"));
            i.defaultTaxMode = TaxMode.fromName(Json.string(ext, "defaultTaxMode"),
                    TaxMode.KLEINUNTERNEHMER);
            i.exemptionText = orEmpty(Json.string(ext, "exemptionText"));
            i.defaultDueDays = (int) Json.number(ext, 30L, "defaultDueDays");
            i.paymentTermsText = orEmpty(Json.string(ext, "paymentTermsText"));
            String lang = Json.string(ext, "defaultInvoiceLanguage");
            if (lang != null) i.defaultInvoiceLanguage = lang;
        } else if (Json.bool(node, false, "company", "allowTaxFreeInvoices")) {
            i.defaultTaxMode = TaxMode.KLEINUNTERNEHMER;
        }
        return i;
    }

    /** True when the contact is marked as a vendor, which is how the issuer is recognised. */
    public static boolean isVendor(Object node) {
        return Json.at(node, "roles", "vendor") != null;
    }

    private static Object firstBillingAddress(Object node) {
        java.util.List<Object> list = Json.array(node, "addresses", "billing");
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static Object firstContactPerson(Object node) {
        java.util.List<Object> list = Json.array(node, "company", "contactPersons");
        if (list == null || list.isEmpty()) return null;
        for (Object o : list) {
            if (Json.bool(o, false, "primary")) return o;
        }
        return list.get(0);
    }

    private static String join(String first, String last) {
        boolean f = notEmpty(first);
        boolean l = notEmpty(last);
        if (f && l) return first.trim() + " " + last.trim();
        if (f) return first.trim();
        return l ? last.trim() : null;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
