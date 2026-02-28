package org.dcsim;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.dcsim.actors.GridModelActor;
import org.dcsim.actors.SimulationSpeed;
import org.dcsim.actors.TrainActor;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.DcIterativeAdapterSolver;
import org.dcsim.electric.Device;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.NodeKind;
import org.dcsim.electric.Substation;
import org.dcsim.export.LongFiles;
import org.dcsim.export.LongTableWriter;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.export.ScenarioMaterializer;
import org.dcsim.math.Real;
import org.dcsim.power.PointsPowerProfile;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;
import org.dcsim.sim.EdgeRef;
import org.dcsim.sim.TrainAnchorComponent;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.dcsim.utils.PositionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.dcsim.DcSimApp.Root.resolveInputPath;

public final class DcSimApp {

    private static LongTableWriter LONG_WRITER = null;
    static final String projectName = "dc-simulator";
    static final String scenarioName = "3subs1train";
    static final String baseHash = "8110f7deeaa232a8602fcccbd55f512cebdfc7af"; // TODO: replace with dynamic git hash
    static final String longPath = "output/longtable.csv";


    // ----- Root actor that wires everything with backpressure -----
    public static final class Root extends AbstractBehavior<Root.Command> {
        interface Command {
        }

        private static final class Kick implements Command {
        }

        private static final class FromGrid implements Command {
            final GridModelActor.SolveReply reply;

            FromGrid(GridModelActor.SolveReply r) {
                this.reply = r;
            }
        }

        private final double tickSec;
        private final double endAtSec;
        private double nowSec;
        private long step;

        private final SimulationSpeed speed;
        private final ActorRef<GridModelActor.Command> grid;
        private final List<ActorRef<TrainActor.Command>> trains = new ArrayList<>();
        private final ActorRef<GridModelActor.SolveReply> gridReplyAdapter;
        private final long wallStartNanos;

        // --- Ankare: installeras dynamiskt vid avgång ---
        private final Map<String, TrainAnchorComponent> trainComps = new LinkedHashMap<>();
        private final Map<String, Double> sByTrain = new HashMap<>();
        private final Map<String, TrainSpawn> pending = new LinkedHashMap<>();

        // --- Parametrar som återanvänds för varje ankarinstans ---
        private final int anchorNodeId;
        private final List<EdgeRef> path;
        private final double vMS, pW, Rmin, epsFrac;

        private static Path repoRootFromConfFile(Path confFile) {
            // Finds ".../<repoRoot>/project/..." and returns <repoRoot>
            Path abs = confFile.toAbsolutePath().normalize();
            Path p = abs.getParent(); // directory of the conf file
            while (p != null) {
                Path name = p.getFileName();
                if (name != null && name.toString().equalsIgnoreCase("project")) {
                    Path repoRoot = p.getParent();
                    if (repoRoot != null) return repoRoot;
                }
                p = p.getParent();
            }
            return null;
        }

        public static Path resolveInputPath(Path confFile, String rawStr) {
            Path raw = Paths.get(rawStr);
            if (raw.isAbsolute()) return raw.normalize();

            Path confDir = confFile.toAbsolutePath().normalize().getParent();
            if (confDir == null) confDir = Paths.get(".").toAbsolutePath().normalize();

            // 1) primary rule: relative to conf dir
            Path c1 = confDir.resolve(raw).normalize();
            if (Files.exists(c1)) return c1;

            // 2) legacy: allow "project/..." relative to repo root
            String rawNorm = rawStr.replace('\\', '/');
            if (rawNorm.startsWith("project/") || rawNorm.startsWith("./project/")) {
                Path repoRoot = repoRootFromConfFile(confFile);
                if (repoRoot != null) {
                    Path c2 = repoRoot.resolve(raw).normalize();
                    if (Files.exists(c2)) return c2;
                }
            }

            // fallback: confDir resolution (even if it doesn't exist)
            return c1;
        }
        public static Behavior<Command> create(
                GridModel<Real> model,
                ElectricSolver solver,
                String csvOut,
                double tickSec,
                int startSec,
                int endSec,
                List<TrainSpawn> trainSpawns,
                SimulationSpeed speed,
                int stopAfterSteps,
                int anchorNodeId,
                List<EdgeRef> path,
                double vMS, double pW, double Rmin, double epsFrac
        ) {
            return Behaviors.setup(ctx ->
                    new Root(ctx, model, solver, csvOut, tickSec, startSec, endSec,
                            trainSpawns, speed, stopAfterSteps, anchorNodeId,
                            path, vMS, pW, Rmin, epsFrac)
            );
        }

