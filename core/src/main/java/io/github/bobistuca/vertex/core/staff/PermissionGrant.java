package io.github.bobistuca.vertex.core.staff;

/** How the bypass permission is held by a player. */
public enum PermissionGrant {
    /** Not granted. */
    NONE,
    /** The exact node {@code vertex.bypass.gameplay} is granted. */
    EXPLICIT,
    /** Only a wildcard (for example {@code *}) makes the node effective. */
    WILDCARD_ONLY
}
