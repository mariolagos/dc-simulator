package org.supply.domain;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SwitchGearModelTest {
    @Test public void computesParallelCableResistanceFromPhysicalLength() {
        var type = new ConductorType("test", 0.08);
        assertEquals(0.0002, new CableConnection("C", "a", "b", 15, type, 6).resistanceOhm(), 1e-15);
        assertEquals(0.0004, new CableConnection("C", "a", "b", 30, type, 6).resistanceOhm(), 1e-15);
    }
    @Test public void rejectsInvalidPhysicalData() {
        assertThrows(IllegalArgumentException.class, () -> new ConductorType("C", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ConductorType("C", -1));
        var type = new ConductorType("C", 0.08);
        assertThrows(IllegalArgumentException.class, () -> new CableConnection("C", "a", "b", 0, type, 1));
        assertThrows(IllegalArgumentException.class, () -> new CableConnection("C", "a", "b", 15, type, 0));
    }
    @Test public void rejectsCableAcrossFeedingAndReturn() {
        assertThrows(IllegalArgumentException.class, () -> new WiredSwitchGear("SS", List.of(
                new SwitchGearTerminal("a", "A", ConnectionType.FEEDING),
                new SwitchGearTerminal("b", "B", ConnectionType.RETURN)), List.of(
                new CableConnection("C", "a", "b", 1, new ConductorType("T", 1), 1))));
    }
    @Test public void rejectsTerminalAliasesAcrossPoles() {
        assertThrows(IllegalArgumentException.class, () -> new WiredSwitchGear("SS", List.of(
                new SwitchGearTerminal("a", "same", ConnectionType.FEEDING),
                new SwitchGearTerminal("b", "same", ConnectionType.RETURN)), List.of()));
    }
    @Test public void wiringCollectionsAreImmutable() {
        var gear = new WiredSwitchGear("SS", List.of(), List.of());
        assertThrows(UnsupportedOperationException.class, () -> gear.cables().clear());
        assertThrows(UnsupportedOperationException.class, () -> gear.terminals().clear());
    }
    @Test public void copiesCallerCollectionsAndRejectsMissingCableEndpoints() {
        var terminals=new java.util.ArrayList<>(List.of(new SwitchGearTerminal("A","node",ConnectionType.FEEDING)));
        var cables=new java.util.ArrayList<CableConnection>();var gear=new WiredSwitchGear("SS",terminals,cables);
        terminals.clear();cables.add(new CableConnection("bad","missing","A",1,new ConductorType("wire",1),1));
        assertEquals(1,gear.terminals().size());assertTrue(gear.cables().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new WiredSwitchGear("SS",gear.terminals(),cables));
    }
    @Test public void rejectsMissingRequiredWiringValues() {
        assertThrows(IllegalArgumentException.class,()->new WiredSwitchGear(null,List.of(),List.of()));
        assertThrows(NullPointerException.class,()->new WiredSwitchGear("SS",null,List.of()));
        assertThrows(NullPointerException.class,()->new WiredSwitchGear("SS",List.of(),null));
    }
}
