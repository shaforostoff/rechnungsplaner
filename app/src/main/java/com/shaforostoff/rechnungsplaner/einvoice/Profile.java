package com.shaforostoff.rechnungsplaner.einvoice;

/**
 * The BT-24 specification identifier, which is the only thing that distinguishes the formats the
 * user asked for. Both writers are parameterised by it, so one semantic model produces every
 * output.
 *
 * <p>Two things worth remembering here:
 *
 * <ul>
 *   <li>The XRechnung <em>patch</em> level is not part of the wire format. 3.0.1, 3.0.2 and 3.0.3
 *       all emit {@code xrechnung_3.0} and differ only in KoSIT's validation rules, so there is one
 *       constant for the whole 3.0 line. The namespace moved from {@code xoev-de:kosit:standard:}
 *       to {@code xeinkauf.de:kosit:} at 3.0.
 *   <li>The ZUGFeRD/Factur-X <em>version</em> (2.2, 2.3.3, 2.5.x) does not appear either. It picks
 *       the ruleset and the XMP {@code fx:ConformanceLevel} string, nothing more — which is why
 *       {@link #conformanceLevel} lives here and the PDF packer takes it from the profile.
 * </ul>
 */
public enum Profile {

    /** XRechnung 3.0.x, the German CIUS. Valid as bare XML and as the ZUGFeRD XRECHNUNG profile. */
    XRECHNUNG_30(
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0", true, "XRECHNUNG"),

    /** XRechnung 2.3, kept because easybill still lists it among the formats it accepts. */
    XRECHNUNG_23(
            "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_2.3",
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0", true, "XRECHNUNG"),

    /** Plain EN 16931, the ZUGFeRD "EN 16931" (Comfort) profile. Widest acceptance, fewest rules. */
    EN16931(
            "urn:cen.eu:en16931:2017",
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0", false, "EN 16931"),

    /** ZUGFeRD/Factur-X BASIC. */
    ZUGFERD_BASIC(
            "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic",
            null, false, "BASIC"),

    /** ZUGFeRD/Factur-X EXTENDED. */
    ZUGFERD_EXTENDED(
            "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended",
            null, false, "EXTENDED");

    /** BT-24. */
    public final String customizationId;
    /** BT-23, omitted when null. */
    public final String profileId;
    /** Whether the stricter BR-DE-* rules apply — chiefly the mandatory BT-10 buyer reference. */
    public final boolean xrechnungRules;
    /** {@code fx:ConformanceLevel} for the Factur-X XMP block in a hybrid PDF. */
    public final String conformanceLevel;

    Profile(String customizationId, String profileId, boolean xrechnungRules,
            String conformanceLevel) {
        this.customizationId = customizationId;
        this.profileId = profileId;
        this.xrechnungRules = xrechnungRules;
        this.conformanceLevel = conformanceLevel;
    }

    public static Profile fromName(String name, Profile fallback) {
        if (name != null) {
            for (Profile p : values()) {
                if (p.name().equals(name)) return p;
            }
        }
        return fallback;
    }
}
