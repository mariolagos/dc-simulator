package org.supply.domain;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SixTerminalRectifierInstallationTest {

    @Test
    public void acceptsAllSixTerminalConnections() {
        SixTerminalRectifierInstallation installation =
                installation(validTerminalNodeIds());

        assertEquals(
                6,
                installation.terminalNodeIds().size()
        );
        assertEquals(
                "F_N_LEFT",
                installation.terminalNodeIds().get(
                        SixTerminalRectifierTerminal.NORTH_POWER_LEFT
                )
        );
    }

    @Test
    public void rejectsMissingTerminalConnection() {
        Map<SixTerminalRectifierTerminal, String> terminalNodeIds =
                validTerminalNodeIds();

        terminalNodeIds.remove(
                SixTerminalRectifierTerminal.SOUTH_POWER_RIGHT
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> installation(terminalNodeIds)
                );

        assertTrue(
                exception.getMessage().contains(
                        "Missing terminal SOUTH_POWER_RIGHT"
                )
        );
        assertTrue(
                exception.getMessage().contains("SS0")
        );
    }

    @Test
    public void copiesTerminalConnections() {
        Map<SixTerminalRectifierTerminal, String> terminalNodeIds =
                validTerminalNodeIds();

        SixTerminalRectifierInstallation installation =
                installation(terminalNodeIds);

        terminalNodeIds.put(
                SixTerminalRectifierTerminal.NORTH_POWER_LEFT,
                "CHANGED"
        );

        assertEquals(
                "F_N_LEFT",
                installation.terminalNodeIds().get(
                        SixTerminalRectifierTerminal.NORTH_POWER_LEFT
                )
        );
    }

    @Test
    public void rejectsNullRequiredValues() {
        Map<SixTerminalRectifierTerminal, String> terminalNodeIds =
                validTerminalNodeIds();

        assertThrows(
                NullPointerException.class,
                () -> new SixTerminalRectifierInstallation(
                        null,
                        true,
                        3120.0,
                        0.08,
                        RectifierType.DIODE,
                        terminalNodeIds
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new SixTerminalRectifierInstallation(
                        "SS0",
                        true,
                        3120.0,
                        0.08,
                        null,
                        terminalNodeIds
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new SixTerminalRectifierInstallation(
                        "SS0",
                        true,
                        3120.0,
                        0.08,
                        RectifierType.DIODE,
                        null
                )
        );
    }

    private SixTerminalRectifierInstallation installation(
            Map<SixTerminalRectifierTerminal, String> terminalNodeIds
    ) {
        return new SixTerminalRectifierInstallation(
                "SS0",
                true,
                3120.0,
                0.08,
                RectifierType.DIODE,
                terminalNodeIds
        );
    }

    private Map<SixTerminalRectifierTerminal, String>
    validTerminalNodeIds() {
        Map<SixTerminalRectifierTerminal, String> terminalNodeIds =
                new EnumMap<>(
                        SixTerminalRectifierTerminal.class
                );

        terminalNodeIds.put(
                SixTerminalRectifierTerminal.NORTH_POWER_LEFT,
                "F_N_LEFT"
        );
        terminalNodeIds.put(
                SixTerminalRectifierTerminal.NORTH_POWER_RIGHT,
                "F_N_RIGHT"
        );
        terminalNodeIds.put(
                SixTerminalRectifierTerminal.SOUTH_POWER_LEFT,
                "F_S_LEFT"
        );
        terminalNodeIds.put(
                SixTerminalRectifierTerminal.SOUTH_POWER_RIGHT,
                "F_S_RIGHT"
        );
        terminalNodeIds.put(
                SixTerminalRectifierTerminal.NORTH_RETURN,
                "R_N"
        );
        terminalNodeIds.put(
                SixTerminalRectifierTerminal.SOUTH_RETURN,
                "R_S"
        );

        return terminalNodeIds;
    }
}