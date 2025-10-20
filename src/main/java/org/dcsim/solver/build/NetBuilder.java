package org.dcsim.solver.build;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.impl.DcDebug;

import java.util.*;

import static org.dcsim.solver.impl.DcDebug.VERBOSE;

/**
 * Translates a production {@code GridModel<Real>} into a compact-indexed {@link DcNet}.
 * Responsibilities:
 * <ul>
 *   <li>Collect nodes and build compact 0..n-1 indexing (with {@code groundIndex}).</li>
 *   <li>Map original node IDs → indices via {@code indexById()}.</li>
 *   <li>Emit {@code LineData}, {@code SubstationData}, and (optionally) {@code TrainData} lists.</li>
 * </ul>
 * This is the "makeNet" step for the production path.
 *
 * In tests you can bypass this and build {@link DcNet} directly using fixtures (see {@code NetFixtures}).
 */
public final class NetBuilder {
    public static volatile boolean VERBOSE = false;

    private NetBuilder() {}

    // Slå på för mer feedback vid "ignorera okända noder"
    private static final boolean WARN_UNKNOWN_NODE = false;

    public static DcNet makeNet(GridModel<Real> model) {
        // 1) Noder och index
        final List<Node<Real>> modelNodes = new ArrayList<>(model.getNodes());
        if (modelNodes.isEmpty()) {
            throw new IllegalArgumentException("Model has no nodes.");
        }

        final int n = modelNodes.size();
        final List<Integer> nodeIds = new ArrayList<>(n);
        final Map<Integer, Integer> idxById = new HashMap<>(n * 2);

        for (int i = 0; i < n; i++) {
            int id = modelNodes.get(i).getId();
            nodeIds.add(id);
            idxById.put(id, i);
        }

        // 2) Ground-index
        final Integer groundNodeId = model.getGroundNodeId();
        if (groundNodeId == null)
            throw new IllegalArgumentException("Model ground node is null.");
        final Integer gnd = idxById.get(groundNodeId);
        if (gnd == null)
            throw new IllegalArgumentException("Ground node " + groundNodeId + " is not present in node list.");

        // 3) Devices (kompakta index)
        final List<LineData> lines = new ArrayList<>();
        final List<SubstationData> substations = new ArrayList<>();
        final List<TrainData> trains = new ArrayList<>();

        for (Device<Real> d : model.getDevices()) {
            if (d instanceof Line ln) {
                final Integer a = idxById.get(ln.getFromNode());
                final Integer b = idxById.get(ln.getToNode());
                if (a == null || b == null) {
                    if (WARN_UNKNOWN_NODE) {
                        System.out.println("[NetBuilder] Skip Line " + ln.getId()
                                + " due to unknown node(s) " + ln.getFromNode() + " or " + ln.getToNode());
                    }
                    continue;
                }
                final double R = safe(ln.getResistance());
                if (!(R > 0.0))
                    throw new IllegalArgumentException("Line " + ln.getId() + " has non-positive resistance: " + R);
                lines.add(new LineData(ln.getId(), a, b, R));

            } else if (d instanceof Substation ss) {
                final Integer a = idxById.get(ss.getFromNode());
                final Integer b = idxById.get(ss.getToNode());
                if (a == null || b == null) {
                    if (WARN_UNKNOWN_NODE) {
                        System.out.println("[NetBuilder] Skip Substation " + ss.getId()
                                + " due to unknown node(s) " + ss.getFromNode() + " or " + ss.getToNode());
                    }
                    continue;
                }
                final double E  = safe(ss.getEmf());
                final double R  = safe(ss.getInternalResistance());
                final boolean allow = ss.isAllowBackfeed();

                if (!(R > 0.0))
                    throw new IllegalArgumentException("Substation " + ss.getId() + " has non-positive internal R: " + R);
                if (!Double.isFinite(E))
                    throw new IllegalArgumentException("Substation " + ss.getId() + " has non-finite EMF: " + E);

                // Har din SubstationData fler fält (t.ex. current_max)? Lägg till här om/när det behövs.
                substations.add(new SubstationData(ss.getId(), a, b, E, R, allow));

            } else if (d instanceof TrainLoad tr) {
                final Integer a = idxById.get(tr.getFromNode());
                final Integer b = idxById.get(tr.getToNode());
                if (a == null || b == null) {
                    if (WARN_UNKNOWN_NODE) {
                        System.out.println("[NetBuilder] Skip Train " + tr.getId()
                                + " due to unknown node(s) " + tr.getFromNode() + " or " + tr.getToNode());
                    }
                    continue;
                }

                final double reqW  = safe(tr.getRequestedPower()); // +W = motordrift, −W = regen
                final double imaxA = safe(tr.getMaxCurrent());
                final double cutV  = safe(tr.getCutoffVoltage());
                final double vmaxV = safe(tr.getMaxVoltage());

                // Rimlighetskoll – imax kan vara 0 (obegränsad i solve), men varna om negativ:
                if (imaxA < 0.0)
                    throw new IllegalArgumentException("Train " + tr.getId() + " has negative Imax: " + imaxA);
                if (!(vmaxV > 0.0))
                    throw new IllegalArgumentException("Train " + tr.getId() + " has non-positive vmax: " + vmaxV);
                if (cutV < 0.0)
                    throw new IllegalArgumentException("Train " + tr.getId() + " has negative cutoff voltage: " + cutV);

                // OBS: Stämmer denna signatur med din TrainData? Om inte, ändra ordningen/fälten.
                trains.add(new TrainData(tr.getId(), a, b, reqW, imaxA, cutV, vmaxV));
            }
        }

        DcNet net = new DcNet(
                n,
                gnd,
                Collections.unmodifiableList(nodeIds),
                Collections.unmodifiableMap(idxById),
                Collections.unmodifiableList(lines),
                Collections.unmodifiableList(substations),
                Collections.unmodifiableList(trains)
        );

        if (VERBOSE || DcDebug.VERBOSE) {
            DcDebug.log("[NetBuilder] Network built from GridModel");
            try {
                // säkra, publika saker DcNet faktiskt har:
                DcDebug.log("[NetBuilder] nodes=%d (ground=%d)", net.nodeIds().size(), net.groundIndex());
                DcDebug.log("[NetBuilder] substations=%d, trains=%d, lines=%d",
                        net.substations().size(),
                        net.trains().size(),
                        net.lines().size());
                // valfritt: lista noder
                DcDebug.log("[NetBuilder] nodeIds=%s", net.nodeIds());
            } catch (Throwable t) {
                DcDebug.log("[NetBuilder] topo dump error: %s", String.valueOf(t));
            }
        }

        return net;
    }

    private static double safe(Real r) {
        return (r == null) ? 0.0 : r.asDouble();
    }
}
