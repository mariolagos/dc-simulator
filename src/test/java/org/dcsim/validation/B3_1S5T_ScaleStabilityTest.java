package org.dcsim.validation;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class B3_1S5T_ScaleStabilityTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void B3_1S5T_scale_and_stability() throws Exception {
        Assume.assumeTrue("AppConf -> CSV generator not implemented",
                TestCapabilities.appConfGeneratorEnabled());

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        ValidationScenarioLoader loader = new ValidationScenarioLoader(new ValidationTestDataFactory());
        ScenarioWorkdir wd = loader.materialize("1S5T", scenarioRoot, tempRoot);

        // TODO later:
        // - assert materialization succeeded
        // - sanity-check run.csv row counts > some minimum (optional)
        // - later: run MATLAB and validate no NaN/Inf etc.
    }
}
