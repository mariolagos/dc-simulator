package org.dcsim.pivot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal stub – we keep the hook, implement later in A2 if needed. */
final class ExcelExporter {
    static void write(Path xlsxPath) throws IOException {
        // Placeholder – writing CSV-only is enough for A2 core.
        // Create an empty placeholder so pipelines expecting the file won't fail when --excel is set later.
        if (!Files.exists(xlsxPath)) {
            Files.createDirectories(xlsxPath.getParent());
            Files.createFile(xlsxPath);
        }
    }
}
