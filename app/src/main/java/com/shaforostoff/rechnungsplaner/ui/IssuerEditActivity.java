package com.shaforostoff.rechnungsplaner.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Issuer;
import com.shaforostoff.rechnungsplaner.data.IssuerDao;
import com.shaforostoff.rechnungsplaner.data.TaxMode;

/**
 * The user's own details, which every invoice is built from.
 *
 * <p>Modelled on the reference app's first wizard step, including the Kleinunternehmer checkbox at
 * the top: it decides whether a tax number or a VAT id is the one that matters, so it belongs above
 * both rather than buried among them.
 */
public class IssuerEditActivity extends BaseActivity {

    private static final String[] LANGUAGE_TAGS = {"de", "en", "es"};

    private IssuerDao issuers;
    private Issuer issuer;

    private CheckBox smallBusinessBox;
    private EditText nameField;
    private EditText streetField;
    private EditText postcodeField;
    private EditText cityField;
    private EditText countryField;
    private EditText contactField;
    private EditText phoneField;
    private EditText emailField;
    private EditText taxNumberField;
    private EditText vatIdField;
    private EditText ibanField;
    private EditText bicField;
    private EditText accountHolderField;
    private Spinner taxSpinner;
    private EditText exemptionField;
    private EditText dueDaysField;
    private EditText paymentTermsField;
    private Spinner languageSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        issuers = new IssuerDao(this);
        issuer = issuers.load();

        setScreenTitle(R.string.title_issuer);
        addTitleAction(R.string.action_save, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        FormBuilder f = form();
        f.requiredLegend();

        smallBusinessBox = f.check(R.string.taxmode_kleinunternehmer,
                issuer.defaultTaxMode == TaxMode.KLEINUNTERNEHMER);

        nameField = f.field(R.string.label_name, issuer.name, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        streetField = f.field(R.string.label_street, issuer.street, true);
        postcodeField = f.field(R.string.label_postcode, issuer.postcode, true,
                InputType.TYPE_CLASS_TEXT);
        cityField = f.field(R.string.label_city, issuer.city, true);
        countryField = f.field(R.string.label_country, issuer.countryCode, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        // BR-DE-6 to BR-DE-8 make all three of these mandatory, which surprises people.
        contactField = f.field(R.string.label_contact_name, issuer.contactName, true);
        phoneField = f.field(R.string.label_phone, issuer.phone, true, InputType.TYPE_CLASS_PHONE);
        emailField = f.field(R.string.label_email, issuer.email, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        taxNumberField = f.field(R.string.label_tax_number, issuer.taxNumber, true,
                InputType.TYPE_CLASS_TEXT);
        vatIdField = f.field(R.string.label_vat_id, issuer.vatId, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        f.caption(getString(R.string.field_seller_tax_id));

        ibanField = f.field(R.string.label_iban, issuer.iban, true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        bicField = f.field(R.string.label_bic, issuer.bic, false,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        accountHolderField = f.field(R.string.label_account_holder, issuer.accountHolder, false);

        taxSpinner = f.spinner(R.string.label_default_tax_mode, TaxModeLabels.modes(this),
                issuer.defaultTaxMode == null ? 0 : issuer.defaultTaxMode.ordinal(), false);
        exemptionField = f.field(R.string.label_exemption_text, issuer.exemptionText, false,
                InputType.TYPE_CLASS_TEXT, getString(R.string.hint_exemption_text));
        dueDaysField = f.field(R.string.label_due_days, Integer.toString(issuer.defaultDueDays),
                false, InputType.TYPE_CLASS_NUMBER);
        paymentTermsField = f.field(R.string.label_payment_terms, issuer.paymentTermsText, false);
        languageSpinner = f.spinner(R.string.label_default_invoice_language,
                new String[]{"Deutsch", "English", "Espanol"},
                languageIndex(issuer.defaultInvoiceLanguage), false);
    }

    private void save() {
        issuer.name = text(nameField);
        issuer.street = text(streetField);
        issuer.postcode = text(postcodeField);
        issuer.city = text(cityField);
        String country = text(countryField);
        issuer.countryCode = country.isEmpty() ? "DE" : country;
        issuer.contactName = text(contactField);
        issuer.phone = text(phoneField);
        issuer.email = text(emailField);
        issuer.taxNumber = text(taxNumberField);
        issuer.vatId = text(vatIdField);
        issuer.iban = text(ibanField);
        issuer.bic = text(bicField);
        issuer.accountHolder = text(accountHolderField);

        // The checkbox and the spinner say the same thing; the checkbox is the one people notice,
        // so it wins when it is ticked.
        TaxMode chosen = TaxMode.values()[FormBuilder.selectionOf(taxSpinner)];
        issuer.defaultTaxMode = smallBusinessBox.isChecked() ? TaxMode.KLEINUNTERNEHMER : chosen;

        issuer.exemptionText = text(exemptionField);
        issuer.defaultDueDays = parseInt(text(dueDaysField), 30);
        issuer.paymentTermsText = text(paymentTermsField);
        issuer.defaultInvoiceLanguage = LANGUAGE_TAGS[FormBuilder.selectionOf(languageSpinner)];

        issuers.save(issuer);
        finish();
    }

    private static int languageIndex(String tag) {
        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equals(tag)) return i;
        }
        return 0;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String text(EditText field) {
        return field.getText().toString().trim();
    }
}
