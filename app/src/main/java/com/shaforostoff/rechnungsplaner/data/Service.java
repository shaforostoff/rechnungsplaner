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
