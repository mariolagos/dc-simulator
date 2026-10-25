package org.supply.loader;

import com.typesafe.config.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.supply.domain.*;
import org.supply.solver.build.*;
import org.supply.solver.electrical.*;
import org.supply.track.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class ReusableSwitchgearExcelTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path fixture(Consumer<XSSFWorkbook> edit) throws Exception {
        Path p=temp.getRoot().toPath().resolve("two_sections.xlsx");
        try(var in=getClass().getResourceAsStream("/excel-grid/two_sections_grid_input.xlsx")) {assertNotNull(in);Files.copy(in,p);}
        try(var in=Files.newInputStream(p);var book=new XSSFWorkbook(in)) {edit.accept(book);try(var out=Files.newOutputStream(p)){book.write(out);}}
        return p;
    }
    @Test public void derivesSixteenBindingsFromOneTypeAndKeepsJunctionInBothRoutes() throws Exception {
        var r=ExcelGridReader.read(fixture(w->{}));
        var m=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",r.grid().root()));
        assertEquals(16,r.bindings().size());assertEquals(2,m.getInstallations().size());assertEquals(20,m.getLines().size());
        assertTrue(r.bindings().stream().anyMatch(b->b.get("substation").equals("SS1")&&b.get("terminal").equals("A")&&b.get("line_id").equals("B__B1__1.N.F")));
        assertTrue(r.bindings().stream().anyMatch(b->b.get("substation").equals("SS2")&&b.get("terminal").equals("D")&&b.get("line_id").equals("B__B2__2.S.F")));
        for(String route:List.of("AD","DA")) {
            var c=ConfigValueFactory.fromMap((Map<String,Object>)r.routes().get(route)).toConfig();
            assertEquals(5,c.getStringList("feeding_lines").size());assertEquals(5,c.getStringList("return_lines").size());
            assertTrue(c.getStringList("feeding_lines").get(2).startsWith("J__J1__"));
        }
        assertEquals(3000,r.grid().getDouble("u_nominal_V"),0);
        assertEquals(5000,r.grid().getDouble("i_train_max_A"),0);
    }
    @Test public void generatedNetworkPassesElectricalRouteCheckAndNoLoadSolve() throws Exception {
        var r=ExcelGridReader.read(fixture(w->{}));
        var m=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",r.grid().root()));
        var transform=new TrackTransformService(){
            public ModelCoordinate toModel(String route,RwyCoordinate c){int p=c.getSectionId().equals("101")?2000-c.getPositionM():2001+c.getPositionM();return new ModelCoordinate(c.getSectionId(),c.getTrackId(),p);}
            public RwyCoordinate toRailway(String route,ModelCoordinate c){throw new UnsupportedOperationException();}
            public ModelCoordinate pathToModel(String route,double p){throw new UnsupportedOperationException();}
            public RwyCoordinate pathToRailway(String route,double p){throw new UnsupportedOperationException();}
            public double distanceOnRoute(String route,RwyCoordinate a,RwyCoordinate b){throw new UnsupportedOperationException();}
            public List<ModelCoordinate> linePath(ModelCoordinate a,ModelCoordinate b){return List.of(a,b);}
        };
        var n=new CalculationNetworkBuilder(transform).buildBase(m);
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(r.routes())));
        var check=ElectricalRouteCheck.inspect(n,routes,null);assertEquals(check.errors().toString(),List.of(),check.errors());
        var v=new LinearSystemSolver().solveVoltages(new AdmittanceSystemBuilder().build(n,"SS1.NEG"));
        for(String id:List.of("SS1","SS2"))assertEquals(3000,v.get(id+".POS").asDouble()-v.get(id+".NEG").asDouble(),.01);
    }
    @Test public void typeCableAndSwitchApplyToEveryStationWithoutDeletingLines() throws Exception {
        var r=ExcelGridReader.read(fixture(w->{var row=w.getSheet("SwitchgearTypes").getRow(10);row.getCell(7).setCellValue(20);row.getCell(8).setCellValue(.00008);row.getCell(9).setCellValue(4);}));
        var m=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",r.grid().root()));
        assertEquals(2,m.getSwitchGears().size());for(var g:m.getSwitchGears())assertEquals(.0004,g.cables().get(0).resistanceOhm(),1e-12);
    }
    @Test public void detectsAmbiguousSideRulesWithExcelLocation() throws Exception {
        var e=assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(fixture(w->w.getSheet("SwitchgearTypes").getRow(11).getCell(2).setCellValue("BEFORE"))));
        assertTrue(e.getMessage(),e.getMessage().contains("duplicate terminal or connection slot"));assertTrue(e.getMessage().contains("row 12"));
    }
    @Test public void opensTypeConnectionForBothStationsAndPreservesNoGapContinuity() throws Exception {
        var r=ExcelGridReader.read(fixture(w->{w.getSheet("SwitchgearTypes").getRow(5).getCell(1).setCellValue(false);w.getSheet("SwitchgearTypes").getRow(10).getCell(6).setCellValue(false);}));
        assertEquals(20,r.grid().getConfigList("lines").size());
        var ids=r.grid().getConfigList("ideal_connections").stream().map(c->c.getString("id")).toList();
        for(String id:List.of("SS1","SS2")){assertFalse(ids.contains(id+"__terminal_A"));assertTrue(ids.contains(id+"__no_gap_A"));}
    }
}
