package io.github.bobistuca.vertex.core.audit;

import java.util.UUID;

/** Who performed a change. {@code UNKNOWN} means attribution was not established, not that someone is guilty. */
public record Actor(Kind kind, UUID id, String name) {
    public enum Kind { CONSOLE, PLAYER, UNKNOWN }

    public static final Actor UNKNOWN = new Actor(Kind.UNKNOWN, null, "unknown");
    public static final Actor CONSOLE = new Actor(Kind.CONSOLE, null, "CONSOLE");

    public static Actor player(UUID id, String name) {
        return new Actor(Kind.PLAYER, id, name);
    }
}
