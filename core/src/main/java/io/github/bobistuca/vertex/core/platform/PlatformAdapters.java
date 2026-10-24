package io.github.bobistuca.vertex.core.platform;

import java.util.UUID;
import java.util.function.Function;

/**
 * Platform-specific services supplied by an adapter module.
 *
 * @param clientBrand optional lookup of a client brand exposed by the server API (untrusted text, may return null)
 */
public record PlatformAdapters(String platformName, Messenger messenger, ServerMetrics metrics,
                               Function<UUID, String> clientBrand) { }
