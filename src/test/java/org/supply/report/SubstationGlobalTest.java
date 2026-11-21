package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public class SubstationGlobalTest {
    @Test
    public void copiesMetadataAndAddsStudyAndCalcTypeWithoutChangingStatistics() throws Exception {
        Path directory = Files.createTempDirectory("substation-global-test");
        Path source = directory.resolve("installations.xlsx");
        Path target = directory.resolve("global-installations.xlsx");
        try {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet metadata = workbook.createSheet("Metadata");
                String[][] pairs = {{"project", "dc-simulator"}, {"scenario", "1"},
                        {"base_hash", "abc123"}, {"generated_at", "2026-11-14T10:55:24Z"}};
                for (int i = 0; i < pairs.length; i++) {
                    Row row = metadata.createRow(i);
                    row.createCell(0).setCellValue(pairs[i][0]);
                    row.createCell(1).setCellValue(pairs[i][1]);
                }
                Sheet data = workbook.createSheet("SS0");
                Row header = data.createRow(0);
                String[] fields = {"time_s", "u_V", "i_A", "p_W", "p_losses_W",
                        "e_supplied_J", "e_absorbed_J", "e_net_J"};
                for (int i = 0; i < fields.length; i++) header.createCell(i).setCellValue("SS0." + fields[i]);
                double[][] samples = {
                        {0, 3000, 10, 30000, 100, 0, 0, 0},
                        {2, 2990, 20, 59800, 200, 120000, 0, 120000},
                        {5, 2980, 30, 89400, 300, 390000, 0, 390000}
                };
                for (int r = 0; r < samples.length; r++) {
                    Row row = data.createRow(r + 1);
                    for (int c = 0; c < fields.length; c++) row.createCell(c).setCellValue(samples[r][c]);
                }
                try (var stream = Files.newOutputStream(source)) { workbook.write(stream); }
            }
            byte[] before = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
            SubstationGlobal.write(source, target, "WE", "FOLLOW");
            byte[] after = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
            assertArrayEquals("Source workbook must remain unchanged", before, after);
            try (Workbook result = WorkbookFactory.create(target.toFile())) {
                Sheet summary = result.getSheet("Substation Global");
                Row row = summary.getRow(1);
                assertEquals("StudyId", summary.getRow(0).getCell(0).getStringCellValue());
                assertEquals("Calc Type", summary.getRow(0).getCell(1).getStringCellValue());
                assertEquals("WE", row.getCell(0).getStringCellValue());
                assertEquals("FOLLOW", row.getCell(1).getStringCellValue());
                assertEquals("SS0", row.getCell(2).getStringCellValue());
                assertEquals(5, row.getCell(5).getNumericCellValue(), 1e-9);
                assertEquals(390000.0 / 3600000.0, row.getCell(8).getNumericCellValue(), 1e-9);
                assertEquals(26, row.getCell(12).getNumericCellValue(), 1e-9);
                Sheet metadata = result.getSheet("Metadata");
                assertNotNull(metadata);
                assertEquals("abc123", metadata.getRow(2).getCell(1).getStringCellValue());
                assertNotNull(result.getSheet("Method"));
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }
}
