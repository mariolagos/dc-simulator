package org.dcsim.validation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ResultsCsvValidatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void resultsValidator_rejectsNaN() throws Exception {
        File dir = tmp.newFolder("results");
        File f = new File(dir, "results_train.csv");

        // Minimal example: you can swap columns later to match MATLAB
        Files.write(f.toPath(),
                ("time_s,train_id,status,losses_W\n" +
                        "0,T1,OK,NaN\n").getBytes(StandardCharsets.UTF_8));

        ResultsCsvValidator validator = new ResultsCsvValidator();

        ResultsCsvValidator.Rules rules = new ResultsCsvValidator.Rules(
                null, // no strict schema yet
                new HashSet<String>(Arrays.asList("time_s", "train_id")),
                new HashSet<String>(Arrays.asList("losses_W")), // numeric column to check
                "status",
                new HashSet<String>(Arrays.asList("OK", "INFEASIBLE", "ERROR"))
        );

        try {
            validator.validate(f.toPath(), rules);
            fail("Expected ValidationInputException");
        } catch (ValidationInputException ex) {
            assertTrue(ex.getMessage().contains("non-finite"));
        }
    }
}
