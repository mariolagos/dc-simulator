package org.dcsim.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimulationControllerActor extends AbstractBehavior<SimulationControllerActor.Command> {

    public interface Command {}

    public static final class RegisterTrain implements Command {
        public final String trainId;
        public final ActorRef<TrainActor.Command> ref;
        public RegisterTrain(String trainId, ActorRef<TrainActor.Command> ref) {
            this.trainId = trainId; this.ref = ref;
        }
    }

    // === NYTT: StartSimulation kan ta även SimulationSpeed ===
    public static final class StartSimulation implements Command {
        public final double tickDurationSec;       // steg i SIMTID
        public final SimulationSpeed speed;        // FAST eller REAL_TIME

        // Bakåtkompatibel ctor (gamla anrop fortsätter funka)
        public StartSimulation(double tickDurationSec) {
            this.tickDurationSec = tickDurationSec == 0.0 ? 1.0 : Math.abs(tickDurationSec);
            this.speed = (tickDurationSec <= 0.0) ? SimulationSpeed.FAST : SimulationSpeed.REAL_TIME;
        }
        // Ny ctor: explicit hastighetsläge
        public StartSimulation(double tickDurationSec, SimulationSpeed speed) {
            this.tickDurationSec = tickDurationSec == 0.0 ? 1.0 : Math.abs(tickDurationSec);
            this.speed = (speed == null) ? SimulationSpeed.REAL_TIME : speed;
        }
    }

    public static final class StopAfterSteps implements Command {
        public final int steps;
        public StopAfterSteps(int steps) { this.steps = steps; }
    }
    public static final class StopSimulation implements Command {}
    private static final class InternalTick implements Command {}

    private final ActorRef<GridModelActor.Command> grid;
    private final Map<String, ActorRef<TrainActor.Command>> trains = new LinkedHashMap<>();
    private final TimerScheduler<Command> timers;

    private double dt = 1.0;     // simtid per steg
    private double tSec = 0.0;   // aktuell simtid
    private int step = 0;
    private int stopAfter = -1;

    private boolean fastMode = false;
    private final int fastBurst;

    public static Behavior<Command> create(ActorRef<GridModelActor.Command> grid) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> new SimulationControllerActor(ctx, timers, grid)));
    }

    private SimulationControllerActor(ActorContext<Command> ctx,
                                      TimerScheduler<Command> timers,
                                      ActorRef<GridModelActor.Command> grid) {
        super(ctx);
        this.timers = timers;
        this.grid = grid;

        int fb = 200;
        try {
            String prop = System.getProperty("dcsim.fastBurst");
            if (prop != null) fb = Math.max(1, Integer.parseInt(prop.trim()));
        } catch (Exception ignore) {}
        this.fastBurst = fb;

        ctx.getLog().info("SimulationControllerActor started. fastBurst={}", fastBurst);
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
        grid.tell(new GridModelActor.EnsureTrainDevice(msg.trainId));
        return this;
    }

    private Behavior<Command> onStart(StartSimulation msg) {
        this.dt = msg.tickDurationSec;
        this.fastMode = (msg.speed == SimulationSpeed.FAST);

        getContext().getLog().info("Controller: starting simulation, dt={} s (sim), speed={}", dt, msg.speed);

        if (fastMode) {
            getContext().scheduleOnce(Duration.ZERO, getContext().getSelf(), new InternalTick());
        } else {
            timers.startTimerAtFixedRate(new InternalTick(), Duration.ofMillis(Math.round(dt * 1000.0)));
        }
        return this;
    }

    private Behavior<Command> onStopAfter(StopAfterSteps msg) {
        this.stopAfter = msg.steps;
        getContext().getLog().info("Controller: will stop after {} steps", msg.steps);
        return this;
    }

    private Behavior<Command> onStop(StopSimulation msg) {
        getContext().getLog().info("Controller: stopping simulation");
        timers.cancelAll();
        grid.tell(new GridModelActor.SimulationFinished());
        return Behaviors.stopped();
    }

    private Behavior<Command> onInternalTick(InternalTick t) {
        if (!fastMode) {
            dispatchTickOnce();
            advanceOneStep();
            if (shouldStop()) return onStop(new StopSimulation());
            return this;
        }

        for (int i = 0; i < fastBurst; i++) {
            dispatchTickOnce();
            advanceOneStep();
            if (shouldStop()) return onStop(new StopSimulation());
        }
        getContext().scheduleOnce(Duration.ZERO, getContext().getSelf(), new InternalTick());
        return this;
    }

    private void dispatchTickOnce() {
        final double thisT = tSec;
        final int thisStep = step;
        for (var e : trains.entrySet()) e.getValue().tell(new TrainActor.Tick(thisT));
        grid.tell(new GridModelActor.SolveTick(thisT, thisStep));
    }

    private void advanceOneStep() {
        tSec += dt; step++;
    }
    private boolean shouldStop() {
        return (stopAfter >= 0 && step >= stopAfter);
    }
}
