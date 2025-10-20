package org.dcsim.solver.impl;

/**
 * Solver-nära debug med global flagga. Aktivera via:
 *   DcDebug.setVerbose(true);
 * eller JVM-flagga:
 *   -Ddcsim.verbose=true
 *
 * Nu med robust extraktion av G/J från DcNet via flera kandidatnamn
 * (metoder eller fält) och backend-agnostiska dumpare (double[][],
 * Apache Commons Math, EJML) via reflection.
 */
public final class DcDebug {
    private DcDebug() {}

    /** Global verbose switch. Sätt via kod eller JVM: -Ddcsim.verbose=true */
    public static volatile boolean VERBOSE;
    static { VERBOSE = Boolean.getBoolean("dcsim.verbose"); }

    public static void setVerbose(boolean v) { VERBOSE = v; }

    public static void log(String fmt, Object... args) {
        if (VERBOSE) System.out.printf(fmt + "%n", args);
    }

    public static void line() {
        if (VERBOSE) System.out.println("----------------------------------------------------------------");
    }

    // ===================== High-level helpers for DcNet =====================

    /**
     * Försök hitta G (lednings-/systemmatris) på ett DcNet-objekt.
     * Testar vanliga namn för method/field i turordning.
     */
    public static Object extractG(Object dcNet) {
        // Vanliga varianter vi sett "in the wild"
        String[] meth = {"getG", "G", "getGMatrix", "getY", "Y", "getA", "A", "getSystemMatrix", "systemMatrix"};
        String[] fld  = {"G", "g", "GMatrix", "Y", "A", "systemMatrix"};
        Object m = tryGetAny(dcNet, meth);
        if (m == null) m = tryFieldAny(dcNet, fld);
        if (m == null) {
            log("[DcDebug] Could not find G on %s. Public methods: %s",
                    dcNet.getClass().getName(), listInteresting(dcNet, 'g','y','a'));
        }
        return m;
    }

    /**
     * Försök hitta J (RHS/injection/strömmar).
     * Stöd både vektor och Nx1-matris.
     */
    public static Object extractJ(Object dcNet) {
        String[] meth = {"getJ", "J", "getRhs", "rhs", "getB", "B", "getI", "I", "getRightHandSide", "rightHandSide", "getLoadVector", "loadVector"};
        String[] fld  = {"J", "rhs", "b", "B", "I", "rightHandSide", "loadVector"};
        Object m = tryGetAny(dcNet, meth);
        if (m == null) m = tryFieldAny(dcNet, fld);
        if (m == null) {
            log("[DcDebug] Could not find J on %s. Public methods: %s",
                    dcNet.getClass().getName(), listInteresting(dcNet, 'j','r','b','i'));
        }
        return m;
    }

    // ===================== Dumpers (matrix/vector) =====================

    public static void dumpMatrixOrVector(String title, Object obj) {
        if (!VERBOSE || obj == null) return;
        Class<?> c = obj.getClass();
        // double[] ?
        if (c.isArray() && c.getComponentType() == double.class) {
            dumpVector(title, obj);
            return;
        }
        // double[][] ?
        if (c.isArray() && c.getComponentType().isArray()) {
            dumpMatrix(title, obj);
            return;
        }
        // Kolla kända typer via reflection
        if (isInstanceOf(obj, "org.apache.commons.math3.linear.RealVector")) {
            dumpVector(title, obj);
            return;
        }
        if (isInstanceOf(obj, "org.apache.commons.math3.linear.RealMatrix")
                || isInstanceOf(obj, "org.ejml.data.DMatrixRMaj")
                || isInstanceOf(obj, "org.ejml.simple.SimpleMatrix")) {
            dumpMatrix(title, obj);
            return;
        }
        // Osäker typ: försök tolka som vektor (dimension>kolumner) annars matris
        // men skriv ut typ-info.
        log("%s: <unknown numeric type: %s>", title, c.getName());
    }

