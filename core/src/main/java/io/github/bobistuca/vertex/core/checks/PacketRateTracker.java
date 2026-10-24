package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sustained packet frequency analysis. Movement packets are expected at no more than about 20 per second; a
 * client that was stalled may legitimately flush a burst afterwards, so bursts that follow a stall are ignored.
 * Only runs of consecutive over-limit windows count.
 */
public final class PacketRateTracker {
    public static final String MOVEMENT_CHECK_ID = "PacketRateAnomaly";
    public static final String FLOOD_CHECK_ID = "PacketFlood";
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int STALL_COUNT = 10;

    private final Settings.PacketRate config;
    private boolean started;
    private long windowStart;
    private int movementCount;
    private int consecutiveOver;
    private int peakInRun;
    private long stallUntil = Long.MIN_VALUE;
    private int floodConsecutive;
    private int floodPeak;

    public PacketRateTracker(Settings.PacketRate config) {
        this.config = config;
    }

    /** Records one movement packet. {@code suspended} is true while a grace period applies. */
    public Optional<Finding> onMovementPacket(long nowNanos, boolean suspended) {
        Optional<Finding> result = Optional.empty();
        if (!started) {
            started = true;
            windowStart = nowNanos;
        }
        int guard = 0;
        while (nowNanos - windowStart >= WINDOW_NANOS && guard++ < 5) {
            Optional<Finding> closed = closeMovementWindow(windowStart + WINDOW_NANOS, suspended);
            if (closed.isPresent()) {
                result = closed;
            }
            windowStart += WINDOW_NANOS;
        }
        if (nowNanos - windowStart >= WINDOW_NANOS) {
            windowStart = nowNanos;
            movementCount = 0;
            consecutiveOver = 0;
        }
        movementCount++;
        return result;
    }

    private Optional<Finding> closeMovementWindow(long windowEnd, boolean suspended) {
        int count = movementCount;
        movementCount = 0;
        if (suspended) {
            consecutiveOver = 0;
            return Optional.empty();
        }
        if (count < STALL_COUNT) {
            stallUntil = windowEnd + config.stallGraceMillis() * 1_000_000L;
            consecutiveOver = 0;
            return Optional.empty();
        }
        if (count > config.windowLimit()) {
            if (windowEnd < stallUntil) {
                consecutiveOver = 0;
                return Optional.empty();
            }
            consecutiveOver++;
            peakInRun = Math.max(peakInRun, count);
            if (consecutiveOver >= config.sustainedWindows()) {
                int peak = peakInRun;
                int windows = consecutiveOver;
                consecutiveOver = 0;
                peakInRun = 0;
                Map<String, String> m = new LinkedHashMap<>();
                m.put("peakPacketsPerSecond", Integer.toString(peak));
                m.put("limitPerSecond", Integer.toString(config.windowLimit()));
                m.put("consecutiveWindows", Integer.toString(windows));
                return Optional.of(new Finding(MOVEMENT_CHECK_ID, DetectionKind.SUSPICION, Confidence.MEDIUM,
                        "Movement packets exceeded the expected rate for " + windows
                                + " consecutive seconds without a preceding stall. Lag, proxies and external "
                                + "tools can also cause this.", m));
            }
        } else {
            consecutiveOver = 0;
            peakInRun = 0;
        }
        return Optional.empty();
    }

    /** Feeds the total inbound packet count of one elapsed second. */
    public Optional<Finding> onSecond(int totalPackets, boolean suspended) {
        if (suspended || totalPackets <= config.floodLimit()) {
            floodConsecutive = 0;
            floodPeak = 0;
            return Optional.empty();
        }
        floodConsecutive++;
        floodPeak = Math.max(floodPeak, totalPackets);
        if (floodConsecutive >= config.floodSustainedWindows()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("peakPacketsPerSecond", Integer.toString(floodPeak));
            m.put("limitPerSecond", Integer.toString(config.floodLimit()));
            m.put("consecutiveSeconds", Integer.toString(floodConsecutive));
            floodConsecutive = 0;
            floodPeak = 0;
            return Optional.of(new Finding(FLOOD_CHECK_ID, DetectionKind.SUSPICION, Confidence.MEDIUM,
                    "The total inbound packet rate stayed far above normal client behavior for consecutive "
                            + "seconds.", m));
        }
        return Optional.empty();
    }

    public void reset() {
        started = false;
        movementCount = 0;
        consecutiveOver = 0;
        peakInRun = 0;
        floodConsecutive = 0;
        floodPeak = 0;
    }
}
