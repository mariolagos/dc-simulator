package org.dcsim.export;

import org.dcsim.electric.*;
import org.dcsim.math.Real;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CSV writer for node voltages and device powers/currents.
 * Lazy header build (on first append) so trains created by EnsureTrainDevice
 * are present. Falls back to I^2 R for line losses if solver didn’t fill them.
 *
 * Aggregates (sign convention):
 *  - Substations:  P > 0 = delivering to the network
 *  - Trains:       P > 0 = consuming from the network
 *  - Lines:        P > 0 = losses (always ≥ 0)
 *  - Brake:        P > 0 = dissipated in the brake resistor (off-network)
 *
 * mismatch = sumSub - sumTrain - sumLine          (network KCL check; ≈ 0)
 * balance  = sumSub - sumTrain - sumLine - sumBrake (total energy incl. brake; ≈ 0)
 * underSup = max(0, sumReq - sumTrain)            (requested vs actually served to trains)
 * underRecv (regen case) ~ how much regen exceeds substation absorption.
 */
public final class ResultCsvWriter implements Closeable, Flushable {

    static {
        System.out.println("[WHERE] ResultCsvWriter loaded from: "
                + ResultCsvWriter.class.getProtectionDomain().getCodeSource().getLocation());
    }

    private final GridModel model;
    private final BufferedWriter out;

    private boolean headerWritten = false;

    // Built when writing the first row:
    private final List<String> deviceOrder = new ArrayList<>();
    private final Map<String, String> prettyP = new LinkedHashMap<>();
    private final Map<String, String> prettyI = new LinkedHashMap<>();
    private final List<String> trainIds = new ArrayList<>();

    public ResultCsvWriter(GridModel model, String path) throws IOException {
        this(model, path, false);
    }

