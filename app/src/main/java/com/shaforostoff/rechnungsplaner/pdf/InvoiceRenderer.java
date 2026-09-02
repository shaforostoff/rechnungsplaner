package com.shaforostoff.rechnungsplaner.pdf;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.shaforostoff.rechnungsplaner.R;
import com.shaforostoff.rechnungsplaner.einvoice.EnInvoice;
import com.shaforostoff.rechnungsplaner.einvoice.EnLine;
import com.shaforostoff.rechnungsplaner.einvoice.EnParty;
import com.shaforostoff.rechnungsplaner.einvoice.Money;
import com.shaforostoff.rechnungsplaner.einvoice.TaxBreakdown;
import com.shaforostoff.rechnungsplaner.util.Dates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Draws the human-readable invoice with the framework's {@link PdfDocument}.
 *
 * <p>Labels come from the <em>invoice's</em> locale, not the device's. {@code getString} would
 * follow the phone, which is wrong here: the app can be in English while the invoice going to a
 * Hamburg club has to be in German. {@link Context#createConfigurationContext} gives a Resources
 * bound to a chosen locale, so all three label sets still live in the {@code values-xx} resource
 * directories instead of being hard-coded.
 *
 * <p>Column positions are measured rather than hard-coded, because the German labels are
 * noticeably wider than the English ones and a fixed layout clips them.
 */
public class InvoiceRenderer {

    /** A4 at 72 dpi, which is the unit PdfDocument works in. */
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 56f;
    private static final float LINE = 13f;

    private final Resources res;
    private final Locale locale;
    private final NumberFormat money;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heading = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint();

    public InvoiceRenderer(Context ctx, String languageTag) {
        this.locale = toLocale(languageTag);
        this.res = localizedResources(ctx, locale);
        this.money = NumberFormat.getCurrencyInstance(locale);
        this.money.setCurrency(Currency.getInstance("EUR"));

        // The platform typeface is used deliberately: Android embeds a subset of whatever is
        // drawn, so font embedding holds without shipping a megabyte of TTF.
        body.setTypeface(Typeface.SANS_SERIF);
        body.setTextSize(9.5f);
        body.setColor(Color.BLACK);

        bold.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        bold.setTextSize(9.5f);
        bold.setColor(Color.BLACK);

        small.setTypeface(Typeface.SANS_SERIF);
        small.setTextSize(7.5f);
        small.setColor(Color.DKGRAY);

        heading.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        heading.setTextSize(18f);
        heading.setColor(Color.BLACK);

        rule.setColor(Color.LTGRAY);
        rule.setStrokeWidth(0.6f);
    }

    public byte[] render(EnInvoice inv) throws IOException {
        PdfDocument doc = new PdfDocument();
        try {
            List<List<EnLine>> pages = paginate(inv.lines);
            for (int index = 0; index < pages.size(); index++) {
                PdfDocument.Page page = doc.startPage(
                        new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1)
                                .create());
                drawPage(page.getCanvas(), inv, pages.get(index), index, pages.size());
                doc.finishPage(page);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(32768);
            doc.writeTo(out);
            return out.toByteArray();
        } finally {
            doc.close();
        }
    }

    private void drawPage(Canvas c, EnInvoice inv, List<EnLine> lines, int pageIndex,
                          int pageCount) {
        float y = MARGIN;
        boolean first = pageIndex == 0;

        if (first) {
            y = drawSender(c, inv.seller, y);
            y = drawRecipient(c, inv.buyer, y + 18f);
            y = drawMeta(c, inv, y + 24f);
            y += 18f;
        } else {
            c.drawText(res.getString(R.string.inv_title) + " " + nullToEmpty(inv.number),
                    MARGIN, y, bold);
            y += LINE * 2;
        }

        y = drawTable(c, inv, lines, y);

        boolean last = pageIndex == pageCount - 1;
        if (last) {
            y = drawTotals(c, inv, y + 10f);
            y = drawTaxNotes(c, inv, y + 14f);
            drawPayment(c, inv, y + 16f);
        }
        drawFooter(c, inv, pageIndex + 1, pageCount);
    }

    private float drawSender(Canvas c, EnParty s, float y) {
        c.drawText(nullToEmpty(s.name), MARGIN, y, bold);
        y += LINE;
        for (String line : addressLines(s)) {
            c.drawText(line, MARGIN, y, small);
            y += LINE - 3f;
        }
        return y;
    }

    private float drawRecipient(Canvas c, EnParty b, float y) {
        c.drawText(nullToEmpty(b.name), MARGIN, y, body);
        y += LINE;
        for (String line : addressLines(b)) {
            c.drawText(line, MARGIN, y, body);
            y += LINE;
        }
        return y;
    }

    private float drawMeta(Canvas c, EnInvoice inv, float y) {
        c.drawText(res.getString(R.string.inv_title), MARGIN, y, heading);

        // Right-aligned label/value pairs, with the label column sized to the widest label so the
        // German words do not run into the values.
        List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[]{res.getString(R.string.inv_number), nullToEmpty(inv.number)});
        rows.add(new String[]{res.getString(R.string.inv_date), date(inv.issueDate)});
        if (notEmpty(inv.deliveryDate)) {
            rows.add(new String[]{res.getString(R.string.inv_service_date),
                    date(inv.deliveryDate)});
        } else if (notEmpty(inv.periodStart)) {
            rows.add(new String[]{res.getString(R.string.inv_service_period),
                    date(inv.periodStart) + " – " + date(inv.periodEnd)});
        }
        if (notEmpty(inv.dueDate)) {
            rows.add(new String[]{res.getString(R.string.inv_due_date), date(inv.dueDate)});
        }
        if (notEmpty(inv.buyer.identifier)) {
            rows.add(new String[]{res.getString(R.string.inv_customer_no), inv.buyer.identifier});
        }
        if (notEmpty(inv.buyerReference)) {
            rows.add(new String[]{res.getString(R.string.inv_your_reference), inv.buyerReference});
        }

        float right = PAGE_WIDTH - MARGIN;
        float valueWidth = 0f;
        for (String[] row : rows) valueWidth = Math.max(valueWidth, body.measureText(row[1]));
        float valueLeft = right - valueWidth;

        float ry = y - heading.getTextSize() + 4f;
        for (String[] row : rows) {
            c.drawText(row[0], valueLeft - 8f - body.measureText(row[0]), ry, small);
            c.drawText(row[1], valueLeft, ry, body);
            ry += LINE;
        }
        return Math.max(y, ry);
    }

    /**
     * Column geometry: each numeric column is as wide as its own header needs, measured rather
     * than guessed because the German labels are markedly longer than the English ones.
     *
     * <p>The right edges tile leftwards from the page margin, each one its own width clear of the
     * next. Getting that wrong by a single column is what made "Einzelpreis" -- the longest of the
     * four labels -- print over "Menge": it was being given the quantity column's width.
     */
    private float[] columns() {
        float right = PAGE_WIDTH - MARGIN;
        float amount = Math.max(60f, bold.measureText(res.getString(R.string.inv_col_amount)) + 12f);
        float vat = Math.max(34f, bold.measureText(res.getString(R.string.inv_col_vat)) + 12f);
        float unit = Math.max(58f,
                bold.measureText(res.getString(R.string.inv_col_unit_price)) + 12f);
        float qty = Math.max(42f, bold.measureText(res.getString(R.string.inv_col_qty)) + 12f);
        return columnEdges(right, MARGIN, MARGIN + 16f, qty, unit, vat, amount);
    }

    /**
     * The tiling itself, kept free of {@link Paint} and {@link android.content.res.Resources} so
     * the one part of this renderer that is arithmetic rather than drawing can be tested without a
     * device.
     */
    static float[] columnEdges(float right, float left, float descriptionLeft,
                               float qty, float unit, float vat, float amount) {
        return new float[]{
                left,                                     // 0 pos left
                descriptionLeft,                          // 1 description left
                right - amount - vat - unit - qty,        // 2 description right limit
                right - amount - vat - unit,              // 3 qty right
                right - amount - vat,                     // 4 unit-price right
                right - amount,                           // 5 vat right
                right,                                    // 6 amount right
        };
    }

    private float drawTable(Canvas c, EnInvoice inv, List<EnLine> lines, float y) {
        float[] col = columns();

        c.drawText(res.getString(R.string.inv_col_pos), col[0], y, bold);
        c.drawText(res.getString(R.string.inv_col_description), col[1] + 6f, y, bold);
        rightText(c, res.getString(R.string.inv_col_qty), col[3], y, bold);
        rightText(c, res.getString(R.string.inv_col_unit_price), col[4], y, bold);
        rightText(c, res.getString(R.string.inv_col_vat), col[5], y, bold);
        rightText(c, res.getString(R.string.inv_col_amount), col[6], y, bold);
        y += 5f;
        c.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule);
        y += LINE;

        for (EnLine line : lines) {
            c.drawText(nullToEmpty(line.id), col[0], y, body);
            float descriptionWidth = col[2] - col[1] - 20f;
            List<String> wrapped = wrap(describe(line), descriptionWidth, body);
            c.drawText(wrapped.get(0), col[1] + 6f, y, body);
            rightText(c, quantity(line), col[3], y, body);
            rightText(c, money.format(line.unitPriceCents / 100.0), col[4], y, body);
            rightText(c, Money.percent(line.ratePermille) + " %", col[5], y, body);
            rightText(c, money.format(line.netCents / 100.0), col[6], y, body);
            y += LINE;
            for (int i = 1; i < wrapped.size(); i++) {
                c.drawText(wrapped.get(i), col[1] + 6f, y, small);
                y += LINE - 3f;
            }
            y += 3f;
        }

        c.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule);
        return y;
    }

    private float drawTotals(Canvas c, EnInvoice inv, float y) {
        float right = PAGE_WIDTH - MARGIN;
        float labelRight = right - 90f;

        rightText(c, res.getString(R.string.inv_net), labelRight, y, body);
        rightText(c, money.format(inv.taxBasisCents / 100.0), right, y, body);
        y += LINE;

        for (TaxBreakdown g : inv.taxBreakdowns) {
            String label = res.getString(R.string.inv_vat) + " " + Money.percent(g.ratePermille)
                    + " %";
            rightText(c, label, labelRight, y, body);
            rightText(c, money.format(g.taxAmountCents / 100.0), right, y, body);
            y += LINE;
        }

        y += 2f;
        c.drawLine(labelRight - 60f, y, right, y, rule);
        y += LINE;
        rightText(c, res.getString(R.string.inv_total), labelRight, y, bold);
        rightText(c, money.format(inv.grandTotalCents / 100.0), right, y, bold);
        return y;
    }

    private float drawTaxNotes(Canvas c, EnInvoice inv, float y) {
        for (TaxBreakdown g : inv.taxBreakdowns) {
            if (notEmpty(g.exemptionReason)) {
                for (String line : wrap(g.exemptionReason, PAGE_WIDTH - 2 * MARGIN, body)) {
                    c.drawText(line, MARGIN, y, body);
                    y += LINE;
                }
            }
        }
        if (notEmpty(inv.note)) {
            for (String line : wrap(inv.note, PAGE_WIDTH - 2 * MARGIN, small)) {
                c.drawText(line, MARGIN, y, small);
                y += LINE - 3f;
            }
        }
        return y;
    }

    private void drawPayment(Canvas c, EnInvoice inv, float y) {
        if (!notEmpty(inv.iban)) return;
        c.drawText(res.getString(R.string.inv_payment_heading), MARGIN, y, bold);
        y += LINE;
        if (notEmpty(inv.dueDate)) {
            c.drawText(res.getString(R.string.inv_payable_by, date(inv.dueDate)), MARGIN, y, body);
            y += LINE;
        }
        c.drawText(labelled(R.string.inv_account_holder, inv.accountName), MARGIN, y, body);
        y += LINE;
        c.drawText(labelled(R.string.inv_iban, inv.iban), MARGIN, y, body);
        y += LINE;
        if (notEmpty(inv.bic)) {
            c.drawText(labelled(R.string.inv_bic, inv.bic), MARGIN, y, body);
            y += LINE;
        }
        if (notEmpty(inv.remittanceInformation)) {
            c.drawText(labelled(R.string.inv_reference, inv.remittanceInformation), MARGIN, y,
                    body);
        }
    }

    private void drawFooter(Canvas c, EnInvoice inv, int page, int pages) {
        float y = PAGE_HEIGHT - MARGIN + 18f;
        c.drawLine(MARGIN, y - 12f, PAGE_WIDTH - MARGIN, y - 12f, rule);

        StringBuilder left = new StringBuilder();
        left.append(nullToEmpty(inv.seller.name));
        if (notEmpty(inv.seller.vatId)) {
            left.append(" · ").append(res.getString(R.string.inv_vat_id)).append(' ')
                    .append(inv.seller.vatId);
        } else if (notEmpty(inv.seller.taxNumber)) {
            left.append(" · ").append(res.getString(R.string.inv_tax_number)).append(' ')
                    .append(inv.seller.taxNumber);
        }
        c.drawText(left.toString(), MARGIN, y, small);
        if (pages > 1) {
            rightText(c, res.getString(R.string.inv_page, page, pages), PAGE_WIDTH - MARGIN, y,
                    small);
        }
    }

    /** Splits lines across pages, leaving room for the totals block on the last one. */
    private List<List<EnLine>> paginate(List<EnLine> lines) {
        List<List<EnLine>> pages = new ArrayList<List<EnLine>>();
        List<EnLine> current = new ArrayList<EnLine>();
        // The first page loses roughly 300 points to the address and meta blocks.
        int capacity = 26;
        for (EnLine line : lines) {
            if (current.size() >= capacity) {
                pages.add(current);
                current = new ArrayList<EnLine>();
                capacity = 44;
            }
            current.add(line);
        }
        pages.add(current);
        return pages;
    }

    private String describe(EnLine line) {
        if (notEmpty(line.description)) return line.description;
        return nullToEmpty(line.name);
    }

    private String quantity(EnLine line) {
        String unit = "HUR".equals(line.unitCode) ? res.getString(R.string.inv_unit_hour)
                : res.getString(R.string.inv_unit_piece);
        return Money.quantity(line.quantityMilli) + " " + unit;
    }

    private String labelled(int labelRes, String value) {
        return res.getString(labelRes) + ": " + nullToEmpty(value);
    }

    private String date(String iso) {
        return Dates.forLanguage(iso, locale.getLanguage());
    }

    private static List<String> addressLines(EnParty p) {
        List<String> out = new ArrayList<String>(4);
        if (notEmpty(p.line1)) out.add(p.line1);
        if (notEmpty(p.line2)) out.add(p.line2);
        StringBuilder town = new StringBuilder();
        if (notEmpty(p.postcode)) town.append(p.postcode).append(' ');
        if (notEmpty(p.city)) town.append(p.city);
        if (town.length() > 0) out.add(town.toString().trim());
        if (notEmpty(p.countryCode) && !"DE".equalsIgnoreCase(p.countryCode)) out.add(p.countryCode);
        return out;
    }

    private static void rightText(Canvas c, String text, float right, float y, Paint paint) {
        c.drawText(text, right - paint.measureText(text), y, paint);
    }

    private static List<String> wrap(String text, float width, Paint paint) {
        List<String> out = new ArrayList<String>(2);
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        out.add(line.toString());
        return out;
    }

    private static Locale toLocale(String tag) {
        if (tag == null) return Locale.GERMAN;
        String t = tag.trim().toLowerCase(Locale.US);
        if (t.startsWith("en")) return Locale.ENGLISH;
        if (t.startsWith("es")) return new Locale("es");
        return Locale.GERMAN;
    }

    private static Resources localizedResources(Context ctx, Locale locale) {
        Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
        cfg.setLocale(locale);
        return ctx.createConfigurationContext(cfg).getResources();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
