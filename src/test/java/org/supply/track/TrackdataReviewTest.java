package org.supply.track;

import com.typesafe.config.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.supply.app.TrackdataCheck;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrackdataReviewTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path input(int distance) throws Exception {
        Path p=temp.newFile("data-"+distance+".xlsx").toPath();
        try(var b=new XSSFWorkbook()) {
            var s=b.createSheet("A");var h=s.createRow(0);
            String[] names={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
            for(int i=0;i<names.length;i++)h.createCell(i).setCellValue(names[i]);
            for(int i=0;i<2;i++){var r=s.createRow(i+1);r.createCell(0).setCellValue(i*distance);
                r.createCell(1).setCellValue("X");r.createCell(2).setCellValue("N");r.createCell(3).setCellValue(i);r.createCell(4).setCellValue(0);}
            try(var out=Files.newOutputStream(p)){b.write(out);}
        }
        return p;
    }
    private Path template(Path input) throws Exception {
        Path review=temp.getRoot().toPath().resolve("review.conf");
        TrackdataReview.createTemplate(review,TrackdataChecker.check(input,List.of()).coordinateIntervals());return review;
    }
    private void approve(Path file,String reason) throws Exception {
        Config config=ConfigFactory.parseFile(file.toFile());List<Map<String,Object>> rows=new ArrayList<>();
        for(Config c:config.getConfigList("coordinate_intervals")) {
            rows.add(c.withValue("accepted",ConfigValueFactory.fromAnyRef(true))
                    .withValue("reason",ConfigValueFactory.fromAnyRef(reason)).root().unwrapped());
        }
        Files.writeString(file,ConfigFactory.empty().withValue("coordinate_intervals",ConfigValueFactory.fromIterable(rows)).root().render());
    }
    @Test public void templateDoesNotApproveAnything() throws Exception {
        Path p=input(1010),r=template(p);
        assertEquals(0,TrackdataReview.read(r).acceptedIntervals().size());
        var report=TrackdataChecker.check(p,List.of(),TrackdataReview.read(r));assertEquals(1,report.warnings());
    }
    @Test public void exactReviewedIntervalBecomesInfoAndReviewIsPreserved() throws Exception {
        Path p=input(1010),r=template(p);approve(r,"Verified synthetic long kilometre");byte[] original=Files.readAllBytes(r);
        var report=TrackdataChecker.check(p,List.of(),TrackdataReview.read(r));
        assertEquals(0,report.errors());assertEquals(0,report.warnings());assertTrue(report.text().contains("Accepted coordinate interval"));
        assertTrue(report.text().contains("Verified synthetic long kilometre"));
        TrackdataCheck.main(new String[]{p.toString(),"--review",r.toString(),"--output",temp.getRoot().toPath().resolve("report.txt").toString()});
        assertArrayEquals(original,Files.readAllBytes(r));
    }
    @Test public void changedDistanceRemainsWarningAndStaleApprovalIsReported() throws Exception {
        Path p=input(1010),r=template(p);approve(r,"Verified");
        var report=TrackdataChecker.check(input(1020),List.of(),TrackdataReview.read(r));
        assertEquals(2,report.warnings());assertTrue(report.text().contains("no longer matches"));
        assertFalse(report.text().contains("Accepted coordinate interval from row"));
    }
    @Test public void acceptanceRequiresReasonAndRejectsUnknownFields() throws Exception {
        Path p=input(1010),r=template(p);approve(r,"");
        assertThrows(IllegalArgumentException.class,()->TrackdataReview.read(r));
        Files.writeString(r,"coordinate_intervals=[]\nunrecognised=true");
        assertThrows(IllegalArgumentException.class,()->TrackdataReview.read(r));
    }
    @Test public void newTemplateIsCreatedByAppAndNeverOverwritten() throws Exception {
        Path p=input(1010),r=temp.getRoot().toPath().resolve("approvals.conf"),out=temp.getRoot().toPath().resolve("result.txt");
        TrackdataCheck.main(new String[]{p.toString(),"--review",r.toString(),"--output",out.toString()});
        assertEquals(0,TrackdataReview.read(r).acceptedIntervals().size());
        assertTrue(Files.readString(out).contains("warnings=1"));
        assertThrows(FileAlreadyExistsException.class,()->TrackdataReview.createTemplate(r,List.of()));
    }
    @Test public void approvalsCannotOverwriteWorkbookOrReport() throws Exception {
        Path p=input(1010),out=temp.getRoot().toPath().resolve("report.txt");byte[] before=Files.readAllBytes(p);
        assertThrows(IllegalArgumentException.class,()->TrackdataCheck.main(new String[]{p.toString(),"--review",p.toString(),"--output",out.toString()}));
        assertArrayEquals(before,Files.readAllBytes(p));
        assertThrows(IllegalArgumentException.class,()->TrackdataCheck.main(new String[]{p.toString(),"--review",out.toString(),"--output",out.toString()}));
    }
    @Test public void acceptedCoordinateDifferenceCannotHideSameTrackContradiction() throws Exception {
        Path p=input(1010),r=template(p);approve(r,"Verified coordinate interval");
        XSSFWorkbook book;
        try(var in=Files.newInputStream(p)){book=new XSSFWorkbook(in);}
        try(book){var copy=book.cloneSheet(0);book.setSheetName(book.getSheetIndex(copy),"B");
            copy.getRow(2).getCell(0).setCellValue(1000);
            try(var out=Files.newOutputStream(p)){book.write(out);}}
        var report=TrackdataChecker.check(p,List.of(),TrackdataReview.read(r));
        assertTrue(report.text(),report.errors()>0);assertTrue(report.text().contains("incompatible distances"));
    }
    @Test public void oppositeDirectionOrOtherTrackCannotReuseApproval() throws Exception {
        var interval=new TrackdataReview.Interval("A","X","N",0,1000,1010);
        Path r=temp.getRoot().toPath().resolve("review.conf");TrackdataReview.createTemplate(r,List.of(interval));approve(r,"Verified");
        var review=TrackdataReview.read(r);
        assertNull(review.reason(new TrackdataReview.Interval("A","X","S",0,1000,1010)));
        assertNull(review.reason(new TrackdataReview.Interval("A","X","N",1000,0,1010)));
        assertNull(review.reason(new TrackdataReview.Interval("A","X","N",0,1000,1011)));
    }
}
