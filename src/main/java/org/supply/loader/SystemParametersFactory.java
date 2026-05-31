package org.supply.loader;

import com.typesafe.config.Config;
import org.supply.domain.SystemParameters;

public final class SystemParametersFactory {
    public SystemParameters build(Config dcsim) {
        Config grid = dcsim.getConfig("grid");

        return new SystemParameters(
                grid.getDouble("u_nominal_V"),
                grid.getDouble("u_min1_V"),
                grid.getDouble("u_min2_V"),
                grid.getDouble("u_max_V"),
                grid.getDouble("i_train_max_A")
        );
    }
}