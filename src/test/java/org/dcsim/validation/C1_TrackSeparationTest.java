package org.dcsim.validation;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class C1_TrackSeparationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void C1_track_separation() throws Exception {
        Assume.assumeTrue("AppConf -> CSV generator not implemented",
                TestCapabilities.appConfGeneratorEnabled());

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        ValidationScenarioLoader loader = new ValidationScenarioLoader(new ValidationTestDataFactory());
        ScenarioWorkdir wd = loader.materialize("trackSeparation", scenarioRoot, tempRoot);

        // TODO later:
        // - run MATLAB
        // - verify results show no cross-track coupling (when you define the invariant)
    }
}
