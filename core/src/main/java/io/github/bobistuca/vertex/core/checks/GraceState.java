package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;

/**
 * Event timestamps (monotonic nanoseconds) written immediately by server-thread event handlers and read by the
 * analysis thread. Grace periods are bounded and configurable.
 */
public final class GraceState {
    /** Why a check is currently suppressed. */
    public enum Reason { JOIN, TELEPORT, RESPAWN, WORLD_CHANGE, VELOCITY, DAMAGE, GAME_MODE, FLIGHT, RANK_CHANGE, LOW_TPS }

    private static final long NEVER = Long.MIN_VALUE;

    private volatile long join = NEVER;
    private volatile long teleport = NEVER;
    private volatile long respawn = NEVER;
    private volatile long worldChange = NEVER;
    private volatile long velocity = NEVER;
    private volatile long damage = NEVER;
    private volatile long gameMode = NEVER;
    private volatile long flight = NEVER;
    private volatile long windowChange = NEVER;
    private volatile long rankChange = NEVER;

    public void joined(long nanos) { join = nanos; }
    public void teleported(long nanos) { teleport = nanos; }
    public void respawned(long nanos) { respawn = nanos; }
    public void changedWorld(long nanos) { worldChange = nanos; }
    public void velocityChanged(long nanos) { velocity = nanos; }
    public void damaged(long nanos) { damage = nanos; }
    public void gameModeChanged(long nanos) { gameMode = nanos; }
    public void flightChanged(long nanos) { flight = nanos; }
    public void windowChanged(long nanos) { windowChange = nanos; }
    public void rankChanged(long nanos) { rankChange = nanos; }

    public long windowChangeNanos() {
        return windowChange;
    }

    /** Returns the active grace reason, or {@code null} when no grace applies. */
    public Reason active(long nowNanos, Settings.Grace grace, double tps) {
        if (within(nowNanos, join, grace.joinMillis())) return Reason.JOIN;
        if (within(nowNanos, teleport, grace.teleportMillis())) return Reason.TELEPORT;
        if (within(nowNanos, respawn, grace.respawnMillis())) return Reason.RESPAWN;
        if (within(nowNanos, worldChange, grace.worldChangeMillis())) return Reason.WORLD_CHANGE;
        if (within(nowNanos, velocity, grace.velocityMillis())) return Reason.VELOCITY;
        if (within(nowNanos, damage, grace.damageMillis())) return Reason.DAMAGE;
        if (within(nowNanos, gameMode, grace.gameModeMillis())) return Reason.GAME_MODE;
        if (within(nowNanos, flight, grace.flightMillis())) return Reason.FLIGHT;
        if (within(nowNanos, rankChange, grace.rankChangeMillis())) return Reason.RANK_CHANGE;
        if (!Double.isNaN(tps) && tps < grace.lowTpsThreshold()) return Reason.LOW_TPS;
        return null;
    }

    private static boolean within(long now, long then, long millis) {
        return then != NEVER && now - then >= 0 && now - then < millis * 1_000_000L;
    }
}
