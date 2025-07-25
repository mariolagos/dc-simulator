package org.dcsim;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExcelExport {

    public static void exportTrainData(String filename, Map<Integer, List<Main.TrainState>> trainData, boolean useSI) throws IOException {
        Workbook workbook = new XSSFWorkbook();

        for (Map.Entry<Integer, List<Main.TrainState>> entry : trainData.entrySet()) {
            int trainId = entry.getKey();
            List<Main.TrainState> states = entry.getValue();
            Sheet sheet = workbook.createSheet("Train_" + trainId);

            Row header = sheet.createRow(0);
            String prefix = "Train " + trainId + " - ";
            header.createCell(0).setCellValue(prefix + "Time [hh:mm:ss]");
            header.createCell(1).setCellValue(prefix + "Time [s]");
            header.createCell(2).setCellValue(prefix + "Position [m]");
            header.createCell(3).setCellValue(prefix + "Expected Position");
            header.createCell(4).setCellValue(prefix + "Expected Speed");
            header.createCell(5).setCellValue(prefix + "Expected Net Power");
            header.createCell(6).setCellValue(prefix + "Obtained Position");
            header.createCell(7).setCellValue(prefix + "Obtained Speed");
            header.createCell(8).setCellValue(prefix + "Obtained Net Power");
            int rowIdx = 1;

            for (Main.TrainState state : states) {
                Row row = sheet.createRow(rowIdx++);
                double speed = useSI ? state.speed() : state.speed() * 3.6;
                double power = useSI ? state.power() : state.power() / 1000.0;
                String posLabel = PositionUtils.format(state.position());

                row.createCell(0).setCellValue(TimeUtils.format((int) state.time()));
                row.createCell(1).setCellValue(state.time());
                row.createCell(2).setCellValue(state.position());
                row.createCell(3).setCellValue(posLabel);
                row.createCell(4).setCellValue(speed);
                row.createCell(5).setCellValue(power);
                row.createCell(6).setCellValue(posLabel);
                row.createCell(7).setCellValue(speed);
                row.createCell(8).setCellValue(power);
            }
        }

        try (FileOutputStream out = new FileOutputStream(filename)) {
            workbook.write(out);
        }

        workbook.close();
        System.out.println("Excel export completed to " + filename);
    }
}
