package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigResolveOptions;
import org.dcsim.actors.SimulationSpeed;
import org.dcsim.config.PowerProfiles;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.NodeKind;
import org.dcsim.electric.Substation;
import org.dcsim.export.ScenarioMaterializer;
import org.dcsim.math.Real;
import org.dcsim.power.PointsPowerProfile;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;
import org.dcsim.sim.EdgeRef;
import org.dcsim.utils.PositionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DcSimScenarioLoader implements ScenarioLoader<Real> {

    @Override
    public SimulationInputModel<Real> load(Path confFile, Path outputRoot) throws IOException {
        try {
            confFile = confFile.toAbsolutePath().normalize();

            Config scenario = loadScenarioConfig(confFile);
            Config dcsim = requireDcsim(scenario, confFile);

            applyVerboseAllIfConfigured(scenario);
            logConfigSummary(dcsim, confFile);

            RunLayout layout = RunLayoutFactory.fromCliArgs(
                    confFile.toString(),
                    outputRoot != null ? outputRoot.toString() : null
            );

            Path runCsv = layout.exportDir().resolve("run.csv");

            Files.createDirectories(layout.resultsDir());
            Files.createDirectories(layout.exportDir());


            String projectId = layout.projectId();
            String scenarioId = layout.scenarioId();

            String hash = dcsim.hasPath("hash")
                    ? dcsim.getString("hash")
                    : System.getProperty("dcsim.hash", "no-hash");

            boolean exportEnabled = dcsim.hasPath("export.enabled") && dcsim.getBoolean("export.enabled");

            if (exportEnabled) {
                String trainId = dcsim.getString("exportTrainId");
                Path runExcelRaw = Paths.get(dcsim.getString("exportRunExcel"));
                Path runExcel;

                if (runExcelRaw.isAbsolute()) {
                    runExcel = runExcelRaw.normalize();
                } else {
                    Path cwd = Paths.get("").toAbsolutePath().normalize();
                    String s = runExcelRaw.toString().replace('\\', '/');

                    if (s.startsWith("project/") || s.startsWith("output/")) {
                        runExcel = cwd.resolve(runExcelRaw).normalize();
                    } else {
                        runExcel = layout.inputDir().resolve(runExcelRaw).normalize();
                    }
                }

                if (!Files.exists(runExcel)) {
                    throw new RuntimeException("Export failed: runExcel not found: " + runExcel);
                }

                ScenarioMaterializer.materializeScenario(
                        confFile,
                        layout.exportDir(),
                        runExcel,
                        trainId
                );

                System.out.println("CSV inputs exported to: " + layout.exportDir().toAbsolutePath());

                return SimulationInputModel.<Real>builder()
                        .projectId(projectId)
                        .scenarioId(scenarioId)
                        .configFile(confFile)
                        .inputDir(layout.inputDir())
                        .outputRoot(layout.outputRoot())
                        .exportDir(layout.exportDir())
                        .resultsDir(layout.resultsDir())
                        .reportsDir(layout.outputRoot().resolve("reports"))
                        .runCsv(runCsv)
                        .exportOnly(true)
                        .build();
            }

            String effectiveDcsim = "dcsim " + dcsim.root().render(
                    ConfigRenderOptions.defaults()
                            .setComments(false)
                            .setOriginComments(false)
                            .setFormatted(true)
                            .setJson(false)
            );

            Files.writeString(layout.resultsDir().resolve("effective_dcsim.conf"), effectiveDcsim);
            String effectiveMd = "# Effective dcsim config\n\n```hocon\n" + effectiveDcsim + "\n```\n";
            Files.writeString(layout.resultsDir().resolve("effective_dcsim.md"), effectiveMd);

            Config sim = dcsim.getConfig("simulationControl");
            double tickSec = sim.hasPath("tickDurationSec") ? sim.getDouble("tickDurationSec")
                    : sim.getDouble("tickDuration");
            int startSec = sim.hasPath("simulationStart") ? parseHmsToSeconds(sim.getString("simulationStart")) : 0;
            int endSec = sim.hasPath("simulationEnd") ? parseHmsToSeconds(sim.getString("simulationEnd"))
                    : Integer.MAX_VALUE;
            SimulationSpeed speed = sim.hasPath("simulationSpeed")
                    ? SimulationSpeed.valueOf(sim.getString("simulationSpeed").trim().toUpperCase(Locale.ROOT))
                    : SimulationSpeed.FAST;
            int stopAfterSteps = sim.hasPath("stopAfterSteps") ? sim.getInt("stopAfterSteps") : -1;

            GridModel<Real> model = GridModelLoader.load(dcsim);

            List<TrackPoint> trackPoints = buildTrackPoints(model);

            if (model == null) {
                throw new RuntimeException("GridModelLoader returned null — check grid configuration");
            }
            var extentByTrack = ContractChecks.extentByTrackFromModel(model);

            System.out.println("=== MODEL EXTENTS ===");
            extentByTrack.forEach((track, ext) ->
                    System.out.println("track=" + track + " extent=[" + ext.minM() + "," + ext.maxM() + "]")
            );

            System.out.println("=== LINES ===");
            model.getLines().forEach(line ->
                    System.out.println(
                            "track=" + line.getId()
                                    + " length=" + ((Line) line).getLength()
                    )
            );

            int anchorNodeId = dcsim.hasPath("grid.anchorNodeId")
                    ? dcsim.getInt("grid.anchorNodeId")
                    : firstNonGroundNonBus(model);

            Node<Real> anchor = model.getNodeById(anchorNodeId);
            anchor.setNodeKind(NodeKind.TRAIN);

            Map<String, List<PowerPoint>> byTpl = dcsim.hasPath("powerProfiles")
                    ? PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"))
                    : Collections.emptyMap();

            Map<String, List<PowerPoint>> byTplNormalised = new LinkedHashMap<>();
            for (var e : byTpl.entrySet()) {
                final String tplId = e.getKey();
                final List<PowerPoint> src = e.getValue();
                final List<PowerPoint> dst = new ArrayList<>(src.size());

                for (PowerPoint p : src) {
                    String posStr = p.positionString();
                    double posM = p.positionM();

                    System.out.println("TPL=" + tplId + " RAW posM=" + posM + " posStr=[" + posStr + "]");

                    if (Double.isNaN(posM) && posStr != null && !posStr.isBlank()) {
                        try {
                            posM = Double.parseDouble(posStr.trim().replace(',', '.'));
                        } catch (NumberFormatException ex) {
                            var tp = PositionUtils.parseFlexibleTP(posStr).normalized();
                            posM = Math.floor(tp.metersInLine());
                        }
                    }

                    if (!Double.isNaN(posM)) {
                        posM = interpolateAbsolutePositionM(posM, trackPoints);
                    }

                    System.out.println("PROJECTED posM=" + posM);

                    dst.add(p.withPositionM(posM));
                }
                ContractChecks.validateRunPointsAgainstModelExtent(dst, extentByTrack);
                byTplNormalised.put(tplId, dst);
            }

            Map<String, PowerProfile> profileByTemplate = new HashMap<>();
            for (var e : byTplNormalised.entrySet()) {
                profileByTemplate.put(e.getKey(), new PointsPowerProfile(e.getValue()));
            }

            boolean sameModel = dcsim.hasPath("powerProfiles.motoringAndAuxiliariesInSameModel")
                    && dcsim.getBoolean("powerProfiles.motoringAndAuxiliariesInSameModel");
            double auxKW = dcsim.hasPath("powerProfiles.auxiliaryPower")
                    ? dcsim.getDouble("powerProfiles.auxiliaryPower") : 0.0;

            List<SimulationInputModel.TrainSpawn> spawns = new ArrayList<>();
            if (dcsim.hasPath("traffic")) {
                var timetable = dcsim.getConfig("traffic.timetable");
                var trainsConf = timetable.getConfigList("trains");

                for (Config tr : trainsConf) {
                    String idBase = tr.getString("id");
                    String tpl = tr.getString("templateId");
                    int dep0Abs = parseHmsToSeconds(tr.getString("departure"));
                    Integer headway = optHmsToSeconds(tr, "headway");
                    int count = tr.hasPath("count") ? tr.getInt("count") : 1;
                    String sig = tr.hasPath("signature") ? tr.getString("signature") : "";

                    PowerProfile prof = profileByTemplate.get(tpl);

                    for (int k = 0; k < count; k++) {
                        int depKAbs = dep0Abs + ((headway != null) ? k * headway : 0);
                        int depRel = Math.max(0, depKAbs - startSec);
                        String tid = (count == 1) ? idBase : uniqId(idBase, sig, k + 1);

                        spawns.add(new SimulationInputModel.TrainSpawn(
                                tid, prof, depRel, sameModel, auxKW
                        ));
                    }
                }
            }

            List<EdgeRef> raw = new ArrayList<>();
            for (Object o : (Collection<?>) model.getDevices()) {
                Device<?> d = (Device<?>) o;
                if (d instanceof Line ln) {
                    raw.add(new EdgeRef(
                            ln.getFromNode(),
                            ln.getToNode(),
                            ln.getResistance().asDouble(),
                            ln.getLength()
                    ));
                }
            }

            List<EdgeRef> path = linearizePath(raw, findPathStart(raw));

            double vMS = readSpeedMS(dcsim);
            double pW = dcsim.hasPath("train.pW") ? dcsim.getDouble("train.pW") : 150_000.0;
            double rMin = dcsim.hasPath("train.Rmin") ? dcsim.getDouble("train.Rmin") : 1e-6;
            double epsFrac = dcsim.hasPath("train.epsFrac") ? dcsim.getDouble("train.epsFrac") : 1e-3;

            String longTablePath = layout.resultsDir().resolve("longtable.csv").toString();

            String baseName = confFile.getFileName().toString();
            int dot = baseName.lastIndexOf('.');
            String testName = (dot > 0 ? baseName.substring(0, dot) : baseName);
            String solverCsvPath = layout.resultsDir().resolve("electrical_" + testName + ".csv").toString();

            return SimulationInputModel.<Real>builder()
                    .projectId(projectId)
                    .scenarioId(scenarioId)
                    .configFile(confFile)
                    .inputDir(layout.inputDir())
                    .outputRoot(layout.outputRoot())
                    .exportDir(layout.exportDir())
                    .resultsDir(layout.resultsDir())
                    .reportsDir(layout.outputRoot().resolve("reports"))
                    .startSec(startSec)
                    .endSec(endSec)
                    .tickSec(tickSec)
                    .simulationSpeed(speed)
                    .stopAfterSteps(stopAfterSteps)
                    .gridModel(model)
                    .runCsv(runCsv)
                    .powerProfiles(new PowerProfiles())
                    .spawns(spawns)
                    .exportOnly(false)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load simulation input", e);
        }
    }

    // --- utility methods from AppRunner ---

    private static List<TrackPoint> buildTrackPoints(GridModel<Real> model) {
        List<TrackPoint> pts = new ArrayList<>();

        double acc = 0.0;

        for (Device<Real> device : model.getLines()) {
            Line line = (Line) device;
            double len = line.getLength();

            // startpunkt
            pts.add(new TrackPoint(acc, (int) (acc / 1000), acc % 1000));

            acc += len;

            // endpoint
            pts.add(new TrackPoint(acc, (int) (acc / 1000), acc % 1000));
        }

        return pts;
    }

    private static double interpolateAbsolutePositionM(
            double runPosM,
            List<TrackPoint> trackPoints
    ) {
        if (trackPoints == null || trackPoints.isEmpty()) {
            throw new IllegalArgumentException("trackPoints is empty");
        }

        // Clamp on boundaries
        if (runPosM <= trackPoints.get(0).positionM) {
            return trackPoints.get(0).bisKm * 1000.0 + trackPoints.get(0).bisMeter;
        }
        if (runPosM >= trackPoints.get(trackPoints.size() - 1).positionM) {
            TrackPoint last = trackPoints.get(trackPoints.size() - 1);
            return last.bisKm * 1000.0 + last.bisMeter;
        }

        for (int i = 0; i < trackPoints.size() - 1; i++) {
            TrackPoint a = trackPoints.get(i);
            TrackPoint b = trackPoints.get(i + 1);

            if (runPosM >= a.positionM && runPosM <= b.positionM) {
                double span = b.positionM - a.positionM;
                if (span <= 0.0) {
                    return a.bisKm * 1000.0 + a.bisMeter;
                }

                double t = (runPosM - a.positionM) / span;

                double aAbsM = a.bisKm * 1000.0 + a.bisMeter;
                double bAbsM = b.bisKm * 1000.0 + b.bisMeter;

                return aAbsM + t * (bAbsM - aAbsM);
            }
        }

        throw new IllegalStateException("Could not interpolate runPosM=" + runPosM);
    }

    private static final class TrackPoint {
        final double positionM; // track.position [m]
        final int bisKm;        // track.bisKm
        final double bisMeter;  // track.bisMeter

        TrackPoint(double positionM, int bisKm, double bisMeter) {
            this.positionM = positionM;
            this.bisKm = bisKm;
            this.bisMeter = bisMeter;
        }
    }

    private static int parseHmsToSeconds(String s) {
        String[] p = s.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        int sec = (p.length == 3) ? Integer.parseInt(p[2]) : 0;
        return h * 3600 + m * 60 + sec;
    }

    private static Integer optHmsToSeconds(Config cfg, String key) {
        return cfg.hasPath(key) ? parseHmsToSeconds(cfg.getString(key)) : null;
    }

    private static String uniqId(String base, String signature, int ord) {
        if (signature != null && !signature.isBlank()) return base + "_" + signature + ord;
        return base + "_" + ord;
    }

    private static int findPathStart(List<EdgeRef> edges) {
        Set<Integer> froms = new HashSet<>(), tos = new HashSet<>();
        for (EdgeRef e : edges) {
            froms.add(e.i);
            tos.add(e.j);
        }
        for (int f : froms) if (!tos.contains(f)) return f;
        return edges.isEmpty() ? 0 : edges.get(0).i;
    }

    private static List<EdgeRef> linearizePath(List<EdgeRef> edges, int startNodeId) {
        List<EdgeRef> remaining = new ArrayList<>(edges);
        List<EdgeRef> out = new ArrayList<>();
        int cur = startNodeId;

        while (!remaining.isEmpty()) {
            int hit = -1;
            boolean flip = false;
            for (int k = 0; k < remaining.size(); k++) {
                EdgeRef e = remaining.get(k);
                if (e.i == cur) {
                    hit = k;
                    flip = false;
                    break;
                }
                if (e.j == cur) {
                    hit = k;
                    flip = true;
                    break;
                }
            }
            if (hit < 0) throw new IllegalStateException("Path not contiguous from node " + cur);

            EdgeRef e = remaining.remove(hit);
            if (flip) e = new EdgeRef(e.j, e.i, e.R, e.lengthM);
            out.add(e);
            cur = e.j;
        }
        return out;
    }

    private static double readSpeedMS(Config dcsim) {
        if (dcsim.hasPath("powerProfiles.templates")) {
            var list = dcsim.getConfig("powerProfiles").getConfigList("templates");
            for (Config tpl : list) {
                if (tpl.hasPath("speedMS")) return tpl.getDouble("speedMS");
                if (tpl.hasPath("speedKPH")) return tpl.getDouble("speedKPH") / 3.6;
            }
        }
        if (dcsim.hasPath("train.vMS")) return dcsim.getDouble("train.vMS");
        return 25.0;
    }

    private static int firstNonGroundNonBus(GridModel<Real> m) {
        int g = m.getGroundNodeId();
        Set<Integer> bus = new HashSet<>();
        for (Object did : m.getDeviceIds()) {
            Device<Real> d = m.getDevice(String.valueOf(did));
            if (d instanceof Substation ss) bus.add(ss.getFromNode());
        }
        for (Object o : m.getNodeIds()) {
            int id = (o instanceof Integer) ? (Integer) o : Integer.parseInt(o.toString());
            if (id != g && !bus.contains(id)) return id;
        }
        return g;
    }

    /**
     * Loads and resolves the full scenario config (with fallback to classpath).
     */
    public static Config loadScenarioConfig(Path confFile) {
        ConfigParseOptions parseOpts = ConfigParseOptions.defaults().setAllowMissing(false);
        ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setAllowUnresolved(false)
                .setUseSystemEnvironment(true);

        return ConfigFactory
                .parseFileAnySyntax(confFile.toFile(), parseOpts)
                .withFallback(ConfigFactory.load())
                .resolve(resolveOpts);
    }

    /**
     * Convenience: returns the dcsim sub-config (and fails with clear error if missing).
     */
    public static Config requireDcsim(Config scenario, Path confFile) {
        if (!scenario.hasPath("dcsim")) {
            throw new IllegalArgumentException("Top-level 'dcsim' section is missing in: " + confFile.toAbsolutePath());
        }
        return scenario.getConfig("dcsim");
    }

    /**
     * Accepts a file or directory (dir -> scenario.conf). Mirrors DcSimApp behavior.
     */
    public static Path resolveConfArg(String arg) {

        Path conf = Paths.get(arg);

        if (!conf.toString().endsWith(".conf") && !conf.toString().endsWith(".hocon")) {
            throw new IllegalArgumentException("Config must be a .conf or .hocon file: " + conf);
        }

        // resolve relative paths against CWD
        if (!conf.isAbsolute()) {
            conf = Paths.get("").toAbsolutePath().resolve(conf);
        }

        conf = conf.normalize();

        if (!Files.exists(conf)) {
            throw new IllegalArgumentException("Config not found: " + conf);
        }

        if (!Files.isRegularFile(conf)) {
            throw new IllegalArgumentException("Config is not a file: " + conf);
        }

        return conf;
    }

    public static void applyVerboseAllIfConfigured(Config scenario) {
        boolean verboseAll = false;
        try {
            if (scenario.hasPath("dcsim.verbose.all")) {
                verboseAll = scenario.getBoolean("dcsim.verbose.all");
            } else if (scenario.hasPath("dcsim.verbose") && scenario.getConfig("dcsim.verbose").hasPath("all")) {
                verboseAll = scenario.getConfig("dcsim.verbose").getBoolean("all");
            }
        } catch (Exception ignore) {
        }

        if (verboseAll) {
            System.setProperty("dcsim.verbose.all", "true");
            System.out.println("[CONF] Verbose mode enabled (dcsim.verbose.all=true)");
        }
    }

    public static void logConfigSummary(Config dcsim, Path confFile) {
        // ---- 4) Logga vad som faktiskt lästes (bra vid “<MISSING>") ----
        ConfigRenderOptions compactPretty = ConfigRenderOptions.concise().setFormatted(true).setJson(false);
        System.out.println("[CONF] Loaded from: " + confFile.toAbsolutePath());
        System.out.println("[CONF] dcsim.trains.defaults = " +
                (dcsim.hasPath("trains.defaults")
                        ? dcsim.getConfig("trains.defaults").root().render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.trains.overrides = " +
                (dcsim.hasPath("trains.overrides")
                        ? dcsim.getConfig("trains.overrides").root().render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.grid.substations = " +
                (dcsim.hasPath("grid.substations")
                        ? dcsim.getConfig("grid").getList("substations").render(compactPretty)
                        : "<MISSING>"));
        System.out.println("[CONF] dcsim.export = " +
                (dcsim.hasPath("export")
                        ? dcsim.getConfig("export").root().render(compactPretty)
                        : "<MISSING>"));

    }

}
