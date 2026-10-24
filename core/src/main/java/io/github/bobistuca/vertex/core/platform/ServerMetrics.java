package io.github.bobistuca.vertex.core.platform;

/** Server performance measurements. Unavailable values are reported as {@code NaN}, never invented. */
public interface ServerMetrics {
    /** Ticks per second, {@code NaN} if unknown. */
    double tps();

    /** Average milliseconds per tick, {@code NaN} if unknown. */
    double mspt();

    /** Where the values come from, for example {@code server-api} or {@code scheduler-derived}. */
    String source();
}