    public ResultCsvWriter(GridModel model, String path, boolean overwrite) throws IOException {
        this.model = model;
        File f = new File(path);
        if (overwrite) {
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream trunc = new FileOutputStream(f, false)) { /* truncate */ }
        }
        this.out = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
    }

    // ---------- lazy metadata ----------

    private static String catAbbrev(String cat) {
        if (cat == null || cat.isEmpty()) return "line";
        String lc = cat.toLowerCase(Locale.ROOT);
        if (lc.startsWith("feeder"))   return "feeder";
        if (lc.startsWith("catenary")) return "catenary";
        return cat;
    }

    private static String uniquify(Collection<String> used, String base) {
        if (!used.contains(base)) return base;
        int k = 2;
        while (used.contains(base + "#" + k)) k++;
        return base + "#" + k;
    }

    private void buildMetadataFromModel() {
        deviceOrder.clear(); prettyP.clear(); prettyI.clear(); trainIds.clear();

        for (String id : model.getDeviceIds()) {
            deviceOrder.add(id);
            Device<Real> d = model.getDevice(id);
            String pName, iName;

            if (d instanceof Line line) {
                String cat = null;
                try { cat = line.getCategory(); } catch (Throwable ignore) {}
                if (cat == null || cat.isEmpty()) {
                    try { cat = line.getDescription(); } catch (Throwable ignore) {}
                }
                cat = catAbbrev(cat);
                String base = "L_" + line.getFromNode() + "_" + line.getToNode() + "|" + cat;
                pName = "P[" + base + "]";
                iName = "I[" + base + "]";
            } else {
                pName = "P[" + id + "]";
                iName = "I[" + id + "]";
            }

            // ensure unique labels
            pName = uniquify(prettyP.values(), pName);
            iName = uniquify(prettyI.values(), iName);
            prettyP.put(id, pName);
            prettyI.put(id, iName);

            if (d instanceof TrainLoad) trainIds.add(id);
        }
        Collections.sort(trainIds);
    }

    private void writeHeader() throws IOException {
        StringBuilder h = new StringBuilder(4096);
        h.append("time,step");
        for (int nodeId : model.getNodeIds()) h.append(",V(").append(nodeId).append(")");
        for (String id : deviceOrder) h.append(',').append(prettyP.get(id));
        for (String id : deviceOrder) h.append(',').append(prettyI.get(id));
        for (String tid : trainIds)   h.append(",P_req[").append(tid).append(']');
        for (String tid : trainIds)   h.append(",P_brake[").append(tid).append(']');
        h.append(",P_substations_out,P_trains,P_lines,P_brake,P_req_trains,Balance,Mismatch,UnderSupply,UnderReceptivity\n");
        out.write(h.toString());
    }

    // ---------- public API ----------

    public void append(GridResult res, double timeSec, int step) throws IOException {
        if (!headerWritten) {
            buildMetadataFromModel();          // trains now exist → include them
            writeHeader();
            headerWritten = true;
        }

        StringBuilder row = new StringBuilder(4096);
        row.append(timeToHms(timeSec)).append(',').append(step);

        // --- node voltages ---
        for (int nodeId : model.getNodeIds()) {
            row.append(',').append(fmt(res.getLatestNodeVoltage(nodeId)));
        }

        // --- device powers & aggregates ---
        double sumSub = 0.0, sumLine = 0.0, sumTrain = 0.0, sumBrake = 0.0;

        for (String id : deviceOrder) {
            Device<Real> d = model.getDevice(id);
            Real p = res.getLatestDevicePower(id);

            // Fallback: compute I^2R if a line has no power set
            if (p == null && d instanceof Line line) {
                Real Va = res.getLatestNodeVoltage(line.getFromNode());
                Real Vb = res.getLatestNodeVoltage(line.getToNode());
                double R = line.getResistance().asDouble();
                if (Va != null && Vb != null && R > 0.0) {
                    double I = (Va.asDouble() - Vb.asDouble()) / R;
                    p = Real.fromDouble(I * I * R);
                }
            }

            double val = (p == null) ? 0.0 : p.asDouble();
            row.append(',').append(fmt(val));

            if (d instanceof Substation)      sumSub  += val;
            else if (d instanceof Line)       sumLine += val;
            else if (d instanceof TrainLoad)  sumTrain+= val;

            // collect per-train brake pseudo-device if present
            Real pb = res.getLatestDevicePower(id + "#brake");
            if (pb != null) sumBrake += pb.asDouble();
        }

        // --- currents ---
        for (String id : deviceOrder) {
            row.append(',').append(fmt(res.getLatestDeviceCurrent(id)));
        }

        // --- requested & brake per train ---
        double sumReq = 0.0;
        for (String tid : trainIds) {
            Real r = res.getLatestDeviceRequestedPower(tid);
            double v = (r == null) ? 0.0 : r.asDouble();
            sumReq += v;
            row.append(',').append(fmt(v));
        }
        for (String tid : trainIds) {
            row.append(',').append(fmt(res.getLatestDevicePower(tid + "#brake")));
        }

        // --- aggregates ---
        double mismatch  = sumSub - sumTrain - sumLine;              // network-only (≈ 0)
        double balance   = mismatch - sumBrake;                      // total incl brake (≈ 0)

        // Request fulfilment and receptivity
        double underSup  = Math.max(0.0, sumReq - sumTrain);         // requested vs actually served
        double underRecv = 0.0;
        if (sumTrain < 0.0) {
            // regen case: how much regen not absorbed by substations (network view)
            double absorption = Math.max(0.0, -sumSub);              // substations absorbing
            underRecv = Math.max(0.0, (-sumTrain) - absorption);
        }

        // optional cosmetic zeroing
        final double EPS_BAL = 1e-9;
        if (Math.abs(mismatch) < EPS_BAL) mismatch = 0.0;
        if (Math.abs(balance)  < EPS_BAL) balance  = 0.0;

        row.append(',')
                .append(fmt(sumSub)).append(',')
                .append(fmt(sumTrain)).append(',')
                .append(fmt(sumLine)).append(',')
                .append(fmt(sumBrake)).append(',')
                .append(fmt(sumReq)).append(',')
                .append(fmt(balance)).append(',')
                .append(fmt(mismatch)).append(',')
                .append(fmt(underSup)).append(',')
                .append(fmt(underRecv))
                .append('\n');

        out.write(row.toString());
    }

    // ---------- utils ----------

    private static String fmt(Real r) { return (r == null) ? "0" : fmt(r.asDouble()); }

    private static String fmt(double d) {
        if (!Double.isFinite(d)) return "0";
        return String.format(Locale.ROOT, "%.6f", d);
    }

    private static String timeToHms(double sec) {
        int s = (int)Math.floor(sec + 0.5);
        int h = s / 3600; s -= h*3600;
        int m = s / 60;   s -= m*60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    @Override public void flush() throws IOException { out.flush(); }
    @Override public void close() throws IOException { out.close(); }
}
