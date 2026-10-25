package io.github.bobistuca.vertex.core.engine;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

/** Per-player packet counters by relevant type; fixed size, no packet contents are kept. */
final class PacketTally {
    enum Type { MOVEMENT, ATTACK, SWING, DIGGING, CONTAINER_CLICK, HELD_SLOT, ABILITIES }

    private final AtomicLongArray counts = new AtomicLongArray(Type.values().length);
    private volatile long lastNanos;

    void count(Type type, long nanos) {
        counts.incrementAndGet(type.ordinal());
        lastNanos = nanos;
    }

    long get(Type type) {
        return counts.get(type.ordinal());
    }

    long lastNanos() {
        return lastNanos;
    }

    Map<String, Long> asMap() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (Type t : Type.values()) {
            out.put(t.name().toLowerCase(java.util.Locale.ROOT), counts.get(t.ordinal()));
        }
        return out;
    }
}
