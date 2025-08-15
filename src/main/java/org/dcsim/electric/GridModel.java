package org.dcsim.electric;

import org.dcsim.PowerPoint;
import org.dcsim.export.ElectricalExcelExport;
import org.dcsim.math.Real;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GridModel {
    private final List<Node> nodes = new ArrayList<>();
    private final List<Device<Real>> devices = new ArrayList<>();
    private final Map<String, List<PowerPoint>> powerProfiles = new HashMap<>();
    private final List<GridResult> results = new ArrayList<>();
    private final int groundNodeId;

    private final Map<Integer, List<VoltageSample>> voltageResults = new HashMap<>();
    private final Map<String, List<CurrentPowerSample>> currentPowerResults = new HashMap<>();
    private final Map<String, List<PowerPoint>> updatedPowerCurves = new HashMap<>();

    public GridModel(int groundNodeId) {
        this.groundNodeId = groundNodeId;
    }

    public int getGroundNodeId() {
        return groundNodeId;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void addDevice(Device<Real> device) {
        devices.add(device);
    }

    public void setPowerProfile(String deviceId, List<PowerPoint> profile) {
        powerProfiles.put(deviceId, profile);
    }

    public void addPowerProfile(String id, List<PowerPoint> profile) {
        powerProfiles.put(id, profile);
    }

    public List<PowerPoint> getPowerProfile(String id) {
        return powerProfiles.get(id);
    }

    public Set<String> getPowerProfileIds() {
        return powerProfiles.keySet();
    }

    public Node getNodeById(int id) {
        return nodes.stream()
                .filter(n -> n.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No node with id " + id));
    }

    public Device<Real> getDevice(String id) {
        return devices.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No device with id " + id));
    }

    public List<Integer> getNodeIds() {
        List<Integer> ids = new ArrayList<>();
        for (Node node : nodes) {
            ids.add(node.getId());
        }
        return ids;
    }

    public List<String> getDeviceIds() {
        List<String> ids = new ArrayList<>();
        for (Device<Real> d : devices) {
            ids.add(d.getId());
        }
        return ids;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Device<Real>> getDevices() {
        return devices;
    }

    public List<Device<Real>> getLines() {
        return devices.stream()
                .filter(d -> d instanceof Line)
                .collect(Collectors.toList());
    }

    public List<Substation> getSubstations() {
        return devices.stream()
                .filter(d -> d instanceof Substation)
                .map(d -> (Substation) d)
                .collect(Collectors.toList());
    }

    public void storeResult(GridResult result) {
        results.add(result);
    }

    public List<GridResult> getAllResults() {
        return results;
    }

    public int getNumberOfTimesteps() {
        return voltageResults.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
    }

    public Map<Integer, List<Real>> getNodeVoltages() {
        Map<Integer, List<Real>> allVoltages = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<VoltageSample>> entry : voltageResults.entrySet()) {
            List<Real> voltageSeries = new ArrayList<>();
            for (VoltageSample vs : entry.getValue()) {
                voltageSeries.add(vs.voltage);
            }
            allVoltages.put(entry.getKey(), voltageSeries);
        }
        return allVoltages;
    }

    public Map<String, List<Real>> getUpdatedDeviceCurrents() {
        Map<String, List<Real>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurrentPowerSample>> entry : currentPowerResults.entrySet()) {
            List<Real> list = new ArrayList<>();
            for (CurrentPowerSample sample : entry.getValue()) {
                list.add(sample.current);
            }
            result.put(entry.getKey(), list);
        }
        return result;
    }

    public Map<String, List<Real>> getUpdatedDevicePowers() {
        Map<String, List<Real>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurrentPowerSample>> entry : currentPowerResults.entrySet()) {
            List<Real> list = new ArrayList<>();
            for (CurrentPowerSample sample : entry.getValue()) {
                list.add(sample.power);
            }
            result.put(entry.getKey(), list);
        }
        return result;
    }

    public void exportResults(String outputPath) {
        try {
            ElectricalExcelExport.export(outputPath, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export electrical results", e);
        }
    }

    public void appendVoltageResult(int nodeId, double time, Real voltage) {
        voltageResults.computeIfAbsent(nodeId, k -> new ArrayList<>())
                .add(new VoltageSample(time, voltage));
    }

    public void appendResult(String deviceId, double time, Real current, Real power) {
        currentPowerResults.computeIfAbsent(deviceId, k -> new ArrayList<>())
                .add(new CurrentPowerSample(time, current, power));
    }

    public void appendPowerPoint(String deviceId, PowerPoint point) {
        updatedPowerCurves.computeIfAbsent(deviceId, k -> new ArrayList<>())
                .add(point);
    }

    public List<PowerPoint> getUpdatedPowerPoints(String deviceId) {
        return updatedPowerCurves.getOrDefault(deviceId, List.of());
    }
}
