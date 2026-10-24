package io.github.bobistuca.vertex.messaging;

import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.platform.Messenger;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Last-resort messenger that uses only the Bukkit API: colored text through legacy section-sign codes with
 * hexadecimal colors. Hover text and the action bar are unavailable and the status command says so.
 */
public final class LegacyMessenger implements Messenger {
    @Override
    public String id() {
        return "bukkit-legacy-text";
    }

    @Override
    public boolean supportsHover() {
        return false;
    }

    @Override
    public boolean supportsActionBar() {
        return false;
    }

    @Override
    public void send(UUID playerId, Rich message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message.toLegacyText());
        }
    }

    @Override
    public void sendActionBar(UUID playerId, Rich message) {
        // Not available through the Bukkit API.
    }
}
