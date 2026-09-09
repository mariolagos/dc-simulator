package org.supply.track;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class RouteViewExcelReaderTest {

    @Test
    public void readsRouteViewFromTrackdataWorkbook()
            throws Exception {

        Path workbookPath =
                Files.createTempFile(
                        "track-route-",
                        ".xlsx"
                );

        try {
            writeWorkbook(workbookPath);

            RouteView route =
                    new RouteViewExcelReader().read(
                            workbookPath,
                            "W-E",
                            "RED-WE"
                    );

            assertEquals("RED-WE", route.getRouteId());
            assertEquals("W-E", route.getDescription());

            List<PathSample> samples =
                    route.getSamples();

            assertEquals(4, samples.size());

            assertSample(
                    samples.get(0),
                    0,
                    "101",
                    "U",
                    12_400
            );

            assertSample(
                    samples.get(1),
                    6_349,
                    "101",
                    "U",
                    18_749
            );

            assertSample(
                    samples.get(2),
                    6_350,
                    "200",
                    "U1",
                    0
            );

            assertSample(
                    samples.get(3),
                    12_550,
                    "102",
                    "U",
                    41_300
            );
        } finally {
            Files.deleteIfExists(workbookPath);
        }
    }

    private static void writeWorkbook(Path path)
            throws Exception {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("W-E");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("position [m]");
            header.createCell(1).setCellValue("trackSection");
            header.createCell(2).setCellValue("trackNumber");
            header.createCell(3).setCellValue("bisKm");
            header.createCell(4).setCellValue("bisMeter");
            header.createCell(5).setCellValue("trackInformation");

            writeRow(
                    sheet,
                    1,
                    0,
                    101,
                    "U",
                    12,
                    400,
                    "RED-WE"
            );

            writeRow(
                    sheet,
                    2,
                    6_349,
                    101,
                    "U",
                    18,
                    749,
                    "RED-WE"
            );

            writeRow(
                    sheet,
                    3,
                    6_350,
                    200,
                    "U1",
                    0,
                    0,
                    "RED-WE"
            );

            writeRow(
                    sheet,
                    4,
                    12_550,
                    102,
                    "U",
                    41,
                    300,
                    "RED-WE"
            );

            try (OutputStream output =
                         Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
    }

    private static void writeRow(
            Sheet sheet,
            int rowIndex,
            double pathPositionM,
            int sectionId,
            String trackId,
            int bisKm,
            int bisMeter,
            String routeId
    ) {
        Row row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(pathPositionM);
        row.createCell(1).setCellValue(sectionId);
        row.createCell(2).setCellValue(trackId);
        row.createCell(3).setCellValue(bisKm);
        row.createCell(4).setCellValue(bisMeter);
        row.createCell(5).setCellValue(routeId);
    }

    private static void assertSample(
            PathSample sample,
            double expectedPathPositionM,
            String expectedSectionId,
            String expectedTrackId,
            int expectedRailwayPositionM
    ) {
        assertEquals(
                expectedPathPositionM,
                sample.getPathPositionM(),
                1e-9
        );

        RwyCoordinate coordinate =
                sample.getRailwayCoordinate();

        assertEquals(
                expectedSectionId,
                coordinate.getSectionId()
        );
        assertEquals(
                expectedTrackId,
                coordinate.getTrackId()
        );
        assertEquals(
                expectedRailwayPositionM,
                coordinate.getPositionM()
        );
    }
}