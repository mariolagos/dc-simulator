package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * A train modeled as a two-node device from the network viewpoint.
 * Motoring draws power from the line; regenerative braking can feed the line
 * below a cutoff voltage. Above/at the cutoff, braking energy is dumped into
 * an onboard brake resistor (no network current) and is exported as a
 * separate "pseudo-device" power via GridModelActor.
 */
public class TrainLoad implements Device<Real> {

    private final String id;
    private int fromNode;
    private final int toNode;

    // Requested components (W)
    private Real requestedMotoringPower  = Real.ZERO;   // >= 0
    private Real requestedBrakingPower   = Real.ZERO;   // <= 0 (already negative when braking)
    private Real requestedAuxiliaryPower = Real.ZERO;   // >= 0

    /** Total requested (W) used by solver/network side. */
    private Real requestedPower = Real.ZERO;

    /** Network current (from -> to). */
    private Real current = Real.ZERO;

    /** Regeneration cutoff: at/above this, no line regen; braking goes to resistor. */
    private Real cutoffVoltage = Real.fromDouble(850.0);

    /** Optional caps (not currently enforced except maxCurrent). */
    private Real maxVoltage = Real.fromDouble(1000.0);
    private Real maxCurrent = Real.fromDouble(300.0);

    /** Brake resistor (train internal); used only for reporting P_brake. */
    private final Real brakeResistance = Real.fromDouble(1.0);

    // Simple logging buffers (optional)
    private final List<Double> brakeCurrents = new ArrayList<>();
    private final List<Double> lineCurrents = new ArrayList<>();
    private final List<Double> brakePowers = new ArrayList<>();
    private final List<Double> linePowers = new ArrayList<>();
    private final List<Double> times = new ArrayList<>();

    public TrainLoad(String id, int fromNode, int toNode) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
    }

    public String getId() { return id; }
    public int getFromNode() { return fromNode; }
    public int getToNode() { return toNode; }
    /** Allow topology to move train along the line later */
    public void setFromNode(int newFromNode) { this.fromNode = newFromNode; }

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
    public void setMaxCurrent(Real maxCurrent)  { this.maxCurrent = maxCurrent; }
    public Real getCutoffVoltage()              { return cutoffVoltage; }
    public Real getMaxVoltage()                 { return maxVoltage; }
    public Real getMaxCurrent()                 { return maxCurrent; }

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
     * Instantaneous brake-resistor power (>= 0). Purely informative; not stamped into the network.
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
        // Treat as an internal current equivalent for logging only
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
     * Stamp as a Thevenin-like conductance so the solver has something linear to work with
     * (simple Norton linearization around nominal voltage). This is a heuristic; true
     * non-linearities (cutoff/diode) are handled in post-processing and in computeCurrent().
     */
    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {
        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);

        double P = Math.abs(requestedPower.asDouble()) + 1.0; // avoid 0
        double nominalV = 800.0; // neutral linearization point
        double R = (nominalV * nominalV) / P;
        double g = 1.0 / R;

        yMatrix.addToEntry(i, i, g);
        yMatrix.addToEntry(j, j, g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);

        // Reset network current; will be set by computeCurrent() path used after solve.
        current = Real.ZERO;
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
}
