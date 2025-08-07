package org.dcsim;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.util.List;

public class SimpleExcelExport {
    public static void export(String sheetName, List<PowerPoint> data, boolean useSI) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(sheetName);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Time [s]");
        header.createCell(1).setCellValue("Position");
        header.createCell(2).setCellValue("Position [m]");
        header.createCell(3).setCellValue(useSI ? "Speed [m/s]" : "Speed [km/h]");
        header.createCell(4).setCellValue(useSI ? "Power [W]" : "Power [kW]");

        int rowIdx = 1;
        for (PowerPoint p : data) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(p.time());
            row.createCell(1).setCellValue(p.position());
            row.createCell(2).setCellValue(PositionUtils.parseKmPlus(p.position())[1]);
            row.createCell(3).setCellValue(useSI ? p.speed() : p.speed() * 3.6);
            row.createCell(4).setCellValue(useSI ? p.power() : p.power() / 1000.0);
        }

        try (FileOutputStream out = new FileOutputStream("output/" + sheetName + ".xlsx")) {
            wb.write(out);
        }
        wb.close();
    }
}
