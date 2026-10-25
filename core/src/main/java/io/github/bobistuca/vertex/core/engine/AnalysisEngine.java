package io.github.bobistuca.vertex.core.engine;

import io.github.bobistuca.vertex.core.checks.ClickAnalyzer;
import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.MovementAnalyzer;
import io.github.bobistuca.vertex.core.checks.PacketRateTracker;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.checks.ProtocolValidator;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.staff.ExemptionRegistry;
import java.util.ArrayList;
import java.util.List;
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
 * a failing check is counted against that check and skipped so the network pipeline is never disturbed. The engine
 * only produces {@link Detection} records; it cannot affect gameplay. Exempt players are not judged at all, and the
 * exemption decision is read from one {@link ExemptionRegistry}.
 */
public final class AnalysisEngine {
    private static final long DIGGING_CONTEXT_NANOS = 300_000_000L;

    private final Supplier<Settings> settings;
    private final Function<UUID, PlayerContext> contexts;
    private final Consumer<Detection> sink;
    private final LongSupplier wallClock;
    private final ExemptionRegistry exemptions;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PacketTally> tallies = new ConcurrentHashMap<>();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();

    public AnalysisEngine(Supplier<Settings> settings, Function<UUID, PlayerContext> contexts,
                          Consumer<Detection> sink, LongSupplier wallClock, ExemptionRegistry exemptions) {
        this.settings = settings;
        this.contexts = contexts;
        this.sink = sink;
        this.wallClock = wallClock;
        this.exemptions = exemptions;
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
            tally(observation);
            handle(observation);
        } catch (RuntimeException | StackOverflowError e) {
            failures.incrementAndGet();
        }
    }

    private void tally(Observation o) {
        PacketTally.Type type = switch (o) {
            case Observation.Flying f -> PacketTally.Type.MOVEMENT;
            case Observation.Attack a -> PacketTally.Type.ATTACK;
            case Observation.Swing s -> PacketTally.Type.SWING;
            case Observation.Digging d -> PacketTally.Type.DIGGING;
            case Observation.ClickWindow c -> PacketTally.Type.CONTAINER_CLICK;
            case Observation.HeldSlot h -> PacketTally.Type.HELD_SLOT;
            case Observation.Abilities a -> PacketTally.Type.ABILITIES;
            default -> null;
        };
        if (type != null) {
            tallies.computeIfAbsent(o.playerId(), k -> new PacketTally()).count(type, o.nanos());
        }
    }

    /**
     * Records a finding produced outside the packet pipeline (the reach check needs server-side entity state).
     * Thread safe; the incident counters feed the inspect and diagnose commands.
     */
    public void record(UUID id, String name, Finding finding, String dedupKey) {
        if (exemptions.isExempt(id)) {
            return;
        }
        PlayerState state = stateFor(id);
        emit(state, id, name, finding, dedupKey);
    }

    /** Notes that an externally evaluated check (reach) ran for this player. */
    public void noteEvaluated(UUID id, String checkId) {
        if (!exemptions.isExempt(id)) {
            stateFor(id).stat(checkId).evaluated.incrementAndGet();
        }
    }

    /** Drops every detection buffer of a player (rank gained or lost, test mode). Historical evidence is untouched. */
    public void forget(UUID id) {
        states.remove(id);
    }

    public Optional<PlayerReport> report(UUID id) {
        if (exemptions.isExempt(id)) {
            return Optional.empty();
        }
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

    private void run(PlayerState state, String group, Runnable check) {
        PlayerState.Stat stat = state.stat(group);
        stat.evaluated.incrementAndGet();
        try {
            check.run();
        } catch (RuntimeException | StackOverflowError e) {
            stat.errors.incrementAndGet();
            stat.lastError = e.getClass().getSimpleName();
            failures.incrementAndGet();
        }
    }

    private void handle(Observation o) {
        if (o instanceof Observation.Quit) {
            states.remove(o.playerId());
            tallies.remove(o.playerId());
            return;
        }
        if (exemptions.isExempt(o.playerId())) {
            states.remove(o.playerId()); // exempt players are never judged and keep no detection buffers
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
        UUID id = o.playerId();
        String name = o.playerName();

        switch (o) {
            case Observation.Flying f -> {
                if (cfg.checks().invalidValues().enabled()) {
                    run(state, ProtocolValidator.INVALID_NUMERIC, () -> ProtocolValidator.validateLocation(
                            f.hasPosition(), f.x(), f.y(), f.z(), f.hasRotation(), f.yaw(), f.pitch())
                            .ifPresent(x -> emit(state, id, name, x, null)));
                }
                if (cfg.checks().packetRate().enabled()) {
                    run(state, PacketRateTracker.MOVEMENT_CHECK_ID, () -> state.rate
                            .onMovementPacket(o.nanos(), suspended).ifPresent(x -> emit(state, id, name, x, null)));
                }
                if (cfg.checks().movement().enabled() && f.hasPosition()) {
                    run(state, "Movement", () -> {
                        for (Finding x : state.movement.onPosition(f.x(), f.y(), f.z(), f.onGround(), snap, suspended)) {
                            emit(state, id, name, x, null);
                        }
                    });
                }
            }
            case Observation.Attack a -> {
                if (cfg.checks().invalidFields().enabled()) {
                    run(state, ProtocolValidator.SELF_ATTACK, () -> ProtocolValidator
                            .validateEntityId(a.entityId(), snap.entityId()).ifPresent(x -> emit(state, id, name, x, null)));
                }
                if (cfg.checks().autoClicker().enabled() && !suspended) {
                    run(state, ClickAnalyzer.CHECK_ID, () -> feedClick(state, o, ClickAnalyzer.Source.ATTACK, false, snap));
                }
            }
            case Observation.Swing s -> {
                if (cfg.checks().autoClicker().enabled() && !suspended) {
                    boolean blocked = state.digging || o.nanos() - state.lastDiggingNanos < DIGGING_CONTEXT_NANOS;
                    run(state, ClickAnalyzer.CHECK_ID, () -> feedClick(state, o, ClickAnalyzer.Source.SWING, blocked, snap));
                }
            }
            case Observation.Digging d -> {
                state.digging = d.active();
                state.lastDiggingNanos = o.nanos();
            }
            case Observation.ClickWindow c -> {
                if (cfg.checks().invalidFields().enabled()) {
                    run(state, ProtocolValidator.INVALID_FIELD, () -> ProtocolValidator
                            .validateClickSlot(c.windowId(), c.slot()).ifPresent(x -> emit(state, id, name, x, null)));
                }
                if (cfg.checks().inventory().enabled()) {
                    run(state, ProtocolValidator.WINDOW_MISMATCH, () -> {
                        boolean within = o.nanos() - grace.windowChangeNanos() >= 0
                                && o.nanos() - grace.windowChangeNanos() < cfg.checks().inventory().windowGraceMillis() * 1_000_000L
                                || suspended;
                        ProtocolValidator.validateWindow(c.windowId(), snap.containerOpen(), within)
                                .ifPresent(x -> emit(state, id, name, x, "window:" + c.windowId() + ":" + o.nanos()));
                    });
                }
            }
            case Observation.HeldSlot h -> {
                if (cfg.checks().invalidFields().enabled()) {
                    run(state, ProtocolValidator.INVALID_FIELD, () -> ProtocolValidator.validateHeldSlot(h.slot())
                            .ifPresent(x -> emit(state, id, name, x, null)));
                }
            }
            case Observation.Abilities a -> {
                if (cfg.checks().flightToggle().enabled()) {
                    run(state, ProtocolValidator.FLIGHT_TOGGLE, () -> ProtocolValidator
                            .validateFlightToggle(a.flying(), snap.allowFlight(), suspended)
                            .ifPresent(x -> emit(state, id, name, x, null)));
                }
            }
            case Observation.SecondTick t -> {
                if (cfg.checks().packetRate().enabled()) {
                    run(state, PacketRateTracker.FLOOD_CHECK_ID, () -> state.rate.onSecond(t.totalPackets(), suspended)
                            .ifPresent(x -> emit(state, id, name, x, null)));
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
            r.finding().ifPresent(x -> emit(state, o.playerId(), o.playerName(), x, null));
        });
    }

    private void emit(PlayerState state, UUID id, String name, Finding f, String dedupKey) {
        long now = wallClock.getAsLong();
        state.incidents.computeIfAbsent(f.checkId(), k -> new AtomicLong()).incrementAndGet();
        state.lastIncidentWallMillis = now;
        sink.accept(new Detection(id, name, f.checkId(), f.kind(), f.confidence(), f.reason(), f.measurements(), now,
                dedupKey));
    }

    // ------------------------------------------------------------------ diagnostics

    private static final Map<String, PacketTally.Type[]> REQUIRES = Map.ofEntries(
            Map.entry(ProtocolValidator.INVALID_NUMERIC, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(ProtocolValidator.INVALID_FIELD, new PacketTally.Type[] {PacketTally.Type.HELD_SLOT,
                    PacketTally.Type.CONTAINER_CLICK, PacketTally.Type.ATTACK}),
            Map.entry(ProtocolValidator.SELF_ATTACK, new PacketTally.Type[] {PacketTally.Type.ATTACK}),
            Map.entry(ProtocolValidator.FLIGHT_TOGGLE, new PacketTally.Type[] {PacketTally.Type.ABILITIES}),
            Map.entry(ProtocolValidator.WINDOW_MISMATCH, new PacketTally.Type[] {PacketTally.Type.CONTAINER_CLICK}),
            Map.entry(PacketRateTracker.MOVEMENT_CHECK_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(PacketRateTracker.FLOOD_CHECK_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(ClickAnalyzer.CHECK_ID, new PacketTally.Type[] {PacketTally.Type.ATTACK}),
            Map.entry(MovementAnalyzer.SPEED_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(MovementAnalyzer.SUSTAINED_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(MovementAnalyzer.ASCENT_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(MovementAnalyzer.HOVER_ID, new PacketTally.Type[] {PacketTally.Type.MOVEMENT}),
            Map.entry(ReachEvaluator.CHECK_ID, new PacketTally.Type[] {PacketTally.Type.ATTACK}));

    private static boolean enabled(Settings.Checks c, String id) {
        return switch (id) {
            case ProtocolValidator.INVALID_NUMERIC -> c.invalidValues().enabled();
            case ProtocolValidator.INVALID_FIELD, ProtocolValidator.SELF_ATTACK -> c.invalidFields().enabled();
            case ProtocolValidator.FLIGHT_TOGGLE -> c.flightToggle().enabled();
            case ProtocolValidator.WINDOW_MISMATCH -> c.inventory().enabled();
            case PacketRateTracker.MOVEMENT_CHECK_ID, PacketRateTracker.FLOOD_CHECK_ID -> c.packetRate().enabled();
            case ClickAnalyzer.CHECK_ID -> c.autoClicker().enabled();
            case MovementAnalyzer.SPEED_ID, MovementAnalyzer.SUSTAINED_ID, MovementAnalyzer.ASCENT_ID,
                 MovementAnalyzer.HOVER_ID -> c.movement().enabled();
            case ReachEvaluator.CHECK_ID -> c.reach().enabled();
            default -> false;
        };
    }

    /** Checks that pause during grace periods (the established protocol contradictions do not). */
    private static boolean graceSensitive(String id) {
        return switch (id) {
            case ProtocolValidator.INVALID_NUMERIC, ProtocolValidator.INVALID_FIELD, ProtocolValidator.SELF_ATTACK,
                 PacketRateTracker.FLOOD_CHECK_ID -> id.equals(PacketRateTracker.FLOOD_CHECK_ID);
            default -> true;
        };
    }

    /**
     * Builds the diagnostic view for one player.
     *
     * @param ctx                 current server-side context, {@code null} when none exists yet
     * @param packetObservation   whether the packet library is running (otherwise packet checks are unsupported)
     * @param eventObservation    whether server events are registered
     * @param droppedObservations observations dropped by the bounded queue (server wide)
     */
    public Diagnosis diagnose(UUID id, long nowNanos, PlayerContext ctx, boolean packetObservation,
                              boolean eventObservation, long droppedObservations) {
        Settings cfg = settings.get();
        PacketTally tally = tallies.get(id);
        PlayerState state = states.get(id);
        boolean exempt = exemptions.isExempt(id);
        GraceState.Reason grace = ctx == null ? null : ctx.grace().active(nowNanos, cfg.grace(), ctx.snapshot().tps());
        List<CheckDiagnosis> out = new ArrayList<>();
        for (String check : REQUIRES.keySet().stream().sorted().toList()) {
            out.add(diagnoseCheck(check, cfg, tally, state, exempt, grace, ctx, packetObservation, eventObservation));
        }
        long ago = tally == null || tally.lastNanos() == 0 ? -1 : Math.max(0, (nowNanos - tally.lastNanos()) / 1_000_000L);
        return new Diagnosis(tally == null ? Map.of() : tally.asMap(), ago, droppedObservations, out);
    }

    private CheckDiagnosis diagnoseCheck(String check, Settings cfg, PacketTally tally, PlayerState state,
                                         boolean exempt, GraceState.Reason grace, PlayerContext ctx,
                                         boolean packetObservation, boolean eventObservation) {
        long incidents = state == null || state.incidents.get(check) == null ? 0 : state.incidents.get(check).get();
        boolean reach = check.equals(ReachEvaluator.CHECK_ID);
        if (!enabled(cfg.checks(), check)) {
            return new CheckDiagnosis(check, CheckStatus.DISABLED, 0, 0, 0, incidents, "", "disabled in config.yml");
        }
        if (exempt) {
            return new CheckDiagnosis(check, CheckStatus.EXEMPT, 0, 0, 0, incidents, "", "see the exemption reason");
        }
        if (!packetObservation && !(reach && eventObservation)) {
            return new CheckDiagnosis(check, CheckStatus.UNSUPPORTED, 0, 0, 0, incidents, "",
                    "packet observation is not running on this server");
        }
        String group = check.startsWith("Movement") && !check.equals(PacketRateTracker.MOVEMENT_CHECK_ID) ? "Movement" : check;
        PlayerState.Stat stat = state == null ? null : state.stats.get(group);
        if (stat != null && stat.errors.get() > 0) {
            return new CheckDiagnosis(check, CheckStatus.ERROR, stat.evaluated.get(), 0, 0, incidents, "",
                    stat.errors.get() + " error(s), last: " + stat.lastError);
        }
        if (ctx == null) {
            return new CheckDiagnosis(check, CheckStatus.INSUFFICIENT_DATA, 0, 0, 0, incidents, "",
                    "missing input: no server-side snapshot of the player yet");
        }
        if (grace != null && graceSensitive(check)) {
            return new CheckDiagnosis(check, CheckStatus.GRACE_PERIOD, 0, 0, 0, incidents, "", "grace: " + grace.name());
        }
        boolean sawInput = false;
        for (PacketTally.Type t : REQUIRES.get(check)) {
            sawInput |= tally != null && tally.get(t) > 0;
        }
        long evaluated = stat == null ? 0 : stat.evaluated.get();
        int samples = 0;
        int needed = 0;
        String buffer = "";
        String note = "";
        switch (check) {
            case ClickAnalyzer.CHECK_ID -> {
                needed = cfg.checks().autoClicker().windowSamples();
                samples = state == null ? 0 : state.clicks.bufferedSamples();
                evaluated = state == null ? 0 : state.analyzedWindows;
                buffer = state == null ? "" : String.format(Locale.ROOT, "%.2f", state.clicks.bufferValue());
                note = state == null ? "" : state.lastWindowSummary;
            }
            case MovementAnalyzer.SPEED_ID, MovementAnalyzer.ASCENT_ID, MovementAnalyzer.HOVER_ID -> {
                evaluated = state == null ? 0 : state.movement.evaluatedPackets();
                buffer = state == null ? "" : state.movement.bufferSummary();
            }
            case MovementAnalyzer.SUSTAINED_ID -> {
                needed = state == null ? 0 : state.movement.sustainedWindow();
                samples = state == null ? 0 : state.movement.sustainedFill();
                evaluated = state == null ? 0 : state.movement.sustainedEvaluations();
            }
            default -> { }
        }
        if (reach && state != null) {
            evaluated = stat == null ? 0 : stat.evaluated.get();
            if (tally == null || tally.get(PacketTally.Type.ATTACK) == 0) {
                sawInput = false;
            } else {
                sawInput = true;
            }
        }
        if (!sawInput) {
            return new CheckDiagnosis(check, CheckStatus.INSUFFICIENT_DATA, evaluated, samples, needed, incidents, buffer,
                    "missing input: no " + describe(check) + " observed yet");
        }
        if (evaluated == 0) {
            return new CheckDiagnosis(check, CheckStatus.INSUFFICIENT_DATA, 0, samples, needed, incidents, buffer,
                    needed > 0 ? "collecting samples " + samples + "/" + needed : "no evaluation completed yet");
        }
        if (ctx.snapshot().pingMillis() < 0 && note.isEmpty()) {
            note = "latency unknown (tolerances assume none)";
        }
        return new CheckDiagnosis(check, CheckStatus.ACTIVE, evaluated, samples, needed, incidents, buffer, note);
    }

    private static String describe(String check) {
        return switch (check) {
            case ProtocolValidator.FLIGHT_TOGGLE -> "player-abilities packets";
            case ProtocolValidator.WINDOW_MISMATCH -> "container click packets";
            case ClickAnalyzer.CHECK_ID, ProtocolValidator.SELF_ATTACK, ReachEvaluator.CHECK_ID -> "attack packets";
            case ProtocolValidator.INVALID_FIELD -> "held-slot, container-click or attack packets";
            default -> "movement packets";
        };
    }
}
