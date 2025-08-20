package org.dcsim.runtime;

import org.dcsim.config.AppConfig;
import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.utils.TimeUtils;

import java.time.Duration;
import java.time.Instant;

public final class Runner {
    private Runner() {}

    public static void run(GridModel model, AppConfig cfg) {
        var ctl = cfg.getSimulationControl();
        double dt = ctl.getTickDuration(); // seconds

        Instant start = TimeUtils.parseInstant(ctl.getSimulationStart());
        Instant end   = TimeUtils.parseInstant(ctl.getSimulationEnd());

        DcElectricSolver solver = new DcElectricSolver(); // instance call

        Instant now = start;
        double simT = 0.0;
        int tick = 0;

        while (!now.isAfter(end)) {
            // TODO: update each TrainLoad with setRequestedComponents(mot, brake, aux)
            // TODO: stamp any per-tick state if your model requires it

            GridResult result = solver.solve(model, simT, tick);
            // TODO: export/log result if needed

            now  = now.plus(Duration.ofMillis(Math.round(dt * 1000.0)));
            simT += dt;
            tick++;
        }
    }
}
