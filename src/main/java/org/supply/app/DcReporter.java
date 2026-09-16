package org.supply.app;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supply.solver.io.ResultMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DcReporter {

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
                resultsTrain
        );

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
    }

    private static void writeTrainWorkbook(
            List<LongTableRow> rows,
            ResultMetadata metadata, Path outputPath
    ) throws IOException {

        Map<String, Map<Double, TrainResult>> results =
                collectTrainResults(rows);

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
                .setCellValue(trainId + ".u_V");
        header.createCell(7)
                .setCellValue(trainId + ".i_A");
        header.createCell(8)
                .setCellValue(trainId + ".p_req_W");
        header.createCell(9)
                .setCellValue(trainId + ".p_W");
        header.createCell(10)
                .setCellValue(trainId + ".p_delta_W");
        header.createCell(11)
                .setCellValue(trainId + ".e_consumed_J");
        header.createCell(12)
                .setCellValue(trainId + ".e_regenerated_J");
        header.createCell(13)
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
            setNumericCell(row, 6, result.uV);
            setNumericCell(row, 7, result.iA);
            setNumericCell(row, 8, result.pReqW);
            setNumericCell(row, 9, result.pW);
            setNumericCell(row, 10, result.pDeltaW);
            setNumericCell(row, 11, result.eConsumedJ);
            setNumericCell(row, 12, result.eRegeneratedJ);
            setNumericCell(row, 13, result.eNetJ);
        }

        sheet.createFreezePane(0, 1);

        for (int column = 0; column < 14; column++) {
            sheet.autoSizeColumn(column);
        }
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
        private Double uV;
        private Double iA;
        private Double pReqW;
        private Double pW;
        private Double pDeltaW;
        private Double eConsumedJ;
        private Double eRegeneratedJ;
        private Double eNetJ;
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
