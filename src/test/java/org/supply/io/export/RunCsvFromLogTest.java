package org.supply.io.export;

import org.junit.Test;
import org.supply.domain.RunColumn;
import org.supply.domain.RunLogFormat;
import org.supply.domain.RunSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public final class RunCsvFromLogTest {

    @Test
    public void integratesSpeedAndConvertsKilowatts() throws Exception {
        Path log = Files.createTempFile("measured-run", ".csv");
        Files.writeString(log, """
                Date;Speed;Power;Voltage;Current
                2026-09-09 02:16:21.000;0;20;700;28.5
                2026-09-09 02:16:21.500;2;21;701;30
                2026-09-09 02:16:22.000;2;22;702;31
                """);

        RunReadResult result = new RunCsvFromLog().read(RunSource.log(
                log,
                new RunLogFormat(
                        ';',
                        new RunColumn("Date", "", "yyyy-MM-dd HH:mm:ss.SSS"),
                        null,
                        new RunColumn("Speed", "m/s", ""),
                        new RunColumn("Power", "kW", ""),
                        new RunColumn("Voltage", "V", ""),
                        new RunColumn("Current", "A", ""),
                        true
                )
        ));

        assertEquals(3, result.samples().size());
        assertEquals(0.0, result.samples().get(0).positionM(), 1e-9);
        assertEquals(0.5, result.samples().get(1).positionM(), 1e-9);
        assertEquals(1.5, result.samples().get(2).positionM(), 1e-9);
        assertEquals(21_000.0, result.samples().get(1).powerW(), 1e-9);
    }

    @Test
    public void derivesPowerFromVoltageAndCurrentAndNormalizesSign()
            throws Exception {
        Path log = Files.createTempFile("measured-run-ui", ".csv");
        Files.writeString(log, """
                t,v,u,i
                0,0,700,10
                1,1,710,-5
                """);

        RunReadResult result = new RunCsvFromLog().read(RunSource.log(
                log,
                new RunLogFormat(
                        ',',
                        new RunColumn("t", "s", ""),
                        null,
                        new RunColumn("v", "m/s", ""),
                        null,
                        new RunColumn("u", "V", ""),
                        new RunColumn("i", "A", ""),
                        true
                )
        ));

        assertEquals(7_000.0, result.samples().get(0).powerW(), 1e-9);
        assertEquals(-3_550.0, result.samples().get(1).powerW(), 1e-9);
    }
}
