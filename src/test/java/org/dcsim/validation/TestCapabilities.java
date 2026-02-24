package org.dcsim.validation;

public final class TestCapabilities {
    private TestCapabilities() {
    }

    public static boolean appConfGeneratorEnabled() {
        return Boolean.parseBoolean(
                System.getProperty("appconf.generator.enabled", "false")
        );
    }

    // Slå på när generatorn är klar
    public static final boolean APP_CONF_TO_CSV_IMPLEMENTED =
            Boolean.parseBoolean(System.getProperty("appconf.generator.enabled", "false"));

    public static final boolean MATLAB_AVAILABLE =
            Boolean.parseBoolean(System.getProperty("matlab.enabled", "false"));
}
