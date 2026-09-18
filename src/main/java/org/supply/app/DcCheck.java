package org.supply.app;

import org.supply.domain.Route;
import org.supply.domain.RunSample;
import org.supply.domain.SystemParameters;
import org.supply.io.export.RunCsvWriter;
import org.supply.loader.GridModelLoader;
import org.supply.loader.RouteFactory;
import org.supply.loader.RunSampleLoader;
import org.supply.loader.SystemParametersFactory;
import org.supply.model.GridModel;
import org.supply.solver.build.CalculationNetworkBuilder;
import org.supply.solver.build.ElectricalRouteCheck;
import org.supply.solver.build.ElectricalTopologyGraph;
import org.supply.solver.build.TrainNodeInserter;
import org.supply.solver.build.TrainPositionFactory;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.DefaultTrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;
import org.supply.track.TrackTransformService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Input preflight. Never invokes DcSolver or writes into exports/results. */
public final class DcCheck {
    private DcCheck() { }

    public record CheckResult(List<String> errors, List<String> warnings,
                              String report, Path reportPath) {
        public boolean valid() { return errors.isEmpty(); }
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--help")) {
            System.out.println("DcCheck [--check] <study.conf> [--route <routeId>]");
            return;
        }
        String conf = null;
        String routeId = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--check".equals(arg)) {
                continue;
            } else if ("--route".equals(arg)) {
                if (++i == args.length || args[i].isBlank()) {
                    throw new IllegalArgumentException("--route requires a route ID");
                }
                routeId = args[i];
            } else if (arg.startsWith("--") || conf != null) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            } else {
                conf = arg;
            }
        }
        if (conf == null) {
            throw new IllegalArgumentException("Usage: DcCheck <study.conf> [--route <routeId>]");
        }
        CheckResult result = run(DcStudyContextLoader.load(conf), routeId);
        System.out.print(result.report());
        System.out.println("Check report: " + result.reportPath());
        if (!result.valid()) {
            throw new IllegalArgumentException("Input check failed: " + result.errors().size() + " error(s)");
        }
    }

    /** selectedRouteId filters only topology output; all routes and traffic are checked. */
    public static CheckResult run(DcStudyContext context, String selectedRouteId) throws Exception {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        // Validate original definitions: split calculation branches may legitimately share sourceId.
        if (context.dcsim().hasPath("grid.lines")) {
            Set<String> lineIds = new HashSet<>();
            stage("Unique input line IDs", errors, detail, () -> {
                Set<String> duplicates = new LinkedHashSet<>();
                for (var line : context.dcsim().getConfigList("grid.lines")) {
                    String id = line.getString("line_id");
                    if (!lineIds.add(id)) { duplicates.add(id); }
                }
                if (!duplicates.isEmpty()) {
                    throw new IllegalArgumentException("Duplicate input line_id: " + String.join(", ", duplicates));
                }
                return Boolean.TRUE;
            });
        }

        GridModel grid = stage("Grid and installation input", errors, detail,
                () -> new GridModelLoader().load(context.dcsim()));
        LoadedTrackModel track = stage("Track input", errors, detail,
                () -> new TrackConfigLoader().load(context.dcsim(), context.confFile()));
        SystemParameters parameters = stage("System parameters", errors, detail,
                () -> new SystemParametersFactory().build(context.dcsim()));
        List<Route> routes = stage("Route definitions", errors, detail,
                () -> new RouteFactory().build(context.dcsim().getConfig("traffic")));

        TrackTransformService transform = track == null ? null : new DefaultTrackTransformService(track);
        CalculationNetwork network = null;
        if (grid != null && transform != null) {
            network = stage("Calculation network (no solve)", errors, detail,
                    () -> new CalculationNetworkBuilder(transform).buildBase(grid));
        } else {
            detail.append("SKIP Calculation network: grid or track input failed\n");
        }
        if (track != null) {
            List<org.supply.track.TrackRouteDiagram.ObjectPoint> objects = new ArrayList<>();
            if (network != null) {
                for (var element : network.elements()) {
                    String id, nodeId;
                    if (element instanceof org.supply.solver.model.DiodeSubstationElement sub) {
                        id = sub.id(); nodeId = sub.feedingNodeId();
                    } else if (element instanceof org.supply.solver.model.ThyristorSubstationElement sub) {
                        id = sub.id(); nodeId = sub.feedingNodeId();
                    } else { continue; }
                    Set<String> terminals = new HashSet<>(List.of(nodeId));
                    boolean changed;
                    do {
                        changed = false;
                        for (var branch : network.branches()) {
                            if (!branch.id().startsWith("internal_")) { continue; }
                            if (terminals.contains(branch.fromNodeId())) changed |= terminals.add(branch.toNodeId());
                            if (terminals.contains(branch.toNodeId())) changed |= terminals.add(branch.fromNodeId());
                        }
                    } while (changed);
                    // Diagram coordinates must be railway coordinates from input, not model metres.
                    for (var node : grid.getNodes()) {
                        if (terminals.contains(node.getNodeId())) {
                            objects.add(new org.supply.track.TrackRouteDiagram.ObjectPoint("substation", id,
                                    org.supply.track.RwyCoordinateParser.parse(node.getPositionRwy())));
                        }
                    }
                }
            } else {
                warnings.add("Track band diagrams omit substations: calculation network unavailable");
            }
            try {
                org.supply.track.TrackRouteDiagram.write(track, selectedRouteId,
                        context.exportDirectory().getParent().resolve("checks"), detail, warnings, objects);
            } catch (IOException failure) {
                warnings.add("Track route diagram export failed: " + failure.getMessage());
            }
        }
        if (network != null && routes != null) {
            ElectricalRouteCheck.Result routeCheck =
                    ElectricalRouteCheck.inspect(network, routes, selectedRouteId);
            errors.addAll(routeCheck.errors());
            warnings.addAll(routeCheck.warnings());
            detail.append("Network: ").append(network.nodes().size()).append(" nodes, ")
                    .append(network.branches().size()).append(" branches\n");
            detail.append(routeCheck.topology());
            try {
                Map<String,Double> ohmPerM = new LinkedHashMap<>();
                for (var line : context.dcsim().getConfigList("grid.lines")) {
                    ohmPerM.put(line.getString("line_id"), line.getDouble("resistance_ohm_per_m"));
                }
                Map<String,org.supply.track.RwyCoordinate> railwayPositions=new LinkedHashMap<>();
                for(var node:grid.getNodes()) {
                    railwayPositions.put(node.getNodeId(),
                            org.supply.track.RwyCoordinateParser.parse(node.getPositionRwy()));
                }
                var schematicContext=new org.supply.solver.build.RouteSchematic.Context(
                        railwayPositions,track.getJunctions());
                ElectricalTopologyGraph.write(network, routes, selectedRouteId,
                        context.exportDirectory().getParent().resolve("checks"), detail, warnings, ohmPerM,schematicContext);
            } catch (IOException failure) {
                warnings.add("Topology graph export failed: " + failure.getMessage());
            }
        } else {
            detail.append("SKIP Electrical route topology: network or routes unavailable\n");
        }
        if (network != null && parameters != null && network.nodes().stream()
                .noneMatch(node -> Objects.equals(node.id(), parameters.referenceNodeId()))) {
            errors.add("Unknown system reference node: " + parameters.referenceNodeId());
        }

        Path temporary = Files.createTempDirectory("dc-input-check-");
        try {
            Path runCsv = stage("RunExcel traffic export and leg timing", errors, detail, () -> {
                new RunCsvWriter().write(context.dcsim(), context.confFile(), temporary);
                return temporary.resolve("run.csv");
            });
            if (runCsv != null) {
                List<RunSample> samples = stage("Generated traffic CSV", errors, detail,
                        () -> new RunSampleLoader().load(runCsv));
                if (samples != null) {
                    if (samples.isEmpty()) {
                        warnings.add("No train samples in the simulation window; train placement was not checked");
                    } else if (network != null && parameters != null && routes != null) {
                        checkPlacements(samples, network, transform, parameters, routes,
                                errors, detail);
                    } else {
                        detail.append("SKIP Train placement: network, routes or parameters unavailable\n");
                    }
                }
            }
        } finally {
            try {
                deleteTemporary(temporary);
            } catch (IOException failure) {
                warnings.add("Could not remove temporary check directory " + temporary
                        + ": " + failure.getMessage());
            }
        }

        StringBuilder report = new StringBuilder("DC INPUT CHECK\n");
        report.append("Study: ").append(context.studyId()).append('\n');
        report.append("Configuration: ").append(context.confFile()).append('\n');
        report.append("Status: ").append(errors.isEmpty() ? "PASS" : "FAIL")
                .append("; errors=").append(errors.size())
                .append("; warnings=").append(warnings.size()).append("\n\n");
        for (String error : errors) { report.append("ERROR ").append(error).append('\n'); }
        for (String warning : warnings) { report.append("WARNING ").append(warning).append('\n'); }
        report.append("\n").append(detail);
        report.append("\nScope: input parsing, existing loader validation, route adjacency and exported train placement.\n")
                .append("No timestep was solved. PASS does not guarantee convergence or power availability.\n")
                .append("Each input loader reports its first failure; later dependent checks may be skipped.\n")
                .append("Routes are checked for electrical adjacency, not a unique directed traversal.\n");

        Path checkDirectory = context.exportDirectory().getParent().resolve("checks");
        Files.createDirectories(checkDirectory);
        Path reportPath = checkDirectory.resolve(context.studyId() + "_input_check.txt");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        return new CheckResult(List.copyOf(errors), List.copyOf(warnings),
                report.toString(), reportPath);
    }

    private static void checkPlacements(List<RunSample> samples, CalculationNetwork network,
                                         TrackTransformService transform, SystemParameters parameters,
                                         List<Route> routes, List<String> errors, StringBuilder detail) {
        TrainPositionFactory positions = new TrainPositionFactory(transform);
        TrainNodeInserter inserter = new TrainNodeInserter(parameters, routes);
        Set<String> knownRoutes = new HashSet<>();
        for (Route route : routes) { knownRoutes.add(route.id()); }
        Set<PlacementKey> checked = new HashSet<>();
        Map<Double, Set<String>> trainIdsByTime = new HashMap<>();
        for (RunSample sample : samples) {
            if (!Double.isFinite(sample.timeS())) {
                errors.add("Traffic contains non-finite time_s: " + sample.timeS());
                continue;
            }
            List<CalculationTrainPosition> generated;
            try {
                generated = positions.fromRunSamples(List.of(sample));
            } catch (Exception failure) {
                errors.add("Traffic at time_s=" + sample.timeS() + ": " + message(failure));
                continue;
            }
            for (CalculationTrainPosition train : generated) {
                Set<String> trainIds = trainIdsByTime.computeIfAbsent(
                        sample.timeS(), ignored -> new HashSet<>());
                if (!trainIds.add(train.trainId())) {
                    errors.add("Duplicate train " + train.trainId() + " at time_s=" + sample.timeS());
                }
                if (!Double.isFinite(train.positionM()) || !Double.isFinite(train.pReqW().asDouble())) {
                    errors.add("Non-finite position or requested power for train " + train.trainId()
                            + " at time_s=" + sample.timeS());
                    continue;
                }
                PlacementKey key = new PlacementKey(train.routeId(), train.sectionId(),
                        train.trackId(), train.positionM());
                if (!checked.add(key)) { continue; }
                if (!routes.isEmpty() && !knownRoutes.contains(train.routeId())) {
                    errors.add("Train " + train.trainId() + ": unknown route " + train.routeId());
                    continue;
                }
                try {
                    inserter.insertTrainNodes(network, List.of(train));
                } catch (Exception failure) {
                    errors.add("Train placement at time_s=" + sample.timeS() + ": " + message(failure));
                }
            }
        }
        detail.append("Traffic: ").append(samples.size()).append(" samples; ")
                .append(checked.size()).append(" distinct route/track/position combinations checked\n");
    }

    private record PlacementKey(String routeId, String sectionId, String trackId, double positionM) { }

    private interface CheckedSupplier<T> { T get() throws Exception; }

    private static <T> T stage(String name, List<String> errors, StringBuilder detail,
                                CheckedSupplier<T> action) {
        try {
            T result = action.get();
            detail.append("PASS ").append(name).append('\n');
            return result;
        } catch (Exception failure) {
            errors.add(name + ": " + message(failure));
            detail.append("FAIL ").append(name).append('\n');
            return null;
        }
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void deleteTemporary(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
