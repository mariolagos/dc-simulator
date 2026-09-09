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
            int cTrack = required(columns, "trackNumber");
            int cKm = required(columns, "bisKm");
            int cMeter = required(columns, "bisMeter");
            int cRoute = required(columns, "trackInformation");

            List<PathSample> samples = new ArrayList<>();

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
                        text(row.getCell(cTrack));

                int bisKm =
                        integer(row.getCell(cKm), "bisKm", rowIndex);

                int bisMeter =
                        integer(row.getCell(cMeter), "bisMeter", rowIndex);

                String rowRouteId =
                        text(row.getCell(cRoute));

                if (!routeId.equals(rowRouteId)) {
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

            return new RouteView(
                    routeId,
                    sheetName,
                    samples
            );
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