package org.dcsim.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.math3.linear.*;
import org.dcsim.electric.*;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;

import java.io.IOException;
import java.util.*;

/**
 * Owns the electrical grid model and runs the DC solver on each tick.
 */
public class GridModelActor extends AbstractBehavior<GridModelActor.Command> {

    static {
        System.out.println("[WHERE] GridModelActor loaded from: "
                + GridModelActor.class.getProtectionDomain().getCodeSource().getLocation());
    }

    // ====== knobs for runtime diagnostics ======
    private static final boolean ENABLE_TOPO_SANITY   = true;  // warn if train anchored on a substation bus
    private static final boolean ENABLE_POWER_SANITY  = true;  // check P_sub against E^2/(4R)
    private static final double  P_HEADROOM_FACTOR    = 1.20;  // allow 20% headroom over ideal max before warning
    private static final double  SMALL_CURRENT_A      = 1e-3;  // ~0 A
    private static final double  SMALL_POWER_W        = 1e3;   // ~1 kW

    // Cached last voltages for nodes (nodeId -> V)
    private final Map<Integer, Double> lastNodeV = new HashMap<>();

    // ===== Protocol =====
    public interface Command {}

    /** Ensure a TrainLoad device exists for a given train id. */
    public static final class EnsureTrainDevice implements Command {
        public final String trainId;
        public EnsureTrainDevice(String trainId) { this.trainId = trainId; }
    }

    /** Latest power request from a train (kW). brakingKW is NEGATIVE for regen. */
    public static final class UpdateTrainPower implements Command {
        public final String trainId;
        public final double motoringKW;    // ≥ 0
        public final double brakingKW;     // ≤ 0 (regen)
        public final double auxiliaryKW;   // ≥ 0
        public final double positionMeters; // reserved for future "move anchor" logic
        public UpdateTrainPower(String trainId, double motoringKW, double brakingKW,
                                double auxiliaryKW, double positionMeters) {
            this.trainId = trainId;
            this.motoringKW = motoringKW;
            this.brakingKW = brakingKW;
            this.auxiliaryKW = auxiliaryKW;
            this.positionMeters = positionMeters;
        }
    }

    /** Reply protocol for backpressure (Root waits for this before next tick). */
    public interface SolveReply {}
    public static final class Solved implements SolveReply {
        public final double timeSec;
        public final int step;
        public Solved(double timeSec, int step) { this.timeSec = timeSec; this.step = step; }
    }

    /** Trigger a solve step and append one CSV row (optionally ack to replyTo). */
    public static final class SolveTick implements Command {
        public final double timeSec;
        public final int step;
        public final ActorRef<SolveReply> replyTo; // may be null

        public SolveTick(double timeSec, int step) { this(timeSec, step, null); }
        public SolveTick(double timeSec, int step, ActorRef<SolveReply> replyTo) {
            this.timeSec = timeSec;
            this.step = step;
            this.replyTo = replyTo;
        }
    }

    /** Graceful shutdown: close writer. */
    public static final class SimulationFinished implements Command {}

    // Per-train configuration (cutoff voltage, max voltage, current limit)
    static final class TrainCfg {
        final double cutoffV, maxV, maxA;
        TrainCfg(double c, double mv, double ma) { cutoffV = c; maxV = mv; maxA = ma; }
    }

    private TrainCfg trainDefaults = new TrainCfg(850.0, 1000.0, 4000.0); // safe fallbacks
    private final Map<String, TrainCfg> trainOverrides = new HashMap<>();

    private static TrainCfg readTrainCfgFor(String id, Config defaults, Config overrides) {
        double c  = defaults.hasPath("cutoffVoltage") ? defaults.getDouble("cutoffVoltage") : 850.0;
        double mv = defaults.hasPath("maxVoltage")    ? defaults.getDouble("maxVoltage")    : 1000.0;
        double ma = defaults.hasPath("maxCurrentA")   ? defaults.getDouble("maxCurrentA")   : 4000.0;
        if (overrides != null && overrides.hasPath(id)) {
            Config oc = overrides.getConfig(id);
            if (oc.hasPath("cutoffVoltage")) c  = oc.getDouble("cutoffVoltage");
            if (oc.hasPath("maxVoltage"))    mv = oc.getDouble("maxVoltage");
            if (oc.hasPath("maxCurrentA"))   ma = oc.getDouble("maxCurrentA");
        }
        return new TrainCfg(c, mv, ma);
    }

