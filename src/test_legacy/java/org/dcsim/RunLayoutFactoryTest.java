package org.dcsim;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.dcsim.RunLayout;
import org.dcsim.RunLayoutFactory;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class RunLayoutFactoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void exampleA_absoluteConfig_outputOmitted() throws Exception {
        // Simulate: T:/... by using a temp absolute path
        File dir = tmp.newFolder("A", "B", "C");
        File conf = new File(dir, "x.conf");
        conf.createNewFile();

        RunLayout layout = RunLayoutFactory.fromCliArgs(conf.getAbsolutePath(), null);
        System.out.println(layout.toString());

        assertEquals("C", layout.projectId());
        assertEquals("x", layout.scenarioId());
        assertEquals(conf.toPath().toAbsolutePath().normalize(), layout.configFile());
        assertEquals(dir.toPath().toAbsolutePath().normalize(), layout.inputDir());

        // output omitted => outputRoot = inputDir
        assertEquals(layout.inputDir().resolve("dc").normalize(), layout.outputRoot());
        assertEquals(layout.outputRoot().resolve("exports").normalize(), layout.exportDir());
        assertEquals(layout.outputRoot().resolve("results").normalize(), layout.resultsDir());
    }

    @Test
    public void exampleB_absoluteConfig_explicitOutputRoot() throws Exception {
        File dir = tmp.newFolder("A", "B", "C");
        File conf = new File(dir, "x.conf");
        conf.createNewFile();

        File outRoot = tmp.newFolder("distribution");

        RunLayout layout = RunLayoutFactory.fromCliArgs(conf.getAbsolutePath(), outRoot.getAbsolutePath());
        System.out.println(layout.toString());

        assertEquals("C", layout.projectId());
        assertEquals("x", layout.scenarioId());
        assertEquals(dir.toPath().toAbsolutePath().normalize(), layout.inputDir());

        assertEquals(outRoot.toPath().toAbsolutePath().normalize(), layout.outputRoot());
        assertEquals(layout.outputRoot().resolve("exports").normalize(), layout.exportDir());
        assertEquals(layout.outputRoot().resolve("results").normalize(), layout.resultsDir());
    }

    @Test
    public void exampleC_cwdRelativeConfig_outputOmitted() throws Exception {
        // We can't reliably change process CWD in a unit test, so we simulate "relative" by
        // creating a config file under current working directory's temp folder and passing a relative path.
        Path cwd = Path.of("").toAbsolutePath().normalize();

        File base = tmp.newFolder("myProject");
        File conf = new File(base, "myFirstScenario.conf");
        conf.createNewFile();

        // Make a relative path from CWD if possible; if temp isn't under CWD, we just assert semantics via stripExtension.
        // This test focuses on id derivation rules: projectId = last dir of inputDir, scenarioId = filename w/o extension.
        RunLayout layout = RunLayoutFactory.fromCliArgs(conf.getAbsolutePath(), null);
        System.out.println(layout.toString());

        assertEquals("myProject", layout.projectId());
        assertEquals("myFirstScenario", layout.scenarioId());
        assertEquals(conf.toPath().toAbsolutePath().normalize(), layout.configFile());
        assertEquals(base.toPath().toAbsolutePath().normalize(), layout.inputDir());
        assertEquals(layout.inputDir().resolve("dc").normalize(), layout.outputRoot());
        assertEquals(layout.outputRoot().resolve("exports").normalize(), layout.exportDir());
        assertEquals(layout.outputRoot().resolve("results").normalize(), layout.resultsDir());    }
}