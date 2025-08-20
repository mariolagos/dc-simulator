package org.dcsim.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.dcsim.export.ResultCsvWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Receives per-train power updates, runs the DC solver each tick,
 * and writes results using ResultCsvWriter.
 */
public class GridModelActor extends AbstractBehavior<GridModelActor.Command> {

    // ===== Protocol =====
    public interface Command {}

    /** Train → latest power/pos update */
    public static final class UpdateTrainPower implements Command {
        public final String trainId;
        public final double motoringKW, brakingKW, auxiliaryKW;
        public final double positionMeters; // reserved for topology mapping
        public UpdateTrainPower(String trainId, double motoringKW, double brakingKW,
                                double auxiliaryKW, double positionMeters) {
            this.trainId = trainId;
            this.motoringKW = motoringKW;
            this.brakingKW = brakingKW;
            this.auxiliaryKW = auxiliaryKW;
            this.positionMeters = positionMeters;
        }
    }

    /** Trigger solver and append a CSV row */
    public static final class SolveTick implements Command {
        public final double timeSec; public final int step;
        public SolveTick(double timeSec, int step) { this.timeSec = timeSec; this.step = step; }
    }

    /** Signal that the simulation is finished → flush/close writer */
    public static final class SimulationFinished implements Command { }

    // ===== State =====
    private final GridModel model;
    private final DcElectricSolver solver;
    private final Map<String, TrainLoad> trainDevices = new HashMap<>();
    private final Map<String, UpdateTrainPower> latest = new HashMap<>();
    private final ResultCsvWriter writer;

    public static Behavior<Command> create(GridModel model,
                                           DcElectricSolver solver,
                                           String csvOutPath) {
        return Behaviors.setup(ctx -> new GridModelActor(ctx, model, solver, csvOutPath));
    }

    private GridModelActor(ActorContext<Command> ctx,
                           GridModel model,
                           DcElectricSolver solver,
                           String csvOutPath) {
        super(ctx);
        this.model = model;
        this.solver = solver;
        this.writer = new ResultCsvWriter(model, (csvOutPath != null ? csvOutPath : "output/electrical.csv"), true);
        ctx.getLog().info("GridModelActor started. Writing to {}", (csvOutPath != null ? csvOutPath : "output/electrical.csv"));

        // Index existing TrainLoad devices (if any pre-added to the model)
        for (String devId : model.getDeviceIds()) {
            Device<Real> dev = model.getDevice(devId);
            if (dev instanceof TrainLoad tl) {
                trainDevices.put(devId, tl);
            }
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(UpdateTrainPower.class, this::onUpdateTrainPower)
                .onMessage(SolveTick.class, this::onSolveTick)
                .onMessage(SimulationFinished.class, this::onFinished)
                .onSignal(PostStop.class, sig -> onPostStop())
                .build();
    }

    private Behavior<Command> onUpdateTrainPower(UpdateTrainPower msg) {
        latest.put(msg.trainId, msg);

        // Lazily create TrainLoad device if missing
        trainDevices.computeIfAbsent(msg.trainId, id -> {
            int g = model.getGroundNodeId();
            TrainLoad tl = new TrainLoad(id, g, g);  // initially tie to ground; topology mapping can update later
            model.addDevice(tl);
            getContext().getLog().info("Grid: created TrainLoad device for {}", id);
            return tl;
        });

        return this;
    }


    private Behavior<Command> onSolveTick(SolveTick tick) {
        // 1) Apply latest requested components to each TrainLoad
        for (var e : latest.entrySet()) {
            TrainLoad tl = trainDevices.get(e.getKey());
            UpdateTrainPower u = e.getValue();
            if (tl != null) {
                tl.setRequestedComponents(u.motoringKW, u.brakingKW, u.auxiliaryKW);
                // TODO: use u.positionMeters + Topology to move terminals if/when needed
            }
        }

        // 2) Solve and append CSV row
        GridResult result = solver.solve(model, tick.timeSec, tick.step);
        try {
            writer.append(result, tick.timeSec, tick.step);
        } catch (IOException ex) {
            getContext().getLog().error("CSV write failed: {}", ex.toString());
        }

        return this;
    }

    private Behavior<Command> onFinished(SimulationFinished f) {
        getContext().getLog().info("GridModelActor: SimulationFinished received. Closing writer.");
        try { writer.close(); } catch (Exception ignore) {}
        return Behaviors.stopped();
    }

    private Behavior<Command> onPostStop() {
        try { writer.close(); } catch (Exception ignore) {}
        return this;
    }
}
