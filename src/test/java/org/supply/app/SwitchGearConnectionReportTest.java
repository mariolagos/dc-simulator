package org.supply.app;

import com.typesafe.config.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.supply.loader.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class SwitchGearConnectionReportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Config input() { return ConfigFactory.parseResources("switchgear/double-track-gap.conf").resolve(); }
    private Map<String,String> reports(Config description) {
        var grid=BranchGridGenerator.generate(description.getConfig("dcsim.grid"));
        var model=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",grid.root()));
        return SwitchGearConnectionReport.render(model,grid);
    }
    @Test public void exportsBothCableTiersAndComputedResistance() {
        var reports=reports(input());
        assertEquals(Set.of("SS0_connections.csv","SS0_connections.txt"),reports.keySet());
        var csv=reports.get("SS0_connections.csv");
        assertEquals(12,csv.lines().count());
        assertTrue(csv.contains("SS0,FC1,POS,PBB1,SS0.SG.POS,SS0.SG.PBB1,FEEDING"));
        assertTrue(csv.contains("SS0,FP3,PBB3,3,SS0.SG.PBB3,SS0.B,FEEDING"));
        assertTrue(csv.contains(",25.0,cable240,0.08,4,5.0E-4"));
        assertTrue(reports.get("SS0_connections.txt").contains("B__101__1.N.F"));
    }
    @Test public void reversedOrientationIsVisibleWithPhysicalTrackAndGap() {
        var conf=input().withValue("dcsim.grid.branches.101",ConfigValueFactory.fromIterable(List.of("P1","-SS0","P2")));
        var txt=reports(conf).get("SS0_connections.txt");
        assertTrue(txt.contains("Orientation: REVERSED"));
        assertTrue(txt.contains("NORTH_POWER_LEFT | 1 | SS0.A | 101 14+000 S | B__101__1.S.F"));
        assertTrue(txt.contains("Track S: 1 [SS0.A] | GAP | 3 [SS0.B]; distinct_nodes=true; same_railway_position=true"));
        assertTrue(txt.contains("FEEDING -> SS0.SG.POS"));
        assertTrue(txt.contains("RETURN -> SS0.SG.NEG"));
    }
    @Test public void reportsUseEffectiveTypeDefaultsAndLocalCableOverrides() {
        var conf=input().withValue("dcsim.grid.electricalObjects.SS0.emf_V",ConfigValueFactory.fromAnyRef(800));
        assertTrue(reports(conf).get("SS0_connections.txt").contains("EMF_V: 800.0"));
    }
    @Test public void legacyStationsDoNotProduceMisleadingCableReports() {
        var conf=input().withoutPath("dcsim.grid.substationTypes.standard.switchgear")
                .withValue("dcsim.grid.substationTypes.standard.terminal_model",ConfigValueFactory.fromAnyRef("SIX_TERMINAL_RECTIFIER"));
        assertTrue(reports(conf).isEmpty());
    }
    @Test public void commandWritesReportsNextToGeneratedGridAndHonorsOverwrite() throws Exception {
        Path folder=temporary.getRoot().toPath();
        Path input=folder.resolve("branches.conf"),output=folder.resolve("WE_grid_generated.conf");
        Files.writeString(input,input().root().render());
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});
        Path csv=folder.resolve("WE_grid_generated_checks/SS0_connections.csv");
        Path txt=folder.resolve("WE_grid_generated_checks/SS0_connections.txt");
        assertTrue(Files.exists(csv)); assertTrue(Files.exists(txt));
        assertThrows(FileAlreadyExistsException.class,()->GridPreprocessor.main(new String[]{input.toString(),output.toString()}));
        Files.writeString(csv,"stale");
        GridPreprocessor.main(new String[]{input.toString(),output.toString(),"--overwrite"});
        assertTrue(Files.readString(csv).startsWith("installation_id,cable_id,"));
    }
    @Test public void existingReportIsDetectedBeforeTheGridIsWritten() throws Exception {
        Path folder=temporary.getRoot().toPath();
        Path input=folder.resolve("branches.conf"),output=folder.resolve("grid.conf");
        Files.writeString(input,input().root().render());
        Path reports=Files.createDirectories(folder.resolve("grid_checks"));
        Files.writeString(reports.resolve("SS0_connections.csv"),"keep");
        assertThrows(FileAlreadyExistsException.class,()->GridPreprocessor.main(new String[]{input.toString(),output.toString()}));
        assertFalse(Files.exists(output));
        assertEquals("keep",Files.readString(reports.resolve("SS0_connections.csv")));
    }
}
