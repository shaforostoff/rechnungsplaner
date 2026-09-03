# Verification

What the automated suite covers, and what it deliberately does not.

## Covered by `./gradlew test` (158 tests, no device needed)

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
| Invoice corrections | Redoing an issued invoice keeps its number, row and issue date while recomputing fees, snapshots and periods |
| Superseding invoices | A replacement takes a new number and today's date, records what it replaces, and states it in the document language |
| BG-3 placement | The preceding-invoice reference sits where each syntax requires -- before the parties in UBL, after the totals in CII -- and is absent entirely when there is nothing to reference |
| Invoice table columns | Each column has its own width of clearance, so no header prints over the one before it |
| Non-ASCII text | "Stresemannstraße", an ampersand beside umlauts, a non-Latin-1 city and an astral character survive both writers, parsed back with a real XML parser; and the same payload comes out of a packed PDF byte-identical |

## Not covered, and why

**No device or emulator was available**, so nothing that needs the Android runtime has been
executed. Specifically unverified:

- `InvoiceRenderer` has now drawn a page on a device -- a single-job invoice, whose header block
  was read off the result closely enough to find that the metadata labels were being measured
  with the wrong paint. What that page does not cover: more than one page, so the pagination
  threshold is still unexercised, and any line long enough to wrap. The column tiling is tested
  on its own, being arithmetic rather than drawing, but the widths feeding it come from
  `Paint.measureText` on a device.
- `PdfA3Packer` has never seen real `android.graphics.pdf.PdfDocument` output. It handles both
  cross-reference flavours and refuses anything it cannot rewrite safely, but Skia's exact
  structure is an assumption until it runs.
- The database is migrated additively rather than recreated, as of schema 3. It used to drop every
  table on a version change, which was fine while nothing was installed anywhere and became a
  data-loss bug the moment a phone held an invoice series carried over from other software. Both
  paths were executed against the `sqlite3` binary in the Android SDK: the v2-to-v3 step keeps the
  row and its values, adds both columns as NULL and leaves the index standing, and the whole
  `onCreate` schema -- all 16 statements, 7 tables -- runs clean on a fresh database with the new
  columns writable. Re-running an unguarded `ALTER` was executed too and fails with `duplicate
  column name`, which is what `hasColumn` is there for. Still unverified: that `onUpgrade` is
  reached at all on a device, and that an older build tolerates the columns `onDowngrade` now
  leaves in place -- harmless by inspection, since every read is by column name, but not run.
  Schema 4 was executed the same way: the invoice row keeps its values, `paid_year` defaults to
  zero so an existing invoice needs no backfill, moving it to a year and back both work, and the
  `UNIQUE` constraint on the number survived the `ALTER`.
- Schemas 5 and 6 were executed against `sqlite3` like the ones before. Schema 5 names the work
  already recorded 'DJ-Set' and points every job at it -- run twice, to confirm the seed happens
  once and that fees, statuses and invoice links all come through. Schema 6 adds the two service
  flags and the gig end date, and the defaults are what the app did before it had them:
  single-day and mirrored.
- The service name reaching the invoice line and the calendar title is tested, including that it
  is not translated and that a job with no service still describes itself. So is the period an
  invoice states for work spanning days -- BG-14 rather than a BT-72 that would claim a week
  happened on one day -- and that the header span reaches the last day worked rather than the
  last job's start date. Untested: the calendar screen's service buttons, the rename and remove
  dialog, the end-date picker, and whether the provider really renders a multi-day all-day event
  as one block.
- Which year an invoice counts in is tested -- delivery date over period over issue date, a
  payment year overriding the work, and zero meaning derive so that never-moved and moved-back
  are the same state. Untested is the whole list screen: the grouping, the totals, the exclusion
  of superseded invoices from them, and the drag-and-drop are all Android views.
- `InvoiceDao.reissue` runs against SQLite, so its transaction is unverified here: that dropped
  gigs go back to billable, that a gig already marked paid keeps that status, and that the number
  counter is untouched are all argued in code but not executed. The identity rule it depends on
  (`Invoice.takeIdentityFrom`) is tested.
- Settings now commit in `onPause` rather than from a Save button, which removes the only path
  that closed the app from a tab screen. That the lifecycle actually fires before the process
  goes away is the platform's contract, not something asserted here.
- Automatic customer numbering is argued, not executed. That reading `roles.customer.number` off a
  lexoffice contact brings the old numbering across is pinned by tests, but `CustomerDao`'s series
  needs SQLite: that the counter and the `MAX(CAST(...))` scan of existing numbers agree on where
  the series stands, and that the `GLOB` pair really admits only all-digit numbers, are unverified.
- Saving a gig used to write every column back from an object that could be older than the row,
  which unbilled an already-invoiced gig and orphaned its calendar event. An update now writes
  only the fields the gig screen owns. The rule is a column list in `GigDao.editableValues`, and
  nothing enforces it: a column added to `values` is covered by default, but one that later
  becomes another screen's has to be named there by hand. Neither the rule nor the refresh in
  `onResume` is executed here -- both need SQLite and a live back stack.
- The contacts archive exported as a zero-byte zip: it is written straight into the directory
  `ShareProvider` serves, and `Sharing.stage` then copied it onto itself, truncating it through
  the target before the first read. The guard against that is `Paths.isSameFile`, and the
  platform behaviour it turns on -- a copy whose source and target are one path empties the file
  -- is pinned by a test, so the hazard is exercised rather than described. That the fixed share
  now arrives intact at a mail app is still a device check.
- Reading a sequence back out of a hand-typed invoice number is tested, including the round trip
  that matters: every sequence the formatter can render comes back as itself, across four patterns.
  What is not tested is `InvoiceDao.adoptSequence` moving the counter with it, or `numberExists`
  catching a duplicate before the UNIQUE column throws -- both need SQLite.
