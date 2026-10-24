package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.aggregate.AlertPipeline;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.platform.Messenger;
import io.github.bobistuca.vertex.core.recipient.RecipientPolicy;
import io.github.bobistuca.vertex.core.recipient.RecipientView;
import io.github.bobistuca.vertex.core.storage.PreferenceStore;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.engine.PlayerContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Delivers alerts privately to eligible recipients on the server thread. Nothing is ever broadcast: the audience is
 * decided by {@link RecipientPolicy} and each recipient can opt out. The console receives a plain-text log line.
 */
public final class AlertDispatcher {
    private final Plugin plugin;
    private final Supplier<MessageFactory> messages;
    private final Supplier<Settings> settings;
    private final PreferenceStore preferences;
    private final AlertPipeline pipeline;
    private final Supplier<Messenger> messenger;
    private final PlayerTracker tracker;

    public AlertDispatcher(Plugin plugin, Supplier<MessageFactory> messages, Supplier<Settings> settings,
                           PreferenceStore preferences, AlertPipeline pipeline, Supplier<Messenger> messenger,
                           PlayerTracker tracker) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
        this.preferences = preferences;
        this.pipeline = pipeline;
        this.messenger = messenger;
        this.tracker = tracker;
    }

    public RecipientPolicy policy() {
        Settings.Recipients r = settings.get().alerts().recipients();
        return new RecipientPolicy(r.operators(), r.permission(), r.gameModes());
    }

    public RecipientView view(Player player) {
        return new RecipientView(player.isOp(), player.hasPermission("vertex.alerts"),
                player.getGameMode().name(), preferences.isOptedOut(player.getUniqueId()));
    }

    /** Whether the player qualifies as an alert recipient, ignoring their personal toggle. */
    public boolean eligible(Player player) {
        return policy().isEligible(view(player));
    }

    /** Gameplay alert for one closed aggregation window. */
    public void deliver(AggregatedAlert alert) {
        MessageFactory factory = messages.get();
        UUID subject = alert.first().playerId();
        PlayerContext ctx = tracker.context(subject);
        PlayerSnapshot snap = ctx == null ? null : ctx.snapshot();
        String ping = snap == null || snap.pingMillis() < 0 ? "?" : snap.pingMillis() + " ms";
        String tps = snap == null || Double.isNaN(snap.tps()) ? "?" : String.format(Locale.ROOT, "%.1f", snap.tps());
        String mode = snap == null ? "?" : snap.gameMode();
        Rich line = factory.alertLine(alert, ping, tps, mode);
        if (settings.get().alerts().logToConsole()) {
            plugin.getLogger().info(line.toPlainText());
        }
        send(line, subject, true);
    }

    /** Administrative or other single-line notification. */
    public void deliverRich(Rich line, boolean log) {
        if (log && settings.get().alerts().logToConsole()) {
            plugin.getLogger().info(line.toPlainText());
        }
        send(line, null, false);
    }

    /** Multi-line notification (client report). */
    public void deliverLines(List<Rich> lines, boolean log) {
        if (log && settings.get().alerts().logToConsole()) {
            for (Rich line : lines) {
                plugin.getLogger().info(line.toPlainText());
            }
        }
        RecipientPolicy policy = policy();
        Messenger m = messenger.get();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (policy.shouldReceive(view(p)) && pipeline.allowForRecipient(p.getUniqueId())) {
                for (Rich line : lines) {
                    m.send(p.getUniqueId(), line);
                }
            }
        }
    }

    private void send(Rich line, UUID excludeSubject, boolean sound) {
        RecipientPolicy policy = policy();
        Messenger m = messenger.get();
        Settings.Sound snd = settings.get().alerts().sound();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (excludeSubject != null && p.getUniqueId().equals(excludeSubject)) {
                continue;
            }
            if (!policy.shouldReceive(view(p)) || !pipeline.allowForRecipient(p.getUniqueId())) {
                continue;
            }
            m.send(p.getUniqueId(), line);
            if (sound && snd.enabled()) {
                try {
                    p.playSound(p.getLocation(), snd.name(), (float) snd.volume(), (float) snd.pitch());
                } catch (RuntimeException ignored) {
                    // An invalid sound name must never disturb alert delivery.
                }
            }
        }
    }

    /** Optional live "Nx" counter in the action bar while a window is still open. */
    public void actionBar(AlertPipeline.LiveUpdate update) {
        Messenger m = messenger.get();
        if (!m.supportsActionBar()) {
            return;
        }
        Rich bar = messages.get().actionBar(update.playerName(), update.checkId(), update.count());
        RecipientPolicy policy = policy();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(update.playerId()) && policy.shouldReceive(view(p))) {
                m.sendActionBar(p.getUniqueId(), bar);
            }
        }
    }
}
