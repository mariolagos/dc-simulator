package org.supply.report;

import org.junit.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.nio.file.Files;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

/** Focused regression tests for timetable boundaries and cumulative counters. */
public class TrainGlobalTest {

    @Test
    public void readsLegLocalTimetableTimes() throws Exception {
        Path file = Files.createTempFile("train-global-timetable", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("timetable");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Station");
            header.createCell(1).setCellValue("Time s");
            header.createCell(2).setCellValue("Type");
            header.createCell(3).setCellValue("Time");
            var departure = sheet.createRow(1);
            departure.createCell(0).setCellValue("CE");
            departure.createCell(1).setCellValue(0.0);
            departure.createCell(2).setCellValue("departure");
            departure.createCell(3).setCellValue("00:12:19");
            var arrival = sheet.createRow(2);
            arrival.createCell(0).setCellValue("E");
            arrival.createCell(1).setCellValue(299.0);
            arrival.createCell(2).setCellValue("arrival");
            arrival.createCell(3).setCellValue("00:17:18");
            try (var out = Files.newOutputStream(file)) { workbook.write(out); }
        }
        Method read = TrainGlobal.class.getDeclaredMethod("readTimetable", Path.class);
        read.setAccessible(true);
        Object timetable = read.invoke(null, file);
        assertEquals("CE", field(timetable, "from"));
        assertEquals("E", field(timetable, "to"));
        assertEquals(0.0, (double) field(timetable, "departureS"), 1e-9);
        assertEquals(299.0, (double) field(timetable, "arrivalS"), 1e-9);
        assertEquals(739.0, (double) field(timetable, "sourceDepartureS"), 1e-9);
    }

