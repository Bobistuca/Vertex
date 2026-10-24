package io.github.bobistuca.vertex.core.client;

import java.util.List;
import java.util.UUID;

/**
 * Client information gathered at join. Every value is untrusted: a brand can be spoofed by any client and a
 * "vanilla" brand does not prove a clean client. Injection status is never established by server-side data.
 */
public record ClientReport(UUID playerId, String playerName, String brand, Provenance brandProvenance,
                           int protocol, Provenance protocolProvenance, String versionName,
                           Provenance versionProvenance, List<String> channels, boolean timedOut) {

    /** Constant by design: a server cannot inspect the player's computer. */
    public static final String INJECTION_STATUS = "not-verifiable";
}
