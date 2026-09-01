package com.shaforostoff.rechnungsplaner.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.MainActivity;
import com.shaforostoff.rechnungsplaner.R;

/**
 * The chrome every screen shares: a title bar, a scrolling body, and the four-tab bar along the
 * bottom.
 *
 * <p>Built in code rather than as an XML layout that ten activities would each have to include and
 * then wire up identically. The bottom bar is four activities rather than fragments, matching the
 * dependency-free, plain-{@link Activity} approach the rest of the app takes; switching tabs
 * reuses the existing task entry so the back stack does not fill with them.
 */
public abstract class BaseActivity extends Activity {

    protected static final int TAB_NONE = -1;
    protected static final int TAB_CALENDAR = 0;
    protected static final int TAB_CUSTOMERS = 1;
    protected static final int TAB_INVOICES = 2;
    protected static final int TAB_SETTINGS = 3;

    private LinearLayout body;
    private FrameLayout bodyHost;
    private TextView titleView;
    private LinearLayout actionRow;

    /** Which bottom-bar entry to highlight, or {@link #TAB_NONE} for a detail screen. */
    protected int bottomTab() {
        return TAB_NONE;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Locales.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildChrome());
    }

    /** The vertical container screens add their content to. */
    protected LinearLayout body() {
        return body;
    }

    protected FormBuilder form() {
        return new FormBuilder(this, body);
    }

    protected void setScreenTitle(int titleRes) {
        titleView.setText(titleRes);
    }

    protected void setScreenTitle(CharSequence title) {
        titleView.setText(title);
    }

    /** Adds a text action to the right of the title, e.g. "Save". */
    protected TextView addTitleAction(int labelRes, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(labelRes);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        tv.setTextColor(getColor(R.color.accent));
        tv.setPadding(dp(12), dp(8), dp(4), dp(8));
        tv.setOnClickListener(onClick);
        actionRow.addView(tv);
        return tv;
    }

    protected int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View buildChrome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(getColor(R.color.background));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(getColor(R.color.surface));
        header.setPadding(dp(16), dp(14), dp(12), dp(14));

        titleView = new TextView(this);
        titleView.setTextSize(20f);
        titleView.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titleView.setTextColor(getColor(R.color.text_primary));
        header.addView(titleView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        bodyHost = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        bodyHost.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(bodyHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (bottomTab() != TAB_NONE) root.addView(buildBottomBar());
        return root;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(getColor(R.color.surface));

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.border));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f))));

        int[] labels = {R.string.tab_calendar, R.string.tab_customers, R.string.tab_invoices,
                R.string.tab_settings};
        final Class<?>[] targets = {MainActivity.class, CustomerListActivity.class,
                InvoiceListActivity.class, SettingsActivity.class};

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setTextSize(12f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(12), 0, dp(12));
            boolean selected = i == bottomTab();
            tab.setTextColor(getColor(selected ? R.color.accent : R.color.text_secondary));
            if (selected) tab.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            if (!selected) {
                tab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // CLEAR_TOP keeps the four tabs from stacking up behind each other.
                        startActivity(new Intent(BaseActivity.this, targets[index])
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        overridePendingTransition(0, 0);
                        finish();
                    }
                });
            }
            bar.addView(tab, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrapper.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }
}
