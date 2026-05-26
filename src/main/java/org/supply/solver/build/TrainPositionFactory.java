package org.supply.solver.build;

import org.supply.domain.RunSample;
import org.supply.math.Real;
import org.supply.solver.model.CalculationTrainPosition;

import java.util.ArrayList;
import java.util.List;

public final class TrainPositionFactory {

    public List<CalculationTrainPosition> fromRunSamples(List<RunSample> samples) {
        List<CalculationTrainPosition> out = new ArrayList<>();

        for (RunSample sample : samples) {
            out.add(new CalculationTrainPosition(
                    sample.trainId(),
                    sample.sectionId(),
                    sample.trackId(),
                    sample.positionM(),
                    Real.fromDouble(sample.pReqW())
            ));
        }

        return out;
    }
}