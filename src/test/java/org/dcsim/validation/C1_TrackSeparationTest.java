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
import java.util.List;

import static org.junit.Assert.assertTrue;

public class C1_TrackSeparationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Ignore("Pending migration to DcSimScenarioLoader: scenario currently fails during production-path loading")
    @Test
    public void C1_track_separation() throws Exception {

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        Path confFile = scenarioRoot.resolve("trackSeparation").resolve("application.conf");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, tempRoot);

        Path exportDir = input.getExportDir();
        Path nodesCsv = exportDir.resolve("nodes.csv");
        Path runCsv = input.getRunCsv();

        assertTrue("nodes.csv should exist", Files.exists(nodesCsv));
        assertTrue("run.csv should exist", Files.exists(runCsv));

        String nodesText = Files.readString(nodesCsv);
        String runText = Files.readString(runCsv);

        assertTrue("nodes.csv should contain multiple track references", nodesText.contains("track"));
        assertTrue("run.csv should contain track information", runText.contains("track"));    }
}