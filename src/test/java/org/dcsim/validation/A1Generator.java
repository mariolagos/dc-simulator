package org.dcsim.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class A1Generator {

    private static final Path OUTPUT_ROOT =
            Paths.get("project", "validationTests", "A1", "dc", "exports");

    private A1Generator() {}

    public static void main(String[] args) throws Exception {
        generateAll(OUTPUT_ROOT);
        System.out.println("A1 test package written to: " + OUTPUT_ROOT.toAbsolutePath());
    }

    public static void generateAll(Path root) throws IOException {
        Files.createDirectories(root);

        List<A1Case> cases = buildCases();
        List<String> manifest = new ArrayList<>();
        manifest.add("test_id,section,test_case,expected_behaviour,expected_error_code,case_dir");

        for (A1Case tc : cases) {
            Path caseDir = root.resolve(tc.testId);
            Files.createDirectories(caseDir);

            writeBaseline(caseDir);
            applyMutation(caseDir, tc.mutation);
            writeExpected(caseDir, tc);
            writeReadme(caseDir, tc);

            manifest.add(csv(
                    tc.testId,
                    "A1",
                    tc.testCase,
                    tc.expectedBehaviour,
                    tc.expectedErrorCode,
                    root.relativize(caseDir).toString().replace('\\', '/')
            ));
        }

        write(root.resolve("manifest.csv"), String.join("\n", manifest) + "\n");
    }

    private static void writeBaseline(Path dir) throws IOException {
        write(dir.resolve("nodes.csv"),
                "node_id,section_id,track_id,position_m\n" +
                        "N1,S1,T1,0.0\n" +
                        "N2,S1,T1,1000.0\n");

        write(dir.resolve("substations.csv"),
                "substation_id,emf_V,internal_resistance_ohm\n" +
                        "SS1,750.0,0.01\n");

        write(dir.resolve("substation_connections.csv"),
                "substation_id,node_id,connection_type\n" +
                        "SS1,N1,feed\n" +
                        "SS1,N2,return\n");

        write(dir.resolve("lines.csv"),
                "section_id,track_id,from_node_id,to_node_id,length_m,resistance_ohm_per_m\n" +
                        "S1,T1,N1,N2,1000.0,0.05\n");

        write(dir.resolve("run.csv"),
                "time_s,train_id,from_node_id,to_node_id,position_m,P_req_W\n" +
                        "0.0,Train1,N1,N2,0.0,0.0\n");
    }

    private static void applyMutation(Path dir, Mutation mutation) throws IOException {
        switch (mutation) {
            case MISSING_REQUIRED_FILE:
                Files.deleteIfExists(dir.resolve("lines.csv"));
                break;

            case INVALID_CSV_FORMAT:
                write(dir.resolve("run.csv"),
                        "time_s,train_id,from_node_id,to_node_id,position_m,P_req_W\n" +
                                "0.0,Train1,N1,N2,\"broken,0.0\n");
                break;

            case MISSING_REQUIRED_FIELD:
                removeColumn(dir.resolve("nodes.csv"), "section_id");
                break;

            case INVALID_CROSS_FILE_REFERENCE:
                replaceCell(dir.resolve("lines.csv"), 0, "to_node_id", "N999");
                break;

            default:
                throw new IllegalArgumentException("Unhandled mutation: " + mutation);
        }
    }

    private static void removeColumn(Path file, String col) throws IOException {
        Csv table = read(file);
        int index = table.header.indexOf(col);
        if (index < 0) {
            throw new IllegalArgumentException("Column not found: " + col + " in " + file);
        }

        table.header.remove(index);
        for (List<String> row : table.rows) {
            if (index < row.size()) {
                row.remove(index);
            }
        }

        writeCsv(file, table);
    }

    private static void replaceCell(Path file, int rowIndex, String col, String newValue) throws IOException {
        Csv table = read(file);
        int colIndex = table.header.indexOf(col);
        if (colIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + col + " in " + file);
        }
        if (rowIndex < 0 || rowIndex >= table.rows.size()) {
            throw new IllegalArgumentException("Row index out of range: " + rowIndex + " in " + file);
        }

        List<String> row = table.rows.get(rowIndex);
        if (colIndex >= row.size()) {
            throw new IllegalArgumentException("Column index out of range in row: " + col);
        }
        row.set(colIndex, newValue);

        writeCsv(file, table);
    }

    private static Csv read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty: " + file);
        }

        List<String> header = new ArrayList<>(Arrays.asList(lines.get(0).split(",", -1)));
        List<List<String>> rows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank()) {
                rows.add(new ArrayList<>(Arrays.asList(line.split(",", -1))));
            }
        }

        return new Csv(header, rows);
    }

    private static void writeCsv(Path file, Csv table) throws IOException {
        List<String> out = new ArrayList<>();
        out.add(csv(table.header.toArray(new String[0])));
        for (List<String> row : table.rows) {
            out.add(csv(row.toArray(new String[0])));
        }
        write(file, String.join("\n", out) + "\n");
    }

    private static void writeExpected(Path dir, A1Case tc) throws IOException {
        write(dir.resolve("expected.csv"),
                "test_id,expected_status,expected_error_code,expected_behaviour\n" +
                        csv(tc.testId, "REJECTED", tc.expectedErrorCode, tc.expectedBehaviour) + "\n");
    }

    private static void writeReadme(Path dir, A1Case tc) throws IOException {
        write(dir.resolve("README.txt"),
                "Test ID: " + tc.testId + "\n" +
                        "Section: A1\n" +
                        "Test Case: " + tc.testCase + "\n" +
                        "Expected Behaviour: " + tc.expectedBehaviour + "\n" +
                        "Expected Error Code: " + tc.expectedErrorCode + "\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.write(
                file,
                content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static String csv(String... values) {
        return String.join(",", values);
    }

    private static List<A1Case> buildCases() {
        return List.of(
                new A1Case(
                        "A1.1",
                        "Missing input file",
                        Mutation.MISSING_REQUIRED_FILE,
                        "Execution is rejected with clear missing file error",
                        "ERR_MISSING_FILE"
                ),
                new A1Case(
                        "A1.2",
                        "Invalid CSV format",
                        Mutation.INVALID_CSV_FORMAT,
                        "Execution is rejected with parse/format error",
                        "ERR_INVALID_CSV_FORMAT"
                ),
                new A1Case(
                        "A1.3",
                        "Missing required field",
                        Mutation.MISSING_REQUIRED_FIELD,
                        "Execution is rejected with field-level error",
                        "ERR_MISSING_REQUIRED_FIELD"
                ),
                new A1Case(
                        "A1.4",
                        "Invalid cross-file reference",
                        Mutation.INVALID_CROSS_FILE_REFERENCE,
                        "Execution is rejected with reference error",
                        "ERR_INVALID_REFERENCE"
                ),
                new A1Case(
                        "A1.5",
                        "Deterministic failure response",
                        Mutation.INVALID_CROSS_FILE_REFERENCE,
                        "Same failure is returned on each run",
                        "ERR_INVALID_REFERENCE"
                )
        );
    }

    private enum Mutation {
        MISSING_REQUIRED_FILE,
        INVALID_CSV_FORMAT,
        MISSING_REQUIRED_FIELD,
        INVALID_CROSS_FILE_REFERENCE
    }

    private static final class A1Case {
        final String testId;
        final String testCase;
        final Mutation mutation;
        final String expectedBehaviour;
        final String expectedErrorCode;

        A1Case(
                String testId,
                String testCase,
                Mutation mutation,
                String expectedBehaviour,
                String expectedErrorCode
        ) {
            this.testId = testId;
            this.testCase = testCase;
            this.mutation = mutation;
            this.expectedBehaviour = expectedBehaviour;
            this.expectedErrorCode = expectedErrorCode;
        }
    }

    private static final class Csv {
        final List<String> header;
        final List<List<String>> rows;

        Csv(List<String> header, List<List<String>> rows) {
            this.header = header;
            this.rows = rows;
        }
    }
}