        // Back-compat överlagring så gammal kod fortfarande kompilerar
        public static Behavior<Command> create(
                GridModel<Real> model,
                DcElectricSolver legacySolver,      // konkret klass
                String outPath,
                double tickSec,
                int startSec,
                int endSec,
                List<TrainSpawn> spawns,
                SimulationSpeed speed,
                int stopAfterSteps,
                int anchorNodeId,
                List<EdgeRef> path,
                double vMS, double pW, double Rmin, double epsFrac
        ) {
            return create(model, (ElectricSolver) legacySolver, outPath, tickSec, startSec, endSec,
                    spawns, speed, stopAfterSteps, anchorNodeId, path, vMS, pW, Rmin, epsFrac);
        }

        private Root(
                ActorContext<Command> ctx,
                GridModel<Real> model,
                ElectricSolver solver,
                String csvOut,
                double tickSec,
                int startSec,
                int endSec,
                List<TrainSpawn> trainSpawns,
                SimulationSpeed speed,
                int stopAfterSteps,
                int anchorNodeId,
                List<EdgeRef> path,
                double vMS, double pW, double Rmin, double epsFrac
        ) {
            super(ctx);
            this.tickSec = tickSec;
            this.nowSec = startSec;
            this.step = 0;
            this.speed = speed;

            this.anchorNodeId = anchorNodeId;
            this.path = path;
            this.vMS = vMS;
            this.pW = pW;
            this.Rmin = Rmin;
            this.epsFrac = epsFrac;

            double stopByTime = (endSec <= 0 ? Double.POSITIVE_INFINITY : endSec);
            double stopBySteps = (stopAfterSteps > 0) ? (startSec + stopAfterSteps * tickSec)
                    : Double.POSITIVE_INFINITY;
            this.endAtSec = Math.min(stopByTime, stopBySteps);

            // Grid actor
            this.grid = ctx.spawn(GridModelActor.create(model, solver, csvOut, anchorNodeId), "grid");
            System.out.println("[GMA] anchorNodeId=" + anchorNodeId
                    + " label=" + model.getNodeById(anchorNodeId).getPosition());


            // Train actors — men ankare installeras inte här!
            for (var ts : trainSpawns) {
                var tr = ctx.spawn(
                        TrainActor.create(ts.trainId, grid, ts.profile, ts.departureSec, ts.sameModel, ts.auxKW),
                        "train_" + ts.trainId
                );
                trains.add(tr);
                pending.put(ts.trainId, ts);
            }

            this.gridReplyAdapter = ctx.messageAdapter(GridModelActor.SolveReply.class, FromGrid::new);
            this.wallStartNanos = System.nanoTime();

            ctx.scheduleOnce(java.time.Duration.ofMillis(1), ctx.getSelf(), new Kick());
            ctx.getLog().info("Simulation started ({}), dt={} s.", speed, tickSec);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Kick.class, k -> onKick())
                    .onMessage(FromGrid.class, this::onFromGrid)
                    .build();
        }

        private Behavior<Command> onKick() {
            if (nowSec >= endAtSec - 1e-9) {
                grid.tell(new GridModelActor.SimulationFinished());
                return Behaviors.stopped();
            }

            final double t = nowSec;
            final int s = (int) step;

            // 0) Installera nya ankare vars avgångstid passerats
            if (!pending.isEmpty()) {
                var it = pending.entrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    var ts = e.getValue();
                    if (t + 1e-9 >= ts.departureSec) {
                        var comp = new TrainAnchorComponent(anchorNodeId, path, vMS, pW, Rmin, epsFrac);
                        trainComps.put(ts.trainId, comp);
                        sByTrain.put(ts.trainId, 0.0);
                        grid.tell(new GridModelActor.InstallTrainAnchor(ts.trainId, comp, this.tickSec));
                        getContext().getLog().info("Installed anchor for {} at t={}s (dep={})",
                                ts.trainId, String.format(java.util.Locale.ROOT, "%.1f", t), ts.departureSec);
                        it.remove();
                    }
                }
            }

