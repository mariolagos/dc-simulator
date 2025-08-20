package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * A train modeled as a power-injecting (or absorbing) element with braking behavior.
 * Supports regeneration into the line or braking resistor.
 */
public class TrainLoad implements Device<Real> {
    private final String id;
    private int fromNode;
    private final int toNode;

    // Requested components (W)
    private Real requestedMotoringPower  = Real.ZERO;   // >= 0
    private Real requestedBrakingPower   = Real.ZERO;   // <= 0 (already negative when braking)
    private Real requestedAuxiliaryPower = Real.ZERO;   // >= 0

    // Total requested (W) used by solver
    private Real requestedPower = Real.ZERO;

    private Real current = Real.ZERO;

    private Real cutoffVoltage = Real.fromDouble(850.0);
    private Real maxVoltage = Real.fromDouble(1000.0);
    private Real maxCurrent = Real.fromDouble(300.0);
    private final Real brakeResistance = Real.fromDouble(1.0);

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

    /** Set requested motoring/braking/auxiliary in kW (braking is already negative). */
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

    public void setPowerProfile(org.dcsim.power.PowerProfile profile) { /* kept for compatibility */ }
    public org.dcsim.power.PowerProfile getPowerProfile() { return null; }

    public void setRequestedPower(Real power) { this.requestedPower = power; }

    public void setCutoffVoltage(Real cutoff) { this.cutoffVoltage = cutoff; }
    public void setMaxVoltage(Real maxVoltage) { this.maxVoltage = maxVoltage; }
    public void setMaxCurrent(Real maxCurrent) { this.maxCurrent = maxCurrent; }

    public Real getCutoffVoltage() { return cutoffVoltage; }
    public Real getMaxVoltage() { return maxVoltage; }
    public Real getMaxCurrent() { return maxCurrent; }

    @Override public int getConnectedNode() { throw new UnsupportedOperationException("TrainLoad is a two-node device"); }
    @Override public Real getCurrent() { return current; }
    @Override public Real getPower() { throw new UnsupportedOperationException("Use getPower(from, to) instead"); }

    @Override
    public Real computeCurrent(Real voltage, double time) {
        throw new UnsupportedOperationException("TrainLoad is a two-node device; use computeCurrent(fromVoltage, toVoltage)");
    }

    public Real computeCurrent(Real fromVoltage, Real toVoltage) {
        Real voltage = fromVoltage.minus(toVoltage);

        if (requestedPower.isZero()) {
            current = Real.ZERO;
            return current;
        }

        if (requestedPower.isPositive()) {
            // motoring
            if (voltage.lt(cutoffVoltage)) {
                current = Real.ZERO;
            } else {
                current = requestedPower.divide(voltage);
            }
        } else {
            // braking
            if (voltage.lt(cutoffVoltage)) {
                current = requestedPower.divide(voltage);
            } else {
                // Above cutoff: send to brake resistor (diode-like behavior)
                Real brakeCurrent = voltage.divide(brakeResistance).negate();
                Real regenCurrent = requestedPower.divide(voltage);
                // pick the less negative magnitude (limit)
                current = brakeCurrent.abs().lt(regenCurrent.abs()) ? brakeCurrent : regenCurrent;
            }
        }
        // enforce max current
        if (current.abs().compareTo(maxCurrent) > 0) {
            Real sign = current.divide(current.abs()); // +1 or -1
            current = sign.times(maxCurrent);
        }
        return current;
    }

    public Real getPower(Real fromVoltage, Real toVoltage) {
        return fromVoltage.minus(toVoltage).times(current);
    }

    public void logCurrents(double time, Real fromVoltage, Real toVoltage) {
        Real voltage = fromVoltage.minus(toVoltage);
        double v = voltage.asDouble();

        times.add(time);

        if (requestedPower.isZero()) {
            lineCurrents.add(0.0);  brakeCurrents.add(0.0);
            linePowers.add(0.0);    brakePowers.add(0.0);
            return;
        }

        if (requestedPower.isPositive()) {
            double i = current.asDouble();  double p = v * i;
            lineCurrents.add(i); linePowers.add(p);
            brakeCurrents.add(0.0); brakePowers.add(0.0);
        } else {
            if (v < cutoffVoltage.asDouble()) {
                double i = current.asDouble();  double p = v * i;
                lineCurrents.add(i); linePowers.add(p);
                brakeCurrents.add(0.0); brakePowers.add(0.0);
            } else {
                double i = current.asDouble();  double p = v * i;
                lineCurrents.add(0.0); linePowers.add(0.0);
                brakeCurrents.add(i); brakePowers.add(p);
            }
        }
    }

    public void exportCurrentsToCsv(String path) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write("time,substationCurrent,brakeResistorCurrent,substationPower,brakeResistorPower\n");
            for (int i = 0; i < times.size(); i++) {
                writer.write(String.format(Locale.US, "%.3f,%.6f,%.6f,%.3f,%.3f\n",
                        times.get(i), lineCurrents.get(i), brakeCurrents.get(i), linePowers.get(i), brakePowers.get(i)));
            }
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
        }
    }

    @Override
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector,
                      int timestep, Map<Integer, Integer> nodeIndexMap) {
        int i = nodeIndexMap.get(fromNode);
        int j = nodeIndexMap.get(toNode);

        double nominalVoltage = requestedPower.isNegative() ? 850.0 : 750.0;
        double resistance = (nominalVoltage * nominalVoltage) / Math.max(1.0, Math.abs(requestedPower.asDouble()));
        double g = 1.0 / resistance;

        yMatrix.addToEntry(i, i, g);
        yMatrix.addToEntry(j, j, g);
        yMatrix.addToEntry(i, j, -g);
        yMatrix.addToEntry(j, i, -g);

        current = Real.ZERO;
    }

    /** Returns power balance components: [linePower, brakeResistorPower, totalPower]. */
    public Real[] getPowerBalanceComponents(Real fromVoltage, Real toVoltage) {
        Real voltage = fromVoltage.minus(toVoltage);
        if (requestedPower.isZero()) {
            return new Real[] {Real.ZERO, Real.ZERO, Real.ZERO};
        }

        if (requestedPower.isPositive()) {
            Real pLine = voltage.times(current);
            return new Real[] {pLine, Real.ZERO, pLine};
        } else {
            if (voltage.lt(cutoffVoltage)) {
                Real pLine = voltage.times(current);
                return new Real[] {pLine, Real.ZERO, pLine};
            } else {
                Real pTotal = voltage.times(current);
                return new Real[] {Real.ZERO, pTotal, pTotal};
            }
        }
    }

    /** Total requested power in W (motoring + braking + auxiliaries). */
    public Real getRequestedPower() {
        return requestedPower;
    }

    // Optional breakdown (in W)
    public double getMotoringW()  { return requestedMotoringPower.asDouble(); }
    public double getBrakingW()   { return requestedBrakingPower.asDouble(); }
    public double getAuxiliaryW() { return requestedAuxiliaryPower.asDouble(); }
}
