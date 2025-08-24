package org.dcsim.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import org.apache.commons.math3.linear.*;
import org.dcsim.electric.*;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;

import java.io.IOException;
import java.util.*;

/**
 * Owns the electrical grid model and runs the DC solver each tick.
 * - Receives per-train power requests (motoring, braking, auxiliaries)
 * - Stamps/solves the network
 * - Reconstructs/calc device powers if solver didn’t store them
 * - Writes CSV rows each tick
 */
public class GridModelActor extends AbstractBehavior<GridModelActor.Command> {

    // ===== Protocol =====
    public interface Command {}

    /** Ensure a TrainLoad device exists in the model for a given train id. */
    public static final class EnsureTrainDevice implements Command {
        public final String trainId;
        public EnsureTrainDevice(String trainId) { this.trainId = trainId; }
    }

    /** Latest power request from a train (kW). brakingKW is NEGATIVE for regen. */
    public static final class UpdateTrainPower implements Command {
        public final String trainId;
        public final double motoringKW;   // ≥ 0
        public final double brakingKW;    // ≤ 0 (regen)
        public final double auxiliaryKW;  // ≥ 0
        public final double positionMeters;
        public UpdateTrainPower(String trainId, double motoringKW, double brakingKW,
                                double auxiliaryKW, double positionMeters) {
            this.trainId = trainId;
            this.motoringKW = motoringKW;
            this.brakingKW = brakingKW;
            this.auxiliaryKW = auxiliaryKW;
            this.positionMeters = positionMeters;
        }
    }

    /** Trigger a solve step and append a CSV row. */
    public static final class SolveTick implements Command {
        public final double timeSec;
        public final int step;
        public SolveTick(double timeSec, int step) {
            this.timeSec = timeSec;
            this.step = step;
        }
    }

    /** Graceful shutdown: close writer. */
    public static final class SimulationFinished implements Command {}

    // ===== State =====
    private final GridModel model;
    private final DcElectricSolver solver;
    private final int anchorNodeId; // physical busbar/catenary node (not ground)
    private final Map<String, TrainLoad> trainDevices = new HashMap<>();
    private final Map<String, UpdateTrainPower> latest = new HashMap<>();
    private final ResultCsvWriter writer;

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
                           int anchorNodeId) {
        super(ctx);
        this.model = model;
        this.solver = solver;
        this.anchorNodeId = anchorNodeId;
        if (anchorNodeId == model.getGroundNodeId()) {
            throw new IllegalArgumentException("anchorNodeId must not be ground");
        }
        this.writer = new ResultCsvWriter(model,
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                true);
        ctx.getLog().info("GridModelActor started. Writing to {}. Anchor node={}",
                (csvOutPath != null ? csvOutPath : "output/electrical.csv"),
                anchorNodeId);

        // Index any pre-created TrainLoad devices in the model
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
                .onMessage(EnsureTrainDevice.class, this::onEnsureTrainDevice)
                .onMessage(UpdateTrainPower.class, this::onUpdateTrainPower)
                .onMessage(SolveTick.class, this::onSolveTick)
                .onMessage(SimulationFinished.class, this::onFinished)
                .onSignal(PostStop.class, sig -> onPostStop())
                .build();
    }

    private TrainLoad ensureTrainDeviceInternal(String trainId) {
        return trainDevices.computeIfAbsent(trainId, id -> {
            int g = model.getGroundNodeId();
            TrainLoad tl = new TrainLoad(id, anchorNodeId, g);
            model.addDevice(tl);
            getContext().getLog().info("Grid: created TrainLoad device for {} at {} -> gnd", id, anchorNodeId);
            return tl;
        });
    }

    private Behavior<Command> onEnsureTrainDevice(EnsureTrainDevice msg) {
        if (!trainDevices.containsKey(msg.trainId)) {
            int g = model.getGroundNodeId();
            int anchor = pickAnchorNodeId(); // first non-ground node
            TrainLoad tl = new TrainLoad(msg.trainId, anchor, g);
            model.addDevice(tl);
            trainDevices.put(msg.trainId, tl);
            getContext().getLog().info("Grid: pre-created TrainLoad {} at {} -> gnd", msg.trainId, anchor);
        }
        return this;
    }

    private int pickAnchorNodeId() {
        for (int id : model.getNodeIds()) if (id != model.getGroundNodeId()) return id;
        return model.getGroundNodeId(); // fallback (should not happen)
    }

    private Behavior<Command> onUpdateTrainPower(UpdateTrainPower msg) {
        latest.put(msg.trainId, msg);
        ensureTrainDeviceInternal(msg.trainId);
        return this;
    }

    private Behavior<Command> onSolveTick(SolveTick tick) {
        // 1) Apply latest train requests (solver uses TrainLoad.requested* internally if needed)
        for (Map.Entry<String, UpdateTrainPower> e : latest.entrySet()) {
            TrainLoad tl = ensureTrainDeviceInternal(e.getKey());
            UpdateTrainPower u = e.getValue();
            tl.setRequestedComponents(u.motoringKW, u.brakingKW, u.auxiliaryKW);
            // NOTE: u.positionMeters could be used later to move the train’s anchor node
        }

        // 2) Solve
        final GridResult res;
        try {
            res = solver.solve(model, tick.timeSec, tick.step);
        } catch (Exception ex) {
            getContext().getLog().error("Solve failed at t={}s step={}: {}", tick.timeSec, tick.step, ex.toString());
            return this;
        }

        // 3) Ensure voltages & device powers if solver didn’t populate them
        try {
            populateVoltagesIfMissing(res);
            populatePowers(res);
        } catch (Exception ex) {
            getContext().getLog().warn("Post-process failed at t={}s step={}: {}", tick.timeSec, tick.step, ex.toString());
        }

        // 4) Write CSV (flush each row to be safe for short runs)
        try {
            writer.append(res, tick.timeSec, tick.step);
            writer.flush();
        } catch (IOException ex) {
            getContext().getLog().error("CSV write failed: {}", ex.toString());
        }

        return this;
    }

    // ---- helpers ----

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

        // Eliminate ground row/col and solve reduced system
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

    /**
     * Beräkna effekt/ström för substationer, linjer och tåg utifrån voltages + senaste train-request.
     * - Substation: diode (ingen backfeed), P_out = I*Vnode
     * - Line: I = (Va-Vb)/R, P_loss = I^2*R
     * - Train: I ≈ P_req/ΔV (tecken bibehålls), P[T] = I*ΔV; P_brake[T] skrivs som pseudo-id "<id>#brake"
     */
    private void populatePowers(GridResult res) {
        // --- Substations ---
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof Substation ss) {
                double Vnode = res.getLatestNodeVoltage(ss.getFromNode()).asDouble(); // node vs gnd (kan vara negativ)
                double E = ss.getEmf().asDouble();
                double R = ss.getInternalResistance().asDouble();

                double I = (R > 0.0) ? Math.max(0.0, (E - Math.abs(Vnode)) / R) : 0.0; // diod: bara framriktning
                double Pout = I * Math.abs(Vnode);                                     // effekt *till* nätet (alltid ≥0)

                res.setCurrent(ss.getId(), Real.fromDouble(I));
                res.setPower(ss.getId(),   Real.fromDouble(Pout));
            }
        }
        // --- Lines ---
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof Line line) {
                int a = line.getFromNode(), b = line.getToNode();
                double Va = res.getLatestNodeVoltage(a).asDouble();
                double Vb = res.getLatestNodeVoltage(b).asDouble();
                double R  = line.getResistance().asDouble();

                if (R > 0.0) {
                    double Iab   = (Va - Vb) / R;
                    double Ploss = Iab * Iab * R;
                    res.setCurrent(line.getId(), Real.fromDouble(Iab));
                    res.setPower(line.getId(),   Real.fromDouble(Ploss));
                } else {
                    res.setPower(line.getId(), Real.ZERO);
                }
            }
        }

        // --- Trains ---
        final double VDIFF_FLOOR = 50.0; // skydda mot för små ΔV
