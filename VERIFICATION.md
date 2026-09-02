# Verification

What the automated suite covers, and what it deliberately does not.

## Covered by `./gradlew test` (136 tests, no device needed)

The `einvoice`, `data`, `exchange`, `util` and `pdf` packages avoid `android.*` imports
specifically so they can be tested on the JVM. The `ui` package cannot be, but its pure decisions
-- money parsing, the mirrored-field rule -- are static methods that load and run there anyway.

| Area | What is pinned |
|---|---|
| Money and totals | Half-up rounding per VAT category, never per line (BR-CO-17) |
| UBL and CII writers | Golden files for both syntaxes across all four VAT situations |
| Validator | Each BR-DE rule fires on the field it should, and only under XRechnung |
| Invoice building | Delivery-date vs invoicing-period rule, tax-mode and language precedence |
| Stored vs emitted totals | One test pins them together; they are computed by different code |
| PDF packer | Real Ghostscript PDFs, classic xref and xref stream, byte-stability of the original |
| Attachment discovery | poppler `pdfdetach` finds and extracts `factur-x.xml` (skipped if poppler absent) |
| Tour list | The per-city, per-customer disambiguation rule |
| Contacts archive | Round-trip, plus reading a contact straight from the lexoffice API shape |
| Fee fields | Both decimal separators, grouping, junk and overflow; and that what a field shows reads back as the same cents |
| Gig status | A past date starts as played, including across a year boundary |

## Not covered, and why

**No device or emulator was available**, so nothing that needs the Android runtime has been
executed. Specifically unverified:

- `InvoiceRenderer` has never drawn a page. The layout arithmetic, the pagination threshold and
  the measured column widths are unexercised.
- `PdfA3Packer` has never seen real `android.graphics.pdf.PdfDocument` output. It handles both
  cross-reference flavours and refuses anything it cannot rewrite safely, but Skia's exact
  structure is an assumption until it runs.
- `CalendarMirror`, `ShareProvider`, `SafExporter` and every screen are compile-verified only.
  This includes the field mechanics whose *decisions* are tested -- that the account holder mirrors
  the name is pinned as a rule, but nothing has confirmed the `TextWatcher` is wired to the right
  two fields.

**Strict PDF/A-3b conformance is claimed in the XMP but not proven.** The output intent, the
generated sRGB profile and the metadata stream are built to the requirements, but only veraPDF
can confirm it. Until then the PDF is known to be a valid PDF carrying a discoverable,
byte-intact invoice attachment -- which is what the target systems actually read.

## Steps that need a machine with more than this one has

```bash
./gradlew test assembleDebug
```

Then, per output format, with a generated file:

- **XRechnung XML** — KoSIT validator with the current configuration:
  `java -jar validationtool-*-standalone.jar -s scenarios.xml -r <config-dir> invoice.xml`.
  Expect zero errors for a fully-filled customer, and exactly the BR-DE-15 error when the buyer
  reference is blank -- which `EnValidator` must already have flagged in-app.
- **ZUGFeRD hybrid PDF** — `java -jar Mustang-CLI-*.jar --action validate --source invoice.pdf`
- **PDF/A-3b container** — `verapdf --flavour 3b invoice.pdf`

## Three-way import (the acceptance test that actually matters)

The validators prove the file is well-formed. They do not prove these three like it. Import both
the `.xml` and the `.pdf` into each and check invoice number, both dates, net/VAT/gross, IBAN and
the full party details land correctly.

| Target | XRechnung UBL | XRechnung CII | ZUGFeRD PDF | Notes |
|---|---|---|---|---|
| Lexware Office | | | | |
| easybill | | | | |
| sevDesk | | | | |

Re-run this table after any change to `UblWriter`, `CiiWriter`, `Totals` or `PdfA3Packer`. The
failure modes differ between the three, so passing one says little about the others.

## Manual device pass

1. Fill **My details**; the blue outlines clear as required fields are completed.
2. Create a customer with **only a venue name**; add a DJ-set; confirm it appears in the month
   grid and in Google Calendar within a sync cycle; edit it and confirm the event updates rather
   than duplicating.
3. Add the club's real details afterwards; create the invoice; the missing-fields list should
   shrink to zero, and "Create anyway" must work before it does.
4. Bill two gigs of one customer on one invoice: two XML line items, each with its own BG-26
   period, and a header BG-14 spanning both.
5. Share to Telegram and to a mail app; the attachment opens, the file name matches
   `%issuername%-%Y-%M-%D`, and the invoice number reads `2026-001`.
6. Set the app to English and the customer's invoice language to German, then Spanish: the PDF
   labels and number formatting change, the UI does not, and the XML amounts stay dot-decimal.
7. Export the tour list with two customers in one city: those two lines show venues, the rest
   show cities.
8. Export contacts, clear app data, re-import: customers and issuer come back intact.
