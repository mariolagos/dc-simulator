package org.dcsim;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.*;
import org.dcsim.export.ResultCsvWriter;
import org.dcsim.math.Real;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;
import org.dcsim.PowerPoint;
import org.dcsim.utils.PositionUtils;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Batch runner (no Akka): loads grid + traffic + profiles,
 * creates TrainLoad devices, and runs the DC solver while writing CSV.
 *
 * v0.4: Also maps each train to the nearest infrastructure node per tick
 * based on its profile position (supports both "line km+m" and "km.m").
 */
public final class DcSimApp {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: DcSimApp <path-to-application.conf>");
            System.exit(1);
        }

        File confFile = new File(args[0]);
        if (!confFile.exists()) {
            System.err.println("[ERROR] Config file not found: " + confFile.getAbsolutePath());
            System.exit(2);
        }

        // -------- Load config --------
        Config root  = ConfigFactory.parseFile(confFile).resolve();
        Config dcsim = root.getConfig("dcsim");

        // -------- Simulation control --------
        Config sim   = dcsim.getConfig("simulationControl");
        double tickSec = sim.getDouble("tickDuration");
        int startSec   = parseHmsToSeconds(sim.getString("simulationStart"));
        int endSec     = parseHmsToSeconds(sim.getString("simulationEnd"));
        if (endSec <= startSec) throw new IllegalArgumentException("simulationEnd must be after simulationStart.");

        // -------- Grid model --------
        GridModel model = GridModelLoader.load(dcsim.getConfig("grid"));
        System.out.printf(Locale.ROOT, "[DcSim] Loaded grid: %d nodes, %d devices%n",
                model.getNodes().size(), model.getDevices().size());

        // -------- Power templates (Excel) --------
        Map<String, List<PowerPoint>> profileMap = Collections.emptyMap();
        if (dcsim.hasPath("powerProfiles")) {
            profileMap = PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"));
            System.out.println("[DcSim] Templates loaded: " + profileMap.keySet());
        }

        // -------- Traffic → create TrainLoad devices and bind profiles --------
        Map<String, PowerProfile> profileByTemplate = new HashMap<>();
        for (var e : profileMap.entrySet()) {
            profileByTemplate.put(e.getKey(), new PowerProfile(e.getValue()));
        }

        int ground = model.getGroundNodeId();
        int createdTrains = 0;

        if (dcsim.hasPath("traffic")) {
            Config traffic   = dcsim.getConfig("traffic");
            var timetable    = traffic.getConfig("timetable");
            var trainsConf   = timetable.getConfigList("trains");
            Config templates = traffic.getConfig("templates");

            for (Config tr : trainsConf) {
                String id         = tr.getString("id");
                String templateId = tr.getString("templateId");

                // Start position from first stop signature
                var stops = templates.getConfig(templateId).getConfigList("stops");
                String firstSig = stops.get(0).getString("signature");
                String startPos = stationPositionByAbbrev(dcsim.getConfig("track"), firstSig);
                int startNodeId = gridNodeIdByPositionString(model, startPos);

                TrainLoad tl = new TrainLoad(id, startNodeId, ground);
                model.addDevice(tl);
                createdTrains++;

                PowerProfile prof = profileByTemplate.get(templateId);
                if (prof != null) {
                    tl.setPowerProfile(prof);
                }
                System.out.printf(Locale.ROOT, "[DcSim] Train %s created at node %d (template=%s, profile=%s)%n",
                        id, startNodeId, templateId, (prof != null));
            }
        }

        System.out.printf(Locale.ROOT, "[DcSim] Added %d TrainLoad device(s). Total devices: %d%n",
                createdTrains, model.getDevices().size());

        // -------- Build topology from infrastructure node positions --------
        Map<Integer, String> infraPos = model.getNodes().stream()
                .filter(n -> n.getPosition() != null && !n.getPosition().isBlank())
                .collect(Collectors.toMap(Node::getId, Node::getPosition));
        NearestNodeTopology topo = new NearestNodeTopology(infraPos);

        // -------- Solver & CSV writer --------
        DcElectricSolver solver = new DcElectricSolver();

        String outPath = "output/electrical.csv";
        File outFile = new File(outPath);
        outFile.getParentFile().mkdirs();
        System.out.println("[DcSim] Writing results to: " + outFile.getAbsolutePath());

        ResultCsvWriter writer;
        try {
            writer = new ResultCsvWriter(model, outPath); // truncates/creates fresh file
        } catch (UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ResultCsvWriter: " + e, e);
        }

        // -------- Main loop --------
        // Default line if profile positions use "km.m" without explicit line id.
        final int defaultLineId = 1;

        int step = 0;
        for (double t = startSec; t <= endSec; t += tickSec, step++) {
            // 1) For each train: sample requested power and map position → nearest node
            double sumReqW = 0.0;

            for (Device<Real> dev : model.getDevices()) {
                if (dev instanceof TrainLoad tl) {

                    // power
                    PowerProfile prof = tl.getPowerProfile();
                    Real req = (prof != null) ? prof.getPowerAtTime(t) : Real.ZERO;
                    tl.setRequestedPower(req);
                    sumReqW += req.asDouble();

                    // position (if available in profile points) → nearest node
                    if (prof != null && prof.getPoints() != null && !prof.getPoints().isEmpty()) {
                        String bis = sampleBisPositionAt(prof.getPoints(), t);
                        if (bis != null && !bis.isBlank()) {
                            int[] pos = PositionUtils.parseFlexible(bis, defaultLineId);
                            int node  = topo.nearestInfraNodeId(pos);
                            if (node >= 0 && node != tl.getFromNode()) {
                                tl.setFromNode(node);
                            }
                        }
                    }
                }
            }

            if ((step % 10) == 0) {
                System.out.printf(Locale.ROOT, "[t=%s step=%d] ΣP_req = %.1f kW%n",
                        formatHms((int) t), step, sumReqW / 1000.0);
            }

            // 2) Solve
            GridResult result = solver.solve(model, t, step);

            // 3) CSV
            try {
                writer.append(result, t, step);
            } catch (Exception ex) {
                System.err.println("[DcSim] CSV append failed at t=" + t + ": " + ex);
                break;
            }
        }

        try { writer.close(); } catch (Exception ignore) {}
        System.out.println("[DcSim] Finished OK.");
    }

    // --- helpers ---

    private static int parseHmsToSeconds(String s) {
        String[] p = s.trim().split(":");
        if (p.length < 2 || p.length > 3) throw new IllegalArgumentException("HH:mm or HH:mm:ss: " + s);
        int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        int sec = (p.length == 3) ? Integer.parseInt(p[2]) : 0;
        return h * 3600 + m * 60 + sec;
    }

    private static String formatHms(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    /** Look up a station position string by abbreviation in dcsim.track.stations */
    private static String stationPositionByAbbrev(Config trackCfg, String abbrev) {
        for (Config st : trackCfg.getConfigList("stations")) {
            if (st.getString("abbreviation").equals(abbrev)) {
                return st.getString("position");
            }
        }
        throw new IllegalArgumentException("Station abbreviation not found: " + abbrev);
    }

    /** Map a position string (e.g. "1 0+000") to the id of the grid node with the same stored position string. */
    private static int gridNodeIdByPositionString(GridModel model, String positionString) {
        return model.getNodes().stream()
                .filter(n -> positionString.equals(n.getPosition()))
                .findFirst()
                .map(Node::getId)
                .orElseGet(() -> {
                    // fallback: the first non-ground node
                    int g = model.getGroundNodeId();
                    return model.getNodes().stream().map(Node::getId)
                            .filter(id -> id != g).findFirst()
                            .orElseThrow(() -> new IllegalStateException("No non-ground nodes in grid"));
                });
    }

    /**
     * Linear sample of bisPosition at time t.
     * Assumes PowerPoint has time() and bisPosition() accessors.
     */
    private static String sampleBisPositionAt(List<PowerPoint> pts, double tSec) {
        if (pts == null || pts.isEmpty()) return null;

        if (tSec <= pts.get(0).time()) return pts.get(0).position();
        if (tSec >= pts.get(pts.size()-1).time()) return pts.get(pts.size()-1).position();

        int hi = 1;
        while (hi < pts.size() && pts.get(hi).time() < tSec) hi++;
        int lo = hi - 1;

        // If segment crosses different lineIds, pick the nearer in time.
        // Here we just return the closer endpoint's string to avoid guessing interpolation across lines.
        double tLo = pts.get(lo).time();
        double tHi = pts.get(hi).time();
        double a = (tSec - tLo) / Math.max(1e-9, (tHi - tLo));
        return (a < 0.5) ? pts.get(lo).position() : pts.get(hi).position();
    }
}
