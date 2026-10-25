package io.github.bobistuca.vertex.core.recipient;

/**
 * Immutable view of a potential alert recipient.
 *
 * @param operator         whether the player is a server operator
 * @param hasAlertPermission whether the player has {@code vertex.alerts}
 * @param administrativeStaff whether the player belongs to a configured administrative LuckPerms group
 * @param gameMode         upper-case game mode name (for example {@code CREATIVE})
 * @param optedOut         whether the player disabled their personal alerts
 */
public record RecipientView(boolean operator, boolean hasAlertPermission, boolean administrativeStaff,
                            String gameMode, boolean optedOut) { }
