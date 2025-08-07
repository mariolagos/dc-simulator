package org.dcsim.power;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.PowerPoint;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loads and concatenates all Excel files in a directory to produce a full power profile.
 */
public class T1ProfileLoader {

    public static List<PowerPoint> loadProfileFromDirectory(File folder) throws Exception {
        if (!folder.isDirectory()) throw new IllegalArgumentException("Expected directory: " + folder);

        List<PowerPoint> all = new ArrayList<>();
        for (File file : folder.listFiles((dir, name) -> name.endsWith(".xlsx"))) {
            all.addAll(readPointsFromExcel(file));
        }

        // Sort by time to ensure correct order
        all.sort(Comparator.comparingDouble(PowerPoint::time));
        return all;
    }

    private static List<PowerPoint> readPointsFromExcel(File file) throws Exception {
        List<PowerPoint> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file); Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                double time = row.getCell(0).getNumericCellValue();
                String position = row.getCell(1).getStringCellValue();
                double speed = row.getCell(2).getNumericCellValue();
                double power = row.getCell(3).getNumericCellValue();
                double voltage = row.getCell(4).getNumericCellValue();
                double current = row.getCell(5).getNumericCellValue();
                list.add(new PowerPoint(time, position, speed, power, voltage, current));
            }
        }
        return list;
    }
}
