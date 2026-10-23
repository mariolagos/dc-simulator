package org.supply.domain;

import java.util.List;

/** Internal wiring only. Source characteristics remain in PowerInstallation. */
public interface SwitchGear {
    String installationId();
    List<SwitchGearTerminal> terminals();
    List<CableConnection> cables();
}
