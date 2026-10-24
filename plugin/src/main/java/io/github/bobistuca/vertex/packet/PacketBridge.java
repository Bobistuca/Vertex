package io.github.bobistuca.vertex.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import io.github.bobistuca.vertex.core.engine.Observation;
import io.github.bobistuca.vertex.packet.VertexPacketListener.BrandSink;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Owns the packet library lifecycle: load in {@code onLoad}, init in {@code onEnable}, terminate in
 * {@code onDisable}. The library is embedded and relocated into Vertex.jar; no second plugin is required.
 * Every call is guarded: a server where the library cannot initialize keeps running without packet checks and the
 * status command reports the exact reason.
 */
public final class PacketBridge {
    public enum State { NOT_LOADED, LOADED, RUNNING, FAILED, STOPPED }

    private final Plugin plugin;
    private volatile State state = State.NOT_LOADED;
    private volatile String failure = "";
    private volatile String libraryVersion = "unknown";
    private PacketListenerCommon registered;
    private VertexPacketListener listener;

    public PacketBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public State state() {
        return state;
    }

    public String failure() {
        return failure;
    }

    public String libraryVersion() {
        return libraryVersion;
    }

    public long listenerErrors() {
        return listener == null ? 0 : listener.errors();
    }

    /** {@code onLoad}. */
    public void load() {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
            PacketEvents.getAPI().getSettings()
                    .reEncodeByDefault(false)
                    .checkForUpdates(false)
                    .bStats(false);
            PacketEvents.getAPI().load();
            state = State.LOADED;
        } catch (Throwable t) {
            fail("load", t);
        }
    }

    /** {@code onEnable}. */
    public void start(Consumer<Observation> sink, PacketCounters counters, BrandSink brandSink) {
        if (state != State.LOADED) {
            return;
        }
        try {
            PacketEvents.getAPI().init();
            listener = new VertexPacketListener(sink, counters, brandSink);
            registered = PacketEvents.getAPI().getEventManager().registerListener(listener);
            try {
                libraryVersion = PacketEvents.getAPI().getVersion().toString();
            } catch (Throwable ignored) {
                libraryVersion = "unknown";
            }
            state = State.RUNNING;
        } catch (Throwable t) {
            fail("init", t);
        }
    }

    /** {@code onDisable}. */
    public void stop() {
        if (state == State.NOT_LOADED || state == State.STOPPED) {
            return;
        }
        try {
            if (registered != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(registered);
            }
            PacketEvents.getAPI().terminate();
        } catch (Throwable ignored) {
            // Shutting down; nothing useful can be done.
        } finally {
            registered = null;
            state = State.STOPPED;
        }
    }

    /** Client version for a connected player, or {@code null} when it is not known. */
    public ClientVersion clientVersion(Player player) {
        if (state != State.RUNNING) {
            return null;
        }
        try {
            ClientVersion v = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            return v == null || v == ClientVersion.UNKNOWN ? null : v;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Latency in milliseconds, or -1 when unavailable. */
    public int ping(Player player) {
        if (state != State.RUNNING) {
            return -1;
        }
        try {
            int ping = PacketEvents.getAPI().getPlayerManager().getPing(player);
            return ping < 0 ? -1 : ping;
        } catch (Throwable t) {
            return -1;
        }
    }

    private void fail(String phase, Throwable t) {
        state = State.FAILED;
        failure = phase + ": " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + t.getMessage());
        plugin.getLogger().warning("Packet observation unavailable (" + failure + ")");
    }
}
