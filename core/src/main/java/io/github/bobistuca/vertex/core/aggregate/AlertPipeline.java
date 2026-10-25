package io.github.bobistuca.vertex.core.aggregate;

import io.github.bobistuca.vertex.core.audit.AuditOutcome;
import io.github.bobistuca.vertex.core.audit.AuditRecord;
import io.github.bobistuca.vertex.core.client.ClientReport;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.storage.EvidenceEntry;
import io.github.bobistuca.vertex.core.storage.EvidenceStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Accepts detections from any thread, aggregates them per player and check, applies rate limits and records the
 * evidence. Closed windows are delivered by {@link #drainDue()} on the server thread. Every incident total is
 * preserved in the evidence even when the chat message is suppressed.
 */
public final class AlertPipeline {
    private static final int MAX_GROUPS = 4096;
    private static final int MAX_LIVE = 256;

    /** A live counter update for the optional action bar. */
    public record LiveUpdate(UUID playerId, String playerName, String checkId, int count) { }

    private final Supplier<Settings> settings;
    private final EvidenceStore store;
    private final LongSupplier nanoClock;
    private final java.util.function.Predicate<UUID> exempt;
    private final Object lock = new Object();

    private AlertAggregator aggregator;
    private long aggregatorWindow = -1;
    private RateLimiter perPlayer;
    private RateLimiter perRecipient;
    private int perPlayerLimit = -1;
    private int perRecipientLimit = -1;

    private final ConcurrentLinkedQueue<LiveUpdate> live = new ConcurrentLinkedQueue<>();
    private final AtomicInteger liveSize = new AtomicInteger();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong exemptDropped = new AtomicLong();

    public AlertPipeline(Supplier<Settings> settings, EvidenceStore store, LongSupplier nanoClock,
                         java.util.function.Predicate<UUID> exempt) {
        this.settings = settings;
        this.store = store;
        this.nanoClock = nanoClock;
        this.exempt = exempt;
    }

    /** Thread-safe. Returns false when the incident was a duplicate observation and was not counted. */
    public boolean submit(Detection detection) {
        if (exempt.test(detection.playerId())) {
            exemptDropped.incrementAndGet();
            return false; // administrative staff never accumulate violations
        }
        submitted.incrementAndGet();
        AlertAggregator.Offer offer;
        synchronized (lock) {
            offer = aggregatorFor().offer(detection, nanoClock.getAsLong());
        }
        if (offer.outcome() == AlertAggregator.Outcome.DUPLICATE) {
            duplicates.incrementAndGet();
            return false;
        }
        if (offer.outcome() == AlertAggregator.Outcome.DROPPED_FULL) {
            persist(new AggregatedAlert(detection, detection, 1, settings.get().alerts().windowMillis(), true));
            return true;
        }
        if (settings.get().alerts().actionBar().enabled() && liveSize.get() < MAX_LIVE) {
            live.add(new LiveUpdate(detection.playerId(), detection.playerName(), detection.checkId(), offer.count()));
            liveSize.incrementAndGet();
        }
        return true;
    }

    /**
     * Server thread. Closes windows that are due, records their evidence and returns the alerts that may be shown
     * (windows suppressed by the per-player rate limit are recorded but not returned).
     */
    public List<AggregatedAlert> drainDue() {
        List<AggregatedAlert> due;
        synchronized (lock) {
            due = aggregatorFor().drainDue(nanoClock.getAsLong());
        }
        return process(due);
    }

    /** Shutdown / session cleanup: closes everything that is still open. */
    public List<AggregatedAlert> drainAll() {
        List<AggregatedAlert> all;
        synchronized (lock) {
            all = aggregatorFor().drainAll();
        }
        return process(all);
    }

    public List<AggregatedAlert> clearPlayer(UUID player) {
        List<AggregatedAlert> removed;
        synchronized (lock) {
            removed = aggregatorFor().clearPlayer(player);
        }
        return process(removed);
    }

    /**
     * Discards the pending (not yet delivered) gameplay alerts and live counters of a player, for example when the
     * player gains an exempt rank. Evidence that was already persisted is kept.
     */
    public void discardPlayer(UUID player) {
        synchronized (lock) {
            aggregatorFor().clearPlayer(player);
        }
        live.removeIf(u -> {
            boolean mine = u.playerId().equals(player);
            if (mine) {
                liveSize.decrementAndGet();
            }
            return mine;
        });
    }

    public List<LiveUpdate> drainLive() {
        List<LiveUpdate> out = new ArrayList<>();
        LiveUpdate u;
        while ((u = live.poll()) != null) {
            liveSize.decrementAndGet();
            out.add(u);
        }
        return out;
    }

    /** Whether one more chat alert may be sent to this recipient right now. */
    public boolean allowForRecipient(UUID recipient) {
        synchronized (lock) {
            limiters();
            return perRecipient.tryAcquire(recipient.toString(), nanoClock.getAsLong());
        }
    }

    public void forgetRecipient(UUID recipient) {
        synchronized (lock) {
            limiters();
            perRecipient.forget(recipient.toString());
            perPlayer.forget(recipient.toString());
        }
    }

    private List<AggregatedAlert> process(List<AggregatedAlert> closed) {
        List<AggregatedAlert> deliverable = new ArrayList<>(closed.size());
        for (AggregatedAlert alert : closed) {
            boolean allowed;
            synchronized (lock) {
                limiters();
                allowed = perPlayer.tryAcquire(alert.first().playerId().toString(), nanoClock.getAsLong());
            }
            AggregatedAlert finalAlert = alert.withSuppressed(!allowed);
            persist(finalAlert);
            if (allowed) {
                delivered.incrementAndGet();
                deliverable.add(finalAlert);
            } else {
                suppressed.incrementAndGet();
            }
        }
        return deliverable;
    }

    private void persist(AggregatedAlert alert) {
        if (store == null) {
            return;
        }
        Detection first = alert.first();
        Map<String, String> data = new LinkedHashMap<>(first.measurements());
        data.put("firstAtMillis", Long.toString(first.wallClockMillis()));
        data.put("lastAtMillis", Long.toString(alert.latest().wallClockMillis()));
        data.put("windowMillis", Long.toString(alert.windowMillis()));
        store.add(new EvidenceEntry(alert.latest().wallClockMillis(), "DETECTION", first.playerId(),
                first.playerName(), first.checkId(), first.kind().name(), first.confidence().name(), alert.count(),
                alert.suppressed(), first.reason(), data));
    }

    /** Persists an audit entry (separate type from gameplay detections). */
    public void recordAudit(AuditRecord r) {
        if (store == null) {
            return;
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("outcome", r.outcome().name());
        data.put("before", r.before());
        data.put("after", r.after());
        data.put("actor", r.actor().name());
        data.put("actorKind", r.actor().kind().name());
        data.put("actorEstablished", Boolean.toString(r.actorEstablished()));
        data.put("severity", r.severity().name());
        if (!r.command().isEmpty()) {
            data.put("command", r.command());
        }
        store.add(new EvidenceEntry(r.wallClockMillis(), "ADMIN", r.subjectId(), r.subjectName(),
                r.aspect().checkId(), "ADMIN", r.outcome() == AuditOutcome.CHANGED ? r.severity().name() : "INFO", 1,
                false, "Administrative state audit entry.", data));
    }

    /** Persists the join-time client report. */
    public void recordClient(ClientReport r, long wallMillis) {
        if (store == null) {
            return;
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("brand", r.brand());
        data.put("brandProvenance", r.brandProvenance().name());
        data.put("protocol", Integer.toString(r.protocol()));
        data.put("protocolProvenance", r.protocolProvenance().name());
        data.put("version", r.versionName());
        data.put("channels", String.join(",", r.channels()));
        data.put("timedOut", Boolean.toString(r.timedOut()));
        data.put("injectionStatus", ClientReport.INJECTION_STATUS);
        store.add(new EvidenceEntry(wallMillis, "CLIENT", r.playerId(), r.playerName(), "ClientInfo", "INFO", "INFO",
                1, false, "Client information reported at join. Untrusted data.", data));
    }

    private AlertAggregator aggregatorFor() {
        long window = settings.get().alerts().windowMillis();
        if (aggregator == null) {
            aggregator = new AlertAggregator(window, MAX_GROUPS, new DuplicateGuard(10_000_000_000L, 8192));
            aggregatorWindow = window;
        } else if (window != aggregatorWindow) {
            // Window length changed by a reload: close the old groups so no incident is lost.
            List<AggregatedAlert> old = aggregator.drainAll();
            aggregator = new AlertAggregator(window, MAX_GROUPS, new DuplicateGuard(10_000_000_000L, 8192));
            aggregatorWindow = window;
            for (AggregatedAlert a : old) {
                persist(a);
            }
        }
        return aggregator;
    }

    private void limiters() {
        Settings.Alerts a = settings.get().alerts();
        if (perPlayer == null || perPlayerLimit != a.maxPerPlayerPerMinute()) {
            perPlayer = new RateLimiter(a.maxPerPlayerPerMinute(), 60_000, 8192);
            perPlayerLimit = a.maxPerPlayerPerMinute();
        }
        if (perRecipient == null || perRecipientLimit != a.maxPerRecipientPerMinute()) {
            perRecipient = new RateLimiter(a.maxPerRecipientPerMinute(), 60_000, 8192);
            perRecipientLimit = a.maxPerRecipientPerMinute();
        }
    }

    public long submitted() { return submitted.get(); }
    public long duplicates() { return duplicates.get(); }
    public long suppressed() { return suppressed.get(); }
    public long exemptDropped() { return exemptDropped.get(); }
    public long delivered() { return delivered.get(); }

    public int openGroups() {
        synchronized (lock) {
            return aggregatorFor().openGroups();
        }
    }
}
