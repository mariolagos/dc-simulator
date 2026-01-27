package org.dcsim.unit.contracts;

import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridModelLoader;
import org.dcsim.math.Real;
import org.junit.Test;

public class contract_rejects_line_crossing_tracks {
    @Test(expected = IllegalArgumentException.class)
    public void contract_rejects_line_crossing_tracks() throws Exception {
        GridModel<Real> m = (GridModel<Real>) GridModelLoader.load(ConfigFactory.parseString("""
        groundNodeId=0
        nodes=[
          {id=0, position="GND"}
          {id=1, position="1 0+000"}
          {id=2, position="2 0+100"}
        ]
        substations=[]
        lines=[ {from=1, to=2, rPerKm=0.1} ]
    """));
        // validate is called inside loader; test will already fail
    }

}
