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

    public static Intent createIntent(Context ctx, String isoDate) {
        return new Intent(ctx, GigEditActivity.class).putExtra(EXTRA_DATE, isoDate);
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
    private Spinner taxSpinner;
    private Spinner statusSpinner;
    private EditText notesField;
    private TextView midnightHint;

    private static final int REQUEST_NEW_CUSTOMER = 1;

    private final List<Customer> customerChoices = new ArrayList<Customer>();

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

        dateField = f.pickerField(R.string.label_date, displayDate(), true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickDate();
                    }
                });
        // Start and end are two halves of one answer, and both are short enough to share a line.
        FormBuilder.Row times = f.row();
        startField = times.left.pickerField(R.string.label_start,
                Ui.timeOfDay(gig.startMillis), false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickTime(true);
                    }
                });
        endField = times.right.pickerField(R.string.label_end, Ui.timeOfDay(gig.endMillis), false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickTime(false);
                    }
                });
        midnightHint = f.caption("");
        updateMidnightHint();

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

        int taxIndex = FormBuilder.selectionOf(taxSpinner);
        gig.taxMode = taxIndex == 0 ? null : TaxMode.values()[taxIndex - 1];
        gig.status = Gig.Status.values()[FormBuilder.selectionOf(statusSpinner)];

        gigs.save(gig);

        // Mirroring is best-effort: a gig is still a gig if the calendar write fails.
        long calendarId = settings.getCalendarId();
        if (calendarId > 0L) {
            Customer customer = customers.byId(gig.customerId);
            new CalendarMirror(this).upsert(gig, customer == null ? null : customer.displayName(),
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