- Customer-number uniqueness is enforced in code, not by the schema. `holderOfNumber` blocks a
  duplicate typed on the customer screen and the allocator steps its sequence past taken numbers,
  both unverified for the same reason. There is deliberately no `UNIQUE` index: adding one means a
  schema version, and `onUpgrade` currently wipes. So the guarantee is only as good as the two
  paths that check -- a contacts import writing numbers it was given does not, and neither would
  any future writer that forgets to.
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
   `%issuername%-%Y-%M-%D`, and the invoice number reads `2026-001`. Then save to the chosen
   folder and take the offer to open the PDF: the viewer must show the invoice, not an empty
   document. Repeat with an XML-only output format, where the offer must not appear at all.
6. Set the app to English and the customer's invoice language to German, then Spanish: the PDF
   labels and number formatting change, the UI does not, and the XML amounts stay dot-decimal.
7. Export the tour list with two customers in one city: those two lines show venues, the rest
   show cities.
8. Export contacts, clear app data, re-import: customers and issuer come back intact. Check the
   size of the shared zip before anything else -- it was silently zero, and every assertion about
   its contents passed while it was.
9. Share wording. Settings shows the message that will be sent, not an empty box. Change one word,
   share an invoice, and confirm the mail app receives the edited text with `%invoiceno%` and
   `%issuername%` filled in. Then clear both fields, switch the app language, and confirm the
   wording is that language's -- and that merely opening Settings did not pin the old one. Then
   give one customer their own subject and message: theirs must be used while every other customer
   still gets the Settings wording, and clearing their two fields must fall back again. Export
   contacts and re-import, and confirm both survived. A message containing a literal `%` must
   share, not crash.
10. Upgrading with data -- do this one first, and on a copy. With customers, gigs and an issued
    invoice already on the phone, install this build over the previous one and confirm nothing was
    lost: the invoice and its number, the customer numbers, the gig-to-invoice links. The schema
    step is additive as of version 3, where every bump before it wiped.
11. Settings has no Save button: change the invoice format, the file-name pattern and the strict
   lexoffice box, leave by the bottom bar, come back -- all three held. Then change the UI
   language and confirm the screen relabels itself on the spot without closing the app.
12. Customer numbers. Leave the pattern empty and confirm a new customer gets none, and that a
    number typed by hand is kept exactly as typed, leading zeros included. Then import contacts
    carrying numbers `10001..10004`, set the pattern to `%seq%`, and confirm the preview in
    settings reads `10005` and that the next customer created gets it. Finally type `10009` by
    hand on one customer and confirm the following automatic number is `10010`, not `10006` --
    that is the `MAX` scan, and it is the whole reason the series survives an import.
13. Mid-year switch. With no invoices in the app, create one and type the number following the
    last the old software issued, say `2026-038`; the hint must read that the next invoice becomes
    `2026-039`, and it must. Then tick a second gig onto a draft after typing a number and confirm
    the number survives the re-render. Type `2026-038` again on a later invoice and confirm it is
    refused by name rather than crashing on the UNIQUE column. Type `2026/038` against the default
    pattern and confirm the hint says the series will not follow it -- and that it does not.
    Finally correct an issued invoice in place and confirm its number is not editable at all.
14. Stale gig writes. Create an invoice from a gig and press back to the gig screen, which is
    still on the stack: it must now offer "Open invoice" and "Recreate", not "Create invoice".
    Change the fee there, save, reopen, and confirm the gig is still invoiced, still points at
    the same invoice, and that the calendar event was updated rather than duplicated.
15. Relinking. On a gig that is not invoiced, "Link to an existing invoice" must offer only that
    customer's invoices, each showing how many DJ-sets it bills -- a stranded one shows zero.
    Pick it, confirm the buttons become "Open invoice" and "Recreate", then Recreate under the
    same number and confirm the rebuilt document carries the corrected fee and the original
    number. A gig marked paid must still be paid afterwards.
16. Invoice years. Group headings with a total each, and the total must exclude a superseded
    invoice -- issue one, supersede it, and confirm the year counts the replacement only, with
    the excluded count shown. Then hold a December invoice and drag it into the next year: both
    totals must change, and the year it came from must still take it back. Dropping it on any
    other year must be refused, and the empty next-year group has to be there to drop into before
    anything is in it. An invoice paid in the year it was earned should need no dragging at all.
    Then do the same three ways without dragging: tap the year button on the card, long-press the
    card and pick from the two years, and pick the year it is already filed under -- which must
    change nothing rather than reporting a move. With TalkBack on, the year button must announce
    which invoice it moves and where to.
17. Services. On a fresh install there are no buttons, only "Add a new service" and a line saying
    so. Add one, and confirm it appears as a button that books a job of that kind. Long-press it
    to rename, and confirm an unbilled job's invoice line follows the new name. Long-press to
    remove: with no job using it the button goes and the service is gone; with a job using it the
    button goes and that job still shows the old name and can still be billed under it.
18. Multi-day and sync. Make a service multi-day and confirm the job form asks for a last day
    instead of two times, refuses a last day before the first, and that switching the same job to
    a single-day service drops the end date rather than keeping it. Confirm the calendar shows a
    multi-day job as one block across the days, not as one three-hour event. Then turn its sync
    off, save, and confirm the event is removed rather than left behind.
19. Duplicate customer numbers. Give a second customer a number another one already has and
    confirm the save is refused and names the holder. Repeat with the holder archived, and with
    the case changed (`k-007` against `K-007`), which must also be refused. Then confirm a
    customer keeps its own number when saved unchanged.
