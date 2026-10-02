package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.SystemParameters;

public final class SystemParametersFactory {
    public SystemParameters build(Config dcsim) {
        Config grid = dcsim.getConfig("grid");

        double uMinV = grid.getDouble("u_min_V");
        double uCutoffV = grid.getDouble("u_cutoff_V");
        double uMaxV = grid.getDouble("u_max_V");

        if (!(uMinV < uCutoffV && uCutoffV < uMaxV)) {
            throw new IllegalArgumentException(
                    "Expected u_min_V < u_cutoff_V < u_max_V"
                            + "; got u_min_V=" + uMinV
                            + ", u_cutoff_V=" + uCutoffV
                            + ", u_max_V=" + uMaxV
            );
        }

        return new SystemParameters(
                grid.getDouble("u_nominal_V"),
                uMinV,
                uCutoffV,
                uMaxV,
                grid.getDouble("i_train_max_A"),
                grid.getDouble("p_train_max_W"),
                grid.getDouble("p_allow_W"),
                grid.hasPath("reference_node_id")
                        ? grid.getString("reference_node_id")
                        : "R1"
        );
    }
}