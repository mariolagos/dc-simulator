package org.supply.loader;

import org.supply.domain.RunSample;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunSampleLoader {

    public List<RunSample> load(Path file) throws IOException {
        List<RunSample> out = new ArrayList<>();

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

                String[] p = line.split(",", -1);

                if (p.length < 5) {
                    throw new IllegalArgumentException(
                            "Invalid row in " + file + ": " + line
                    );
                }

                out.add(new RunSample(
                        Double.parseDouble(p[0].trim()),
                        p[1].trim(),
                        p[2].trim(),
                        Double.parseDouble(p[3].trim()),
                        Double.parseDouble(p[4].trim())
                ));
            }
        }

        return out;
    }
}