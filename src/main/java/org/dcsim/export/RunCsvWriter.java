package org.dcsim.export;

import org.dcsim.validation.CsvSchema;
import org.dcsim.validation.ValidationInputException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RunCsvWriter {
    private final CsvSchema schema;

    public RunCsvWriter(CsvSchema schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    public void write(Path path, List<Map<String, String>> rows) throws IOException {
        schema.validate(rows);
        Objects.requireNonNull(path);
        Objects.requireNonNull(rows);

        Files.createDirectories(path.getParent());

        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // header
            writeRow(w, schema.headers());

            // rows
            int rowIndex = 0;
            for (Map<String, String> row : rows) {
                rowIndex++;
                List<String> values = new ArrayList<>(schema.headers().size());
                for (String h : schema.headers()) {
                    String v = row.get(h);
                    if (v == null) {
                        throw new ValidationInputException(schema.fileName()
                                + " missing value at row=" + rowIndex + " col=" + h);
                    }
                    values.add(v);
                }
                writeRow(w, values);
            }
        }
    }

    private static void writeRow(BufferedWriter w, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) w.write(',');
            w.write(escape(fields.get(i)));
        }
        w.write('\n');
    }

    private static String escape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
