package org.dcsim.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.*;

public class TrainActor extends AbstractBehavior<TrainActor.Command> {

    // ===== Protocol =====
    public interface Command {}

    public static final class Tick implements Command {
        public final double timeSec;
        public Tick(double timeSec) { this.timeSec = timeSec; }
    }

    // ===== State =====
    private final String trainId;
    private final ActorRef<GridModelActor.Command> grid;
    private double positionMeters = 0.0;

    public static Behavior<Command> create(String trainId, ActorRef<GridModelActor.Command> grid) {
        return Behaviors.setup(ctx -> new TrainActor(ctx, trainId, grid));
    }

    private TrainActor(ActorContext<Command> ctx, String trainId, ActorRef<GridModelActor.Command> grid) {
        super(ctx);
        this.trainId = trainId;
        this.grid = grid;
        ctx.getLog().info("TrainActor [{}] initialized.", trainId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        // Simple dummy movement: +10 m per tick
        positionMeters += 10.0;

        // Dummy power model: 500 kW traction when moving forward, 0 kW brake, 20 kW aux during dwell=false
        double motKW = 500.0;
        double brkKW = 0.0;
        double auxKW = 0.0;

        // Send update to GridModelActor
        grid.tell(new GridModelActor.UpdateTrainPower(trainId, motKW, brkKW, auxKW, positionMeters));

        // Log for visibility
        getContext().getLog().info("Train [{}] t={}s pos={} m -> mot={} kW, brk={} kW, aux={} kW",
                trainId, msg.timeSec, positionMeters, motKW, brkKW, auxKW);

        return this;
    }
}
