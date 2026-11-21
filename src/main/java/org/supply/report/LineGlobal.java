package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ranks exported lines by cumulative energy loss for one study case. */
public final class LineGlobal {
    private static final String RESULT_SHEET = "Global Lines";
    private static final double J_PER_KWH = 3_600_000.0;
    private static final String[] RESULT_HEADERS = {
            "StudyId", "Calc Type", "rank", "line_id", "e_losses_kWh", "node_from_id", "node_to_id"
    };
    private static final String[] SOURCE_HEADERS = {
            "StudyId", "Calc Type", "project", "scenario", "base_hash", "generated_at"
    };

    private LineGlobal() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: GlobalLines <results_line.xlsx> <global_lines.xlsx> <StudyId> <CalcType> <n>");
        }
        write(Path.of(args[0]), Path.of(args[1]), args[2], args[3], Integer.parseInt(args[4]));
    }

    public static void write(Path source, Path destination, String studyId, String calcType, int n) throws IOException {
        if (studyId == null || studyId.isBlank() || calcType == null || calcType.isBlank()) {
            throw new IllegalArgumentException("StudyId and Calc Type must not be blank");
        }
        if (n < 1) throw new IllegalArgumentException("n must be positive");
        destination = destination.toAbsolutePath().normalize();
        if (source.toAbsolutePath().normalize().equals(destination)
                || (Files.exists(destination) && Files.isSameFile(source, destination))) {
            throw new IllegalArgumentException("Source and destination must be different files");
        }

        try (var stream = Files.newInputStream(source);
             Workbook input = WorkbookFactory.create(stream);
             Workbook output = new XSSFWorkbook()) {
            List<LineLoss> lines = readLosses(input);
            Map<String, Endpoints> endpoints = readEndpoints(input);
            Map<String, String> metadata = readMetadata(input);
            Sheet result = output.createSheet(RESULT_SHEET);
            Sheet metadataSheet = output.createSheet("Metadata");
            Sheet sources = output.createSheet("Sources");
            writeHeader(result, RESULT_HEADERS);
            writeHeader(sources, SOURCE_HEADERS);
            CellStyle energyStyle = output.createCellStyle();
            energyStyle.setDataFormat(output.createDataFormat().getFormat("0.000000"));

            int resultRow = 1;
            int sourceRow = 1;
            if (Files.exists(destination)) {
                try (var previousStream = Files.newInputStream(destination);
                     Workbook previous = WorkbookFactory.create(previousStream)) {
                    resultRow = copyCases(previous, result, RESULT_HEADERS, studyId, calcType, energyStyle);
                    sourceRow = copyCases(previous, sources, SOURCE_HEADERS, studyId, calcType, null);
                }
            }

            for (int i = 0; i < Math.min(n, lines.size()); i++) {
                LineLoss line = lines.get(i);
                Row summary = createCaseRow(result, resultRow++, studyId, calcType);
                summary.createCell(2).setCellValue(i + 1);
                summary.createCell(3).setCellValue(line.id());
                Cell energy = summary.createCell(4);
                energy.setCellValue(line.loss_J() / J_PER_KWH);
                energy.setCellStyle(energyStyle);
                Endpoints nodes = endpoints.get(line.id());
                if (nodes != null) {
                    summary.createCell(5).setCellValue(nodes.from());
                    summary.createCell(6).setCellValue(nodes.to());
                }
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

    private static List<LineLoss> readLosses(Workbook input) {
        List<LineLoss> lines = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String suffix = ".e_losses_J";
        for (Sheet sheet : input) {
            if ("Metadata".equals(sheet.getSheetName()) || "Lines".equals(sheet.getSheetName())) continue;
            Row header = sheet.getRow(0);
            if (header == null) continue;

            Map<String, Integer> columns = new LinkedHashMap<>();
            for (Cell cell : header) {
                if (cell.getCellType() == CellType.STRING) {
                    columns.put(cell.getStringCellValue(), cell.getColumnIndex());
                }
            }
            for (var column : columns.entrySet()) {
                String name = column.getKey();
                if (!name.endsWith(suffix)) continue;
                String id = name.substring(0, name.length() - suffix.length());
                if (id.isBlank() || !ids.add(id)) {
                    throw new IllegalArgumentException("Invalid or duplicate line ID: " + id);
                }
                Integer timeColumn = columns.get(id + ".time_s");
                if (timeColumn == null) {
                    throw new IllegalArgumentException("Missing time_s for line " + id);
                }
                Row last = null;
                for (int i = sheet.getLastRowNum(); i > 0; i--) {
                    Row row = sheet.getRow(i);
                    if (row != null && row.getCell(timeColumn, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) != null) {
                        requireNumber(row, timeColumn, id + ".time_s");
                        last = row;
                        break;
                    }
                }
                if (last == null) throw new IllegalArgumentException("No samples for line " + id);
                lines.add(new LineLoss(id, requireNumber(last, column.getValue(), name)));
            }
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("No line loss series found");
        lines.sort(Comparator.comparingDouble(LineLoss::loss_J).reversed().thenComparing(LineLoss::id));
        return lines;
    }

    private static Map<String, Endpoints> readEndpoints(Workbook workbook) {
        Sheet sheet = workbook.getSheet("Lines");
        if (sheet == null) return Map.of();

        Row header = sheet.getRow(0);
        if (header == null || !"line_id".equals(requireText(header, 0))
                || !"node_from_id".equals(requireText(header, 1))
                || !"node_to_id".equals(requireText(header, 2))) {
            throw new IllegalArgumentException("Incompatible header in Lines");
        }

        Map<String, Endpoints> result = new LinkedHashMap<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String id = requireText(row, 0);
            Endpoints nodes = new Endpoints(optionalText(row, 1), optionalText(row, 2));
            if (result.putIfAbsent(id, nodes) != null) {
                throw new IllegalArgumentException("Duplicate line ID in Lines: " + id);
            }
        }
        return result;
    }

    private static String optionalText(Row row, int index) {
        return row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) == null
                ? "" : requireText(row, index);
    }

    private record Endpoints(String from, String to) {}

    private record LineLoss(String id, double loss_J) {}

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
        int existingColumns = energyStyle != null && header != null && header.getLastCellNum() == 5
                ? 5 : headers.length;
        for (int i = 0; i < existingColumns; i++) {
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
            for (int i = 2; i < existingColumns; i++) {
                Cell cell = copy.createCell(i);
                if (energyStyle != null && i >= 5) {
                    cell.setCellValue(optionalText(row, i));
                } else if (energyStyle == null || i == 3) {
                    cell.setCellValue(requireText(row, i));
                } else {
                    cell.setCellValue(requireNumber(row, i, headers[i]));
                    if (i == 4) cell.setCellStyle(energyStyle);
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
                "Source: results_line.xlsx. The line ID is the prefix of the .e_losses_J column.",
                "Energy loss: final cumulative value divided by 3,600,000 J/kWh.",
                "Ranking: descending cumulative energy loss, then ascending line ID for equal losses.",
                "At most n lines are reported per StudyId and Calc Type. Re-running replaces that case.",
                "Metadata describes the latest source. Sources tracks individual cases."
        };
        for (int i = 0; i < notes.length; i++) sheet.createRow(i).createCell(0).setCellValue(notes[i]);
        sheet.setColumnWidth(0, 110 * 256);
    }

    private static void save(Workbook workbook, Path destination) throws IOException {
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "global-lines-", ".xlsx");
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
