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

        double badPos = 1234.5;
<<<<<<< HEAD
        double trackLengthM = 1000.0;

=======

        // Write a schema-correct run.csv but with an out-of-range position
>>>>>>> test(validation): align A1/A2 with ValidationInputException and domain validation semantics
        ValidationTestDataFactory factory = new ValidationTestDataFactory();
        factory.writeA2_outOfRangeRunCsv(run.toPath(), badPos);

        try {
            var rows = new RunCsvReader(Schemas.RUN_V0_9).read(run.toPath());
<<<<<<< HEAD
            new RunDomainValidator().validatePositions(rows, trackLengthM);
=======

            // track length chosen so badPos is out-of-range
            double trackLengthM = 1000.0;
            new RunDomainValidator().validatePositions(rows, trackLengthM);

>>>>>>> test(validation): align A1/A2 with ValidationInputException and domain validation semantics
            fail("Expected ValidationInputException");
        } catch (ValidationInputException ex) {
            String msg = String.valueOf(ex.getMessage()).toLowerCase();

<<<<<<< HEAD
            // "Clear error": must mention position, and should include either the bad value or the allowed bound.
            assertTrue("message should mention position. msg=" + msg,
                    msg.contains("position") || msg.contains("position_m"));

            assertTrue("message should include badPos or bound. msg=" + msg,
                    msg.contains(String.valueOf(badPos).toLowerCase())
                            || msg.contains(String.valueOf(trackLengthM).toLowerCase())
                            || msg.contains("1000")
                            || msg.contains("max")
                            || msg.contains("outside")
                            || msg.contains("bound"));
        }
    }
=======
            // Must be a clear input validation error (not schema mismatch anymore)
            // Accept any of these tokens depending on validator wording.
            boolean ok =
                    msg.contains("position") ||
                            msg.contains("position_m") ||
                            msg.contains("run.csv") ||
                            msg.contains("out of range") ||
                            msg.contains("outside") ||
                            msg.contains("invalid");

            assertTrue("Unexpected message: " + ex.getMessage(), ok);
        }    }
>>>>>>> test(validation): align A1/A2 with ValidationInputException and domain validation semantics
}