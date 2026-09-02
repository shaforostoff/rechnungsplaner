package com.shaforostoff.rechnungsplaner;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.ui.BaseActivity;
import com.shaforostoff.rechnungsplaner.ui.ExportActivity;
import com.shaforostoff.rechnungsplaner.ui.GigEditActivity;
import com.shaforostoff.rechnungsplaner.ui.MonthCalendarView;
import com.shaforostoff.rechnungsplaner.ui.Ui;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.text.DateFormatSymbols;
import java.util.List;

/** The calendar: a month grid, and the DJ-sets on whichever day is selected. */
public class MainActivity extends BaseActivity implements MonthCalendarView.Listener {

    private MonthCalendarView calendar;
    private LinearLayout dayList;
    private GigDao gigs;
    private CustomerDao customers;
    private String selectedDate;

    @Override
    protected int bottomTab() {
        return TAB_CALENDAR;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gigs = new GigDao(this);
        customers = new CustomerDao(this);

        addTitleAction(R.string.menu_today, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calendar.select(Dates.today());
            }
        });
        addTitleAction(R.string.title_import_export, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ExportActivity.class));
            }
        });

        calendar = new MonthCalendarView(this);
        calendar.setListener(this);
        calendar.setWeekdayInitials(getResources().getStringArray(R.array.weekday_initials));
        body().addView(calendar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dayList = new LinearLayout(this);
        dayList.setOrientation(LinearLayout.VERTICAL);
        body().addView(dayList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        selectedDate = Dates.today();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the editor, both the month markers and the day list may have changed.
        refreshMonth();
        showDay(selectedDate);
    }

    @Override
    public void onDaySelected(String isoDate) {
        selectedDate = isoDate;
        showDay(isoDate);
    }

    @Override
    public void onMonthChanged(int year, int month) {
        setScreenTitle(monthTitle(year, month));
        refreshMonth();
    }

    private void refreshMonth() {
        setScreenTitle(monthTitle(calendar.getYear(), calendar.getMonth()));
        calendar.setGigCounts(gigs.countsByDate(calendar.firstVisibleDate(),
                calendar.lastVisibleDate()));
    }

    private String monthTitle(int year, int month) {
        String[] months = DateFormatSymbols.getInstance(
                getResources().getConfiguration().getLocales().get(0)).getMonths();
        String name = month >= 1 && month <= 12 ? months[month - 1] : "";
        return name + " " + year;
    }

    private void showDay(final String isoDate) {
        dayList.removeAllViews();

        TextView heading = new TextView(this);
        heading.setText(Dates.forLanguage(isoDate,
                getResources().getConfiguration().getLocales().get(0).getLanguage()));
        heading.setTextSize(15f);
        heading.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        heading.setPadding(0, dp(16), 0, dp(8));
        dayList.addView(heading);

        List<Gig> onDay = gigs.between(isoDate, isoDate);
        if (onDay.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_gigs_on_day);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(0, dp(4), 0, dp(12));
            dayList.addView(empty);
        }
        for (final Gig gig : onDay) {
            dayList.addView(gigRow(gig));
        }

        TextView add = new TextView(this);
        add.setText(R.string.add_gig);
        add.setTextSize(15f);
        add.setTextColor(getColor(R.color.accent));
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.field);
        add.setPadding(dp(12), dp(14), dp(12), dp(14));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(GigEditActivity.createIntent(MainActivity.this, isoDate));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        dayList.addView(add, lp);
    }

    private View gigRow(final Gig gig) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        Customer customer = customers.byId(gig.customerId);
        TextView title = new TextView(this);
        title.setText(where(gig, customer));
        title.setTextSize(16f);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(title);

        StringBuilder detail = new StringBuilder();
        String time = Ui.timeOfDay(gig.startMillis);
        if (!time.isEmpty()) detail.append(time).append(" · ");
        detail.append(Ui.money(gig.totalNetCents()));
        detail.append(" · ").append(statusLabel(gig.status));

        TextView subtitle = new TextView(this);
        subtitle.setText(detail.toString());
        subtitle.setTextSize(13f);
        subtitle.setTextColor(getColor(R.color.text_secondary));
        row.addView(subtitle);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(GigEditActivity.editIntent(MainActivity.this, gig.id));
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private String where(Gig gig, Customer customer) {
        if (gig.placeName != null && !gig.placeName.trim().isEmpty()) return gig.placeName.trim();
        if (customer != null) return customer.displayName();
        if (gig.city != null && !gig.city.trim().isEmpty()) return gig.city.trim();
        return getString(R.string.title_edit_gig);
    }

    private String statusLabel(Gig.Status status) {
        switch (status) {
            case PLAYED: return getString(R.string.status_played);
            case INVOICED: return getString(R.string.status_invoiced);
            case PAID: return getString(R.string.status_paid);
            case PLANNED:
            default: return getString(R.string.status_planned);
        }
    }
}
