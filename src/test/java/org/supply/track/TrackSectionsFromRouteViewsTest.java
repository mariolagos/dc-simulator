package org.supply.track;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrackSectionsFromRouteViewsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static PathSample p(double path, String section, int railway, String track) {
        return new PathSample(path, new RwyCoordinate(section, RwyCoordinate.formatKmShort(railway), railway, track));
    }
    private static RouteView view(String name, PathSample... samples) {
        return new RouteView(name, name, List.of(samples));
    }
    private static RwyCoordinate c(String section, int pos, String track) {
        return new RwyCoordinate(section, RwyCoordinate.formatKmShort(pos), pos, track);
    }
    @Test public void derivesPhysicalDistancesAndChecksOppositeRouteWithDifferentTrack() {
        var forward=view("forward",p(0,"1",0,"N"),p(1040,"1",1000,"N"),p(2040,"1",2000,"N"));
        var reverse=view("reverse",p(0,"1",2000,"S"),p(1000,"1",1000,"S"),p(2040,"1",0,"S"));
        var sections=TrackSectionsFromRouteViews.build(Map.of("reverse",reverse,"forward",forward));
        var mapper=new TrackSectionMapper();
        assertEquals(520,mapper.toModel(sections.get("1"),c("1",500,"N")).getPositionM());
        assertEquals(1040,mapper.toModel(sections.get("1"),c("1",1000,"N")).getPositionM());
        assertEquals(1540,mapper.toModel(sections.get("1"),c("1",1500,"S")).getPositionM());
        assertEquals(2040,mapper.toModel(sections.get("1"),c("1",2000,"S")).getPositionM());
    }
    @Test public void partialOverlappingViewsExtendSectionWithoutExtrapolation() {
        var a=view("a",p(0,"1",500,"N"),p(500,"1",1000,"N"),p(1000,"1",1500,"N"));
        var b=view("b",p(0,"1",1000,"S"),p(1000,"1",2000,"S"));
        var section=TrackSectionsFromRouteViews.build(Map.of("b",b,"a",a)).get("1");
        var m=new TrackSectionMapper();assertEquals(1500,m.toModel(section,c("1",2000,"N")).getPositionM());
        assertThrows(IllegalArgumentException.class,()->m.toModel(section,c("1",499,"N")));
    }
    @Test public void rejectsDisjointIntervalsAndContradictingPhysicalDistances() {
        var a=view("a",p(0,"1",0,"N"),p(1000,"1",1000,"N"));
        var gap=view("gap",p(0,"1",2000,"S"),p(1000,"1",3000,"S"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("a",a,"gap",gap))).getMessage().contains("no route data covers"));
        var contradiction=view("b",p(0,"1",0,"S"),p(1020,"1",1000,"S"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("a",a,"b",contradiction))).getMessage().contains("incompatible distances"));
    }
    @Test public void detectsAccumulatedDifferenceAndHandlesRoundedDistances() {
        var a=view("a",p(0,"1",0,"N"),p(1000.4,"1",1000,"N"),p(2000.8,"1",2000,"N"));
        var b=view("b",p(0,"1",0,"S"),p(1001.0,"1",1000,"S"),p(2002.2,"1",2000,"S"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("a",a,"b",b))).getMessage().contains("incompatible distances"));
    }
    @Test public void detectsDifferentIntermediateScaleDespiteEqualEndpoints() {
        var detailed=view("a",p(0,"1",0,"N"),p(600,"1",500,"N"),p(1000,"1",1000,"N"));
        var sparse=view("b",p(0,"1",0,"S"),p(1000,"1",1000,"S"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("a",detailed,"b",sparse))).getMessage().contains("incompatible distances"));
    }
    @Test public void rejectsInconsistentSamplesAndUncoveredSingleSection() {
        var turn=view("x",p(0,"1",0,"N"),p(100,"1",100,"N"),p(200,"1",0,"N"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("x",turn))).getMessage().contains("change direction"));
        var lone=view("x",p(0,"1",0,"N"),p(1,"2",0,"N"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TrackSectionsFromRouteViews.build(Map.of("x",lone))).getMessage().contains("at least two positions"));
    }
    private static void writeSheet(Workbook book, String name, boolean reverse) {
        Sheet s=book.createSheet(name); Row header=s.createRow(0);
        String[] labels={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
        for(int i=0;i<labels.length;i++)header.createCell(i).setCellValue(labels[i]);
        int[] sec=reverse?new int[]{2,2,1,1,1}:new int[]{1,1,1,2,2};
        int[] railway=reverse?new int[]{1000,0,2000,1000,0}:new int[]{0,1000,2000,0,1000};
        double[] path=reverse?new double[]{0,1000,1001,2001,3041}:new double[]{0,1040,2040,2041,3041};
        for(int i=0;i<path.length;i++) {
            Row r=s.createRow(i+1);r.createCell(0).setCellValue(path[i]);r.createCell(1).setCellValue(sec[i]);
            r.createCell(2).setCellValue(reverse?"S":"N");r.createCell(3).setCellValue(railway[i]/1000);r.createCell(4).setCellValue(railway[i]%1000);
        }
    }
    @Test public void trackConfigLoadsExcelWithoutManualBoardsAndKeepsJunctions() throws Exception {
        Path dir=temp.newFolder().toPath(),file=dir.resolve("track.xlsx");
        try(Workbook book=new XSSFWorkbook()) {writeSheet(book,"fwd",false);writeSheet(book,"rev",true);try(var out=Files.newOutputStream(file)){book.write(out);}}
        var config=ConfigFactory.parseString("""
            track {
              route_data_excel = "track.xlsx"
              route_views = [{route_id=FWD,sheet=fwd},{route_id=REV,sheet=rev}]
              junctions = [{from_position_rwy="1 2+000 N",to_position_rwy="2 0+000 N"}]
            }
            """);
        var model=new TrackConfigLoader().load(config,dir.resolve("study.conf"));
        assertEquals(Set.of("1","2"),model.getSectionsById().keySet());assertEquals(1,model.getJunctions().size());
        var transform=new DefaultTrackTransformService(model);
        assertEquals(1040,transform.toModel("FWD",c("1",1000,"N")).getPositionM());
        assertEquals(1000,transform.toModel("REV",c("2",1000,"S")).getPositionM());
        assertEquals(4,transform.linePath(transform.toModel("FWD",c("1",1500,"N")),transform.toModel("FWD",c("2",500,"N"))).size());
    }
    @Test public void extendsOnlyMatchedOneMetreJunctionAtRouteTransition() {
        var a=view("AD",p(0,"101",2000,"N"),p(1999,"101",1,"N"),p(2000,"102",0,"N"),p(3000,"102",1000,"N"));
        var b=view("DA",p(0,"102",1000,"S"),p(1000,"102",0,"S"),p(1001,"101",1,"S"),p(3000,"101",2000,"S"));
        var links=List.of(new TrackJunction(c("101",0,"N"),c("102",0,"N")),new TrackJunction(c("101",0,"S"),c("102",0,"S")));
        var sections=TrackSectionsFromRouteViews.build(Map.of("AD",a,"DA",b),links);
        var mapper=new TrackSectionMapper();
        assertEquals(0,mapper.toModel(sections.get("101"),c("101",0,"N")).getPositionM());
        assertEquals(1,mapper.toModel(sections.get("101"),c("101",1,"N")).getPositionM());
        assertThrows(IllegalArgumentException.class,()->mapper.toModel(TrackSectionsFromRouteViews.build(Map.of("AD",a,"DA",b)).get("101"),c("101",0,"N")));
        var longer=view("AD",p(0,"101",2000,"N"),p(1999,"101",1,"N"),p(2001,"102",0,"N"),p(3001,"102",1000,"N"));
        assertThrows(IllegalArgumentException.class,()->mapper.toModel(TrackSectionsFromRouteViews.build(Map.of("AD",longer),links).get("101"),c("101",0,"N")));
    }
    @Test public void explicitBoardsRemainSupported() throws Exception {
        Path dir=temp.newFolder().toPath(),file=dir.resolve("track.xlsx");
        try(Workbook book=new XSSFWorkbook()) {writeSheet(book,"fwd",false);try(var out=Files.newOutputStream(file)){book.write(out);}}
        var config=ConfigFactory.parseString("""
            track {
              route_data_excel = "track.xlsx"
              route_views = [{route_id=FWD,sheet=fwd}]
              kilometer_boards = [{section="1",km="0+000",length=1100},{section="1",km="1+000",length=0}]
            }
            """);
        var model=new TrackConfigLoader().load(config,dir.resolve("study.conf"));
        assertEquals(1100,new DefaultTrackTransformService(model).toModel("FWD",c("1",1000,"N")).getPositionM());
    }
}