            // 1) trafik (tick) — profil-styrning m.m. ligger i TrainActor
            for (var tr : trains) tr.tell(new TrainActor.Tick(t));

            // 2) flytta enbart installerade ankare
            for (var e : trainComps.entrySet()) {
                String id = e.getKey();
                var comp = e.getValue();
                double v = comp.getSpeedMS();
                double sM = sByTrain.getOrDefault(id, 0.0) + v * tickSec;
                sByTrain.put(id, sM);
                comp.setAbsoluteProgressM(sM);
            }

            // 3) lös steget
            grid.tell(new GridModelActor.SolveTick(t, s, gridReplyAdapter));
            return this;
        }

        private Behavior<Command> onFromGrid(FromGrid fg) {
            if (fg.reply instanceof GridModelActor.Solved) {
                nowSec = nowSec + tickSec;
                step = step + 1;

                if (nowSec >= endAtSec - 1e-9) {
                    grid.tell(new GridModelActor.SimulationFinished());
                    return Behaviors.stopped();
                }

                if (speed == SimulationSpeed.REAL_TIME) {
                    long elapsed = System.nanoTime() - wallStartNanos;
                    long shouldElapsed = (long) Math.round(nowSec * 1_000_000_000.0);
                    long delayNanos = Math.max(0L, shouldElapsed - elapsed);
                    getContext().scheduleOnce(java.time.Duration.ofNanos(delayNanos), getContext().getSelf(), new Kick());
                } else {
                    getContext().scheduleOnce(java.time.Duration.ZERO, getContext().getSelf(), new Kick());
                }
            }
            return this;
        }
    }

    private record TrainSpawn(String trainId, PowerProfile profile, int departureSec,
                              boolean sameModel, double auxKW) {
    }

    public static void main(String[] args) throws IOException {
        System.out.println("[MAIN] DcSimApp starting");

        Path confFile = DcSimScenarioLoader.resolveConfArg(args[0]);
        Config scenario = DcSimScenarioLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, confFile);

        DcSimScenarioLoader.applyVerboseAllIfConfigured(scenario);
        DcSimScenarioLoader.logConfigSummary(dcsim, confFile);

        // ---- Resolve deterministic output roots (independent of working directory) ----
        DcSimPaths.OutputRoots roots = DcSimPaths.resolveOutputs(dcsim, confFile);

        // ---- Write “effective config" to disk (.conf and .md) ----
        Path outDir = roots.effectiveDir();
        java.nio.file.Files.createDirectories(outDir);

        String effectiveDcsim = "dcsim " + dcsim.root().render(
                ConfigRenderOptions.defaults()
                        .setComments(false)
                        .setOriginComments(false)
                        .setFormatted(true)
                        .setJson(false)
        );

        LongFiles.bootstrapFromConfig();

        // Prefer config-derived ids (deterministic), fallback to system properties if you still need them
        final String project = dcsim.hasPath("project")
                ? dcsim.getString("project")
                : System.getProperty("dcsim.project", "no-project");

        final String scenarioId = dcsim.hasPath("scenario")
                ? dcsim.getString("scenario")
                : System.getProperty("dcsim.scenario", "no-scenario");

        final String hash = dcsim.hasPath("hash")
                ? dcsim.getString("hash")
                : System.getProperty("dcsim.hash", "no-hash");

        // longtable path is deterministic under results/<project>/<scenario>/ unless overridden
        final String csvPath = roots.longtablePath().toString();

        // overwrite=true is typical for a fresh run; switch to false if you want to append
        final boolean overwrite = true;

        // ---- Export inputs mode (legacy: dcsim.exportInputs) OR optional new flag dcsim.export.enabled ----
        boolean exportEnabled = dcsim.hasPath("exportInputs");
        if (!exportEnabled && dcsim.hasPath("export.enabled")) {
            exportEnabled = dcsim.getBoolean("export.enabled");
        }

        if (exportEnabled) {
            String trainId = dcsim.getString("exportTrainId");

            Path confDir = confFile.toAbsolutePath().normalize().getParent();
            if (confDir == null) confDir = Paths.get(".").toAbsolutePath().normalize();

            Path runExcelRaw = Paths.get(dcsim.getString("exportRunExcel"));
            Path runExcel = resolveInputPath(confFile, scenario.getString("dcsim.exportRunExcel"));
            if (!Files.exists(runExcel)) {
                throw new RuntimeException("Export failed: " + runExcel);
            }
            Path exportDir = roots.exportDir();
            java.nio.file.Files.createDirectories(exportDir);

            try {
                ScenarioMaterializer.materializeScenario(
                        confFile,
                        exportDir,
                        runExcel,
                        trainId
                );
            } catch (Exception e) {
                throw new RuntimeException("Export failed: " + e.getMessage(), e);
            }

            System.out.println("CSV inputs exported to: " + exportDir.toAbsolutePath());
            return; // stop here, don't run solver
        }

        LongTableWriter longWriter = new LongTableWriter(csvPath, overwrite, project, scenarioId, hash);
        GridModelActor.setLongWriter(longWriter);
        TrainActor.setLongWriter(longWriter);

        java.nio.file.Files.writeString(outDir.resolve("effective_dcsim.conf"), effectiveDcsim);
        String effectiveMd = "# Effective dcsim config\n\n```hocon\n" + effectiveDcsim + "\n```\n";
        java.nio.file.Files.writeString(outDir.resolve("effective_dcsim.md"), effectiveMd);
        System.out.println("[CONF] Wrote effective config to output/effective_dcsim.conf and .md");

        // ---- 6) Plocka sim-parametrar ----
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

        // ---- 7) Bygg modellen från dcsim-config ----
        GridModel<Real> model = GridModelLoader.load(dcsim);
        // Note: validateGridModel happens inside GridModelLoader.load(dcsim)

        // Build track extents from model nodes
        var extentByTrack = ContractChecks.extentByTrackFromModel(model);

        int anchorNodeId = dcsim.hasPath("grid.anchorNodeId")
                ? dcsim.getInt("grid.anchorNodeId")
                : firstNonGroundNonBus(model);

        Node<Real> anchor = model.getNodeById(anchorNodeId);
        anchor.setNodeKind(NodeKind.TRAIN);

        // ---- 8) Power-profiler ----
        Map<String, List<PowerPoint>> byTpl = dcsim.hasPath("powerProfiles")
                ? PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"))
                : Collections.emptyMap();
        Map<String, PowerProfile> profileByTemplate = new HashMap<>();

        // --- Normaliseringspass: byTpl -> byTplNormalised ---
        // - positionM sätts via PositionUtils.parseFlexible(bisPosition)
        // - speedMS måste finnas; annars kastas IllegalStateException
        Map<String, List<PowerPoint>> byTplNormalised = new LinkedHashMap<>();

        for (var e : byTpl.entrySet()) {
            final String tplId = e.getKey();
            final List<PowerPoint> src = e.getValue();
            final List<PowerPoint> dst = new ArrayList<>(src.size());

            for (int i = 0; i < src.size(); i++) {
                PowerPoint p = src.get(i);

                // 1) positionM via parseFlexible (vi antar att den returnerar int[]{section, km, m})
                // Läs position
                String posStr = p.positionString();   // "" om okänd
                double posM   = p.positionM();        // NaN om okänd

                // Om meters saknas men vi har en BIS-sträng → räkna fram lokalt
                if (Double.isNaN(posM) && posStr != null && !posStr.isBlank()) {
                    int[] skm = PositionUtils.parseFlexible(posStr); // er util
                    if (skm == null || skm.length < 3)
                        throw new IllegalStateException("Kunde inte tolka bisPosition \"" + posStr + "\"");
                    int km = skm[1], mInt = skm[2];
                    posM = km * 1000.0 + Math.floor(mInt); // trunkera meter-delen
                }
                // Om meters saknas men vi har en BIS-sträng → räkna fram lokalt (absolute meters, truncate)
                if (Double.isNaN(posM) && posStr != null && !posStr.isBlank()) {
                    var tp = PositionUtils.parseFlexibleTP(posStr).normalized();
                    posM = Math.floor(tp.metersInLine()); // truncate
                }

                dst.add(p.withPositionM(posM));
            }

            ContractChecks.validateRunPointsAgainstModelExtent(dst, extentByTrack);

            byTplNormalised.put(tplId, dst);
        }

        // använd den normaliserade mappen för profiler
        for (var e : byTplNormalised.entrySet()) {
            profileByTemplate.put(e.getKey(), new PointsPowerProfile(e.getValue()));
        }
        boolean sameModel = dcsim.hasPath("powerProfiles.motoringAndAuxiliariesInSameModel")
                && dcsim.getBoolean("powerProfiles.motoringAndAuxiliariesInSameModel");
        double auxKW = dcsim.hasPath("powerProfiles.auxiliaryPower")
                ? dcsim.getDouble("powerProfiles.auxiliaryPower") : 0.0;

        System.out.println("=== NODE METADATA AT START ===");
        for (Node<Real> n : model.getNodes()) {
            System.out.println("[NODE0] id=" + n.getId()
                    + " label=\"" + n.getPosition() + "\""
                    + " kind=" + n.getNodeKind()
                    + " trackId=" + n.getTrackId()
                    + " posM=" + n.getPositionM());
        }
        System.out.println("=== END NODE METADATA ===");

        // ---- 9) Traffic → Train spawns ----
        List<TrainSpawn> spawns = new ArrayList<>();
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
                    spawns.add(new TrainSpawn(tid, prof, depRel, sameModel, auxKW));
                }
            }
        }

        // ---- 10) Solver + CSV path ----
