package org.dcsim.electric;

import org.dcsim.math.Real;
import org.supply.domain.InstallationType;
import org.supply.domain.PowerInstallation;
import org.supply.domain.RectifierType;

public final class LegacyPowerInstallationAdapter {

    private LegacyPowerInstallationAdapter() {
    }

    public static PowerInstallation fromLegacy(
            String installationId,
            InstallationType installationType,
            Real emfV,
            Real internalResistanceOhm,
            String rectifierType,
            String description
    ) {
        return new PowerInstallation(
                installationId,
                installationType,
                emfV,
                internalResistanceOhm,
                RectifierType.valueOf(rectifierType.toUpperCase()),
                description
        );
    }
}