package com.shaforostoff.rechnungsplaner.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Invoice;
import com.shaforostoff.rechnungsplaner.data.InvoiceDao;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.List;

/** Issued invoices, newest first, so any of them can be re-shared without regenerating. */
public class InvoiceListActivity extends BaseActivity {

    private InvoiceDao invoices;
    private CustomerDao customers;

    @Override
    protected int bottomTab() {
        return TAB_INVOICES;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        invoices = new InvoiceDao(this);
        customers = new CustomerDao(this);
        setScreenTitle(R.string.tab_invoices);
    }

    @Override
    protected void onResume() {
        super.onResume();
        body().removeAllViews();

        List<Invoice> all = invoices.all();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.invoices_empty);
            empty.setTextColor(getColor(R.color.text_secondary));
            body().addView(empty);
            return;
        }
        for (Invoice invoice : all) body().addView(row(invoice));
    }

    private View row(final Invoice invoice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView number = new TextView(this);
        number.setText(invoice.number);
        number.setTextSize(16f);
        number.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(number);

        Customer customer = customers.byId(invoice.customerId);
        StringBuilder detail = new StringBuilder();
        detail.append(Dates.forLanguage(invoice.issueDate,
                getResources().getConfiguration().getLocales().get(0).getLanguage()));
        if (customer != null) detail.append(" · ").append(customer.displayName());
        detail.append(" · ").append(Ui.money(invoice.grandTotalCents));

        TextView subtitle = new TextView(this);
        subtitle.setText(detail.toString());
        subtitle.setTextSize(13f);
        subtitle.setTextColor(getColor(R.color.text_secondary));
        row.addView(subtitle);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(InvoiceActivity.openIntent(InvoiceListActivity.this, invoice.id));
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }
}
