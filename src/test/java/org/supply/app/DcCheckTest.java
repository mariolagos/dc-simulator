package org.supply.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class DcCheckTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void invalidInputProducesFailureReportWithoutOverwritingResultsOrExports() throws Exception {
        Path study = temporary.newFolder("study").toPath();
        Path exports = Files.createDirectories(study.resolve("exports"));
        Path results = Files.createDirectories(study.resolve("results"));
        Path oldRun = exports.resolve("run.csv");
        Path oldResult = results.resolve("existing.txt");
        Files.writeString(oldRun, "existing run");
        Files.writeString(oldResult, "existing result");
        Config empty = ConfigFactory.empty();
        DcStudyContext context = new DcStudyContext(study.resolve("study.conf"), study,
                empty, empty, "test", "test", "", exports, results);

        DcCheck.CheckResult check = DcCheck.run(context, null);

        assertFalse(check.valid());
        assertTrue(check.report().contains("Status: FAIL"));
        assertTrue(Files.exists(check.reportPath()));
        assertEquals("existing run", Files.readString(oldRun));
        assertEquals("existing result", Files.readString(oldResult));
        assertEquals(study.resolve("checks/test_input_check.txt"), check.reportPath());
    }
}
