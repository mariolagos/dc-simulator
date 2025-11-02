package org.dcsim.export;

import org.dcsim.electric.*;
import org.dcsim.math.Real;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CSV-skrivare: spänningar, effekter, tågtelemetri och aggregat.
 *
 * Viktigt:
 *  - Endast bas-ID för tåg i kolumnerna: "T1", "T2", ...
 *  - V[T] = buss-spänningen på tågets anslutningsnod (TrainLoad.fromNode).
 *  - P_substations_internal = sum(I^2 * Rint) för stationerna.
 */
public final class ResultCsvWriter implements Closeable, Flushable {

    private String project = "";
    private String scenario = "";
    private String baseHash = "";
    private boolean headerWritten = false;

    private final GridModel<Real> model;
    private final BufferedWriter bw;
    private boolean writeHeader = true;
    private int everyNthStep = 1;

    // stabil ordning
    private final List<Substation> substations = new ArrayList<>();
    private final List<Line>       lines       = new ArrayList<>();

    // bara bas-ID:n (T1, T2, …) i den ordning de lades till
    private final LinkedHashSet<String> knownTrainIds = new LinkedHashSet<>();

    // per-tick broms (W), nycklad med bas-ID (T1…)
    private Map<String,Double> lastBrakeReqW = Collections.emptyMap();
    private Map<String,Double> lastBrakeNetW = Collections.emptyMap();
    private Map<String,Double> lastBrakeResW = Collections.emptyMap();

