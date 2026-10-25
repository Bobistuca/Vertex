package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.engine.AnalysisEngine;
import io.github.bobistuca.vertex.core.engine.PlayerContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Reach needs server-side entity state, so the network thread only queues the attack attempt (attacker and target
 * entity id) and the server thread resolves the target and measures eye-to-hitbox distance. Because the attempt comes
 * from the attack packet it is evaluated whether or not the server ended up applying damage, which is what lets a
 * client that swings at targets out of range be seen at all.
 */
public final class ReachService {
    private static final int QUEUE_CAP = 512;
    private static final int PER_TICK = 128;
    /** Largest search radius when the target is not cached; far beyond any honest interaction range. */
    private static final double SEARCH = 16.0;

    private record Attempt(UUID attacker, int entityId, long nanos) { }

    private final Supplier<Settings> settings;
    private final PlayerTracker tracker;
    private final AnalysisEngine engine;
    private final ConcurrentLinkedQueue<Attempt> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong unresolved = new AtomicLong();
    private final Map<UUID, Entity> cache = new ConcurrentHashMap<>();

    public ReachService(Supplier<Settings> settings, PlayerTracker tracker, AnalysisEngine engine) {
        this.settings = settings;
        this.tracker = tracker;
        this.engine = engine;
    }

    /** Any thread. Bounded; excess attempts are counted, never queued. */
    public void enqueue(UUID attacker, int entityId, long nanos) {
        received.incrementAndGet();
        if (size.get() >= QUEUE_CAP) {
            dropped.incrementAndGet();
            return;
        }
        size.incrementAndGet();
        queue.add(new Attempt(attacker, entityId, nanos));
    }

    public long received() { return received.get(); }
    public long dropped() { return dropped.get(); }
    public long unresolved() { return unresolved.get(); }

    public void forget(UUID id) {
        cache.remove(id);
    }

    /** Server thread, every tick. */
    public void drain() {
        for (int i = 0; i < PER_TICK; i++) {
            Attempt a = queue.poll();
            if (a == null) {
                return;
            }
            size.decrementAndGet();
            try {
                evaluate(a);
            } catch (RuntimeException e) {
                // One malformed attempt must not stop the others.
            }
        }
    }

    private void evaluate(Attempt a) {
        Player attacker = Bukkit.getPlayer(a.attacker());
        Settings cfg = settings.get();
        if (attacker == null || !cfg.checks().reach().enabled()) {
            return;
        }
        UUID id = a.attacker();
        PlayerContext ctx = tracker.context(id);
        GraceState grace = tracker.grace(id);
        ReachEvaluator evaluator = tracker.reach(id);
        if (ctx == null || grace == null || evaluator == null || ctx.snapshot().spectator()) {
            return;
        }
        if (a.entityId() == attacker.getEntityId()) {
            return;
        }
        long now = System.nanoTime();
        if (grace.active(now, cfg.grace(), ctx.snapshot().tps()) != null) {
            return;
        }
        Entity target = resolve(attacker, a.entityId());
        if (target == null) {
            unresolved.incrementAndGet();
            return;
        }
        double distance = distance(attacker.getEyeLocation(), target.getBoundingBox());
        double targetSpeed = target instanceof Player tp ? tracker.speed(tp.getUniqueId())
                : target.getVelocity().length() * 20.0;
        double uncertainty = evaluator.uncertainty(ctx.snapshot().pingMillis(), targetSpeed, tracker.speed(id));
        double range = Compat.interactionRange(attacker);
        engine.noteEvaluated(id, ReachEvaluator.CHECK_ID);
        Finding finding = evaluator.evaluate(distance, range, ctx.snapshot().creative(), uncertainty,
                ctx.snapshot().pingMillis(), target.getType().name()).orElse(null);
        if (finding != null) {
            engine.record(id, attacker.getName(), finding, "reach:" + target.getEntityId() + ":" + (a.nanos() / 50_000_000L));
        }
    }

    /**
     * Fallback used only when packet observation is not running: the damage event of an attack that the server did
     * apply. Never used together with the packet path, so an attack is not counted twice.
     */
    public void evaluateApplied(Player attacker, Entity target) {
        if (target == attacker || target.getWorld() != attacker.getWorld()) {
            return;
        }
        cache.put(attacker.getUniqueId(), target);
        evaluate(new Attempt(attacker.getUniqueId(), target.getEntityId(), System.nanoTime()));
    }

    private Entity resolve(Player attacker, int entityId) {
        Entity cached = cache.get(attacker.getUniqueId());
        if (cached != null && cached.getEntityId() == entityId && cached.isValid()
                && cached.getWorld() == attacker.getWorld()) {
            return cached;
        }
        for (Entity e : attacker.getNearbyEntities(SEARCH, SEARCH, SEARCH)) {
            if (e.getEntityId() == entityId) {
                cache.put(attacker.getUniqueId(), e);
                return e;
            }
        }
        return null;
    }

    private static double distance(Location eye, BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - eye.getX(), 0.0), eye.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - eye.getY(), 0.0), eye.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - eye.getZ(), 0.0), eye.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
