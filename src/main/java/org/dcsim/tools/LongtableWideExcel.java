package org.dcsim.tools;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads all *.csv pivot files in a directory and produces a wide Excel:
 * - One sheet per CSV (e.g. trains, subs, nodes, lines)
 * - Columns: time_s, <id>.<signal1>, <id>.<signal2>, ...
 *
 * Assumptions:
 * - First column is time (e.g. "time_s")
 * - Second column is some id (e.g. train_id, sub_id, node_id, line_id)
 * - Remaining columns are numeric signals.
 */
public final class LongtableWideExcel {

    public static void main(String[] args) throws Exception {
        String pivotDir = (args.length >= 1)
                ? args[0]
                : "project/3subs2train2/scenario1/pivot";
        String outPath = (args.length >= 2)
                ? args[1]
                : pivotDir + "/wide.xlsx";

        Path dir = Paths.get(pivotDir);
        if (!Files.isDirectory(dir)) {
            System.err.println("Pivot directory does not exist: " + dir.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("[WideExcel] Reading pivot CSVs from: " + dir.toAbsolutePath());
        System.out.println("[WideExcel] Output Excel: " + Paths.get(outPath).toAbsolutePath());

        try (Workbook wb = new XSSFWorkbook()) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
                for (Path csv : stream) {
                    String baseName = stripExt(csv.getFileName().toString());
                    // Begränsa ark-namn till 31 tecken för Excel
                    String sheetName = baseName.length() > 31
                            ? baseName.substring(0, 31)
                            : baseName;
                    System.out.println("[WideExcel] Processing " + csv.getFileName() +
                            " -> sheet '" + sheetName + "'");
                    Sheet sheet = wb.createSheet(sheetName);
                    buildWideSheetFromCsv(csv, sheet);
                }
            }

            try (OutputStream out = Files.newOutputStream(Paths.get(outPath))) {
                wb.write(out);
            }
        }

        System.out.println("[WideExcel] Done.");
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }

    /**
     * Build a wide sheet from a single pivot CSV.
     */
    private static void buildWideSheetFromCsv(Path csv, Sheet sheet) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        if (lines.isEmpty()) {
            System.out.println("[WideExcel]   (empty file, skipping)");
            return;
        }

        // --- 1) Parse header ---
        String headerLine = lines.get(0);
        String[] colNames = splitLine(headerLine);
        if (colNames.length < 3) {
            System.out.println("[WideExcel]   (too few columns, skipping)");
            return;
        }

        int idxTime = 0;      // first column
        int idxId   = 1;      // second column
        // remaining columns are signals
        List<Integer> signalIdx = new ArrayList<>();
        List<String> signalNames = new ArrayList<>();
        for (int i = 2; i < colNames.length; i++) {
            signalIdx.add(i);
            signalNames.add(colNames[i].trim());
        }

        // --- 2) Collect data: time -> id -> signal -> value ---
        TreeSet<Double> times = new TreeSet<>();
        TreeSet<String> ids = new TreeSet<>();
        // key: time|id|signal
        Map<String, Double> values = new HashMap<>();

        for (int li = 1; li < lines.size(); li++) {
            String line = lines.get(li).trim();
            if (line.isEmpty()) continue;
            String[] f = splitLine(line);
            if (f.length < colNames.length) {
                // pad missing columns
                f = Arrays.copyOf(f, colNames.length);
            }

            Double t = parseDouble(f[idxTime]);
            if (t == null) continue;
            String id = safe(f[idxId]);
            if (id.isEmpty()) continue;

            times.add(t);
            ids.add(id);

            for (int si = 0; si < signalIdx.size(); si++) {
                int cIdx = signalIdx.get(si);
                String sName = signalNames.get(si);
                Double v = parseDouble(get(f, cIdx));
                if (v == null) {
                    continue; // leave empty cell
                }
                String key = key(t, id, sName);
                values.put(key, v);
            }
        }

        // --- 3) Write header row ---
        int rowIdx = 0;
        Row header = sheet.createRow(rowIdx++);
        int col = 0;
        header.createCell(col++).setCellValue(colNames[idxTime].trim()); // "time_s" eller liknande

        List<String> idList = new ArrayList<>(ids);
        for (String id : idList) {
            for (String sName : signalNames) {
                String h = id + "." + sName;
                header.createCell(col++).setCellValue(h);
            }
        }

        // --- 4) Write data rows ---
        for (Double t : times) {
            Row r = sheet.createRow(rowIdx++);
            int c = 0;
            r.createCell(c++).setCellValue(t);

            for (String id : idList) {
                for (String sName : signalNames) {
                    String k = key(t, id, sName);
                    Double v = values.get(k);
                    if (v != null) {
                        r.createCell(c++).setCellValue(v);
                    } else {
                        c++; // leave empty
                    }
                }
            }
        }

        // Auto-size a few first columns (optional)
        for (int i = 0; i < Math.min(4, header.getLastCellNum()); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String[] splitLine(String line) {
        // support both comma and tab as separators
        return line.split("[,\t]");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String get(String[] arr, int idx) {
        return (idx >= 0 && idx < arr.length) ? arr[idx] : "";
    }

    private static Double parseDouble(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String key(Double t, String id, String signal) {
        return t + "|" + id + "|" + signal;
    }
}
