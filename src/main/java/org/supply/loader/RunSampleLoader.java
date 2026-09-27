package org.supply.loader;

import org.supply.domain.RunSample;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public final class RunSampleLoader {

    public List<RunSample> load(Path file) throws IOException {

        List<RunSample> out = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {

            String header = reader.readLine();

            if (header == null) {
                throw new IllegalArgumentException(
                        "Empty file: " + file
                );
            }

            String[] headerParts = header.split(",", -1);
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < headerParts.length; i++) {
                columns.put(headerParts[i].trim(), i);
            }
            int speedColumn = columns.getOrDefault("speed_mps", -1);

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] p = line.split(",", -1);

                if (p.length < 7) {
                    throw new IllegalArgumentException(
                            "Invalid row in " + file + ": " + line
                    );
                }

                out.add(new RunSample(
                        Double.parseDouble(p[0].trim()),
                        p[1].trim(),
                        p[2].trim(),
                        p[3].trim(),
                        p[4].trim(),
                        Double.parseDouble(p[5].trim()),
                        Double.parseDouble(p[6].trim()),
                        optionalDouble(p, speedColumn)
                ));
            }
        }

        return out;
    }

    private static Double optionalDouble(String[] values, int column) {
        if (column < 0 || column >= values.length) {
            return null;
        }
        String value = values[column].trim();
        return value.isEmpty() ? null : Double.parseDouble(value);
    }
}
