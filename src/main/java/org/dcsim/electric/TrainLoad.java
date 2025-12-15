package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A train modeled as a two-node device from the network point of view.
 * Motoring draws power from the line; regenerative braking can feed the line
 * below a cutoff voltage. At/above the cutoff, braking energy is dumped into
 * an onboard brake resistor (no network current) and is exported as a
 * separate “pseudo-device" power via GridModelActor.
 */
public class TrainLoad implements Device<Real> {

    private final String id;
    private int fromNode;
    private int toNode;

    // Requested components (W)
    private Real requestedMotoringPower  = Real.ZERO;   // >= 0
    private Real requestedBrakingPower   = Real.ZERO;   // <= 0 (already negative when braking)
    private Real requestedAuxiliaryPower = Real.ZERO;   // >= 0

    /** Total requested (W) used by solver / network side. */
    private Real requestedPower = Real.ZERO;

    /** Network current (from -> to). */
    private Real current = Real.ZERO;

    /** Regeneration cutoff: at/above this, no line regen; braking goes to resistor. */
    private Real cutoffVoltage = Real.fromDouble(850.0);

    /** Optional caps (used for regen window). */
    private Real maxVoltage = Real.fromDouble(1000.0);
    private Real minVoltage = Real.fromDouble(500.0);
    private Real maxCurrent = Real.fromDouble(300.0);

    /** Brake resistor (train internal); used only for reporting P_brake. */
    private final Real brakeResistance = Real.fromDouble(1.0);

    // Simple logging buffers (optional)
    private final List<Double> brakeCurrents = new ArrayList<>();
    private final List<Double> lineCurrents  = new ArrayList<>();
    private final List<Double> brakePowers   = new ArrayList<>();
    private final List<Double> linePowers    = new ArrayList<>();
    private final List<Double> times         = new ArrayList<>();

    // === Previous-tick |ΔV| to decide what to stamp (prevents phantom losses) ===
    private double lastDeltaV = 800.0;     // [V] magnitude from previous tick (actor supplies)
    private final double vFloor = 50.0;    // guard against near-zero ΔV in linearization

    private static volatile boolean __stampBannerShown = false;

    public static final java.util.concurrent.atomic.AtomicInteger DEBUG_STAMP_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    // ---- DEBUG COUNTERS (static, per-tick) ----
    private static volatile int DBG_stampTick = -1;
    private static volatile int DBG_stampCount = 0;

    private static final AtomicInteger DBG_STAMP_COUNT = new AtomicInteger(0);
    public static int DBG_getAndResetStampCount() { return DBG_STAMP_COUNT.getAndSet(0); }

    public static void DBG_resetStampCounter(int step) {
        DBG_stampTick = step;
        DBG_stampCount = 0;
    }
    public static int DBG_getStampCount() { return DBG_stampCount; }

    // Class origin (for quick diagnostics)
    static {
        System.out.println("[WHERE] TrainLoad loaded from: "
                + TrainLoad.class.getProtectionDomain().getCodeSource().getLocation());
    }

    /** Split requested power into line/brake parts from |ΔV| and cutoff window. */
    private double[] splitRequestedPowerForLine(double dvAbs) {
	final double vmin = minVoltage.asDouble();   // t.ex. 500 V
	final double cut  = cutoffVoltage.asDouble();// t.ex. 850 V
	final double vmax = maxVoltage.asDouble();   // t.ex. 1000 V

        double motW = requestedMotoringPower.asDouble();    // ≥ 0
        double brkW = requestedBrakingPower.asDouble();     // ≤ 0 (regen)
        double auxW = requestedAuxiliaryPower.asDouble();   // ≥ 0

        double pLine = 0.0;
        double pBrake = 0.0;

        // Aux always from line
        pLine += auxW;

        // Motoring: only if enough voltage (simple undervoltage guard)
	// Motoring: undervoltage-guard mot minVoltage (inte cutoff)
	if (motW > 0.0) {
	    if (dvAbs >= vmin) pLine += motW;
	}

        // Regenerative braking (negative)
        if (brkW < 0.0) {
            if (dvAbs <= cut) {
                // All regen to line
                pLine += brkW;
            } else if (dvAbs >= vmax) {
                // All to brake resistor
                pBrake += -brkW;
            } else {
                // Linear ramp from cut..vmax : 1..0 to line
                double fracLine = (vmax - dvAbs) / (vmax - cut);
                pLine  += brkW * fracLine;             // negative
                pBrake += (-brkW) * (1.0 - fracLine);  // positive
            }
        }
        return new double[] { pLine, pBrake };
    }

