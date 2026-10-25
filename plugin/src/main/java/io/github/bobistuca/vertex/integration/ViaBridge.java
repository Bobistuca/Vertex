package io.github.bobistuca.vertex.integration;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Optional ViaVersion awareness, built on the official ViaVersion API (compile-only; ViaVersion and ViaBackwards are
 * never bundled). Nothing here exempts anyone: it only tells diagnostics which protocol the client really speaks.
 *
 * <p>The packet library observes the connection after ViaVersion has translated it to the server's protocol, so
 * observed movement and attack packets already use the server's field layout. The client's original protocol is read
 * from the Via API, which may only know it a short moment after the connection starts.
 *
 * <p>This class never references a Via type directly; those are confined to {@link Api}, which is only loaded when
 * the plugin is enabled, so a server without ViaVersion never sees a missing class.
 */
public final class ViaBridge {
    /** Original client protocol as reported by ViaVersion. */
    public record ClientProtocol(int protocol, String name) { }

    private final boolean viaVersion;
    private final boolean viaBackwards;
    private final String viaVersionVersion;
    private final String viaBackwardsVersion;

    private ViaBridge(boolean viaVersion, boolean viaBackwards, String vv, String vb) {
        this.viaVersion = viaVersion;
        this.viaBackwards = viaBackwards;
        this.viaVersionVersion = vv;
        this.viaBackwardsVersion = vb;
    }

    public static ViaBridge create() {
        Plugin vv = Bukkit.getPluginManager().getPlugin("ViaVersion");
        Plugin vb = Bukkit.getPluginManager().getPlugin("ViaBackwards");
        boolean viaOn = vv != null && vv.isEnabled();
        boolean backOn = vb != null && vb.isEnabled();
        return new ViaBridge(viaOn, backOn, viaOn ? vv.getDescription().getVersion() : "",
                backOn ? vb.getDescription().getVersion() : "");
    }

    public boolean viaVersion() { return viaVersion; }

    public boolean viaBackwards() { return viaBackwards; }

    public String describe() {
        if (!viaVersion) {
            return "not installed";
        }
        return "ViaVersion " + viaVersionVersion + (viaBackwards ? ", ViaBackwards " + viaBackwardsVersion : "");
    }

    /** Original client protocol, or {@code null} when ViaVersion is absent or does not know the connection yet. */
    public ClientProtocol clientProtocol(UUID id) {
        if (!viaVersion) {
            return null;
        }
        try {
            return Api.clientProtocol(id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class Api {
        static ClientProtocol clientProtocol(UUID id) {
            int protocol = com.viaversion.viaversion.api.Via.getAPI().getPlayerVersion(id);
            if (protocol <= 0) {
                return null;
            }
            var version = com.viaversion.viaversion.api.protocol.version.ProtocolVersion.getProtocol(protocol);
            return new ClientProtocol(protocol, version == null ? "protocol " + protocol : version.getName());
        }
    }
}
