package io.github.bobistuca.vertex.core.platform;

import io.github.bobistuca.vertex.core.i18n.Rich;
import java.util.UUID;

/** Delivers rich text to an online player. Implementations are chosen by runtime capability checks. */
public interface Messenger {
    /** Short implementation name for status output (for example {@code paper-adventure}). */
    String id();

    /** Whether hover text can be shown. When false, the plain colored text is delivered instead. */
    boolean supportsHover();

    boolean supportsActionBar();

    /** Server thread only. A missing player is ignored. */
    void send(UUID playerId, Rich message);

    /** Server thread only. Implementations without action-bar support do nothing. */
    void sendActionBar(UUID playerId, Rich message);
}
