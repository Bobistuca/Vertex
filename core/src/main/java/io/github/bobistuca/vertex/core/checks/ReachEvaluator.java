package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Compares the eye-to-hitbox distance of an attack attempt with the server-known interaction range plus an
 * explicit uncertainty. The attempt comes from the attack packet, so it is evaluated whether or not the server
 * ended up applying damage.
 *
 * <p>Uncertainty is derived, not arbitrary: the server measures a tick or more after the client acted, and the
 * client's view is older by about half the round-trip time, so both attacker and target may have moved by their
 * speed times that delay.
 */
public final class ReachEvaluator {
    public static final String CHECK_ID = "ReachDistance";

    private final Settings.Reach config;
    private final SuspicionBuffer buffer = new SuspicionBuffer();
    private long evaluated;

    public ReachEvaluator(Settings.Reach config) {
        this.config = config;
    }

    /**
     * Blocks by which the measured distance may legitimately differ from what the client saw.
     *
     * @param pingMillis      round-trip latency, negative if unknown (then only the processing delay counts)
     * @param targetSpeed     target speed in blocks per second
     * @param attackerSpeed   attacker speed in blocks per second
     */
    public double uncertainty(int pingMillis, double targetSpeed, double attackerSpeed) {
        double oneWay = pingMillis < 0 ? 0.0 : Math.min(pingMillis, config.latencyCapMillis()) / 2000.0;
        double delay = oneWay + config.processingDelayMillis() / 1000.0;
        return (Math.max(0, targetSpeed) + Math.max(0, attackerSpeed)) * delay;
    }

    public double allowed(double interactionRange, boolean creative, double uncertainty) {
        return interactionRange + (creative ? config.creativeBonus() : 0.0) + config.tolerance() + uncertainty;
    }

    public long evaluated() {
        return evaluated;
    }

    public double bufferValue() {
        return buffer.value();
    }

    public Optional<Finding> evaluate(double distance, double interactionRange, boolean creative, double uncertainty,
                                      int pingMillis, String targetType) {
        if (!Double.isFinite(distance)) {
            return Optional.empty();
        }
        evaluated++;
        double allowed = allowed(interactionRange, creative, uncertainty);
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
        m.put("uncertainty", Values.f(uncertainty, 2));
        m.put("pingMs", pingMillis < 0 ? "unknown" : Integer.toString(pingMillis));
        m.put("target", targetType);
        m.put("creative", Boolean.toString(creative));
        return Optional.of(new Finding(CHECK_ID, DetectionKind.SUSPICION, excess > 1.0 ? Confidence.HIGH : Confidence.MEDIUM,
                String.format(Locale.ROOT, "Repeated attack attempts from %.2f blocks, beyond the %.2f allowed by the "
                        + "server-known interaction range plus latency uncertainty.", distance, allowed), m));
    }

    public void reset() {
        buffer.reset();
    }
}
