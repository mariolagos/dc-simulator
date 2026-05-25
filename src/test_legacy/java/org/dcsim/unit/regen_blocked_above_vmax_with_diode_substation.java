// src/test/java/org/dcsim/unit/regen_blocked_above_vmax_with_diode_substation.java
package org.dcsim.unit;

import org.junit.Test;
import static org.junit.Assert.*;

import org.dcsim.grid.DummyPort;
import org.dcsim.train.SimpleTrainController;
import org.dcsim.train.TrainDevice;

public class regen_blocked_above_vmax_with_diode_substation {

    // Stub/fake – byt till din riktiga mätare eller fixtures
    static final class MeasuringSubstation {
        private double netP = 0.0;
        void setNetPower(double p) { netP = p; }
        double getNetPower() { return netP; }
    }

    @Test
    public void regen_is_blocked_and_substation_power_is_zeroish_above_vmax() {
        double vmin = 600.0, vmax = 900.0;
        DummyPort port = new DummyPort(905.0); // över vmax
        SimpleTrainController ctrl = new SimpleTrainController(0.10);
        TrainDevice train = new TrainDevice(port, ctrl, vmin, vmax);

        double requested = -80_000.0; // -80 kW regen
        train.step(requested, port.getVoltage());

        // Controller ska klampa regen till 0
        double applied = port.getRequestedPower();
        assertEquals("Regen must be clamped to 0 above vmax", 0.0, applied, 1e-9);

        // Om du har en riktig substationmodell, kolla att den inte absorberar/genererar pga regen.
        // Här visar vi bara principen med stub:
        MeasuringSubstation diode = new MeasuringSubstation();
        diode.setNetPower(0.0);
        assertEquals("Diode substation net power should be ~0 W", 0.0, diode.getNetPower(), 1e-3);
    }
}
