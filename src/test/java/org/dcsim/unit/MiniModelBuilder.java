package org.dcsim.unit;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;

import java.util.*;

import static org.dcsim.math.Real.fromDouble;

public final class MiniModelBuilder {
    private final Map<String, Node<Real>> nodes = new LinkedHashMap<>();
    private final List<Device<Real>> devs = new ArrayList<>();
    private final Set<String> deviceIds = new HashSet<>();
    private String groundId = null;

    private int nextInternalId = 0;

    public MiniModelBuilder node(String id) {
        return node(id, "0 0+00" + id);
    }

    public MiniModelBuilder node(String id, String positionKmPlusM) {
        ensureUniqueNode(id);

        Node<Real> n = new Node<>(nextInternalId++, Real.ZERO, positionKmPlusM);
        n.setName(id);
        nodes.put(id, n);
        return this;
    }

    @Deprecated
    public MiniModelBuilder node(int id) {
        return node(String.valueOf(id));
    }

    @Deprecated
    public MiniModelBuilder node(int id, String positionKmPlusM) {
        return node(String.valueOf(id), positionKmPlusM);
    }

    public MiniModelBuilder ground(String id) {
        this.groundId = id;
        if (!nodes.containsKey(id)) node(id);
        return this;
    }

    @Deprecated
    public MiniModelBuilder ground(int id) {
        return ground(String.valueOf(id));
    }

    public MiniModelBuilder line(String id, String a, String b, double rOhm) {
        ensureNodesExist(a, b, "Line " + id);
        ensureUniqueDeviceId(id, "Line");
        devs.add(new Line(a, b, fromDouble(rOhm), id, "u", 1000));
        return this;
    }

    @Deprecated
    public MiniModelBuilder line(String id, int a, int b, double rOhm) {
        return line(id, String.valueOf(a), String.valueOf(b), rOhm);
    }

    public MiniModelBuilder substation(String id, String a, String b, double emfV, double rintOhm, boolean allowBackfeed) {
        ensureNodesExist(a, b, "Substation " + id);
        ensureUniqueDeviceId(id, "Substation");
        if (groundId == null) {
            throw new IllegalStateException("Ground node not set. Call ground(...) before substation(...).");
        }

        Substation ss = new Substation(
                id,
                a,
                b,
                groundId,
                fromDouble(emfV),
                fromDouble(rintOhm)
        );
        ss.setAllowBackfeed(allowBackfeed);
        devs.add(ss);
        return this;
    }

    @Deprecated
    public MiniModelBuilder substation(String id, int a, int b, double emfV, double rintOhm, boolean allowBackfeed) {
        return substation(id, String.valueOf(a), String.valueOf(b), emfV, rintOhm, allowBackfeed);
    }

    public MiniModelBuilder train(String id, String a, String b,
                                  double preqW, double imaxA, double cutV, double vmaxV) {
        ensureNodesExist(a, b, "Train " + id);
        ensureUniqueDeviceId(id, "Train");
        TrainLoad tr = new TrainLoad(id, a, b);
        tr.setRequestedPower(fromDouble(preqW));
        tr.setMaxCurrent(fromDouble(imaxA));
        tr.setCutoffVoltage(fromDouble(cutV));
        tr.setMaxVoltage(fromDouble(vmaxV));
        devs.add(tr);
        return this;
    }

    @Deprecated
    public MiniModelBuilder train(String id, int a, int b,
                                  double preqW, double imaxA, double cutV, double vmaxV) {
        return train(id, String.valueOf(a), String.valueOf(b), preqW, imaxA, cutV, vmaxV);
    }

    public GridModel<Real> build() {
        if (groundId == null)
            throw new IllegalStateException("Ground node not set. Call ground(id) before build().");
        if (!nodes.containsKey(groundId))
            throw new IllegalStateException("Ground node " + groundId + " was not declared via node().");

        GridModel<Real> m = new GridModel<>(groundId);

        for (Node<Real> n : nodes.values()) {
            m.addNode(n);
        }
        for (Device<Real> d : devs) {
            m.addDevice(d);
        }

        Node<Real> gnd = nodes.get(groundId);
        m.setGroundNodeId(gnd);

        return m;
    }

    private void ensureUniqueNode(String id) {
        if (nodes.containsKey(id))
            throw new IllegalArgumentException("Node " + id + " already exists.");
    }

    private void ensureNodesExist(String a, String b, String what) {
        if (!nodes.containsKey(a) || !nodes.containsKey(b))
            throw new IllegalStateException(what + " references missing node(s): " + a + ", " + b +
                    ". Declare them with node() before adding the device.");
    }

    private void ensureUniqueDeviceId(String id, String kind) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException(kind + " id must be non-empty.");
        if (!deviceIds.add(id))
            throw new IllegalArgumentException(kind + " id already exists: " + id);
    }
}