package org.supply.solver.testsupport;

import org.supply.domain.*;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.track.*;

import java.util.*;

/** Shared deterministic infrastructure and traffic for electrical regression tests. */
public final class SyntheticScenarioGenerator {
    public static final int LENGTH_M = 4_000;
    public static final int JOURNEY_S = 60;
    public static final int HEADWAY_S = 10;
    public static final double RESISTANCE_OHM_PER_M = 0.000015;
    public static final double INTERNAL_RESISTANCE_OHM = 0.01;

    public enum Scenario {
        THREE_S_ONE_T("3S1T", 3, 1),
        THREE_S_TWO_T("3S2T", 3, 2),
        ONE_S_FIVE_T("1S5T", 1, 5);

        private final String id;
        private final int substations;
        private final int trains;

        Scenario(String id, int substations, int trains) {
            this.id = id;
            this.substations = substations;
            this.trains = trains;
        }
        public String id() { return id; }
        public int substations() { return substations; }
        public int trains() { return trains; }
        public int durationS() { return JOURNEY_S + (trains - 1) * HEADWAY_S; }
    }

    public record Infrastructure(GridModel grid, LoadedTrackModel track,
                                 List<Route> routes, SystemParameters parameters) { }

    private SyntheticScenarioGenerator() { }

    public static Infrastructure infrastructure(Scenario scenario) {
        LoadedTrackModel sections = new TrackLoader().load(List.of(
                new KilometerBoard("1", "0+000", 0, LENGTH_M),
                new KilometerBoard("1", "4+000", LENGTH_M, 0)), List.of(), List.of());
        Map<String, RouteView> views = new LinkedHashMap<>();
        views.put("AD", new RouteView("AD", "AD", List.of(path(0, 0), path(LENGTH_M, LENGTH_M))));
        views.put("DA", new RouteView("DA", "DA", List.of(path(0, LENGTH_M), path(LENGTH_M, 0))));
        LoadedTrackModel track = new LoadedTrackModel(sections.getSectionsById(),
                sections.getJunctions(), sections.getStations(), views);

        GridModel grid = new GridModel();
        int[] positions = {0, 2_000, 4_000};
        for (int i = 0; i < positions.length; i++) {
            String coordinate = "1 " + RwyCoordinate.formatKmShort(positions[i]) + " N";
            grid.addNode(new Node("F" + i, coordinate));
            grid.addNode(new Node("R" + i, coordinate));
        }
        List<String> feeding = new ArrayList<>();
        List<String> returning = new ArrayList<>();
        for (int i = 0; i < positions.length - 1; i++) {
            String f = "FEED" + i;
            String r = "RETURN" + i;
            grid.addLine(new Line(f, grid.getNode("F" + i), grid.getNode("F" + (i + 1)),
                    Real.fromDouble(RESISTANCE_OHM_PER_M)));
            grid.addLine(new Line(r, grid.getNode("R" + i), grid.getNode("R" + (i + 1)),
                    Real.fromDouble(RESISTANCE_OHM_PER_M)));
            feeding.add(f);
            returning.add(r);
        }
        for (int i = 0; i < positions.length; i++) {
            if (scenario.substations() == 1 && i != 1) continue;
            String id = "SS" + i;
            grid.addInstallation(new PowerInstallation(id, InstallationCategory.SUBSTATION, true,
                    Real.fromDouble(750), Real.fromDouble(INTERNAL_RESISTANCE_OHM), RectifierType.DIODE));
            grid.addInstallationConnection(new InstallationConnection(id, "F" + i, ConnectionType.FEEDING));
            grid.addInstallationConnection(new InstallationConnection(id, "R" + i, ConnectionType.RETURN));
        }
        List<String> reverseF = new ArrayList<>(feeding);
        List<String> reverseR = new ArrayList<>(returning);
        Collections.reverse(reverseF);
        Collections.reverse(reverseR);
        List<Route> routes = List.of(new Route("AD", feeding, returning), new Route("DA", reverseF, reverseR));
        SystemParameters parameters = new SystemParameters(750, 500, 600, 900,
                6_000, 10_000_000, 3_600_000, "R0");
        return new Infrastructure(grid, track, routes, parameters);
    }

    /** Left-endpoint samples: each sample represents the following one-second interval. */
    public static List<RunSample> samples(Scenario scenario, int timeS) {
        List<RunSample> samples = new ArrayList<>();
        for (int i = 0; i < scenario.trains(); i++) {
            int age = timeS - i * HEADWAY_S;
            if (age < 0 || age >= JOURNEY_S) continue;
            String route = i % 2 == 0 ? "AD" : "DA";
            double position = 600 + travelledM(age);
            double speed = age < 20 ? 2.0 * age : age < 40 ? 40 : 2.0 * (60 - age);
            double power = age < 20 ? 120_000 : age < 40 ? 40_000 : -100_000;
            samples.add(new RunSample(timeS, "T" + (i + 1), "1", "N", route, position, power, speed));
        }
        return List.copyOf(samples);
    }

    private static double travelledM(int age) {
        if (age < 20) return age * age;
        if (age < 40) return 400 + 40 * (age - 20);
        double brakingAge = age - 40;
        return 1_200 + 40 * brakingAge - brakingAge * brakingAge;
    }

    private static PathSample path(int routeM, int railwayM) {
        return new PathSample(routeM, new RwyCoordinate("1",
                RwyCoordinate.formatKmShort(railwayM), railwayM, "N"));
    }
}
