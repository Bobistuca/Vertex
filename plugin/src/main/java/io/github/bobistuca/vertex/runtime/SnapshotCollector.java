package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Reads server-authoritative state with the Bukkit API. Server thread only. */
final class SnapshotCollector {
    private SnapshotCollector() { }

    static PlayerSnapshot capture(Player player, double tps, int pingMillis, double jitterMillis) {
        double movementSpeed = Compat.movementSpeed(player);

        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int jumpLevel = jump == null ? 0 : jump.getAmplifier() + 1;
        boolean levitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean slowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

        boolean complex = false;
        boolean supported = true;
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world != null && Double.isFinite(loc.getX()) && Double.isFinite(loc.getZ())) {
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            if (world.isChunkLoaded(cx, cz)) {
                complex = complexAt(world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                        || complexAt(world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()))
                        || complexAt(world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()));
                supported = supportedBelow(world, loc);
            }
        }

        boolean container = Compat.containerOpen(player);
        boolean slippery = world != null && slipperyBelow(world, loc);

        return new PlayerSnapshot(
                player.getGameMode().name(),
                player.getEntityId(),
                player.getAllowFlight(),
                player.isFlying(),
                player.isGliding(),
                player.isSwimming(),
                player.isClimbing(),
                player.isInsideVehicle(),
                player.isRiptiding(),
                movementSpeed,
                player.getFlySpeed(),
                jumpLevel,
                levitation,
                slowFalling,
                complex,
                supported,
                player.getVelocity().length(),
                container,
                tps,
                pingMillis,
                jitterMillis,
                player.isSprinting(),
                slippery);
    }

    private static boolean slipperyBelow(World world, Location loc) {
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return false;
        }
        Material type = world.getBlockAt(bx, (int) Math.floor(loc.getY() - 0.1), bz).getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE
                || type == Material.FROSTED_ICE;
    }

    private static boolean complexAt(Block block) {
        Material type = block.getType();
        return block.isLiquid()
                || Tag.CLIMBABLE.isTagged(type)
                || Tag.FALL_DAMAGE_RESETTING.isTagged(type)
                || type == Material.SLIME_BLOCK
                || type == Material.HONEY_BLOCK
                || type == Material.BUBBLE_COLUMN
                || type == Material.COBWEB
                || type == Material.SCAFFOLDING;
    }

    /** True when a non-passable (or liquid) block is directly below the feet at any of five sample points. */
    private static boolean supportedBelow(World world, Location loc) {
        double[][] offsets = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};
        for (double[] o : offsets) {
            int bx = (int) Math.floor(loc.getX() + o[0]);
            int bz = (int) Math.floor(loc.getZ() + o[1]);
            int by = (int) Math.floor(loc.getY() - 0.1);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                return true;
            }
            Block block = world.getBlockAt(bx, by, bz);
            if (!block.isPassable() || block.isLiquid()) {
                return true;
            }
        }
        return false;
    }
}
