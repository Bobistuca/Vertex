package io.github.bobistuca.vertex.integration;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Optional LuckPerms integration for group membership auditing. It is referenced only after LuckPerms is found
 * enabled, so Vertex loads normally without it. LuckPerms events do not name the actor; attribution relies on the
 * command correlation done by the auditor.
 */
public final class LuckPermsBridge implements AutoCloseable {
    private final Plugin plugin;
    private final Logger logger;
    private final BiConsumer<UUID, Set<String>> onGroupsChanged;
    private EventSubscription<NodeAddEvent> add;
    private EventSubscription<NodeRemoveEvent> remove;
    private LuckPerms api;

    public LuckPermsBridge(Plugin plugin, BiConsumer<UUID, Set<String>> onGroupsChanged) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.onGroupsChanged = onGroupsChanged;
    }

    public static boolean pluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    public boolean start() {
        try {
            api = LuckPermsProvider.get();
            add = api.getEventBus().subscribe(plugin, NodeAddEvent.class, this::handle);
            remove = api.getEventBus().subscribe(plugin, NodeRemoveEvent.class, this::handle);
            return true;
        } catch (Throwable t) {
            logger.warning("LuckPerms integration unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Current group names of a loaded user, or {@code null} when the user is not loaded. */
    public Set<String> groupsOf(UUID id) {
        try {
            User user = api == null ? null : api.getUserManager().getUser(id);
            return user == null ? null : groups(user);
        } catch (Throwable t) {
            return null;
        }
    }

    private void handle(NodeMutateEvent event) {
        if (!event.isUser() || !(event.getTarget() instanceof User user)) {
            return;
        }
        UUID id = user.getUniqueId();
        Set<String> groups = groups(user);
        Bukkit.getScheduler().runTask(plugin, () -> onGroupsChanged.accept(id, groups));
    }

    private static Set<String> groups(User user) {
        Set<String> out = new TreeSet<>();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            out.add(node.getGroupName());
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (add != null) {
                add.close();
            }
            if (remove != null) {
                remove.close();
            }
        } catch (Throwable ignored) {
            // Shutting down.
        }
    }
}
