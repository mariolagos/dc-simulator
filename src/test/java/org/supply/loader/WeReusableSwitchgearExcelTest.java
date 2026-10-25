package org.supply.loader;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class WeReusableSwitchgearExcelTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path fixture(String name,Consumer<XSSFWorkbook> edit) throws Exception {
        Path p=Files.createTempFile(temp.getRoot().toPath(),"input-",".xlsx");
        try(var in=getClass().getResourceAsStream("/excel-grid/"+name)) {assertNotNull(in);Files.copy(in,p,StandardCopyOption.REPLACE_EXISTING);}
        try(var in=Files.newInputStream(p);var book=new XSSFWorkbook(in)) {edit.accept(book);try(var out=Files.newOutputStream(p)){book.write(out);}}
        return p;
    }
    private Path reusable(Consumer<XSSFWorkbook> edit) throws Exception{return fixture("WE_grid_reusable_input.xlsx",edit);}
    private static Set<Object> records(ExcelGridReader.Result result,String name){return new HashSet<>(result.grid().getAnyRefList(name));}
    @Test public void reusableWeIsElectricallyIdenticalToVerifiedExplicitWorkbook() throws Exception {
        var original=ExcelGridReader.read(fixture("WE_grid_input.xlsx",w->w.getSheet("Types").getRow(5).getCell(1).setCellValue(3000)));
        var reusable=ExcelGridReader.read(reusable(w->{}));
        for(String key:List.of("nodes","lines","ideal_connections","power_installations","installation_connections","switchgears"))
            assertEquals(key,records(original,key),records(reusable,key));
        for(String key:List.of("u_nominal_V","u_min_V","u_cutoff_V","u_max_V","i_train_max_A","p_train_max_W","p_allow_W","reference_node_id"))
            assertEquals(key,original.grid().getAnyRef(key),reusable.grid().getAnyRef(key));
        assertEquals(original.routes(),reusable.routes());assertEquals(new HashSet<>(original.bindings()),new HashSet<>(reusable.bindings()));
        assertEquals(56,reusable.bindings().size());
    }
    @Test public void rejectsDuplicateBranchMappingInsteadOfSilentlyLosingASide() throws Exception {
        var e=assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(reusable(w->w.getSheet("Paths").getRow(45).getCell(2).setCellValue("200"))));
        assertTrue(e.getMessage(),e.getMessage().contains("map every incident junction branch exactly once"));
    }
    @Test public void rejectsMissingPortAndUnknownStation() throws Exception {
        var e=assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(reusable(w->{var s=w.getSheet("Paths");for(int c=0;c<4;c++)s.getRow(45).getCell(c).setBlank();})));
        assertTrue(e.getMessage(),e.getMessage().contains("one StationPorts row"));
        var unknown=assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(reusable(w->w.getSheet("Paths").getRow(41).getCell(0).setCellValue("MISSING"))));
        assertTrue(unknown.getMessage(),unknown.getMessage().contains("Unknown substation"));
    }
    @Test public void junctionSideNamesAreUserDefinedNotJavaVariants() throws Exception {
        var original=ExcelGridReader.read(reusable(w->{}));
        var renamed=ExcelGridReader.read(reusable(w->{
            var types=w.getSheet("SwitchgearTypes");
            for(int i=11;i<=38;i++){var row=types.getRow(i);if(row.getCell(0).getStringCellValue().equals("Branching")&&row.getCell(2).getStringCellValue().equals("BEFORE"))row.getCell(2).setCellValue("MAIN");}
            var paths=w.getSheet("Paths");for(int i=41;i<=48;i++){var row=paths.getRow(i);if(Set.of("SS1","SS3").contains(row.getCell(0).getStringCellValue())&&row.getCell(1).getStringCellValue().equals("BEFORE"))row.getCell(1).setCellValue("MAIN");}
        }));
        assertEquals(new HashSet<>(original.bindings()),new HashSet<>(renamed.bindings()));assertEquals(original.routes(),renamed.routes());
    }
}
