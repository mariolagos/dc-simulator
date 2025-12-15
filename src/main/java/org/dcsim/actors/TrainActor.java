package org.dcsim.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.dcsim.export.LongTableWriter;
import org.dcsim.power.PowerProfile;
import org.dcsim.solver.impl.DcIterativeSolver;

import java.util.OptionalDouble;

public class TrainActor extends AbstractBehavior<TrainActor.Command> {

    private static volatile LongTableWriter LONG_WRITER = null;

    public static void setLongWriter(LongTableWriter writer) {
        LONG_WRITER = writer;
    }

    private static LongTableWriter lw() {
        return LONG_WRITER;
    }

    // ===== Protocol =====
    public interface Command {
    }

    public static final class Tick implements Command {
        public final double timeSec;

        public Tick(double timeSec) {
            this.timeSec = timeSec;
        }
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

    private static boolean finite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

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
        double xM = 0.;
        double vMs = 0;
        if (profile != null && localT >= 0.0) {
            netW = profile.getPowerAtTime(localT).asDouble();
            xM = profile.getPositionAtTime(localT).getAsDouble();
            vMs = profile.getSpeedAtTime(localT).getAsDouble();
        }
        final double tractionKW = netW / 1000.0;

        // TrainLoad contract:
        //   motoringKW >= 0
        //   brakingKW  <= 0  (NEGATIVE for regen back to the network)
        final double motKW = Math.max(0.0, tractionKW);
        final double regenKW = Math.min(0.0, tractionKW);
        final double brkKW = regenKW;                 // send NEGATIVE during regen
        final double auxKWout = sameModel ? 0.0 : this.auxKW;
        // === DEBUG TA: after computing mot/brk/aux this tick, before sending it ===
        System.out.printf(
                "[TA] id=%s step=%.0f mot=%.3f kW brk=%.3f kW aux=%.3f kW%n",
                trainId, dt, motKW, brkKW, auxKW
        );
        // --- 2) Kinematics from profile (best effort, with safe fallbacks)
        Double xMeters = null, vMS = null;
        if (profile != null && localT >= 0.0) {
            OptionalDouble xOpt = profile.getPositionAtTime(localT);
            OptionalDouble vOpt = profile.getSpeedAtTime(localT);

            if (xOpt.isPresent() && vOpt.isPresent()) {
                xMeters = xOpt.getAsDouble();
                vMS = vOpt.getAsDouble();
            } else if (xOpt.isPresent()) {
                double xNow = xOpt.getAsDouble();
                double vEst = (dt > 0.0) ? (xNow - lastPosM) / dt : 0.0;
                xMeters = xNow;
                vMS = vEst;
            } else if (vOpt.isPresent()) {
                double vNow = vOpt.getAsDouble();
                double xNow = lastPosM + vNow * dt;
                xMeters = xNow;
                vMS = vNow;
            }
        }
        if (xMeters == null) xMeters = lastPosM;
        if (vMS == null) vMS = 0.0;

        // Update local history
        lastPosM = xMeters;
        lastTickAbsSec = msg.timeSec;

        // --- 3) Single push to grid (no duplicate tell)

        grid.tell(new GridModelActor.UpdateTrainPower(
                trainId,
                motKW,
                brkKW,
                auxKW,
                lastPosM,
                vMS,
                localT  // samma t som du loggar pos_m med
        ));

        try {
            LongTableWriter lt = DcIterativeSolver.getLongWriter();
            if (lt != null) {
                final double t = msg.timeSec;   // 🔁 GLOBAL simtid, inte localT

                if (finite(t)) {// getter nedan i fix B{}
                    if (finite(motKW)) lt.signalRow(t, "Train", trainId, "mot_W", motKW * 1000, "W", "TA", null, null);
                    if (finite(brkKW)) lt.signalRow(t, "Train", trainId, "brk_W", brkKW * 1000, "W", "TA", null, null);
                    if (finite(auxKW)) lt.signalRow(t, "Train", trainId, "aux_W", auxKW * 1000, "W", "TA", null, null);
                    if (finite(xM)) lt.signalRow(t, "Train", trainId, "pos_m", xM, "m", "TA", null, null);
                    if (finite(vMs)) lt.signalRow(t, "Train", trainId, "speed_mps", vMs, "m/s", "TA", null, null);
                }
            }
        } catch (
                Throwable ignore) { /* keep run robust */ }

        return this;
    }
}
