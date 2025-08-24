package org.dcsim;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.actors.GridModelActor;
import org.dcsim.actors.SimulationSpeed; // enum FAST, REAL_TIME
import org.dcsim.actors.TrainActor;
import org.dcsim.electric.*;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class DcSimApp {

    // ----- Root actor that wires everything -----
    public static final class Root extends AbstractBehavior<Root.Command> {
        interface Command {}
        private static final class Tick implements Command {}

        private final TimerScheduler<Command> timers;

        private final double tickSec;
        private final double endAtSec;   // hard stop (min of endSec and step-limit)
        private double nowSec;
        private long step;

        private final SimulationSpeed speed;
        private final ActorRef<GridModelActor.Command> grid;
        private final List<ActorRef<TrainActor.Command>> trains = new ArrayList<>();

        public static Behavior<Command> create(
                GridModel model,
                DcElectricSolver solver,
                String csvOut,
                double tickSec,
                int startSec,
                int endSec,               // may be Integer.MAX_VALUE if missing
                List<TrainSpawn> trainSpawns,
                SimulationSpeed speed,
                int stopAfterSteps        // <0 if missing
        ) {
            return Behaviors.setup(ctx ->
                    Behaviors.withTimers(timers ->
                            new Root(ctx, timers, model, solver, csvOut, tickSec, startSec, endSec, trainSpawns, speed, stopAfterSteps)
                    )
            );
        }

        private Root(
                ActorContext<Command> ctx,
                TimerScheduler<Command> timers,
                GridModel model,
                DcElectricSolver solver,
                String csvOut,
                double tickSec,
                int startSec,
                int endSec,
                List<TrainSpawn> trainSpawns,
                SimulationSpeed speed,
                int stopAfterSteps
        ) {
            super(ctx);
            this.timers = timers;
            this.tickSec = tickSec;
            this.nowSec = startSec;
            this.step = 0;
            this.speed = speed;

            // compute hard stop
            double stopByTime  = (endSec <= 0 ? Double.POSITIVE_INFINITY : endSec);
            double stopBySteps = (stopAfterSteps > 0)
                    ? (startSec + stopAfterSteps * tickSec)
                    : Double.POSITIVE_INFINITY;
            this.endAtSec = Math.min(stopByTime, stopBySteps);

            // Grid actor (anchorNode could be from config; hard-coded 1 here)
            int anchorNodeId = 1;
            this.grid = ctx.spawn(
                    GridModelActor.create(model, solver, csvOut, anchorNodeId),
                    "grid"
            );

            // spawn trains and prime TrainLoad devices in the grid
            for (var ts : trainSpawns) {
                var tr = ctx.spawn(
                        TrainActor.create(ts.trainId, grid, ts.profile, ts.departureSec, ts.sameModel, ts.auxKW),
                        "train_" + ts.trainId
                );
                trains.add(tr);
                grid.tell(new GridModelActor.EnsureTrainDevice(ts.trainId)); // prime so first solve writes a row
            }

            // Kick off ticking – exactly once, per mode
            if (speed == SimulationSpeed.REAL_TIME) {
                long periodMs = Math.max(1L, Math.round(tickSec * 1000.0));
                timers.startTimerAtFixedRate(new Tick(), Duration.ZERO, Duration.ofMillis(periodMs));
                ctx.getLog().info("Simulation started (REAL_TIME), dt={} s.", tickSec);
            } else {
                // FAST: start after 1 ms to let EnsureTrainDevice messages process first
                ctx.scheduleOnce(Duration.ofMillis(1), ctx.getSelf(), new Tick());
                ctx.getLog().info("Simulation started (FAST), dt={} s.", tickSec);
            }
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Tick.class, t -> onTick())
                    .build();
        }

        private Behavior<Command> onTick() {
            // Frys värden för det här steget
            final double t = nowSec;
            final int s = (int) step;

            // 1) dispatch ticks to trains
            for (var tr : trains) {
                tr.tell(new TrainActor.Tick(t));
            }

            // 2) solve current time/step
            grid.tell(new GridModelActor.SolveTick(t, s));

            // 3) advance simulation time (görs efter att vi skickat SolveTick)
            nowSec = t + tickSec;
            step = s + 1;

            // 4) stop AFTER producing this row, när nästa tick skulle kliva förbi endAtSec
            if (nowSec >= endAtSec - 1e-9) {
                grid.tell(new GridModelActor.SimulationFinished());
                return Behaviors.stopped();
            }

            // 5) FAST => reschedulera exakt en gång; REAL_TIME använder timers
            if (speed == SimulationSpeed.FAST) {
                getContext().scheduleOnce(java.time.Duration.ZERO, getContext().getSelf(), new Tick());
            }

            return this;
        }
    }

    private record TrainSpawn(String trainId, PowerProfile profile, int departureSec, boolean sameModel, double auxKW) {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: DcSimApp <path-to-application.conf>");
            System.exit(1);
        }
        File confFile = new File(args[0]);
        if (!confFile.exists()) {
            System.err.println("[ERROR] Config not found: " + confFile.getAbsolutePath());
            System.exit(2);
        }

        // ---- Config ----
        Config root = ConfigFactory.parseFile(confFile).resolve();
        Config dcsim = root.getConfig("dcsim");

        // ---- Sim control ----
        Config sim = dcsim.getConfig("simulationControl");

        double tickSec = sim.hasPath("tickDurationSec")
                ? sim.getDouble("tickDurationSec")
                : sim.getDouble("tickDuration"); // fallback to legacy key

        int startSec = sim.hasPath("simulationStart")
                ? parseHmsToSeconds(sim.getString("simulationStart"))
                : 0;

        int endSec = sim.hasPath("simulationEnd")
                ? parseHmsToSeconds(sim.getString("simulationEnd"))
                : Integer.MAX_VALUE; // step limit controls if time end missing

        SimulationSpeed speed = sim.hasPath("simulationSpeed")
                ? SimulationSpeed.valueOf(sim.getString("simulationSpeed").trim().toUpperCase(Locale.ROOT))
                : SimulationSpeed.FAST;

        int stopAfterSteps = sim.hasPath("stopAfterSteps")
                ? sim.getInt("stopAfterSteps")
                : -1;

        // ---- Grid ----
        GridModel model = GridModelLoader.load(dcsim.getConfig("grid"));

        // ---- Power templates ----
        Map<String, List<PowerPoint>> byTpl = dcsim.hasPath("powerProfiles")
                ? PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"))
                : Collections.emptyMap();

        Map<String, PowerProfile> profileByTemplate = new HashMap<>();
        for (var e : byTpl.entrySet()) {
            profileByTemplate.put(e.getKey(), new PowerProfile(e.getValue()));
        }

        boolean sameModel = dcsim.hasPath("powerProfiles.motoringAndAuxiliariesInSameModel")
                && dcsim.getBoolean("powerProfiles.motoringAndAuxiliariesInSameModel");

        double auxKW = dcsim.hasPath("powerProfiles.auxiliaryPower")
                ? dcsim.getDouble("powerProfiles.auxiliaryPower") : 0.0;

        // ---- Traffic → Train spawns ----
        List<TrainSpawn> spawns = new ArrayList<>();
        if (dcsim.hasPath("traffic")) {
            var timetable = dcsim.getConfig("traffic.timetable");
            var trainsConf = timetable.getConfigList("trains");
            for (Config tr : trainsConf) {
                String id = tr.getString("id");
                String tpl = tr.getString("templateId");
                int depSec = parseHmsToSeconds(tr.getString("departure"));
                PowerProfile prof = profileByTemplate.get(tpl);
                spawns.add(new TrainSpawn(id, prof, depSec, sameModel, auxKW));
            }
        }

        // ---- Solver + CSV path ----
        DcElectricSolver solver = new DcElectricSolver();
        String outPath = "output/electrical.csv";
        new File(outPath).getParentFile().mkdirs();
        try (ResultCsvWriter pre = new ResultCsvWriter(model, outPath)) {
            // pre-create/clear file
        } catch (IOException e) {
            System.err.println("[WARN] Could not prepare CSV file: " + e.getMessage());
        }

        // ---- Start Akka ----
        ActorSystem<Root.Command> system = ActorSystem.create(
                Root.create(model, solver, outPath, tickSec, startSec, endSec, spawns, speed, stopAfterSteps),
                "SimulatorSystem"
        );
        // System terminates when Root stops after SimulationFinished
    }

    private static int parseHmsToSeconds(String s) {
        String[] p = s.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        int sec = (p.length == 3) ? Integer.parseInt(p[2]) : 0;
        return h * 3600 + m * 60 + sec;
    }
}
