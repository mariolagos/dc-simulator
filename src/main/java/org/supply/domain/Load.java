package org.supply.domain;

import org.supply.track.RwyCoordinate;

public interface Load {

    String id();
    RwyCoordinate position();

    record FixedLoad(
            String id,
            RwyCoordinate position,
            double powerW
    ) implements Load {
    }
}