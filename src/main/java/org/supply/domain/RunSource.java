package org.supply.domain;

import java.nio.file.Path;

/** Configuration for one Excel run or one measured log. */
public record RunSource(
        RunSourceType type,
        Path file,
        String sheet,
        RunLogFormat logFormat
) {
    public RunSource {
        if (type == null || file == null) {
            throw new IllegalArgumentException("Run source type and file are required");
        }
        sheet = sheet == null ? "" : sheet;
        if (type == RunSourceType.LOG && logFormat == null) {
            throw new IllegalArgumentException("Log source requires logFormat");
        }
    }

    public static RunSource excel(Path file, String sheet) {
        return new RunSource(RunSourceType.EXCEL, file, sheet, null);
    }

    public static RunSource log(Path file, RunLogFormat format) {
        return new RunSource(RunSourceType.LOG, file, "", format);
    }
}
