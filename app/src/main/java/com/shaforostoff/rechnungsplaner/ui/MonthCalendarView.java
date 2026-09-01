package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.shaforostoff.rechnungsplaner.util.Dates;

import java.util.Collections;
import java.util.Map;

/**
 * A month grid with a marker on days that have gigs.
 *
 * <p>Hand-drawn rather than pulled from a library, in the same spirit as the sibling projects'
 * custom views, and because the requirement is narrow: seven columns, a number per cell, a dot when
 * something is booked. A calendar library would be several hundred kilobytes for that.
 *
 * <p>Weeks start on Monday, which is what a German calendar looks like and is not what
 * {@link java.util.Calendar} numbers days as.
 */
public class MonthCalendarView extends View {

    /** Told when the user picks a day or pages to another month. */
    public interface Listener {
        void onDaySelected(String isoDate);

        void onMonthChanged(int year, int month);
    }

    private static final int ROWS = 6;
    private static final int COLUMNS = 7;

    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cell = new RectF();

    private String[] weekdayInitials = {"M", "T", "W", "T", "F", "S", "S"};
    private int year;
    private int month;
    private String selectedDate;
    private final String today = Dates.today();
    private Map<String, Integer> gigCounts = Collections.emptyMap();
    private Listener listener;

    private final GestureDetector gestures;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;
        dayPaint.setTextSize(14f * density);
        dayPaint.setColor(Color.BLACK);
        dayPaint.setTextAlign(Paint.Align.CENTER);

        headerPaint.setTextSize(11f * density);
        headerPaint.setColor(Color.parseColor("#777777"));
        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        markerPaint.setColor(Color.parseColor("#1A56DB"));
        selectionPaint.setColor(Color.parseColor("#E3ECFD"));
        todayPaint.setColor(Color.parseColor("#1A56DB"));
        todayPaint.setStyle(Paint.Style.STROKE);
        todayPaint.setStrokeWidth(1.5f * density);

        year = Dates.year(today);
        month = Dates.month(today);
        selectedDate = today;

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!selectAt(e.getX(), e.getY())) return false;
                // Routing through performClick is what lets accessibility services announce the
                // tap; handling it only in the gesture detector makes the grid silent to them.
                performClick();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX,
                                   float velocityY) {
                if (down == null || up == null) return false;
                if (Math.abs(velocityX) < Math.abs(velocityY)) return false;
                // A fling left means "next month", matching the direction the content moves.
                showMonth(velocityX < 0 ? nextMonthYear() : previousMonthYear(),
                        velocityX < 0 ? nextMonth() : previousMonth());
                return true;
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Weekday initials in the UI language, Monday first. */
    public void setWeekdayInitials(String[] initials) {
        if (initials != null && initials.length == COLUMNS) {
            this.weekdayInitials = initials;
            invalidate();
        }
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public String getSelectedDate() {
        return selectedDate;
    }

    public void setGigCounts(Map<String, Integer> counts) {
        this.gigCounts = counts == null ? Collections.<String, Integer>emptyMap() : counts;
        invalidate();
    }

    public void showMonth(int year, int month) {
        this.year = year;
        this.month = month;
        invalidate();
        if (listener != null) listener.onMonthChanged(year, month);
    }

    public void select(String isoDate) {
        if (!Dates.isValid(isoDate)) return;
        selectedDate = isoDate;
        int y = Dates.year(isoDate);
        int m = Dates.month(isoDate);
        if (y != year || m != month) {
            showMonth(y, m);
        } else {
            invalidate();
        }
        if (listener != null) listener.onDaySelected(isoDate);
    }

    /** The inclusive date range the grid currently covers, for querying gigs in one go. */
    public String firstVisibleDate() {
        return Dates.iso(year, month, 1);
    }

    public String lastVisibleDate() {
        return Dates.iso(year, month, Dates.daysInMonth(year, month));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        // Cells a little wider than tall keeps six rows on screen without the grid feeling cramped.
        int rowHeight = (int) (width / (float) COLUMNS * 0.82f);
        int headerHeight = (int) (headerPaint.getTextSize() * 2.2f);
        setMeasuredDimension(width, headerHeight + rowHeight * ROWS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float columnWidth = getWidth() / (float) COLUMNS;
        float headerHeight = headerPaint.getTextSize() * 2.2f;
        float rowHeight = (getHeight() - headerHeight) / ROWS;

        for (int c = 0; c < COLUMNS; c++) {
            canvas.drawText(weekdayInitials[c], (c + 0.5f) * columnWidth,
                    headerPaint.getTextSize() * 1.3f, headerPaint);
        }

        int leading = Dates.mondayBasedDayOfWeek(year, month, 1);
        int days = Dates.daysInMonth(year, month);
        float radius = Math.min(columnWidth, rowHeight) * 0.38f;

        for (int day = 1; day <= days; day++) {
            int index = leading + day - 1;
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            float cx = (column + 0.5f) * columnWidth;
            float cy = headerHeight + (row + 0.5f) * rowHeight;
            String date = Dates.iso(year, month, day);

            if (date.equals(selectedDate)) {
                cell.set(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawOval(cell, selectionPaint);
            }
            if (date.equals(today)) {
                cell.set(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawOval(cell, todayPaint);
            }

            float baseline = cy + dayPaint.getTextSize() * 0.35f;
            canvas.drawText(Integer.toString(day), cx, baseline, dayPaint);

            Integer count = gigCounts.get(date);
            if (count != null && count > 0) {
                // Up to three dots; beyond that the count stops being worth counting at a glance.
                int dots = Math.min(3, count.intValue());
                float dotRadius = radius * 0.13f;
                float spacing = dotRadius * 3f;
                float startX = cx - (dots - 1) * spacing / 2f;
                float dotY = cy + radius * 0.72f;
                for (int i = 0; i < dots; i++) {
                    canvas.drawCircle(startX + i * spacing, dotY, dotRadius, markerPaint);
                }
            }
        }
    }

    private boolean selectAt(float x, float y) {
        float columnWidth = getWidth() / (float) COLUMNS;
        float headerHeight = headerPaint.getTextSize() * 2.2f;
        float rowHeight = (getHeight() - headerHeight) / ROWS;
        if (y < headerHeight) return false;

        int column = (int) (x / columnWidth);
        int row = (int) ((y - headerHeight) / rowHeight);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) return false;

        int day = row * COLUMNS + column - Dates.mondayBasedDayOfWeek(year, month, 1) + 1;
        if (day < 1 || day > Dates.daysInMonth(year, month)) return false;

        select(Dates.iso(year, month, day));
        return true;
    }

    private int nextMonth() {
        return month == 12 ? 1 : month + 1;
    }

    private int nextMonthYear() {
        return month == 12 ? year + 1 : year;
    }

    private int previousMonth() {
        return month == 1 ? 12 : month - 1;
    }

    private int previousMonthYear() {
        return month == 1 ? year - 1 : year;
    }
}
