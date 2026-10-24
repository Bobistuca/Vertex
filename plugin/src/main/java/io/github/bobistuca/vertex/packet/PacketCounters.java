package io.github.bobistuca.vertex.packet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Lock-free per-player inbound packet counters, read and reset once per second by the main thread. */
public final class PacketCounters {
    private static final int MAX_PLAYERS = 4096;

    private final Map<UUID, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** Network thread. */
    public void increment(UUID id) {
        AtomicInteger counter = counters.get(id);
        if (counter == null) {
            if (counters.size() >= MAX_PLAYERS) {
                return;
            }
            counter = counters.computeIfAbsent(id, k -> new AtomicInteger());
        }
        counter.incrementAndGet();
    }

    /** Returns the count since the last call and resets it. */
    public int drain(UUID id) {
        AtomicInteger counter = counters.get(id);
        return counter == null ? 0 : counter.getAndSet(0);
    }

    public void remove(UUID id) {
        counters.remove(id);
    }
}
