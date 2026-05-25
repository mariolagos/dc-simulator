package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class OutputPathsResolutionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void resolvesOutputsIndependentOfWorkingDir() throws Exception {
        Path confDir = tmp.newFolder("scenario").toPath();
        Path confFile = confDir.resolve("application.conf");

        Config dcsim = ConfigFactory.parseString(
                "dcsim {\n" +
                        "  project = \"acme.969.speed.up\"\n" +
                        "  scenario = \"H0BrakePreCalculationState\"\n" +
                        "  export { root = \"D:/calc/_export\" }\n" +
                        "  results { root = \"D:/calc/_results\" }\n" +
                        "}"
        ).getConfig("dcsim");

        // Simulate two different working directories by changing user.dir
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.newFolder("wd1").getAbsolutePath());
            DcSimPaths.OutputRoots r1 = DcSimPaths.resolveOutputs(dcsim, confFile);

            System.setProperty("user.dir", tmp.newFolder("wd2").getAbsolutePath());
            DcSimPaths.OutputRoots r2 = DcSimPaths.resolveOutputs(dcsim, confFile);

            assertEquals(r1.exportDir(), r2.exportDir());
            assertEquals(r1.resultsDir(), r2.resultsDir());
            assertEquals(r1.longtablePath(), r2.longtablePath());
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }
}