package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.supply.domain.RunCsvInput;
import org.supply.utils.StationFileChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class FolderTemplateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void ordersWeChainIndependentlyOfFilenameOrder() {
        List<Path> files = paths("WE_C-CE.xlsx", "WE_CE-E.xlsx", "WE_W-CW.xlsx", "WE_CW-C.xlsx");
        assertEquals(paths("WE_W-CW.xlsx", "WE_CW-C.xlsx", "WE_C-CE.xlsx", "WE_CE-E.xlsx"),
                StationFileChain.order(files, "WE_", ".xlsx"));
    }

    @Test public void rejectsAmbiguityGapsCyclesAndMalformedNames() {
        for (List<Path> files : List.of(
                paths("A-B.xlsx", "A-C.xlsx"), paths("A-C.xlsx", "B-C.xlsx"),
                paths("A-B.xlsx", "C-D.xlsx"), paths("A-B.xlsx", "B-A.xlsx"),
                paths("A-B.xlsx", "C-D.xlsx", "D-C.xlsx"), paths("A-A.xlsx"),
                paths("A-B-C.xlsx"), paths("broken.xlsx"), List.<Path>of())) {
            assertThrows(IllegalArgumentException.class, () -> StationFileChain.order(files, "", ".xlsx"));
        }
    }

    @Test public void supportsOneLegAndUnicodeStations() {
        assertEquals(paths("Å-Ö.xlsx"), StationFileChain.order(paths("Å-Ö.xlsx"), "", ".xlsx"));
    }

    @Test public void factoryExpandsOrderedLegsAndInheritsAuxiliaries() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path folder = Files.createDirectory(root.resolve("analysis"));
        Path second = Files.createFile(folder.resolve("WE_B-C.xlsx"));
        Path first = Files.createFile(folder.resolve("WE_A-B.xlsx"));
        Files.createFile(folder.resolve("EW_C-B.xlsx"));
        Files.createFile(folder.resolve("WE_ignored.xlsx.bak"));
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.createFile(nested.resolve("WE_invalid.xlsx"));
        Config config = ConfigFactory.parseString("""
                simulationControl { simulationStart = "07:00:00", simulationEnd = "08:00:00" }
                traffic {
                  templates.WE {
                    folder = "analysis"
                    prefix = "WE_"
                    sheet = "+0sek"
                    motoring_and_auxiliaries_in_same_model = false
                    auxiliary_power_W = 100000
                  }
                  timetable.trains = [{id = "T", template_id = "WE", routeId = "WE",
                    trackId = "N", departure = "07:00:00", count = 2, headway = "00:05:00"}]
                }
                """);
        RunCsvInput input = new RunCsvInputFactory().build(config, root.resolve("run.conf"));
        assertEquals(List.of(first, second, first, second), input.runExcels());
        assertEquals(List.of("+0sek", "+0sek", "+0sek", "+0sek"), input.runExcelSheets());
        assertEquals(List.of(false, false, false, false), input.motoringAndAuxiliariesInSameModel());
        assertEquals(List.of(100000.0, 100000.0, 100000.0, 100000.0), input.auxiliaryPowersW());
        assertEquals(List.of("T-001", "T-001", "T-002", "T-002"), input.trainIds());
        assertEquals(List.of(25200, 25200, 25500, 25500), input.departureTimes());
    }

    @Test public void rejectsMixedConfigurationAndMissingMatches() throws Exception {
        Path folder = temporary.newFolder().toPath();
        Config template = ConfigFactory.parseString("prefix = WE_\nsheet = run");
        assertThrows(IllegalArgumentException.class, () -> FolderTemplateLegs.read(template, folder));
        Config mixed = template.withFallback(ConfigFactory.parseString("legs = []"));
        assertThrows(IllegalArgumentException.class, () -> FolderTemplateLegs.read(mixed, folder));
        Config blank = ConfigFactory.parseString("prefix = \"\"\nsheet = run");
        assertThrows(IllegalArgumentException.class, () -> FolderTemplateLegs.read(blank, folder));
    }

    private static List<Path> paths(String... names) {
        return java.util.Arrays.stream(names).map(Path::of).toList();
    }
}
