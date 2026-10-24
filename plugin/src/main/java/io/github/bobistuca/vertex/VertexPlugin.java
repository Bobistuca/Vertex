package io.github.bobistuca.vertex;

import io.github.bobistuca.vertex.command.VertexCommand;
import io.github.bobistuca.vertex.runtime.VertexRuntime;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vertex: a Minecraft anti-cheat that only observes, records evidence and notifies authorized staff privately.
 * It never punishes, teleports, cancels events or blocks packets.
 */
public final class VertexPlugin extends JavaPlugin {
    private VertexRuntime runtime;

    @Override
    public void onLoad() {
        runtime = new VertexRuntime(this);
        runtime.load();
    }

    @Override
    public void onEnable() {
        try {
            runtime.enable();
        } catch (RuntimeException | LinkageError e) {
            getLogger().severe("Vertex failed to start and will be disabled: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PluginCommand command = getCommand("vertex");
        if (command != null) {
            VertexCommand handler = new VertexCommand(runtime);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.disable();
        }
    }
}
