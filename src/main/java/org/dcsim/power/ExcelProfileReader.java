package org.dcsim.power;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.PowerPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelProfileReader {
    public static List<PowerPoint> read(File directory) {
        List<PowerPoint> points = new ArrayList<>();

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xlsx"));
        if (files == null) return points;

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file);
                 Workbook workbook = new XSSFWorkbook(fis)) {

                Sheet sheet = workbook.getSheetAt(0);
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    double time = row.getCell(0).getNumericCellValue();
                    String position = row.getCell(1).getStringCellValue();
                    double speed = row.getCell(2).getNumericCellValue();
                    double power = row.getCell(3).getNumericCellValue();

                    points.add(new PowerPoint(time, position, speed, power, 0.0, 0.0));
                }
            } catch (IOException e) {
                System.err.println("Failed to read file: " + file.getName());
                e.printStackTrace();
            }
        }

        return points;
    }
}
