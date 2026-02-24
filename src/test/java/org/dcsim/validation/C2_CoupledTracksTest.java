package org.dcsim.validation;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class C2_CoupledTracksTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void C2_coupled_tracks() throws Exception {
        Assume.assumeTrue("AppConf -> CSV generator not implemented",
                TestCapabilities.appConfGeneratorEnabled());

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();
        Path tempRoot = tmp.newFolder("work").toPath();

        ValidationScenarioLoader loader = new ValidationScenarioLoader(new ValidationTestDataFactory());
        ScenarioWorkdir wd = loader.materialize("coupledTracks", scenarioRoot, tempRoot);

        // TODO later:
        // - run MATLAB
        // - verify coupling effects exist (when you define the invariant)
    }
}