    public static void dumpMatrix(String title, Object M) {
        if (!VERBOSE || M == null) return;
        try {
            if (M instanceof double[][] dd) {
                int r = dd.length, c = r > 0 ? dd[0].length : 0;
                System.out.printf("%s (%dx%d):%n", title, r, c);
                for (int i = 0; i < r; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < c; j++) {
                        sb.append(String.format("% .6e", dd[i][j]));
                        if (j < c - 1) sb.append(' ');
                    }
                    System.out.println(sb.toString());
                }
                return;
            }
            if (isInstanceOf(M, "org.apache.commons.math3.linear.RealMatrix")) {
                int r = (int) invoke(M, "getRowDimension");
                int c = (int) invoke(M, "getColumnDimension");
                System.out.printf("%s (%dx%d):%n", title, r, c);
                for (int i = 0; i < r; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < c; j++) {
                        double v = (double) invoke(M, "getEntry",
                                new Class[]{int.class, int.class}, new Object[]{i, j});
                        sb.append(String.format("% .6e", v));
                        if (j < c - 1) sb.append(' ');
                    }
                    System.out.println(sb.toString());
                }
                return;
            }
            if (isInstanceOf(M, "org.ejml.data.DMatrixRMaj")) {
                int r = (int) invoke(M, "getNumRows");
                int c = (int) invoke(M, "getNumCols");
                System.out.printf("%s (%dx%d):%n", title, r, c);
                for (int i = 0; i < r; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < c; j++) {
                        double v = (double) invoke(M, "get", new Class[]{int.class, int.class},
                                new Object[]{i, j});
                        sb.append(String.format("% .6e", v));
                        if (j < c - 1) sb.append(' ');
                    }
                    System.out.println(sb.toString());
                }
                return;
            }
            if (isInstanceOf(M, "org.ejml.simple.SimpleMatrix")) {
                int r = (int) invoke(M, "numRows");
                int c = (int) invoke(M, "numCols");
                System.out.printf("%s (%dx%d):%n", title, r, c);
                for (int i = 0; i < r; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < c; j++) {
                        double v = (double) invoke(M, "get", new Class[]{int.class, int.class},
                                new Object[]{i, j});
                        sb.append(String.format("% .6e", v));
                        if (j < c - 1) sb.append(' ');
                    }
                    System.out.println(sb.toString());
                }
                return;
            }
            log("%s: <unrecognized matrix type: %s>", title, M.getClass().getName());
        } catch (Throwable t) {
            log("%s: <error dumping matrix: %s>", title, String.valueOf(t));
        }
    }

    public static void dumpVector(String title, Object v) {
        if (!VERBOSE || v == null) return;
        try {
            if (v instanceof double[] dd) {
                System.out.printf("%s (%d):%n", title, dd.length);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < dd.length; i++) {
                    sb.append(String.format("% .6e", dd[i]));
                    if (i < dd.length - 1) sb.append(' ');
                }
                System.out.println(sb.toString());
                return;
            }
            if (isInstanceOf(v, "org.apache.commons.math3.linear.RealVector")) {
                int n = (int) invoke(v, "getDimension");
                System.out.printf("%s (%d):%n", title, n);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    double x = (double) invoke(v, "getEntry",
                            new Class[]{int.class}, new Object[]{i});
                    sb.append(String.format("% .6e", x));
                    if (i < n - 1) sb.append(' ');
                }
                System.out.println(sb.toString());
                return;
            }
            log("%s: <unrecognized vector type: %s>", title, v.getClass().getName());
        } catch (Throwable t) {
            log("%s: <error dumping vector: %s>", title, String.valueOf(t));
        }
    }

    // ===================== reflection helpers =====================

    private static boolean isInstanceOf(Object o, String fqcn) {
        try { return Class.forName(fqcn).isInstance(o); } catch (Throwable t) { return false; }
    }

    private static Object invoke(Object target, String method) throws Exception {
        var m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static Object invoke(Object target, String method, Class<?>[] ptypes, Object[] args) throws Exception {
        var m = target.getClass().getMethod(method, ptypes);
        return m.invoke(target, args);
    }

    private static Object tryGetAny(Object target, String[] methodNames) {
        for (String name : methodNames) {
            try {
                var m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object tryFieldAny(Object target, String[] fieldNames) {
        for (String name : fieldNames) {
            try {
                var f = target.getClass().getField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String listInteresting(Object target, char... inkl) {
        try {
            var b = new StringBuilder();
            var methods = target.getClass().getMethods();
            for (var m : methods) {
                String n = m.getName();
                for (char c : inkl) {
                    if (n.toLowerCase().indexOf(c) >= 0) {
                        if (b.length() > 0) b.append(", ");
                        b.append(n).append("()");
                        break;
                    }
                }
            }
            return b.toString();
        } catch (Throwable t) {
            return "<err>";
        }
    }
}
