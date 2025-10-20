package org.dcsim.actors;

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

/**
 * Äger DC-nätmodellen och kör lösaren varje tick.
 * VIKTIGT: Alla tågsignaler skrivs under det rena id:t (t.ex. "T1") – aldrig "Train:T1" eller "Train1".
 * Tågspänningen V[T] tas alltid som bussspänning vid tågets node.
 * Bromsrapport: P_brake_req / _net / _resistor per tåg och tick skickas till ResultCsvWriter.
 */
public class GridModelActor extends AbstractBehavior<GridModelActor.Command> {

    // ===== debug/sanity =====
    private static final boolean ENABLE_TOPO_SANITY = true;
    private static final boolean ENABLE_POWER_SANITY = true;
    private static final double P_HEADROOM_FACTOR = 1.20;
    private static final double SMALL_CURRENT_A = 1e-3;
    private static final double SMALL_POWER_W = 1e3;

    private final Map<Integer, Double> lastNodeV = new HashMap<>();
    private final Map<String, Double> lastPosM = new HashMap<>();
    private Double lastTickTime = null;

    // Stabil header i CSV (bara rena id:n, t.ex. "T1")
    private final LinkedHashSet<String> knownTrainIds = new LinkedHashSet<>();

    // uppe i klassen
    private static final double BF_EPS_A = 1e-6;  // tolerans för backfeed (A)
    private static final int MAX_BF_ITERS = 3;


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
    private final DcElectricSolver solver;
    private final int anchorNodeId;
    private final Map<String, TrainLoad> trainDevices = new HashMap<>();
    private final Map<String, UpdateTrainPower> latest = new HashMap<>();
    private final ResultCsvWriter writer;

    private final Map<String, TrainAnchorComponent> anchors = new LinkedHashMap<>();
    private double trainDtSec = 0.0;

    private final Set<String> warnedTrainOnBus = new HashSet<>();
    private final Set<Integer> substationBusNodes = new HashSet<>();

    private boolean treatNegBrakingAsBrake = false; // rapportera regen som "brake" (endast för visning)

    // ===== factory =====
    public static Behavior<Command> create(GridModel<Real> model,
                                           DcElectricSolver solver,
                                           String csvOutPath,
                                           int anchorNodeId) {
        return Behaviors.setup(ctx -> new GridModelActor(ctx, model, solver, csvOutPath, anchorNodeId));
    }

