package io.github.bobistuca.vertex.core.aggregate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Bounded fixed-window rate limiter keyed by string. */
public final class RateLimiter {
    private record Window(long startNanos, int used) { }

    private final int maxPerWindow;
    private final long windowNanos;
    private final int maxKeys;
    private final Map<String, Window> windows = new HashMap<>();

    public RateLimiter(int maxPerWindow, long windowMillis, int maxKeys) {
        this.maxPerWindow = maxPerWindow;
        this.windowNanos = windowMillis * 1_000_000L;
        this.maxKeys = maxKeys;
    }

    /** A non-positive limit disables limiting. */
    public synchronized boolean tryAcquire(String key, long nowNanos) {
        if (maxPerWindow <= 0) {
            return true;
        }
        if (windows.size() > maxKeys) {
            prune(nowNanos);
        }
        Window window = windows.get(key);
        if (window == null || nowNanos - window.startNanos() >= windowNanos) {
            windows.put(key, new Window(nowNanos, 1));
            return true;
        }
        if (window.used() >= maxPerWindow) {
            return false;
        }
        windows.put(key, new Window(window.startNanos(), window.used() + 1));
        return true;
    }

    private void prune(long nowNanos) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (nowNanos - it.next().getValue().startNanos() >= windowNanos) {
                it.remove();
            }
        }
        if (windows.size() > maxKeys) {
            windows.clear();
        }
    }

    public synchronized void forget(String key) {
        windows.remove(key);
    }
}
