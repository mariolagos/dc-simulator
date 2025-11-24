package org.dcsim.pivot;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** CSV helpers with deterministic formatting. */
final class Csvs {
    static Writer newUtf8Writer(Path p) throws IOException {
        Files.createDirectories(p.getParent());
        return new OutputStreamWriter(Files.newOutputStream(p), StandardCharsets.UTF_8);
    }

    static CSVWriter openCsv(Path p) throws IOException {
        return new CSVWriter(newUtf8Writer(p));
    }

    static String f(double v) {
        // Deterministic US-locale formatting
        return String.format(Locale.US, "%.6f", v);
    }
}
