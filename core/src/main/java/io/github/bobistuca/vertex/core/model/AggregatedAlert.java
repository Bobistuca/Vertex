package io.github.bobistuca.vertex.core.model;

/**
 * Result of one closed aggregation window for a (player, check) pair.
 *
 * @param first      the first incident of the window (representative measurements)
 * @param latest     the most recent incident of the window
 * @param count      accurate number of qualifying incidents in the window
 * @param windowMillis configured window length used
 * @param suppressed whether the chat message was suppressed by a rate limit (evidence is still recorded)
 */
public record AggregatedAlert(Detection first, Detection latest, int count, long windowMillis, boolean suppressed) {

    public AggregatedAlert withSuppressed(boolean value) {
        return new AggregatedAlert(first, latest, count, windowMillis, value);
    }
}