    // --- ctors ---
    public ResultCsvWriter(GridModel<Real> model, String path, boolean overwrite) throws IOException {
        this.model = model;
        File f = new File(path);
        if (overwrite) {
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream trunc = new FileOutputStream(f, false)) { /* truncate */ }
        }
        this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
        this.headerWritten = f.exists() && f.length() > 0 && !overwrite;
        if (!this.headerWritten) {
            writeLine("time_s,project,scenario,base_hash,object_type,object_id,signal,value,unit,stage,iter,note");
            this.headerWritten = true;
        }
        indexModel();
    }

    public ResultCsvWriter(GridModel<Real> model, String path) throws IOException {
        this(model, path, false);
    }

    public void signalRow(Double time_s,
                          String objectType, String objectId,
                          String signal, Double value, String unit,
                          String stage, Integer iter, String note) {
        // tomma => blanks
        String line = String.format(java.util.Locale.ROOT,
                "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                toCsv(time_s), toCsv(project), toCsv(scenario), toCsv(baseHash),
                toCsv(objectType), toCsv(objectId), toCsv(signal), toCsv(value),
                toCsv(unit), toCsv(stage), toCsv(iter), toCsv(note)
        );
        writeLine(line);
    }

    private void writeLine(String s) {
        try {
            bw.write(s);
            bw.newLine();
            bw.flush(); // säkert för strömmande
        } catch (IOException e) {
            System.err.println("[CSV] write failed: " + e.getMessage());
        }
    }

    private static String toCsv(Object o) { return o == null ? "" : o.toString(); }

    public ResultCsvWriter setRunIdentity(String project, String scenario, String baseHash) {
        this.project = project != null ? project : "";
        this.scenario = scenario != null ? scenario : "";
        this.baseHash = baseHash != null ? baseHash : "";
        return this;
    }

    // --- knobs ---
    public void setEveryNthStep(int n) { this.everyNthStep = Math.max(1, n); }
    public void setWriteHeader(boolean enabled) { this.writeHeader = enabled; }

    /** Ta emot vilka tåg som ska synas som kolumner. Allt normaliseras till bas-ID (t.ex. "T1"). */
    public void setKnownTrains(Collection<String> ids) {
        if (ids == null) return;
        for (String raw : ids) {
            String base = toBaseTrainId(raw);
            if (base != null && !base.isBlank()) knownTrainIds.add(base);
        }
    }

    /** Legacy: om bara "net"-broms skickas, skriv den – övriga 0. */
    public void setLatestBrakeW(Map<String, Double> netW) {
        this.lastBrakeReqW = Collections.emptyMap();
        this.lastBrakeNetW = normalizeBrakeMap(netW);
        this.lastBrakeResW = Collections.emptyMap();
    }

    /** Föredragen: full uppdelning. */
    public void setLatestBrakeMaps(Map<String, Double> reqW,
                                   Map<String, Double> netW,
                                   Map<String, Double> resW) {
        this.lastBrakeReqW = normalizeBrakeMap(reqW);
        this.lastBrakeNetW = normalizeBrakeMap(netW);
        this.lastBrakeResW = normalizeBrakeMap(resW);
    }

    // --- skriv en rad ---
    public void append(GridResult res, double timeSec, int step) throws IOException {
        if ((step % everyNthStep) != 0) return;

        if (!headerWritten && writeHeader) {
            writeHeader();
            headerWritten = true;
        }

        List<String> cols = new ArrayList<>();
        cols.add(formatTime(timeSec));
        cols.add(Integer.toString(step));

        // 1) stationers buss-spänningar
        for (Substation ss : substations) cols.add(real(res.getLatestNodeVoltage(ss.getFromNode())));

        // 2) stationers enhetseffekt (t.ex. interna laster mm — OBS: denna är INTE P_out)
        for (Substation ss : substations) cols.add(real(res.getLatestDevicePower(ss.getId())));

        // 3) tåg: V, P_net, I, x, v, P_req
        double sumPTrains = 0.0;
        double sumPReq    = 0.0;

        for (String t : knownTrainIds) cols.add(trainBusV(res, t));

        for (String t : knownTrainIds) {
            double p = trainPower(res, t);
            sumPTrains += p;
            cols.add(fmt(p)); // P_net[T]
        }
        for (String t : knownTrainIds) cols.add(trainCurrent(res, t));
        for (String t : knownTrainIds) cols.add(trainPos(res, t));    // OK om 0 tills vidare
        for (String t : knownTrainIds) cols.add(trainSpeed(res, t));  // OK om 0 tills vidare

        for (String t : knownTrainIds) {
            double r = trainRequestedPowerVal(res, t);
            sumPReq += r;
            cols.add(fmt(r)); // P_req[T]
        }

        // 4) broms per tåg
        double sumBrakeRes = 0.0;
        for (String t : knownTrainIds) cols.add(fmt(get(lastBrakeReqW, t)));
        for (String t : knownTrainIds) cols.add(fmt(get(lastBrakeNetW, t)));
        for (String t : knownTrainIds) {
            double v = get(lastBrakeResW, t);
            sumBrakeRes += v;
            cols.add(fmt(v));
        }

        // 5) linjeförluster
        double sumLines = 0.0;
        for (Line ln : lines) {
            Real p = res.getLatestDevicePower(ln.getId());
            if (p != null) sumLines += p.asDouble();
        }

        // 6) stationers OUT (= V_bus * I_ss) och interna (I^2 Rint)
        double sumSubOut = 0.0;
        double sumSubInternal = 0.0;
        double substationAbsorb = 0.0;

        for (Substation ss : substations) {
            Real v = res.getLatestNodeVoltage(ss.getFromNode());
            Real i = res.getLatestDeviceCurrent(ss.getId());

            if (v != null && i != null) {
                double vv = v.asDouble(), ii = i.asDouble();
                double pOut = vv * ii;                    // kan vara <0 om stationen absorberar
                sumSubOut += pOut;
                if (pOut < 0) substationAbsorb += -pOut;

                // internförlust ≈ I^2 * Rint
                double rint = 0.0;
                try {
                    Real rr = ss.getInternalResistance();
                    if (rr != null) rint = rr.asDouble();
                } catch (Throwable ignore) {}
                sumSubInternal += (ii * ii * rint);
            }
        }

        // 7) aggregat
        double mismatch = sumSubOut - sumPTrains - sumLines; // ∼0 numeriskt
        double balance  = mismatch - sumBrakeRes;            // ∼0 när rheostat används
        double underSup = Math.max(0.0, sumPReq - sumPTrains);
        double underRec = Math.max(0.0, (-sumPTrains) - substationAbsorb);

        cols.add(fmt(sumSubOut));
        cols.add(fmt(sumSubInternal));
        cols.add(fmt(sumPTrains));
        cols.add(fmt(sumLines));
        cols.add(fmt(sumBrakeRes));
        cols.add(fmt(sumPReq));
        cols.add(fmt(balance));
        cols.add(fmt(mismatch));
        cols.add(fmt(underSup));
        cols.add(fmt(underRec));

        bw.write(String.join(",", cols));
        bw.write("\n");
    }

    // --- indexera modell ---
    private void indexModel() {
        for (Object idObj : model.getDeviceIds()) {
            String id = String.valueOf(idObj);
            Device<Real> d = model.getDevice(id);
            if (d instanceof Substation ss) substations.add(ss);
            else if (d instanceof Line ln) lines.add(ln);
        }
        substations.sort(Comparator.comparing(Substation::getId, String.CASE_INSENSITIVE_ORDER));
        lines.sort(Comparator.comparing(Line::getId, String.CASE_INSENSITIVE_ORDER));
    }

    // --- header ---
    private void writeHeader() throws IOException {
        StringBuilder h = new StringBuilder(2048);
        h.append("time,step");

        for (Substation ss : substations) h.append(",V[").append(ss.getId()).append(']');
        for (Substation ss : substations) h.append(",P[").append(ss.getId()).append(']');

        for (String t : knownTrainIds) h.append(",V[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",P_net[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",I[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",x[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",v[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",P_req[").append(t).append(']');

        for (String t : knownTrainIds) h.append(",P_brake_req[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",P_brake_net[").append(t).append(']');
        for (String t : knownTrainIds) h.append(",P_brake_resistor[").append(t).append(']');

        h.append(",P_substations_out")
                .append(",P_substations_internal")
                .append(",P_trains")
                .append(",P_lines")
                .append(",P_brake_resistor")
                .append(",P_req_trains")
                .append(",Balance")
                .append(",Mismatch")
                .append(",UnderSupply")
                .append(",UnderReceptivity")
                .append("\n");

        bw.write(h.toString());
    }

    // --- hjälpmetoder (tåg) ---
    private String resolveTrainDeviceId(String baseId) {
        // prova "T1", sedan "Train:T1"
        try {
            Device<Real> a = model.getDevice(baseId);
            if (a instanceof TrainLoad) return baseId;
        } catch (Throwable ignore) {}
        try {
            String alt = "Train:" + baseId;
            Device<Real> b = model.getDevice(alt);
            if (b instanceof TrainLoad) return alt;
        } catch (Throwable ignore) {}
        return null;
    }

    private String trainBusV(GridResult res, String baseId) {
        String devId = resolveTrainDeviceId(baseId);
        if (devId == null) return "0";
        Device<Real> d = model.getDevice(devId);
        if (d instanceof TrainLoad tl) {
            Real v = res.getLatestNodeVoltage(tl.getFromNode());
            return real(v);
        }
        return "0";
    }

    private double trainPower(GridResult res, String baseId) {
        String devId = resolveTrainDeviceId(baseId);
        if (devId == null) return 0.0;
        Real p = res.getLatestDevicePower(devId);
        return (p != null) ? p.asDouble() : 0.0;
    }

    private String trainCurrent(GridResult res, String baseId) {
        String devId = resolveTrainDeviceId(baseId);
        if (devId == null) return "0";
        return real(res.getLatestDeviceCurrent(devId));
    }

    private double trainRequestedPowerVal(GridResult res, String baseId) {
        String devId = resolveTrainDeviceId(baseId);
        if (devId == null) return 0.0;
        Real r = res.getLatestDeviceRequestedPower(devId);
        return (r != null) ? r.asDouble() : 0.0;
    }
    private String trainRequestedPower(GridResult res, String baseId) {
        return fmt(trainRequestedPowerVal(res, baseId));
    }

    private String trainPos(GridResult res, String baseId) {
        Map<String, List<Real>> px = model.getUpdatedTrainPositions();
        if (px != null) {
            List<Real> s = px.get("Train:" + baseId);
            if (s != null && !s.isEmpty()) return real(s.get(s.size()-1));
        }
        return "0";
    }

    private String trainSpeed(GridResult res, String baseId) {
        Map<String, List<Real>> pv = model.getUpdatedTrainSpeeds();
        if (pv != null) {
            List<Real> s = pv.get("Train:" + baseId);
            if (s != null && !s.isEmpty()) return real(s.get(s.size()-1));
        }
        return "0";
    }

    // --- utils ---
    private static String toBaseTrainId(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("Train:")) s = s.substring("Train:".length()).trim(); // Train:T1 -> T1
        // Train1 -> T1
        if (s.matches("(?i)train\\d+")) return "T" + s.replaceAll("(?i)train", "");
        // T1 -> T1
        return s;
    }

    private static Map<String,Double> normalizeBrakeMap(Map<String,Double> src) {
        if (src == null || src.isEmpty()) return Collections.emptyMap();
        Map<String,Double> out = new HashMap<>();
        for (var e : src.entrySet()) {
            String k = toBaseTrainId(e.getKey());
            if (k == null) continue;
            double v = (e.getValue() == null) ? 0.0 : e.getValue();
            if (v > 0) out.put(k, v);
        }
        return out;
    }

    private static String real(Real r) { return (r == null) ? "0" : fmt(r.asDouble()); }
    private static String fmt(double d) {
        if (!Double.isFinite(d)) return "0";
        return String.format(java.util.Locale.ROOT, "%.6f", d);
    }

    private static String formatTime(double sec) {
        int s = (int)Math.floor(sec + 0.5);
        int h = s / 3600; s -= h*3600;
        int m = s / 60;   s -= m*60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    private static double get(Map<String,Double> m, String k) {
        if (m == null) return 0.0;
        Double v = m.get(k);
        return (v != null) ? v : 0.0;
    }

    @Override public void flush() throws IOException { bw.flush(); }
    @Override public void close() throws IOException { bw.close(); }
}
