package org.supply.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ExecutionLayoutFactory {

    private ExecutionLayoutFactory() {
    }

    public static ExecutionLayout fromCliArgs(String confArg, String outputArgOrNull) {
        Path configFile = resolveConfArg(confArg);
        Path inputDir = configFile.getParent();

        if (inputDir == null) {
            throw new IllegalArgumentException("Config file has no parent directory: " + configFile);
        }

        String scenarioId = stripExtension(configFile.getFileName().toString());
        String projectId = inputDir.getFileName() != null
                ? inputDir.getFileName().toString()
                : "no-project";

        Path outputRoot;
        if (outputArgOrNull == null || outputArgOrNull.trim().isEmpty()) {
            outputRoot = inputDir.resolve("dc").normalize();
        } else {
            outputRoot = resolvePathArg(outputArgOrNull);
        }

        return new ExecutionLayout(
                projectId,
                scenarioId,
                configFile,
                inputDir,
                outputRoot
        );
    }

    public static Path resolveConfArg(String arg) {
        Path raw = Paths.get(arg);

        if (raw.isAbsolute()) {
            Path abs = raw.normalize();
            validateConfigPath(abs);
            return abs;
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();

        Path direct = cwd.resolve(raw).normalize();
        if (Files.exists(direct)) {
            validateConfigPath(direct);
            return direct;
        }

        Path underProject = cwd.resolve("project").resolve(raw).normalize();
        if (Files.exists(underProject)) {
            validateConfigPath(underProject);
            return underProject;
        }

        throw new IllegalArgumentException("Config not found: " + raw);
    }

    public static Path resolvePathArg(String arg) {
        Path raw = Paths.get(arg);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        return Paths.get("").toAbsolutePath().resolve(raw).normalize();
    }

    private static void validateConfigPath(Path configFile) {
        String s = configFile.toString().toLowerCase();
        if (!(s.endsWith(".conf") || s.endsWith(".hocon"))) {
            throw new IllegalArgumentException("Config must be a .conf or .hocon file: " + configFile);
        }
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config not found: " + configFile);
        }
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalArgumentException("Config is not a file: " + configFile);
        }
    }

    static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }
}