package io.github.bobistuca.vertex.core.engine;

import java.util.UUID;

/**
 * Immutable observations handed from network threads to the analysis thread. They contain only primitives and
 * strings copied out of packet wrappers, so nothing unsafe crosses the thread boundary.
 */
public sealed interface Observation {

    UUID playerId();

    String playerName();

    /** Monotonic arrival time ({@code System.nanoTime()}). */
    long nanos();

    record Flying(UUID playerId, String playerName, long nanos, boolean hasPosition, double x, double y, double z,
                  boolean hasRotation, float yaw, float pitch, boolean onGround) implements Observation { }

    record Attack(UUID playerId, String playerName, long nanos, int entityId) implements Observation { }

    record Swing(UUID playerId, String playerName, long nanos) implements Observation { }

    /** {@code active} is true when block breaking starts and false when it stops, is cancelled or finishes. */
    record Digging(UUID playerId, String playerName, long nanos, boolean active) implements Observation { }

    record ClickWindow(UUID playerId, String playerName, long nanos, int windowId, int slot) implements Observation { }

    record HeldSlot(UUID playerId, String playerName, long nanos, int slot) implements Observation { }

    record Abilities(UUID playerId, String playerName, long nanos, boolean flying) implements Observation { }

    /** Total inbound packets counted for the player during the last second. */
    record SecondTick(UUID playerId, String playerName, long nanos, int totalPackets) implements Observation { }

    /** Session cleanup. */
    record Quit(UUID playerId, String playerName, long nanos) implements Observation { }
}
