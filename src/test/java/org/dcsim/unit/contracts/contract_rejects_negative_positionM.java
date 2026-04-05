package org.dcsim.unit.contracts;

import com.typesafe.config.ConfigFactory;
import org.dcsim.contracts.ContractChecks;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.math.Real;
import org.junit.Test;

public class contract_rejects_negative_positionM {
    @Test(expected = IllegalArgumentException.class)
    public void contract_rejects_negative_positionM() throws Exception {
        GridModelLoader loader = new GridModelLoader();
        GridModel<Real> m = (GridModel<Real>) loader.load(ConfigFactory.parseString("""
        groundNodeId=0
        nodes=[
          {id=0, position="GND"}
          {id=1, position="1 0+000"}
        ]
        substations=[]
        lines=[]
    """));

        // Force a bad value (simulating a buggy loader / future regression)
        m.nodeOrThrow(1).setPositionM(-1);

        ContractChecks.validateGridModel(m);
    }

}
