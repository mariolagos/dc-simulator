package org.dcsim;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.typesafe.config.Config;
import org.dcsim.actors.GridModelActor;
import org.dcsim.actors.SimulationSpeed;
import org.dcsim.actors.TrainActor;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.DcIterativeAdapterSolver;
import org.dcsim.electric.Device;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Substation;
import org.dcsim.export.LongFiles;
import org.dcsim.export.LongTableWriter;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;
import org.dcsim.power.PowerProfile;
import org.dcsim.sim.EdgeRef;
import org.dcsim.sim.TrainAnchorComponent;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.dcsim.RunLayoutFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppRunner {

    static final boolean DEBUG_VERBOSITY = false;

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
        private final String anchorNodeId;
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
                String anchorNodeId,
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
                String anchorNodeId,
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
                String anchorNodeId,
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

            if (DEBUG_VERBOSITY) {
                System.out.println("[GMA] anchorNodeId=" + anchorNodeId
                        + " label=" + model.getNodeById(anchorNodeId).getPosition());
            }


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
            if (DEBUG_VERBOSITY) System.out.println("[ROOT] step=" + step);
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

    public static void run(String[] args) throws IOException {
        if (DEBUG_VERBOSITY) System.out.println("[MAIN] AppRunner starting");

        Path confFile = RunLayoutFactory.resolveConfArg(args[0]);
        Path outputRoot = (args.length >= 2) ? Paths.get(args[1]) : null;

        ScenarioLoader<Real> loader = new DcSimScenarioLoader();
        SimulationInputModel<Real> input = loader.load(confFile, outputRoot);

        if (input.isExportOnly()) {
            return;
        }

        if (!input.shouldRunSolver()) {
            return;
        }

        // Legacy config access kept temporarily until runtime is fully separated
        Config scenario = DcSimScenarioLoader.loadScenarioConfig(confFile);
        Config dcsim = DcSimScenarioLoader.requireDcsim(scenario, confFile);

        final String project = input.getProjectId();
        final String scenarioId = input.getScenarioId();
        final String hash = dcsim.hasPath("hash")
                ? dcsim.getString("hash")
                : System.getProperty("dcsim.hash", "no-hash");
        final boolean overwrite = true;

        String csvPath = input.longTablePath().toString();
        String outPath = input.electricalCsvPath(confFile.getFileName().toString()).toString();
        LongTableWriter longWriter = new LongTableWriter(csvPath, overwrite, project, scenarioId, hash);
        GridModelActor.setLongWriter(longWriter);
        TrainActor.setLongWriter(longWriter);

        ElectricSolver solver = new DcIterativeAdapterSolver();
        if (DEBUG_VERBOSITY) System.out.println("[CONF] Using ElectricSolver: " + solver.getClass().getName());

        new File(outPath).getParentFile().mkdirs();

        try (ResultCsvWriter ignored = new ResultCsvWriter(input.getGridModel(), outPath, true)) {
            // truncate/create file
        } catch (IOException e) {
            System.err.println("[WARN] Could not prepare CSV file: " + e.getMessage());
        }

        DcIterativeSolver.setLongWriter(longWriter);

        // ---- Legacy runtime parameters kept local for now ----
        String anchorNodeId = dcsim.hasPath("grid.anchorNodeId")
                ? dcsim.getString("grid.anchorNodeId")
                : firstNonGroundNonBus(input.getGridModel());

        List<EdgeRef> raw = new ArrayList<>();
        for (Object o : (java.util.Collection<?>) input.getGridModel().getDevices()) {
            Device<?> d = (Device<?>) o;
            if (d instanceof Line ln) {
                String from = ln.getFromNode();
                String to = ln.getToNode();
                double lm = ln.getLength();
                double r = ln.getResistance().asDouble();
                raw.add(new EdgeRef(from, to, r, lm));
            }
        }
        List<EdgeRef> path = linearizePath(raw, findPathStart(raw));

        double vMS = readSpeedMS(dcsim);
        double pW = dcsim.hasPath("train.pW") ? dcsim.getDouble("train.pW") : 150_000.0;
        double rMin = dcsim.hasPath("train.Rmin") ? dcsim.getDouble("train.Rmin") : 1e-6;
        double epsFrac = dcsim.hasPath("train.epsFrac") ? dcsim.getDouble("train.epsFrac") : 1e-3;

        ActorSystem<Root.Command> system = ActorSystem.create(
                Root.create(
                        input.getGridModel(),
                        solver,
                        outPath,
                        input.getTickSec(),
                        (int) input.getStartSec(),
                        (int) input.getEndSec(),
                        convertSpawns(input.getSpawns()),
                        input.getSimulationSpeed(),
                        input.getStopAfterSteps(),
                        anchorNodeId,
                        path,
                        vMS, pW, rMin, epsFrac
                ),
                "SimulatorSystem",
                scenario
        );

        try {
            system.getWhenTerminated().toCompletableFuture().join();
        } finally {
            try {
                if (DEBUG_VERBOSITY) System.out.println("[ROOT] TERMINATING");
                longWriter.close();
            } catch (Exception ignore) {
            }
            LongFiles.closeQuietly();
        }
    }
    // ---- Utilities ----

    private static List<TrainSpawn> convertSpawns(List<SimulationInputModel.TrainSpawn> src) {
        List<TrainSpawn> out = new ArrayList<>();
        for (SimulationInputModel.TrainSpawn s : src) {
            out.add(new TrainSpawn(
                    s.trainId(),
                    s.profile(),
                    s.departureSec(),
                    s.sameModel(),
                    s.auxKW()
            ));
        }
        return out;
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

    private static String findPathStart(List<EdgeRef> edges) {
        Set<String> froms = new HashSet<>(), tos = new HashSet<>();
        for (EdgeRef e : edges) {
            froms.add(e.i);
            tos.add(e.j);
        }
        for (String f : froms) if (!tos.contains(f)) return f;
        return edges.isEmpty() ? "" : edges.get(0).i;
    }

    private static List<EdgeRef> linearizePath(List<EdgeRef> edges, String startNodeId) {
        List<EdgeRef> remaining = new ArrayList<>(edges);
        List<EdgeRef> out = new ArrayList<>();
        String cur = startNodeId;
        while (!remaining.isEmpty()) {
            int hit = -1;
            boolean flip = false;
            for (int k = 0; k < remaining.size(); k++) {
                EdgeRef e = remaining.get(k);
                if (e.i.equals(cur)) {
                    hit = k;
                    flip = false;
                    break;
                }
                if (e.j.equals(cur)) {
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

    private static String firstNonGroundNonBus(GridModel<Real> m) {
        String g = m.getGroundNodeId();
        Set<String> bus = new HashSet<>();
        for (Object did : m.getDeviceIds()) {
            Device<Real> d = m.getDevice(String.valueOf(did));
            if (d instanceof Substation ss) bus.add(ss.getFromNode());
        }
        for (Object o : m.getNodeIds()) {
            if (!o.equals(g) && !bus.contains(0)) return o.toString();
        }
        return g;
    }

    // (används ej längre — men lämnas kvar om du vill ha annan fallback)
    @SuppressWarnings("unused")
    private static String firstNonGround(GridModel<Real> m) {
        String g = m.getGroundNodeId();
        for (Object o : m.getNodeIds()) {
            int id = (o instanceof Integer) ? (Integer) o : Integer.parseInt(o.toString());
            if (!o.equals(g)) return o.toString();
        }
        return g;
    }
}