// --- Trains: actual network exchange + brake resistor power ---
// --- Trains: split requested mot/brk/aux into line vs brake resistor ---
        for (String devId : model.getDeviceIds()) {
            Device<Real> d = model.getDevice(devId);
            if (d instanceof TrainLoad tr) {
                int a = tr.getFromNode(), b = tr.getToNode();
                double Va = res.getLatestNodeVoltage(a).asDouble();
                double Vb = res.getLatestNodeVoltage(b).asDouble();
                double Vabs = Math.abs(Va - Vb);

                // Hämta komponenterna (W). brakingKW är redan NEGATIV vid regen.
                double motW = 0.0, brkW = 0.0, auxW = 0.0;
                UpdateTrainPower u = latest.get(tr.getId());
                if (u != null) {
                    motW = u.motoringKW  * 1000.0;          // ≥ 0
                    brkW = u.brakingKW   * 1000.0;          // ≤ 0
                    auxW = u.auxiliaryKW * 1000.0;          // ≥ 0
                } else {
                    // fallback om inget meddelande – behåll gammalt beteende (ingen brake-split)
                    double preqW = tr.getRequestedPower() != null ? tr.getRequestedPower().asDouble() : 0.0;
                    res.setPower(tr.getId(),            Real.fromDouble(preqW));
                    res.setRequestedPower(tr.getId(),   Real.fromDouble(preqW));
                    res.setPower(tr.getId() + "#brake", Real.ZERO);
                    continue;
                }

                double pReq = motW + brkW + auxW;          // begärd nettoeffekt (signerad)
                double vCut = tr.getCutoffVoltage().asDouble();
                final double EPS = 1e-6;

                double pLine, pBrake;

                if (brkW < 0.0) { // broms begärd
                    if (Vabs + EPS < vCut) {
                        // regen tillåten → broms går till linan
                        pLine  = pReq;                     // kan bli negativ
                        pBrake = 0.0;
                    } else {
                        // regen spärrad (diod) → allt broms till motståndet
                        pLine  = motW + auxW;              // ingen negativ linjeeffekt
                        pBrake = -brkW;                    // dissiperas i resistorn (≥0)
                    }
                } else {
                    // motdrag / vila
                    pLine  = motW + auxW;
                    pBrake = 0.0;
                }

                // Skriv ut
                res.setPower(tr.getId(),            Real.fromDouble(pLine));      // nätets syn
                res.setRequestedPower(tr.getId(),   Real.fromDouble(pReq));       // info-kolumn
                res.setPower(tr.getId() + "#brake", Real.fromDouble(pBrake));     // pseudo-device
            }
        }
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
                double Pnet = g * (E * dV - dV * dV); // classic Norton/Thevenin form
                result.setPower(ss.getId(), Real.fromDouble(Pnet));
            }
        }
    }
}
