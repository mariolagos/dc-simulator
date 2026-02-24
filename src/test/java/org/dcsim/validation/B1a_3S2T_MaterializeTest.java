package org.dcsim.validation;

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

        ValidationScenarioLoader loader =
                new ValidationScenarioLoader(new ValidationTestDataFactory());

        // Act
        ScenarioWorkdir wd =
                loader.materialize("3S2T", scenarioRoot, tempRoot);

        // Assert: input files exist
        assertTrue(Files.exists(wd.sectionsCsv()));
        assertTrue(Files.exists(wd.trackCsv()));
        assertTrue(Files.exists(wd.nodesCsv()));
        assertTrue(Files.exists(wd.substationsCsv()));
        assertTrue(Files.exists(wd.linesCsv()));
        assertTrue(Files.exists(wd.runCsv()));

        // A1: schema
        List<Map<String, String>> rows =
                new RunCsvReader(Schemas.RUN_V0_9).read(wd.runCsv());
        assertFalse(rows.isEmpty());

        // A3 (B): normalization
        List<Map<String, String>> normalized =
                new RunInputNormalizer(RunInputNormalizer.Mode.SORT_ON_READ)
                        .normalizeAndValidate(rows);

        assertEquals(rows.size(), normalized.size());
    }
}
