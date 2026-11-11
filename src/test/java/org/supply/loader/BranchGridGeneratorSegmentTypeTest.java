package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class BranchGridGeneratorSegmentTypeTest {
    private static Config input(String endpointType) {
        return ConfigFactory.parseString("""
            lineTypes {
              standard { feeding_resistance_ohm_per_m = 0.0001, return_resistance_ohm_per_m = 0.0002 }
              reinforced { feeding_resistance_ohm_per_m = 0.0003, return_resistance_ohm_per_m = 0.0004 }
            }
            electricalObjects {
              A { kind = POINT, positions { N = \"101 0+0 N\", S = \"101 0+0 S\" } }
              P { kind = POINT, positions { N = \"101 0+100 N\", S = \"101 0+100 S\" } }
              B { kind = POINT, positions { N = \"101 0+200 N\", S = \"101 0+200 S\" } }
            }
            branches.B1 { section = \"101\", objects = [
              { object = A, lineType = reinforced },
              { object = P, lineType = %s },
              B
            ] }
            """.formatted(endpointType));
    }

    @Test public void supportsDifferentLineTypesWithinOneBranch() {
        Config result = BranchGridGenerator.generate(input("standard"));
        assertEquals("reinforced", result.getString("electricalObjects.B__B1__1.type"));
        assertEquals("standard", result.getString("electricalObjects.B__B1__2.type"));
    }

    @Test public void rejectsUnknownSegmentType() {
        try {
            BranchGridGenerator.generate(input("missing"));
            fail("Expected unknown line type error");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unknown line type"));
        }
    }
}
