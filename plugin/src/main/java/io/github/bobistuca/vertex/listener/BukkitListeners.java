package io.github.bobistuca.vertex.listener;

import io.github.bobistuca.vertex.core.audit.Actor;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.runtime.VertexRuntime;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.util.BoundingBox;

/**
 * Bukkit event bridge. Handlers only observe: they record grace timestamps, feed the auditor and the reach check,
 * and never cancel or modify an event. They run at MONITOR priority so other plugins decide the outcome first.
 */
public final class BukkitListeners implements Listener {
    private final VertexRuntime runtime;

    public BukkitListeners(VertexRuntime runtime) {
        this.runtime = runtime;
    }

    private GraceState grace(Player p) {
        return runtime.tracker().grace(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        runtime.playerJoined(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        runtime.playerLeft(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) g.teleported(System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) g.respawned(System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) g.changedWorld(System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) g.velocityChanged(System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            GraceState g = grace(p);
            if (g != null) g.damaged(System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) g.flightChanged(System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        GraceState g = grace(e.getPlayer());
        if (g != null) {
            g.gameModeChanged(System.nanoTime());
            g.flightChanged(System.nanoTime());
        }
        if (!e.isCancelled()) {
            runtime.audit().onGameModeEvent(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) {
            GraceState g = grace(p);
            if (g != null) g.windowChanged(System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            GraceState g = grace(p);
            if (g != null) g.windowChanged(System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannel(PlayerRegisterChannelEvent e) {
        runtime.channelRegistered(e.getPlayer().getUniqueId(), e.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        runtime.audit().onCommand(Actor.player(p.getUniqueId(), p.getName()), e.getMessage(), System.nanoTime(),
                System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent e) {
        CommandSender sender = e.getSender();
        Actor actor;
        if (sender instanceof Player p) {
            actor = Actor.player(p.getUniqueId(), p.getName());
        } else if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            actor = Actor.CONSOLE;
        } else {
            actor = Actor.UNKNOWN;
        }
        runtime.audit().onCommand(actor, e.getCommand(), System.nanoTime(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Entity target = e.getEntity();
        if (target == attacker || target.getWorld() != attacker.getWorld()) {
            return;
        }
        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.getMinX() - eye.getX(), 0.0), eye.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - eye.getY(), 0.0), eye.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - eye.getZ(), 0.0), eye.getZ() - box.getMaxZ());
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        AttributeInstance range = attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        runtime.reachSample(attacker, target, distance, range == null ? 3.0 : range.getValue());
    }
}
