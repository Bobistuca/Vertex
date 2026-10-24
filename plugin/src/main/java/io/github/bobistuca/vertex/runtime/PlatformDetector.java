package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.platform.Capabilities;
import io.github.bobistuca.vertex.core.platform.Messenger;
import io.github.bobistuca.vertex.core.platform.PlatformAdapters;
import io.github.bobistuca.vertex.core.platform.ServerMetrics;
import io.github.bobistuca.vertex.messaging.LegacyMessenger;
import io.github.bobistuca.vertex.messaging.SpigotMessenger;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Detects what the running server really offers and selects the platform adapters accordingly. */
public final class PlatformDetector {
    private static final String PAPER_ADAPTERS = "io.github.bobistuca.vertex.paper.PaperAdapters";

    /**
     * Builds a dotted name from parts. The names of server-provided Adventure classes must not appear as string
     * constants, because shading would rewrite them to Vertex's relocated copy of the library.
     */
    private static String name(String... parts) {
        return String.join(".", parts);
    }

    private final ClassLoader loader;
    private final Logger logger;

    public PlatformDetector(ClassLoader loader, Logger logger) {
        this.loader = loader;
        this.logger = logger;
    }

    /** Result of detection. {@code adapters.metrics()} may be replaced by the scheduler-derived monitor. */
    public record Detected(Capabilities capabilities, PlatformAdapters adapters, String serverName,
                           String serverVersion, String minecraftVersion) { }

    public Detected detect(TickRateMonitor fallbackMetrics, boolean packetObservation) {
        boolean nativeAdventure = isAssignable(name("net", "kyori", "adventure", "audience", "Audience"), Player.class);
        String serverName = Bukkit.getName();
        boolean paperNamed = serverName != null && serverName.toLowerCase().contains("paper");
        boolean paper = nativeAdventure && (paperNamed || classExists("io.papermc.paper.ServerBuildInfo")
                || classExists("io.papermc.paper.configuration.Configuration"));
        boolean spigotPlayerApi = hasMethod(Player.class, "spigot");
        boolean bungee = classExists("net.md_5.bungee.api.chat.BaseComponent")
                && classExists("net.md_5.bungee.api.chat.TextComponent")
                && classExists("net.md_5.bungee.api.chat.HoverEvent");
        boolean serverTps = paper && hasMethod(Server.class, "getAverageTickTime");

        Capabilities caps = new Capabilities(paper, nativeAdventure, spigotPlayerApi, bungee, serverTps,
                packetObservation, true);

        Messenger messenger = null;
        ServerMetrics metrics = fallbackMetrics;
        Function<UUID, String> brand = id -> null;
        String platform = serverName == null ? "unknown" : serverName;

        if (caps.messaging() == Capabilities.Messaging.PAPER_NATIVE) {
            try {
                Class<?> type = Class.forName(PAPER_ADAPTERS, true,
                        new EmbeddedClassLoader(loader, "paper", "io.github.bobistuca.vertex.paper."));
                PlatformAdapters paperAdapters = (PlatformAdapters) type.getMethod("create").invoke(null);
                messenger = paperAdapters.messenger();
                if (serverTps) {
                    metrics = paperAdapters.metrics();
                }
                brand = paperAdapters.clientBrand();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                logger.warning("Paper adapter unavailable (" + e.getClass().getSimpleName()
                        + "); using the Spigot-compatible adapter instead.");
                caps = new Capabilities(paper, false, spigotPlayerApi, bungee, false, packetObservation, true);
            }
        }
        if (messenger == null && caps.messaging() == Capabilities.Messaging.SPIGOT_BUNGEE) {
            try {
                messenger = new SpigotMessenger();
            } catch (LinkageError e) {
                caps = new Capabilities(paper, false, false, false, caps.serverTps(), packetObservation, true);
            }
        }
        if (messenger == null) {
            messenger = new LegacyMessenger();
        }
        return new Detected(caps, new PlatformAdapters(platform, messenger, metrics, brand), serverName,
                Bukkit.getVersion(), Bukkit.getBukkitVersion());
    }

    private boolean classExists(String name) {
        try {
            Class.forName(name, false, Bukkit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private boolean isAssignable(String interfaceName, Class<?> target) {
        try {
            return Class.forName(interfaceName, false, Bukkit.class.getClassLoader()).isAssignableFrom(target);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (var m : type.getMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
