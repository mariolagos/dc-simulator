package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.supply.utils.FolderFiles;
import org.supply.utils.StationFileChain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FolderTemplateLegs {
    private FolderTemplateLegs() { }

    static List<Config> read(Config template, Path folder) throws IOException {
        for (String field : List.of("legs", "run_excel", "run_log", "run_excel_sheet", "departure")) {
            if (template.hasPath(field)) {
                throw new IllegalArgumentException("Folder template cannot also specify " + field);
            }
        }
        String prefix = template.getString("prefix");
        String sheet = template.getString("sheet");
        if (prefix.isBlank() || sheet.isBlank()) {
            throw new IllegalArgumentException("Folder template prefix and sheet must not be blank");
        }
        Path absoluteFolder = folder.toAbsolutePath().normalize();
        List<Path> files = FolderFiles.list(absoluteFolder, prefix, ".xlsx");
        // Excel's temporary lock files are never input legs.
        files = files.stream().filter(p -> !p.getFileName().toString().startsWith("~$")).toList();
        List<Config> legs = new ArrayList<>();
        try {
            for (Path file : StationFileChain.order(files, prefix, ".xlsx")) {
                legs.add(ConfigFactory.parseMap(Map.of(
                        "run_excel", file.toString(), "run_excel_sheet", sheet)));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Folder template " + absoluteFolder + ": " + e.getMessage(), e);
        }
        return List.copyOf(legs);
    }
}
