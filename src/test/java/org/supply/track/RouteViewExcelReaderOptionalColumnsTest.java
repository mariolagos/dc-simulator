package org.supply.track;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RouteViewExcelReaderOptionalColumnsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path workbook(boolean optional, String track, String route,
                          int[][] points) throws Exception {
        Path path = temp.newFile().toPath();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("test");
            Row header = sheet.createRow(0);
            String[] names = optional
                    ? new String[]{"position [m]", "trackSection", "bisKm",
                    "bisMeter", "trackNumber", "trackInformation"}
                    : new String[]{"position [m]", "trackSection", "bisKm", "bisMeter"};
            for (int c = 0; c < names.length; c++) header.createCell(c).setCellValue(names[c]);
            for (int i = 0; i < points.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < 4; c++) row.createCell(c).setCellValue(points[i][c]);
                if (optional && track != null) row.createCell(4).setCellValue(track);
                if (optional && route != null) row.createCell(5).setCellValue(route);
            }
            try (OutputStream out = Files.newOutputStream(path)) { book.write(out); }
        }
        return path;
    }

    private static int[][] forward() {
        return new int[][]{{0,21,0,0},{1000,21,1,0},{2000,21,2,0}};
    }

    @Test public void acceptsAbsentOptionalColumns() throws Exception {
        RouteView view = new RouteViewExcelReader().read(workbook(false,null,null,forward()),"test","F-M");
        assertEquals("F-M",view.getRouteId());
        assertNull(view.getSamples().get(0).getRailwayCoordinate().getTrackId());
    }

    @Test public void acceptsBlankOptionalColumns() throws Exception {
        RouteView view = new RouteViewExcelReader().read(workbook(true," "," ",forward()),"test","F-M");
        assertNull(view.getSamples().get(0).getRailwayCoordinate().getTrackId());
    }

    @Test public void preservesExplicitTrackAndRoute() throws Exception {
        RouteView view = new RouteViewExcelReader().read(workbook(true,"U","F-M",forward()),"test","F-M");
        assertEquals("U",view.getSamples().get(0).getRailwayCoordinate().getTrackId());
    }

    @Test public void rejectsExplicitRouteMismatch() throws Exception {
        expectFailure(workbook(true,null,"wrong",forward()),"Unexpected trackInformation");
    }

    @Test public void reportsMissingBoardForwardAndReverse() throws Exception {
        expectFailure(workbook(false,null,null,new int[][]{{0,21,0,0},{2000,21,2,0}}),"21 1+000");
        expectFailure(workbook(false,null,null,new int[][]{{0,21,2,0},{2000,21,0,0}}),"21 1+000");
    }

    @Test public void acceptsReverseAndPartialIntervals() throws Exception {
        new RouteViewExcelReader().read(workbook(false,null,null,
                new int[][]{{0,23,8,70},{70,23,8,0},{1070,23,7,0},{2070,23,6,0},
                        {3070,23,5,0},{4070,23,4,0},{4494,23,3,576}}),"test","F-M");
    }

    @Test public void doesNotRequireBoardsOutsideSectionInterval() throws Exception {
        new RouteViewExcelReader().read(workbook(false,null,null,
                new int[][]{{0,21,0,480},{100,21,0,600},{101,24,2,600},{501,24,3,0}}),"test","F-M");
    }

    @Test public void doesNotBorrowBoardsFromSeparateVisit() throws Exception {
        expectFailure(workbook(false,null,null,
                new int[][]{{0,21,0,0},{2000,21,2,0},{2001,24,0,0},{2002,24,0,1},
                        {2003,21,1,0},{2004,21,1,1}}),"21 1+000");
    }

    @Test public void stillRejectsNonIncreasingRouteDistance() throws Exception {
        expectFailure(workbook(false,null,null,new int[][]{{0,21,0,0},{0,21,1,0}}),"strictly increasing");
    }

    private void expectFailure(Path path, String message) throws Exception {
        try {
            new RouteViewExcelReader().read(path,"test","F-M");
            fail("Expected " + message);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),expected.getMessage().contains(message));
        }
    }
}
