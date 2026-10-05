package org.supply.app;

import com.typesafe.config.Config;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.domain.RunSample;
import org.supply.solver.build.TrainPositionFactory;
import org.supply.solver.io.ResultMetadata;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.TrackTransformService;
import org.supply.track.LoadedTrackModel;
import org.supply.track.PathSample;
import org.supply.track.RouteView;
import org.supply.track.RwyCoordinate;
import org.supply.track.TrackConfigLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.supply.app.BaseRoutePositionMapper.*;
import static org.supply.app.GraphicalTimetableMarkerWriter.*;
import static org.supply.app.ReportWorkbookWriter.*;

public final class DcReporter {

    static final double MIN_TRAIN_VOLTAGE_V =
            Double.parseDouble(System.getProperty(
                    "dcsim.report.minTrainVoltageV", "600.0"));
    static final double MIN_POWER_DEFICIT_W =
            Double.parseDouble(System.getProperty(
                    "dcsim.report.minPowerDeficitW", "1000.0"));
    static final double MAX_EVENT_GAP_S =
            Double.parseDouble(System.getProperty(
                    "dcsim.report.maxEventGapS", "1.01"));

    public static void main(String[] args) throws Exception {
        DcStudyContext context =
                DcStudyContextLoader.load(args[0]);

        run(context);
    }

    public static void run(
            DcStudyContext context
    ) throws Exception {

        Path longTablePath =
                context.resultDirectory()
                        .resolve(context.studyId() + "_longtable.csv");

        Path outputPath =
                context.resultDirectory()
                        .resolve(context.studyId() + "_results_installation.xlsx");

        List<LongTableRow> rows =
                readLongTable(longTablePath);

        ResultMetadata metadata =
                extractMetadata(rows);

        BaseRoutePositionMapper baseRoutePositionMapper =
                loadBaseRoutePositionMapper(context);

        writeInstallationWorkbook(
                rows,
                metadata,
                outputPath
        );

        Path resultsTrain =
                context.resultDirectory()
                        .resolve(context.studyId() + "_results_train.xlsx");

        writeTrainWorkbook(
                rows,
                metadata,
                baseRoutePositionMapper,
                resultsTrain
        );

        if (baseRoutePositionMapper.isEnabled()) {
            Path graphicalTimetableInput =
                    context.resultDirectory().resolve(
                            context.studyId()
                                    + "_graphical_timetable.csv"
                    );

            writeGraphicalTimetableInput(
                    rows,
                    baseRoutePositionMapper,
                    graphicalTimetableInput
            );

            Path graphicalTimetableMarkers =
                    context.resultDirectory().resolve(
                            context.studyId()
                                    + "_graphical_timetable_markers.csv"
                    );
            writeGraphicalTimetableMarkers(
                    context,
                    baseRoutePositionMapper,
                    context.exportDirectory(),
                    graphicalTimetableMarkers
            );
        }

        Path resultsLine =
                context.resultDirectory()
                        .resolve(context.studyId() + "_results_line.xlsx");

        writeLineWorkbook(
                rows,
                metadata,
                resultsLine
        );

        Path resultsSystem =
                context.resultDirectory()
                        .resolve(context.studyId() + "_results_system.xlsx");

        writeSystemWorkbook(rows, metadata, resultsSystem);

        Path deviations =
                context.resultDirectory()
                        .resolve(context.studyId() + "_deviations.xlsx");

        writeDeviationWorkbook(rows, metadata, deviations);
    }

    static void writePlannedGraphicalTimetable(
            DcStudyContext context,
            Path exportDirectory,
            List<RunSample> samples,
            TrackTransformService transform,
            Path outputDirectory
    ) throws IOException {
        TrainPositionFactory positions = new TrainPositionFactory(transform);
        List<PlannedTimetablePoint> points = new ArrayList<>();
        for (RunSample sample : samples) {
            for (CalculationTrainPosition train
                    : positions.fromRunSamples(List.of(sample))) {
                points.add(new PlannedTimetablePoint(
                        sample.timeS(),
                        train.trainId(),
                        train.sectionId(),
                        train.positionM()
                ));
            }
        }
        writePlannedGraphicalTimetable(
                context, exportDirectory, points, outputDirectory
        );
    }

