// src/test/java/org/dcsim/unit/motor_throttled_near_zero_by_vmin.java
package org.dcsim.unit;

import org.junit.Test;
import static org.junit.Assert.*;

import org.dcsim.grid.DummyPort;
import org.dcsim.train.SimpleTrainController;
import org.dcsim.train.TrainDevice;

public class motor_throttled_near_zero_by_vmin {

    @Test
    public void traction_is_scaled_down_close_to_vmin() {
        double vmin = 600.0, vmax = 900.0;
        DummyPort port = new DummyPort(610.0); // nära vmin
        SimpleTrainController ctrl = new SimpleTrainController(0.10); // 10% throttle-band
        TrainDevice train = new TrainDevice(port, ctrl, vmin, vmax);

        double requested = 100_000.0; // 100 kW traction
        train.step(requested, port.getVoltage());

        double applied = port.getRequestedPower();
        assertTrue("Should throttle traction near vmin", applied > 0.0 && applied < requested);
    }
}
