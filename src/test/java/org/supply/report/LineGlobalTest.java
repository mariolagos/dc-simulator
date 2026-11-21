package org.supply.report;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class LineGlobalTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void ranksByFinalEnergyAndLimitsEachCase() throws IOException {
        Path root = folder.getRoot().toPath();
        Path source = root.resolve("results_line.xlsx");
        Path destination = root.resolve("line_global.xlsx");
        writeInput(source);

        LineGlobal.write(source, destination, "WE", "Base", 10);
        LineGlobal.write(source, destination, "WE", "FollowCalc", 2);
        LineGlobal.write(source, destination, "WE", "Base", 1);

        try (var stream = Files.newInputStream(destination);
             Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet result = workbook.getSheetAt(0);
            assertEquals(3, result.getLastRowNum());
            assertEquals("e_losses_kWh", result.getRow(0).getCell(4).getStringCellValue());
            assertLine(result.getRow(1), "FollowCalc", 1, "A.F", 5.0);
            assertLine(result.getRow(2), "FollowCalc", 2, "B.R", 5.0);
            assertLine(result.getRow(3), "Base", 1, "A.F", 5.0);
            assertEquals(2, workbook.getSheet("Sources").getLastRowNum());
        }
    }

    @Test
    public void reportsAllLinesWhenLimitExceedsLineCount() throws IOException {
        Path root = folder.getRoot().toPath();
        Path source = root.resolve("results_line.xlsx");
        Path destination = root.resolve("line_global.xlsx");
        writeInput(source);

        LineGlobal.write(source, destination, "WE", "FollowCalc", 10);

        try (var stream = Files.newInputStream(destination);
             Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet result = workbook.getSheetAt(0);
            assertEquals(3, result.getLastRowNum());
            assertLine(result.getRow(3), "FollowCalc", 3, "C.F", 3.0);
        }
    }

    private static void assertLine(Row row, String calcType, int rank, String id, double loss_kWh) {
        assertEquals("WE", row.getCell(0).getStringCellValue());
        assertEquals(calcType, row.getCell(1).getStringCellValue());
        assertEquals(rank, row.getCell(2).getNumericCellValue(), 0.0);
        assertEquals(id, row.getCell(3).getStringCellValue());
        assertEquals(loss_kWh, row.getCell(4).getNumericCellValue(), 1e-12);
    }

    private static void writeInput(Path source) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet metadata = workbook.createSheet("Metadata");
            String[][] fields = {
                    {"project", "dc-simulator"}, {"scenario", "WE"},
                    {"base_hash", "test-hash"}, {"generated_at", "2026-11-19T10:00:00Z"}
            };
            for (int i = 0; i < fields.length; i++) {
                Row row = metadata.createRow(i);
                row.createCell(0).setCellValue(fields[i][0]);
                row.createCell(1).setCellValue(fields[i][1]);
            }

            writeLine(workbook, "C.F", 0.0, 10_800_000.0);
            writeLine(workbook, "B.R", 14_400_000.0, 18_000_000.0);
            writeLine(workbook, "A.F", 7_200_000.0, 18_000_000.0);
            try (var stream = Files.newOutputStream(source)) {
                workbook.write(stream);
            }
        }
    }

    private static void writeLine(Workbook workbook, String id, double first_J, double last_J) {
        Sheet sheet = workbook.createSheet(id);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(id + ".time_s");
        header.createCell(1).setCellValue(id + ".p_losses_W");
        header.createCell(2).setCellValue(id + ".e_losses_J");
        writeSample(sheet.createRow(1), 36_000.0, first_J, first_J);
        writeSample(sheet.createRow(2), 36_001.0, last_J - first_J, last_J);
    }

    private static void writeSample(Row row, double time_s, double loss_W, double loss_J) {
        row.createCell(0).setCellValue(time_s);
        row.createCell(1).setCellValue(loss_W);
        row.createCell(2).setCellValue(loss_J);
    }
}
