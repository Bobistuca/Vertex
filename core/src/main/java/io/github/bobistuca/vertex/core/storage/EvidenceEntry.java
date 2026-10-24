package io.github.bobistuca.vertex.core.storage;

import java.util.Map;
import java.util.UUID;

/**
 * A persisted evidence record: a closed gameplay aggregation window, an administrative audit entry or a client
 * information report. Bounded fields only; no packet dumps.
 *
 * @param type     {@code DETECTION}, {@code ADMIN} or {@code CLIENT}
 * @param kind     {@code ESTABLISHED}, {@code SUSPICION} or {@code ADMIN}
 * @param count    accurate incident total (always at least 1)
 * @param suppressed whether the chat message was rate-limited (the record is still kept)
 */
public record EvidenceEntry(long wallClockMillis, String type, UUID playerId, String playerName, String checkId,
                            String kind, String confidence, int count, boolean suppressed, String reason,
                            Map<String, String> data) {

    public EvidenceEntry {
        data = Map.copyOf(data);
    }
}
