package org.dcsim.solver.scenario;

import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.profile.ProfilePlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DcScenario {
    public final DcNet net;
    public final double dtSec;
    public final Map<String, ProfilePlayer> playersByTrainId;

    public DcScenario(DcNet net, double dtSec, Map<String, ProfilePlayer> playersByTrainId) {
        this.net = net;
        this.dtSec = dtSec;
        this.playersByTrainId = new LinkedHashMap<>(playersByTrainId);
    }
}
