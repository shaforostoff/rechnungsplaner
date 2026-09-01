package com.shaforostoff.rechnungsplaner.ui;

import android.Manifest;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.calendar.CalendarMirror;
import com.shaforostoff.rechnungsplaner.data.Gig;
import com.shaforostoff.rechnungsplaner.data.GigDao;
import com.shaforostoff.rechnungsplaner.data.SettingsStore;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.List;
import java.util.UUID;

/**
 * Brings calendar events into the app as DJ-sets.
 *
 * <p>Two different things are offered here and the distinction matters. An event this app created
 * earlier whose gig has since gone -- after restoring a backup, say -- is re-linked to a rebuilt
 * gig rather than duplicated. An event the user made in their calendar app is adopted as a new gig.
 */
public class CalendarImportActivity extends BaseActivity {

    private static final int REQUEST_PERMISSION = 51;

    private CalendarMirror mirror;
    private GigDao gigs;
    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mirror = new CalendarMirror(this);
        gigs = new GigDao(this);
        settings = new SettingsStore(this);
        setScreenTitle(R.string.title_import_calendar);

        if (!mirror.hasPermission()) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR}, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        body().removeAllViews();

        if (!mirror.hasPermission()) {
            message(R.string.calendar_permission_needed);
            return;
        }
        long calendarId = settings.getCalendarId();
        if (calendarId <= 0L) {
            message(R.string.setting_calendar_off);
            return;
        }

        // A year back and a year forward covers "I booked this months ago" without listing a
        // decade of birthdays.
        String from = Dates.plusDays(Dates.today(), -365);
        String to = Dates.plusDays(Dates.today(), 365);
        List<CalendarMirror.AdoptableEvent> events = mirror.scan(calendarId, from, to);

        if (events.isEmpty()) {
            message(R.string.calendar_nothing_to_import);
            return;
        }
        for (CalendarMirror.AdoptableEvent event : events) body().addView(row(event));
    }

    private void message(int textRes) {
        TextView tv = new TextView(this);
        tv.setText(textRes);
        tv.setTextColor(getColor(R.color.text_secondary));
        body().addView(tv);
    }

    private View row(final CalendarMirror.AdoptableEvent event) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView title = new TextView(this);
        title.setText(event.title == null ? "?" : event.title);
        title.setTextSize(16f);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(title);

        TextView subtitle = new TextView(this);
        StringBuilder detail = new StringBuilder(Dates.forLanguage(event.date,
                getResources().getConfiguration().getLocales().get(0).getLanguage()));
        if (event.location != null && !event.location.trim().isEmpty()) {
            detail.append(" · ").append(event.location.trim());
        }
        if (event.orphanedUuid != null) {
            detail.append(" · ").append(getString(R.string.calendar_relink_desc));
        }
        subtitle.setText(detail.toString());
        subtitle.setTextSize(13f);
        subtitle.setTextColor(getColor(R.color.text_secondary));
        row.addView(subtitle);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adopt(event);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void adopt(CalendarMirror.AdoptableEvent event) {
        Gig gig = new Gig();
        gig.date = event.date;
        gig.startMillis = event.startMillis;
        gig.endMillis = event.endMillis;
        gig.placeName = event.location;
        gig.notes = event.notes;
        gig.calendarId = settings.getCalendarId();
        gig.calendarEventId = event.eventId;
        // Reusing the marker keeps the event linked to the rebuilt gig instead of orphaning it
        // again the next time this screen is opened.
        gig.syncUuid = event.orphanedUuid != null ? event.orphanedUuid
                : UUID.randomUUID().toString();
        gigs.save(gig);
        startActivity(GigEditActivity.editIntent(this, gig.id));
    }
}
