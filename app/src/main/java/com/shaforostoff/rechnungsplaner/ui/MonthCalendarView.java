package com.shaforostoff.rechnungsplaner.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;

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
 *
 * <p>Paging follows the finger: a horizontal drag slides the grid and brings the neighbouring
 * month in behind it, and letting go either completes the move or snaps back. That is why the
 * loaded date range covers three months rather than one -- a month that arrives without its
 * markers and then sprouts them a moment later looks broken.
 */
public class MonthCalendarView extends View {

    /** Told when the user picks a day or pages to another month. */
    public interface Listener {
        void onDaySelected(String isoDate);

        void onMonthChanged(int year, int month);
    }

    private static final int ROWS = 6;
    private static final int COLUMNS = 7;

    /** Below this the drag snaps back rather than completing the month change. */
    private static final float COMMIT_FRACTION = 0.33f;
    private static final int SETTLE_MIN_MS = 130;
    private static final int SETTLE_MAX_MS = 260;

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

    /** How far the grid is currently shifted from its resting place, in pixels. */
    private float dragX;
    private boolean dragging;
    private ValueAnimator settle;

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
                // A tap landing while the grid is mid-slide would pick the day now under the
                // finger using coordinates for a month that is about to change.
                if (isSettling()) return false;
                if (!selectAt(e.getX(), e.getY())) return false;
                // Routing through performClick is what lets accessibility services announce the
                // tap; handling it only in the gesture detector makes the grid silent to them.
                performClick();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent down, MotionEvent move, float distanceX,
                                    float distanceY) {
                if (!dragging) {
                    // A mostly-vertical drag belongs to the scrolling page around us, and asking
                    // for it would trap the finger in the calendar.
                    if (Math.abs(distanceX) <= Math.abs(distanceY)) return false;
                    dragging = true;
                    cancelSettle();
                    // Once the horizontal drag is ours, stop the ScrollView taking it over the
                    // moment the finger drifts off the horizontal.
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                // Clamped to one screen either way: only one neighbour is drawn, so dragging
                // further would expose blank space.
                dragX = clamp(dragX - distanceX);
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX,
                                   float velocityY) {
                if (down == null || up == null) return false;
                if (Math.abs(velocityX) < Math.abs(velocityY)) return false;
                dragging = false;
                // A flick completes the month change however short the drag was: the gesture says
                // "next", and the distance travelled is beside the point.
                // Flinging left means the content moves left, revealing the next month.
                settleTo(velocityX < 0 ? -1 : 1);
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
        // "Today", or selecting a date in another month, jumps rather than slides -- so drop any
        // drag or animation in progress instead of landing at an offset.
        cancelSettle();
        dragX = 0f;
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

    /**
     * The inclusive date range that can appear on screen, for querying gigs in one go.
     *
     * <p>Three months rather than one: a drag brings a neighbour into view before it becomes the
     * current month, and it has to arrive with its markers already drawn.
     */
    public String firstVisibleDate() {
        return Dates.iso(previousMonthYear(), previousMonth(), 1);
    }

    public String lastVisibleDate() {
        int y = nextMonthYear();
        int m = nextMonth();
        return Dates.iso(y, m, Dates.daysInMonth(y, m));
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
        // The detector first, so a fling has already decided what to do before the release below
        // gets a chance to settle the same drag a second time.
        boolean handled = gestures.onTouchEvent(event);
        int action = event.getActionMasked();
        if (dragging && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            dragging = false;
            int width = getWidth();
            // A cancel is the gesture being taken away rather than finished, so it snaps back
            // however far the drag had got.
            boolean commit = action == MotionEvent.ACTION_UP && width > 0
                    && Math.abs(dragX) > width * COMMIT_FRACTION;
            settleTo(!commit ? 0 : dragX > 0f ? 1 : -1);
            handled = true;
        }
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float columnWidth = getWidth() / (float) COLUMNS;

        // The weekday row is the same for every month, so it stays put while the grid slides
        // underneath it.
        for (int c = 0; c < COLUMNS; c++) {
            canvas.drawText(weekdayInitials[c], (c + 0.5f) * columnWidth,
                    headerPaint.getTextSize() * 1.3f, headerPaint);
        }

        drawMonth(canvas, year, month, dragX);
        // Only the neighbour the drag is uncovering needs drawing, and it sits exactly one screen
        // away from the current one.
        if (dragX > 0f) {
            drawMonth(canvas, previousMonthYear(), previousMonth(), dragX - getWidth());
        } else if (dragX < 0f) {
            drawMonth(canvas, nextMonthYear(), nextMonth(), dragX + getWidth());
        }
    }

    private void drawMonth(Canvas canvas, int year, int month, float offsetX) {
        float columnWidth = getWidth() / (float) COLUMNS;
        float headerHeight = headerPaint.getTextSize() * 2.2f;
        float rowHeight = (getHeight() - headerHeight) / ROWS;

        int leading = Dates.mondayBasedDayOfWeek(year, month, 1);
        int days = Dates.daysInMonth(year, month);
        float radius = Math.min(columnWidth, rowHeight) * 0.38f;

        for (int day = 1; day <= days; day++) {
            int index = leading + day - 1;
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            float cx = offsetX + (column + 0.5f) * columnWidth;
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

    private boolean isSettling() {
        return settle != null && settle.isRunning();
    }

    private float clamp(float offset) {
        int width = getWidth();
        if (width <= 0) return 0f;
        return Math.max(-width, Math.min(width, offset));
    }

    /**
     * Animates the grid to rest.
     *
     * @param direction 0 to snap back to the current month, 1 to complete a move to the previous
     *                  month, -1 to the next -- matching the sign of the offset that reveals it
     */
    private void settleTo(final int direction) {
        cancelSettle();
        int width = getWidth();
        if (width <= 0) {
            dragX = 0f;
            invalidate();
            return;
        }

        float target = direction * (float) width;
        // Time proportional to the distance left, so a nearly-complete drag finishes promptly
        // instead of crawling the last few pixels.
        int duration = (int) (SETTLE_MIN_MS + (SETTLE_MAX_MS - SETTLE_MIN_MS)
                * Math.abs(target - dragX) / width);

        settle = ValueAnimator.ofFloat(dragX, target);
        settle.setDuration(duration);
        settle.setInterpolator(new DecelerateInterpolator());
        settle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dragX = ((Float) animation.getAnimatedValue()).floatValue();
                invalidate();
            }
        });
        settle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Done with it, and released before showMonth below reaches cancelSettle.
                settle = null;
                // Back to no offset first: the month that is now current draws in the same place
                // the outgoing one was sliding towards, so nothing jumps.
                dragX = 0f;
                if (direction == 0) {
                    invalidate();
                } else if (direction > 0) {
                    showMonth(previousMonthYear(), previousMonth());
                } else {
                    showMonth(nextMonthYear(), nextMonth());
                }
            }
        });
        settle.start();
    }

    private void cancelSettle() {
        if (settle == null) return;
        // Listeners off before cancelling: onAnimationEnd fires on cancel too, and it would
        // commit a month change the user has just grabbed back.
        settle.removeAllListeners();
        settle.cancel();
        settle = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSettle();
        super.onDetachedFromWindow();
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
