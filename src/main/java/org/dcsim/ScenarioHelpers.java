package org.dcsim;

import com.typesafe.config.Config;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.dcsim.config.PowerProfiles;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.power.PointsPowerProfile;
import org.dcsim.power.PowerProfile;
import org.dcsim.power.PowerTemplateParser;
import org.dcsim.utils.PositionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScenarioHelpers {

    private ScenarioHelpers() {
    }

    public static Map<String, List<PowerPoint>> getNormalisedTemplates(
            Config dcsim,
            Map<Integer, ContractChecks.TrackExtent> extentByTrack,
            List<TrackPoint> trackPoints
    ) throws Exception {

        Map<String, List<PowerPoint>> byTpl = dcsim.hasPath("powerProfiles")
                ? PowerTemplateParser.parse(dcsim.getConfig("powerProfiles"))
                : Collections.emptyMap();

        Map<String, List<PowerPoint>> byTplNormalised = new LinkedHashMap<>();

        for (var e : byTpl.entrySet()) {
            final List<PowerPoint> src = e.getValue();
            final List<PowerPoint> dst = new ArrayList<>(src.size());

            for (PowerPoint p : src) {
                String posStr = p.positionString();
                double posM = p.positionM();

                if (Double.isNaN(posM) && posStr != null && !posStr.isBlank()) {
                    try {
                        posM = Double.parseDouble(posStr.trim().replace(',', '.'));
                    } catch (NumberFormatException ex) {
                        var tp = PositionUtils.parseFlexibleTP(posStr).normalized();
                        posM = Math.floor(tp.metersInLine());
                    }
                }

                if (!Double.isNaN(posM)) {
                    posM = toAbsolutePositionM(posM, trackPoints);
                }

                dst.add(p.withPositionM(posM));
            }

            ContractChecks.validateRunPointsAgainstModelExtent(dst, extentByTrack);
            byTplNormalised.put(e.getKey(), dst);
        }

        return byTplNormalised;
    }

    public static Map<String, PowerProfile> getProfilesByTemplate(
            Map<String, List<PowerPoint>> byTplNormalised
    ) {
        Map<String, PowerProfile> profileByTemplate = new HashMap<>();
        for (var e : byTplNormalised.entrySet()) {
            profileByTemplate.put(e.getKey(), new PointsPowerProfile(e.getValue()));
        }
        return profileByTemplate;
    }

    public static List<SimulationInputModel.TrainSpawn> getTrainSpawns(
            Config dcsim,
            Map<String, PowerProfile> profileByTemplate,
            int startSec
    ) {
        boolean sameModel = dcsim.hasPath("powerProfiles.motoringAndAuxiliariesInSameModel")
                && dcsim.getBoolean("powerProfiles.motoringAndAuxiliariesInSameModel");
        double auxKW = dcsim.hasPath("powerProfiles.auxiliaryPower")
                ? dcsim.getDouble("powerProfiles.auxiliaryPower") : 0.0;

        List<SimulationInputModel.TrainSpawn> spawns = new ArrayList<>();
        if (!dcsim.hasPath("traffic")) {
            return spawns;
        }

        var timetable = dcsim.getConfig("traffic.timetable");
        var trainsConf = timetable.getConfigList("trains");

        for (Config tr : trainsConf) {
            String idBase = tr.getString("id");
            String tpl = tr.getString("templateId");
            int dep0Abs = parseHmsToSeconds(tr.getString("departure"));
            Integer headway = optHmsToSeconds(tr, "headway");
            int count = tr.hasPath("count") ? tr.getInt("count") : 1;
            String sig = tr.hasPath("signature") ? tr.getString("signature") : "";

            PowerProfile prof = profileByTemplate.get(tpl);
            if (prof == null) {
                throw new IllegalArgumentException("Missing power profile for templateId=" + tpl);
            }

            for (int k = 0; k < count; k++) {
                int depKAbs = dep0Abs + ((headway != null) ? k * headway : 0);
                int depRel = Math.max(0, depKAbs - startSec);
                String tid = (count == 1) ? idBase : uniqId(idBase, sig, k + 1);

                spawns.add(new SimulationInputModel.TrainSpawn(
                        tid, prof, depRel, sameModel, auxKW
                ));
            }
        }

        return spawns;
    }

    public static List<TrackPoint> buildTrackPoints(Sheet shTrack) {
        if (shTrack == null) {
            throw new IllegalArgumentException("track sheet must not be null");
        }

        Iterator<Row> it = shTrack.rowIterator();
        if (!it.hasNext()) {
            throw new IllegalArgumentException("Empty 'track' sheet");
        }

        Row header = it.next();
        Map<String, List<Integer>> col = headerIndexes(header);

        int cPos = requireExactlyOneColumn(col, "position [m]");
        int cBisKm = requireExactlyOneColumn(col, "bisKm");
        int cBisMeter = requireExactlyOneColumn(col, "bisMeter");

        List<TrackPoint> pts = new ArrayList<>();

        while (it.hasNext()) {
            Row r = it.next();
            if (r == null) continue;

            Cell p = r.getCell(cPos);
            Cell km = r.getCell(cBisKm);
            Cell m = r.getCell(cBisMeter);

            if (p == null && km == null && m == null) {
                continue;
            }

            double posM = requireNumericCell(p, "position [m]", r.getRowNum());
            int bisKm = (int) requireNumericCell(km, "bisKm", r.getRowNum());
            double bisMeter = requireNumericCell(m, "bisMeter", r.getRowNum());

            if (bisMeter < 0.0 || bisMeter >= 1000.0) {
                throw new IllegalArgumentException(
                        "Invalid bisMeter at row " + r.getRowNum() + ": " + bisMeter + " (expected 0 <= bisMeter < 1000)"
                );
            }

            pts.add(new TrackPoint(posM, bisKm, bisMeter));
        }

        validateTrackPoints(pts);
        return pts;
    }

    public static double toAbsolutePositionM(double runPosM, List<TrackPoint> trackPoints) {
        if (Double.isNaN(runPosM) || Double.isInfinite(runPosM)) {
            throw new IllegalArgumentException("runPosM must be finite, got: " + runPosM);
        }
        validateTrackPoints(trackPoints);

        TrackPoint first = trackPoints.get(0);
        TrackPoint last = trackPoints.get(trackPoints.size() - 1);

        if (runPosM < first.positionM || runPosM > last.positionM) {
            throw new IllegalArgumentException(
                    "run position outside track bounds: runPosM=" + runPosM +
                            ", trackRange=[" + first.positionM + "," + last.positionM + "]"
            );
        }

        for (int i = 0; i < trackPoints.size() - 1; i++) {
            TrackPoint a = trackPoints.get(i);
            TrackPoint b = trackPoints.get(i + 1);

            if (runPosM == a.positionM) {
                return a.absolutePositionM();
            }
            if (runPosM == b.positionM) {
                return b.absolutePositionM();
            }

            if (runPosM > a.positionM && runPosM < b.positionM) {
                double span = b.positionM - a.positionM;
                if (span <= 0.0) {
                    throw new IllegalArgumentException(
                            "Track points must be strictly increasing, found span=" + span +
                                    " between " + a.positionM + " and " + b.positionM
                    );
                }

                double t = (runPosM - a.positionM) / span;
                return a.absolutePositionM() + t * (b.absolutePositionM() - a.absolutePositionM());
            }
        }

        throw new IllegalStateException("Could not interpolate runPosM=" + runPosM);
    }

    public static void validateTrackPoints(List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.isEmpty()) {
            throw new IllegalArgumentException("trackPoints must not be empty");
        }
        if (trackPoints.size() < 2) {
            throw new IllegalArgumentException("trackPoints must contain at least 2 points");
        }

        double prev = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint tp = trackPoints.get(i);

            if (Double.isNaN(tp.positionM) || Double.isInfinite(tp.positionM)) {
                throw new IllegalArgumentException("Invalid track position at index " + i + ": " + tp.positionM);
            }
            if (tp.bisMeter < 0.0 || tp.bisMeter >= 1000.0) {
                throw new IllegalArgumentException(
                        "Invalid bisMeter at index " + i + ": " + tp.bisMeter + " (expected 0 <= bisMeter < 1000)"
                );
            }
            if (i > 0 && tp.positionM <= prev) {
                throw new IllegalArgumentException(
                        "track position must be strictly increasing; found " + prev + " then " + tp.positionM
                );
            }
            prev = tp.positionM;
        }
    }

    public static PowerProfiles emptyPowerProfiles() {
        return new PowerProfiles();
    }

    public static int parseHmsToSeconds(String s) {
        String[] p = s.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        int sec = (p.length == 3) ? Integer.parseInt(p[2]) : 0;
        return h * 3600 + m * 60 + sec;
    }

    public static Integer optHmsToSeconds(Config cfg, String key) {
        return cfg.hasPath(key) ? parseHmsToSeconds(cfg.getString(key)) : null;
    }

    public static String uniqId(String base, String signature, int ord) {
        if (signature != null && !signature.isBlank()) return base + "_" + signature + ord;
        return base + "_" + ord;
    }

    public static final class TrackPoint {
        public final double positionM;
        public final int bisKm;
        public final double bisMeter;

        public TrackPoint(double positionM, int bisKm, double bisMeter) {
            this.positionM = positionM;
            this.bisKm = bisKm;
            this.bisMeter = bisMeter;
        }

        public double absolutePositionM() {
            return bisKm * 1000.0 + bisMeter;
        }

        @Override
        public String toString() {
            return "TrackPoint{" +
                    "positionM=" + positionM +
                    ", bisKm=" + bisKm +
                    ", bisMeter=" + bisMeter +
                    '}';
        }
    }

    private static Map<String, List<Integer>> headerIndexes(Row headerRow) {
        Map<String, List<Integer>> idx = new LinkedHashMap<>();
        for (Cell c : headerRow) {
            if (c == null) continue;
            String s = c.toString();
            if (s == null) continue;
            String key = s.trim();
            idx.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getColumnIndex());
        }
        return idx;
    }

    private static int requireExactlyOneColumn(Map<String, List<Integer>> cols, String name) {
        List<Integer> hits = cols.get(name);
        if (hits == null || hits.isEmpty()) {
            throw new IllegalArgumentException("Missing required column: '" + name + "'");
        }
        if (hits.size() > 1) {
            throw new IllegalArgumentException("Ambiguous header: '" + name + "' appears " + hits.size() + " times");
        }
        return hits.get(0);
    }

    private static double requireNumericCell(Cell cell, String colName, int rowNum) {
        if (cell == null) {
            throw new IllegalArgumentException("Missing value for '" + colName + "' at row " + rowNum);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        String s = cell.toString().trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Blank value for '" + colName + "' at row " + rowNum);
        }
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Non-numeric value for '" + colName + "' at row " + rowNum + ": '" + s + "'"
            );
        }
    }
}