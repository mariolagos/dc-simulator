package org.dcsim.validation;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public final class TestScenarioPaths {
    private TestScenarioPaths() {
    }

    public static Path scenarioRoot() {
        // 1) Prefer src/test/resources/validationTests (classpath)
        URL res = TestScenarioPaths.class.getClassLoader().getResource("validationTests");
        if (res != null) {
            try {
                Path p = Paths.get(res.toURI());
                if (looksLikeScenarioRoot(p)) return p;
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to resolve test resource path for validationTests", e);
            }
        }

        // 2) Fallback candidates in repo (support BOTH layouts)
        List<Path> candidates = Arrays.asList(
                Paths.get("project/validationTests"),          // preferred new layout: .../validationTests/<scenarioId>
                Paths.get("project/validationTests/src"),      // old layout
                Paths.get("validationTests"),                  // if repo root has it
                Paths.get("validationTests/src")
        );

        for (Path c : candidates) {
            if (looksLikeScenarioRoot(c)) return c;
        }

        throw new IllegalStateException(
                "Could not locate scenario root. Expected a folder that contains scenario dirs like '3S1T'. " +
                        "Tried: " + candidates);
    }

    private static boolean looksLikeScenarioRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) return false;

        // If any known scenario dir exists, treat it as root.
        // (You can add more IDs here.)
        return Files.isDirectory(root.resolve("3S1T"))
                || Files.isDirectory(root.resolve("3S2T"))
                || Files.isDirectory(root.resolve("1S5T"))
                || Files.isDirectory(root.resolve("trackSeparation"))
                || Files.isDirectory(root.resolve("coupledTracks"));
    }
}
