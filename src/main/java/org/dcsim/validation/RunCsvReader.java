package org.dcsim.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RunCsvReader {

    private final CsvSchema schema;

    public RunCsvReader(CsvSchema schema) {
        this.schema = schema;
    }

    public List<Map<String, String>> read(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

            String headerLine = r.readLine();
            if (headerLine == null) {
                throw new CsvSchemaException(schema.fileName() + " schema mismatch: empty file");
            }

            List<String> headers = parseHeader(headerLine);
            schema.validateHeader(headers);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            int rowIndex = 0;

            while ((line = r.readLine()) != null) {
                rowIndex++;
                List<String> values = split(line);

                if (values.size() != headers.size()) {
                    throw new ValidationInputException(
                            schema.fileName() + " row width mismatch at row=" + rowIndex);
                }

                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), values.get(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    // -------- helpers --------

    private List<String> parseHeader(String line) {
        List<String> raw = split(line);
        List<String> headers = new ArrayList<>(raw.size());

        for (int i = 0; i < raw.size(); i++) {
            String h = raw.get(i).trim();
            // strip UTF-8 BOM if present on first header
            if (i == 0 && !h.isEmpty() && h.charAt(0) == '\uFEFF') {
                h = h.substring(1);
            }
            headers.add(h);
        }
        return headers;
    }

    private static List<String> split(String line) {
        String[] parts = line.split(",", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }
}
