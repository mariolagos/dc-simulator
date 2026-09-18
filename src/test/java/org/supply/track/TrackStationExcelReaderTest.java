package org.supply.track;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrackStationExcelReaderTest {
    private static Sheet sheet(Workbook workbook) {
        Sheet sheet=workbook.createSheet("F-M");
        Row header=sheet.createRow(0);
        String[] names={"dataType","fromStationAbbr","toStationAbbr","trackSection","bisKm","bisMeter"};
        for(int i=0;i<names.length;i++) header.createCell(i).setCellValue(names[i]);
        return sheet;
    }
    private static void row(Sheet sheet,int index,String type,String from,String to,int metre) {
        Row row=sheet.createRow(index);
        row.createCell(0).setCellValue(type);row.createCell(1).setCellValue(from);row.createCell(2).setCellValue(to);
        row.createCell(3).setCellValue(23);row.createCell(4).setCellValue(7);row.createCell(5).setCellValue(metre);
    }
    @Test public void readsNamedPEventsWithoutTrackNumberAndKeepsDistinctPositions() throws Exception {
        try(Workbook workbook=new XSSFWorkbook()) {
            Sheet sheet=sheet(workbook);
            row(sheet,1,"P","STA","STA",900);
            row(sheet,2,"P","STA","STA",900);
            row(sheet,3,"P","STA","STA",910);
            row(sheet,4,"P","STA","NEXT",920);
            row(sheet,5,"M","NEXT","NEXT",930);
            row(sheet,6,"P","","",940);
            List<Station> stations=TrackStationExcelReader.read(sheet);
            assertEquals(2,stations.size());assertEquals("STA",stations.get(0).getName());
            assertEquals(7900,stations.get(0).getPosition().getPositionM());
            assertEquals(7910,stations.get(1).getPosition().getPositionM());
            assertNull(stations.get(0).getPosition().getTrackId());
            assertEquals(2,TrackStationExcelReader.merge(stations,stations).size());
        }
    }
    @Test public void readsWorkbookAndTreatsMissingStationColumnsAsOptional() throws Exception {
        Path path=Files.createTempFile("station-events-",".xlsx");
        try {
            try(Workbook workbook=new XSSFWorkbook()) {
                Sheet sheet=sheet(workbook);row(sheet,1,"P","STA","STA",900);
                try(var output=Files.newOutputStream(path)) {workbook.write(output);}
            }
            assertEquals(1,new TrackStationExcelReader().read(path,"F-M").size());
            try(Workbook workbook=new XSSFWorkbook()) {
                Sheet empty=workbook.createSheet("minimal");empty.createRow(0).createCell(0).setCellValue("position [m]");
                assertTrue(TrackStationExcelReader.read(empty).isEmpty());
            }
        } finally {Files.deleteIfExists(path);}
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidCoordinateOnNamedStation() throws Exception {
        try(Workbook workbook=new XSSFWorkbook()) {
            Sheet sheet=sheet(workbook);row(sheet,1,"P","STA","STA",1000);
            TrackStationExcelReader.read(sheet);
        }
    }
}
