package org.supply.track;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class RouteViewExcelReader {

    public RouteView read(
            Path workbookPath,
            String sheetName,
            String routeId
    ) throws Exception {
        if (!Files.isRegularFile(workbookPath)) {
            throw new IllegalArgumentException(
                    "Track route workbook not found: "
                            + workbookPath
            );
        }

        try (InputStream input =
                     Files.newInputStream(workbookPath);
             Workbook workbook =
                     WorkbookFactory.create(input)) {

            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Track route workbook does not contain sheet '"
                                + sheetName
                                + "': "
                                + workbookPath
                );
            }

            Row header = sheet.getRow(sheet.getFirstRowNum());

            if (header == null) {
                throw new IllegalArgumentException(
                        "Empty track route sheet: " + sheetName
                );
            }

            Map<String, Integer> columns =
                    headerColumns(header);

            int cPath = required(columns, "position [m]");
            int cSection = required(columns, "trackSection");
            Integer cTrack = columns.get("trackNumber");
            int cKm = required(columns, "bisKm");
            int cMeter = required(columns, "bisMeter");
            Integer cRoute = columns.get("trackInformation");

            List<PathSample> samples = new ArrayList<>();
            List<KmRow> kmRows = new ArrayList<>();

            for (int rowIndex = header.getRowNum() + 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);
                if (row == null || blank(row.getCell(cPath))) {
                    continue;
                }

                double pathPositionM =
                        numeric(row.getCell(cPath), "position [m]", rowIndex);

                String sectionId =
                        identifier(row.getCell(cSection));

                String trackId =
                        cTrack == null ? "" : text(row.getCell(cTrack));
                if (trackId.isBlank()) {
                    trackId = null;
                }

                int bisKm =
                        integer(row.getCell(cKm), "bisKm", rowIndex);

                int bisMeter =
                        integer(row.getCell(cMeter), "bisMeter", rowIndex);

                String rowRouteId =
                        cRoute == null ? "" : text(row.getCell(cRoute));

                // The configured route ID is authoritative. Retain validation
                // for an explicitly supplied, nonblank route ID.
                if (!rowRouteId.isBlank() && !routeId.equals(rowRouteId)) {
                    throw new IllegalArgumentException(
                            "Unexpected trackInformation at row "
                                    + rowIndex
                                    + ": expected "
                                    + routeId
                                    + ", got "
                                    + rowRouteId
                    );
                }

                int railwayPositionM =
                        bisKm * 1000 + bisMeter;
                kmRows.add(new KmRow(sectionId, trackId, railwayPositionM,
                        bisMeter == 0, rowIndex + 1));

                samples.add(new PathSample(
                        pathPositionM,
                        new RwyCoordinate(
                                sectionId,
                                bisKm + "+" + bisMeter,
                                railwayPositionM,
                                trackId
                        )
                ));
            }

            validateKilometerBoards(kmRows, sheetName);
            return new RouteView(
                    routeId,
                    sheetName,
                    samples
            );
        }
    }

    private record KmRow(String section, String track, int positionM,
                         boolean board, int excelRow) { }

    /** Check each contiguous section/track interval, not unrelated visits. */
    private static void validateKilometerBoards(List<KmRow> rows, String sheet) {
        List<String> missing = new ArrayList<>();
        int start = 0;
        while (start < rows.size()) {
            KmRow first = rows.get(start);
            int end = start;
            int min = first.positionM();
            int max = min;
            Set<Integer> boards = new HashSet<>();
            while (end < rows.size()) {
                KmRow row = rows.get(end);
                if (!Objects.equals(first.section(), row.section())
                        || !Objects.equals(first.track(), row.track())) {
                    break;
                }
                min = Math.min(min, row.positionM());
                max = Math.max(max, row.positionM());
                if (row.board()) {
                    boards.add(Math.floorDiv(row.positionM(), 1000));
                }
                end++;
            }
            long firstKm = -Math.floorDiv(-(long) min, 1000L);
            long lastKm = Math.floorDiv((long) max, 1000L);
            for (long km = firstKm; km <= lastKm; km++) {
                if (!boards.contains((int) km)) {
                    missing.add(first.section() + " " + km + "+000"
                            + (first.track() == null ? "" : " " + first.track())
                            + " (Excel rows " + first.excelRow() + "-"
                            + rows.get(end - 1).excelRow() + ")");
                }
            }
            start = end;
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing km+000 rows in sheet '"
                    + sheet + "': " + String.join(", ", missing));
        }
    }

    private static Map<String, Integer> headerColumns(Row header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();

        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isEmpty()) {
                result.put(name, cell.getColumnIndex());
            }
        }

        return result;
    }

    private static int required(
            Map<String, Integer> columns,
            String name
    ) {
        Integer index = columns.get(name);

        if (index == null) {
            throw new IllegalArgumentException(
                    "Missing required track route column: " + name
            );
        }

        return index;
    }

    private static boolean blank(Cell cell) {
        return cell == null || text(cell).isBlank();
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }

        return new DataFormatter()
                .formatCellValue(cell)
                .trim();
    }

    private static String identifier(Cell cell) {
        String value = text(cell);

        if (value.endsWith(".0")) {
            return value.substring(
                    0,
                    value.length() - 2
            );
        }

        return value;
    }

    private static double numeric(
            Cell cell,
            String column,
            int rowIndex
    ) {
        try {
            if (cell != null
                    && cell.getCellType()
                    == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }

            return Double.parseDouble(
                    text(cell).replace(',', '.')
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid "
                            + column
                            + " at row "
                            + rowIndex,
                    exception
            );
        }
    }

    private static int integer(
            Cell cell,
            String column,
            int rowIndex
    ) {
        return Math.toIntExact(
                Math.round(
                        numeric(cell, column, rowIndex)
                )
        );
    }
}
