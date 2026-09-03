package com.shaforostoff.rechnungsplaner.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.calendar.CalendarMirror;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.OutputFormat;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.exchange.GigTextExporter;
import com.shaforostoff.rechnungsplaner.output.SafExporter;
import com.shaforostoff.rechnungsplaner.util.Dates;
import com.shaforostoff.rechnungsplaner.util.PatternFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Output, naming, calendar and language preferences.
 *
 * <p>There is no Save button: every field is written back in {@link #onPause()}. A tab screen has
 * nothing behind it in the task, so a Save that finished the screen closed the app instead of
 * returning anywhere -- and a preferences screen that keeps what was typed is the platform habit
 * anyway. The title action goes to import/export, which is the one thing reached from here often
 * enough to be worth a shortcut.
 */
public class SettingsActivity extends BaseActivity {

    private static final int PREVIEW_FILE_NAME = 0;
    private static final int PREVIEW_INVOICE_NUMBER = 1;
    private static final int PREVIEW_CUSTOMER_NUMBER = 2;

    private static final int REQUEST_PICK_FOLDER = 71;
    private static final int REQUEST_CALENDAR_PERMISSION = 72;

    private static final String[] UI_LANGUAGE_TAGS = {SettingsStore.LANGUAGE_SYSTEM, "en", "de",
            "es"};
    private static final String[] TOUR_FORMATS = {GigTextExporter.FORMAT_ISO,
            GigTextExporter.FORMAT_GERMAN, GigTextExporter.FORMAT_SHORT};

    private SettingsStore settings;
    private int nextCustomerSequence;
    private Spinner formatSpinner;
    private EditText fileNameField;
    private TextView fileNamePreview;
    private EditText numberField;
    private TextView numberPreview;
    private EditText customerNumberField;
    private TextView customerNumberPreview;
    private EditText mailSubjectField;
    private EditText mailBodyField;
    private TextView calendarField;
    private TextView folderField;
    private Spinner tourFormatSpinner;
    private Spinner languageSpinner;
    private CheckBox strictBox;

    @Override
    protected int bottomTab() {
        return TAB_SETTINGS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        // Read once: the preview is refreshed on every keystroke and the series cannot move
        // while this screen is open.
        nextCustomerSequence = new CustomerDao(this).peekNextSequence();
        setScreenTitle(R.string.tab_settings);
        addTitleAction(R.string.title_import_export, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ExportActivity.class));
            }
        });

        FormBuilder f = form();

        formatSpinner = f.spinner(R.string.setting_output_format, formatLabels(),
                settings.getOutputFormat().ordinal(), false);

        fileNameField = f.field(R.string.setting_filename_pattern, settings.getFileNamePattern(),
                false);
        fileNamePreview = f.caption("");
        f.caption(getString(R.string.tokens_legend, tokenLegend()));
        watch(fileNameField, fileNamePreview, PREVIEW_FILE_NAME);

        numberField = f.field(R.string.setting_number_pattern,
                settings.getInvoiceNumberPattern(), false);
        numberPreview = f.caption("");
        watch(numberField, numberPreview, PREVIEW_INVOICE_NUMBER);

        customerNumberField = f.field(R.string.setting_customer_number_pattern,
                settings.getCustomerNumberPattern(), false);
        customerNumberPreview = f.caption("");
        f.caption(getString(R.string.setting_customer_number_desc));
        watch(customerNumberField, customerNumberPreview, PREVIEW_CUSTOMER_NUMBER);

        mailSubjectField = f.field(R.string.setting_mail_subject, mailSubject(), false);
        mailBodyField = f.multiline(R.string.setting_mail_body, mailBody());
        f.caption(getString(R.string.setting_mail_desc));

        calendarField = f.pickerField(R.string.setting_calendar, calendarLabel(), false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickCalendar();
                    }
                });
        f.caption(getString(R.string.calendar_google_hint));

        folderField = f.pickerField(R.string.setting_export_folder, folderLabel(), false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivityForResult(SafExporter.pickFolderIntent(),
                                REQUEST_PICK_FOLDER);
                    }
                });

        tourFormatSpinner = f.spinner(R.string.setting_tour_date_format,
                new String[]{"2026-09-12", "12.09.2026", "12.09."},
                indexOf(TOUR_FORMATS, settings.getTourDateFormat()), false);

        languageSpinner = f.spinner(R.string.setting_ui_language,
                new String[]{getString(R.string.language_system), "English", "Deutsch", "Espanol"},
                indexOf(UI_LANGUAGE_TAGS, settings.getUiLanguage()), false);
        watchLanguage();

        strictBox = f.check(R.string.setting_strict_lexoffice,
                settings.isStrictLexofficeExport());
        f.caption(getString(R.string.setting_strict_lexoffice_desc));

        updatePreviews();
    }

    /**
     * The one setting that has to take effect before the screen is left, since its whole effect is
     * on this screen's own labels.
     *
     * <p>Comparing against the stored value also swallows the callback a spinner fires for its
     * initial selection, which would otherwise recreate the activity on every open.
     */
    private void watchLanguage() {
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String chosen = UI_LANGUAGE_TAGS[position];
                if (chosen.equals(settings.getUiLanguage())) return;
                settings.setUiLanguage(chosen);
                // attachBaseContext reads the setting, so the screen has to be rebuilt to show it.
                recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void watch(final EditText field, final TextView preview, final int kind) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                preview.setText(previewOf(kind, s.toString()));
            }
        });
    }

    private void updatePreviews() {
        fileNamePreview.setText(previewOf(PREVIEW_FILE_NAME,
                fileNameField.getText().toString()));
        numberPreview.setText(previewOf(PREVIEW_INVOICE_NUMBER,
                numberField.getText().toString()));
        customerNumberPreview.setText(previewOf(PREVIEW_CUSTOMER_NUMBER,
                customerNumberField.getText().toString()));
    }

    /**
     * What one of the three pattern fields would produce as it stands.
     *
     * <p>An empty customer-number pattern is not an empty preview but a statement: that field
     * being blank is how automatic numbering is switched off, so it has to read as a setting
     * rather than as something half-typed.
     */
    private String previewOf(int kind, String pattern) {
        if (kind == PREVIEW_CUSTOMER_NUMBER) {
            if (pattern.trim().isEmpty()) return getString(R.string.customer_number_manual);
            return getString(R.string.pattern_preview,
                    CustomerDao.formatNumber(pattern, nextCustomerSequence));
        }
        return getString(R.string.pattern_preview, kind == PREVIEW_INVOICE_NUMBER
                ? samples().format(pattern) : samples().formatFileName(pattern) + ".pdf");
    }

    /** A live preview built from plausible values, so the effect of a pattern is visible at once. */
    private PatternFormatter samples() {
        return new PatternFormatter()
                .put(PatternFormatter.ISSUER_NAME, "Nick Shaforostov")
                .put(PatternFormatter.CUSTOMER_NAME, "Club Muster GmbH")
                .put(PatternFormatter.PLACE, "Muster Club")
                .put(PatternFormatter.CITY, "Hamburg")
                .put(PatternFormatter.INVOICE_NO, "2026-001")
                .put(PatternFormatter.FORMAT, "zugferd")
                .putSequence(1)
                .putDate(Dates.today())
                .putGigDate(Dates.today());
    }

    private String tokenLegend() {
        StringBuilder sb = new StringBuilder();
        for (String token : PatternFormatter.TOKENS) {
            if (sb.length() > 0) sb.append("  ");
            sb.append('%').append(token).append('%');
        }
        return sb.toString();
    }

    private void pickCalendar() {
        final CalendarMirror mirror = new CalendarMirror(this);
        if (!mirror.hasPermission()) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR}, REQUEST_CALENDAR_PERMISSION);
            return;
        }
        final List<CalendarMirror.CalendarInfo> calendars = mirror.writableCalendars();
        final String[] labels = new String[calendars.size() + 1];
        labels[0] = getString(R.string.setting_calendar_off);
        for (int i = 0; i < calendars.size(); i++) labels[i + 1] = calendars.get(i).toString();

        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_calendar)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setCalendarId(which == 0 ? -1L : calendars.get(which - 1).id);
                        calendarField.setText(calendarLabel());
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALENDAR_PERMISSION) return;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) granted = false;
        }
        if (granted) {
            pickCalendar();
        } else {
            Ui.toast(this, R.string.calendar_permission_denied);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FOLDER && data != null && data.getData() != null) {
            SafExporter.persistPermission(this, data.getData());
            settings.setExportTreeUri(data.getData().toString());
            folderField.setText(folderLabel());
        }
    }

    /**
     * Leaving the screen -- by back, by a tab, by the import/export action, or by the language
     * change recreating it -- is what commits the fields.
     *
     * <p>The calendar and the export folder are absent because their pickers write immediately:
     * both hold a value this screen only displays and never edits.
     */
    @Override
    protected void onPause() {
        super.onPause();
        settings.setOutputFormat(OutputFormat.values()[FormBuilder.selectionOf(formatSpinner)]);
        settings.setFileNamePattern(fileNameField.getText().toString());
        settings.setInvoiceNumberPattern(numberField.getText().toString());
        settings.setCustomerNumberPattern(customerNumberField.getText().toString());
        saveMailWording();
        settings.setTourDateFormat(TOUR_FORMATS[FormBuilder.selectionOf(tourFormatSpinner)]);
        settings.setStrictLexofficeExport(strictBox.isChecked());
        settings.setUiLanguage(UI_LANGUAGE_TAGS[FormBuilder.selectionOf(languageSpinner)]);
    }

    /**
     * The wording the invoice would actually be shared with: the user's own, or the default for
     * the app's language.
     *
     * <p>Showing the resolved text rather than an empty box is the point -- it is a paragraph
     * addressed to a customer, and nobody should have to retype it from scratch to change one
     * word of it.
     */
    private String mailSubject() {
        String own = settings.getMailSubject();
        return own.isEmpty() ? getString(R.string.mail_subject) : own;
    }

    private String mailBody() {
        String own = settings.getMailBody();
        return own.isEmpty() ? getString(R.string.mail_body) : own;
    }

    /**
     * Stores the wording only once it differs from the default.
     *
     * <p>Otherwise merely opening this screen would pin the current language's text forever, and
     * a later switch of app language would go on sending German to a Spanish booker.
     */
    private void saveMailWording() {
        String subject = mailSubjectField.getText().toString();
        settings.setMailSubject(subject.trim().equals(getString(R.string.mail_subject).trim())
                ? "" : subject);
        String body = mailBodyField.getText().toString();
        settings.setMailBody(body.trim().equals(getString(R.string.mail_body).trim())
                ? "" : body);
    }

    private String calendarLabel() {
        long id = settings.getCalendarId();
        if (id <= 0L) return getString(R.string.setting_calendar_off);
        for (CalendarMirror.CalendarInfo info : new CalendarMirror(this).writableCalendars()) {
            if (info.id == id) return info.toString();
        }
        return getString(R.string.setting_calendar_off);
    }

    private String folderLabel() {
        String name = SafExporter.displayName(this, settings.getExportTreeUri());
        return name == null ? getString(R.string.not_set) : name;
    }

    private String[] formatLabels() {
        OutputFormat[] formats = OutputFormat.values();
        List<String> labels = new ArrayList<String>(formats.length);
        for (OutputFormat format : formats) {
            switch (format) {
                case ZUGFERD_EN16931:
                    labels.add(getString(R.string.format_zugferd_en16931));
                    break;
                case XRECHNUNG_UBL: labels.add(getString(R.string.format_xrechnung_ubl)); break;
                case XRECHNUNG_CII: labels.add(getString(R.string.format_xrechnung_cii)); break;
                case XRECHNUNG_23_UBL:
                    labels.add(getString(R.string.format_xrechnung_23_ubl));
                    break;
                case PDF_ONLY: labels.add(getString(R.string.format_pdf_only)); break;
                case MAX_COMPAT: labels.add(getString(R.string.format_max_compat)); break;
                case ZUGFERD_XRECHNUNG:
                default: labels.add(getString(R.string.format_zugferd_xrechnung));
            }
        }
        return labels.toArray(new String[0]);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }
}
