package com.shaforostoff.rechnungsplaner.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the output, naming and calendar preferences across app restarts. */
public class SettingsStore {

    private static final String PREFS = "rechnungsplaner_settings";

    /** Exactly the pattern the user asked for, mixing wrapped and bare tokens. */
    public static final String DEFAULT_FILENAME_PATTERN = "%issuername%-%Y-%M-%D";
    /** Year first, three-digit sequence: 2026-001. */
    public static final String DEFAULT_NUMBER_PATTERN = "%Y%-%seq3%";
    public static final String DEFAULT_TOUR_DATE_FORMAT = "yyyy-MM-dd";

    /** Follows the device locale. The other values force one of the three translations. */
    public static final String LANGUAGE_SYSTEM = "system";

    private static final String K_OUTPUT_FORMAT = "output_format";
    private static final String K_FILENAME_PATTERN = "filename_pattern";
    private static final String K_NUMBER_PATTERN = "invoice_number_pattern";
    private static final String K_CALENDAR_ID = "calendar_id";
    private static final String K_EXPORT_TREE_URI = "export_tree_uri";
    private static final String K_TOUR_DATE_FORMAT = "tour_date_format";
    private static final String K_UI_LANGUAGE = "ui_language";
    private static final String K_STRICT_LEXOFFICE = "strict_lexoffice_export";

    private final SharedPreferences prefs;

    public SettingsStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public OutputFormat getOutputFormat() {
        return OutputFormat.fromName(prefs.getString(K_OUTPUT_FORMAT, null),
                OutputFormat.ZUGFERD_XRECHNUNG);
    }

    public void setOutputFormat(OutputFormat format) {
        prefs.edit().putString(K_OUTPUT_FORMAT, format.name()).apply();
    }

    public String getFileNamePattern() {
        return prefs.getString(K_FILENAME_PATTERN, DEFAULT_FILENAME_PATTERN);
    }

    public void setFileNamePattern(String pattern) {
        prefs.edit().putString(K_FILENAME_PATTERN,
                isBlank(pattern) ? DEFAULT_FILENAME_PATTERN : pattern.trim()).apply();
    }

    public String getInvoiceNumberPattern() {
        return prefs.getString(K_NUMBER_PATTERN, DEFAULT_NUMBER_PATTERN);
    }

    public void setInvoiceNumberPattern(String pattern) {
        prefs.edit().putString(K_NUMBER_PATTERN,
                isBlank(pattern) ? DEFAULT_NUMBER_PATTERN : pattern.trim()).apply();
    }

    /** The device calendar gigs are mirrored into, or -1 when mirroring is off. */
    public long getCalendarId() {
        return prefs.getLong(K_CALENDAR_ID, -1L);
    }

    public void setCalendarId(long id) {
        prefs.edit().putLong(K_CALENDAR_ID, id).apply();
    }

    public boolean isCalendarMirrorEnabled() {
        return getCalendarId() > 0L;
    }

    /** Persisted SAF tree the "save" action writes into, or null when only sharing is used. */
    public String getExportTreeUri() {
        return prefs.getString(K_EXPORT_TREE_URI, null);
    }

    public void setExportTreeUri(String uri) {
        prefs.edit().putString(K_EXPORT_TREE_URI, uri).apply();
    }

    public String getTourDateFormat() {
        return prefs.getString(K_TOUR_DATE_FORMAT, DEFAULT_TOUR_DATE_FORMAT);
    }

    public void setTourDateFormat(String format) {
        prefs.edit().putString(K_TOUR_DATE_FORMAT,
                isBlank(format) ? DEFAULT_TOUR_DATE_FORMAT : format).apply();
    }

    public String getUiLanguage() {
        return prefs.getString(K_UI_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setUiLanguage(String tag) {
        prefs.edit().putString(K_UI_LANGUAGE, isBlank(tag) ? LANGUAGE_SYSTEM : tag).apply();
    }

    /** Omits the app-specific extension block from the contacts export. */
    public boolean isStrictLexofficeExport() {
        return prefs.getBoolean(K_STRICT_LEXOFFICE, false);
    }

    public void setStrictLexofficeExport(boolean strict) {
        prefs.edit().putBoolean(K_STRICT_LEXOFFICE, strict).apply();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
