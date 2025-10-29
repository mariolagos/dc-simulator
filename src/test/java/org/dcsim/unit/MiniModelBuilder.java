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

/**
 * Superenkel test-builder för små nät.
 * Användning:
 *   GridResult res = solver.solve(new MiniModelBuilder()
 *       .node(0).node(1)
 *       .ground(0)
 *       .substation("SS", 1, 0, 900.0, 0.1, false)
 *       .line("L10", 1, 0, 0.5)
 *       .train("Tregen", 1, 0, -100_000, 500, 850, 1000)
 *       .build());
 */
public final class MiniModelBuilder {
    private final Map<Integer, Node<Real>> nodes = new LinkedHashMap<>();
    private final List<Device<Real>> devs = new ArrayList<>();
    private final Set<String> deviceIds = new HashSet<>();
    private Integer groundId = null;

    /** Skapa nod med defaultposition "0 0+00{id}" och initV = 0. */
    public MiniModelBuilder node(int id) {
        ensureUniqueNode(id);
        nodes.put(id, new Node<>(id, Real.ZERO, "0 0+00" + id));
        return this;
    }

    /** Skapa nod med explicit positionssträng (t.ex. "12 3+45"). */
    public MiniModelBuilder node(int id, String positionKmPlusM) {
        ensureUniqueNode(id);
        nodes.put(id, new Node<>(id, Real.ZERO, positionKmPlusM));
        return this;
    }

    public MiniModelBuilder ground(int id) {
        this.groundId = id;
        // säkerställ att ground-noden finns
        if (!nodes.containsKey(id)) node(id);
        return this;
    }

    /** Enkel DC-lina. */
    public MiniModelBuilder line(String id, int a, int b, double R_ohm) {
        ensureNodesExist(a, b, "Line " + id);
        ensureUniqueDeviceId(id, "Line");
        // Signatur enligt din snutt: new Line(a,b,R,"id","u",1000)
        devs.add(new Line(a, b, fromDouble(R_ohm), id, "u", 1000));
        return this;
    }

    /** Enkel station. allowBackfeed sätts via setter enligt din snutt. */
    public MiniModelBuilder substation(String id, int a, int b, double emfV, double rintOhm, boolean allowBackfeed) {
        ensureNodesExist(a, b, "Substation " + id);
        ensureUniqueDeviceId(id, "Substation");
        Substation ss = new Substation(id, a, b, // device-id, from, to
                groundId != null ? groundId : 0, // ground-id (kräver setGround innan solve)
                fromDouble(emfV), fromDouble(rintOhm));
        ss.setAllowBackfeed(allowBackfeed);
        devs.add(ss);
        return this;
    }

    /**
     * Tåg/laster.
     * preqW: +W = motor (tar från nät), −W = regen (matar till nät)
     * imaxA: max absolutström
     * cutV:  cutoff för motordrift (under detta skalas upptag ner)
     * vmaxV: övre fönster för regen
     */
    public MiniModelBuilder train(String id, int a, int b,
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

    /** Bygger en FULL modell (noder + devices) med vald ground. */
    public GridModel<Real> build() {
        if (groundId == null)
            throw new IllegalStateException("Ground node not set. Call ground(id) before build().");
        if (!nodes.containsKey(groundId))
            throw new IllegalStateException("Ground node " + groundId + " was not declared via node().");

        GridModel<Real> m = new GridModel<>(groundId);

        // Lägg in noder
        for (Node<Real> n : nodes.values()) {
            m.addNode(n);
        }
        // Lägg in devices
        for (Device<Real> d : devs) {
            m.addDevice(d);
        }
        return m;
    }

    // -------- valideringshjälp

    private void ensureUniqueNode(int id) {
        if (nodes.containsKey(id))
            throw new IllegalArgumentException("Node " + id + " already exists.");
    }

    private void ensureNodesExist(int a, int b, String what) {
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
