package io.github.bobistuca.vertex.core.engine;

import java.util.List;
import java.util.Map;

/** Bounded diagnostic view of a player's detection state. Cheap to build; no packet logging is involved. */
public record Diagnosis(Map<String, Long> packets, long lastPacketAgoMillis, long observationsDropped,
                        List<CheckDiagnosis> checks) { }
