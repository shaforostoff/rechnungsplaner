package com.shaforostoff.rechnungsplaner.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.data.Customer;
import com.shaforostoff.rechnungsplaner.data.CustomerDao;
import com.shaforostoff.rechnungsplaner.data.Invoice;
import com.shaforostoff.rechnungsplaner.data.InvoiceDao;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Issued invoices, grouped by the year they count in, so any of them can be re-shared without
 * regenerating and the year's takings can be read off the bottom of each group.
 *
 * <p>The year is the one income is declared in, which is the year the money arrived rather than
 * the year on the paper. Most of the time those agree and nothing has to be said; for a set
 * played in December and paid in January they do not, and an invoice can be dragged into the
 * following year to say so.
 */
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
        // Your own details appear on every invoice here, so this is where noticing a wrong IBAN or
        // tax number happens. Until now the only way in was the calendar's overflow menu.
        addTitleAction(R.string.menu_issuer, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(InvoiceListActivity.this, IssuerEditActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        body().removeAllViews();

        List<Invoice> all = invoices.all();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.invoices_empty);
            empty.setTextColor(getColor(R.color.text_secondary));
            body().addView(empty);
            return;
        }

        for (Integer year : yearsToShow(all)) body().addView(group(year.intValue(), all));
    }

    /**
     * The years to render, newest first.
     *
     * <p>Every year that holds an invoice, plus the year after each invoice's own -- otherwise the
     * place to drag a December invoice to would not exist until something was already in it.
     * Since the service year never moves, this adds exactly one year beyond the newest and does
     * not then keep growing.
     */
    private List<Integer> yearsToShow(List<Invoice> all) {
        Set<Integer> years = new TreeSet<Integer>(Collections.reverseOrder());
        for (Invoice invoice : all) {
            int taxYear = invoice.taxYear();
            if (taxYear > 0) years.add(Integer.valueOf(taxYear));
            int service = invoice.serviceYear();
            if (service > 0) years.add(Integer.valueOf(service + 1));
        }
        return new ArrayList<Integer>(years);
    }

    /** One year: a heading, its invoices, and what they came to. */
    private View group(final int year, List<Invoice> all) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(this);
        heading.setText(getString(R.string.year_heading, year));
        heading.setTextSize(20f);
        heading.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        heading.setPadding(0, dp(6), 0, dp(6));
        box.addView(heading);

        long total = 0L;
        long vat = 0L;
        int counted = 0;
        int superseded = 0;
        for (Invoice invoice : all) {
            if (invoice.taxYear() != year) continue;
            box.addView(row(invoice));
            // A superseded invoice is on screen because it is part of the record, but it was
            // replaced by another that is also in this list -- counting both would declare the
            // same fee twice, which is the one error here that matters.
            if (invoices.replacementNumberOf(invoice.id) != null) {
                superseded++;
                continue;
            }
            total += invoice.grandTotalCents;
            vat += invoice.taxTotalCents;
            counted++;
        }

        box.addView(totalLine(year, total, vat, counted, superseded));
        box.setOnDragListener(new YearDropListener(year));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(18);
        box.setLayoutParams(lp);
        return box;
    }

    private View totalLine(int year, long total, long vat, int counted, int superseded) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(8), dp(14), dp(10));

        TextView sum = new TextView(this);
        sum.setText(getString(R.string.year_total, Integer.toString(year), Ui.money(total)));
        sum.setTextSize(16f);
        sum.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        sum.setTextColor(getColor(R.color.accent));
        wrap.addView(sum);

        StringBuilder detail = new StringBuilder();
        if (vat != 0L) {
            detail.append(getString(R.string.year_total_vat, Ui.money(total - vat),
                    Ui.money(vat)));
        }
        if (superseded > 0) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(getString(R.string.year_total_excluded, superseded));
        }
        if (counted == 0 && superseded == 0) {
            detail.setLength(0);
            detail.append(getString(R.string.year_total_empty));
        }
        if (detail.length() > 0) {
            TextView note = new TextView(this);
            note.setText(detail.toString());
            note.setTextSize(12f);
            note.setTextColor(getColor(R.color.text_secondary));
            wrap.addView(note);
        }
        return wrap;
    }

    /**
     * Accepts an invoice dragged from the year next to this one.
     *
     * <p>Only the service year and the one after it are offered, and the drag is refused rather
     * than ignored anywhere else: those two are the years a payment can honestly be assigned to,
     * and a list where anything could be dropped anywhere would invite moving income to whichever
     * year suited it.
     */
    private final class YearDropListener implements View.OnDragListener {

        private final int year;

        YearDropListener(int year) {
            this.year = year;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            Invoice dragged = event.getLocalState() instanceof Invoice
                    ? (Invoice) event.getLocalState() : null;
            if (dragged == null) return false;
            boolean allowed = accepts(dragged);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                case DragEvent.ACTION_DRAG_LOCATION:
                    return allowed;
                case DragEvent.ACTION_DRAG_ENTERED:
                    if (allowed) v.setBackgroundResource(R.drawable.card_accent);
                    return allowed;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackground(null);
                    return allowed;
                case DragEvent.ACTION_DROP:
                    v.setBackground(null);
                    if (!allowed) return false;
                    moveToYear(dragged, year);
                    return true;
                default:
                    return false;
            }
        }

        /** Its own year is a no-op worth accepting, so dropping back where it came from works. */
        private boolean accepts(Invoice dragged) {
            int service = dragged.serviceYear();
            return service > 0 && (year == service || year == service + 1);
        }
    }

    /**
     * Records the move, storing zero when the invoice goes back to its service year.
     *
     * <p>Zero rather than the year itself, so an invoice that was never moved and one moved back
     * are the same thing afterwards -- and a service date corrected later still carries its own
     * year with it.
     */
    private void moveToYear(Invoice invoice, int year) {
        int stored = year == invoice.serviceYear() ? 0 : year;
        if (stored == invoice.paidYear) return;
        invoices.setPaidYear(invoice.id, stored);
        Ui.toast(this, getString(R.string.invoice_moved_to_year, invoice.number,
                Integer.toString(year)));
        render();
    }

    private View row(final Invoice invoice) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setBackgroundResource(R.drawable.card);
        shell.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView number = new TextView(this);
        number.setText(invoice.number);
        number.setTextSize(16f);
        number.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        row.addView(number);

        // Which of two invoices for the same gig is the current one is not guessable from a date,
        // so a superseded one says so on its face and is struck through.
        String replacedBy = invoices.replacementNumberOf(invoice.id);
        if (replacedBy != null) {
            number.setPaintFlags(number.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            TextView replaced = new TextView(this);
            replaced.setText(getString(R.string.two_part_label,
                    getString(R.string.label_replaced_by), replacedBy));
            replaced.setTextSize(12f);
            replaced.setTextColor(getColor(R.color.warning));
            row.addView(replaced);
        } else if (invoice.replacesNumber != null) {
            TextView replaces = new TextView(this);
            replaces.setText(getString(R.string.two_part_label,
                    getString(R.string.label_replaces), invoice.replacesNumber));
            replaces.setTextSize(12f);
            replaces.setTextColor(getColor(R.color.text_secondary));
            row.addView(replaces);
        }

        // Once an invoice has been moved, the year it is filed under is the group heading and the
        // year it was earned in is nowhere -- so it says so here.
        if (invoice.paidYear > 0 && invoice.serviceYear() > 0) {
            TextView earned = new TextView(this);
            earned.setText(getString(R.string.label_earned_year, invoice.serviceYear()));
            earned.setTextSize(12f);
            earned.setTextColor(getColor(R.color.text_secondary));
            row.addView(earned);
        }

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

        shell.addView(row, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View mover = yearControl(invoice, shell);
        if (mover != null) shell.addView(mover);

        shell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(InvoiceActivity.openIntent(InvoiceListActivity.this, invoice.id));
            }
        });
        // Long-press opens the menu rather than starting the drag. The drag has the handle to
        // itself, and this is the path that works without one: a hidden gesture is no way to
        // reach the only control on this screen that changes what a tax total says.
        shell.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showYearMenu(invoice);
                return true;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        shell.setLayoutParams(lp);
        return shell;
    }

    /**
     * The control that moves one invoice between its two candidate years.
     *
     * <p>Labelled with the year it would move to rather than an icon, so what it does is legible
     * without being learnt -- and with only two years in play, naming the other one says
     * everything. Tapping it moves the invoice; holding it starts the drag, which is now the only
     * thing long-press on the handle does. Null when the invoice has no date to reason from.
     */
    private View yearControl(final Invoice invoice, final View dragSource) {
        final int service = invoice.serviceYear();
        if (service <= 0) return null;
        final int target = invoice.taxYear() == service ? service + 1 : service;

        TextView mover = new TextView(this);
        mover.setText(getString(R.string.move_to_year, target));
        mover.setTextSize(14f);
        mover.setTextColor(getColor(R.color.accent));
        mover.setBackgroundResource(R.drawable.field);
        mover.setPadding(dp(10), dp(6), dp(10), dp(6));
        mover.setContentDescription(getString(R.string.move_to_year_desc, invoice.number, target));
        mover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToYear(invoice, target);
            }
        });
        mover.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // The whole card is dragged, not the handle: a shadow of one small box would not
                // show which invoice is in flight. The invoice travels as local state rather than
                // in the ClipData, because nothing outside this window should receive an invoice.
                return dragSource.startDragAndDrop(
                        ClipData.newPlainText(invoice.number, invoice.number),
                        new View.DragShadowBuilder(dragSource), invoice, 0);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        mover.setLayoutParams(lp);
        return mover;
    }

    /**
     * The two years this invoice's payment could honestly belong to, with the current one marked.
     *
     * <p>A list of both rather than a single toggle: which year it is filed under now is the thing
     * being decided, so it has to be visible while deciding, not inferred from the label of the
     * button that would change it.
     */
    private void showYearMenu(final Invoice invoice) {
        final int service = invoice.serviceYear();
        if (service <= 0) {
            Ui.toast(this, R.string.invoice_no_year);
            return;
        }
        final int[] years = {service, service + 1};
        String[] labels = {getString(R.string.paid_in_year, years[0]),
                getString(R.string.paid_in_year, years[1])};
        int checked = invoice.taxYear() == years[1] ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(invoice.number)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        moveToYear(invoice, years[which]);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
