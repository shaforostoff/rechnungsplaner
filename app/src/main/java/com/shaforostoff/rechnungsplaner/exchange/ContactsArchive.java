package com.shaforostoff.rechnungsplaner.exchange;

import android.content.Context;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.IssuerDao;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.Json;
import com.shaforostoff.rechnungsplaner.util.Slug;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exports and imports all contact data as a zip of lexoffice-shaped JSON.
 *
 * <p>One file per contact rather than one big document, so an individual contact can be pulled out
 * and posted to {@code /v1/contacts} on its own, and so a diff between two exports shows which
 * contact changed.
 *
 * <pre>
 * manifest.json               what produced this, and when
 * issuer.json                 the user's own details, as a vendor-role contact
 * contacts/0001-club-x.json   one customer each, as customer-role contacts
 * </pre>
 *
 * <p>Nothing here talks to {@code api.lexware.io}. The format is compatible; the transport is the
 * user's own file, shared wherever they like.
 */
public class ContactsArchive {

    public static final int SCHEMA_VERSION = 1;

    /** What an import would do, so the user can look before it happens. */
    public static class ImportPlan {
        public enum Action {
            /** No existing contact matched. */
            CREATE,
            /** Matched; incoming values overwrite the ones that are filled in. */
            UPDATE,
            /** Matched; leave the local record alone. */
            KEEP_MINE,
            /** Matched, but treat the incoming one as a separate contact. */
            KEEP_BOTH
        }

        public static class Entry {
            public Customer incoming;
            /** The matched local record, or null when this is new. */
            public Customer existing;
            public Action action;
            /** How the match was made, for the merge screen to explain itself. */
            public String matchedBy;
        }

        public final List<Entry> entries = new ArrayList<Entry>();
        public Issuer issuer;
        /** Set when the archive carries an issuer; the user decides whether to take it. */
        public boolean replaceIssuer;
        /** Anything unreadable, reported rather than thrown, so one bad file is not fatal. */
        public final List<String> warnings = new ArrayList<String>();

        public int countMatching(Action action) {
            int n = 0;
            for (Entry e : entries) {
                if (e.action == action) n++;
            }
            return n;
        }
    }

    private final Context ctx;
    private final CustomerDao customers;
    private final IssuerDao issuers;

    public ContactsArchive(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.customers = new CustomerDao(this.ctx);
        this.issuers = new IssuerDao(this.ctx);
    }

    public String suggestedFileName() {
        return "rechnungsplaner-contacts-" + Dates.today() + ".zip";
    }

    // ----------------------------------------------------------------- export

    /** Writes the archive into {@code targetDir} and returns it. */
    public File export(File targetDir, boolean strict) throws IOException {
        targetDir.mkdirs();
        File file = new File(targetDir, suggestedFileName());
        OutputStream raw = new FileOutputStream(file);
        try {
            writeTo(raw, strict);
        } finally {
            raw.close();
        }
        return file;
    }

