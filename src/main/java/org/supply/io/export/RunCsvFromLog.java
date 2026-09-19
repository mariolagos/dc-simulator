package org.supply.io.export;

import org.supply.domain.RunColumn;
import org.supply.domain.RunLogFormat;
import org.supply.domain.RunSource;
import org.supply.domain.RunSourceType;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads measured, delimited train logs using a project-specific column map.
 * Values are converted to SI units. If position is absent it is obtained by
 * trapezoidal integration of speed. If power is absent it is calculated as
 * voltage times current.
 *
 * <p>The reader deliberately does not add auxiliary power and does not create
 * station dwell samples: measured total power already contains those effects.</p>
 */
public final class RunCsvFromLog implements RunDataReader {

    @Override
    public RunReadResult read(RunSource source) throws Exception {
        if (source.type() != RunSourceType.LOG) {
            throw new IllegalArgumentException("RunCsvFromLog requires a LOG source");
        }

        RunLogFormat format = source.logFormat();
        try (BufferedReader reader = Files.newBufferedReader(
                source.file(), StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("Empty log file: " + source.file());
            }

            List<String> headings = parseRow(stripBom(headerLine), format.delimiter());
            Map<String, Integer> columns = index(headings);
            require(columns, format.time());
            require(columns, format.position());
            require(columns, format.speed());
            require(columns, format.power());
            require(columns, format.voltage());
            require(columns, format.current());

            List<RawSample> raw = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = parseRow(line, format.delimiter());
                try {
                    raw.add(readRaw(cells, columns, format));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(
                            "Invalid log value at " + source.file()
                                    + ":" + lineNumber + ": " + e.getMessage(), e
                    );
                }
            }
            return normalize(raw, format.consumptionPositive(), source.file().toString());
        }
    }

    private static RunReadResult normalize(
            List<RawSample> raw,
            boolean consumptionPositive,
            String sourceName
    ) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("No data rows in log file: " + sourceName);
        }

        double originS = raw.get(0).absoluteTimeS();
        double positionM = raw.get(0).positionM() == null
                ? 0.0 : raw.get(0).positionM();
        List<RunSample> samples = new ArrayList<>(raw.size());

        for (int i = 0; i < raw.size(); i++) {
            RawSample current = raw.get(i);
            double timeS = current.absoluteTimeS() - originS;
            if (i > 0) {
                RawSample previous = raw.get(i - 1);
                double dt = current.absoluteTimeS() - previous.absoluteTimeS();
                if (dt <= 0.0) {
                    throw new IllegalArgumentException(
                            "Log timestamps must be strictly increasing in " + sourceName
                    );
                }
                if (current.positionM() == null) {
                    positionM += 0.5 * (previous.speedMps() + current.speedMps()) * dt;
                } else {
                    positionM = current.positionM();
                }
            }

            double powerW = current.powerW() != null
                    ? current.powerW()
                    : current.voltageV() * current.currentA();
            if (!consumptionPositive) {
                powerW = -powerW;
            }
            samples.add(new RunSample(
                    timeS,
                    positionM,
                    powerW,
                    current.speedMps(),
                    current.voltageV(),
                    current.currentA()
            ));
        }
        return new RunReadResult(samples, 0.0);
    }

    private static RawSample readRaw(
            List<String> cells,
            Map<String, Integer> columns,
            RunLogFormat format
    ) {
        Double position = number(cells, columns, format.position(), Quantity.POSITION);
        Double speed = number(cells, columns, format.speed(), Quantity.SPEED);
        Double power = number(cells, columns, format.power(), Quantity.POWER);
        Double voltage = number(cells, columns, format.voltage(), Quantity.VOLTAGE);
        Double current = number(cells, columns, format.current(), Quantity.CURRENT);
        return new RawSample(
                time(cells, columns, format.time()),
                position,
                speed,
                power,
                voltage,
                current
        );
    }

    private static double time(
            List<String> cells,
            Map<String, Integer> columns,
            RunColumn column
    ) {
        String value = cell(cells, columns.get(column.name()), column.name());
        if (column.format().isBlank()) {
            return convert(Double.parseDouble(value), column.unit(), Quantity.TIME);
        }
        LocalDateTime dateTime = LocalDateTime.parse(
                value,
                DateTimeFormatter.ofPattern(column.format(), Locale.ROOT)
        );
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000.0;
    }

    private static Double number(
            List<String> cells,
            Map<String, Integer> columns,
            RunColumn column,
            Quantity quantity
    ) {
        if (column == null) {
            return null;
        }
        return convert(
                Double.parseDouble(cell(cells, columns.get(column.name()), column.name())),
                column.unit(),
                quantity
        );
    }

    private static double convert(double value, String unit, Quantity quantity) {
        String normalized = unit.trim().toLowerCase(Locale.ROOT);
        return switch (quantity) {
            case TIME -> switch (normalized) {
                case "", "s" -> value;
                case "ms" -> value / 1000.0;
                default -> unsupported(unit, quantity);
            };
            case POSITION -> switch (normalized) {
                case "", "m" -> value;
                case "km" -> value * 1000.0;
                default -> unsupported(unit, quantity);
            };
            case SPEED -> switch (normalized) {
                case "", "m/s" -> value;
                case "km/h", "kmh" -> value / 3.6;
                default -> unsupported(unit, quantity);
            };
            case POWER -> switch (normalized) {
                case "", "w" -> value;
                case "kw" -> value * 1000.0;
                case "mw" -> value * 1_000_000.0;
                default -> unsupported(unit, quantity);
            };
            case VOLTAGE -> switch (normalized) {
                case "", "v" -> value;
                case "kv" -> value * 1000.0;
                default -> unsupported(unit, quantity);
            };
            case CURRENT -> switch (normalized) {
                case "", "a" -> value;
                case "ka" -> value * 1000.0;
                default -> unsupported(unit, quantity);
            };
        };
    }

    private static double unsupported(String unit, Quantity quantity) {
        throw new IllegalArgumentException(
                "Unsupported " + quantity.name().toLowerCase(Locale.ROOT)
                        + " unit '" + unit + "'"
        );
    }

    private static void require(Map<String, Integer> columns, RunColumn column) {
        if (column != null && !columns.containsKey(column.name())) {
            throw new IllegalArgumentException(
                    "Missing configured log column '" + column.name() + "'"
            );
        }
    }

    private static Map<String, Integer> index(List<String> headings) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < headings.size(); i++) {
            if (result.put(headings.get(i), i) != null) {
                throw new IllegalArgumentException(
                        "Duplicate log column '" + headings.get(i) + "'"
                );
            }
        }
        return result;
    }

    private static String cell(List<String> cells, int index, String heading) {
        if (index >= cells.size() || cells.get(index).isBlank()) {
            throw new IllegalArgumentException("Blank value in column '" + heading + "'");
        }
        return cells.get(index).trim();
    }

    static List<String> parseRow(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unterminated quoted CSV value");
        }
        values.add(value.toString().trim());
        return values;
    }

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private enum Quantity { TIME, POSITION, SPEED, POWER, VOLTAGE, CURRENT }

    private record RawSample(
            double absoluteTimeS,
            Double positionM,
            Double speedMps,
            Double powerW,
            Double voltageV,
            Double currentA
    ) {
    }
}
