package io.github.bobistuca.vertex.core.engine;

import io.github.bobistuca.vertex.core.checks.ClickAnalyzer;
import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.checks.ProtocolValidator;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Detection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs every packet-driven check. It is single-threaded by contract (one analysis thread) and never throws:
 * a failing check is counted and skipped so the network pipeline is never disturbed. The engine only produces
 * {@link Detection} records; it cannot affect gameplay.
 */
public final class AnalysisEngine {
    private static final long DIGGING_CONTEXT_NANOS = 300_000_000L;

    private final Supplier<Settings> settings;
    private final Function<UUID, PlayerContext> contexts;
    private final Consumer<Detection> sink;
    private final LongSupplier wallClock;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();

    public AnalysisEngine(Supplier<Settings> settings, Function<UUID, PlayerContext> contexts,
                          Consumer<Detection> sink, LongSupplier wallClock) {
        this.settings = settings;
        this.contexts = contexts;
        this.sink = sink;
        this.wallClock = wallClock;
    }

    public long failures() {
        return failures.get();
    }

    public long processed() {
        return processed.get();
    }

    public int trackedPlayers() {
        return states.size();
    }

    /** Processes one observation. Never throws. */
    public void process(Observation observation) {
        try {
            processed.incrementAndGet();
            handle(observation);
        } catch (RuntimeException | StackOverflowError e) {
            failures.incrementAndGet();
        }
    }

    public Optional<PlayerReport> report(UUID id) {
        PlayerState state = states.get(id);
        if (state == null) {
            return Optional.empty();
        }
        Map<String, Long> counts = new TreeMap<>();
        state.incidents.forEach((k, v) -> counts.put(k, v.get()));
        return Optional.of(new PlayerReport(counts, state.lastIncidentWallMillis, state.analyzedWindows,
                state.skippedWindows, state.lastWindowSummary));
    }

    private PlayerState stateFor(UUID id) {
        Settings current = settings.get();
        PlayerState state = states.get(id);
        if (state == null || state.settings != current) {
            PlayerState fresh = new PlayerState(current);
            if (state != null) {
                fresh.incidents.putAll(state.incidents);
                fresh.lastIncidentWallMillis = state.lastIncidentWallMillis;
            }
            states.put(id, fresh);
            return fresh;
        }
        return state;
    }

    private void handle(Observation o) {
        if (o instanceof Observation.Quit) {
            states.remove(o.playerId());
            return;
        }
        PlayerContext ctx = contexts.apply(o.playerId());
        if (ctx == null) {
            return;
        }
        Settings cfg = settings.get();
        PlayerState state = stateFor(o.playerId());
        PlayerSnapshot snap = ctx.snapshot();
        GraceState grace = ctx.grace();
        GraceState.Reason graceReason = grace.active(o.nanos(), cfg.grace(), snap.tps());
        boolean suspended = graceReason != null;

        switch (o) {
            case Observation.Flying f -> {
                if (cfg.checks().invalidValues().enabled()) {
                    ProtocolValidator.validateLocation(f.hasPosition(), f.x(), f.y(), f.z(), f.hasRotation(),
                            f.yaw(), f.pitch()).ifPresent(x -> emit(state, o, x, null));
                }
                if (cfg.checks().packetRate().enabled()) {
                    state.rate.onMovementPacket(o.nanos(), suspended).ifPresent(x -> emit(state, o, x, null));
                }
                if (cfg.checks().movement().enabled() && f.hasPosition()) {
                    for (Finding x : state.movement.onPosition(f.x(), f.y(), f.z(), f.onGround(), snap, suspended)) {
                        emit(state, o, x, null);
                    }
                }
            }
            case Observation.Attack a -> {
                if (cfg.checks().invalidFields().enabled()) {
                    ProtocolValidator.validateEntityId(a.entityId(), snap.entityId())
                            .ifPresent(x -> emit(state, o, x, null));
                }
                if (cfg.checks().autoClicker().enabled() && !suspended) {
                    feedClick(state, o, ClickAnalyzer.Source.ATTACK, false, snap);
                }
            }
            case Observation.Swing s -> {
                if (cfg.checks().autoClicker().enabled() && !suspended) {
                    boolean blocked = state.digging || o.nanos() - state.lastDiggingNanos < DIGGING_CONTEXT_NANOS;
                    feedClick(state, o, ClickAnalyzer.Source.SWING, blocked, snap);
                }
            }
            case Observation.Digging d -> {
                state.digging = d.active();
                state.lastDiggingNanos = o.nanos();
            }
            case Observation.ClickWindow c -> {
                if (cfg.checks().invalidFields().enabled()) {
                    ProtocolValidator.validateClickSlot(c.windowId(), c.slot()).ifPresent(x -> emit(state, o, x, null));
                }
                if (cfg.checks().inventory().enabled()) {
                    boolean within = o.nanos() - grace.windowChangeNanos() >= 0
                            && o.nanos() - grace.windowChangeNanos() < cfg.checks().inventory().windowGraceMillis() * 1_000_000L
                            || suspended;
                    ProtocolValidator.validateWindow(c.windowId(), snap.containerOpen(), within)
                            .ifPresent(x -> emit(state, o, x, "window:" + c.windowId() + ":" + o.nanos()));
                }
            }
            case Observation.HeldSlot h -> {
                if (cfg.checks().invalidFields().enabled()) {
                    ProtocolValidator.validateHeldSlot(h.slot()).ifPresent(x -> emit(state, o, x, null));
                }
            }
            case Observation.Abilities a -> {
                if (cfg.checks().flightToggle().enabled()) {
                    ProtocolValidator.validateFlightToggle(a.flying(), snap.allowFlight(), suspended)
                            .ifPresent(x -> emit(state, o, x, null));
                }
            }
            case Observation.SecondTick t -> {
                if (cfg.checks().packetRate().enabled()) {
                    state.rate.onSecond(t.totalPackets(), suspended).ifPresent(x -> emit(state, o, x, null));
                }
            }
            case Observation.Quit q -> { }
        }
    }

    private void feedClick(PlayerState state, Observation o, ClickAnalyzer.Source source, boolean blocked,
                           PlayerSnapshot snap) {
        Optional<ClickAnalyzer.WindowResult> result = state.clicks.accept(source, o.nanos(), blocked, snap.tps(),
                snap.jitterMillis());
        result.ifPresent(r -> {
            state.analyzedWindows++;
            if (r.skipped()) {
                state.skippedWindows++;
            }
            ClickAnalyzer.Stats s = r.stats();
            state.lastWindowSummary = String.format(Locale.ROOT, "samples=%d cps=%.2f cv=%.3f skipped=%s",
                    s.samples(), s.meanCps(), s.cv(), r.skipped() ? r.skipReason() : "no");
            r.finding().ifPresent(x -> emit(state, o, x, null));
        });
    }

    private void emit(PlayerState state, Observation o, Finding f, String dedupKey) {
        long now = wallClock.getAsLong();
        state.incidents.computeIfAbsent(f.checkId(), k -> new AtomicLong()).incrementAndGet();
        state.lastIncidentWallMillis = now;
        sink.accept(new Detection(o.playerId(), o.playerName(), f.checkId(), f.kind(), f.confidence(), f.reason(),
                f.measurements(), now, dedupKey));
    }
}
