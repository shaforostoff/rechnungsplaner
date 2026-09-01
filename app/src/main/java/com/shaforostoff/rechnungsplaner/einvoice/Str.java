package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * The two string helpers this package needs. {@code String.isBlank} is API 34 on Android and
 * {@code String.strip} is API 35, so neither is available at minSdk 24.
 */
final class Str {

    private Str() {
    }

    static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    static boolean isEmpty(String s) {
        return !notEmpty(s);
    }

    /** Returns the trimmed string, or null when it holds nothing worth writing. */
    static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
