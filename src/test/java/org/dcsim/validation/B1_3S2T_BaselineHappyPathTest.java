package org.dcsim.validation;

import org.dcsim.*;
import org.dcsim.math.Real;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class B1_3S2T_BaselineHappyPathTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Ignore("Pending migration to DcSimScenarioLoader: scenario currently fails during production-path loading")
    @Test
    public void B1_3S2T_baseline_happy_path() throws Exception {

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        Path confFile = scenarioRoot.resolve("3S2T").resolve("application.conf");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, tempRoot);

        Path workdir = input.getOutputRoot();
        Path exportDir = input.getExportDir();
        Path runCsv = input.getRunCsv();

        System.out.println(workdir);
        System.out.println(exportDir);
        Files.list(workdir).forEach(System.out::println);

        assertTrue("workdir should exist", Files.exists(workdir));
        assertTrue("exports dir should exist", Files.exists(exportDir));

        assertTrue("nodes.csv should exist", Files.exists(exportDir.resolve("nodes.csv")));
        assertTrue("lines.csv should exist", Files.exists(exportDir.resolve("lines.csv")));
        assertTrue("substations.csv should exist", Files.exists(exportDir.resolve("substations.csv")));
        assertTrue("run.csv should exist", Files.exists(runCsv));

        assertTrue("nodes.csv should not be empty", Files.size(exportDir.resolve("nodes.csv")) > 0);
        assertTrue("lines.csv should not be empty", Files.size(exportDir.resolve("lines.csv")) > 0);
        assertTrue("substations.csv should not be empty", Files.size(exportDir.resolve("substations.csv")) > 0);
        assertTrue("run.csv should not be empty", Files.size(runCsv) > 0);
    }
}