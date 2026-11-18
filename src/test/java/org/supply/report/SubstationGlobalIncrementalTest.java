package org.supply.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SubstationGlobalIncrementalTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void accumulatesAndReplacesStudyCaseWithoutDuplicates() throws Exception {
        Path a = temp.getRoot().toPath().resolve("a.xlsx");
        Path b = temp.getRoot().toPath().resolve("b.xlsx");
        Path global = temp.getRoot().toPath().resolve("global.xlsx");
        source(a, "SS_A", 3_600_000.0);
        source(b, "SS_B", 7_200_000.0);

        SubstationGlobal.write(a, global, "WE", "+0sek");
        assertRows(global, 1, 1, 0);
        SubstationGlobal.write(b, global, "two_sections", "DC");
        assertRows(global, 2, 1, 1);
        SubstationGlobal.write(a, global, "WE", "+0sek");
        assertRows(global, 2, 1, 1);
        assertTrue(Files.size(global) > 0);
    }

    @Test public void differentCalcTypesForSameStudyRemainDistinct() throws Exception {
        Path source = temp.getRoot().toPath().resolve("input.xlsx");
        Path global = temp.getRoot().toPath().resolve("global.xlsx");
        source(source, "SS_A", 3_600_000.0);
        SubstationGlobal.write(source, global, "WE", "+0sek");
        SubstationGlobal.write(source, global, "WE", "minRunTime");
        try (Workbook book = WorkbookFactory.create(Files.newInputStream(global))) {
            assertEquals(2, book.getSheet("Substation Global").getLastRowNum());
            assertEquals(2, book.getSheet("Sources").getLastRowNum());
        }
    }

    private static void assertRows(Path path, int total, int we, int twoSections) throws Exception {
        try (var in = Files.newInputStream(path); Workbook book = WorkbookFactory.create(in)) {
            Sheet sheet = book.getSheet("Substation Global");
            assertEquals(total, sheet.getLastRowNum());
            int a = 0, b = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                String study = sheet.getRow(r).getCell(0).getStringCellValue();
                if (study.equals("WE")) a++;
                if (study.equals("two_sections")) b++;
            }
            assertEquals(we, a);
            assertEquals(twoSections, b);
            assertEquals(total, book.getSheet("Sources").getLastRowNum());
        }
    }

    private static void source(Path path, String id, double energy) throws Exception {
        String[] fields = {"time_s", "u_V", "i_A", "p_W", "p_losses_W",
                "e_supplied_J", "e_absorbed_J", "e_net_J"};
        try (Workbook book = new XSSFWorkbook()) {
            Sheet data = book.createSheet("Results");
            Row header = data.createRow(0);
            for (int c = 0; c < fields.length; c++)
                header.createCell(c).setCellValue(id + "." + fields[c]);
            double[][] values = {
                    {0, 3000, 100, 300000, 1000, 0, 0, 0},
                    {3600, 3000, 100, 300000, 1000, energy, 0, energy}
            };
            for (int r = 0; r < values.length; r++) {
                Row row = data.createRow(r + 1);
                for (int c = 0; c < fields.length; c++) row.createCell(c).setCellValue(values[r][c]);
            }
            Sheet metadata = book.createSheet("Metadata");
            String[] keys = {"project", "scenario", "base_hash", "generated_at"};
            String[] vals = {"dc-simulator", id, "test-hash", "2026-11-18T00:00:00Z"};
            for (int r = 0; r < keys.length; r++) {
                Row row = metadata.createRow(r);
                row.createCell(0).setCellValue(keys[r]);
                row.createCell(1).setCellValue(vals[r]);
            }
            try (var out = Files.newOutputStream(path)) { book.write(out); }
        }
    }
}
