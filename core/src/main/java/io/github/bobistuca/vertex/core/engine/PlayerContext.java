package io.github.bobistuca.vertex.core.engine;

import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;

/** Server-authoritative state for one tracked player. */
public record PlayerContext(PlayerSnapshot snapshot, GraceState grace) { }
