package org.dcsim.validation;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class B1_3S2T_BaselineHappyPathTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void B1_3S2T_baseline_happy_path() throws Exception {
        Assume.assumeTrue(
                "AppConf -> CSV generator not implemented yet. Enable with -Dappconf.generator.enabled=true",
                Boolean.parseBoolean(System.getProperty("appconf.generator.enabled", "false"))
        );

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        ValidationScenarioLoader loader = new ValidationScenarioLoader(new ValidationTestDataFactory());
        ScenarioWorkdir wd = loader.materialize("3S2T", scenarioRoot, tempRoot);

        // När generatorn finns: fortsätt med run.csv validation + ev MATLAB
        // ... (B1 assertions)
    }
}
