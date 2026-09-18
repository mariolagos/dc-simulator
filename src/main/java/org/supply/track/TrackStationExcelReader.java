package org.supply.track;

import org.apache.poi.ss.usermodel.*;
import java.nio.file.*;
import java.io.InputStream;
import java.util.*;

/** Station events from trackdata; unequal from/to names describe an interval. */
public final class TrackStationExcelReader {
    public List<Station> read(Path workbookPath,String sheetName) throws Exception {
        try(InputStream input=Files.newInputStream(workbookPath);
            Workbook workbook=WorkbookFactory.create(input)) {
            Sheet sheet=workbook.getSheet(sheetName);
            if(sheet==null) throw new IllegalArgumentException("Missing track route sheet: "+sheetName);
            return read(sheet);
        }
    }
    static List<Station> read(Sheet sheet) {
        Row header=sheet.getRow(sheet.getFirstRowNum());
        if(header==null) return List.of();
        DataFormatter formatter=new DataFormatter(Locale.ROOT);
        Map<String,Integer> columns=new LinkedHashMap<>();
        for(Cell cell:header) columns.put(formatter.formatCellValue(cell).trim(),cell.getColumnIndex());
        if(!columns.containsKey("dataType")||!columns.containsKey("fromStationAbbr")||
                !columns.containsKey("toStationAbbr")) return List.of();
        List<Station> result=new ArrayList<>();
        for(int i=header.getRowNum()+1;i<=sheet.getLastRowNum();i++) {
            Row row=sheet.getRow(i);if(row==null) continue;
            if(!text(row,columns,"dataType",formatter).equalsIgnoreCase("P")) continue;
            String from=text(row,columns,"fromStationAbbr",formatter);
            String to=text(row,columns,"toStationAbbr",formatter);
            // An unnamed P row is not enough to identify a station; never guess
            // which endpoint of an interval the row might represent.
            if(from.isBlank()||!from.equals(to)) continue;
            String section=text(row,columns,"trackSection",formatter);
            if(section.endsWith(".0")) section=section.substring(0,section.length()-2);
            if(section.isBlank()) throw invalid(sheet,i,"missing trackSection");
            int km=integer(row,columns,"bisKm",formatter,sheet,i);
            int metre=integer(row,columns,"bisMeter",formatter,sheet,i);
            if(metre<0||metre>999) throw invalid(sheet,i,"bisMeter must be 0..999");
            int position=Math.addExact(Math.multiplyExact(km,1000),metre);
            String track=text(row,columns,"trackNumber",formatter);
            RwyCoordinate coordinate=new RwyCoordinate(section,
                    km+"+"+String.format(Locale.ROOT,"%03d",metre),position,track.isBlank()?null:track);
            result.add(new Station(from,coordinate));
        }
        return merge(List.of(),result);
    }
    /** Deduplicate exact station events, not all occurrences of a station name. */
    static List<Station> merge(List<Station> configured,List<Station> excel) {
        record Key(String name,String section,int position,String track) { }
        Map<Key,Station> result=new LinkedHashMap<>();
        List<Station> all=new ArrayList<>(configured);all.addAll(excel);
        for(Station station:all) {
            RwyCoordinate c=station.getPosition();
            String track=c.getTrackId();
            result.putIfAbsent(new Key(station.getName(),c.getSectionId(),c.getPositionM(),
                    track==null||track.isBlank()?null:track),station);
        }
        return List.copyOf(result.values());
    }
    private static String text(Row row,Map<String,Integer> columns,String name,DataFormatter formatter) {
        Integer column=columns.get(name);
        return column==null?"":formatter.formatCellValue(row.getCell(column)).trim();
    }
    private static int integer(Row row,Map<String,Integer> columns,String name,DataFormatter formatter,Sheet sheet,int index) {
        String value=text(row,columns,name,formatter);
        try { return new java.math.BigDecimal(value).intValueExact(); }
        catch(NumberFormatException|ArithmeticException e) { throw invalid(sheet,index,"invalid "+name+": "+value); }
    }
    private static IllegalArgumentException invalid(Sheet sheet,int row,String message) {
        return new IllegalArgumentException("Station in sheet '"+sheet.getSheetName()+"', Excel row "+(row+1)+": "+message);
    }
}
