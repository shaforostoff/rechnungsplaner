package com.shaforostoff.rechnungsplaner.pdf;

import java.io.ByteArrayOutputStream;

/**
 * Builds a minimal ICC v2 sRGB display profile for the PDF/A output intent.
 *
 * <p>PDF/A requires the output intent to carry an actual ICC destination profile, not a device
 * colour space. Rather than ship someone else's profile as an asset and inherit its licence, the
 * profile is generated: it is under a kilobyte, it is the same bytes on every device, and it can be
 * checked by a unit test.
 *
 * <p>The tone curves are a plain gamma 2.2 rather than the sRGB piecewise curve, because ICC v2
 * {@code curv} tags cannot express the piecewise form. That is a close approximation and a
 * conformant profile; it is not bit-exact sRGB. Nothing in an invoice is colour-critical -- the
 * page is black text on white -- so the approximation costs nothing that matters here.
 */
final class SrgbIcc {

    private SrgbIcc() {
    }

    /** sRGB primaries and white point, Bradford-adapted to the D50 profile connection space. */
    private static final double[] RED = {0.4360, 0.2225, 0.0139};
    private static final double[] GREEN = {0.3851, 0.7169, 0.0971};
    private static final double[] BLUE = {0.1431, 0.0606, 0.7141};
    private static final double[] WHITE = {0.9642, 1.0000, 0.8249};

    private static final String DESCRIPTION = "sRGB approximation";
    private static final String COPYRIGHT = "Public domain";

    private static byte[] cached;

    static synchronized byte[] profile() {
        if (cached == null) cached = build();
        return cached;
    }

    private static byte[] build() {
        String[] signatures = {"desc", "wtpt", "rXYZ", "gXYZ", "bXYZ", "rTRC", "gTRC", "bTRC",
                "cprt"};
        byte[][] tags = {
                textDescription(DESCRIPTION),
                xyz(WHITE), xyz(RED), xyz(GREEN), xyz(BLUE),
                gamma(2.2), gamma(2.2), gamma(2.2),
                text(COPYRIGHT),
        };

        int headerSize = 128;
        int tableSize = 4 + signatures.length * 12;
        int offset = headerSize + tableSize;

        int[] offsets = new int[tags.length];
        int[] sizes = new int[tags.length];
        for (int i = 0; i < tags.length; i++) {
            offsets[i] = offset;
            sizes[i] = tags[i].length;
            offset += align4(tags[i].length);
        }
        int total = offset;

        ByteArrayOutputStream out = new ByteArrayOutputStream(total);
        writeUint32(out, total);
        writeAscii(out, "    ");                 // preferred CMM: none
        writeUint32(out, 0x02100000);            // ICC version 2.1
        writeAscii(out, "mntr");                 // display device class
        writeAscii(out, "RGB ");
        writeAscii(out, "XYZ ");
        // A fixed creation date keeps the profile, and therefore every generated PDF, reproducible.
        writeUint16(out, 2026);
        writeUint16(out, 1);
        writeUint16(out, 1);
        writeUint16(out, 0);
        writeUint16(out, 0);
        writeUint16(out, 0);
        writeAscii(out, "acsp");
        writeUint32(out, 0);                     // platform
        writeUint32(out, 0);                     // flags
        writeUint32(out, 0);                     // manufacturer
        writeUint32(out, 0);                     // model
        writeUint32(out, 0);                     // attributes, high
        writeUint32(out, 0);                     // attributes, low
        writeUint32(out, 0);                     // rendering intent: perceptual
        writeS15Fixed16(out, WHITE[0]);
        writeS15Fixed16(out, WHITE[1]);
        writeS15Fixed16(out, WHITE[2]);
        writeUint32(out, 0);                     // creator
        for (int i = 0; i < 44; i++) out.write(0);

        writeUint32(out, signatures.length);
        for (int i = 0; i < signatures.length; i++) {
            writeAscii(out, signatures[i]);
            writeUint32(out, offsets[i]);
            writeUint32(out, sizes[i]);
        }

        for (byte[] tag : tags) {
            out.write(tag, 0, tag.length);
            for (int pad = tag.length; pad < align4(tag.length); pad++) out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] xyz(double[] v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(20);
        writeAscii(out, "XYZ ");
        writeUint32(out, 0);
        writeS15Fixed16(out, v[0]);
        writeS15Fixed16(out, v[1]);
        writeS15Fixed16(out, v[2]);
        return out.toByteArray();
    }

    private static byte[] gamma(double value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(14);
        writeAscii(out, "curv");
        writeUint32(out, 0);
        writeUint32(out, 1);                     // one entry means "this is a gamma value"
        writeUint16(out, (int) Math.round(value * 256.0));
        return out.toByteArray();
    }

    private static byte[] text(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() + 12);
        writeAscii(out, "text");
        writeUint32(out, 0);
        writeAscii(out, s);
        out.write(0);
        return out.toByteArray();
    }

    /** ICC v2 {@code textDescriptionType}: ASCII, then empty Unicode and ScriptCode sections. */
    private static byte[] textDescription(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() + 96);
        writeAscii(out, "desc");
        writeUint32(out, 0);
        writeUint32(out, s.length() + 1);
        writeAscii(out, s);
        out.write(0);
        writeUint32(out, 0);                     // Unicode language code
        writeUint32(out, 0);                     // Unicode count
        writeUint16(out, 0);                     // ScriptCode code
        out.write(0);                            // ScriptCode count
        for (int i = 0; i < 67; i++) out.write(0);
        return out.toByteArray();
    }

    private static int align4(int n) {
        return (n + 3) & ~3;
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < s.length(); i++) out.write(s.charAt(i) & 0x7F);
    }

    private static void writeUint16(ByteArrayOutputStream out, int v) {
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeUint32(ByteArrayOutputStream out, long v) {
        out.write((int) ((v >> 24) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) (v & 0xFF));
    }

    private static void writeS15Fixed16(ByteArrayOutputStream out, double v) {
        writeUint32(out, Math.round(v * 65536.0) & 0xFFFFFFFFL);
    }
}
