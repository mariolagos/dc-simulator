package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.app.SwitchGearConnectionReport;
import java.util.*;
import static org.junit.Assert.*;

public class SwitchGearTypeExpanderTest {
    private Config detailed() { return ConfigFactory.parseResources("switchgear/double-track-gap.conf").resolve().getConfig("dcsim.grid"); }
    private Config simple() {
        Config full=detailed();
        return full.withValue("switchgearTypes.standard_gap",full.getValue("substationTypes.standard.switchgear"))
                .withoutPath("substationTypes.standard.switchgear")
                .withValue("substationTypes.standard.switchgear_type",ConfigValueFactory.fromAnyRef("standard_gap"));
    }
    private Map<String,Object> expanded(Config input) {
        return ElectricalObjectExpander.inspect(BranchGridGenerator.generate(input).root().unwrapped()).expandedGrid();
    }
    private Map<String,String> report(Config input) {
        var objects=BranchGridGenerator.generate(input);
        var model=new GridModelLoader().load(ConfigFactory.empty().withValue("grid",objects.root()));
        return SwitchGearConnectionReport.render(model,objects);
    }
    @Test public void namedTypeProducesIdenticalElectricalGridAndReports() {
        assertEquals(expanded(detailed()),expanded(simple()));
        assertEquals(report(detailed()),report(simple()));
    }
    @Test public void localCableLengthOverridePreservesEveryOtherCableField() {
        var input=simple().withValue("electricalObjects.SS0.switchgear.cable_overrides.FP1.length_m",ConfigValueFactory.fromAnyRef(30));
        var result=SwitchGearTypeExpander.expand(input);
        var cable=result.getConfig("electricalObjects.SS0.switchgear").getConfigList("connections").stream()
                .filter(c->c.getString("id").equals("FP1")).findFirst().orElseThrow();
        assertEquals(30,cable.getDouble("length_m"),0); assertEquals(6,cable.getInt("multiplicity"));
        assertEquals("PBB1",cable.getString("from")); assertEquals("1",cable.getString("to"));
        assertTrue(report(input).get("SS0_connections.csv").contains(",30.0,cable240,0.08,6,3.9999999999999996E-4"));
    }
    @Test public void localCableOverrideMergesWithSubstationTypeOverridesById() {
        var input=simple().withValue("substationTypes.standard.switchgear.cable_overrides.FP1.length_m",ConfigValueFactory.fromAnyRef(30))
                .withValue("electricalObjects.SS0.switchgear.cable_overrides.FP1.multiplicity",ConfigValueFactory.fromAnyRef(3));
        var result=SwitchGearTypeExpander.expand(input);
        var cable=result.getConfig("electricalObjects.SS0.switchgear").getConfigList("connections").stream()
                .filter(c->c.getString("id").equals("FP1")).findFirst().orElseThrow();
        assertEquals(30,cable.getDouble("length_m"),0);assertEquals(3,cable.getInt("multiplicity"));
    }
    @Test public void localTypeSelectionDoesNotInheritPreviousWiring() {
        var input=simple().withValue("switchgearTypes.other",simple().getValue("switchgearTypes.standard_gap"))
                .withValue("electricalObjects.SS0.switchgear_type",ConfigValueFactory.fromAnyRef("other"))
                .withValue("substationTypes.standard.switchgear.cable_overrides.FP1.length_m",ConfigValueFactory.fromAnyRef(999));
        assertEquals(expanded(detailed()),expanded(input));
    }
    @Test public void acceptsIndependentStationSelectionWithoutSubstationType() {
        var original=detailed();var station=original.getConfig("electricalObjects.SS0").withoutPath("type")
                .withFallback(original.getConfig("substationTypes.standard")).withoutPath("switchgear")
                .withValue("switchgear_type",ConfigValueFactory.fromAnyRef("standard_gap"));
        var input=simple().withValue("electricalObjects.SS0",station.root());
        assertEquals(expanded(detailed()),expanded(input));
    }
    @Test public void rejectsUnknownTypeAndUnknownCableOverride() {
        assertThrows(IllegalArgumentException.class,()->expanded(simple().withValue("electricalObjects.SS0.switchgear_type",ConfigValueFactory.fromAnyRef("missing"))));
        assertThrows(IllegalArgumentException.class,()->expanded(simple().withValue("electricalObjects.SS0.switchgear.cable_overrides.missing.length_m",ConfigValueFactory.fromAnyRef(30))));
    }
    @Test public void rejectsChangingCableTopologyThroughDataOverrides() {
        assertThrows(IllegalArgumentException.class,()->expanded(simple().withValue("electricalObjects.SS0.switchgear.cable_overrides.FP1.to",ConfigValueFactory.fromAnyRef("2"))));
    }
    @Test public void normalizerIsIdempotentAndLeavesInputUntouched() {
        var input=simple();var before=input.root().unwrapped();var result=SwitchGearTypeExpander.expand(input);
        assertEquals(before,input.root().unwrapped());
        assertEquals(result.root().unwrapped(),SwitchGearTypeExpander.expand(result).root().unwrapped());
        assertFalse(result.hasPath("electricalObjects.SS0.type"));
        assertFalse(result.hasPath("electricalObjects.SS0.switchgear_type"));
    }
    @Test public void ordinaryElectricalObjectsAlsoSupportNamedSwitchgearTypes() {
        var generated=BranchGridGenerator.generate(detailed());
        var input=generated.withValue("switchgearTypes.gap",detailed().getValue("substationTypes.standard.switchgear"))
                .withoutPath("electricalObjects.SS0.switchgear")
                .withValue("electricalObjects.SS0.switchgear_type",ConfigValueFactory.fromAnyRef("gap"));
        assertEquals(ElectricalObjectExpander.inspect(generated.root().unwrapped()).expandedGrid(),ElectricalObjectExpander.inspect(input.root().unwrapped()).expandedGrid());
    }
}
