package com.shaforostoff.rechnungsplaner.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PathsTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void recognisesOneFileReachedTwoWays() throws Exception {
        File dir = temp.newFolder("share");
        File direct = new File(dir, "contacts.zip");

        assertTrue(Paths.isSameFile(direct, new File(dir, "./contacts.zip")));
        assertTrue(Paths.isSameFile(direct, new File(dir, "sub/../contacts.zip")));
        assertTrue("a path need not exist to be compared",
                Paths.isSameFile(new File(dir, "gone.zip"), new File(dir, "gone.zip")));
    }

    @Test
    public void distinguishesTwoFilesOfTheSameName() throws Exception {
        File served = temp.newFolder("share");
        File archive = temp.newFolder("archive");

        assertFalse(Paths.isSameFile(new File(archive, "invoice.pdf"),
                new File(served, "invoice.pdf")));
    }

    @Test
    public void placesFilesInsideAndOutsideADirectory() throws Exception {
        File dir = temp.newFolder("share");

        assertTrue(Paths.isInside(dir, new File(dir, "contacts.zip")));
        assertTrue(Paths.isInside(dir, new File(dir, "nested/contacts.zip")));
        assertFalse("a crafted Uri must not walk out",
                Paths.isInside(dir, new File(dir, "../secret.db")));
        assertFalse("nor by a longer route",
                Paths.isInside(dir, new File(dir, "a/b/../../../secret.db")));
        assertFalse("the directory is not inside itself", Paths.isInside(dir, dir));
    }

    @Test
    public void doesNotMistakeANamePrefixForContainment() throws Exception {
        File dir = temp.newFolder("share");
        File sibling = temp.newFolder("share-old");

        assertFalse(Paths.isInside(dir, new File(sibling, "contacts.zip")));
    }

    @Test
    public void nullIsNeverTheSameFileAndNeverInside() throws Exception {
        File dir = temp.newFolder("share");

        assertFalse(Paths.isSameFile(null, new File(dir, "a")));
        assertFalse(Paths.isSameFile(new File(dir, "a"), null));
        assertFalse(Paths.isInside(null, new File(dir, "a")));
        assertFalse(Paths.isInside(dir, null));
    }

    /**
     * The failure the {@code isSameFile} guard in {@code Sharing.stage} exists to prevent.
     *
     * <p>Not a test of production code but of the platform behaviour it turns on: opening a file
     * for writing truncates it, so a copy whose source and target are one path destroys the
     * content before the first read. This is what emptied the contacts archive, and it is worth
     * pinning because the copy loop itself looks entirely correct.
     */
    @Test
    public void copyingAFileOntoItselfEmptiesIt() throws Exception {
        File file = temp.newFile("contacts.zip");
        OutputStream seed = new FileOutputStream(file);
        try {
            seed.write(new byte[4096]);
        } finally {
            seed.close();
        }
        assertEquals(4096L, file.length());

        copy(file, new File(file.getPath()));

        assertEquals("truncated by its own target before the read", 0L, file.length());
    }

    /** The body of {@code Sharing.stage}, reproduced so the hazard is exercised, not described. */
    private static void copy(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
