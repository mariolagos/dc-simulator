package org.supply.app;

import org.junit.Test;
import org.supply.solver.testsupport.SyntheticScenarioGenerator.Scenario;

public final class SyntheticScenarioRegressionTest {
    @Test
    public void threeSubstationsOneTrain() {
        verify(Scenario.THREE_S_ONE_T);
    }

    @Test
    public void threeSubstationsTwoTrains() {
        verify(Scenario.THREE_S_TWO_T);
    }

    @Test
    public void oneSubstationFiveTrains() {
        verify(Scenario.ONE_S_FIVE_T);
    }

    private static void verify(Scenario scenario) {
        SyntheticScenarioRegression.verify(scenario, SyntheticScenarioRegression.run(scenario));
    }
}