    public TrainLoad(String id, int fromNode, int toNode) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
    }

    public String getId() { return id; }
    public int getFromNode() { return fromNode; }
    public int getToNode() { return toNode; }

    /** Allow topology to move train along the line later. */
    public void setFromNode(int newFromNode) { this.fromNode = newFromNode; }
    public void setToNode(int newToNode) { this.toNode = newToNode; }

    /** Set requested motoring/braking/auxiliary in kW (braking already negative). */
    public void setRequestedComponents(double motoringKW, double brakingKW, double auxiliaryKW) {
        this.requestedMotoringPower  = Real.fromDouble(motoringKW  * 1000.0);
        this.requestedBrakingPower   = Real.fromDouble(brakingKW   * 1000.0);
        this.requestedAuxiliaryPower = Real.fromDouble(auxiliaryKW * 1000.0);

        this.requestedPower = requestedMotoringPower
                .plus(requestedBrakingPower)
                .plus(requestedAuxiliaryPower);
    }

    public Real getRequestedMotoringPower()  { return requestedMotoringPower; }
    public Real getRequestedBrakingPower()   { return requestedBrakingPower; }
    public Real getRequestedAuxiliaryPower() { return requestedAuxiliaryPower; }
    public Real getRequestedTotalPower()     { return requestedPower; }
    public void setRequestedPower(Real power) { this.requestedPower = power; }

    public void setCutoffVoltage(Real cutoff)   { this.cutoffVoltage = cutoff; }
    public void setMaxVoltage(Real maxVoltage)  { this.maxVoltage = maxVoltage; }
    public void setMinVoltage(Real minVoltage)  { this.minVoltage = minVoltage; }
    public void setMaxCurrent(Real maxCurrent)  { this.maxCurrent = maxCurrent; }
    public Real getCutoffVoltage()              { return cutoffVoltage; }
    public Real getMaxVoltage()                 { return maxVoltage; }
    public Real getMaxCurrent()                 { return maxCurrent; }

    /** Actor supplies last ΔV each tick; keep its magnitude ≥ vFloor. */
    public void setLastDeltaV(double dv) { this.lastDeltaV = Math.max(vFloor, Math.abs(dv)); }

    @Override public String toString() { return "TrainLoad(" + id + ")"; }

    @Override public int getConnectedNode() { throw new UnsupportedOperationException("TrainLoad is a two-node device"); }
    @Override public Real getCurrent() { return current; }
    @Override public Real getPower()   { throw new UnsupportedOperationException("Use getPower(from,to)"); }

    @Override
    public Real computeCurrent(Real voltage, double time) {
        throw new UnsupportedOperationException("TrainLoad is a two-node device; use computeCurrent(fromVoltage, toVoltage)");
    }

    /**
     * Compute network current based on requestedPower and bus voltage.
     * Sign convention: positive current flows from 'fromNode' to 'toNode'.
     * For braking above/at cutoffVoltage, network current is zero (all to resistor).
     */
    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        Real v = fromVoltage.minus(toVoltage);
        double V = v.asDouble();
        double P = requestedPower.asDouble();

        if (P == 0.0) {
            current = Real.ZERO;
            return current;
        }

        if (P > 0.0) {
            // Motoring: draw from line
            if (Math.abs(V) < 1e-9) current = Real.ZERO;
            else current = Real.fromDouble(P / V);
        } else {
            // Braking (P < 0)
            if (V < cutoffVoltage.asDouble()) {
                // Below cutoff: allow regen to the line
                if (Math.abs(V) < 1e-9) current = Real.ZERO;
                else current = Real.fromDouble(P / V); // negative current if V>0
            } else {
                // At/above cutoff: dump to brake resistor, zero network current
                current = Real.ZERO;
            }
        }

        // Enforce max |I|
        double I = current.asDouble();
        double Imax = maxCurrent.asDouble();
        if (Math.abs(I) > Imax) current = Real.fromDouble(Math.copySign(Imax, I));
        return current;
    }

    /** Network power (signed): P = ΔV * I. */
    public Real getPower(Real fromVoltage, Real toVoltage) {
        return fromVoltage.minus(toVoltage).times(current);
    }

    /**
     * Instantaneous brake-resistor power (>= 0). Informational only; not stamped into the network.
     * Active only when requestedPower < 0 and V >= cutoffVoltage.
     */
    public Real getBrakeResistorInstantPower(Real fromVoltage, Real toVoltage) {
        double V = fromVoltage.minus(toVoltage).asDouble();
        double P = requestedPower.asDouble(); // negative in braking
        if (P >= 0.0) return Real.ZERO;
        if (V < cutoffVoltage.asDouble()) return Real.ZERO;

        // Cap by resistor capability at this V
        double presCap = (brakeResistance.asDouble() > 0.0) ? (V*V / brakeResistance.asDouble()) : 0.0;
        double pReqAbs = -P; // positive magnitude of requested braking
        double pBrake  = Math.min(pReqAbs, presCap);
        return Real.fromDouble(Math.max(0.0, pBrake));
    }

    /** Optional simple logging for debugging split currents/powers. */
    public void logCurrents(double time, Real fromVoltage, Real toVoltage) {
        Real v = fromVoltage.minus(toVoltage);
        double V = v.asDouble();
        double I = current.asDouble();

        times.add(time);
        // Network-side
        lineCurrents.add(I);
        linePowers.add(V * I);
        // Brake side (derived)
        double pBrake = getBrakeResistorInstantPower(fromVoltage, toVoltage).asDouble();
        // Map to an “equivalent" current just for logging
        double iBrake = (V != 0.0) ? (pBrake / V) : 0.0;
        brakeCurrents.add(iBrake);
        brakePowers.add(pBrake);
    }

    public void exportCurrentsToCsv(String path) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write("time,lineCurrent,brakeResistorCurrent,linePower,brakeResistorPower\n");
            for (int i = 0; i < times.size(); i++) {
                writer.write(String.format(Locale.US, "%.3f,%.6f,%.6f,%.3f,%.3f\n",
                        times.get(i), lineCurrents.get(i), brakeCurrents.get(i), linePowers.get(i), brakePowers.get(i)));
            }
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
        }
    }

    /**
     * Stamp only the **line portion** of the requested power.
     * When all braking goes to the resistor, we stamp (almost) nothing → no phantom line losses.
     */
    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {
        // visible regardless of logger config:
        DBG_STAMP_COUNT.incrementAndGet();
        System.out.println("[TL.STAMP] id=" + id + " step=" + timestep + " PreqW=" + requestedPower.asDouble());
        if (!__stampBannerShown) {
            System.out.println("[TrainLoad.stamp] USING v0.4-baseline: pLineW = mot + aux, no braking on network");
            __stampBannerShown = true;
        }
        Integer iObj = nodeIndexMap.get(fromNode);
        Integer jObj = nodeIndexMap.get(toNode);
        if (iObj == null || jObj == null) return;
        final int i = iObj, j = jObj;

        // DEBUG: count each stamp and print one line
        if (timestep != DBG_stampTick) { DBG_stampTick = timestep; DBG_stampCount = 0; }
        DBG_stampCount++;
        System.out.println(
                "[TL.STAMP] id=" + id
                        + " step=" + timestep
                        + " reqW=" + requestedPower.asDouble()
                        + " lastDeltaV=" + lastDeltaV
        );

        // Only the portion actually taken from the line
        final double motW = requestedMotoringPower.asDouble();    // ≥ 0
        final double auxW = requestedAuxiliaryPower.asDouble();   // ≥ 0
        final double pLineW = Math.max(0.0, motW + auxW);

        if (pLineW <= 1e-9) {
            // No network exchange when we only brake → no phantom current
            current = Real.ZERO;
            return;
        }

        // Neutral linearization around a nominal voltage (only for Y)
        final double nominalV = 800.0;
        final double g = pLineW / (nominalV * nominalV);

        yMatrix.addToEntry(i, i,  g);
        yMatrix.addToEntry(j, j,  g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);

        // Telemetry: approximate network current (positive in motoring)
        final double I = pLineW / nominalV;
        current = Real.fromDouble(I);
    }

    /** Convenience breakdown for callers that want both pieces at once (no recursion). */
    public Real[] getPowerBalanceComponents(Real fromVoltage, Real toVoltage) {
        Real v = fromVoltage.minus(toVoltage);
        Real pLine  = v.times(current);                     // signed (can be <0 in regen)
        Real pBrake = getBrakeResistorInstantPower(fromVoltage, toVoltage); // >= 0
        Real pTotal = pLine.plus(pBrake);                   // informational
        return new Real[] { pLine, pBrake, pTotal };
    }

    /** Total requested power in W (motoring + braking + auxiliaries). */
    public Real getRequestedPower() { return requestedPower; }

    // Optional breakdown (in W)
    public double getMotoringW()  { return requestedMotoringPower.asDouble(); }
    public double getBrakingW()   { return requestedBrakingPower.asDouble(); }
    public double getAuxiliaryW() { return requestedAuxiliaryPower.asDouble(); }

    public Real getBrakeResistance() { return brakeResistance; }

    // Optional: helpers for debug reset/inspect
    public static void debugResetStampCounter() { DEBUG_STAMP_COUNT.set(0); }
    public static int  debugGetStampCount()     { return DEBUG_STAMP_COUNT.get(); }

}
