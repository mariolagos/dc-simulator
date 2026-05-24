package org.supply.domain;

import org.supply.math.Real;

import java.util.Objects;

public class PowerInstallation {

    private final String installation_id;
    private final InstallationType installation_type;

    // Electrical parameters
    private final Real emf_v;
    private final Real internal_resistance_ohm;
    private final RectifierType rectifier_type;

    // Optional metadata
    private final String description;

    public PowerInstallation(
            String installation_id,
            InstallationType installation_type,
            Real emf_v,
            Real internal_resistance_ohm,
            RectifierType rectifier_type
    ) {
        this(installation_id, installation_type, emf_v, internal_resistance_ohm, rectifier_type, null);
    }

    public PowerInstallation(
            String installation_id,
            InstallationType installation_type,
            Real emf_v,
            Real internal_resistance_ohm,
            RectifierType rectifier_type,
            String description
    ) {
        this.installation_id = Objects.requireNonNull(installation_id);
        this.installation_type = Objects.requireNonNull(installation_type);
        this.emf_v = emf_v;
        this.internal_resistance_ohm = internal_resistance_ohm;
        this.rectifier_type = rectifier_type;
        this.description = description;
    }

    public String getInstallationId() {
        return installation_id;
    }

    public InstallationType getInstallationType() {
        return installation_type;
    }

    public Real getEmfV() {
        return emf_v;
    }

    public Real getInternalResistanceOhm() {
        return internal_resistance_ohm;
    }

    public RectifierType getRectifierType() {
        return rectifier_type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSubstation() {
        return installation_type == InstallationType.SUBSTATION;
    }

    public boolean isPoint() {
        return installation_type == InstallationType.POINT;
    }

    @Override
    public String toString() {
        return "PowerInstallation{" +
                "installation_id='" + installation_id + '\'' +
                ", installation_type=" + installation_type +
                ", emf_v=" + emf_v +
                ", internal_resistance_ohm=" + internal_resistance_ohm +
                ", rectifier_type='" + rectifier_type +
                ", description='" + description + '\'' +
                '}';
    }
}