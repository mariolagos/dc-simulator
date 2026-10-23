package org.supply.app;

import com.typesafe.config.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import static org.junit.Assert.*;

public class GridPreprocessorRoutesTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Path writeInput(Path folder) throws Exception {
        var input=folder.resolve("branches.conf");
        Files.writeString(input,ConfigFactory.parseResources("branch-routes/we.conf").resolve().root().render());
        return input;
    }
    @Test public void writesSeparateRoutesAndCanIncludeThemWithoutReplacingTraffic() throws Exception {
        Path folder=temporary.getRoot().toPath(),input=writeInput(folder),output=folder.resolve("WE_grid_generated.conf");
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});
        assertTrue(Files.exists(folder.resolve("WE_routes_generated.conf")));
        assertFalse(ConfigFactory.parseFile(output.toFile()).hasPath("dcsim.grid.routeDefinitions"));
        Path traffic=folder.resolve("traffic.conf");
        Files.writeString(traffic,"include required(\"WE_routes_generated.conf\")\ndcsim.traffic.timetable.trains=[]\ndcsim.traffic.templates={}");
        var loaded=ConfigFactory.parseFile(traffic.toFile()).resolve().getConfig("dcsim.traffic");
        assertEquals(4,loaded.getConfig("routes").root().size());
        assertTrue(loaded.hasPath("timetable.trains"));assertTrue(loaded.hasPath("templates"));
    }
    @Test public void existingRoutesFileIsProtectedBeforeWritingGrid() throws Exception {
        Path folder=temporary.getRoot().toPath(),input=writeInput(folder),output=folder.resolve("WE_grid_generated.conf");
        Path routes=folder.resolve("WE_routes_generated.conf");Files.writeString(routes,"keep");
        assertThrows(FileAlreadyExistsException.class,()->GridPreprocessor.main(new String[]{input.toString(),output.toString()}));
        assertFalse(Files.exists(output));assertEquals("keep",Files.readString(routes));
        GridPreprocessor.main(new String[]{input.toString(),output.toString(),"--overwrite"});
        assertTrue(ConfigFactory.parseFile(routes.toFile()).hasPath("dcsim.traffic.routes.WE"));
    }
    @Test public void nonstandardGridNameUsesDocumentedRouteFilename() throws Exception {
        Path folder=temporary.getRoot().toPath(),input=writeInput(folder),output=folder.resolve("network.conf");
        GridPreprocessor.main(new String[]{input.toString(),output.toString()});
        assertTrue(Files.exists(folder.resolve("network_routes.conf")));
    }
}