    // ===== State =====
    private final GridModel model;
    private final DcElectricSolver solver;
    private final int anchorNodeId; // the catenary/feeder node trains connect to (not ground)
    private final Map<String, TrainLoad> trainDevices = new HashMap<>();
    private final Map<String, UpdateTrainPower> latest = new HashMap<>();
    private final ResultCsvWriter writer;

    // For topology sanity messages (log once per pair)
    private final Set<String> warnedTrainOnBus = new HashSet<>();
    private final Set<Integer> substationBusNodes = new HashSet<>();

    public static Behavior<Command> create(GridModel model,
                                           DcElectricSolver solver,
                                           String csvOutPath,
                                           int anchorNodeId) {
        return Behaviors.setup(ctx -> new GridModelActor(ctx, model, solver, csvOutPath, anchorNodeId));
    }

    private GridModelActor(ActorContext<Command> ctx,
                           GridModel model,
                           DcElectricSolver solver,
                           String csvOutPath,
                           int anchorNodeId) throws IOException {
        super(ctx);
        this.model = model;
        this.solver = solver;
        this.anchorNodeId = anchorNodeId;
        if (anchorNodeId == model.getGroundNodeId()) {
            throw new IllegalArgumentException("anchorNodeId must not be ground");
        }

        this.writer = new ResultCsvWriter(
                model,
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                true
        );
        ctx.getLog().info("GridModelActor started. Writing to {}. Anchor node={}",
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                anchorNodeId);

        // Index any pre-created TrainLoad devices present in the model
        for (String devId : model.getDeviceIds()) {
            Device<Real> dev = model.getDevice(devId);
            if (dev instanceof TrainLoad tl) {
                trainDevices.put(devId, tl);
            }
        }

        // Collect substation bus nodes (for topology sanity)
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof Substation ss) {
                substationBusNodes.add(ss.getFromNode());
            }
        }

        // ---- Read train config (supports root-level or dcsim.*) ----
        Config sys   = ctx.getSystem().settings().config();
        Config root  = sys.hasPath("dcsim") ? sys.getConfig("dcsim") : sys;
        Config trains= root.hasPath("trains") ? root.getConfig("trains") : ConfigFactory.empty();
        Config tdef  = trains.hasPath("defaults")  ? trains.getConfig("defaults")  : ConfigFactory.empty();
        Config tovr  = trains.hasPath("overrides") ? trains.getConfig("overrides") : null;

        this.trainDefaults = readTrainCfgFor("__defaults__", tdef, null);
        if (tovr != null) {
            for (String key : tovr.root().keySet()) {
                trainOverrides.put(key, readTrainCfgFor(key, tdef, tovr));
            }
        }

        System.out.println("[WHERE] GridModelActor loaded from: " +
                GridModelActor.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("[WHERE] TrainLoad loaded from: " +
                TrainLoad.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnsureTrainDevice.class, this::onEnsureTrainDevice)
                .onMessage(UpdateTrainPower.class, this::onUpdateTrainPower)
                .onMessage(SolveTick.class, this::onSolveTick)
                .onMessage(SimulationFinished.class, this::onFinished)
                .onSignal(PostStop.class, sig -> onPostStop())
                .build();
    }

    /** Ensure a TrainLoad exists and is configured from defaults/overrides. */
    private TrainLoad ensureTrainDeviceInternal(String trainId) {
        TrainLoad tl = trainDevices.computeIfAbsent(trainId, id -> {
            int g = model.getGroundNodeId();
            TrainLoad ntl = new TrainLoad(id, anchorNodeId, g);
            TrainCfg cfg = trainOverrides.getOrDefault(id, trainDefaults);
            ntl.setCutoffVoltage(Real.fromDouble(cfg.cutoffV));
            ntl.setMaxVoltage(Real.fromDouble(cfg.maxV));
            ntl.setMaxCurrent(Real.fromDouble(cfg.maxA));
            model.addDevice(ntl);
            getContext().getLog().info(
                    "Grid: created TrainLoad device for {} at {} (cutoff={}V, vmax={}V, Imax={}A)",
                    id, anchorNodeId, cfg.cutoffV, cfg.maxV, cfg.maxA
            );
            return ntl;
        });

        // Topology sanity: warn if anchored on a substation bus node
        if (ENABLE_TOPO_SANITY && substationBusNodes.contains(tl.getFromNode())) {
            String key = tl.getId() + "@" + tl.getFromNode();
            if (warnedTrainOnBus.add(key)) {
                getContext().getLog().warn(
                        "Train {} anchored at node {} which is also a substation bus. " +
                                "This can bypass line flows (symptom: P_lines≈0, huge P_sub). " +
                                "For A3 ensure anchorNodeId=2 and no legacy grid.trains with same id.",
                        tl.getId(), tl.getFromNode()
                );
            }
        }
        return tl;
    }

    private Behavior<Command> onEnsureTrainDevice(EnsureTrainDevice msg) {
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onUpdateTrainPower(UpdateTrainPower msg) {
        latest.put(msg.trainId, msg);
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onSolveTick(SolveTick tick) {
        // 1) Apply the latest train requests
        for (Map.Entry<String, UpdateTrainPower> e : latest.entrySet()) {
            TrainLoad tl = ensureTrainDeviceInternal(e.getKey());
            UpdateTrainPower u = e.getValue();
            tl.setRequestedComponents(u.motoringKW, u.brakingKW, u.auxiliaryKW);
        }

        // 2) Solve
        final GridResult res;
        try {
            res = solver.solve(model, tick.timeSec, tick.step);

            // Remember voltages for the next tick
            for (int nid : model.getNodeIds()) {
                lastNodeV.put(nid, res.getLatestNodeVoltage(nid).asDouble());
            }
        } catch (Exception ex) {
            getContext().getLog().error("Solve failed at t={}s step={}: {}", tick.timeSec, tick.step, ex.toString());
            return this;
        }

        // 3) No post recomputation of device powers — just totals + sanity diagnostics
        try {
            // Headline totals
            var totals = PowerAccounting.compute(model, res);
            res.addTotals(totals);

            // Keep voltages cached
            for (int nid : model.getNodeIds()) {
                lastNodeV.put(nid, res.getLatestNodeVoltage(nid).asDouble());
            }

            // ---- Sanity diagnostics ----
            if (ENABLE_POWER_SANITY) {
                checkSubstationPowerSanity(res, tick.timeSec);
                checkTrainLineFlowSanity(res);
            }

        } catch (Exception ex) {
            getContext().getLog().warn("Post-process failed at t={}s step={}: {}", tick.timeSec, tick.step, ex.toString());
        }

        // 4) Write CSV (flush so Root can proceed)
        try {
            writer.append(res, tick.timeSec, tick.step);
            writer.flush();
        } catch (IOException ex) {
            getContext().getLog().error("CSV write failed: {}", ex.toString());
        }

        // 5) Backpressure ACK to Root (after flush)
        if (tick.replyTo != null) {
            tick.replyTo.tell(new Solved(tick.timeSec, tick.step));
        }

        return this;
    }

    // ---- helpers ----

    /** If the solver didn’t set voltages (rare), reconstruct them from Y and J. */
    private void populateVoltagesIfMissing(GridResult res) {
        boolean any = false;
        for (int nid : model.getNodeIds()) {
            Real v = res.getLatestNodeVoltage(nid);
            if (v != null && v.asDouble() != 0.0) { any = true; break; }
        }
        if (any) return;

        RealMatrix Y = res.getYMatrix();
        RealVector J = res.getJVector();
        if (Y == null || J == null) return;

        List<Integer> nodes = new ArrayList<>(model.getNodeIds());
        int g = model.getGroundNodeId();
        List<Integer> nonGIdx = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i) != g) nonGIdx.add(i);

        int n = nonGIdx.size();
        if (n == 0) return;

        RealMatrix Yr = new Array2DRowRealMatrix(n, n);
        RealVector Jr = new ArrayRealVector(n);
        for (int ri = 0; ri < n; ri++) {
            int iFull = nonGIdx.get(ri);
            Jr.setEntry(ri, J.getEntry(iFull));
            for (int rj = 0; rj < n; rj++) {
                int jFull = nonGIdx.get(rj);
                Yr.setEntry(ri, rj, Y.getEntry(iFull, jFull));
            }
        }

        RealVector Vr;
        try {
            Vr = new LUDecomposition(Yr).getSolver().solve(Jr);
        } catch (Exception e) {
            return; // keep zeros if singular
        }

        double[] V = new double[nodes.size()];
        Arrays.fill(V, 0.0); // ground clamped
        for (int k = 0; k < n; k++) {
            int fullIdx = nonGIdx.get(k);
            V[fullIdx] = Vr.getEntry(k);
        }
        for (int i = 0; i < nodes.size(); i++) {
            res.setVoltage(nodes.get(i), Real.fromDouble(V[i]));
        }
    }

    /** No-op now: solver is authoritative for device powers/currents. */
    @SuppressWarnings("unused")
    private void populatePowers(GridResult res, double t) {
        // intentionally empty – keep any ad-hoc logging here if needed
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

    // ===== Sanity utilities =====

    /** Substation power should not exceed E^2/(4R) in magnitude. Warn if it does. */
// ===== Sanity utilities =====
    private void checkSubstationPowerSanity(GridResult res, double tSec) {
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (!(d instanceof Substation ss)) continue;

            double E = ss.getEmf().asDouble();
            double R = ss.getInternalResistance().asDouble();
            if (R <= 0) continue;

            Real pR = res.getLatestDevicePower(ss.getId());
            if (pR == null) continue;
            double P = pR.asDouble();

            double Pmax = (E * E) / (4.0 * R); // ideal Thevenin/Norton transfer max
            int headroomPct = (int) Math.round(P_HEADROOM_FACTOR * 100.0);

            if (Math.abs(P) > P_HEADROOM_FACTOR * Pmax) {
                double Va = res.getLatestNodeVoltage(ss.getFromNode()).asDouble();
                double Vb = res.getLatestNodeVoltage(ss.getToNode()).asDouble();
                double dV = Va - Vb;

                // SLF4J-style {} placeholders; preformat time to 3 decimals
                String tStr = String.format(java.util.Locale.ROOT, "%.3f", tSec);
                getContext().getLog().error(
                        "Power sanity: Substation {} has |P|={} W > {}% of Pmax={} W at t={} s. "
                                + "(E={} V, R={} ohm, Va-Vb={} V). Check J-signs (+Eg on 'from', -Eg on 'to'), "
                                + "train anchor node, and duplicate power computation.",
                        ss.getId(),
                        Math.abs(P),
                        headroomPct,
                        Pmax,
                        tStr,
                        E, R, dV
                );
            }
        }
    }

    /** Warn if trains consume power but line currents are ~0 A (likely anchored on bus). */
    private void checkTrainLineFlowSanity(GridResult res) {
        double sumTrainAbs = 0.0;
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof TrainLoad) {
                Real p = res.getLatestDevicePower(d.getId());
                if (p != null) sumTrainAbs += Math.abs(p.asDouble());
            }
        }
        if (sumTrainAbs < SMALL_POWER_W) return;

        // total absolute line current (rough indicator)
        double sumAbsILines = 0.0;
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof Line) {
                Real i = res.getLatestDeviceCurrent(d.getId());
                if (i != null) sumAbsILines += Math.abs(i.asDouble());
            }
        }
        if (sumAbsILines < SMALL_CURRENT_A) {
            getContext().getLog().warn(
                    "Train(s) exchange significant power but summed line current ≈ 0 A. " +
                            "Likely anchored on the substation bus or topology issue."
            );
        }
    }

    @SuppressWarnings("unused")
    private void recomputeSubstationPowers(GridResult result) {
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof Substation ss) {
                double Va = result.getLatestNodeVoltage(ss.getFromNode()).asDouble();
                double Vb = result.getLatestNodeVoltage(ss.getToNode()).asDouble();
                double E  = ss.getEmf().asDouble();
                double R  = ss.getInternalResistance().asDouble();
                double g  = (R > 0.0) ? 1.0 / R : 0.0;
                double dV = Vb - Va;
                double Pnet = g * (E * dV - dV * dV); // reference only
                result.setPower(ss.getId(), Real.fromDouble(Pnet));
            }
        }
    }
}
