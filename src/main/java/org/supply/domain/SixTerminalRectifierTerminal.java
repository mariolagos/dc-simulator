package org.supply.domain;

public enum SixTerminalRectifierTerminal {
    NORTH_POWER_LEFT(ConnectionType.FEEDING),
    NORTH_POWER_RIGHT(ConnectionType.FEEDING),
    SOUTH_POWER_LEFT(ConnectionType.FEEDING),
    SOUTH_POWER_RIGHT(ConnectionType.FEEDING),
    NORTH_RETURN(ConnectionType.RETURN),
    SOUTH_RETURN(ConnectionType.RETURN);

    private final ConnectionType connectionType;

    SixTerminalRectifierTerminal(
            ConnectionType connectionType
    ) {
        this.connectionType = connectionType;
    }

    public ConnectionType connectionType() {
        return connectionType;
    }
}