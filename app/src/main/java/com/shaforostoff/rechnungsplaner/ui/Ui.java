package com.shaforostoff.rechnungsplaner.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.shaforostoff.rechnungsplaner.R;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Small shared UI helpers: money in and out of text fields, toasts, confirmations. */
public final class Ui {

    private Ui() {
    }

    public static void toast(Context ctx, int messageRes) {
        Toast.makeText(ctx, messageRes, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context ctx, CharSequence message) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }

    public static void confirm(Context ctx, int messageRes, Runnable onConfirmed) {
        confirm(ctx, ctx.getString(messageRes), R.string.action_delete, onConfirmed);
    }

    /**
     * @param confirmRes the affirmative button's label, which should name what is about to happen
     *                   -- a dialog whose only button says "Delete" cannot ask anything else
     */
    public static void confirm(Context ctx, CharSequence message, int confirmRes,
                               final Runnable onConfirmed) {
        new AlertDialog.Builder(ctx)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(confirmRes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onConfirmed.run();
                    }
                })
                .show();
    }

    /**
     * Whether a field that mirrors another should keep following it.
     *
     * <p>True while the mirror is empty or still shows what the source said <em>before</em> the
     * current edit -- meaning the user has not given it a value of its own. Comparing against the
     * previous text rather than the new one is what lets a mirror track typing keystroke by
     * keystroke and stop the moment the two are deliberately different.
     */
    public static boolean stillMirrors(String mirrored, String sourceBefore) {
        String mine = mirrored == null ? "" : mirrored.trim();
        String theirs = sourceBefore == null ? "" : sourceBefore.trim();
        return mine.isEmpty() || mine.equals(theirs);
    }

    /** Cents as a plain editable number, e.g. {@code 35000} to {@code "350,00"} in a German UI. */
    public static String centsToEditable(long cents) {
        NumberFormat f = NumberFormat.getNumberInstance();
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        f.setGroupingUsed(false);
        return f.format(cents / 100.0);
    }

    /**
     * Parses what the user typed into cents.
     *
     * <p>Accepts both separators regardless of locale: someone typing "350.00" on a German phone
     * means three hundred and fifty euros, and refusing that would be pedantic. The last separator
     * in the string is the decimal one, so "1.234,56" and "1,234.56" both work.
     *
     * <p>How many digits follow that last separator decides what it was. Up to two are cents.
     * Exactly three is ambiguous -- "1.234" is a grouped thousand to a German and one euro
     * twenty-three point four to nobody -- so grouping wins. More than three cannot be grouping in
     * any locale, so it is someone typing past the cents and the surplus is dropped.
     */
    public static long editableToCents(String text) {
        if (text == null) return 0L;
        String cleaned = text.trim().replaceAll("[^0-9.,-]", "");
        if (cleaned.isEmpty()) return 0L;

        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        int decimalAt = Math.max(lastComma, lastDot);

        String digits;
        String fraction = "";
        int trailing = decimalAt < 0 ? -1 : cleaned.length() - decimalAt - 1;
        if (decimalAt >= 0 && trailing != 3) {
            digits = cleaned.substring(0, decimalAt);
            fraction = cleaned.substring(decimalAt + 1);
        } else {
            digits = cleaned;
        }
        digits = digits.replaceAll("[.,]", "");
        boolean negative = digits.startsWith("-");
        digits = digits.replace("-", "");
        while (fraction.length() < 2) fraction = fraction + "0";
        if (fraction.length() > 2) fraction = fraction.substring(0, 2);

        try {
            long whole = digits.isEmpty() ? 0L : Long.parseLong(digits);
            long cents = whole * 100L + Long.parseLong(fraction);
            return negative ? -cents : cents;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Cents formatted with the currency symbol, for display only. */
    public static String money(long cents) {
        NumberFormat f = NumberFormat.getCurrencyInstance();
        f.setCurrency(Currency.getInstance("EUR"));
        return f.format(cents / 100.0);
    }

    /**
     * {@code 21:30} in the device's zone from an instant, or an empty string for "not set".
     *
     * <p>Twenty-four hour whatever the locale: these times belong to sets that run past midnight,
     * where "4:00" without the 0 leaves which day it falls on open to reading.
     */
    public static String timeOfDay(long millis) {
        if (millis <= 0L) return "";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return String.format(Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }
}
