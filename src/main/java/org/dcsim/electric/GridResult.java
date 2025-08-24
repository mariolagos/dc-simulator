package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.*;

/**
 * Per-tick storage for solved network state.
 * - Voltages per node
 * - Currents and net powers per device (network viewpoint)
 * - Requested power per device (for power-controlled devices like trains)
 * - Brake-resistor power per device (non-network dissipation; e.g. trains)
 */
public class GridResult {

    private final Map<Integer, List<Real>> nodeVoltages = new HashMap<>();

    private final Map<String, List<Real>> deviceCurrents = new HashMap<>();
    /** Net/delivered power from the network perspective (can be +/-) */
    private final Map<String, List<Real>> devicePowers   = new HashMap<>();
    /** Requested power by device (e.g., train’s requested total line power) */
    private final Map<String, List<Real>> deviceRequestedPowers = new HashMap<>();
    /** Brake resistor (or other local dissipation) power per device (>= 0) */
    private final Map<String, List<Real>> deviceBrakePowers = new HashMap<>();

    private final RealMatrix yMatrix;
    private final RealVector jVector;

    public GridResult(RealMatrix yMatrix, RealVector jVector) {
        this.yMatrix = yMatrix;
        this.jVector = jVector;
    }

    // ---- setters ----

    public void setVoltage(int nodeId, Real value) {
        nodeVoltages.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(value);
    }

    public void setCurrent(String deviceId, Real value) {
        deviceCurrents.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    /** Net (signed) power from the network viewpoint. */
    public void setPower(String deviceId, Real value) {
        devicePowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    /** Requested (signed) power for a device (e.g. train). */
    public void setRequestedPower(String deviceId, Real value) {
        deviceRequestedPowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    /** Local dissipation power (e.g. brake resistor), expected >= 0. */
    public void setBrakePower(String deviceId, Real value) {
        deviceBrakePowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    // ---- getters (full series) ----

    public List<Real> getNodeVoltage(int nodeId) {
        return nodeVoltages.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<Real> getDeviceCurrent(String deviceId) {
        return deviceCurrents.getOrDefault(deviceId, Collections.emptyList());
    }

    public List<Real> getDevicePower(String deviceId) {
        return devicePowers.getOrDefault(deviceId, Collections.emptyList());
    }

    public List<Real> getDeviceRequestedPower(String deviceId) {
        return deviceRequestedPowers.getOrDefault(deviceId, Collections.emptyList());
    }

    public List<Real> getDeviceBrakePower(String deviceId) {
        return deviceBrakePowers.getOrDefault(deviceId, Collections.emptyList());
    }

    // ---- getters (latest) ----

    public Real getLatestNodeVoltage(int nodeId) {
        return latestOf(getNodeVoltage(nodeId));
    }

    public Real getLatestDeviceCurrent(String deviceId) {
        return latestOf(getDeviceCurrent(deviceId));
    }

    public Real getLatestDevicePower(String deviceId) {
        return latestOf(getDevicePower(deviceId));
    }

    public Real getLatestDeviceRequestedPower(String deviceId) {
        return latestOf(getDeviceRequestedPower(deviceId));
    }

    public Real getLatestDeviceBrakePower(String deviceId) {
        return latestOf(getDeviceBrakePower(deviceId));
    }

    // ---- getters (by index) ----

    public Real getDevicePowerAt(String deviceId, int index) {
        return atOrZero(getDevicePower(deviceId), index);
    }

    public Real getDeviceRequestedPowerAt(String deviceId, int index) {
        return atOrZero(getDeviceRequestedPower(deviceId), index);
    }

    public Real getDeviceBrakePowerAt(String deviceId, int index) {
        return atOrZero(getDeviceBrakePower(deviceId), index);
    }

    // ---- snapshot copy ----

    public void storeSnapshot(GridModel model, GridResult snapshot) {
        for (Integer nodeId : model.getNodeIds()) {
            setVoltage(nodeId, snapshot.getLatestNodeVoltage(nodeId));
        }
        for (String deviceId : model.getDeviceIds()) {
            setCurrent(deviceId,        snapshot.getLatestDeviceCurrent(deviceId));
            setPower(deviceId,          snapshot.getLatestDevicePower(deviceId));
            setRequestedPower(deviceId, snapshot.getLatestDeviceRequestedPower(deviceId));
            setBrakePower(deviceId,     snapshot.getLatestDeviceBrakePower(deviceId));
        }
    }

    // ---- raw matrices (for post-solve reconstruction if needed) ----

    public RealMatrix getYMatrix() { return yMatrix; }
    public RealVector getJVector() { return jVector; }

    // ---- helpers ----

    private static Real latestOf(List<Real> list) {
        return list.isEmpty() ? Real.ZERO : list.get(list.size() - 1);
    }

    private static Real atOrZero(List<Real> list, int idx) {
        return (idx >= 0 && idx < list.size()) ? list.get(idx) : Real.ZERO;
    }
}
