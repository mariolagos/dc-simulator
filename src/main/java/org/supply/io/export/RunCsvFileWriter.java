package org.supply.io.export;

import org.dcsim.validation.CsvSchema;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RunCsvFileWriter {

    private final CsvSchema schema;

    public RunCsvFileWriter(CsvSchema schema) {
        this.schema = schema;
    }

    public void write(
            Path outCsv,
            List<Map<String, String>> rows
    ) throws IOException {

        try (BufferedWriter w = Files.newBufferedWriter(outCsv)) {

            w.write(String.join(",", schema.headers()));
            w.newLine();

            for (Map<String, String> row : rows) {

                boolean first = true;

                for (String col : schema.headers()) {

                    if (!first) {
                        w.write(",");
                    }

                    String value = row.getOrDefault(col, "");
                    w.write(value);

                    first = false;
                }

                w.newLine();
            }
        }
    }
}