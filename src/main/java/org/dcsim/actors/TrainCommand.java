package org.dcsim.actors;

import org.dcsim.power.PowerProfile;

public interface TrainCommand {
}

class AttachProfile implements TrainCommand {
    public final PowerProfile profile;
    public final double startTimeS;

    public AttachProfile(PowerProfile profile, double startTimeS) {
        this.profile = profile;
        this.startTimeS = startTimeS;
    }
}

class Tick implements TrainCommand {
    public final double simTimeS;

    public Tick(double simTimeS) {
        this.simTimeS = simTimeS;
    }
}

class Stop implements TrainCommand {
}
