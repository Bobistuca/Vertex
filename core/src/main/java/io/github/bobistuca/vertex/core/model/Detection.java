package io.github.bobistuca.vertex.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One incident produced by a check.
 *
 * @param playerId   subject of the observation
 * @param playerName last known name (untrusted text; always escaped when rendered)
 * @param checkId    stable English check identifier
 * @param kind       established contradiction or statistical suspicion
 * @param confidence documented confidence label
 * @param reason     explainable English reason stored with the evidence
 * @param measurements relevant measurements (ordered, immutable)
 * @param wallClockMillis ordinary timestamp for evidence records
 * @param dedupKey   optional key that identifies the underlying observation, so that the packet and event
 *                   listeners observing the same action count once; {@code null} when not applicable
 */
public record Detection(
        UUID playerId,
        String playerName,
        String checkId,
        DetectionKind kind,
        Confidence confidence,
        String reason,
        Map<String, String> measurements,
        long wallClockMillis,
        String dedupKey) {

    public Detection {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(confidence, "confidence");
        playerName = playerName == null ? "unknown" : playerName;
        reason = reason == null ? "" : reason;
        measurements = measurements == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(measurements));
    }
}
