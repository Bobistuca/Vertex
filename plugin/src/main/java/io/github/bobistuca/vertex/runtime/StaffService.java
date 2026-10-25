package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.staff.Exemption;
import io.github.bobistuca.vertex.core.staff.ExemptionRegistry;
import io.github.bobistuca.vertex.core.staff.StaffEvaluator;
import io.github.bobistuca.vertex.core.staff.StaffGroups;
import io.github.bobistuca.vertex.integration.LuckPermsBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Keeps the {@link ExemptionRegistry} current. Group data comes from LuckPerms (when present), the explicit
 * {@code vertex.bypass.gameplay} permission is always honoured. Evaluation runs on the server thread when something
 * changes (join, LuckPerms recalculation, reload) and as a slow round-robin safety net; the hot paths only read the
 * cached registry. Recognizing a rank here grants nothing: no OP, no permissions, no commands.
 */
public final class StaffService {
    private final ExemptionRegistry registry = new ExemptionRegistry();
    private final Supplier<Settings> settings;
    private final BiConsumer<UUID, ExemptionRegistry.Transition> onTransition;
    private LuckPermsBridge luckPerms;
    private Settings.Staff compiledFor;
    private StaffGroups groups;
    private int cursor;

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

    public Set<String> configuredGroupIds() {
        return groups().ids();
    }

    private StaffGroups groups() {
        Settings.Staff current = settings.get().staff();
        if (groups == null || compiledFor != current) {
            groups = StaffGroups.of(current);
            compiledFor = current;
        }
        return groups;
    }

    /** Server thread. Re-evaluates one player and reports a transition when the state changed. */
    public void refresh(Player player) {
        Settings.Staff config = settings.get().staff();
        Set<String> memberOf = luckPerms != null && config.luckPerms() ? luckPerms.effectiveGroupsOf(player) : null;
        Optional<Exemption> result = StaffEvaluator.evaluate(config, groups(),
                player.hasPermission(Exemption.BYPASS_PERMISSION), memberOf);
        ExemptionRegistry.Transition transition = registry.set(player.getUniqueId(), result.orElse(null));
        if (transition != ExemptionRegistry.Transition.UNCHANGED) {
            onTransition.accept(player.getUniqueId(), transition);
        }
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
    }

    public void forget(UUID id) {
        registry.forget(id);
    }

    /** Short text for status output. */
    public String integrationStatus() {
        Settings.Staff config = settings.get().staff();
        if (!config.exemptFromGameplayChecks()) {
            return "exemption disabled in config.yml";
        }
        String perm = "permission " + Exemption.BYPASS_PERMISSION + " active";
        if (!config.luckPerms()) {
            return "LuckPerms groups disabled in config.yml; " + perm;
        }
        if (luckPerms == null) {
            return "LuckPerms group integration inactive (LuckPerms not available); " + perm;
        }
        return "LuckPerms groups active " + groups().ids() + "; " + perm + "; exempt now: " + registry.size();
    }
}
