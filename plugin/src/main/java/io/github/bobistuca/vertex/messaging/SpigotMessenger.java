package io.github.bobistuca.vertex.messaging;

import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.i18n.RichSpan;
import io.github.bobistuca.vertex.core.platform.Messenger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Messenger for servers that provide the Spigot player API and BungeeCord chat components. Loaded only when
 * both are present at runtime, so it never loads on plain CraftBukkit.
 */
public final class SpigotMessenger implements Messenger {
    @Override
    public String id() {
        return "spigot-bungee-chat";
    }

    @Override
    public boolean supportsHover() {
        return true;
    }

    @Override
    public boolean supportsActionBar() {
        return true;
    }

    @Override
    public void send(UUID playerId, Rich message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.spigot().sendMessage(convert(message));
        }
    }

    @Override
    public void sendActionBar(UUID playerId, Rich message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, convert(message));
        }
    }

    static BaseComponent[] convert(Rich rich) {
        List<BaseComponent> parts = new ArrayList<>(rich.spans().size());
        for (RichSpan span : rich.spans()) {
            TextComponent component = new TextComponent(span.text());
            if (span.rgb() >= 0) {
                component.setColor(ChatColor.of(String.format(Locale.ROOT, "#%06x", span.rgb())));
            }
            component.setBold(span.bold());
            component.setItalic(span.italic());
            component.setUnderlined(span.underlined());
            if (span.hover() != null) {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(convert(span.hover()))));
            }
            parts.add(component);
        }
        return parts.toArray(new BaseComponent[0]);
    }
}
