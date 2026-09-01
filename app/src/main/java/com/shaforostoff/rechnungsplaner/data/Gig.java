package com.shaforostoff.rechnungsplaner.data;

/**
 * One DJ-set.
 *
 * <p>{@link #date} is stored separately from {@link #startMillis} on purpose. Sets routinely run
 * from 23:00 to 04:00, and the invoice must show the date the set <em>started</em>. Deriving that
 * from an instant makes the answer depend on the reader's time zone; storing it explicitly does
 * not.
 */
public class Gig {

    public enum Status {
        PLANNED, PLAYED, INVOICED, PAID;

        public static Status fromName(String name, Status fallback) {
            if (name != null) {
                for (Status s : values()) {
                    if (s.name().equals(name)) return s;
                }
            }
            return fallback;
        }
    }

    public long id = -1L;

    /** Local start date, {@code yyyy-MM-dd}. This is the Leistungsdatum on the invoice. */
    public String date;
    public long startMillis;
    public long endMillis;

    public long customerId = -1L;
    /** Defaulted from the customer, overridable when the same booker uses another venue. */
    public String placeName;
    public String city;

    public long feeCents;
    public long travelCents;
    /** Null means fall back to the customer's, then the issuer's, default. */
    public TaxMode taxMode;

    public String title;
    public String notes;
    public Status status = Status.PLANNED;

    /** Set once the gig has been billed. */
    public long invoiceId = -1L;

    /** The mirrored device-calendar event, or -1 when not mirrored. */
    public long calendarId = -1L;
    public long calendarEventId = -1L;
    /**
     * Stable identity written into the calendar event's description, so a restored database can
     * find its events again and an event copied elsewhere is still recognisable.
     */
    public String syncUuid;

    public boolean isMirrored() {
        return calendarEventId > 0L;
    }

    public boolean isInvoiced() {
        return invoiceId > 0L;
    }

    /** Fee plus travel, which is what the gig is worth before VAT. */
    public long totalNetCents() {
        return feeCents + travelCents;
    }
}
