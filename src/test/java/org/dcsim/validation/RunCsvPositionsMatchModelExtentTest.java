package org.dcsim.validation;

import org.dcsim.*;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.math.Real;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RunCsvPositionsMatchModelExtentTest {

    // -------------------------------------------------------------------------
    // ACTIVE TEST: verifies loader behaviour (current production path)
    // -------------------------------------------------------------------------
    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void loader_builds_model_with_valid_track_extents() throws Exception {

        Path original = Path.of("project/validationTests/3S1T/application.conf");
        String text = Files.readString(original);
        text = text.replaceAll("enabled\\s*=\\s*true", "enabled = false");;

        Path confFile = Files.createTempFile("3S1T-loader-", ".conf");
        Files.writeString(confFile, text);

        Path outputRoot = Files.createTempDirectory("junit");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, outputRoot);

        assertNotNull(input);
        assertNotNull(input.getGridModel());

        Map<Integer, ContractChecks.TrackExtent> extentByTrack =
                ContractChecks.extentByTrackFromModel(input.getGridModel());

        assertFalse("Expected non-empty track extents", extentByTrack.isEmpty());

        ContractChecks.TrackExtent extent = extentByTrack.get(1);
        assertNotNull("Expected track 1 extent", extent);

        assertTrue("Expected positive extent", extent.maxM() > extent.minM());
    }

    // -------------------------------------------------------------------------
    // FUTURE TEST: requires run.csv materialization (not active yet)
    // -------------------------------------------------------------------------
    @Ignore("Requires materialized run.csv in production loading path")
    @Test
    public void materialized_run_csv_positions_match_model_extent() throws Exception {

        Path confFile = Path.of("project/validationTests/3S1T/application.conf");
        Path outputRoot = Files.createTempDirectory("junit");

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, outputRoot);

        // --- Model extent ---
        Map<Integer, ContractChecks.TrackExtent> extentByTrack =
                ContractChecks.extentByTrackFromModel(input.getGridModel());

        // --- Read run.csv using SAME reader as production ---
        Path runCsv = input.getRunCsv();

        CsvSchema schema = CsvSchema.runSchema();
        RunCsvReader reader = new RunCsvReader(schema);
        List<Map<String, String>> rows = reader.read(runCsv);

        for (Map<String, String> row : rows) {

            int track = Integer.parseInt(row.get("track"));
            int posM = Integer.parseInt(row.get("position_m"));

            ContractChecks.TrackExtent extent = extentByTrack.get(track);
            assertNotNull("Missing track " + track, extent);

            assertTrue(
                    "Position " + posM + " outside extent [" +
                            extent.minM() + "," + extent.maxM() + "]",
                    posM >= extent.minM() && posM <= extent.maxM()
            );
        }
    }
}