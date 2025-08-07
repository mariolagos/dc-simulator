package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.Real;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GridResult {

    private final Map<Integer, List<Real>> nodeVoltages = new HashMap<>();
    private final Map<String, List<Real>> deviceCurrents = new HashMap<>();
    private final Map<String, List<Real>> devicePowers = new HashMap<>();
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

    public void setPower(String deviceId, Real value) {
        devicePowers.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(value);
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

    public Real getLatestNodeVoltage(int nodeId) {
        List<Real> list = getNodeVoltage(nodeId);
        return list.isEmpty() ? Real.ZERO : list.get(list.size() - 1);
    }

    public Real getLatestDeviceCurrent(String deviceId) {
        List<Real> list = getDeviceCurrent(deviceId);
        return list.isEmpty() ? Real.ZERO : list.get(list.size() - 1);
    }

    public Real getLatestDevicePower(String deviceId) {
        List<Real> list = getDevicePower(deviceId);
        return list.isEmpty() ? Real.ZERO : list.get(list.size() - 1);
    }

    public void storeSnapshot(GridModel model, GridResult snapshot) {
        for (Integer nodeId : model.getNodeIds()) {
            setVoltage(nodeId, snapshot.getLatestNodeVoltage(nodeId));
        }
        for (String deviceId : model.getDeviceIds()) {
            setCurrent(deviceId, snapshot.getLatestDeviceCurrent(deviceId));
            setPower(deviceId, snapshot.getLatestDevicePower(deviceId));
        }
    }

    // I GridResult.java
    public RealMatrix getYMatrix() {
        return yMatrix;
    }

    public RealVector getJVector() {
        return jVector;
    }

}
