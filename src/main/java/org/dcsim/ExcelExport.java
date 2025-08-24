package org.dcsim;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.utils.PositionUtils;
import org.dcsim.utils.TimeUtils;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

public class ExcelExport {

    public static void exportTrainData(String filename, Map<Integer, List<TrainState>> trainData, boolean useSI) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Train Data");

        Row header = sheet.createRow(0);
        int col = 0;

        for (Integer trainId : trainData.keySet()) {
            String prefix = "Train " + trainId;
            header.createCell(col++).setCellValue(prefix + " - Time [hh:mm:ss]");
            header.createCell(col++).setCellValue(prefix + " - Time [s]");
            header.createCell(col++).setCellValue(prefix + " - Position [m]");
            header.createCell(col++).setCellValue(prefix + " - Expected Position");
            header.createCell(col++).setCellValue(prefix + " - Expected Speed");
            header.createCell(col++).setCellValue(prefix + " - Expected Net Power");
            header.createCell(col++).setCellValue(prefix + " - Obtained Position");
            header.createCell(col++).setCellValue(prefix + " - Obtained Speed");
            header.createCell(col++).setCellValue(prefix + " - Obtained Net Power");
        }

        int maxRows = trainData.values().stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            col = 0;

            for (List<TrainState> states : trainData.values()) {
                if (i < states.size()) {
                    TrainState state = states.get(i);
                    row.createCell(col++).setCellValue(TimeUtils.format((int) state.time()));
                    row.createCell(col++).setCellValue(state.time());
                    row.createCell(col++).setCellValue(state.position());
                    row.createCell(col++).setCellValue(
                            PositionUtils.format(state.getLineId(), state.getPositionMeters())
                    );

                    row.createCell(col++).setCellValue(state.speed());
                    row.createCell(col++).setCellValue(state.power());
                    row.createCell(col++).setCellValue(state.position()); // placeholder for actual obtained position
                    row.createCell(col++).setCellValue(state.speed());    // placeholder for actual obtained speed
                    row.createCell(col++).setCellValue(state.power());    // placeholder for actual obtained power
                } else {
                    col += 9; // skip columns for trains that are finished
                }
            }
        }

        for (int i = 0; i < header.getPhysicalNumberOfCells(); i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream out = new FileOutputStream(filename)) {
            workbook.write(out);
        }
        workbook.close();
        System.out.println("Excel export completed to " + filename);
    }
}
