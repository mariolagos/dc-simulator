package org.supply.loader;

import org.supply.domain.SystemParameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SystemParametersLoader {

    public SystemParameters load(Path file) throws IOException {
        Map<String, Double> values = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();

            if (header == null) {
                throw new IllegalArgumentException("Empty file: " + file);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", -1);

                if (parts.length < 2) {
                    throw new IllegalArgumentException(
                            "Invalid row in " + file + ": " + line
                    );
                }

                values.put(
                        parts[0].trim(),
                        Double.parseDouble(parts[1].trim())
                );
            }
        }

        return new SystemParameters(
                required(values, "u_nominal_V"),
                required(values, "u_min_V"),
                required(values, "u_cutoff_V"),
                required(values, "u_max_V"),
                required(values, "i_train_max_A")
        );
    }

    private static double required(Map<String, Double> values, String key) {
        Double value = values.get(key);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required system parameter: " + key
            );
        }

        return value;
    }
}