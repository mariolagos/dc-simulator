package org.supply.loader;

import com.typesafe.config.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.supply.app.GridPreprocessor;
import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.build.*;
import org.supply.solver.electrical.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class ExcelGridReaderTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path fixture() throws Exception {
        Path p=Files.createTempFile(temp.getRoot().toPath(),"input-",".xlsx");
        try(var in=getClass().getResourceAsStream("/excel-grid/WE_grid_input.xlsx")) {if(in==null)throw new AssertionError("Missing fixture");Files.copy(in,p,StandardCopyOption.REPLACE_EXISTING);}
        return p;
    }
    private Path change(Consumer<XSSFWorkbook> edit) throws Exception {
        Path p=fixture();try(var in=Files.newInputStream(p);var book=new XSSFWorkbook(in)){edit.accept(book);try(var out=Files.newOutputStream(p)){book.write(out);}}return p;
    }
    private static GridModel model(ExcelGridReader.Result r){return new GridModelLoader().load(ConfigFactory.empty().withValue("grid",r.grid().root()));}
    private static CalculationNetwork network(GridModel model) {
        return new CalculationNetworkBuilder(new TrackTransformService(){
            public ModelCoordinate toModel(String route,RwyCoordinate c){return new ModelCoordinate(c.getSectionId(),c.getTrackId(),c.getPositionM());}
            public RwyCoordinate toRailway(String r,ModelCoordinate c){throw new UnsupportedOperationException();}
            public ModelCoordinate pathToModel(String r,double p){throw new UnsupportedOperationException();}
            public RwyCoordinate pathToRailway(String r,double p){throw new UnsupportedOperationException();}
            public double distanceOnRoute(String r,RwyCoordinate a,RwyCoordinate b){throw new UnsupportedOperationException();}
            public List<ModelCoordinate> linePath(ModelCoordinate a,ModelCoordinate b){assertEquals(a.getSectionId(),b.getSectionId());assertEquals(a.getTrackId(),b.getTrackId());return List.of(a,b);}
        }).buildBase(model);
    }
    @Test public void createsWeNetworkAndAllRoutesWithExactIdealReduction() throws Exception {
        var input=ExcelGridReader.read(fixture());var m=model(input);var n=network(m);
        assertEquals(128,m.getNodes().size());assertEquals(36,m.getLines().size());assertEquals(102,m.getIdealConnections().size());assertEquals(6,m.getInstallations().size());assertEquals(56,input.bindings().size());
        assertEquals(0,m.getSwitchGears().stream().mapToInt(g->g.cables().size()).sum());
        var system=new AdmittanceSystemBuilder().build(n,"SS0.NEG");assertEquals(28,system.size());
        var voltage=new LinearSystemSolver().solveVoltages(system);
        for(var c:m.getIdealConnections())assertEquals(voltage.get(c.fromNodeId()).asDouble(),voltage.get(c.toNodeId()).asDouble(),0);
        for(var station:m.getInstallations())assertEquals(750,voltage.get(station.getInstallationId()+".POS").asDouble()-voltage.get(station.getInstallationId()+".NEG").asDouble(),1e-7);
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(input.routes())));
        var check=ElectricalRouteCheck.inspect(n,routes,null);assertEquals(check.errors().toString(),List.of(),check.errors());assertEquals(4,routes.size());
        assertTrue(input.bindings().stream().anyMatch(b->b.get("substation").equals("SS1")&&b.get("line_id").toString().contains("201")));
        assertTrue(input.bindings().stream().anyMatch(b->b.get("substation").equals("SS3")&&b.get("line_id").toString().contains("202")));
        // Physical section/track positions survive electrical union.
        assertTrue(m.getIdealConnections().stream().anyMatch(c->!m.getNode(c.fromNodeId()).getPositionRwy().equals(m.getNode(c.toNodeId()).getPositionRwy())));
    }
    @Test public void trainInsertionAndConstantPowerSolveKeepIdealConnections() throws Exception {
        var input=ExcelGridReader.read(fixture());var m=model(input);var n=network(m);
        var routes=new RouteFactory().build(ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(input.routes())));
        var params=new SystemParameters(750,500,600,900,1500,10000000,3600000,"SS0.NEG");
        var trains=List.of(new CalculationTrainPosition("T1","101","N","WE",0,16000,Real.fromDouble(50000)),
                new CalculationTrainPosition("T2","200","N","NwSe",0,3000,Real.fromDouble(50000)),
                new CalculationTrainPosition("T3","102","S","EW",0,44000,Real.fromDouble(50000)),
                new CalculationTrainPosition("T4","202","S","SeNw",0,26000,Real.fromDouble(50000)));
        var loaded=new TrainNodeInserter(params,routes).insertTrainNodes(n,trains);assertEquals(4,loaded.trainLoads().size());
        var powers=Map.of("T1",50000.0,"T2",50000.0,"T3",50000.0,"T4",50000.0);
        var result=new SingleTimestepSolver(params,new LinearSystemSolver()).solve(loaded,"SS0.NEG",powers,200,1e-6);
        assertTrue(result.converged());
        for(var train:loaded.trainLoads()) {
            double v=result.voltages().get(train.feedingNodeId()).asDouble()-result.voltages().get(train.returnNodeId()).asDouble();
            assertTrue("Unexpected train voltage "+v,v>650&&v<750);
        }
        Map<String,Double> resistance=new HashMap<>();m.getLines().forEach(l->resistance.put(l.getLineId(),l.getResistanceOhmPerM().asDouble()));
        for(var route:routes) {
            String svg=RouteSchematic.svg(n,route,resistance);
            if(route.id().equals("NwSe")||route.id().equals("SeNw")) {assertTrue(svg.contains("SS1"));assertTrue(svg.contains("SS3"));}
        }
    }
    @Test public void finiteCableUsesLengthResistanceAndMultiplicity() throws Exception {
        Path p=change(w->{var r=w.getSheet("Substations").getRow(21);r.createCell(4).setCellValue(20);r.createCell(5).setCellValue(.00008);r.createCell(6).setCellValue(4);});
        var input=ExcelGridReader.read(p);var m=model(input);var cable=m.getSwitchGears().stream().filter(g->g.installationId().equals("SS0")).findFirst().orElseThrow().cables().get(0);
        assertEquals(.0004,cable.resistanceOhm(),1e-15);
        var n=network(m);var system=new AdmittanceSystemBuilder().build(n,"SS0.NEG");assertEquals(29,system.size());
        var pos=m.getLines().stream().filter(l->l.getLineId().equals("B__101__1.S.F")).findFirst().orElseThrow().getFromNode().getNodeId();
        var neg=m.getLines().stream().filter(l->l.getLineId().equals("B__101__1.S.R")).findFirst().orElseThrow().getFromNode().getNodeId();
        var solved=new LinearSystemSolver().solveVoltages(new AdmittanceSystemBuilder().build(n,"SS0.NEG",List.of(new CurrentInjection(pos,Real.fromDouble(-300)),new CurrentInjection(neg,Real.fromDouble(300)))));
        assertTrue(solved.get(pos).asDouble()-solved.get(neg).asDouble()<750);
    }
    @Test public void switchOffOpensOnlyInternalBranchAndKeepsRailwayLine() throws Exception {
        Path p=change(w->w.getSheet("Substations").getRow(21).getCell(3).setCellValue(false));
        var input=ExcelGridReader.read(p);var m=model(input);
        assertEquals(36,m.getLines().size());assertFalse(m.getIdealConnections().stream().anyMatch(c->c.id().equals("SS0__terminal_A")));
        assertTrue(input.bindings().stream().anyMatch(b->b.get("substation").equals("SS0")&&b.get("terminal").equals("A")&&b.get("enabled").equals(false)));
    }
    @Test public void noGapContinuityIsIndependentOfFeederSwitch() throws Exception {
        Path p=change(w->{var sh=w.getSheet("Substations");for(int r=21;r<=76;r++){var row=sh.getRow(r);if(row.getCell(0).getStringCellValue().equals("SS5")&&Set.of("A","B").contains(row.getCell(1).getStringCellValue()))row.getCell(3).setCellValue(false);}});
        var m=model(ExcelGridReader.read(p));assertTrue(m.getIdealConnections().stream().anyMatch(c->c.id().equals("SS5__no_gap_A")));
        assertFalse(m.getIdealConnections().stream().anyMatch(c->Set.of("SS5__terminal_A","SS5__terminal_B").contains(c.id())));
    }
    @Test public void rejectsPartialCableWithExcelLocation() throws Exception {
        Path p=change(w->w.getSheet("Substations").getRow(21).createCell(4).setCellValue(20));
        var e=assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(p));assertTrue(e.getMessage(),e.getMessage().contains("Substations row 22"));assertTrue(e.getMessage().contains("incomplete internal cable"));
    }
    @Test public void rejectsDuplicateLineEndBindingAndPolarityMismatch() throws Exception {
        Path duplicate=change(w->w.getSheet("Substations").getRow(22).getCell(2).setCellValue("101 / P1 → SS0 / S.F"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(duplicate)).getMessage().contains("already bound"));
        Path polarity=change(w->w.getSheet("Substations").getRow(21).getCell(2).setCellValue("101 / P1 → SS0 / S.R"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(polarity)).getMessage().contains("polarity"));
    }
    @Test public void preprocessingWritesReloadableGridRoutesAndProtectsOutputs() throws Exception {
        Path input=fixture(),output=temp.getRoot().toPath().resolve("WE_grid_generated.conf");
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});byte[] before=Files.readAllBytes(output);
        var loaded=new GridModelLoader().load(ConfigFactory.parseFile(output.toFile()).getConfig("dcsim"));assertEquals(102,loaded.getIdealConnections().size());
        assertTrue(Files.exists(output.resolveSibling("WE_routes_generated.conf")));assertTrue(Files.readString(output.resolveSibling("WE_grid_generated_checks/network_summary.txt")).contains("Electrical nodes after ideal reduction: 28"));
        assertThrows(FileAlreadyExistsException.class,()->GridPreprocessor.main(new String[]{input.toString(),output.toString()}));assertArrayEquals(before,Files.readAllBytes(output));
    }
}
