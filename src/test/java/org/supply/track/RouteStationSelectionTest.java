package org.supply.track;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class RouteStationSelectionTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    private static void sheet(Workbook book,String name,boolean reverse) {
        Sheet s=book.createSheet(name);Row h=s.createRow(0);
        String[] names={"position [m]","trackSection","bisKm","bisMeter","dataType","fromStationAbbr","toStationAbbr"};
        for(int n=0;n<names.length;n++)h.createCell(n).setCellValue(names[n]);
        int[] positions=reverse?new int[]{1000,600,0}:new int[]{0,400,1000};
        for(int n=0;n<3;n++) {
            Row r=s.createRow(n+1);r.createCell(0).setCellValue(n==0?0:n==1?400:1000);
            r.createCell(1).setCellValue("1");r.createCell(2).setCellValue(positions[n]/1000);
            r.createCell(3).setCellValue(positions[n]%1000);r.createCell(4).setCellValue(n==1?"P":"M");
            if(n==1){r.createCell(5).setCellValue("ST");r.createCell(6).setCellValue("ST");}
        }
    }
    private static int count(String s,String q) {return (s.length()-s.replace(q,"").length())/q.length();}
    @Test public void routeSheetSelectsItsStationEvenWithoutTrackNumbers() throws Exception {
        Path dir=tmp.newFolder().toPath();Path file=dir.resolve("track.xlsx");
        try(Workbook book=new XSSFWorkbook()) {
            sheet(book,"east",false);sheet(book,"west",true);
            try(var out=Files.newOutputStream(file)){book.write(out);}
        }
        var config=ConfigFactory.parseString("""
   track {
    route_data_excel = "track.xlsx"
    route_views = [{route_id=WE,sheet=east},{route_id=EW,sheet=west}]
    kilometer_boards = [{section="1",km="0+000",length=1000},{section="1",km="1+000",length=0}]
    stations = [{name=ST,position_rwy="1 0+800"},{name=Extra,position_rwy="1 0+200"}]
   }
   """);
        var model=new TrackConfigLoader().load(config,dir.resolve("study.conf"));
        assertEquals(3,model.getStations().size()); // unchanged global export view
        assertEquals(2,model.getStationsForRoute("WE").size());
        for(String id:List.of("WE","EW")) {
            var selected=model.getStationsForRoute(id).stream().filter(s->s.getName().equals("ST")).toList();
            assertEquals(1,selected.size());assertEquals(id.equals("WE")?400:600,selected.get(0).getPosition().getPositionM());
            String svg=TrackRouteDiagram.svg(model,model.getRouteViewsById().get(id));
            assertEquals(1,count(svg,">ST<"));assertEquals(1,count(svg,">Extra<"));
        }
    }
    @Test public void oldConstructorsAndUnknownRouteKeepGlobalStations() {
        var station=new Station("ST",new RwyCoordinate("1","0+500",500,null));
        var model=new LoadedTrackModel(Map.of(),List.of(),List.of(station));
        assertEquals(List.of(station),model.getStationsForRoute("unknown"));
        assertThrows(UnsupportedOperationException.class,()->model.getStationsForRoute("unknown").clear());
    }
}
