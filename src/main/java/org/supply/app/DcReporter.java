package org.supply.app;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
                        .resolve("longtable.csv");

        Path outputPath =
                context.resultDirectory()
                        .resolve("results_installation.xlsx");

        List<LongTableRow> rows =
                readLongTable(longTablePath);

        writeInstallationWorkbook(
                rows,
                outputPath
        );

        Path resultsTrain =
                context.resultDirectory()
                        .resolve("results_train.xlsx");

        writeTrainWorkbook(
                rows,
                resultsTrain
        );

        System.out.println(
                "Created " + outputPath
        );
    }

    private static void writeTrainWorkbook(
            List<LongTableRow> rows,
            Path outputPath
    ) throws IOException {

        Map<String, Map<Double, TrainResult>> results =
                collectTrainResults(rows);

        try (Workbook workbook = new XSSFWorkbook()) {

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
        Row header =
                sheet.createRow(0);

        header.createCell(0)
                .setCellValue(trainId + ".time_s");

        header.createCell(1)
                .setCellValue(trainId + ".position_m");

        header.createCell(2)
                .setCellValue(trainId + ".u_V");

        header.createCell(3)
                .setCellValue(trainId + ".i_A");

        header.createCell(4)
                .setCellValue(trainId + ".p_req_W");

        header.createCell(5)
                .setCellValue(trainId + ".p_W");

        int rowIndex = 1;

        for (Map.Entry<Double, TrainResult> entry
                : results.entrySet()) {

            Row row =
                    sheet.createRow(rowIndex++);

            row.createCell(0)
                    .setCellValue(entry.getKey());

            TrainResult result =
                    entry.getValue();

            setNumericCell(row, 1, result.positionM);
            setNumericCell(row, 2, result.uV);
            setNumericCell(row, 3, result.iA);
            setNumericCell(row, 4, result.pReqW);
            setNumericCell(row, 5, result.pW);
        }

        sheet.createFreezePane(0, 1);

        for (int column = 0; column < 6; column++) {
            sheet.autoSizeColumn(column);
        }
    }
    private static void writeInstallationWorkbook(
            List<LongTableRow> rows,
            Path outputPath
    ) throws IOException {

        Map<String, Double> resistanceByInstallation =
                findInternalResistances(rows);

        Map<String, Map<Double, InstallationResult>> results =
                collectInstallationResults(rows);

        try (Workbook workbook = new XSSFWorkbook()) {

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
        }

        sheet.createFreezePane(0, 1);

        for (int column = 0; column < 6; column++) {
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

            if (!"DIODE_SUBSTATION".equals(row.objectType)) {
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

            if (!"DIODE_SUBSTATION".equals(row.objectType)) {
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
                                fields[4],
                                fields[5],
                                fields[6],
                                fields[7],
                                fields[8],
                                fields[9]
                        )
                );
            }
        }

        return result;
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
            String objectType,
            String objectId,
            String signal,
            String value,
            String unit,
            String stage
    ) {
    }

    private static final class InstallationResult {
        private Double uV;
        private Double iA;
        private Double pW;
        private String state;
    }

    private static final class TrainResult {
        private Double positionM;
        private Double uV;
        private Double iA;
        private Double pReqW;
        private Double pW;
    }
}