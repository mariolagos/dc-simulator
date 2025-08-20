package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.*;

public class GridResult {

    private final Map<Integer, List<Real>> nodeVoltages = new HashMap<>();
    private final Map<String, List<Real>> deviceCurrents = new HashMap<>();
    private final Map<String, List<Real>> devicePowers   = new HashMap<>(); // delivered/net
    private final Map<String, List<Real>> deviceRequestedPowers = new HashMap<>(); // requested

    private final RealMatrix yMatrix;
    private final RealVector jVector;

    public GridResult(RealMatrix yMatrix, RealVector jVector) {
        this.yMatrix = yMatrix;
        this.jVector = jVector;
    }

    public void setVoltage(int nodeId, Real value) {
        nodeVoltages.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(value);
    }

    public void setCurrent(String deviceId, Real value) {
        deviceCurrents.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    /** Delivered/net power from network viewpoint. */
    public void setPower(String deviceId, Real value) {
        devicePowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

    /** Requested power by a device (trains). */
    public void setRequestedPower(String deviceId, Real value) {
        deviceRequestedPowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
    }

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

    public Real getDevicePowerAt(String deviceId, int index) {
        return atOrZero(getDevicePower(deviceId), index);
    }

    public Real getDeviceRequestedPowerAt(String deviceId, int index) {
        return atOrZero(getDeviceRequestedPower(deviceId), index);
    }

    public void storeSnapshot(GridModel model, GridResult snapshot) {
        for (Integer nodeId : model.getNodeIds()) {
            setVoltage(nodeId, snapshot.getLatestNodeVoltage(nodeId));
        }
        for (String deviceId : model.getDeviceIds()) {
            setCurrent(deviceId, snapshot.getLatestDeviceCurrent(deviceId));
            setPower(deviceId, snapshot.getLatestDevicePower(deviceId));
            setRequestedPower(deviceId, snapshot.getLatestDeviceRequestedPower(deviceId));
        }
    }

    public RealMatrix getYMatrix() { return yMatrix; }
    public RealVector getJVector() { return jVector; }

    private static Real latestOf(List<Real> list) {
        return list.isEmpty() ? Real.ZERO : list.get(list.size() - 1);
    }

    private static Real atOrZero(List<Real> list, int idx) {
        return (idx >= 0 && idx < list.size()) ? list.get(idx) : Real.ZERO;
    }
}
