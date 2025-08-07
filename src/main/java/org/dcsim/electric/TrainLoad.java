package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;
import org.dcsim.power.PowerProfile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A train modeled as a power-injecting (or absorbing) element with braking behavior.
 * Supports regeneration into the line or braking resistor.
 */

/**
 * A train modeled as a power-injecting (or absorbing) element with braking behavior.
 * Supports regeneration into the line or braking resistor.
 */
public class TrainLoad implements Device<Real> {
    private final String id;
    private final int fromNode;
    private final int toNode;
    private Real current = Real.ZERO;
    private Real requestedPower = Real.ZERO;
    private PowerProfile powerProfile;

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

    public void setPowerProfile(PowerProfile profile) {
        this.powerProfile = profile;
    }

    public PowerProfile getPowerProfile() {
        return powerProfile;
    }

    public void setRequestedPower(Real power) {
        this.requestedPower = power;
    }

    public void setCutoffVoltage(Real cutoff) {
        this.cutoffVoltage = cutoff;
    }

    public void setMaxVoltage(Real maxVoltage) {
        this.maxVoltage = maxVoltage;
    }

    public void setMaxCurrent(Real maxCurrent) {
        this.maxCurrent = maxCurrent;
    }

    public Real getCutoffVoltage() {
        return cutoffVoltage;
    }

    public Real getMaxVoltage() {
        return maxVoltage;
    }

    public Real getMaxCurrent() {
        return maxCurrent;
    }

    public int getFromNode() {
        return fromNode;
    }

    public int getToNode() {
        return toNode;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getConnectedNode() {
        throw new UnsupportedOperationException("TrainLoad is a two-node device");
    }

    @Override
    public Real getCurrent() {
        return current;
    }

    @Override
    public Real getPower() {
        throw new UnsupportedOperationException("Use getPower(from, to) instead");
    }

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
            // Motordrift
            if (voltage.lt(cutoffVoltage)) {
                current = Real.ZERO;
            } else {
                current = requestedPower.divide(voltage);
            }
        } else {
            // Bromsning
            if (voltage.lt(cutoffVoltage)) {
                current = requestedPower.divide(voltage);
            } else {
                // All effekt till bromsmotståndet om spänningen är hög nog
                Real brakeCurrent = voltage.divide(brakeResistance).negate();
                Real regenCurrent = requestedPower.divide(voltage);

                // Ta den minst negativa (begränsa till -requestedPower)
                current = brakeCurrent.abs().lt(regenCurrent.abs()) ? brakeCurrent : regenCurrent;
            }
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
            lineCurrents.add(0.0);
            brakeCurrents.add(0.0);
            linePowers.add(0.0);
            brakePowers.add(0.0);
            return;
        }

        if (requestedPower.isPositive()) {
            double i = current.asDouble();
            double p = v * i;
            lineCurrents.add(i);
            linePowers.add(p);
            brakeCurrents.add(0.0);
            brakePowers.add(0.0);
        } else {
            if (v < cutoffVoltage.asDouble()) {
                double i = current.asDouble();
                double p = v * i;
                lineCurrents.add(i);
                linePowers.add(p);
                brakeCurrents.add(0.0);
                brakePowers.add(0.0);
            } else {
                double i = current.asDouble();
                double p = v * i;
                lineCurrents.add(0.0);
                linePowers.add(0.0);
                brakeCurrents.add(i);
                brakePowers.add(p);
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

    /**
     * Returns power balance components: [linePower, brakeResistorPower, totalPower].
     */
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

}