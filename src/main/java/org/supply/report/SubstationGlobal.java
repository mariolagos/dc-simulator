package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds one global row per installation from results_installation.xlsx.
 *
 * Usage: SubstationGlobal <results_installation.xlsx> <substation_global.xlsx> <StudyId> <CalcType>
 *
 * Time-weighted statistics use each sample at t[i] for (t[i-1], t[i]].
 * The exporter must provide strictly increasing timestamps, but dt need not be constant.
 * Cumulative energy counters are differenced between the first and last samples.
 */
public final class SubstationGlobal {
    private static final double J_PER_KWH = 3_600_000.0;
    private static final Pattern COLUMN = Pattern.compile("^(.+)\\.(time_s|u_V|i_A|p_W|p_losses_W|e_supplied_J|e_absorbed_J|e_net_J)$");
    private static final String[] HEADERS = {
            "StudyId", "Calc Type", "substation_id", "start_time", "end_time", "duration_s",
            "e_supplied_kWh", "e_absorbed_kWh", "e_net_kWh",
            "p_mean_kW", "p_min_kW", "p_max_kW",
            "i_mean_A", "i_rms_A", "i_min_A", "i_max_A",
            "u_mean_V", "u_min_V", "u_max_V",
            "p_losses_mean_kW"
    };

