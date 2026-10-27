package org.supply.track;

import com.typesafe.config.ConfigFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.supply.app.GridPreprocessor;
import java.nio.file.*;
import static org.junit.Assert.*;

public class GeneratedTrackIntegrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private void sheet(Workbook book, String name, boolean reverse) {
        Sheet sheet=book.createSheet(name); Row h=sheet.createRow(0);
        String[] headers={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
        for(int i=0;i<headers.length;i++)h.createCell(i).setCellValue(headers[i]);
        int[] section=reverse?new int[]{102,102,102,101,101,101}:new int[]{101,101,101,102,102,102};
        int[] km=reverse?new int[]{2,1,0,0,1,2}:new int[]{2,1,0,0,1,2};
        int[] pos={0,1000,2000,2001,3001,4001};
        for(int i=0;i<pos.length;i++) { Row row=sheet.createRow(i+1);row.createCell(0).setCellValue(pos[i]);
            row.createCell(1).setCellValue(section[i]);row.createCell(2).setCellValue(reverse?"S":"N");
            row.createCell(3).setCellValue(km[i]);row.createCell(4).setCellValue(0); }
    }
    @Test public void generatedRoutesSupplyViewsAndOnlyUsedJunctions() throws Exception {
        Path dir=temp.newFolder().toPath(), input=dir.resolve("input.xlsx"), grid=dir.resolve("case_grid_generated.conf");
        try(var in=getClass().getResourceAsStream("/excel-grid/two_sections_grid_input.xlsx")) {Files.copy(in,input);}
        GridPreprocessor.main(new String[]{input.toString(),grid.toString()});
        var generated=ConfigFactory.parseFile(dir.resolve("case_routes_generated.conf").toFile()).resolve();
        assertEquals(2,generated.getConfigList("dcsim.track.route_views").size());
        assertEquals(2,generated.getConfigList("dcsim.track.junctions").size());
        assertEquals("AD",generated.getConfigList("dcsim.track.route_views").stream().filter(v->v.getString("route_id").equals("AD")).findFirst().orElseThrow().getString("sheet"));
        Path workbook=dir.resolve("track.xlsx");try(Workbook book=new XSSFWorkbook()) {
            sheet(book,"AD",false);sheet(book,"DA",true);try(var out=Files.newOutputStream(workbook)){book.write(out);}}
        var run=ConfigFactory.parseString("dcsim.track.route_data_excel=\"track.xlsx\"").withFallback(generated).resolve();
        var model=new TrackConfigLoader().load(run.getConfig("dcsim"),dir.resolve("run.conf"));
        assertEquals(2,model.getJunctions().size());
        var t=new DefaultTrackTransformService(model);
        var a=new RwyCoordinate("101","1+000",1000,"N");var b=new RwyCoordinate("102","1+000",1000,"N");
        assertEquals(4,t.linePath(t.toModel("AD",a),t.toModel("AD",b)).size());
    }
}
