// src/testFixtures/java/org/dcsim/testing/PowerAsserts.java
package org.dcsim.testing;

import static org.junit.Assert.assertEquals;

public final class PowerAsserts {
    private PowerAsserts() {}

    /** Sum device powers whose id starts with a prefix (e.g. "SS") and assert equals.
     *  Accepts values like Double, Number, Real, or List<?> (uses last element). */
    public static void assertSumPowerByPrefix(
            java.util.Map<String, ?> devicePowers,
            String idPrefix,
            double expectedWatts,
            double tolAbs) {

        double sum = 0.0;
        if (devicePowers != null) {
            for (var e : devicePowers.entrySet()) {
                final String id = e.getKey();
                if (id != null && id.startsWith(idPrefix)) {
                    sum += toDouble(e.getValue());
                }
            }
        }
        assertEquals("Sum power for devices with id prefix '" + idPrefix + "'",
                expectedWatts, sum, tolAbs);
    }

    /** Convert supported types to double (Double/Number/Real/List<?> -> last element). */
    @SuppressWarnings("unchecked")
    private static double toDouble(Object value) {
        if (value == null) return 0.0;

        // If it's already a number
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        // If it's your Real type
        if (isReal(value)) {
            Double d = realToDouble(value);
            if (d != null) return d;
        }

        // If it's a list, use the last entry (assumed latest)
        if (value instanceof java.util.List) {
            var list = (java.util.List<?>) value;
            if (list.isEmpty()) return 0.0;
            Object last = list.get(list.size() - 1);
            return toDouble(last); // recurse
        }

        // Unknown → treat as 0
        return 0.0;
    }

    /** Heuristic check for org.dcsim.math.Real without hard dependency. */
    private static boolean isReal(Object o) {
        return o.getClass().getName().equals("org.dcsim.math.Real");
    }

    /** Extract double from Real via common method names (no compile-time dep). */
    private static Double realToDouble(Object real) {
        try {
            // Try common patterns: toDouble(), doubleValue(), value(), get(), asDouble()
            for (String m : new String[]{"toDouble", "doubleValue", "value", "get", "asDouble"}) {
                try {
                    var method = real.getClass().getMethod(m);
                    Object v = method.invoke(real);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                } catch (NoSuchMethodException ignore) {}
            }
        } catch (Throwable ignore) {}
        return null;
    }
}
