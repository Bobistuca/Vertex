package io.github.bobistuca.vertex.core.checks;

import java.util.Locale;

/** Stable number formatting for measurements (locale independent). */
final class Values {
    private Values() { }

    static String f(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "unknown";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
