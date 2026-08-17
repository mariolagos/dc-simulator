package org.supply.io.export;

import java.util.Locale;

public final class NumericFormat {

    private NumericFormat() {
    }

    public static String format(double value) {
        return String.format(
                Locale.ROOT,
                "%.6E",
                value
        );
    }
}