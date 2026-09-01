package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private static final int REQUEST_PICK_FOLDER = 41;

    public static Intent draftIntent(Context ctx, long gigId) {
        return new Intent(ctx, InvoiceActivity.class).putExtra(EXTRA_GIG_ID, gigId);
    }

    public static Intent openIntent(Context ctx, long invoiceId) {
        return new Intent(ctx, InvoiceActivity.class).putExtra(EXTRA_INVOICE_ID, invoiceId);
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
    private Spinner formatSpinner;
    private List<File> lastFiles = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        invoices = new InvoiceDao(this);
        gigs = new GigDao(this);
        issuer = new IssuerDao(this).load();

        long invoiceId = getIntent().getLongExtra(EXTRA_INVOICE_ID, -1L);
        if (invoiceId > 0L) {
            invoice = invoices.byId(invoiceId);
            issued = invoice != null;
        }
        if (invoice == null && !buildDraft()) {
            finish();
            return;
        }
        customer = new CustomerDao(this).byId(invoice.customerId);

        setScreenTitle(issued ? invoice.number : getString(R.string.title_invoice));
        render();
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
        invoice = InvoiceBuilder.build(issuer, customer, chosen, Dates.today());
        invoice.number = invoices.peekNextNumber(settings.getInvoiceNumberPattern(),
                invoice.issueDate);
    }

    private void render() {
        body().removeAllViews();
        FormBuilder f = form();

        f.add(summaryCard());

        if (!issued && !selectableGigs.isEmpty()) {
            f.section(R.string.select_gigs_to_bill);
            for (int i = 0; i < selectableGigs.size(); i++) {
                final int index = i;
                Gig g = selectableGigs.get(i);
                android.widget.CheckBox box = f.check(R.string.label_date,
                        selected.get(i).booleanValue());
                box.setText(getString(R.string.two_part_label,
                        Dates.forLanguage(g.date, uiLanguage()),
                        Ui.money(g.totalNetCents())));
                box.setOnCheckedChangeListener(
                        new android.widget.CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(android.widget.CompoundButton v,
                                                         boolean checked) {
                                selected.set(index, Boolean.valueOf(checked));
                                rebuildInvoice();
                                render();
                            }
                        });
            }
        }

        f.add(problemsCard());

        if (!issued) {
            formatSpinner = f.spinner(R.string.label_format, formatLabels(),
                    settings.getOutputFormat().ordinal(), false);
            f.caption(getString(R.string.two_part_label, getString(R.string.label_filename),
                    new InvoiceWriter(this).baseName(issuer, customer, invoice,
                            settings.getOutputFormat())));
            f.primaryButton(hasErrors() ? R.string.action_create_anyway : R.string.action_create,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            issue();
                        }
                    });
        } else {
            f.section(R.string.action_share);
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

        card.addView(line(getString(R.string.label_invoice_number), invoice.number, false));
        card.addView(line(getString(R.string.label_issue_date),
                Dates.forLanguage(invoice.issueDate, uiLanguage()), false));
        if (invoice.dueDate != null) {
            card.addView(line(getString(R.string.label_due_date),
                    Dates.forLanguage(invoice.dueDate, uiLanguage()), false));
        }
        card.addView(line(getString(R.string.label_customer),
                customer == null ? "-" : customer.displayName(), false));

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

    private void issue() {
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
        // any re-export years later.
        invoice.number = null;
        invoice.issuerSnapshot = LexofficeContacts.issuerToJson(issuer, false);
        invoice.customerSnapshot = customer == null ? null
                : LexofficeContacts.customerToJson(customer, false);
        invoices.issue(invoice, settings.getInvoiceNumberPattern(), gigIds);

        try {
            InvoiceWriter.Result result = new InvoiceWriter(this)
                    .write(issuer, customer, invoice, format);
            lastFiles = result.files;
            issued = true;
            setScreenTitle(invoice.number);
            render();
            Ui.toast(this, getString(R.string.invoice_created, invoice.number));
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
                    getString(R.string.mail_subject, invoice.number),
                    getString(R.string.mail_body, invoice.number, issuer.name),
                    getString(R.string.action_share)));
        } catch (IOException e) {
            Ui.toast(this, getString(R.string.import_failed, String.valueOf(e.getMessage())));
        }
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
            Ui.toast(this, written > 0 ? getString(R.string.saved_to_folder)
                    : getString(R.string.nothing_to_export));
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
