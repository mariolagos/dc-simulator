package org.supply.loader;

import com.typesafe.config.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.supply.app.GridPreprocessor;
import org.supply.app.SwitchGearConnectionReport;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class SwedishIdentifiersTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path excel(String station) throws Exception {
        Path p=Files.createTempFile(temp.getRoot().toPath(),"svenska-",".xlsx");
        try(var in=getClass().getResourceAsStream("/excel-grid/WE_grid_reusable_input.xlsx");var book=new XSSFWorkbook(in)) {
            for(var sheet:book)for(var row:sheet)for(var cell:row)if(cell.getCellType()==CellType.STRING) {
                String value=cell.getStringCellValue().replace("SS0",station).replace("SS1","261_ÖMT").replace("SS3","Ångström");
                cell.setCellValue(value);
            }
            // Branch references use the same Unicode name in Branches, Routes and StationPorts.
            for(var table:book.getSheet("Paths").getTables()) {
                int column=-1;var header=book.getSheet("Paths").getRow(table.getStartRowIndex());
                for(int c=table.getStartColIndex();c<=table.getEndColIndex();c++)if(header.getCell(c).getStringCellValue().equals("Branch"))column=c;
                if(column>=0)for(int r=table.getStartRowIndex()+1;r<=table.getEndRowIndex();r++) {
                    var cell=book.getSheet("Paths").getRow(r).getCell(column);
                    if(cell!=null&&cell.getCellType()==CellType.STRING&&cell.getStringCellValue().equals("101"))cell.setCellValue("FRÄ_B");
                }
            }
            try(var out=Files.newOutputStream(p)){book.write(out);}
        }
        return p;
    }
    @Test public void excelAndGeneratedConfKeepSwedishObjectAndBranchNames() throws Exception {
        Path input=excel("248_VÄT"),output=temp.getRoot().toPath().resolve("svenska_grid_generated.conf");
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});
        var config=ConfigFactory.parseFile(output.toFile()).resolve();var model=new GridModelLoader().load(config.getConfig("dcsim"));
        assertEquals(6,model.getInstallations().size());assertNotNull(model.getNode("248_VÄT.NEG"));assertNotNull(model.getNode("261_ÖMT.POS"));assertNotNull(model.getNode("Ångström.POS"));
        assertTrue(model.getLines().stream().anyMatch(l->l.getLineId().startsWith("B__FRÄ_B__")));
        String routes=Files.readString(output.resolveSibling("svenska_routes_generated.conf"),StandardCharsets.UTF_8);assertTrue(routes.contains("B__FRÄ_B__"));
        String bindings=Files.readString(output.resolveSibling("svenska_grid_generated_checks/terminal_bindings.csv"),StandardCharsets.UTF_8);assertTrue(bindings.contains("248_VÄT"));assertTrue(bindings.contains("Ångström"));
    }
    @Test public void confExpansionAndStationReportKeepSwedishNames() throws Exception {
        String text;try(var in=getClass().getResourceAsStream("/switchgear/double-track-gap.conf")){text=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        var input=ConfigFactory.parseString(text.replace("\r\n","\n").replace("SS0","248_VÄT").replace("type = \"standard\"","type = \"Strömtyp\"").replace("substationTypes = {\n        standard =", "substationTypes = {\n        Strömtyp ="));
        var grid=BranchGridGenerator.generate(input.getConfig("dcsim.grid"));
        var model=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",grid.root()));
        var reports=SwitchGearConnectionReport.render(model,grid);
        assertTrue(reports.keySet().toString(),reports.keySet().stream().anyMatch(n->n.startsWith("248_VÄT")));
        assertTrue(reports.values().stream().anyMatch(v->v.contains("248_VÄT")));
    }
    @Test public void swedishPortAndTerminalIdentifiersAreAccepted() {
        var terminals=Map.<String,Object>of("VÄST.N.F","Å","VÄST.S.F","Ä","VÄST.N.R","Ö","VÄST.S.R","å");
        var ids=SwitchGearCompiler.terminalIds(Map.of("terminals",terminals));assertEquals("Å",ids.get("VÄST.N.F"));assertEquals("Ö",ids.get("VÄST.N.R"));
    }
    @Test public void unsafeIdentifierCharactersAreStillRejected() throws Exception {
        for(String id:List.of("../VÄT","VÄT station","VÄT.1","VÄT?")) {
            Path path=excel(id);
            assertTrue(assertThrows(IllegalArgumentException.class,()->ExcelGridReader.read(path)).getMessage().contains("Invalid identifier"));
        }
    }
}
