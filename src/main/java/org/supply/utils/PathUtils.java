package org.supply.utils;

import java.nio.file.Files;
import java.nio.file.Path;

public class PathUtils {
    public static void createParent(Path file) throws Exception {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

}