    private SubstationGlobal() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: SubstationGlobal <results_installation.xlsx> <substation_global.xlsx> <StudyId> <CalcType>");
        }
        write(Path.of(args[0]), Path.of(args[1]), args[2], args[3]);
    }

    public static void write(Path source, Path destination, String studyId, String calcType) throws IOException {
        if (studyId == null || studyId.isBlank() || calcType == null || calcType.isBlank()) {
            throw new IllegalArgumentException("StudyId and Calc Type must not be blank");
        }
        try (var sourceStream = Files.newInputStream(source);
             Workbook input = WorkbookFactory.create(sourceStream);
             Workbook output = new XSSFWorkbook()) {
            Sheet result = output.createSheet("Substation Global");
            Row header = result.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) header.createCell(c).setCellValue(HEADERS[c]);

            CellStyle timeStyle = output.createCellStyle();
            timeStyle.setDataFormat(output.createDataFormat().getFormat("[h]:mm:ss"));
            CellStyle numberStyle = output.createCellStyle();
            numberStyle.setDataFormat(output.createDataFormat().getFormat("0.000"));

            int rowNumber = 1;
            for (int s = 0; s < input.getNumberOfSheets(); s++) {
                Sheet sheet = input.getSheetAt(s);
                Row first = sheet.getRow(0);
                if (first == null) continue;

                Map<String, Map<String, Integer>> groups = new LinkedHashMap<>();
                for (Cell cell : first) {
                    if (cell.getCellType() != CellType.STRING) continue;
                    Matcher m = COLUMN.matcher(cell.getStringCellValue().trim());
                    if (m.matches()) {
                        groups.computeIfAbsent(m.group(1), k -> new HashMap<>())
                                .put(m.group(2), cell.getColumnIndex());
                    }
                }
                for (Map.Entry<String, Map<String, Integer>> entry : groups.entrySet()) {
                    Map<String, Integer> cols = entry.getValue();
                    for (String required : List.of("time_s", "u_V", "i_A", "p_W",
                            "p_losses_W", "e_supplied_J", "e_absorbed_J", "e_net_J")) {
                        if (!cols.containsKey(required)) {
                            throw new IllegalArgumentException("Missing " + entry.getKey() + "." + required);
                        }
                    }
                    List<Sample> samples = new ArrayList<>();
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null || row.getCell(cols.get("time_s")) == null
                                || row.getCell(cols.get("time_s")).getCellType() == CellType.BLANK) continue;
                        samples.add(new Sample(
                                read(row, cols, "time_s"), read(row, cols, "u_V"),
                                read(row, cols, "i_A"), read(row, cols, "p_W"),
                                read(row, cols, "p_losses_W"), read(row, cols, "e_supplied_J"),
                                read(row, cols, "e_absorbed_J"), read(row, cols, "e_net_J")));
                    }
                    if (samples.size() < 2) throw new IllegalArgumentException(
                            "At least two samples required for " + entry.getKey());

                    Row target = result.createRow(rowNumber++);
                    target.createCell(0).setCellValue(studyId);
                    target.createCell(1).setCellValue(calcType);
                    writeSummary(target, entry.getKey(), samples, timeStyle, numberStyle);
                }
            }
            if (rowNumber == 1) throw new IllegalArgumentException("No installation time series found");
            result.createFreezePane(1, 1);
            for (int c = 0; c < HEADERS.length; c++) result.setColumnWidth(c, c < 3 ? 18 * 256 : 19 * 256);

            copyMetadata(input, output);

            Sheet method = output.createSheet("Method");
            String[] notes = {
                    "Source: results_installation.xlsx",
                    "StudyId and Calc Type are supplied by the user from the controlling Study cases document.",
                    "Metadata values are copied unchanged from the source results workbook.",
                    "One row per installation; all available samples are used.",
                    "Timestamps must be strictly increasing; sampling intervals may vary.",
                    "For interval (t[i-1],t[i]], the value at t[i] is used (right-endpoint convention).",
                    "Time-weighted mean: sum(x[i] * dt[i]) / sum(dt[i]).",
                    "Current RMS: sqrt(sum(i[i]^2 * dt[i]) / sum(dt[i])).",
                    "Energy: cumulative counter at final sample minus counter at initial sample.",
                    "p_mean_kW is calculated from e_net_J / duration, not from sampled p_W.",
                    "Min/max are extrema of the exported samples, not guaranteed internal solver extrema.",
                    "Start/end are Excel time values; format [h]:mm:ss, duration is seconds.",
                    "Power and current sign conventions are preserved from the simulator.",
                    "Verify counter semantics against the exporter, especially for sparse output."
            };
            for (int i = 0; i < notes.length; i++) method.createRow(i).createCell(0).setCellValue(notes[i]);
            method.setColumnWidth(0, 110 * 256);
            Path parent = destination.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (var stream = Files.newOutputStream(destination)) { output.write(stream); }
        }
    }

    private static void copyMetadata(Workbook source, Workbook destination) {
        Sheet original = source.getSheet("Metadata");
        if (original == null) {
            throw new IllegalArgumentException("Missing Metadata sheet in results_installation.xlsx");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Row row : original) {
            Cell key = row.getCell(0);
            Cell value = row.getCell(1);
            if (key != null && key.getCellType() == CellType.STRING
                    && value != null && value.getCellType() == CellType.STRING) {
                values.put(key.getStringCellValue(), value.getStringCellValue());
            }
        }
        for (String required : List.of("project", "scenario", "base_hash", "generated_at")) {
            if (!values.containsKey(required)) {
                throw new IllegalArgumentException("Missing metadata: " + required);
            }
        }
        Sheet metadata = destination.createSheet("Metadata");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Row row = metadata.createRow(index++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        metadata.autoSizeColumn(0);
        metadata.autoSizeColumn(1);
    }

    private static double read(Row row, Map<String, Integer> cols, String field) {
        Cell cell = row.getCell(cols.get(field));
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            throw new IllegalArgumentException("Expected numeric " + field + " at row " + (row.getRowNum() + 1));
        }
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + field);
        return value;
    }

    private static void writeSummary(Row row, String id, List<Sample> a,
                                     CellStyle timeStyle, CellStyle numberStyle) {
        Sample first = a.get(0), last = a.get(a.size() - 1);
        double duration = last.t - first.t;
        if (duration <= 0) throw new IllegalArgumentException("Invalid duration for " + id);

        double sumI = 0, sumI2 = 0, sumU = 0, sumLoss = 0;
        double minI = Double.POSITIVE_INFINITY, maxI = Double.NEGATIVE_INFINITY;
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        double minP = Double.POSITIVE_INFINITY, maxP = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < a.size(); k++) {
            Sample v = a.get(k);
            minI = Math.min(minI, v.i); maxI = Math.max(maxI, v.i);
            minU = Math.min(minU, v.u); maxU = Math.max(maxU, v.u);
            minP = Math.min(minP, v.p); maxP = Math.max(maxP, v.p);
            if (k == 0) continue;
            double dt = v.t - a.get(k - 1).t;
            if (dt <= 0) throw new IllegalArgumentException(
                    "Non-increasing time for " + id + " at sample " + k);
            sumI += v.i * dt;
            sumI2 += v.i * v.i * dt;
            sumU += v.u * dt;
            sumLoss += v.loss * dt;
        }
        row.createCell(2).setCellValue(id);
        set(row, 3, first.t / 86400.0, timeStyle);
        set(row, 4, last.t / 86400.0, timeStyle);
        set(row, 5, duration, numberStyle);
        double supplied = (last.supplied - first.supplied) / J_PER_KWH;
        double absorbed = (last.absorbed - first.absorbed) / J_PER_KWH;
        double net = (last.net - first.net) / J_PER_KWH;
        set(row, 6, supplied, numberStyle);
        set(row, 7, absorbed, numberStyle);
        set(row, 8, net, numberStyle);
        set(row, 9, net * 3600.0 / duration, numberStyle);
        set(row, 10, minP / 1000.0, numberStyle);
        set(row, 11, maxP / 1000.0, numberStyle);
        set(row, 12, sumI / duration, numberStyle);
        set(row, 13, Math.sqrt(sumI2 / duration), numberStyle);
        set(row, 14, minI, numberStyle);
        set(row, 15, maxI, numberStyle);
        set(row, 16, sumU / duration, numberStyle);
        set(row, 17, minU, numberStyle);
        set(row, 18, maxU, numberStyle);
        set(row, 19, sumLoss / duration / 1000.0, numberStyle);
    }

    private static void set(Row row, int index, double value, CellStyle style) {
        Cell c = row.createCell(index);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static final class Sample {
        final double t, u, i, p, loss, supplied, absorbed, net;
        Sample(double t, double u, double i, double p, double loss,
               double supplied, double absorbed, double net) {
            this.t = t; this.u = u; this.i = i; this.p = p; this.loss = loss;
            this.supplied = supplied; this.absorbed = absorbed; this.net = net;
        }
    }
}
