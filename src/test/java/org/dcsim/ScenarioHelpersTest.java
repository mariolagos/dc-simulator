package org.dcsim;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ScenarioHelpersTest {

    @Test
    public void toAbsolutePositionM_interpolates_inside_bounds() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 0, 0.0),
                new ScenarioHelpers.TrackPoint(5000.0, 5, 0.0)
        );

        double abs = ScenarioHelpers.toAbsolutePositionM(2500.0, pts);

        assertEquals(2500.0, abs, 1e-9);
    }

    @Test
    public void toAbsolutePositionM_exact_hit_returns_exact_absolute_position() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 0, 0.0),
                new ScenarioHelpers.TrackPoint(5000.0, 5, 0.0)
        );

        assertEquals(0.0, ScenarioHelpers.toAbsolutePositionM(0.0, pts), 1e-9);
        assertEquals(5000.0, ScenarioHelpers.toAbsolutePositionM(5000.0, pts), 1e-9);
    }

    @Test
    public void buildTrackPoints_rejects_missing_column() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("track");

            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("position [m]");
            h.createCell(1).setCellValue("bisKm");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScenarioHelpers.buildTrackPoints(sh)
            );

            assertTrue(ex.getMessage().contains("Missing required column"));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void toAbsolutePositionM_interpolates_forward_direction() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 0, 0.0),
                new ScenarioHelpers.TrackPoint(5000.0, 5, 0.0)
        );

        assertEquals(0.0, ScenarioHelpers.toAbsolutePositionM(0.0, pts), 1e-9);
        assertEquals(2500.0, ScenarioHelpers.toAbsolutePositionM(2500.0, pts), 1e-9);
        assertEquals(5000.0, ScenarioHelpers.toAbsolutePositionM(5000.0, pts), 1e-9);
    }

    @Test
    public void toAbsolutePositionM_interpolates_reverse_direction() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 4, 655.0),
                new ScenarioHelpers.TrackPoint(4655.0, 0, 0.0)
        );

        assertEquals(4655.0, ScenarioHelpers.toAbsolutePositionM(0.0, pts), 1e-9);
        assertEquals(2327.5, ScenarioHelpers.toAbsolutePositionM(2327.5, pts), 1e-9);
        assertEquals(0.0, ScenarioHelpers.toAbsolutePositionM(4655.0, pts), 1e-9);
    }

    @Test
    public void toAbsolutePositionM_rejects_below_track_range() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 0, 0.0),
                new ScenarioHelpers.TrackPoint(5000.0, 5, 0.0)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.toAbsolutePositionM(-0.1, pts)
        );

        assertTrue(ex.getMessage().contains("outside track bounds"));
    }

    @Test
    public void toAbsolutePositionM_rejects_above_track_range() {
        List<ScenarioHelpers.TrackPoint> pts = List.of(
                new ScenarioHelpers.TrackPoint(0.0, 0, 0.0),
                new ScenarioHelpers.TrackPoint(5000.0, 5, 0.0)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.toAbsolutePositionM(5000.1, pts)
        );

        assertTrue(ex.getMessage().contains("outside track bounds"));
    }

    @Test
    public void validateTrackPoints_rejects_empty() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.validateTrackPoints(List.of())
        );

        assertTrue(ex.getMessage().contains("must not be empty"));
    }

    @Test
    public void validateTrackPoints_rejects_single_point() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.validateTrackPoints(
                        List.of(new ScenarioHelpers.TrackPoint(0.0, 0, 0.0))
                )
        );

        assertTrue(ex.getMessage().contains("at least 2 points"));
    }

    @Test
    public void validateTrackPoints_rejects_duplicate_positions() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.validateTrackPoints(List.of(
                        new ScenarioHelpers.TrackPoint(10.0, 0, 10.0),
                        new ScenarioHelpers.TrackPoint(10.0, 0, 20.0)
                ))
        );

        assertTrue(ex.getMessage().contains("strictly increasing"));
    }

    @Test
    public void validateTrackPoints_rejects_unsorted_positions() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScenarioHelpers.validateTrackPoints(List.of(
                        new ScenarioHelpers.TrackPoint(20.0, 0, 20.0),
                        new ScenarioHelpers.TrackPoint(10.0, 0, 10.0)
                ))
        );

        assertTrue(ex.getMessage().contains("strictly increasing"));
    }

    @Test
    public void buildTrackPoints_reads_valid_sheet() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("track");

            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("position [m]");
            h.createCell(1).setCellValue("bisKm");
            h.createCell(2).setCellValue("bisMeter");

            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(0.0);
            r1.createCell(1).setCellValue(0);
            r1.createCell(2).setCellValue(0.0);

            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue(5000.0);
            r2.createCell(1).setCellValue(5);
            r2.createCell(2).setCellValue(0.0);

            List<ScenarioHelpers.TrackPoint> pts = ScenarioHelpers.buildTrackPoints(sh);

            assertEquals(2, pts.size());
            assertEquals(0.0, pts.get(0).positionM, 1e-9);
            assertEquals(5000.0, pts.get(1).positionM, 1e-9);
            assertEquals(0.0, pts.get(0).absolutePositionM(), 1e-9);
            assertEquals(5000.0, pts.get(1).absolutePositionM(), 1e-9);
        }
    }

    @Test
    public void buildTrackPoints_rejects_duplicate_position_header() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("track");

            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("position [m]");
            h.createCell(1).setCellValue("bisKm");
            h.createCell(2).setCellValue("bisMeter");
            h.createCell(3).setCellValue("position [m]");

            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(0.0);
            r1.createCell(1).setCellValue(0);
            r1.createCell(2).setCellValue(0.0);
            r1.createCell(3).setCellValue(999.0);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScenarioHelpers.buildTrackPoints(sh)
            );

            assertTrue(ex.getMessage().contains("Ambiguous header"));
        }
    }

    @Test
    public void buildTrackPoints_rejects_missing_bisMeter() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("track");

            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("position [m]");
            h.createCell(1).setCellValue("bisKm");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScenarioHelpers.buildTrackPoints(sh)
            );

            assertTrue(ex.getMessage().contains("Missing required column"));
        }
    }

    @Test
    public void buildTrackPoints_rejects_invalid_bisMeter() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("track");

            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("position [m]");
            h.createCell(1).setCellValue("bisKm");
            h.createCell(2).setCellValue("bisMeter");

            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(0.0);
            r1.createCell(1).setCellValue(0);
            r1.createCell(2).setCellValue(0.0);

            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue(5000.0);
            r2.createCell(1).setCellValue(5);
            r2.createCell(2).setCellValue(1000.0);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScenarioHelpers.buildTrackPoints(sh)
            );

            assertTrue(ex.getMessage().contains("Invalid bisMeter"));
        }
    }
}