package org.dcsim.tools;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Läs longtable.csv och generera pivoter i en Excel-workbook.
 *
 * Indata-kolumner (förväntade):
 * time_s, project, scenario, base_hash, object_type, object_id, signal, value, unit, stage
 *
 * Utdata-flikar:
 * - Stations_V, Stations_I, Stations_P
 * - Lines_I, Lines_P
 * - Trains_Power (req_W, P_W)
 * - Trains_Kinematics (pos_m, speed_mps)
 * - Totals_Power_ByType
 * - Power_Balance (Node - (Train + Line))
 * - Inventory_Signals
 * - Meta_ProjectScenarioHash
 */
public class LongtablePivot {

    public static void main(String[] args) throws Exception {
        // CLI: in.csv [out.xlsx]
        String inCsv  = args.length > 0 ? args[0] : "output/longtable.csv";
        // Default: skriv alltid till output/pivots/pivots.xlsx (kan överskridas av args[1])
        String outXlsx = args.length > 1 ? args[1] : "output/pivots/pivots.xlsx";

        List<LTRow> rows = readLongtable(Path.of(inCsv));

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            DataFormat fmt = wb.createDataFormat();
            CellStyle timeStyle = wb.createCellStyle();
            timeStyle.setDataFormat(fmt.getFormat("0.########"));
            CellStyle valueStyle = wb.createCellStyle();
            valueStyle.setDataFormat(fmt.getFormat("0.########"));

            // ... (oförändrat pivotbyggande) ...

            // Spara workbook – se till att katalogen finns
            // --- Spara workbook atomiskt ---
            Path outPath = Path.of(outXlsx).toAbsolutePath();
            Path parent = outPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createDirectories(outPath.getParent());
            try (OutputStream os = Files.newOutputStream(outPath)) {
                wb.write(os);
            }

            // Skriv först till tempfil, byt sedan namn → minskar risk för korrupt fil
/*
            Path tmp = outPath.resolveSibling(outPath.getFileName().toString() + ".tmp");

            try (OutputStream os = Files.newOutputStream(
                    tmp,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE)) {
                wb.write(os);
                os.flush(); // säkerställ att allt är på disk
            }
            // Försök atomisk flytt där filsystemet stödjer det
            try {
                Files.move(tmp, outPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                // Fallback: vanlig flytt om ATOMIC_MOVE inte stöds
                Files.move(tmp, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
*/

            System.out.println("Wrote: " + outPath + " (size=" + Files.size(outPath) + " bytes)");
        }
    }

    /* ------------ Datamodell ------------ */
    static class LTRow {
        final double timeS;
        final String project;
        final String scenario;
        final String baseHash;
        final String objectType;
        final String objectId;
        final String signal;
        final double value;
        final String unit;
        final String stage;

        LTRow(double timeS, String project, String scenario, String baseHash, String objectType,
              String objectId, String signal, double value, String unit, String stage) {
            this.timeS = timeS;
            this.project = project;
            this.scenario = scenario;
            this.baseHash = baseHash;
            this.objectType = objectType;
            this.objectId = objectId;
            this.signal = signal;
            this.value = value;
            this.unit = unit;
            this.stage = stage;
        }
    }

    /* ------------ Läs CSV ------------ */
    static List<LTRow> readLongtable(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            CSVParser p = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(r);
            List<LTRow> out = new ArrayList<>();
            for (CSVRecord rec : p) {
                double time = parseDouble(rec.get("time_s"));
                String project = rec.get("project");
                String scenario = rec.get("scenario");
                String baseHash = rec.get("base_hash");
                String objectType = rec.get("object_type");
                String objectId = rec.get("object_id");
                String signal = rec.get("signal");
                double value = parseDouble(rec.get("value"));
                String unit = rec.isMapped("unit") ? rec.get("unit") : "";
                String stage = rec.isMapped("stage") ? rec.get("stage") : "";
                out.add(new LTRow(time, project, scenario, baseHash, objectType, objectId, signal, value, unit, stage));
            }
            return out;
        }
    }

    static double parseDouble(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        return Double.parseDouble(s.trim());
    }

    /* ------------ Pivot-hjälp ------------ */

    static class PivotResult {
        final SortedSet<Double> times;
        final SortedSet<String> columns;
        final Map<Double, Map<String, Double>> data; // time -> (col -> value)

        PivotResult(SortedSet<Double> times, SortedSet<String> columns, Map<Double, Map<String, Double>> data) {
            this.times = times;
            this.columns = columns;
            this.data = data;
        }
    }

    /** Pivot: kolumnnyckel = ett fält (t.ex. object_id). */
    static PivotResult pivot(List<LTRow> rows, Predicate<LTRow> filter, String columnKeyField,
                             CellStyle timeStyle, CellStyle valueStyle) {
        List<LTRow> filtered = rows.stream().filter(filter).toList();
        SortedSet<Double> times = new TreeSet<>(filtered.stream().map(r -> r.timeS).collect(Collectors.toSet()));
        SortedSet<String> columns = new TreeSet<>();
        Map<Double, Map<String, Double>> grid = new LinkedHashMap<>();
        for (Double t : times) grid.put(t, new LinkedHashMap<>());

        for (LTRow r : filtered) {
            String col = switch (columnKeyField) {
                case "object_id" -> r.objectId;
                case "signal" -> r.signal;
                case "object_type" -> r.objectType;
                default -> throw new IllegalArgumentException("Unknown column key: " + columnKeyField);
            };
            columns.add(col);
            grid.computeIfAbsent(r.timeS, k -> new LinkedHashMap<>()).put(col, r.value);
        }
        return new PivotResult(times, columns, grid);
    }

    /** Pivot med komposit kolumnnyckel (t.ex. objectId__signal). */
    static PivotResult pivotWithCompositeColumn(List<LTRow> rows, Predicate<LTRow> filter,
                                                java.util.function.Function<LTRow, String> columnKeyFn,
                                                CellStyle timeStyle, CellStyle valueStyle) {
        List<LTRow> filtered = rows.stream().filter(filter).toList();
        SortedSet<Double> times = new TreeSet<>(filtered.stream().map(r -> r.timeS).collect(Collectors.toSet()));
        SortedSet<String> columns = new TreeSet<>();
        Map<Double, Map<String, Double>> grid = new LinkedHashMap<>();
        for (Double t : times) grid.put(t, new LinkedHashMap<>());
        for (LTRow r : filtered) {
            String col = columnKeyFn.apply(r);
            columns.add(col);
            grid.computeIfAbsent(r.timeS, k -> new LinkedHashMap<>()).put(col, r.value);
        }
        return new PivotResult(times, columns, grid);
    }

    /* ------------ Excel-utskrift ------------ */

    static void writePivotSheet(Workbook wb, String name, PivotResult pivot,
                                CellStyle timeStyle, CellStyle valueStyle) {
        Sheet sh = wb.createSheet(trimSheetName(name));
        if (pivot.times.isEmpty() || pivot.columns.isEmpty()) {
            writeNoData(sh, name);
            autosize(sh, 1);
            return;
        }
        // Header
        List<String> headers = new ArrayList<>();
        headers.add("time_s");
        headers.addAll(pivot.columns);
        writeHeader(sh, headers, headerStyle(wb));

        int rowIdx = 1;
        for (Double t : pivot.times) {
            org.apache.poi.ss.usermodel.Row row = sh.createRow(rowIdx++);
            Cell c0 = row.createCell(0);
            c0.setCellValue(t);
            c0.setCellStyle(timeStyle);
            int colIdx = 1;
            Map<String, Double> vals = pivot.data.getOrDefault(t, Collections.emptyMap());
            for (String col : pivot.columns) {
                Cell c = row.createCell(colIdx++);
                Double v = vals.get(col);
                if (v != null && !v.isNaN() && !v.isInfinite()) {
                    c.setCellValue(v);
                    c.setCellStyle(valueStyle);
                } else {
                    c.setBlank();
                }
            }
        }
        autosize(sh, headers.size());
    }

    static void writePivotSheet(Workbook wb, String name, PivotResult pivot) {
        writePivotSheet(wb, name, pivot, defaultTimeStyle(wb), defaultValueStyle(wb));
    }

    static void writeNoData(Sheet sh, String name) {
        org.apache.poi.ss.usermodel.Row r = sh.createRow(0);
        r.createCell(0).setCellValue("No data for " + name);
    }

    static void writeHeader(Sheet sh, List<String> names, CellStyle style) {
        org.apache.poi.ss.usermodel.Row r = sh.createRow(0);
        for (int i = 0; i < names.size(); i++) {
            Cell c = r.createCell(i);
            c.setCellValue(names.get(i));
            if (style != null) c.setCellStyle(style);
        }
    }

    static void autosize(Sheet sh, int cols) {
        for (int i = 0; i < cols; i++) {
            try { sh.autoSizeColumn(i); } catch (Exception ignored) {}
        }
    }

    static String trimSheetName(String s) {
        String t = s;
        if (t.length() > 31) t = t.substring(0, 31);
        // ersätt ogiltiga tecken
        return t.replaceAll("[\\\\/?*\\[\\]:]", "_");
    }

    static CellStyle headerStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        return st;
    }

    static CellStyle defaultTimeStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setDataFormat(wb.createDataFormat().getFormat("0.########"));
        return st;
    }

    static CellStyle defaultValueStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setDataFormat(wb.createDataFormat().getFormat("0.########"));
        return st;
    }
}
