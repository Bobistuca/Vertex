package io.github.bobistuca.vertex.core.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe cache of who currently bypasses the gameplay checks, plus per-player test mode. Packet threads and
 * the analysis thread read it with a single lookup; it is written from the server thread when a rank, permission or
 * context changes. This is the one place every check consults, so all checks follow the same policy.
 *
 * <p>Test mode overrides the exemption decision for one player for a limited time: checks run through the real
 * pipeline even though the underlying exemption (and the player's OP, ranks and permissions) stays untouched.
 */
public final class ExemptionRegistry {
    public enum Transition { GAINED, LOST, CHANGED, UNCHANGED }

    /** A player whose test mode expired, with the resulting effective-state transition. */
    public record Expired(UUID id, Transition transition) { }

    private final Map<UUID, Exemption> exempt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> testModeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> membership = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;

    public ExemptionRegistry() {
        this(System::nanoTime);
    }

    public ExemptionRegistry(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    // ------------------------------------------------------------------ effective state

    /** Effective: an exemption exists and test mode is not overriding it. */
    public boolean isExempt(UUID id) {
        return exempt.containsKey(id) && !testModeActive(id);
    }

    /** The exemption that would apply without test mode. */
    public Optional<Exemption> underlying(UUID id) {
        return Optional.ofNullable(exempt.get(id));
    }

    /** The exemption in effect right now (empty while test mode overrides it). */
    public Optional<Exemption> get(UUID id) {
        return isExempt(id) ? underlying(id) : Optional.empty();
    }

    /** Stores the underlying exemption ({@code null} = none) and reports the change of the effective state. */
    public Transition set(UUID id, Exemption next) {
        Exemption previous = next == null ? exempt.remove(id) : exempt.put(id, next);
        if (testModeActive(id)) {
            return Transition.UNCHANGED; // effective state is "monitored" either way
        }
        if (previous == null) {
            return next == null ? Transition.UNCHANGED : Transition.GAINED;
        }
        if (next == null) {
            return Transition.LOST;
        }
        return previous.equals(next) ? Transition.UNCHANGED : Transition.CHANGED;
    }

    // ------------------------------------------------------------------ test mode

    /** Starts test mode. Returns LOST when the player was effectively exempt (monitoring resumes). */
    public Transition enableTestMode(UUID id, long durationMillis) {
        boolean wasExempt = isExempt(id);
        testModeUntil.put(id, nanoClock.getAsLong() + durationMillis * 1_000_000L);
        return wasExempt ? Transition.LOST : Transition.UNCHANGED;
    }

    /** Ends test mode. Returns GAINED when the underlying exemption applies again. */
    public Transition disableTestMode(UUID id) {
        boolean wasActive = testModeActive(id);
        testModeUntil.remove(id);
        return wasActive && exempt.containsKey(id) ? Transition.GAINED : Transition.UNCHANGED;
    }

    public boolean testModeActive(UUID id) {
        Long until = testModeUntil.get(id);
        return until != null && nanoClock.getAsLong() - until < 0;
    }

    public long testModeRemainingMillis(UUID id) {
        Long until = testModeUntil.get(id);
        return until == null ? 0 : Math.max(0, (until - nanoClock.getAsLong()) / 1_000_000L);
    }

    /** Removes expired test modes; call periodically on the server thread. */
    public List<Expired> expireTestModes() {
        List<Expired> out = new ArrayList<>();
        long now = nanoClock.getAsLong();
        for (Map.Entry<UUID, Long> e : testModeUntil.entrySet()) {
            if (now - e.getValue() >= 0 && testModeUntil.remove(e.getKey(), e.getValue())) {
                out.add(new Expired(e.getKey(), exempt.containsKey(e.getKey()) ? Transition.GAINED : Transition.UNCHANGED));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ recognition

    public void setMembership(UUID id, String group) {
        if (group == null) {
            membership.remove(id);
        } else {
            membership.put(id, group);
        }
    }

    /** The configured administrative group the player belongs to (recognition only, independent of exemption). */
    public Optional<String> membership(UUID id) {
        return Optional.ofNullable(membership.get(id));
    }

    /** Session cleanup: logout clears the exemption, test mode and recognition state. */
    public void forget(UUID id) {
        exempt.remove(id);
        testModeUntil.remove(id);
        membership.remove(id);
    }

    public int size() {
        return (int) exempt.keySet().stream().filter(this::isExempt).count();
    }

    public int testModeCount() {
        return (int) testModeUntil.keySet().stream().filter(this::testModeActive).count();
    }
}