    @Test
    public void readsNumericExcelDepartureTime() throws Exception {
        Path file = Files.createTempFile("train-global-excel-time", ".xlsx");
        try {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                var sheet = workbook.createSheet("timetable");
                var header = sheet.createRow(0);
                header.createCell(0).setCellValue("Station");
                header.createCell(1).setCellValue("Time s");
                header.createCell(2).setCellValue("Type");
                header.createCell(3).setCellValue("Time");
                var departure = sheet.createRow(1);
                departure.createCell(0).setCellValue("CE");
                departure.createCell(1).setCellValue(0.0);
                departure.createCell(2).setCellValue("departure");
                departure.createCell(3).setCellValue(739.0 / 86400.0);
                var arrival = sheet.createRow(2);
                arrival.createCell(0).setCellValue("E");
                arrival.createCell(1).setCellValue(299.0);
                arrival.createCell(2).setCellValue("arrival");
                try (var out = Files.newOutputStream(file)) { workbook.write(out); }
            }
            Method read = TrainGlobal.class.getDeclaredMethod("readTimetable", Path.class);
            read.setAccessible(true);
            Object timetable = read.invoke(null, file);
            assertEquals(739.0, (double) field(timetable, "sourceDepartureS"), 1e-8);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void mapsTwoLegsUsingJourneyTimeAndLocalDurations() {
        // Source timetable: A-B 00:00:00 -> 00:05:00,
        // B-C 00:06:00 -> 00:11:00; both legs have local Time s = 0..300.
        double origin = 6 * 3600.0;
        double[] ab = TrainGlobal.legBounds(origin, 0, 0, 0, 300, null);
        double[] bc = TrainGlobal.legBounds(origin, 0, 360, 0, 300, null);
        assertArrayEquals(new double[]{21600, 21900}, ab, 1e-9);
        assertArrayEquals(new double[]{21960, 22260}, bc, 1e-9);
        // Explicit relative departure is honored.
        assertArrayEquals(new double[]{22020, 22320},
                TrainGlobal.legBounds(origin, 0, 360, 0, 300, 420), 1e-9);
    }

    @Test
    public void interpolatesCumulativeEnergyAtLegBoundaries() throws Exception {
        Class<?> sampleClass = Class.forName("org.supply.report.TrainGlobal$Sample");
        Constructor<?> ctor = sampleClass.getDeclaredConstructor(
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class);
        ctor.setAccessible(true);
        // t, voltage, current, power, consumed, regenerated, net
        Object a = ctor.newInstance(10.0, 3000.0, 10.0, 30000.0, 100.0, 20.0, 80.0);
        Object b = ctor.newInstance(12.0, 3200.0, 20.0, 64000.0, 300.0, 60.0, 240.0);
        Method at = TrainGlobal.class.getDeclaredMethod("at", List.class, double.class);
        at.setAccessible(true);
        Object mid = at.invoke(null, List.of(a, b), 11.0);
        assertEquals(11.0, (double) field(mid, "t"), 1e-9);
        assertEquals(3100.0, (double) field(mid, "u"), 1e-9);
        assertEquals(200.0, (double) field(mid, "consumed"), 1e-9);
        assertEquals(40.0, (double) field(mid, "regenerated"), 1e-9);
        assertEquals(160.0, (double) field(mid, "net"), 1e-9);
    }

    @Test
    public void rejectsTimeOutsideAvailableSamples() throws Exception {
        Class<?> sampleClass = Class.forName("org.supply.report.TrainGlobal$Sample");
        Constructor<?> ctor = sampleClass.getDeclaredConstructor(
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class);
        ctor.setAccessible(true);
        Object a = ctor.newInstance(10.0, 3000.0, 10.0, 30000.0, 100.0, 20.0, 80.0);
        Object b = ctor.newInstance(12.0, 3200.0, 20.0, 64000.0, 300.0, 60.0, 240.0);
        Method at = TrainGlobal.class.getDeclaredMethod("at", List.class, double.class);
        at.setAccessible(true);
        try {
            at.invoke(null, List.of(a, b), 12.1);
            fail("Expected rejection of uncovered leg boundary");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void stitchesThreeTrainsAtCompleteLegBoundaries() {
        Map<String, List<TrainGlobal.Leg>> legs = journeys();
        Map<String, List<TrainGlobal.Sample>> samples = Map.of(
                "fm-029", samples(100, 350),
                "fm-025", samples(0, 260),
                "fm-021", samples(0, 230));
        List<TrainGlobal.ChosenLeg> result = TrainGlobal.selectJourney("fm-029", legs, samples);
        assertEquals(List.of("fm-029", "fm-025", "fm-021"),
                result.stream().map(TrainGlobal.ChosenLeg::trainId).toList());
        assertEquals(List.of(0, 1, 2),
                result.stream().map(TrainGlobal.ChosenLeg::index).toList());
    }

    @Test
    public void keepsCompleteTrainWithoutSwitching() {
        Map<String, List<TrainGlobal.Leg>> legs = journeys();
        Map<String, List<TrainGlobal.Sample>> samples = Map.of(
                "fm-029", samples(0, 600),
                "fm-025", samples(0, 260),
                "fm-021", samples(0, 230));
        List<TrainGlobal.ChosenLeg> result = TrainGlobal.selectJourney("fm-029", legs, samples);
        assertEquals(List.of("fm-029", "fm-029", "fm-029"),
                result.stream().map(TrainGlobal.ChosenLeg::trainId).toList());
    }

    @Test
    public void rejectsMissingContinuationWithStation() {
        Map<String, List<TrainGlobal.Leg>> legs = journeys();
        try {
            TrainGlobal.selectJourney("fm-029", legs, Map.of("fm-029", samples(100, 350)));
            fail("Expected missing continuation");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("station X"));
        }
    }


    @Test
    public void acceptsArrivalGapAtToleranceButRejectsLargerGap() {
        Map<String, List<TrainGlobal.Leg>> legs = Map.of(
                "ad-001", List.of(new TrainGlobal.Leg("AD", "C", "D", 200.0, 288.0)));

        var accepted = TrainGlobal.selectJourney("ad-001", legs,
                Map.of("ad-001", samples(200.0, 287.0)));
        assertEquals(1, accepted.size());

        try {
            TrainGlobal.selectJourney("ad-001", legs,
                    Map.of("ad-001", samples(200.0, 286.9)));
            fail("Expected a 1.1 s arrival gap to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("No complete journey departure"));
        }
    }

    @Test
    public void incrementalUpdatePreservesOtherCasesAndReplacesMatchingCase() throws Exception {
        Path destination = Files.createTempFile("train-global-incremental", ".xlsx");
        try {
            try (XSSFWorkbook previous = new XSSFWorkbook()) {
                Sheet old = previous.createSheet("Train Global");
                String[] headers = {"StudyId", "Calc Type", "train_id", "route_id", "leg",
                        "from_station", "to_station", "start_time", "end_time", "duration_s",
                        "e_consumed_kWh", "e_regenerated_kWh", "e_net_kWh",
                        "p_mean_kW", "p_min_kW", "p_max_kW", "i_mean_A", "i_rms_A",
                        "i_min_A", "i_max_A", "u_mean_V", "u_min_V", "u_max_V"};
                Row header = old.createRow(0);
                for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);
                addGlobalRow(old, 1, "WE", "+0sek", "old-we");
                addGlobalRow(old, 2, "two_sections", "+0sek", "keep-other-study");
                addGlobalRow(old, 3, "WE", "+30sek", "keep-other-case");
                Sheet sources = previous.createSheet("Sources");
                String[] sourceHeaders = {"StudyId", "Calc Type", "project", "scenario", "base_hash", "generated_at"};
                Row sourceHeader = sources.createRow(0);
                for (int c = 0; c < sourceHeaders.length; c++) sourceHeader.createCell(c).setCellValue(sourceHeaders[c]);
                addSourceRow(sources, 1, "WE", "+0sek", "old");
                addSourceRow(sources, 2, "two_sections", "+0sek", "preserved");
                addSourceRow(sources, 3, "WE", "+30sek", "preserved-case");
                try (var stream = Files.newOutputStream(destination)) { previous.write(stream); }
            }
            try (XSSFWorkbook target = new XSSFWorkbook(); XSSFWorkbook input = new XSSFWorkbook()) {
                Sheet result = target.createSheet("Train Global");
                Row header = result.createRow(0);
                try (Workbook old = WorkbookFactory.create(destination.toFile())) {
                    Row oldHeader = old.getSheet("Train Global").getRow(0);
                    for (int c = 0; c < oldHeader.getLastCellNum(); c++)
                        header.createCell(c).setCellValue(oldHeader.getCell(c).getStringCellValue());
                }
                CellStyle time = target.createCellStyle();
                time.setDataFormat(target.createDataFormat().getFormat("[h]:mm:ss"));
                CellStyle number = target.createCellStyle();
                int next = TrainGlobal.copyExistingRows(destination, result, "WE", "+0sek", time, number);
                assertEquals(3, next);
                assertEquals("keep-other-study", result.getRow(1).getCell(2).getStringCellValue());
                assertEquals("keep-other-case", result.getRow(2).getCell(2).getStringCellValue());
                addGlobalRow(result, next, "WE", "+0sek", "new-we");
                Sheet metadata = input.createSheet("Metadata");
                Row project = metadata.createRow(0);
                project.createCell(0).setCellValue("project");
                project.createCell(1).setCellValue("new-project");
                TrainGlobal.updateSources(destination, input, target, "WE", "+0sek");
                Sheet sources = target.getSheet("Sources");
                assertEquals(3, sources.getLastRowNum());
                assertEquals("preserved", sources.getRow(1).getCell(2).getStringCellValue());
                assertEquals("preserved-case", sources.getRow(2).getCell(2).getStringCellValue());
                assertEquals("new-project", sources.getRow(3).getCell(2).getStringCellValue());
                assertEquals("new-we", result.getRow(3).getCell(2).getStringCellValue());
            }
        } finally {
            Files.deleteIfExists(destination);
        }
    }

    @Test
    public void rejectsIncompatibleExistingTrainGlobalHeader() throws Exception {
        Path destination = Files.createTempFile("train-global-bad-header", ".xlsx");
        try {
            try (XSSFWorkbook previous = new XSSFWorkbook()) {
                previous.createSheet("Train Global").createRow(0).createCell(0).setCellValue("Wrong");
                try (var stream = Files.newOutputStream(destination)) { previous.write(stream); }
            }
            try (XSSFWorkbook target = new XSSFWorkbook()) {
                try {
                    TrainGlobal.copyExistingRows(destination, target.createSheet("Train Global"),
                            "WE", "+0sek", target.createCellStyle(), target.createCellStyle());
                    fail("Expected incompatible header rejection");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("Incompatible"));
                }
            }
        } finally {
            Files.deleteIfExists(destination);
        }
    }

    private static void addGlobalRow(Sheet sheet, int index, String study, String calc, String train) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(study);
        row.createCell(1).setCellValue(calc);
        row.createCell(2).setCellValue(train);
        row.createCell(7).setCellValue(10.0 / 24.0);
        row.createCell(8).setCellValue(11.0 / 24.0);
        row.createCell(10).setCellValue(25.5);
    }

