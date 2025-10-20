package org.dcsim.solver.impl;

import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;

public final class Behaviors {
    private Behaviors() {}

    public static SubstationBehavior forSubstation(SubstationData ss) {
        // Hook for future substation kinds
        return DiodeSubstationBehavior.INSTANCE;
    }

    public static TrainBehavior forTrain(TrainData tr) {
        // Hook for future train kinds
        return BasicTrainBehavior.INSTANCE;
    }
}
