package io.github.bobistuca.vertex.command;

import io.github.bobistuca.vertex.core.audit.AuditAspect;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.client.ClientReport;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsHolder;
import io.github.bobistuca.vertex.core.engine.PlayerContext;
import io.github.bobistuca.vertex.core.engine.PlayerReport;
import io.github.bobistuca.vertex.core.i18n.MessageCatalog;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.platform.CheckCatalog;
import io.github.bobistuca.vertex.core.platform.Messenger;
import io.github.bobistuca.vertex.core.recipient.CommandAccess;
import io.github.bobistuca.vertex.core.recipient.CommandAccess.Sub;
import io.github.bobistuca.vertex.core.storage.EvidenceEntry;
import io.github.bobistuca.vertex.runtime.VertexRuntime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /vertex} (alias {@code /vx}). Every subcommand is permission checked and feedback is localized. */
public final class VertexCommand implements TabExecutor {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final VertexRuntime runtime;

    public VertexCommand(VertexRuntime runtime) {
        this.runtime = runtime;
    }

    private static Sub resolve(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "help", "ajuda", "?" -> Sub.HELP;
            case "alerts", "alertas" -> Sub.ALERTS;
            case "inspect", "inspecionar" -> Sub.INSPECT;
            case "history", "historico", "histórico" -> Sub.HISTORY;
            case "status" -> Sub.STATUS;
            case "reload", "recarregar" -> Sub.RELOAD;
            default -> null;
        };
    }

    private boolean allowed(CommandSender sender, Sub sub) {
        boolean eligible = sender instanceof Player p && runtime.dispatcher().eligible(p);
        return CommandAccess.canUse(sub, sender::hasPermission, eligible);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageFactory f = runtime.messages();
        MessageCatalog c = f.catalog();
        if (args.length == 0) {
            help(sender);
            return true;
        }
        Sub sub = resolve(args[0]);
        if (sub == null) {
            reply(sender, c.render("command.unknown-sub", Map.of("sub", args[0])));
            return true;
        }
        if (!allowed(sender, sub)) {
            reply(sender, c.render("command.no-permission"));
            return true;
        }
        try {
            switch (sub) {
                case HELP -> help(sender);
                case ALERTS -> alerts(sender);
                case INSPECT -> inspect(sender, args);
                case HISTORY -> history(sender, args);
                case STATUS -> status(sender);
                case RELOAD -> reload(sender);
            }
        } catch (RuntimeException e) {
            runtime.plugin().getLogger().warning("Command failed: " + e.getClass().getSimpleName());
        }
        return true;
    }

    // ------------------------------------------------------------------ subcommands

    private void help(CommandSender sender) {
        MessageCatalog c = runtime.messages().catalog();
        reply(sender, c.render("command.help.header"));
        for (Sub sub : new Sub[] {Sub.ALERTS, Sub.INSPECT, Sub.HISTORY, Sub.STATUS, Sub.RELOAD}) {
            if (allowed(sender, sub)) {
                reply(sender, c.render("command.help." + sub.name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    private void alerts(CommandSender sender) {
        MessageCatalog c = runtime.messages().catalog();
        if (!(sender instanceof Player player)) {
            reply(sender, c.render("command.player-only"));
            return;
        }
        boolean optedOut = runtime.preferences().toggle(player.getUniqueId());
        reply(sender, c.render(optedOut ? "command.alerts.disabled" : "command.alerts.enabled"));
        if (!runtime.dispatcher().eligible(player)) {
            reply(sender, c.render("command.alerts.not-eligible"));
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        MessageCatalog c = runtime.messages().catalog();
        if (args.length < 2) {
            reply(sender, c.render("command.usage", Map.of("usage", "/vertex inspect [player]")));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            reply(sender, c.render("command.player-not-found", Map.of("name", args[1])));
            return;
        }
        UUID id = target.getUniqueId();
        reply(sender, c.render("command.inspect.header", Map.of("player", target.getName(), "uuid", id.toString())));
        PlayerContext ctx = runtime.tracker().context(id);
        PlayerSnapshot snap = ctx == null ? null : ctx.snapshot();
        line(sender, "label.online", "yes");
        line(sender, "label.gamemode", snap == null ? target.getGameMode().name() : snap.gameMode());
        line(sender, "label.ping", snap == null || snap.pingMillis() < 0 ? unknown() : snap.pingMillis() + " ms");
        line(sender, "label.tps", snap == null || Double.isNaN(snap.tps()) ? unknown()
                : String.format(Locale.ROOT, "%.1f", snap.tps()));
        ClientReport client = runtime.lastClientReport(id);
        line(sender, "label.client", client == null ? unknown()
                : (client.brand().isEmpty() ? unknown() : client.brand()) + " / "
                + (client.versionName().isEmpty() ? unknown() : client.versionName()));
        GraceState grace = runtime.tracker().grace(id);
        if (grace != null && ctx != null) {
            GraceState.Reason reason = grace.active(System.nanoTime(), runtime.settings().grace(), ctx.snapshot().tps());
            line(sender, "label.capabilities", "grace=" + (reason == null ? "none" : reason.name()));
        }
        PlayerReport report = runtime.engine().report(id).orElse(null);
        if (report == null) {
            reply(sender, c.render("command.inspect.none"));
            return;
        }
        line(sender, "label.incidents", report.incidentsByCheck().isEmpty() ? "0"
                : report.incidentsByCheck().toString());
        line(sender, "label.last", report.lastIncidentWallMillis() == 0
                ? c.render("label.never").toPlainText() : TIME.format(java.time.Instant.ofEpochMilli(report.lastIncidentWallMillis())) + " UTC");
        line(sender, "label.windows", report.analyzedWindows() + " (" + report.skippedWindows() + " skipped) "
                + report.lastWindowSummary());
    }

    private void history(CommandSender sender, String[] args) {
        MessageCatalog c = runtime.messages().catalog();
        UUID filter = null;
        String scope = "*";
        int limit = 10;
        for (int i = 1; i < args.length; i++) {
            Integer number = parseInt(args[i]);
            if (number != null) {
                limit = Math.max(1, Math.min(25, number));
            } else {
                Player target = Bukkit.getPlayerExact(args[i]);
                if (target == null) {
                    reply(sender, c.render("command.player-not-found", Map.of("name", args[i])));
                    return;
                }
                filter = target.getUniqueId();
                scope = target.getName();
            }
        }
        List<EvidenceEntry> entries = runtime.store().recent(filter, limit);
        reply(sender, c.render("command.history.header", Map.of("scope", scope, "count", Integer.toString(entries.size()))));
        if (entries.isEmpty()) {
            reply(sender, c.render("command.history.empty"));
            return;
        }
        for (EvidenceEntry e : entries) {
            reply(sender, c.render("command.history.entry", Map.of("time", TIME.format(java.time.Instant.ofEpochMilli(e.wallClockMillis())),
                    "player", e.playerName(), "check", e.checkId(), "count", Integer.toString(e.count()),
                    "kind", e.kind() + (e.suppressed() ? "*" : ""))));
        }
    }

    private void status(CommandSender sender) {
        MessageCatalog c = runtime.messages().catalog();
        var platform = runtime.platform();
        var caps = platform.capabilities();
        Messenger m = runtime.adapters().messenger();
        Settings s = runtime.settings();
        reply(sender, c.render("command.status.header"));
        line(sender, "label.platform", platform.serverName() + " (" + platform.serverVersion() + ")");
        line(sender, "label.minecraft", platform.minecraftVersion());
        line(sender, "label.java", System.getProperty("java.version"));
        line(sender, "label.messaging", m.id() + " | hover=" + m.supportsHover() + " | actionbar=" + m.supportsActionBar());
        var packets = runtime.packets();
        line(sender, "label.packets", packets.state().name().toLowerCase(Locale.ROOT)
                + (packets.failure().isEmpty() ? "" : " (" + packets.failure() + ")")
                + " | listener errors=" + packets.listenerErrors());
        var metrics = runtime.metrics();
        line(sender, "label.metrics", metrics.source() + " | tps=" + fmt(metrics.tps()) + " | mspt=" + fmt(metrics.mspt()));
        line(sender, "label.dependency", "PacketEvents " + packets.libraryVersion() + " (embedded, relocated); LuckPerms "
                + (runtime.luckPermsActive() ? "active" : "not detected"));
        var engine = runtime.engine();
        var analysis = runtime.analysis();
        var pipe = runtime.pipeline();
        line(sender, "label.health", "processed=" + engine.processed() + " failures=" + engine.failures()
                + " dropped=" + analysis.dropped() + " queued=" + analysis.queued() + " tracked=" + engine.trackedPlayers()
                + " alerts=" + pipe.delivered() + " duplicates=" + pipe.duplicates() + " suppressed=" + pipe.suppressed()
                + " open=" + pipe.openGroups());
        var store = runtime.store();
        line(sender, "label.storage", "written=" + store.written() + " dropped=" + store.dropped()
                + " failures=" + store.failures() + " queued=" + store.queued());
        line(sender, "label.audit", (s.audit().enabled() ? "on" : "off") + " | pending=" + runtime.auditor().pendingAttempts()
                + " | aspects=" + AuditAspect.values().length);
        Settings.Recipients r = s.alerts().recipients();
        line(sender, "label.recipients", "operators=" + r.operators() + " permission=" + r.permission() + " gameModes=" + r.gameModes());
        line(sender, "label.language", s.language());
        line(sender, "label.capabilities", "paper=" + caps.paper() + " nativeAdventure=" + caps.nativeAdventure()
                + " spigotApi=" + caps.spigotPlayerApi() + " bungeeChat=" + caps.bungeeChat() + " serverTps=" + caps.serverTps());
        for (CheckCatalog.Entry e : CheckCatalog.status(s, caps)) {
            reply(sender, e.active()
                    ? c.render("command.status.check-active", Map.of("check", e.id()))
                    : c.render("command.status.check-inactive", Map.of("check", e.id(), "reason", e.inactiveReason())));
        }
    }

    private void reload(CommandSender sender) {
        MessageCatalog before = runtime.messages().catalog();
        SettingsHolder.ReloadResult result = runtime.reload();
        MessageCatalog c = runtime.messages().catalog();
        if (result.applied()) {
            reply(sender, c.render("command.reload.ok"));
        } else {
            reply(sender, before.render("command.reload.failed"));
            for (String problem : result.problems()) {
                reply(sender, before.render("command.reload.problem", Map.of("problem", problem)));
            }
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String name : new String[] {"help", "alerts", "inspect", "history", "status", "reload"}) {
                Sub sub = resolve(name);
                if (allowed(sender, sub) && name.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
            return out;
        }
        Sub sub = resolve(args[0]);
        if (sub == null || !allowed(sender, sub)) {
            return out;
        }
        if (sub == Sub.INSPECT && args.length == 2 || sub == Sub.HISTORY && args.length >= 2) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
            if (sub == Sub.HISTORY) {
                for (String n : new String[] {"5", "10", "25"}) {
                    if (n.startsWith(prefix)) out.add(n);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private void line(CommandSender sender, String labelKey, String value) {
        MessageCatalog c = runtime.messages().catalog();
        reply(sender, c.render("command.line", Map.of("label", c.render(labelKey).toPlainText(), "value", value)));
    }

    private String unknown() {
        return runtime.messages().catalog().render("label.unknown").toPlainText();
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "unknown" : String.format(Locale.ROOT, "%.1f", v);
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void reply(CommandSender sender, Rich rich) {
        if (sender instanceof Player player) {
            runtime.adapters().messenger().send(player.getUniqueId(), rich);
        } else {
            sender.sendMessage(rich.toLegacyText());
        }
    }
}
