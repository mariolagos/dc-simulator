package org.supply.report;

import org.junit.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.Files;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

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

    private static Object field(Object record, String name) throws Exception {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }
}
