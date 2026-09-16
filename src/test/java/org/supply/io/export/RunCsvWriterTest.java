package org.supply.io.export;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class RunCsvWriterTest {

    @Test
    public void carriesAuxiliaryConfigurationThroughCompleteExport()
            throws Exception {
        Path tempDir = Files.createTempDirectory("run-csv-writer-aux-test");
        Path firstLeg = tempDir.resolve("A-B.xlsx");
        Path secondLeg = tempDir.resolve("B-C.xlsx");
        Path confFile = tempDir.resolve("application.conf");
        Path exportDir = tempDir.resolve("exports");

        writeWorkbook(firstLeg);
        writeWorkbook(secondLeg);

        Config dcsim = ConfigFactory.parseString("""
                simulationControl {
                  simulationStart = "06:00:00"
                  simulationEnd = "07:00:00"
                }

                traffic {
                  timetable.trains = [
                    {
                      id = "T1"
                      template_id = "ABC"
                      departure = "06:00:00"
                      count = 1
                      routeId = "A-C"
                    }
                  ]

                  templates.ABC.legs = [
                    {
                      run_excel = "A-B.xlsx"
                      run_excel_sheet = "+0sek"
                      motoring_and_auxiliaries_in_same_model = false
                      auxiliary_power_W = 100000
                    },
                    {
                      run_excel = "B-C.xlsx"
                      run_excel_sheet = "+0sek"
                      departure = "00:00:15"
                      motoring_and_auxiliaries_in_same_model = true
                      auxiliary_power_W = 200000
                    }
                  ]
                }
                """);

        new RunCsvWriter().write(dcsim, confFile, exportDir);

        List<String> lines = Files.readAllLines(exportDir.resolve("run.csv"));
        assertEquals(5, lines.size());
        assertEquals(120000.0, power(lines.get(1)), 1e-9);
        assertEquals(100000.0, power(lines.get(2)), 1e-9);
        assertEquals(20000.0, power(lines.get(3)), 1e-9);
    }

    private static double power(String csvLine) {
        return Double.parseDouble(csvLine.split(",", -1)[6]);
    }

    private static void writeWorkbook(Path path) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet run = workbook.createSheet("+0sek");
            Row runHeader = run.createRow(0);
            runHeader.createCell(0).setCellValue("time [s]");
            runHeader.createCell(1).setCellValue("position [m]");
            runHeader.createCell(2).setCellValue("bisPosition [km,m]");
            runHeader.createCell(3).setCellValue("primaryMotoringPower [kW]");
            runHeader.createCell(4).setCellValue("primaryMotorBrakingPower [kW]");
            addRunRow(run, 1, 0.0, 0.0, 0.500);
            addRunRow(run, 2, 10.0, 4653.0, 5.153);

            Sheet track = workbook.createSheet("track");
            Row trackHeader = track.createRow(0);
            trackHeader.createCell(0).setCellValue("position [m]");
            trackHeader.createCell(1).setCellValue("bisKm");
            trackHeader.createCell(2).setCellValue("bisMeter");
            Row first = track.createRow(1);
            first.createCell(0).setCellValue(0.0);
            first.createCell(1).setCellValue(0);
            first.createCell(2).setCellValue(500.0);
            Row last = track.createRow(2);
            last.createCell(0).setCellValue(6000.0);
            last.createCell(1).setCellValue(6);
            last.createCell(2).setCellValue(0.0);

            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private static void addRunRow(
            Sheet sheet,
            int rowNumber,
            double timeS,
            double positionM,
            double bisPosition
    ) {
        Row row = sheet.createRow(rowNumber);
        row.createCell(0).setCellValue(timeS);
        row.createCell(1).setCellValue(positionM);
        row.createCell(2).setCellValue(bisPosition);
        row.createCell(3).setCellValue(20.0);
        row.createCell(4).setCellValue(0.0);
    }
}