    void writeTo(OutputStream raw, boolean strict) throws IOException {
        List<Customer> all = customers.all(true);
        Issuer issuer = issuers.load();

        ZipOutputStream zip = new ZipOutputStream(raw);
        try {
            Json.Obj manifest = new Json.Obj();
            manifest.put("app", "Rechnungsplaner");
            manifest.put("schemaVersion", (long) SCHEMA_VERSION);
            manifest.put("exportedAt", Dates.iso8601(System.currentTimeMillis()));
            manifest.put("strictLexoffice", strict);
            manifest.put("contacts", all.size());
            manifest.put("hasIssuer", !issuer.isEmpty());
            put(zip, "manifest.json", manifest.toJson());

            if (!issuer.isEmpty()) {
                put(zip, "issuer.json", LexofficeContacts.issuerToJson(issuer, strict));
            }

            int index = 1;
            for (Customer c : all) {
                String name = String.format(Locale.US, "contacts/%04d-%s.json", index++,
                        Slug.slug(c.displayName()));
                put(zip, name, LexofficeContacts.customerToJson(c, strict));
            }
        } finally {
            zip.close();
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // A fixed timestamp keeps two exports of unchanged data byte-identical, so a diff shows
        // only what really changed.
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }

    // ----------------------------------------------------------------- import

    /**
     * Reads an archive, a single contact, or a page from {@code GET /v1/contacts}, and works out
     * what importing it would do. Nothing is written until {@link #apply} is called.
     */
    public ImportPlan plan(InputStream in, String fileName) throws IOException {
        ImportPlan plan = new ImportPlan();
        boolean looksZipped = fileName != null
                && fileName.toLowerCase(Locale.US).endsWith(".zip");

        if (looksZipped) {
            readZip(in, plan);
        } else {
            readJson(readAll(in), plan, fileName);
        }
        return plan;
    }

    private void readZip(InputStream in, ImportPlan plan) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.toLowerCase(Locale.US).endsWith(".json")) continue;
                if (name.endsWith("manifest.json")) continue;
                byte[] bytes = readAll(zip);
                readJson(bytes, plan, name);
            }
        } finally {
            zip.close();
        }
    }

    private void readJson(byte[] bytes, ImportPlan plan, String name) {
        String text;
        try {
            text = new String(bytes, "UTF-8");
        } catch (IOException e) {
            plan.warnings.add(name + ": unreadable");
            return;
        }
        Object node;
        try {
            node = Json.parse(text);
        } catch (Json.MalformedJsonException e) {
            plan.warnings.add(name + ": " + e.getMessage());
            return;
        }

        // A page from GET /v1/contacts wraps the records in "content"; a bare array also occurs.
        List<Object> page = Json.array(node, "content");
        if (page == null && node instanceof List) page = castList(node);
        if (page != null) {
            for (Object item : page) addContact(item, plan);
            return;
        }
        addContact(node, plan);
    }

    private void addContact(Object node, ImportPlan plan) {
        if (node == null) return;
        if (LexofficeContacts.isVendor(node)) {
            plan.issuer = LexofficeContacts.issuerFrom(node);
            plan.replaceIssuer = true;
            return;
        }
        Customer incoming = LexofficeContacts.customerFrom(node);
        if (incoming.billingName().isEmpty() && isEmpty(incoming.city)) return;

        ImportPlan.Entry entry = new ImportPlan.Entry();
        entry.incoming = incoming;
        entry.existing = customers.findMatch(incoming.lexofficeId, incoming.billingName(),
                incoming.city);
        if (entry.existing == null) {
            entry.action = ImportPlan.Action.CREATE;
        } else {
            entry.action = ImportPlan.Action.UPDATE;
            entry.matchedBy = incoming.lexofficeId != null
                    && incoming.lexofficeId.equals(entry.existing.lexofficeId)
                    ? "lexoffice id" : "name and city";
        }
        plan.entries.add(entry);
    }

    /** Carries out a plan the user has reviewed. */
    public void apply(ImportPlan plan) {
        if (plan.replaceIssuer && plan.issuer != null) issuers.save(plan.issuer);
        for (ImportPlan.Entry entry : plan.entries) {
            switch (entry.action) {
                case CREATE:
                    entry.incoming.id = -1L;
                    customers.save(entry.incoming);
                    break;
                case UPDATE:
                    customers.save(merge(entry.existing, entry.incoming));
                    break;
                case KEEP_BOTH:
                    entry.incoming.id = -1L;
                    entry.incoming.lexofficeId = null;
                    customers.save(entry.incoming);
                    break;
                case KEEP_MINE:
                default:
                    break;
            }
        }
    }

    /**
     * Incoming values win where they are filled in; a blank incoming field leaves the local one
     * alone. Re-importing an older, thinner export therefore cannot silently erase details the
     * user has since added.
     */
    static Customer merge(Customer existing, Customer incoming) {
        Customer out = existing;
        out.officialName = pick(incoming.officialName, out.officialName);
        out.placeName = pick(incoming.placeName, out.placeName);
        out.street = pick(incoming.street, out.street);
        out.postcode = pick(incoming.postcode, out.postcode);
        out.city = pick(incoming.city, out.city);
        out.countryCode = pick(incoming.countryCode, out.countryCode);
        out.email = pick(incoming.email, out.email);
        out.contactName = pick(incoming.contactName, out.contactName);
        out.phone = pick(incoming.phone, out.phone);
        out.vatId = pick(incoming.vatId, out.vatId);
        out.buyerReference = pick(incoming.buyerReference, out.buyerReference);
        out.customerNumber = pick(incoming.customerNumber, out.customerNumber);
        out.invoiceLanguage = pick(incoming.invoiceLanguage, out.invoiceLanguage);
        out.note = pick(incoming.note, out.note);
        out.lexofficeId = pick(incoming.lexofficeId, out.lexofficeId);
        if (incoming.defaultFeeCents > 0L) out.defaultFeeCents = incoming.defaultFeeCents;
        if (incoming.defaultTaxMode != null) out.defaultTaxMode = incoming.defaultTaxMode;
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object node) {
        return (List<Object>) node;
    }

    private static String pick(String incoming, String existing) {
        return incoming != null && !incoming.trim().isEmpty() ? incoming : existing;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
