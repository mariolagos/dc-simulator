package org.dcsim.power;

public interface ProfileSampler {
    /** Returnerar ett prov vid tid t (sek), auxiliaryKW adderas alltid till begärd effekt. */
    ProfileSample sampleAt(double tSec, double auxiliaryKW);
}
