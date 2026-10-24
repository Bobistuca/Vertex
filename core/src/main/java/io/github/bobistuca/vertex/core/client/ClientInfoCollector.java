package io.github.bobistuca.vertex.core.client;

import io.github.bobistuca.vertex.core.i18n.Sanitizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects brand, protocol and plugin-channel information per joining player and releases a {@link ClientReport}
 * once the data is complete or a bounded timeout elapses. Thread-safe; bounded by the number of joining players.
 */
public final class ClientInfoCollector {
    private static final int MAX_PENDING = 2048;
    /** Data that arrives before the join event (configuration phase) is kept at most this long. */
    private static final long EARLY_NANOS = 30_000_000_000L;

    private static final class Pending {
        String name;
        long startedNanos;
        final long createdNanos;
        boolean joined;
        String brand;
        Provenance brandProvenance = Provenance.UNKNOWN;
        int protocol = -1;
        Provenance protocolProvenance = Provenance.UNKNOWN;
        String versionName;
        Provenance versionProvenance = Provenance.UNKNOWN;
        final Set<String> channels = new LinkedHashSet<>();

        Pending(String name, long nowNanos) {
            this.name = name;
            this.startedNanos = nowNanos;
            this.createdNanos = nowNanos;
        }

        boolean complete() {
            return brand != null && protocol >= 0;
        }
    }

    private final long timeoutNanos;
    private final int maxChannels;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ClientInfoCollector(long timeoutMillis, int maxChannels) {
        this.timeoutNanos = timeoutMillis * 1_000_000L;
        this.maxChannels = maxChannels;
    }

    /** Marks the join; the timeout counts from here. Data received earlier is kept. */
    public void begin(UUID id, String name, long nowNanos) {
        Pending p = pending.get(id);
        if (p == null && pending.size() < MAX_PENDING) {
            p = new Pending(Sanitizer.clean(name, 32), nowNanos);
            Pending existing = pending.putIfAbsent(id, p);
            if (existing != null) {
                p = existing;
            }
        }
        if (p != null) {
            synchronized (p) {
                p.name = Sanitizer.clean(name, 32);
                p.startedNanos = nowNanos;
                p.joined = true;
            }
        }
    }

    /** Early (pre-join) data: creates the pending entry on first sight. */
    private Pending early(UUID id, long nowNanos) {
        Pending p = pending.get(id);
        if (p == null && pending.size() < MAX_PENDING) {
            p = new Pending("", nowNanos);
            Pending existing = pending.putIfAbsent(id, p);
            if (existing != null) {
                p = existing;
            }
        }
        return p;
    }

    /** Players that joined but still lack a brand (candidates for a server-API brand lookup). */
    public List<UUID> awaitingBrand() {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, Pending> e : pending.entrySet()) {
            if (e.getValue().joined && e.getValue().brand == null) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    public void brandPayload(UUID id, byte[] payload, long nowNanos) {
        early(id, nowNanos);
        brandText(id, BrandParser.parse(payload), Provenance.REPORTED);
    }

    public void brandText(UUID id, String brand, Provenance provenance) {
        Pending p = pending.get(id);
        if (p != null && p.brand == null && brand != null && !brand.isEmpty()) {
            synchronized (p) {
                p.brand = Sanitizer.clean(brand, BrandParser.MAX_BRAND_CHARS);
                p.brandProvenance = provenance;
            }
        }
    }

    /** The protocol number comes from the handshake (reported); the release name is derived (inferred). */
    public void protocol(UUID id, int protocol, String releaseName) {
        Pending p = pending.get(id);
        if (p != null && protocol >= 0) {
            synchronized (p) {
                p.protocol = protocol;
                p.protocolProvenance = Provenance.REPORTED;
                if (releaseName != null && !releaseName.isEmpty()) {
                    p.versionName = Sanitizer.clean(releaseName, 24);
                    p.versionProvenance = Provenance.INFERRED;
                }
            }
        }
    }

    public void channel(UUID id, String channel) {
        Pending p = pending.get(id);
        if (p != null && channel != null) {
            synchronized (p) {
                if (p.channels.size() < maxChannels) {
                    p.channels.add(Sanitizer.clean(channel, 64));
                }
            }
        }
    }

    /** Returns and removes every report that is complete or timed out. */
    public List<ClientReport> poll(long nowNanos) {
        List<ClientReport> ready = new ArrayList<>();
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending p = entry.getValue();
            synchronized (p) {
                if (!p.joined) {
                    if (nowNanos - p.createdNanos >= EARLY_NANOS) {
                        it.remove();
                    }
                    continue;
                }
                boolean timedOut = nowNanos - p.startedNanos >= timeoutNanos;
                if (p.complete() || timedOut) {
                    it.remove();
                    ready.add(new ClientReport(entry.getKey(), p.name,
                            p.brand == null ? "" : p.brand, p.brand == null ? Provenance.UNKNOWN : p.brandProvenance,
                            p.protocol, p.protocolProvenance,
                            p.versionName == null ? "" : p.versionName, p.versionProvenance,
                            List.copyOf(p.channels), !p.complete()));
                }
            }
        }
        return ready;
    }

    public void remove(UUID id) {
        pending.remove(id);
    }

    public int pendingCount() {
        return pending.size();
    }
}
