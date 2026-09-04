package com.shaforostoff.rechnungsplaner.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.Invoice;
import com.shaforostoff.rechnungsplaner.data.InvoiceBuilder;
import com.shaforostoff.rechnungsplaner.data.InvoiceDao;
import com.shaforostoff.rechnungsplaner.data.InvoiceMapper;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.IssuerDao;
import com.shaforostoff.rechnungsplaner.data.OutputFormat;
import com.shaforostoff.rechnungsplaner.data.Service;
import com.shaforostoff.rechnungsplaner.data.ServiceDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.einvoice.EnInvoice;
import com.shaforostoff.rechnungsplaner.einvoice.EnValidator;
import com.shaforostoff.rechnungsplaner.einvoice.Problem;
import com.shaforostoff.rechnungsplaner.einvoice.Profile;
import com.shaforostoff.rechnungsplaner.exchange.LexofficeContacts;
import com.shaforostoff.rechnungsplaner.output.InvoiceWriter;
import com.shaforostoff.rechnungsplaner.output.SafExporter;
import com.shaforostoff.rechnungsplaner.output.Sharing;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.PatternFormatter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Builds, checks and issues an invoice.
 *
 * <p>The shape of this screen is borrowed from the reference app: a live totals card, and a panel
 * that names every field still missing with its BT code. The important behaviour is that the panel
 * never blocks. "Create anyway" is always available, because drafting an invoice before the club
 * has sent its address is the normal way this app gets used, not a mistake to be prevented.
 */
public class InvoiceActivity extends BaseActivity {

    private static final String EXTRA_GIG_ID = "gig_id";
    private static final String EXTRA_INVOICE_ID = "invoice_id";
    private static final String EXTRA_REISSUE = "reissue";
    private static final String EXTRA_NEW_NUMBER = "new_number";
    private static final int REQUEST_PICK_FOLDER = 41;

    public static Intent draftIntent(Context ctx, long gigId) {
        return new Intent(ctx, InvoiceActivity.class).putExtra(EXTRA_GIG_ID, gigId);
    }

    public static Intent openIntent(Context ctx, long invoiceId) {
        return new Intent(ctx, InvoiceActivity.class).putExtra(EXTRA_INVOICE_ID, invoiceId);
    }

    /**
     * Opens an issued invoice as an editable draft that will replace it.
     *
     * @param newNumber true to issue a second document referencing the first, which is what an
     *                  invoice already in the customer's hands needs; false to correct the
     *                  existing one in place, keeping its number
     */
    public static Intent reissueIntent(Context ctx, long invoiceId, boolean newNumber) {
        return new Intent(ctx, InvoiceActivity.class).putExtra(EXTRA_INVOICE_ID, invoiceId)
                .putExtra(EXTRA_REISSUE, true)
                .putExtra(EXTRA_NEW_NUMBER, newNumber);
    }

