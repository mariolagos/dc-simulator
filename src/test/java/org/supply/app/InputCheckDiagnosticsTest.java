package org.supply.app;

import org.junit.Test;
import org.supply.math.Real;
import org.supply.solver.model.CalculationTrainPosition;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.Assert.*;

public class InputCheckDiagnosticsTest {
    @Test public void placementFailureIncludesTrainTimeRouteAndBothPositions() {
        var train = new CalculationTrainPosition("ad-001", "101", "N", "AD", 1392, 608, Real.fromDouble(1000));
        String error = InputCheckDiagnostics.placementFailure(42, train, new IllegalArgumentException("No return connection"));
        for (String field : List.of("time_s=42.0", "train=ad-001", "route=AD", "section=101", "track=N",
                "position_m=608.0", "route_position_m=1392.0", "No return connection")) assertTrue(error, error.contains(field));
    }
    @Test public void consoleShowsBoundedErrorsAndOmitsTopologyDump() {
        var errors = IntStream.range(0, 25).mapToObj(i -> "placement " + i).toList();
        String report = "DC INPUT CHECK\nStudy: test\nStatus: FAIL; errors=25; warnings=0\n\nbranch=HUGE";
        String summary = InputCheckDiagnostics.consoleSummary(report, errors, List.of());
        assertTrue(summary.contains("Status: FAIL")); assertTrue(summary.contains("ERROR placement 19\n"));
        assertFalse(summary.contains("ERROR placement 20\n")); assertFalse(summary.contains("branch=HUGE"));
        assertTrue(summary.contains("5 further messages")); assertEquals(25, errors.size());
    }
}
