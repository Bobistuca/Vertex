package io.github.bobistuca.vertex.integration;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Optional, read-only LuckPerms integration built on the official API (compile-only dependency; the LuckPerms plugin
 * is never bundled). Vertex never modifies stored groups and never assigns ranks.
 *
 * <p>It provides (1) the direct group set for auditing, and (2) the effective group membership of an online player,
 * including inherited groups, in the player's active contexts and honouring temporary memberships. LuckPerms
 * events can arrive on any thread, so every callback is rescheduled onto the server thread.
 */
public final class LuckPermsBridge implements AutoCloseable {
    private final Plugin plugin;
    private final Logger logger;
    private final BiConsumer<UUID, Set<String>> onDirectGroupsChanged;
    private final Consumer<UUID> onUserRecalculated;
    private EventSubscription<NodeAddEvent> add;
    private EventSubscription<NodeRemoveEvent> remove;
    private EventSubscription<UserDataRecalculateEvent> recalculate;
    private LuckPerms api;

    public LuckPermsBridge(Plugin plugin, BiConsumer<UUID, Set<String>> onDirectGroupsChanged,
                           Consumer<UUID> onUserRecalculated) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.onDirectGroupsChanged = onDirectGroupsChanged;
        this.onUserRecalculated = onUserRecalculated;
    }

    public static boolean pluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    public boolean start() {
        try {
            api = LuckPermsProvider.get();
            add = api.getEventBus().subscribe(plugin, NodeAddEvent.class, this::handleNode);
            remove = api.getEventBus().subscribe(plugin, NodeRemoveEvent.class, this::handleNode);
            // Fired whenever a user's cached data is recalculated: group changes, inheritance, context changes
            // (for example a world switch) and temporary memberships that were added or expired.
            recalculate = api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, this::handleRecalculate);
            return true;
        } catch (Throwable t) {
            logger.warning("LuckPerms integration unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Direct group names of a loaded user (for auditing), or {@code null} when the user is not loaded. */
    public Set<String> directGroupsOf(UUID id) {
        try {
            User user = api == null ? null : api.getUserManager().getUser(id);
            return user == null ? null : directGroups(user);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Effective group identifiers (lower case) of an online player: direct and inherited groups in the player's
     * current server/world contexts, excluding expired temporary memberships. {@code null} when unavailable.
     * Server thread only (the contexts are read from the Bukkit player).
     */
    public Set<String> effectiveGroupsOf(Player player) {
        try {
            if (api == null) {
                return null;
            }
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }
            QueryOptions options = api.getContextManager().getQueryOptions(player);
            Set<String> out = new TreeSet<>();
            for (Group group : user.getInheritedGroups(options)) {
                out.add(group.getName().toLowerCase(Locale.ROOT));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private void handleNode(NodeMutateEvent event) {
        if (!event.isUser() || !(event.getTarget() instanceof User user)) {
            return;
        }
        UUID id = user.getUniqueId();
        Set<String> groups = directGroups(user);
        Bukkit.getScheduler().runTask(plugin, () -> onDirectGroupsChanged.accept(id, groups));
    }

    private void handleRecalculate(UserDataRecalculateEvent event) {
        UUID id = event.getUser().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> onUserRecalculated.accept(id));
    }

    private static Set<String> directGroups(User user) {
        Set<String> out = new TreeSet<>();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (!node.hasExpired()) {
                out.add(node.getGroupName());
            }
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (add != null) add.close();
            if (remove != null) remove.close();
            if (recalculate != null) recalculate.close();
        } catch (Throwable ignored) {
            // Shutting down.
        }
    }
}
