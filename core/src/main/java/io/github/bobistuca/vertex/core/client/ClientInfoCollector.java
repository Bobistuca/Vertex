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

    private static final class Pending {
        final String name;
        final long startedNanos;
        String brand;
        Provenance brandProvenance = Provenance.UNKNOWN;
        int protocol = -1;
        Provenance protocolProvenance = Provenance.UNKNOWN;
        String versionName;
        Provenance versionProvenance = Provenance.UNKNOWN;
        final Set<String> channels = new LinkedHashSet<>();

        Pending(String name, long startedNanos) {
            this.name = name;
            this.startedNanos = startedNanos;
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

    public void begin(UUID id, String name, long nowNanos) {
        if (pending.size() < MAX_PENDING) {
            pending.putIfAbsent(id, new Pending(Sanitizer.clean(name, 32), nowNanos));
        }
    }

    public void brandPayload(UUID id, byte[] payload) {
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
            boolean timedOut = nowNanos - p.startedNanos >= timeoutNanos;
            synchronized (p) {
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
