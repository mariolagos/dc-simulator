package org.dcsim.validation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class A1Generator {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: A1Generator <A1_cases.csv> <outDir>");
            System.exit(1);
        }
        Path casesCsv = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        List<Map<String, String>> cases = readCsvAsMaps(casesCsv);

        // write manifest
        Path manifest = outDir.resolve("manifest.csv");
        try (BufferedWriter w = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            w.write("case_id,expected_status,expected_code\n");

            for (Map<String, String> c : cases) {
                String caseId = c.get("case_id");
                String mutation = c.get("mutation");
                String expectedStatus = c.get("expected_status");
                String expectedCode = c.get("expected_code");

                Path caseDir = outDir.resolve(caseId);
                Files.createDirectories(caseDir);

                // 1) Write baseline valid inputs
                writeBaseline(caseDir);

                // 2) Apply mutation (make it invalid in a controlled way)
                applyMutation(caseDir, mutation);

                // 3) Write expectation file for MATLAB dev
                writeExpectation(caseDir, expectedStatus, expectedCode, c.get("comment"));

                w.write(caseId + "," + expectedStatus + "," + expectedCode + "\n");
            }
        }
        System.out.println("Wrote A1 dataset to: " + outDir.toAbsolutePath());
    }

    private static void writeBaseline(Path d) throws IOException {
        // NAME RULES (important):
        // - All names are case-insensitive identifiers.
        // - Matching must be done after: trim + toUpperCase(Locale.ROOT)
        // - Duplicate names after normalization are invalid.

        // nodes.csv (SPEC): name,section,track,position_m
        write(d.resolve("nodes.csv"),
                "name,section,track,position_m\n" +
                        "N1,1,U,0.0\n" +
                        "N2,1,U,1000.0\n");

        // lines.csv (SPEC per your MHO): from_node,to_node,length_m,resistance_ohm
        // NOTE: from_node/to_node refer to nodes.name (case-insensitive).
        // We intentionally mix casing to assert case-insensitive behavior in MATLAB later.
        write(d.resolve("lines.csv"),
                "from_node,to_node,length_m,resistance_ohm\n" +
                        "n1,N2,1000.0,0.05\n");

        // substations.csv (SPEC): id,feed_nodes,return_node,emf,internal_resistance,rectifier_type
        // feed_nodes is a semicolon-separated list of node names.
        write(d.resolve("substations.csv"),
                "id,feed_nodes,return_node,emf,internal_resistance,rectifier_type\n" +
                        "S1,N1;N2,N1,750,0.01,DIODE\n");

        // run.csv (SPEC): time_s,train_id,section,track,position_m,P_req_W
        write(d.resolve("run.csv"),
                "time_s,train_id,section,track,position_m,P_req_W\n" +
                        "0,T1,1,U,0.0,0.0\n");
    }

    private static void applyMutation(Path d, String mutation) throws IOException {
        if ("UNKNOWN_NODE_REF".equals(mutation)) {
            // Broken reference: to_node refers to missing node name "N999"
            // Keep schema correct, break topology.
            write(d.resolve("lines.csv"),
                    "from_node,to_node,length_m,resistance_ohm\n" +
                            "N1,n999,1000.0,0.05\n");
            return;
        }

        if ("NEGATIVE_RESISTANCE".equals(mutation)) {
            // Broken parameter: negative resistance
            write(d.resolve("lines.csv"),
                    "from_node,to_node,length_m,resistance_ohm\n" +
                            "N1,N2,1000.0,-0.01\n");
            return;
        }

        throw new IllegalArgumentException("Unknown mutation: " + mutation);
    }

    private static void writeExpectation(Path d, String status, String code, String comment) throws IOException {
        write(d.resolve("expected.csv"),
                "status,error_code,comment\n" +
                        status + "," + code + "," + (comment == null ? "" : comment) + "\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    // Super-enkel CSV-läsare (utan quotes). Räcker för våra case-tabeller.
    private static List<Map<String, String>> readCsvAsMaps(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalArgumentException("Empty cases csv: " + file);
        String[] headers = lines.get(0).split(",");
        List<Map<String, String>> out = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String ln = lines.get(i).trim();
            if (ln.isEmpty()) continue;
            String[] vals = ln.split(",", -1);
            Map<String, String> m = new HashMap<>();
            for (int j = 0; j < headers.length && j < vals.length; j++) {
                m.put(headers[j].trim(), vals[j].trim());
            }
            out.add(m);
        }
        return out;
    }
}
