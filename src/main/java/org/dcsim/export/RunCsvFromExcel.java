package org.dcsim.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.ScenarioHelpers;
import org.dcsim.validation.CsvSchema;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunCsvFromExcel {

    static final boolean DEBUG_VERBOSITY = false;

    private RunCsvFromExcel() {
    }

    // Required output keys for RunCsvWriter schema (headers):
    private static final String K_TIME = "time_s";
    private static final String K_TRAIN = "train_id";
    private static final String K_TRACK = "track";
    private static final String K_POS = "position_m";
    private static final String K_P = "p_req_W";

    private record RunPoint(double timeS, double positionM, double pReqW) {
    }

    private static List<RunPoint> toRunPoints(List<Map<String, String>> rows) {
        List<RunPoint> pts = new ArrayList<>(rows.size());
        for (Map<String, String> r : rows) {
            pts.add(new RunPoint(
                    Double.parseDouble(r.get(K_TIME)),
                    Double.parseDouble(r.get(K_POS)),
                    Double.parseDouble(r.get(K_P))
            ));
        }
        return pts;
    }

    private static List<RunPoint> resampleRunPoints(List<RunPoint> src, double resolutionS) {
        if (src == null || src.isEmpty()) {
            throw new IllegalArgumentException("src must not be empty");
        }
        if (resolutionS <= 0.0) {
            throw new IllegalArgumentException("resolutionS must be > 0");
        }
        if (src.size() == 1) {
            return src;
        }

        List<RunPoint> out = new ArrayList<>();

        double tStartRaw = src.get(0).timeS();
        double tEnd = src.get(src.size() - 1).timeS();

        double anchor = 0.0;
        double tStart = Math.ceil((tStartRaw - anchor) / resolutionS) * resolutionS + anchor;

        for (double t0 = tStart; t0 <= tEnd + 1e-9; t0 += resolutionS) {
            double t1 = Math.min(t0 + resolutionS, tEnd);

            double pos = interpolatePositionAt(src, t0);
            double pAvg = averagePowerOver(src, t0, t1);

            out.add(new RunPoint(t0, pos, pAvg));

            if (t1 >= tEnd) {
                break;
            }
        }

        return out;
    }


    private static double interpolatePositionAt(List<RunPoint> src, double t) {
        if (t <= src.get(0).timeS()) {
            return src.get(0).positionM();
        }
        if (t >= src.get(src.size() - 1).timeS()) {
            return src.get(src.size() - 1).positionM();
        }

        for (int i = 0; i < src.size() - 1; i++) {
            RunPoint a = src.get(i);
            RunPoint b = src.get(i + 1);

            if (t == a.timeS()) return a.positionM();
            if (t == b.timeS()) return b.positionM();

            if (t > a.timeS() && t < b.timeS()) {
                double span = b.timeS() - a.timeS();
                if (span <= 0.0) {
                    throw new IllegalArgumentException("Non-increasing time series between " + a.timeS() + " and " + b.timeS());
                }
                double alpha = (t - a.timeS()) / span;
                return a.positionM() + alpha * (b.positionM() - a.positionM());
            }
        }

        throw new IllegalStateException("Could not interpolate position at t=" + t);
    }

    private static double averagePowerOver(List<RunPoint> src, double t0, double t1) {
        if (t1 < t0) {
            throw new IllegalArgumentException("Invalid interval: [" + t0 + "," + t1 + "]");
        }
        if (Math.abs(t1 - t0) < 1e-12) {
            return powerAt(src, t0);
        }

        double energy = 0.0;

        for (int i = 0; i < src.size() - 1; i++) {
            RunPoint a = src.get(i);
            RunPoint b = src.get(i + 1);

            double segStart = Math.max(t0, a.timeS());
            double segEnd = Math.min(t1, b.timeS());

            if (segEnd > segStart) {
                // Piecewise-constant over [a.timeS, b.timeS)
                energy += a.pReqW() * (segEnd - segStart);
            }
        }

        // Tail case: if interval touches the last sample exactly
        if (t0 >= src.get(src.size() - 1).timeS()) {
            return src.get(src.size() - 1).pReqW();
        }

        return energy / (t1 - t0);
    }

    private static double powerAt(List<RunPoint> src, double t) {
        if (t <= src.get(0).timeS()) {
            return src.get(0).pReqW();
        }
        for (int i = 0; i < src.size() - 1; i++) {
            RunPoint a = src.get(i);
            RunPoint b = src.get(i + 1);
            if (t >= a.timeS() && t < b.timeS()) {
                return a.pReqW();
            }
        }
        return src.get(src.size() - 1).pReqW();
    }

    private static List<Map<String, String>> fromRunPoints(List<RunPoint> pts, String trainId) {
        List<Map<String, String>> out = new ArrayList<>(pts.size());

        for (RunPoint p : pts) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(K_TIME, fmt(p.timeS()));
            row.put(K_TRAIN, trainId);
            row.put(K_TRACK, "1");
            row.put(K_POS, fmt(p.positionM()));
            row.put(K_P, fmt(p.pReqW()));
            out.add(row);
        }

        return out;
    }

    private static String fmt(double v) {
        return Double.toString(v);
    }

    private static List<Map<String, String>> readRunSheet(
            Sheet shRun,
            List<ScenarioHelpers.TrackPoint> trackPoints,
            String trainId,
            int departureTime
    ) {
        Iterator<Row> it = shRun.rowIterator();
        if (!it.hasNext()) throw new IllegalArgumentException("Empty 'run' sheet");

        Row header = it.next();
        Map<String, Integer> col = headerIndex(header);

        int cTime = requireCol(col, "time [s]");
        int cPos = requireCol(col, "bisPosition [km,m]");
        int cMot = requireCol(col, "primaryMotoringPower [kW]");
        int cBrk = requireCol(col, "primaryMotorBrakingPower [kW]");

        List<Map<String, String>> out = new ArrayList<>();
        while (it.hasNext()) {
            Row r = it.next();
            if (r == null) continue;

            Cell timeCell = r.getCell(cTime);
            Cell posCell = r.getCell(cPos);
            if (timeCell == null || posCell == null) continue;
            if (timeCell.getCellType() != CellType.NUMERIC || posCell.getCellType() != CellType.NUMERIC) continue;

            double timeS = timeCell.getNumericCellValue() + departureTime;

            double runPosM = posCell.getNumericCellValue() * 1000;
            double posM = ScenarioHelpers.toAbsolutePositionM(runPosM, trackPoints);
            double motKW = numericOrZero(r.getCell(cMot));
            double brkKW = numericOrZero(r.getCell(cBrk));

            // Contract: one power column, braking negative
            double pReqW = (motKW + brkKW) * 1000.0;

            Map<String, String> row = new LinkedHashMap<>();
            row.put(K_TIME, fmt(timeS));
            row.put(K_TRAIN, trainId);
            row.put(K_TRACK, "1");
            row.put(K_POS, fmt(posM));
            row.put(K_P, fmt(pReqW));
            out.add(row);
        }

        if (out.isEmpty()) throw new IllegalArgumentException("No numeric rows parsed in 'run' sheet");
        return out;
    }

    private static Map<String, Integer> headerIndex(Row headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (Cell c : headerRow) {
            if (c == null) continue;
            String s = c.toString();
            if (s != null) idx.put(s.trim(), c.getColumnIndex());
        }
        return idx;
    }

    private static int requireCol(Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null) {
            throw new IllegalArgumentException("Missing required column: '" + name + "'");
        }
        return i;
    }

    private static double numericOrZero(Cell c) {
        if (c == null) return 0.0;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        String s = c.toString().trim();
        if (s.isEmpty()) return 0.0;
        return Double.parseDouble(s.replace(',', '.'));
    }

    public static List<Map<String, String>> readFullRunRows(Path excelXlsx, String trainId, int departureTime) throws Exception {
        try (InputStream in = Files.newInputStream(excelXlsx);
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet shRun = wb.getSheet("run");
            Sheet shTrack = wb.getSheet("track");
            if (shRun == null || shTrack == null) {
                throw new IllegalArgumentException("Workbook must contain sheets named 'run' and 'track': " + excelXlsx);
            }

            List<ScenarioHelpers.TrackPoint> trackPoints = ScenarioHelpers.buildTrackPoints(shTrack);
            return readRunSheet(shRun, trackPoints, trainId, departureTime);
        }
    }

    public static void writeRunCsv(
            List<Path> excelXlsxs,
            List<String> trainIds,
            Path outRunCsv,
            List<Integer> departureTimes,
            double exportResolutionS
    ) throws Exception {
        if (excelXlsxs == null || trainIds == null) {
            throw new IllegalArgumentException("excelXlsxs and trainIds must not be null");
        }
        if (excelXlsxs.size() != trainIds.size()) {
            throw new IllegalArgumentException("excelXlsxs and trainIds must have the same size");
        }
        if (departureTimes == null) {
            throw new IllegalArgumentException("departureTimes must not be null");
        }
        if (excelXlsxs.size() != departureTimes.size()) {
            throw new IllegalArgumentException("excelXlsxs and departureTimes must have the same size");
        }
        if (exportResolutionS < 0.0) {
            throw new IllegalArgumentException("exportResolutionS must be >= 0");
        }

        List<Map<String, String>> allRows = new ArrayList<>();

        if (DEBUG_VERBOSITY) {
            System.out.println("RUNCSV about to resolve exportResolution_s");
            System.out.println("RUNCSV about to write: trains=" + trainIds.size() + " files=" + excelXlsxs.size());
            System.out.println("RUNCSV departures=" + departureTimes);
            System.out.println("CWD=" + Path.of("").toAbsolutePath());
        }

        for (int i = 0; i < excelXlsxs.size(); i++) {
            Path runExcel = excelXlsxs.get(i);
            String trainId = trainIds.get(i);

            List<Map<String, String>> rows = readFullRunRows(runExcel, trainId, departureTimes.get(i));

            if (exportResolutionS > 0.0) {
                List<RunPoint> pts = toRunPoints(rows);
                pts = resampleRunPoints(pts, exportResolutionS);
                rows = fromRunPoints(pts, trainId);
            }

            allRows.addAll(rows);
        }

        allRows.sort(Comparator
                .comparing((Map<String, String> r) -> Double.parseDouble(r.get(K_TIME)))
                .thenComparing(r -> r.get(K_TRAIN)));

        CsvSchema schema = CsvSchema.runSchema();
        RunCsvWriter writer = new RunCsvWriter(schema);
        writer.write(outRunCsv, allRows);

        Path p = outRunCsv.toAbsolutePath();
        if (DEBUG_VERBOSITY) {
            System.out.println("RUNCSV total rows=" + allRows.size());
            System.out.println("RUNCSV exists after write=" + Files.exists(p));
            System.out.println("RUNCSV size after write=" + (Files.exists(p) ? Files.size(p) : -1));
            System.out.println("RUNCSV modified after write=" + (Files.exists(p) ? Files.getLastModifiedTime(p) : "n/a"));
            System.out.println("RUNCSV absolute path=" + p);
        }
    }
}
