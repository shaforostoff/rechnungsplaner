package com.shaforostoff.rechnungsplaner.pdf;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The cross-reference information the packer needs: where the catalog lives, how many objects
 * exist, and which flavour of cross-reference section to append.
 *
 * <p>Both flavours are handled because both occur in the wild. Ghostscript writes a classic table
 * below PDF 1.5 and a cross-reference stream above it, and an appended section has to match what
 * the file already uses or readers reject the update.
 */
final class PdfTrailer {

    /** Byte offset of each directly-stored object. Compressed objects are absent. */
    final Map<Integer, Long> offsets = new HashMap<Integer, Long>();
    /** One past the highest object number in use. */
    int size;
    /** Object number of the document catalog. */
    int rootNumber = -1;
    /** Raw {@code /Info} reference, carried into the appended trailer. */
    String infoRef;
    /** Raw {@code /ID} array, carried into the appended trailer. */
    String idArray;
    /** Offset the appended section must point back to. */
    long startxref;
    /** True when the file uses cross-reference streams. */
    boolean usesXrefStream;

    static PdfTrailer parse(String pdf) throws UnsupportedPdfException {
        int marker = pdf.lastIndexOf("startxref");
        if (marker < 0) throw new UnsupportedPdfException("no startxref");

        PdfTrailer t = new PdfTrailer();
        int at = PdfSyntax.skipWhitespace(pdf, marker + "startxref".length());
        int end = at;
        while (end < pdf.length() && Character.isDigit(pdf.charAt(end))) end++;
        try {
            t.startxref = Long.parseLong(pdf.substring(at, end));
        } catch (NumberFormatException e) {
            throw new UnsupportedPdfException("unreadable startxref");
        }

        // Follow the /Prev chain so an already-incremented file still resolves. Sections are read
        // newest first and the first offset seen for an object wins, which is the correct override
        // order.
        long section = t.startxref;
        int guard = 0;
        while (section >= 0 && section < pdf.length() && guard++ < 64) {
            section = t.readSection(pdf, (int) section);
        }
        if (t.rootNumber < 0) throw new UnsupportedPdfException("no /Root");
        if (!t.offsets.containsKey(t.rootNumber)) {
            // The catalog sits inside an object stream. Rewriting it would mean rebuilding that
            // stream, so the caller degrades to a plain PDF plus a separate XML instead of risking
            // a corrupt hybrid.
            throw new UnsupportedPdfException("catalog is inside an object stream");
        }
        return t;
    }

    /** @return the {@code /Prev} offset to continue with, or -1 when the chain ends */
    private long readSection(String pdf, int at) throws UnsupportedPdfException {
        int i = PdfSyntax.skipWhitespace(pdf, at);
        return pdf.startsWith("xref", i) ? readClassic(pdf, i + 4) : readStream(pdf, i);
    }

    private long readClassic(String pdf, int i) throws UnsupportedPdfException {
        while (true) {
            i = PdfSyntax.skipWhitespace(pdf, i);
            if (pdf.startsWith("trailer", i)) break;
            if (i >= pdf.length() || !Character.isDigit(pdf.charAt(i))) {
                throw new UnsupportedPdfException("malformed xref table");
            }
            int firstEnd = i;
            while (Character.isDigit(pdf.charAt(firstEnd))) firstEnd++;
            int first = Integer.parseInt(pdf.substring(i, firstEnd));

            int countStart = PdfSyntax.skipWhitespace(pdf, firstEnd);
            int countEnd = countStart;
            while (countEnd < pdf.length() && Character.isDigit(pdf.charAt(countEnd))) countEnd++;
            int count = Integer.parseInt(pdf.substring(countStart, countEnd));

            i = PdfSyntax.skipWhitespace(pdf, countEnd);
            for (int n = 0; n < count; n++) {
                // Entries are nominally a fixed 20 bytes, but generators vary the line ending, so
                // parse by token rather than by offset arithmetic.
                int offEnd = i;
                while (offEnd < pdf.length() && Character.isDigit(pdf.charAt(offEnd))) offEnd++;
                long offset = Long.parseLong(pdf.substring(i, offEnd));
                int genStart = PdfSyntax.skipWhitespace(pdf, offEnd);
                int genEnd = genStart;
                while (genEnd < pdf.length() && Character.isDigit(pdf.charAt(genEnd))) genEnd++;
                int typeAt = PdfSyntax.skipWhitespace(pdf, genEnd);
                char type = pdf.charAt(typeAt);
                if (type == 'n') remember(first + n, offset);
                i = PdfSyntax.skipWhitespace(pdf, typeAt + 1);
            }
        }

        int dictAt = PdfSyntax.skipWhitespace(pdf, pdf.indexOf("trailer", i) + "trailer".length());
        String dict = PdfSyntax.dictAt(pdf, dictAt);
        absorb(dict);
        return PdfSyntax.longValue(dict, "/Prev", -1L);
    }

