package com.shaforostoff.rechnungsplaner.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The line-table column tiling.
 *
 * <p>Written after "Einzelpreis" printed over "Menge": every column's right edge was one column's
 * width off, so each label was allocated its neighbour's space. The German labels are the ones that
 * expose it, being the longest of the three languages, and no test could see it because the widths
 * came from {@code Paint.measureText} on a device.
 *
 * <p>So the invariant is asserted directly instead: a right-aligned label at its column's edge must
 * have at least its column's width of room to its left, or it runs into the column before it.
 */
public class ColumnLayoutTest {

    private static final float RIGHT = 595f - 56f;
    private static final float LEFT = 56f;
    private static final float DESCRIPTION_LEFT = 72f;

    /** Roughly what 9.5pt bold sans measures for the German headers, which are the widest set. */
    private static final float QTY = 42f;      // "Menge"
    private static final float UNIT = 70f;     // "Einzelpreis" -- the long one
    private static final float VAT = 38f;      // "MwSt."
    private static final float AMOUNT = 60f;   // "Betrag"

    @Test
    public void everyColumnHasItsOwnWidthOfRoom() {
        float[] col = InvoiceRenderer.columnEdges(RIGHT, LEFT, DESCRIPTION_LEFT,
                QTY, UNIT, VAT, AMOUNT);

        assertEquals("quantity", QTY, col[3] - col[2], 0.01f);
        assertEquals("unit price", UNIT, col[4] - col[3], 0.01f);
        assertEquals("vat", VAT, col[5] - col[4], 0.01f);
        assertEquals("amount", AMOUNT, col[6] - col[5], 0.01f);
    }

    @Test
    public void theWidestHeaderStillClearsTheColumnBeforeIt() {
        float[] col = InvoiceRenderer.columnEdges(RIGHT, LEFT, DESCRIPTION_LEFT,
                QTY, UNIT, VAT, AMOUNT);

        // The width passed in already includes the padding the renderer adds, so the label itself
        // is narrower than its column by that much. Left edge of "Einzelpreis" against the right
        // edge of the quantity column is the collision that was reported.
        float unitLabelLeft = col[4] - (UNIT - 12f);
        assertTrue("Einzelpreis overlaps Menge", unitLabelLeft > col[3]);
    }

    @Test
    public void theNumbersStayInsideThePage() {
        float[] col = InvoiceRenderer.columnEdges(RIGHT, LEFT, DESCRIPTION_LEFT,
                QTY, UNIT, VAT, AMOUNT);

        assertEquals(RIGHT, col[6], 0.01f);
        assertTrue("the block must not reach into the description's left margin",
                col[2] > DESCRIPTION_LEFT);
        for (int i = 1; i < col.length; i++) {
            assertTrue("edge " + i + " must be right of edge " + (i - 1), col[i] > col[i - 1]);
        }
    }

    @Test
    public void aLongerLabelSetTakesRoomFromTheDescriptionOnly() {
        // Whatever the language, widening a numeric column may only eat into the description --
        // never into another number, which is what would produce overlapping text.
        float[] narrow = InvoiceRenderer.columnEdges(RIGHT, LEFT, DESCRIPTION_LEFT,
                QTY, UNIT, VAT, AMOUNT);
        float[] wide = InvoiceRenderer.columnEdges(RIGHT, LEFT, DESCRIPTION_LEFT,
                QTY, UNIT + 40f, VAT, AMOUNT);

        assertEquals("the amount column is anchored to the margin", narrow[6], wide[6], 0.01f);
        assertEquals("and so is everything right of the wider column",
                narrow[5], wide[5], 0.01f);
        assertTrue("the description gives up the space", wide[2] < narrow[2]);
        assertEquals("while the wider column keeps its full width",
                UNIT + 40f, wide[4] - wide[3], 0.01f);
    }
}
