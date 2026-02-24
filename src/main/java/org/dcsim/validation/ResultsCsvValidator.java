package org.dcsim.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates MATLAB-produced results_*.csv files with:
 * - schema/header checks (optional)
 * - invariants: non-empty, no NaN/Inf in numeric columns, allowed status values (if present)
 * <p>
 * Intentionally "semilong-friendly": does not require exact columns beyond those you configure.
 */
public final class ResultsCsvValidator {

    public static final class Rules {
        public final CsvSchema schema; // can be null if you don't want schema validation yet
        public final Set<String> requiredColumns; // must exist in header
        public final Set<String> numericColumnsToCheck; // if present, must parse finite doubles
        public final String statusColumn; // nullable
        public final Set<String> allowedStatuses; // used only if statusColumn present

        public Rules(CsvSchema schema,
                     Set<String> requiredColumns,
                     Set<String> numericColumnsToCheck,
                     String statusColumn,
                     Set<String> allowedStatuses) {
            this.schema = schema;
            this.requiredColumns = requiredColumns != null ? requiredColumns : Collections.<String>emptySet();
            this.numericColumnsToCheck = numericColumnsToCheck != null ? numericColumnsToCheck : Collections.<String>emptySet();
            this.statusColumn = statusColumn;
            this.allowedStatuses = allowedStatuses != null ? allowedStatuses : Collections.<String>emptySet();
        }
    }

    public void validate(Path resultsCsv, Rules rules) throws IOException {
        Objects.requireNonNull(resultsCsv, "resultsCsv");
        Objects.requireNonNull(rules, "rules");

        if (!Files.exists(resultsCsv)) {
            throw new ValidationInputException("Missing results file: " + resultsCsv.getFileName());
        }

        try (BufferedReader r = Files.newBufferedReader(resultsCsv, StandardCharsets.UTF_8)) {
            String headerLine = r.readLine();
            if (headerLine == null) {
                throw new ValidationInputException(resultsCsv.getFileName() + " is empty");
            }

            List<String> headers = normalizeHeaders(split(headerLine));
            if (rules.schema != null) {
                rules.schema.validateHeader(headers);
            }

            // Require columns
            for (String req : rules.requiredColumns) {
                if (!headers.contains(req)) {
                    throw new CsvSchemaException(resultsCsv.getFileName() + " missing required column: " + req);
                }
            }

            // Map column name -> index
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) idx.put(headers.get(i), i);

            // Determine which numeric columns we will check (only those present)
            List<String> numericToCheck = new ArrayList<>();
            for (String c : rules.numericColumnsToCheck) {
                if (idx.containsKey(c)) numericToCheck.add(c);
            }

            Integer statusIdx = null;
            if (rules.statusColumn != null && idx.containsKey(rules.statusColumn)) {
                statusIdx = idx.get(rules.statusColumn);
            }

            int rowIndex = 0;
            String line;
            while ((line = r.readLine()) != null) {
                rowIndex++;
                if (line.trim().isEmpty()) continue; // ignore blank lines

                List<String> fields = split(line);
                if (fields.size() != headers.size()) {
                    throw new ValidationInputException(resultsCsv.getFileName()
                            + " row width mismatch at row=" + rowIndex
                            + " expected=" + headers.size() + " actual=" + fields.size());
                }

                // Numeric finite checks
                for (String col : numericToCheck) {
                    int i = idx.get(col);
                    String s = fields.get(i).trim();
                    if (s.isEmpty()) continue; // allow empty if MATLAB writes blanks; tighten later if needed

                    double v;
                    try {
                        v = Double.parseDouble(s);
                    } catch (NumberFormatException nfe) {
                        throw new ValidationInputException(resultsCsv.getFileName()
                                + " invalid number at row=" + rowIndex + " col=" + col + " value=" + s);
                    }
                    if (Double.isNaN(v) || Double.isInfinite(v)) {
                        throw new ValidationInputException(resultsCsv.getFileName()
                                + " non-finite number at row=" + rowIndex + " col=" + col + " value=" + s);
                    }
                }

                // Status checks (only if status column exists in file)
                if (statusIdx != null) {
                    String status = fields.get(statusIdx).trim();
                    if (!rules.allowedStatuses.isEmpty() && !rules.allowedStatuses.contains(status)) {
                        throw new ValidationInputException(resultsCsv.getFileName()
                                + " invalid status at row=" + rowIndex + " value=" + status
                                + " allowed=" + rules.allowedStatuses);
                    }
                }
            }

            if (rowIndex == 0) {
                throw new ValidationInputException(resultsCsv.getFileName() + " contains no data rows");
            }
        }
    }

    // --- helpers ---

    private static List<String> normalizeHeaders(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String h = raw.get(i).trim();
            // strip UTF-8 BOM on first header
            if (i == 0 && !h.isEmpty() && h.charAt(0) == '\uFEFF') {
                h = h.substring(1);
            }
            out.add(h);
        }
        return out;
    }

    // Minimal CSV split (OK when MATLAB writes simple CSV without quotes).
    // Upgrade later if you start seeing quoted fields with commas.
    private static List<String> split(String line) {
        String[] parts = line.split(",", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }
}
