package org.dcsim.electric;

public final class NodeMeta {

    // existing fields
    private final int index = 0;
    private final String id = "";
    // ...

    // v0.8 additions (MFE):
    private String trackId;                       // null if not applicable
    private Double positionM;                     // null if not applicable

    // --- getters/setters ---

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public Double getPositionM() {
        return positionM;
    }

    public void setPositionM(Double positionM) {
        this.positionM = positionM;
    }

    // existing methods unchanged...
}
