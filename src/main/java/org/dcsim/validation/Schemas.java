package org.dcsim.validation;

import java.util.List;

public final class Schemas {
    private Schemas() {
    }

    // Minimal run.csv schema for v0.9 (adjust columns to match MATLAB needs)
    public static final CsvSchema RUN_V0_9 = new CsvSchema("run.csv", List.of(
            "time_s", "train_id", "section", "track", "position_m", "P_req_W"
    ));
}
