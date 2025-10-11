package org.dcsim.utils;

import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.electric.Line;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.math.Real;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exports GridModel to Graphviz DOT.
 * - Topology (undirected)
 * - With results (directed arrows along current / high->low voltage)
 * - Ground node is styled and placed at the sink rank.
 */
public final class GraphExport {

    private GraphExport() { }

    private static final int V_DECIMALS = 3;

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static Set<Integer> collectNodeIds(GridModel<Real> m) {
        Set<Integer> nodes = new LinkedHashSet<>();
        try {
            for (int n : m.getNodeIds()) nodes.add(n);
        } catch (Throwable ignore) {
            for (Device<Real> d : m.getDevices()) {
                if (d instanceof Line ln) {
                    nodes.add(ln.getFromNode());
                    nodes.add(ln.getToNode());
                } else if (d instanceof Substation ss) {
                    nodes.add(ss.getFromNode());
                    nodes.add(ss.getToNode());
                } else if (d instanceof TrainLoad tr) {
                    nodes.add(tr.getFromNode());
                    nodes.add(tr.getToNode());
                }
            }
            try { nodes.add(m.getGroundNodeId()); } catch (Throwable ignore2) {}
        }
        return nodes;
    }

    private static double nodeV(GridResult res, int node) {
        try {
            Real V = res.getLatestNodeVoltage(node);
            return (V == null) ? Double.NaN : V.asDouble();
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    private static String fmtV(double v) {
        return String.format("%." + V_DECIMALS + "f V", v);
    }

    /** Topology only (directed), optional title. */
    public static void writeDotTopology(GridModel<Real> m, Path outDot, String title) throws IOException {
        try (Writer w = Files.newBufferedWriter(outDot)) {
            w.write("digraph Grid {\n");
            w.write("  rankdir=LR;\n");
            w.write("  splines=true; overlap=false;\n");
            if (title != null && !title.isEmpty()) {
                w.write("  labelloc=\"t\"; labeljust=\"c\"; fontsize=22; fontname=\"Helvetica-Bold\";\n");
                w.write("  label=\"" + esc(title) + "\";\n");
            }

            Integer ground = null;
            try { ground = m.getGroundNodeId(); } catch (Throwable ignore) {}

            for (int n : collectNodeIds(m)) {
                boolean isGnd = (ground != null && n == ground);
                if (isGnd) {
                    w.write("  " + n + " [shape=plaintext,label=\"0\\nGND\",fontcolor=\"#555\"];\n");
                } else {
                    w.write("  " + n + " [shape=circle,label=\"" + n + "\"];\n");
                }
            }

            for (Device<Real> d : m.getDevices()) {
                int a = -1, b = -1;
                String color = "#888", style = "solid", pen = "1";
                String extra = "";
                if (d instanceof Line ln) {
                    a = ln.getFromNode(); b = ln.getToNode();
                    color="#888"; style="solid"; pen="1.4";
                    extra = "dir=both,arrowhead=normal,arrowtail=normal";
                } else if (d instanceof Substation ss){
                    a = ss.getFromNode(); b = ss.getToNode();
                    color="#1f77b4"; style="solid"; pen="2.2";
                    extra = "arrowhead=vee";
                } else if (d instanceof TrainLoad tr){
                    a = tr.getFromNode(); b = tr.getToNode();
                    color="#2ca02c"; style="dashed"; pen="2.0";
                    extra = "arrowhead=open";
                }
                if (a >= 0 && b >= 0) {
                    w.write(String.format(
                            "  %d -> %d [label=\"%s\",color=\"%s\",style=\"%s\",penwidth=%s,%s];\n",
                            a, b, esc(d.getId()), color, style, pen, extra));
                }
            }

            if (ground != null) {
                w.write("{ rank=sink; " + ground + " }\n");
            }
            w.write("}\n");
        }
    }

    /** With results: directed arrows following current direction (or high->low voltage). */
    public static void writeDotWithResults(GridModel<Real> m, GridResult res, Path outDot, String title) throws IOException {
        try (Writer w = Files.newBufferedWriter(outDot)) {
            w.write("digraph Grid {\n");
            w.write("  rankdir=LR;\n");
            if (title != null && !title.isEmpty()) {
                w.write("  labelloc=\"t\"; labeljust=\"c\"; fontsize=22; fontname=\"Helvetica-Bold\";\n");
                w.write("  label=\"" + esc(title) + "\";\n");
            }

            Integer ground = null;
            try { ground = m.getGroundNodeId(); } catch (Throwable ignore) {}

            // Nodes with voltage
            for (int n : collectNodeIds(m)) {
                boolean isGnd = (ground != null && n == ground);
                if (isGnd) {
                    w.write("  " + n + " [shape=plaintext,label=\"0\\nGND\",fontcolor=\"#555\"];\n");
                } else {
                    double v = nodeV(res, n);
                    String vstr = Double.isNaN(v) ? "?" : fmtV(v);
                    w.write("  " + n + " [shape=circle,label=\"" + n + "\\n" + vstr + "\"];\n");
                }
            }

            // Edges with ΔV and I/P; arrows by direction
            for (Device<Real> d : m.getDevices()) {
                int a = -1, b = -1;
                String color = "#888", style = "solid"; double pen = 1.0;
                if (d instanceof Line ln) {
                    a = ln.getFromNode(); b = ln.getToNode();
                    color = "#888"; style = "solid"; pen = 1.4;
                } else if (d instanceof Substation ss) {
                    a = ss.getFromNode(); b = ss.getToNode();
                    color = "#1f77b4"; style = "solid"; pen = 2.0;
                } else if (d instanceof TrainLoad tr) {
                    a = tr.getFromNode(); b = tr.getToNode();
                    color = "#2ca02c"; style = "dashed"; pen = 2.0;
                }
                if (a < 0 || b < 0) continue;

                double Va = nodeV(res, a);
                double Vb = nodeV(res, b);
                double dV = (Double.isNaN(Va) || Double.isNaN(Vb)) ? Double.NaN : (Va - Vb);

                Double I = null, P = null;
                try { Real rI = res.getLatestDeviceCurrent(d.getId()); if (rI != null) I = rI.asDouble(); } catch (Throwable ignore) {}
                try { Real rP = res.getLatestDevicePower(d.getId());   if (rP != null) P = rP.asDouble(); } catch (Throwable ignore) {}

                int from = a, to = b;
                if (I != null) {
                    if (I < 0) { from = b; to = a; }
                } else {
                    if (!Double.isNaN(dV) && dV < 0) { from = b; to = a; } // high -> low voltage
                }

                double penw = pen;
                if (I != null) {
                    double mag = Math.abs(I);
                    penw = Math.max(pen, Math.min(4.0, 1.0 + mag / 200.0));
                }

                StringBuilder label = new StringBuilder();
                label.append(esc(d.getId()));
                if (!Double.isNaN(dV)) label.append(String.format("\\nΔV=%.3f V", dV));
                if (I != null)         label.append(String.format("\\nI=%.3f A", Math.abs(I)));
                if (P != null)         label.append(String.format("\\nP=%.1f W", P));

                w.write(String.format("  %d -> %d [label=\"%s\",color=\"%s\",style=\"%s\",penwidth=%.2f,arrowhead=normal];\n",
                        from, to, label.toString(), color, style, penw));
            }

            if (ground != null) {
                w.write("{ rank=sink; " + ground + " }\n");
            }
            w.write("}\n");
        }
    }
}
