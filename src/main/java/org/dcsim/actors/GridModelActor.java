package org.dcsim.actors;


import org.dcsim.export.LongTableWriter;
import org.dcsim.solver.impl.DcIterativeSolver;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.Device;
import org.dcsim.electric.ElectricSolver;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.electric.Line;
import org.dcsim.electric.PowerAccounting;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;
import org.dcsim.sim.TrainAnchorComponent;


import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// TODO(bf-iteration): add logging of bf_iter, alpha and ΔV when backfeed iteration is introduced.
// TODO(v0.9): Consider splitting GridModelActor into
//  - TrainPowerAggregator
//  - RectifierLimiter
//  - NetLoggingAdapter
// to reduce size/complexity. v0.7 keeps monolith for stability.

public class GridModelActor extends AbstractBehavior<GridModelActor.Command> {
    // Fast-track logging nodes for trains
    private final java.util.Map<String, Integer> trainLogNode = new LinkedHashMap<>();

    private static boolean finite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }


    // ===== debug/sanity =====
    private static final boolean ENABLE_TOPO_SANITY = true;
    private static final boolean ENABLE_POWER_SANITY = true;
    private static final double P_HEADROOM_FACTOR = 1.20;
    private static final double SMALL_CURRENT_A = 1e-3;
    private static final double SMALL_POWER_W = 1e3;

    private final Map<Integer, Double> lastNodeV = new HashMap<>();
    private final Map<String, Double> lastPosM = new HashMap<>();

    // Energiackumulatorer (J) per tåg
    private final Map<String, Double> burnedJ = new java.util.HashMap<>();

    // (valfritt) spara senaste simtid för dt
    private double lastTickSec = Double.NaN;


    // Linje-ID:n vi vill emit:a (lägg till/ändra efter din topologi)
    private static final String[] LINE_IDS = new String[]{"L_1_2", "L_2_3"};

    // Stabil header i CSV (bara rena id:n, t.ex. "T1")
    private final LinkedHashSet<String> knownTrainIds = new LinkedHashSet<>();

    // --- Long-file writer (set from DcSimApp) ---
    private static volatile LongTableWriter LONG_WRITER = null;

    /**
     * Inject long writer once from DcSimApp (before first SolveTick).
     */
    public static void setLongWriter(LongTableWriter writer) {
        LONG_WRITER = writer;
    }

    /**
     * Null-safe accessor.
     */
    private static LongTableWriter lw() {
        return LONG_WRITER;
    }

    // ===== protokoll =====
    public interface Command {
    }

    /**
     * Se till att ett TrainLoad-device finns (profilstyrt tåg).
     */
    public static final class EnsureTrainDevice implements Command {
        public final String trainId;

        public EnsureTrainDevice(String trainId) {
            this.trainId = trainId;
        }
    }

    /**
     * Profiluppdatering (motoring ≥0, braking ≤0 = regen; >0 = ohmisk broms), med ev. x/v.
     */
    public static final class UpdateTrainPower implements Command {
        public final String trainId;
        public final double motoringKW;     // ≥ 0
        public final double brakingKW;      // ≤ 0 regen, >0 dissipativ
        public final double auxiliaryKW;    // ≥ 0
        public final Double positionMeters; // nullable
        public final Double speedMS;        // nullable

        public UpdateTrainPower(String trainId, double motoringKW, double brakingKW,
                                double auxiliaryKW, Double positionMeters, Double speedMS) {
            this.trainId = trainId;
            this.motoringKW = motoringKW;
            this.brakingKW = brakingKW;
            this.auxiliaryKW = auxiliaryKW;
            this.positionMeters = positionMeters;
            this.speedMS = speedMS;
        }

        // bakåtkompatibel
        public UpdateTrainPower(String trainId, double motoringKW, double brakingKW,
                                double auxiliaryKW, double positionMeters) {
            this(trainId, motoringKW, brakingKW, auxiliaryKW, Double.valueOf(positionMeters), null);
        }

    }

    public interface SolveReply {
    }

    public static final class Solved implements SolveReply {
        public final double timeSec;
        public final int step;

        public Solved(double timeSec, int step) {
            this.timeSec = timeSec;
            this.step = step;
        }
    }

    public static final class SolveTick implements Command {
        public final double timeSec;
        public final int step;
        public final ActorRef<SolveReply> replyTo; // nullable

        public SolveTick(double timeSec, int step) {
            this(timeSec, step, null);
        }

        public SolveTick(double timeSec, int step, ActorRef<SolveReply> replyTo) {
            this.timeSec = timeSec;
            this.step = step;
            this.replyTo = replyTo;
        }
    }

    public static final class SimulationFinished implements Command {
    }

    private static final double EPS = 1e-6;

    private static final class SolveBundle {
        final GridResult res;
        final Map<String, Double> brakeReqW; // begärd bromseffekt (W)
        final Map<String, Double> brakeNetW; // som faktiskt gick ut på nätet (W)
        final Map<String, Double> brakeResW; // som brändes i motstånd (W)

        SolveBundle(GridResult r,
                    Map<String, Double> req,
                    Map<String, Double> net,
                    Map<String, Double> resW) {
            this.res = r;
            this.brakeReqW = req;
            this.brakeNetW = net;
            this.brakeResW = resW;
        }
    }

    public static final class InstallTrainAnchor implements Command {
        public final String trainId;
        public final TrainAnchorComponent comp;
        public final double dtSec;

        public InstallTrainAnchor(String trainId, TrainAnchorComponent comp, double dtSec) {
            this.trainId = trainId;
            this.comp = comp;
            this.dtSec = dtSec;
        }
    }

    // ===== limits per tåg =====
    static final class TrainCfg {
        final double minV, maxV, maxA;

        TrainCfg(double minV, double maxV, double maxA) {
            this.minV = minV;
            this.maxV = maxV;
            this.maxA = maxA;
        }
    }

    private TrainCfg trainDefaults = new TrainCfg(850.0, 1000.0, 4000.0);
    private final Map<String, TrainCfg> trainOverrides = new HashMap<>();

    private static TrainCfg readTrainCfgFor(String id, Config defaults, Config overrides) {
        double minV = defaults.hasPath("cutoffVoltage") ? defaults.getDouble("cutoffVoltage") :
                (defaults.hasPath("minVoltage") ? defaults.getDouble("minVoltage") : 850.0);
        double maxV = defaults.hasPath("maxVoltage") ? defaults.getDouble("maxVoltage") : 1000.0;
        double maxA = defaults.hasPath("maxCurrentA") ? defaults.getDouble("maxCurrentA") : 4000.0;

        if (overrides != null && overrides.hasPath(id)) {
            Config oc = overrides.getConfig(id);
            if (oc.hasPath("cutoffVoltage")) minV = oc.getDouble("cutoffVoltage");
            if (oc.hasPath("minVoltage")) minV = oc.getDouble("minVoltage");
            if (oc.hasPath("maxVoltage")) maxV = oc.getDouble("maxVoltage");
            if (oc.hasPath("maxCurrentA")) maxA = oc.getDouble("maxCurrentA");
        }
        return new TrainCfg(minV, maxV, maxA);
    }

    // ===== state =====
    private final GridModel<Real> model;
    private final ElectricSolver solver;
    private final int anchorNodeId;
    private final Map<String, TrainLoad> trainDevices = new HashMap<>();
    private final Map<String, UpdateTrainPower> latest = new HashMap<>();
    private final ResultCsvWriter writer;


    // Carry-forward of last non-zero requested power to avoid transient 0 W when TA update lags
    private final Map<String, Double> lastRequestedPowerW = new HashMap<>();

    private final Map<String, TrainAnchorComponent> anchors = new LinkedHashMap<>();
    private double trainDtSec = 0.0;

    private final Set<String> warnedTrainOnBus = new HashSet<>();
    private final Set<Integer> substationBusNodes = new HashSet<>();

    // ===== factory =====
    public static Behavior<Command> create(GridModel<Real> model,
                                           ElectricSolver solver,
                                           String csvOutPath,
                                           int anchorNodeId) {
        return Behaviors.setup(ctx -> new GridModelActor(ctx, model, solver, csvOutPath, anchorNodeId));
    }


    public static Behavior<Command> create(GridModel<Real> model,
                                           DcElectricSolver legacySolver,
                                           String csvOutPath,
                                           int anchorNodeId) {
        return create(model, (ElectricSolver) legacySolver, csvOutPath, anchorNodeId);
    }

    private GridModelActor(ActorContext<Command> ctx,
                           GridModel<Real> model,
                           ElectricSolver solver,
                           String csvOutPath,
                           int anchorNodeId) throws IOException {
        super(ctx);
        this.model = model;
        this.solver = solver;
//        this.csvOutPath = csvOutPath;
        this.anchorNodeId = anchorNodeId;
        if (anchorNodeId == model.getGroundNodeId()) {
            throw new IllegalArgumentException("anchorNodeId must not be ground");
        }

        this.writer = new ResultCsvWriter(
                model,
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                true
        );

        // ev. nedprovning av CSV
        try {
            Config sys = ctx.getSystem().settings().config();
            Config root = sys.hasPath("dcsim") ? sys.getConfig("dcsim") : sys;
            int nth = -1;
            if (root.hasPath("simulationControl.csvEveryNthStep")) {
                nth = root.getInt("simulationControl.csvEveryNthStep");
            } else if (root.hasPath("export.csvEveryNthStep")) {
                nth = root.getInt("export.csvEveryNthStep");
            }
            if (nth > 0) writer.setEveryNthStep(nth);
        } catch (Throwable ignore) {
        }

        ctx.getLog().info("GridModelActor started. Writing to {}. Anchor node={} (config source=SYSTEM)",
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                anchorNodeId);

        // indexera bef. TrainLoad
        for (Object device : model.getDeviceIds()) {
            String id = String.valueOf(device);
            Device<Real> d = model.getDevice(id);
            if (d instanceof TrainLoad tl) {
                trainDevices.put(id, tl);
                registerTrainId(id);
            }
        }
        // substation-bus
        for (Object device : model.getDeviceIds()) {
            String id = String.valueOf(device);
            Device<Real> d = model.getDevice(id);
            if (d instanceof Substation ss) substationBusNodes.add(ss.getFromNode());
        }

        // limits från conf
        Config sys = ctx.getSystem().settings().config();
        Config root = sys.hasPath("dcsim") ? sys.getConfig("dcsim") : sys;

        Config trains = root.hasPath("trains") ? root.getConfig("trains") : ConfigFactory.empty();
        Config tdef = trains.hasPath("defaults") ? trains.getConfig("defaults") : ConfigFactory.empty();
        Config tovr = trains.hasPath("overrides") ? trains.getConfig("overrides") : null;
        this.trainDefaults = readTrainCfgFor("__defaults__", tdef, null);
        if (tovr != null) {
            for (String key : tovr.root().keySet()) {
                trainOverrides.put(key, readTrainCfgFor(key, tdef, tovr));
            }
        }

        // lås header
        writer.setKnownTrains(knownTrainIds);
    }

    // ===== receive =====
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnsureTrainDevice.class, this::onEnsureTrainDevice)
                .onMessage(UpdateTrainPower.class, this::onUpdateTrainPower)
                .onMessage(SolveTick.class, this::onSolveTick)
                .onMessage(SimulationFinished.class, this::onFinished)
                .onSignal(PostStop.class, sig -> onStopped())
                .onMessage(InstallTrainAnchor.class, msg -> {
                    anchors.put(msg.trainId, msg.comp);
                    this.trainDtSec = msg.dtSec;
                    this.solver.setTrainAnchors(anchors.entrySet(), this.trainDtSec);

                    // seed för header
                    registerTrainId(msg.trainId);
                    writer.setKnownTrains(knownTrainIds);

                    long share = anchors.values().stream()
                            .filter(c -> c.getAnchorNode() == msg.comp.getAnchorNode()).count();
                    if (share > 1) {
                        getContext().getLog().warn(
                                "Multiple TrainAnchorComponents share anchor node {} – risk för remap-kollision.",
                                msg.comp.getAnchorNode());
                    }
                    getContext().getLog().info("TrainAnchorComponent installed for {} (dt={} s)",
                            msg.trainId, trainDtSec);
                    return this;
                })
                .build();
    }

    private void registerTrainId(String id) {
        if (id != null && !id.isBlank()) knownTrainIds.add(id); // endast rena id:t, ex. "T1"
    }

    private TrainLoad ensureTrainDeviceInternal(String trainId) {
        TrainLoad tl = trainDevices.computeIfAbsent(trainId, id -> {
            int g = model.getGroundNodeId();
            TrainLoad ntl = new TrainLoad(id, anchorNodeId, g);
            TrainCfg cfg = trainOverrides.getOrDefault(id, trainDefaults);
            try {
                ntl.setCutoffVoltage(Real.fromDouble(cfg.minV));
            } catch (Throwable ignore) {
            }
            try {
                ntl.setMaxVoltage(Real.fromDouble(cfg.maxV));
            } catch (Throwable ignore) {
            }
            try {
                ntl.setMaxCurrent(Real.fromDouble(cfg.maxA));
            } catch (Throwable ignore) {
            }
            model.addDevice(ntl);
            getContext().getLog().info(
                    "Grid: created TrainLoad device for {} at {} (cutoff={}V, vmax={}V, Imax={}A)",
                    id, anchorNodeId, cfg.minV, cfg.maxV, cfg.maxA
            );
            return ntl;
        });

        if (ENABLE_TOPO_SANITY && substationBusNodes.contains(tl.getFromNode())) {
            String key = tl.getId() + "@" + tl.getFromNode();
            if (warnedTrainOnBus.add(key)) {
                getContext().getLog().warn(
                        "Train {} anchored at node {} which is also a substation bus – linjeströmmar kan kortslutas.",
                        tl.getId(), tl.getFromNode()
                );
            }
        }
        return tl;
    }

    private Behavior<Command> onEnsureTrainDevice(EnsureTrainDevice msg) {
        registerTrainId(msg.trainId);
        writer.setKnownTrains(knownTrainIds);
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onUpdateTrainPower(UpdateTrainPower msg) {
        // Debug: visa vad som kom in
        System.out.println("[GMA] onUpdateTrainPower recv train=" + msg.trainId
                + " mot=" + String.format(java.util.Locale.ROOT, "%.3f", msg.motoringKW) + "kW"
                + " brk=" + String.format(java.util.Locale.ROOT, "%.3f", msg.brakingKW) + "kW"
                + " aux=" + String.format(java.util.Locale.ROOT, "%.3f", msg.auxiliaryKW) + "kW");

        latest.put(msg.trainId, msg);
        System.out.println("[GMA] latest.size=" + latest.size() + " after put(" + msg.trainId + ")");

        registerTrainId(msg.trainId);
        writer.setKnownTrains(knownTrainIds);
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onSolveTick(SolveTick tick) {

        final double timeSec = tick.timeSec;
        final int step = tick.step;

        // Debug basic tick info
        System.out.printf("[GMA] onSolveTick t=%.3f latest.size=%d knownTrainIds=%d%n",
                timeSec, latest.size(), knownTrainIds.size());

        // Warn for missing train updates
        if (!knownTrainIds.isEmpty()) {
            Set<String> missing = new HashSet<>(knownTrainIds);
            missing.removeAll(latest.keySet());
            if (!missing.isEmpty())
                System.out.println("[GMA] Missing UpdateTrainPower for: " + missing);
        }

        // Prepare solver state for this tick
        DcIterativeSolver.clearProbeBranches();
        DcIterativeSolver.setSimTimeSec(timeSec);

        // Send anchors
        solver.setTrainAnchors(anchors.entrySet(), trainDtSec);

        // ================================================================
        // PASS 0: Build requested network power per train (including full regen)
        // ================================================================
        // ===== 1) Apply traction/braking/aux requests and build net P_W (PASS 0) =====
        Map<String, Double> reqW_direct = new LinkedHashMap<>();

        for (String trainId : knownTrainIds) {

            UpdateTrainPower u = latest.get(trainId);

            // Motoring and auxiliaries as-is (normally ≥ 0)
            double motKW    = (u != null) ? u.motoringKW   : 0.0;
            double auxKW    = (u != null) ? u.auxiliaryKW  : 0.0;

            // brakingKW from upstream is interpreted as braking *magnitude* (≥ 0)
//            double brkMagKW = (u != null) ? u.brakingKW : 0.0;

            // FULL regenerative braking candidate: all braking goes towards the DC network.
            // Rectifier logic (solveWithRectifierBlocks) will later limit this based on receptivity.
            double brkRegenKW = (u != null) ? u.brakingKW : 0.0;   // ≤ 0 during braking

            // Update TrainLoad – negative brakingKW means regenerative braking.
            TrainLoad tl = ensureTrainDeviceInternal(trainId);
            tl.setRequestedComponents(motKW, brkRegenKW, auxKW);

            // Net electrical power with respect to the DC network [W]:
            // > 0 : network → train (motoring)
            // < 0 : train → network (regeneration)
            double train_p_req_net_W = (motKW + auxKW + brkRegenKW) * 1000.0;

            // NO clamping – negative power is exactly what we want for regen
            reqW_direct.put(trainId, train_p_req_net_W);
            lastRequestedPowerW.put(trainId, train_p_req_net_W);

            // Simple debug (optional)
            System.out.printf(
                    "[GMA-A] id=%s mot=%.3f kW brkMag=%.3f kW -> brkRegen=%.3f kW aux=%.3f kW -> push %.1f W%n",
                    trainId, motKW, brkRegenKW, brkRegenKW, auxKW, train_p_req_net_W
            );

            LongTableWriter w = lw();
            if (w != null) {
                w.signalRow(timeSec, "Train", trainId,
                        "brk_prof_kW", brkRegenKW, "kW", "GMA-prof", null, null);
                double pTractionW = (motKW + brkRegenKW + auxKW) * 1000.0; // braking negative
                w.signalRow(timeSec, "Train", trainId,
                        "req_trac_W", pTractionW, "W", "GMA-A", null, null);
            }
        }

        // PASS 0: send to solver
        solver.setTrainRequestedPower(reqW_direct, trainDtSec);

        // ================================================================
        // PASS 1: Run 2-step rectifier logic (receptivity, regen limiting)
        // ================================================================
        final SolveBundle sb;
        try {
            sb = solveWithRectifierBlocks(timeSec, tick.step);
        } catch (Exception ex) {
            getContext().getLog().error("Solve failed at t={}s step={}: {}", timeSec, tick.step, ex.toString());
            return this;
        }

        GridResult res = sb.res;

        // ================================================================
        // LOG: P_net_W for trains
        // ================================================================

        LongTableWriter w = lw();
        if (w != null) {
            for (String trainId : trainDevices.keySet()) {
                Real pDev = res.getLatestDevicePower(trainId);
                if (pDev != null) {
                    w.signalRow(timeSec, "Train", trainId,
                            "P_net_W", pDev.asDouble(), "W", "NET", tick.step, null);
                }
            }
        }

        // --- Train voltages: TrainX.V_V ---
        if (w != null) {
            double t = tick.timeSec;

            for (String trainId : knownTrainIds) {
                TrainLoad tl = trainDevices.get(trainId);
                if (tl == null) continue;

                // Todo: generalisera. Hämta nod-id för tåget (anpassa till ditt API)
                int nodeId = 3; //tl.getFromNode();

                Real vNode = res.getLatestNodeVoltage(nodeId);
                if (vNode == null) continue;

                double v = vNode.asDouble();

                w.signalRow(
                        t,
                        "Train",      // object_type
                        trainId,      // object_id
                        "V_V",        // signal
                        v,            // value
                        "V",          // unit
                        "NET",        // stage
                        tick.step,    // iter
                        null          // note
                );
            }
        }


        // Save brake maps (magnitudes)
        writer.setLatestBrakeMaps(sb.brakeReqW, sb.brakeNetW, sb.brakeResW);

        // ================================================================
        // LOG final brake split (negative values for braking)
        // ================================================================
        if (w != null) {
            for (String trainId : sb.brakeReqW.keySet()) {
                double reqMag = sb.brakeReqW.get(trainId);
                double netMag = sb.brakeNetW.get(trainId);
                double resMag = sb.brakeResW.get(trainId);

                w.signalRow(timeSec, "Train", trainId,
                        "P_brk_req_W", -reqMag, "W", "NET", tick.step, null);
                w.signalRow(timeSec, "Train", trainId,
                        "P_brk_net_W", -netMag, "W", "NET", tick.step, null);
                w.signalRow(timeSec, "Train", trainId,
                        "P_brk_res_W", -resMag, "W", "NET", tick.step, null);
            }
        }

        // ================================================================
        // Cache node voltages
        // ================================================================
        for (Object node : model.getNodeIds()) {
            int nid = (node instanceof Integer) ? (Integer) node : Integer.parseInt(node.toString());
            Real v = res.getLatestNodeVoltage(nid);
            if (v != null) lastNodeV.put(nid, v.asDouble());
        }

//        lastTickTime = timeSec;

        // Totals, sanity checks, etc.
        try {
            var totals = PowerAccounting.compute(model, res);
            res.addTotals(totals);

            if (ENABLE_POWER_SANITY) {
                checkSubstationPowerSanity(res, timeSec);
                checkTrainLineFlowSanity(res);
            }

        } catch (Exception ex) {
            getContext().getLog().warn("Post-process failed at t={} step={}: {}", timeSec, tick.step, ex.toString());
        }

        // Write CSV
        try {
            writer.append(res, timeSec, tick.step);
            writer.flush();
        } catch (IOException ex) {
            getContext().getLog().error("CSV write failed: {}", ex.toString());
        }

        // ACK
        if (tick.replyTo != null)
            tick.replyTo.tell(new Solved(timeSec, tick.step));

        return this;
    }

    // === Brake splitting helpers ===

    /**
     * Sant om minst en substation leder (diodlikriktare) och tillåter backfeed.
     */
    // === Brake splitting helpers (unchanged) ===
    static final class BrakeSplit {
        final double netW;  // actual export to the DC net (W)
        final double resW;  // burned in braking resistor (W)

        BrakeSplit(double netW, double resW) {
            this.netW = netW;
            this.resW = resW;
        }
    }

    static BrakeSplit computeBrakeSplit(double reqRegenW, double pFromNetW, double Va, double Vmax, boolean receptive) {
        final double EPS = 1e-6;
        if (reqRegenW <= 0.0) return new BrakeSplit(0.0, 0.0);

        double exportedBySolver = (pFromNetW < 0.0) ? (-pFromNetW) : 0.0; // solver sign: +motoring, −regen
        boolean overVmax = Va >= Vmax - EPS;

        if (overVmax || !receptive) {
            return new BrakeSplit(0.0, reqRegenW); // everything to resistor
        }
        double netW = Math.min(exportedBySolver, reqRegenW);
        double resW = Math.max(0.0, reqRegenW - netW);
        return new BrakeSplit(netW, resW);
    }

    /**
     * Try to read "allowBackfeed" from Substation via reflection. Defaults to false if absent.
     */
    private boolean allowBackfeed(Substation ss) {
        try {
            try {
                var m = ss.getClass().getMethod("isBackfeedAllowed");
                Object o = m.invoke(ss);
                if (o instanceof Boolean) return (Boolean) o;
            } catch (NoSuchMethodException ignore) {
            }

            try {
                var m = ss.getClass().getMethod("getAllowBackfeed");
                Object o = m.invoke(ss);
                if (o instanceof Boolean) return (Boolean) o;
            } catch (NoSuchMethodException ignore) {
            }

            try {
                var m = ss.getClass().getMethod("isAllowBackfeed");
                Object o = m.invoke(ss);
                if (o instanceof Boolean) return (Boolean) o;
            } catch (NoSuchMethodException ignore) {
            }
        } catch (Throwable ignore) {
        }
        // Conservative fallback if the flag isn't available in your class
        return false;
    }

    /**
     * True if at least one substation is receptive (diode forward-biased AND backfeed allowed).
     */
    private boolean isAnySubstationReceptive(GridResult res) {
        final double EPS = 1e-6;
        for (Object did : model.getDeviceIds()) {
            Device<Real> dev = model.getDevice(String.valueOf(did));
            if (!(dev instanceof Substation ss)) continue;

            if (!allowBackfeed(ss)) continue;           // blocks backfeed ⇒ not receptive
            Real vBusR = res.getLatestNodeVoltage(ss.getFromNode());
            if (vBusR == null) continue;

            double vBus = vBusR.asDouble();
            double emf = ss.getEmf().asDouble();

            // Diode rectifier “conducts" only when bus < emf
            if (vBus < emf - EPS) return true;
        }
        return false;
    }

    /**
     * Summerar aktuell positiv tågeffekt (motoring) från ALLA tåg utom 'excludeId'.
     */
    private double currentPositiveTrainDemandExcluding(GridResult res, String excludeId) {
        double sum = 0.0;

        // TrainLoad-devices
        for (var e : trainDevices.entrySet()) {
            String id = e.getKey();
            if (id.equals(excludeId)) continue;
            Real P = res.getLatestDevicePower(id);
            if (P != null) {
                double w = P.asDouble();
                if (w > 0.0) sum += w;
            }
        }

        // Ankare (icke-Device)
        for (var e : anchors.entrySet()) {
            String id = e.getKey();
            if (id.equals(excludeId)) continue;
            double w = e.getValue().getPowerW();
            if (w > 0.0) sum += w;
        }

        return sum;
    }

    // ===== helpers =====
    private Double posFromProfile(String trainId) {
        UpdateTrainPower u = latest.get(trainId);
        return (u != null) ? u.positionMeters : null;
    }

    private Double velFromProfile(String trainId) {
        UpdateTrainPower u = latest.get(trainId);
        return (u != null) ? u.speedMS : null;
    }

    private static double tryGetAbsOrLocalX(TrainAnchorComponent c) {
        try {
            Method m = c.getClass().getMethod("getAbsoluteProgressM");
            Object v = m.invoke(c);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Throwable ignore) {
        }
        try {
            Method m2 = c.getClass().getMethod("getXM");
            Object v2 = m2.invoke(c);
            if (v2 instanceof Number) return ((Number) v2).doubleValue();
        } catch (Throwable ignoreToo) {
        }
        return 0.0;
    }

    private Behavior<Command> onFinished(SimulationFinished f) {
        getContext().getLog().info("GridModelActor: SimulationFinished received. Closing writer.");
        try {
            writer.close();
        } catch (Exception ignore) {
        }
        return Behaviors.stopped();
    }

    private Behavior<Command> onStopped() {
        try {
            writer.close();
        } catch (Exception ignore) {
        }
        return this;
    }

    private void checkSubstationPowerSanity(GridResult res, double tSec) {
        for (Object device : model.getDeviceIds()) {
            String deviceId = String.valueOf(device);
            Device<Real> dev = model.getDevice(deviceId);
            if (!(dev instanceof Substation ss)) continue;

            double E = ss.getEmf().asDouble();
            double R = ss.getInternalResistance().asDouble();
            if (R <= 0) continue;

            Real pR = res.getLatestDevicePower(ss.getId());
            if (pR == null) continue;
            double P = pR.asDouble();
            double Pmax = (E * E) / (4.0 * R);
            int headroomPct = (int) Math.round(P_HEADROOM_FACTOR * 100.0);

            if (Math.abs(P) > P_HEADROOM_FACTOR * Pmax) {
                double Va = res.getLatestNodeVoltage(ss.getFromNode()).asDouble();
                double Vb = res.getLatestNodeVoltage(ss.getToNode()).asDouble();
                double dV = Va - Vb;
                String tStr = String.format(Locale.ROOT, "%.3f", tSec);
                getContext().getLog().error(
                        "Power sanity: Substation {} has |P|={} W > {}% of Pmax={} W at t={} s. (E={} V, R={} ohm, Va-Vb={} V).",
                        ss.getId(), Math.abs(P), headroomPct, Pmax, tStr, E, R, dV
                );
            }
        }
    }

    private void checkTrainLineFlowSanity(GridResult res) {
        double sumTrainAbs = 0.0;
        for (Object device : model.getDeviceIds()) {
            String id = String.valueOf(device);
            Device<Real> d = model.getDevice(id);
            if (d instanceof TrainLoad) {
                Real p = res.getLatestDevicePower(d.getId());
                if (p != null) sumTrainAbs += Math.abs(p.asDouble());
            }
        }
        if (sumTrainAbs < SMALL_POWER_W) return;

        double sumAbsILines = 0.0;
        for (Object device : model.getDeviceIds()) {
            String id = String.valueOf(device);
            Device<Real> d = model.getDevice(id);
            if (d instanceof Line) {
                Real i = res.getLatestDeviceCurrent(d.getId());
                if (i != null) sumAbsILines += Math.abs(i.asDouble());
            }
        }
        if (sumAbsILines < SMALL_CURRENT_A) {
            getContext().getLog().warn(
                    "Train(s) exchange significant power but summed line current ≈ 0 A. Likely anchored on the substation bus or topology issue."
            );
        }
    }

    /**
     * Tvåstegslösning med "rektifierblock":
     * 1) Lös basfallet och mät nätets receptivitet
     * 2) Om export > receptivitet: proportionellt strypa regen och lös igen
     * Återger även per-tåg delning (req/net/res).
     */
    private SolveBundle solveWithRectifierBlocks(double timeSec, int step) {
        System.out.printf("[RECT] latest.size=%d%n", latest.size());

        // ----------------------------------------------------
        // PASS 0: Lös basfallet med nuvarande train requests
        // ----------------------------------------------------
//        System.out.printf("[DBG] t=%.1f requested=%s%n", timeSec, requestedPowerW);
        GridResult r0 = solver.solve(model, timeSec, step);

        // per-tåg: bromsbegäran (magnitud, W) och faktisk export i basfallet (magnitud, W)
        Map<String, Double> brkReqMagW   = new LinkedHashMap<>();
        Map<String, Double> netExport0W  = new LinkedHashMap<>();

        double sumExportW    = 0.0; // totalt tåg → nät (regen), W (magnitud)
        double sumMotoringW  = 0.0; // totalt nät → tåg (motoring), W (magnitud)

        // --- Samla info per tåg från PASS 0 ---
        for (var e : latest.entrySet()) {
            String id = e.getKey();
            UpdateTrainPower u = e.getValue();

            // 1) Begärd bromseffekt (magnitud, W).
            //    brakingKW > 0  = ren resistiv broms (allt går i resistor, ingen regen)
            //    brakingKW < 0  = regenerativ bromsbegäran (magnitud = -brakingKW)
            double brkReqMag = 0.0;
            if (u != null) {
                if (u.brakingKW > 0.0) {
                    // resistiv broms: begäran till resistor
                    brkReqMag = u.brakingKW * 1000.0;
                } else if (u.brakingKW < 0.0) {
                    // regen-begäran: magnitud
                    brkReqMag = -u.brakingKW * 1000.0;
                }
            }
            brkReqMagW.put(id, brkReqMag);

            // 2) Enhetskraft i baslösningen:
            //    p > 0  = last (nät -> tåg)
            //    p < 0  = export (tåg -> nät, regen)
            Real pDev = r0.getLatestDevicePower(id);
            double p = (pDev != null) ? pDev.asDouble() : 0.0;

            if (p > 0.0) {
                sumMotoringW += p;  // positiv = last
            }

            double exportMag = (p < 0.0) ? (-p) : 0.0; // magnitud av tåg→nät
            netExport0W.put(id, exportMag);
            sumExportW += exportMag;
        }
        System.out.printf("[RECT] brkReqMagW.size=%d netExport0W.size=%d%n",
                brkReqMagW.size(), netExport0W.size());

        // --- Substationers absorption i PASS 0 ---
        // p < 0 => substation tar emot effekt (t.ex. mot AC-sidan)
        double sumSubAbsorbW = 0.0;
        for (Object did : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(String.valueOf(did));
            if (d instanceof Substation) {
                Real p = r0.getLatestDevicePower(d.getId());
                if (p != null && p.asDouble() < 0.0) {
                    sumSubAbsorbW += (-p.asDouble());
                }
            }
        }

        // Receptivitet = annan förbrukning (motoringtåg) + substationers absorption
        double receptivityW = sumMotoringW + sumSubAbsorbW;

        System.out.printf("[RECT] sumExportW=%.1f sumMotoringW=%.1f sumSubAbsorbW=%.1f receptivityW=%.1f%n",
                sumExportW, sumMotoringW, sumSubAbsorbW, receptivityW);

        // ----------------------------------------------------
        // FALL A: basfallet ryms inom receptiviteten
        //         -> inga justeringar, använd PASS 0 direkt
        // ----------------------------------------------------
        if (sumExportW <= receptivityW + EPS) {
            Map<String, Double> netMagW = new LinkedHashMap<>();
            Map<String, Double> resMagW = new LinkedHashMap<>();

            for (String id : brkReqMagW.keySet()) {
                double reqMag = brkReqMagW.getOrDefault(id, 0.0);     // W >= 0
                double netMag = netExport0W.getOrDefault(id, 0.0);    // W >= 0 (tåg -> nät)
                double resMag = Math.max(0.0, reqMag - netMag);       // W >= 0

                netMagW.put(id, netMag);
                resMagW.put(id, resMag);
            }

            // SolveBundle behåller magnituderna (bakåtkompatibelt internt),
            // även om longtable får negativa värden.
            return new SolveBundle(r0, brkReqMagW, netMagW, resMagW);
        }

        // ----------------------------------------------------
        // FALL B: vi måste strypa regen (sumExportW > receptivityW)
        //         -> skala ner varje tågs export proportionellt
        //            och lös igen (PASS 1)
        // ----------------------------------------------------
        double factor = (receptivityW <= EPS || sumExportW <= EPS)
                ? 0.0
                : Math.min(1.0, receptivityW / sumExportW);

        System.out.printf("[RECT] regen limiting factor=%.6f%n", factor);

        // Skala ner varje tågs nät-export proportionellt i kW-komponenterna
        for (var e : latest.entrySet()) {
            String id = e.getKey();
            UpdateTrainPower u = e.getValue();
            TrainLoad tl = trainDevices.get(id);
            if (tl == null || u == null) continue;

            double exportMag0 = netExport0W.getOrDefault(id, 0.0); // W
            double allowedExportMag = exportMag0 * factor;         // W
            double regenKW = -allowedExportMag / 1000.0;           // <0 = regen-komponent

            // Behåll motoring/aux som i begäran, ersätt bara regen-komponenten
            tl.setRequestedComponents(u.motoringKW, regenKW, u.auxiliaryKW);
        }

        // Bygg total begärd effekt per tåg efter regen-begränsning (för loggning/debug).
        Map<String, Double> reqW_direct2 = new LinkedHashMap<>();
        for (var e : latest.entrySet()) {
            String id2 = e.getKey();
            UpdateTrainPower u2 = e.getValue();
            if (u2 == null) continue;

            double exportMag0 = netExport0W.getOrDefault(id2, 0.0);
            double allowedExportMag = exportMag0 * factor;
            double regenKW2 = -allowedExportMag / 1000.0;

            double pTotW2 = (u2.motoringKW + regenKW2 + u2.auxiliaryKW) * 1000.0;
            reqW_direct2.put(id2, pTotW2);

            System.out.printf(
                    "[NET] id=%s mot=%.3f kW brkAdj=%.3f kW aux=%.3f kW -> push %.1f W (factor=%.6f)%n",
                    id2, u2.motoringKW, regenKW2, u2.auxiliaryKW, pTotW2, factor
            );
            LongTableWriter w = lw();
            if (w != null) {
                w.signalRow(timeSec, "Train", id2,
                        "req_W", pTotW2, "W", "NET", step, null);
            }
        }

        System.out.printf("[NET] pushing requestedPowerW2: %s%n", reqW_direct2);
        this.solver.setTrainRequestedPower(reqW_direct2, this.trainDtSec);

        // PASS 1: lös med begränsad regen
        GridResult r1 = solver.solve(model, timeSec, step);

        // Slutlig delning: använd FAKTISK export efter PASS 1
        Map<String, Double> netMagW_final = new LinkedHashMap<>();
        Map<String, Double> resMagW_final = new LinkedHashMap<>();

        for (String id : brkReqMagW.keySet()) {
            Real pDev1 = r1.getLatestDevicePower(id);
            double p1 = (pDev1 != null) ? pDev1.asDouble() : 0.0;

            double netMag = (p1 < 0.0) ? (-p1) : 0.0; // W, faktisk tåg→nät efter begränsning
            double reqMag = brkReqMagW.getOrDefault(id, 0.0);      // W, begärd broms
            double resMag = Math.max(0.0, reqMag - netMag);        // W, till resistor

            netMagW_final.put(id, netMag);
            resMagW_final.put(id, resMag);
        }

        return new SolveBundle(r1, brkReqMagW, netMagW_final, resMagW_final);
    }

}