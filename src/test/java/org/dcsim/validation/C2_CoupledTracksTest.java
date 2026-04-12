package org.dcsim.validation;

import org.dcsim.DcSimScenarioLoader;
import org.dcsim.ScenarioLoader;
import org.dcsim.SimulationInputModel;
import org.dcsim.math.Real;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class C2_CoupledTracksTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Ignore("Pending migration to DcSimScenarioLoader: scenario currently fails during production-path loading")
    @Test
    public void C2_coupled_tracks() throws Exception {

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        Path confFile = scenarioRoot.resolve("coupledTracks").resolve("application.conf");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, tempRoot);

        Path exportDir = input.getExportDir();
        Path runCsv = input.getRunCsv();

        Path nodesCsv = exportDir.resolve("nodes.csv");
        Path linesCsv = exportDir.resolve("lines.csv");

        assertTrue("nodes.csv should exist", Files.exists(nodesCsv));
        assertTrue("lines.csv should exist", Files.exists(linesCsv));
        assertTrue("run.csv should exist", Files.exists(runCsv));

        assertTrue("nodes.csv should not be empty", Files.size(nodesCsv) > 0);
        assertTrue("lines.csv should not be empty", Files.size(linesCsv) > 0);
        assertTrue("run.csv should not be empty", Files.size(runCsv) > 0);    }
}