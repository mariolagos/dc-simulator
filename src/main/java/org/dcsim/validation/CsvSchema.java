package org.dcsim.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CsvSchema {

    private final String fileName;
    private final List<String> headers;

    public CsvSchema(String fileName, List<String> headers) {
        this.fileName = Objects.requireNonNull(fileName);
        this.headers = List.copyOf(headers);
    }

    public String fileName() {
        return fileName;
    }

    public List<String> headers() {
        return headers;
    }

    public static CsvSchema runSchema() {
        return new CsvSchema(
                "run.csv",
                List.of(
                        "time_s",
                        "train_id",
//                        "section",
//                        "track",
                        "position_m",
                        "P_req_W"
                )
        );
    }

    public void validate(List<Map<String, String>> rows) {
        Objects.requireNonNull(rows, "rows");

        if (rows.isEmpty()) {
            throw new ValidationInputException(fileName + " schema mismatch: empty file");
        }

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            for (String h : headers) {
                if (!row.containsKey(h)) {
                    throw new ValidationInputException(
                            fileName + " schema mismatch: missing header '" + h + "'"
                    );
                }
            }
        }
    }

    public void validateHeader(List<String> headers) {
        Objects.requireNonNull(headers, "headers");

        if (headers.size() != this.headers.size()) {
            throw new ValidationInputException(
                    fileName + " header mismatch: expected "
                            + this.headers + " but got " + headers
            );
        }

        for (int i = 0; i < this.headers.size(); i++) {
            String expected = this.headers.get(i);
            String actual = headers.get(i);
            if (!expected.equals(actual)) {
                throw new ValidationInputException(
                        fileName + " header mismatch at col " + i
                                + ": expected '" + expected
                                + "' but got '" + actual + "'"
                );
            }
        }
    }
}