package org.dcsim.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimulationControllerActor extends AbstractBehavior<SimulationControllerActor.Command> {

    // ===== Protocol =====
    public interface Command {}

    public static final class RegisterTrain implements Command {
        public final String trainId;
        public final ActorRef<TrainActor.Command> ref;
        public RegisterTrain(String trainId, ActorRef<TrainActor.Command> ref) {
            this.trainId = trainId; this.ref = ref;
        }
    }

    public static final class StartSimulation implements Command {
        public final double tickDurationSec;
        public StartSimulation(double tickDurationSec) { this.tickDurationSec = tickDurationSec; }
    }

    /** Optional: end after a fixed number of steps */
    public static final class StopAfterSteps implements Command {
        public final int steps;
        public StopAfterSteps(int steps) { this.steps = steps; }
    }

    public static final class StopSimulation implements Command {}

    private static final class InternalTick implements Command {}

    // ===== State =====
    private final ActorRef<GridModelActor.Command> grid;
    private final Map<String, ActorRef<TrainActor.Command>> trains = new LinkedHashMap<>();
    private final TimerScheduler<Command> timers;
    private double dt = 1.0;
    private double tSec = 0.0;
    private int step = 0;
    private int stopAfter = -1; // <0 => run until externally stopped

    public static Behavior<Command> create(ActorRef<GridModelActor.Command> grid) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> new SimulationControllerActor(ctx, timers, grid)));
    }

    private SimulationControllerActor(ActorContext<Command> ctx,
                                      TimerScheduler<Command> timers,
                                      ActorRef<GridModelActor.Command> grid) {
        super(ctx);
        this.timers = timers;
        this.grid = grid;
        ctx.getLog().info("SimulationControllerActor started.");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RegisterTrain.class, this::onRegisterTrain)
                .onMessage(StartSimulation.class, this::onStart)
                .onMessage(StopAfterSteps.class, this::onStopAfter)
                .onMessage(StopSimulation.class, this::onStop)
                .onMessage(InternalTick.class, this::onInternalTick)
                .build();
    }

    private Behavior<Command> onRegisterTrain(RegisterTrain msg) {
        getContext().getLog().info("Controller: registered train {}", msg.trainId);
        trains.put(msg.trainId, msg.ref);
        return this;
    }

    private Behavior<Command> onStart(StartSimulation msg) {
        this.dt = msg.tickDurationSec;
        getContext().getLog().info("Controller: starting simulation, dt={} s", msg.tickDurationSec);
        timers.startTimerAtFixedRate(new InternalTick(),
                Duration.ofMillis((long) Math.round(dt * 1000.0)));
        return this;
    }

    private Behavior<Command> onStopAfter(StopAfterSteps msg) {
        this.stopAfter = msg.steps;
        getContext().getLog().info("Controller: will stop after {} steps", Integer.valueOf(msg.steps));
        return this;
    }

    private Behavior<Command> onStop(StopSimulation msg) {
        getContext().getLog().info("Controller: stopping simulation");
        timers.cancelAll();
        grid.tell(new GridModelActor.SimulationFinished());
        return Behaviors.stopped();
    }

    private Behavior<Command> onInternalTick(InternalTick t) {
        // 1) Dispatch tick to trains
        for (var e : trains.entrySet()) {
            e.getValue().tell(new TrainActor.Tick(tSec));
        }
        // 2) Solve tick
        grid.tell(new GridModelActor.SolveTick(tSec, step));

        // Advance
        tSec += dt;
        step++;

        if (stopAfter >= 0 && step >= stopAfter) {
            // Stop condition reached
            return onStop(new StopSimulation());
        }
        return this;
    }
}
