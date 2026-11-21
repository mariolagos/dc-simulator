package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class SystemGlobalTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void reportsFinalCountersAndReplacesOnlyMatchingCase() throws IOException {
        Path root = folder.getRoot().toPath();
        Path input = root.resolve("results_system.xlsx");
        Path output = root.resolve("system_global.xlsx");

        writeInput(input, "original", 10.0);
        SystemGlobal.write(input, output, "WE", "Base");
        SystemGlobal.write(input, output, "WE", "Alternative");
        SystemGlobal.write(input, output, "Other", "Base");

        writeInput(input, "updated", 20.0);
        SystemGlobal.write(input, output, "WE", "Base");

        try (var stream = Files.newInputStream(output);
             Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet result = workbook.getSheet("System Global");
            assertEquals(3, result.getLastRowNum());
            assertEquals("e_substations_supplied_kWh", result.getRow(0).getCell(2).getStringCellValue());
            assertEquals("e_balance_kWh", result.getRow(0).getCell(10).getStringCellValue());
            assertCase(result.getRow(1), "WE", "Alternative", 10.0);
            assertCase(result.getRow(2), "Other", "Base", 10.0);
            assertCase(result.getRow(3), "WE", "Base", 20.0);
            assertEquals(-0.25, result.getRow(3).getCell(10).getNumericCellValue(), 1e-12);

            Sheet sources = workbook.getSheet("Sources");
            assertEquals(3, sources.getLastRowNum());
            assertEquals("original", sources.getRow(1).getCell(4).getStringCellValue());
            assertEquals("original", sources.getRow(2).getCell(4).getStringCellValue());
            assertEquals("updated", sources.getRow(3).getCell(4).getStringCellValue());
        }
    }

    private static void assertCase(Row row, String studyId, String calcType, double supplied_kWh) {
        assertEquals(studyId, row.getCell(0).getStringCellValue());
        assertEquals(calcType, row.getCell(1).getStringCellValue());
        assertEquals(supplied_kWh, row.getCell(2).getNumericCellValue(), 1e-12);
    }

    private static void writeInput(Path path, String hash, double supplied_kWh) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet data = workbook.createSheet("Power and energy balance");
            String[] headers = {
                    "time_s", "e_substations_supplied_J", "e_substations_absorbed_J",
                    "e_substations_net_J", "e_trains_consumed_J", "e_trains_regenerated_J",
                    "e_trains_net_J", "e_fixed_loads_consumed_J", "e_losses_J", "e_balance_J"
            };
            Row header = data.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            writeSample(data.createRow(1), 36_000, 2.0);
            writeSample(data.createRow(2), 36_001, supplied_kWh);

            Sheet metadata = workbook.createSheet("Metadata");
            String[][] entries = {
                    {"project", "dc-simulator"}, {"scenario", "WE"}, {"base_hash", hash},
                    {"generated_at", "2026-11-19T10:00:00Z"}
            };
            for (int i = 0; i < entries.length; i++) {
                Row row = metadata.createRow(i);
                row.createCell(0).setCellValue(entries[i][0]);
                row.createCell(1).setCellValue(entries[i][1]);
            }
            try (var stream = Files.newOutputStream(path)) {
                workbook.write(stream);
            }
        }
    }

    private static void writeSample(Row row, double time_s, double supplied_kWh) {
        row.createCell(0).setCellValue(time_s);
        double[] energy_kWh = {
                supplied_kWh, 0.5, supplied_kWh - 0.5, supplied_kWh - 0.5, 1.0,
                supplied_kWh - 1.5, 0.75, 0.5, -0.25
        };
        for (int i = 0; i < energy_kWh.length; i++) {
            row.createCell(i + 1).setCellValue(energy_kWh[i] * 3_600_000.0);
        }
    }
}
