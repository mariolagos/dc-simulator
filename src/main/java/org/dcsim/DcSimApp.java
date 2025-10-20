package org.dcsim;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigResolveOptions;
import org.dcsim.actors.GridModelActor;
import org.dcsim.actors.SimulationSpeed;
import org.dcsim.actors.TrainActor;
import org.dcsim.electric.*;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;
import org.dcsim.sim.EdgeRef;
import org.dcsim.sim.TrainAnchorComponent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class DcSimApp {

    // ----- Root actor that wires everything with backpressure -----
    public static final class Root extends AbstractBehavior<Root.Command> {
        interface Command {}

        private static final class Kick implements Command {}
        private static final class FromGrid implements Command {
            final GridModelActor.SolveReply reply;
            FromGrid(GridModelActor.SolveReply r) { this.reply = r; }
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

        public static Behavior<Command> create(
                GridModel<Real> model,
                DcElectricSolver solver,
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

        private Root(
                ActorContext<Command> ctx,
                GridModel<Real> model,
                DcElectricSolver solver,
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
            this.path   = path;
            this.vMS    = vMS;
            this.pW     = pW;
            this.Rmin   = Rmin;
            this.epsFrac= epsFrac;

            double stopByTime  = (endSec <= 0 ? Double.POSITIVE_INFINITY : endSec);
            double stopBySteps = (stopAfterSteps > 0) ? (startSec + stopAfterSteps * tickSec)
                    : Double.POSITIVE_INFINITY;
            this.endAtSec = Math.min(stopByTime, stopBySteps);

            // Grid actor
            this.grid = ctx.spawn(GridModelActor.create(model, solver, csvOut, anchorNodeId), "grid");

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
                                ts.trainId, String.format(java.util.Locale.ROOT,"%.1f", t), ts.departureSec);
                        it.remove();
                    }
                }
            }

            // 1) trafik (tick) — profil-styrning m.m. ligger i TrainActor
            for (var tr : trains) tr.tell(new TrainActor.Tick(t));

            // 2) flytta enbart installerade ankare
            for (var e : trainComps.entrySet()) {
                String id = e.getKey();
                var comp  = e.getValue();
                double v  = comp.getSpeedMS();
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
                step   = step + 1;

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
                              boolean sameModel, double auxKW) {}

    public static void main(String[] args) throws IOException {
        System.out.println("[MAIN] DcSimApp starting");

        // ---- 1) Hämta filen (tillåt att man anger en katalog och leta efter scenario.conf där) ----
        if (args.length != 1) {
            System.err.println("Usage: DcSimApp <path-to-scenario.conf|dir>");
            System.exit(1);
        }
        File confArg  = new File(args[0]);
        File confFile = confArg.isDirectory() ? new File(confArg, "scenario.conf") : confArg;
        if (!confFile.exists()) {
            System.err.println("[ERROR] Config not found: " + confFile.getAbsolutePath());
            System.exit(2);
        }

        // ---- 2) Läs, med fallback till classpath (application.conf/reference.conf), och resolve() ----
        ConfigParseOptions parseOpts = ConfigParseOptions.defaults().setAllowMissing(false);
        ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setAllowUnresolved(false)
                .setUseSystemEnvironment(true);

        Config scenario = ConfigFactory
                .parseFileAnySyntax(confFile, parseOpts)
                .withFallback(ConfigFactory.load())      // classpath fallback
                .resolve(resolveOpts);

        // ---- 3) Säkerställ att 'dcsim' finns och plocka ut den delkonfigurationen ----
        if (!scenario.hasPath("dcsim")) {
            System.err.println("[ERROR] Top-level 'dcsim' section is missing in: " + confFile.getAbsolutePath());
            System.exit(3);
        }
        Config dcsim = scenario.getConfig("dcsim");

        // ---- 4) Logga vad som faktiskt lästes (bra vid “<MISSING>") ----
        ConfigRenderOptions compactPretty = ConfigRenderOptions.concise().setFormatted(true).setJson(false);
        System.out.println("[CONF] Loaded from: " + confFile.getAbsolutePath());
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

        // ---- 5) Skriv “effective config" till disk (både .conf och .md) ----
        Path outDir = Paths.get("output");
        java.nio.file.Files.createDirectories(outDir);

        String effectiveDcsim = "dcsim " + dcsim.root().render(
                ConfigRenderOptions.defaults()
                        .setComments(false)
                        .setOriginComments(false)
                        .setFormatted(true)
                        .setJson(false)
        );
        java.nio.file.Files.writeString(outDir.resolve("effective_dcsim.conf"), effectiveDcsim);
        String effectiveMd = "# Effective dcsim config\n\n```hocon\n" + effectiveDcsim + "\n```\n";
        java.nio.file.Files.writeString(outDir.resolve("effective_dcsim.md"), effectiveMd);
        System.out.println("[CONF] Wrote effective config to output/effective_dcsim.conf and .md");

        // ---- 6) Plocka sim-parametrar ----
        Config sim = dcsim.getConfig("simulationControl");
        double tickSec = sim.hasPath("tickDurationSec") ? sim.getDouble("tickDurationSec")
                : sim.getDouble("tickDuration");
        int startSec = sim.hasPath("simulationStart") ? parseHmsToSeconds(sim.getString("simulationStart")) : 0;
        int endSec   = sim.hasPath("simulationEnd")   ? parseHmsToSeconds(sim.getString("simulationEnd"))
                : Integer.MAX_VALUE;
        SimulationSpeed speed = sim.hasPath("simulationSpeed")
                ? SimulationSpeed.valueOf(sim.getString("simulationSpeed").trim().toUpperCase(Locale.ROOT))
                : SimulationSpeed.FAST;
        int stopAfterSteps = sim.hasPath("stopAfterSteps") ? sim.getInt("stopAfterSteps") : -1;

        // ---- 7) Bygg modellen från dcsim-config ----
        GridModel<Real> model = GridModelLoader.load(dcsim);

        int anchorNodeId = dcsim.hasPath("grid.anchorNodeId")
                ? dcsim.getInt("grid.anchorNodeId")
                : firstNonGroundNonBus(model);

        // ---- 8) Power-profiler ----
        Map<String, List<PowerPoint>> byTpl = dcsim.hasPath("powerProfiles")
                ? PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"))
                : Collections.emptyMap();
        Map<String, PowerProfile> profileByTemplate = new HashMap<>();
        for (var e : byTpl.entrySet()) {
            profileByTemplate.put(e.getKey(), new org.dcsim.power.TablePowerProfile(e.getValue()));
        }
        boolean sameModel = dcsim.hasPath("powerProfiles.motoringAndAuxiliariesInSameModel")
                && dcsim.getBoolean("powerProfiles.motoringAndAuxiliariesInSameModel");
        double auxKW = dcsim.hasPath("powerProfiles.auxiliaryPower")
                ? dcsim.getDouble("powerProfiles.auxiliaryPower") : 0.0;

        // ---- 9) Traffic → Train spawns ----
        List<TrainSpawn> spawns = new ArrayList<>();
        if (dcsim.hasPath("traffic")) {
            var timetable = dcsim.getConfig("traffic.timetable");
            var trainsConf = timetable.getConfigList("trains");
            for (Config tr : trainsConf) {
                String idBase   = tr.getString("id");
                String tpl      = tr.getString("templateId");
                int dep0Abs     = parseHmsToSeconds(tr.getString("departure"));
                Integer headway = optHmsToSeconds(tr, "headway");
                int count       = tr.hasPath("count") ? tr.getInt("count") : 1;
                String sig      = tr.hasPath("signature") ? tr.getString("signature") : "";

                PowerProfile prof = profileByTemplate.get(tpl);

                for (int k = 0; k < count; k++) {
                    int depKAbs = dep0Abs + ((headway != null) ? k * headway : 0);
                    int depRel  = Math.max(0, depKAbs - startSec);
                    String tid  = (count == 1) ? idBase : uniqId(idBase, sig, k + 1);
                    spawns.add(new TrainSpawn(tid, prof, depRel, sameModel, auxKW));
                }
            }
        }

        // ---- 10) Solver + CSV path ----
        DcElectricSolver solver = new DcElectricSolver();

        // robust filbaserat namn (Windows/Unix)
        String baseName = new File(confFile.getName()).getName();
        int dot = baseName.lastIndexOf('.');
        String testName = (dot > 0 ? baseName.substring(0, dot) : baseName);

        String outPath = "output/electrical_" + testName + ".csv";
        new File(outPath).getParentFile().mkdirs();
        try (ResultCsvWriter ignored = new ResultCsvWriter(model, outPath, true)) { /* truncate */ }
        catch (IOException e) { System.err.println("[WARN] Could not prepare CSV file: " + e.getMessage()); }

        // ---- 11) Bygg path från modellen (linjer) ----
        List<EdgeRef> raw = new ArrayList<>();
        for (Device<Real> d : model.getDevices()) {
            if (d instanceof Line ln) {
                int from  = ln.getFromNode();
                int to    = ln.getToNode();
                double Lm = ln.getLength();
                double R  = ln.getResistance().asDouble();
                raw.add(new EdgeRef(from, to, R, Lm));
            }
        }
        List<EdgeRef> path = linearizePath(raw, findPathStart(raw));

        // ---- 12) Ankare-parametrar (från conf) ----
        double vMS     = readSpeedMS(dcsim);
        double pW      = dcsim.hasPath("train.pW")      ? dcsim.getDouble("train.pW")      : 150_000.0;
        double Rmin    = dcsim.hasPath("train.Rmin")    ? dcsim.getDouble("train.Rmin")    : 1e-6;
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
        for (EdgeRef e : edges) { froms.add(e.i); tos.add(e.j); }
        for (int f : froms) if (!tos.contains(f)) return f;
        return edges.isEmpty() ? 0 : edges.get(0).i;
    }

    private static List<EdgeRef> linearizePath(List<EdgeRef> edges, int startNodeId) {
        List<EdgeRef> remaining = new ArrayList<>(edges);
        List<EdgeRef> out = new ArrayList<>();
        int cur = startNodeId;
        while (!remaining.isEmpty()) {
            int hit = -1; boolean flip = false;
            for (int k = 0; k < remaining.size(); k++) {
                EdgeRef e = remaining.get(k);
                if (e.i == cur) { hit = k; flip = false; break; }
                if (e.j == cur) { hit = k; flip = true;  break; }
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
                if (tpl.hasPath("speedMS"))  return tpl.getDouble("speedMS");
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
