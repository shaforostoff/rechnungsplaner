package com.shaforostoff.rechnungsplaner.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;

import java.util.List;

/** The customer list, showing at a glance which ones are ready to invoice. */
public class CustomerListActivity extends BaseActivity {

    private CustomerDao customers;

    @Override
    protected int bottomTab() {
        return TAB_CUSTOMERS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customers = new CustomerDao(this);
        setScreenTitle(R.string.tab_customers);
        addTitleAction(R.string.new_customer, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(CustomerEditActivity.createIntent(CustomerListActivity.this));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        body().removeAllViews();

        List<Customer> all = customers.all(true);
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.customers_empty);
            empty.setTextColor(getColor(R.color.text_secondary));
            body().addView(empty);
            return;
        }
        for (Customer customer : all) body().addView(row(customer));
    }

    private View row(final Customer customer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView name = new TextView(this);
        name.setText(customer.displayName());
        name.setTextSize(16f);
        name.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(name);

        StringBuilder detail = new StringBuilder();
        if (customer.city != null && !customer.city.trim().isEmpty()) {
            detail.append(customer.city.trim());
        }
        if (customer.archived) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(getString(R.string.customer_archived));
        }
        if (detail.length() > 0) {
            TextView subtitle = new TextView(this);
            subtitle.setText(detail.toString());
            subtitle.setTextSize(13f);
            subtitle.setTextColor(getColor(R.color.text_secondary));
            row.addView(subtitle);
        }

        // The readiness line is the point of this list: it says whether an invoice can go out
        // today, without having to open the customer and compare against the rules.
        int missing = CustomerReadiness.missingCount(customer);
        TextView readiness = new TextView(this);
        readiness.setTextSize(13f);
        if (missing == 0) {
            readiness.setText(R.string.ready_to_invoice);
            readiness.setTextColor(getColor(R.color.accent));
        } else {
            readiness.setText(getResources().getQuantityString(R.plurals.fields_missing, missing,
                    missing));
            readiness.setTextColor(getColor(R.color.warning));
        }
        row.addView(readiness);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(CustomerEditActivity.editIntent(CustomerListActivity.this,
                        customer.id));
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }
}
