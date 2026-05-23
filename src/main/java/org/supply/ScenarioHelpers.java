package org.supply;

import com.typesafe.config.Config;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.supply.track.TrackInterpolationPoint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small supply-owned helper methods extracted from the legacy ScenarioHelpers.
 *
 * This class intentionally contains only generic config/time and track-sheet
 * helpers. Runtime/power-profile helpers are not copied, to avoid pulling the
 * legacy dcsim runtime into supply.
 */
public final class ScenarioHelpers {

    private ScenarioHelpers() {
    }

    public static List<TrackInterpolationPoint> buildTrackInterpolationPoints(Sheet shTrack) {
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

        List<TrackInterpolationPoint> pts = new ArrayList<>();

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

            pts.add(new TrackInterpolationPoint(posM, bisKm, bisMeter));
        }

        validateTrackInterpolationPoints(pts);
        return pts;
    }

    public static double toAbsolutePositionM(double runPosM, List<TrackInterpolationPoint> TrackInterpolationPoints) {
        if (Double.isNaN(runPosM) || Double.isInfinite(runPosM)) {
            throw new IllegalArgumentException("runPosM must be finite, got: " + runPosM);
        }
        validateTrackInterpolationPoints(TrackInterpolationPoints);

        TrackInterpolationPoint first = TrackInterpolationPoints.get(0);
        TrackInterpolationPoint last = TrackInterpolationPoints.get(TrackInterpolationPoints.size() - 1);

        if (runPosM < first.positionM || runPosM > last.positionM) {
            throw new IllegalArgumentException(
                    "run position outside track bounds: runPosM=" + runPosM +
                            ", trackRange=[" + first.positionM + "," + last.positionM + "]"
            );
        }

        for (int i = 0; i < TrackInterpolationPoints.size() - 1; i++) {
            TrackInterpolationPoint a = TrackInterpolationPoints.get(i);
            TrackInterpolationPoint b = TrackInterpolationPoints.get(i + 1);

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

    public static void validateTrackInterpolationPoints(List<TrackInterpolationPoint> TrackInterpolationPoints) {
        if (TrackInterpolationPoints == null || TrackInterpolationPoints.isEmpty()) {
            throw new IllegalArgumentException("TrackInterpolationPoints must not be empty");
        }
        if (TrackInterpolationPoints.size() < 2) {
            throw new IllegalArgumentException("TrackInterpolationPoints must contain at least 2 points");
        }

        double prev = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < TrackInterpolationPoints.size(); i++) {
            TrackInterpolationPoint tp = TrackInterpolationPoints.get(i);

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
