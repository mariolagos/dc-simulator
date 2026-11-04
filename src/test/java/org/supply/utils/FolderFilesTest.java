package org.supply.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class FolderFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void matchesOnlyDirectFilesAndSortsByFilename() throws Exception {
        Path folder = temporary.newFolder("ÅÄÖ data").toPath();

        Path second = Files.createFile(folder.resolve("WE_timetable_W-CW.xlsx"));
        Path first = Files.createFile(folder.resolve("WE_timetable_C-CE.xlsx"));

        Files.createFile(folder.resolve("EW_timetable_E-CE.xlsx"));
        Files.createFile(folder.resolve("WE_timetable_W-CW.xlsx.bak"));
        Files.createFile(folder.resolve("WE_timetable_case_check.XLSX"));
        Files.createDirectory(folder.resolve("WE_timetable_directory.xlsx"));

        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.createFile(nested.resolve("WE_timetable_hidden.xlsx"));

        assertEquals(
                List.of(first, second),
                FolderFiles.list(folder, "WE_timetable_", ".xlsx")
        );
    }
    @Test public void supportsCustomConditionsAndEmptyResults() throws Exception {
        Path folder = temporary.newFolder().toPath();
        Path selected = Files.writeString(folder.resolve("data.csv"), "sample");
        Files.createFile(folder.resolve("other.txt"));
        assertEquals(List.of(selected), FolderFiles.list(folder,
                path -> path.getFileName().toString().equals("data.csv")));
        assertTrue(FolderFiles.list(folder, "missing", ".xlsx").isEmpty());
    }

    @Test public void rejectsMissingDirectoriesAndFileAsDirectory() throws Exception {
        Path file = temporary.newFile().toPath();
        assertThrows(IOException.class, () -> FolderFiles.list(file, "", ""));
        assertThrows(IOException.class, () -> FolderFiles.list(file.resolveSibling("missing"), "", ""));
    }
}
