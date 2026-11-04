package org.supply.track;

import org.junit.Test;
import org.supply.io.export.NetworkInputCsvWriter;
import com.typesafe.config.ConfigFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class PerTrackStationExportTest {
    private static RwyCoordinate c(int p,String track){return new RwyCoordinate("X",RwyCoordinate.formatKmShort(p),p,track);}
    private static RouteView view(String id,String track,int length){return new RouteView(id,id,List.of(new PathSample(0,c(0,track)),new PathSample(length,c(1000,track))));}
    @Test public void unlabelledConfiguredStationExportsBothIndependentPositions() throws Exception {
        var views=Map.of("A",view("A","N",1010),"B",view("B","S",1000));
        var model=new LoadedTrackModel(TrackSectionsFromRouteViews.build(views),List.of(),List.of(new Station("Stop",c(1000,null))),views);
        Path file=Files.createTempFile("station-export-",".csv");
        try {
            new NetworkInputCsvWriter().writeTrackStations(model,file);
            var lines=Files.readAllLines(file);
            assertEquals(3,lines.size());
            assertTrue(lines.toString(),lines.stream().anyMatch(s->s.contains(" N,")&&s.endsWith(",1010")));
            assertTrue(lines.toString(),lines.stream().anyMatch(s->s.contains(" S,")&&s.endsWith(",1000")));
        }finally{Files.deleteIfExists(file);}
    }
    @Test public void explicitStationDoesNotCreateAnExtraTrack() throws Exception {
        var views=Map.of("A",view("A","N",1010),"B",view("B","S",1000));
        var model=new LoadedTrackModel(TrackSectionsFromRouteViews.build(views),List.of(),List.of(new Station("Stop",c(1000,"S"))),views);
        Path file=Files.createTempFile("station-export-",".csv");
        try{new NetworkInputCsvWriter().writeTrackStations(model,file);assertEquals(2,Files.readAllLines(file).size());}
        finally{Files.deleteIfExists(file);}
    }
    @Test public void excelStationsInheritRouteTrackAndExporterHandlesDifferentEnds() throws Exception {
        Path dir=Files.createTempDirectory("station-track-"),file=dir.resolve("track.xlsx"),csv=dir.resolve("stations.csv");
        try {
            try(var book=new XSSFWorkbook()) {
                for(String name:List.of("A","B")) {
                    var sheet=book.createSheet(name);var header=sheet.createRow(0);
                    String[] labels={"position [m]","trackSection","bisKm","bisMeter","dataType","fromStationAbbr","toStationAbbr"};
                    for(int i=0;i<labels.length;i++)header.createCell(i).setCellValue(labels[i]);
                    for(int i=0;i<2;i++) {var r=sheet.createRow(i+1);r.createCell(0).setCellValue(i*(name.equals("A")?1010:1000));
                        r.createCell(1).setCellValue("X");r.createCell(2).setCellValue(i);r.createCell(3).setCellValue(0);
                        r.createCell(4).setCellValue("P");r.createCell(5).setCellValue("Stop"+i);r.createCell(6).setCellValue("Stop"+i);}
                }
                try(var out=Files.newOutputStream(file)){book.write(out);}
            }
            var cfg=ConfigFactory.parseString("track {route_data_excel=\"track.xlsx\",route_views=[{route_id=A,sheet=A,track_id=N},{route_id=B,sheet=B,track_id=S}]}");
            var model=new TrackConfigLoader().load(cfg,dir.resolve("run.conf"));
            assertEquals(4,model.getStations().size());
            assertEquals("N",model.getStationsForRoute("A").get(0).getPosition().getTrackId());
            assertEquals("S",model.getStationsForRoute("B").get(0).getPosition().getTrackId());
            new NetworkInputCsvWriter().writeTrackStations(model,csv);
            assertEquals(5,Files.readAllLines(csv).size());
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(csv);Files.deleteIfExists(dir);}
    }
}