//        DcElectricSolver solver = new DcElectricSolver();
        ElectricSolver solver = new DcIterativeAdapterSolver();
        System.out.println("[CONF] Using ElectricSolver: " + solver.getClass().getName());

        // robust filbaserat namn (Windows/Unix)
        String baseName = confFile.getFileName().toString();

        int dot = baseName.lastIndexOf('.');
        String testName = (dot > 0 ? baseName.substring(0, dot) : baseName);

        String outPath = "output/electrical_" + testName + ".csv";
        new File(outPath).getParentFile().mkdirs();
        try (ResultCsvWriter ignored = new ResultCsvWriter(model, outPath, true)) { /* truncate */ } catch (
                IOException e) {
            System.err.println("[WARN] Could not prepare CSV file: " + e.getMessage());
        }

        LONG_WRITER = new LongTableWriter(longPath, true, projectName, scenarioName, baseHash);
        System.out.println("[LongCSV] writing to " + longPath);


        // Registrera i solvern (statiskt)
        DcIterativeSolver.setLongWriter(LONG_WRITER);

        // ---- 11) Bygg path från modellen (linjer) ----
        List<EdgeRef> raw = new ArrayList<>();
        for (Object o : (java.util.Collection<?>) model.getDevices()) {
            Device<?> d = (Device<?>) o;
            if (d instanceof Line ln) {
                int from = ln.getFromNode();
                int to = ln.getToNode();
                double Lm = ln.getLength();
                double R = ln.getResistance().asDouble();
                raw.add(new EdgeRef(from, to, R, Lm));
            }
        }
        List<EdgeRef> path = linearizePath(raw, findPathStart(raw));

        // ---- 12) Ankare-parametrar (från conf) ----
        double vMS = readSpeedMS(dcsim);
        double pW = dcsim.hasPath("train.pW") ? dcsim.getDouble("train.pW") : 150_000.0;
        double Rmin = dcsim.hasPath("train.Rmin") ? dcsim.getDouble("train.Rmin") : 1e-6;
        double epsFrac = dcsim.hasPath("train.epsFrac") ? dcsim.getDouble("train.epsFrac") : 1e-3;

        // ---- 13) Start Akka — INJICERA HELA SCENARIO-KONFIGEN ----
        // Viktigt: nu kommer GridModelActor att läsa rätt värden via ctx.getSystem().settings().config()
        ActorSystem<Root.Command> system = ActorSystem.create(
                Root.create(model, solver, outPath, tickSec, startSec, endSec,
                        spawns, speed, stopAfterSteps, anchorNodeId,
                        path, vMS, pW, Rmin, epsFrac),
                "SimulatorSystem",
                scenario // <— injicera
        );

        LongFiles.closeQuietly();

    }

    // ---- Utilities ----

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

    // (används ej längre — men lämnas kvar om du vill ha annan fallback)
    @SuppressWarnings("unused")
    private static int firstNonGround(GridModel<Real> m) {
        int g = m.getGroundNodeId();
        for (Object o : m.getNodeIds()) {
            int id = (o instanceof Integer) ? (Integer) o : Integer.parseInt(o.toString());
            if (id != g) return id;
        }
        return g;
    }
}
