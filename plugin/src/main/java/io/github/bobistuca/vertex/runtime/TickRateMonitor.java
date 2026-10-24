package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.platform.ServerMetrics;

/**
 * Scheduler-derived tick rate for servers whose API does not expose TPS. Fed once per server tick; it measures how
 * fast the scheduler actually runs and reports {@code NaN} until enough ticks were observed.
 */
public final class TickRateMonitor implements ServerMetrics {
    private static final int WINDOW_TICKS = 40;

    private long windowStartNanos;
    private int ticks;
    private volatile double tps = Double.NaN;
    private volatile double mspt = Double.NaN;

    /** Server thread, once per tick. */
    public void tick(long nowNanos) {
        if (windowStartNanos == 0) {
            windowStartNanos = nowNanos;
            return;
        }
        ticks++;
        if (ticks >= WINDOW_TICKS) {
            double elapsedSeconds = (nowNanos - windowStartNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tps = Math.min(20.0, ticks / elapsedSeconds);
                mspt = elapsedSeconds * 1000.0 / ticks;
            }
            windowStartNanos = nowNanos;
            ticks = 0;
        }
    }

    @Override
    public double tps() {
        return tps;
    }

    @Override
    public double mspt() {
        return mspt;
    }

    @Override
    public String source() {
        return "scheduler-derived";
    }
}
