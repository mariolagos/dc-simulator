package org.dcsim.unit.contracts;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.dcsim.electric.GridModelLoader;
import org.junit.Test;

public class gridModelLoader_rejects_unparseable_position {
    @Test(expected = IllegalArgumentException.class)
    public void gridModelLoader_rejects_unparseable_position() throws Exception {
        Config cfg = ConfigFactory.parseString("""
        groundNodeId=0
        nodes=[ {id=0, position="GND"}, {id=1, position="NOT_A_POS"} ]
        substations=[]
        lines=[]
    """);
        GridModelLoader.load(cfg);
    }
}