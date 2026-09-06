package org.supply.solver.build;

import org.supply.domain.RunSample;
import org.supply.math.Real;
import org.supply.solver.model.CalculationTrainPosition;
import org.supply.track.ModelCoordinate;
import org.supply.track.RwyCoordinate;
import org.supply.track.TrackTransformService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TrainPositionFactory {

    private final TrackTransformService trackTransform;

    public TrainPositionFactory(TrackTransformService trackTransform) {
        this.trackTransform =
                Objects.requireNonNull(
                        trackTransform,
                        "trackTransform"
                );
    }

    public List<CalculationTrainPosition> fromRunSamples(
            List<RunSample> samples
    ) {
        List<CalculationTrainPosition> out = new ArrayList<>();

        for (RunSample sample : samples) {
            int railwayPositionM =
                    Math.toIntExact(
                            Math.round(sample.positionM())
                    );

            RwyCoordinate railwayCoordinate =
                    new RwyCoordinate(
                            sample.sectionId(),
                            RwyCoordinate.formatKmShort(railwayPositionM),
                            railwayPositionM,
                            sample.trackId()
                    );

            ModelCoordinate modelCoordinate =
                    trackTransform.toModel(
                            sample.routeId(),
                            railwayCoordinate
                    );

            out.add(new CalculationTrainPosition(
                    sample.trainId(),
                    modelCoordinate.getSectionId(),
                    modelCoordinate.getTrackId(),
                    sample.routeId(),
                    modelCoordinate.getPositionM(),
                    Real.fromDouble(sample.pReqW())
            ));
        }

        return out;
    }
}