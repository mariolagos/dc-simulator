package org.dcsim.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.dcsim.power.PowerProfile;

public class TrainActor extends AbstractBehavior<TrainActor.Command> {

    // ===== Protocol =====
    public interface Command {}

    public static final class Tick implements Command {
        public final double timeSec;
        public Tick(double timeSec) { this.timeSec = timeSec; }
    }

    // ===== Factory =====
    public static Behavior<Command> create(
            String trainId,
            ActorRef<GridModelActor.Command> grid,
            PowerProfile profile,
            int departureSec,
            boolean motoringAndAuxInSameModel,
            double auxiliaryKW
    ) {
        return Behaviors.setup(ctx ->
                new TrainActor(ctx, trainId, grid, profile, departureSec, motoringAndAuxInSameModel, auxiliaryKW)
        );
    }

    // ===== State =====
    private final String trainId;
    private final ActorRef<GridModelActor.Command> grid;
    private final PowerProfile profile;
    private final int departureSec;
    private final boolean sameModel;
    private final double auxKW;
    private double positionMeters = 0.0;

    private TrainActor(
            ActorContext<Command> ctx,
            String trainId,
            ActorRef<GridModelActor.Command> grid,
            PowerProfile profile,
            int departureSec,
            boolean motoringAndAuxInSameModel,
            double auxiliaryKW
    ) {
        super(ctx);
        this.trainId = trainId;
        this.grid = grid;
        this.profile = profile;
        this.departureSec = departureSec;
        this.sameModel = motoringAndAuxInSameModel;
        this.auxKW = auxiliaryKW;
        ctx.getLog().info("TrainActor [{}] ready (dep={}, sameModel={}, auxKW={})",
                trainId, departureSec, sameModel, auxKW);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        positionMeters += 10.0;

        double localT = msg.timeSec - departureSec;
        double netW = 0.0;
        if (profile != null && localT >= 0.0) {
            netW = profile.getPowerAtTime(localT).asDouble(); // W (±)
        }

        // Traction/braking split in kW: braking as POSITIVE magnitude
        double tractionKW = netW / 1000.0;
        double motKW = (tractionKW > 0) ? tractionKW : 0.0;
        double brkKW = (tractionKW < 0) ? tractionKW : 0.0;
        double auxKWout = sameModel ? 0.0 : auxKW;

        grid.tell(new GridModelActor.UpdateTrainPower(trainId, motKW, brkKW, auxKWout, positionMeters));

        getContext().getLog().info(
                "Train [{}] t={}s localT={} mot={} kW brk={} kW aux={} kW",
                trainId, msg.timeSec, localT, motKW, brkKW, auxKWout
        );
        return this;
    }
}
