package org.dcsim.solver.build;

import org.dcsim.electric.DcLine;
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

    public NetBuilder() {}

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
            int id = modelNodes.get(i).get_internal_id();
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

        // v0.8: choose which line devices to use
        List<Device<Real>> lineDevices;
        if (!model.getDynamicLineDevices().isEmpty()) {
            // dynamic topology present -> use those only
            lineDevices = new ArrayList<>(model.getDynamicLineDevices());
            System.out.println("[ADAPT] Using DYNAMIC line devices: n=" + lineDevices.size());
        } else {
            // legacy behaviour: use static lines
            lineDevices = new ArrayList<>(model.getLines());
            System.out.println("[ADAPT] Using STATIC line devices: n=" + lineDevices.size());
        }

        for (Device<Real> d : lineDevices) {
            if (d instanceof Line ln) {
                Line l = (Line) d;
                int fromId = l.getFromNode();
                int toId = l.getToNode();
                Integer a = idxById.get(fromId);
                Integer b = idxById.get(toId);
                if (a == null || b == null) {
                    System.out.printf("[ADAPT-LINE] SKIP static %s from=%d to=%d (idx a=%s b=%s)%n",
                            l.getDescription(), fromId, toId, a, b);
                    continue;
                }

                double R = l.getResistance().asDouble();
                System.out.printf("[ADAPT-LINE] static %s a=%d b=%d R=%.6f%n",
                        l.getDescription(), a, b, R);

                lines.add(new LineData(l.getId(), a, b, R));

            } else if (d instanceof DcLine) {
                DcLine l = (DcLine) d;
                int fromId = l.getFromNode();
                int toId = l.getToNode();
                Integer a = idxById.get(fromId);
                Integer b = idxById.get(toId);
                if (a == null || b == null) {
                    System.out.printf("[ADAPT-DCLINE] SKIP dynamic %s from=%d to=%d (idx a=%s b=%s)%n",
                            l.getDescription(), fromId, toId, a, b);
                    continue;
                }

                double R = l.getResistance().asDouble();
                System.out.printf("[ADAPT-DCLINE] dynamic %s a=%d b=%d R=%.6f%n",
                        l.getDescription(), a, b, R);

                lines.add(new LineData(l.getId(), a, b, R));
            }
        }

        for (Device<Real> d : model.getDevices()) {

            if (d instanceof Substation ss) {
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
                if (a == b)
                    throw new IllegalArgumentException("Substation " + ss.getId() + " must have distinct terminals");

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

                final double reqW  = safe(tr.getRequestedPower());
                final double imaxA = safe(tr.getMaxCurrent());
                final double cutV  = safe(tr.getCutoffVoltage());
                final double vmaxV = safe(tr.getMaxVoltage());

                if (imaxA < 0.0)
                    throw new IllegalArgumentException("Train " + tr.getId() + " has negative Imax: " + imaxA);
                if (!(vmaxV > 0.0))
                    throw new IllegalArgumentException("Train " + tr.getId() + " has non-positive vmax: " + vmaxV);
                if (cutV < 0.0)
                    throw new IllegalArgumentException("Train " + tr.getId() + " has negative cutoff voltage: " + cutV);

                trains.add(new TrainData(tr.getId(), a, b, reqW, imaxA, cutV, vmaxV));
            }
        }

        System.out.printf("[NetBuilder] substations=%d, trains=%d, lines=%d%n",
                substations.size(), trains.size(), lines.size());

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
