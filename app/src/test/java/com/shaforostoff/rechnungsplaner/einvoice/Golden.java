package com.shaforostoff.rechnungsplaner.einvoice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Golden-file comparison. A missing golden is written and the test fails, so a new expectation is
 * always reviewed by a human before it becomes the baseline rather than silently blessed.
 */
final class Golden {

    private static final File DIR = resolveDir();

    /** AGP has moved the unit-test working directory between versions, so accept either root. */
    private static File resolveDir() {
        File local = new File("src/test/resources/golden");
        if (local.isDirectory()) return local;
        File fromRoot = new File("app/src/test/resources/golden");
        if (fromRoot.isDirectory()) return fromRoot;
        return local;
    }

    private Golden() {
    }

    static void assertMatches(String name, String actual) throws IOException {
        File expected = new File(DIR, name);
        if (!expected.exists()) {
            write(expected, actual);
            fail("golden " + name + " did not exist and has been written; review it and re-run");
        }
        String want = new String(Files.readAllBytes(expected.toPath()), StandardCharsets.UTF_8);
        if (!want.equals(actual)) {
            File got = new File(DIR, name + ".actual");
            write(got, actual);
            assertEquals("golden mismatch, actual written to " + got.getPath(), want, actual);
        }
    }

    private static void write(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
