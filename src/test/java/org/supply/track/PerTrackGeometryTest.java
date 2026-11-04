package org.supply.track;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class PerTrackGeometryTest {
    private static RwyCoordinate c(int p,String track) {
        return new RwyCoordinate("X",RwyCoordinate.formatKmShort(p),p,track);
    }
    private static RouteView view(String name,String track,double middle,double total,boolean reverse) {
        return new RouteView(name,name,List.of(new PathSample(0,c(reverse?2000:0,track)),
                new PathSample(middle,c(1000,track)),new PathSample(total,c(reverse?0:2000,track))));
    }
    @Test public void differentTrackLengthsAndIntermediateScalesRemainIndependent() {
        var n=view("a","N",1010,2010,false);
        var s=view("b","S",1000,2000,true);
        var sections=TrackSectionsFromRouteViews.build(Map.of("a",n,"b",s));
        var section=sections.get("X");var mapper=new TrackSectionMapper();
        assertEquals(1010,mapper.toModel(section,c(1000,"N")).getPositionM());
        assertEquals(1000,mapper.toModel(section,c(1000,"S")).getPositionM());
        assertEquals(2010,mapper.toModel(section,c(2000,"N")).getPositionM());
        assertEquals(2000,mapper.toModel(section,c(2000,"S")).getPositionM());
        assertThrows(IllegalArgumentException.class,()->mapper.toModel(section,c(1000,null)));
        assertThrows(IllegalArgumentException.class,()->mapper.toModel(section,c(1000,"Z")));
        var transform=new DefaultTrackTransformService(new LoadedTrackModel(sections,List.of(),List.of(),Map.of("a",n,"b",s)));
        assertEquals(2010,transform.distanceOnRoute("a",c(0,null),c(2000,null)),0);
        assertEquals(2000,transform.distanceOnRoute("b",c(0,null),c(2000,null)),0);
        assertEquals(1500,transform.pathToModel("b",500).getPositionM());
    }
    @Test public void equalTotalsDoNotRequireEqualIntermediateScalesAcrossTracks() {
        var sections=TrackSectionsFromRouteViews.build(Map.of("a",view("a","N",1100,2000,false),
                "b",view("b","S",1000,2000,false)));
        var m=new TrackSectionMapper();
        assertEquals(1100,m.toModel(sections.get("X"),c(1000,"N")).getPositionM());
        assertEquals(1000,m.toModel(sections.get("X"),c(1000,"S")).getPositionM());
    }
    @Test public void conflictingObservationsOfSameTrackAreRejected() {
        var ex=assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of(
                "a",view("a","N",1000,2000,false),"b",view("b","N",1000,2010,true))));
        assertTrue(ex.getMessage(),ex.getMessage().contains("Track N"));
        assertTrue(ex.getMessage(),ex.getMessage().contains("same track"));
    }
    @Test public void trackCoverageIsNotBorrowedFromOtherTrack() {
        var a=new RouteView("a","a",List.of(new PathSample(0,c(0,"N")),new PathSample(1000,c(1000,"N"))));
        var b=new RouteView("b","b",List.of(new PathSample(0,c(0,"S")),new PathSample(2000,c(2000,"S"))));
        var sec=TrackSectionsFromRouteViews.build(Map.of("a",a,"b",b)).get("X");
        assertThrows(IllegalArgumentException.class,()->new TrackSectionMapper().toModel(sec,c(1500,"N")));
    }
    @Test public void explicitAndUnspecifiedTracksCannotSilentlyMerge() {
        assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of(
                "a",view("a","N",1000,2000,false),"b",view("b",null,1000,2000,false))));
    }
    @Test public void unlabelledExcelSamplesReceiveDeclaredRouteTracks() throws Exception {
        Path dir=Files.createTempDirectory("track-geometry-test"),file=dir.resolve("track.xlsx");
        try {
            try(Workbook book=new XSSFWorkbook()) {
                sheet(book,"A",1010," ");sheet(book,"B",1000,"");
                try(var out=Files.newOutputStream(file)){book.write(out);}
            }
            var cfg=ConfigFactory.parseString("track { route_data_excel=\"track.xlsx\", route_views=[{route_id=A,sheet=A,track_id=N},{route_id=B,sheet=B,track_id=S}] }");
            var model=new TrackConfigLoader().load(cfg,dir.resolve("run.conf"));
            var transform=new DefaultTrackTransformService(model);
            assertEquals(1010,transform.toModel("A",c(1000,"N")).getPositionM());
            assertEquals(1000,transform.toModel("B",c(1000,"S")).getPositionM());
            assertEquals("S",transform.pathToRailway("B",500).getTrackId());
            try(Workbook book=new XSSFWorkbook()) {sheet(book,"A",1010,"S");try(var out=Files.newOutputStream(file)){book.write(out);}}
            assertThrows(IllegalArgumentException.class,()->new TrackConfigLoader().load(cfg,dir.resolve("run.conf")));
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(dir);}
    }
    private static void sheet(Workbook book,String name,int distance,String track) {
        Sheet s=book.createSheet(name);Row h=s.createRow(0);
        String[] names={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
        for(int i=0;i<names.length;i++)h.createCell(i).setCellValue(names[i]);
        for(int i=0;i<2;i++) {Row r=s.createRow(i+1);r.createCell(0).setCellValue(i*distance);
            r.createCell(1).setCellValue("X");r.createCell(2).setCellValue(track);r.createCell(3).setCellValue(i);r.createCell(4).setCellValue(0);}
    }
}
