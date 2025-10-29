package org.dcsim.utils;

import java.io.PrintStream;
import java.lang.reflect.Method;

/**
 * Unified Debug utility.
 *
 * Goals:
 * - Default OFF (no console spam in CI).
 * - Opt-in via JVM flag: -Ddcsim.verbose=true
 * - Preserve existing API: ENABLED/OUT fields and println/printf(enabled, ...) overloads.
 * - Provide convenience overloads without the boolean flag.
 * - Keep legacy reflection helpers but deprecate their use.
 */
public final class Debug {

    private Debug() {}

    /** Global on/off switch. Default is derived from the JVM property "dcsim.verbose". */
    public static volatile boolean ENABLED = Boolean.parseBoolean(System.getProperty("dcsim.verbose", "false"));

    /** Output stream, defaults to System.out. */
    public static volatile PrintStream OUT = System.out;

    /** Refresh ENABLED from system property (useful in tests). */
    public static void refreshFromSystemProperty() {
        ENABLED = Boolean.parseBoolean(System.getProperty("dcsim.verbose", "false"));
    }

    /** Explicitly set enabled flag (overrides property until next refresh). */
    public static void setEnabled(boolean enabled) { ENABLED = enabled; }

    /** Query current enabled flag. */
    public static boolean isEnabled() { return ENABLED; }

    // ---------------------------------------------------------------------
    // Printing API (kept for compatibility + convenience overloads)
    // ---------------------------------------------------------------------

    /** Print line if either parameter or global flag is true. */
    public static void println(boolean enabled, String msg) {
        if (enabled || ENABLED) OUT.println(msg);
    }

    /** Printf if either parameter or global flag is true. */
    public static void printf(boolean enabled, String fmt, Object... args) {
        if (enabled || ENABLED) OUT.printf(fmt, args);
    }

    /** Convenience: print line only when globally enabled. */
    public static void println(String msg) {
        if (ENABLED) OUT.println(msg);
    }

    /** Convenience: printf only when globally enabled. */
    public static void printf(String fmt, Object... args) {
        if (ENABLED) OUT.printf(fmt, args);
    }

    /** Convenience: alias for println. */
    public static void log(String msg) { println(msg); }

    // ---------------------------------------------------------------------
    // Legacy helpers (reflection). Keep for binary compatibility; avoid in tests.
    // ---------------------------------------------------------------------

    /**
     * @deprecated Avoid reflection in tests and high-frequency paths.
     *             Prefer explicit types and APIs. This will be removed in a future release.
     */
    @Deprecated
    public static boolean isInstanceOf(Object o, String fqcn) {
        try { return Class.forName(fqcn).isInstance(o); } catch (Throwable t) { return false; }
    }

    /**
     * @deprecated Avoid reflection in tests and high-frequency paths.
     */
    @Deprecated
    public static Object invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    /**
     * @deprecated Avoid reflection in tests and high-frequency paths.
     */
    @Deprecated
    public static Object invoke(Object target, String method, Class<?>[] ptypes, Object[] args) throws Exception {
        Method m = target.getClass().getMethod(method, ptypes);
        return m.invoke(target, args);
    }
}
