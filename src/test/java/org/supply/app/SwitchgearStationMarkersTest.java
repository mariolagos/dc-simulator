package org.supply.app;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.supply.loader.GridModelLoader;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.track.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class SwitchgearStationMarkersTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static void sheet(Workbook book, String name, boolean reverse) {
        Sheet sheet=book.createSheet(name); Row h=sheet.createRow(0);
        String[] headers={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
        for(int i=0;i<headers.length;i++)h.createCell(i).setCellValue(headers[i]);
        int[] section=reverse?new int[]{102,102,102,101,101,101}:new int[]{101,101,101,102,102,102};
        int[] km={2,1,0,0,1,2}; int[] pos={0,1000,2000,2001,3001,4001};
        for(int i=0;i<pos.length;i++) { Row row=sheet.createRow(i+1);row.createCell(0).setCellValue(pos[i]);
            row.createCell(1).setCellValue(section[i]);row.createCell(2).setCellValue(reverse?"S":"N");
            row.createCell(3).setCellValue(km[i]);row.createCell(4).setCellValue(0); }
    }
    @Test public void generatedGridShowsStationsOnBothRoutesAndTimetableAxes() throws Exception {
        Path root=temp.newFolder().toPath(), input=root.resolve("grid.xlsx"), output=root.resolve("case_grid_generated.conf");
        try(var in=getClass().getResourceAsStream("/excel-grid/two_sections_grid_input.xlsx")){Files.copy(in,input);}
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});
        Path workbook=root.resolve("track.xlsx");try(Workbook book=new XSSFWorkbook()) {sheet(book,"AD",false);sheet(book,"DA",true);try(var o=Files.newOutputStream(workbook)){book.write(o);}}
        var grid=ConfigFactory.parseFile(output.toFile());var routes=ConfigFactory.parseFile(root.resolve("case_routes_generated.conf").toFile());
        var settings=ConfigFactory.parseString("dcsim.track.route_data_excel=\"track.xlsx\"\nreport.graphical-timetable {enabled=true, base-route { id=AD, sections=[\"101\",\"102\"] }}");
        var scenario=settings.withFallback(grid).withFallback(routes).resolve();var dcsim=scenario.getConfig("dcsim");
        var track=new TrackConfigLoader().load(dcsim,root.resolve("run.conf"));var gridModel=new GridModelLoader().load(dcsim);
        var network=new CalculationNetworkBuilder(new DefaultTrackTransformService(track)).buildBase(gridModel);
        var points=DcCheck.supplyPoints(gridModel,network);
        for(String route:List.of("AD","DA")) {String svg=TrackRouteDiagram.bandSvg(track,track.getRouteViewsById().get(route),points);
            assertTrue(route+" missing SS1",svg.contains("SS1"));assertTrue(route+" missing SS2",svg.contains("SS2"));}
        Path exports=Files.createDirectory(root.resolve("exports")),checks=Files.createDirectory(root.resolve("checks"));
        Files.writeString(exports.resolve("track_segments.csv"),"section,from_rwy,to_rwy,start_model_m,length_m\n101,101 0+000,101 1+000,0,1000\n101,101 1+000,101 2+000,1000,1000\n102,102 0+000,102 1+000,0,1000\n102,102 1+000,102 2+000,1000,1000\n");
        Files.writeString(exports.resolve("track_stations.csv"),"name,position_rwy,model_position_m\n");
        var context=new DcStudyContext(root.resolve("run.conf"),root,scenario,dcsim,"two_sections","two_sections","",exports,root.resolve("results"));
        DcReporter.writePlannedGraphicalTimetable(context,exports,List.of(new DcReporter.PlannedTimetablePoint(0,"T1","101",1000.0)),checks);
        var lines=Files.readAllLines(checks.resolve("two_sections_graphical_timetable_markers.csv"));
        for(String station:List.of("SS1","SS2"))for(String axis:List.of("TOP","BOTTOM"))
            assertTrue(station+" missing "+axis+" in "+lines,lines.stream().anyMatch(x->x.startsWith(station+",")&&x.endsWith(",SUPPLY_POINT,"+axis)));
    }
}
