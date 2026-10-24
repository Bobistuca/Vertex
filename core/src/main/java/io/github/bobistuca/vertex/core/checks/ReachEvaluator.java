package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Compares the eye-to-hitbox distance of an attack with the server-known interaction range plus tolerances for
 * latency and target movement. Distances come from server-side entity state on the server thread.
 */
public final class ReachEvaluator {
    public static final String CHECK_ID = "ReachDistance";

    private final Settings.Reach config;
    private final SuspicionBuffer buffer = new SuspicionBuffer();

    public ReachEvaluator(Settings.Reach config) {
        this.config = config;
    }

    /** Maximum distance tolerated for this attack. */
    public double allowed(double interactionRange, boolean creative, int pingMillis) {
        double latency = pingMillis < 0 ? 0.0
                : Math.min(pingMillis, config.latencyCapMillis()) / 1000.0 * config.latencyBlocksPerSecond();
        return interactionRange + (creative ? config.creativeBonus() : 0.0) + config.tolerance() + latency;
    }

    public Optional<Finding> evaluate(double distance, double interactionRange, boolean creative, int pingMillis,
                                      String targetType) {
        double allowed = allowed(interactionRange, creative, pingMillis);
        if (!Double.isFinite(distance)) {
            return Optional.empty();
        }
        if (distance <= allowed) {
            buffer.decay(config.bufferDecay());
            return Optional.empty();
        }
        double excess = distance - allowed;
        if (!buffer.add(1.0 + Math.min(2.0, excess), config.bufferThreshold())) {
            return Optional.empty();
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("distance", Values.f(distance, 2));
        m.put("allowed", Values.f(allowed, 2));
        m.put("interactionRange", Values.f(interactionRange, 2));
        m.put("pingMs", pingMillis < 0 ? "unknown" : Integer.toString(pingMillis));
        m.put("target", targetType);
        return Optional.of(new Finding(CHECK_ID, DetectionKind.SUSPICION, excess > 1.5 ? Confidence.HIGH : Confidence.MEDIUM,
                "Repeated attacks landed from beyond the server-known interaction range plus latency tolerance.", m));
    }

    public void reset() {
        buffer.reset();
    }
}
