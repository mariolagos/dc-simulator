package org.dcsim.validation;

import org.dcsim.export.RunCsvWriter;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class B1_3S1T_EndToEndMatlabTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void B1_3S1T_endToEnd_with_matlab() throws Exception {
        Assume.assumeTrue(
                "AppConf -> CSV generator not implemented yet",
                TestCapabilities.appConfGeneratorEnabled()
        );

        // Enable with: -Dmatlab.enabled=true
        Assume.assumeTrue(Boolean.parseBoolean(System.getProperty("matlab.enabled", "false")));

        Path scenarioRoot = TestScenarioPaths.scenarioRoot();

        File temp = tmp.newFolder("work");
        Path tempRoot = temp.toPath();

        ValidationTestDataFactory factory = new ValidationTestDataFactory();
        ValidationScenarioLoader loader = new ValidationScenarioLoader(factory);
        ScenarioWorkdir wd = loader.materialize("3S1T", scenarioRoot, tempRoot);

        // Normalize run rows and write back (recommended for determinism)
        List<Map<String, String>> runRows = new RunCsvReader(Schemas.RUN_V0_9).read(wd.runCsv());
        List<Map<String, String>> normalized =
                new RunInputNormalizer(RunInputNormalizer.Mode.SORT_ON_READ).normalizeAndValidate(runRows);
        new RunCsvWriter(Schemas.RUN_V0_9).write(wd.runCsv(), normalized);

        // Run MATLAB
        String matlabExe = System.getProperty("matlab.exe", "matlab");
        MatlabRunner matlab = new ProcessMatlabRunner(matlabExe, null);
        matlab.run(wd.workdir());

        // Validate output (invariants)
        ResultsCsvValidator v = new ResultsCsvValidator();
        ResultsCsvValidator.Rules rules = new ResultsCsvValidator.Rules(
                null,
                new HashSet<String>(Arrays.asList("time_s")), // adjust if your results don't have time_s
                new HashSet<String>(Arrays.asList("losses_W", "power_W", "voltage_V")), // checked only if present
                "status",
                new HashSet<String>(Arrays.asList("OK", "INFEASIBLE", "ERROR"))
        );

        v.validate(wd.workdir().resolve(ValidationFiles.RESULTS_TRAIN_CSV), rules);
        v.validate(wd.workdir().resolve(ValidationFiles.RESULTS_SUBSTATION_CSV), rules);
        v.validate(wd.workdir().resolve(ValidationFiles.RESULTS_LINE_CSV), rules);
    }
}
