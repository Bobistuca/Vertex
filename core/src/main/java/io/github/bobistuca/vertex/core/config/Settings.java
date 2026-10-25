package io.github.bobistuca.vertex.core.config;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable, validated plugin settings. Built only by {@link SettingsLoader}. */
public record Settings(
        String language,
        Appearance appearance,
        Alerts alerts,
        ClientInfo clientInfo,
        Grace grace,
        Checks checks,
        Audit audit,
        Staff staff,
        TestMode testMode,
        Storage storage) {

    /** Palette (light green, dark green, white by default) and label symbols. */
    public record Appearance(String primary, String secondary, String text,
                             String symbolInfo, String symbolSuspicion, String symbolEstablished, String symbolAdmin) { }

    public record ActionBar(boolean enabled) { }

    public record Sound(boolean enabled, String name, double volume, double pitch) { }

    public record Recipients(boolean operators, boolean permission, boolean staff, Set<String> gameModes) { }

    public record Alerts(long windowMillis, int maxPerPlayerPerMinute, int maxPerRecipientPerMinute,
                         boolean logToConsole, boolean notifySubject, ActionBar actionBar, Sound sound, Recipients recipients) { }

    public record ClientInfo(boolean enabled, long timeoutMillis, int maxChannels) { }

    public record Grace(long joinMillis, long teleportMillis, long respawnMillis, long worldChangeMillis,
                        long velocityMillis, long damageMillis, long gameModeMillis, long flightMillis,
                        long rankChangeMillis, double lowTpsThreshold) { }

    public record InvalidValues(boolean enabled) { }

    public record InvalidFields(boolean enabled) { }

    public record PacketRate(boolean enabled, int windowLimit, int sustainedWindows, long stallGraceMillis,
                             int floodLimit, int floodSustainedWindows) { }

    public record AutoClicker(boolean enabled, int windowSamples, long maxGapMillis, double minMeanCps,
                              double batchMillis, double maxBatchedShare, double dominantTickShare,
                              double maxTickEntropyBits, double periodMatchShare, int maxPeriod, double maxCv,
                              double maxResidualMillis, int requiredSignals, boolean includeAirSwings,
                              double dedupeMillis, double minTps, double maxJitterMillis,
                              double bufferThreshold, double bufferDecay) { }

    public record Movement(boolean enabled, double speedFactor, double speedBase, double flyFactor,
                           double maxStepUp, double jumpBoostPerLevel, double airRiseLimit, int hoverPackets,
                           double sustainedFactor, int sustainedPackets, double slipperyMultiplier,
                           double bufferThreshold, double bufferDecay) { }

    public record Reach(boolean enabled, double tolerance, long latencyCapMillis, long processingDelayMillis,
                        double creativeBonus, double bufferThreshold, double bufferDecay) { }

    public record Inventory(boolean enabled, long windowGraceMillis) { }

    public record FlightToggle(boolean enabled) { }

    public record Checks(InvalidValues invalidValues, InvalidFields invalidFields, PacketRate packetRate,
                         AutoClicker autoClicker, Movement movement, Reach reach, Inventory inventory,
                         FlightToggle flightToggle) { }

    public record Monitored(boolean op, boolean gameMode, boolean permissionGroups, boolean tags, boolean teams) { }

    public record Audit(boolean enabled, int opIntervalTicks, int reconcilePerRun, long eventCorrelationMillis,
                        long reconcileCorrelationMillis, Set<UUID> trustedUuids, Set<UUID> authorizedActors,
                        boolean consoleAuthorized, Monitored monitored, Set<String> sensitiveTags,
                        List<String> monitoredPermissions, boolean notifyAuthorized, boolean notifyNoChange) { }

    /**
     * Who bypasses the gameplay checks. Receiving alerts never exempts anyone, and operator status alone exempts
     * nobody unless {@code operatorsExempt} is enabled explicitly.
     *
     * @param bypassPermission  honour the explicit permission {@code vertex.bypass.gameplay}
     * @param wildcardBypass    also accept that permission when it is only granted through a wildcard ({@code *})
     * @param operatorsExempt   exempt operators (explicit opt-in, default off)
     * @param luckPerms         read LuckPerms groups (recognition, alert eligibility, diagnostics)
     * @param luckPermsExempt   exempt members of the configured groups (explicit opt-in, default off)
     * @param groups            configured ranks: group ids or labels that have an entry in {@code mappings}
     * @param mappings          label -> candidate real group ids (for example "Mod Plus" -> modplus, mod_plus)
     */
    public record Staff(boolean bypassPermission, boolean wildcardBypass, boolean operatorsExempt, boolean luckPerms,
                        boolean luckPermsExempt, List<String> groups, java.util.Map<String, List<String>> mappings) { }

    /** Per-player forced evaluation for diagnosing detections. */
    public record TestMode(boolean enabled, int durationMinutes) { }

    public record Storage(int maxEntries, long maxBytes, int retentionDays, int queueCapacity,
                          int memoryPerPlayer, int memoryGlobal) { }
}
