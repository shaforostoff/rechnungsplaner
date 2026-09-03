package com.shaforostoff.rechnungsplaner.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.calendar.CalendarMirror;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.Invoice;
import com.shaforostoff.rechnungsplaner.data.InvoiceDao;
import com.shaforostoff.rechnungsplaner.data.IssuerDao;
import com.shaforostoff.rechnungsplaner.data.Service;
import com.shaforostoff.rechnungsplaner.data.ServiceDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.data.TaxMode;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Creates and edits one DJ-set. */
public class GigEditActivity extends BaseActivity {

    private static final String EXTRA_GIG_ID = "gig_id";
    private static final String EXTRA_DATE = "date";
    private static final String EXTRA_SERVICE_ID = "service_id";

    public static Intent createIntent(Context ctx, String isoDate, long serviceId) {
        return new Intent(ctx, GigEditActivity.class).putExtra(EXTRA_DATE, isoDate)
                .putExtra(EXTRA_SERVICE_ID, serviceId);
    }

    public static Intent editIntent(Context ctx, long gigId) {
        return new Intent(ctx, GigEditActivity.class).putExtra(EXTRA_GIG_ID, gigId);
    }

    private GigDao gigs;
    private IssuerDao issuers;
    private CustomerDao customers;
    private SettingsStore settings;
    private Gig gig;

    private TextView dateField;
    private TextView startField;
    private TextView endField;
    private TextView customerField;
    private EditText placeField;
    private EditText cityField;
    private EditText feeField;
    private EditText travelField;
    private Spinner serviceSpinner;
    private Spinner taxSpinner;
    private Spinner statusSpinner;
    private EditText notesField;
    private TextView midnightHint;
    private TextView endDateField;

    private static final int REQUEST_NEW_CUSTOMER = 1;

    private final List<Customer> customerChoices = new ArrayList<Customer>();
    private final List<Service> serviceChoices = new ArrayList<Service>();

    /**
     * The status this screen picked from the date, or null once the gig is the user's to decide.
     * Comparing against it is how {@link #pickDate()} knows whether it may still move the status:
     * an existing gig never gets one, so re-dating an invoiced set cannot silently unbill it.
     */
    private Gig.Status autoStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gigs = new GigDao(this);
        customers = new CustomerDao(this);
        issuers = new IssuerDao(this);
        settings = new SettingsStore(this);

