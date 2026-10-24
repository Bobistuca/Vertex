package io.github.bobistuca.vertex.paper;

import io.github.bobistuca.vertex.core.platform.PlatformAdapters;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Entry point loaded reflectively by Vertex when it runs on Paper. */
public final class PaperAdapters {
    private PaperAdapters() { }

    public static PlatformAdapters create() {
        Function<UUID, String> brand = id -> {
            Player player = Bukkit.getPlayer(id);
            return player == null ? null : player.getClientBrandName();
        };
        return new PlatformAdapters("Paper", new PaperMessenger(), new PaperMetrics(), brand);
    }
}
