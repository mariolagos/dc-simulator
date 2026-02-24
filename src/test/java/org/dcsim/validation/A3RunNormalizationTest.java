package org.dcsim.validation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class A3RunNormalizationTest {

    @Test
    public void A3_unsortedInput_isSortedDeterministically() {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();

        rows.add(row("10", "T1"));
        rows.add(row("0", "T2"));
        rows.add(row("5", "T1"));
        rows.add(row("5", "T2"));

        RunInputNormalizer n = new RunInputNormalizer(RunInputNormalizer.Mode.SORT_ON_READ);
        List<Map<String, String>> out = n.normalizeAndValidate(rows);

        // Expected order: time asc, then train_id asc
        assertEquals("0", out.get(0).get("time_s"));
        assertEquals("T2", out.get(0).get("train_id"));

        assertEquals("5", out.get(1).get("time_s"));
        assertEquals("T1", out.get(1).get("train_id"));

        assertEquals("5", out.get(2).get("time_s"));
        assertEquals("T2", out.get(2).get("train_id"));

        assertEquals("10", out.get(3).get("time_s"));
        assertEquals("T1", out.get(3).get("train_id"));
    }

    @Test
    public void A3_duplicateTrainTime_failsFast() {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();

        rows.add(row("0", "T1"));
        rows.add(row("0", "T1")); // duplicate (train_id,time_s)

        RunInputNormalizer n = new RunInputNormalizer(RunInputNormalizer.Mode.SORT_ON_READ);

        try {
            n.normalizeAndValidate(rows);
            fail("Expected ValidationInputException");
        } catch (ValidationInputException ex) {
            assertTrue(ex.getMessage().contains("duplicate (train_id,time_s)"));
        }
    }

    private static Map<String, String> row(String time, String train) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("time_s", time);
        m.put("train_id", train);

        // rest of columns not needed for this test, but can be present
        m.put("track_id", "A");
        m.put("position_m", "0");
        m.put("speed_mps", "0");
        return m;
    }
}
