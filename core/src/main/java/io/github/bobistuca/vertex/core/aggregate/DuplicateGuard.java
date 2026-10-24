package io.github.bobistuca.vertex.core.aggregate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded memory of recently seen observation keys. It prevents one underlying action from being counted twice
 * when both a packet listener and an event listener report it.
 */
public final class DuplicateGuard {
    private final long ttlNanos;
    private final int maxEntries;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    public DuplicateGuard(long ttlNanos, int maxEntries) {
        if (ttlNanos <= 0 || maxEntries <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.ttlNanos = ttlNanos;
        this.maxEntries = maxEntries;
    }

    /** Returns {@code true} the first time a key is seen within the time-to-live, {@code false} for duplicates. */
    public synchronized boolean firstSeen(String key, long nowNanos) {
        expire(nowNanos);
        Long previous = seen.get(key);
        if (previous != null && nowNanos - previous < ttlNanos) {
            return false;
        }
        seen.remove(key);
        seen.put(key, nowNanos);
        while (seen.size() > maxEntries) {
            Iterator<String> it = seen.keySet().iterator();
            it.next();
            it.remove();
        }
        return true;
    }

    private void expire(long nowNanos) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (nowNanos - it.next().getValue() >= ttlNanos) {
                it.remove();
            } else {
                break; // insertion order is time order
            }
        }
    }

    public synchronized int size() {
        return seen.size();
    }
}
