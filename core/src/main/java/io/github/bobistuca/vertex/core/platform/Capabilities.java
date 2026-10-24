package io.github.bobistuca.vertex.core.platform;

/**
 * Runtime facts detected at startup. Selection of platform adapters depends only on this record, so it can be
 * tested without a server.
 *
 * @param nativeAdventure     the server provides Adventure audiences (Paper)
 * @param spigotPlayerApi     {@code Player#spigot()} exists at runtime (Spigot, Paper)
 * @param bungeeChat         BungeeCord chat components are on the server class path
 * @param serverTps          the server exposes TPS (Paper)
 * @param packetObservation  the packet library initialized successfully
 * @param eventObservation   Bukkit events are registered
 */
public record Capabilities(boolean paper, boolean nativeAdventure, boolean spigotPlayerApi, boolean bungeeChat,
                           boolean serverTps, boolean packetObservation, boolean eventObservation) {

    /** Messaging implementations in order of preference. */
    public enum Messaging { PAPER_NATIVE, SPIGOT_BUNGEE, LEGACY_TEXT }

    public Messaging messaging() {
        if (paper && nativeAdventure) {
            return Messaging.PAPER_NATIVE;
        }
        if (spigotPlayerApi && bungeeChat) {
            return Messaging.SPIGOT_BUNGEE;
        }
        return Messaging.LEGACY_TEXT;
    }

    /** Hover text and action bar need a rich implementation; legacy text has neither. */
    public boolean richMessaging() {
        return messaging() != Messaging.LEGACY_TEXT;
    }

    public String metricsSource() {
        return serverTps ? "server-api" : "scheduler-derived";
    }
}
