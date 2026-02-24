package org.dcsim.validation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

public class A1_InvalidNetworkTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void A1_invalid_network_unknown_node() throws Exception {
        Path wd = tmp.newFolder("A1").toPath();

        A1TestData.writeBrokenNetworkCsvs(wd);

        // Ingen Java-validering här.
        // Det är POÄNGEN: MATLAB ska få detta.

        // Senare, när MATLAB finns:
        // Result r = matlabRunner.run(wd);
        // assertEquals(NOT_OK, r.status());
    }
}
