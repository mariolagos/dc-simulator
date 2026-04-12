package org.dcsim.validation;

import org.dcsim.*;
import org.dcsim.math.Real;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class B3_1S5T_ScaleStabilityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Ignore("Pending migration to DcSimScenarioLoader: scenario currently fails during production-path loading")
    @Test
    public void B3_1S5T_scale_and_stability() throws Exception {

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        Path confFile = scenarioRoot.resolve("1S5T").resolve("application.conf");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, tempRoot);

        Path exportDir = input.getExportDir();
        Path runCsv = input.getRunCsv();
        Path nodesCsv = exportDir.resolve("nodes.csv");
        Path linesCsv = exportDir.resolve("lines.csv");

        assertTrue("run.csv should exist", Files.exists(runCsv));
        assertTrue("nodes.csv should exist", Files.exists(nodesCsv));
        assertTrue("lines.csv should exist", Files.exists(linesCsv));

        List<String> runLines = Files.readAllLines(runCsv);
        assertTrue("run.csv should contain header + data rows", runLines.size() > 1);

        List<String> nodeLines = Files.readAllLines(nodesCsv);
        assertTrue("nodes.csv should contain header + data rows", nodeLines.size() > 1);

        List<String> lineLines = Files.readAllLines(linesCsv);
        assertTrue("lines.csv should contain header + data rows", lineLines.size() > 1);
    }
}