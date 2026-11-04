package org.supply.app;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import static org.junit.Assert.*;

public class TrackdataCheckTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Path input() throws Exception {
        Path file=temporary.newFile("input.xlsx").toPath();
        try(var b=new XSSFWorkbook()) {
            var s=b.createSheet("Route");var h=s.createRow(0);
            String[] names={"position [m]","trackSection","trackNumber","bisKm","bisMeter"};
            for(int i=0;i<names.length;i++)h.createCell(i).setCellValue(names[i]);
            for(int i=0;i<2;i++){var r=s.createRow(i+1);r.createCell(0).setCellValue(i*1010);
                r.createCell(1).setCellValue("X");r.createCell(2).setCellValue("N");r.createCell(3).setCellValue(i);r.createCell(4).setCellValue(0);}
            try(var out=Files.newOutputStream(file)){b.write(out);}
        }
        return file;
    }
    @Test public void writesReportForWarningsWithoutGridAndPreservesWorkbook() throws Exception {
        Path file=input(),report=temporary.getRoot().toPath().resolve("checks/result.txt");byte[] before=Files.readAllBytes(file);
        TrackdataCheck.main(new String[]{file.toString(),"--output",report.toString(),"--sheet","Route"});
        String text=Files.readString(report);assertTrue(text.contains("Status: PASS"));assertTrue(text.contains("1010.0 physical m"));
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test public void writesFailureReportBeforeFailing() throws Exception {
        Path file=input(),report=temporary.getRoot().toPath().resolve("failed.txt");
        assertThrows(IllegalArgumentException.class,()->TrackdataCheck.main(new String[]{file.toString(),"--output",report.toString(),"--sheet","missing"}));
        assertTrue(Files.readString(report).contains("Status: FAIL"));
    }
    @Test public void rejectsOverwritingInputWorkbook() throws Exception {
        Path file=input();byte[] original=Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class,()->TrackdataCheck.main(new String[]{file.toString(),"--output",file.toString()}));
        assertArrayEquals(original,Files.readAllBytes(file));
    }
}