    private static void addSourceRow(Sheet sheet, int index, String study, String calc, String project) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(study);
        row.createCell(1).setCellValue(calc);
        for (int c = 2; c < 6; c++) row.createCell(c).setCellValue(c == 2 ? project : "");
    }

    private static Map<String, List<TrainGlobal.Leg>> journeys() {
        Map<String, List<TrainGlobal.Leg>> result = new LinkedHashMap<>();
        result.put("fm-029", List.of(
                new TrainGlobal.Leg("FM", "A", "X", 100, 200),
                new TrainGlobal.Leg("FM", "X", "Y", 300, 400),
                new TrainGlobal.Leg("FM", "Y", "D", 500, 600)));
        result.put("fm-025", List.of(
                new TrainGlobal.Leg("FM", "A", "X", -200, -100),
                new TrainGlobal.Leg("FM", "X", "Y", 100, 200),
                new TrainGlobal.Leg("FM", "Y", "D", 300, 400)));
        result.put("fm-021", List.of(
                new TrainGlobal.Leg("FM", "A", "X", -300, -200),
                new TrainGlobal.Leg("FM", "X", "Y", -100, 0),
                new TrainGlobal.Leg("FM", "Y", "D", 100, 200)));
        return result;
    }

    private static List<TrainGlobal.Sample> samples(double from, double to) {
        return List.of(new TrainGlobal.Sample(from, 3000, 10, 30000, 0, 0, 0),
                new TrainGlobal.Sample(to, 3000, 10, 30000, 100, 0, 100));
    }

    private static Object field(Object record, String name) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }
}
