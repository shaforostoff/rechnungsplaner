package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;

/**
 * Builds the label-above-outlined-field forms the app uses.
 *
 * <p>Programmatic rather than XML because there are five of these screens with sixty-odd fields
 * between them, and the XML would be several hundred lines of near-identical blocks. Building them
 * here also makes the one affordance worth being consistent about impossible to get wrong: a field
 * XRechnung requires gets a blue outline, everywhere, from one code path.
 */
public class FormBuilder {

    private final Context ctx;
    private final LinearLayout container;
    private final float density;

    public FormBuilder(Context ctx, LinearLayout container) {
        this.ctx = ctx;
        this.container = container;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public int dp(float value) {
        return Math.round(value * density);
    }

    /**
     * Two half-width columns, each with its own builder, so a pair of short fields can share a
     * line: start and end, fee and travel costs.
     *
     * <p>Each column is a vertical container of its own, which is what keeps the label-above-field
     * shape and the required outline working unchanged inside it. Baseline alignment is off because
     * it would align the two labels' first text lines rather than the columns' tops, which comes
     * apart the moment one label wraps in a longer language.
     */
    public Row row() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        leftParams.rightMargin = dp(6);
        row.addView(left, leftParams);

        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.leftMargin = dp(6);
        row.addView(right, rightParams);

        // No bottom margin: the fields inside bring their own.
        container.addView(row, matchWidth());
        return new Row(new FormBuilder(ctx, left), new FormBuilder(ctx, right));
    }

    /** The two halves of a {@link #row()}, to build a field in each. */
    public static final class Row {
        public final FormBuilder left;
        public final FormBuilder right;

        Row(FormBuilder left, FormBuilder right) {
            this.left = left;
            this.right = right;
        }
    }

    /** A bold section heading with space above it. */
    public TextView section(int labelRes) {
        TextView tv = new TextView(ctx);
        tv.setText(labelRes);
        tv.setTextSize(17f);
        tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        tv.setTextColor(ctx.getColor(R.color.text_primary));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(6);
        container.addView(tv, lp);
        return tv;
    }

    /** Small grey explanatory text. */
    public TextView caption(CharSequence text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(ctx.getColor(R.color.text_secondary));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(8);
        container.addView(tv, lp);
        return tv;
    }

    /** The legend explaining what the blue outlines mean. */
    public TextView requiredLegend() {
        TextView tv = new TextView(ctx);
        tv.setText(R.string.required_for_xrechnung);
        tv.setTextSize(12f);
        tv.setTextColor(ctx.getColor(R.color.accent));
        tv.setBackgroundResource(R.drawable.card_accent);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(12);
        container.addView(tv, lp);
        return tv;
    }

    public EditText field(int labelRes, String value, boolean required) {
        return field(labelRes, value, required, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, null);
    }

    public EditText field(int labelRes, String value, boolean required, int inputType) {
        return field(labelRes, value, required, inputType, null);
    }

    /**
     * @param required draws the blue outline, meaning XRechnung will reject the invoice without it
     * @param hint     shown when empty, for the fields whose purpose is not obvious
     */
    public EditText field(int labelRes, String value, boolean required, int inputType,
                          String hint) {
        label(labelRes, required);
        EditText et = new EditText(ctx);
        et.setText(value == null ? "" : value);
        et.setInputType(inputType);
        et.setTextSize(15f);
        et.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        if (hint != null) et.setHint(hint);
        if ((inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) {
            // A number field holds one complete value -- an amount, a count of days -- and it
            // arrives prefilled, so tapping into it is almost always about replacing it. Without
            // this, typing 400 into a field showing 350,00 gives 350,00400. A second tap still
            // places the cursor, for correcting a digit.
            et.setSelectAllOnFocus(true);
        }
        et.setBackgroundResource(required ? R.drawable.field_required : R.drawable.field);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        container.addView(et, lp);
        return et;
    }

    /** A multi-line notes field. */
    public EditText multiline(int labelRes, String value) {
        label(labelRes, false);
        EditText et = new EditText(ctx);
        et.setText(value == null ? "" : value);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setTextSize(15f);
        et.setBackgroundResource(R.drawable.field);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        container.addView(et, lp);
        return et;
    }

    public Spinner spinner(int labelRes, String[] entries, int selectedIndex, boolean required) {
        label(labelRes, required);
        Spinner spinner = new Spinner(ctx);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ctx,
                android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selectedIndex >= 0 && selectedIndex < entries.length) {
            spinner.setSelection(selectedIndex);
        }
        spinner.setBackgroundResource(required ? R.drawable.field_required : R.drawable.field);
        spinner.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        container.addView(spinner, lp);
        return spinner;
    }

    /** A field that opens a picker instead of accepting typing. */
    public TextView pickerField(int labelRes, String value, boolean required,
                                View.OnClickListener onClick) {
        label(labelRes, required);
        TextView tv = new TextView(ctx);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(15f);
        tv.setTextColor(ctx.getColor(R.color.text_primary));
        tv.setBackgroundResource(required ? R.drawable.field_required : R.drawable.field);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        tv.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        container.addView(tv, lp);
        return tv;
    }

    public CheckBox check(int labelRes, boolean checked) {
        CheckBox cb = new CheckBox(ctx);
        cb.setText(labelRes);
        cb.setChecked(checked);
        cb.setTextSize(15f);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(8);
        container.addView(cb, lp);
        return cb;
    }

    public Button primaryButton(int labelRes, View.OnClickListener onClick) {
        Button b = new Button(ctx);
        b.setText(labelRes);
        b.setAllCaps(false);
        b.setTextSize(16f);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundResource(R.drawable.button_primary);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.height = dp(48);
        lp.topMargin = dp(16);
        container.addView(b, lp);
        return b;
    }

    public Button secondaryButton(int labelRes, View.OnClickListener onClick) {
        Button b = new Button(ctx);
        b.setText(labelRes);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setTextColor(ctx.getColor(R.color.accent));
        b.setBackgroundResource(R.drawable.field);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.height = dp(46);
        lp.topMargin = dp(8);
        container.addView(b, lp);
        return b;
    }

    /** Adds an arbitrary view with the form's usual spacing. */
    public <T extends View> T add(T view) {
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        container.addView(view, lp);
        return view;
    }

    public LinearLayout getContainer() {
        return container;
    }

    private void label(int labelRes, boolean required) {
        TextView tv = new TextView(ctx);
        tv.setText(labelRes);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        tv.setTextColor(ctx.getColor(
                required ? R.color.accent : R.color.text_secondary));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(4);
        container.addView(tv, lp);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Convenience for reading a spinner without repeating the cast at every call site. */
    public static int selectionOf(Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        return position == AdapterView.INVALID_POSITION ? 0 : position;
    }
}
