package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summarizes the exported DC system energy balance for one study case. */
public final class SystemGlobal {
    private static final String INPUT_SHEET = "Power and energy balance";
    private static final String RESULT_SHEET = "System Global";
    private static final double J_PER_KWH = 3_600_000.0;
    private static final String[] ENERGY_FIELDS = {
            "e_substations_supplied", "e_substations_absorbed", "e_substations_net",
            "e_trains_consumed", "e_trains_regenerated", "e_trains_net",
            "e_fixed_loads_consumed", "e_losses", "e_balance"
    };
    private static final String[] SOURCE_HEADERS = {
            "StudyId", "Calc Type", "project", "scenario", "base_hash", "generated_at"
    };

    private SystemGlobal() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: SystemGlobal <results_system.xlsx> <system_global.xlsx> <StudyId> <CalcType>");
        }
        write(Path.of(args[0]), Path.of(args[1]), args[2], args[3]);
    }

    public static void write(Path source, Path destination, String studyId, String calcType) throws IOException {
        if (studyId == null || studyId.isBlank() || calcType == null || calcType.isBlank()) {
            throw new IllegalArgumentException("StudyId and Calc Type must not be blank");
        }
        destination = destination.toAbsolutePath().normalize();
        if (source.toAbsolutePath().normalize().equals(destination)
                || (Files.exists(destination) && Files.isSameFile(source, destination))) {
            throw new IllegalArgumentException("Source and destination must be different files");
        }

        try (var stream = Files.newInputStream(source);
             Workbook input = WorkbookFactory.create(stream);
             Workbook output = new XSSFWorkbook()) {
            double[] energy = readEnergy(input);
            Map<String, String> metadata = readMetadata(input);
            Sheet result = output.createSheet(RESULT_SHEET);
            Sheet metadataSheet = output.createSheet("Metadata");
            Sheet sources = output.createSheet("Sources");
            String[] resultHeaders = new String[2 + ENERGY_FIELDS.length];
            resultHeaders[0] = "StudyId";
            resultHeaders[1] = "Calc Type";
            for (int i = 0; i < ENERGY_FIELDS.length; i++) {
                resultHeaders[i + 2] = ENERGY_FIELDS[i] + "_kWh";
            }
            writeHeader(result, resultHeaders);
            writeHeader(sources, SOURCE_HEADERS);
            CellStyle energyStyle = output.createCellStyle();
            energyStyle.setDataFormat(output.createDataFormat().getFormat("0.000000"));

            int resultRow = 1;
            int sourceRow = 1;
            if (Files.exists(destination)) {
                try (var previousStream = Files.newInputStream(destination);
                     Workbook previous = WorkbookFactory.create(previousStream)) {
                    resultRow = copyCases(previous, result, resultHeaders, studyId, calcType, energyStyle);
                    sourceRow = copyCases(previous, sources, SOURCE_HEADERS, studyId, calcType, null);
                }
            }

            Row summary = createCaseRow(result, resultRow, studyId, calcType);
            for (int i = 0; i < energy.length; i++) {
                Cell cell = summary.createCell(i + 2);
                cell.setCellValue(energy[i]);
                cell.setCellStyle(energyStyle);
            }
            Row provenance = createCaseRow(sources, sourceRow, studyId, calcType);
            for (int i = 2; i < SOURCE_HEADERS.length; i++) {
                provenance.createCell(i).setCellValue(metadata.get(SOURCE_HEADERS[i]));
            }

            int index = 0;
            for (var entry : metadata.entrySet()) {
                Row row = metadataSheet.createRow(index++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }
            metadataSheet.setColumnWidth(0, 20 * 256);
            metadataSheet.setColumnWidth(1, 48 * 256);
            writeMethod(output);
            save(output, destination);
        }
    }

    private static double[] readEnergy(Workbook input) {
        Sheet sheet = requireSheet(input, INPUT_SHEET);
        Row header = sheet.getRow(0);
        if (header == null) throw new IllegalArgumentException("Missing system results header");

        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            if (cell.getCellType() == CellType.STRING) {
                String name = cell.getStringCellValue().trim();
                if (!name.isEmpty() && columns.putIfAbsent(name, cell.getColumnIndex()) != null) {
                    throw new IllegalArgumentException("Duplicate system results column: " + name);
                }
            }
        }
        int timeColumn = requireColumn(columns, "time_s");
        int[] energyColumns = new int[ENERGY_FIELDS.length];
        for (int i = 0; i < ENERGY_FIELDS.length; i++) {
            energyColumns[i] = requireColumn(columns, ENERGY_FIELDS[i] + "_J");
        }

        Row last = null;
        double previousTime = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(timeColumn, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) == null) {
                continue;
            }
            double time = requireNumber(row, timeColumn, "time_s");
            if (time <= previousTime) {
                throw new IllegalArgumentException("Non-increasing time_s at row " + (i + 1));
            }
            previousTime = time;
            last = row;
        }
        if (last == null) throw new IllegalArgumentException("No system results samples");

        double[] energy = new double[ENERGY_FIELDS.length];
        for (int i = 0; i < energy.length; i++) {
            energy[i] = requireNumber(last, energyColumns[i], ENERGY_FIELDS[i] + "_J") / J_PER_KWH;
        }
        return energy;
    }

    private static int requireColumn(Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) throw new IllegalArgumentException("Missing system results column: " + name);
        return index;
    }

    private static Map<String, String> readMetadata(Workbook input) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Row row : requireSheet(input, "Metadata")) {
            Cell key = row.getCell(0);
            Cell value = row.getCell(1);
            if (key != null && key.getCellType() == CellType.STRING
                    && value != null && value.getCellType() == CellType.STRING) {
                values.put(key.getStringCellValue(), value.getStringCellValue());
            }
        }
        for (int i = 2; i < SOURCE_HEADERS.length; i++) {
            if (!values.containsKey(SOURCE_HEADERS[i])) {
                throw new IllegalArgumentException("Missing metadata: " + SOURCE_HEADERS[i]);
            }
        }
        return values;
    }

    private static int copyCases(Workbook previous, Sheet target, String[] headers,
                                 String studyId, String calcType, CellStyle energyStyle) {
        Sheet old = requireSheet(previous, target.getSheetName());
        Row header = old.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            if (header == null || !headers[i].equals(requireText(header, i))) {
                throw new IllegalArgumentException("Incompatible header in " + old.getSheetName());
            }
        }

        int next = 1;
        for (Row row : old) {
            if (row.getRowNum() == 0) continue;
            String oldStudyId = requireText(row, 0);
            String oldCalcType = requireText(row, 1);
            if (studyId.equals(oldStudyId) && calcType.equals(oldCalcType)) continue;
            Row copy = createCaseRow(target, next++, oldStudyId, oldCalcType);
            for (int i = 2; i < headers.length; i++) {
                Cell cell = copy.createCell(i);
                if (energyStyle == null) {
                    cell.setCellValue(requireText(row, i));
                } else {
                    cell.setCellValue(requireNumber(row, i, headers[i]));
                    cell.setCellStyle(energyStyle);
                }
            }
        }
        return next;
    }

    private static Row createCaseRow(Sheet sheet, int index, String studyId, String calcType) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(studyId);
        row.createCell(1).setCellValue(calcType);
        return row;
    }

    private static void writeHeader(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
            sheet.setColumnWidth(i, Math.max(18, headers[i].length() + 2) * 256);
        }
        sheet.createFreezePane(2, 1);
    }

    private static Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) throw new IllegalArgumentException("Missing sheet: " + name);
        return sheet;
    }

    private static String requireText(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() != CellType.STRING) {
            throw new IllegalArgumentException("Expected text at row " + (row.getRowNum() + 1)
                    + ", column " + (index + 1));
        }
        return cell.getStringCellValue();
    }

    private static double requireNumber(Row row, int index, String field) {
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() != CellType.NUMERIC
                || !Double.isFinite(cell.getNumericCellValue())) {
            throw new IllegalArgumentException("Expected finite numeric " + field
                    + " at row " + (row.getRowNum() + 1));
        }
        return cell.getNumericCellValue();
    }

    private static void writeMethod(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Method");
        String[] notes = {
                "Source: results_system.xlsx, sheet Power and energy balance.",
                "One row per StudyId and Calc Type. Re-running a case replaces that case.",
                "Energy: final cumulative value including the first sample contribution, divided by 3,600,000 J/kWh.",
                "Net energy and balance residual are taken directly from the export, with their original signs.",
                "Losses are the combined losses exported in e_losses_J.",
                "Metadata describes the latest source. Sources tracks individual cases."
        };
        for (int i = 0; i < notes.length; i++) sheet.createRow(i).createCell(0).setCellValue(notes[i]);
        sheet.setColumnWidth(0, 110 * 256);
    }

    private static void save(Workbook workbook, Path destination) throws IOException {
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "system-global-", ".xlsx");
        try {
            try (var stream = Files.newOutputStream(temp)) {
                workbook.write(stream);
            }
            try {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