    static void writePlannedGraphicalTimetable(
            DcStudyContext context,
            Path exportDirectory,
            List<PlannedTimetablePoint> points,
            Path outputDirectory
    ) throws IOException {
        BaseRoutePositionMapper mapper =
                loadBaseRoutePositionMapper(context, exportDirectory);
        if (!mapper.isEnabled()) {
            return;
        }

        Files.createDirectories(outputDirectory);
        Path timetable = outputDirectory.resolve(
                context.studyId() + "_graphical_timetable.csv"
        );
        try (BufferedWriter writer = Files.newBufferedWriter(timetable)) {
            writer.write("time_s,train_id,base_route_position_m,p_delta_W");
            writer.newLine();
            for (PlannedTimetablePoint point : points) {
                writer.write(Double.toString(point.timeS()));
                writer.write(',');
                writer.write(csv(point.trainId()));
                writer.write(',');
                Double position = mapper.map(
                        point.sectionId(), point.positionM()
                );
                if (position != null) {
                    writer.write(Double.toString(position));
                }
                writer.write(",0.0");
                writer.newLine();
            }
        }

        writeGraphicalTimetableMarkers(
                context,
                mapper,
                exportDirectory,
                outputDirectory.resolve(
                        context.studyId()
                                + "_graphical_timetable_markers.csv"
                )
        );
    }

    record PlannedTimetablePoint(
            double timeS,
            String trainId,
            String sectionId,
            double positionM
    ) { }

    static void populateBaseRoutePositions(
            Map<String, Map<Double, TrainResult>> trains,
            BaseRoutePositionMapper mapper
    ) {
        for (Map<Double, TrainResult> byTime : trains.values()) {
            for (TrainResult train : byTime.values()) {
                train.baseRoutePositionM = mapper.map(
                        train.sectionId,
                        train.positionM
                );
            }
        }
    }

