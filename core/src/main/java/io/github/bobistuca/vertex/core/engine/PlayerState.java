package io.github.bobistuca.vertex.core.engine;

import io.github.bobistuca.vertex.core.checks.ClickAnalyzer;
import io.github.bobistuca.vertex.core.checks.MovementAnalyzer;
import io.github.bobistuca.vertex.core.checks.PacketRateTracker;
import io.github.bobistuca.vertex.core.config.Settings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Per-player analysis state. Analyzers are touched only by the analysis thread; counters are readable anywhere. */
final class PlayerState {
    /** Counters of one check. */
    static final class Stat {
        final AtomicLong evaluated = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        volatile String lastError = "";
    }

    final Settings settings;
    final ClickAnalyzer clicks;
    final PacketRateTracker rate;
    final MovementAnalyzer movement;

    boolean digging;
    long lastDiggingNanos = Long.MIN_VALUE;

    final Map<String, AtomicLong> incidents = new ConcurrentHashMap<>();
    final Map<String, Stat> stats = new ConcurrentHashMap<>();
    volatile long lastIncidentWallMillis;
    volatile String lastWindowSummary = "";
    volatile long skippedWindows;
    volatile long analyzedWindows;

    PlayerState(Settings settings) {
        this.settings = settings;
        this.clicks = new ClickAnalyzer(settings.checks().autoClicker());
        this.rate = new PacketRateTracker(settings.checks().packetRate());
        this.movement = new MovementAnalyzer(settings.checks().movement());
    }

    Stat stat(String checkId) {
        return stats.computeIfAbsent(checkId, k -> new Stat());
    }
}