    private long readStream(String pdf, int i) throws UnsupportedPdfException {
        usesXrefStream = true;
        int dictAt = pdf.indexOf("<<", i);
        if (dictAt < 0) throw new UnsupportedPdfException("no xref stream dictionary");
        String dict = PdfSyntax.dictAt(pdf, dictAt);
        absorb(dict);

        int streamAt = pdf.indexOf("stream", PdfSyntax.endOfDict(pdf, dictAt));
        if (streamAt < 0) throw new UnsupportedPdfException("no xref stream data");
        int dataAt = streamAt + "stream".length();
        if (dataAt < pdf.length() && pdf.charAt(dataAt) == '\r') dataAt++;
        if (dataAt < pdf.length() && pdf.charAt(dataAt) == '\n') dataAt++;
        int length = PdfSyntax.intValue(dict, "/Length", -1);
        if (length < 0) throw new UnsupportedPdfException("xref stream without /Length");

        byte[] raw = PdfSyntax.toBytes(pdf.substring(dataAt, Math.min(pdf.length(), dataAt + length)));
        String filter = PdfSyntax.value(dict, "/Filter");
        byte[] data = filter != null && filter.contains("FlateDecode") ? inflate(raw) : raw;

        String parms = PdfSyntax.value(dict, "/DecodeParms");
        if (parms != null && parms.startsWith("<<")) {
            int predictor = PdfSyntax.intValue(parms, "/Predictor", 1);
            if (predictor >= 10) {
                data = undoPngPredictor(data, PdfSyntax.intValue(parms, "/Columns", 1));
            }
        }

        int[] widths = PdfSyntax.intArray(PdfSyntax.value(dict, "/W"));
        if (widths.length < 3) throw new UnsupportedPdfException("xref stream without /W");
        int[] index = PdfSyntax.intArray(PdfSyntax.value(dict, "/Index"));
        if (index.length == 0) index = new int[]{0, PdfSyntax.intValue(dict, "/Size", 0)};

        int rowLength = widths[0] + widths[1] + widths[2];
        if (rowLength == 0) throw new UnsupportedPdfException("zero-width xref rows");
        int pos = 0;
        for (int pair = 0; pair + 1 < index.length; pair += 2) {
            int first = index[pair];
            int count = index[pair + 1];
            for (int n = 0; n < count && pos + rowLength <= data.length; n++, pos += rowLength) {
                long type = widths[0] == 0 ? 1 : readField(data, pos, widths[0]);
                long f2 = readField(data, pos + widths[0], widths[1]);
                if (type == 1) remember(first + n, f2);
            }
        }
        return PdfSyntax.longValue(dict, "/Prev", -1L);
    }

    private void absorb(String dict) {
        if (size == 0) size = PdfSyntax.intValue(dict, "/Size", 0);
        if (rootNumber < 0) rootNumber = PdfSyntax.referenceNumber(PdfSyntax.value(dict, "/Root"));
        if (infoRef == null) infoRef = PdfSyntax.value(dict, "/Info");
        if (idArray == null) idArray = PdfSyntax.value(dict, "/ID");
    }

    private void remember(int number, long offset) {
        // Newer sections are read first, so never let an older one overwrite.
        if (!offsets.containsKey(number)) offsets.put(number, offset);
    }

    private static long readField(byte[] data, int at, int width) {
        long v = 0L;
        for (int i = 0; i < width; i++) v = (v << 8) | (data[at + i] & 0xFF);
        return v;
    }

    private static byte[] inflate(byte[] raw) throws UnsupportedPdfException {
        Inflater inflater = new Inflater();
        inflater.setInput(raw);
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 4);
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) break;
                out.write(buffer, 0, n);
            }
        } catch (DataFormatException e) {
            throw new UnsupportedPdfException("xref stream is not valid flate data");
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    /** Cross-reference streams are almost always PNG "up"-predicted; undo it row by row. */
    private static byte[] undoPngPredictor(byte[] data, int columns) {
        int rowLength = columns + 1;
        int rows = data.length / rowLength;
        byte[] out = new byte[rows * columns];
        byte[] previous = new byte[columns];
        for (int r = 0; r < rows; r++) {
            int tag = data[r * rowLength] & 0xFF;
            for (int c = 0; c < columns; c++) {
                int value = data[r * rowLength + 1 + c] & 0xFF;
                int left = c == 0 ? 0 : out[r * columns + c - 1] & 0xFF;
                int up = previous[c] & 0xFF;
                int restored;
                switch (tag) {
                    case 1: restored = value + left; break;
                    case 2: restored = value + up; break;
                    case 3: restored = value + ((left + up) / 2); break;
                    case 4: restored = value + paeth(left, up, 0); break;
                    default: restored = value;
                }
                out[r * columns + c] = (byte) restored;
            }
            System.arraycopy(out, r * columns, previous, 0, columns);
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }
}
