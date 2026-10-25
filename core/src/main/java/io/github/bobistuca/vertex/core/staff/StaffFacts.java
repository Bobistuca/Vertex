package io.github.bobistuca.vertex.core.staff;

import java.util.Collection;

/**
 * Server-authoritative inputs of the exemption decision.
 *
 * @param operator    the player is a server operator
 * @param permission  how the bypass permission is held
 * @param memberOf    effective LuckPerms group ids (inherited, active contexts, no expired memberships), or
 *                    {@code null} when group data is unavailable
 */
public record StaffFacts(boolean operator, PermissionGrant permission, Collection<String> memberOf) { }
