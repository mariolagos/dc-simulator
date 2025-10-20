package org.dcsim.solver.profile;

public final class ProfilePlayer {
    private final TrainProfile profile;
    public ProfilePlayer(TrainProfile profile) { this.profile = profile; }
    public double reqW(double tSec) { return profile.powerAt(tSec); }
}
