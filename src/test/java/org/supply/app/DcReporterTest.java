package org.supply.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import org.supply.solver.io.LongTableWriter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DcReporterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportsTrainPowerDeltaAndLineLosses() throws Exception {
        Path resultDirectory = temporaryFolder.newFolder("results").toPath();
        String studyId = "report-test";
        Path longTable = resultDirectory.resolve(studyId + "_longtable.csv");

        try (LongTableWriter writer = new LongTableWriter(
                longTable.toString(),
                true,
                "project",
                "scenario",
                "hash"
        )) {
            writer.signalRow(1.0, "TRAIN", "T1", "p_delta_W",
                    125.0, "W", "RESULT", null, "");
            writer.signalRow(1.0, "TRAIN", "T1", "e_consumed_J",
                    300.0, "J", "RESULT", null, "");
            writer.signalRow(1.0, "TRAIN", "T1", "e_regenerated_J",
                    50.0, "J", "RESULT", null, "");
            writer.signalRow(1.0, "TRAIN", "T1", "e_net_J",
                    250.0, "J", "RESULT", null, "");
            writer.signalRow(1.0, "DIODE_SUBSTATION", "SS1", "e_supplied_J",
                    400.0, "J", "RESULT", null, "");
            writer.signalRow(1.0, "LINE", "L1", "p_losses_W",
                    25.0, "W", "RESULT", null, "");
            writer.signalRow(1.0, "LINE", "L1", "e_losses_J",
                    50.0, "J", "RESULT", null, "");
            writer.signalRow(1.0, "SYSTEM", "DC", "p_balance_W",
                    0.5, "W", "RESULT", null, "");
            writer.signalRow(1.0, "SYSTEM", "DC", "e_balance_J",
                    1.0, "J", "RESULT", null, "");
        }

        Config empty = ConfigFactory.empty();
        DcStudyContext context = new DcStudyContext(
                resultDirectory.resolve("study.conf"),
                resultDirectory,
                empty,
                empty,
                studyId,
                studyId,
                "",
                resultDirectory,
                resultDirectory
        );

        DcReporter.run(context);

        Path trainWorkbook =
                resultDirectory.resolve(studyId + "_results_train.xlsx");
        assertTrue(Files.exists(trainWorkbook));
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(trainWorkbook))) {
            Sheet sheet = workbook.getSheet("T1");
            assertEquals("T1.p_delta_W", sheet.getRow(0).getCell(10).getStringCellValue());
            assertEquals(125.0, sheet.getRow(1).getCell(10).getNumericCellValue(), 1e-9);
            assertEquals(300.0, sheet.getRow(1).getCell(11).getNumericCellValue(), 1e-9);
            assertEquals(50.0, sheet.getRow(1).getCell(12).getNumericCellValue(), 1e-9);
            assertEquals(250.0, sheet.getRow(1).getCell(13).getNumericCellValue(), 1e-9);
        }


        Path installationWorkbook =
                resultDirectory.resolve(studyId + "_results_installation.xlsx");
        try (Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(installationWorkbook))) {
            assertEquals(
                    400.0,
                    workbook.getSheet("SS1").getRow(1).getCell(6)
                            .getNumericCellValue(),
                    1e-9
            );
        }

        Path lineWorkbook =
                resultDirectory.resolve(studyId + "_results_line.xlsx");
        assertTrue(Files.exists(lineWorkbook));
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(lineWorkbook))) {
            Sheet sheet = workbook.getSheet("L1");
            Row row = sheet.getRow(1);
            assertEquals(25.0, row.getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(50.0, row.getCell(2).getNumericCellValue(), 1e-9);
        }


        Path systemWorkbook =
                resultDirectory.resolve(studyId + "_results_system.xlsx");
        assertTrue(Files.exists(systemWorkbook));
        try (Workbook workbook = new XSSFWorkbook(
                Files.newInputStream(systemWorkbook))) {
            Sheet sheet = workbook.getSheet("Power and energy balance");
            assertEquals(0.5, sheet.getRow(1).getCell(5).getNumericCellValue(), 1e-9);
            assertEquals(1.0, sheet.getRow(1).getCell(14).getNumericCellValue(), 1e-9);
        }
    }
}
