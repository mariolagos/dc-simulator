package org.dcsim.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.dcsim.power.PowerProfile;

import java.util.OptionalDouble;

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
    // kept for compatibility with logs; DcSimApp may not pass this, default to true
    private final boolean allowRegen = true;

    private double lastPosM = 0.0;
    private Double lastTickAbsSec = null;

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

        ctx.getLog().info("TrainActor [{}] ready (dep={}, sameModel={}, auxKW={}, allowRegen={})",
                trainId, departureSec, sameModel, auxKW, allowRegen);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        final double localT = msg.timeSec - departureSec;
        final double dt = (lastTickAbsSec == null) ? 0.0 : (msg.timeSec - lastTickAbsSec);

        // --- 1) Power from profile (W; +motoring, −regen)
        double netW = 0.0;
        if (profile != null && localT >= 0.0) {
            netW = profile.getPowerAtTime(localT).asDouble();
        }
        final double tractionKW = netW / 1000.0;

        // TrainLoad contract:
        //   motoringKW >= 0
        //   brakingKW  <= 0  (NEGATIVE for regen back to the network)
        final double motKW    = Math.max(0.0, tractionKW);
        final double regenKW  = Math.min(0.0, tractionKW);
        final double brkKW    = regenKW;                 // send NEGATIVE during regen
        final double auxKWout = sameModel ? 0.0 : this.auxKW;

        // --- 2) Kinematics from profile (best effort, with safe fallbacks)
        Double xMeters = null, vMS = null;
        if (profile != null && localT >= 0.0) {
            OptionalDouble xOpt = profile.getPositionAtTime(localT);
            OptionalDouble vOpt = profile.getSpeedAtTime(localT);

            if (xOpt.isPresent() && vOpt.isPresent()) {
                xMeters = xOpt.getAsDouble();
                vMS     = vOpt.getAsDouble();
            } else if (xOpt.isPresent()) {
                double xNow = xOpt.getAsDouble();
                double vEst = (dt > 0.0) ? (xNow - lastPosM) / dt : 0.0;
                xMeters = xNow;
                vMS     = vEst;
            } else if (vOpt.isPresent()) {
                double vNow = vOpt.getAsDouble();
                double xNow = lastPosM + vNow * dt;
                xMeters = xNow;
                vMS     = vNow;
            }
        }
        if (xMeters == null) xMeters = lastPosM;
        if (vMS == null)     vMS     = 0.0;

        // Update local history
        lastPosM = xMeters;
        lastTickAbsSec = msg.timeSec;

        // --- 3) Single push to grid (no duplicate tell)
        grid.tell(new GridModelActor.UpdateTrainPower(
                trainId,
                motKW, brkKW, auxKWout,   // NOTE: brkKW <= 0 for regen
                xMeters, vMS
        ));
        return this;
    }
}
