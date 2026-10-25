package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.staff.Exemption;
import io.github.bobistuca.vertex.core.staff.ExemptionRegistry;
import io.github.bobistuca.vertex.core.staff.PermissionGrant;
import io.github.bobistuca.vertex.core.staff.StaffDecision;
import io.github.bobistuca.vertex.core.staff.StaffEvaluator;
import io.github.bobistuca.vertex.core.staff.StaffFacts;
import io.github.bobistuca.vertex.core.staff.StaffGroups;
import io.github.bobistuca.vertex.integration.LuckPermsBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Keeps the {@link ExemptionRegistry} current. Exemption is never implicit: operator status, LuckPerms groups and the
 * bypass permission each count only as configured (see {@link StaffEvaluator}), receiving alerts never exempts, and
 * the bypass permission is honoured only when it is granted by its exact name unless wildcards are explicitly
 * allowed. Evaluation runs on the server thread when something changes and as a slow round-robin safety net; the
 * hot paths only read the cached registry. Recognizing a rank here grants nothing.
 */
public final class StaffService {
    private final ExemptionRegistry registry = new ExemptionRegistry();
    private final Supplier<Settings> settings;
    private final BiConsumer<UUID, ExemptionRegistry.Transition> onTransition;
    private final Map<UUID, Details> details = new ConcurrentHashMap<>();
    private LuckPermsBridge luckPerms;
    private Settings.Staff compiledFor;
    private StaffGroups groups;
    private int cursor;

    /** Last evaluation facts, for diagnostics. */
    public record Details(boolean operator, PermissionGrant permission, Set<String> resolvedGroups, StaffDecision decision) { }

    public StaffService(Supplier<Settings> settings, BiConsumer<UUID, ExemptionRegistry.Transition> onTransition) {
        this.settings = settings;
        this.onTransition = onTransition;
    }

    public ExemptionRegistry registry() {
        return registry;
    }

    public void luckPerms(LuckPermsBridge bridge) {
        this.luckPerms = bridge;
    }

    public boolean luckPermsActive() {
        return luckPerms != null && settings.get().staff().luckPerms();
    }

    public Details details(UUID id) {
        return details.get(id);
    }

    private StaffGroups groups() {
        Settings.Staff current = settings.get().staff();
        if (groups == null || compiledFor != current) {
            groups = StaffGroups.of(current);
            compiledFor = current;
        }
        return groups;
    }

    /** Server thread. Re-evaluates one player and reports a transition when the effective state changed. */
    public void refresh(Player player) {
        Settings.Staff config = settings.get().staff();
        Set<String> memberOf = luckPerms != null && config.luckPerms() ? luckPerms.effectiveGroupsOf(player) : null;
        PermissionGrant grant = permissionGrant(player);
        StaffDecision decision = StaffEvaluator.evaluate(config, groups(),
                new StaffFacts(player.isOp(), grant, memberOf));
        UUID id = player.getUniqueId();
        registry.setMembership(id, decision.group().orElse(null));
        details.put(id, new Details(player.isOp(), grant, memberOf, decision));
        ExemptionRegistry.Transition transition = registry.set(id, decision.exemption().orElse(null));
        if (transition != ExemptionRegistry.Transition.UNCHANGED) {
            onTransition.accept(id, transition);
        }
    }

    /**
     * Distinguishes a bypass permission granted by its exact name from one that is only effective through a
     * wildcard such as {@code *} or {@code vertex.*}, which administrators often hold without intending an exemption.
     */
    private static PermissionGrant permissionGrant(Player player) {
        if (!player.hasPermission(Exemption.BYPASS_PERMISSION)) {
            return PermissionGrant.NONE;
        }
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue() && Exemption.BYPASS_PERMISSION.equalsIgnoreCase(info.getPermission())) {
                return PermissionGrant.EXPLICIT;
            }
        }
        return PermissionGrant.WILDCARD_ONLY;
    }

    public void refresh(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            refresh(player);
        }
    }

    public void refreshAll() {
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            refresh(p);
        }
    }

    /** Safety net for permission changes that raise no event: a few players per call, round-robin. */
    public void refreshSome(int budget) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < Math.min(budget, online.size()); i++) {
            refresh(online.get(Math.floorMod(cursor++, online.size())));
        }
        for (ExemptionRegistry.Expired expired : registry.expireTestModes()) {
            if (expired.transition() != ExemptionRegistry.Transition.UNCHANGED) {
                onTransition.accept(expired.id(), expired.transition());
            }
        }
    }

    // ------------------------------------------------------------------ test mode

    public void enableTestMode(UUID id) {
        long ms = settings.get().testMode().durationMinutes() * 60_000L;
        ExemptionRegistry.Transition t = registry.enableTestMode(id, ms);
        onTransition.accept(id, t);
    }

    public void disableTestMode(UUID id) {
        onTransition.accept(id, registry.disableTestMode(id));
    }

    public void forget(UUID id) {
        registry.forget(id);
        details.remove(id);
    }

    public Set<String> configuredGroupIds() {
        return groups().ids();
    }

    /** Short text for status output. */
    public String integrationStatus() {
        Settings.Staff config = settings.get().staff();
        StringBuilder sb = new StringBuilder();
        sb.append("bypass permission ").append(config.bypassPermission()
                ? (config.wildcardBypass() ? "on (wildcards accepted)" : "on (exact node only)") : "off");
        sb.append("; operators exempt: ").append(config.operatorsExempt());
        if (!config.luckPerms()) {
            sb.append("; LuckPerms groups disabled in config.yml");
        } else if (luckPerms == null) {
            sb.append("; LuckPerms group integration inactive (LuckPerms not available)");
        } else {
            sb.append("; LuckPerms active, ").append(config.luckPermsExempt() ? "groups EXEMPT " : "groups recognized only ")
                    .append(groups().ids());
        }
        sb.append("; exempt now: ").append(registry.size()).append("; in test mode: ").append(registry.testModeCount());
        return sb.toString();
    }
}
