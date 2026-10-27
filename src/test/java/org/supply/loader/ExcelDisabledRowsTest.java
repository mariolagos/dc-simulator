package org.supply.loader;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ExcelDisabledRowsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path fixture() throws Exception {
        Path path=temp.newFile("input.xlsx").toPath();
        try(var in=getClass().getResourceAsStream("/excel-grid/WE_grid_reusable_input.xlsx")){Files.copy(in,path,StandardCopyOption.REPLACE_EXISTING);}
        return path;
    }
    @Test public void redFillInFirstColumnExcludesEveryRowOfAWholeRoute() throws Exception {
        Path path=fixture();
        try(var in=Files.newInputStream(path);var w=new XSSFWorkbook(in)) {
            var sheet=w.getSheet("Paths");
            var red=w.createCellStyle();red.setFillForegroundColor(IndexedColors.RED.getIndex());red.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for(int i=24;i<=35;i++) {var row=sheet.getRow(i);if(row!=null && row.getCell(0)!=null && row.getCell(0).toString().equals("EW")) row.getCell(0).setCellStyle(red);}
            try(var out=Files.newOutputStream(path)){w.write(out);}
        }
        var r=ExcelGridReader.read(path);
        assertFalse(r.routes().containsKey("EW"));
        assertEquals(3,r.routes().size());assertTrue(r.routes().containsKey("WE"));
        assertTrue(r.routes().containsKey("SeNw"));
    }
    @Test public void activeInvalidReferenceStillFails() throws Exception {
        Path path=fixture();
        try(var in=Files.newInputStream(path);var w=new XSSFWorkbook(in)) {
            var sheet=w.getSheet("Paths");sheet.getRow(24).getCell(3).setCellValue("UNKNOWN_BRANCH");
            var red=w.createCellStyle();red.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255,0,0},new DefaultIndexedColorMap()));red.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sheet.getRow(24).getCell(3).setCellStyle(red);
            try(var out=Files.newOutputStream(path)){w.write(out);}
        }
        assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(path));
    }
}
