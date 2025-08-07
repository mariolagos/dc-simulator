package org.dcsim.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.electric.GridModel;

import java.io.FileOutputStream;
import java.io.IOException;

public class ElectricalExcelExport {
    public static void export(String filePath, GridModel model) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet voltageSheet = workbook.createSheet("Node Voltages");
            Sheet currentSheet = workbook.createSheet("Device Currents");
            Sheet powerSheet = workbook.createSheet("Device Powers");

            int timesteps = model.getNumberOfTimesteps();

            // Node Voltages Sheet
            Row vHeader = voltageSheet.createRow(0);
            vHeader.createCell(0).setCellValue("Time [s]");
            int vCol = 1;
            for (Integer nodeId : model.getNodeIds()) {
                vHeader.createCell(vCol++).setCellValue("Node " + nodeId + " [V]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = voltageSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (Integer nodeId : model.getNodeIds()) {
                    row.createCell(col++).setCellValue(model.getNodeVoltages().get(nodeId).get(t).asDouble());
                }
            }

            // Device Currents Sheet
            Row cHeader = currentSheet.createRow(0);
            cHeader.createCell(0).setCellValue("Time [s]");
            int cCol = 1;
            for (String deviceId : model.getDeviceIds()) {
                cHeader.createCell(cCol++).setCellValue("" + deviceId + " [A]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = currentSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (String deviceId : model.getDeviceIds()) {
                    row.createCell(col++).setCellValue(model.getUpdatedDeviceCurrents().get(deviceId).get(t).asDouble());
                }
            }

            // Device Powers Sheet
            Row pHeader = powerSheet.createRow(0);
            pHeader.createCell(0).setCellValue("Time [s]");
            int pCol = 1;
            for (String deviceId : model.getDeviceIds()) {
                pHeader.createCell(pCol++).setCellValue("" + deviceId + " [W]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = powerSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (String deviceId : model.getDeviceIds()) {
                    row.createCell(col++).setCellValue(model.getUpdatedDevicePowers().get(deviceId).get(t).asDouble());
                }
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath + ".xlsx")) {
                workbook.write(fileOut);
            }
        }
    }
}