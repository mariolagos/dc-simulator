package org.supply.app;

import com.typesafe.config.Config;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.solver.io.ResultMetadata;

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

public final class DcReporter {

    private static final double MIN_TRAIN_VOLTAGE_V =
            Double.parseDouble(System.getProperty(
                    "dcsim.report.minTrainVoltageV", "600.0"));
    private static final double MIN_POWER_DEFICIT_W =
            Double.parseDouble(System.getProperty(
                    "dcsim.report.minPowerDeficitW", "1000.0"));
    private static final double MAX_EVENT_GAP_S =
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

    private static void writeTrainWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata,
            BaseRoutePositionMapper baseRoutePositionMapper,
            Path outputPath
    ) throws IOException {

        Map<String, Map<Double, TrainResult>> results =
                collectTrainResults(rows);

        populateBaseRoutePositions(results, baseRoutePositionMapper);

        try (Workbook workbook = new XSSFWorkbook()) {

            writeMetadataSheet(workbook, metadata);

            for (Map.Entry<String, Map<Double, TrainResult>> trainEntry
                    : results.entrySet()) {

                String trainId =
                        trainEntry.getKey();

                Sheet sheet =
                        workbook.createSheet(
                                safeSheetName(trainId)
                        );

                writeTrainSheet(
                        sheet,
                        trainId,
                        trainEntry.getValue()
                );
            }

            Files.createDirectories(outputPath.getParent());

            try (OutputStream out =
                         Files.newOutputStream(outputPath)) {

                workbook.write(out);
            }
        }
    }

    private static Map<String, Map<Double, TrainResult>>
    collectTrainResults(
            List<LongTableRow> rows
    ) {
        Map<String, Map<Double, TrainResult>> result =
                new LinkedHashMap<>();

        for (LongTableRow row : rows) {

            if (!"TRAIN".equals(row.objectType)) {
                continue;
            }

            if (row.timeS == null) {
                continue;
            }

            Map<Double, TrainResult> byTime =
                    result.computeIfAbsent(
                            row.objectId,
                            ignored -> new LinkedHashMap<>()
                    );

            TrainResult trainResult =
                    byTime.computeIfAbsent(
                            row.timeS,
                            ignored -> new TrainResult()
                    );

            switch (row.signal) {

                case "electric_route_id" ->
                        trainResult.electricRouteId =
                                row.value;

                case "electric_route_position_m" ->
                        trainResult.electricRoutePositionM =
                                parseDouble(row.value);

                case "section_id" ->
                        trainResult.sectionId =
                                row.value;

                case "track_id" ->
                        trainResult.trackId =
                                row.value;

                case "position_m" ->
                        trainResult.positionM =
                                parseDouble(row.value);

                case "u_V" ->
                        trainResult.uV =
                                parseDouble(row.value);

                case "i_A" ->
                        trainResult.iA =
                                parseDouble(row.value);

                case "p_req_W" ->
                        trainResult.pReqW =
                                parseDouble(row.value);

                case "p_W" ->
                        trainResult.pW =
                                parseDouble(row.value);

                case "p_delta_W" ->
                        trainResult.pDeltaW =
                                parseDouble(row.value);

                case "e_consumed_J" ->
                        trainResult.eConsumedJ = parseDouble(row.value);

                case "e_regenerated_J" ->
                        trainResult.eRegeneratedJ = parseDouble(row.value);

                case "e_net_J" ->
                        trainResult.eNetJ = parseDouble(row.value);

                default -> {
                    // Ignore other signals.
                }
            }
        }

        return result;
    }

    private static void populateBaseRoutePositions(
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

    private static void writeGraphicalTimetableInput(
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

    private static String csv(String value) {
        if (value.indexOf(',') < 0
                && value.indexOf('"') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }

        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static void writeGraphicalTimetableMarkers(
            DcStudyContext context,
            BaseRoutePositionMapper mapper,
            Path outputPath
    ) throws IOException {
        Path trackStationsPath = context.exportDirectory()
                .resolve("track_stations.csv");
        if (!Files.exists(trackStationsPath)) {
            throw new IllegalArgumentException(
                    "Track-station export not found: " + trackStationsPath
            );
        }

        Map<String, StationRange> stations = new LinkedHashMap<>();
        try (BufferedReader reader =
                     Files.newBufferedReader(trackStationsPath)) {
            String header = reader.readLine();
            if (!"name,position_rwy,model_position_m".equals(header)) {
                throw new IllegalArgumentException(
                        "Unexpected track_stations.csv header in "
                                + trackStationsPath + ": " + header
                );
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = csvFields(line, lineNumber);
                if (fields.size() != 3) {
                    throw new IllegalArgumentException(
                            "Expected three fields at " + trackStationsPath
                                    + ":" + lineNumber
                    );
                }
                String name = fields.get(0).trim();
                String railwayPosition = fields.get(1).trim();
                int separator = railwayPosition.indexOf(' ');
                if (name.isEmpty() || separator <= 0) {
                    throw new IllegalArgumentException(
                            "Invalid station row at " + trackStationsPath
                                    + ":" + lineNumber
                    );
                }
                String sectionId =
                        railwayPosition.substring(0, separator);
                double modelPositionM =
                        parseDouble(fields.get(2).trim());
                Double baseRoutePositionM =
                        mapper.map(sectionId, modelPositionM);
                if (baseRoutePositionM != null) {
                    stations.computeIfAbsent(
                            name, ignored -> new StationRange()
                    ).include(baseRoutePositionM);
                }
            }
        }

        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("name,base_route_position_m,kind");
            writer.newLine();
            for (Map.Entry<String, StationRange> station
                    : stations.entrySet()) {
                writeMarker(writer, station.getKey(),
                        station.getValue().centre(), "STATION");
            }
            writeSupplyPointMarkers(writer, context, mapper);
            writeSectionBoundaryMarkers(writer, mapper);
        }
    }

    private static void writeSupplyPointMarkers(
            BufferedWriter writer,
            DcStudyContext context,
            BaseRoutePositionMapper mapper
    ) throws IOException {
        Config scenario = context.scenario();
        String gridPath = "dcsim.grid";
        if (!scenario.hasPath(gridPath + ".nodes")
                || !scenario.hasPath(gridPath + ".power_installations")) {
            return;
        }

        Map<String, String> nodeRailwayPositions = new LinkedHashMap<>();
        for (Config node : scenario.getConfigList(gridPath + ".nodes")) {
            String railwayPosition = node.getString("position_rwy").trim();
            nodeRailwayPositions.put(
                    node.getString("node_id"), railwayPosition
            );
        }

        Map<String, Set<String>> connectionNodes = new LinkedHashMap<>();
        if (scenario.hasPath(gridPath + ".installation_connections")) {
            for (Config connection : scenario.getConfigList(
                    gridPath + ".installation_connections")) {
                connectionNodes.computeIfAbsent(
                        connection.getString("installation_id"),
                        ignored -> new HashSet<>()
                ).add(connection.getString("node_id"));
            }
        }

        for (Config installation : scenario.getConfigList(
                gridPath + ".power_installations")) {
            if (!"SUBSTATION".equals(
                    installation.getString("installation_category"))) {
                continue;
            }
            if (installation.hasPath("enabled")
                    && !installation.getBoolean("enabled")) {
                continue;
            }

            String installationId =
                    installation.getString("installation_id");
            Set<String> installationNodes = new HashSet<>();
            Set<String> connected = connectionNodes.get(installationId);
            if (connected != null) {
                installationNodes.addAll(connected);
            }
            if (installation.hasPath("terminals")) {
                for (Object value : installation.getConfig("terminals")
                        .root().unwrapped().values()) {
                    installationNodes.add(String.valueOf(value));
                }
            }

            StationRange positions = new StationRange();
            for (String nodeId : installationNodes) {
                Double basePositionM = mapper.mapRailwayPosition(
                        nodeRailwayPositions.get(nodeId)
                );
                if (basePositionM != null) {
                    positions.include(basePositionM);
                }
            }
            if (!positions.isEmpty()) {
                writeMarker(writer, installationId,
                        positions.centre(), "SUPPLY_POINT");
            }
        }
    }

    private static void writeSectionBoundaryMarkers(
            BufferedWriter writer,
            BaseRoutePositionMapper mapper
    ) throws IOException {
        String previousSection = null;
        for (Map.Entry<String, Double> section
                : mapper.sectionOffsetsM.entrySet()) {
            if (previousSection != null) {
                writeMarker(writer,
                        previousSection + "/" + section.getKey(),
                        section.getValue(), "SECTION_BOUNDARY");
            }
            previousSection = section.getKey();
        }
    }

    private static void writeMarker(
            BufferedWriter writer,
            String name,
            double positionM,
            String kind
    ) throws IOException {
        writer.write(csv(name));
        writer.write(',');
        writer.write(Double.toString(positionM));
        writer.write(',');
        writer.write(kind);
        writer.newLine();
    }

    private static List<String> csvFields(
            String line, int lineNumber
    ) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(
                    "Unclosed quoted field at CSV line " + lineNumber
            );
        }
        fields.add(field.toString());
        return fields;
    }

    private static void writeTrainSheet(
            Sheet sheet,
            String trainId,
            Map<Double, TrainResult> results
    ) {
        Row header = sheet.createRow(0);

        header.createCell(0)
                .setCellValue(trainId + ".time_s");
        header.createCell(1)
                .setCellValue(trainId + ".electric_route_id");
        header.createCell(2)
                .setCellValue(trainId + ".electric_route_position_m");
        header.createCell(3)
                .setCellValue(trainId + ".section_id");
        header.createCell(4)
                .setCellValue(trainId + ".track_id");
        header.createCell(5)
                .setCellValue(trainId + ".position_m");
        header.createCell(6)
                .setCellValue(trainId + ".base_route_position_m");
        header.createCell(7)
                .setCellValue(trainId + ".u_V");
        header.createCell(8)
                .setCellValue(trainId + ".i_A");
        header.createCell(9)
                .setCellValue(trainId + ".p_req_W");
        header.createCell(10)
                .setCellValue(trainId + ".p_W");
        header.createCell(11)
                .setCellValue(trainId + ".p_delta_W");
        header.createCell(12)
                .setCellValue(trainId + ".e_consumed_J");
        header.createCell(13)
                .setCellValue(trainId + ".e_regenerated_J");
        header.createCell(14)
                .setCellValue(trainId + ".e_net_J");

        int rowIndex = 1;

        for (Map.Entry<Double, TrainResult> entry
                : results.entrySet()) {

            Row row = sheet.createRow(rowIndex++);

            row.createCell(0)
                    .setCellValue(entry.getKey());

            TrainResult result = entry.getValue();

            setTextCell(row, 1, result.electricRouteId);
            setNumericCell(row, 2, result.electricRoutePositionM);
            setTextCell(row, 3, result.sectionId);
            setTextCell(row, 4, result.trackId);
            setNumericCell(row, 5, result.positionM);
            setNumericCell(row, 6, result.baseRoutePositionM);
            setNumericCell(row, 7, result.uV);
            setNumericCell(row, 8, result.iA);
            setNumericCell(row, 9, result.pReqW);
            setNumericCell(row, 10, result.pW);
            setNumericCell(row, 11, result.pDeltaW);
            setNumericCell(row, 12, result.eConsumedJ);
            setNumericCell(row, 13, result.eRegeneratedJ);
            setNumericCell(row, 14, result.eNetJ);
        }

        sheet.createFreezePane(0, 1);

        for (int column = 0; column < 15; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static BaseRoutePositionMapper loadBaseRoutePositionMapper(
            DcStudyContext context
    ) throws IOException {
        String configPath = "report.graphical-timetable";
        Config scenario = context.scenario();

        if (!scenario.hasPath(configPath)) {
            return BaseRoutePositionMapper.disabled();
        }

        Config graphicalTimetable = scenario.getConfig(configPath);
        if (graphicalTimetable.hasPath("enabled")
                && !graphicalTimetable.getBoolean("enabled")) {
            return BaseRoutePositionMapper.disabled();
        }

        if (!graphicalTimetable.hasPath("base-route.sections")) {
            throw new IllegalArgumentException(
                    "Missing configuration: " + configPath
                            + ".base-route.sections"
            );
        }

        List<String> sections =
                graphicalTimetable.getStringList("base-route.sections");

        Path trackSegments =
                context.exportDirectory().resolve("track_segments.csv");

        return BaseRoutePositionMapper.load(trackSegments, sections);
    }

    private static void writeDeviationWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata,
            Path outputPath
    ) throws IOException {
        Map<String, Map<Double, TrainResult>> trains =
                collectTrainResults(rows);
        List<DeviationPeriod> periods =
                collectDeviationPeriods(trains);
        Map<String, SectorSummary> sectors =
                summarizeSectors(periods);

        try (Workbook workbook = new XSSFWorkbook()) {
            writeMetadataSheet(workbook, metadata);
            writeThresholdSheet(workbook);
            writeDeviationPeriodsSheet(
                    workbook.createSheet("Deviation periods"), periods);
            writeSectorSummarySheet(
                    workbook.createSheet("Weak sectors"), sectors);

            Files.createDirectories(outputPath.getParent());
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
    }

    private static List<DeviationPeriod> collectDeviationPeriods(
            Map<String, Map<Double, TrainResult>> trains
    ) {
        List<RawDeviation> deviations = new ArrayList<>();

        for (Map.Entry<String, Map<Double, TrainResult>> trainEntry
                : trains.entrySet()) {
            String trainId = trainEntry.getKey();
            for (Map.Entry<Double, TrainResult> timeEntry
                    : trainEntry.getValue().entrySet()) {
                double timeS = timeEntry.getKey();
                TrainResult train = timeEntry.getValue();

                if (train.uV != null && train.uV < MIN_TRAIN_VOLTAGE_V) {
                    deviations.add(new RawDeviation(
                            DeviationType.UNDER_VOLTAGE,
                            trainId,
                            timeS,
                            train,
                            train.uV,
                            null
                    ));
                }

                if (train.pDeltaW != null
                        && train.pDeltaW > MIN_POWER_DEFICIT_W) {
                    deviations.add(new RawDeviation(
                            DeviationType.POWER_DEFICIT,
                            trainId,
                            timeS,
                            train,
                            train.uV,
                            train.pDeltaW
                    ));
                }
            }
        }

        deviations.sort(Comparator
                .comparing((RawDeviation d) -> d.trainId)
                .thenComparing(d -> d.type)
                .thenComparingDouble(d -> d.timeS));

        List<DeviationPeriod> periods = new ArrayList<>();
        DeviationPeriod current = null;
        for (RawDeviation deviation : deviations) {
            if (current == null || !current.canAppend(deviation)) {
                current = new DeviationPeriod(deviation);
                periods.add(current);
            } else {
                current.append(deviation);
            }
        }

        periods.sort(Comparator
                .comparingDouble((DeviationPeriod p) -> p.startTimeS)
                .thenComparing(p -> p.trainId)
                .thenComparing(p -> p.type));
        return periods;
    }

    private static Map<String, SectorSummary> summarizeSectors(
            List<DeviationPeriod> periods
    ) {
        Map<String, SectorSummary> result = new LinkedHashMap<>();
        for (DeviationPeriod period : periods) {
            String section = valueOrUnknown(period.sectionId);
            String track = valueOrUnknown(period.trackId);
            String key = section + " / " + track;
            result.computeIfAbsent(
                    key,
                    ignored -> new SectorSummary(section, track)
            ).add(period);
        }
        return result;
    }

    private static void writeThresholdSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Thresholds");
        writeThresholdRow(sheet, 0, "min_train_voltage_V",
                MIN_TRAIN_VOLTAGE_V,
                "UNDER_VOLTAGE when u_V is below this value");
        writeThresholdRow(sheet, 1, "min_power_deficit_W",
                MIN_POWER_DEFICIT_W,
                "POWER_DEFICIT when p_delta_W is above this value");
        writeThresholdRow(sheet, 2, "max_event_gap_s",
                MAX_EVENT_GAP_S,
                "Largest time gap merged into one deviation period");
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
    }

    private static void writeThresholdRow(
            Sheet sheet,
            int rowIndex,
            String name,
            double value,
            String description
    ) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(value);
        row.createCell(2).setCellValue(description);
    }

    private static void writeDeviationPeriodsSheet(
            Sheet sheet,
            List<DeviationPeriod> periods
    ) {
        String[] headers = {
                "type", "train_id", "section_id", "track_id",
                "start_time_s", "end_time_s", "duration_s",
                "start_position_m", "end_position_m",
                "start_route_position_m", "end_route_position_m",
                "min_voltage_V", "max_power_deficit_W", "sample_count"
        };
        writeHeader(sheet, headers);

        int rowIndex = 1;
        for (DeviationPeriod period : periods) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(period.type.name());
            row.createCell(1).setCellValue(period.trainId);
            setTextCell(row, 2, period.sectionId);
            setTextCell(row, 3, period.trackId);
            row.createCell(4).setCellValue(period.startTimeS);
            row.createCell(5).setCellValue(period.endTimeS);
            row.createCell(6).setCellValue(period.endTimeS - period.startTimeS);
            setNumericCell(row, 7, period.startPositionM);
            setNumericCell(row, 8, period.endPositionM);
            setNumericCell(row, 9, period.startRoutePositionM);
            setNumericCell(row, 10, period.endRoutePositionM);
            setNumericCell(row, 11, period.minVoltageV);
            setNumericCell(row, 12, period.maxPowerDeficitW);
            row.createCell(13).setCellValue(period.sampleCount);
        }

        sheet.createFreezePane(0, 1);
        autoSize(sheet, headers.length);
    }

    private static void writeSectorSummarySheet(
            Sheet sheet,
            Map<String, SectorSummary> sectors
    ) {
        String[] headers = {
                "section_id", "track_id", "period_count",
                "total_duration_s", "min_voltage_V",
                "max_power_deficit_W", "affected_trains"
        };
        writeHeader(sheet, headers);

        List<SectorSummary> sorted = new ArrayList<>(sectors.values());
        sorted.sort(Comparator
                .comparingDouble((SectorSummary s) -> s.totalDurationS)
                .reversed()
                .thenComparing(
                        Comparator.comparingInt(
                                        (SectorSummary s) -> s.periodCount)
                                .reversed())
                .thenComparing(
                        s -> s.minVoltageV,
                        Comparator.nullsLast(Double::compareTo))
                .thenComparing(
                        s -> s.maxPowerDeficitW,
                        Comparator.nullsLast(
                                Comparator.reverseOrder())));

        int rowIndex = 1;
        for (SectorSummary sector : sorted) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(sector.sectionId);
            row.createCell(1).setCellValue(sector.trackId);
            row.createCell(2).setCellValue(sector.periodCount);
            row.createCell(3).setCellValue(sector.totalDurationS);
            setNumericCell(row, 4, sector.minVoltageV);
            setNumericCell(row, 5, sector.maxPowerDeficitW);
            row.createCell(6).setCellValue(String.join(", ", sector.trainIds));
        }

        sheet.createFreezePane(0, 1);
        autoSize(sheet, headers.length);
    }

    private static void writeHeader(Sheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        for (int column = 0; column < headers.length; column++) {
            header.createCell(column).setCellValue(headers[column]);
        }
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static void writeSystemWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata,
            Path outputPath
    ) throws IOException {
        Map<Double, SystemResult> results = collectSystemResults(rows);

        try (Workbook workbook = new XSSFWorkbook()) {
            writeMetadataSheet(workbook, metadata);
            Sheet sheet = workbook.createSheet("Power and energy balance");
            writeSystemSheet(sheet, results);

            Files.createDirectories(outputPath.getParent());
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
    }

    private static Map<Double, SystemResult> collectSystemResults(
            List<LongTableRow> rows
    ) {
        Map<Double, SystemResult> result = new LinkedHashMap<>();

        for (LongTableRow row : rows) {
            if (!"SYSTEM".equals(row.objectType)
                    || !"DC".equals(row.objectId)
                    || row.timeS == null
                    || !"RESULT".equals(row.stage)) {
                continue;
            }

            SystemResult system = result.computeIfAbsent(
                    row.timeS,
                    ignored -> new SystemResult()
            );

            Double value = parseDouble(row.value);
            switch (row.signal) {
                case "p_substations_W" -> system.pSubstationsW = value;
                case "p_trains_W" -> system.pTrainsW = value;
                case "p_fixed_loads_W" -> system.pFixedLoadsW = value;
                case "p_losses_W" -> system.pLossesW = value;
                case "p_balance_W" -> system.pBalanceW = value;
                case "e_substations_supplied_J" -> system.eSubstationsSuppliedJ = value;
                case "e_substations_absorbed_J" -> system.eSubstationsAbsorbedJ = value;
                case "e_substations_net_J" -> system.eSubstationsNetJ = value;
                case "e_trains_consumed_J" -> system.eTrainsConsumedJ = value;
                case "e_trains_regenerated_J" -> system.eTrainsRegeneratedJ = value;
                case "e_trains_net_J" -> system.eTrainsNetJ = value;
                case "e_fixed_loads_consumed_J" -> system.eFixedLoadsConsumedJ = value;
                case "e_losses_J" -> system.eLossesJ = value;
                case "e_balance_J" -> system.eBalanceJ = value;
                default -> {
                    // Ignore static system parameters and other signals.
                }
            }
        }

        return result;
    }

    private static void writeSystemSheet(
            Sheet sheet,
            Map<Double, SystemResult> results
    ) {
        String[] headers = {
                "time_s",
                "p_substations_W",
                "p_trains_W",
                "p_fixed_loads_W",
                "p_losses_W",
                "p_balance_W",
                "e_substations_supplied_J",
                "e_substations_absorbed_J",
                "e_substations_net_J",
                "e_trains_consumed_J",
                "e_trains_regenerated_J",
                "e_trains_net_J",
                "e_fixed_loads_consumed_J",
                "e_losses_J",
                "e_balance_J"
        };

        Row header = sheet.createRow(0);
        for (int column = 0; column < headers.length; column++) {
            header.createCell(column).setCellValue(headers[column]);
        }

        int rowIndex = 1;
        for (Map.Entry<Double, SystemResult> entry : results.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            SystemResult value = entry.getValue();
            row.createCell(0).setCellValue(entry.getKey());
            setNumericCell(row, 1, value.pSubstationsW);
            setNumericCell(row, 2, value.pTrainsW);
            setNumericCell(row, 3, value.pFixedLoadsW);
            setNumericCell(row, 4, value.pLossesW);
            setNumericCell(row, 5, value.pBalanceW);
            setNumericCell(row, 6, value.eSubstationsSuppliedJ);
            setNumericCell(row, 7, value.eSubstationsAbsorbedJ);
            setNumericCell(row, 8, value.eSubstationsNetJ);
            setNumericCell(row, 9, value.eTrainsConsumedJ);
            setNumericCell(row, 10, value.eTrainsRegeneratedJ);
            setNumericCell(row, 11, value.eTrainsNetJ);
            setNumericCell(row, 12, value.eFixedLoadsConsumedJ);
            setNumericCell(row, 13, value.eLossesJ);
            setNumericCell(row, 14, value.eBalanceJ);
        }

        sheet.createFreezePane(0, 1);
        for (int column = 0; column < headers.length; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static void writeLineWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata,
            Path outputPath
    ) throws IOException {
        Map<String, Map<Double, LineResult>> results =
                collectLineResults(rows);

        try (Workbook workbook = new XSSFWorkbook()) {
            writeMetadataSheet(workbook, metadata);

            for (Map.Entry<String, Map<Double, LineResult>> lineEntry
                    : results.entrySet()) {
                String lineId = lineEntry.getKey();
                Sheet sheet = workbook.createSheet(safeSheetName(lineId));
                writeLineSheet(sheet, lineId, lineEntry.getValue());
            }

            Files.createDirectories(outputPath.getParent());
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
    }

    private static Map<String, Map<Double, LineResult>> collectLineResults(
            List<LongTableRow> rows
    ) {
        Map<String, Map<Double, LineResult>> result = new LinkedHashMap<>();

        for (LongTableRow row : rows) {
            if (!"LINE".equals(row.objectType)
                    || row.timeS == null
                    || !"RESULT".equals(row.stage)) {
                continue;
            }

            Map<Double, LineResult> byTime =
                    result.computeIfAbsent(
                            row.objectId,
                            ignored -> new LinkedHashMap<>()
                    );
            LineResult lineResult =
                    byTime.computeIfAbsent(
                            row.timeS,
                            ignored -> new LineResult()
                    );

            switch (row.signal) {
                case "p_losses_W" ->
                        lineResult.pLossesW = parseDouble(row.value);
                case "e_losses_J" ->
                        lineResult.eLossesJ = parseDouble(row.value);
                default -> {
                    // Ignore other signals.
                }
            }
        }

        return result;
    }

    private static void writeLineSheet(
            Sheet sheet,
            String lineId,
            Map<Double, LineResult> results
    ) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(lineId + ".time_s");
        header.createCell(1).setCellValue(lineId + ".p_losses_W");
        header.createCell(2).setCellValue(lineId + ".e_losses_J");

        int rowIndex = 1;
        for (Map.Entry<Double, LineResult> entry : results.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            setNumericCell(row, 1, entry.getValue().pLossesW);
            setNumericCell(row, 2, entry.getValue().eLossesJ);
        }

        sheet.createFreezePane(0, 1);
        for (int column = 0; column < 3; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static void writeInstallationWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata, Path outputPath
    ) throws IOException {

        Map<String, Double> resistanceByInstallation =
                findInternalResistances(rows);

        Map<String, Map<Double, InstallationResult>> results =
                collectInstallationResults(rows);

        try (Workbook workbook = new XSSFWorkbook()) {

            writeMetadataSheet(workbook, metadata);

            for (Map.Entry<String, Map<Double, InstallationResult>> installationEntry
                    : results.entrySet()) {

                String installationId =
                        installationEntry.getKey();

                Sheet sheet =
                        workbook.createSheet(
                                safeSheetName(installationId)
                        );

                writeInstallationSheet(
                        sheet,
                        installationId,
                        installationEntry.getValue(),
                        resistanceByInstallation.get(installationId)
                );
            }

            Files.createDirectories(outputPath.getParent());

            try (OutputStream out =
                         Files.newOutputStream(outputPath)) {

                workbook.write(out);
            }
        }
    }

    private static void writeMetadataSheet(
            Workbook workbook,
            ResultMetadata metadata
    ) {
        Sheet sheet =
                workbook.createSheet("Metadata");

        writeMetadataRow(sheet, 0, "project", metadata.project());
        writeMetadataRow(sheet, 1, "scenario", metadata.scenario());
        writeMetadataRow(sheet, 2, "base_hash", metadata.baseHash());
        writeMetadataRow(sheet, 3, "generated_at", metadata.generatedAt());

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private static void writeMetadataRow(
            Sheet sheet,
            int rowIndex,
            String name,
            String value
    ) {
        Row row =
                sheet.createRow(rowIndex);

        row.createCell(0)
                .setCellValue(name);

        row.createCell(1)
                .setCellValue(value);
    }
    private static void writeInstallationSheet(
            Sheet sheet,
            String installationId,
            Map<Double, InstallationResult> results,
            Double internalResistanceOhm
    ) {
        Row header =
                sheet.createRow(0);

        header.createCell(0)
                .setCellValue(installationId + ".time_s");

        header.createCell(1)
                .setCellValue(installationId + ".u_V");

        header.createCell(2)
                .setCellValue(installationId + ".i_A");

        header.createCell(3)
                .setCellValue(installationId + ".p_W");

        header.createCell(4)
                .setCellValue(installationId + ".p_losses_W");

        header.createCell(5)
                .setCellValue(installationId + ".state");

        header.createCell(6)
                .setCellValue(installationId + ".e_supplied_J");
        header.createCell(7)
                .setCellValue(installationId + ".e_absorbed_J");
        header.createCell(8)
                .setCellValue(installationId + ".e_net_J");

        int rowIndex = 1;

        for (Map.Entry<Double, InstallationResult> entry
                : results.entrySet()) {

            double timeS =
                    entry.getKey();

            InstallationResult result =
                    entry.getValue();

            Row row =
                    sheet.createRow(rowIndex++);

            row.createCell(0)
                    .setCellValue(timeS);

            setNumericCell(
                    row,
                    1,
                    result.uV
            );

            setNumericCell(
                    row,
                    2,
                    result.iA
            );

            setNumericCell(
                    row,
                    3,
                    result.pW
            );

            if (result.iA != null
                    && internalResistanceOhm != null) {

                double pLossesW =
                        result.iA
                                * result.iA
                                * internalResistanceOhm;

                row.createCell(4)
                        .setCellValue(pLossesW);
            }

            if (result.state != null) {
                row.createCell(5)
                        .setCellValue(result.state);
            }

            setNumericCell(row, 6, result.eSuppliedJ);
            setNumericCell(row, 7, result.eAbsorbedJ);
            setNumericCell(row, 8, result.eNetJ);
        }

        sheet.createFreezePane(0, 1);

        for (int column = 0; column < 9; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private static Map<String, Map<Double, InstallationResult>>
    collectInstallationResults(
            List<LongTableRow> rows
    ) {
        Map<String, Map<Double, InstallationResult>> result =
                new LinkedHashMap<>();

        for (LongTableRow row : rows) {

            if (!isSubstation(row.objectType)) {
                continue;
            }

            if (row.timeS == null) {
                continue;
            }

            if (!"RESULT".equals(row.stage)) {
                continue;
            }

            Map<Double, InstallationResult> byTime =
                    result.computeIfAbsent(
                            row.objectId,
                            ignored -> new LinkedHashMap<>()
                    );

            InstallationResult installationResult =
                    byTime.computeIfAbsent(
                            row.timeS,
                            ignored -> new InstallationResult()
                    );

            switch (row.signal) {

                case "u_V" ->
                        installationResult.uV =
                                parseDouble(row.value);

                case "i_A" ->
                        installationResult.iA =
                                parseDouble(row.value);

                case "p_W" ->
                        installationResult.pW =
                                parseDouble(row.value);

                case "state" ->
                        installationResult.state =
                                row.value;

                case "e_supplied_J" ->
                        installationResult.eSuppliedJ = parseDouble(row.value);

                case "e_absorbed_J" ->
                        installationResult.eAbsorbedJ = parseDouble(row.value);

                case "e_net_J" ->
                        installationResult.eNetJ = parseDouble(row.value);

                default -> {
                    // Ignore other signals.
                }
            }
        }

        return result;
    }

    private static Map<String, Double> findInternalResistances(
            List<LongTableRow> rows
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        for (LongTableRow row : rows) {

            if (!isSubstation(row.objectType)) {
                continue;
            }

            if (!"internal_resistance_ohm".equals(row.signal)) {
                continue;
            }

            Double resistance =
                    parseDouble(row.value);

            if (resistance != null) {
                result.put(
                        row.objectId,
                        resistance
                );
            }
        }

        return result;
    }

    private static boolean isSubstation(String objectType) {
        return "DIODE_SUBSTATION".equals(objectType)
                || "THYRISTOR_SUBSTATION".equals(objectType);
    }

    private static List<LongTableRow> readLongTable(
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

    private static ResultMetadata extractMetadata(
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

    private static void setNumericCell(
            Row row,
            int column,
            Double value
    ) {
        if (value != null) {
            row.createCell(column)
                    .setCellValue(value);
        }
    }

    private static Double parseDouble(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(value);
    }

    private static Double parseNullableDouble(
            String value
    ) {
        return parseDouble(value);
    }

    private static String safeSheetName(
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

    private record LongTableRow(
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

    private static final class InstallationResult {
        private Double uV;
        private Double iA;
        private Double pW;
        private String state;
        private Double eSuppliedJ;
        private Double eAbsorbedJ;
        private Double eNetJ;
    }

    private static final class TrainResult {
        private String electricRouteId;
        private Double electricRoutePositionM;
        private String sectionId;
        private String trackId;
        private Double positionM;
        private Double baseRoutePositionM;
        private Double uV;
        private Double iA;
        private Double pReqW;
        private Double pW;
        private Double pDeltaW;
        private Double eConsumedJ;
        private Double eRegeneratedJ;
        private Double eNetJ;
    }

    private static final class StationRange {
        private double minimumM = Double.POSITIVE_INFINITY;
        private double maximumM = Double.NEGATIVE_INFINITY;

        private void include(double positionM) {
            minimumM = Math.min(minimumM, positionM);
            maximumM = Math.max(maximumM, positionM);
        }

        private double centre() {
            return (minimumM + maximumM) / 2.0;
        }

        private boolean isEmpty() {
            return minimumM == Double.POSITIVE_INFINITY;
        }
    }

    private static final class BaseRoutePositionMapper {
        private final Map<String, Double> sectionOffsetsM;
        private final Map<String, List<RailwaySegment>> segmentsBySection;

        private BaseRoutePositionMapper(
                Map<String, Double> sectionOffsetsM,
                Map<String, List<RailwaySegment>> segmentsBySection
        ) {
            this.sectionOffsetsM = sectionOffsetsM;
            this.segmentsBySection = segmentsBySection;
        }

        private static BaseRoutePositionMapper disabled() {
            return new BaseRoutePositionMapper(Map.of(), Map.of());
        }

        private boolean isEnabled() {
            return !sectionOffsetsM.isEmpty();
        }

        private static BaseRoutePositionMapper load(
                Path trackSegmentsPath,
                List<String> baseRouteSections
        ) throws IOException {
            if (baseRouteSections.isEmpty()) {
                throw new IllegalArgumentException(
                        "Base route must contain at least one section"
                );
            }

            Map<String, Double> sectionLengthsM =
                    readSectionLengths(trackSegmentsPath);
            Map<String, List<RailwaySegment>> segmentsBySection =
                    readRailwaySegments(trackSegmentsPath);
            Map<String, Double> offsetsM = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            double offsetM = 0.0;

            for (String section : baseRouteSections) {
                if (!seen.add(section)) {
                    throw new IllegalArgumentException(
                            "Duplicate section in base route: " + section
                    );
                }

                Double lengthM = sectionLengthsM.get(section);
                if (lengthM == null) {
                    throw new IllegalArgumentException(
                            "Base-route section " + section
                                    + " is missing from "
                                    + trackSegmentsPath
                    );
                }

                offsetsM.put(section, offsetM);
                offsetM += lengthM;
            }

            return new BaseRoutePositionMapper(
                    offsetsM, segmentsBySection
            );
        }

        private Double map(String sectionId, Double positionM) {
            if (sectionId == null || positionM == null) {
                return null;
            }

            Double offsetM = sectionOffsetsM.get(sectionId);
            return offsetM == null ? null : offsetM + positionM;
        }

        private Double mapRailwayPosition(String railwayPosition) {
            if (railwayPosition == null) {
                return null;
            }
            String[] parts = railwayPosition.trim().split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "Invalid railway position: " + railwayPosition
                );
            }
            String sectionId = parts[0];
            Double sectionOffsetM = sectionOffsetsM.get(sectionId);
            if (sectionOffsetM == null) {
                return null;
            }
            double railwayPositionM = railwayMetres(parts[1]);
            List<RailwaySegment> segments =
                    segmentsBySection.get(sectionId);
            if (segments == null) {
                return null;
            }
            for (RailwaySegment segment : segments) {
                if (segment.contains(railwayPositionM)) {
                    return sectionOffsetM
                            + segment.modelPositionM(railwayPositionM);
                }
            }
            throw new IllegalArgumentException(
                    "Railway position is outside section " + sectionId
                            + ": " + railwayPosition
            );
        }

        private static double railwayMetres(String value) {
            int plus = value.indexOf('+');
            if (plus <= 0 || plus == value.length() - 1) {
                throw new IllegalArgumentException(
                        "Invalid railway coordinate: " + value
                );
            }
            return Double.parseDouble(value.substring(0, plus)) * 1000.0
                    + Double.parseDouble(value.substring(plus + 1));
        }

        private static Map<String, List<RailwaySegment>>
        readRailwaySegments(Path trackSegmentsPath) throws IOException {
            Map<String, List<RailwaySegment>> result =
                    new LinkedHashMap<>();
            try (BufferedReader reader =
                         Files.newBufferedReader(trackSegmentsPath)) {
                String header = reader.readLine();
                if (!"section,from_rwy,to_rwy,start_model_m,length_m"
                        .equals(header)) {
                    throw new IllegalArgumentException(
                            "Unexpected track_segments.csv header in "
                                    + trackSegmentsPath + ": " + header
                    );
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] fields = line.split(",", -1);
                    if (fields.length != 5) {
                        throw new IllegalArgumentException(
                                "Invalid track segment row: " + line
                        );
                    }
                    result.computeIfAbsent(
                            fields[0], ignored -> new ArrayList<>()
                    ).add(new RailwaySegment(
                            railwayCoordinateMetres(fields[1]),
                            railwayCoordinateMetres(fields[2]),
                            Double.parseDouble(fields[3]),
                            Double.parseDouble(fields[4])
                    ));
                }
            }
            return result;
        }

        private static double railwayCoordinateMetres(String value) {
            String[] parts = value.trim().split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "Invalid railway position: " + value
                );
            }
            return railwayMetres(parts[1]);
        }

        private static Map<String, Double> readSectionLengths(
                Path trackSegmentsPath
        ) throws IOException {
            if (!Files.exists(trackSegmentsPath)) {
                throw new IllegalArgumentException(
                        "Track-segment export not found: "
                                + trackSegmentsPath
                );
            }

            Map<String, Double> lengthsM = new LinkedHashMap<>();

            try (BufferedReader reader =
                         Files.newBufferedReader(trackSegmentsPath)) {
                String header = reader.readLine();
                if (!"section,from_rwy,to_rwy,start_model_m,length_m"
                        .equals(header)) {
                    throw new IllegalArgumentException(
                            "Unexpected track_segments.csv header in "
                                    + trackSegmentsPath + ": " + header
                    );
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    String[] fields = line.split(",", -1);
                    if (fields.length != 5) {
                        throw new IllegalArgumentException(
                                "Invalid track-segment row: " + line
                        );
                    }

                    String section = fields[0];
                    double startModelM;
                    double lengthM;
                    try {
                        startModelM = Double.parseDouble(fields[3]);
                        lengthM = Double.parseDouble(fields[4]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid track-segment coordinate in row: "
                                        + line,
                                e
                        );
                    }

                    if (startModelM < 0.0 || !(lengthM > 0.0)) {
                        throw new IllegalArgumentException(
                                "Track-segment start must be non-negative "
                                        + "and length must be positive: "
                                        + line
                        );
                    }

                    double sectionEndM = startModelM + lengthM;
                    lengthsM.merge(section, sectionEndM, Math::max);
                }
            }

            return lengthsM;
        }
    }

    private static final class RailwaySegment {
        private final double fromRailwayM;
        private final double toRailwayM;
        private final double startModelM;
        private final double lengthM;

        private RailwaySegment(
                double fromRailwayM,
                double toRailwayM,
                double startModelM,
                double lengthM
        ) {
            this.fromRailwayM = fromRailwayM;
            this.toRailwayM = toRailwayM;
            this.startModelM = startModelM;
            this.lengthM = lengthM;
        }

        private boolean contains(double positionM) {
            return positionM >= Math.min(fromRailwayM, toRailwayM)
                    && positionM <= Math.max(fromRailwayM, toRailwayM);
        }

        private double modelPositionM(double railwayPositionM) {
            double railwayLengthM = toRailwayM - fromRailwayM;
            if (railwayLengthM == 0.0) {
                return startModelM;
            }
            double fraction =
                    (railwayPositionM - fromRailwayM) / railwayLengthM;
            return startModelM + fraction * lengthM;
        }
    }

    private static final class LineResult {
        private Double pLossesW;
        private Double eLossesJ;
    }

    private static final class SystemResult {
        private Double pSubstationsW;
        private Double pTrainsW;
        private Double pFixedLoadsW;
        private Double pLossesW;
        private Double pBalanceW;
        private Double eSubstationsSuppliedJ;
        private Double eSubstationsAbsorbedJ;
        private Double eSubstationsNetJ;
        private Double eTrainsConsumedJ;
        private Double eTrainsRegeneratedJ;
        private Double eTrainsNetJ;
        private Double eFixedLoadsConsumedJ;
        private Double eLossesJ;
        private Double eBalanceJ;
    }

    private enum DeviationType {
        UNDER_VOLTAGE,
        POWER_DEFICIT
    }

    private static final class RawDeviation {
        private final DeviationType type;
        private final String trainId;
        private final double timeS;
        private final String sectionId;
        private final String trackId;
        private final Double positionM;
        private final Double routePositionM;
        private final Double voltageV;
        private final Double powerDeficitW;

        private RawDeviation(
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

    private static final class DeviationPeriod {
        private final DeviationType type;
        private final String trainId;
        private final String sectionId;
        private final String trackId;
        private final double startTimeS;
        private double endTimeS;
        private final Double startPositionM;
        private Double endPositionM;
        private final Double startRoutePositionM;
        private Double endRoutePositionM;
        private Double minVoltageV;
        private Double maxPowerDeficitW;
        private int sampleCount;

        private DeviationPeriod(RawDeviation deviation) {
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

        private boolean canAppend(RawDeviation deviation) {
            return type == deviation.type
                    && trainId.equals(deviation.trainId)
                    && same(sectionId, deviation.sectionId)
                    && same(trackId, deviation.trackId)
                    && deviation.timeS >= endTimeS
                    && deviation.timeS - endTimeS <= MAX_EVENT_GAP_S;
        }

        private void append(RawDeviation deviation) {
            endTimeS = deviation.timeS;
            endPositionM = deviation.positionM;
            endRoutePositionM = deviation.routePositionM;
            minVoltageV = minimum(minVoltageV, deviation.voltageV);
            maxPowerDeficitW = maximum(
                    maxPowerDeficitW, deviation.powerDeficitW);
            sampleCount++;
        }
    }

    private static final class SectorSummary {
        private final String sectionId;
        private final String trackId;
        private int periodCount;
        private double totalDurationS;
        private Double minVoltageV;
        private Double maxPowerDeficitW;
        private final List<String> trainIds = new ArrayList<>();

        private SectorSummary(String sectionId, String trackId) {
            this.sectionId = sectionId;
            this.trackId = trackId;
        }

        private void add(DeviationPeriod period) {
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

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static Double minimum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    private static Double maximum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static void setTextCell(
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
