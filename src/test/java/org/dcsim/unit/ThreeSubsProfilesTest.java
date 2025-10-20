package org.dcsim.unit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * Simple profile-driven scenario test scaffold.
 * - Java 17 compatible (no top-level code).
 * - Uses JUnit 4.
 * - Writes a temporary CSV and passes its Path to a scenario runner.
 * Replace runScenarioWithProfiles(...) with your real scenario invocation.
 */
public class ThreeSubsProfilesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void threeSubs_oneRegenTrain_profiles() throws Exception {
        // 1) Create a temporary CSV with a tiny profile
        Path csv = tmp.newFile("profiles.csv").toPath();
        String csvBody = String.join("\n",
                "time_s,train_id,req_W,iMax_A,vmin_V,vmax_V",
                "0,T1,-100000,300,850,1000",
                "5,T1,-120000,300,850,1000",
                "10,T1,-80000,300,850,1000",
                "15,T1,-100000,300,850,1000"
        );
        Files.writeString(csv, csvBody);

        // 2) Sanity check: file exists and has content
        assertTrue("profiles.csv must exist", Files.exists(csv));
        assertTrue("profiles.csv must not be empty", Files.size(csv) > 0);

        // 3) Execute your scenario using the CSV
        runScenarioWithProfiles(csv);
    }

    /**
     * Hook this up to your real pipeline.
     * For now, it only asserts that the file is readable.
     */
    private void runScenarioWithProfiles(Path profilesCsv) throws IOException {
        // Example: if you later add a ProfilesLoader:
        // Profiles p = ProfilesLoader.load(profilesCsv);
        // DcNet net = TestNets.threeSubsOneTrack();
        // DcSolveResult result = DcIterativeSolver.solve(net, p);
        // assert... result conditions ...

        // Temporary no-op: ensure we can read lines
        assertTrue("profiles.csv should have at least 2 lines",
                Files.readAllLines(profilesCsv).size() >= 2);
    }
}