    static void writeGraphicalTimetableInput(
            List<LongTableRow> rows,
            BaseRoutePositionMapper mapper,
            Path outputPath
    ) throws IOException {
        Map<String, Map<Double, TrainResult>> trains =
                collectTrainResults(rows);

        populateBaseRoutePositions(trains, mapper);
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(
                    "time_s,train_id,base_route_position_m,p_delta_W"
            );
            writer.newLine();

            for (Map.Entry<String, Map<Double, TrainResult>> trainEntry
                    : trains.entrySet()) {
                String trainId = trainEntry.getKey();

                for (Map.Entry<Double, TrainResult> timeEntry
                        : trainEntry.getValue().entrySet()) {
                    TrainResult train = timeEntry.getValue();

                    if (train.pDeltaW == null) {
                        continue;
                    }

                    writer.write(Double.toString(timeEntry.getKey()));
                    writer.write(',');
                    writer.write(csv(trainId));
                    writer.write(',');
                    if (train.baseRoutePositionM != null) {
                        writer.write(Double.toString(
                                train.baseRoutePositionM
                        ));
                    }
                    writer.write(',');
                    writer.write(Double.toString(train.pDeltaW));
                    writer.newLine();
                }
            }
        }
    }

    static String csv(String value) {
        if (value.indexOf(',') < 0
                && value.indexOf('"') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }

        return '"' + value.replace("\"", "\"\"") + '"';
    }

    static List<LongTableRow> readLongTable(
            Path path
    ) throws IOException {

        List<LongTableRow> result =
                new ArrayList<>();

        try (BufferedReader reader =
                     Files.newBufferedReader(path)) {

            String header =
                    reader.readLine();

            if (header == null) {
                return result;
            }

            String line;

            while ((line = reader.readLine()) != null) {

                String[] fields =
                        line.split(",", -1);

                if (fields.length < 12) {
                    throw new IllegalArgumentException(
                            "Invalid longtable row: " + line
                    );
                }

                result.add(
                        new LongTableRow(
                                parseNullableDouble(fields[0]),
                                fields[1],
                                fields[2],
                                fields[3],
                                fields[4],
                                fields[5],
                                fields[6],
                                fields[7],
                                fields[8],
                                fields[9],
                                fields[11]
                        )
                );
            }
        }

        return result;
    }

    static ResultMetadata extractMetadata(
            List<LongTableRow> rows
    ) {
        String project = null;
        String scenario = null;
        String baseHash = null;
        String generatedAt = null;

        for (LongTableRow row : rows) {

            if (!row.project.isBlank()) {
                if (project != null && !project.equals(row.project)) {
                    throw new IllegalArgumentException(
                            "Multiple projects in longtable.csv"
                    );
                }
                project = row.project;
            }

            if (!row.scenario.isBlank()) {
                if (scenario != null && !scenario.equals(row.scenario)) {
                    throw new IllegalArgumentException(
                            "Multiple scenarios in longtable.csv"
                    );
                }
                scenario = row.scenario;
            }

            if (!row.baseHash.isBlank()) {
                if (baseHash != null && !baseHash.equals(row.baseHash)) {
                    throw new IllegalArgumentException(
                            "Multiple base hashes in longtable.csv"
                    );
                }
                baseHash = row.baseHash;
            }

            if (row.note.startsWith("generated_at=")) {
                String value =
                        row.note.substring("generated_at=".length());

                if (generatedAt != null && !generatedAt.equals(value)) {
                    throw new IllegalArgumentException(
                            "Multiple generation timestamps in longtable.csv"
                    );
                }

                generatedAt = value;
            }
        }

        if (project == null
                || scenario == null
                || baseHash == null
                || generatedAt == null) {

            throw new IllegalArgumentException(
                    "Missing provenance metadata in longtable.csv"
            );
        }

        return new ResultMetadata(
                project,
                scenario,
                baseHash,
                generatedAt
        );
    }

    static void setNumericCell(
            Row row,
            int column,
            Double value
    ) {
        if (value != null) {
            row.createCell(column)
                    .setCellValue(value);
        }
    }

    static Double parseDouble(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(value);
    }

    static Double parseNullableDouble(
            String value
    ) {
        return parseDouble(value);
    }

    static String safeSheetName(
            String name
    ) {
        String safe =
                name.replaceAll(
                        "[\\\\/?*\\[\\]:]",
                        "_"
                );

        if (safe.length() > 31) {
            return safe.substring(0, 31);
        }

        return safe;
    }

    record LongTableRow(
            Double timeS,
            String project,
            String scenario,
            String baseHash,
            String objectType,
            String objectId,
            String signal,
            String value,
            String unit,
            String stage,
            String note
    ) {
    }

    static final class InstallationResult {
        Double uV;
        Double iA;
        Double pW;
        String state;
        Double eSuppliedJ;
        Double eAbsorbedJ;
        Double eNetJ;
    }

    static final class TrainResult {
        String electricRouteId;
        Double electricRoutePositionM;
        String sectionId;
        String trackId;
        Double positionM;
        Double baseRoutePositionM;
        Double speedMps;
        Double uV;
        Double iA;
        Double pReqW;
        Double pW;
        Double pDeltaW;
        Double eConsumedJ;
        Double eRegeneratedJ;
        Double eNetJ;
    }

    static final class StationRange {
        double minimumM = Double.POSITIVE_INFINITY;
        double maximumM = Double.NEGATIVE_INFINITY;

        void include(double positionM) {
            minimumM = Math.min(minimumM, positionM);
            maximumM = Math.max(maximumM, positionM);
        }

        double centre() {
            return (minimumM + maximumM) / 2.0;
        }

        boolean isEmpty() {
            return minimumM == Double.POSITIVE_INFINITY;
        }
    }

    static final class LineResult {
        Double pLossesW;
        Double eLossesJ;
    }

    static final class SystemResult {
        Double pSubstationsW;
        Double pTrainsW;
        Double pFixedLoadsW;
        Double pLossesW;
        Double pBalanceW;
        Double eSubstationsSuppliedJ;
        Double eSubstationsAbsorbedJ;
        Double eSubstationsNetJ;
        Double eTrainsConsumedJ;
        Double eTrainsRegeneratedJ;
        Double eTrainsNetJ;
        Double eFixedLoadsConsumedJ;
        Double eLossesJ;
        Double eBalanceJ;
    }

    enum DeviationType {
        UNDER_VOLTAGE,
        POWER_DEFICIT
    }

    static final class RawDeviation {
        final DeviationType type;
        final String trainId;
        final double timeS;
        final String sectionId;
        final String trackId;
        final Double positionM;
        final Double routePositionM;
        final Double voltageV;
        final Double powerDeficitW;

        RawDeviation(
                DeviationType type,
                String trainId,
                double timeS,
                TrainResult train,
                Double voltageV,
                Double powerDeficitW
        ) {
            this.type = type;
            this.trainId = trainId;
            this.timeS = timeS;
            this.sectionId = train.sectionId;
            this.trackId = train.trackId;
            this.positionM = train.positionM;
            this.routePositionM = train.electricRoutePositionM;
            this.voltageV = voltageV;
            this.powerDeficitW = powerDeficitW;
        }
    }

    static final class DeviationPeriod {
        final DeviationType type;
        final String trainId;
        final String sectionId;
        final String trackId;
        final double startTimeS;
        double endTimeS;
        final Double startPositionM;
        Double endPositionM;
        final Double startRoutePositionM;
        Double endRoutePositionM;
        Double minVoltageV;
        Double maxPowerDeficitW;
        int sampleCount;

        DeviationPeriod(RawDeviation deviation) {
            this.type = deviation.type;
            this.trainId = deviation.trainId;
            this.sectionId = deviation.sectionId;
            this.trackId = deviation.trackId;
            this.startTimeS = deviation.timeS;
            this.endTimeS = deviation.timeS;
            this.startPositionM = deviation.positionM;
            this.endPositionM = deviation.positionM;
            this.startRoutePositionM = deviation.routePositionM;
            this.endRoutePositionM = deviation.routePositionM;
            this.minVoltageV = deviation.voltageV;
            this.maxPowerDeficitW = deviation.powerDeficitW;
            this.sampleCount = 1;
        }

        boolean canAppend(RawDeviation deviation) {
            return type == deviation.type
                    && trainId.equals(deviation.trainId)
                    && same(sectionId, deviation.sectionId)
                    && same(trackId, deviation.trackId)
                    && deviation.timeS >= endTimeS
                    && deviation.timeS - endTimeS <= MAX_EVENT_GAP_S;
        }

        void append(RawDeviation deviation) {
            endTimeS = deviation.timeS;
            endPositionM = deviation.positionM;
            endRoutePositionM = deviation.routePositionM;
            minVoltageV = minimum(minVoltageV, deviation.voltageV);
            maxPowerDeficitW = maximum(
                    maxPowerDeficitW, deviation.powerDeficitW);
            sampleCount++;
        }
    }

    static final class SectorSummary {
        final String sectionId;
        final String trackId;
        int periodCount;
        double totalDurationS;
        Double minVoltageV;
        Double maxPowerDeficitW;
        final List<String> trainIds = new ArrayList<>();

        SectorSummary(String sectionId, String trackId) {
            this.sectionId = sectionId;
            this.trackId = trackId;
        }

        void add(DeviationPeriod period) {
            periodCount++;
            totalDurationS += period.endTimeS - period.startTimeS;
            minVoltageV = minimum(minVoltageV, period.minVoltageV);
            maxPowerDeficitW = maximum(
                    maxPowerDeficitW, period.maxPowerDeficitW);
            if (!trainIds.contains(period.trainId)) {
                trainIds.add(period.trainId);
            }
        }

    }

    static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    static Double minimum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    static Double maximum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    static void setTextCell(
            Row row,
            int column,
            String value
    ) {
        if (value != null) {
            row.createCell(column)
                    .setCellValue(value);
        }
    }
}
