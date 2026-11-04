package org.supply.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Non-recursive file discovery, independent of traffic and railway models. */
public final class FolderFiles {
    private FolderFiles() { }

    /** Matches literal, case-sensitive filename prefixes and suffixes. */
    public static List<Path> list(Path folder, String prefix, String suffix) throws IOException {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        return list(folder, path -> {
            String name = path.getFileName().toString();
            return name.startsWith(prefix) && name.endsWith(suffix);
        });
    }

    /**
     * Returns regular files in filename order; never visits child directories.
     * No matches returns an empty list. Directory access failures propagate as IOException.
     * The predicate receives each regular file's path, resolved against the supplied folder.
     */
    public static List<Path> list(Path folder, Predicate<Path> matches) throws IOException {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(matches, "matches");
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(Files::isRegularFile)
                    .filter(matches)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
