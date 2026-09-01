package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * Amount, percentage and quantity formatting for EN 16931 payloads.
 *
 * <p>Money is a {@code long} count of cents everywhere in this app; no {@code double} ever touches
 * the money path. Every importer we target (Lexware Office, easybill, sevDesk) re-verifies the
 * arithmetic and rejects the invoice on a one-cent mismatch, so rounding happens in exactly one
 * place — {@link #taxOf} — and always half-up.
 *
 * <p>XML amounts are plain decimal: dot separator, exactly two fraction digits, no grouping, no
 * currency symbol. That is fixed by the standard and is deliberately independent of the invoice's
 * display language — only the PDF renderer localises numbers.
 */
public final class Money {

    private Money() {
    }

    /** BT-131 and friends: {@code 35000} to {@code "350.00"}. */
    public static String amount(long cents) {
        StringBuilder sb = new StringBuilder(16);
        long abs = cents;
        if (cents < 0) {
            sb.append('-');
            abs = -cents;
        }
        sb.append(abs / 100).append('.');
        long frac = abs % 100;
        if (frac < 10) sb.append('0');
        return sb.append(frac).toString();
    }

    /**
     * BT-119/BT-152 as a percentage with two decimals. Rates are held as permille so 7.5&nbsp;%
     * stays exact: {@code 190} to {@code "19.00"}, {@code 75} to {@code "7.50"}.
     */
    public static String percent(int permille) {
        long hundredths = permille * 10L;
        StringBuilder sb = new StringBuilder(8);
        long abs = hundredths;
        if (hundredths < 0) {
            sb.append('-');
            abs = -hundredths;
        }
        sb.append(abs / 100).append('.');
        long frac = abs % 100;
        if (frac < 10) sb.append('0');
        return sb.append(frac).toString();
    }

    /**
     * BT-129 from a milli-quantity, trailing zeros trimmed: {@code 1000} to {@code "1"},
     * {@code 1500} to {@code "1.5"}.
     */
    public static String quantity(long milli) {
        if (milli % 1000 == 0) return Long.toString(milli / 1000);
        StringBuilder sb = new StringBuilder(16);
        long abs = milli;
        if (milli < 0) {
            sb.append('-');
            abs = -milli;
        }
        sb.append(abs / 1000).append('.');
        long frac = abs % 1000;
        if (frac < 100) sb.append('0');
        if (frac < 10) sb.append('0');
        sb.append(frac);
        while (sb.charAt(sb.length() - 1) == '0') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * BT-117 for one VAT breakdown: {@code basis * rate}, rounded half-up to the cent.
     *
     * <p>Callers must pass the <em>already summed</em> basis of a whole BG-23 group. Rounding each
     * line and re-summing produces off-by-a-cent totals that BR-CO-17 rejects.
     */
    public static long taxOf(long basisCents, int ratePermille) {
        return roundHalfUp(basisCents * (long) ratePermille, 1000L);
    }

    /** Line net from quantity and unit price, rounded half-up to the cent. */
    public static long lineNet(long quantityMilli, long unitPriceCents) {
        return roundHalfUp(quantityMilli * unitPriceCents, 1000L);
    }

    /** Half-up division that rounds away from zero on a tie, matching commercial rounding. */
    public static long roundHalfUp(long numerator, long divisor) {
        if (divisor == 0) throw new ArithmeticException("divide by zero");
        boolean negative = (numerator < 0) != (divisor < 0);
        long n = Math.abs(numerator);
        long d = Math.abs(divisor);
        long q = (n + d / 2) / d;
        return negative ? -q : q;
    }
}
