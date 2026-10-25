package io.github.bobistuca.vertex.core.staff;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of who currently bypasses the gameplay checks. Packet threads and the analysis thread read it
 * with a single map lookup; it is written from the server thread when a rank, permission or context changes.
 * This is the one place every check consults, so all checks follow the same policy.
 */
public final class ExemptionRegistry {
    public enum Transition { GAINED, LOST, CHANGED, UNCHANGED }

    private final Map<UUID, Exemption> exempt = new ConcurrentHashMap<>();

    public boolean isExempt(UUID id) {
        return exempt.containsKey(id);
    }

    public Optional<Exemption> get(UUID id) {
        return Optional.ofNullable(exempt.get(id));
    }

    /** Stores the new state ({@code null} = not exempt) and reports what changed. */
    public Transition set(UUID id, Exemption next) {
        Exemption previous = next == null ? exempt.remove(id) : exempt.put(id, next);
        if (previous == null) {
            return next == null ? Transition.UNCHANGED : Transition.GAINED;
        }
        if (next == null) {
            return Transition.LOST;
        }
        return previous.equals(next) ? Transition.UNCHANGED : Transition.CHANGED;
    }

    public void forget(UUID id) {
        exempt.remove(id);
    }

    public int size() {
        return exempt.size();
    }
}
