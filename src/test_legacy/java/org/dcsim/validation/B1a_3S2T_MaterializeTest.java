package org.dcsim.validation;

import org.dcsim.DcSimScenarioLoader;
import org.dcsim.ScenarioLoader;
import org.dcsim.SimulationInputModel;
import org.dcsim.math.Real;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class B1a_3S2T_MaterializeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void B1a_materialize_and_validate_inputs_csvScenario() throws Exception {
        Assume.assumeTrue(
                "AppConf -> CSV generator not implemented yet",
                TestCapabilities.appConfGeneratorEnabled()
        );

        // Arrange
        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        Path confFile = scenarioRoot.resolve("3S2T").resolve("application.conf");
        ScenarioLoader<Real> loader = new DcSimScenarioLoader();

        SimulationInputModel<Real> input = loader.load(confFile, tempRoot);

        Path exportDir = input.getExportDir();

        assertTrue(Files.exists(exportDir.resolve("sections.csv")));
        assertTrue(Files.exists(exportDir.resolve("track.csv")));
        assertTrue(Files.exists(exportDir.resolve("nodes.csv")));
        assertTrue(Files.exists(exportDir.resolve("substations.csv")));
        assertTrue(Files.exists(exportDir.resolve("lines.csv")));
        assertTrue(Files.exists(input.getRunCsv()));

        // A1: schema
        List<Map<String, String>> rows =
                new RunCsvReader(Schemas.RUN_V0_9).read(input.getRunCsv());

        assertFalse(rows.isEmpty());
        // A3 (B): normalization
        List<Map<String, String>> normalized =
                new RunInputNormalizer(RunInputNormalizer.Mode.SORT_ON_READ)
                        .normalizeAndValidate(rows);

        assertEquals(rows.size(), normalized.size());
    }
}
