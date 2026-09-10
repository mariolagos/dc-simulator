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
    public void usesRoutePositionInsteadOfBisPosition()
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
                        "+0sek",
                        "T_up",
                        "1",
                        "u",
                        "U",
                        0
                );

        List<Map<String, String>> down =
                RunCsvFromExcel.readFullRunRows(
                        ea,
                        "+0sek",
                        "T_down",
                        "1",
                        "d",
                        "D",
                        0
                );

        assertEquals(
                0.0,
                position(up.get(0)),
                1e-9
        );
        assertEquals(
                4653.0,
                position(up.get(1)),
                1e-9
        );

        assertEquals(
                0.0,
                position(down.get(0)),
                1e-9
        );
        assertEquals(
                4653.0,
                position(down.get(1)),
                1e-9
        );
    }

    @Test
    public void clipsRunToAbsoluteSimulationWindow()
            throws Exception {

        Path tempDir =
                Files.createTempDirectory(
                        "run-csv-time-window-test"
                );

        Path runExcel = tempDir.resolve("run.xlsx");
        Path runCsv = tempDir.resolve("run.csv");

        writeWorkbook(runExcel, 0.500, 5.153);

        RunCsvFromExcel.writeRunCsv(
                List.of(runExcel),
                List.of("+0sek"),
                List.of("T1"),
                List.of("1"),
                List.of("u"),
                List.of("U"),
                runCsv,
                List.of(36000),
                36004,
                36008,
                0.0
        );

        List<String> lines = Files.readAllLines(runCsv);

        assertEquals(3, lines.size());

        String[] first = lines.get(1).split(",", -1);
        String[] last = lines.get(2).split(",", -1);

        assertEquals(36004.0, Double.parseDouble(first[0]), 1e-9);
        assertEquals(1861.2, Double.parseDouble(first[5]), 1e-9);

        assertEquals(36008.0, Double.parseDouble(last[0]), 1e-9);
        assertEquals(3722.4, Double.parseDouble(last[5]), 1e-9);
    }

    @Test
    public void omitsRunOutsideSimulationWindow()
            throws Exception {

        Path tempDir =
                Files.createTempDirectory(
                        "run-csv-outside-window-test"
                );

        Path runExcel = tempDir.resolve("run.xlsx");
        Path runCsv = tempDir.resolve("run.csv");

        writeWorkbook(runExcel, 0.500, 5.153);

        RunCsvFromExcel.writeRunCsv(
                List.of(runExcel),
                List.of("+0sek"),
                List.of("T1"),
                List.of("1"),
                List.of("u"),
                List.of("U"),
                runCsv,
                List.of(36000),
                36100,
                36200,
                0.0
        );

        List<String> lines = Files.readAllLines(runCsv);

        assertEquals(1, lines.size());
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
                10.0,
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