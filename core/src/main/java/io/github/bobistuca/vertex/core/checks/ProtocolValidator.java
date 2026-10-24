package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Established contradictions: values and states that an unmodified client cannot produce. These may be reported
 * from a single packet because they are protocol facts, not statistics. They are still only observations.
 */
public final class ProtocolValidator {
    public static final String INVALID_NUMERIC = "InvalidNumericValue";
    public static final String INVALID_FIELD = "InvalidPacketField";
    public static final String SELF_ATTACK = "SelfAttack";
    public static final String FLIGHT_TOGGLE = "ImpossibleFlightToggle";
    public static final String WINDOW_MISMATCH = "InventoryWindowMismatch";

    /** Vanilla servers clamp and reject beyond these magnitudes. */
    private static final double MAX_HORIZONTAL = 3.0E7;
    private static final double MAX_VERTICAL = 2.0E7;

    private ProtocolValidator() { }

    public static Optional<Finding> validateLocation(boolean hasPosition, double x, double y, double z,
                                                      boolean hasRotation, float yaw, float pitch) {
        if (hasPosition) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return Optional.of(numeric("non-finite coordinate", x, y, z, yaw, pitch));
            }
            if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL || Math.abs(y) > MAX_VERTICAL) {
                return Optional.of(numeric("coordinate beyond the representable world", x, y, z, yaw, pitch));
            }
        }
        if (hasRotation) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                return Optional.of(numeric("non-finite rotation", x, y, z, yaw, pitch));
            }
            if (Math.abs(pitch) > 90.0f) {
                return Optional.of(numeric("pitch outside the -90 to 90 range", x, y, z, yaw, pitch));
            }
        }
        return Optional.empty();
    }

    private static Finding numeric(String what, double x, double y, double z, float yaw, float pitch) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("x", Values.f(x, 3));
        m.put("y", Values.f(y, 3));
        m.put("z", Values.f(z, 3));
        m.put("yaw", Values.f(yaw, 2));
        m.put("pitch", Values.f(pitch, 2));
        return new Finding(INVALID_NUMERIC, DetectionKind.ESTABLISHED, Confidence.HIGH,
                "The position/rotation packet carried a " + what + ", which an unmodified client does not send.", m);
    }

    public static Optional<Finding> validateHeldSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            return Optional.empty();
        }
        return Optional.of(field("held-item slot outside the hotbar (0-8)", Map.of("slot", Integer.toString(slot))));
    }

    /** Valid slot values are -999 (outside), -1, and non-negative indexes. */
    public static Optional<Finding> validateClickSlot(int windowId, int slot) {
        boolean ok = slot == -999 || slot == -1 || (slot >= 0 && slot <= 32767);
        if (windowId < 0 || windowId > 255 || !ok) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("window", Integer.toString(windowId));
            m.put("slot", Integer.toString(slot));
            return Optional.of(field("inventory click with an impossible window id or slot", m));
        }
        return Optional.empty();
    }

    public static Optional<Finding> validateEntityId(int entityId, int ownEntityId) {
        if (entityId < 0) {
            return Optional.of(field("negative entity id in an interaction", Map.of("entityId", Integer.toString(entityId))));
        }
        if (ownEntityId > 0 && entityId == ownEntityId) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("entityId", Integer.toString(entityId));
            return Optional.of(new Finding(SELF_ATTACK, DetectionKind.ESTABLISHED, Confidence.HIGH,
                    "The client attacked its own entity, which an unmodified client cannot do.", m));
        }
        return Optional.empty();
    }

    private static Finding field(String what, Map<String, String> measurements) {
        return new Finding(INVALID_FIELD, DetectionKind.ESTABLISHED, Confidence.HIGH,
                "Packet field out of the valid protocol range: " + what + ".", measurements);
    }

    /**
     * A client cannot toggle flying on without server-granted flight. {@code withinGrace} covers packets that were
     * already in flight when the server revoked flight.
     */
    public static Optional<Finding> validateFlightToggle(boolean clientFlying, boolean serverAllowsFlight,
                                                         boolean withinGrace) {
        if (!clientFlying || serverAllowsFlight || withinGrace) {
            return Optional.empty();
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clientFlying", "true");
        m.put("serverAllowFlight", "false");
        return Optional.of(new Finding(FLIGHT_TOGGLE, DetectionKind.ESTABLISHED, Confidence.HIGH,
                "The client reported flying while the server has not granted flight.", m));
    }

    /**
     * A container click (window id other than the player inventory) requires a container the server has open for
     * the player. Clicks inside the grace period after the server opened or closed a window are tolerated.
     */
    public static Optional<Finding> validateWindow(int clickedWindow, boolean containerOpen, boolean withinGrace) {
        if (clickedWindow == 0 || containerOpen || withinGrace) {
            return Optional.empty();
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clickedWindow", Integer.toString(clickedWindow));
        m.put("serverContainerOpen", "false");
        return Optional.of(new Finding(WINDOW_MISMATCH, DetectionKind.ESTABLISHED, Confidence.HIGH,
                "The client clicked a container window while the server has no container open for it.", m));
    }
}
