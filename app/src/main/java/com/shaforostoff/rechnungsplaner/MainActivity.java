package com.shaforostoff.rechnungsplaner;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.Service;
import com.shaforostoff.rechnungsplaner.data.ServiceDao;
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
    private ServiceDao services;
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
        services = new ServiceDao(this);

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

        List<Service> offered = services.all(false);
        if (offered.isEmpty()) {
            // A fresh install seeds nothing: what this business sells is not for the app to guess,
            // and the button below is the whole of the setup.
            TextView none = new TextView(this);
            none.setText(R.string.no_services_yet);
            none.setTextSize(13f);
            none.setTextColor(getColor(R.color.text_secondary));
            none.setPadding(0, dp(8), 0, 0);
            dayList.addView(none);
        }
        for (final Service service : offered) {
            dayList.addView(addButton(service.displayName(), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(GigEditActivity.createIntent(MainActivity.this, isoDate,
                            service.id));
                }
            }, new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    editService(service);
                    return true;
                }
            }));
        }
        dayList.addView(addButton(getString(R.string.add_service), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nameService(new Service());
            }
        }, null));
    }

    /**
     * One full-width button under the day's list.
     *
     * <p>A button per kind of work rather than one generic "add": which service it is decides the
     * invoice line and the calendar title, so asking once here saves asking again on the next
     * screen -- and the list of them is the closest thing this app has to a statement of what the
     * business does.
     */
    private View addButton(String label, View.OnClickListener onClick,
                           View.OnLongClickListener onLongClick) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15f);
        button.setTextColor(getColor(R.color.accent));
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.field);
        button.setPadding(dp(12), dp(14), dp(12), dp(14));
        button.setOnClickListener(onClick);
        if (onLongClick != null) {
            button.setOnLongClickListener(onLongClick);
            button.setContentDescription(getString(R.string.add_service_desc, label));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    /** Rename or remove, offered on a long press so the buttons stay buttons. */
    private void editService(final Service service) {
        new AlertDialog.Builder(this)
                .setTitle(service.displayName())
                .setItems(new String[]{getString(R.string.action_rename),
                        getString(R.string.action_delete)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) nameService(service);
                                else removeService(service);
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * The add and edit dialog, which are the same dialog with a different starting value.
     *
     * <p>Three fields rather than a name alone: what the work is called, whether it is measured
     * in days, and whether it belongs in the calendar. The last two change what the next screen
     * asks for and what leaves the app, so they are worth deciding once per kind of work rather
     * than once per job.
     */
    private void nameService(final Service service) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText field = new EditText(this);
        field.setText(service.name == null ? "" : service.name);
        field.setHint(R.string.hint_service_name);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSelection(field.getText().length());
        box.addView(field);

        final CheckBox multiDay = new CheckBox(this);
        multiDay.setText(R.string.service_multi_day);
        multiDay.setChecked(service.multiDay);
        box.addView(multiDay);

        final CheckBox sync = new CheckBox(this);
        sync.setText(R.string.service_sync_calendar);
        sync.setChecked(service.syncToCalendar);
        box.addView(sync);

        new AlertDialog.Builder(this)
                .setTitle(service.id > 0L ? R.string.action_rename : R.string.add_service)
                .setView(box)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = field.getText().toString().trim();
                        if (name.isEmpty()) {
                            Ui.toast(MainActivity.this, R.string.needs_a_name);
                            return;
                        }
                        service.name = name;
                        service.multiDay = multiDay.isChecked();
                        service.syncToCalendar = sync.isChecked();
                        services.save(service);
                        // Renaming changes the invoice line of every job not yet billed, and the
                        // buttons, so the day is rebuilt rather than patched.
                        calendar.select(selectedDate);
                    }
                })
                .show();
    }

    /**
     * Removes a service, or retires it when jobs still name it.
     *
     * <p>The confirmation says which of the two will happen, because they are different promises:
     * one forgets the service, the other only takes its button away.
     */
    private void removeService(final Service service) {
        Ui.confirm(this, getString(R.string.confirm_delete_service, service.displayName()),
                R.string.action_delete, new Runnable() {
                    @Override
                    public void run() {
                        boolean gone = services.deleteOrArchive(service.id);
                        Ui.toast(MainActivity.this, gone
                                ? getString(R.string.service_deleted, service.displayName())
                                : getString(R.string.service_archived, service.displayName()));
                        calendar.select(selectedDate);
                    }
                });
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
            case PLAYED: return getString(R.string.status_executed);
            case INVOICED: return getString(R.string.status_invoiced);
            case PAID: return getString(R.string.status_paid);
            case PLANNED:
            default: return getString(R.string.status_planned);
        }
    }
}
