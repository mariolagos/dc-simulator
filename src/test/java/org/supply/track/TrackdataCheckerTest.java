package org.supply.track;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class TrackdataCheckerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String[] HEADERS={"position [m]","trackSection","trackNumber","bisKm","bisMeter","trackInformation"};
    private static void sheet(XSSFWorkbook book,String name,String track,int distance) {
        var sheet=book.createSheet(name);var header=sheet.createRow(0);
        for(int i=0;i<HEADERS.length;i++)header.createCell(i).setCellValue(HEADERS[i]);
        for(int i=0;i<2;i++) {
            var row=sheet.createRow(i+1);row.createCell(0).setCellValue(i*distance);
            row.createCell(1).setCellValue("X");row.createCell(2).setCellValue(track);
            row.createCell(3).setCellValue(i);row.createCell(4).setCellValue(0);
        }
    }
    private Path workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        Path file=temporary.newFile("track"+UUID.randomUUID()+".xlsx").toPath();
        try(var book=new XSSFWorkbook()) {edit.accept(book);try(var out=Files.newOutputStream(file)){book.write(out);}}
        return file;
    }
    private TrackdataChecker.Report check(Consumer<XSSFWorkbook> edit) throws Exception {
        return TrackdataChecker.check(workbook(edit),List.of());
    }
    @Test public void acceptsDifferentLengthsOnDifferentTracksAsWarnings() throws Exception {
        var report=check(b->{sheet(b,"A","N",1010);sheet(b,"B","S",1000);});
        assertEquals(report.text(),0,report.errors());assertTrue(report.warnings()>0);
        assertEquals(2,report.intervals().size());assertEquals(4,report.samples());
        assertTrue(report.text().contains("1010.0"));
    }
    @Test public void detectsContradictingDistancesOnSameTrackAcrossSheets() throws Exception {
        var report=check(b->{sheet(b,"A","N",1010);sheet(b,"B","N",1000);});
        assertTrue(report.text(),report.errors()>0);assertTrue(report.text().contains("incompatible distances"));
    }
    @Test public void doesNotGuessPhysicalIdentityOfUnspecifiedTracks() throws Exception {
        var report=check(b->{sheet(b,"A","",1010);sheet(b,"B","",1000);});
        assertEquals(report.text(),0,report.errors());assertTrue(report.text().contains("Blank trackNumber"));
    }
    @Test public void diagnosesMultipleMalformedRowsAndRejectsFractionalCoordinates() throws Exception {
        var report=check(b->{sheet(b,"A","N",1000);var s=b.getSheet("A");
            s.getRow(1).getCell(4).setCellValue(1000);s.getRow(2).getCell(3).setCellValue(1.5);});
        assertTrue(report.text(),report.errors()>=2);assertTrue(report.text().contains("bisMeter must be 0..999"));
        assertTrue(report.text().contains("bisKm must be an integer"));
    }
    @Test public void rejectsNonFinitePathValues() throws Exception {
        var report=check(b->{sheet(b,"A","N",1000);b.getSheet("A").getRow(2).getCell(0).setCellValue("NaN");});
        assertTrue(report.text(),report.errors()>0);assertTrue(report.text().contains("Non-finite"));
    }
    @Test public void rejectsBackwardPathAndLongSectionTransition() throws Exception {
        var report=check(b->{sheet(b,"A","N",10);b.getSheet("A").getRow(2).getCell(1).setCellValue("Y");
            sheet(b,"B","S",1000);b.getSheet("B").getRow(1).getCell(0).setCellValue(2000);});
        assertTrue(report.text().contains("transition exceeds 1 m"));assertTrue(report.text().contains("must increase"));
    }
    @Test public void knownRouteIdNeedNotEqualSheetName() throws Exception {
        var report=check(b->{sheet(b,"physical-data","N",1000);var s=b.getSheet("physical-data");
            s.getRow(1).createCell(5).setCellValue("RouteA");s.getRow(2).createCell(5).setCellValue("RouteA");});
        assertEquals(report.text(),0,report.errors());
    }
    @Test public void reportsMissingSheetAndMissingColumns() throws Exception {
        Path file=workbook(b->sheet(b,"A","N",1000));
        assertTrue(TrackdataChecker.check(file,List.of("missing")).errors()>0);
        var report=check(b->{var s=b.createSheet("A");s.createRow(0).createCell(0).setCellValue("position [m]");});
        assertTrue(report.text().contains("Missing columns"));
    }
    @Test public void checksOnlySelectedSheetsAndSkipsNotes() throws Exception {
        Path file=workbook(b->{sheet(b,"good","N",1000);sheet(b,"bad","S",1000);
            b.getSheet("bad").getRow(2).getCell(4).setCellValue(1000);b.createSheet("Notes").createRow(0).createCell(0).setCellValue("Notes");});
        assertEquals(0,TrackdataChecker.check(file,List.of("good")).errors());
        var all=TrackdataChecker.check(file,List.of());assertTrue(all.errors()>0);assertTrue(all.text().contains("Skipped"));
    }
    @Test public void disjointObservationsAreNotFalseContradictions() throws Exception {
        var report=check(b->{sheet(b,"A","N",1000);sheet(b,"B","N",1000);
            b.getSheet("B").getRow(1).getCell(3).setCellValue(2);b.getSheet("B").getRow(2).getCell(3).setCellValue(3);});
        assertEquals(report.text(),0,report.errors());
    }
    @Test public void reportsGenuineDuplicateRowsWithoutInventingDistanceErrors() throws Exception {
        var report=check(b->{sheet(b,"A","N",1000);var s=b.getSheet("A");var r=s.createRow(3);
            r.createCell(0).setCellValue(1000);r.createCell(1).setCellValue("X");r.createCell(2).setCellValue("N");
            r.createCell(3).setCellValue(1);r.createCell(4).setCellValue(0);});
        assertEquals(report.text(),0,report.errors());
    }
}
