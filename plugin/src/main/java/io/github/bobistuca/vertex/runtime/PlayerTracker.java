package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.engine.PlayerContext;
import io.github.bobistuca.vertex.core.config.Settings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Per-player server-side state: the periodic snapshot, the grace timestamps written by events, latency samples and
 * the reach evaluator. The map is read from the analysis thread; everything else is server-thread only.
 */
public final class PlayerTracker {
    private static final int PING_SAMPLES = 10;
    private static final long PING_SAMPLE_NANOS = 1_000_000_000L;

    private static final class Entry {
        final GraceState grace = new GraceState();
        ReachEvaluator reach;
        Settings.Reach reachSettings;
        final Deque<Integer> pings = new ArrayDeque<>();
        long lastPingSample;
        double lastX, lastY, lastZ;
        java.util.UUID lastWorld;
        long lastMoveNanos;
        volatile double speed; // blocks per second, from consecutive snapshots
    }

    private final Map<UUID, PlayerContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Supplier<Settings> settings;
    private final TickValues tick;

    /** Server values needed per capture. */
    public interface TickValues {
        double tps();

        int ping(Player player);
    }

    public PlayerTracker(Supplier<Settings> settings, TickValues tick) {
        this.settings = settings;
        this.tick = tick;
    }

    /** Analysis thread and anywhere else. */
    public PlayerContext context(UUID id) {
        return contexts.get(id);
    }

    public GraceState grace(UUID id) {
        Entry e = entries.get(id);
        return e == null ? null : e.grace;
    }

    /** Server thread. */
    public void join(Player player, long nowNanos) {
        Entry entry = new Entry();
        entry.grace.joined(nowNanos);
        entries.put(player.getUniqueId(), entry);
        refresh(player, nowNanos);
    }

    public void remove(UUID id) {
        entries.remove(id);
        contexts.remove(id);
    }

    /** Server thread. Rebuilds the snapshot for the player. */
    public void refresh(Player player, long nowNanos) {
        Entry entry = entries.get(player.getUniqueId());
        if (entry == null) {
            return;
        }
        int ping = tick.ping(player);
        if (ping >= 0 && nowNanos - entry.lastPingSample >= PING_SAMPLE_NANOS) {
            entry.lastPingSample = nowNanos;
            entry.pings.addLast(ping);
            while (entry.pings.size() > PING_SAMPLES) {
                entry.pings.removeFirst();
            }
        }
        trackSpeed(entry, player, nowNanos);
        PlayerSnapshot snapshot = SnapshotCollector.capture(player, tick.tps(), ping, jitter(entry));
        contexts.put(player.getUniqueId(), new PlayerContext(snapshot, entry.grace));
    }

    private static void trackSpeed(Entry entry, Player player, long nowNanos) {
        org.bukkit.Location l = player.getLocation();
        java.util.UUID world = l.getWorld() == null ? null : l.getWorld().getUID();
        double dt = (nowNanos - entry.lastMoveNanos) / 1e9;
        if (entry.lastMoveNanos != 0 && dt > 0.01 && dt < 2.0 && world != null && world.equals(entry.lastWorld)) {
            double dx = l.getX() - entry.lastX;
            double dy = l.getY() - entry.lastY;
            double dz = l.getZ() - entry.lastZ;
            entry.speed = Math.min(30.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / dt);
        } else {
            entry.speed = 0.0;
        }
        entry.lastX = l.getX();
        entry.lastY = l.getY();
        entry.lastZ = l.getZ();
        entry.lastWorld = world;
        entry.lastMoveNanos = nowNanos;
    }

    /** Recent movement speed in blocks per second (0 when unknown). */
    public double speed(UUID id) {
        Entry e = entries.get(id);
        return e == null ? 0.0 : e.speed;
    }

    /** Standard deviation of recent latency samples, NaN until enough samples exist. */
    private static double jitter(Entry entry) {
        if (entry.pings.size() < 5) {
            return Double.NaN;
        }
        double mean = 0;
        for (int p : entry.pings) {
            mean += p;
        }
        mean /= entry.pings.size();
        double var = 0;
        for (int p : entry.pings) {
            var += (p - mean) * (p - mean);
        }
        return Math.sqrt(var / entry.pings.size());
    }

    /** Reach evaluator bound to the current settings. */
    public ReachEvaluator reach(UUID id) {
        Entry e = entries.get(id);
        if (e == null) {
            return null;
        }
        Settings.Reach current = settings.get().checks().reach();
        if (e.reach == null || e.reachSettings != current) {
            e.reach = new ReachEvaluator(current);
            e.reachSettings = current;
        }
        return e.reach;
    }

    /** Forgets the buffered reach evidence (rank gained or lost). */
    public void resetReach(UUID id) {
        Entry e = entries.get(id);
        if (e != null) {
            e.reach = null;
        }
    }

    public int tracked() {
        return entries.size();
    }

    /** Convenience for tests of wiring: builds a tick-values object from suppliers. */
    public static TickValues values(java.util.function.DoubleSupplier tps, java.util.function.ToIntFunction<Player> ping) {
        return new TickValues() {
            @Override
            public double tps() {
                return tps.getAsDouble();
            }

            @Override
            public int ping(Player player) {
                return ping.applyAsInt(player);
            }
        };
    }
}