    /**
     * Asks which kind of redo this is, then starts it.
     *
     * <p>The choice cannot be inferred: it turns on whether the invoice has already been sent,
     * which the app has no way of knowing. So the options name the consequence rather than the
     * mechanism -- one keeps the number, the other spends a new one.
     *
     * @param closeHost true when the caller is showing the invoice being replaced, and would be
     *                  left displaying stale totals behind the new screen
     */
    public static void askHowToRecreate(final Activity host, final long invoiceId,
                                        final String number, final boolean closeHost) {
        String[] options = {
                host.getString(R.string.recreate_same_number, number),
                host.getString(R.string.recreate_new_number),
        };
        new AlertDialog.Builder(host)
                .setTitle(R.string.action_recreate)
                .setNegativeButton(R.string.action_cancel, null)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.startActivity(reissueIntent(host, invoiceId, which == 1));
                        if (closeHost) host.finish();
                    }
                })
                .show();
    }

    private SettingsStore settings;
    private InvoiceDao invoices;
    private GigDao gigs;
    private Issuer issuer;
    private Customer customer;
    private Invoice invoice;

    /** Empty once the invoice is issued; the gigs are then recorded on the invoice itself. */
    private final List<Gig> billedGigs = new ArrayList<Gig>();
    private final List<Gig> selectableGigs = new ArrayList<Gig>();
    private final List<Boolean> selected = new ArrayList<Boolean>();

    private boolean issued;
    /**
     * The issued invoice being corrected, or null for a new one. Held whole rather than as copied
     * fields because each rebuild replaces {@link #invoice} and has to take its identity again.
     */
    private Invoice replacing;
    /** True when {@link #replacing} is to be superseded by a new document rather than corrected. */
    private boolean withNewNumber;
    private Spinner formatSpinner;
    private EditText numberField;
    private TextView numberHint;
    /** A number the user typed, kept across re-renders of the form. Null means follow the series. */
    private String numberOverride;
    private List<File> lastFiles = new ArrayList<File>();

    /**
     * The two answers on a draft that are the user's rather than the data's, carried across a
     * rebuild the way {@code GigEditActivity} carries its gig.
     *
     * <p>Both would otherwise be recomputed from scratch when the activity is recreated, which
     * unticks every gig that had been added to the bill and drops a hand-typed number back to the
     * next one in the series.
     */
    private static final class Unsaved {
        final List<Boolean> selected;
        final String numberOverride;

        Unsaved(List<Boolean> selected, String numberOverride) {
            this.selected = new ArrayList<Boolean>(selected);
            this.numberOverride = numberOverride;
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        captureNumber();
        return new Unsaved(selected, numberOverride);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        invoices = new InvoiceDao(this);
        gigs = new GigDao(this);
        issuer = new IssuerDao(this).load();

        long invoiceId = getIntent().getLongExtra(EXTRA_INVOICE_ID, -1L);
        boolean wantsReissue = getIntent().getBooleanExtra(EXTRA_REISSUE, false);
        if (invoiceId > 0L) {
            invoice = invoices.byId(invoiceId);
            issued = invoice != null;
        }
        if (invoice != null && wantsReissue && !buildCorrection()) {
            finish();
            return;
        }
        if (invoice == null && !buildDraft()) {
            finish();
            return;
        }
        if (replacing == null) customer = new CustomerDao(this).byId(invoice.customerId);
        restoreUnsaved();

        setScreenTitle(invoice.number == null ? getString(R.string.title_invoice) : invoice.number);
        render();
    }

    /**
     * Loads an issued invoice back into a draft that will overwrite it.
     *
     * <p>Everything is recomputed from current data -- the gigs' fees, the customer's details, the
     * issuer's address -- because that is the whole point: the reasons to redo an invoice are all
     * "something was wrong or stale when it was first written". What is deliberately <em>not</em>
     * recomputed is the invoice's identity: the number, the issue date and therefore the document
     * this is a correction of.
     *
     * @return false when the invoice's gigs have gone, leaving nothing to rebuild from
     */
    private boolean buildCorrection() {
        if (issuer.isEmpty()) {
            Ui.toast(this, R.string.issuer_incomplete);
            startActivity(new Intent(this, IssuerEditActivity.class));
            return false;
        }
        List<Gig> onInvoice = gigs.forInvoice(invoice.id);
        if (onInvoice.isEmpty()) return false;

        replacing = invoice;
        withNewNumber = getIntent().getBooleanExtra(EXTRA_NEW_NUMBER, false);
        issued = false;

        // The gig's customer rather than the invoice's: reassigning a gig to the booker who
        // actually pays is one of the corrections this exists for.
        CustomerDao customers = new CustomerDao(this);
        Gig first = onInvoice.get(0);
        customer = customers.byId(first.customerId);
        if (customer == null) customer = customers.byId(invoice.customerId);

        billedGigs.clear();
        billedGigs.addAll(onInvoice);

        selectableGigs.clear();
        selected.clear();
        if (customer != null) {
            for (Gig other : gigs.billableFor(customer.id)) {
                if (containsGig(onInvoice, other.id)) continue;
                selectableGigs.add(other);
                selected.add(Boolean.FALSE);
            }
        }
        rebuildInvoice();
        return true;
    }

    private static boolean containsGig(List<Gig> gigs, long id) {
        for (Gig g : gigs) {
            if (g.id == id) return true;
        }
        return false;
    }

    /** @return false when there is nothing to bill, which should not happen from the gig screen */
    private boolean buildDraft() {
        Gig gig = gigs.byId(getIntent().getLongExtra(EXTRA_GIG_ID, -1L));
        if (gig == null) return false;
        if (issuer.isEmpty()) {
            Ui.toast(this, R.string.issuer_incomplete);
            startActivity(new Intent(this, IssuerEditActivity.class));
            return false;
        }
        customer = new CustomerDao(this).byId(gig.customerId);

        billedGigs.clear();
        billedGigs.add(gig);

        // Other unbilled gigs for the same customer can join this invoice, which is the normal way
        // a club that booked three nights gets one bill.
        selectableGigs.clear();
        selected.clear();
        if (customer != null) {
            for (Gig other : gigs.billableFor(customer.id)) {
                if (other.id == gig.id) continue;
                selectableGigs.add(other);
                selected.add(Boolean.FALSE);
            }
        }
        rebuildInvoice();
        return true;
    }

    private void rebuildInvoice() {
        List<Gig> chosen = new ArrayList<Gig>(billedGigs);
        for (int i = 0; i < selectableGigs.size(); i++) {
            if (selected.get(i).booleanValue()) chosen.add(selectableGigs.get(i));
        }
        // Correcting in place keeps the original issue date: it is the same document, and moving
        // the date would move the payment deadline with it. A replacement under a new number is a
        // new document and is dated today, which restarts the payment period -- as it should,
        // since this is the bill the customer will actually pay against.
        boolean inPlace = replacing != null && !withNewNumber;
        invoice = InvoiceBuilder.build(issuer, customer, chosen,
                inPlace ? replacing.issueDate : Dates.today(), serviceNames());
        if (replacing == null) {
            invoice.number = draftNumber();
        } else if (withNewNumber) {
            invoice.supersede(replacing);
            invoice.note = InvoiceBuilder.correctionNote(replacing.number, replacing.issueDate,
                    invoice.language);
            invoice.number = draftNumber();
        } else {
            // An in-place correction is the same document, so its number is not up for discussion.
            invoice.takeIdentityFrom(replacing);
        }
    }

    /**
     * The number the draft is showing: what the user typed, or the next in the series.
     *
     * <p>Held separately from the field because ticking a gig rebuilds the whole form, and a
     * number typed for a mid-year switch must not quietly revert to the series when the invoice
     * grows a second night.
     */
    /**
     * Every service name by id, including retired ones.
     *
     * <p>Read fresh each rebuild rather than cached: a job billed here may name a service that was
     * archived long ago, and the invoice has to say what was actually done.
     */
    private Map<Long, String> serviceNames() {
        Map<Long, String> names = new HashMap<Long, String>();
        for (Service service : new ServiceDao(this).all(true)) {
            names.put(Long.valueOf(service.id), service.displayName());
        }
        return names;
    }

    /**
     * Puts the carried answers back over the freshly built draft.
     *
     * <p>Only when the ticks still line up with the gigs. Nothing can have billed one of them
     * while this screen was being recreated, so the lengths should always match; if they somehow
     * do not, the indices mean something else now and applying them would tick the wrong nights.
     *
     * <p>Nothing at all for an invoice that has been issued: it is a record being looked at rather
     * than a draft being decided, and rebuilding it here would replace the document on screen with
     * an empty one built from the gigs a draft would have had.
     */
    private void restoreUnsaved() {
        if (issued) return;
        Unsaved carried = (Unsaved) getLastNonConfigurationInstance();
        if (carried == null) return;
        numberOverride = carried.numberOverride;
        if (carried.selected.size() == selected.size()) {
            for (int i = 0; i < selected.size(); i++) {
                selected.set(i, carried.selected.get(i));
            }
        }
        rebuildInvoice();
    }

    private String draftNumber() {
        return numberOverride != null ? numberOverride
                : invoices.peekNextNumber(settings.getInvoiceNumberPattern(), invoice.issueDate);
    }

    /** True while the number is still the draft's to choose. */
    private boolean numberEditable() {
        return !issued && !(replacing != null && !withNewNumber);
    }

    /** Reads the field back into {@link #numberOverride} before the form is thrown away. */
    private void captureNumber() {
        if (numberField == null) return;
        String typed = numberField.getText().toString().trim();
        numberOverride = typed.isEmpty() ? null : typed;
    }

    private void render() {
        body().removeAllViews();
        // The views are gone, so the handles on them must go too: whether the number field exists
        // is what tells the rest of this screen the number is still the draft's to choose.
        numberField = null;
        numberHint = null;
        FormBuilder f = form();

        f.add(summaryCard());

        if (!issued && !selectableGigs.isEmpty()) {
            f.section(R.string.select_gigs_to_bill);
            for (int i = 0; i < selectableGigs.size(); i++) {
                final int index = i;
                Gig g = selectableGigs.get(i);
                android.widget.CheckBox box = f.check(R.string.label_date,
                        selected.get(i).booleanValue());
                // One box per gig under a single label, so only the first could take an id
                // anyway -- and restoring it would fire the listener below, which throws this
                // whole form away while the framework is still walking it. The list above is
                // where these ticks live, and restoreUnsaved is what carries them.
                box.setSaveEnabled(false);
                box.setText(getString(R.string.two_part_label,
                        Dates.forLanguage(g.date, uiLanguage()),
                        Ui.money(g.totalNetCents())));
                box.setOnCheckedChangeListener(
                        new android.widget.CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(android.widget.CompoundButton v,
                                                         boolean checked) {
                                selected.set(index, Boolean.valueOf(checked));
                                captureNumber();
                                rebuildInvoice();
                                render();
                            }
                        });
            }
        }

        f.add(problemsCard());

        if (!issued) {
            if (numberEditable()) {
                numberField = f.field(R.string.label_invoice_number, invoice.number, false);
                numberHint = f.caption("");
                watchNumber();
                updateNumberHint();
            }

            formatSpinner = f.spinner(R.string.label_format, formatLabels(),
                    settings.getOutputFormat().ordinal(), false);
            f.caption(getString(R.string.two_part_label, getString(R.string.label_filename),
                    new InvoiceWriter(this).baseName(issuer, customer, invoice,
                            settings.getOutputFormat())));
            final int label = replacing == null
                    ? (hasErrors() ? R.string.action_create_anyway : R.string.action_create)
                    : withNewNumber ? R.string.action_issue_correction
                            : R.string.action_overwrite;
            f.primaryButton(label, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (replacing == null) {
                        issue();
                        return;
                    }
                    // Both kinds of replacement affect a document that may be in someone else's
                    // hands, so both confirm, naming the invoice concerned.
                    int message = withNewNumber ? R.string.confirm_supersede_invoice
                            : R.string.confirm_overwrite_invoice;
                    Ui.confirm(InvoiceActivity.this,
                            getString(message, replacing.number), label, new Runnable() {
                                @Override
                                public void run() {
                                    issue();
                                }
                            });
                }
            });
        } else {
            f.section(R.string.action_share);
            // Only offered when there are gigs to rebuild from: a correction recomputes the
            // lines from them, so without any there is nothing to recompute.
            if (!gigs.forInvoice(invoice.id).isEmpty()) {
                f.secondaryButton(R.string.action_recreate, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        askHowToRecreate(InvoiceActivity.this, invoice.id, invoice.number, true);
                    }
                });
            }
            f.primaryButton(R.string.action_share, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    share();
                }
            });
            f.secondaryButton(R.string.action_save_to_folder, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveToFolder();
                }
            });
        }
    }

    private View summaryCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_accent);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        // While the invoice is still a draft the number is editable below, so showing it here too
        // would be two versions of the same field with one of them stale.
        if (!numberEditable()) {
            card.addView(line(getString(R.string.label_invoice_number), invoice.number, false));
        }
        card.addView(line(getString(R.string.label_issue_date),
                Dates.forLanguage(invoice.issueDate, uiLanguage()), false));
        if (invoice.dueDate != null) {
            card.addView(line(getString(R.string.label_due_date),
                    Dates.forLanguage(invoice.dueDate, uiLanguage()), false));
        }
        card.addView(line(getString(R.string.label_customer),
                customer == null ? "-" : customer.displayName(), false));
        if (invoice.replacesNumber != null) {
            card.addView(line(getString(R.string.label_replaces), invoice.replacesNumber, false));
        }
        String replacedBy = issued ? invoices.replacementNumberOf(invoice.id) : null;
        if (replacedBy != null) {
            card.addView(line(getString(R.string.label_replaced_by), replacedBy, false));
        }

        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));

        card.addView(line(getString(R.string.totals_net), Ui.money(invoice.taxBasisCents), false));
        if (invoice.taxTotalCents != 0L) {
            card.addView(line(getString(R.string.totals_vat), Ui.money(invoice.taxTotalCents),
                    false));
        }
        card.addView(line(getString(R.string.totals_total), Ui.money(invoice.grandTotalCents),
                true));
        return card;
    }

    /** The "N fields still missing" panel, with the BT code on every line. */
    private View problemsCard() {
        List<Problem> problems = validate();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        int errors = 0;
        for (Problem p : problems) {
            if (p.isError()) errors++;
        }

        TextView heading = new TextView(this);
        heading.setTextSize(15f);
        heading.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        if (problems.isEmpty()) {
            heading.setText(R.string.no_problems);
            heading.setTextColor(getColor(R.color.accent));
        } else {
            heading.setText(getResources().getQuantityString(R.plurals.fields_missing,
                    problems.size(), problems.size()));
            heading.setTextColor(getColor(errors > 0 ? R.color.error : R.color.warning));
        }
        card.addView(heading);

        for (Problem problem : problems) {
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.bullet_item,
                    ProblemLabels.describe(this, problem)));
            tv.setTextSize(13f);
            tv.setTextColor(getColor(problem.isError() ? R.color.text_primary
                    : R.color.text_secondary));
            tv.setPadding(0, dp(4), 0, 0);
            card.addView(tv);
        }

        // Saying what the check is worth matters: this is a rule subset, not a real validator, and
        // presenting it as proof of validity would be a lie the user could get fined for.
        TextView caveat = new TextView(this);
        caveat.setText(R.string.validation_caveat);
        caveat.setTextSize(11f);
        caveat.setTextColor(getColor(R.color.text_secondary));
        caveat.setPadding(0, dp(10), 0, 0);
        card.addView(caveat);
        return card;
    }

    private List<Problem> validate() {
        OutputFormat format = settings.getOutputFormat();
        Profile profile = format.profile == null ? Profile.XRECHNUNG_30 : format.profile;
        EnInvoice en = InvoiceMapper.toEnInvoice(issuer, customer, invoice);
        return EnValidator.validate(en, profile);
    }

    private boolean hasErrors() {
        for (Problem p : validate()) {
            if (p.isError()) return true;
        }
        return false;
    }

    /**
     * Keeps the hint under the number field honest about what the series will do next.
     *
     * <p>Editing the number is how a mid-year switch is made, and the whole question the user has
     * at that moment is whether the app has understood the number well enough to carry on from
     * it. Saying so before the invoice is issued is worth more than reporting it afterwards.
     */
    private void watchNumber() {
        numberField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateNumberHint();
            }
        });
    }

    private void updateNumberHint() {
        String pattern = settings.getInvoiceNumberPattern();
        String typed = numberField.getText().toString().trim();
        if (typed.isEmpty()) {
            numberHint.setText(getString(R.string.invoice_number_auto,
                    invoices.peekNextNumber(pattern, invoice.issueDate)));
            return;
        }
        int sequence = PatternFormatter.extractSequence(pattern, typed);
        if (sequence < 0) {
            numberHint.setText(getString(R.string.invoice_number_off_pattern, pattern));
            return;
        }
        numberHint.setText(getString(R.string.invoice_number_then,
                new PatternFormatter().putDate(invoice.issueDate).putSequence(sequence + 1)
                        .format(pattern)));
    }

    /**
     * The number this invoice should carry, or null to let the series allocate one.
     *
     * <p>The previewed number left untouched is passed back rather than being recognised and
     * dropped, which lands in the same place: the series adopts a supplied number that sits above
     * it, so spending the number the preview showed and allocating it come to the same thing. The
     * one case where they differ -- the number having been taken meanwhile -- is refused by name
     * in {@link #issue()} instead of being papered over.
     */
    private String chosenNumber() {
        if (numberField == null) return null;
        String typed = numberField.getText().toString().trim();
        return typed.isEmpty() ? null : typed;
    }

    private void issue() {
        String chosen = chosenNumber();
        if (chosen != null && invoices.numberExists(chosen)) {
            Ui.toast(this, getString(R.string.invoice_number_taken, chosen));
            numberField.requestFocus();
            return;
        }

        OutputFormat format = OutputFormat.values()[FormBuilder.selectionOf(formatSpinner)];
        settings.setOutputFormat(format);

        List<Long> gigIds = new ArrayList<Long>();
        for (Gig g : billedGigs) gigIds.add(Long.valueOf(g.id));
        for (int i = 0; i < selectableGigs.size(); i++) {
            if (selected.get(i).booleanValue()) {
                gigIds.add(Long.valueOf(selectableGigs.get(i).id));
            }
        }

        // Snapshot before writing: the files must reflect the parties as they are now, and so must
        // any re-export years later. A correction re-snapshots for the same reason -- picking up
        // the address the first version got wrong is what it is for.
        invoice.issuerSnapshot = LexofficeContacts.issuerToJson(issuer, false);
        invoice.customerSnapshot = customer == null ? null
                : LexofficeContacts.customerToJson(customer, issuer, false);
        boolean inPlace = replacing != null && !withNewNumber;
        String superseded = withNewNumber && replacing != null ? replacing.number : null;
        if (inPlace) {
            invoices.reissue(invoice, gigIds);
        } else {
            // A new document either way, so the series advances. The gigs move to it, which is
            // what leaves the superseded invoice with its lines but no gigs of its own.
            invoice.number = chosen;
            invoices.issue(invoice, settings.getInvoiceNumberPattern(), gigIds);
        }

        try {
            // Only an in-place correction replaces files. A superseding invoice has its own id and
            // its own directory, and the document it replaces stays in the archive: it was sent,
            // so it is part of the record.
            InvoiceWriter.Result result = new InvoiceWriter(this)
                    .write(issuer, customer, invoice, format, inPlace);
            lastFiles = result.files;
            issued = true;
            replacing = null;
            withNewNumber = false;
            numberOverride = null;
            setScreenTitle(invoice.number);
            render();
            Ui.toast(this, superseded != null
                    ? getString(R.string.invoice_supersedes, invoice.number, superseded)
                    : getString(inPlace ? R.string.invoice_recreated : R.string.invoice_created,
                            invoice.number));
            if (result.hybridDegraded) {
                Ui.toast(this, getString(R.string.hybrid_degraded));
            }
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

    private List<File> filesToShare() {
        if (!lastFiles.isEmpty()) return lastFiles;
        List<File> out = new ArrayList<File>();
        File dir = new InvoiceWriter(this).archiveDir(invoice.id);
        File[] listed = dir.listFiles();
        if (listed != null) {
            for (File f : listed) out.add(f);
        }
        return out;
    }

    private void share() {
        List<File> files = filesToShare();
        if (files.isEmpty()) {
            Ui.toast(this, R.string.nothing_to_export);
            return;
        }
        try {
            startActivity(Sharing.share(this, files,
                    customer == null ? null : customer.email,
                    mailWording(customer == null ? null : customer.shareSubject,
                            settings.getMailSubject(), R.string.mail_subject),
                    mailWording(customer == null ? null : customer.shareMessage,
                            settings.getMailBody(), R.string.mail_body),
                    getString(R.string.action_share)));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

    /**
     * Expands the subject or message: this customer's own wording, else the global setting, else
     * the wording for the app's language.
     *
     * <p>Through {@link PatternFormatter} rather than {@code getString} with positional arguments,
     * which is what these strings used to be: text the user can edit must never reach
     * {@code String.format}, where a single stray {@code %} in a sentence is a crash. The token
     * form is the one already used for file names and invoice numbers, and it leaves anything it
     * does not recognise visible instead of throwing.
     */
    private String mailWording(String perCustomer, String global, int defaultRes) {
        String pattern = firstWritten(perCustomer, global, getString(defaultRes));
        return new PatternFormatter()
                .put(PatternFormatter.INVOICE_NO, invoice.number)
                .put(PatternFormatter.ISSUER_NAME, issuer.name)
                .put(PatternFormatter.CUSTOMER_NAME, customer == null ? "" : customer.displayName())
                .put(PatternFormatter.PLACE, customer == null ? "" : customer.placeName)
                .put(PatternFormatter.CITY, customer == null ? "" : customer.city)
                .putDate(invoice.issueDate)
                .format(pattern);
    }

    private static String firstWritten(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) return candidate;
        }
        return "";
    }

    private void saveToFolder() {
        String tree = settings.getExportTreeUri();
        if (tree == null) {
            Ui.toast(this, R.string.no_export_folder);
            startActivityForResult(SafExporter.pickFolderIntent(), REQUEST_PICK_FOLDER);
            return;
        }
        int written = 0;
        try {
            for (File file : filesToShare()) {
                if (SafExporter.copyInto(this, tree, file) != null) written++;
            }
            if (written == 0) {
                Ui.toast(this, R.string.nothing_to_export);
                return;
            }
            // The folder name is what the user recognises; the tree URI is not readable.
            String folder = SafExporter.displayName(this, tree);
            offerToOpen(folder == null ? getString(R.string.saved_to_folder_unnamed)
                    : getString(R.string.saved_to_folder, folder));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

    /**
     * Reports the save, and offers to open the PDF when the chosen format produced one.
     *
     * <p>A dialog rather than the toast this replaces, because the obvious next thing after saving
     * an invoice is looking at it, and a toast cannot be acted on. An XML-only format falls back
     * to the toast: there is no viewer worth offering for an XRechnung.
     */
    private void offerToOpen(String message) {
        final File pdf = firstPdf();
        if (pdf == null) {
            Ui.toast(this, message);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton(R.string.action_close, null)
                .setPositiveButton(R.string.action_open_pdf, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openPdf(pdf);
                    }
                })
                .show();
    }

    private File firstPdf() {
        for (File f : filesToShare()) {
            if (f.getName().toLowerCase(Locale.US).endsWith(".pdf")) return f;
        }
        return null;
    }

    /**
     * Opens the archive's copy through this app's own provider, not the document just written to
     * the chosen folder.
     *
     * <p>Both are the same bytes, and this app can grant a read on what it serves itself.
     * Re-delegating the read it holds on the picked tree would put the outcome in the hands of
     * whichever provider backs that folder, which for a cloud one need not be a local file at all.
     */
    private void openPdf(File pdf) {
        try {
            startActivity(Sharing.view(this, pdf));
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, R.string.no_pdf_viewer);
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FOLDER && data != null && data.getData() != null) {
            SafExporter.persistPermission(this, data.getData());
            settings.setExportTreeUri(data.getData().toString());
            saveToFolder();
        }
    }

    private String[] formatLabels() {
        OutputFormat[] formats = OutputFormat.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            switch (formats[i]) {
                case ZUGFERD_EN16931: labels[i] = getString(R.string.format_zugferd_en16931); break;
                case XRECHNUNG_UBL: labels[i] = getString(R.string.format_xrechnung_ubl); break;
                case XRECHNUNG_CII: labels[i] = getString(R.string.format_xrechnung_cii); break;
                case XRECHNUNG_23_UBL:
                    labels[i] = getString(R.string.format_xrechnung_23_ubl);
                    break;
                case PDF_ONLY: labels[i] = getString(R.string.format_pdf_only); break;
                case MAX_COMPAT: labels[i] = getString(R.string.format_max_compat); break;
                case ZUGFERD_XRECHNUNG:
                default: labels[i] = getString(R.string.format_zugferd_xrechnung);
            }
        }
        return labels;
    }

    private View line(String label, String value, boolean emphasised) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(emphasised ? 16f : 14f);
        left.setTextColor(getColor(R.color.text_secondary));
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView right = new TextView(this);
        right.setText(value == null ? "-" : value);
        right.setTextSize(emphasised ? 17f : 14f);
        right.setGravity(Gravity.END);
        right.setTextColor(getColor(emphasised ? R.color.accent : R.color.text_primary));
        if (emphasised) right.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(right, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(3);
        row.setLayoutParams(lp);
        return row;
    }

    private String uiLanguage() {
        return getResources().getConfiguration().getLocales().get(0).getLanguage();
    }
}
