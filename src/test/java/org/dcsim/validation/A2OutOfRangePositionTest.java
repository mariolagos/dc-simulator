package org.dcsim.validation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class A2OutOfRangePositionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void A2_positionOutOfRange_failsWithClearError() throws Exception {
        File dir = tmp.newFolder("A2");
        File run = new File(dir, "run.csv");

        ValidationTestDataFactory factory = new ValidationTestDataFactory();
        factory.writeA2_outOfRangeRunCsv(run.toPath(), 1234.5);

        try {
            var rows = new RunCsvReader(Schemas.RUN_V0_9).read(run.toPath());
            new RunDomainValidator().validatePositions(rows, 1000.0);
            fail("Expected ValidationInputException");
        } catch (ValidationInputException ex) {
            assertTrue(ex.getMessage().contains("run.csv invalid position"));
            assertTrue(ex.getMessage().contains("allowed=[0.0, 1000.0]"));
        }
    }
}
