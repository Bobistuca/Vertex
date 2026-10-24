package io.github.bobistuca.vertex.paper;

import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.i18n.RichSpan;
import io.github.bobistuca.vertex.core.platform.Messenger;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Native Adventure messenger. Used only on Paper, where Adventure audiences are part of the server API. */
final class PaperMessenger implements Messenger {
    @Override
    public String id() {
        return "paper-adventure";
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
            player.sendMessage(convert(message));
        }
    }

    @Override
    public void sendActionBar(UUID playerId, Rich message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendActionBar(convert(message));
        }
    }

    static Component convert(Rich rich) {
        Component root = Component.empty();
        for (RichSpan span : rich.spans()) {
            Component part = Component.text(span.text());
            if (span.rgb() >= 0) {
                part = part.color(TextColor.color(span.rgb()));
            }
            part = part.decoration(TextDecoration.BOLD, span.bold())
                    .decoration(TextDecoration.ITALIC, span.italic())
                    .decoration(TextDecoration.UNDERLINED, span.underlined());
            if (span.hover() != null) {
                part = part.hoverEvent(HoverEvent.showText(convert(span.hover())));
            }
            root = root.append(part);
        }
        return root;
    }
}
