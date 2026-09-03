package com.shaforostoff.rechnungsplaner.data;

/**
 * A kind of work the user sells: a DJ set, a haircut, a repair, an hour of tuition.
 *
 * <p>The app was written around one of these and now holds a list. The name is the user's own
 * words in their own language and is never translated -- it goes on the invoice line and into the
 * calendar event title, where a booker reads it, so guessing at a translation would put words the
 * user never chose in front of their customer.
 */
public class Service {

    public long id = -1L;
    public String name;
    /**
     * Work measured in days rather than hours.
     *
     * <p>A DJ set is an evening and wants a start and end time; a week of scaffolding or a month
     * of tuition wants an end date instead. Off by default, since the shorter kind is what the
     * app already assumed and the one that needs no explaining.
     */
    public boolean multiDay;
    /**
     * Whether jobs of this kind are mirrored into the chosen calendar.
     *
     * <p>On by default -- a booking you have to turn up to belongs in the calendar. It is worth
     * turning off for work that would only clutter it: a month-long retainer is not an
     * appointment, and a calendar full of week-long blocks hides the things that are.
     */
    public boolean syncToCalendar = true;
    /** Position among the buttons on the calendar screen; ties fall back to the name. */
    public int sortOrder;
    /**
     * Kept out of the buttons but not deleted.
     *
     * <p>A service still named by an existing job cannot be removed outright without that job
     * losing what it was, so it is archived instead -- the same bargain {@link Customer} strikes.
     */
    public boolean archived;

    public String displayName() {
        return name == null || name.trim().isEmpty() ? "?" : name.trim();
    }
}
