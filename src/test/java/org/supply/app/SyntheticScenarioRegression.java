package org.supply.app;

import org.supply.domain.RunSample;
import org.supply.math.Real;
import org.supply.solver.build.*;
import org.supply.solver.model.*;
import org.supply.solver.testsupport.SyntheticScenarioGenerator;
import org.supply.solver.testsupport.SyntheticScenarioGenerator.Scenario;
import org.supply.track.DefaultTrackTransformService;

import java.util.*;

/** Executes the production mapping, train insertion and fallback solver without reports. */
public final class SyntheticScenarioRegression {
    public record Metrics(double minimumTrainVoltageV, double consumedTrainEnergyJ,
                          double regeneratedTrainEnergyJ, double maximumBalanceErrorW) { }

    private SyntheticScenarioRegression() { }

    public static Metrics run(Scenario scenario) {
        var infra = SyntheticScenarioGenerator.infrastructure(scenario);
        var transform = new DefaultTrackTransformService(infra.track());
        var base = new CalculationNetworkBuilder(transform).buildBase(infra.grid());
        var positions = new TrainPositionFactory(transform);
        var inserter = new TrainNodeInserter(infra.parameters(), infra.routes());
        Map<String, Real> previous = Map.of();
        double minimumV = Double.POSITIVE_INFINITY;
        double consumedJ = 0, regeneratedJ = 0, maximumBalanceW = 0;
        Set<String> seen = new HashSet<>();
        int trainSeconds = 0;
        for (int time = 0; time < scenario.durationS(); time++) {
            List<RunSample> samples = SyntheticScenarioGenerator.samples(scenario, time);
            var mapped = positions.fromRunSamples(samples);
            // Independent geometry oracle: reverse direction must map to L - position.
            for (int i = 0; i < samples.size(); i++) {
                var sample = samples.get(i);
                double expected = sample.routeId().equals("AD") ? sample.positionM()
                        : SyntheticScenarioGenerator.LENGTH_M - sample.positionM();
                require(Math.abs(mapped.get(i).positionM() - expected) <= 0.5,
                        scenario, time, "Incorrect mapped position for " + sample.trainId());
                seen.add(sample.trainId());
            }
            var network = inserter.insertTrainNodes(base, mapped);
            require(network.trainLoads().size() == samples.size(), scenario, time, "Missing train load");
            for (int i = 0; i < samples.size(); i++) {
                var sample = samples.get(i);
                var load = network.trainLoads().stream()
                        .filter(candidate -> candidate.trainId().equals(sample.trainId())).findFirst().orElseThrow();
                for (String terminal : List.of(load.feedingNodeId(), load.returnNodeId())) {
                    var node = network.nodes().stream().filter(candidate -> candidate.id().equals(terminal))
                            .findFirst().orElseThrow();
                    require(node.sectionId().equals("1") && node.trackId().equals("N")
                                    && Math.abs(node.positionM() - mapped.get(i).positionM()) <= 0.5,
                            scenario, time, "Incorrect inserted terminal for " + sample.trainId());
                }
            }
            double totalLineResistance = network.branches().stream()
                    .mapToDouble(branch -> branch.resistanceOhm().asDouble()).sum();
            require(Math.abs(totalLineResistance - 2 * SyntheticScenarioGenerator.LENGTH_M
                            * SyntheticScenarioGenerator.RESISTANCE_OHM_PER_M) < 1e-9,
                    scenario, time, "Train insertion changed total line resistance");
            trainSeconds += samples.size();
            Map<String, Double> requested = new LinkedHashMap<>();
            for (var sample : samples) requested.put(sample.trainId(), sample.pReqW());
            var solved = DcSolver.solveWithFallback(infra.parameters(), network, requested, previous);
            require(solved.networkResult().converged(), scenario, time, "Solver did not converge");
            var voltages = solved.networkResult().voltages();
            double trainsW = 0, suppliedW = 0, lossesW = 0;
            for (var load : network.trainLoads()) {
                double v = voltage(voltages, load.feedingNodeId(), load.returnNodeId());
                require(Double.isFinite(v) && v >= 500 - 0.01 && v <= 900 + 0.01,
                        scenario, time, "Train voltage outside limits: " + v);
                minimumV = Math.min(minimumV, v);
                double allocated = solved.allocatedPowersW().get(load.trainId());
                double p = v * DcSolver.trainCurrentA(infra.parameters(), allocated, v);
                require(Double.isFinite(p), scenario, time, "Non-finite delivered power");
                double req = requested.get(load.trainId());
                require(req >= 0 ? p >= -1 && p <= req + 1 : p <= 1 && p >= req - 1,
                        scenario, time, "Invalid power allocation: " + p);
                trainsW += p;
                consumedJ += Math.max(0, p); // dt = 1 s
                regeneratedJ += Math.max(0, -p);
            }
            for (var element : network.elements()) {
                if (element instanceof DiodeSubstationElement diode) {
                    double v = voltage(voltages, diode.feedingNodeId(), diode.returnNodeId());
                    double current = diode.enabled() ? Math.max(0,
                            (diode.emfV().asDouble() - v) / diode.internalResistanceOhm().asDouble()) : 0;
                    suppliedW += v * current;
                }
            }
            for (var branch : network.branches()) {
                double drop = voltage(voltages, branch.fromNodeId(), branch.toNodeId());
                lossesW += drop * drop / branch.resistanceOhm().asDouble();
            }
            double residual = Math.abs(suppliedW - trainsW - lossesW);
            require(Double.isFinite(residual), scenario, time, "Non-finite power balance");
            maximumBalanceW = Math.max(maximumBalanceW, residual);
            previous = voltages;
        }
        require(seen.size() == scenario.trains()
                        && trainSeconds == scenario.trains() * SyntheticScenarioGenerator.JOURNEY_S,
                scenario, -1, "Incomplete traffic coverage");
        return new Metrics(minimumV, consumedJ, regeneratedJ, maximumBalanceW);
    }

