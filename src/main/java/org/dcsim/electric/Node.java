package org.dcsim.electric;

import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;
import org.dcsim.utils.PositionUtils;

import java.util.List;
import java.util.Objects;

/**
 * A Node represents a point in the electric network with a voltage.
 * Devices are connected to nodes and inject/extract current based on voltage.
 */
@Deprecated
public class Node<T extends FieldElement<T>> {
    private String description; // Optional descriptive text

    private final int internal_id;
    private String node_id;
    private final int section_id;
    private Real voltage;
    private final String position;

    // v0.8 additions (MFE)
    private int trackId;           // -1 if not on a track
    private int position_m;      // numeric coordinate [m] along track

    // ?? Main constructor (the only "real" one)
    public Node(
            int internal_id,
            String node_id,
            int section_id,
            int position_m,
            String position
    ) {
        this.internal_id = internal_id;
        this.node_id = Objects.requireNonNull(node_id);
        this.section_id = section_id;
        this.position_m = position_m;
        this.position = position; // may be null in future if removed
        this.voltage = Real.ZERO;
    }

    @Deprecated
    public Node(int internal_id, Real voltage, String position) {
        this(
                internal_id,
                "legacy_" + internal_id,
                legacySectionId(position),
                legacyPositionM(position),
                position
        );
    }

    @Deprecated
    public Node(int internal_id, Real voltage, String position, String description) {
        this(
                internal_id,
                description != null ? description : "legacy_" + internal_id,
                legacySectionId(position),
                legacyPositionM(position),
                position
        );
    }

    private static int legacySectionId(String position) {
        try {
            return PositionUtils.parseFlexible(position)[0];
        } catch (Exception e) {
            return 0;
        }
    }

    private static int legacyPositionM(String position) {
        try {
            return PositionUtils.parseFlexible(position)[1];
        } catch (Exception e) {
            return 0;
        }
    }
    @Deprecated
    public void setName(String name) {
        this.node_id = name;
    }

    @Deprecated
    public String getName() {
        return this.node_id;
    }

    public String getNode_id() {
        return node_id;
    }

    public String getNameOrDefault() {
        return node_id != null && !node_id.isBlank() ? node_id : "N" + internal_id;
    }

    public int get_internal_id() {
        return internal_id;
    }

    public Real getVoltage() {
        return voltage;
    }

    public String getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    public int getPositionM() {
        return position_m;
    }

    public void setPositionM(int positionM) {
        this.position_m = positionM;
    }

    public void setVoltage(Real voltage) {
        this.voltage = voltage;
    }


}
