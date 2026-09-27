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
import java.util.List;

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
            writer.signalRow(1.0, "TRAIN", "T1", "speed_mps",
                    12.5, "m/s", "INPUT", null, "");
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
            assertEquals(
                    "T1.base_route_position_m",
                    sheet.getRow(0).getCell(6).getStringCellValue()
            );
            assertEquals(
                    "T1.speed_mps",
                    sheet.getRow(0).getCell(7).getStringCellValue()
            );
            assertEquals(12.5,
                    sheet.getRow(1).getCell(7).getNumericCellValue(), 1e-9);
            assertEquals(45.0,
                    sheet.getRow(1).getCell(8).getNumericCellValue(), 1e-9);
            assertEquals("T1.p_delta_W",
                    sheet.getRow(0).getCell(13).getStringCellValue());
            assertEquals(
                    125.0,
                    sheet.getRow(1).getCell(13).getNumericCellValue(),
                    1e-9
            );
            assertEquals(
                    300.0,
                    sheet.getRow(1).getCell(14).getNumericCellValue(),
                    1e-9
            );
            assertEquals(
                    50.0,
                    sheet.getRow(1).getCell(15).getNumericCellValue(),
                    1e-9
            );
            assertEquals(
                    250.0,
                    sheet.getRow(1).getCell(16).getNumericCellValue(),
                    1e-9
            );
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

    @Test
    public void exportsGraphicalTimetableInputOnConfiguredBaseRoute()
            throws Exception {
        Path resultDirectory =
                temporaryFolder.newFolder("graphical-results").toPath();
        Path exportDirectory =
                temporaryFolder.newFolder("graphical-exports").toPath();
        String studyId = "graphical-report-test";
        Path longTable =
                resultDirectory.resolve(studyId + "_longtable.csv");

        try (LongTableWriter writer = new LongTableWriter(
                longTable.toString(),
                true,
                "project",
                "scenario",
                "hash"
        )) {
            writeTrainPosition(writer, 1.0, "T1", "200", 25.0, 125.0);
            writeTrainPosition(writer, 2.0, "T1", "200", 35.0, -250.0);
            writeTrainPosition(writer, 3.0, "T1", "999", 5.0, 0.0);
        }

        Files.writeString(
                exportDirectory.resolve("track_segments.csv"),
                "section,from_rwy,to_rwy,start_model_m,length_m\n"
                        + "101,101 10+0,101 10+600,0,600\n"
                        + "200,200 0+0,200 1+0,0,1000\n"
        );
        Files.writeString(
                exportDirectory.resolve("track_stations.csv"),
                "name,position_rwy,model_position_m\n"
                        + "W,101 0+0 U,0\n"
                        + "W,101 0+600 D,600\n"
                        + "\"Central, upper\",200 0+100 U1,100\n"
                        + "\"Central, upper\",200 0+300 D1,300\n"
                        + "Outside,999 0+50 U,50\n"
        );
        Config scenario = ConfigFactory.parseString(
                "report.graphical-timetable {"
                        + "\n enabled = true"
                        + "\n base-route.sections = [\"101\", \"200\"]"
                        + "\n"
                        + " }\n"
                        + "dcsim.grid {\n"
                        + " nodes = [\n"
                        + "  { node_id=F_SS0, position_rwy=\"101 10+300 U\" },\n"
                        + "  { node_id=F_SS1, position_rwy=\"200 0+400 U1\" },\n"
                        + "  { node_id=R_SS1, position_rwy=\"200 0+400 D1\" },\n"
                        + "  { node_id=F_U_SS2_LEFT, position_rwy=\"200 0+700 U1\" },\n"
                        + "  { node_id=F_D_SS2_RIGHT, position_rwy=\"200 0+700 D1\" }\n"
                        + " ]\n"
                        + " power_installations = [\n"
                        + "  { installation_id=SS0, installation_category=SUBSTATION, enabled=true },\n"
                        + "  { installation_id=SS1, installation_category=SUBSTATION, enabled=true },\n"
                        + "  { installation_id=SS2, installation_category=SUBSTATION, enabled=true,"
                        + " terminals { NORTH=F_U_SS2_LEFT, SOUTH=F_D_SS2_RIGHT } }\n"
                        + " ]\n"
                        + " installation_connections = [\n"
                        + "  { installation_id=SS0, node_id=F_SS0, connection_type=FEEDING },\n"
                        + "  { installation_id=SS1, node_id=F_SS1, connection_type=FEEDING },\n"
                        + "  { installation_id=SS1, node_id=R_SS1, connection_type=RETURN }\n"
                        + " ]\n"
                        + "}\n"
        );
        Config empty = ConfigFactory.empty();
        DcStudyContext context = new DcStudyContext(
                resultDirectory.resolve("study.conf"),
                resultDirectory,
                scenario,
                empty,
                studyId,
                studyId,
                "",
                exportDirectory,
                resultDirectory
        );

        DcReporter.run(context);

        Path graphicalTimetable = resultDirectory.resolve(
                studyId + "_graphical_timetable.csv"
        );
        assertEquals(
                List.of(
                        "time_s,train_id,base_route_position_m,p_delta_W",
                        "1.0,T1,625.0,125.0",
                        "2.0,T1,635.0,-250.0",
                        "3.0,T1,,0.0"
                ),
                Files.readAllLines(graphicalTimetable)
        );

        Path markers = resultDirectory.resolve(
                studyId + "_graphical_timetable_markers.csv"
        );
        assertEquals(
                List.of(
                        "name,base_route_position_m,kind",
                        "W,300.0,STATION",
                        "\"Central, upper\",800.0,STATION",
                        "SS0,300.0,SUPPLY_POINT",
                        "SS1,1000.0,SUPPLY_POINT",
                        "SS2,1300.0,SUPPLY_POINT",
                        "101/200,600.0,SECTION_BOUNDARY"
                ),
                Files.readAllLines(markers)
        );

        Path checkDirectory =
                temporaryFolder.newFolder("graphical-checks").toPath();
        DcReporter.writePlannedGraphicalTimetable(
                context,
                exportDirectory,
                List.of(
                        new DcReporter.PlannedTimetablePoint(1.0, "T1", "200", 25.0),
                        new DcReporter.PlannedTimetablePoint(2.0, "T1", "200", 35.0),
                        new DcReporter.PlannedTimetablePoint(3.0, "T1", "999", 5.0)
                ),
                checkDirectory
        );

        assertEquals(
                List.of(
                        "time_s,train_id,base_route_position_m,p_delta_W",
                        "1.0,T1,625.0,0.0",
                        "2.0,T1,635.0,0.0",
                        "3.0,T1,,0.0"
                ),
                Files.readAllLines(checkDirectory.resolve(
                        studyId + "_graphical_timetable.csv"
                ))
        );
        assertEquals(
                Files.readAllLines(markers),
                Files.readAllLines(checkDirectory.resolve(
                        studyId + "_graphical_timetable_markers.csv"
                ))
        );
    }

    @Test
    public void orientsGraphicalTimetableSectionsFromTrackdata()
            throws Exception {
        Path root = temporaryFolder.newFolder("two-sections").toPath();
        Path exportDirectory = Files.createDirectory(root.resolve("exports"));
        Path outputDirectory = Files.createDirectory(root.resolve("checks"));
        Path confFile = root.resolve("two_sections.conf");
        Path routeWorkbook = root.resolve("two_sections_trackdata.xlsx");

        writeTwoSectionRouteWorkbook(routeWorkbook);
        Files.writeString(
                exportDirectory.resolve("track_segments.csv"),
                "section,from_rwy,to_rwy,start_model_m,length_m\n"
                        + "101,101 0+000,101 1+000,0,1000\n"
                        + "101,101 1+000,101 2+000,1000,1000\n"
                        + "102,102 0+000,102 1+000,0,1000\n"
                        + "102,102 1+000,102 2+000,1000,1000\n"
        );
        Files.writeString(
                exportDirectory.resolve("track_stations.csv"),
                "name,position_rwy,model_position_m\n"
                        + "A,101 1+500 U,1500\n"
                        + "B,101 0+500 U,500\n"
                        + "C,102 0+500 U,500\n"
                        + "D,102 1+500 U,1500\n"
        );

        Config scenario = ConfigFactory.parseString(
                "report.graphical-timetable {\n"
                        + " enabled = true\n"
                        + " base-route { id=\"TEST-WE\", sections=[\"101\",\"102\"] }\n"
                        + "}\n"
                        + "dcsim {\n"
                        + " track {\n"
                        + "  route_data_excel=two_sections_trackdata.xlsx\n"
                        + "  route_views=[{route_id=\"TEST-WE\",sheet=\"TEST-WE\"}]\n"
                        + "  kilometer_boards=[\n"
                        + "   {section=\"101\",km=\"0+000\",length=1000},\n"
                        + "   {section=\"101\",km=\"1+000\",length=1000},\n"
                        + "   {section=\"101\",km=\"2+000\",length=1000},\n"
                        + "   {section=\"102\",km=\"0+000\",length=1000},\n"
                        + "   {section=\"102\",km=\"1+000\",length=1000},\n"
                        + "   {section=\"102\",km=\"2+000\",length=1000}\n"
                        + "  ]\n"
                        + " }\n"
                        + " grid {\n"
                        + "  nodes=[\n"
                        + "   {node_id=F_SS1,position_rwy=\"101 1+000 U\"},\n"
                        + "   {node_id=F_SS2,position_rwy=\"102 1+000 U\"}\n"
                        + "  ]\n"
                        + "  power_installations=[\n"
                        + "   {installation_id=SS1,installation_category=SUBSTATION},\n"
                        + "   {installation_id=SS2,installation_category=SUBSTATION}\n"
                        + "  ]\n"
                        + "  installation_connections=[\n"
                        + "   {installation_id=SS1,node_id=F_SS1,connection_type=FEEDING},\n"
                        + "   {installation_id=SS2,node_id=F_SS2,connection_type=FEEDING}\n"
                        + "  ]\n"
                        + " }\n"
                        + "}\n"
        );
        Config dcsim = scenario.getConfig("dcsim");
        DcStudyContext context = new DcStudyContext(
                confFile, root, scenario, dcsim,
                "two_sections", "two_sections", "",
                exportDirectory, root.resolve("results")
        );

        DcReporter.writePlannedGraphicalTimetable(
                context,
                exportDirectory,
                List.of(
                        new DcReporter.PlannedTimetablePoint(
                                0.0, "T1", "101", 2000.0),
                        new DcReporter.PlannedTimetablePoint(
                                1.0, "T1", "101", 1500.0),
                        new DcReporter.PlannedTimetablePoint(
                                2.0, "T1", "101", 0.0),
                        new DcReporter.PlannedTimetablePoint(
                                3.0, "T1", "102", 0.0),
                        new DcReporter.PlannedTimetablePoint(
                                4.0, "T1", "102", 500.0),
                        new DcReporter.PlannedTimetablePoint(
                                5.0, "T1", "102", 2000.0)
                ),
                outputDirectory
        );

        assertEquals(
                List.of(
                        "time_s,train_id,base_route_position_m,p_delta_W",
                        "0.0,T1,0.0,0.0",
                        "1.0,T1,500.0,0.0",
                        "2.0,T1,2000.0,0.0",
                        "3.0,T1,2000.0,0.0",
                        "4.0,T1,2500.0,0.0",
                        "5.0,T1,4000.0,0.0"
                ),
                Files.readAllLines(outputDirectory.resolve(
                        "two_sections_graphical_timetable.csv"))
        );
        assertEquals(
                List.of(
                        "name,base_route_position_m,kind",
                        "A,500.0,STATION",
                        "B,1500.0,STATION",
                        "C,2500.0,STATION",
                        "D,3500.0,STATION",
                        "SS1,1000.0,SUPPLY_POINT",
                        "SS2,3000.0,SUPPLY_POINT",
                        "101/102,2000.0,SECTION_BOUNDARY"
                ),
                Files.readAllLines(outputDirectory.resolve(
                        "two_sections_graphical_timetable_markers.csv"))
        );
    }

    private static void writeTwoSectionRouteWorkbook(Path path)
            throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("TEST-WE");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("position [m]");
            header.createCell(1).setCellValue("trackSection");
            header.createCell(2).setCellValue("trackNumber");
            header.createCell(3).setCellValue("bisKm");
            header.createCell(4).setCellValue("bisMeter");
            header.createCell(5).setCellValue("trackInformation");

            Object[][] values = {
                    {0.0, "101", "U", 2, 0, "TEST-WE"},
                    {1000.0, "101", "U", 1, 0, "TEST-WE"},
                    {1999.0, "101", "U", 0, 1, "TEST-WE"},
                    {2000.0, "102", "U", 0, 0, "TEST-WE"},
                    {3000.0, "102", "U", 1, 0, "TEST-WE"},
                    {4000.0, "102", "U", 2, 0, "TEST-WE"}
            };
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue((double) values[rowIndex][0]);
                row.createCell(1).setCellValue((String) values[rowIndex][1]);
                row.createCell(2).setCellValue((String) values[rowIndex][2]);
                row.createCell(3).setCellValue((int) values[rowIndex][3]);
                row.createCell(4).setCellValue((int) values[rowIndex][4]);
                row.createCell(5).setCellValue((String) values[rowIndex][5]);
            }
            try (java.io.OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private static void writeTrainPosition(
            LongTableWriter writer,
            double timeS,
            String trainId,
            String sectionId,
            double positionM,
            double powerDeltaW
    ) throws Exception {
        writer.signalRow(timeS, "TRAIN", trainId, "section_id",
                sectionId, "", "INPUT", null, "");
        writer.signalRow(timeS, "TRAIN", trainId, "position_m",
                positionM, "m", "INPUT", null, "");
        writer.signalRow(timeS, "TRAIN", trainId, "p_delta_W",
                powerDeltaW, "W", "RESULT", null, "");
    }
}
