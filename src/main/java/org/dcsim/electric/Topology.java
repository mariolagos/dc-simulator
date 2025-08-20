package org.dcsim.electric;

/** Line-aware, infrastructure-only topology lookup. */
public interface Topology {
    /** Nearest infrastructure node id for the parsed position [line, km, m]. */
    int nearestInfraNodeId(int[] parsedPosition);

    /** Bracketing infrastructure nodes [leftId, rightId] for the parsed position on the same line. */
    int[] bracketInfraNodeIds(int[] parsedPosition);
}
