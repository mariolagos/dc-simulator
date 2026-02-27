package org.dcsim.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcsim.electric.GridModel;
import org.dcsim.math.FieldElement;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Deprecated
public final class ElectricalExcelExport {
    private ElectricalExcelExport() {}

    public static <F extends FieldElement<F>> void export(String filePath,
                                                          GridModel<F> model) throws IOException {

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("ElectricalExcelExport.export: filePath is null/empty");
        }
        if (model == null) {
            throw new IllegalArgumentException("ElectricalExcelExport.export: model is null");
        }

        // Normalize output filename: allow caller to pass with or without .xlsx
        String outPath = filePath.endsWith(".xlsx") ? filePath : (filePath + ".xlsx");

        File outFile = new File(outPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            // best-effort; if it fails, FileOutputStream will throw with a clear error anyway
            parent.mkdirs();
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet voltageSheet = workbook.createSheet("Node Voltages");
            Sheet currentSheet = workbook.createSheet("Device Currents");
            Sheet powerSheet = workbook.createSheet("Device Powers");

            int timesteps = model.getNumberOfTimesteps();

            // Node Voltages Sheet
            Row vHeader = voltageSheet.createRow(0);
            vHeader.createCell(0).setCellValue("Time [s]");
            int vCol = 1;
            for (Object o : model.getNodeIds()) {
                int nodeId = (o instanceof Integer) ? (Integer) o : Integer.parseInt(o.toString());
                vHeader.createCell(vCol++).setCellValue("Node " + nodeId + " [V]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = voltageSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (Object o : model.getNodeIds()) {
                    int nodeId = (o instanceof Integer) ? (Integer) o : Integer.parseInt(o.toString());
                    row.createCell(col++).setCellValue(model.getNodeVoltages().get(nodeId).get(t).asDouble());
                }
            }

            // Device Currents Sheet
            Row cHeader = currentSheet.createRow(0);
            cHeader.createCell(0).setCellValue("Time [s]");
            int cCol = 1;
            for (Object o : model.getDeviceIds()) {
                String deviceId = java.util.Objects.toString(o, null);
                cHeader.createCell(cCol++).setCellValue(deviceId + " [A]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = currentSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (Object o : model.getDeviceIds()) {
                    String deviceId = java.util.Objects.toString(o, null);
                    row.createCell(col++).setCellValue(
                            model.getUpdatedDeviceCurrents().get(deviceId).get(t).asDouble()
                    );
                }
            }

            // Device Powers Sheet
            Row pHeader = powerSheet.createRow(0);
            pHeader.createCell(0).setCellValue("Time [s]");
            int pCol = 1;
            for (Object o : model.getDeviceIds()) {
                String deviceId = java.util.Objects.toString(o, null);
                pHeader.createCell(pCol++).setCellValue(deviceId + " [W]");
            }
            for (int t = 0; t < timesteps; t++) {
                Row row = powerSheet.createRow(t + 1);
                row.createCell(0).setCellValue(t);
                int col = 1;
                for (Object o : model.getDeviceIds()) {
                    String deviceId = java.util.Objects.toString(o, null);
                    row.createCell(col++).setCellValue(
                            model.getUpdatedDevicePowers().get(deviceId).get(t).asDouble()
                    );
                }
            }

            try (FileOutputStream fileOut = new FileOutputStream(outFile)) {
                workbook.write(fileOut);
            }
        }
    }
}