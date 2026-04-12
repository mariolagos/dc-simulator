package org.dcsim.electric;

import org.dcsim.PowerPoint;
import org.dcsim.export.ElectricalExcelExport;
import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GridModel<F extends FieldElement<F>> {

    // --- Noder: generiskt typade och indexerade ---
    private final List<Node<F>> nodes = new ArrayList<>();
    private final Map<Integer, Node<F>> nodesById = new HashMap<>();

    // --- Devices (Real-baserade i din kodbas) ---
    private final List<Device<Real>> devices = new ArrayList<>();
    private final List<Substation> substations = new ArrayList<>();

    // v0.8: dynamic line devices (rebuilt each electrical solve tick)
    private List<Device<Real>> dynamicLineDevices = Collections.emptyList();

    private final Map<String, List<PowerPoint>> powerProfiles = new HashMap<>();
    private final List<GridResult> results = new ArrayList<>();
    private String groundNodeId;

    private final Map<Integer, List<VoltageSample>> voltageResults = new HashMap<>();
    private final Map<String, List<CurrentPowerSample>> currentPowerResults = new HashMap<>();
    private final Map<String, List<PowerPoint>> updatedPowerCurves = new HashMap<>();

    private volatile boolean anyBackfeedAllowed = false;

    // ---- Virtuella tåg (ankare) – telemetri ----
    // Konvention: nyckeln ska vara "Train:<id>", t.ex. "Train:T1"
    private final Map<String, List<Real>> updatedTrainVoltages = new LinkedHashMap<>();
    private final Map<String, List<Real>> updatedTrainPositions = new LinkedHashMap<>();
    private final Map<String, List<Real>> updatedTrainSpeeds = new LinkedHashMap<>();

    // Publika, ofiltrerade vyer (behålls för bakåtkomp)
    public Map<String, List<Real>> getUpdatedTrainVoltages() {
        return updatedTrainVoltages;
    }

    public Map<String, List<Real>> getUpdatedTrainPositions() {
        return updatedTrainPositions;
    }

    public Map<String, List<Real>> getUpdatedTrainSpeeds() {
        return updatedTrainSpeeds;
    }

    private static void appendTo(Map<String, List<Real>> m, String id, Real v) {
        m.computeIfAbsent(id, k -> new ArrayList<>()).add(v);
    }

    /**
     * Logga telemetri för ett "virtuellt" tåg (ankare).
     * id måste följa "Train:<id>"-konventionen (ex. "Train:T1").
     */
    public void appendTrainTelemetry(String id, double t,
                                     Real V, Real I, Real P,
                                     Real xM, Real vMS) {
        appendResult(id, t, I, P);            // återanvänd dev-resultlagring (för P/I)
        appendTo(updatedTrainVoltages, id, V);
        appendTo(updatedTrainPositions, id, xM);
        appendTo(updatedTrainSpeeds, id, vMS);
    }

    public boolean isAnyBackfeedAllowed() {
        return anyBackfeedAllowed;
    }

    public void recomputeBackfeedFlag() {
        boolean any = false;
        for (Device<Real> d : devices) {
            if (d instanceof Substation ss && ss.isAllowBackfeed()) {
                any = true;
                break;
            }
        }
        anyBackfeedAllowed = any;
    }

    public GridModel(String groundNodeId) {
        this.groundNodeId = groundNodeId;
    }

    public String getGroundNodeId() {
        return groundNodeId;
    }

    // -------- Nodes --------
    public void addNode(Node<F> node) {
        nodes.add(node);
        nodesById.put(node.get_internal_id(), node);
    }

    public Node<F> getNodeById(String id) {
        Node<F> n = nodesById.get(id);
        if (n == null) throw new IllegalArgumentException("No node with id " + id);
        return n;
    }

    public Node<F> nodeOrThrow(String id) {
        Node<F> n = nodesById.get(id);
        if (n == null) throw new NoSuchElementException("Node id=" + id + " not found");
        return n;
    }

    public Optional<Node<F>> findNode(int id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    //    public List<Integer> getNodeIds() { return nodes.stream().map(Node::get_internal_id).collect(Collectors.toList()); }
    public List<String> getNodeIds() {
        return nodes.stream().map(Node::getNode_id).collect(Collectors.toList());
    }


    public List<Node<F>> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    // -------- Devices --------
//    public void addDevice(Device<Real> device) {
//        devices.add(device);
//        if (device instanceof Substation) recomputeBackfeedFlag();
//    }
    public void addDevice(Device<Real> d) {
        devices.add(d); // <-- alltid
        if (d instanceof Substation) {
            substations.add((Substation) d);
            // om du behöver:
            // recomputeBackfeedFlag();
        }
    }


    public Device<Real> getDevice(String id) {
        return devices.stream().filter(d -> d.getId().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No device with id " + id));
    }

    public List<Device<Real>> getDevices() {
        if (dynamicLineDevices == null || dynamicLineDevices.isEmpty()) {
            return devices;
        }

        // v0.8: static devices + dynamic lines
        List<Device<Real>> merged = new ArrayList<>(devices.size() + dynamicLineDevices.size());
        merged.addAll(devices);
        merged.addAll(dynamicLineDevices);
        return merged;
    }

    public List<Device<Real>> getLines() {
        if (dynamicLineDevices != null && !dynamicLineDevices.isEmpty()) {
            return dynamicLineDevices.stream()
                    .filter(d -> d instanceof Line)
                    .collect(Collectors.toList());
        }

        return devices.stream()
                .filter(d -> d instanceof Line)
                .collect(Collectors.toList());
    }

//    public List<Substation> getSubstations() {
//        return devices.stream().filter(d -> d instanceof Substation).map(d -> (Substation)d).collect(Collectors.toList());
//    }

    public void addSubstation(Substation s) {
        substations.add(s);
    }

    public List<Substation> getSubstations() {
        return substations;
    }

    public void setDynamicLineDevices(List<Device<Real>> devices) {
        if (devices == null || devices.isEmpty()) {
            this.dynamicLineDevices = Collections.emptyList();
        } else {
            this.dynamicLineDevices = devices;
        }
    }

    // v0.8: dynamic per-tick line devices (DcLine)
    public List<Device<Real>> getDynamicLineDevices() {
        return dynamicLineDevices != null ? dynamicLineDevices : Collections.emptyList();
    }

    // -------- Power profiles / results --------
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

    public void storeResult(GridResult result) {
        results.add(result);
    }

    public List<GridResult> getAllResults() {
        return results;
    }

    public int getNumberOfTimesteps() {
        return voltageResults.values().stream().mapToInt(List::size).max().orElse(0);
    }

    public Map<Integer, List<Real>> getNodeVoltages() {
        Map<Integer, List<Real>> allVoltages = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<VoltageSample>> e : voltageResults.entrySet()) {
            List<Real> series = new ArrayList<>();
            for (VoltageSample vs : e.getValue()) series.add(vs.voltage);
            allVoltages.put(e.getKey(), series);
        }
        return allVoltages;
    }

    public Map<String, List<Real>> getUpdatedDeviceCurrents() {
        Map<String, List<Real>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurrentPowerSample>> e : currentPowerResults.entrySet()) {
            List<Real> series = new ArrayList<>();
            for (CurrentPowerSample s : e.getValue()) series.add(s.current);
            out.put(e.getKey(), series);
        }
        return out;
    }

    public Map<String, List<Real>> getUpdatedDevicePowers() {
        Map<String, List<Real>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<CurrentPowerSample>> e : currentPowerResults.entrySet()) {
            List<Real> series = new ArrayList<>();
            for (CurrentPowerSample s : e.getValue()) series.add(s.power);
            out.put(e.getKey(), series);
        }
        return out;
    }

    public void exportResults(String outputPath) {
        try {
            ElectricalExcelExport.export(outputPath, this);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export electrical results", e);
        }
    }

    public void appendVoltageResult(int nodeId, double time, Real voltage) {
        voltageResults.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(new VoltageSample(time, voltage));
    }

    public void appendResult(String deviceId, double time, Real current, Real power) {
        currentPowerResults.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(new CurrentPowerSample(time, current, power));
    }

    public void appendPowerPoint(String deviceId, PowerPoint point) {
        updatedPowerCurves.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(point);
    }

    public List<PowerPoint> getUpdatedPowerPoints(String deviceId) {
        return updatedPowerCurves.getOrDefault(deviceId, List.of());
    }

    // --- Vyer (läs-optimiserade) ---
    public Collection<Node<F>> nodesView() {
        return Collections.unmodifiableCollection(nodes);
    }

    public Collection<Device<Real>> devicesView() {
        return Collections.unmodifiableCollection(devices);
    }

    public List<String> getDeviceIds() {
        List<String> ids = new ArrayList<>(devices.size());
        for (Device<Real> d : devices) ids.add(d.getId());
        return ids;
    }

    // =======================
    // NYTT: resultatvänliga getters för tåg-telemetri
    // =======================

    /**
     * Strippar "Train:" för snygg etikett.
     */
    public static String prettyTrainLabel(String key) {
        return (key != null && key.startsWith("Train:")) ? key.substring("Train:".length()) : key;
    }

    /**
     * Samla alla *virtuella* tåg (ankare) som har telemetri (nycklar "Train:<id>").
     */
    public List<String> getVirtualTrainIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(updatedTrainPositions.keySet());
        ids.addAll(updatedTrainVoltages.keySet());
        ids.addAll(updatedTrainSpeeds.keySet());
        // Säkerställ att de som bara har P/I också kommer med:
        for (String k : currentPowerResults.keySet()) {
            if (k != null && k.startsWith("Train:")) ids.add(k);
        }
        List<String> out = new ArrayList<>(ids);
        out.sort(Comparator.comparing(GridModel::prettyTrainLabel, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Alla "tåg": TrainLoad-devices + virtuella (ankare).
     */
    public List<String> getAllTrainIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        // TrainLoad devices
        for (Device<Real> d : devices) if (d instanceof TrainLoad) ids.add(d.getId());
        // Virtuals
        ids.addAll(getVirtualTrainIds());
        List<String> out = new ArrayList<>(ids);
        out.sort(Comparator.comparing(GridModel::prettyTrainLabel, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Telemetri-serier för V per ankartåg.
     */
    public Map<String, List<Real>> getTrainVoltages() {
        // Begränsa till erkända tågnycklar så vi inte drar omkring skräp
        Set<String> valid = new LinkedHashSet<>(getVirtualTrainIds());
        Map<String, List<Real>> out = new LinkedHashMap<>();
        for (String k : valid) {
            List<Real> s = updatedTrainVoltages.get(k);
            if (s != null) out.put(k, s);
        }
        return out;
    }

    /**
     * Telemetri-serier för position x[m] per ankartåg.
     */
    public Map<String, List<Real>> getTrainPositions() {
        Set<String> valid = new LinkedHashSet<>(getVirtualTrainIds());
        Map<String, List<Real>> out = new LinkedHashMap<>();
        for (String k : valid) {
            List<Real> s = updatedTrainPositions.get(k);
            if (s != null) out.put(k, s);
        }
        return out;
    }

    /**
     * Telemetri-serier för hastighet v[m/s] per ankartåg.
     */
    public Map<String, List<Real>> getTrainSpeeds() {
        Set<String> valid = new LinkedHashSet<>(getVirtualTrainIds());
        Map<String, List<Real>> out = new LinkedHashMap<>();
        for (String k : valid) {
            List<Real> s = updatedTrainSpeeds.get(k);
            if (s != null) out.put(k, s);
        }
        return out;
    }

    public void setGroundNodeId(Node grd) {
        groundNodeId = grd.getNode_id();
    }
}