    /** Fixed regression references from the initial run; never refreshed by a test run. */
    public static void verify(Scenario scenario, Metrics actual) {
        double expectedVoltage;
        double expectedRegeneration;
        double regenerationTolerance;
        switch (scenario) {
            case THREE_S_ONE_T -> {
                expectedVoltage = 746.837270;
                expectedRegeneration = 0;
                regenerationTolerance = 1;
            }
            case THREE_S_TWO_T -> {
                expectedVoltage = 746.534573;
                expectedRegeneration = 400_290.872397;
                regenerationTolerance = 50;
            }
            case ONE_S_FIVE_T -> {
                expectedVoltage = 737.206531;
                expectedRegeneration = 4_200_979.103225;
                regenerationTolerance = 500;
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
        near(scenario, "minimum train voltage", expectedVoltage, actual.minimumTrainVoltageV(), 0.05);
        // Independent rectangular-profile energy: (120 kW * 20 s + 40 kW * 20 s) per train.
        near(scenario, "consumed train energy", 3_200_000.0 * scenario.trains(),
                actual.consumedTrainEnergyJ(), 20);
        near(scenario, "regenerated train energy", expectedRegeneration,
                actual.regeneratedTrainEnergyJ(), regenerationTolerance);
        require(Double.isFinite(actual.maximumBalanceErrorW()) && actual.maximumBalanceErrorW() <= 1,
                scenario, -1, "Power balance error exceeds 1 W: " + actual.maximumBalanceErrorW());
    }

    private static void near(Scenario scenario, String metric, double expected, double actual, double tolerance) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, scenario, -1,
                metric + ": expected " + expected + " +/- " + tolerance + ", actual " + actual);
    }

    private static double voltage(Map<String, Real> voltages, String from, String to) {
        return voltages.get(from).asDouble() - voltages.get(to).asDouble();
    }

    private static void require(boolean condition, Scenario scenario, int time, String message) {
        if (!condition) throw new AssertionError(scenario.id() + " at t=" + time + ": " + message);
    }

    public static void main(String[] args) {
        for (Scenario scenario : Scenario.values()) {
            Metrics m = run(scenario);
            verify(scenario, m);
            System.out.printf(Locale.ROOT, "%s PASS: minU=%.6f V, consumed=%.6f J, regen=%.6f J, balance=%.6f W%n",
                    scenario.id(), m.minimumTrainVoltageV(), m.consumedTrainEnergyJ(),
                    m.regeneratedTrainEnergyJ(), m.maximumBalanceErrorW());
        }
    }
}