        long id = getIntent().getLongExtra(EXTRA_GIG_ID, -1L);
        gig = id > 0L ? gigs.byId(id) : null;
        boolean isNew = gig == null;
        if (isNew) {
            gig = new Gig();
            String date = getIntent().getStringExtra(EXTRA_DATE);
            gig.date = Dates.isValid(date) ? date : Dates.today();
            gig.status = Gig.defaultStatusFor(gig.date);
            autoStatus = gig.status;
            gig.serviceId = getIntent().getLongExtra(EXTRA_SERVICE_ID, -1L);
        }
        setScreenTitle(isNew ? R.string.title_new_gig : R.string.title_edit_gig);
        addTitleAction(R.string.action_save, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Saving is the end of this screen's job, so hand the user back to the month view.
                // The "create invoice" button below also saves but deliberately stays, because it
                // is on its way to another screen and the gig belongs behind it in the back stack.
                if (save()) finish();
            }
        });

        buildForm(isNew);
    }

    /**
     * Picks up what another screen did to this gig while this one waited behind it.
     *
     * <p>Creating an invoice leaves this screen on the back stack, so the gig it holds still says
     * un-invoiced when the user comes back -- and it would go on offering to create an invoice
     * that already exists. The row itself is no longer at risk, since an update writes only the
     * fields this screen owns, but the buttons have to catch up.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (gig.id <= 0L) return;
        Gig fresh = gigs.byId(gig.id);
        if (fresh == null) {
            // Deleted from somewhere else, so there is nothing left for this screen to edit.
            finish();
            return;
        }
        boolean wasInvoiced = gig.isInvoiced();
        gig.invoiceId = fresh.invoiceId;
        gig.calendarId = fresh.calendarId;
        gig.calendarEventId = fresh.calendarEventId;
        gig.syncUuid = fresh.syncUuid;
        if (fresh.isInvoiced() == wasInvoiced) return;

        // Only when the invoice link actually changed, which is the one case worth losing an
        // unsaved edit over -- and in that flow the edits were saved on the way out anyway.
        gig.status = fresh.status;
        body().removeAllViews();
        buildForm(false);
    }

    private void buildForm(boolean isNew) {
        FormBuilder f = form();

        // Resolved before anything is laid out: whether this kind of work is measured in days
        // decides whether the form asks for clock times or an end date.
        serviceChoices.clear();
        serviceChoices.addAll(new ServiceDao(this).all(false));
        Service own = new ServiceDao(this).byId(gig.serviceId);
        // An archived service still on this job stays selectable, or opening the job would move
        // it to another kind of work just by being looked at.
        if (own != null && !containsService(own.id)) serviceChoices.add(own);
        if (own == null && !serviceChoices.isEmpty()) own = serviceChoices.get(0);
        final boolean multiDay = own != null && own.multiDay;

        dateField = f.pickerField(R.string.label_date, displayDate(), true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickDate();
                    }
                });
        if (multiDay) {
            // Work measured in days is asked for an end date instead of two clock times: a week
            // of it has no start hour worth recording, and the pair of pickers would be asking
            // the wrong question in a form that has no room for spare ones.
            endDateField = f.pickerField(R.string.label_end_date, displayEndDate(), false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickEndDate();
                        }
                    });
            f.caption(getString(R.string.end_date_hint));
        } else {
            // Start and end are two halves of one answer, both short enough to share a line.
            FormBuilder.Row times = f.row();
            startField = times.left.pickerField(R.string.label_start,
                    Ui.timeOfDay(gig.startMillis), false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickTime(true);
                        }
                    });
            endField = times.right.pickerField(R.string.label_end,
                    Ui.timeOfDay(gig.endMillis), false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickTime(false);
                        }
                    });
            midnightHint = f.caption("");
            updateMidnightHint();
        }

        customerField = f.pickerField(R.string.label_customer, customerLabel(), true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickCustomer();
                    }
                });

        placeField = f.field(R.string.label_place, gig.placeName, false);
        cityField = f.field(R.string.label_city, gig.city, false);
        FormBuilder.Row amounts = f.row();
        feeField = amounts.left.field(R.string.label_fee, Ui.centsToEditable(gig.feeCents), false,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        travelField = amounts.right.field(R.string.label_travel,
                Ui.centsToEditable(gig.travelCents), false,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        if (!serviceChoices.isEmpty()) {
            serviceSpinner = f.spinner(R.string.label_service, serviceLabels(),
                    serviceIndex(own == null ? gig.serviceId : own.id), false);
            watchService(multiDay);
        }

        taxSpinner = f.spinner(R.string.label_tax_mode, taxModeLabels(),
                gig.taxMode == null ? 0 : gig.taxMode.ordinal() + 1, false);
        statusSpinner = f.spinner(R.string.label_status, statusLabels(), gig.status.ordinal(),
                false);
        notesField = f.multiline(R.string.label_notes, gig.notes);

        if (!isNew) {
            if (gig.isInvoiced()) {
                f.secondaryButton(R.string.action_open_invoice, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(InvoiceActivity.openIntent(GigEditActivity.this,
                                gig.invoiceId));
                    }
                });
                f.secondaryButton(R.string.action_recreate, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Save first: correcting the fee here is one of the reasons to redo the
                        // invoice, and it would be read from the database a moment from now.
                        if (save()) {
                            InvoiceActivity.askHowToRecreate(GigEditActivity.this, gig.invoiceId,
                                    invoiceNumber(), false);
                        }
                    }
                });
            } else {
                f.primaryButton(R.string.action_create_invoice, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (save()) {
                            startActivity(InvoiceActivity.draftIntent(GigEditActivity.this,
                                    gig.id));
                        }
                    }
                });
                f.secondaryButton(R.string.action_link_invoice, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Save first, as "Recreate" does: the corrected fee is what the invoice
                        // will be rebuilt from a moment from now.
                        if (save()) pickInvoiceToLink();
                    }
                });
            }
            f.secondaryButton(R.string.action_delete, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.confirm(GigEditActivity.this, R.string.confirm_delete_gig, new Runnable() {
                        @Override
                        public void run() {
                            new CalendarMirror(GigEditActivity.this).delete(gig);
                            gigs.delete(gig.id);
                            finish();
                        }
                    });
                }
            });
        }
    }

    private boolean save() {
        if (!Dates.isValid(gig.date)) {
            Ui.toast(this, R.string.needs_date);
            return false;
        }
        gig.placeName = text(placeField);
        gig.city = text(cityField);
        gig.feeCents = Ui.editableToCents(placeholderText(feeField));
        gig.travelCents = Ui.editableToCents(placeholderText(travelField));
        gig.notes = text(notesField);

        if (serviceSpinner != null && !serviceChoices.isEmpty()) {
            gig.serviceId = serviceChoices.get(FormBuilder.selectionOf(serviceSpinner)).id;
        }

        int taxIndex = FormBuilder.selectionOf(taxSpinner);
        gig.taxMode = taxIndex == 0 ? null : TaxMode.values()[taxIndex - 1];
        gig.status = Gig.Status.values()[FormBuilder.selectionOf(statusSpinner)];

        gigs.save(gig);

        // Mirroring is best-effort: a gig is still a gig if the calendar write fails.
        Service service = new ServiceDao(this).byId(gig.serviceId);
        long calendarId = settings.getCalendarId();
        // A service can opt out. An existing event is removed rather than left behind, or turning
        // the setting off would leave the calendar showing work the app no longer mirrors.
        if (service != null && !service.syncToCalendar) {
            new CalendarMirror(this).delete(gig);
        } else if (calendarId > 0L) {
            Customer customer = customers.byId(gig.customerId);
            new CalendarMirror(this).upsert(gig, service == null ? null : service.displayName(),
                    customer == null ? null : customer.displayName(),
                    calendarId);
        }
        return true;
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        if (Dates.isValid(gig.date)) {
            c.set(Dates.year(gig.date), Dates.month(gig.date) - 1, Dates.day(gig.date));
        }
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                String previous = gig.date;
                gig.date = Dates.iso(year, month + 1, day);
                // Times are stored as instants, so moving the date has to move them too or the
                // event lands on the old day.
                gig.startMillis = shiftToDate(gig.startMillis, previous, gig.date);
                gig.endMillis = shiftToDate(gig.endMillis, previous, gig.date);
                dateField.setText(displayDate());
                updateMidnightHint();
                followDateWithStatus();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Rebuilds the form when the chosen service changes what the form should ask.
     *
     * <p>Only when the answer actually changes shape -- swapping one single-day service for
     * another leaves the fields alone. Comparing against the value the form was built with also
     * swallows the callback a spinner fires for its own initial selection.
     */
    private void watchService(final boolean builtAsMultiDay) {
        serviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Service chosen = serviceChoices.get(position);
                if (chosen.id == gig.serviceId && chosen.multiDay == builtAsMultiDay) return;
                gig.serviceId = chosen.id;
                if (chosen.multiDay == builtAsMultiDay) return;
                // Moving to the other kind drops the answers that no longer apply, rather than
                // keeping times on work measured in days or an end date on an evening's work.
                if (chosen.multiDay) {
                    gig.startMillis = 0L;
                    gig.endMillis = 0L;
                } else {
                    gig.endDate = null;
                }
                body().removeAllViews();
                buildForm(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * The end-date picker, floored at the start date.
     *
     * <p>An end before the start is not a shorter job, it is a typo, and the invoice period built
     * from it would run backwards.
     */
    private void pickEndDate() {
        String current = gig.lastDay();
        Calendar c = Calendar.getInstance();
        c.set(Dates.year(current), Dates.month(current) - 1, Dates.day(current));
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                String chosen = Dates.iso(year, month + 1, day);
                if (chosen.compareTo(gig.date) < 0) {
                    Ui.toast(GigEditActivity.this, R.string.end_before_start);
                    return;
                }
                gig.endDate = chosen.equals(gig.date) ? null : chosen;
                endDateField.setText(displayEndDate());
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String displayEndDate() {
        return Dates.forLanguage(gig.lastDay(),
                getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private void pickTime(final boolean start) {
        long current = start ? gig.startMillis : gig.endMillis;
        Calendar c = Calendar.getInstance();
        if (current > 0L) c.setTimeInMillis(current);
        else c.set(Calendar.HOUR_OF_DAY, start ? 23 : 4);

        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hour, int minute) {
                long millis = atTime(gig.date, hour, minute);
                if (start) {
                    gig.startMillis = millis;
                    startField.setText(Ui.timeOfDay(millis));
                } else {
                    // An end earlier than the start means the set runs past midnight, so the end
                    // belongs to the following day rather than being invalid.
                    if (gig.startMillis > 0L && millis <= gig.startMillis) {
                        millis += 86_400_000L;
                    }
                    gig.endMillis = millis;
                    endField.setText(Ui.timeOfDay(millis));
                }
                updateMidnightHint();
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    /**
     * Keeps the status in step with the date until the user overrules it. Moving a set from last
     * month to next month should stop calling it played, but only while the shown status is the one
     * this screen chose -- a deliberate pick is left alone.
     */
    private void followDateWithStatus() {
        if (autoStatus == null) return;
        if (FormBuilder.selectionOf(statusSpinner) != autoStatus.ordinal()) {
            autoStatus = null;
            return;
        }
        autoStatus = Gig.defaultStatusFor(gig.date);
        statusSpinner.setSelection(autoStatus.ordinal());
    }

    private void updateMidnightHint() {
        if (midnightHint == null) return;
        boolean crosses = gig.startMillis > 0L && gig.endMillis > gig.startMillis
                && !Dates.fromMillis(gig.endMillis).equals(Dates.fromMillis(gig.startMillis));
        midnightHint.setText(crosses ? getString(R.string.crosses_midnight) : "");
        midnightHint.setVisibility(crosses ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_NEW_CUSTOMER || resultCode != RESULT_OK || data == null) return;

        // Creating a customer from here was always in service of filling this field, so select it
        // rather than sending the user back through the picker for a record they just typed in.
        Customer saved = customers.byId(data.getLongExtra(CustomerEditActivity.EXTRA_SAVED_ID, -1L));
        if (saved != null) applyCustomer(saved);
    }

    private void pickCustomer() {
        customerChoices.clear();
        customerChoices.addAll(customers.all(false));

        final String[] labels = new String[customerChoices.size() + 1];
        for (int i = 0; i < customerChoices.size(); i++) {
            labels[i] = customerChoices.get(i).displayName();
        }
        labels[labels.length - 1] = getString(R.string.new_customer);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_customer)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == labels.length - 1) {
                            startActivityForResult(
                                    CustomerEditActivity.createIntent(GigEditActivity.this),
                                    REQUEST_NEW_CUSTOMER);
                            return;
                        }
                        applyCustomer(customerChoices.get(which));
                    }
                })
                .show();
    }

    /** Copies the customer's defaults into any field the user has not filled in yet. */
    private void applyCustomer(Customer customer) {
        gig.customerId = customer.id;
        customerField.setText(customer.displayName());
        if (isEmpty(placeField) && customer.placeName != null) {
            placeField.setText(customer.placeName);
        }
        if (isEmpty(cityField) && customer.city != null) cityField.setText(customer.city);
        // A different customer can mean a different inherited tax mode, and the whole point of
        // naming it in the label is that it is current.
        FormBuilder.setEntries(taxSpinner, taxModeLabels());
        if (Ui.editableToCents(placeholderText(feeField)) == 0L && customer.defaultFeeCents > 0L) {
            feeField.setText(Ui.centsToEditable(customer.defaultFeeCents));
        }
    }

    private String customerLabel() {
        Customer customer = customers.byId(gig.customerId);
        return customer == null ? getString(R.string.no_customer) : customer.displayName();
    }

    private String[] serviceLabels() {
        String[] labels = new String[serviceChoices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = serviceChoices.get(i).displayName();
        return labels;
    }

    private int serviceIndex(long id) {
        for (int i = 0; i < serviceChoices.size(); i++) {
            if (serviceChoices.get(i).id == id) return i;
        }
        return 0;
    }

    private boolean containsService(long id) {
        for (Service s : serviceChoices) {
            if (s.id == id) return true;
        }
        return false;
    }

    private String displayDate() {
        return Dates.forLanguage(gig.date,
                getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    /**
     * The mode list, whose inherit entry names what this gig would actually be taxed at. It depends
     * on the selected customer, so it is rebuilt rather than computed once.
     */
    private String[] taxModeLabels() {
        return TaxModeLabels.withInherit(this, issuers.load(), customers.byId(gig.customerId));
    }

    /** The gig's invoice number, for the dialog that offers to redo it. */
    /**
     * Points this gig at an invoice that already exists.
     *
     * <p>The way back for a gig that has come adrift from its invoice -- which the app itself used
     * to do when a gig was edited after being billed. The invoice keeps its number, and
     * "Recreate" then rebuilds its lines from the gig as it now stands, so a number already sent
     * to a customer stays the number they were sent.
     *
     * <p>Only this customer's invoices are offered. Reissuing takes the customer from the gigs it
     * bills, so linking across customers would quietly rebill someone else's invoice to this one.
     */
    private void pickInvoiceToLink() {
        final List<Invoice> choices = new InvoiceDao(this).forCustomer(gig.customerId);
        if (choices.isEmpty()) {
            Ui.toast(this, R.string.no_invoices_to_link);
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            Invoice invoice = choices.get(i);
            // The count is what identifies a stranded invoice: it bills nothing and still holds
            // its number, which is exactly the one being looked for here.
            labels[i] = getString(R.string.invoice_choice, invoice.number,
                    Dates.forLanguage(invoice.issueDate,
                            getResources().getConfiguration().getLocales().get(0).getLanguage()),
                    gigs.forInvoice(invoice.id).size());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_link_invoice)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        linkTo(choices.get(which));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void linkTo(Invoice invoice) {
        // A gig marked paid stays paid: the money arrived, and linking the document it arrived
        // against does not unmake that.
        Gig.Status status = gig.status == Gig.Status.PAID ? Gig.Status.PAID
                : Gig.Status.INVOICED;
        gigs.setInvoice(gig.id, invoice.id, status);
        gig.invoiceId = invoice.id;
        gig.status = status;
        body().removeAllViews();
        buildForm(false);
        Ui.toast(this, getString(R.string.linked_to_invoice, invoice.number));
    }

    private String invoiceNumber() {
        Invoice invoice = new InvoiceDao(this).byId(gig.invoiceId);
        return invoice == null ? "" : invoice.number;
    }

    private String[] statusLabels() {
        return new String[]{
                getString(R.string.status_planned), getString(R.string.status_played),
                getString(R.string.status_invoiced), getString(R.string.status_paid),
        };
    }

    private static long atTime(String isoDate, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Dates.year(isoDate), Dates.month(isoDate) - 1, Dates.day(isoDate), hour, minute);
        return c.getTimeInMillis();
    }

    private static long shiftToDate(long millis, String fromDate, String toDate) {
        if (millis <= 0L || !Dates.isValid(fromDate) || !Dates.isValid(toDate)) return millis;
        long delta = Dates.startOfDayMillis(toDate) - Dates.startOfDayMillis(fromDate);
        return millis + delta;
    }

    private static String text(EditText field) {
        String s = field.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String placeholderText(EditText field) {
        return field.getText().toString();
    }

    private static boolean isEmpty(EditText field) {
        return field.getText().toString().trim().isEmpty();
    }
}