    private GridModelActor(ActorContext<Command> ctx,
                           GridModel<Real> model,
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

        // exportflaggor
        boolean negAsBrake = false;
        try {
            if (root.hasPath("export.treatNegativeBrakingAsBrake"))
                negAsBrake = root.getBoolean("export.treatNegativeBrakingAsBrake");
        } catch (Throwable ignore) {
        }
        this.treatNegBrakingAsBrake = negAsBrake;

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
                    this.solver.setTrainAnchors(anchors.values(), msg.dtSec);

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
        latest.put(msg.trainId, msg);
        registerTrainId(msg.trainId);
        writer.setKnownTrains(knownTrainIds);
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onSolveTick(SolveTick tick) {
        // 1) applicera senaste begäran till TrainLoad (profilstyrda)
        for (Map.Entry<String, UpdateTrainPower> e : latest.entrySet()) {
            TrainLoad tl = ensureTrainDeviceInternal(e.getKey());
            UpdateTrainPower u = e.getValue();
            tl.setRequestedComponents(u.motoringKW, u.brakingKW, u.auxiliaryKW);
        }

        // per-tåg bromsmappar (W)
        final Map<String, Double> pBrakeReqW = new HashMap<>();
        final Map<String, Double> pBrakeNetW = new HashMap<>();
        final Map<String, Double> pBrakeResW = new HashMap<>();

// --- lös med rektifierblock: grundlös -> mät receptivitet -> ev. stryp regen -> lös igen
        final SolveBundle sb;
        try {
            sb = solveWithRectifierBlocks(tick.timeSec, tick.step);
        } catch (Exception ex) {
            getContext().getLog().error(
                    "Solve failed at t={}s step={}: {}",
                    tick.timeSec, tick.step, ex.toString()
            );
            return this;
        }

// resultatet från tvåstegaren
        final GridResult res = sb.res;

// uppdatera writer med faktisk bromsdelning (W)
        writer.setLatestBrakeMaps(sb.brakeReqW, sb.brakeNetW, sb.brakeResW);

// (frivilligt) cachea nodspänningar om du gjorde det tidigare
        for (Object node : model.getNodeIds()) {
            int nid = (node instanceof Integer) ? (Integer) node : Integer.parseInt(node.toString());
            var vv = res.getLatestNodeVoltage(nid);
            if (vv != null) lastNodeV.put(nid, vv.asDouble());
        }

// uppdatera tick-tid (om du använder den senare i metoden)
        lastTickTime = tick.timeSec;

// ====== VIKTIGT ======
// Låt resten av din onSolveTick(...) (totals/sanity, writer.append/flush, replyTo.tell(...))
// ligga kvar precis som tidigare, efter detta block.

        // totals + sanity
        try {
            var totals = PowerAccounting.compute(model, res);
            res.addTotals(totals);

            if (ENABLE_POWER_SANITY) {
                checkSubstationPowerSanity(res, tick.timeSec);
                checkTrainLineFlowSanity(res);
            }
        } catch (Exception ex) {
            getContext().getLog().warn("Post-process failed at t={}s step={}: {}", tick.timeSec, tick.step, ex.toString());
        }

        // CSV
        try {
            writer.append(res, tick.timeSec, tick.step);
            writer.flush();
        } catch (IOException ex) {
            getContext().getLog().error("CSV write failed: {}", ex.toString());
        }

        // ack
        if (tick.replyTo != null) tick.replyTo.tell(new Solved(tick.timeSec, tick.step));
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
        // ---------- Pass 0: lös basfallet ----------
        GridResult r0 = solver.solve(model, timeSec, step);

        // per-tåg begäran (W) och faktiskt export i basfallet (W)
        java.util.Map<String, Double> reqW = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> netW0 = new java.util.LinkedHashMap<>();

        double sumExport = 0.0;   // totalt tåg → nät
        double sumMotoring = 0.0;   // totalt nät → tåg (positiv last)

        for (var e : latest.entrySet()) {
            String id = e.getKey();
            UpdateTrainPower u = e.getValue();

            // Begärd bromseffekt (W): positivt oavsett om den kommer som +ohmisk eller −regen i profilen
            double req = 0.0;
            if (u != null) {
                if (u.brakingKW > 0.0) req = u.brakingKW * 1000.0;    // dissipativ begäran
                if (u.brakingKW < 0.0) req = -u.brakingKW * 1000.0;    // regen-begäran (magnitud)
            }
            reqW.put(id, req);

            // Enhetskraft i baslösningen (+förbrukning från nätet, −export till nätet)
            Real pDev = r0.getLatestDevicePower(id);
            double p = (pDev != null) ? pDev.asDouble() : 0.0;

            if (p > 0.0) sumMotoring += p;                 // positiv = last
            double export = (p < 0.0) ? (-p) : 0.0;        // export = −P
            netW0.put(id, export);
            sumExport += export;
        }

        // Substationers absorption i baslösningen (om backfeed tillåts blir detta > 0)
        double sumSubAbsorb = 0.0;
        for (Object did : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(String.valueOf(did));
            if (d instanceof Substation) {
                Real p = r0.getLatestDevicePower(d.getId());
                if (p != null && p.asDouble() < 0.0) sumSubAbsorb += (-p.asDouble());
            }
        }

        // Receptivitet = annan förbrukning (motoringtåg) + substationers absorption
        double receptivity = sumMotoring + sumSubAbsorb;

        // Om basfallet redan ryms i receptiviteten: inget att strypa
        if (sumExport <= receptivity + EPS) {
            java.util.Map<String, Double> resW = new java.util.LinkedHashMap<>();
            for (String id : reqW.keySet()) {
                double net = netW0.getOrDefault(id, 0.0);
                double res = Math.max(0.0, reqW.get(id) - net);
                resW.put(id, res);
            }
            return new SolveBundle(r0, reqW, netW0, resW);
        }

        // ---------- Pass 1: proportionellt strypa regen och lös igen ----------
        double factor = (receptivity <= EPS || sumExport <= EPS)
                ? 0.0 : Math.min(1.0, receptivity / sumExport);

        // Skala ner varje tågs nät-export (regen) proportionellt
        for (var e : latest.entrySet()) {
            String id = e.getKey();
            UpdateTrainPower u = e.getValue();
            TrainLoad tl = trainDevices.get(id);
            if (tl == null || u == null) continue;

            double allowedNetW = netW0.getOrDefault(id, 0.0) * factor;  // W till nät
            double regenKW = -allowedNetW / 1000.0;               // <0 = regen-komponent
            // behåll motoring/aux som i begäran
            tl.setRequestedComponents(u.motoringKW, regenKW, u.auxiliaryKW);
        }

        GridResult r1 = solver.solve(model, timeSec, step);

        // Slutlig delning: net = netW0*factor, res = req − net
        java.util.Map<String, Double> netW = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> resW = new java.util.LinkedHashMap<>();
        for (String id : reqW.keySet()) {
            double net = netW0.getOrDefault(id, 0.0) * factor;
            double res = Math.max(0.0, reqW.get(id) - net);
            netW.put(id, net);
            resW.put(id, res);
        }

        return new SolveBundle(r1, reqW, netW, resW);
    }

}
