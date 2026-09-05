package org.supply.io.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RunCsvFromExcelTest {

    @Test
    public void usesBisPositionAndPreservesDirection()
            throws Exception {

        Path tempDir =
                Files.createTempDirectory(
                        "run-csv-from-excel-test"
                );

        Path ae = tempDir.resolve("A-E.xlsx");
        Path ea = tempDir.resolve("E-A.xlsx");

        writeWorkbook(ae, 0.500, 5.153);
        writeWorkbook(ea, 5.153, 0.500);

        List<Map<String, String>> up =
                RunCsvFromExcel.readFullRunRows(
                        ae,
                        "T_up",
                        "1",
                        "u",
                        "U",
                        0
                );

        List<Map<String, String>> down =
                RunCsvFromExcel.readFullRunRows(
                        ea,
                        "T_down",
                        "1",
                        "d",
                        "D",
                        0
                );

        assertEquals(
                500.0,
                position(up.get(0)),
                1e-9
        );
        assertEquals(
                5153.0,
                position(up.get(1)),
                1e-9
        );

        assertEquals(
                5153.0,
                position(down.get(0)),
                1e-9
        );
        assertEquals(
                500.0,
                position(down.get(1)),
                1e-9
        );

        assertTrue(
                position(up.get(1))
                        > position(up.get(0))
        );
        assertTrue(
                position(down.get(1))
                        < position(down.get(0))
        );
    }

    private static double position(
            Map<String, String> row
    ) {
        return Double.parseDouble(
                row.get("position_m")
        );
    }

    private static void writeWorkbook(
            Path path,
            double firstBisPosition,
            double lastBisPosition
    ) throws Exception {
        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            writeRunSheet(
                    workbook,
                    firstBisPosition,
                    lastBisPosition
            );
            writeTrackSheet(workbook);

            try (OutputStream out =
                         Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private static void writeRunSheet(
            XSSFWorkbook workbook,
            double firstBisPosition,
            double lastBisPosition
    ) {
        Sheet sheet =
                workbook.createSheet("+0sek");

        Row header = sheet.createRow(0);
        header.createCell(0)
                .setCellValue("time [s]");
        header.createCell(1)
                .setCellValue("position [m]");
        header.createCell(2)
                .setCellValue("bisPosition [km,m]");
        header.createCell(3)
                .setCellValue(
                        "primaryMotoringPower [kW]"
                );
        header.createCell(4)
                .setCellValue(
                        "primaryMotorBrakingPower [kW]"
                );

        addRunRow(
                sheet,
                1,
                0.0,
                0.0,
                firstBisPosition
        );
        addRunRow(
                sheet,
                2,
                1.0,
                4653.0,
                lastBisPosition
        );
    }

    private static void addRunRow(
            Sheet sheet,
            int rowNumber,
            double timeS,
            double runPositionM,
            double bisPosition
    ) {
        Row row = sheet.createRow(rowNumber);
        row.createCell(0).setCellValue(timeS);
        row.createCell(1)
                .setCellValue(runPositionM);
        row.createCell(2)
                .setCellValue(bisPosition);
        row.createCell(3).setCellValue(20.0);
        row.createCell(4).setCellValue(0.0);
    }

    private static void writeTrackSheet(
            XSSFWorkbook workbook
    ) {
        Sheet sheet =
                workbook.createSheet("track");

        Row header = sheet.createRow(0);
        header.createCell(0)
                .setCellValue("position [m]");
        header.createCell(1)
                .setCellValue("bisKm");
        header.createCell(2)
                .setCellValue("bisMeter");

        Row first = sheet.createRow(1);
        first.createCell(0).setCellValue(0.0);
        first.createCell(1).setCellValue(0);
        first.createCell(2).setCellValue(500.0);

        Row last = sheet.createRow(2);
        last.createCell(0).setCellValue(6000.0);
        last.createCell(1).setCellValue(6);
        last.createCell(2).setCellValue(0.0);
    }
}