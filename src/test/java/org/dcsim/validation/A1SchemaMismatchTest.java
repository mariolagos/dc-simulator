package org.dcsim.validation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class A1SchemaMismatchTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void A1_runSchemaMismatch_failsFast() throws Exception {
        File dir = tmp.newFolder("A1");
        File run = new File(dir, "run.csv");

        // wrong headers: t,id
        Files.write(run.toPath(), "t,id\n0,T1\n".getBytes(StandardCharsets.UTF_8));

        try {
            new RunCsvReader(Schemas.RUN_V0_9).read(run.toPath());
            fail("Expected ValidationInputException");
        } catch (ValidationInputException ex) {
            String msg = ex.getMessage();
            assertTrue(msg.contains("run.csv"));
            assertTrue(msg.contains("header mismatch"));
            assertTrue(msg.contains("expected"));
            assertTrue(msg.contains("got"));
        }
    }
}