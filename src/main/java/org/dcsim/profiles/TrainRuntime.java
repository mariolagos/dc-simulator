// org.dcsim.profiles/TrainRuntime.java (updated signature)
package org.dcsim.profiles;

import org.dcsim.electric.Topology;
import org.dcsim.electric.TrainLoad;
import org.dcsim.power.ProfileSample;
import org.dcsim.power.ProfileSampler;
import org.dcsim.runtime.DwellDetector;

public final class TrainRuntime {
    private final String trainId;
    private final ProfileSampler sampler;
    private final DwellDetector dwellDetector;
    private final Topology topology;
    private final double auxiliaryPowerKW;
    private final boolean motAndAuxTogether;

    public TrainRuntime(String trainId,
                        ProfileSampler sampler,
                        DwellDetector dwellDetector,
                        Topology topology,
                        double auxiliaryPowerKW,
                        boolean motoringAndAuxiliariesInSameModel) {
        this.trainId = trainId;
        this.sampler = sampler;
        this.dwellDetector = dwellDetector;
        this.topology = topology;
        this.auxiliaryPowerKW = Math.max(0.0, auxiliaryPowerKW);
        this.motAndAuxTogether = motoringAndAuxiliariesInSameModel;
    }

    public void updateAt(TrainLoad load, double secondsSinceStart) {
        ProfileSample s = sampler.sampleAt(secondsSinceStart, 0.);

        double aux = dwellDetector.isDwellingAt(secondsSinceStart, /*start*/ java.time.Instant.EPOCH)
                ? auxiliaryPowerKW : 0.0;

        double motKW = s.motoringKW;
        double brkKW = s.brakingKW;
        double auxKW = aux;

        if (motAndAuxTogether) {
            // If profiles already model auxiliaries, don't double-count
            auxKW = 0.0;
        }
        load.setRequestedComponents(motKW, brkKW, auxKW);

        // (Position→node mapping is left as static device wiring for now; or add setters on TrainLoad)
    }
}
