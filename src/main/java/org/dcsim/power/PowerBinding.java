package org.dcsim.power;

import com.typesafe.config.Config;
import org.dcsim.traffic.TrainTemplate;

public final class PowerBinding {
    private PowerBinding() {}

    public static PowerBinding fromConfig(Config powerProfiles) {
        // Parse auxiliaryPower, template folders, legs → files
        return new PowerBinding();
    }

    public ProfileSampler buildSamplerForTemplate(TrainTemplate tpl) {
        // Stitch leg samplers (excel files) into a single sampler that can cover the whole trip
        return (tSec, auxKW) -> new ProfileSample(tSec, 0.0, 0.0, 0.0, auxKW);    }
}
