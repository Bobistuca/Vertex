package io.github.bobistuca.vertex.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import io.github.bobistuca.vertex.core.engine.Observation;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Observe-only packet listener. It copies the few fields it needs into immutable observations and never cancels,
 * modifies or re-encodes a packet. Failures are counted and swallowed so the network pipeline is never disturbed.
 */
public final class VertexPacketListener extends PacketListenerAbstract {
    /** Receives a raw brand payload (any thread) so the join report can include it. */
    public interface BrandSink {
        void brand(UUID id, byte[] payload, long nanos);
    }

    private final Consumer<Observation> sink;
    private final PacketCounters counters;
    private final BrandSink brandSink;
    private final AtomicLong errors = new AtomicLong();

    public VertexPacketListener(Consumer<Observation> sink, PacketCounters counters, BrandSink brandSink) {
        super(PacketListenerPriority.MONITOR);
        this.sink = sink;
        this.counters = counters;
        this.brandSink = brandSink;
    }

    public long errors() {
        return errors.get();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            User user = event.getUser();
            if (user == null) {
                return;
            }
            UUID id = user.getUUID();
            if (id == null) {
                return;
            }
            long now = System.nanoTime();
            ConnectionState state = event.getConnectionState();
            PacketTypeCommon type = event.getPacketType();
            if (state == ConnectionState.CONFIGURATION) {
                if (type == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                    WrapperConfigClientPluginMessage w = new WrapperConfigClientPluginMessage(event);
                    captureBrand(id, w.getChannelName(), w.getData(), now);
                }
                return;
            }
            if (state != ConnectionState.PLAY) {
                return;
            }
            counters.increment(id);
            String name = user.getName();
            if (WrapperPlayClientPlayerFlying.isFlying(type)) {
                WrapperPlayClientPlayerFlying w = new WrapperPlayClientPlayerFlying(event);
                Location l = w.getLocation();
                boolean pos = w.hasPositionChanged();
                boolean rot = w.hasRotationChanged();
                sink.accept(new Observation.Flying(id, name, now, pos, pos ? l.getX() : 0, pos ? l.getY() : 0,
                        pos ? l.getZ() : 0, rot, rot ? l.getYaw() : 0f, rot ? l.getPitch() : 0f, w.isOnGround()));
            } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
                if (w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    sink.accept(new Observation.Attack(id, name, now, w.getEntityId()));
                }
            } else if (type == PacketType.Play.Client.ATTACK) {
                sink.accept(new Observation.Attack(id, name, now, new WrapperPlayClientAttack(event).getEntityId()));
            } else if (type == PacketType.Play.Client.ANIMATION) {
                sink.accept(new Observation.Swing(id, name, now));
            } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
                DiggingAction action = new WrapperPlayClientPlayerDigging(event).getAction();
                sink.accept(new Observation.Digging(id, name, now, action == DiggingAction.START_DIGGING));
            } else if (type == PacketType.Play.Client.CLICK_WINDOW) {
                WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
                sink.accept(new Observation.ClickWindow(id, name, now, w.getWindowId(), w.getSlot()));
            } else if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                sink.accept(new Observation.HeldSlot(id, name, now, new WrapperPlayClientHeldItemChange(event).getSlot()));
            } else if (type == PacketType.Play.Client.PLAYER_ABILITIES) {
                sink.accept(new Observation.Abilities(id, name, now, new WrapperPlayClientPlayerAbilities(event).isFlying()));
            } else if (type == PacketType.Play.Client.PLUGIN_MESSAGE) {
                WrapperPlayClientPluginMessage w = new WrapperPlayClientPluginMessage(event);
                captureBrand(id, w.getChannelName(), w.getData(), now);
            }
        } catch (Throwable t) {
            errors.incrementAndGet();
        }
    }

    private void captureBrand(UUID id, String channel, byte[] data, long now) {
        if (channel != null && (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) && data != null) {
            brandSink.brand(id, data, now);
        }
    }
}
