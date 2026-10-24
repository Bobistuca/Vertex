package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.aggregate.AlertPipeline;
import io.github.bobistuca.vertex.core.audit.Actor;
import io.github.bobistuca.vertex.core.audit.AdminAuditor;
import io.github.bobistuca.vertex.core.audit.AuditAspect;
import io.github.bobistuca.vertex.core.audit.AuditOutcome;
import io.github.bobistuca.vertex.core.audit.AuditRecord;
import io.github.bobistuca.vertex.core.audit.AuditSeverity;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Connects the {@link AdminAuditor} to the server: baselines at startup and join, event-driven observation where
 * events exist, and bounded round-robin reconciliation where they do not. Server thread only.
 */
public final class AuditCoordinator {
    private final Plugin plugin;
    private final AdminAuditor auditor;
    private final Supplier<Settings> settings;
    private final Supplier<MessageFactory> messages;
    private final AlertPipeline pipeline;
    private final AlertDispatcher dispatcher;
    private int cursor;
    private long lastOpCheckNanos;

    public AuditCoordinator(Plugin plugin, AdminAuditor auditor, Supplier<Settings> settings,
                            Supplier<MessageFactory> messages, AlertPipeline pipeline, AlertDispatcher dispatcher) {
        this.plugin = plugin;
        this.auditor = auditor;
        this.settings = settings;
        this.messages = messages;
        this.pipeline = pipeline;
        this.dispatcher = dispatcher;
    }

    /** Establishes the startup baseline: existing operators, tags, teams, modes and groups are not violations. */
    public void baselineAll(long nanos, long wall) {
        auditor.observeOperators(operators(), false, nanos, wall);
        for (Player p : Bukkit.getOnlinePlayers()) {
            baselinePlayer(p);
        }
    }

    public void baselinePlayer(Player p) {
        UUID id = p.getUniqueId();
        auditor.baseline(AuditAspect.GAME_MODE, id, Set.of(p.getGameMode().name()));
        auditor.baseline(AuditAspect.TAG, id, new TreeSet<>(p.getScoreboardTags()));
        auditor.baseline(AuditAspect.TEAM, id, teamValue(p));
        Set<String> granted = grantedNodes(p);
        auditor.baseline(AuditAspect.PERMISSION_NODE, id, granted);
    }

    public void baselineGroups(UUID id, Set<String> groups) {
        auditor.baseline(AuditAspect.PERMISSION_GROUP, id, groups);
    }

    public void forget(UUID id) {
        auditor.forget(id);
    }

    public void onCommand(Actor actor, String command, long nanos, long wall) {
        handle(auditor.onCommand(actor, command, nanos, wall));
    }

    /** Game-mode events fire before the change is applied, so the real state is read one tick later. */
    public void onGameModeEvent(Player player) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                handle(auditor.observe(AuditAspect.GAME_MODE, id, p.getName(), Set.of(p.getGameMode().name()), true,
                        System.nanoTime(), System.currentTimeMillis()));
            }
        });
    }

    public void onGroupsChanged(UUID id, Set<String> groups) {
        Player p = Bukkit.getPlayer(id);
        String name = p == null ? "unknown" : p.getName();
        handle(auditor.observe(AuditAspect.PERMISSION_GROUP, id, name, groups, true, System.nanoTime(),
                System.currentTimeMillis()));
    }

    /** Called about once per second. */
    public void tick(long nanos, long wall) {
        Settings.Audit cfg = settings.get().audit();
        if (!cfg.enabled()) {
            return;
        }
        long interval = cfg.opIntervalTicks() * 50_000_000L;
        if (nanos - lastOpCheckNanos >= interval) {
            lastOpCheckNanos = nanos;
            handle(auditor.observeOperators(operators(), false, nanos, wall));
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!online.isEmpty()) {
            for (int i = 0; i < Math.min(cfg.reconcilePerRun(), online.size()); i++) {
                Player p = online.get(Math.floorMod(cursor++, online.size()));
                reconcile(p, nanos, wall);
            }
        }
        handle(auditor.sweep(nanos, wall));
    }

    private void reconcile(Player p, long nanos, long wall) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        List<AuditRecord> out = new ArrayList<>();
        out.addAll(auditor.observe(AuditAspect.TAG, id, name, new TreeSet<>(p.getScoreboardTags()), false, nanos, wall));
        out.addAll(auditor.observe(AuditAspect.TEAM, id, name, teamValue(p), false, nanos, wall));
        out.addAll(auditor.observe(AuditAspect.GAME_MODE, id, name, Set.of(p.getGameMode().name()), false, nanos, wall));
        if (!settings.get().audit().monitoredPermissions().isEmpty()) {
            out.addAll(auditor.observe(AuditAspect.PERMISSION_NODE, id, name, grantedNodes(p), false, nanos, wall));
        }
        handle(out);
    }

    private Set<String> grantedNodes(Player p) {
        Set<String> granted = new TreeSet<>();
        for (String node : settings.get().audit().monitoredPermissions()) {
            if (p.hasPermission(node)) {
                granted.add(node);
            }
        }
        return granted;
    }

    private static Set<String> teamValue(Player p) {
        try {
            Scoreboard board = p.getScoreboard();
            Team team = board.getEntryTeam(p.getName());
            if (team == null) {
                return Set.of();
            }
            return Set.of(team.getName() + "|" + team.getPrefix());
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static Map<UUID, String> operators() {
        Map<UUID, String> ops = new LinkedHashMap<>();
        for (OfflinePlayer op : Bukkit.getOperators()) {
            ops.put(op.getUniqueId(), op.getName() == null ? "unknown" : op.getName());
        }
        return ops;
    }

    private void handle(List<AuditRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        Settings.Audit cfg = settings.get().audit();
        for (AuditRecord r : records) {
            pipeline.recordAudit(r);
            boolean notify = switch (r.outcome()) {
                case ATTEMPT -> false;
                case CHANGED -> r.severity() == AuditSeverity.ATTENTION || cfg.notifyAuthorized();
                case NO_CHANGE -> cfg.notifyNoChange();
            };
            if (notify && r.outcome() != AuditOutcome.ATTEMPT) {
                dispatcher.deliverRich(messages.get().adminLine(r), true);
            }
        }
    }
}
