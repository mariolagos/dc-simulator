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

    @Test
    public void usesRunExcelRelativeDepartureWhenLegDepartureIsMissing()
            throws Exception {
        Path tempDir = Files.createTempDirectory("run-csv-leg-times-test");
        Path firstLeg = tempDir.resolve("A-B.xlsx");
        Path secondLeg = tempDir.resolve("B-C.xlsx");
        Path runCsv = tempDir.resolve("run.csv");

        writeWorkbookWithTimetableDeparture(firstLeg, 0.0, 10.0);
        writeWorkbookWithTimetableDeparture(secondLeg, 15.0, 10.0);

        RunCsvFromExcel.writeRunCsv(
                List.of(firstLeg, secondLeg),
                List.of("+0sek", "+0sek"),
                List.of("T1", "T1"),
                List.of("", ""),
                List.of("", ""),
                List.of("A-C", "A-C"),
                runCsv,
                List.of(36000, 36000),
                java.util.Arrays.asList(null, null),
                36000,
                36100,
                0.0
        );

        List<String> lines = Files.readAllLines(runCsv);
        assertEquals(5, lines.size());
        assertEquals(36000.0, time(lines.get(1)), 1e-9);
        assertEquals(36010.0, time(lines.get(2)), 1e-9);
        assertEquals(36015.0, time(lines.get(3)), 1e-9);
        assertEquals(36025.0, time(lines.get(4)), 1e-9);
        assertEquals(9306.0, csvPosition(lines.get(4)), 1e-9);
    }

    @Test
    public void acceptsNextLegStartingWhenPreviousLegEnds()
            throws Exception {
        Path tempDir = Files.createTempDirectory("run-csv-adjacent-legs-test");
        Path firstLeg = tempDir.resolve("A-B.xlsx");
        Path secondLeg = tempDir.resolve("B-C.xlsx");
        Path runCsv = tempDir.resolve("run.csv");

        writeWorkbookWithTimes(firstLeg, 0.0, 10.0);
        writeWorkbookWithTimes(secondLeg, 0.0, 10.0);

        RunCsvFromExcel.writeRunCsv(
                List.of(firstLeg, secondLeg),
                List.of("+0sek", "+0sek"),
                List.of("T1", "T1"),
                List.of("", ""),
                List.of("", ""),
                List.of("A-C", "A-C"),
                runCsv,
                List.of(36000, 36000),
                java.util.Arrays.asList(null, 10),
                36000,
                36100,
                0.0
        );

        List<String> lines = Files.readAllLines(runCsv);
        assertEquals(4, lines.size());
        assertEquals(36010.0, time(lines.get(2)), 1e-9);
        assertEquals(4653.0, csvPosition(lines.get(2)), 1e-9);
        assertEquals(36020.0, time(lines.get(3)), 1e-9);
        assertEquals(9306.0, csvPosition(lines.get(3)), 1e-9);
    }

    @Test
    public void rejectsNextLegStartingBeforePreviousLegEnds()
            throws Exception {
        Path tempDir = Files.createTempDirectory("run-csv-overlapping-legs-test");
        Path firstLeg = tempDir.resolve("A-B.xlsx");
        Path secondLeg = tempDir.resolve("B-C.xlsx");

        writeWorkbookWithTimes(firstLeg, 0.0, 10.0);
        writeWorkbookWithTimes(secondLeg, 0.0, 10.0);

        try {
            RunCsvFromExcel.writeRunCsv(
                    List.of(firstLeg, secondLeg),
                    List.of("+0sek", "+0sek"),
                    List.of("T1", "T1"),
                    List.of("", ""),
                    List.of("", ""),
                    List.of("A-C", "A-C"),
                    tempDir.resolve("run.csv"),
                    List.of(36000, 36000),
                    java.util.Arrays.asList(null, 5),
                    36000,
                    36100,
                    0.0
            );
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("before previous leg ends"));
            return;
        }

        throw new AssertionError("Expected overlapping legs to be rejected");
    }

    private static double time(String csvLine) {
        return Double.parseDouble(csvLine.split(",", -1)[0]);
    }

    private static double csvPosition(String csvLine) {
        return Double.parseDouble(csvLine.split(",", -1)[5]);
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
        writeWorkbook(
                path,
                firstBisPosition,
                lastBisPosition,
                0.0,
                10.0
        );
    }

    private static void writeWorkbookWithTimes(
            Path path,
            double firstTimeS,
            double lastTimeS
    ) throws Exception {
        writeWorkbook(
                path,
                0.500,
                5.153,
                firstTimeS,
                lastTimeS
        );
    }

    private static void writeWorkbookWithTimetableDeparture(
            Path path,
            double relativeDepartureS,
            double runDurationS
    ) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeRunSheet(
                    workbook,
                    0.500,
                    5.153,
                    0.0,
                    runDurationS
            );
            writeTrackSheet(workbook);
            writeTimetableSheet(workbook, relativeDepartureS);

            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private static void writeTimetableSheet(
            XSSFWorkbook workbook,
            double relativeDepartureS
    ) {
        Sheet sheet = workbook.createSheet("timetable");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Station");
        header.createCell(1).setCellValue("Time");
        header.createCell(2).setCellValue("Time s");
        header.createCell(3).setCellValue("Type");

        Row departure = sheet.createRow(1);
        departure.createCell(0).setCellValue("A");
        int totalSeconds = (int) relativeDepartureS;
        departure.createCell(1).setCellValue(String.format(
                "%02d:%02d:%02d",
                totalSeconds / 3600,
                totalSeconds % 3600 / 60,
                totalSeconds % 60
        ));
        departure.createCell(2).setCellValue(0.0);
        departure.createCell(3).setCellValue("departure");
    }

    private static void writeWorkbook(
            Path path,
            double firstBisPosition,
            double lastBisPosition,
            double firstTimeS,
            double lastTimeS
    ) throws Exception {
        try (XSSFWorkbook workbook =
                     new XSSFWorkbook()) {

            writeRunSheet(
                    workbook,
                    firstBisPosition,
                    lastBisPosition,
                    firstTimeS,
                    lastTimeS
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
            double lastBisPosition,
            double firstTimeS,
            double lastTimeS
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
                firstTimeS,
                0.0,
                firstBisPosition
        );
        addRunRow(
                sheet,
                2,
                lastTimeS,
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
