package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.IssuerDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.data.TaxMode;

/**
 * Creates and edits a customer.
 *
 * <p>Only the venue name is really required. The app exists partly because billing details arrive
 * after the first gig, so the editor saves a half-filled record without complaint and the blue
 * outlines simply show what an invoice will still be missing.
 */
public class CustomerEditActivity extends BaseActivity {

    private static final String EXTRA_ID = "customer_id";

    /**
     * The id of the customer that was saved, returned to whoever started this screen for a result.
     * Set on every save, edits included, so a caller can refresh rather than having to guess.
     */
    public static final String EXTRA_SAVED_ID = "saved_customer_id";

    public static Intent createIntent(Context ctx) {
        return new Intent(ctx, CustomerEditActivity.class);
    }

    public static Intent editIntent(Context ctx, long id) {
        return new Intent(ctx, CustomerEditActivity.class).putExtra(EXTRA_ID, id);
    }

    private CustomerDao customers;
    private Customer customer;

    private EditText placeField;
    private EditText officialNameField;
    private EditText streetField;
    private EditText postcodeField;
    private EditText cityField;
    private EditText countryField;
    private EditText emailField;
    private EditText contactField;
    private EditText phoneField;
    private EditText vatIdField;
    private EditText buyerReferenceField;
    private EditText customerNumberField;
    private EditText defaultFeeField;
    private Spinner taxSpinner;
    private Spinner languageSpinner;
    private EditText shareSubjectField;
    private EditText shareMessageField;
    private EditText noteField;

    private static final String[] LANGUAGE_TAGS = {null, "de", "en", "es"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customers = new CustomerDao(this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1L);
        customer = id > 0L ? customers.byId(id) : null;
        final boolean isNew = customer == null;
        if (isNew) customer = new Customer();

        setScreenTitle(isNew ? R.string.title_new_customer : R.string.title_edit_customer);
        addTitleAction(R.string.action_save, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        FormBuilder f = form();
        f.requiredLegend();

        placeField = f.field(R.string.label_place, customer.placeName, false);
        officialNameField = f.field(R.string.label_official_name, customer.officialName, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        streetField = f.field(R.string.label_street, customer.street, true);
        postcodeField = f.field(R.string.label_postcode, customer.postcode, true,
                InputType.TYPE_CLASS_TEXT);
        cityField = f.field(R.string.label_city, customer.city, true);
        countryField = f.field(R.string.label_country, customer.countryCode, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        emailField = f.field(R.string.label_email, customer.email, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        contactField = f.field(R.string.label_contact_name, customer.contactName, false);
        phoneField = f.field(R.string.label_phone, customer.phone, false, InputType.TYPE_CLASS_PHONE);
        vatIdField = f.field(R.string.label_vat_id, customer.vatId, false,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        buyerReferenceField = f.field(R.string.label_buyer_reference, customer.buyerReference, true,
                InputType.TYPE_CLASS_TEXT, getString(R.string.hint_buyer_reference));
        customerNumberField = f.field(R.string.label_customer_number, customer.customerNumber,
                false, InputType.TYPE_CLASS_TEXT);
        defaultFeeField = f.field(R.string.label_default_fee,
                Ui.centsToEditable(customer.defaultFeeCents), false,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        // A customer inherits from the issuer only, so the label is fixed for as long as this
        // screen is open.
        taxSpinner = f.spinner(R.string.label_tax_mode,
                TaxModeLabels.withInherit(this, new IssuerDao(this).load(), null),
                customer.defaultTaxMode == null ? 0 : customer.defaultTaxMode.ordinal() + 1, false);
        languageSpinner = f.spinner(R.string.label_invoice_language, languageLabels(),
                languageIndex(customer.invoiceLanguage), false);
        shareSubjectField = f.field(R.string.label_share_subject, customer.shareSubject, false);
        shareMessageField = f.multiline(R.string.label_share_message, customer.shareMessage);
        f.caption(getString(R.string.label_share_desc));

        noteField = f.multiline(R.string.label_note, customer.note);

        if (!isNew) {
            f.secondaryButton(R.string.action_delete, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.confirm(CustomerEditActivity.this, R.string.confirm_delete_customer,
                            new Runnable() {
                                @Override
                                public void run() {
                                    boolean deleted = customers.deleteOrArchive(customer.id);
                                    if (!deleted) {
                                        Ui.toast(CustomerEditActivity.this,
                                                R.string.customer_archived_because_used);
                                    }
                                    finish();
                                }
                            });
                }
            });
        }
    }

    private void save() {
        customer.placeName = text(placeField);
        customer.officialName = text(officialNameField);
        if (customer.placeName == null && customer.officialName == null) {
            Ui.toast(this, R.string.needs_a_name);
            return;
        }
        customer.street = text(streetField);
        customer.postcode = text(postcodeField);
        customer.city = text(cityField);
        String country = text(countryField);
        customer.countryCode = country == null ? "DE" : country;
        customer.email = text(emailField);
        customer.contactName = text(contactField);
        customer.phone = text(phoneField);
        customer.vatId = text(vatIdField);
        customer.buyerReference = text(buyerReferenceField);
        customer.customerNumber = text(customerNumberField);
        Customer clash = customers.holderOfNumber(customer.customerNumber, customer.id);
        if (clash != null) {
            // Naming the holder is the useful half: a number is only a duplicate relative to
            // someone, and that someone may well be archived and not in any list on screen.
            Ui.toast(this, getString(R.string.customer_number_taken, customer.customerNumber,
                    clash.displayName()));
            customerNumberField.requestFocus();
            return;
        }
        customer.defaultFeeCents = Ui.editableToCents(defaultFeeField.getText().toString());

        int taxIndex = FormBuilder.selectionOf(taxSpinner);
        customer.defaultTaxMode = taxIndex == 0 ? null : TaxMode.values()[taxIndex - 1];
        customer.invoiceLanguage = LANGUAGE_TAGS[FormBuilder.selectionOf(languageSpinner)];
        customer.shareSubject = text(shareSubjectField);
        customer.shareMessage = text(shareMessageField);
        customer.note = text(noteField);

        customers.save(customer, new SettingsStore(this).getCustomerNumberPattern());
        // Hand the id back: a caller that opened this screen to fill a customer field can select
        // the new record instead of making the user pick it out of the list again.
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_SAVED_ID, customer.id));
        finish();
    }

    private String[] languageLabels() {
        Issuer issuer = new IssuerDao(this).load();
        return new String[]{
                getString(R.string.language_default, issuer.defaultInvoiceLanguage),
                "Deutsch", "English", "Espanol",
        };
    }

    private static int languageIndex(String tag) {
        for (int i = 1; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equals(tag)) return i;
        }
        return 0;
    }

    private static String text(EditText field) {
        String s = field.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }
}
