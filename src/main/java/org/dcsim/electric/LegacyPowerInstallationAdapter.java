package org.dcsim.electric;

import org.dcsim.math.Real;

public final class LegacyPowerInstallationAdapter {

    private LegacyPowerInstallationAdapter() {
    }

    public static PowerInstallation fromLegacy(
            String installationId,
            InstallationCategory installationCategory,
            Real emfV,
            Real internalResistanceOhm,
            String rectifierType,
            String description
    ) {
        return new PowerInstallation(
                installationId,
                installationCategory,
                emfV,
                internalResistanceOhm,
                RectifierType.valueOf(rectifierType.toUpperCase()),
                description
        );
    }
}