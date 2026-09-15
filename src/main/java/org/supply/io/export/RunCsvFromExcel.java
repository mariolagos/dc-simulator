package org.supply.io.export;

import org.supply.ScenarioHelpers;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.track.TrackInterpolationPoint;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
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
    private static final String K_SECTION = "section_id";
    private static final String K_TRACK = "track_id";
    private static final String K_ROUTE = "route_id";
    private static final String K_POS = "position_m";
    private static final String K_P = "p_req_W";

    private record RunPoint(double timeS, double positionM, double pReqW) {
    }

    private record RunExcelData(
            List<Map<String, String>> rows,
            Double relativeDepartureS
    ) {
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

    private static List<RunPoint> shiftRunPoints(
            List<RunPoint> source,
            double timeShiftS,
            double positionShiftM
    ) {
        List<RunPoint> shifted = new ArrayList<>(source.size());
        for (RunPoint point : source) {
            shifted.add(new RunPoint(
                    point.timeS() + timeShiftS,
                    point.positionM() + positionShiftM,
                    point.pReqW()
            ));
        }
        return shifted;
    }

    private static List<RunPoint> clipRunPoints(
            List<RunPoint> source,
            double simulationStartS,
            double simulationEndS
    ) {
        if (simulationEndS < simulationStartS) {
            throw new IllegalArgumentException(
                    "simulationEndS must not be before simulationStartS"
            );
        }

        if (source == null || source.isEmpty()) {
            return List.of();
        }

        double runStartS = source.get(0).timeS();
        double runEndS = source.get(source.size() - 1).timeS();

        double overlapStartS =
                Math.max(simulationStartS, runStartS);
        double overlapEndS =
                Math.min(simulationEndS, runEndS);

        if (overlapEndS < overlapStartS) {
            return List.of();
        }

        List<RunPoint> clipped = new ArrayList<>();

        clipped.add(new RunPoint(
                overlapStartS,
                interpolatePositionAt(source, overlapStartS),
                powerAt(source, overlapStartS)
        ));

        for (RunPoint point : source) {
            if (point.timeS() > overlapStartS
                    && point.timeS() < overlapEndS) {
                clipped.add(point);
            }
        }

        if (overlapEndS > overlapStartS) {
            clipped.add(new RunPoint(
                    overlapEndS,
                    interpolatePositionAt(source, overlapEndS),
                    powerAt(source, overlapEndS)
            ));
        }

        return clipped;
    }

    private static List<RunPoint> resampleRunPoints(
            List<RunPoint> src,
            double resolutionS,
            double anchor
    ) {
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

    private static List<Map<String, String>> fromRunPoints(
            List<RunPoint> pts,
            String trainId,
            String sectionId,
            String trackId,
            String routeId) {
        List<Map<String, String>> out = new ArrayList<>(pts.size());

        for (RunPoint p : pts) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(K_TIME, fmt(p.timeS()));
            row.put(K_TRAIN, trainId);
            row.put(K_SECTION, sectionId);
            row.put(K_TRACK, trackId);
            row.put(K_ROUTE, routeId);
            row.put(K_POS, fmt(p.positionM()));
            row.put(K_P, fmt(p.pReqW()));
            out.add(row);
        }

        return out;
    }

    private static String fmt(double v) {
        return Double.toString(v);
    }

    private static List<Map<String, String>> removeDuplicateTrainTimes(
            List<Map<String, String>> sortedRows
    ) {
        List<Map<String, String>> result = new ArrayList<>(sortedRows.size());

        for (Map<String, String> row : sortedRows) {
            if (!result.isEmpty()) {
                Map<String, String> previous = result.get(result.size() - 1);
                if (previous.get(K_TRAIN).equals(row.get(K_TRAIN))
                        && Math.abs(
                        Double.parseDouble(previous.get(K_TIME))
                                - Double.parseDouble(row.get(K_TIME))
                ) <= 1e-9) {
                    result.set(result.size() - 1, row);
                    continue;
                }
            }
            result.add(row);
        }

        return result;
    }

    private static List<Map<String, String>> readRunSheet(
            Sheet shRun,
            List<TrackInterpolationPoint> trackPoints,
            String trainId,
            String sectionId,
            String trackId,
            String routeId,
            int departureTime
    ) {
        Iterator<Row> it = shRun.rowIterator();
        if (!it.hasNext()) throw new IllegalArgumentException("Empty 'run' sheet");

        Row header = it.next();
        Map<String, Integer> col = headerIndex(header);

        int cTime = requireCol(col, "time [s]");
        int cPos = requireCol(col, "position [m]");
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

            double posM =
                    posCell.getNumericCellValue();

            double motKW = numericOrZero(r.getCell(cMot));
            double brkKW = numericOrZero(r.getCell(cBrk));

            // Contract: one power column, braking negative
            double pReqW = (motKW + brkKW) * 1000.0;

            Map<String, String> row = new LinkedHashMap<>();
            row.put(K_TIME, fmt(timeS));
            row.put(K_TRAIN, trainId);
            row.put(K_SECTION, sectionId);
            row.put(K_TRACK, trackId);
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

    private static Double readRelativeDepartureS(Workbook workbook) {
        Sheet timetable = workbook.getSheet("timetable");
        if (timetable == null) {
            return null;
        }

        Iterator<Row> rows = timetable.rowIterator();
        if (!rows.hasNext()) {
            return null;
        }

        Map<String, Integer> columns = headerIndex(rows.next());
        Integer timeColumn = columns.get("Time");
        Integer typeColumn = columns.get("Type");
        if (timeColumn == null || typeColumn == null) {
            return null;
        }

        while (rows.hasNext()) {
            Row row = rows.next();
            Cell typeCell = row.getCell(typeColumn);
            if (typeCell == null
                    || !"departure".equalsIgnoreCase(
                    typeCell.toString().trim()
            )) {
                continue;
            }

            Cell timeCell = row.getCell(timeColumn);
            if (timeCell == null) {
                throw new IllegalArgumentException(
                        "Departure row in 'timetable' has no 'Time' value"
                );
            }
            if (timeCell.getCellType() == CellType.NUMERIC) {
                return timeCell.getNumericCellValue() * 24.0 * 60.0 * 60.0;
            }

            String value = timeCell.toString().trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Departure row in 'timetable' has an empty 'Time' value"
                );
            }
            return parseHmsToSeconds(value);
        }

        return null;
    }

    private static double parseHmsToSeconds(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid timetable departure time: " + value
            );
        }

        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2].replace(',', '.'));

            if (hours < 0
                    || minutes < 0
                    || minutes >= 60
                    || seconds < 0.0
                    || seconds >= 60.0) {
                throw new IllegalArgumentException(
                        "Invalid timetable departure time: " + value
                );
            }

            return hours * 3600.0 + minutes * 60.0 + seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid timetable departure time: " + value,
                    e
            );
        }
    }

    private static RunExcelData readRunExcel(
            Path excelXlsx,
            String runExcelSheet,
            String trainId,
            String sectionId,
            String trackId,
            String routeId,
            int departureTime
    ) throws Exception {
        try (InputStream in = Files.newInputStream(excelXlsx);
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet shRun = wb.getSheet(runExcelSheet);
            Sheet shTrack = wb.getSheet("track");

            if (shRun == null) {
                throw new IllegalArgumentException(
                        "Workbook does not contain run sheet '"
                                + runExcelSheet
                                + "': "
                                + excelXlsx
                );
            }

            if (shTrack == null) {
                throw new IllegalArgumentException(
                        "Workbook does not contain sheet 'track': "
                                + excelXlsx
                );
            }

            List<TrackInterpolationPoint> trackPoints =
                    ScenarioHelpers.buildTrackInterpolationPoints(shTrack);

            return new RunExcelData(
                    readRunSheet(
                            shRun,
                            trackPoints,
                            trainId,
                            sectionId,
                            trackId,
                            routeId,
                            departureTime
                    ),
                    readRelativeDepartureS(wb)
            );
        }
    }

    public static List<Map<String, String>> readFullRunRows(
            Path excelXlsx,
            String runExcelSheet,
            String trainId,
            String sectionId,
            String trackId,
            String routeId,
            int departureTime
    ) throws Exception {
        return readRunExcel(
                excelXlsx,
                runExcelSheet,
                trainId,
                sectionId,
                trackId,
                routeId,
                departureTime
        ).rows();
    }

    public static void writeRunCsv(
            List<Path> excelXlsxs,
            List<String> runExcelSheets,
            List<String> trainIds,
            List<String> sectionIds,
            List<String> trackIds,
            List<String> routeIds,
            Path outRunCsv,
            List<Integer> departureTimes,
            int simulationStartSec,
            int simulationEndSec,
            double exportResolutionS
    ) throws Exception {
        writeRunCsv(
                excelXlsxs,
                runExcelSheets,
                trainIds,
                sectionIds,
                trackIds,
                routeIds,
                outRunCsv,
                departureTimes,
                excelXlsxs == null
                        ? null
                        : Collections.nCopies(excelXlsxs.size(), null),
                simulationStartSec,
                simulationEndSec,
                exportResolutionS
        );
    }

    public static void writeRunCsv(
            List<Path> excelXlsxs,
            List<String> runExcelSheets,
            List<String> trainIds,
            List<String> sectionIds,
            List<String> trackIds,
            List<String> routeIds,
            Path outRunCsv,
            List<Integer> departureTimes,
            List<Integer> relativeLegDepartureTimes,
            int simulationStartSec,
            int simulationEndSec,
            double exportResolutionS
    ) throws Exception {
        if (excelXlsxs == null
                || runExcelSheets == null
                || trainIds == null
                || sectionIds == null
                || trackIds == null
                || routeIds == null
                || departureTimes == null
                || relativeLegDepartureTimes == null) {
            throw new IllegalArgumentException(
                    "Run CSV input lists must not be null"
            );
        }

        int inputSize = excelXlsxs.size();
        if (runExcelSheets.size() != inputSize
                || trainIds.size() != inputSize
                || sectionIds.size() != inputSize
                || trackIds.size() != inputSize
                || routeIds.size() != inputSize
                || departureTimes.size() != inputSize
                || relativeLegDepartureTimes.size() != inputSize) {
            throw new IllegalArgumentException(
                    "Run CSV input lists must have the same size"
            );
        }

        if (exportResolutionS < 0.0) {
            throw new IllegalArgumentException("exportResolutionS must be >= 0");
        }

        if (simulationEndSec < simulationStartSec) {
            throw new IllegalArgumentException(
                    "simulationEndSec must not be before simulationStartSec"
            );
        }

        List<Map<String, String>> allRows = new ArrayList<>();
        Map<String, Double> previousLegEndTimes = new HashMap<>();
        Map<String, Double> previousLegEndPositions = new HashMap<>();

        for (int i = 0; i < excelXlsxs.size(); i++) {
            Path runExcel = excelXlsxs.get(i);
            String runExcelSheet = runExcelSheets.get(i);
            String trainId = trainIds.get(i);
            String sectionId = sectionIds.get(i);
            String trackId = trackIds.get(i);
            String routeId = routeIds.get(i);

            RunExcelData runExcelData =
                    readRunExcel(
                            runExcel,
                            runExcelSheet,
                            trainId,
                            sectionId,
                            trackId,
                            routeId,
                            0
                    );

            List<Map<String, String>> rows = runExcelData.rows();

            List<RunPoint> points =
                    toRunPoints(rows);

            double runExcelStartS = points.get(0).timeS();
            Integer configuredRelativeDepartureS =
                    relativeLegDepartureTimes.get(i);
            double relativeDepartureS =
                    configuredRelativeDepartureS != null
                            ? configuredRelativeDepartureS
                            : runExcelData.relativeDepartureS() != null
                            ? runExcelData.relativeDepartureS()
                            : runExcelStartS;
            double absoluteLegStartS =
                    departureTimes.get(i) + relativeDepartureS;

            Double previousLegEndS = previousLegEndTimes.get(trainId);
            if (previousLegEndS != null
                    && absoluteLegStartS < previousLegEndS - 1e-9) {
                throw new IllegalArgumentException(
                        "Leg for train "
                                + trainId
                                + " starts at "
                                + absoluteLegStartS
                                + " s, before previous leg ends at "
                                + previousLegEndS
                                + " s: "
                                + runExcel
                );
            }

            Double previousLegEndPositionM =
                    previousLegEndPositions.get(trainId);
            double positionShiftM =
                    previousLegEndPositionM == null
                            ? 0.0
                            : previousLegEndPositionM
                            - points.get(0).positionM();

            points = shiftRunPoints(
                    points,
                    absoluteLegStartS - runExcelStartS,
                    positionShiftM
            );

            RunPoint fullLegEnd = points.get(points.size() - 1);
            previousLegEndTimes.put(trainId, fullLegEnd.timeS());
            previousLegEndPositions.put(trainId, fullLegEnd.positionM());

            points = clipRunPoints(
                    points,
                    simulationStartSec,
                    simulationEndSec
            );

            if (points.isEmpty()) {
                continue;
            }

            if (exportResolutionS > 0.0) {
                points = resampleRunPoints(
                        points,
                        exportResolutionS,
                        simulationStartSec
                );
            }

            rows = fromRunPoints(
                    points,
                    trainId,
                    sectionId,
                    trackId,
                    routeId
            );

            allRows.addAll(rows);
        }

        allRows.sort(Comparator
                .comparing((Map<String, String> r) -> Double.parseDouble(r.get(K_TIME)))
                .thenComparing(r -> r.get(K_TRAIN)));

        allRows = removeDuplicateTrainTimes(allRows);

        org.supply.io.export.CsvSchema schema = org.supply.io.export.CsvSchema.runSchema();

        org.supply.io.export.RunCsvFileWriter writer =
                new org.supply.io.export.RunCsvFileWriter(schema);

        writer.write(outRunCsv, allRows);

        Path p = outRunCsv.toAbsolutePath();
    }
}
