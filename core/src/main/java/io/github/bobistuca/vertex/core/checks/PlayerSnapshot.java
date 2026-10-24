package io.github.bobistuca.vertex.core.checks;

/**
 * Immutable view of server-authoritative player state, captured on the server thread and read by the analysis
 * thread. Unknown measurements are {@code NaN} or negative where documented, never invented.
 *
 * @param gameMode         upper-case game mode name
 * @param entityId         server entity id
 * @param movementSpeed    movement-speed attribute value (includes potion and sprint modifiers)
 * @param flySpeed         ability flying speed
 * @param jumpBoostLevel   jump boost amplifier + 1, or 0 when absent
 * @param complexEnvironment true when liquids, climbables, webs, honey, scaffolding or similar mechanics are
 *                         near the player (movement checks are relaxed)
 * @param supportedBelow   true when a collidable block is directly below, or when that cannot be determined
 * @param velocity         magnitude of the server-known velocity
 * @param containerOpen    true when a non-player container (chest, furnace, merchant, ...) is open
 * @param tps              scheduler-derived or server-reported ticks per second, {@code NaN} if unknown
 * @param pingMillis       reported latency, {@code -1} if unknown
 * @param jitterMillis     recent latency deviation, {@code NaN} if unknown
 */
public record PlayerSnapshot(
        String gameMode,
        int entityId,
        boolean allowFlight,
        boolean flying,
        boolean gliding,
        boolean swimming,
        boolean climbing,
        boolean inVehicle,
        boolean riptiding,
        double movementSpeed,
        float flySpeed,
        int jumpBoostLevel,
        boolean levitation,
        boolean slowFalling,
        boolean complexEnvironment,
        boolean supportedBelow,
        double velocity,
        boolean containerOpen,
        double tps,
        int pingMillis,
        double jitterMillis) {

    /** A neutral survival snapshot, useful for tests and as the pre-initialization value. */
    public static PlayerSnapshot survivalDefaults() {
        return new PlayerSnapshot("SURVIVAL", 0, false, false, false, false, false, false, false,
                0.1, 0.05f, 0, false, false, false, true, 0.0, false, Double.NaN, -1, Double.NaN);
    }

    public boolean spectator() {
        return "SPECTATOR".equals(gameMode);
    }

    public boolean creative() {
        return "CREATIVE".equals(gameMode);
    }
}
