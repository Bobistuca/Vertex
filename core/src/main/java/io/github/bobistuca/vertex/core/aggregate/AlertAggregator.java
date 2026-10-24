package io.github.bobistuca.vertex.core.aggregate;

import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.model.Detection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groups incidents by player UUID and check identifier inside a fixed window that opens at the first incident.
 * Chat messages cannot be edited after they are sent, so a group is emitted once, when its window closes.
 * State is bounded and can be cleaned per player session.
 */
public final class AlertAggregator {

    /** Outcome of offering one incident. */
    public enum Outcome { NEW_GROUP, JOINED_GROUP, DUPLICATE, DROPPED_FULL }

    /** Snapshot returned for live (action-bar) counters. */
    public record Offer(Outcome outcome, int count) { }

    private record Key(UUID player, String check) { }

    private static final class Group {
        final Detection first;
        Detection latest;
        int count;
        final long closesAtNanos;

        Group(Detection first, long closesAtNanos) {
            this.first = first;
            this.latest = first;
            this.count = 1;
            this.closesAtNanos = closesAtNanos;
        }
    }

    private final long windowNanos;
    private final long windowMillis;
    private final int maxGroups;
    private final DuplicateGuard duplicateGuard;
    private final Map<Key, Group> groups = new LinkedHashMap<>();
    private final List<AggregatedAlert> closed = new ArrayList<>();
    private long droppedGroups;

    public AlertAggregator(long windowMillis, int maxGroups, DuplicateGuard duplicateGuard) {
        if (windowMillis <= 0 || maxGroups <= 0) {
            throw new IllegalArgumentException("window and size must be positive");
        }
        this.windowMillis = windowMillis;
        this.windowNanos = windowMillis * 1_000_000L;
        this.maxGroups = maxGroups;
        this.duplicateGuard = duplicateGuard;
    }

    /** Offers one incident. {@code nowNanos} must come from a monotonic clock. */
    public synchronized Offer offer(Detection detection, long nowNanos) {
        if (detection.dedupKey() != null) {
            String key = detection.playerId() + "|" + detection.checkId() + "|" + detection.dedupKey();
            if (!duplicateGuard.firstSeen(key, nowNanos)) {
                return new Offer(Outcome.DUPLICATE, 0);
            }
        }
        Key key = new Key(detection.playerId(), detection.checkId());
        Group group = groups.get(key);
        if (group != null && nowNanos < group.closesAtNanos) {
            group.count++;
            group.latest = detection;
            return new Offer(Outcome.JOINED_GROUP, group.count);
        }
        if (group != null) {
            // The window closed before the next drain: retire it intact and start a fresh window.
            closed.add(new AggregatedAlert(group.first, group.latest, group.count, windowMillis, false));
            groups.remove(key);
        }
        if (groups.size() >= maxGroups) {
            droppedGroups++;
            return new Offer(Outcome.DROPPED_FULL, 0);
        }
        groups.put(key, new Group(detection, nowNanos + windowNanos));
        return new Offer(Outcome.NEW_GROUP, 1);
    }

    /** Removes and returns every group whose window has closed. */
    public synchronized List<AggregatedAlert> drainDue(long nowNanos) {
        List<AggregatedAlert> due = new ArrayList<>(closed);
        closed.clear();
        Iterator<Map.Entry<Key, Group>> it = groups.entrySet().iterator();
        while (it.hasNext()) {
            Group group = it.next().getValue();
            if (nowNanos >= group.closesAtNanos) {
                due.add(new AggregatedAlert(group.first, group.latest, group.count, windowMillis, false));
                it.remove();
            }
        }
        return due;
    }

    /** Emits everything that is still open (used on shutdown so no evidence is lost). */
    public synchronized List<AggregatedAlert> drainAll() {
        List<AggregatedAlert> all = new ArrayList<>(closed);
        closed.clear();
        for (Group group : groups.values()) {
            all.add(new AggregatedAlert(group.first, group.latest, group.count, windowMillis, false));
        }
        groups.clear();
        return all;
    }

    /** Session cleanup: forget open groups for a player (they are returned so the evidence is not lost). */
    public synchronized List<AggregatedAlert> clearPlayer(UUID player) {
        List<AggregatedAlert> removed = new ArrayList<>();
        closed.removeIf(alert -> {
            if (alert.first().playerId().equals(player)) {
                removed.add(alert);
                return true;
            }
            return false;
        });
        Iterator<Map.Entry<Key, Group>> it = groups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Group> entry = it.next();
            if (entry.getKey().player().equals(player)) {
                Group group = entry.getValue();
                removed.add(new AggregatedAlert(group.first, group.latest, group.count, windowMillis, false));
                it.remove();
            }
        }
        return removed;
    }

    public synchronized int openGroups() {
        return groups.size();
    }

    public synchronized long droppedGroups() {
        return droppedGroups;
    }
}
